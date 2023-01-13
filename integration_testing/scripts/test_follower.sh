#!/bin/bash

BASEDIR=$(dirname $0)
source "$BASEDIR/utils.sh"
export CACOPHONY_ENABLE_VERIFICATIONS=1


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

STATIC2=/tmp/static2

rm -rf "$USER1"
rm -rf "$USER2"

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

echo "Creating Cacophony instance1..."
CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5001
checkPreviousCommand "createNewChannel"

echo "Creating Cacophony instance2..."
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --createNewChannel --ipfs /ip4/127.0.0.1/tcp/5002
checkPreviousCommand "createNewChannel"

echo "Reading public key for instance1..."
RESULT_STRING=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --getPublicKey)
checkPreviousCommand "getPublicKey"
# Parse this from the human-readable string.
PUBLIC1=$(echo "$RESULT_STRING" | cut -d " " -f 10)
echo "Key is \"$PUBLIC1\""

echo "Reading public key for instance2..."
RESULT_STRING=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --getPublicKey)
checkPreviousCommand "getPublicKey"
# Parse this from the human-readable string.
PUBLIC2=$(echo "$RESULT_STRING" | cut -d " " -f 10)
echo "Key is \"$PUBLIC2\""

echo "Make key 2 follow key 1"
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --startFollowing --publicKey "$PUBLIC1"
checkPreviousCommand "startFollowing"

echo "List followees"
CANONICAL1=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --canonicalizeKey --key "$PUBLIC1")
LIST=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --listFollowees)
requireSubstring "$LIST" "Following: $CANONICAL1"

LIST=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --listFollowee --publicKey "$CANONICAL1")
requireSubstring "$LIST" "Followee has 0 elements"

echo "Verify that the static HTML is generated"
rm -rf "$STATIC2"
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --htmlOutput --directory "$STATIC2"
checkPreviousCommand "htmlOutput"
checkFileExists "$STATIC2/index.html"
checkFileExists "$STATIC2/prefs.html"
checkFileExists "$STATIC2/utils.js"
checkFileExists "$STATIC2/user.html"
checkFileExists "$STATIC2/play.html"
checkFileExists "$STATIC2/recommending.html"
checkFileExists "$STATIC2/following.html"
checkFileExists "$STATIC2/generated_db.js"

# Post an update and refresh to make sure that the follower sees the update and can re-render the HTML output
echo "Generate test files"
IMAGE_FILE="/tmp/image_file"
rm -f "$IMAGE_FILE"
dd if=/dev/zero of="$IMAGE_FILE" bs=1K count=512
checkPreviousCommand "dd image"
VIDEO_FILE="/tmp/video_file"
rm -f "$VIDEO_FILE"
dd if=/dev/zero of="$VIDEO_FILE" bs=1K count=2048
checkPreviousCommand "dd video"

echo "Publishing post..."
CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --publishSingleVideo --name "basic post" --description "no description" --thumbnailJpeg "$IMAGE_FILE" --videoFile "$VIDEO_FILE" --videoMime "video/webm" --videoHeight 640 --videoWidth 480
checkPreviousCommand "publishSingleVideo"

echo "Refresh followee"
REFRESH_OUTPUT=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --refreshFollowee --publicKey "$PUBLIC1")
requireSubstring "$REFRESH_OUTPUT" "-thumbnail 524.29 kB (524288 bytes)"
requireSubstring "$REFRESH_OUTPUT" "-leaf 2.10 MB (2097152 bytes)"
requireSubstring "$REFRESH_OUTPUT" "<1 Refresh successful!"

echo "Publish a post with an audio attachment, refresh the follower, and verify that we can see this..."
AUDIO_FILE="/tmp/audio_file"
rm -f "$AUDIO_FILE"
dd if=/dev/zero of="$AUDIO_FILE" bs=1K count=256
checkPreviousCommand "dd audio"
CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --publishToThisChannel --name "audio post" --description "this includes audio" --element --mime "audio/ogg" --file "$AUDIO_FILE"
checkPreviousCommand "publishToThisChannel"
REFRESH_OUTPUT=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --refreshFollowee --publicKey "$PUBLIC1")
requireSubstring "$REFRESH_OUTPUT" "Not pruning cache since 2.62 MB (2621440 bytes) is below target of 9.00 GB (9000000000 bytes)"
LIST_OUTPUT=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --listFollowee --publicKey "$PUBLIC1")
requireSubstring "$LIST_OUTPUT" "(image: (none), leaf: Qm"

echo "Make sure that cleaning cache won't do anything..."
CLEAN_OUTPUT=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --cleanCache)
requireSubstring "$CLEAN_OUTPUT" "Not pruning cache since 2.88 MB (2883584 bytes) is below target of 10.00 GB (10000000000 bytes)"

echo "Regenerate the static HTML"
rm -rf "$STATIC2"
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --htmlOutput --directory "$STATIC2"
checkPreviousCommand "htmlOutput"
checkFileExists "$STATIC2/index.html"
checkFileExists "$STATIC2/prefs.html"
checkFileExists "$STATIC2/utils.js"
checkFileExists "$STATIC2/user.html"
checkFileExists "$STATIC2/play.html"
checkFileExists "$STATIC2/recommending.html"
checkFileExists "$STATIC2/following.html"
checkFileExists "$STATIC2/generated_db.js"

echo "Verify that we can refresh the \"next\" followee, and that we do correctly try this only user..."
REFRESH_NEXT_OUTPUT=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --refreshNextFollowee)
requireSubstring "$REFRESH_NEXT_OUTPUT" "Refreshing followee IpfsKey($PUBLIC1)"
REFRESH_NEXT_OUTPUT=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --refreshNextFollowee 2>&1)
requireSubstring "$REFRESH_NEXT_OUTPUT" "Usage error in running command: Not following any users"

echo "Shrink the cache and force a cache cleaning to verify it doesn't break anything..."
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --setGlobalPrefs --followCacheTargetBytes 2000000
CLEAN_OUTPUT=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --cleanCache)
requireSubstring "$CLEAN_OUTPUT" "Pruning cache to 2.00 MB (2000000 bytes) from current size of 2.88 MB (2883584 bytes)"
# The second clean attempt should change nothing but report the remaining audio entry or nothing at all (since there is randomness in the eviction - it should at least be satisfed).
CLEAN_OUTPUT=$(CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --cleanCache)
# The size remaining here is either 262144 bytes or 0 bytes, depending on the order of eviction decisions, so we don't check that part of the line.
requireSubstring "$CLEAN_OUTPUT" "Not pruning cache since "
requireSubstring "$CLEAN_OUTPUT" " is below target of 2.00 MB (2000000 bytes)"

echo "Stop following and verify it is no longer in the list"
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --stopFollowing --publicKey "$PUBLIC1"
checkPreviousCommand "stopFollowing"
CACOPHONY_STORAGE="$USER2" java -Xmx32m -jar Cacophony.jar --listFollowees
checkPreviousCommand "listFollowees"

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
