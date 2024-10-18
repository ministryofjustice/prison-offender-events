# prison offender events
[![repo standards badge](https://img.shields.io/badge/endpoint.svg?&style=flat&logo=github&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fprison-offender-events)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-report/prison-offender-events "Link to report")
[![CircleCI](https://circleci.com/gh/ministryofjustice/prison-offender-events/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/prison-offender-events)
[![Docker Repository on Quay](https://img.shields.io/badge/quay.io-repository-2496ED.svg?logo=docker)](https://quay.io/repository/hmpps/prison-offender-events)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://offender-events-dev.prison.service.justice.gov.uk/swagger-ui/index.html)
[![Event docs](https://img.shields.io/badge/Event_docs-view-85EA2D.svg)](https://studio.asyncapi.com/?url=https://raw.githubusercontent.com/ministryofjustice/prison-offender-events/main/async-api.yml&readOnly)
Publishes Events about prison offender changes to Pub / Sub Topics



## Running localstack
```bash
TMPDIR=/private$TMPDIR docker-compose up localstack
```

## Creating the Topic and Queue
Simplest way is running the following script
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

#### Smoke Tests

The source set `testSmoke` contains the smoke tests.

These tests are not intended to be run locally, but instead are run against a deployed application (as happens in the Circle build).

For more information on the smoke tests see the project `dps-smoketest`.
