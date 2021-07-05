# prison offender events
Publishes Events about prison offender changes to Pub / Sub Topics

[![API docs](https://img.shields.io/badge/API_docs_(needs_VPN)-view-85EA2D.svg?logo=swagger)](https://offender-events-dev.prison.service.justice.gov.uk/swagger-ui.html)


## Running localstack and database
```bash
TMPDIR=/private$TMPDIR docker-compose up localstack prison-offender-events-db
```

## Creating the Topic and Queue
Simpliest way is running the following script
```bash
./setup-sns.bash
```

Or you can run the scripts individually as shown below.

## Creating a topic and queue on localstack

```bash
aws --endpoint-url=http://localhost:4566 sns create-topic --name offender_events
```

Results in:
```json
{
    "TopicArn": "arn:aws:sns:eu-west-2:000000000000:offender_events"
}

```

## Creating a queue
```bash
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name event_queue
```

Results in:
```json
{
   "QueueUrl": "http://localhost:4566/queue/event_queue"
}
```

## Creating a subscription
```bash
aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/event_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[\"EXTERNAL_MOVEMENT_RECORD-INSERTED\", \"BOOKING_NUMBER-CHANGED\"]}"}'
```

Results in:
```json
{
    "SubscriptionArn": "arn:aws:sns:eu-west-2:000000000000:offender_events:074545bd-393c-4a43-ad62-95b1809534f0"
}
```

## Read off the queue
```bash
aws --endpoint-url=http://localhost:4566 sqs receive-message --queue-url http://localhost:4566/queue/event_queue
```

## Running tests
The integration tests depend on localstack to be running to access the topics and test queues, this can be started with docker-compose

```bash
docker-compose -f docker-compose-test.yml up
```
