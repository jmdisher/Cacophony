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
FAIL_PROCESS_FIFO=/tmp/fail_fifo

WS_STATUS2=/tmp/status2
WS_PROCESSING1=/tmp/processing1
WS_PROCESSING2=/tmp/processing2
WS_PROCESSING3=/tmp/processing3
WS_EXISTING=/tmp/existing

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"

rm -f "$WS_STATUS2".*
rm -f "$WS_PROCESSING1".*
rm -f "$WS_PROCESSING2".*
rm -f "$WS_PROCESSING3".*
rm -f "$WS_EXISTING".*


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

echo "Creating Cacophony instance..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --createNewChannel
checkPreviousCommand "createNewChannel"

echo "Start the interactive server..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --run --commandSelection DANGEROUS &
SERVER_PID=$!
waitForHttpStart 8000

echo "Make sure that we can access static files..."
INDEX=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET -L "http://127.0.0.1:8000/")
requireSubstring "$INDEX" "Cacophony - Index"

echo "Requesting creation of XSRF token..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/server/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)

echo "Now that we have verified that the server is up, start listening to status events..."
# We will open 2 connections to verify that concurrent connections are ok but we will also use one as a pipe, allowing us to precisely observe events, and the other one just as a file, so we can verify it ends up with the same events, at the end.  In theory, these could mismatch but that will probably never be observed due to the relative cost of a refresh versus sending a WebSocket message.
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN" "ws://127.0.0.1:8000/server/events/status" 9000 &
STATUS_PID1=$!
waitForHttpStart 9000
# This second connection will use WebSocketUtility since it is just writing to an output file to test concurrent connections.
touch "$WS_STATUS2.out"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" DRAIN "ws://127.0.0.1:8000/server/events/status" "event_api" "$WS_STATUS2.out" &
STATUS_PID2=$!

echo "Get the default video config..."
VIDEO_CONFIG=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/server/videoConfig)
requireSubstring "$VIDEO_CONFIG" "ffmpeg"

echo "Get the empty list of drafts..."
echo "CALLING curl --cookie $COOKIES1 --cookie-jar $COOKIES1 --no-progress-meter -XGET http://127.0.0.1:8000/allDrafts/all"
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/allDrafts/all)
requireSubstring "$DRAFTS" "[]"

echo "Verify that even fetching the drafts requires the XSRF token..."
curl --fail --no-progress-meter -XGET http://127.0.0.1:8000/allDrafts/all >& /dev/null
if [ "22" != "$?" ];
then
	echo "Expected failure"
	exit 1
fi

echo "Check that we can read our public key"
PUBLIC_KEY=$(getPublicKey "$COOKIES1" "http://127.0.0.1:8000")
# (we only know that the key starts with "z".
requireSubstring "$PUBLIC_KEY" "z"

echo "Attach the followee post listener..."
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketToRestUtility "$XSRF_TOKEN" "ws://127.0.0.1:8000/server/events/entries/$PUBLIC_KEY" 9001 &
ENTRIES_PID=$!
waitForHttpStart 9001

echo "Create a new draft..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new/NONE)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
ID=$(echo $ID_PARSE)
echo "...working with draft $ID"

echo "Verify that we can read the draft..."
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"title\":\"New Draft - $ID\""

echo "Verify that we can see the draft in the list..."
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/allDrafts/all)
requireSubstring "$DRAFTS" "\"title\":\"New Draft - $ID\""

echo "Update the title, description, then thumbnail..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Updated%20Title&DESCRIPTION=" http://127.0.0.1:8000/draft/$ID
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"title\":\"Updated Title\""
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: image/jpeg" --data "FAKE_IMAGE_DATA" http://127.0.0.1:8000/draft/thumb/$ID/5/6/jpeg
checkPreviousCommand "POST /draft/thumb"
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"thumbnail\":{\"mime\":\"image/jpeg\",\"height\":5,\"width\":6,\"byteSize\":15}"

echo "Upload the video for the draft..."
echo "aXbXcXdXe" | java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" SEND "ws://127.0.0.1:8000/draft/originalVideo/upload/$ID/1/2/webm" video

