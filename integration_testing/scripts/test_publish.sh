#!/bin/bash

BASEDIR=$(dirname $0)
source "$BASEDIR/utils.sh"


# START.
if [ $# -ne 3 ]; then
	echo "Missing arguments: path_to_ipfs path_to_resources path_to_jar"
	exit 1
fi
PATH_TO_IPFS="$1"
RESOURCES="$2"
PATH_TO_JAR="$3"

USER1=/tmp/user1
USER2=/tmp/user2

rm -rf "$USER1"
rm -rf "$USER2"

# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar

setupIpfsInstance "$PATH_TO_IPFS" "$RESOURCES" 1
setupIpfsInstance "$PATH_TO_IPFS" "$RESOURCES" 2
startIpfsInstance "$PATH_TO_IPFS" 1
PID1=$RET
echo "Daemon 1: $PID1"
startIpfsInstance "$PATH_TO_IPFS" 2
PID2=$RET
echo "Daemon 2: $PID2"

echo "Pausing for startup..."
waitForIpfsStart "$PATH_TO_IPFS" 1
waitForIpfsStart "$PATH_TO_IPFS" 2

# Verify that the swarm is stable.
verifySwarmWorks "$PATH_TO_IPFS" "$PID1"
PID1="$RET"

echo "Creating Cacophony instance..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --createNewChannel
checkPreviousCommand "createNewChannel"

echo "Create the 512 KiB file for testing..."
TEST_FILE="/tmp/zero_file"
createBinaryFile "$TEST_FILE" 512

echo "Publishing test..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --publishToThisChannel --name "test post" --description "no description" --discussionUrl "http://www.example.com" --element --mime "application/octet-stream" --file "$TEST_FILE"
checkPreviousCommand "publishToThisChannel"

echo "Make sure we see this in the list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "QmeBAFpC3fbNhVMsExM8uS23gKmiaPQJbNu5rFEKDGdhcW - application/octet-stream"

echo "Create the second file..."
TEST_FILE2="/tmp/zero_file2"
createBinaryFile "$TEST_FILE2" 1024

echo "Publishing second test..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --publishToThisChannel --name "test post 2" --description "this is a short-term entry" --discussionUrl "http://www.example.com" --element --mime "application/octet-stream" --file "$TEST_FILE2"
checkPreviousCommand "publishToThisChannel"

echo "Make sure this new entry shows up in the list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "QmVkbauSDEaMP4Tkq6Epm9uW75mWm136n81YH8fGtfwdHU - application/octet-stream"

# Get the element CID.
SECOND_CID=$(echo "$LISTING" | grep "test post 2" | cut -d ":" -f 1 | cut -d " " -f 3)

echo "Verify that the favourites work as expected..."
FAVOURITE="$SECOND_CID"
# We want to add the post, but also miscellaneous data, as a favourite and verify that only the real post ends up in the list.
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --addFavourite --elementCid "$FAVOURITE"
checkPreviousCommand "Add favourite"
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --addFavourite --elementCid "QmVkbauSDEaMP4Tkq6Epm9uW75mWm136n81YH8fGtfwdHU"
if [ $? -ne 1 ]; then
	exit 1
fi
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listFavourites)
requireSubstring "$LISTING" "Found 1 favourites:"

echo "Add a third entry with no attachments..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --publishToThisChannel --name "test post 3" --description "minimal post"
checkPreviousCommand "publishToThisChannel"
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "test post 3"

echo "Edit the second post to make sure it is replaced, inline..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --editPost --elementCid "$SECOND_CID" --name "updated post 2"
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "updated post 2"
SECOND_CID=$(echo "$LISTING" | grep "updated post 2" | cut -d ":" -f 1 | cut -d " " -f 3)

echo "Make sure that we can remove the second entry from the stream..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --removeFromThisChannel --elementCid "$SECOND_CID"

echo "Make sure this new entry is no longer in the list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
if [[ "$LISTING" =~ "$SECOND_CID" ]]; then
	echo -e "\033[31;40m$SECOND_CID was not expected in $LISTING\033[00m"
	exit 1
fi
# Check for entries 1 and 3.
requireSubstring "$LISTING" "QmeBAFpC3fbNhVMsExM8uS23gKmiaPQJbNu5rFEKDGdhcW - application/octet-stream"
requireSubstring "$LISTING" "test post 3"

echo "Just run a republish to make sure nothing goes wrong..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --republish
checkPreviousCommand "republish"

echo "Verify that the prefs look right..."
PREFS=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --getGlobalPrefs)
requireSubstring "$PREFS" "Video preferred bounds: 1280 x 1280"
requireSubstring "$PREFS" "Follower cache target size: 10.00 GB (10000000000 bytes)"

echo "Run the GC and verify that we still see the post we added as a favourite..."
requestIpfsGc "$PATH_TO_IPFS" 1
POST_DETAILS=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --showPost --elementCid "$FAVOURITE")
requireSubstring "$POST_DETAILS" "Name: test post 2"
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --removeFavourite --elementCid "$FAVOURITE"
checkPreviousCommand "Remove favourite"
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listFavourites)
requireSubstring "$LISTING" "Found 0 favourites:"

echo "Make a new post using the options for a typical video post with thumbnail and make sure that we can see its information..."
IMAGE_FILE="/tmp/image_file"
createBinaryFile "$IMAGE_FILE" 512
VIDEO_FILE="/tmp/video_file"
createBinaryFile "$VIDEO_FILE" 4096
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --publishToThisChannel --name "post with image" --description "includes a thumbnail and video" --thumbnailMime "image/jpeg" --thumbnailFile "$IMAGE_FILE" --element --mime "video/webm" --file "$VIDEO_FILE" --height 480 --width 640
checkPreviousCommand "publishToThisChannel"
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
# We should see the basics of this post in the listing.
requireSubstring "$LISTING" "post with image"
requireSubstring "$LISTING" "Thumbnail: QmeBAFpC3fbNhVMsExM8uS23gKmiaPQJbNu5rFEKDGdhcW"
requireSubstring "$LISTING" "QmWxHCZ2Xr2SQkSQn5ZFUVtvoF7tHt8Z3eJe4MFpEHFZt5 - video/webm"
NEW_CID=$(echo "$LISTING" | grep "post with image" | cut -d ":" -f 1 | cut -d " " -f 3)
POST_DETAILS=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --showPost --elementCid "$NEW_CID")
requireSubstring "$POST_DETAILS" "Thumbnail: IpfsFile(QmeBAFpC3fbNhVMsExM8uS23gKmiaPQJbNu5rFEKDGdhcW)"
requireSubstring "$POST_DETAILS" "Video: IpfsFile(QmWxHCZ2Xr2SQkSQn5ZFUVtvoF7tHt8Z3eJe4MFpEHFZt5)"


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
