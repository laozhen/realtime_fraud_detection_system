#!/bin/bash

echo "Initializing LocalStack resources..."

# Wait for LocalStack to be ready
sleep 5

# Create SQS queue
awslocal sqs create-queue \
    --queue-name fraud-detection-queue \
    --attributes VisibilityTimeout=30,MessageRetentionPeriod=86400

# Create Dead Letter Queue
awslocal sqs create-queue \
    --queue-name fraud-detection-dlq \
    --attributes MessageRetentionPeriod=1209600

# Get DLQ ARN
DLQ_ARN=$(awslocal sqs get-queue-attributes \
    --queue-url http://localhost:4566/000000000000/fraud-detection-dlq \
    --attribute-names QueueArn \
    --output text \
    --query 'Attributes.QueueArn')

# Configure redrive policy for main queue
awslocal sqs set-queue-attributes \
    --queue-url http://localhost:4566/000000000000/fraud-detection-queue \
    --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

echo "SQS queue 'fraud-detection-queue' created successfully with DLQ"

# List queues
awslocal sqs list-queues

echo "LocalStack initialization complete!"

