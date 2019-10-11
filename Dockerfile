FROM openjdk:11-slim
LABEL maintainer="HMPPS Digital Studio <info@digital.justice.gov.uk>"

RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

ENV TZ=Europe/London
RUN ln -snf "/usr/share/zoneinfo/$TZ" /etc/localtime && echo "$TZ" > /etc/timezone

RUN addgroup --gid 2000 --system appgroup && \
    adduser --uid 2000 --system appuser --gid 2000

# Install AWS RDS Root cert into Java truststore
RUN mkdir /home/appuser/.postgresql \
  && curl https://s3.amazonaws.com/rds-downloads/rds-ca-2015-root.pem \
    > /home/appuser/.postgresql/root.crt

WORKDIR /app

COPY --chown=appuser:appgroup build/libs/offender-events*.jar /app/app.jar
COPY --chown=appuser:appgroup run.sh /app

USER 2000

ENTRYPOINT ["/bin/sh", "/app/run.sh"]
