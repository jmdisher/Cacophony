# Cacophony

An experimental video sharing platform on top of [IPFS node](https://ipfs.io/).  One way to think of this is like a "decentralized YouTube."

More generally, it can also be used just for decentralized web logging, etc.

It provides a way to find and synchronize user data across the IPFS peer-to-peer network.  This means that it operates without any central server to define what data is on the network, nor what users are publishing to it.

More details can be found [on this project wiki](https://github.com/jmdisher/Cacophony/wiki).


## Usage

Generally, just (unzip the latest release)[https://github.com/jmdisher/Cacophony/releases] and run as `java -Xmx32m Cacophony.jar --run` and then access the instance via http://127.0.0.1:8000.

Details of the usage can be found on the [Getting Started page](https://github.com/jmdisher/Cacophony/wiki/Getting-Started).

Note that Cacophony requires that you have a Java Runtime Environment of at least version 16 or later.  Additionally, Cacophony is built on top of IPFS (tested on version 0.23.0), so it requires that the IPFS command-line client or desktop client be running whenever using it.


## How to build

Clone the repository and run `ant` in the root directory.  The finished build is put in `build/Cacophony.jar` and expects to reference its dependencies in the `lib/` directory (meaning the finished jar needs to be copied into the root).

During active development, the full build and integration test suite is run by running `./run_all_integration_tests_on_clean_build.sh` in the root of the repository.  Note that you will likely need to update some referenced paths in this script to point to our local IPFS installation as this is a full integration test run, not just depending on Cacophony build products.


### Build dependencies

All code dependencies are in the `lib/` directory.

Building requires `ant` to be installed, as well as a Java JDK (currently targeting Java 16).

