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
FAIL_PROCESS_FIFO=/tmp/fail_fifo
CANCEL_PROCESS_INPUT=/tmp/fail_input
PROCESS_INPUT=/tmp/process_input
PROCESS_OUTPUT=/tmp/process_output
EXISTING_INPUT=/tmp/existing_input
EXISTING_OUTPUT=/tmp/existing_output
ENTRIES_OUTPUT=/tmp/entries_output
ENTRIES_INPUT=/tmp/entries_input

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"
rm -f "$STATUS_OUTPUT.1" "$STATUS_OUTPUT.2"
rm -f "$STATUS_INPUT.1"
rm -f "$PROCESS_INPUT" "$PROCESS_OUTPUT"
rm -f "$EXISTING_INPUT" "$EXISTING_OUTPUT"
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

echo "Creating Cacophony instance..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001
checkPreviousCommand "createNewChannel"

echo "Start the interactive server and wait 5 seconds for it to bind the port..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --run --commandSelection DANGEROUS &
SERVER_PID=$!
sleep 5

echo "Make sure that we can access static files..."
INDEX=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET -L "http://127.0.0.1:8000/")
requireSubstring "$INDEX" "Cacophony - Static Index"

echo "Requesting creation of XSRF token..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)

echo "Now that we have verified that the server is up, start listening to status events..."
# We will open 2 connections to verify that concurrent connections are ok but we will also use one as a pipe, allowing us to precisely observe events, and the other one just as a file, so we can verify it ends up with the same events, at the end.  In theory, these could mismatch but that will probably never be observed due to the relative cost of a refresh versus sending a WebSocket message.
mkfifo "$STATUS_INPUT.1"
mkfifo "$STATUS_OUTPUT.1"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/backgroundStatus" "event_api" "$STATUS_INPUT.1" "$STATUS_OUTPUT.1" &
STATUS_PID1=$!
touch "$STATUS_OUTPUT.2"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/backgroundStatus" "event_api" "$STATUS_OUTPUT.2" &
STATUS_PID2=$!
# Wait for connect.
cat "$STATUS_OUTPUT.1" > /dev/null

echo "Get the default video config..."
VIDEO_CONFIG=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/videoConfig)
requireSubstring "$VIDEO_CONFIG" "ffmpeg"

echo "Get the empty list of drafts..."
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/drafts)
requireSubstring "$DRAFTS" "[]"

echo "Verify that even fetching the drafts requires the XSRF token..."
curl --fail --no-progress-meter -XGET http://127.0.0.1:8000/drafts >& /dev/null
if [ "22" != "$?" ];
then
	echo "Expected failure"
	exit 1
fi

echo "Check that we can read our public key"
PUBLIC_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/publicKey")
# (we only know that the key starts with "z".
requireSubstring "$PUBLIC_KEY" "z"

echo "Attach the followee post listener..."
mkfifo "$ENTRIES_INPUT"
mkfifo "$ENTRIES_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/user/entries/$PUBLIC_KEY" "event_api" "$ENTRIES_INPUT" "$ENTRIES_OUTPUT" &
ENTRIES_PID=$!
cat "$ENTRIES_OUTPUT" > /dev/null

echo "Create a new draft..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/createDraft)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
ID=$(echo $ID_PARSE)
echo "...working with draft $ID"

echo "Verify that we can read the draft..."
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"title\":\"New Draft - $ID\""

echo "Verify that we can see the draft in the list..."
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/drafts)
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
echo "aXbXcXdXe" | java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" SEND "ws://127.0.0.1:8000/draft/saveVideo/$ID/1/2/webm" video

echo "Verify that we can cancel a video processing operation..."
rm -f "$FAIL_PROCESS_FIFO"
mkfifo "$FAIL_PROCESS_FIFO"
rm -f "$CANCEL_PROCESS_INPUT"
mkfifo "$CANCEL_PROCESS_INPUT"
# Note that the value of FAIL_PROCESS_FIFO is hard-coded in this process:  "%2Ftmp%2Ffail_fifo"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/draft/processVideo/$ID/cat%20%2Ftmp%2Ffail_fifo" "event_api" "$CANCEL_PROCESS_INPUT" "/dev/null" &
FAIL_PID=$!
FAIL_PROC_COUNT=$(ps auxww | grep fail | grep --count fifo)
if [ "$FAIL_PROC_COUNT" -ne 1 ]; then
	echo "Failed to find the stuck process with $FAIL_PROC_COUNT matches"
	kill -9 $FAIL_PID
	exit 1
