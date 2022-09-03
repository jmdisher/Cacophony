#!/bin/bash

BASEDIR=$(dirname $0)
source "$BASEDIR/utils.sh"


# START.
if [ $# -ne 3 ]; then
	echo "Missing arguments: path_to_ipfs path_to_resources path_to_jar"
	exit 1
fi

# We always want to run our tests with extra verifications.
export CACOPHONY_ENABLE_VERIFICATIONS=1

BASEDIR=$(dirname $0)
PATH_TO_IPFS="$1"
RESOURCES="$2"
PATH_TO_JAR="$3"

REPO1=/tmp/repo1
REPO2=/tmp/repo2

USER1=/tmp/user1
USER2=/tmp/user2
COOKIES1=/tmp/cookies1

rm -rf "$REPO1"
rm -rf "$REPO2"
rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"

mkdir "$REPO1"
mkdir "$REPO2"

# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar

IPFS_PATH="$REPO1" $PATH_TO_IPFS init
checkPreviousCommand "repo1 init"
IPFS_PATH="$REPO2" $PATH_TO_IPFS init
checkPreviousCommand "repo2 init"

cp "$RESOURCES/swarm.key" "$REPO1/"
cp "$RESOURCES/seed_config" "$REPO1/config"
cp "$RESOURCES/swarm.key" "$REPO2/"
cp "$RESOURCES/node1_config" "$REPO2/config"

IPFS_PATH="$REPO1" $PATH_TO_IPFS daemon &
PID1=$!
echo "Daemon 1: $PID1"
IPFS_PATH="$REPO2" $PATH_TO_IPFS daemon &
PID2=$!
echo "Daemon 2: $PID2"

echo "Pausing for startup..."
sleep 5

echo "Creating Cacophony instance..."
CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001
checkPreviousCommand "createNewChannel"

echo "Start the interactive server and wait 5 seconds for it to bind the port..."
CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --run --commandSelection DANGEROUS &
SERVER_PID=$!
sleep 5

echo "Make sure that we can access static files..."
INDEX=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET -L "http://127.0.0.1:8000/")
requireSubstring "$INDEX" "Cacophony - Static Index"

echo "Requesting creation of XSRF token..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/cookie

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
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data "title=Updated%20Title&description=" http://127.0.0.1:8000/draft/$ID
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"title\":\"Updated Title\""
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST -H  "Content-Type: image/jpeg" --data "FAKE_IMAGE_DATA" http://127.0.0.1:8000/draft/thumb/$ID/5/6/jpeg
checkPreviousCommand "POST /draft/thumb"
DRAFT=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/draft/$ID)
requireSubstring "$DRAFT" "\"thumbnail\":{\"mime\":\"image/jpeg\",\"height\":5,\"width\":6,\"byteSize\":15}"

echo "Upload and process data as the video for the draft..."
XSRF_TOKEN=$(grep XSRF "$COOKIES1" | cut -f 7)
echo "aXbXcXdXe" | java -cp build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" SEND "ws://127.0.0.1:8000/draft/saveVideo/$ID/1/2/webm" video
java -cp build/test:lib/* com.jeffdisher.cacophony.testutils.WebSocketUtility "$XSRF_TOKEN" DRAIN "ws://127.0.0.1:8000/draft/processVideo/$ID/cut%20-d%20X%20-f%203" process
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

echo "Verify that we can delete the individual video files..."
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

echo "Verify that we can delete the draft and see an empty list..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XDELETE http://127.0.0.1:8000/draft/$ID
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/drafts)
requireSubstring "$DRAFTS" "[]"

echo "Create a new draft and publish it..."
CREATED=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/createDraft)
# We need to parse out the ID (look for '{"id":2107961294,')
ID_PARSE=$(echo "$CREATED" | sed 's/{"id":/\n/g'  | cut -d , -f 1)
PUBLISH_ID=$(echo $ID_PARSE)
echo "...working with draft $PUBLISH_ID"
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XPOST http://127.0.0.1:8000/draft/publish/$PUBLISH_ID

echo "Verify that it is not in the list..."
DRAFTS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET http://127.0.0.1:8000/drafts)
requireSubstring "$DRAFTS" "[]"

echo "Check that we can read our public key"
PUBLIC_KEY=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET http://127.0.0.1:8000/publicKey)
# (we only know that the key starts with "z".
requireSubstring "$PUBLIC_KEY" "z"

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
requireSubstring "$POST_STRUCT" "\"cached\":true"

echo "Check the list of followee keys for this user"
FOLLOWEE_KEYS=$(curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1"  --no-progress-meter -XGET "http://127.0.0.1:8000/followeeKeys")
requireSubstring "$FOLLOWEE_KEYS" "[]"

echo "Verify that we see it in the generated_db.js (since we are generating that as a stop-gap before transitioning to the dynamic mode)..."
GENERATED_DB=$(curl --no-progress-meter -XGET http://127.0.0.1:8000/generated_db.js)
requireSubstring "$GENERATED_DB" "File is generated for the interactive --run method."
requireSubstring "$GENERATED_DB" "$PUBLISH_ID"

echo "Stop the server and wait for it to exit..."
curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" -XPOST http://127.0.0.1:8000/stop
wait $SERVER_PID

echo "Verify that we can see the published post in out list..."
LISTING=$(CACOPHONY_STORAGE="$USER1" java -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "New Draft - $PUBLISH_ID"


kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
