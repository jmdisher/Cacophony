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
}

