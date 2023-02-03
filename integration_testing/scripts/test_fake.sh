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
COOKIES1=/tmp/cookies1
STATUS_OUTPUT=/tmp/status_output
STATUS_INPUT=/tmp/status_input

rm -rf "$USER1"
rm -rf "$USER2"
rm -f "$COOKIES1"
rm -f "$STATUS_OUTPUT" "$STATUS_INPUT"


# The Class-Path entry in the Cacophony.jar points to lib/ so we need to copy this into the root, first.
cp "$PATH_TO_JAR" Cacophony.jar

echo "Check the info from the basic interface..."
FOLLOWEES=$(CACOPHONY_ENABLE_FAKE_SYSTEM=1 java -Xmx1g -jar "Cacophony.jar" --listFollowees)
requireSubstring "$FOLLOWEES" "Following: z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV"
OUR_DESCRIPTION=$(CACOPHONY_ENABLE_FAKE_SYSTEM=1 java -Xmx1g -jar "Cacophony.jar" --readDescription)
requireSubstring "$OUR_DESCRIPTION" "-name: us"
THEIR_DESCRIPTION=$(CACOPHONY_ENABLE_FAKE_SYSTEM=1 java -Xmx1g -jar "Cacophony.jar" --readDescription --publicKey z5AanNVJCxnN4WUyz1tPDQxHx1QZxndwaCCeHAFj4tcadpRKaht3QxV)
requireSubstring "$THEIR_DESCRIPTION" "-name: them"


echo -e "\033[32;40mSUCCESS!\033[0m"
