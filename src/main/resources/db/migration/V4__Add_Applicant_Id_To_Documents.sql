-- Add applicant_id column to documents table
ALTER TABLE documents ADD COLUMN applicant_id VARCHAR(255);

-- Create a composite unique constraint on document_type_id and applicant_id
-- This ensures that each applicant can have only one document of each type
ALTER TABLE documents ADD CONSTRAINT uk_documents_type_applicant 
    UNIQUE (document_type_id, applicant_id);

-- Create an index on applicant_id for better query performance
CREATE INDEX idx_documents_applicant_id ON documents (applicant_id);
