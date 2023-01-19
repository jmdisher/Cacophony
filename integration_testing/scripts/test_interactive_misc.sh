#!/bin/bash

BASEDIR=$(dirname $0)
source "$BASEDIR/utils.sh"
export CACOPHONY_ENABLE_VERIFICATIONS=1


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
COOKIES1=/tmp/cookies1
STATUS_OUTPUT=/tmp/status_output
STATUS_INPUT=/tmp/status_input
FOLLOWEE_OUTPUT=/tmp/followee_output
FOLLOWEE_INPUT=/tmp/followee_input
ENTRIES_OUTPUT=/tmp/entries_output
ENTRIES_INPUT=/tmp/entries_input

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"
rm -f "$STATUS_INPUT" "$STATUS_OUTPUT"
rm -f "$FOLLOWEE_INPUT" "$FOLLOWEE_OUTPUT"
rm -f "$ENTRIES_INPUT" "$ENTRIES_OUTPUT"


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

echo "Create user1..."
CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar "Cacophony.jar" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001
checkPreviousCommand "createNewChannel"

echo "Create user2..."
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar "Cacophony.jar" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5002

echo "Make user1 follow user2 and verify an empty stream..."
RESULT_STRING=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --getPublicKey)
PUBLIC2=$(echo "$RESULT_STRING" | cut -d " " -f 10)
CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --startFollowing --publicKey "$PUBLIC2"
checkPreviousCommand "startFollowing"
LIST=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --listFollowee --publicKey "$PUBLIC2")
requireSubstring "$LIST" "Followee has 0 elements"

echo "Start the interactive server, give it 5 seconds to bind the port, then verify we can load a page, and initialize the cookies and XSRF token..."
CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar "Cacophony.jar" --run &
SERVER_PID=$!
sleep 5
INDEX=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET -L "http://127.0.0.1:8000/")
requireSubstring "$INDEX" "Cacophony - Static Index"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)

echo "Attach the status listener..."
mkfifo "$STATUS_INPUT"
mkfifo "$STATUS_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/backgroundStatus" "event_api" "$STATUS_INPUT" "$STATUS_OUTPUT" &
STATUS_PID=$!
# Wait for connect so that we know we will see the refresh.
cat "$STATUS_OUTPUT" > /dev/null

echo "Attach the followee post listener..."
mkfifo "$ENTRIES_INPUT"
mkfifo "$ENTRIES_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/user/entries/$PUBLIC2" "event_api" "$ENTRIES_INPUT" "$ENTRIES_OUTPUT" &
ENTRIES_PID=$!
cat "$ENTRIES_OUTPUT" > /dev/null

echo "Request a refresh of user2 and wait for the event to show up in status, that it is complete, then also verify an empty stream..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST "http://127.0.0.1:8000/followee/refresh/$PUBLIC2"
SAMPLE=$(cat "$STATUS_OUTPUT")
echo -n "-ACK" > "$STATUS_INPUT"
while [ "$SAMPLE" != "{\"event\":\"create\",\"key\":2,\"value\":\"Refresh IpfsKey($PUBLIC2)\"}" ]
do
	SAMPLE=$(cat "$STATUS_OUTPUT")
	echo -n "-ACK" > "$STATUS_INPUT"
done
SAMPLE=$(cat "$STATUS_OUTPUT")
echo -n "-ACK" > "$STATUS_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":2,\"value\":null}"
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[]"

echo "Attach the followee refresh WebSocket and verify followee state..."
mkfifo "$FOLLOWEE_INPUT"
mkfifo "$FOLLOWEE_OUTPUT"
java -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/followee/refreshTime" "event_api" "$FOLLOWEE_INPUT" "$FOLLOWEE_OUTPUT" &
FOLLOWEE_PID=$!
cat "$FOLLOWEE_OUTPUT" > /dev/null
SAMPLE=$(cat "$FOLLOWEE_OUTPUT")
echo -n "-ACK" > "$FOLLOWEE_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$PUBLIC2\",\"value\":"

echo "Make a post as user2..."
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --publishToThisChannel --name "post" --description "no description"
checkPreviousCommand "publishToThisChannel"

echo "Request a refresh of this user and wait for the event to show up in status, that it is complete, then also verify we see this element in the stream..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST "http://127.0.0.1:8000/followee/refresh/$PUBLIC2"
SAMPLE=$(cat "$STATUS_OUTPUT")
echo -n "-ACK" > "$STATUS_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":3,\"value\":\"Refresh IpfsKey($PUBLIC2)\"}"
SAMPLE=$(cat "$STATUS_OUTPUT")
echo -n "-ACK" > "$STATUS_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":3,\"value\":null}"
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[\"Qm"

echo "Verify that we see the refresh in the followee socket..."
SAMPLE=$(cat "$FOLLOWEE_OUTPUT")
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$PUBLIC2\",\"value\":"
echo -n "-ACK" > "$FOLLOWEE_INPUT"

echo "Verify that we see the new entry in the entry socket..."
SAMPLE=$(cat "$ENTRIES_OUTPUT")
echo -n "-ACK" > "$ENTRIES_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":"

echo "Test the add/remove of the followee..."
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[\""
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XDELETE "http://127.0.0.1:8000/followees/$PUBLIC2"
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC2")
if [ "" != "$POST_LIST" ];
then
	exit 1
fi
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST "http://127.0.0.1:8000/followees/$PUBLIC2"
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[\""
SAMPLE=$(cat "$FOLLOWEE_OUTPUT")
echo -n "-ACK" > "$FOLLOWEE_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$PUBLIC2\",\"value\":null}"
SAMPLE=$(cat "$FOLLOWEE_OUTPUT")
echo -n "-ACK" > "$FOLLOWEE_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$PUBLIC2\",\"value\":"

echo "Check asking for information about users, including invalid keys..."
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/unknownUser/BOGUS")
requireSubstring "$USER_INFO" "Invalid key: BOGUS"
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/unknownUser/$PUBLIC2")
requireSubstring "$USER_INFO" "{\"name\":\"Unnamed\",\"description\":\"Description forthcoming\",\"userPicUrl\":\"http://127.0.0.1:8080/ipfs/QmXsfdKGurBGFfzyRjVQ5APrhC6JE8x3hRRm8kGfGWRA5V\",\"email\":null,\"website\":null}"


echo "Stop the server and wait for it to exit..."
echo -n "COMMAND_STOP" > "$STATUS_INPUT"
wait $SERVER_PID
echo -n "-WAIT" > "$STATUS_INPUT"
echo -n "-WAIT" > "$FOLLOWEE_INPUT"
echo -n "-WAIT" > "$ENTRIES_INPUT"
wait $STATUS_PID
wait $FOLLOWEE_PID
wait $ENTRIES_PID

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
