# Cacophony

An experimental video sharing platform on top of IPFS.  One way to think of this is like a "decentralized YouTube."

## How to build

Clone the repository and run `ant` in the root directory.  The build is in `build/Cacophony.jar` and expects to reference its dependencies in the `lib/` directory (meaning the finished jar needs to be copied into the root).

## Dependencies

All code dependencies are in the `lib/` directory.

Building requires `ant` to be installed, as well as a Java JDK (currently targeting Java 16).

Running will require access to an [IPFS node](https://ipfs.io/) where you can generate a key for publishing (running this locally is recommended).

## How to use

Basic usage will be printed when starting the program with no arguments (`java -jar Cacophony.jar`).

Detailed documentation will be added prior to 1.0 release but examples of usage can be found in the `integration_test/scripts/` directory.

## Future plans

### Version 1.0

Version 1.0 will be mostly just a technology demo.  It will be fully usable but requires all interactions to be made in individual invocations of the program with command-line arguments to perform specific actions.

### Version 2.0

Version 2.0 will focus on building a UI to handle the common-case interactions.  The plan is to start a local web server so the UI can be rendered in a browser, also allowing easy access to viewing and posting videos.

### Version 3.0

Version 3.0 will focus on improving data cache usage to improve the discovery use-cases and better distribute data across the network.

