#!/bin/bash
DOCKERIMAGE=$1
PORT=$2
REGION=$3

docker run -it --rm -p ${PORT}:8082 -e REGION=${REGION}  \
-e AWS_ACCESS_KEY=$AWS_ACCESS_KEY \
-e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
$DOCKERIMAGE