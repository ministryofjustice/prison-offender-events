#!/bin/sh
exec java ${JAVA_OPTS} \
  -Djava.security.egd=file:/dev/./urandom \
  -javaagent:/app/agent.jar \
  -jar /app/app.jar
