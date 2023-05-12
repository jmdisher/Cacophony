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

rm -rf "$USER1"

# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar

REPO1=$(getIpfsRepoPath 1)

setupIpfsInstance "$PATH_TO_IPFS" "$RESOURCES" 1
startIpfsInstance "$PATH_TO_IPFS" 1
PID1=$RET
echo "Daemon 1: $PID1"

echo "Pausing for startup..."
waitForIpfsStart "$PATH_TO_IPFS" 1

echo "Count the number of pins (by default, the system seems to start with 9)..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "9"

echo "Make sure we don't see any channels..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 0 channels:"

echo "Creating channel on node 1..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --createNewChannel
checkPreviousCommand "createNewChannel1"

echo "Make sure we see the new channel..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 1 channels:"
requireSubstring "$CHANNEL_LIST" "Key name: test1 (SELECTED)"

echo "Count the pins after the creation (14 = 9 + 5 (index, recommendations, records, description, pic))..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "14"

echo "Delete the channel..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --deleteChannel

echo "Make sure we don't see any channels..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 0 channels:"

echo "Count the pins after the delete (should be back to default 9)..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "9"


kill $PID1

wait $PID1

echo -e "\033[32;40mSUCCESS!\033[0m"
