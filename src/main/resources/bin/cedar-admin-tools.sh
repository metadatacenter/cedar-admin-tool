#!/bin/sh
clear
echo ----------------------------------------------
echo Launching CEDAR Admin tools ${version}-SNAPSHOT
echo ----------------------------------------------
echo
DIRNAME=`dirname "$0"`

java -jar $DIRNAME/cedar-admin-tools-${version}.jar "$@"