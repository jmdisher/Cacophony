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
if [ $# -ne 4 ]; then
	echo "Missing arguments: path_to_ipfs path_to_resources path_to_jar path_to_libs"
	exit 1
fi

BASEDIR=$(dirname $0)
PATH_TO_IPFS="$1"
RESOURCES="$2"
PATH_TO_JAR="$3"
PATH_TO_LIBS="$4"

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
java -cp "$PATH_TO_LIBS/*:$PATH_TO_JAR" com.jeffdisher.cacophony.Cacophony "$USER1" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001 --keyName test1

echo "Creating key on node 2..."
PUBLIC2=$(IPFS_PATH="$REPO2" $PATH_TO_IPFS key gen test2)
echo "Key is $PUBLIC2"
echo "Attaching Cacophony instance2 to this key..."
java -cp "$PATH_TO_LIBS/*:$PATH_TO_JAR" com.jeffdisher.cacophony.Cacophony "$USER2" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5002 --keyName test2

echo "Verify that they can each resolve each other..."
DESCRIPTION=$(java -cp "$PATH_TO_LIBS/*:$PATH_TO_JAR" com.jeffdisher.cacophony.Cacophony "$USER1" --readDescription --publicKey $PUBLIC2)
requireSubstring "$DESCRIPTION" "name: Unnamed"
DESCRIPTION=$(java -cp "$PATH_TO_LIBS/*:$PATH_TO_JAR" com.jeffdisher.cacophony.Cacophony "$USER2" --readDescription --publicKey $PUBLIC1)
requireSubstring "$DESCRIPTION" "name: Unnamed"

echo "Update the names and make sure that they both see each others' updates..."
java -cp "$PATH_TO_LIBS/*:$PATH_TO_JAR" com.jeffdisher.cacophony.Cacophony "$USER1" --updateDescription --name "NAME1"
java -cp "$PATH_TO_LIBS/*:$PATH_TO_JAR" com.jeffdisher.cacophony.Cacophony "$USER2" --updateDescription --name "NAME2"
DESCRIPTION=$(java -cp "$PATH_TO_LIBS/*:$PATH_TO_JAR" com.jeffdisher.cacophony.Cacophony "$USER1" --readDescription --publicKey $PUBLIC2)
requireSubstring "$DESCRIPTION" "name: NAME2"
DESCRIPTION=$(java -cp "$PATH_TO_LIBS/*:$PATH_TO_JAR" com.jeffdisher.cacophony.Cacophony "$USER2" --readDescription --publicKey $PUBLIC1)
requireSubstring "$DESCRIPTION" "name: NAME1"

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"