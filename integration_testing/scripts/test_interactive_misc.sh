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

WS_STATUS=/tmp/status
WS_REFRESH=/tmp/refresh
WS_ENTRIES=/tmp/entries

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"

rm -f "$WS_STATUS".*
rm -f "$WS_REFRESH".*
rm -f "$WS_ENTRIES".*


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

echo "Create user1..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --createNewChannel
checkPreviousCommand "createNewChannel"

echo "Create user2..."
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar "Cacophony.jar" --createNewChannel

# Get the public keys for both users.
RESULT_STRING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --getPublicKey)
PUBLIC1=$(echo $RESULT_STRING | cut -d " " -f 14)
RESULT_STRING=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --getPublicKey)
PUBLIC2=$(echo $RESULT_STRING | cut -d " " -f 14)

echo "Start the interactive server, verify we can load a page, and initialize the cookies and XSRF token..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --run --port 8001 &
SERVER_PID=$!
waitForCacophonyStart 8001
INDEX=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET -L "http://127.0.0.1:8001/")
requireSubstring "$INDEX" "Cacophony - Static Index"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/server/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)

echo "Attach the status listener..."
mkfifo "$WS_STATUS.out" "$WS_STATUS.in" "$WS_STATUS.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8001/server/events/status" "event_api" "$WS_STATUS.out" "$WS_STATUS.in" "$WS_STATUS.clear" &
STATUS_PID=$!
# Wait for connect so that we know we will see the refresh.
cat "$WS_STATUS.out" > /dev/null

echo "Attach the followee refresh WebSocket..."
mkfifo "$WS_REFRESH.out" "$WS_REFRESH.in" "$WS_REFRESH.clear"
java -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8001/followee/events/refreshTime" "event_api" "$WS_REFRESH.out" "$WS_REFRESH.in" "$WS_REFRESH.clear" &
FOLLOWEE_REFRESH_PID=$!
cat "$WS_REFRESH.out" > /dev/null

echo "Make user1 follow user2 and verify an empty stream..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST "http://127.0.0.1:8001/followees/add/$PUBLIC2"
# Verify that we see the new followee reference created in the follow refresh time socket.
SAMPLE=$(cat "$WS_REFRESH.out")
echo -n "-ACK" > "$WS_REFRESH.in" && cat "$WS_REFRESH.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$PUBLIC2\",\"value\":0,\"isNewest\":true}"
# Note that we need to wait for the refresh to finish, since it is now asynchronous.
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
# Note that the first event we see is typically a create of key 2 but we will see key 1 if the start-up publish is unusually slow.
if [[ "$SAMPLE" =~ "{\"event\":\"create\",\"key\":1," ]];
then
	requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":1,\"value\":\"Publish IpfsFile("
	SAMPLE=$(cat "$WS_STATUS.out")
	echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
	requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":1,"
	SAMPLE=$(cat "$WS_STATUS.out")
	echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
fi
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":2,\"value\":\"Refresh IpfsKey($PUBLIC2)\",\"isNewest\":true}"
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":2,\"value\":null,\"isNewest\":false}"
# The followee refresh should similarly show that this has completed.
SAMPLE=$(cat "$WS_REFRESH.out")
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$PUBLIC2\",\"value\":"
echo -n "-ACK" > "$WS_REFRESH.in" && cat "$WS_REFRESH.clear" > /dev/null
# Verify that the post list is empty.
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[]"

echo "Verify that we can read the followee data from the cache..."
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/userInfo/$PUBLIC2")
# We always know the default state of the info so just verify it.
requireSubstring "$USER_INFO" "{\"name\":\"Unnamed\",\"description\":\"Description forthcoming\",\"userPicUrl\":\"http://127.0.0.1:8080/ipfs/QmXsfdKGurBGFfzyRjVQ5APrhC6JE8x3hRRm8kGfGWRA5V\",\"email\":null,\"website\":null,\"feature\":null}"

echo "Attach the followee post listener..."
mkfifo "$WS_ENTRIES.out" "$WS_ENTRIES.in" "$WS_ENTRIES.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8001/server/events/entries/$PUBLIC2" "event_api" "$WS_ENTRIES.out" "$WS_ENTRIES.in" "$WS_ENTRIES.clear" &
ENTRIES_PID=$!
cat "$WS_ENTRIES.out" > /dev/null

