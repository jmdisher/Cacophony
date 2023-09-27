#!/bin/bash

# This script is just a tool to help with documentation and general insight into the shape of the codebase, but it is writting not to be too specific to this project.
# When given a root source directory and root package name, it will walk all source in the directory and build a GraphViz DOT graph of the package relationships under the root package, writing the source to STDOUT.
# If any additional arguments are provided, they are treated as packages to be collapsed.  This means that any sub-packages of these packages are treated as though they are in the top-level (useful for collapsing parts of the system which are internally organized, but their details are not relevant from the top-level).


if [ $# -lt 2 ]; then
        echo "Missing arguments: root_src_dir root_package [package_to_collapse]*"
        exit 1
fi

ROOT_DIR="$1"
ROOT_PACKAGE="$2"

# We want to build an array of all packages to collapse.
COLLAPSE=()
INDEX=2
ARGS=($@)
while [ $INDEX -lt $# ]; do
	NAME=${ARGS[INDEX]}
	COLLAPSE+=("$NAME")
	INDEX=$((INDEX+1))
done

# We are searching for the package declaration and import lines.  We are assuming only a single space between.
PACKAGE_PREFIX="package $ROOT_PACKAGE"
IMPORT_PREFIX="import $ROOT_PACKAGE"

# We will collect all the relationships in a set (since we can find them multiple times) so define an associative array.
declare -A SET

# Walk all the Java source files from the root directory.
for FILE in `find "$ROOT_DIR" -name "*.java"`; do
	# Look for the package prefix, knowing that it might be a direct match for our root pacakge.
	TOP=`grep "^$PACKAGE_PREFIX" "$FILE" | sed -e "s/^$PACKAGE_PREFIX//" -e "s/;$//"`
	SOURCE=""
	if [ -z "$TOP" ]; then
		SOURCE="ROOT"
	else
		SOURCE=`echo "$TOP" | cut -d . -f 2-`
	fi
	
	# See if we want to collapse this one.
	for CHECK in "${COLLAPSE[@]}"; do
		if [[ "$SOURCE" == "$CHECK"* ]]; then
			SOURCE="$CHECK"
		fi
	done
	
	# Walk all the import statements.
	for SEGMENT in `grep "$IMPORT_PREFIX" $FILE | sed -e "s/^$IMPORT_PREFIX\.//" -e "s/;$//" | sort | uniq`; do
		# Assume we want to get rid of the last name (since that would be the class - this assumes that we don't include inner classes, directly).
		# Note that this might also be a reference to the root package, so handle that as a special case.
		COUNT=`echo "$SEGMENT" | sed "s/\./\\n/g" | wc -l`
		COUNT=$((COUNT-1))
		TARGET=""
		if [ "$COUNT" -eq "0" ]; then
			TARGET="ROOT"
		else
			TARGET=`echo "$SEGMENT" | cut -d . -f 1-$COUNT`
		fi
		
		# See if we want to collapse this one.
		for CHECK in "${COLLAPSE[@]}"; do
			if [[ "$TARGET" == "$CHECK"* ]]; then
				TARGET="$CHECK"
			fi
		done
		
		# Build the association we will be writing into the DOT source and store it in the associative array.
		if [[ "$SOURCE" != "$TARGET" ]]; then
			KEY="\"$SOURCE\" -> \"$TARGET\""
			SET[${KEY}]="$KEY"
		fi
	done
done

# Output the DOT directed graph by walking the associative array.
echo "digraph {"
for ELEMENT in "${SET[@]}" ; do
	echo -e "\t$ELEMENT;"
done
echo "}"

