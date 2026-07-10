#!/bin/sh
# Gradle wrapper script
GRADLE_WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
JAVA_EXE="$JAVA_HOME/bin/java"
if [ -z "$JAVA_HOME" ]; then
  JAVA_EXE="java"
fi
exec "$JAVA_EXE" \
  -classpath "$GRADLE_WRAPPER_JAR" \
  -Dgradle.user.home="$HOME/.gradle" \
  org.gradle.wrapper.GradleWrapperMain "$@"
