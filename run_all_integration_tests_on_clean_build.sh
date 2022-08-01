#!/bin/bash
# This is just a helper script to automate the usual build and integration test process.

BASEDIR=$(dirname $0)
source "$BASEDIR/integration_testing/scripts/utils.sh"

echo -e "\033[34;40mBuilding with ant...\033[00m"
ant
checkPreviousCommand "ant"

echo -e "\033[34;40mRunning integration tests...\033[00m"
./integration_testing/scripts/test_simple_resolve.sh /mnt/data/ipfs/go-ipfs/ipfs integration_testing/resources/ ./build/Cacophony.jar
checkPreviousCommand "test_simple_resolve.sh"
./integration_testing/scripts/test_follower.sh /mnt/data/ipfs/go-ipfs/ipfs integration_testing/resources/ ./build/Cacophony.jar
checkPreviousCommand "test_follower.sh"
./integration_testing/scripts/test_publish.sh /mnt/data/ipfs/go-ipfs/ipfs integration_testing/resources/ ./build/Cacophony.jar
checkPreviousCommand "test_publish.sh"
./integration_testing/scripts/test_interactive.sh /mnt/data/ipfs/go-ipfs/ipfs integration_testing/resources/ ./build/Cacophony.jar
checkPreviousCommand "test_interactive.sh"

echo -e "\033[32;40mCOMPLETE SUCCESS!\033[0m"
