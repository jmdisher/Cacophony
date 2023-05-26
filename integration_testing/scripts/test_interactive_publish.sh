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

WS_STATUS1=/tmp/status1
WS_STATUS2=/tmp/status2
WS_ENTRIES=/tmp/entries
WS_PROCESSING1=/tmp/processing1
WS_PROCESSING2=/tmp/processing2
WS_PROCESSING3=/tmp/processing3
WS_EXISTING=/tmp/existing

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"

rm -f "$WS_STATUS1".*
rm -f "$WS_STATUS2".*
rm -f "$WS_ENTRIES".*
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

echo "Creating Cacophony instance..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --createNewChannel
checkPreviousCommand "createNewChannel"

echo "Start the interactive server..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --run --commandSelection DANGEROUS &
SERVER_PID=$!
waitForCacophonyStart 8000

echo "Make sure that we can access static files..."
INDEX=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET -L "http://127.0.0.1:8000/")
requireSubstring "$INDEX" "Cacophony - Static Index"

echo "Requesting creation of XSRF token..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/server/cookie
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)

echo "Now that we have verified that the server is up, start listening to status events..."
# We will open 2 connections to verify that concurrent connections are ok but we will also use one as a pipe, allowing us to precisely observe events, and the other one just as a file, so we can verify it ends up with the same events, at the end.  In theory, these could mismatch but that will probably never be observed due to the relative cost of a refresh versus sending a WebSocket message.
mkfifo "$WS_STATUS1.out" "$WS_STATUS1.in" "$WS_STATUS1.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/server/events/status" "event_api" "$WS_STATUS1.out" "$WS_STATUS1.in" "$WS_STATUS1.clear" &
STATUS_PID1=$!
touch "$WS_STATUS2.out"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" DRAIN "ws://127.0.0.1:8000/server/events/status" "event_api" "$WS_STATUS2.out" &
STATUS_PID2=$!
# Wait for connect.
cat "$WS_STATUS1.out" > /dev/null

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
PUBLIC_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/home/publicKey")
# (we only know that the key starts with "z".
requireSubstring "$PUBLIC_KEY" "z"

echo "Attach the followee post listener..."
mkfifo "$WS_ENTRIES.out" "$WS_ENTRIES.in" "$WS_ENTRIES.clear"
java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" JSON_IO "ws://127.0.0.1:8000/server/events/entries/$PUBLIC_KEY" "event_api" "$WS_ENTRIES.out" "$WS_ENTRIES.in" "$WS_ENTRIES.clear" &
ENTRIES_PID=$!
cat "$WS_ENTRIES.out" > /dev/null

echo "Create a new draft..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new)
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
requireSubstring "$DRAFT" ",\"originalVideo\":{\"mime\":\"video/webm\",\"height\":1,\"width\":2,\"byteSize\":10},\"processedVideo\":null,\"audio\":null}"

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
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/allDrafts/all)
requireSubstring "$DRAFTS" "[]"
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
if [ ! -z "$DRAFT" ]; then
	echo "Draft not empty: $DRAFT"
	exit 1
fi

echo "Create a new draft and publish it..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
echo "...working with draft $PUBLISH_ID"
# First, make sure that giving an invalid type argument has no effect.
ERROR=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/BOGUS)
requireSubstring "$ERROR" "Invalid draft type: \"BOGUS\""
# Now, do it correctly.
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/VIDEO

echo "Waiting for draft publish..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/waitPublish

echo "Verify that we see the new entry in the entry socket..."
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":"

# We will verify that this is in the pipe we are reading from the WebSocket (note that we may sometimes see event "1" from the start-up publish, so just skip that one in this case).
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
if [[ "$STATUS_EVENT" =~ "\"event\":\"create\",\"key\":1," ]]; then
	STATUS_EVENT=$(cat "$WS_STATUS1.out")
	echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
	STATUS_EVENT=$(cat "$WS_STATUS1.out")
	echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
fi
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":2,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":2,\"value\":null"

echo "Verify that it is not in the list..."
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/allDrafts/all)
requireSubstring "$DRAFTS" "[]"

