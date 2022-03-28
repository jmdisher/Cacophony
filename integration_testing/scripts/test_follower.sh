#!/bin/bash

BASEDIR=$(dirname $0)
source "$BASEDIR/utils.sh"

function checkFileExists()
{
	FILE="$1"
	if [ ! -f "$FILE" ]; then
		echo "File not found: \"$FILE\""
		exit 1
	fi
}


# START.
if [ $# -ne 3 ]; then
	echo "Missing arguments: path_to_ipfs path_to_resources path_to_jar"
	exit 1
fi

BASEDIR=$(dirname $0)
PATH_TO_IPFS="$1"
RESOURCES="$2"
PATH_TO_JAR="$3"

REPO1=/tmp/repo1
REPO2=/tmp/repo2

USER1=/tmp/user1
USER2=/tmp/user2

STATIC2=/tmp/static2

rm -rf "$REPO1"
rm -rf "$REPO2"
rm -rf "$USER1"
rm -rf "$USER2"

mkdir "$REPO1"
mkdir "$REPO2"
mkdir "$USER1"
mkdir "$USER2"

# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar

IPFS_PATH="$REPO1" $PATH_TO_IPFS init
checkPreviousCommand "repo1 init"
IPFS_PATH="$REPO2" $PATH_TO_IPFS init
checkPreviousCommand "repo2 init"

cp "$RESOURCES/swarm.key" "$REPO1/"
cp "$RESOURCES/seed_config" "$REPO1/config"
cp "$RESOURCES/swarm.key" "$REPO2/"
cp "$RESOURCES/node1_config" "$REPO2/config"

IPFS_PATH="$REPO1" $PATH_TO_IPFS daemon &
PID1=$!
echo "Daemon 1: $PID1"
IPFS_PATH="$REPO2" $PATH_TO_IPFS daemon &
PID2=$!
echo "Daemon 2: $PID2"

echo "Pausing for startup..."
sleep 5

echo "Creating key on node 1..."
PUBLIC1=$(IPFS_PATH="$REPO1" $PATH_TO_IPFS key gen test1)
echo "Key is $PUBLIC1"
echo "Attaching Cacophony instance1 to this key..."
CACOPHONY_STORAGE="$USER1" java -jar Cacophony.jar --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001 --keyName test1
checkPreviousCommand "createNewChannel"

echo "Creating key on node 2..."
PUBLIC2=$(IPFS_PATH="$REPO2" $PATH_TO_IPFS key gen test2)
echo "Key is $PUBLIC2"
echo "Attaching Cacophony instance2 to this key..."
CACOPHONY_STORAGE="$USER2" java -jar Cacophony.jar --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5002 --keyName test2
checkPreviousCommand "createNewChannel"

echo "Make key 2 follow key 1"
CACOPHONY_STORAGE="$USER2" java -jar Cacophony.jar --startFollowing --publicKey "$PUBLIC1"
checkPreviousCommand "startFollowing"

echo "List followees"
CANONICAL1=$(CACOPHONY_STORAGE="$USER1" java -jar Cacophony.jar --canonicalizeKey --key "$PUBLIC1")
LIST=$(CACOPHONY_STORAGE="$USER2" java -jar Cacophony.jar --listFollowees)
requireSubstring "$LIST" "Following: $CANONICAL1"

LIST=$(CACOPHONY_STORAGE="$USER2" java -jar Cacophony.jar --listFollowee --publicKey "$CANONICAL1")
requireSubstring "$LIST" "Followee has 0 elements"

echo "Verify that the static HTML is generated"
rm -rf "$STATIC2"
CACOPHONY_STORAGE="$USER2" java -jar Cacophony.jar --htmlOutput --directory "$STATIC2"
checkPreviousCommand "htmlOutput"
checkFileExists "$STATIC2/index.html"
checkFileExists "$STATIC2/prefs.html"
checkFileExists "$STATIC2/utils.js"
checkFileExists "$STATIC2/user.html"
checkFileExists "$STATIC2/play.html"
checkFileExists "$STATIC2/recommending.html"
checkFileExists "$STATIC2/following.html"
checkFileExists "$STATIC2/generated_db.js"

# Post an update and refresh to make sure that the follower sees the update and can re-render the HTML output
echo "Generate test files"
IMAGE_FILE="/tmp/image_file"
rm -f "$IMAGE_FILE"
dd if=/dev/zero of="$IMAGE_FILE" bs=1K count=512
checkPreviousCommand "dd image"
VIDEO_FILE="/tmp/video_file"
rm -f "$VIDEO_FILE"
dd if=/dev/zero of="$VIDEO_FILE" bs=1K count=2048
checkPreviousCommand "dd video"

echo "Publishing post..."
CACOPHONY_STORAGE="$USER1" java -jar Cacophony.jar --publishSingleVideo --name "basic post" --description "no description" --thumbnailJpeg "$IMAGE_FILE" --videoFile "$VIDEO_FILE" --videoMime "video/webm" --videoHeight 640 --videoWidth 480
checkPreviousCommand "publishSingleVideo"

echo "Refresh followee"
CACOPHONY_STORAGE="$USER2" java -jar Cacophony.jar --refreshFollowee --publicKey "$PUBLIC1"
checkPreviousCommand "Follow index changed"

echo "Regenerate the static HTML"
rm -rf "$STATIC2"
CACOPHONY_STORAGE="$USER2" java -jar Cacophony.jar --htmlOutput --directory "$STATIC2"
checkPreviousCommand "htmlOutput"
checkFileExists "$STATIC2/index.html"
checkFileExists "$STATIC2/prefs.html"
checkFileExists "$STATIC2/utils.js"
checkFileExists "$STATIC2/user.html"
checkFileExists "$STATIC2/play.html"
checkFileExists "$STATIC2/recommending.html"
checkFileExists "$STATIC2/following.html"
checkFileExists "$STATIC2/generated_db.js"

echo "Stop following and verify it is no longer in the list"
CACOPHONY_STORAGE="$USER2" java -jar Cacophony.jar --stopFollowing --publicKey "$PUBLIC1"
checkPreviousCommand "stopFollowing"
CACOPHONY_STORAGE="$USER2" java -jar Cacophony.jar --listFollowees
checkPreviousCommand "listFollowees"

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
