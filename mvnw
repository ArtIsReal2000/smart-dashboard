#!/bin/sh
# Lightweight Maven Wrapper for Unix
MAVEN_VERSION="3.9.6"
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION/apache-maven-$MAVEN_VERSION"

if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
    echo "[Smart-Dashboard] Bootstrapping Apache Maven $MAVEN_VERSION..."
    mkdir -p "$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"
    curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip" -o "/tmp/maven-$MAVEN_VERSION.zip"
    unzip -q "/tmp/maven-$MAVEN_VERSION.zip" -d "$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"
    rm -f "/tmp/maven-$MAVEN_VERSION.zip"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
