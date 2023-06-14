#!/bin/bash
# This is a stress test used to verify that swarm stability works as expected on a given IPFS build.

BASEDIR=$(dirname $0)
source "$BASEDIR/utils.sh"


# START.
if [ $# -ne 2 ]; then
	echo "Missing arguments: path_to_ipfs path_to_resources"
	exit 1
fi
PATH_TO_IPFS="$1"
RESOURCES="$2"

REPO1=$(getIpfsRepoPath "1")
REPO2=$(getIpfsRepoPath "2")

ITERATION=0
while [[ true ]];
do
	echo "***** Start test (iteration $ITERATION)"
	setupIpfsInstance "$PATH_TO_IPFS" "$RESOURCES" 1
	setupIpfsInstance "$PATH_TO_IPFS" "$RESOURCES" 2
	
	startIpfsInstance "$PATH_TO_IPFS" 1
	PID1=$RET
	echo "Daemon 1: $PID1"
	startIpfsInstance "$PATH_TO_IPFS" 2
	PID2=$RET
	echo "Daemon 2: $PID2"
	
	waitForIpfsStart "$PATH_TO_IPFS" 1
	waitForIpfsStart "$PATH_TO_IPFS" 2
	
	verifySwarmWorks "$PATH_TO_IPFS" "$PID1"
	PID1="$RET"
	
	date > /tmp/test1
	echo "TESTING" > /tmp/test2
	
	echo "Upload files to node 1"
	HASH_DATE=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" add --quieter /tmp/test1)
	HASH_CONSTANT=$(IPFS_PATH="$REPO1" "$PATH_TO_IPFS" add --quieter /tmp/test2)
	echo "DATE: $HASH_DATE"
	echo "CONT: $HASH_CONSTANT"
	
	echo "Fetch files from node 1"
	IPFS_PATH="$REPO1" "$PATH_TO_IPFS" cat "$HASH_DATE"
	IPFS_PATH="$REPO1" "$PATH_TO_IPFS" cat "$HASH_CONSTANT"
	echo "Fetch files from node 2"
	IPFS_PATH="$REPO2" "$PATH_TO_IPFS" cat "$HASH_DATE"
	IPFS_PATH="$REPO2" "$PATH_TO_IPFS" cat "$HASH_CONSTANT"
	
	echo "Shutdown test"
	IPFS_PATH="$REPO1" "$PATH_TO_IPFS" shutdown
	IPFS_PATH="$REPO2" "$PATH_TO_IPFS" shutdown
	wait $PID1
	wait $PID2
	
	ITERATION=$((ITERATION+1))
done


