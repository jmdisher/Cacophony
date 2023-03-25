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
ENTRIES_OUTPUT=/tmp/entries_output
ENTRIES_INPUT=/tmp/entries_input
COMBINED_OUTPUT=/tmp/combined_output
COMBINED_INPUT=/tmp/combined_input
DRAFTS_DIR=/tmp/drafts

rm -f "$COOKIES1"
rm -f "$ENTRIES_OUTPUT" "$ENTRIES_INPUT"
rm -f "$COMBINED_OUTPUT" "$COMBINED_INPUT"
rm -rf "$DRAFTS_DIR"


# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar
mkdir "$DRAFTS_DIR"

echo "Start the interactive server..."
CACOPHONY_ENABLE_FAKE_SYSTEM="$DRAFTS_DIR" java -Xmx1g -jar "Cacophony.jar" --run &
SERVER_PID=$!
sleep 5
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)
PUBLIC_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/publicKey")
# We know the hard-coded key in this mode.
requireSubstring "$PUBLIC_KEY" "z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3Qx1"

echo "Make 12 posts and verify that we only see 10 in the entries socket..."
for N in {1..12}; 
do
	CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/createDraft)
	# We need to parse out the ID (look for '{"id":2107961294,')
	ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
	PUBLISH_ID=$(echo $ID_PARSE)
	# The draft initial contents are currently being initialized from the time so make sure we change it.
	curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Post%20$N" http://127.0.0.1:8000/draft/$PUBLISH_ID
	curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLISH_ID/TEXT_ONLY
done

echo "Connect the entries socket..."
mkfifo "$ENTRIES_INPUT"
mkfifo "$ENTRIES_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/user/entries/$PUBLIC_KEY" "event_api" "$ENTRIES_INPUT" "$ENTRIES_OUTPUT" &
ENTRIES_PID=$!
cat "$ENTRIES_OUTPUT" > /dev/null

echo "We expect to see the last 10 (but we can't verify the others _don't_ appear - although the WS util will hang on disconnect if there are unread messages)."
for N in {12..3}; 
do
	SAMPLE=$(cat "$ENTRIES_OUTPUT")
	echo -n "-ACK" > "$ENTRIES_INPUT"
	requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\""
	requireSubstring "$SAMPLE" "\",\"value\":null,\"isNewest\":false}"
done

echo "Request more and verify we see the other 2."
echo -n "COMMAND_SCROLL_BACK" > "$ENTRIES_INPUT"
for N in {2..1}; 
do
	SAMPLE=$(cat "$ENTRIES_OUTPUT")
	echo -n "-ACK" > "$ENTRIES_INPUT"
	requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\""
	requireSubstring "$SAMPLE" "\",\"value\":null,\"isNewest\":false}"
done

echo "Connect the combined socket and do a similar verification..."
mkfifo "$COMBINED_INPUT"
mkfifo "$COMBINED_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/combined/entries" "event_api" "$COMBINED_INPUT" "$COMBINED_OUTPUT" &
COMBINED_PID=$!
cat "$COMBINED_OUTPUT" > /dev/null

# Similarly, we should see the last 10.
for N in {12..3}; 
do
	SAMPLE=$(cat "$COMBINED_OUTPUT")
	echo -n "-ACK" > "$COMBINED_INPUT"
	requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\""
	requireSubstring "$SAMPLE" "\",\"value\":null,\"isNewest\":false}"
done

# Scroll back and see the others (they will all be present since we haven't restarted - the combined per-user limit only applies to start-up).
echo -n "COMMAND_SCROLL_BACK" > "$COMBINED_INPUT"
for N in {2..1}; 
do
	SAMPLE=$(cat "$COMBINED_OUTPUT")
	echo -n "-ACK" > "$COMBINED_INPUT"
	requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\""
	requireSubstring "$SAMPLE" "\",\"value\":null,\"isNewest\":false}"
done
# Note that this will also see the fake entry for the "other user" so read that, too.
SAMPLE=$(cat "$COMBINED_OUTPUT")
echo -n "-ACK" > "$COMBINED_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\""
requireSubstring "$SAMPLE" "\",\"value\":null,\"isNewest\":false}"


echo "We will just kill the background process instead of creating the status socket."
kill "$SERVER_PID"
wait $SERVER_PID
echo -n "-WAIT" > "$ENTRIES_INPUT"
wait $ENTRIES_PID
echo -n "-WAIT" > "$COMBINED_INPUT"
wait $COMBINED_PID


echo -e "\033[32;40mSUCCESS!\033[0m"
