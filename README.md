# UrPal
Applying sanity checks to find commonly made errors in Uppaal models
## Setup
### Repository initialization
Clone the repository with the ```--recurse-submodules``` argument in order to automatically initialize and update each submodule in the repository (recommended).
Or execute ```git submodule update --init --recursive``` in the repository after cloning normally to achieve the same.
### Environment variables
Set an environment variable ```UPPAAL_ROOT``` to the root folder of the UPPAAL distribution (i.e. ```$UPPAAL_ROOT/uppaal.jar``` should point to the main jar file). Make sure you use Uppaal version 4.1.22 or higher.
### Build
Run ```./gradlew build``` to build the plugin. It's that easy!
### IDE setup
The project is set-up using Gradle, meaning that any Java IDE suffices (we're not forced to use Eclipse YEEHAH).
