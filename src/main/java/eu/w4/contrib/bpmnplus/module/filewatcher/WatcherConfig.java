package eu.w4.contrib.bpmnplus.module.filewatcher;

public class WatcherConfig
{

  private String _definitionsIdentifier;

  private String _definitionsVersion;

  private String _collaborationIdentifier;

  private String _processIdentifier;

  private String _signalIdentifier;

  private String _dataEntry;

  private String _dataType;

  private boolean _recursive;

  private boolean _pickingDirectories;

  private boolean _pickingFiles;

  public String getDefinitionsIdentifier()
  {
    return _definitionsIdentifier;
  }

  public void setDefinitionsIdentifier(String definitionsIdentifier)
  {
    _definitionsIdentifier = definitionsIdentifier;
  }

  public String getDefinitionsVersion()
  {
    return _definitionsVersion;
  }

  public void setDefinitionsVersion(String definitionsVersion)
  {
    _definitionsVersion = definitionsVersion;
  }

  public String getCollaborationIdentifier()
  {
    return _collaborationIdentifier;
  }

  public void setCollaborationIdentifier(String collaborationIdentifier)
  {
    _collaborationIdentifier = collaborationIdentifier;
  }

  public String getProcessIdentifier()
  {
    return _processIdentifier;
  }

  public void setProcessIdentifier(String processIdentifier)
  {
    _processIdentifier = processIdentifier;
  }

  public String getSignalIdentifier()
  {
    return _signalIdentifier;
  }

  public void setSignalIdentifier(String signalIdentifier)
  {
    _signalIdentifier = signalIdentifier;
  }

  public String getDataEntry()
  {
    return _dataEntry;
  }

  public void setDataEntry(String dataEntry)
  {
    _dataEntry = dataEntry;
  }

  public String getDataType()
  {
    return _dataType;
  }

  public void setDataType(String dataType)
  {
    _dataType = dataType;
  }

  public boolean isRecursive()
  {
    return _recursive;
  }

  public void setRecursive(boolean recursive)
  {
    _recursive = recursive;
  }

  public boolean isPickingDirectories()
  {
    return _pickingDirectories;
  }

  public void setPickingDirectories(boolean pickingDirectories)
  {
    _pickingDirectories = pickingDirectories;
  }

  public boolean isPickingFiles()
  {
    return _pickingFiles;
  }

  public void setPickingFiles(boolean pickingFiles)
  {
    _pickingFiles = pickingFiles;
  }
}
