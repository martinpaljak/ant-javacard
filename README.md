# Building JavaCard applet CAP files with Ant

> Easy to use [Ant](https://ant.apache.org/) task for building JavaCard CAP files in a declarative way.

[![Build Status](https://github.com/martinpaljak/ant-javacard/workflows/Continuous%20Integration/badge.svg)](https://github.com/martinpaljak/ant-javacard/actions) [![Latest release](https://img.shields.io/github/release/martinpaljak/ant-javacard.svg)](https://github.com/martinpaljak/ant-javacard/releases/latest) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.martinpaljak/ant-javacard/badge.svg)](https://mvnrepository.com/artifact/com.github.martinpaljak/ant-javacard) [![Maven version](https://img.shields.io/maven-metadata/v?label=javacard.pro%20version&metadataUrl=https%3A%2F%2Fjavacard.pro%2Fmaven%2Fcom%2Fgithub%2Fmartinpaljak%2Fant-javacard%2Fmaven-metadata.xml)](https://gist.github.com/martinpaljak/c77d11d671260e24eef6c39123345cae) [![MIT licensed](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/martinpaljak/ant-javacard/blob/master/LICENSE)

## Features
 * **[Do What I Mean](http://en.wikipedia.org/wiki/DWIM)**. You will [love it](#happy-users)!
 * **No dependencies**, no extra or unrelated downloads. Just **a 46KB jar**.
 * Supports **all available JavaCard SDK versions**: 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5 and 3.1
   * Get one from [oracle.com](https://www.oracle.com/java/technologies/javacard-sdk-downloads.html) or use the [handy Github repository](https://github.com/martinpaljak/oracle_javacard_sdks)
 * **Works on all platforms** with Java 1.8+: Windows, OSX, Linux.
   * [Usable SDK-s depend on JDK version](https://github.com/martinpaljak/ant-javacard/wiki/Version-compatibility); 1.8 recommended!
 * Almost **everything integrates** or works with Ant.
   * Trigger it [from Maven](https://github.com/martinpaljak/ant-javacard/wiki/How-to-use-from-Maven) or via [Gradle wrapper](https://github.com/bertrandmartel/javacard-gradle-plugin)
 * Can be easily integrated into **continuous integration** workflows.
 * Generates CAP files from **sources** or **pre-compiled** class files.
 * Import **external libraries**: natural use of `.jar` libraries and/or `.exp` files.
 * **No restrictions** on project folder layout (but `src/main/javacard` works).
 * Loading JavaCard applets is equally pleasing with **[GlobalPlatformPro](https://github.com/martinpaljak/GlobalPlatformPro)**

## Download & Use
 * Download [`ant-javacard.jar`](https://github.com/martinpaljak/ant-javacard/releases/latest/download/ant-javacard.jar)
   * Java version usable with all SDK-s is 1.8! Use SDK 3.0.5u3 and `targetsdk` to compile with Java 10 for older versions.
 * Or use the download task:
```xml
<get src="https://github.com/martinpaljak/ant-javacard/releases/latest/download/ant-javacard.jar" dest="." skipexisting="true"/>
```
 * Then add the following to your `build.xml` file:
```xml
<taskdef name="javacard" classname="pro.javacard.ant.JavaCard" classpath="ant-javacard.jar"/>
```
 * Now you can create applets within your Ant targets like this:
```xml
<javacard>
  <cap jckit="/path/to/jckit_dir" aid="0102030405">
    <applet class="myapplet.MyApplet" aid="0102030405060708"/>
  </cap>
</javacard>
```
(which results in output similar to this)
```
target:
      [cap] INFO: using JavaCard 3.0.4 SDK in sdks/jc304_kit
      [cap] INFO: targeting JavaCard 2.2.2 SDK in sdks/jc222_kit
      [cap] Setting package name to testapplets.empty
      [cap] INFO: generated applet AID: A000000617008E5CDAAE01 for testapplets.empty.Empty
      [cap] Building CAP with 1 applet from package testapplets.empty (AID: A000000617008E5CDAAE)
      [cap] testapplets.empty.Empty A000000617008E5CDAAE01
  [compile] Compiling files from /Users/martin/projects/ant-javacard/src/testapplets/empty
  [compile] Compiling 1 source file to /var/folders/gf/_m9mq9td3lz32qv1hd4r12yw0000gn/T/jccpro841338375581620546
  [convert] [ INFO: ] Converter [v3.0.4]
  [convert] [ INFO: ]     Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
  [convert]
  [convert]
  [convert] [ INFO: ] conversion completed with 0 errors and 0 warnings.
 [javacard] NB! Please use JavaCard SDK 3.0.5u3 or later for verifying!
      [cap] CAP saved to /Users/martin/projects/ant-javacard/Empty_A000000617008E5CDAAE_50da91a4_2.2.2.cap
```
## Syntax
Sample:

```xml
<javacard jckit="/path/to/jckit_dir1">
  <cap targetsdk="/path/to/jckit_dir2" aid="0102030405" package="package.name" version="0.1" output="MyApplet.cap" sources="src/myapplet" classes="path/to/classes" export="mylib">
    <applet class="myapplet.MyApplet" aid="0102030405060708"/>
    <import exps="path/to/exps" jar="/path/to/lib.jar"/>
  </cap>
</javacard>
```
Details:
 * `javacard` tag - generic task
   * `jckit` attribute - path to the JavaCard SDK that is used if individual `cap` does not specify one. Optional if `cap` defines one, required otherwise.
 * `cap` tag - construct a CAP file
   * `jckit` attribute - path to the JavaCard SDK to be used. Optional if `javacard` defines one, required otherwise.
   * `targetsdk` attribute - path to the target JavaCard SDK to be used for this CAP. Optional, value of `jckit` used by default. Allows to use a more recent converter to target older JavaCard platforms.
   * `sources` attribute - path to Java source code, to be compiled against the JavaCard SDK. Either `sources` or `classes` is required, unless `src/main/javacard` exists.
   * `sources2` attribute - additional sources to build per-platform applets. Optional.
   * `classes` attribute - path to pre-compiled class files to be assembled into a CAP file. If both `classes` and `sources` are specified, compiled class files will be put to `classes` folder, which is created if missing.
   * `includes` attribute - comma or space separated list of patterns of files that must be included.
   * `excludes` attribute - comma or space separated list of patterns of files that must be excluded.
   * `package` attribute - name of the package of the CAP file. Optional for applets - set to the parent package of the applet class if left unspecified, required for libraries
   * `version` attribute - version of the package. Optional - defaults to 0.1 if left unspecified.
   * `fidesmoappid` attribute - [Fidesmo](https://developer.fidesmo.com) appId, to create the package AID and applet AID-s automatically. Optional.
   * `aid` attribute - AID (hex) of the package. Recommended - or set to the 5 first bytes of the applet AID if left unspecified.
   * `output` attribute - path where to save the generated CAP file. Optional, see below for variables.
   * `export` attribtue - path (folder) where to place the JAR and generated EXP file. Optional.
   * `jar` attribute - path where to save the generated archive JAR file. Optional.
   * `jca` attribute - path where to save the generated JavaCard Assembly (JCA) file. Optional.
   * `verify` attribute - if set to false, disables verification of the resulting CAP file with offcardeverifier. Optional.
   * `debug` attribute - if set to true, generates debug CAP components. Optional.
   * `strip` attribute - if set to true, removes class files from CAP. Optional.
   * `ints` attribute - if set to true, enables support for 32 bit `int` type. Optional.
 * `applet` tag - for creating an applet inside the CAP
   * `class` attribute - class of the Applet where install() method is defined. Required.
   * `aid` attribute - AID (hex) of the applet. Recommended - or set to package `aid`+`i` where `i` is index of the applet definition in the build.xml instruction
 * `import` tag - for linking against external components/libraries, like `GPSystem` or `OPSystem`
   * `exps` attribute - path to the folder keeping `.exp` files. Optional. Required if file in `jar` does not include .exp files.
   * `jar` attribute - path to the JAR file for compilation. Required if using `sources` mode and not necessary with `classes` mode if java code is already compiled

Notes:
 * `jc.home` property has the highest precedence, followed by `jckit` path of `cap`, followed by path in `javacard`, followed by `JC_HOME` environment variable. SDK must be valid to be considered for use.

### Output file name variables
 * `%h` - 8 character prefix of the SHA-256 Load File Data Block hash of the CAP file
 * `%H` - SHA-256 Load File Data Block hash of the CAP file
 * `%n` - _common name_ of the entity, either applet class or package
 * `%p` - package name
 * `%a` - package AID
 * `%j` - targeted JavaCard version

## Maven dependency
Releases are published to [`https://javacard.pro/maven/`](https://javacard.pro/maven/). To use it, add this to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>javacard-pro</id>
        <url>https://javacard.pro/maven/</url>
    </repository>
</repositories>
```

Pushes to Maven Central happen manually and only for selected final versions.

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
  * [SmartPGP](https://github.com/ANSSI-FR/SmartPGP) by [@ANSSI-FR](https://github.com/ANSSI-FR)
  * [SatochipApplet](https://github.com/Toporin/SatochipApplet) (Bitcoin Hardware Wallet) by [@Toporin](https://github.com/Toporin)
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
