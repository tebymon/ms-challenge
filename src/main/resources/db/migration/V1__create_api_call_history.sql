-- V1__create_api_call_history.sql
-- Tabla para historial de llamadas a la API

CREATE TABLE api_call_history (
    id          BIGSERIAL PRIMARY KEY,
    called_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    endpoint    VARCHAR(255) NOT NULL,
    http_method VARCHAR(10)  NOT NULL,
    parameters  JSONB,
    response    JSONB,
    error       TEXT,
    status_code INTEGER      NOT NULL,
    duration_ms BIGINT
);

-- Índice para consultas paginadas ordenadas por fecha (caso de uso principal)
CREATE INDEX idx_history_called_at ON api_call_history (called_at DESC);

-- Índice para filtrado por endpoint
CREATE INDEX idx_history_endpoint ON api_call_history (endpoint);

COMMENT ON TABLE api_call_history IS 'Historial asíncrono de todas las llamadas a los endpoints de la API';
COMMENT ON COLUMN api_call_history.parameters IS 'Parámetros recibidos en formato JSON';
COMMENT ON COLUMN api_call_history.response    IS 'Respuesta exitosa en formato JSON';
COMMENT ON COLUMN api_call_history.error       IS 'Mensaje de error si la llamada falló';
COMMENT ON COLUMN api_call_history.duration_ms IS 'Duración de la llamada en milisegundos';
