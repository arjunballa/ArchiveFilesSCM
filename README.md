ArchiveFileSCM
===============
Jenkins plugin to download archive files as extract to job workspace.

ArchiveFilesSCM plugin for Jenkins supports checkout, extraction and also pooling of archives files.

The current version supports extraction of zip,tar,gz,jar,war,ear files.

The type detection of archive file is based on file name (i.e URL must end with zip,tar,tar.gz,jar,war,ear).

Polling is done by checking the last-modified header returned when connecting to a URL.

Note: If the type is unknown the plugin will simply copy the file to workspace.