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

function checkFunctionExists()
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
java -jar Cacophony.jar "$USER1" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001 --keyName test1

echo "Creating key on node 2..."
PUBLIC2=$(IPFS_PATH="$REPO2" $PATH_TO_IPFS key gen test2)
echo "Key is $PUBLIC2"
echo "Attaching Cacophony instance2 to this key..."
java -jar Cacophony.jar "$USER2" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5002 --keyName test2

echo "Make key 2 follow key 1"
java -jar Cacophony.jar "$USER2" --startFollowing --publicKey "$PUBLIC1"

echo "List followees"
CANONICAL1=$(java -jar Cacophony.jar "$USER1" --canonicalizeKey --key "$PUBLIC1")
LIST=$(java -jar Cacophony.jar "$USER2" --listFollowees)
requireSubstring "$LIST" "Following: $CANONICAL1"

LIST=$(java -jar Cacophony.jar "$USER2" --listFollowee --publicKey "$CANONICAL1")
requireSubstring "$LIST" "Followee has 0 elements"

echo "Verify that the static HTML is generated"
rm -rf "$STATIC2"
java -jar Cacophony.jar "$USER2" --htmlOutput --directory "$STATIC2"
checkFunctionExists "$STATIC2/index.html"
checkFunctionExists "$STATIC2/prefs.html"
checkFunctionExists "$STATIC2/utils.js"
checkFunctionExists "$STATIC2/user.html"
checkFunctionExists "$STATIC2/play.html"
checkFunctionExists "$STATIC2/recommending.html"
checkFunctionExists "$STATIC2/following.html"
checkFunctionExists "$STATIC2/generated_db.js"

echo "Stop following and verify it is no longer in the list"
java -jar Cacophony.jar "$USER2" --stopFollowing --publicKey "$PUBLIC1"
java -jar Cacophony.jar "$USER2" --listFollowees

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
