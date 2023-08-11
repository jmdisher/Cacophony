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

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"
rm -f "$COOKIES2"


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
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN1" "ws://127.0.0.1:8001/server/events/status" event_api 9000 &
STATUS_PID1=$!
waitForHttpStart 9000
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN2" "ws://127.0.0.1:8002/server/events/status" event_api 9001 &
STATUS_PID2=$!
waitForHttpStart 9001

echo "Connect the refresh WebSockets (since we will need to wait on followee refresh)..."
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN1" "ws://127.0.0.1:8001/followee/events/refreshTime" event_api 9002 &
FOLLOWEE_REFRESH1_PID=$!
waitForHttpStart 9002
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN2" "ws://127.0.0.1:8002/followee/events/refreshTime" event_api 9003 &
FOLLOWEE_REFRESH2_PID=$!
waitForHttpStart 9003

echo "Creating Cacophony channels (1 on each node)..."
KEY1=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/home/channel/new/CACO1)
echo "Key on server1: $KEY1"
KEY2=$(curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/home/channel/new/CACO2)
echo "Key on server2: $KEY2"

echo "Make them follow each other..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/followees/add/$KEY2
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/followees/add/$KEY1
# This also requires waiting for us to see the initial follow attempts happen.
INDEX_REFRESH1=0
SAMPLE=$(curl -XGET http://127.0.0.1:9002/waitAndGet/$INDEX_REFRESH1 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$KEY2\",\"value\":0,\"isNewest\":true}"
INDEX_REFRESH1=$((INDEX_REFRESH1 + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9002/waitAndGet/$INDEX_REFRESH1 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY2\",\"value\""
INDEX_REFRESH2=0
SAMPLE=$(curl -XGET http://127.0.0.1:9003/waitAndGet/$INDEX_REFRESH2 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$KEY1\",\"value\":0,\"isNewest\":true}"
INDEX_REFRESH2=$((INDEX_REFRESH2 + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9003/waitAndGet/$INDEX_REFRESH2 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY1\",\"value\""
# Make sure that we drain the initial refresh from the status socket.
INDEX_STATUS1=0
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
if [[ "$STATUS_EVENT" =~ "\"event\":\"create\",\"key\":1," ]]; then
	INDEX_STATUS1=$((INDEX_STATUS1 + 1))
	STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
	INDEX_STATUS1=$((INDEX_STATUS1 + 1))
	STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
fi
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":2,\"value\":\"Refresh IpfsKey("
INDEX_STATUS1=$((INDEX_STATUS1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":2,\"value\":null"
# ...and the second server.
INDEX_STATUS2=0
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
if [[ "$STATUS_EVENT" =~ "\"event\":\"create\",\"key\":1," ]]; then
	INDEX_STATUS2=$((INDEX_STATUS2 + 1))
	STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
	INDEX_STATUS2=$((INDEX_STATUS2 + 1))
	STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
fi
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":2,\"value\":\"Refresh IpfsKey("
INDEX_STATUS2=$((INDEX_STATUS2 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":2,\"value\":null"

echo "Make a basic post on server 1, refresh server 2 to see it, and have them post a response from server 2..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/allDrafts/new/NONE)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
ORIGINAL_ID=$(echo $ID_PARSE)
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Root%20Entry&DESCRIPTION=Nothing" http://127.0.0.1:8001/draft/$ORIGINAL_ID
# Publish and wait for completion.
CID=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/draft/publish/$KEY1/$ORIGINAL_ID/TEXT_ONLY)
requireSubstring "$CID" "Qm"
INDEX_STATUS1=$((INDEX_STATUS1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":3,\"value\":\"Publish IpfsFile("
INDEX_STATUS1=$((INDEX_STATUS1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":3,\"value\":null"

# Refresh from the other user.
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/followee/refresh/$KEY1
INDEX_REFRESH2=$((INDEX_REFRESH2 + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9003/waitAndGet/$INDEX_REFRESH2 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY1\",\"value\""
# ...and the status socket.
INDEX_STATUS2=$((INDEX_STATUS2 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":3,\"value\":\"Refresh IpfsKey("
INDEX_STATUS2=$((INDEX_STATUS2 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
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
CID=$(curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XPOST http://127.0.0.1:8002/draft/publish/$KEY2/$ORIGINAL_ID/TEXT_ONLY)
requireSubstring "$CID" "Qm"
INDEX_STATUS2=$((INDEX_STATUS2 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":4,\"value\":\"Publish IpfsFile("
INDEX_STATUS2=$((INDEX_STATUS2 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":4,\"value\":null"

echo "Refresh server 1, verify that we see the response, and then verify that it is in the WebSocket..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/followee/refresh/$KEY2
INDEX_REFRESH1=$((INDEX_REFRESH1 + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9002/waitAndGet/$INDEX_REFRESH1 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY2\",\"value\""
# ...and the status socket.
INDEX_STATUS1=$((INDEX_STATUS1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":4,\"value\":\"Refresh IpfsKey("
INDEX_STATUS1=$((INDEX_STATUS1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":4,\"value\":null"

# We can verify that we see it in the post list.
LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8001/server/postHashes/$KEY2)
requireSubstring "$LIST" "[\"Qm"
NEW_POST=$(echo "$LIST" | cut -d \" -f 2)

# Open the WebSocket for the replies and verify that we see this entry.
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN1" "ws://127.0.0.1:8001/server/events/replies" event_api 9004 &
REPLIES1_PID=$!
waitForHttpStart 9004

INDEX_REPLIES1=0
SAMPLE=$(curl -XGET http://127.0.0.1:9004/waitAndGet/$INDEX_REPLIES1 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$NEW_POST\",\"value\":\"$REPLY_TO\",\"isNewest\":false}"

echo "Delete this entry, refresh the followee, and then observe that the replyTo disappears from the WebSocket..."
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" --no-progress-meter -XDELETE "http://127.0.0.1:8002/home/post/delete/$KEY2/$NEW_POST"
checkPreviousCommand "DELETE post"
INDEX_STATUS2=$((INDEX_STATUS2 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":5,\"value\":\"Publish IpfsFile("
INDEX_STATUS2=$((INDEX_STATUS2 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX_STATUS2 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":5,\"value\":null"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/followee/refresh/$KEY2
INDEX_REFRESH1=$((INDEX_REFRESH1 + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9002/waitAndGet/$INDEX_REFRESH1 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$KEY2\",\"value\""
# ...and the status socket.
INDEX_STATUS1=$((INDEX_STATUS1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":5,\"value\":\"Refresh IpfsKey("
INDEX_STATUS1=$((INDEX_STATUS1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX_STATUS1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":5,\"value\":null"
LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8001/server/postHashes/$KEY2)
requireSubstring "$LIST" "[]"
INDEX_REPLIES1=$((INDEX_REPLIES1 + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9004/waitAndGet/$INDEX_REPLIES1 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$NEW_POST\",\"value\":null,\"isNewest\":false}"

# We can also now bring down the WebSocket.
curl -XPOST http://127.0.0.1:9004/close 2> /dev/null
wait $REPLIES1_PID


echo "Stop the servers and wait for exit..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/server/stop"
curl --cookie "$COOKIES2" --cookie-jar "$COOKIES2" -XPOST "http://127.0.0.1:8002/server/stop"
wait $SERVER1_PID
wait $SERVER2_PID

# Allow the sockets to close.
wait $STATUS_PID1
wait $STATUS_PID2
wait $FOLLOWEE_REFRESH1_PID
wait $FOLLOWEE_REFRESH2_PID


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
