#!/usr/bin/env bash

# helper script to quickly check the gRPC services in a jar file, which seems to be root cause
# of the gRPC issue in the cloud function

RED='\e[0;31m'
BLUE='\e[34m'
NC='\e[0m' # No Color

# determine the jar file to check
VERSION=$1
if [ -z "$VERSION" ]; then
  printf "${RED}ERROR: version is required${NC}\n"
  echo "Usage: $0 <version>"
  exit 1
fi

JAR_FILE=target/psoxy-gcp-${VERSION}.jar


printf "Checking grpc load balancer services in jar file: ${BLUE}${JAR_FILE}${NC}\n"
printf " (this should contain long list, including 'io.grpc.internal.PickFirstLoadBalancerProvider')\n"
jar xf $JAR_FILE META-INF/services/io.grpc.LoadBalancerProvider

cat META-INF/services/io.grpc.LoadBalancerProvider
rm -rf META-INF
