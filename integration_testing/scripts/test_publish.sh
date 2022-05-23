#!/bin/bash

BASEDIR=$(dirname $0)
source "$BASEDIR/utils.sh"


# START.
if [ $# -ne 3 ]; then
	echo "Missing arguments: path_to_ipfs path_to_resources path_to_jar"
	exit 1
fi

# We always want to run our tests with extra verifications.
export CACOPHONY_ENABLE_VERIFICATIONS=1

BASEDIR=$(dirname $0)
PATH_TO_IPFS="$1"
RESOURCES="$2"
PATH_TO_JAR="$3"

REPO1=/tmp/repo1
REPO2=/tmp/repo2

USER1=/tmp/user1
USER2=/tmp/user2

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

echo "Creating Cacophony instance..."
CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001
checkPreviousCommand "createNewChannel"

echo "Create the 512 KiB file for testing..."
TEST_FILE="/tmp/zero_file"
rm -f "$TEST_FILE"
dd if=/dev/zero of="$TEST_FILE" bs=1K count=512
checkPreviousCommand "dd"

echo "Publishing test..."
CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --publishToThisChannel --name "test post" --description "no description" --discussionUrl "URL" --element --mime "application/octet-stream" --file "$TEST_FILE"
checkPreviousCommand "publishToThisChannel"

echo "Make sure we see this in the list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "QmeBAFpC3fbNhVMsExM8uS23gKmiaPQJbNu5rFEKDGdhcW - application/octet-stream"

echo "Create the second file..."
TEST_FILE2="/tmp/zero_file2"
rm -f "$TEST_FILE2"
dd if=/dev/zero of="$TEST_FILE2" bs=1K count=1024
checkPreviousCommand "dd"

echo "Publishing second test..."
CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --publishToThisChannel --name "test post 2" --description "this is a short-term entry" --discussionUrl "URL" --element --mime "application/octet-stream" --file "$TEST_FILE2"
checkPreviousCommand "publishToThisChannel"

echo "Make sure this new entry shows up in the list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "QmVkbauSDEaMP4Tkq6Epm9uW75mWm136n81YH8fGtfwdHU - application/octet-stream"

# Get the element CID.
NEW_CID=$(echo "$LISTING" | grep "test post 2" | cut -d ":" -f 1 | cut -d " " -f 2)

echo "Make sure that we can remove this entry from the stream..."
CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --removeFromThisChannel --elementCid "$NEW_CID"

echo "Make sure this new entry is no longer in the list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --listChannel)
if [[ "$LISTING" =~ "$NEW_CID" ]]; then
	echo -e "\033[31;40m$NEW_CID was not expected in $LISTING\033[00m"
	exit 1
fi

echo "Just run a republish to make sure nothing goes wrong..."
CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --republish
checkPreviousCommand "republish"

echo "Verify that the prefs look right..."
PREFS=$(CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --getGlobalPrefs)
requireSubstring "$PREFS" "Video preferred bounds: 1280 x 1280"
requireSubstring "$PREFS" "Follower cache target size: 1.00 GB (10000000000 bytes)"

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