fi
echo "Close the processing channel to cancel it"
echo -n "-CLOSE" > "$CANCEL_PROCESS_INPUT"
wait $FAIL_PID
# We expect that the process is still running.
FAIL_PROC_COUNT=$(ps auxww | grep fail | grep --count fifo)
if [ "$FAIL_PROC_COUNT" -ne 1 ]; then
	echo "Failed to find the stuck process, after closing connection, with $FAIL_PROC_COUNT matches"
	exit 1
fi

# Do the re-open.
echo "Do the re-open..."
mkfifo "$EXISTING_INPUT"
mkfifo "$EXISTING_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/draft/existingVideo/$ID" "event_api" "$EXISTING_INPUT" "$EXISTING_OUTPUT" &
FAIL_PID=$!
# Wait for connect.
cat "$EXISTING_OUTPUT" > /dev/null

# We should see the replay of the creation of inputBytes key.
SINGLE_EVENT=$(cat "$EXISTING_OUTPUT")
echo -n "-ACK" > "$EXISTING_INPUT"
requireSubstring "$SINGLE_EVENT" "{\"event\":\"create\",\"key\":\"inputBytes\",\"value\":"
# Do the cancel (VideoProcessorCallbackHandler.COMMAND_CANCEL_PROCESSING) and wait for the close.
echo "Cancel the processing..."
echo -n "COMMAND_CANCEL_PROCESSING" > "$EXISTING_INPUT"
# We should see this force-stop as a delete of that input bytes key followed by a create and delete pair of the output bytes set to -1.
SINGLE_EVENT=$(cat "$EXISTING_OUTPUT")
echo -n "-ACK" > "$EXISTING_INPUT"
requireSubstring "$SINGLE_EVENT" "{\"event\":\"delete\",\"key\":\"inputBytes\",\"value\":null,\"isNewest\":false}"
SINGLE_EVENT=$(cat "$EXISTING_OUTPUT")
echo -n "-ACK" > "$EXISTING_INPUT"
requireSubstring "$SINGLE_EVENT" "{\"event\":\"create\",\"key\":\"outputBytes\",\"value\":-1,\"isNewest\":true}"
SINGLE_EVENT=$(cat "$EXISTING_OUTPUT")
echo -n "-ACK" > "$EXISTING_INPUT"
requireSubstring "$SINGLE_EVENT" "{\"event\":\"delete\",\"key\":\"outputBytes\",\"value\":null,\"isNewest\":false}"
# Verify that the process has terminated.
FAIL_PROC_COUNT=$(ps auxww | grep fail | grep --count fifo)
if [ "$FAIL_PROC_COUNT" -ne 0 ]; then
	echo "Stuck processes remaining: $FAIL_PROC_COUNT"
	exit 1
fi
echo -n "-WAIT" > "$EXISTING_INPUT"
wait $FAIL_PID
echo "Verify that the draft shows no processed video..."
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" ",\"originalVideo\":{\"mime\":\"video/webm\",\"height\":1,\"width\":2,\"byteSize\":10},\"processedVideo\":null,\"audio\":null}"

echo "Verify that we can make sense of the error when the command isn't found (the default is ffmpeg, which not everyone has)..."
mkfifo "$PROCESS_INPUT"
mkfifo "$PROCESS_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/draft/processVideo/$ID/bogusProgramName" "event_api" "$PROCESS_INPUT" "$PROCESS_OUTPUT" &
SAMPLE_PID=$!
# Wait for connect and then wait for disconnect - that is what happens on the error (there will be no events).
cat "$PROCESS_OUTPUT" > /dev/null
echo -n "-WAIT" > "$PROCESS_INPUT"
wait $SAMPLE_PID
rm -f "$PROCESS_INPUT" "$PROCESS_OUTPUT"

