-- V2__add_client_ip_to_api_call_history.sql
-- Agrega la IP del cliente al historial de llamadas para auditoría.

ALTER TABLE api_call_history
    ADD COLUMN client_ip VARCHAR(45);

CREATE INDEX idx_history_client_ip ON api_call_history (client_ip);

COMMENT ON COLUMN api_call_history.client_ip IS 'IP del cliente (X-Forwarded-For si existe, sino remote address)';
