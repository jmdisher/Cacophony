# Cacophony

An experimental video sharing platform on top of [IPFS node](https://ipfs.io/).  One way to think of this is like a "decentralized YouTube."

More generally, it can also be used just for decentralized web logging, etc.

It provides a way to find and synchronize user data across the IPFS peer-to-peer network.  This means that it operates without any central server to define what data is on the network, nor what users are publishing to it.

## Usage

Details of the usage can be found on the [Getting Started page](https://github.com/jmdisher/Cacophony/wiki/Getting-Started).

Note that Cacophony requires that you have a Java Runtime Environment of at least version 16 or later.  Additionally, Cacophony is built on top of IPFS (developed on version 0.20.0), so it requires that the IPFS command-line client or desktop client be running whenever using it.

## How to build

Clone the repository and run `ant` in the root directory.  The build is in `build/Cacophony.jar` and expects to reference its dependencies in the `lib/` directory (meaning the finished jar needs to be copied into the root).

During active development, the full build and integration test suite is run by running `./run_all_integration_tests_on_clean_build.sh` in the root of the repository.

### Build dependencies

All code dependencies are in the `lib/` directory.

Building requires `ant` to be installed, as well as a Java JDK (currently targeting Java 16).

## IPFS Breaking your Internet Connection?

If you are finding your internet connection going down after running the IPFS node for a while, you are not alone.  This seems to be related to limitations in many consumer home routers when using systems which open lots of peer-to-peer connections (BitTorrent, IPFS, Blockchain nodes, etc).

[There is an issue about this on the IPFS GitHub](https://github.com/ipfs/go-ipfs/issues/3320) and the user **kakra** proposed a solution which worked for me.  Modify your IPFS config file's `ConnMgr` stanza to look like this (reduces some limits):

```
    "ConnMgr": {
      "GracePeriod": "60s",
      "HighWater": 200,
      "LowWater": 150,
      "Type": "basic"
    },
```

## Future plans

Consult the [Roadmap](https://github.com/jmdisher/Cacophony/wiki/Roadmap) to see future plans.
