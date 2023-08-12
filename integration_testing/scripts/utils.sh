#!/bin/bash
# Common helpers used by the integration test scripts.

function requireSubstring()
{
	HAYSTACK="$1"
	NEEDLE="$2"
	if [[ "$HAYSTACK" =~ "$NEEDLE" ]]; then
		# Matched
		true
	else
		echo -e "\033[31;40mFailed to find \"$NEEDLE\" in \"$HAYSTACK\"\033[00m"
		exit 1
	fi
}

function checkPreviousCommand()
{
	CODE="$?"
	MESSAGE="$1"
	if [ $CODE != 0 ]; then
		echo -e "\033[31;40mERROR:  $MESSAGE\033[00m"
		exit 1
	fi
}

function checkFileExists()
{
	FILE="$1"
	if [ ! -f "$FILE" ]; then
		echo "File not found: \"$FILE\""
		exit 1
	fi
}

# Gets the path of where we want an IPFS instance's repository to be created.  Args:
# 1) instance_number
# Returns path via echo.
function getIpfsRepoPath()
{
	if [ $# -ne 1 ]; then
		echo "Missing arguments: instance_number"
		exit 1
	fi
	INSTANCE_NUMBER="$1"
	
	REPO_PATH="/tmp/repo.$INSTANCE_NUMBER"
	echo "$REPO_PATH"
}

# Sets up an IPFS instance.  Args:
# 1) path_to_ipfs
# 2) path_to_resources
# 3) instance_number
function setupIpfsInstance()
{
	if [ $# -ne 3 ]; then
		echo "Missing arguments: path_to_ipfs path_to_resources instance_number"
		exit 1
	fi
	PATH_TO_IPFS="$1"
	RESOURCES="$2"
	INSTANCE_NUMBER="$3"
	
	REPO_PATH=$(getIpfsRepoPath "$INSTANCE_NUMBER")
	rm -rf "$REPO_PATH"
	mkdir "$REPO_PATH"
	IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" init
	checkPreviousCommand "repo $INSTANCE_NUMBER init"
	cp "$RESOURCES/swarm.key" "$REPO_PATH/swarm.key"
	cp "$RESOURCES/node_config.$INSTANCE_NUMBER" "$REPO_PATH/config"
}

# Starts up an IPFS instance in the background.  Args:
# 1) path_to_ipfs
# 2) instance_number
# Returns PID of background process via variable RET (MUST not be invoked as a sub-shell since it starts a background process).
function startIpfsInstance()
{
	if [ $# -ne 2 ]; then
		echo "Missing arguments: path_to_ipfs instance_number"
		exit 1
	fi
	PATH_TO_IPFS="$1"
	INSTANCE_NUMBER="$2"
	
	REPO_PATH=$(getIpfsRepoPath "$INSTANCE_NUMBER")
	IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" daemon &
	RET="$!"
}

# Waits for the given IPFS instance to respond to the API port.  Args:
# 1) path_to_ipfs
# 2) instance_number
function waitForIpfsStart()
{
	if [ $# -ne 2 ]; then
		echo "Missing arguments: path_to_ipfs instance_number"
		exit 1
	fi
	PATH_TO_IPFS="$1"
	INSTANCE_NUMBER="$2"
	
	REPO_PATH=$(getIpfsRepoPath "$INSTANCE_NUMBER")
	
	RET=1
	ATTEMPTS=1
	while [[ "$RET" != 0 ]]
	do
		# We can't use an IPFS command since it may grab the repo lock before the actual daemon does so we will use curl to see if it is running (we just need to sleeze the port number).
		# This command will return 0 with "Permanently moved" if running, but return 7 if the daemon isn't yet listening.
		PORT="500$INSTANCE_NUMBER"
		curl -XGET http://127.0.0.1:$PORT/api/v0 >& /dev/null
		RET=$?
		
		# We only want to try this each second for 30 seconds - more than that and something is probably wrong (5 is usually overkill for our isolated tests).
		if [[ "$RET" != 0 ]]; then
			if [[ "$ATTEMPTS" == 30 ]]; then
				echo "Instance failed to respond after 30 seconds"
				exit 1
			else
				sleep 1
			fi
		fi
	done
	sleep 1
}

# Verifies that the IPFS swarm is working correctly by adding files until they can be seen across the network.
# This is to work around an intermittent failure observed in tests where the nodes sometimes come up connected to each other and able to agree on which node has data, but unable to actually move the data over the network.
# Args:
# 1) path_to_ipfs
# 2) node1_pid
# Returns the PID of node 1 (whether restarted or not) in RET.
function verifySwarmWorks()
{
	if [ $# -ne 2 ]; then
		echo "Missing arguments: path_to_ipfs node1_pid"
		exit 1
	fi
	P_PATH_TO_IPFS="$1"
	P_PID1="$2"
	P_REPO1=$(getIpfsRepoPath "1")
	P_REPO2=$(getIpfsRepoPath "2")
	
	# We first test upload to 1 and fetch from 2 (as this is the common pattern we use and the one which seems to fail).
	date > /tmp/date_test
	HASH_DATE=$(IPFS_PATH="$P_REPO1" "$P_PATH_TO_IPFS" add --quieter /tmp/date_test)
	IPFS_PATH="$P_REPO2" "$P_PATH_TO_IPFS" --timeout 1s cat "$HASH_DATE" >& /dev/null
	if [[ "$?" == 0 ]]; then
		echo "Swarm is correct (1->2)"
	else
		echo "Swarm check failed - restarting node 1..."
		IPFS_PATH="$P_REPO1" "$P_PATH_TO_IPFS" shutdown
		wait $P_PID1
		startIpfsInstance "$P_PATH_TO_IPFS" 1
		P_PID1="$RET"
		echo "Daemon 1: $P_PID1"

		waitForIpfsStart "$P_PATH_TO_IPFS" 1
		
		# Now, verify that we can fetch after restart (longer timeout since this is expected to pass).
		IPFS_PATH="$P_REPO2" "$P_PATH_TO_IPFS" --timeout 10s cat "$HASH_DATE" >& /dev/null
		if [[ "$?" == 0 ]]; then
			echo "Works after restart (1->2)"
		else
			echo "Still failing after restart!"
			exit 1
		fi
	fi
	
	# Now we verify that we can go from 2 to 1 (we give this one a longer timeout since this is expected to pass).
	echo "Constant" > /tmp/const_test
	HASH_CONST=$(IPFS_PATH="$P_REPO2" "$P_PATH_TO_IPFS" add --quieter /tmp/const_test)
	IPFS_PATH="$P_REPO1" "$P_PATH_TO_IPFS" --timeout 10s cat "$HASH_CONST" >& /dev/null
	if [[ "$?" == 0 ]]; then
		echo "Verified (2->1)"
	else
		echo "Reverse swarm stability failed!"
		exit 1
	fi
	RET="$P_PID1"
}

# Waits for the HTTP server on the given port to start.  Args:
# 1) port
function waitForHttpStart()
{
	if [ $# -ne 1 ]; then
		echo "Missing arguments: port"
		exit 1
	fi
	PORT="$1"
	
	RET=1
	ATTEMPTS=1
	while [[ "$RET" != 0 ]]
	do
		# This command will return 0 if running, but return 7 if the daemon isn't yet listening.
		curl --cookie "$COOKIES1" --cookie-jar "$COOKIES1" --no-progress-meter -XGET -L "http://127.0.0.1:$PORT/" >& /dev/null
		RET=$?
		
		# We only want to try this each second for 30 seconds - more than that and something is probably wrong (this is usually within 1 second).
		if [[ "$RET" != 0 ]]; then
			if [[ "$ATTEMPTS" == 30 ]]; then
				echo "Instance failed to respond after 30 seconds"
				exit 1
			else
				sleep 1
			fi
		fi
	done
}

# Creates a zero-file with the given size.  Args:
# 1) file path
# 2) size in KiB
function createBinaryFile()
{
	if [ $# -ne 2 ]; then
		echo "Missing arguments: file_path size_in_KiB"
		exit 1
	fi
	FILE_PATH="$1"
	SIZE_IN_KIB="$2"
	
	rm -f "$FILE_PATH"
	dd if=/dev/zero of="$FILE_PATH" bs=1K count=$SIZE_IN_KIB >& /dev/null
	checkPreviousCommand "dd"
}

# Requests a repo GC for the given IPFS instance  Args:
# 1) path_to_ipfs
# 2) instance_number
function requestIpfsGc()
{
	if [ $# -ne 2 ]; then
		echo "Missing arguments: path_to_ipfs instance_number"
		exit 1
	fi
	PATH_TO_IPFS="$1"
	INSTANCE_NUMBER="$2"
	
	REPO_PATH=$(getIpfsRepoPath "$INSTANCE_NUMBER")
	IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" repo gc >& /dev/null
}

# Looks up the current public key in the given server and returns it via echo, empty is none selected.  Args:
# 1) Cookies file path
# 2) Base URL of server
function getPublicKey()
{
	if [ $# -ne 2 ]; then
		echo "Missing arguments: cookies_path server_base_url"
		exit 1
	fi
	COOKIES="$1"
	SERVER_BASE="$2"
	
	# Split the JSON by the { and look for the selected user, returning the key segment if found (otherwise returns nothing).
	CHANNEL_LIST=$(curl --cookie "$COOKIES" --cookie-jar "$COOKIES"  --no-progress-meter -XGET "$SERVER_BASE/home/channels")
	echo "$CHANNEL_LIST" | sed 's/{/\n/g' | grep "\"isSelected\":true" | cut -d \" -f 8
}
