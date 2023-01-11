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
# 2) path_to_resources
# 3) instance_number
# Returns PID of background process via variable RET (MUST not be invoked as a sub-shell since it starts a background process).
function startIpfsInstance()
{
	if [ $# -ne 3 ]; then
		echo "Missing arguments: path_to_ipfs path_to_resources instance_number"
		exit 1
	fi
	PATH_TO_IPFS="$1"
	RESOURCES="$2"
	INSTANCE_NUMBER="$3"
	
	REPO_PATH=$(getIpfsRepoPath "$INSTANCE_NUMBER")
	IPFS_PATH="$REPO_PATH" "$PATH_TO_IPFS" daemon &
	RET="$!"
}

