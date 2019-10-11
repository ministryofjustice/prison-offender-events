# offender-events
Publishes Events about offender change to Pub / Sub Topics


## Running localstack
```bash
TMPDIR=/private$TMPDIR docker-compose start localstack
```

## Running postgres
```bash
docker-compose start offender-events-db
```

## Creating a topic and queue on localstack

```bash
aws --endpoint-url=http://localhost:4575 sns create-topic --name offender_events
```

Results in:
```json
{
    "TopicArn": "arn:aws:sns:eu-west-2:000000000000:offender_events"
}

```

## Creating a queue
```aws --endpoint-url=http://localhost:4576 sqs create-queue --queue-name event_queue```

Results in:
```json
{
   "QueueUrl": "http://localhost:4576/queue/event_queue"
}
```

## Creating a subscription
```bash
aws --endpoint-url=http://localhost:4575 sns subscribe --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events --protocol sqs --notification-endpoint http://localhost:4576/queue/event_queue
```

Results in:
```json
{
    "SubscriptionArn": "arn:aws:sns:eu-west-2:000000000000:offender_events:074545bd-393c-4a43-ad62-95b1809534f0"
}
```

## Read off the queue
```bash
aws --endpoint-url=http://localhost:4576 sqs receive-message --queue-url http://localhost:4576/queue/event_queue
```