echo "Check the user data for this user"
USER_INFO=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/userInfo/$PUBLIC_KEY")
requireSubstring "$USER_INFO" "\"description\":\"Description forthcoming\""

echo "Check the list of posts for this user"
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postHashes/$PUBLIC_KEY")
# (make sure we at least see an entry in the list - we just don't know what it will be)
requireSubstring "$POST_LIST" "[\"Qm"

echo "Check the list of recommended keys for this user"
RECOMMENDED_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/recommendedKeys/$PUBLIC_KEY")
requireSubstring "$RECOMMENDED_KEYS" "[]"

echo "Read the new post through the REST interface"
POST_ID=$(echo "$POST_LIST" | cut -d "\"" -f 2)
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postStruct/$POST_ID")
requireSubstring "$POST_STRUCT" ",\"publisherKey\":\"$PUBLIC_KEY\",\"cached\":true,\"thumbnailUrl\":null,\"videoUrl\":null,\"audioUrl\":null}"

echo "Edit the post and make sure that we see the updates in both sockets and the post list..."
OLD_POST_ID="$POST_ID"
POST_ID=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "NAME=Edit%20Title&DESCRIPTION=Has%20Changed" http://127.0.0.1:8000/home/post/edit/$PUBLIC_KEY/$POST_ID)
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postHashes/$PUBLIC_KEY")
requireSubstring "$POST_LIST" "[\"$POST_ID\"]"
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postStruct/$POST_ID")
requireSubstring "$POST_STRUCT" "{\"name\":\"Edit Title\",\"description\":\"Has Changed\",\"publishedSecondsUtc\":"
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$OLD_POST_ID\",\"value\":null,\"isNewest\":false}"
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":\"$POST_ID\",\"value\":null,\"isNewest\":true}"
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":3,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":3,\"value\":null,\"isNewest\":false}"

echo "Create an audio post, publish it, and make sure we can see it..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/allDrafts/new)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
echo "AUDIO_DATA" | java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" SEND "ws://127.0.0.1:8000/draft/audio/upload/$PUBLISH_ID/ogg" audio
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLIC_KEY/$PUBLISH_ID/AUDIO
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postHashes/$PUBLIC_KEY")
# We want to look for the second post so get field 4:  1 "2" 3 "4" 5
POST_ID=$(echo "$POST_LIST" | cut -d "\"" -f 4)
POST_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postStruct/$POST_ID")
requireSubstring "$POST_STRUCT" ",\"publisherKey\":\"$PUBLIC_KEY\",\"cached\":true,\"thumbnailUrl\":null,\"videoUrl\":null,\"audioUrl\":\"http://127.0.0.1:8080/ipfs/QmQyT5aRrJazL9T3AASkpM8AdS73a6eBGexa7W4GuXbMvJ\"}"

# Check that we see this in the output events.
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":4,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":4,\"value\":null"

echo "Verify that we see the new entry in the entry socket..."
SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"create\",\"key\":"
# Capture the record CID to verify it against the POST_ID from earlier:  1 "2-event" 3 "4-create" 5 "6-key" 7 "8-CID"
EVENT_POST_ID=$(echo "$SAMPLE" | cut -d "\"" -f 8)
if [ "$EVENT_POST_ID" != "$POST_ID" ]; then
	exit 1
fi

echo "See what happens if we add this post to our favourites..."
FAVOURITES_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/favourites/list")
requireSubstring "$FAVOURITES_LIST" "[]"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XPOST "http://127.0.0.1:8000/favourites/add/$POST_ID"
FAVOURITES_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/favourites/list")
requireSubstring "$FAVOURITES_LIST" "[\"$POST_ID\"]"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XDELETE "http://127.0.0.1:8000/favourites/remove/$POST_ID"
FAVOURITES_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/favourites/list")
requireSubstring "$FAVOURITES_LIST" "[]"

echo "Check the list of followee keys for this user"
FOLLOWEE_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/followees/keys")
requireSubstring "$FOLLOWEE_KEYS" "[]"

echo "Check that we can read the preferences"
PREFS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/prefs")
requireSubstring "$PREFS" "{\"edgeSize\":1280,\"followerCacheBytes\":10000000000,\"republishIntervalMillis\":43200000,\"followeeRefreshMillis\":3600000}"

