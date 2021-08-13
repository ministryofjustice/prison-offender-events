    {{/* vim: set filetype=mustache: */}}
{{/*
Environment variables for web and worker containers
*/}}
{{- define "deployment.envs" }}
env:
  - name: JAVA_OPTS
    value: "{{ .Values.env.JAVA_OPTS }}"

  - name: SPRING_PROFILES_ACTIVE
    value: "postgres,sns"

  - name: JWT_PUBLIC_KEY
    value: "{{ .Values.env.JWT_PUBLIC_KEY }}"

  - name: PRISON_API_BASE_URL
    value: "{{ .Values.env.PRISON_API_BASE_URL }}"

  - name: COMMUNITY_API_BASE_URL
    value: "{{ .Values.env.COMMUNITY_API_BASE_URL }}"

  - name: OAUTH_API_BASE_URL
    value: "{{ .Values.env.OAUTH_API_BASE_URL }}"

  - name: WIND_BACK_SECONDS
    value: "{{ .Values.env.WIND_BACK_SECONDS }}"

  - name: APPLICATION_LISTENER_TOTALDELAYDURATION
    value: "{{ .Values.env.APPLICATION_LISTENER_TOTALDELAYDURATION }}"

  - name: APPLICATION_LISTENER_DELAYDURATION
    value: "{{ .Values.env.APPLICATION_LISTENER_DELAYDURATION }}"

  - name: APPINSIGHTS_INSTRUMENTATIONKEY
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: APPINSIGHTS_INSTRUMENTATIONKEY

  - name: APPLICATIONINSIGHTS_CONNECTION_STRING
    value: "InstrumentationKey=$(APPINSIGHTS_INSTRUMENTATIONKEY)"

  - name: APPLICATIONINSIGHTS_CONFIGURATION_FILE
    value: "{{ .Values.env.APPLICATIONINSIGHTS_CONFIGURATION_FILE }}"

  - name: AWS_REGION
    value: "eu-west-2"

  - name: OFFENDER_EVENTS_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: OFFENDER_EVENTS_CLIENT_SECRET

  - name: OFFENDER_EVENTS_CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: OFFENDER_EVENTS_CLIENT_ID

  - name: DATABASE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: dps-rds-instance-output
        key: offender_events_password

  - name: SUPERUSER_USERNAME
    valueFrom:
      secretKeyRef:
        name: dps-rds-instance-output
        key: database_username

  - name: SUPERUSER_PASSWORD
    valueFrom:
      secretKeyRef:
        name: dps-rds-instance-output
        key: database_password

  - name: DATABASE_NAME
    valueFrom:
      secretKeyRef:
        name: dps-rds-instance-output
        key: database_name

  - name: DATABASE_ENDPOINT
    valueFrom:
      secretKeyRef:
        name: dps-rds-instance-output
        key: rds_instance_endpoint

  - name: HMPPS_SQS_TOPICS_PRISON_EVENT_TOPIC_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: offender-events-topic
        key: access_key_id

  - name: HMPPS_SQS_TOPICS_PRISON_EVENT_TOPIC_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: offender-events-topic
        key: secret_access_key

  - name: HMPPS_SQS_TOPICS_PRISON_EVENT_TOPIC_ARN
    valueFrom:
      secretKeyRef:
        name: offender-events-topic
        key: topic_arn

  - name: HMPPS_SQS_TOPICS_HMPPS_EVENT_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: hmpps-domain-events-topic
        key: access_key_id

  - name: HMPPS_SQS_TOPICS_HMPPS_EVENT_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: hmpps-domain-events-topic
        key: secret_access_key

  - name: HMPPS_SQS_TOPICS_HMPPS_EVENT_TOPIC_ARN
    valueFrom:
      secretKeyRef:
        name: hmpps-domain-events-topic
        key: topic_arn

  - name: HMPPS_SQS_QUEUES_PRISON_EVENT_QUEUE_NAME
    valueFrom:
      secretKeyRef:
        name: prisoner-offender-events-queue
        key: sqs_queue_name

  - name: HMPPS_SQS_QUEUES_PRISON_EVENT_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: prisoner-offender-events-queue
        key: access_key_id

  - name: HMPPS_SQS_QUEUES_PRISON_EVENT_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: prisoner-offender-events-queue
        key: secret_access_key

  - name: HMPPS_SQS_QUEUES_PRISON_EVENT_DLQ_NAME
    valueFrom:
      secretKeyRef:
        name: prisoner-offender-events-dlq
        key: sqs_queue_name

  - name: HMPPS_SQS_QUEUES_PRISON_EVENT_DLQ_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: prisoner-offender-events-dlq
        key: access_key_id

  - name: HMPPS_SQS_QUEUES_PRISON_EVENT_DLQ_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: prisoner-offender-events-dlq
        key: secret_access_key

{{- end -}}
