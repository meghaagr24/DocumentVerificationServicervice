# This is a template file for the backend configuration
# Replace the values with your actual values when initializing Terraform

bucket         = "nexus-applications-terraform-state-bucket"
key            = "document-verification-service/terraform.tfstate"
region         = "ap-south-1"
dynamodb_table = "terraform-locks"
encrypt        = true