echo "Check that we can edit the preferences"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "edgeSize=500&followerCacheBytes=2000000000&republishIntervalMillis=70000&followeeRefreshMillis=80000" http://127.0.0.1:8000/server/prefs
PREFS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/prefs")
requireSubstring "$PREFS" "{\"edgeSize\":500,\"followerCacheBytes\":2000000000,\"republishIntervalMillis\":70000,\"followeeRefreshMillis\":80000}"

echo "Check that we can read the version"
VERSION=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/version")
requireSubstring "$VERSION" "\"version\""

echo "Verify that we can load the status page..."
STATUS_PAGE=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/status.html")
requireSubstring "$STATUS_PAGE" "Cacophony - Server Status"

echo "Test that we can request another republish..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8000/home/republish/$PUBLIC_KEY"
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":5,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":5,\"value\":null"

echo "Delete one of the posts from earlier and make sure that the other is still in the list..."
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postHashes/$PUBLIC_KEY")
# Extract fields 2 and 4:  1 "2" 3 "4" 5
POST_TO_DELETE=$(echo "$POST_LIST" | cut -d "\"" -f 2)
POST_TO_KEEP=$(echo "$POST_LIST" | cut -d "\"" -f 4)
# Before deleting the post, we should see that it is known to be cached.
TARGET_STRUCT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XGET "http://127.0.0.1:8000/server/postStruct/$POST_TO_DELETE")
requireSubstring "$TARGET_STRUCT" "\"cached\":true"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XDELETE "http://127.0.0.1:8000/home/post/delete/$PUBLIC_KEY/$POST_TO_DELETE"
checkPreviousCommand "DELETE post"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter --fail -XDELETE "http://127.0.0.1:8000/home/post/delete/$PUBLIC_KEY/$POST_TO_DELETE" >& /dev/null
# 400 bad request.
if [ $? != 22 ]; then
	exit 1
fi
POST_LIST=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/server/postHashes/$PUBLIC_KEY")
requireSubstring "$POST_LIST" "[\"$POST_TO_KEEP\"]"
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"create\",\"key\":6,\"value\":\"Publish IpfsFile("
STATUS_EVENT=$(cat "$WS_STATUS1.out")
echo -n "-ACK" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
requireSubstring "$STATUS_EVENT" "{\"event\":\"delete\",\"key\":6,\"value\":null"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XGET "http://127.0.0.1:8000/server/postStruct/$POST_TO_KEEP" >& /dev/null
checkPreviousCommand "read post"

# We want to verify that something reasonable happens when we fetch the now-deleted element if it has been removed from the network.  This requires a GC of the IPFS nodes to wipe it.
requestIpfsGc "$PATH_TO_IPFS" 1
requestIpfsGc "$PATH_TO_IPFS" 2
echo "Fetching now-wiped element (this should delay for about 10 seconds while waiting for timeout)..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter --fail -XGET "http://127.0.0.1:8000/server/postStruct/$POST_TO_DELETE" >& /dev/null
# Currently appears as error 500 since the timeout is a generic IpfsConnectionException
if [ $? != 22 ]; then
	exit 1
fi

SAMPLE=$(cat "$WS_ENTRIES.out")
echo -n "-ACK" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
requireSubstring "$SAMPLE" "{\"event\":\"delete\",\"key\":\"$POST_TO_DELETE\",\"value\":null,\"isNewest\":false}"

echo "Make sure that the core threads are still running..."
JSTACK=$(jstack "$SERVER_PID")
requireSubstring "$JSTACK" "Background Operations"
requireSubstring "$JSTACK" "Scheduler thread"

echo "Stop the server and wait for it to exit..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST "http://127.0.0.1:8000/server/stop"
wait $SERVER_PID

echo "Now that the server stopped, the status should be done"
echo -n "-WAIT" > "$WS_STATUS1.in" && cat "$WS_STATUS1.clear" > /dev/null
echo -n "-WAIT" > "$WS_ENTRIES.in" && cat "$WS_ENTRIES.clear" > /dev/null
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

echo "Verify that we can see the published post in out list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "New Draft - $PUBLISH_ID"


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
