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
startIpfsInstance "$PATH_TO_IPFS" "$RESOURCES" 1
PID1=$RET
echo "Daemon 1: $PID1"
startIpfsInstance "$PATH_TO_IPFS" "$RESOURCES" 2
PID2=$RET
echo "Daemon 2: $PID2"

echo "Pausing for startup..."
sleep 5

echo "Creating Cacophony instance..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001
checkPreviousCommand "createNewChannel"

echo "Create the 512 KiB file for testing..."
TEST_FILE="/tmp/zero_file"
rm -f "$TEST_FILE"
dd if=/dev/zero of="$TEST_FILE" bs=1K count=512
checkPreviousCommand "dd"

echo "Publishing test..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --publishToThisChannel --name "test post" --description "no description" --discussionUrl "URL" --element --mime "application/octet-stream" --file "$TEST_FILE"
checkPreviousCommand "publishToThisChannel"

echo "Make sure we see this in the list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "QmeBAFpC3fbNhVMsExM8uS23gKmiaPQJbNu5rFEKDGdhcW - application/octet-stream"

echo "Create the second file..."
TEST_FILE2="/tmp/zero_file2"
rm -f "$TEST_FILE2"
dd if=/dev/zero of="$TEST_FILE2" bs=1K count=1024
checkPreviousCommand "dd"

echo "Publishing second test..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --publishToThisChannel --name "test post 2" --description "this is a short-term entry" --discussionUrl "URL" --element --mime "application/octet-stream" --file "$TEST_FILE2"
checkPreviousCommand "publishToThisChannel"

echo "Make sure this new entry shows up in the list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "QmVkbauSDEaMP4Tkq6Epm9uW75mWm136n81YH8fGtfwdHU - application/octet-stream"

# Get the element CID.
SECOND_CID=$(echo "$LISTING" | grep "test post 2" | cut -d ":" -f 1 | cut -d " " -f 2)

echo "Add a third entry with no attachments..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --publishToThisChannel --name "test post 3" --description "minimal post"
checkPreviousCommand "publishToThisChannel"
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "test post 3"

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

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
