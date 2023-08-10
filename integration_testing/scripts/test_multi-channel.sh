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

WS_COMBINED=/tmp/combined

rm -rf "$USER1"

rm -f "$WS_COMBINED".*


# Makes a text post as the given user.
# 1) user public key.
# 2) text title.
function makeTextPost()
{
	if [ $# -ne 2 ]; then
		echo "Missing arguments: user_public_key text_title"
		exit 1
	fi
	PUBLIC_KEY="$1"
	TITLE="$2"
	
	CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/allDrafts/new/NONE)
	# We need to parse out the ID (look for '{"id":2107961294,')
	ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
	ID=$(echo $ID_PARSE)
	curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=$TITLE&DESCRIPTION=empty" "http://127.0.0.1:8001/draft/$ID"
	CID=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST "http://127.0.0.1:8001/draft/publish/$PUBLIC_KEY/$ID/TEXT_ONLY")
	requireSubstring "$CID" "Qm"
}

# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar

REPO1=$(getIpfsRepoPath 1)

setupIpfsInstance "$PATH_TO_IPFS" "$RESOURCES" 1
startIpfsInstance "$PATH_TO_IPFS" 1
PID1=$RET
echo "Daemon 1: $PID1"

echo "Pausing for startup..."
waitForIpfsStart "$PATH_TO_IPFS" 1

echo "Count the number of pins (by default, the system seems to start with 1, as of 0.20.0 - 0.9.1 seemed to start with 9)..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "1"

echo "Make sure we don't see any channels..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 0 channels:"

echo "Before creating any channels, make sure that the interactive server can be started and stopped..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --run --port 8001 &
SERVER_PID=$!
waitForHttpStart 8001
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/server/cookie
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/server/stop"
wait $SERVER_PID

echo "Creating channel on node 1..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --createNewChannel
checkPreviousCommand "createNewChannel1"

echo "Count the pins after the creation (6 = 1 + 5 (index, recommendations, records, description, pic))..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "6"

echo "Quickstart another channel..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_KEY_NAME=quick java -Xmx32m -jar Cacophony.jar --quickstart --name "Quick user"
checkPreviousCommand "create quickstart"

echo "Make sure we see the new channels..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 2 channels:"
requireSubstring "$CHANNEL_LIST" "Key name: test1 (SELECTED)"
requireSubstring "$CHANNEL_LIST" "Key name: quick"

echo "Count the pins after the creation (8 = 1 + 5 (index, recommendations, records, description, pic) + 2 (index, description))..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "8"

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
waitForHttpStart 8001
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/server/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)

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
requireSubstring "$CHANNEL_LIST" ",\"isSelected\":false,\"name\":\"Quick user\",\"userPicUrl\":\"http://127.0.0.1:8080/ipfs/QmXsfdKGurBGFfzyRjVQ5APrhC6JE8x3hRRm8kGfGWRA5V\"}"
requireSubstring "$CHANNEL_LIST" "{\"keyName\":\"test1\",\"publicKey\":\"$PUBLIC_KEY1\","
requireSubstring "$CHANNEL_LIST" ",\"isSelected\":false,\"name\":\"Test1 User\",\"userPicUrl\":\"http://127.0.0.1:8080/ipfs/QmXsfdKGurBGFfzyRjVQ5APrhC6JE8x3hRRm8kGfGWRA5V\"}"

echo "Make sure that we can set the quick user as selected..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XPOST "http://127.0.0.1:8001/home/channel/set/$PUBLIC_KEY2"
checkPreviousCommand "select quick"
CHECKED_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET "http://127.0.0.1:8001/home/publicKey")
requireSubstring "$CHECKED_KEY" "$PUBLIC_KEY2"

echo "Listen to the combined view while we make changes to the user list..."
mkfifo "$WS_COMBINED.out" "$WS_COMBINED.in" "$WS_COMBINED.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8001/server/events/combined/entries" "event_api" "$WS_COMBINED.out" "$WS_COMBINED.in" "$WS_COMBINED.clear" &
COMBINED_PID=$!
cat "$WS_COMBINED.out" > /dev/null

