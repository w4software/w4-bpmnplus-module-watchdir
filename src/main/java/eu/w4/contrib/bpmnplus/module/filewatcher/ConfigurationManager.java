package eu.w4.contrib.bpmnplus.module.filewatcher;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import eu.w4.common.configuration.ConfigurationKeyNotFoundException;
import eu.w4.common.exception.CheckedException;
import eu.w4.common.log.Logger;
import eu.w4.common.log.LoggerFactory;
import eu.w4.engine.client.configuration.ConfigurationHelper;
import eu.w4.engine.client.eci.service.EciContentService;
import eu.w4.engine.core.module.external.ExternalModuleContext;

class ConfigurationManager
{
  private static final List<String> SUPPORTED_DRIVERS = Arrays.asList("eu.w4.engine.core.eci.filesystem.FileSystemDriver");

  private static final String CONFIGURATION_KEY_REPOSITORY_DRIVER = "core.eci.%s.driverClassName";
  private static final String CONFIGURATION_KEY_REPOSITORY_ROOT_FOLDER_PATH = "core.eci.%s.property.rootFolderPath";

  private static final String CONFIGURATION_KEY_WATCHER_ENABLED = "core.eci.source.%s.watch";
  private static final String CONFIGURATION_KEY_WATCHER_TARGET_DEFINITIONS = "core.eci.source.%s.watch.definitionsIdentifier";
  private static final String CONFIGURATION_KEY_WATCHER_TARGET_DEFINITIONS_VERSION = "core.eci.source.%s.watch.definitionsVersion";
  private static final String CONFIGURATION_KEY_WATCHER_TARGET_COLLABORATION = "core.eci.source.%s.watch.collaborationIdentifier";
  private static final String CONFIGURATION_KEY_WATCHER_TARGET_PROCESS = "core.eci.source.%s.watch.processIdentifier";
  private static final String CONFIGURATION_KEY_WATCHER_TARGET_SIGNAL = "core.eci.source.%s.watch.signalIdentifier";
  private static final String CONFIGURATION_KEY_WATCHER_TARGET_DATA_ENTRY = "core.eci.source.%s.watch.dataEntry";
  private static final String CONFIGURATION_KEY_WATCHER_TARGET_DATATYPE = "core.eci.source.%s.watch.dataType";
  private static final String CONFIGURATION_KEY_WATCHER_RECURSIVE = "core.eci.source.%s.watch.recursive";
  private static final String CONFIGURATION_KEY_WATCHER_PICK_DIRECTORIES = "core.eci.source.%s.watch.pickDirectories";
  private static final String CONFIGURATION_KEY_WATCHER_PICK_FILES = "core.eci.source.%s.watch.pickFiles";

  public static enum DataType
  {
    PATH,
    ECI
  }

  private Logger _logger = LoggerFactory.getLogger(ConfigurationManager.class.getName());

  private ExternalModuleContext _context;

  public ConfigurationManager(final ExternalModuleContext context)
  {
    _context = context;
  }

