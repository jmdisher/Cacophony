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
TEMP_FILE=/tmp/temp_file

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$TEMP_FILE"

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

echo "Creating Cacophony instance1..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --createNewChannel
checkPreviousCommand "createNewChannel"

echo "Creating Cacophony instance2..."
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --createNewChannel
checkPreviousCommand "createNewChannel"

echo "Reading public key for instance1..."
RESULT_STRING=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --getPublicKey)
checkPreviousCommand "getPublicKey"
# Parse this from the human-readable string.
PUBLIC1=$(echo $RESULT_STRING | cut -d " " -f 14)
echo "Key is \"$PUBLIC1\""

echo "Reading public key for instance2..."
RESULT_STRING=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --getPublicKey)
checkPreviousCommand "getPublicKey"
# Parse this from the human-readable string.
PUBLIC2=$(echo $RESULT_STRING | cut -d " " -f 14)
echo "Key is \"$PUBLIC2\""

echo "Make key 2 follow key 1"
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --startFollowing --publicKey "$PUBLIC1"
checkPreviousCommand "startFollowing"

echo "List followees"
CANONICAL1=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --canonicalizeKey --key "$PUBLIC1")
LIST=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --listFollowees)
requireSubstring "$LIST" "Following: $CANONICAL1"

LIST=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --listFollowee --publicKey "$CANONICAL1")
requireSubstring "$LIST" "Followee has 0 elements"

DESCRIPTION_FOLLOWER=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --readDescription --publicKey $PUBLIC1)
requireSubstring "$DESCRIPTION_FOLLOWER" "Name: Unnamed"

# Post an update and refresh to make sure that the follower sees the update and can re-render the HTML output
echo "Generate test files"
IMAGE_FILE="/tmp/image_file"
createBinaryFile "$IMAGE_FILE" 512
VIDEO_FILE="/tmp/video_file"
createBinaryFile "$VIDEO_FILE" 2048

echo "Publishing post..."
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --publishSingleVideo --name "basic post" --description "no description" --thumbnailJpeg "$IMAGE_FILE" --videoFile "$VIDEO_FILE" --videoMime "video/webm" --videoHeight 640 --videoWidth 480
checkPreviousCommand "publishSingleVideo"

echo "Refresh followee"
REFRESH_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" CACOPHONY_VERBOSE="" java -Xmx32m -jar Cacophony.jar --refreshFollowee --publicKey "$PUBLIC1")
requireSubstring "$REFRESH_OUTPUT" "-thumbnail 524.29 kB (524288 bytes)"
requireSubstring "$REFRESH_OUTPUT" "-leaf 2.10 MB (2097152 bytes)"
requireSubstring "$REFRESH_OUTPUT" "<1< Refresh successful!"

echo "Publish a post with an audio attachment, refresh the follower, and verify that we can see this..."
AUDIO_FILE="/tmp/audio_file"
createBinaryFile "$AUDIO_FILE" 256
CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --publishToThisChannel --name "audio post" --description "this includes audio" --element --mime "audio/ogg" --file "$AUDIO_FILE"
checkPreviousCommand "publishToThisChannel"
REFRESH_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --refreshFollowee --publicKey "$PUBLIC1")
requireSubstring "$REFRESH_OUTPUT" "Not pruning cache since 2.62 MB (2621440 bytes) is below target of 9.00 GB (9000000000 bytes)"
LIST_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --listFollowee --publicKey "$PUBLIC1")
requireSubstring "$LIST_OUTPUT" "(image: (none), leaf: Qm"
# Capture the element we can rebroadcast.
ELEMENT_TO_REBROADCAST=$(echo "$LIST_OUTPUT" | head -2 | tail -1 | cut -d ' ' -f 4)

echo "Make sure that cleaning cache won't do anything..."
CLEAN_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --cleanCache)
requireSubstring "$CLEAN_OUTPUT" "Not pruning cache since 2.88 MB (2883584 bytes) is below target of 10.00 GB (10000000000 bytes)"

echo "Verify that we can refresh the \"next\" followee, and that we do correctly try this only user..."
REFRESH_NEXT_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --refreshNextFollowee)
requireSubstring "$REFRESH_NEXT_OUTPUT" "Refreshing followee IpfsKey($PUBLIC1)"
REFRESH_NEXT_OUTPUT=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --refreshNextFollowee 2>&1)
requireSubstring "$REFRESH_NEXT_OUTPUT" "Usage error in running command: Not following any users"