echo "Request a refresh of user2 and wait for the event to show up in status, that it is complete, then also verify an empty stream..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST "http://127.0.0.1:8001/followee/refresh/$PUBLIC2"
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":3,\"value\":\"Refresh IpfsKey($PUBLIC2)\",\"isNewest\":true}"
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":3,\"value\":null,\"isNewest\":false}"
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[]"
# We should also see this update the refresh time.
SAMPLE=$(cat "$WS_REFRESH.out")
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$PUBLIC2\",\"value\":"
echo -n "-ACK" > "$WS_REFRESH.in" && cat "$WS_REFRESH.clear" > /dev/null

echo "Make a post as user2..."
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --publishToThisChannel --name "post" --description "no description"
checkPreviousCommand "publishToThisChannel"

echo "Request a refresh of this user and wait for the event to show up in status, that it is complete, then also verify we see this element in the stream..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST "http://127.0.0.1:8001/followee/refresh/$PUBLIC2"
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":4,\"value\":\"Refresh IpfsKey($PUBLIC2)\",\"isNewest\":true}"
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":4,\"value\":null,\"isNewest\":false}"
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[\"Qm"

echo "Verify that we see the refresh in the followee socket..."
SAMPLE=$(cat "$WS_REFRESH.out")
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$PUBLIC2\",\"value\":"
echo -n "-ACK" > "$WS_REFRESH.in" && cat "$WS_REFRESH.clear" > /dev/null

echo "Verify that we see the new entry in the entry socket..."
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"special\",\"key\":\"Refreshing\",\"value\":null,\"isNewest\":false}"
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
while [[ ! "$SAMPLE" =~ "{\"event\":\"create\",\"key\":" ]]
do
	SAMPLE=$(cat "$WS_ENTRIES.out")
	echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
done
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"special\",\"key\":null,\"value\":null,\"isNewest\":false}"

echo "Test the add/remove of the followee..."
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[\""
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XDELETE "http://127.0.0.1:8001/followees/remove/$PUBLIC2"
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/postHashes/$PUBLIC2")
if [ "" != "$POST_LIST" ];
then
	exit 1
fi
# We should observe the delete of this record since we stopped following.
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"special\",\"key\":\"Refreshing\",\"value\":null,\"isNewest\":false}"
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"Qm"
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"special\",\"key\":null,\"value\":null,\"isNewest\":false}"
# We should also see the followee deleted from the refresh output.
SAMPLE=$(cat "$WS_REFRESH.out")
echo -n "-ACK" > "$WS_REFRESH.in" && cat "$WS_REFRESH.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$PUBLIC2\",\"value\":null,\"isNewest\":false}"

curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST "http://127.0.0.1:8001/followees/add/$PUBLIC2"
# Note that we need to wait for the refresh to finish, since it is now asynchronous.
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":5,\"value\":\"Refresh IpfsKey($PUBLIC2)\",\"isNewest\":true}"
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":5,\"value\":null,\"isNewest\":false}"
# Similarly, we need to read the refresh from the followee output.
SAMPLE=$(cat "$WS_REFRESH.out")
echo -n "-ACK" > "$WS_REFRESH.in" && cat "$WS_REFRESH.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$PUBLIC2\",\"value\":0,\"isNewest\":true}"
# We also want to wait for the refresh to complete, asynchronously.
SAMPLE=$(cat "$WS_REFRESH.out")
requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"$PUBLIC2\",\"value\":"
echo -n "-ACK" > "$WS_REFRESH.in" && cat "$WS_REFRESH.clear" > /dev/null

POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[\""

echo "Check asking for information about users, including invalid keys..."
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/unknownUser/BOGUS")
requireSubstring "$USER_INFO" "Invalid key: BOGUS"
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/unknownUser/$PUBLIC2")
requireSubstring "$USER_INFO" "{\"name\":\"Unnamed\",\"description\":\"Description forthcoming\",\"userPicUrl\":\"http://127.0.0.1:8080/ipfs/QmXsfdKGurBGFfzyRjVQ5APrhC6JE8x3hRRm8kGfGWRA5V\",\"email\":null,\"website\":null,\"feature\":null}"

echo "Check the manipulation of the recommended users"
RECOMMENDED_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/recommendedKeys/$PUBLIC1")
requireSubstring "$RECOMMENDED_KEYS" "[]"
# Add the other user.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XPOST "http://127.0.0.1:8001/home/recommend/add/$PUBLIC1/$PUBLIC2"
checkPreviousCommand "add to recommended"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XPOST "http://127.0.0.1:8001/home/recommend/add/$PUBLIC1/$PUBLIC2"
if [ $? != 22 ]; then
	exit 1
fi
RECOMMENDED_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/recommendedKeys/$PUBLIC1")
requireSubstring "$RECOMMENDED_KEYS" "[\"$PUBLIC2\"]"
# Wait for publish.
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":6,\"value\""
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":6,\"value\""
# Remove.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XDELETE "http://127.0.0.1:8001/home/recommend/remove/$PUBLIC1/$PUBLIC2"
checkPreviousCommand "remove from recommended"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XDELETE "http://127.0.0.1:8001/home/recommend/remove/$PUBLIC1/$PUBLIC2"
if [ $? != 22 ]; then
	exit 1
fi
RECOMMENDED_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/recommendedKeys/$PUBLIC1")
requireSubstring "$RECOMMENDED_KEYS" "[]"
# Wait for publish.
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":7,\"value\""
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":7,\"value\""

echo "Update description..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=name&DESCRIPTION=My%20description&EMAIL=&WEBSITE=http%3A%2F%2Fexample.com" "http://127.0.0.1:8001/home/userInfo/info/$PUBLIC1"
checkPreviousCommand "update description info"
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/userInfo/$PUBLIC1")
requireSubstring "$USER_INFO" "{\"name\":\"name\",\"description\":\"My description\",\"userPicUrl\":\"http://127.0.0.1:8080/ipfs/QmXsfdKGurBGFfzyRjVQ5APrhC6JE8x3hRRm8kGfGWRA5V\",\"email\":null,\"website\":\"http://example.com\",\"feature\":null}"
# Wait for publish.
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":8,\"value\""
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":8,\"value\""

NEW_URL=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: image/jpeg" --data "FAKE_IMAGE_DATA" "http://127.0.0.1:8001/home/userInfo/image/$PUBLIC1")
checkPreviousCommand "update description image"
requireSubstring "$NEW_URL" "http://127.0.0.1:8080/ipfs/QmQ3uiKi85stbB6owgnKbxpjbGixFJNfryc2rU7U51MqLd"
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/userInfo/$PUBLIC1")
requireSubstring "$USER_INFO" "{\"name\":\"name\",\"description\":\"My description\",\"userPicUrl\":\"http://127.0.0.1:8080/ipfs/QmQ3uiKi85stbB6owgnKbxpjbGixFJNfryc2rU7U51MqLd\",\"email\":null,\"website\":\"http://example.com\",\"feature\":null}"
# Wait for publish.
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":9,\"value\""
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":9,\"value\""


echo "Stop the server and wait for it to exit..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/server/stop"
wait $SERVER_PID
echo -n "-WAIT" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
echo -n "-WAIT" > "$WS_REFRESH.in" && cat "$WS_REFRESH.clear" > /dev/null
echo -n "-WAIT" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
wait $STATUS_PID
wait $FOLLOWEE_REFRESH_PID
wait $ENTRIES_PID

echo "Start the server again to verify that the on-disk data is correct"
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --run --port 8001 &
SERVER_PID=$!
waitForCacophonyStart 8001

# Make sure that our cookies are set.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8001/server/cookie

# We will verify that the followed user's post can be seen.
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8001/server/postHashes/$PUBLIC2")
requireSubstring "$POST_LIST" "[\""

echo "Stop the server and wait for it to exit..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8001/server/stop"


echo "Check that our upload utility can handle large uploads..."
# Create a 10 MiB file and upload it with an 8 MiB heap.
createBinaryFile "/tmp/zero" 10240
HASH=$(cat /tmp/zero | java -Xmx8m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.StreamUploader /ip4/127.0.0.1/tcp/5001)
requireSubstring "$HASH" "QmZ34B7UQGcVB7Fp2ZVZnVmK2DgNs9rXTsXSQ66b5cAwYW"

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
