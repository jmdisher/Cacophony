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

echo "Make sure we don't see any channels..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 0 channels:"

echo "Creating key on node 1..."
REPO1=$(getIpfsRepoPath 1)
PUBLIC1=$(IPFS_PATH="$REPO1" $PATH_TO_IPFS key gen test1)
echo "Key is $PUBLIC1"
echo "Attaching Cacophony instance1 to this key..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --createNewChannel
checkPreviousCommand "createNewChannel1"

echo "Make sure we see the new channel..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 1 channels:"
requireSubstring "$CHANNEL_LIST" "Key name: test1 (SELECTED)"

echo "Creating key on node 2..."
REPO2=$(getIpfsRepoPath 2)
PUBLIC2=$(IPFS_PATH="$REPO2" $PATH_TO_IPFS key gen test2)
echo "Key is $PUBLIC2"
echo "Attaching Cacophony instance2 to this key..."
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" CACOPHONY_KEY_NAME=test2 java -Xmx32m -jar Cacophony.jar --createNewChannel
checkPreviousCommand "createNewChannel2"

echo "Verify that we can set the prefs..."
PREFS_OUTPUT=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --setGlobalPrefs \
	--edgeMaxPixels 1280 \
	--followCacheTargetBytes 50M \
	--followeeThumbnailMaxBytes 123K \
	--followeeAudioMaxBytes 2M \
	--followeeVideoMaxBytes 3M \
)
requireSubstring "$PREFS_OUTPUT" "Video preferred bounds: 1280 x 1280"
requireSubstring "$PREFS_OUTPUT" "Follower cache target size: 50.00 MB (50000000 bytes)"
requireSubstring "$PREFS_OUTPUT" "Explicit cache target size: 1.00 GB (1000000000 bytes)"
requireSubstring "$PREFS_OUTPUT" "Followee record thumbnail max bytes: 123.00 kB (123000 bytes)"
requireSubstring "$PREFS_OUTPUT" "Followee record audio max bytes: 2.00 MB (2000000 bytes)"
requireSubstring "$PREFS_OUTPUT" "Followee record video max bytes: 3.00 MB (3000000 bytes)"
requireSubstring "$PREFS_OUTPUT" "Update saved"

echo "Verify that the puplic key is correct..."
PUBLIC_KEY_OUTPUT=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --getPublicKey)
# Note that the output we get from --getPublicKey is our canonicalized base58 whereas the key gen we have is base36, so canonicalize it, first.
CANONICAL1=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --canonicalizeKey --key "$PUBLIC1")
requireSubstring "$PUBLIC_KEY_OUTPUT" "Public Key (other users can follow you with this): $CANONICAL1"

echo "Verify that they can each resolve each other..."
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey $PUBLIC2)
requireSubstring "$DESCRIPTION" "Name: Unnamed"
DESCRIPTION=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey $PUBLIC1)
requireSubstring "$DESCRIPTION" "Name: Unnamed"

echo "Update the names and make sure that they both see each others' updates..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --updateDescription --name "NAME1"
checkPreviousCommand "updateDescription1"
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" CACOPHONY_KEY_NAME=test2 java -Xmx32m -jar Cacophony.jar --updateDescription --name "NAME2"
checkPreviousCommand "updateDescription2"
# We need to purge the explicit cache in order to see the update since it isn't currently expiring data.
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --purgeExplicitCache >& /dev/null
checkPreviousCommand "purgeExplicitCache"
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --purgeExplicitCache >& /dev/null
checkPreviousCommand "purgeExplicitCache"
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey $PUBLIC2)
requireSubstring "$DESCRIPTION" "Name: NAME2"
requireSubstring "$DESCRIPTION" "Feature: null"
DESCRIPTION=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey $PUBLIC1)
requireSubstring "$DESCRIPTION" "Name: NAME1"
requireSubstring "$DESCRIPTION" "Feature: null"

echo "Verify that the answer for something which should not exist makes sense..."
# Redirect error since it logs the exception.
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_VERBOSE="" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV 2> /dev/null)
requireSubstring "$DESCRIPTION" "Check explicit cache: IpfsKey(z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV)"

echo "Publish a post and use that as our feature, then clear it..."
PUBLISH_OUTPUT=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar "Cacophony.jar" --publishToThisChannel --name "feature post" --description "no description")
FEATURE_CID=$(echo -n "$PUBLISH_OUTPUT" | grep IpfsFile | grep feature | cut -d "(" -f 2 | cut -d ")" -f 1)
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --updateDescription --feature "$FEATURE_CID")
requireSubstring "$DESCRIPTION" "Feature: IpfsFile($FEATURE_CID)"
# Verify that this fails with a valid CID which is NOT in our list.
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --updateDescription --feature "QmeFfbNEHKDx4c2xhVrajoqE1U5i4GaF5cYuKLSr9cF6nX" 2>&1)
if [[ "$?" != 1 ]]; then
	echo "Failure not seen"
	exit 1
fi
requireSubstring "$DESCRIPTION" "Usage error in running command: Feature post should be a record in your stream."
# Verify that clearing the setting also works.
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --updateDescription --feature "NONE")
requireSubstring "$DESCRIPTION" "Feature: null"


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
