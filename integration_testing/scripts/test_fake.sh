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
DRAFTS_DIR=/tmp/drafts

WS_STATUS=/tmp/status
WS_ENTRIES=/tmp/entries

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"
rm -rf "$DRAFTS_DIR"

rm -f "$WS_STATUS".*
rm -f "$WS_ENTRIES".*


# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar
mkdir "$DRAFTS_DIR"

echo "Check the info from the basic interface..."
HELP=$(CACOPHONY_ENABLE_FAKE_SYSTEM="$DRAFTS_DIR" java -Xmx1g -jar "Cacophony.jar" --help)
requireSubstring "$HELP" "Description: Attaches a file to the new post being made."
FOLLOWEES=$(CACOPHONY_ENABLE_FAKE_SYSTEM="$DRAFTS_DIR" java -Xmx1g -jar "Cacophony.jar" --listFollowees)
requireSubstring "$FOLLOWEES" "Following: z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV"
OUR_DESCRIPTION=$(CACOPHONY_ENABLE_FAKE_SYSTEM="$DRAFTS_DIR" java -Xmx1g -jar "Cacophony.jar" --readDescription)
requireSubstring "$OUR_DESCRIPTION" "Name: us"
THEIR_DESCRIPTION=$(CACOPHONY_ENABLE_FAKE_SYSTEM="$DRAFTS_DIR" java -Xmx1g -jar "Cacophony.jar" --readDescription --publicKey z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV)
requireSubstring "$THEIR_DESCRIPTION" "Name: them"

echo "Start the interactive server..."
CACOPHONY_ENABLE_FAKE_SYSTEM="$DRAFTS_DIR" java -Xmx1g -jar "Cacophony.jar" --run &
SERVER_PID=$!
sleep 5
INDEX=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET -L "http://127.0.0.1:8000/")
requireSubstring "$INDEX" "Cacophony - Static Index"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/server/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)
PUBLIC_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/home/publicKey")
# We know the hard-coded key in this mode.
requireSubstring "$PUBLIC_KEY" "z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3Qx1"

mkfifo "$WS_STATUS.out" "$WS_STATUS.in" "$WS_STATUS.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/server/events/status" "event_api" "$WS_STATUS.out" "$WS_STATUS.in" "$WS_STATUS.clear" &
STATUS_PID=$!
# Wait for connect.
cat "$WS_STATUS.out" > /dev/null

echo "Create and publish a draft..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new/NONE)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/TEXT_ONLY
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/waitPublish

# Observe the republish.
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":2,\"value\":\"Publish IpfsFile(Qm"
SAMPLE=$(cat "$WS_STATUS.out")
echo -n "-ACK" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":2,\"value\":null,\"isNewest\":false}"

mkfifo "$WS_ENTRIES.out" "$WS_ENTRIES.in" "$WS_ENTRIES.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/server/events/entries/$PUBLIC_KEY" "event_api" "$WS_ENTRIES.out" "$WS_ENTRIES.in" "$WS_ENTRIES.clear" &
ENTRIES_PID=$!
cat "$WS_ENTRIES.out" > /dev/null
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null

POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postHashes/$PUBLIC_KEY")
POST_ID=$(echo "$POST_LIST" | cut -d "\"" -f 2)
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postStruct/$POST_ID/OPTIONAL")
requireSubstring "$POST_STRUCT" "{\"name\":\"New Draft - "

# Shutdown.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8000/server/stop"
wait $SERVER_PID
echo -n "-WAIT" > "$WS_STATUS.in" && cat "$WS_STATUS.clear" > /dev/null
wait $STATUS_PID
echo -n "-WAIT" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
wait $ENTRIES_PID


echo -e "\033[32;40mSUCCESS!\033[0m"
