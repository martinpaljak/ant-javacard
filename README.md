# Building JavaCard applet CAP files with Ant

**Easy to use [Ant](https://ant.apache.org/) task** for building JavaCard CAP files in a declarative way.

[![Build status](https://travis-ci.org/martinpaljak/ant-javacard.svg?branch=master)](https://travis-ci.org/martinpaljak/ant-javacard) &nbsp; [![Coverity](https://scan.coverity.com/projects/8418/badge.svg)](https://scan.coverity.com/projects/martinpaljak-ant-javacard) &nbsp; [![Latest release](https://img.shields.io/github/release/martinpaljak/ant-javacard.svg)](https://github.com/martinpaljak/ant-javacard/releases/latest) &nbsp; [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.martinpaljak/ant-javacard/badge.svg)](https://mvnrepository.com/artifact/com.github.martinpaljak/ant-javacard) &nbsp; [![MIT licensed](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/martinpaljak/ant-javacard/blob/master/LICENSE)

## Features
 * **[Do What I Mean](http://en.wikipedia.org/wiki/DWIM)**. You will [love it](#happy-users)!
 * **No dependencies**, no extra or unrelated downloads. Just **a 14KB jar**.
 * Supports **all available JavaCard SDK versions**: 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4 and 3.0.5
   * Get one from [oracle.com](http://www.oracle.com/technetwork/java/embedded/javacard/downloads/javacard-sdk-2043229.html) or use the [handy Github repository](https://github.com/martinpaljak/oracle_javacard_sdks)
 * **Works on all platforms** with Java 1.8: Windows, OSX, Linux.
 * Almost **everything integrates** or works with Ant.
 * Can be easily integrated into **continuous integration** workflows.
 * Generates CAP files from **sources** or **pre-compiled** class files.
 * Import *external libraries*: natural use of `.exp` files and `.jar` libraries.
 * **No restrictions** on project folder layout.
 * Loading JavaCard applets is equally pleasing with **[GlobalPlatformPro](https://github.com/martinpaljak/GlobalPlatformPro)**

## Download & Use
 * Download [`ant-javacard.jar`](https://github.com/martinpaljak/ant-javacard/releases/download/18.01.17/ant-javacard.jar) (be sure to get the [latest version](https://github.com/martinpaljak/ant-javacard/releases/latest))
   * The **only** supported Java version for all SDK targets is 1.8!
 * Or use the download task:
```xml
<get src="https://github.com/martinpaljak/ant-javacard/releases/download/18.01.17/ant-javacard.jar" dest="." skipexisting="true"/>
```
 * Then add the following to your `build.xml` file:
```xml
<taskdef name="javacard" classname="pro.javacard.ant.JavaCard" classpath="ant-javacard.jar"/>
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
      [cap] INFO: using JavaCard v2.2.2 SDK in ../jc222_kit
      [cap] Setting package name to testapplets
      [cap] Building CAP with 1 applet from package testapplets
      [cap] testapplets.Empty 0102030405060708
  [compile] Compiling 1 source file to /var/folders/l7/h99c5w6j0y1b8_qbsth_9v4r0000gn/T/antjc4506897175807383834
      [cap] CAP saved to /Users/martin/projects/ant-javacard/Empty222.cap
```
## Syntax
Sample:

```xml
<javacard jckit="/path/to/jckit_dir1">
  <cap jckit="/path/to/jckit_dir2" aid="0102030405" package="package.name" version="0.1" output="MyApplet.cap" sources="src/myapplet" classes="path/to/classes" export="mylib">
    <applet class="myapplet.MyApplet" aid="0102030405060708"/>
    <import exps="path/to/exps" jar="/path/to/lib.jar"/>
  </cap>
</javacard>
```
Details:
 * `javacard` tag - generic task
   * `jckit` attribute - path to the JavaCard SDK that is used if individual `cap` does not specify one. Optional if `cap` defines one, required otherwise.
   * `javaversion` attribute - override the Java source and target version. Can be overridden for each `cap`. Optional.
 * `cap` tag - construct a CAP file
   * `jckit` attribute - path to the JavaCard SDK to be used for this CAP. Optional if `javacard` defines one, required otherwise. 
   * `sources` attribute - path to Java source code, to be compiled against the current JavaCard SDK. Either `sources` or `classes` is required.
   * `classes` attribute - path to pre-compiled class files to be assembled into a CAP file. If both `classes` and `sources` are specified, compiled class files will be put to `classes` folder, which is created if missing.
   * `package` attribute - name of the package of the CAP file. Optional - set to the parent package of the applet class if left unspecified.
   * `version` attribute - version of the package. Optional - defaults to 0.0 if left unspecified.
   * `aid` attribute - AID (hex) of the package. Recommended - or set to the 5 first bytes of the applet AID if left unspecified.
   * `output` attribute - path where to save the generated CAP file. Required.
   * `export` attribtue - path (folder) where to place the JAR and generated EXP file. Optional.
   * `jar` attribute - path where to save the generated archive JAR file. Optional.
   * `jca` attribute - path where to save the generated JavaCard Assembly (JCA) file. Optional.
   * `verify` attribute - if set to false, disables verification of the resulting CAP file with offcardeverifier. Optional.
   * `debug` attribute - if set to true, generates debug CAP components. Optional.
   * `ints` attribute - if set to true, enables support for 32 bit `int` type. Optional.
   * `javaversion` attribute - override the Java source and target version. Optional.
 * `applet` tag - for creating an applet inside the CAP
   * `class` attribute - class of the Applet where install() method is defined. Required.
   * `aid` attribute - AID (hex) of the applet. Recommended - or set to package `aid`+`i` where `i` is index of the applet definition in the build.xml instruction
 * `import` tag - for linking against external components/libraries, like `GPSystem` or `OPSystem`
   * `exps` attribute - path to the folder keeping `.exp` files. Required
   * `jar` attribute - path to the JAR file for compilation. Optional - only required if using `sources` mode and not necessary with `classes` mode if java code is already compiled

Notes:
 * `jc.home` property has the highest precedence, followed by `jckit` path of `cap`, followed by path in `javacard`, followed by `JC_HOME` environment variable. SDK must be valid to be considered for use.

## License
 * [MIT](./LICENSE)

## Happy users
A random list of users, with a public link:
* Applets:
  * [IsoApplet](https://github.com/philipWendland/IsoApplet) by [@philipWendland](https://github.com/philipWendland)
  * [NdefApplet](https://github.com/promovicz/javacard-ndef) by [@promovicz](https://github.com/promovicz)
  * [GidsApplet](https://github.com/vletoux/GidsApplet) by [@vletoux](https://github.com/vletoux)
  * [LedgerWalletApplet](https://github.com/LedgerHQ/ledger-javacard) by [@LedgerHQ](https://github.com/LedgerHQ)
  * [KeePassNFC](https://github.com/nfd/smartcard_crypto_applet) by [@nfd](https://github.com/nfd)
  * [PivApplet](https://github.com/arekinath/PivApplet) (PIV) by [@arekinath](https://github.com/arekinath)
  * [OpenFIP201](https://github.com/makinako/OpenFIPS201) (PIV) by [@makinako](https://github.com/makinako)
  * [Cryptonit](https://github.com/mbrossard/cryptonit-applet) (PIV) by [@mbrossard](https://github.com/mbrossard)
  * [HTOP NDEF](https://github.com/petrs/hotp_via_ndef) by [@petrs](https://github.com/petrs)
  * [Yubikey OTP](https://github.com/arekinath/YkOtpApplet) by [@arekinath](https://github.com/arekinath)
  * Plus loads of academic projects, classes and papers.
* Integration projects:
  * [JavaCard Gradle plugin](https://github.com/bertrandmartel/javacard-gradle-plugin) by [@bertrandmartel](https://github.com/bertrandmartel)
  * [JavaCard Template project with Gradle](https://github.com/ph4r05/javacard-gradle-template) by [@ph4r05](https://github.com/ph4r05)
* Other:
  * **You!** Don't torture yourself with complexity, **KISS!**

## Contact
 * See [javacard.pro](https://javacard.pro)

## Similar projects
 * standard JavaCard SDK Ant tasks
   * :( as cumbersome to use as the command line utilities
   * :( not declarative/DWIM enough
   * :) very explicit interface with all details exposed
 * JavaCard Gradle plugin (MIT) - https://github.com/bertrandmartel/javacard-gradle-plugin
   * :) Wraps ant-javacard for use with Gradle
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
