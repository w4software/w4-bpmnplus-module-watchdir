FileWatcher module for W4 BPMN+
===============================

Summary
-------

This is an addon module for W4 BPMN+ (v9.2+) that allows to act as a file/folder pick-up so as to instantiate a process or to trigger a signal when
a new item is dropped into a directory.


Download
--------

The package can be downloaded from [release page](https://github.com/w4software/w4-bpmnplus-module-watchdir/releases)


Installation
------------

Extract the package, either zip or tar.gz, at the root of a W4 BPMN+ Engine installation. It will create the necessary entries into `modules` subdirectory of W4 BPMN+ Engine.


Configuration
-------------

Configuration of this module is done in the core configuration file of W4 BPMN+ (W4BPMNPLUS_HOME/conf/w4.properties) although it may also
be stored in its own configuration file(s) using one of `@include` or `@includedir` directives.

### Create/use an ECI source

This watcher module relies on a filesystem document source so the first thing to do is to configure a new one or to identify an existing
one in your actual configuration.

The configuration template for one is as follows: you need to configure a source and bind to to a repository which is using the `FileSystemDriver`.

    core.eci.sources+=<source_name>
    core.eci.source.<source_name>.repository=<repository_name>
    
    core.eci.repositories+=<repository_name>
    core.eci.<repository_name>.driverClassName=eu.w4.engine.core.eci.filesystem.FileSystemDriver
    core.eci.<repository_name>.properties+=rootFolderPath
    core.eci.<repository_name>.property.rootFolderPath=<path>


A real life example based on the template above could be the following one

    core.eci.sources+=document-source
    core.eci.source.document-source.repository=file-storage1
    
    core.eci.repositories+=file-storage1
    core.eci.file-storage1.driverClassName=eu.w4.engine.core.eci.filesystem.FileSystemDriver
    core.eci.file-storage1.properties+=rootFolderPath
    core.eci.file-storage1.property.rootFolderPath=$softwareHome/storage/1


### Configure the watcher module

At global level, the watcher module needs to be configured with a login/password that will be used to authenticate against W4 BPMN+ Engine
to do all operations (access to configuration, access to ECI, instanciate process, trigger signal). Accordingly, the configured account 
should have the correct privileges set.

    module.filewatcher.login=<login>
    module.filewatcher.password=<password>


As this module may be faster to load than the underlying ECI sources, an attempt is made to wait for all sources to be available before actually
starting. This behavior is customizable through two following optional keys

    module.filewatcher.config.retryCount=<number>             # default to 3
    module.filewatcher.config.retryInterval=<milliseconds>    # default to 500 (ms)
    module.filewatcher.config.initialInterval=<milliseconds>  # default to <retryInterval>


### Configure the watched sources

The watcher module configures itself on top of the ECI source and is enabled using the `watch` sub-property. 

    core.eci.source.<source_name>.watch=true


The configuration can then be specified using following sub-properties:

- watch.definitionsIdentifier: identifier of the definitions in which the process or signal is located
- watch.definitionsVersion: version of the definitions in which the process or signal is located
- watch.signalIdentifier: identifier of the signal to trigger each time a new file is picked-up
- watch.processIdentifier: identifier of the process to instanciate each time a new file is picked-up
- watch.dataEntry: name of the data-entry in the process that will store the detected file
- watch.dataType: select the type of data that is filled in the data-entry of the process or in the payload of the signal instance. Possible values are: 
    * PATH: underlying data must be String and it will be filled with the path of the detected file
    * ECI: underlying data must be a filesystem-driver ECI document (or folder) and it will be set to the detected file/folder
- watch.recursive: pick files in all subdirectories (default: false)
- watch.pickDirectories: process detected directories (default: false)
- watch.pickFiles: process detected files (default: true)

Two scenario can be designed, either process instanciation

    core.eci.sources+=<source_name>
    core.eci.source.<source_name>.repository=storage1
    core.eci.source.<source_name>.watch=true
    core.eci.source.<source_name>.watch.definitionsIdentifier=myDefinition
    core.eci.source.<source_name>.watch.definitionsVersion=1.0
    core.eci.source.<source_name>.watch.processIdentifier=myProcess
    core.eci.source.<source_name>.watch.dataEntry=myDocument
    core.eci.source.<source_name>.watch.dataType=ECI

Or signal triggering

    core.eci.sources+=<source_name>
    core.eci.source.<source_name>.repository=storage1
    core.eci.source.<source_name>.watch=true
    core.eci.source.<source_name>.watch.definitionsIdentifier=myDefinition
    core.eci.source.<source_name>.watch.definitionsVersion=1.0
    core.eci.source.<source_name>.watch.signalIdentifier=mySignalWithStringPayload
    core.eci.source.<source_name>.watch.dataType=PATH


Usage
-----

When deployed, the module is started automatically by W4 BPMN+ Engine during its own start cycle.

To ensure it is correctly deployed and started, you should see the following line in the logs (W4BPMNPLUS_HOME/logs/w4.log)

    [INFO]    eu.w4.contrib.bpmnplus.module.filewatcher.FileWatcherModule startup FileWatcher module successfully started


License
-------

Copyright (c) 2015, W4 Software

This project is licensed under the terms of the MIT License (see LICENSE file)

Ce projet est licencié sous les termes de la licence MIT (voir le fichier LICENSE)
