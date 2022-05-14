# Cacophony

An experimental video sharing platform on top of IPFS.  One way to think of this is like a "decentralized YouTube."

## Usage

Details of the usage can be found on the [Getting Started page](https://github.com/jmdisher/Cacophony/wiki/Getting-Started).

Further examples of usage can be found on the [Basic Usage page](https://github.com/jmdisher/Cacophony/wiki/Basic-Usage).

## How to build

Clone the repository and run `ant` in the root directory.  The build is in `build/Cacophony.jar` and expects to reference its dependencies in the `lib/` directory (meaning the finished jar needs to be copied into the root).

## Dependencies

All code dependencies are in the `lib/` directory.

Building requires `ant` to be installed, as well as a Java JDK (currently targeting Java 16).

Running will require access to an [IPFS node](https://ipfs.io/) where you can generate a key for publishing (running this locally is recommended).

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

### Version 1.0

Version 1.0 will be mostly just a technology demo.  It will be fully usable but requires all interactions to be made in individual invocations of the program with command-line arguments to perform specific actions.

### Version 2.0

Version 2.0 will focus on building a UI to handle the common-case interactions.  The plan is to start a local web server so the UI can be rendered in a browser, also allowing easy access to viewing and posting videos.

### Version 3.0

Version 3.0 will focus on improving data cache usage to improve the discovery use-cases and better distribute data across the network.

