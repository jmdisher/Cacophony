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

COOKIES1=/tmp/cookies1
DRAFTS_DIR=/tmp/drafts

rm -f "$COOKIES1"
rm -rf "$DRAFTS_DIR"


# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar
mkdir "$DRAFTS_DIR"

echo "Start the interactive server..."
CACOPHONY_ENABLE_FAKE_SYSTEM="$DRAFTS_DIR" java -Xmx1g -jar "Cacophony.jar" --run &
SERVER_PID=$!
waitForHttpStart 8000
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST "http://127.0.0.1:8000/server/cookie"
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)
PUBLIC_KEY=$(getPublicKey "$COOKIES1" "http://127.0.0.1:8000")
# We know the hard-coded key in this mode.
requireSubstring "$PUBLIC_KEY" "z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3Qx1"

echo "Make 12 posts and verify that we only see 10 in the entries socket..."
for N in {1..12}; 
do
	CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new/NONE)
	# We need to parse out the ID (look for '{"id":2107961294,')
	ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
	PUBLISH_ID=$(echo $ID_PARSE)
	# The draft initial contents are currently being initialized from the time so make sure we change it.
	curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Post%20$N" http://127.0.0.1:8000/draft/$PUBLISH_ID
	POST_CIDS[$N]=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/TEXT_ONLY)
done

echo "Connect the entries socket..."
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN" "ws://127.0.0.1:8000/server/events/entries/$PUBLIC_KEY" 9000 &
ENTRIES_PID=$!
waitForHttpStart 9000

echo "We expect to see the last 10 (but we can't verify the others _don't_ appear - although the WS util will hang on disconnect if there are unread messages)."
INDEX=0
for N in {12..3}; 
do
	SAMPLE=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX 2> /dev/null)
	INDEX=$((INDEX + 1))
	requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"${POST_CIDS[$N]}\",\"value\":null,\"isNewest\":false}"
done

echo "Request more and verify we see the other 2."
curl -XPOST -H  "Content-Type: text/plain" --data "COMMAND_SCROLL_BACK" http://127.0.0.1:9000/send 2> /dev/null
for N in {2..1}; 
do
	SAMPLE=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX 2> /dev/null)
	INDEX=$((INDEX + 1))
	requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"${POST_CIDS[$N]}\",\"value\":null,\"isNewest\":false}"
done
# Note that this will also see the fake entry for the "home user" so read that, too.
SAMPLE=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$INDEX 2> /dev/null)
INDEX=$((INDEX + 1))
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\""
requireSubstring "$SAMPLE" "\",\"value\":null,\"isNewest\":false}"

echo "Connect the combined socket and do a similar verification..."
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN" "ws://127.0.0.1:8000/server/events/combined/entries" 9001 &
COMBINED_PID=$!
waitForHttpStart 9001

# Similarly, we should see the last 10.
INDEX=0
for N in {12..3}; 
do
	SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX 2> /dev/null)
	INDEX=$((INDEX + 1))
	requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"${POST_CIDS[$N]}\",\"value\":null,\"isNewest\":false}"
done

# Scroll back and see the others (they will all be present since we haven't restarted - the combined per-user limit only applies to start-up).
curl -XPOST -H  "Content-Type: text/plain" --data "COMMAND_SCROLL_BACK" http://127.0.0.1:9001/send 2> /dev/null
for N in {2..1}; 
do
	SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX 2> /dev/null)
	INDEX=$((INDEX + 1))
	requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"${POST_CIDS[$N]}\",\"value\":null,\"isNewest\":false}"
done
# Note that this will also see the fake entry for the "home user" so read that, too.
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX 2> /dev/null)
INDEX=$((INDEX + 1))
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\""
requireSubstring "$SAMPLE" "\",\"value\":null,\"isNewest\":false}"
# Note that this will also see the fake entry for the "other user" so read that, too.
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$INDEX 2> /dev/null)
INDEX=$((INDEX + 1))
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\""
requireSubstring "$SAMPLE" "\",\"value\":null,\"isNewest\":false}"

# Check that the keys captured by the WebSocket utility are expected.
KEY_ARRAY=$(curl -XGET http://127.0.0.1:9000/keys 2> /dev/null)
COUNT=$(echo $KEY_ARRAY | grep -o "\"Qm" | wc -l)
requireSubstring "$COUNT" "13"


echo "Shut-down and wait for sockets to close..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8000/server/stop"
wait $SERVER_PID
wait $ENTRIES_PID
wait $COMBINED_PID


echo -e "\033[32;40mSUCCESS!\033[0m"