echo "Create the new channel and do some basic interactions to verify it works..."
NEW_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XPOST "http://127.0.0.1:8001/home/channel/new/LATE_KEY")
checkPreviousCommand "create new"
CHANNEL_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/home/channels")
CHECKED_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET "http://127.0.0.1:8001/home/publicKey")
requireSubstring "$CHANNEL_LIST" "{\"keyName\":\"LATE_KEY\",\"publicKey\":\"$CHECKED_KEY\","
requireSubstring "$NEW_KEY" "$CHECKED_KEY"

echo "Add a post by each user and verify that we see them all in the combined socket..."
makeTextPost "$PUBLIC_KEY1" "post1"
makeTextPost "$PUBLIC_KEY2" "post2"
makeTextPost "$CHECKED_KEY" "post3"
SAMPLE=$(cat "$WS_COMBINED.out")
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"Qm"
echo -n "-ACK" > "$WS_COMBINED.in" && cat "$WS_COMBINED.clear" > /dev/null
SAMPLE=$(cat "$WS_COMBINED.out")
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"Qm"
echo -n "-ACK" > "$WS_COMBINED.in" && cat "$WS_COMBINED.clear" > /dev/null
SAMPLE=$(cat "$WS_COMBINED.out")
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"Qm"
echo -n "-ACK" > "$WS_COMBINED.in" && cat "$WS_COMBINED.clear" > /dev/null

echo "Request a republish of the added channel and test1 and immediately delete them..."
# This should mean that the delete is likely happening while the refresh is still waiting to complete, so it should demonstrate that this case is handled.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XPOST "http://127.0.0.1:8001/home/channel/set/$CHECKED_KEY"
checkPreviousCommand "select CHECKED_KEY"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/home/republish/$CHECKED_KEY"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/home/republish/$PUBLIC_KEY1"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XDELETE "http://127.0.0.1:8001/home/channel/delete/$CHECKED_KEY"
checkPreviousCommand "delete LATE_KEY channel"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XPOST "http://127.0.0.1:8001/home/channel/set/$PUBLIC_KEY1"
checkPreviousCommand "select PUBLIC_KEY1"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XDELETE "http://127.0.0.1:8001/home/channel/delete/$PUBLIC_KEY1"
checkPreviousCommand "delete test1 channel"
# Deleting these should unselect everything so this should be a 404.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XGET "http://127.0.0.1:8001/home/publicKey" >& /dev/null
if [ $? != 22 ]; then
	exit 1
fi

# We expect to see a delete for each of these users since their posts should disappear.
SAMPLE=$(cat "$WS_COMBINED.out")
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"Qm"
echo -n "-ACK" > "$WS_COMBINED.in" && cat "$WS_COMBINED.clear" > /dev/null
SAMPLE=$(cat "$WS_COMBINED.out")
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"Qm"
echo -n "-ACK" > "$WS_COMBINED.in" && cat "$WS_COMBINED.clear" > /dev/null

echo "We can now stop the server..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/server/stop"
wait $SERVER_PID
echo -n "-WAIT" > "$WS_COMBINED.in" && cat "$WS_COMBINED.clear" > /dev/null
wait $COMBINED_PID

echo "Make sure we see only one channel..."
CHANNEL_LIST=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" CACOPHONY_KEY_NAME=test1 java -Xmx32m -jar Cacophony.jar --listChannels)
requireSubstring "$CHANNEL_LIST" "Found 1 channels:"
requireSubstring "$CHANNEL_LIST" "Key name: quick"

echo "Count the pins after the creation (7 = 1 + 5 (index, recommendations, records, description, pic) + 1 elt)..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "7"

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

echo "Count the pins after the delete (should be back to default 1)..."
LIST_SIZE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" pin ls | wc -l)
requireSubstring "$LIST_SIZE" "1"


kill $PID1

wait $PID1

echo -e "\033[32;40mSUCCESS!\033[0m"
