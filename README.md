ArchiveFileSCM
===============
ArchiveFilesSCM plugin for Jenkins checkouts archive files and extracts to Jenkins job workspace

Plugin

- checkouts archive file only when last modified date(last-modified header returned when connecting to a URL) changes from last checkout date

- supports pooling using the same above logic
 
- supports extraction of zip,tar,gz,jar,war,ear files

- detects type of archive file based on file name (i.e URL must end with zip,tar,tar.gz,jar,war,ear)

- supports basic authentication

- supports connection through proxy

- supports running on slave

- supports http:// and file:// protocols e.g - URL can be
                                             
* * http://www.apache.org/dyn/closer.cgi/maven/binaries/apache-maven-3.0.4-bin.tar.gz

* * file:///C:/Arjun/felix.jar (On Windows)

* * file:///home/arjun/felix.jar (On Unix/Linux)

Note: If the type is unknown the plugin will simply copy the file to workspace