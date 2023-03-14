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

echo "Creating key on node 1..."
REPO1=$(getIpfsRepoPath 1)
PUBLIC1=$(IPFS_PATH="$REPO1" $PATH_TO_IPFS key gen test1)
echo "Key is $PUBLIC1"
echo "Attaching Cacophony instance1 to this key..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001
checkPreviousCommand "createNewChannel1"

echo "Creating key on node 2..."
REPO2=$(getIpfsRepoPath 2)
PUBLIC2=$(IPFS_PATH="$REPO2" $PATH_TO_IPFS key gen test2)
echo "Key is $PUBLIC2"
echo "Attaching Cacophony instance2 to this key..."
CACOPHONY_STORAGE="$USER2" CACOPHONY_KEY_NAME=test2 java -Xmx32m -jar Cacophony.jar --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5002
checkPreviousCommand "createNewChannel2"

echo "Verify that we can set the prefs..."
PREFS_OUTPUT=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --setGlobalPrefs --edgeMaxPixels 1280 --followCacheTargetBytes 50M)
requireSubstring "$PREFS_OUTPUT" "Updated prefs!"

echo "Verify that the puplic key is correct..."
PUBLIC_KEY_OUTPUT=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --getPublicKey)
# Note that the output we get from --getPublicKey is our canonicalized base58 whereas the key gen we have is base36, so canonicalize it, first.
CANONICAL1=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --canonicalizeKey --key "$PUBLIC1")
requireSubstring "$PUBLIC_KEY_OUTPUT" "Public Key (other users can follow you with this): $CANONICAL1"

echo "Verify that they can each resolve each other..."
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey $PUBLIC2)
requireSubstring "$DESCRIPTION" "name: Unnamed"
DESCRIPTION=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey $PUBLIC1)
requireSubstring "$DESCRIPTION" "name: Unnamed"

echo "Update the names and make sure that they both see each others' updates..."
CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --updateDescription --name "NAME1"
checkPreviousCommand "updateDescription1"
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --updateDescription --name "NAME2"
checkPreviousCommand "updateDescription2"
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey $PUBLIC2)
requireSubstring "$DESCRIPTION" "name: NAME2"
DESCRIPTION=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey $PUBLIC1)
requireSubstring "$DESCRIPTION" "name: NAME1"

echo "Verify that the answer for something which should not exist makes sense..."
# Redirect error since it logs the exception.
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV 2> /dev/null)
requireSubstring "$DESCRIPTION" "NOT following IpfsKey(z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV)"

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
