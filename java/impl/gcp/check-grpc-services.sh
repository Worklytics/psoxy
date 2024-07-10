#!/usr/bin/env bash

# helper script to quickly check the gRPC services in a jar file, which seems to be root cause
# of the gRPC issue in the cloud function

# determine the jar file to check
VERSION=$1
JAR_FILE=target/psoxy-gcp-${VERSION}.jar

BLUE='\e[34m'
NC='\e[0m' # No Color

printf "Checking grpc load balancer services in jar file: ${BLUE}${JAR_FILE}${NC}\n"
printf " (as of 2024-07-10, good one contains 3 files, including 'io.grpc.internal.PickFirstLoadBalancerProvider')\n"
jar xf $JAR_FILE META-INF/services/io.grpc.LoadBalancerProvider

cat META-INF/services/io.grpc.LoadBalancerProvider
rm -rf META-INF
