# Building JavaCard applet CAP files with Ant
 * **Easy to use** Ant task for building JavaCard CAP files in a declarative way
 * **[Do What I Mean**](http://en.wikipedia.org/wiki/DWIM)** 
 * **No dependencies**, no extra or unrelated downloads
 * Almost **everything integrates** or works with Ant
  * Can be easily integrated into continuous integration workflows
 * **Works on all platforms**: Windows, OSX, Linux
 
## Download
 * Head to [release area](https://github.com/martinpaljak/ant-javacard/releases)

## Use
 * Download ```ant-jcpro.jar``` file and put it into the library folder of your project.
 * Then add the following to you ```build.xml``` file:
```xml
<taskdef name="jcpro" classname="pro.javacard.ant.JCPro" classpath="lib/ant-jcpro.jar"/>
```
 * Now you can create applets within your Ant targets like this:
```xml
<jcpro>
  <cap jckit="/path/to/jckit_dir" aid="0102030405" output="MyApplet.cap" sources="src/myapplet">
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
## Syntax
Sample:

```xml
<jcpro jckit="/path/to/jckit_dir1">
  <cap jckit="/path/to/jckit_dir2" aid="0102030405" package="package.name" version="0.1" output="MyApplet.cap" sources="src/myapplet" classes="path/to/classes">
    <applet class="myapplet.MyApplet" aid="0102030405060708"/>
    <import exps="path/to/exps" jar="/path/to/lib.jar"/>
  </cap>
</jcpro>
```
Details:
 * ```jcpro``` tag - generic task
   * ```jckit``` attribute - path to the JavaCard SDK that is used if individual ```cap``` does not specify one. Optional if ```cap``` defines one, required otherwise.
 * ```cap``` tag - construct a CAP file
   * ```jckit``` attribute - path to the JavaCard SDK to be used for this CAP. Optional if ```jcpro``` defines one, required otherwise. 
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

## Features
 * Supports all recent JavaCard SDK versions: 2.2.1, 2.2.2, 3.0.3 and 3.0.4
 * Automagically adjusts to used SDK version
 * Generate CAP files from sources or pre-compiled class files
 * "import" external libraries (```.exp``` files and ```.jar``` libraries)
 * Use different JavaCard SDK-s for different CAP files within the same target

## Similar projects
 * standard JavaCard SDK Ant tasks
  * :( as cumbersome to use as the command line utilities
  * :( not declarative/DWIM enough
 * gradle-javacard (Apache 2.0) - https://github.com/fidesmo/gradle-javacard
  * :) nice declarative interface
  * :( requires gradle (40M download) 
  * :( only supports JC2.2.2
 * EclipseJCDE (Eclipse 1.0) - http://eclipse-jcde.sourceforge.net/
  * :( only supports JC2.2.2
  * :( not possible to integrate in CI - depends on eclipse
  * :( essentially an Eclipse GUI wrapper for JC SDK
 * JCOP Tools
  * :( not open source
 * NetBeans IDE JC support
  * :( not possible to integrate into CI
  * :( JavaCard 3.0 only
  * :( Netbeans, not cross platform
 * Ant script files and templates
  * :( XML is a *very* bad programming environment 

## License
 * MIT

## Contact
 * See [javacard.pro](http://javacard.pro)