echo "Verify that we can cancel a video processing operation..."
rm -f "$FAIL_PROCESS_FIFO"
mkfifo "$FAIL_PROCESS_FIFO"
mkfifo "$WS_PROCESSING1.out" "$WS_PROCESSING1.in" "$WS_PROCESSING1.clear"
# Note that the value of FAIL_PROCESS_FIFO is hard-coded in this process:  "%2Ftmp%2Ffail_fifo"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/draft/processedVideo/process/$ID/cat%20%2Ftmp%2Ffail_fifo" "event_api" "/dev/null" "$WS_PROCESSING1.in" "$WS_PROCESSING1.clear" &
FAIL_PID=$!
FAIL_PROC_COUNT=$(ps auxww | grep fail | grep --count fifo)
if [ "$FAIL_PROC_COUNT" -ne 1 ]; then
	echo "Failed to find the stuck process with $FAIL_PROC_COUNT matches"
	kill -9 $FAIL_PID
	exit 1
fi
echo "Close the processing channel to cancel it"
echo -n "-CLOSE" > "$WS_PROCESSING1.in" && cat "$WS_PROCESSING1.clear" > /dev/null
wait $FAIL_PID
# We expect that the process is still running.
FAIL_PROC_COUNT=$(ps auxww | grep fail | grep --count fifo)
if [ "$FAIL_PROC_COUNT" -ne 1 ]; then
	echo "Failed to find the stuck process, after closing connection, with $FAIL_PROC_COUNT matches"
	exit 1
fi

# Do the re-open.
echo "Do the re-open..."
mkfifo "$WS_EXISTING.out" "$WS_EXISTING.in" "$WS_EXISTING.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/draft/processedVideo/reconnect/$ID" "event_api" "$WS_EXISTING.out" "$WS_EXISTING.in" "$WS_EXISTING.clear" &
FAIL_PID=$!
# Wait for connect.
cat "$WS_EXISTING.out" > /dev/null

# We should see the replay of the creation of inputBytes key.
SINGLE_EVENT=$(cat "$WS_EXISTING.out")
echo -n "-ACK" > "$WS_EXISTING.in" && cat "$WS_EXISTING.clear" > /dev/null
requireSubstring "$SINGLE_EVENT" "{\"event\":\"create\",\"key\":\"inputBytes\",\"value\":"
# Do the cancel (VideoProcessorCallbackHandler.COMMAND_CANCEL_PROCESSING) and wait for the close.
echo "Cancel the processing..."
echo -n "COMMAND_CANCEL_PROCESSING" > "$WS_EXISTING.in" && cat "$WS_EXISTING.clear" > /dev/null
# We should see this force-stop as a delete of that input bytes key followed by a create and delete pair of the output bytes set to -1.
SINGLE_EVENT=$(cat "$WS_EXISTING.out")
echo -n "-ACK" > "$WS_EXISTING.in" && cat "$WS_EXISTING.clear" > /dev/null
requireSubstring "$SINGLE_EVENT" "{\"event\":\"delete\",\"key\":\"inputBytes\",\"value\":null,\"isNewest\":false}"
SINGLE_EVENT=$(cat "$WS_EXISTING.out")
echo -n "-ACK" > "$WS_EXISTING.in" && cat "$WS_EXISTING.clear" > /dev/null
requireSubstring "$SINGLE_EVENT" "{\"event\":\"create\",\"key\":\"outputBytes\",\"value\":-1,\"isNewest\":true}"
SINGLE_EVENT=$(cat "$WS_EXISTING.out")
echo -n "-ACK" > "$WS_EXISTING.in" && cat "$WS_EXISTING.clear" > /dev/null
requireSubstring "$SINGLE_EVENT" "{\"event\":\"delete\",\"key\":\"outputBytes\",\"value\":null,\"isNewest\":false}"
# Verify that the process has terminated.
FAIL_PROC_COUNT=$(ps auxww | grep fail | grep --count fifo)
if [ "$FAIL_PROC_COUNT" -ne 0 ]; then
	echo "Stuck processes remaining: $FAIL_PROC_COUNT"
	exit 1
