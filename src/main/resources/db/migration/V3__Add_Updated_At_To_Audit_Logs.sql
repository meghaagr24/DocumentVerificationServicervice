-- Add updated_at column to audit_logs table
ALTER TABLE audit_logs ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;