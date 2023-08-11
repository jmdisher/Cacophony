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

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"
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
waitForHttpStart 8000
INDEX=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET -L "http://127.0.0.1:8000/")
requireSubstring "$INDEX" "Cacophony - Static Index"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/server/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)
PUBLIC_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/home/publicKey")
# We know the hard-coded key in this mode.
requireSubstring "$PUBLIC_KEY" "z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3Qx1"

java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN" "ws://127.0.0.1:8000/server/events/status" event_api 9000 &
STATUS_PID=$!
waitForHttpStart 9000

echo "Create and publish a draft..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new/NONE)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
CID=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/TEXT_ONLY)
requireSubstring "$CID" "Qm"

# Observe the republish.
SAMPLE=$(curl -XGET http://127.0.0.1:9000/waitAndGet/0 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":2,\"value\":\"Publish IpfsFile(Qm"
SAMPLE=$(curl -XGET http://127.0.0.1:9000/waitAndGet/1 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":2,\"value\":null,\"isNewest\":false}"

java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN" "ws://127.0.0.1:8000/server/events/entries/$PUBLIC_KEY" event_api 9001 &
ENTRIES_PID=$!
waitForHttpStart 9001
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/0 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"Qm"
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/1 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"Qm"

POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postHashes/$PUBLIC_KEY")
# This list will have 2 entries:  The fake one and the one we just created, so skip to the second (field 4).
POST_ID=$(echo "$POST_LIST" | cut -d "\"" -f 4)
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postStruct/$POST_ID/OPTIONAL")
requireSubstring "$POST_STRUCT" "{\"name\":\"New Draft - "

# Shutdown.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8000/server/stop"
wait $SERVER_PID
wait $STATUS_PID
wait $ENTRIES_PID


echo -e "\033[32;40mSUCCESS!\033[0m"