fi
echo -n "-WAIT" > "$WS_EXISTING.in" && cat "$WS_EXISTING.clear" > /dev/null
wait $FAIL_PID
echo "Verify that the draft shows no processed video..."
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" ",\"originalVideo\":{\"mime\":\"video/webm\",\"height\":1,\"width\":2,\"byteSize\":10},\"processedVideo\":null,\"audio\":null,\"replyTo\":null}"

echo "Verify that we can make sense of the error when the command isn't found (the default is ffmpeg, which not everyone has)..."
mkfifo "$WS_PROCESSING2.out" "$WS_PROCESSING2.in" "$WS_PROCESSING2.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/draft/processedVideo/process/$ID/bogusProgramName" "event_api" "$WS_PROCESSING2.out" "$WS_PROCESSING2.in" "$WS_PROCESSING2.clear" &
SAMPLE_PID=$!
# Wait for connect and then wait for disconnect - that is what happens on the error (there will be no events).
cat "$WS_PROCESSING2.out" > /dev/null
echo -n "-WAIT" > "$WS_PROCESSING2.in" && cat "$WS_PROCESSING2.clear" > /dev/null
wait $SAMPLE_PID

echo "Process the uploaded video..."
mkfifo "$WS_PROCESSING3.out" "$WS_PROCESSING3.in" "$WS_PROCESSING3.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/draft/processedVideo/process/$ID/cut%20-d%20X%20-f%203" "event_api" "$WS_PROCESSING3.out" "$WS_PROCESSING3.in" "$WS_PROCESSING3.clear" &
SAMPLE_PID=$!
# Wait for connect.
cat "$WS_PROCESSING3.out" > /dev/null
# We expect every message to be an "event" - we need to see the "create", zero or more "update", and then a single "delete".
SAMPLE=$(cat "$WS_PROCESSING3.out")
echo -n "-ACK" > "$WS_PROCESSING3.in" && cat "$WS_PROCESSING3.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"inputBytes\",\"value\":"
SAMPLE=$(cat "$WS_PROCESSING3.out")
echo -n "-ACK" > "$WS_PROCESSING3.in" && cat "$WS_PROCESSING3.clear" > /dev/null
while [ "$SAMPLE" != "{\"event\":\"delete\",\"key\":\"inputBytes\",\"value\":null,\"isNewest\":false}" ]
do
	requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"inputBytes\",\"value\":"
	SAMPLE=$(cat "$WS_PROCESSING3.out")
	echo -n "-ACK" > "$WS_PROCESSING3.in" && cat "$WS_PROCESSING3.clear" > /dev/null
done
# At the end, we see the final update with processed size (2 bytes - "c\n").
SAMPLE=$(cat "$WS_PROCESSING3.out")
echo -n "-ACK" > "$WS_PROCESSING3.in" && cat "$WS_PROCESSING3.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"outputBytes\",\"value\":2,\"isNewest\":true}"
SAMPLE=$(cat "$WS_PROCESSING3.out")
echo -n "-ACK" > "$WS_PROCESSING3.in" && cat "$WS_PROCESSING3.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"outputBytes\",\"value\":null,\"isNewest\":false}"
echo -n "-WAIT" > "$WS_PROCESSING3.in" && cat "$WS_PROCESSING3.clear" > /dev/null
wait $SAMPLE_PID

ORIGINAL_VIDEO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET "http://127.0.0.1:8000/draft/originalVideo/$ID")
if [ "aXbXcXdXe" != "$ORIGINAL_VIDEO" ];
then
	echo "Original video not expected: $ORIGINAL_VIDEO"
	exit 1
fi
PROCESSED_VIDEO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET "http://127.0.0.1:8000/draft/processedVideo/$ID")
if [ "c" != "$PROCESSED_VIDEO" ];
then
	echo "Processed video not expected: $PROCESSED_VIDEO"
	exit 1
fi

echo "Upload some audio, as well..."
echo "AUDIO_DATA" | java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" SEND "ws://127.0.0.1:8000/draft/audio/upload/$ID/ogg" audio
ORIGINAL_AUDIO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET "http://127.0.0.1:8000/draft/audio/$ID")
if [ "AUDIO_DATA" != "$ORIGINAL_AUDIO" ];
then
	echo "Original audio not expected: $ORIGINAL_AUDIO"
	exit 1
fi

