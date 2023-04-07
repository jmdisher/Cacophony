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
STATUS_OUTPUT=/tmp/status_output
STATUS_INPUT=/tmp/status_input
ENTRIES_OUTPUT=/tmp/entries_output
ENTRIES_INPUT=/tmp/entries_input
DRAFTS_DIR=/tmp/drafts

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"
rm -f "$STATUS_OUTPUT" "$STATUS_INPUT"
rm -f "$ENTRIES_OUTPUT" "$ENTRIES_INPUT"
rm -rf "$DRAFTS_DIR"


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
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)
PUBLIC_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/publicKey")
# We know the hard-coded key in this mode.
requireSubstring "$PUBLIC_KEY" "z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3Qx1"

mkfifo "$STATUS_INPUT"
mkfifo "$STATUS_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/backgroundStatus" "event_api" "$STATUS_INPUT" "$STATUS_OUTPUT" &
STATUS_PID=$!
# Wait for connect.
cat "$STATUS_OUTPUT" > /dev/null

echo "Create and publish a draft..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/createDraft)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLISH_ID/TEXT_ONLY
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/wait/publish

# Observe the republish.
SAMPLE=$(cat "$STATUS_OUTPUT")
echo -n "-ACK" > "$STATUS_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":2,\"value\":\"Publish IpfsFile(Qm"
SAMPLE=$(cat "$STATUS_OUTPUT")
echo -n "-ACK" > "$STATUS_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":2,\"value\":null,\"isNewest\":false}"

mkfifo "$ENTRIES_INPUT"
mkfifo "$ENTRIES_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/user/entries/$PUBLIC_KEY" "event_api" "$ENTRIES_INPUT" "$ENTRIES_OUTPUT" &
ENTRIES_PID=$!
cat "$ENTRIES_OUTPUT" > /dev/null
SAMPLE=$(cat "$ENTRIES_OUTPUT")
echo -n "-ACK" > "$ENTRIES_INPUT"

POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC_KEY")
POST_ID=$(echo "$POST_LIST" | cut -d "\"" -f 2)
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postStruct/$POST_ID")
requireSubstring "$POST_STRUCT" "{\"name\":\"New Draft - "

# Shutdown.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8000/stop"
wait $SERVER_PID
echo -n "-WAIT" > "$STATUS_INPUT"
wait $STATUS_PID
echo -n "-WAIT" > "$ENTRIES_INPUT"
wait $ENTRIES_PID


echo -e "\033[32;40mSUCCESS!\033[0m"
