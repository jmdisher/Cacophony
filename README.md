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

Common usage pattern:

Create IPFS repo (remember to use `IPFS_PATH` env var if you want to set a specific repo location):  `ipfs init`

Start the IPFS daemon: `ipfs daemon &`

Create a key for Cacophony:  `ipfs key gen KEY_NAME` (be sure the capture the public key printed out when you do this - it is what you will give other people so they can subscribe to you).

Create the data directory you will use for Cacophony meta-data:  `mkdir data_dir`

Create the channel with Cacophony (`--ipfs` arg comes from `API server listening on` line in IPFS node startup): `java -jar ./Cacophony.jar ./data_dir/ --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001 --keyName KEY_NAME`

(this will create a default channel and write it to your IPFS node)

Generate out the contents of the node as browser-readable:  `java -jar ./Cacophony.jar ./data_dir/ --htmlOutput --directory ./html`

You can now view the state of your channel by opening `./html/index.html` in your browser.

Update your description:  `java -jar ./Cacophony.jar ./data_dir/ --updateDescription --name "Your name" --description "Basic description" --pictureFile "Path to a user pic"`

Regenerate out the contents of the node to see the updates:  `rm -rf ./html && java -jar ./Cacophony.jar ./data_dir/ --htmlOutput --directory ./html`

Post a video to your stream:  `java -jar ./Cacophony.jar ./data_dir/ --publishToThisChannel --name "Video name" --description "Video description" --element --mime "video/webm" --file "/path/to/video" --height 480 --width 640 --element --mime "image/jpeg" --file "/path/to/thumbnail" --special image`

Start following someone new:  `java -jar ./Cacophony.jar ./data_dir/ --startFollowing --publicKey "PUBLIC_KEY"`

Republish your data (**needs to be done at least once every 24 hours for your followers to be able to find you**):  `java -jar ./Cacophony.jar ./data_dir/ --republish`

Detailed documentation will be added prior to 1.0 release but examples of usage can be found in the `integration_test/scripts/` directory.

### IPFS Breaking your Internet Connection?

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

