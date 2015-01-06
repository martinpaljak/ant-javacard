# Building JavaCard applets with Apache Ant
 * Easy to us Ant task for building JavaCard CAP files in a declarative way
 * Do What I Mean - omitted parameters are figured out
 * Minimal dependencies, no extra downloads
 * Can be easily be integrated into continuous integration workflow

## Download
 * Head to [release area](https://github.com/martinpaljak/ant-jcpro/releases)

## Use
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
(which results in output similar to this)
```
target:
    [jcpro] JavaCard 2.x SDK detected in ../jc221_kit
      [cap] Setting package name to testapplets
      [cap] Building CAP with 1 applet(s) from package testapplets
      [cap] testapplets.Empty 0102030405060708
  [compile] Compiling 1 source file to /var/folders/l7/h99c5w6j0y1b8_qbsth_9v4r0000gn/T/jcpro1449623494114549040104042558432715
      [cap] CAP saved to /Users/martin/projects/ant-jcpro/Empty221.cap
```
## Features
 * Supports all recent JavaCard SDK versions: 2.2.1, 2.2.2, 3.0.3 and 3.0.4
 * Automagically adjusts to used SDK version
 * Generate CAP files from sources or pre-compiled class files
 * "import" external libraries (```.exp``` files and ```.jar``` libraries)
 * Use different JavaCard SDK-s for different CAP files within the same target

## Similar projects
 * gradle-javacard (Apache 2.0) - https://github.com/fidesmo/gradle-javacard
  * :) nice declarative interface
  * :( requires gradle (40M download) 
  * :( only supports JC2.2.2
 * standard JavaCard SDK Ant tasks
  * :( as cumbersome to use as the command line utilities
  * :( not declarative enough

## License
 * MIT

## Contact
 * martin@martinpaljak.net
