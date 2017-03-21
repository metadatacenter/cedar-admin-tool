#!/bin/sh
clear
echo ----------------------------------------------
echo Launching CEDAR Admin Tool ${version}
echo ----------------------------------------------
echo
DIRNAME=`dirname "$0"`

java -jar ${DIRNAME}/cedar-admin-tool-${version}.jar "$@"