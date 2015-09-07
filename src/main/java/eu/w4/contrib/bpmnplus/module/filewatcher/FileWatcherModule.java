package eu.w4.contrib.bpmnplus.module.filewatcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.w4.common.exception.CheckedException;
import eu.w4.common.log.Logger;
import eu.w4.common.log.LoggerFactory;
import eu.w4.contrib.bpmnplus.module.filewatcher.ConfigurationManager.DataType;
import eu.w4.engine.client.bpmn.w4.collaboration.CollaborationIdentifier;
import eu.w4.engine.client.bpmn.w4.events.SignalIdentifier;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsIdentifier;
import eu.w4.engine.client.bpmn.w4.process.ProcessIdentifier;
import eu.w4.engine.client.configuration.ConfigurationHelper;
import eu.w4.engine.client.eci.ItemIdentifier;
import eu.w4.engine.client.eci.service.EciContentService;
import eu.w4.engine.client.eci.service.EciObjectFactory;
import eu.w4.engine.client.service.EventService;
import eu.w4.engine.client.service.ObjectFactory;
import eu.w4.engine.client.service.ProcessService;
import eu.w4.engine.core.module.external.ExternalModule;
import eu.w4.engine.core.module.external.ExternalModuleContext;

public class FileWatcherModule implements ExternalModule, Runnable
{
  private static final long TIME_BETWEEN_SHUTDOWN_AND_STARTUP = 1000;

  private Logger _logger = LoggerFactory.getLogger(FileWatcherModule.class.getName());

  private ExternalModuleContext _context;

  private Thread _module;

  private FileSystem _defaultFilesystem;

  private WatchService _watchService;

  private Map<WatchKey, Path> _watchKeyPathes;
  private Map<WatchKey, SourceConfig> _watchKeyConfig;

  private String _login;
  private String _password;

  @Override
  public void startup(ExternalModuleContext context) throws CheckedException, RemoteException
  {
    _context = context;

    _watchKeyPathes = new ConcurrentHashMap<WatchKey, Path>();
    _watchKeyConfig = new ConcurrentHashMap<WatchKey, SourceConfig>();

    _defaultFilesystem = FileSystems.getDefault();
    try
    {
      _watchService = _defaultFilesystem.newWatchService();
    }
    catch (IOException e)
    {
      _logger.error("Unexpected exception while trying to create a file watcher. Module will be disabled.", e);
    }

    _login = ConfigurationHelper.getStringValue(_context.getParameters(), "module.filewatcher.login");
    _password = ConfigurationHelper.getStringValue(_context.getParameters(), "module.filewatcher.password");
    
    final ConfigurationManager configurationManager = new ConfigurationManager(context);
    final Principal principal = getPrincipal();
    final Collection<SourceConfig> allSources = configurationManager.getConfiguration(principal);
    _context.getEngineService().getAuthenticationService().logout(principal);
    if (_logger.isDebugEnabled())
    {
      _logger.debug("Found [" + allSources.size() + "] sources: " + allSources);
    }
    final List<SourceConfig> validSources = configurationManager.validateConfiguration(allSources);
    if (_logger.isDebugEnabled())
    {
      _logger.debug("Retained [" + validSources.size() + "] valid sources: " + validSources);
    }

    for (final SourceConfig source : validSources)
    {
      setupWatcher(source, source.getRepository().getPath());
      if (_logger.isInfoEnabled())
      {
        _logger.info("FileWatcher setup for source [" + source.getName() + "]");
      }
    }

    _module = new Thread(this, this.getClass().getSimpleName());
    _module.setContextClassLoader(this.getClass().getClassLoader());

    _module.start();
    if (_logger.isInfoEnabled())
    {
      _logger.info("FileWatcher module successfully started");
    }
  }

  @Override
  public void shutdown() throws CheckedException, RemoteException
  {
    _module.interrupt();
    try
    {
      _module.join();
    }
    catch (final InterruptedException e)
    {
      _logger.error("Shutdown has been interrupted while waiting for working thread", e);
    }

    for (final WatchKey key : _watchKeyPathes.keySet())
    {
      key.cancel();
    }
  }

  private Principal getPrincipal() throws CheckedException, RemoteException
  {
    return _context.getEngineService().getAuthenticationService().login(_login, _password);
  }

  @Override
  public long getShutdownStartupSleepTime() throws CheckedException, RemoteException
  {
    return TIME_BETWEEN_SHUTDOWN_AND_STARTUP;
  }

  public void setupWatcher(final SourceConfig sourceConfig,
                           final String directoryPath)
  {
    final Path watchedDirectory = _defaultFilesystem.getPath(directoryPath);

    try
    {
      final WatchKey key = watchedDirectory.register(_watchService,
                                                     StandardWatchEventKinds.ENTRY_CREATE);
      _watchKeyPathes.put(key, watchedDirectory);
      _watchKeyConfig.put(key, sourceConfig);

      if (sourceConfig.getWatcher().isRecursive())
      {
        for (final File child : new File(directoryPath).listFiles())
        {
          if (child.isDirectory())
          {
            setupWatcher(sourceConfig, child.getAbsolutePath());
          }
        }
      }
    }
    catch (final IOException e)
    {
      _logger.error("Could not register directory [" + directoryPath + "]", e);
    }
  }

  public void tearDownWatcher(final WatchKey watchKey)
  {
    watchKey.cancel();
    _watchKeyPathes.remove(watchKey);
    _watchKeyConfig.remove(watchKey);
  }