echo "Process the uploaded video..."
mkfifo "$PROCESS_INPUT"
mkfifo "$PROCESS_OUTPUT"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/draft/processVideo/$ID/cut%20-d%20X%20-f%203" "event_api" "$PROCESS_INPUT" "$PROCESS_OUTPUT" &
SAMPLE_PID=$!
# Wait for connect.
cat "$PROCESS_OUTPUT" > /dev/null
# We expect every message to be an "event" - we need to see the "create", zero or more "update", and then a single "delete".
SAMPLE=$(cat "$PROCESS_OUTPUT")
echo -n "-ACK" > "$PROCESS_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"inputBytes\",\"value\":"
SAMPLE=$(cat "$PROCESS_OUTPUT")
echo -n "-ACK" > "$PROCESS_INPUT"
while [ "$SAMPLE" != "{\"event\":\"delete\",\"key\":\"inputBytes\",\"value\":null,\"isNewest\":false}" ]
do
	requireSubstring "$SAMPLE" "{\"event\":\"update\",\"key\":\"inputBytes\",\"value\":"
	SAMPLE=$(cat "$PROCESS_OUTPUT")
	echo -n "-ACK" > "$PROCESS_INPUT"
done
# At the end, we see the final update with processed size (2 bytes - "c\n").
SAMPLE=$(cat "$PROCESS_OUTPUT")
echo -n "-ACK" > "$PROCESS_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"outputBytes\",\"value\":2,\"isNewest\":true}"
SAMPLE=$(cat "$PROCESS_OUTPUT")
echo -n "-ACK" > "$PROCESS_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"outputBytes\",\"value\":null,\"isNewest\":false}"
echo -n "-WAIT" > "$PROCESS_INPUT"
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
echo "AUDIO_DATA" | java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" SEND "ws://127.0.0.1:8000/draft/saveAudio/$ID/ogg" audio
ORIGINAL_AUDIO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET "http://127.0.0.1:8000/draft/audio/$ID")
if [ "AUDIO_DATA" != "$ORIGINAL_AUDIO" ];
then
	echo "Original audio not expected: $ORIGINAL_AUDIO"
	exit 1
fi

echo "Verify that the draft information is correct..."
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"title\":\"Updated Title\",\"description\":\"\",\"thumbnail\":{\"mime\":\"image/jpeg\",\"height\":5,\"width\":6,\"byteSize\":15},\"discussionUrl\":\"\",\"originalVideo\":{\"mime\":\"video/webm\",\"height\":1,\"width\":2,\"byteSize\":10},\"processedVideo\":{\"mime\":\"video/webm\",\"height\":1,\"width\":2,\"byteSize\":2},\"audio\":{\"mime\":\"audio/ogg\",\"height\":0,\"width\":0,\"byteSize\":11}}"

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
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/drafts)
requireSubstring "$DRAFTS" "[]"
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
if [ ! -z "$DRAFT" ]; then
	echo "Draft not empty: $DRAFT"
	exit 1
fi

echo "Create a new draft and publish it..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/createDraft)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
echo "...working with draft $PUBLISH_ID"
# First, make sure that giving an invalid type argument has no effect.
ERROR=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLISH_ID/BOGUS)
requireSubstring "$ERROR" "Invalid draft type: \"BOGUS\""
# Now, do it correctly.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLISH_ID/VIDEO

echo "Waiting for draft publish..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/wait/publish

echo "Verify that we see the new entry in the entry socket..."
SAMPLE=$(cat "$ENTRIES_OUTPUT")
echo -n "-ACK" > "$ENTRIES_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":"

# We will verify that this is in the pipe we are reading from the WebSocket (note that we may sometimes see event "1" from the start-up publish, so just skip that one in this case).
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
if [[ "$STATUS_EVENT" =~ "\"event\":\"create\",\"key\":1," ]]; then
	STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
	echo -n "-ACK" > "$STATUS_INPUT.1"
	STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
	echo -n "-ACK" > "$STATUS_INPUT.1"
