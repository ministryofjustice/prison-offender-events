#!/usr/bin/env bash
export AWS_ACCESS_KEY_ID=foobar
export AWS_SECRET_ACCESS_KEY=foobar
export AWS_DEFAULT_REGION=eu-west-2

aws --endpoint-url=http://localhost:4566 sns create-topic --name offender_events
aws --endpoint-url=http://localhost:4566 sns create-topic --name hmpps_domain_events
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prisoner_offender_events_dlq
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prisoner_offender_events_queue
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes --queue-url "http://localhost:4566/queue/prisoner_offender_events_queue" --attributes '{"RedrivePolicy":"{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:prisoner_offender_events_dlq\"}"}'
aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/prisoner_offender_events_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[ \"OFFENDER_MOVEMENT-RECEPTION\", \"OFFENDER_MOVEMENT-DISCHARGE\"] }"}'

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name test-prison-event_queue
aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/test-prison-event_queue

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name test-hmpps-domain-event_queue
aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:hmpps_domain_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/test-hmpps-domain-event_queue

