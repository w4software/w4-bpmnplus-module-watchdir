package eu.w4.contrib.bpmnplus.module.filewatcher;

public class RepositoryConfig
{

  private String _name;

  private String _driver;

  private String _path;

  private boolean _available;

  public String getName()
  {
    return _name;
  }

  public void setName(String name)
  {
    _name = name;
  }

  public String getDriver()
  {
    return _driver;
  }

  public void setDriver(String driver)
  {
    _driver = driver;
  }

  public String getPath()
  {
    return _path;
  }

  public void setPath(String path)
  {
    _path = path;
  }

  public boolean isAvailable()
  {
    return _available;
  }

  public void setAvailable(boolean available)
  {
    _available = available;
  }

  @Override
  public String toString()
  {
    return _name + "<" + _driver + ">";
  }
}
