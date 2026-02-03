-- Add paired_client_id column to link frontend and backend clients
ALTER TABLE kc_clients ADD COLUMN paired_client_id UUID REFERENCES kc_clients(id) ON DELETE SET NULL;

-- Create index for looking up paired clients
CREATE INDEX idx_kc_clients_paired_client_id ON kc_clients(paired_client_id);

-- Add comment explaining the relationship
COMMENT ON COLUMN kc_clients.paired_client_id IS 'Links frontend and backend clients together (e.g., app-web paired with app-backend)';
