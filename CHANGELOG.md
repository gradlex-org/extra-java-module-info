# Extra Java Module Info Gradle Plugin - Changelog

## Version 1.4.1
* [Fixed] [#50](https://github.com/gradlex-org/extra-java-module-info/issues/50) Remove merged Jars from classpath even if they are (automatic) modules

## Version 1.4
* [New] Minimal Gradle version is now 6.8 for integration with recently added features like the Dependency Version Catalog
* [New] [#46](https://github.com/gradlex-org/extra-java-module-info/issues/46) - Validation coordinates and module names
* [New] [#41](https://github.com/gradlex-org/extra-java-module-info/issues/41) - Support version catalog accessors to express dependency coordinates (Thanks [Giuseppe Barbieri](https://github.com/elect86) for suggesting!)
* [New] [#30](https://github.com/gradlex-org/extra-java-module-info/issues/30) - Add 'opens(...)' to module DSL (Thanks [Wexalian](https://github.com/Wexalian) for suggesting!)
* [Fixed] [#47](https://github.com/gradlex-org/extra-java-module-info/issues/47) - requireAllDefinedDependencies() gives error when dependency only appears on runtime path (Thanks [Sola](https://github.com/unlimitedsola) for reporting!)
* [Fixed] [#45](https://github.com/gradlex-org/extra-java-module-info/issues/45) - Sub-folders in 'META-INF/services' are not ignored (Thanks [Jonas Beyer](https://github.com/j-beyer) for reporting!)
* [Fixed] [#44](https://github.com/gradlex-org/extra-java-module-info/issues/44) - Name resolution for jars with '-' character failing if Jars are taken from local .m2 repository (Thanks [Aidan Do](https://github.com/REslim30) for reporting!)

## Version 1.3
* [New] [#42](https://github.com/gradlex-org/extra-java-module-info/issues/42) - Added support for 'uses' directives (Thanks [Stefan Reek](https://github.com/StefanReek) for contributing!)

## Version 1.2
* [New] [#40](https://github.com/gradlex-org/extra-java-module-info/issues/40) - Add requireAllDefinedDependencies() functionality
* [New] [#38](https://github.com/gradlex-org/extra-java-module-info/issues/38) - Add exportAllPackages() functionality (Thanks [Hendrik Ebbers](https://github.com/hendrikebbers) for suggesting!)
* [New] [#37](https://github.com/gradlex-org/extra-java-module-info/issues/37) - Merge Jars - fully support merging Zip into Jar

## Version 1.1
* [Fixed] [#36](https://github.com/gradlex-org/extra-java-module-info/issues/36) - mergeJar can lead to unnecessary build failures (Thanks [nieqian1230](https://github.com/nieqian1230) for reporting!)

## Version 1.0
* Moved project to [GradleX](https://gradlex.org) - new plugin ID: `org.gradlex.extra-java-module-info`

## Version 0.15
* [New] [#34](https://github.com/gradlex-org/extra-java-module-info/issues/34) - Merge Jars - merge service provider files

## Version 0.14
* [Fixed] [#33](https://github.com/gradlex-org/extra-java-module-info/issues/33) - Map 'Jar File Path' to 'group:name' correctly for Jars cached in local .m2 repository (Thanks [Leon Linhart](https://github.com/TheMrMilchmann) for reporting!)

## Version 0.13
* [New] [#32](https://github.com/gradlex-org/extra-java-module-info/issues/32) - Add license information to POM (Thanks [Edward McKnight](https://github.com/EM-Creations) for reporting!)

## Version 0.12
* [New] [#31](https://github.com/gradlex-org/extra-java-module-info/issues/31) - Address Jars by 'group:name' coordinates (instead of file name with version)
* [New] [#1](https://github.com/gradlex-org/extra-java-module-info/issues/1) - Merging several legacy Jars into one Module Jar

## Version 0.11
* [Fixed] [#27](https://github.com/gradlex-org/extra-java-module-info/issues/27) - Avoid 'invalid entry compressed size' on Java < 16 (Thanks [@carlosame](https://github.com/carlosame) for reporting and [Ihor Herasymenko](https://github.com/iherasymenko) 
   for fixing!)

## Version 0.10
* [Fixed] [#23](https://github.com/gradlex-org/extra-java-module-info/issues/23) - Ignore `MANIFEST.MF` files not correctly positioned in Jar (Thanks [Michael Spahn](https://github.com/michael-spahn) reporting!)

## Version 0.9
* [Fixed] [#17](https://github.com/gradlex-org/extra-java-module-info/issues/17) - Exclude signatures from signed Jars (Thanks [Philipp Schneider](https://github.com/p-schneider) for fixing!)
* [New] [#16](https://github.com/gradlex-org/extra-java-module-info/issues/16) - Prevent duplicates in ModuleInfo DSL (Thanks [Ihor Herasymenko](https://github.com/iherasymenko) for contributing!)

## Version 0.8
* [Fixed] [#15](https://github.com/gradlex-org/extra-java-module-info/issues/15) - Error when importing certain multi-project builds in the IDE (Thanks [Michael Spahn](https://github.com/michael-spahn) reporting!)

## Version 0.7
* [New] [#14](https://github.com/gradlex-org/extra-java-module-info/issues/14) - DSL method for omitting unwanted service provider (Thanks [Ihor Herasymenko](https://github.com/iherasymenko) for contributing!)

## Version 0.6
* [New] [#11](https://github.com/gradlex-org/extra-java-module-info/issues/11) - Transform results are cached (Thanks [Carsten Otto](https://github.com/C-Otto) reporting!)

## Version 0.5
* [New] [#9](https://github.com/gradlex-org/extra-java-module-info/issues/9) - Automatically add descriptors to 'module-info.class' (Thanks [Ihor Herasymenko](https://github.com/iherasymenko) for contributing!)
* [New] [#9](https://github.com/gradlex-org/extra-java-module-info/issues/9) - Support 'requires static' in DSL (Thanks [Ihor Herasymenko](https://github.com/iherasymenko) for contributing!)

## Version 0.4
* [Fixed] Issue with 'failOnMissingModuleInfo.set(false)' option

## Version 0.3
* [New] [#3](https://github.com/gradlex-org/extra-java-module-info/issues/3) - Add 'failOnMissingModuleInfo.set(false)' option

## Version 0.2
* [Fixed] [#2](https://github.com/gradlex-org/extra-java-module-info/issues/2) - Handle Jars without manifest (Thanks [Ihor Herasymenko](https://github.com/iherasymenko) for fixing!)

## Version 0.1
* [New] Initial release following discussions in https://github.com/gradle/gradle/issues/12630

