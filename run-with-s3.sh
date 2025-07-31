#!/bin/bash

# Script to run the Document Verification Service with S3 storage

# Default values
PROFILE="local"
ACTION="run"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --profile)
      PROFILE="$2"
      shift 2
      ;;
    --action)
      ACTION="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Validate profile
if [[ "$PROFILE" != "local" && "$PROFILE" != "prod" ]]; then
  echo "Invalid profile: $PROFILE. Valid profiles are 'local' and 'prod'."
  exit 1
fi

# Validate action
if [[ "$ACTION" != "run" && "$ACTION" != "test" ]]; then
  echo "Invalid action: $ACTION. Valid actions are 'run' and 'test'."
  exit 1
fi

# If local profile, start LocalStack
if [[ "$PROFILE" == "local" ]]; then
  echo "Starting LocalStack..."
  docker-compose up -d localstack
  
  # Wait for LocalStack to be ready
  echo "Waiting for LocalStack to be ready..."
  sleep 5
  
  # Check if LocalStack is running
  if ! docker ps | grep -q localstack; then
    echo "LocalStack failed to start. Check docker logs for details."
    exit 1
  fi
  
  # Initialize S3 bucket using the init-s3.sh script
  echo "Initializing S3 bucket using localstack-init/init-s3.sh..."
  docker exec localstack /docker-entrypoint-initaws.d/init-s3.sh
fi

# Run the application or tests
if [[ "$ACTION" == "run" ]]; then
  echo "Running application with profile: $PROFILE"
  mvn spring-boot:run -Dspring-boot.run.profiles=$PROFILE -DskipTests
elif [[ "$ACTION" == "test" ]]; then
  echo "Running tests with profile: $PROFILE"
  mvn test -Dspring.profiles.active=$PROFILE
fi

# If local profile, stop LocalStack when done
if [[ "$PROFILE" == "local" && "$ACTION" == "run" ]]; then
  echo "Stopping LocalStack..."
  docker-compose down
fi
