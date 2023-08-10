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
COOKIES1=/tmp/cookies1
COOKIES2=/tmp/cookies2

WS_STATUS1=/tmp/status1
WS_STATUS2=/tmp/status2
WS_REFRESH1=/tmp/refresh1
WS_REFRESH2=/tmp/refresh2
WS_REPLIES1=/tmp/replies1

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"
rm -f "$COOKIES2"

rm -f "$WS_STATUS1".*
rm -f "$WS_STATUS2".*
rm -f "$WS_REFRESH1".*
rm -f "$WS_REFRESH2".*
rm -f "$WS_REPLIES1".*


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

echo "Starting both Cacophony servers..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --run --port 8001 &
SERVER1_PID=$!
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar "Cacophony.jar" --run --port 8002 &
SERVER2_PID=$!
waitForHttpStart 8001
waitForHttpStart 8002

echo "Requesting creation of XSRF tokens..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/server/cookie
XSRF_TOKEN1=$(grep XSRF "$COOKIES1" | cut -f 7)
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/server/cookie
XSRF_TOKEN2=$(grep XSRF "$COOKIES2" | cut -f 7)

echo "Connect the status WebSockets (since waiting on publish updates is the only way to properly synchronize)..."
mkfifo "$WS_STATUS1.out" "$WS_STATUS1.in" "$WS_STATUS1.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN1" JSON_IO "ws://127.0.0.1:8001/server/events/status" "event_api" "$WS_STATUS1.out" "$WS_STATUS1.in" "$WS_STATUS1.clear" &
STATUS_PID1=$!
cat "$WS_STATUS1.out" > /dev/null
mkfifo "$WS_STATUS2.out" "$WS_STATUS2.in" "$WS_STATUS2.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN2" JSON_IO "ws://127.0.0.1:8002/server/events/status" "event_api" "$WS_STATUS2.out" "$WS_STATUS2.in" "$WS_STATUS2.clear" &
STATUS_PID2=$!
cat "$WS_STATUS2.out" > /dev/null

echo "Connect the refresh WebSockets (since we will need to wait on followee refresh)..."
mkfifo "$WS_REFRESH1.out" "$WS_REFRESH1.in" "$WS_REFRESH1.clear"
java -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN1" JSON_IO "ws://127.0.0.1:8001/followee/events/refreshTime" "event_api" "$WS_REFRESH1.out" "$WS_REFRESH1.in" "$WS_REFRESH1.clear" &
FOLLOWEE_REFRESH1_PID=$!
cat "$WS_REFRESH1.out" > /dev/null
mkfifo "$WS_REFRESH2.out" "$WS_REFRESH2.in" "$WS_REFRESH2.clear"
java -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN2" JSON_IO "ws://127.0.0.1:8002/followee/events/refreshTime" "event_api" "$WS_REFRESH2.out" "$WS_REFRESH2.in" "$WS_REFRESH2.clear" &
FOLLOWEE_REFRESH2_PID=$!
cat "$WS_REFRESH2.out" > /dev/null

echo "Creating Cacophony channels (1 on each node)..."
KEY1=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/home/channel/new/CACO1)
echo "Key on server1: $KEY1"
KEY2=$(curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/home/channel/new/CACO2)
echo "Key on server2: $KEY2"

echo "Make them follow each other..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/followees/add/$KEY2
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/followees/add/$KEY1
# This also requires waiting for us to see the initial follow attempts happen.
SAMPLE=$(cat "$WS_REFRESH1.out") && echo -n "-ACK" > "$WS_REFRESH1.in" && cat "$WS_REFRESH1.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$KEY2\",\"value\":0,\"isNewest\":true}"
SAMPLE=$(cat "$WS_REFRESH1.out") && echo -n "-ACK" > "$WS_REFRESH1.in" && cat "$WS_REFRESH1.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY2\",\"value\""
SAMPLE=$(cat "$WS_REFRESH2.out") && echo -n "-ACK" > "$WS_REFRESH2.in" && cat "$WS_REFRESH2.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$KEY1\",\"value\":0,\"isNewest\":true}"
SAMPLE=$(cat "$WS_REFRESH2.out") && echo -n "-ACK" > "$WS_REFRESH2.in" && cat "$WS_REFRESH2.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY1\",\"value\""
# Make sure that we drain the initial refresh from the status socket.
STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
if [[ "$STATUS_EVENT" =~ "\"event\":\"create\",\"key\":1," ]]; then
	STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
	STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
fi
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":2,\"value\":\"Refresh IpfsKey("
STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":2,\"value\":null"
# ...and the second server.
STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
if [[ "$STATUS_EVENT" =~ "\"event\":\"create\",\"key\":1," ]]; then
	STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
	STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
fi
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":2,\"value\":\"Refresh IpfsKey("
STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":2,\"value\":null"