  @Override
  public void run()
  {
    while (!_module.isInterrupted())
    {
      final WatchKey key;
      try
      {
        key = _watchService.take();
      }
      catch (InterruptedException e)
      {
        return;
      }

      try
      {
        final Path parent = _watchKeyPathes.get(key);
        final SourceConfig sourceConfig = _watchKeyConfig.get(key);
        if (parent == null || sourceConfig == null)
        {
          _logger.warning("Received an event from an unknown directory");
          continue;
        }

        for (WatchEvent<?> event : key.pollEvents())
        {
          final WatchEvent.Kind<?> kind = event.kind();

          if (kind == StandardWatchEventKinds.OVERFLOW)
          {
            _logger.error("Filesystem event overflow in ECI source [" + sourceConfig.getName() + "]. Some files may not have been detected");
            continue;
          }

          if (kind != StandardWatchEventKinds.ENTRY_CREATE)
          {
            continue;
          }

          try
          {
            @SuppressWarnings("unchecked")
            final WatchEvent<Path> watchEvent = (WatchEvent<Path>)event;
            final Path filename = watchEvent.context();
            final Path file = parent.resolve(filename);
  
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
            {
              if (_logger.isDebugEnabled())
              {
                _logger.debug("New directory detected in ECI source [" + sourceConfig.getName() + "] at path [" + file.toString() + "]");
              }
              if (sourceConfig.getWatcher().isRecursive())
              {
                setupWatcher(sourceConfig, file.toAbsolutePath().toString());
              }
              else if (sourceConfig.getWatcher().isPickingDirectories())
              {
                process(sourceConfig, file);
              }
            }
            else
            {
              if (_logger.isDebugEnabled())
              {
                _logger.debug("New file detected in ECI source [" + sourceConfig.getName() + "] at path [" + file.toString() + "]");
              }
              if (sourceConfig.getWatcher().isPickingFiles())
              {
                process(sourceConfig, file);
              }
            }
          }
          catch (final Exception e)
          {
            _logger.error("Could not process event [" + event.kind() + "] on [" + event.context() + "] in ECI source [" + sourceConfig.getName() + "]", e);
          }
        }
      }
      finally
      {
        boolean valid = key.reset();
        if (!valid)
        {
          tearDownWatcher(key);
        }
      }
    }
  }

  /**
   * Process method is called whenever a file is detected in watched directories
   * 
   * @param file
   */
  public void process(final SourceConfig sourceConfig, final Path file)
    throws CheckedException, RemoteException
  {
    final WatcherConfig watcherConfig = sourceConfig.getWatcher();
    final ObjectFactory objectFactory = _context.getEngineService().getObjectFactory();
    final Principal principal = getPrincipal();

    final DefinitionsIdentifier definitionsIdentifier = objectFactory.newDefinitionsIdentifier();
    definitionsIdentifier.setId(watcherConfig.getDefinitionsIdentifier());
    definitionsIdentifier.setVersion(watcherConfig.getDefinitionsVersion());

    final Object payload;
    if (DataType.PATH.name().equals(watcherConfig.getDataType()))
    {
      payload = file.toString();
    }
    else if (DataType.ECI.name().equals(watcherConfig.getDataType()))
    {
      final Path rootPath = _defaultFilesystem.getPath(sourceConfig.getRepository().getPath());
      final Path relativePath = rootPath.relativize(file);
      final EciContentService eciContentService = _context.getEngineService().getEciContentService();
      final EciObjectFactory eciObjectFactory = _context.getEngineService().getEciObjectFactory();
      final ItemIdentifier itemIdentifier = eciObjectFactory.newItemIdentifier();
      itemIdentifier.setSourceName(sourceConfig.getName());
      itemIdentifier.setRepositoryName(sourceConfig.getRepository().getName());
      itemIdentifier.setId(relativePath.toString());
      payload = eciContentService.getDocument(principal, itemIdentifier, null);
    }
    else
    {
      throw new CheckedException("Mode [" + watcherConfig.getDataType() + "] is not implemented");
    }

    if (watcherConfig.getProcessIdentifier() != null)
    {
      final ProcessService processService = _context.getEngineService().getProcessService();

      final CollaborationIdentifier collaborationIdentifier;
      if (watcherConfig.getCollaborationIdentifier() != null)
      {
        collaborationIdentifier = objectFactory.newCollaborationIdentifier();
        collaborationIdentifier.setDefinitionsIdentifier(definitionsIdentifier);
        collaborationIdentifier.setId(watcherConfig.getCollaborationIdentifier());
      }
      else
      {
        collaborationIdentifier = null;
      }

      final ProcessIdentifier processIdentifier = objectFactory.newProcessIdentifier();
      processIdentifier.setDefinitionsIdentifier(definitionsIdentifier);
      processIdentifier.setId(watcherConfig.getProcessIdentifier());

      final Map<String, Object> dataEntries = new HashMap<String, Object>();
      dataEntries.put(watcherConfig.getDataEntry(), payload);

      processService.instantiateProcess(principal,
                                        collaborationIdentifier,
                                        processIdentifier,
                                        file.getFileName().toString(),
                                        true,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null);
    }
    else if (watcherConfig.getSignalIdentifier() != null)
    {
      final EventService eventService = _context.getEngineService().getEventService();
      final SignalIdentifier signalIdentifier = objectFactory.newSignalIdentifier();
      signalIdentifier.setDefinitionsIdentifier(definitionsIdentifier);
      signalIdentifier.setId(watcherConfig.getSignalIdentifier());

      eventService.triggerSignal(principal, signalIdentifier, null, payload);
    }

    _context.getEngineService().getAuthenticationService().logout(principal);
  }
}
