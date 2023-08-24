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

REPO1=$(getIpfsRepoPath 1)

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

echo "Count the number of pins (by default, the system seems to start with 1, as of 0.20.0)..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "1"

echo "Create a channel on each node..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --createNewChannel >& /dev/null
checkPreviousCommand "createNewChannel1"
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --createNewChannel >& /dev/null
checkPreviousCommand "createNewChannel2"

# Also, get the public keys.
PUBLIC_KEY1=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --getPublicKey | grep ": z" | cut -d " " -f 11)
PUBLIC_KEY2=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --getPublicKey | grep ": z" | cut -d " " -f 11)
echo "User1: $PUBLIC_KEY1"
echo "User2: $PUBLIC_KEY2"

echo "Set the names for each channel so we can tell them apart..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --updateDescription --name "user 1" >& /dev/null
checkPreviousCommand "update 1"
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --updateDescription --name "user 2" >& /dev/null
checkPreviousCommand "update 2"

echo "Count the pins after the creation (6 = 1 + 5 (index, recommendations, records, description, pic))..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "6"

echo "Fetch information about the other user and observe the pins created (8 = 6 + 2 (other description, other index))..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey "$PUBLIC_KEY2" >& /dev/null
checkPreviousCommand "update 1"
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "8"

echo "Make a post and fetch that so we can see it isn't impacted by other explicit cache behaviour..."
POST_CID=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --publishToThisChannel --name "post" --description "nothing interesting" | grep "New element" | cut -d "(" -f 2 | cut -d ")" -f 1)

CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --showPost --elementCid "$POST_CID" >& /dev/null
checkPreviousCommand "show post"
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
# Note that this will give us 7 because it will implicitly purge the user entry: 8 - 2 (description and index) + 1 (post).
requireSubstring "$LIST_SIZE" "7"

echo "Now, refetch the purged user entry..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey "$PUBLIC_KEY2" >& /dev/null
checkPreviousCommand "update 1"
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "9"


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
