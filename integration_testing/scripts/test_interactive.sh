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

echo "Start the interactive server and wait 5 seconds for it to bind the port..."
CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --run &
SERVER_PID=$!
sleep 5

echo "Get the empty list of drafts..."
DRAFTS=$(curl --no-progress-meter -XGET http://127.0.0.1:8000/drafts)
requireSubstring "$DRAFTS" "[]"

echo "Create a new draft..."
CREATED=$(curl --no-progress-meter -XPOST http://127.0.0.1:8000/createDraft)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
ID=$(echo $ID_PARSE)
echo "...working with draft $ID"

echo "Verify that we can read the draft..."
DRAFT=$(curl --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"title\":\"New Draft - $ID\""

echo "Verify that we can see the draft in the list..."
DRAFTS=$(curl --no-progress-meter -XGET http://127.0.0.1:8000/drafts)
requireSubstring "$DRAFTS" "\"title\":\"New Draft - $ID\""

echo "Update the title and description..."
curl --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "title=Updated%20Title&description=" http://127.0.0.1:8000/draft/$ID
DRAFT=$(curl --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"title\":\"Updated Title\""

echo "Verify that we can delete the draft and see an empty list..."
curl -XDELETE http://127.0.0.1:8000/draft/$ID
DRAFTS=$(curl --no-progress-meter -XGET http://127.0.0.1:8000/drafts)
requireSubstring "$DRAFTS" "[]"

echo "Stop the server and wait for it to exit..."
curl -XPOST http://127.0.0.1:8000/stop
wait $SERVER_PID


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
