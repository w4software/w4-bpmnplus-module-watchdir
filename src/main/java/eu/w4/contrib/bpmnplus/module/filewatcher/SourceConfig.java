package eu.w4.contrib.bpmnplus.module.filewatcher;

public class SourceConfig
{

  private String _name;

  private boolean _available;

  private RepositoryConfig _repository;

  private WatcherConfig _watcher;

  public String getName()
  {
    return _name;
  }

  public void setName(String name)
  {
    _name = name;
  }

  public boolean isAvailable()
  {
    return _available;
  }

  public void setAvailable(boolean available)
  {
    _available = available;
  }

  public RepositoryConfig getRepository()
  {
    return _repository;
  }

  public void setRepository(RepositoryConfig repository)
  {
    _repository = repository;
  }

  public WatcherConfig getWatcher()
  {
    return _watcher;
  }

  public void setWatcher(WatcherConfig watcher)
  {
    _watcher = watcher;
  }

  @Override
  public String toString()
  {
    return _name + "(repository=" + getRepository() + ", watcher=" + getWatcher() + ")";
  }
}