echo "Verify that failing to refresh a single record will result in us correctly seeing it once it is available (we will fix one but leave the other broken for unfollow checks)..."
# Make a basic post as user1.
OUTPUT=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --publishToThisChannel --name "NEW_POST" --description "empty description")
checkPreviousCommand "publishToThisChannel"
CID1=$(echo "$OUTPUT" | grep "Post with name" | cut -d \( -f 2 | cut -d \) -f 1)
OUTPUT=$(CACOPHONY_STORAGE="$USER1" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5001" java -Xmx32m -jar Cacophony.jar --publishToThisChannel --name "NEXT_POST" --description "empty description")
checkPreviousCommand "publishToThisChannel"
CID2=$(echo "$OUTPUT" | grep "Post with name" | cut -d \( -f 2 | cut -d \) -f 1)
# Capture the file locally, remove it from the server and GC the server (should make it fail to find).
REPO_PATH=$(getIpfsRepoPath "1")
IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" get --output "$TEMP_FILE" "$CID1" >& /dev/null
IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" pin rm "$CID1" >& /dev/null
IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" pin rm "$CID2" >& /dev/null
IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" repo gc >& /dev/null
# Refresh on user2.
echo "(note, the next few calls will time out, so this will appear to stall)"
REFRESH_NEXT_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --refreshNextFollowee)
requireSubstring "$REFRESH_NEXT_OUTPUT" "Refreshing followee IpfsKey($PUBLIC1)"
# Check that we don't know anything about this post.
LIST_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --listFollowee --publicKey "$PUBLIC1")
requireSubstring "$LIST_OUTPUT" "Element CID: $CID1 (not cached)"
requireSubstring "$LIST_OUTPUT" "Element CID: $CID2 (not cached)"
POST_DATA=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --showPost --elementCid "$CID1")
if [ $# -ne 3 ]; then
	echo -e "\033[31;40mERROR Expected033[00m"
	exit 1
fi
# Also verify that the local storage shows them missing.
SKIPPED_OPCODES=$(java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.OpcodeLogReader "$USER2/opcodes.v4.gzlog" | grep Opcode_SkipFolloweeRecord)
SKIPPED_COUNT=$(echo "$SKIPPED_OPCODES" | wc -l)
requireSubstring "$SKIPPED_OPCODES" "Opcode_SkipFolloweeRecord[recordCid=IpfsFile($CID1), isPermanent=false]"
requireSubstring "$SKIPPED_OPCODES" "Opcode_SkipFolloweeRecord[recordCid=IpfsFile($CID2), isPermanent=false]"
requireSubstring "$SKIPPED_COUNT" "2"
# Upload it back to the original server.
IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" add "$TEMP_FILE" >& /dev/null
# Re-run the refresh.
REFRESH_NEXT_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --refreshNextFollowee)
requireSubstring "$REFRESH_NEXT_OUTPUT" "Refreshing followee IpfsKey($PUBLIC1)"
# Check that the data is now available.
LIST_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --listFollowee --publicKey "$PUBLIC1")
requireSubstring "$LIST_OUTPUT" "Element CID: $CID1 (image: (none)"
requireSubstring "$LIST_OUTPUT" "Element CID: $CID2 (not cached)"
POST_DATA=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --showPost --elementCid "$CID1")
requireSubstring "$POST_DATA" "Cached state: Fully cached"
requireSubstring "$POST_DATA" "Name: NEW_POST"
# Also verify that the local storage now only has one skip.
SKIPPED_OPCODES=$(java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.OpcodeLogReader "$USER2/opcodes.v4.gzlog" | grep Opcode_SkipFolloweeRecord)
SKIPPED_COUNT=$(echo "$SKIPPED_OPCODES" | wc -l)
requireSubstring "$SKIPPED_OPCODES" "Opcode_SkipFolloweeRecord[recordCid=IpfsFile($CID2), isPermanent=false]"
requireSubstring "$SKIPPED_COUNT" "1"
POST_DATA=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --showPost --elementCid "$CID2")
if [ $# -ne 3 ]; then
	echo -e "\033[31;40mERROR Expected033[00m"
	exit 1
fi

echo "Shrink the cache and force a cache cleaning to verify it doesn't break anything..."
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --setGlobalPrefs --followeeCacheTargetBytes 2000000
CLEAN_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --cleanCache)
requireSubstring "$CLEAN_OUTPUT" "Pruning cache to 2.00 MB (2000000 bytes) from current size of 2.88 MB (2883584 bytes)"
# The second clean attempt should change nothing but report the remaining audio entry or nothing at all (since there is randomness in the eviction - it should at least be satisfed).
CLEAN_OUTPUT=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --cleanCache)
# The size remaining here is either 262144 bytes or 0 bytes, depending on the order of eviction decisions, so we don't check that part of the line.
requireSubstring "$CLEAN_OUTPUT" "Not pruning cache since "
requireSubstring "$CLEAN_OUTPUT" " is below target of 2.00 MB (2000000 bytes)"

echo "Stop following and verify it is no longer in the list"
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --stopFollowing --publicKey "$PUBLIC1"
checkPreviousCommand "stopFollowing"
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --listFollowees
checkPreviousCommand "listFollowees"
# We should now see no skipped opcodes.
SKIPPED_COUNT=$(java -Xmx32m -cp build/main:build/test:lib/* com.jeffdisher.cacophony.testutils.OpcodeLogReader "$USER2/opcodes.v4.gzlog" | grep Opcode_SkipFolloweeRecord | wc -l)
requireSubstring "$SKIPPED_COUNT" "0"

echo "Make sure that we can read the specific element"
SHOWN=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar "Cacophony.jar" --showPost --elementCid "$ELEMENT_TO_REBROADCAST")
requireSubstring "$SHOWN" "Name: basic post"
requireSubstring "$SHOWN" "Thumbnail: "
requireSubstring "$SHOWN" "Video: "
requireSubstring "$SHOWN" "Publisher: $PUBLIC1"

echo "Rebroadcast one of the elements from the followee and verify that we can see it in our list..."
# Note that the element we are rebroadcasting is version 2 so we need to enable new data model in order for it to be accepted (since this counts as "publishing" the post).
CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar Cacophony.jar --rebroadcast --elementCid "$ELEMENT_TO_REBROADCAST"
checkPreviousCommand "Rebroadcast"
LISTING=$(CACOPHONY_STORAGE="$USER2" CACOPHONY_IPFS_CONNECT="/ip4/127.0.0.1/tcp/5002" java -Xmx32m -jar "Cacophony.jar" --listChannel)
requireSubstring "$LISTING" "$ELEMENT_TO_REBROADCAST"

kill $PID1
kill $PID2

wait $PID1
wait $PID2

echo -e "\033[32;40mSUCCESS!\033[0m"
