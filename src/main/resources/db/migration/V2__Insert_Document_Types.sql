-- Insert predefined document types
INSERT INTO document_types (name, description, validation_rules) VALUES 
('AADHAAR', 'Government-issued identity document with 12-digit unique number', '{
    "required_fields": ["aadhaar_number", "name", "date_of_birth", "gender", "address"],
    "patterns": {
        "aadhaar_number": "^[0-9]{12}$",
        "date_of_birth": "^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$"
    }
}'),
('PAN', 'Permanent Account Number card for tax identification', '{
    "required_fields": ["pan_number", "name", "fathers_name", "date_of_birth"],
    "patterns": {
        "pan_number": "^[A-Z]{5}[0-9]{4}[A-Z]{1}$",
        "date_of_birth": "^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$"
    }
}'),
('DRIVING_LICENSE', 'State-issued driving authorization document', '{
    "required_fields": ["license_number", "name", "date_of_birth", "address", "valid_from", "valid_until"],
    "patterns": {
        "date_of_birth": "^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$",
        "valid_from": "^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$",
        "valid_until": "^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$"
    }
}'),
('BANK_STATEMENT', 'Financial institution account statement', '{
    "required_fields": ["account_number", "account_holder_name", "bank_name", "statement_period", "opening_balance", "closing_balance"],
    "patterns": {
        "account_number": "^[0-9]{9,18}$"
    }
}');