#!/bin/bash
set -e

# Go to project root
cd "$(dirname "$0")"

echo ">>> Running mvn clean install -DskipTests ..."
mvn clean install -DskipTests

echo ">>> Starting retriever ..."
cd gestioncarte
java -jar target/retriever-9.4.0.jar
cd ..
