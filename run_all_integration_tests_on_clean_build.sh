#!/bin/bash
# This is just a helper script to automate the usual build and integration test process.

BASEDIR=$(dirname $0)
source "$BASEDIR/integration_testing/scripts/utils.sh"

echo -e "\033[34;40mBuilding with ant...\033[00m"
ant
checkPreviousCommand "ant"

echo -e "\033[32;40mAnt build success!\033[0m"

echo -e "\033[34;40mRunning integration tests...\033[00m"

for ENTRY in `ls ./integration_testing/scripts/test_*.sh`
do
	echo -e "\033[34;40m$ENTRY /mnt/data/ipfs/kubo/ipfs ./integration_testing/resources/ ./build/Cacophony.jar\033[00m"
	"$ENTRY" "/mnt/data/ipfs/kubo/ipfs" "./integration_testing/resources/" "./build/Cacophony.jar"
	checkPreviousCommand "$ENTRY"
done

echo -e "\033[32;40mCOMPLETE SUCCESS!\033[0m"
