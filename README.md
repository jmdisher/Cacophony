# Cacophony

An experimental video sharing platform on top of IPFS.  One way to think of this is like a "decentralized YouTube."

## How to build

Clone the repository and run `ant` in the root directory.  The build is in `build/Cacophony.jar` and expects to reference its dependencies in the `lib/` directory (meaning the finished jar needs to be copied into the root).

## Dependencies

All code dependencies are in the `lib/` directory.

Building requires `ant` to be installed, as well as a Java JDK (currently targeting Java 16).

Running will require access to an [IPFS node](https://ipfs.io/) where you can generate a key for publishing (running this locally is recommended).

## Usage

Basic usage will be printed when starting the program with no arguments (`java -jar Cacophony.jar`).

Common usage pattern is shown below.  These commands assume that any required `IPFS_PATH` variable has been set and that the `ipfs` binary is on your `PATH`.  To generalize further, we also assume that `CACOPHONY_KEY_NAME` is set to the name of the IPFS public key you want Cacophony to use.

Note that, by default, Cacophony will store its own index data in the directory `~/.cacophony`, but this can be overridden by setting the `CACOPHONY_STORAGE` environment variable to point to the desired location.

### Starting IPFS

Cacophony depends on access to an IPFS node's API gateway so start it up (this assumes default configuration):

```
# Initialize your IPFS repository (skip this if you already did this).
$ ipfs init

# Start the IPFS daemon in the background.
$ ipfs daemon &

# Generate the key you will use in Cacophony.
$ ipfs key gen "$CACOPHONY_KEY_NAME"
```

### Create your Cacophony channel

With the IPFS node running and your key created, you can now create a channel (channel creation is required even if you are just following others):

```
# Creates your Cacophony channel in the default storage directory (~/.cacophony)
$ java -jar Cacophony.jar --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001 --keyName "$CACOPHONY_KEY_NAME"
```

### Set channel description

You can now set the name, description, and/or user picture using this command (you can leave out any you don't want to change):

```
$ java -jar Cacophony.jar --updateDescription --name "Your channel name" --description "A longer description of your channel" --pictureFile "/path/to/a/JPEG/user/pic.jpg"
```

### Post a video

Posting a video will update your channel so anyone following you can refresh and see it:

```
$ java -jar Cacophony.jar --publishSingleVideo --name "Post name" --description "Longer post description" --thumbnailJpeg "/path/to/JPEG/thumbnail.jpg" --videoFile "/path/to/video/file.webm" --videoMime "video/webm" --videoHeight 480 --videoWidth 640
```

### Share your public key

Other Cacophony users can follow you if you give them your public key:

```
$ java -jar Cacophony.jar --getPublicKey
```

You can then give them this key or post it publicly for anyone to use (the can only use this to find your videos, not impersonate you, or anything).

### Follow another user

You can follow another user, if given their public key:

```
$ java -jar Cacophony.jar --startFollowing --publicKey "public key from user"
```

### View Cacophony videos

Version 1.0 of Cacophony does not provide an interactive way to view/post videos (as it is mostly just a tech demo) but you can ask it to output everything it has into a static website for you:

```
$ java -jar Cacophony.jar --htmlOutput --directory "/path/to/output/directory"
```

It will then give you a link you can open in your browser to view the videos posted by you or anyone you are following.

### Refreshing your key on the network

IPFS only stores your public key on the network for up to 24 hours.  While people can still access/cache your content without this, new users won't be able to find you.  This means **you need to run this command once every 24 hours for new followers to be able to find you** (happens automatically when you update your description or post a new video):

```
$ java -jar Cacophony.jar --republish
```

Further examples of usage can be found in the `integration_test/scripts/` directory.

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

