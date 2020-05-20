# offender-events
Publishes Events about offender change to Pub / Sub Topics


## Running localstack and database
```bash
TMPDIR=/private$TMPDIR docker-compose up localstack offender-events-db
```

## Creating the Topic and Queue
Simpliest way is running the following script
```bash
./setup-sns.bash
```

Or you can run the scripts individually as shown below.

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
```bash
aws --endpoint-url=http://localhost:4576 sqs create-queue --queue-name event_queue
```

Results in:
```json
{
   "QueueUrl": "http://localhost:4576/queue/event_queue"
}
```

## Creating a subscription
```bash
aws --endpoint-url=http://localhost:4575 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4576/queue/event_queue \
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
aws --endpoint-url=http://localhost:4576 sqs receive-message --queue-url http://localhost:4576/queue/event_queue
```

### Data Items in NOMIS mapped to Events

| Data | Event |
|------|-------|
| Prisoner Number |  OFFENDER-UPDATED |
|  Book Number |   BOOKING_NUMBER-CHANGED |
|  Booking Id | OFFENDER_BOOKING-INSERTED |
|  PNC / CRO | OFFENDER_IDENTIFIER-INSERTED |
|  Cell Location |  BED_ASSIGNMENT_HISTORY-INSERTED | 
| Prison | EXTERNAL_MOVEMENT_RECORD-INSERTED |
|  Status | EXTERNAL_MOVEMENT_RECORD-INSERTED |
|  Category | ASSESSMENT-CHANGED |
|  CSRA |  ASSESSMENT-CHANGED |
|  DOB | OFFENDER_DETAILS-CHANGED |
|  Names | OFFENDER_DETAILS-CHANGED |
|  Aliases |  OFFENDER_ALIAS-CHANGED / OFFENDER_BOOKING-REASSIGNED |
|  Alerts | ALERT-INSERTED ALERT-UPDATED |
|  Gender | OFFENDER_DETAILS-CHANGED |
|  Ethnicity | OFFENDER-UPDATED |
|  Nationality | OFFENDER_PROFILE_DETAILS-UPDATED |
|  Religion | OFFENDER_PROFILE_DETAILS-INSERTED |
|  Marital Status | OFFENDER_PROFILE_DETAILS-UPDATED |
|  Youth Offender |  OFFENDER_PROFILE_DETAILS-UPDATED |
|  Legal Status | IMPRISONMENT_STATUS-CHANGED |
|  Release Dates | SENTENCE_DATES-CHANGED |
|  Confirmed Release Date |  CONFIRMED_RELEASE_DATE-CHANGED |