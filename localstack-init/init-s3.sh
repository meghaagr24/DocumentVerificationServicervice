#!/bin/bash

# Set the AWS region
export AWS_DEFAULT_REGION=ap-south-1

# Create S3 bucket
awslocal s3 mb s3://document-verification-service

# Set bucket policy to allow public access (for development only)
awslocal s3api put-bucket-policy --bucket document-verification-service --policy '{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadGetObject",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::document-verification-service/*"
    }
  ]
}'

echo "S3 bucket 'document-verification-service' created successfully in ap-south-1 region"
