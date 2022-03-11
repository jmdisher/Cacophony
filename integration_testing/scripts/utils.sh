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