echo "Verify that the draft information is correct..."
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"title\":\"Updated Title\",\"description\":\"\",\"thumbnail\":{\"mime\":\"image/jpeg\",\"height\":5,\"width\":6,\"byteSize\":15},\"discussionUrl\":\"\",\"originalVideo\":{\"mime\":\"video/webm\",\"height\":1,\"width\":2,\"byteSize\":10},\"processedVideo\":{\"mime\":\"video/webm\",\"height\":1,\"width\":2,\"byteSize\":2},\"audio\":{\"mime\":\"audio/ogg\",\"height\":0,\"width\":0,\"byteSize\":11},\"replyTo\":null}"

echo "Verify that we can delete the individual draft files..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XDELETE "http://127.0.0.1:8000/draft/originalVideo/$ID"
checkPreviousCommand "DELETE originalVideo"
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"originalVideo\":null"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XDELETE "http://127.0.0.1:8000/draft/processedVideo/$ID"
checkPreviousCommand "DELETE processedVideo"
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"processedVideo\":null"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XDELETE "http://127.0.0.1:8000/draft/thumb/$ID"
checkPreviousCommand "DELETE thumbnail"
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"thumbnail\":null"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XDELETE "http://127.0.0.1:8000/draft/audio/$ID"
checkPreviousCommand "DELETE thumbnail"
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"audio\":null"

echo "Verify that we can delete the draft and see an empty list..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XDELETE http://127.0.0.1:8000/draft/$ID
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/allDrafts/all)
requireSubstring "$DRAFTS" "[]"
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
if [ ! -z "$DRAFT" ]; then
	echo "Draft not empty: $DRAFT"
	exit 1
fi

echo "Create a new draft and publish it..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new/NONE)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
echo "...working with draft $PUBLISH_ID"
# First, make sure that giving an invalid type argument has no effect.
ERROR=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/BOGUS)
requireSubstring "$ERROR" "Invalid draft type: \"BOGUS\""
# Now, do it correctly.
CID=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/VIDEO)
requireSubstring "$CID" "Qm"

echo "Verify that we see the new entry in the entry socket..."
ENTRIES_INDEX=0
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$ENTRIES_INDEX 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$CID\",\"value\":null,\"isNewest\":true}"

# We will verify that this is in the pipe we are reading from the WebSocket (note that we may sometimes see event "1" from the start-up publish, so just skip that one in this case).
STATUS_INDEX1=0
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
if [[ "$STATUS_EVENT" =~ "\"event\":\"create\",\"key\":1," ]]; then
	STATUS_INDEX1=$((STATUS_INDEX1 + 1))
	STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
	STATUS_INDEX1=$((STATUS_INDEX1 + 1))
	STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
fi
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":2,\"value\":\"Publish IpfsFile("
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":2,\"value\":null"

echo "Verify that it is not in the list..."
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/allDrafts/all)
requireSubstring "$DRAFTS" "[]"

echo "Check the user data for this user"
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/unknownUser/$PUBLIC_KEY")
requireSubstring "$USER_INFO" "\"description\":\"Description forthcoming\""

