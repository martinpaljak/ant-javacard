# Building JavaCard applets with Apache Ant
 * Easy to us Ant task for building JavaCard CAP files in a declarative way
 * Minimal dependencies, no extra downloads
 * Can be easily be integrated into continuous integration workflow

# Download
 * Head to [release area](https://github.com/martinpaljak/ant-jcpro/releases/tag/v0.1)

# Use
 * Download ```ant-jcpro.jar``` file and put it into the library folder of your project.
 * Then add the following to you ```build.xml``` file:
```xml
<taskdef name="jcpro" classname="pro.javacard.ant.JCPro" classpath="lib/ant-jcpro.jar"/>
```
 * Now you can create applets within your Ant targets like this:
```xml
<jcpro>
  <cap jckit="/path/to/jckit_dir" cap="0102030405" output="MyApplet.cap" sources="src/myapplet">
    <applet class="myapplet.MyApplet" aid="0102030405060708"/>
  </cap>
</jcpro>
```

* Supports all recent JavaCard SDK versions:
 * 2.2.1
 * 2.2.2
 * 3.0.3
 * 3.0.4

# Similar projects
 * gradle-javacard (Apache 2.0) - https://github.com/fidesmo/gradle-javacard
  * :) nice declarative interface
  * :( requires gradle (40M download) 
  * :( only supports JC2.2.2
 * standard JavaCard SDK Ant tasks
  * :( as cumbersome to use as the command line utilities
  * :( not declarative enough

# License
 * MIT

# Contact
 * martin@martinpaljak.net
