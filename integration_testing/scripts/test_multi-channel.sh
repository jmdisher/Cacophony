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
COOKIES1=/tmp/cookies1

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

echo "Before creating any channels, make sure that the interactive server can be started and stopped..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --run --port 8001 &
SERVER_PID=$!
waitForCacophonyStart 8001
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/server/cookie
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/server/stop"
wait $SERVER_PID

echo "Creating channel on node 1..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --createNewChannel
checkPreviousCommand "createNewChannel1"

echo "Count the pins after the creation (14 = 9 + 5 (index, recommendations, records, description, pic))..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "14"

echo "Quickstart another channel..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=quick java -Xmx32m -jar Cacophony.jar --quickstart --name "Quick user"
checkPreviousCommand "create quickstart"

echo "Make sure we see the new channels..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 2 channels:"
requireSubstring "$CHANNEL_LIST" "Key name: test1 (SELECTED)"
requireSubstring "$CHANNEL_LIST" "Key name: quick"

echo "Count the pins after the creation (16 = 9 + 5 (index, recommendations, records, description, pic) + 2 (index, description))..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "16"

echo "Verify that we aren't allowed to start following a home user..."
RESULT_STRING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --getPublicKey)
PUBLIC_KEY1=$(echo $RESULT_STRING | cut -d " " -f 14)
RESULT_STRING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=quick java -Xmx32m -jar Cacophony.jar --startFollowing --publicKey "$PUBLIC_KEY1")
# We expect a usage error.
if [ $? -ne 1 ]; then
	exit 1
fi
RESULT_STRING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=quick java -Xmx32m -jar Cacophony.jar --getPublicKey)
PUBLIC_KEY2=$(echo $RESULT_STRING | cut -d " " -f 14)

echo "Before deleting the channels, make sure that the interactive server works..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --run --port 8001 &
SERVER_PID=$!
waitForCacophonyStart 8001
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/server/cookie

echo "Prove that we can update these home users independently..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Test1%20User&DESCRIPTION=The%20test%20user" "http://127.0.0.1:8001/home/userInfo/info/$PUBLIC_KEY1"
checkPreviousCommand "update description info: test1"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "DESCRIPTION=The%20quick%20user" "http://127.0.0.1:8001/home/userInfo/info/$PUBLIC_KEY2"
checkPreviousCommand "update description info: quick"
USER_INFO1=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/userInfo/$PUBLIC_KEY1")
requireSubstring "$USER_INFO1" "{\"name\":\"Test1 User\",\"description\":\"The test user\","
USER_INFO2=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/userInfo/$PUBLIC_KEY2")
requireSubstring "$USER_INFO2" "{\"name\":\"Quick user\",\"description\":\"The quick user\","

echo "Check the list of home channels..."
CHANNEL_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/home/channels")
requireSubstring "$CHANNEL_LIST" "{\"keyName\":\"quick\",\"publicKey\":\"$PUBLIC_KEY2\","
requireSubstring "$CHANNEL_LIST" "{\"keyName\":\"test1\",\"publicKey\":\"$PUBLIC_KEY1\","

echo "We can now stop the server..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/server/stop"
wait $SERVER_PID

echo "Delete the channel..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --deleteChannel
checkPreviousCommand "delete test1"

echo "Make sure we see only one channel..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 1 channels:"
requireSubstring "$CHANNEL_LIST" "Key name: quick"

echo "Count the pins after the creation (14 = 9 + 5 (index, recommendations, records, description, pic))..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "14"

echo "Make sure that we see the expected output from descriptions and recommendations..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --readDescription >& /dev/null
# We expect a usage error.
if [ $? -ne 1 ]; then
	exit 1
fi
DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=quick java -Xmx32m -jar Cacophony.jar --readDescription)
requireSubstring "$DESCRIPTION" "Name: Quick user"

CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listRecommendations >& /dev/null
# We expect a usage error.
if [ $? -ne 1 ]; then
	exit 1
fi
LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=quick java -Xmx32m -jar Cacophony.jar --listRecommendations)
requireSubstring "$LIST" "0 keys in list:"

echo "Delete the quick channel..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=quick java -Xmx32m -jar Cacophony.jar --deleteChannel
checkPreviousCommand "delete quick"

echo "Make sure we don't see any channels..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 0 channels:"

echo "Count the pins after the delete (should be back to default 9)..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "9"


kill $PID1

wait $PID1

echo -e "\033[32;40mSUCCESS!\033[0m"