fi
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":2,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":2,\"value\":null"

echo "Verify that it is not in the list..."
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/drafts)
requireSubstring "$DRAFTS" "[]"

echo "Check the user data for this user"
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/userInfo/$PUBLIC_KEY")
requireSubstring "$USER_INFO" "\"description\":\"Description forthcoming\""

echo "Check the list of posts for this user"
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC_KEY")
# (make sure we at least see an entry in the list - we just don't know what it will be)
requireSubstring "$POST_LIST" "[\"Qm"

echo "Check the list of recommended keys for this user"
RECOMMENDED_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/recommendedKeys/$PUBLIC_KEY")
requireSubstring "$RECOMMENDED_KEYS" "[]"

echo "Read the new post through the REST interface"
POST_ID=$(echo "$POST_LIST" | cut -d "\"" -f 2)
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postStruct/$POST_ID")
requireSubstring "$POST_STRUCT" ",\"publisherKey\":\"$PUBLIC_KEY\",\"cached\":true,\"thumbnailUrl\":null,\"videoUrl\":null,\"audioUrl\":null}"

echo "Edit the post and make sure that we see the updates in both sockets and the post list..."
OLD_POST_ID="$POST_ID"
POST_ID=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Edit%20Title&DESCRIPTION=Has%20Changed" http://127.0.0.1:8000/editPost/$POST_ID)
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC_KEY")
requireSubstring "$POST_LIST" "[\"$POST_ID\"]"
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postStruct/$POST_ID")
requireSubstring "$POST_STRUCT" "{\"name\":\"Edit Title\",\"description\":\"Has Changed\",\"publishedSecondsUtc\":"
SAMPLE=$(cat "$ENTRIES_OUTPUT")
echo -n "-ACK" > "$ENTRIES_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$OLD_POST_ID\",\"value\":null,\"isNewest\":false}"
SAMPLE=$(cat "$ENTRIES_OUTPUT")
echo -n "-ACK" > "$ENTRIES_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$POST_ID\",\"value\":null,\"isNewest\":true}"
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":3,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":3,\"value\":null,\"isNewest\":false}"

echo "Create an audio post, publish it, and make sure we can see it..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/createDraft)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
echo "AUDIO_DATA" | java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" SEND "ws://127.0.0.1:8000/draft/saveAudio/$PUBLISH_ID/ogg" audio
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLISH_ID/AUDIO
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC_KEY")
# We want to look for the second post so get field 4:  1 "2" 3 "4" 5
POST_ID=$(echo "$POST_LIST" | cut -d "\"" -f 4)
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postStruct/$POST_ID")
requireSubstring "$POST_STRUCT" ",\"publisherKey\":\"$PUBLIC_KEY\",\"cached\":true,\"thumbnailUrl\":null,\"videoUrl\":null,\"audioUrl\":\"http://127.0.0.1:8080/ipfs/QmQyT5aRrJazL9T3AASkpM8AdS73a6eBGexa7W4GuXbMvJ\"}"

# Check that we see this in the output events.
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":4,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":4,\"value\":null"

echo "Verify that we see the new entry in the entry socket..."
SAMPLE=$(cat "$ENTRIES_OUTPUT")
echo -n "-ACK" > "$ENTRIES_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":"
# Capture the record CID to verify it against the POST_ID from earlier:  1 "2-event" 3 "4-create" 5 "6-key" 7 "8-CID"
EVENT_POST_ID=$(echo "$SAMPLE" | cut -d "\"" -f 8)
if [ "$EVENT_POST_ID" != "$POST_ID" ]; then
	exit 1
fi

echo "Check the list of followee keys for this user"
FOLLOWEE_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/followeeKeys")
requireSubstring "$FOLLOWEE_KEYS" "[]"

echo "Check that we can read the preferences"
PREFS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/prefs")
requireSubstring "$PREFS" "{\"edgeSize\":1280,\"followerCacheBytes\":10000000000,\"republishIntervalMillis\":43200000,\"followeeRefreshMillis\":3600000}"

