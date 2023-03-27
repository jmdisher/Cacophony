#!/bin/bash

# NOTE:  This is NOT named with "test_" since we don't want to run it in the normal integration tests as it operates on real data, meaning it can be incredibly slow.
# That said, we do want a test to verify that this command works and demonstrate what it does.  Additionally, because this uses heavy data, we could use it as a perf test, of sorts.
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

rm -rf "$USER1"

# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar

# NOTE:  This needs to access the "real" network, so we don't set this up like our normal isolated test network.
REPO_PATH=$(getIpfsRepoPath 1)
rm -rf "$REPO_PATH"
mkdir "$REPO_PATH"
IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" init
checkPreviousCommand "repo init"

startIpfsInstance "$PATH_TO_IPFS" 1
PID1=$RET
echo "Daemon 1: $PID1"

echo "Pausing for startup..."
waitForIpfsStart "$PATH_TO_IPFS" 1

echo "Running quickstart..."
# Note that this instance uses the default CACOPHONY_IPFS_CONNECT so we won't specify it, to verify that the default is correctly applied.
CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --quickstart --name "Quick user"
checkPreviousCommand "createNewChannel"

echo "Verify that the demo channel is in the followee list"
LIST=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --listFollowees)
requireSubstring "$LIST" "Following: z5AanNVJCxnJ6qSdFeWsMDaivGJPPCVx8jiopn9jK7aUThhuQjhERku"

OUR_DESCRIPTION=$(CACOPHONY_STORAGE="$USER1" java -Xmx32m -jar Cacophony.jar --readDescription)
requireSubstring "$OUR_DESCRIPTION" "-name: Quick user"

kill $PID1

wait $PID1

echo -e "\033[32;40mSUCCESS!\033[0m"
