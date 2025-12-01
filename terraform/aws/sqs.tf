# Dead Letter Queue
resource "aws_sqs_queue" "fraud_detection_dlq" {
  name                      = local.dlq_name
  message_retention_seconds = 1209600  # 14 days
  
  tags = merge(
    var.tags,
    {
      Name = local.dlq_name
      Type = "DLQ"
    }
  )
}

# Main Queue
resource "aws_sqs_queue" "fraud_detection_queue" {
  name                      = local.queue_name
  visibility_timeout_seconds = var.sqs_visibility_timeout
  message_retention_seconds = var.sqs_message_retention
  receive_wait_time_seconds = 20  # Long polling
  
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.fraud_detection_dlq.arn
    maxReceiveCount     = var.sqs_max_receive_count
  })
  
  tags = merge(
    var.tags,
    {
      Name = local.queue_name
      Type = "Main"
    }
  )
}

# SQS Queue Policy
resource "aws_sqs_queue_policy" "fraud_detection_queue_policy" {
  queue_url = aws_sqs_queue.fraud_detection_queue.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowIAMRoleAccess"
        Effect = "Allow"
        Principal = {
          AWS = [
            aws_iam_role.fraud_detection_role.arn,
            aws_iam_role.transaction_producer_role.arn
          ]
        }
        Action = [
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.fraud_detection_queue.arn
      }
    ]
  })
}

