#!/bin/bash

# Helper functions.
function requireSubstring()
{
	HAYSTACK="$1"
	NEEDLE="$2"
	if [[ "$HAYSTACK" =~ "$NEEDLE" ]]; then
		# Matched
		true
	else
		echo "Failed to find \"$NEEDLE\" in \"$HAYSTACK\""
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
IPFS_PATH="$REPO2" $PATH_TO_IPFS init

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
java -jar "Cacophony.jar" "$USER1" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001 --keyName test1

echo "Create the 512 KiB file for testing..."
TEST_FILE="/tmp/zero_file"
rm -f "$TEST_FILE"
dd if=/dev/zero of="$TEST_FILE" bs=1K count=512

echo "Publishing test..."
java -jar "Cacophony.jar" "$USER1" --publishToThisChannel --name "test post" --discussionUrl "URL" --element --mime "application/octet-stream" --file "$TEST_FILE"

echo "Make sure we see this in the list..."
LISTING=$(java -jar "Cacophony.jar" "$USER1" --listChannel)
requireSubstring "$LISTING" "QmeBAFpC3fbNhVMsExM8uS23gKmiaPQJbNu5rFEKDGdhcW - application/octet-stream"

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
