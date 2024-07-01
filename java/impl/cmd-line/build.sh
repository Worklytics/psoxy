#!/usr/bin/env bash

mvn clean -f ../../pom.xml
mvn package install -f "../../gateway-core/pom.xml" -Dmaven.test.skip=true
mvn package install -f "../../core/pom.xml" -Dmaven.test.skip=true
mvn package -f "pom.xml"
