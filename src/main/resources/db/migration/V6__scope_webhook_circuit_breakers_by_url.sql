-- Circuit breaker rescoped from per-subscription (client_id, event_type) to
-- per (client_id, web_hook_url). A client can subscribe several event types
-- to the exact same webhook URL; scoped per subscription, each event type
-- accumulated failures independently, so a struggling webhook took longer
-- to trip (and kept getting called once per event type in the meantime)
-- than if all failures against it counted together.
--
-- Existing per-subscription scores have no meaningful way to merge into a
-- single per-webhook score, so this recreates the table instead of
-- migrating data -- every webhook starts back at its default healthy state.
DROP TABLE webhook_circuit_breakers;

CREATE TABLE webhook_circuit_breakers (
    webhook_circuit_breaker_id UUID PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    web_hook_url VARCHAR(2048) NOT NULL,
    success_score INTEGER NOT NULL DEFAULT 100,
    total_calls BIGINT NOT NULL DEFAULT 0,
    circuit_state VARCHAR(20) NOT NULL DEFAULT 'CLOSED',
    circuit_opened_at TIMESTAMP WITH TIME ZONE,
    half_open_calls INTEGER NOT NULL DEFAULT 0
);

-- One breaker per physical webhook a client has registered, regardless of
-- how many of that client's event types point to it.
CREATE UNIQUE INDEX uq_webhook_circuit_breakers_client_url ON webhook_circuit_breakers (client_id, web_hook_url);
