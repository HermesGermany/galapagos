#!/bin/bash
#
# Simple startup script which builds and starts Galapagos e.g. for demo purposes (after running one of the setup
# scripts in the devsetup folder).
# This VERY SIMPLE implementation skips the build if target/galapagos-<version>.jar already exists, so do not use this
# script while developing on Galapagos (changing code).
#
# See README.md on how to run Galapagos from an IDE.
#

if [ ! -f "application-local.properties" ]
then
  echo "Please run one of the devsetup/setup-dev-*.sh scripts first."
  echo "Please read the README.md about the prerequisites for running Galapagos."
  exit 3
fi

# shellcheck disable=SC2016
GALA_VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
OUTPUT_JAR="target/galapagos-$GALA_VERSION.jar"

if [ ! -f "$OUTPUT_JAR" ]
then
  mvn clean package || exit 1
fi

# Check which flavour to run (ccloud or certificate based)
RUN_CCLOUD=$(grep -c apiKey < application-local.properties)

if [ "$RUN_CCLOUD" == "0" ]
then
  GALA_FLAVOUR=dev
else
  GALA_FLAVOUR=ccloud
fi

"$JAVA_HOME/bin/java" -cp "$OUTPUT_JAR" org.springframework.boot.loader.JarLauncher "--spring.profiles.active=local,$GALA_FLAVOUR"
