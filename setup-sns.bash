#!/usr/bin/env bash
aws --endpoint-url=http://localhost:4566 sns create-topic --name offender_events
aws --endpoint-url=http://localhost:4566 sns create-topic --name hmpps_domain_events
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name event_queue
aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/event_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[\"EXTERNAL_MOVEMENT_RECORD-INSERTED\", \"BOOKING_NUMBER-CHANGED\"]}"}'

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name case_note_dlq
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name case_note_queue
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes --queue-url "http://localhost:4566/queue/case_note_queue" --attributes '{"RedrivePolicy":"{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:case_note_dlq\"}"}'
aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/case_note_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[ \"GEN-OSE\", {\"prefix\": \"NEG\"}, {\"prefix\": \"KA\"} ] }"}'

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prison_to_probation_dlq
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prison_to_probation_queue
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes --queue-url "http://localhost:4566/queue/prison_to_probation_queue" --attributes '{"RedrivePolicy":"{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:prison_to_probation_dlq\"}"}'
aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/prison_to_probation_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[ \"EXTERNAL_MOVEMENT_RECORD-INSERTED\"] }"}'

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prisoner_offender_events_dlq
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prisoner_offender_events_queue
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes --queue-url "http://localhost:4566/queue/prisoner_offender_events_queue" --attributes '{"RedrivePolicy":"{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:prisoner_offender_events_dlq\"}"}'
aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/prisoner_offender_events_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[ \"OFFENDER_MOVEMENT-RECEPTION\", \"OFFENDER_MOVEMENT-DISCHARGE\", \"BOOKING_NUMBER-CHANGED\"] }"}'
