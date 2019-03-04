## 2019-03-04  Martin Paljak  <martin@martinpaljak.net>

 - add %g (GlobalPlatform version) to name template
 - fix %n name tamplate to only use applet name
   if a single applet is in the package (and there
   are no exports)
 - update underlying capfile library to 19.03.04
 - fix library export jar generation. Now it is done even
   if package contains (shareable) applets

## 2018-10-15  Martin Paljak  <martin@martinpaljak.net>

 - fix CAP file name generation to always include applet name
 - start to use a changelog.