echo "Check the list of posts for this user"
POST_LIST=$(curl -XGET http://127.0.0.1:9001/keys 2> /dev/null)
requireSubstring "$POST_LIST" "[\"$CID\"]"

echo "Check the list of recommended keys for this user"
RECOMMENDED_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/recommendedKeys/$PUBLIC_KEY")
requireSubstring "$RECOMMENDED_KEYS" "[]"

echo "Read the new post through the REST interface"
POST_ID=$(echo "$POST_LIST" | cut -d "\"" -f 2)
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postStruct/$POST_ID/OPTIONAL")
requireSubstring "$POST_STRUCT" ",\"publisherKey\":\"$PUBLIC_KEY\",\"replyTo\":null,\"hasDataToCache\":false,\"thumbnailUrl\":null,\"videoUrl\":null,\"audioUrl\":null}"

echo "Edit the post and make sure that we see the updates in both sockets and the post list..."
OLD_POST_ID="$POST_ID"
POST_ID=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Edit%20Title&DESCRIPTION=Has%20Changed" http://127.0.0.1:8000/home/post/edit/$PUBLIC_KEY/$POST_ID)
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postStruct/$POST_ID/OPTIONAL")
requireSubstring "$POST_STRUCT" "{\"name\":\"Edit Title\",\"description\":\"Has Changed\",\"publishedSecondsUtc\":"
ENTRIES_INDEX=$((ENTRIES_INDEX + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$ENTRIES_INDEX 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$OLD_POST_ID\",\"value\":null,\"isNewest\":false}"
ENTRIES_INDEX=$((ENTRIES_INDEX + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$ENTRIES_INDEX 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$POST_ID\",\"value\":null,\"isNewest\":true}"
POST_LIST=$(curl --no-progress-meter -XGET "http://127.0.0.1:9001/keys")
requireSubstring "$POST_LIST" "[\"$POST_ID\"]"
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":3,\"value\":\"Publish IpfsFile("
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":3,\"value\":null,\"isNewest\":false}"

echo "Publish a new entry which is a response to $POST_ID and verify that we see the correct struct field..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new/$POST_ID)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
REPLY_HASH=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/TEXT_ONLY)
requireSubstring "$REPLY_HASH" "Qm"

# Check for this in the WebSockets.
ENTRIES_INDEX=$((ENTRIES_INDEX + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$ENTRIES_INDEX 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$REPLY_HASH\",\"value\":null,\"isNewest\":true}"
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":4,\"value\":\"Publish IpfsFile("
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":4,\"value\":null,\"isNewest\":false}"
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postStruct/$REPLY_HASH/OPTIONAL")
requireSubstring "$POST_STRUCT" ",\"publisherKey\":\"$PUBLIC_KEY\",\"replyTo\":\"$POST_ID\",\"hasDataToCache\":false,\"thumbnailUrl\":null,\"videoUrl\":null,\"audioUrl\":null}"
# Find the hash of the new post.
POST_LIST=$(curl --no-progress-meter -XGET "http://127.0.0.1:9001/keys")
requireSubstring "$POST_LIST" "[\"$POST_ID\",\"$REPLY_HASH\"]"

echo "Create an audio post, publish it, and make sure we can see it..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new/NONE)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
echo "AUDIO_DATA" | java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" SEND "ws://127.0.0.1:8000/draft/audio/upload/$PUBLISH_ID/ogg" audio
AUDIO_CID=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/AUDIO)
requireSubstring "$AUDIO_CID" "Qm"

# Check that we see this in the output events.
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":5,\"value\":\"Publish IpfsFile("
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":5,\"value\":null"

echo "Verify that we see the new entry in the entry socket..."
ENTRIES_INDEX=$((ENTRIES_INDEX + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$ENTRIES_INDEX 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$AUDIO_CID\",\"value\":null,\"isNewest\":true}"
POST_LIST=$(curl --no-progress-meter -XGET "http://127.0.0.1:9001/keys")
requireSubstring "$POST_LIST" "[\"$POST_ID\",\"$REPLY_HASH\",\"$AUDIO_CID\"]"
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postStruct/$AUDIO_CID/OPTIONAL")
requireSubstring "$POST_STRUCT" ",\"publisherKey\":\"$PUBLIC_KEY\",\"replyTo\":null,\"hasDataToCache\":false,\"thumbnailUrl\":null,\"videoUrl\":null,\"audioUrl\":\"http://127.0.0.1:8080/ipfs/QmQyT5aRrJazL9T3AASkpM8AdS73a6eBGexa7W4GuXbMvJ\"}"

echo "See what happens if we add this post to our favourites..."
FAVOURITES_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/favourites/list")
requireSubstring "$FAVOURITES_LIST" "[]"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XPOST "http://127.0.0.1:8000/favourites/add/$AUDIO_CID"
FAVOURITES_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/favourites/list")
requireSubstring "$FAVOURITES_LIST" "[\"$AUDIO_CID\"]"
# Check that we see this in the cache sizing data.
STATS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/status")
requireSubstring "$STATS" ",\"explicitCacheBytes\":0,\"followeeCacheBytes\":0,\"favouritesCacheBytes\":810,\"ipfsStatus\":true}"
# We also want to verify that the delete works.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XDELETE "http://127.0.0.1:8000/favourites/remove/$AUDIO_CID"
FAVOURITES_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/favourites/list")
requireSubstring "$FAVOURITES_LIST" "[]"

echo "Check the list of followee keys for this user"
FOLLOWEE_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/followees/keys")
requireSubstring "$FOLLOWEE_KEYS" "[]"

echo "Check that we can read the preferences"
PREFS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/prefs")
requireSubstring "$PREFS" "{\"videoEdgePixelMax\":1280,\"republishIntervalMillis\":43200000,\"explicitCacheTargetBytes\":1000000000,\"explicitUserInfoRefreshMillis\":86400000,\"followeeCacheTargetBytes\":10000000000,\"followeeRefreshMillis\":3600000,\"followeeRecordThumbnailMaxBytes\":10000000,\"followeeRecordAudioMaxBytes\":200000000,\"followeeRecordVideoMaxBytes\":2000000000}"

echo "Check that we can edit the preferences"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "edgeSize=500&followerCacheBytes=2000000000&republishIntervalMillis=70000&followeeRefreshMillis=80000" http://127.0.0.1:8000/server/prefs >& /dev/null
# This should fail since it is missing some parameters: 400 Bad Request
if [ $? != 22 ]; then
	exit 1
fi
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "videoEdgePixelMax=500&republishIntervalMillis=70000&explicitCacheTargetBytes=1000000001&explicitUserInfoRefreshMillis=60001&followeeCacheTargetBytes=2000000000&followeeRefreshMillis=80000&followeeRecordThumbnailMaxBytes=10000000&followeeRecordAudioMaxBytes=200000000&followeeRecordVideoMaxBytes=2000000002" http://127.0.0.1:8000/server/prefs
PREFS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/prefs")
requireSubstring "$PREFS" "{\"videoEdgePixelMax\":500,\"republishIntervalMillis\":70000,\"explicitCacheTargetBytes\":1000000001,\"explicitUserInfoRefreshMillis\":60001,\"followeeCacheTargetBytes\":2000000000,\"followeeRefreshMillis\":80000,\"followeeRecordThumbnailMaxBytes\":10000000,\"followeeRecordAudioMaxBytes\":200000000,\"followeeRecordVideoMaxBytes\":2000000002}"

echo "Check that we can read the version"
VERSION=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/status")
requireSubstring "$VERSION" "\"version\""

echo "Verify that we can load the status page..."
STATUS_PAGE=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/status.html")
requireSubstring "$STATUS_PAGE" "Cacophony - Server Status"

echo "Test that we can request another republish..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8000/home/republish/$PUBLIC_KEY"
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":6,\"value\":\"Publish IpfsFile("
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":6,\"value\":null"

echo "Delete one of the posts from earlier and make sure that the other is still in the list..."
POST_LIST=$(curl --no-progress-meter -XGET "http://127.0.0.1:9001/keys")
requireSubstring "$POST_LIST" "[\"$POST_ID\",\"$REPLY_HASH\",\"$AUDIO_CID\"]"
POST_TO_DELETE="$POST_ID"
POST_TO_KEEP1="$REPLY_HASH"
POST_TO_KEEP2="$AUDIO_CID"
# Before deleting the post, we should see that it is known to be cached so it has no data to cache.
TARGET_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XGET "http://127.0.0.1:8000/server/postStruct/$POST_TO_DELETE/OPTIONAL")
requireSubstring "$TARGET_STRUCT" "\"hasDataToCache\":false"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XDELETE "http://127.0.0.1:8000/home/post/delete/$PUBLIC_KEY/$POST_TO_DELETE"
checkPreviousCommand "DELETE post"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XDELETE "http://127.0.0.1:8000/home/post/delete/$PUBLIC_KEY/$POST_TO_DELETE" >& /dev/null
# 400 bad request.
if [ $? != 22 ]; then
	exit 1
fi
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":7,\"value\":\"Publish IpfsFile("
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":7,\"value\":null"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XGET "http://127.0.0.1:8000/server/postStruct/$POST_TO_KEEP2/OPTIONAL" >& /dev/null
checkPreviousCommand "read post"

echo "Set one of these posts as our feature, then clear it..."
DESCRIPTION=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XPOST "http://127.0.0.1:8000/home/userInfo/feature/$PUBLIC_KEY/$POST_TO_KEEP1")
requireSubstring "$DESCRIPTION" ",\"feature\":\"$POST_TO_KEEP1\"}"
DESCRIPTION=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/unknownUser/$PUBLIC_KEY")
requireSubstring "$DESCRIPTION" ",\"feature\":\"$POST_TO_KEEP1\"}"
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":8,\"value\":\"Publish IpfsFile("
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":8,\"value\":null"
DESCRIPTION=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XPOST "http://127.0.0.1:8000/home/userInfo/feature/$PUBLIC_KEY/NONE")
requireSubstring "$DESCRIPTION" ",\"feature\":null}"
DESCRIPTION=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/unknownUser/$PUBLIC_KEY")
requireSubstring "$DESCRIPTION" ",\"feature\":null}"
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":9,\"value\":\"Publish IpfsFile("
STATUS_INDEX1=$((STATUS_INDEX1 + 1))
STATUS_EVENT=$(curl -XGET http://127.0.0.1:9000/waitAndGet/$STATUS_INDEX1 2> /dev/null)
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":9,\"value\":null"

# We want to verify that something reasonable happens when we fetch the now-deleted element if it has been removed from the network.  This requires a GC of the IPFS nodes to wipe it.
requestIpfsGc "$PATH_TO_IPFS" 1
requestIpfsGc "$PATH_TO_IPFS" 2
echo "Fetching now-wiped element (this should delay for about 10 seconds while waiting for timeout)..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XGET "http://127.0.0.1:8000/server/postStruct/$POST_TO_DELETE/OPTIONAL" >& /dev/null
# Currently appears as error 500 since the timeout is a generic IpfsConnectionException
if [ $? != 22 ]; then
	exit 1
fi

ENTRIES_INDEX=$((ENTRIES_INDEX + 1))
SAMPLE=$(curl -XGET http://127.0.0.1:9001/waitAndGet/$ENTRIES_INDEX 2> /dev/null)
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$POST_TO_DELETE\",\"value\":null,\"isNewest\":false}"
POST_LIST=$(curl --no-progress-meter -XGET "http://127.0.0.1:9001/keys")
requireSubstring "$POST_LIST" "[\"$POST_TO_KEEP1\",\"$POST_TO_KEEP2\"]"

echo "Make sure that the core threads are still running..."
JSTACK=$(jstack "$SERVER_PID")
requireSubstring "$JSTACK" "Background Operations"
requireSubstring "$JSTACK" "Scheduler thread"

# Check that the keys captured by the WebSocket utility are expected.
KEY_ARRAY=$(curl -XGET http://127.0.0.1:9000/keys 2> /dev/null)
requireSubstring "$KEY_ARRAY" "[]"


echo "Stop the server and wait for it to exit..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8000/server/stop"
wait $SERVER_PID

echo "Now that the server stopped, the status should be done"
wait $STATUS_PID1
wait $STATUS_PID2
wait $ENTRIES_PID
# We just want to look for some of the events we would expect to see in a typical run (this is more about coverage than precision).
STATUS_DATA2=$(cat "$WS_STATUS2.out")
requireSubstring "$STATUS_DATA2" "{\"event\":\"create\",\"key\":2,\"value\":\"Publish IpfsFile("
requireSubstring "$STATUS_DATA2" "{\"event\":\"delete\",\"key\":2,\"value\":null"
requireSubstring "$STATUS_DATA2" "{\"event\":\"create\",\"key\":3,\"value\":\"Publish IpfsFile("
requireSubstring "$STATUS_DATA2" "{\"event\":\"delete\",\"key\":3,\"value\":null"
requireSubstring "$STATUS_DATA2" "{\"event\":\"create\",\"key\":4,\"value\":\"Publish IpfsFile("
requireSubstring "$STATUS_DATA2" "{\"event\":\"delete\",\"key\":4,\"value\":null"
requireSubstring "$STATUS_DATA2" "{\"event\":\"create\",\"key\":5,\"value\":\"Publish IpfsFile("
requireSubstring "$STATUS_DATA2" "{\"event\":\"delete\",\"key\":5,\"value\":null"

echo "Verify that we can see the published post in out list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "New Draft - $PUBLISH_ID"


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