echo "Make a basic post on server 1, refresh server 2 to see it, and have them post a response from server 2..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/allDrafts/new/NONE)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
ORIGINAL_ID=$(echo $ID_PARSE)
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Root%20Entry&DESCRIPTION=Nothing" http://127.0.0.1:8001/draft/$ORIGINAL_ID
# Publish and wait for completion.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/draft/publish/$KEY1/$ORIGINAL_ID/TEXT_ONLY
STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":3,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":3,\"value\":null"

# Refresh from the other user.
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/followee/refresh/$KEY1
SAMPLE=$(cat "$WS_REFRESH2.out") && echo -n "-ACK" > "$WS_REFRESH2.in" && cat "$WS_REFRESH2.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY1\",\"value\""
# ...and the status socket.
STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":3,\"value\":\"Refresh IpfsKey("
STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":3,\"value\":null"

# Get the list of posts and verify we see it.
LIST=$(curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XGET http://127.0.0.1:8002/server/postHashes/$KEY1)
requireSubstring "$LIST" "[\"Qm"
REPLY_TO=$(echo "$LIST" | cut -d \" -f 2)

# Post the response.
CREATED=$(curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/allDrafts/new/$REPLY_TO)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
ORIGINAL_ID=$(echo $ID_PARSE)
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Reply%20Entry&DESCRIPTION=Reply" http://127.0.0.1:8002/draft/$ORIGINAL_ID
# Publish and wait for completion.
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/draft/publish/$KEY2/$ORIGINAL_ID/TEXT_ONLY
STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":4,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":4,\"value\":null"

echo "Refresh server 1, verify that we see the response, and then verify that it is in the WebSocket..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/followee/refresh/$KEY2
SAMPLE=$(cat "$WS_REFRESH1.out") && echo -n "-ACK" > "$WS_REFRESH1.in" && cat "$WS_REFRESH1.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY2\",\"value\""
# ...and the status socket.
STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":4,\"value\":\"Refresh IpfsKey("
STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":4,\"value\":null"

# We can verify that we see it in the post list.
LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8001/server/postHashes/$KEY2)
requireSubstring "$LIST" "[\"Qm"
NEW_POST=$(echo "$LIST" | cut -d \" -f 2)

# Open the WebSocket for the replies and verify that we see this entry.
mkfifo "$WS_REPLIES1.out" "$WS_REPLIES1.in" "$WS_REPLIES1.clear"
java -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN1" JSON_IO "ws://127.0.0.1:8001/server/events/replies" "event_api" "$WS_REPLIES1.out" "$WS_REPLIES1.in" "$WS_REPLIES1.clear" &
REPLIES1_PID=$!
cat "$WS_REPLIES1.out" > /dev/null
SAMPLE=$(cat "$WS_REPLIES1.out") && echo -n "-ACK" > "$WS_REPLIES1.in" && cat "$WS_REPLIES1.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$NEW_POST\",\"value\":\"$REPLY_TO\",\"isNewest\":false}"

echo "Delete this entry, refresh the followee, and then observe that the replyTo disappears from the WebSocket..."
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XDELETE "http://127.0.0.1:8002/home/post/delete/$KEY2/$NEW_POST"
checkPreviousCommand "DELETE post"
STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":5,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$WS_STATUS2.out") && echo -n "-ACK" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":5,\"value\":null"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/followee/refresh/$KEY2
SAMPLE=$(cat "$WS_REFRESH1.out") && echo -n "-ACK" > "$WS_REFRESH1.in" && cat "$WS_REFRESH1.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY2\",\"value\""
# ...and the status socket.
STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":5,\"value\":\"Refresh IpfsKey("
STATUS_EVENT=$(cat "$WS_STATUS1.out") && echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":5,\"value\":null"
LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8001/server/postHashes/$KEY2)
requireSubstring "$LIST" "[]"
SAMPLE=$(cat "$WS_REPLIES1.out") && echo -n "-ACK" > "$WS_REPLIES1.in" && cat "$WS_REPLIES1.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$NEW_POST\",\"value\":null,\"isNewest\":false}"

# We can also now bring down the WebSocket.
echo -n "-CLOSE" > "$WS_REPLIES1.in" && cat "$WS_REPLIES1.clear" > /dev/null
wait $REPLIES1_PID


echo "Stop the servers and wait for exit..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/server/stop"
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" -XPOST "http://127.0.0.1:8002/server/stop"
wait $SERVER1_PID
wait $SERVER2_PID

# Allow the sockets to close.
echo -n "-WAIT" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
echo -n "-WAIT" > "$WS_STATUS2.in" && cat "$WS_STATUS2.clear" > /dev/null
echo -n "-WAIT" > "$WS_REFRESH1.in" && cat "$WS_REFRESH1.clear" > /dev/null
echo -n "-WAIT" > "$WS_REFRESH2.in" && cat "$WS_REFRESH2.clear" > /dev/null
wait $STATUS_PID1
wait $STATUS_PID2
wait $FOLLOWEE_REFRESH1_PID
wait $FOLLOWEE_REFRESH2_PID


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