echo "Check that we can edit the preferences"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "edgeSize=500&followerCacheBytes=2000000000&republishIntervalMillis=70000&followeeRefreshMillis=80000" http://127.0.0.1:8000/prefs
PREFS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/prefs")
requireSubstring "$PREFS" "{\"edgeSize\":500,\"followerCacheBytes\":2000000000,\"republishIntervalMillis\":70000,\"followeeRefreshMillis\":80000}"

echo "Check that we can read the version"
VERSION=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/version")
requireSubstring "$VERSION" "\"version\""

echo "Verify that we can load the status page..."
STATUS_PAGE=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/status.html")
requireSubstring "$STATUS_PAGE" "Cacophony - Server Status"

echo "Test that we can request another republish..."
echo -n "COMMAND_REPUBLISH" > "$STATUS_INPUT.1"
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":5,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":5,\"value\":null"

echo "Delete one of the posts from earlier and make sure that the other is still in the list..."
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC_KEY")
# Extract fields 2 and 4:  1 "2" 3 "4" 5
POST_TO_DELETE=$(echo "$POST_LIST" | cut -d "\"" -f 2)
POST_TO_KEEP=$(echo "$POST_LIST" | cut -d "\"" -f 4)
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XDELETE "http://127.0.0.1:8000/post/$POST_TO_DELETE"
checkPreviousCommand "DELETE post"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XDELETE "http://127.0.0.1:8000/post/$POST_TO_DELETE" >& /dev/null
# 400 bad request.
if [ $? != 22 ]; then
	exit 1
fi
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/postHashes/$PUBLIC_KEY")
requireSubstring "$POST_LIST" "[\"$POST_TO_KEEP\"]"
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":6,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$STATUS_OUTPUT.1")
echo -n "-ACK" > "$STATUS_INPUT.1"
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":6,\"value\":null"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XGET "http://127.0.0.1:8000/postStruct/$POST_TO_KEEP" >& /dev/null
checkPreviousCommand "read post"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XGET "http://127.0.0.1:8000/postStruct/$POST_TO_DELETE" >& /dev/null
# 404 not found.
if [ $? != 22 ]; then
	exit 1
fi
SAMPLE=$(cat "$ENTRIES_OUTPUT")
echo -n "-ACK" > "$ENTRIES_INPUT"
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$POST_TO_DELETE\",\"value\":null,\"isNewest\":false}"

echo "Make sure that the core threads are still running..."
JSTACK=$(jstack "$SERVER_PID")
requireSubstring "$JSTACK" "Background Operations"
requireSubstring "$JSTACK" "Scheduler thread"

echo "Stop the server and wait for it to exit..."
echo -n "COMMAND_STOP" > "$STATUS_INPUT.1"
wait $SERVER_PID

echo "Now that the server stopped, the status should be done"
echo -n "-WAIT" > "$STATUS_INPUT.1"
echo -n "-WAIT" > "$ENTRIES_INPUT"
wait $STATUS_PID1
wait $STATUS_PID2
wait $ENTRIES_PID
# We just want to look for some of the events we would expect to see in a typical run (this is more about coverage than precision).
STATUS_DATA2=$(cat "$STATUS_OUTPUT.2")
requireSubstring "$STATUS_DATA2" "{\"event\":\"create\",\"key\":2,\"value\":\"Publish IpfsFile("
requireSubstring "$STATUS_DATA2" "{\"event\":\"delete\",\"key\":2,\"value\":null"
requireSubstring "$STATUS_DATA2" "{\"event\":\"create\",\"key\":3,\"value\":\"Publish IpfsFile("
requireSubstring "$STATUS_DATA2" "{\"event\":\"delete\",\"key\":3,\"value\":null"
requireSubstring "$STATUS_DATA2" "{\"event\":\"create\",\"key\":4,\"value\":\"Publish IpfsFile("
requireSubstring "$STATUS_DATA2" "{\"event\":\"delete\",\"key\":4,\"value\":null"

echo "Verify that we can see the published post in out list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "New Draft - $PUBLISH_ID"


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
