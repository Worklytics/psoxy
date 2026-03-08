#!/usr/bin/env bash

# helper script to quickly check the gRPC services in a jar file, which seems to be root cause
# of the gRPC issue in the cloud function

COLORSCHEME_SH="$(dirname "$0")/../../../tools/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# determine the jar file to check
VERSION=$1
if [ -z "$VERSION" ]; then
  printf "${ERR}ERROR: version is required${NC}\n"
  echo "Usage: $0 <version>"
  exit 1
fi

JAR_FILE=target/psoxy-gcp-${VERSION}.jar

printf "Checking grpc load balancer services in jar file: ${INFO}${JAR_FILE}${NC}\n"
printf " (this should contain long list, including 'io.grpc.internal.PickFirstLoadBalancerProvider')\n"
jar xf $JAR_FILE META-INF/services/io.grpc.LoadBalancerProvider

cat META-INF/services/io.grpc.LoadBalancerProvider
rm -rf META-INF
