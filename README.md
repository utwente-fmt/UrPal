# UrPal
Applying sanity checks to find commonly made errors in Uppaal models
## Setup
### Repository initialization
Clone the repository with the ```--recurse-submodules``` argument in order to automatically initialize and update each submodule in the repository (recommended).
Or execute ```git submodule update --init --recursive``` in the repository after cloning normally to achieve the same.
### Environment variables
Set an environment variable ```UPPAAL_ROOT``` to the root folder of the UPPAAL distribution (i.e. ```$UPPAAL_ROOT/uppaal.jar``` should point to the main jar file). Currently, only (4.1.20-beta14)[http://people.cs.aau.dk/~marius/beta/] works, due to it being to only version supporting plugins.
### Build
Run ```./gradlew build``` to build the plugin. It's that easy!
### IDE setup
The project is set-up using Gradle, meaning that any Java IDE suffices (we're not forced to use Eclipse YEEHAH).
