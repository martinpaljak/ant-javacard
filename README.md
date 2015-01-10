# Building JavaCard applet CAP files with Ant

**Easy to use** Ant task for building JavaCard CAP files in a declarative way.

## Features
 * **[Do What I Mean](http://en.wikipedia.org/wiki/DWIM)**.
 * **No dependencies**, no extra or unrelated downloads. Just a 10KB jar.
 * Supports **all JavaCard SDK versions**: 2.2.1, 2.2.2, 3.0.3 and 3.0.4.
 * **Works on all platforms**: Windows, OSX, Linux.
 * Almost **everything integrates** or works with Ant.
 * Can be easily integrated into **continuous integration** workflows.
 * Generates CAP files from **sources** or **pre-compiled** class files.
 * "import" *external libraries*:```.exp``` files and ```.jar``` libraries.
 * **No restrictions** on project source folder layout.

## Download
 * Head to [release area](https://github.com/martinpaljak/ant-javacard/releases)

## Use
 * Download ```ant-javacard.jar``` file and put it into the library folder of your project.
 * Then add the following to you ```build.xml``` file:
```xml
<taskdef name="javacard" classname="pro.javacard.ant.JavaCard" classpath="lib/ant-javacard.jar"/>
```
 * Now you can create applets within your Ant targets like this:
```xml
<javacard>
  <cap jckit="/path/to/jckit_dir" aid="0102030405" output="MyApplet.cap" sources="src/myapplet">
    <applet class="myapplet.MyApplet" aid="0102030405060708"/>
  </cap>
</javacard>
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
## Syntax
Sample:

```xml
<javacard jckit="/path/to/jckit_dir1">
  <cap jckit="/path/to/jckit_dir2" aid="0102030405" package="package.name" version="0.1" output="MyApplet.cap" sources="src/myapplet" classes="path/to/classes">
    <applet class="myapplet.MyApplet" aid="0102030405060708"/>
    <import exps="path/to/exps" jar="/path/to/lib.jar"/>
  </cap>
</javacard>
```
Details:
 * ```javacard``` tag - generic task
   * ```jckit``` attribute - path to the JavaCard SDK that is used if individual ```cap``` does not specify one. Optional if ```cap``` defines one, required otherwise.
 * ```cap``` tag - construct a CAP file
   * ```jckit``` attribute - path to the JavaCard SDK to be used for this CAP. Optional if ```javacard``` defines one, required otherwise. 
   * ```sources``` attribute - path to Java source code, to be compiled against the current JavaCard SDK. Either ```sources``` or ```classes``` is required.
   * ```classes``` attribute - path to pre-compiled class files to be assembled into a CAP file.
   * ```package``` attribute - name of the package of the CAP file. Optional - set to the parent package of the applet class if left unspecified.
   * ```version``` attribute - version of the package. Optional - defaults to 0.0 if left unspecified.
   * ```aid``` attribute - AID of the package. Recommended - or set to the 5 first bytes of the applet AID if left unspecified.
   * ```output``` attribtue - path where to save the generated CAP file. Optional.
 * ```applet``` tag - for creating an applet inside the CAP
   * ```class``` attribute - class of the Applet where install() method is defined. Required.
   * ```aid``` attribute - AID of the applet. Recommended - or set to package ```aid```+```i``` where ```i``` is index of the applet definition in the build.xml instruction
 * ```import``` tag - for linking against external components/libraries, like ```GPSystem``` or ```OPSystem```
   * ```exps``` attribute - path to the folder keeping ```.exp``` files. Required
   * ```jar``` attribute - path to the JAR file for compilation. Optional - only required if using ```sources``` mode and not necessary with ```classes``` mode if java code is already compiled


## Similar projects
 * standard JavaCard SDK Ant tasks
  * :( as cumbersome to use as the command line utilities
  * :( not declarative/DWIM enough
  * :) very explicit interface with all details exposed
 * gradle-javacard (Apache 2.0) - https://github.com/fidesmo/gradle-javacard
  * :) nice declarative interface
  * :( requires gradle (40M download) 
  * :( JavaCard 2.2.2 only
 * EclipseJCDE (Eclipse 1.0) - http://eclipse-jcde.sourceforge.net/
  * :( JavaCard 2.2.2 only
  * :( not possible to integrate in CI - depends on eclipse
  * :( essentially an Eclipse GUI wrapper for JC SDK
 * JCOP Tools
  * :( not open source
 * NetBeans IDE JC support
  * :( not possible to integrate into CI
  * :( JavaCard 3.0 only
  * :( Netbeans, not cross platform
 * Maven2 task from FedICT (LGPL3) - https://code.google.com/p/eid-quick-key-toolset
  * :( Maven downloads half the internet before even simple tasks
  * :( JavaCard 2.2.2 only
 * Ant script files with templates
  * :( XML is a *very* bad and verbose programming environment

## License
 * MIT

## Contact
 * See [javacard.pro](http://javacard.pro)