  public Collection<SourceConfig> getConfiguration(final Principal principal) throws CheckedException, RemoteException
  {
    final EciContentService eciContentService = _context.getEngineService().getEciContentService();
    final Map<String, Boolean> sourceNames = eciContentService.getSourceNamesWithAvailability(principal);
    final Map<String, Boolean> repositoryNames = eciContentService.getRepositoryNamesWithAvailability(principal);
    final Map<String, Set<String>> repositorySourceMapping = eciContentService.getRepositoryNamesWithSourceNames(principal);
    final Map<String, String> sourceRepositoryMapping = reverseMapping(repositorySourceMapping);
    
    final Map<String, RepositoryConfig> repositories = new HashMap<String, RepositoryConfig>();
    for (final Entry<String, Boolean> repositoryEntry : repositoryNames.entrySet())
    {
      final RepositoryConfig repository = new RepositoryConfig();
      repository.setName(repositoryEntry.getKey());
      repository.setAvailable(repositoryEntry.getValue());
      repository.setDriver(getConfigurationString(String.format(CONFIGURATION_KEY_REPOSITORY_DRIVER,
                                                                repository.getName())));

      repository.setPath(getConfigurationString(String.format(CONFIGURATION_KEY_REPOSITORY_ROOT_FOLDER_PATH,
                                                              repository.getName())));

      repositories.put(repository.getName(), repository);
    }

    final Map<String, SourceConfig> sources = new HashMap<String, SourceConfig>();
    for (final Entry<String, Boolean> sourceEntry : sourceNames.entrySet())
    {
      final SourceConfig source = new SourceConfig();
      source.setName(sourceEntry.getKey());
      source.setAvailable(sourceEntry.getValue());
      source.setRepository(repositories.get(sourceRepositoryMapping.get(source.getName())));

      final String watcherEnabled = getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_ENABLED,
                                                                         source.getName()));
      if (watcherEnabled != null && Boolean.parseBoolean(watcherEnabled))
      {
        final WatcherConfig watcher = new WatcherConfig();
        watcher.setDefinitionsIdentifier(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_TARGET_DEFINITIONS,
                                                                              source.getName())));
        watcher.setDefinitionsVersion(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_TARGET_DEFINITIONS_VERSION,
                                                                           source.getName())));
        watcher.setCollaborationIdentifier(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_TARGET_COLLABORATION,
                                                                                source.getName())));
        watcher.setProcessIdentifier(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_TARGET_PROCESS,
                                                                          source.getName())));
        watcher.setSignalIdentifier(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_TARGET_SIGNAL,
                                                                        source.getName())));
        watcher.setDataEntry(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_TARGET_DATA_ENTRY,
                                                                  source.getName())));
        watcher.setDataType(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_TARGET_DATATYPE,
                                                                 source.getName())));
        watcher.setRecursive(Boolean.parseBoolean(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_RECURSIVE,
                                                                                       source.getName()))));
        watcher.setPickingDirectories(Boolean.parseBoolean(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_PICK_DIRECTORIES,
                                                                                                source.getName()))));
        watcher.setPickingFiles(Boolean.parseBoolean(getConfigurationString(String.format(CONFIGURATION_KEY_WATCHER_PICK_FILES,
                                                                                          source.getName()),
                                                                            "true")));
        source.setWatcher(watcher);
      }
      sources.put(source.getName(), source);
    }

    return sources.values();
  }

  public boolean isFullyAvailable(final Collection<SourceConfig> sources)
  {
    for (final SourceConfig source : sources)
    {
       if (!source.isAvailable())
       {
         return false;
       }
    }
    return true;
  }

  public List<SourceConfig> validateConfiguration(final Collection<SourceConfig> sources)
  {
    final List<SourceConfig> validSourceConfigs = new ArrayList<SourceConfig>();
    for (final SourceConfig source : sources)
    {
      if (source.getWatcher() == null)
      {
        // do not try to validate sources without watcher
        continue;
      }
      if (!source.isAvailable())
      {
        _logger.warning("ECI-SOURCE [" + source.getName() + "]: source ignored because not available");
        continue;
      }
      if (source.getRepository() == null)
      {
        _logger.warning("ECI-SOURCE [" + source.getName() + "]: no repository bound or found");
        continue;
      }
      if (!source.getRepository().isAvailable())
      {
        _logger.warning("ECI-SOURCE [" + source.getName() + "]: source ignored because repository is not available");
        continue;
      }
      if (!SUPPORTED_DRIVERS.contains(source.getRepository().getDriver()))
      {
        _logger.warning("ECI-SOURCE [" + source.getName() + "]: driver [" + source.getRepository().getDriver() + "] is not supported by FileWatcher module");
        continue;
      }
      if (source.getWatcher().getDefinitionsIdentifier() == null)
      {
        _logger.error("ECI-SOURCE [" + source.getName() + "]: a configuration key [ " + 
                      String.format(CONFIGURATION_KEY_WATCHER_TARGET_DEFINITIONS, source.getName()) + 
                      "] is required");
        continue;
      }
      if (source.getWatcher().getProcessIdentifier() == null && source.getWatcher().getSignalIdentifier() == null)
      {
        _logger.error("ECI-SOURCE [" + source.getName() + "]: at least one of configuration keys [ " + 
                      String.format(CONFIGURATION_KEY_WATCHER_TARGET_PROCESS, source.getName()) + 
                      "] and [" + 
                      String.format(CONFIGURATION_KEY_WATCHER_TARGET_SIGNAL, source.getName()) +
                      "] must be present");
        continue;
      }
      if (source.getWatcher().getProcessIdentifier() != null && source.getWatcher().getSignalIdentifier() != null)
      {
        _logger.error("ECI-SOURCE [" + source.getName() + "]: both configuration keys [ " + 
                      String.format(CONFIGURATION_KEY_WATCHER_TARGET_PROCESS, source.getName()) + 
                      "] and [" + 
                      String.format(CONFIGURATION_KEY_WATCHER_TARGET_SIGNAL, source.getName()) +
                      "] cannot be present at the same time");
        continue;
      }
      if (source.getWatcher().getSignalIdentifier() != null && source.getWatcher().getCollaborationIdentifier() != null)
      {
        _logger.error("ECI-SOURCE [" + source.getName() + "]: no collaboration specification is permitted when in signal-triggering mode");
        continue;
      }
      if (source.getWatcher().getSignalIdentifier() != null && source.getWatcher().getDataEntry() != null)
      {
        _logger.error("ECI-SOURCE [" + source.getName() + "]: no data-entry specification is permitted when in signal-triggering mode");
        continue;
      }
      if (source.getWatcher().getDataType() == null)
      {
        _logger.warning("ECI-SOURCE [" + source.getName() + "]: data-type MUST be specified. Supported values are " + Arrays.asList(DataType.values()));
        continue;
      }
      try
      {
        DataType.valueOf(source.getWatcher().getDataType());
      }
      catch(final IllegalArgumentException e)
      {
        _logger.error("ECI-SOURCE [" + source.getName() + "]: data-type [" + source.getWatcher().getDataType() + "] is not supported. "
          + "Supported values are " + Arrays.asList(DataType.values()));
        continue;
      }
      if (source.getWatcher().isPickingDirectories() && source.getWatcher().isRecursive())
      {
        _logger.error("ECI-SOURCE [" + source.getName() + "]: options 'recursive' and 'pickDirectories' are mutually exclusive");
        continue;
      }
      validSourceConfigs.add(source);
    }
    return validSourceConfigs;
  }

  /**
   * Build a Map of &lt;Source, Repository&gt; based on a map of &lt;Repository, Set&lt;Source&gt;&gt;
   * 
   * @param mapping map of &lt;Repository, Set&lt;Source&gt;&gt;
   * @return Map of &lt;Source, Repository&gt;
   */
  private Map<String, String> reverseMapping(final Map<String, Set<String>> mapping)
  {
    final Map<String, String> reverseMapping = new HashMap<String, String>();
    for (final Map.Entry<String, Set<String>> entry : mapping.entrySet())
    {
      final String repository = entry.getKey();
      for (final String source : entry.getValue())
      {
        reverseMapping.put(source, repository);
      }
    }
    return reverseMapping;
  }

  private String getConfigurationString(final String key) throws CheckedException
  {
    return getConfigurationString(key, null);
  }

  private String getConfigurationString(final String key, final String defaultValue) throws CheckedException
  {
    try
    {
      return ConfigurationHelper.getStringValue(_context.getParameters(),
                                                key);
    }
    catch (final ConfigurationKeyNotFoundException e)
    {
      return defaultValue;
    }
  }
}
