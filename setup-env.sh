#!/bin/bash
export JAVA_HOME="/c/Users/TalalAlhawaj/.jdks/openjdk-25.0.2"
export PATH="$JAVA_HOME/bin:$PATH"
export PATH="/c/Users/TalalAlhawaj/Desktop/Discovery/plugins/maven/lib/maven3/bin:$PATH"
echo "✅ Java and Maven added to PATH"
echo "Java version:"
java -version
echo ""
echo "Maven version:"
mvn -version
