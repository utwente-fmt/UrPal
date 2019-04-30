# UrPal
Applying sanity checks to find commonly made errors in Uppaal models
## Setup
### Repository initialization
Clone the repository with the ```--recurse-submodules``` argument in order to automatically initialize and update each submodule in the repository (recommended).
Or execute ```git submodule update --init --recursive``` in the repository after cloning normally to achieve the same.
### Environment variables
Set an environment variable ```UPPAAL_ROOT``` to the root folder of the UPPAAL distribution (i.e. ```$UPPAAL_ROOT/uppaal.jar``` should point to the main jar file). Make sure you use Uppaal version 4.1.22 or higher.
### Ensure plugin folder exists
Plugins should be placed in the plugins directory inside Uppaal. Make shure that ```$UPPAAL_ROOT/plugins/``` exists, make it if it doesn't exist

## Available commands
> Windows should ```gradlew.bat``` instead of ```./gradlew```
### Build
Run ```./gradlew build``` to build the plugin. The plugin can be found ```build/libs/```
### Build and deploy local
Run ```./gradlew deployLocal``` to build and copy the plugin into the plugin directory of Uppaal.
### Build, deploy and run
To build the plugin, copy it to the Uppaal plugins directory, and run Uppaal afterwards, use ```./gradlew runUppaal```

### IDE setup
The project is set-up using Gradle, meaning that any Java IDE with a Gradle plugin should work.  
The base language is Java, however, support for Kotlin (the better Java) is present.
