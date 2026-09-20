-- Per-webhook circuit breaker state, previously columns on the subscriptions
-- row itself. Moved to its own table so the subscription record (what a
-- client subscribed to) and the circuit breaker record (the runtime health
-- of that webhook) are no longer coupled to the same row/table — a client
-- deleting or re-subscribing shouldn't require touching subscription-only
-- columns and circuit-breaker-only columns in the same write.
CREATE TABLE webhook_circuit_breakers (
    subscription_id UUID PRIMARY KEY REFERENCES subscriptions (subscription_id) ON DELETE CASCADE,
    success_score INTEGER NOT NULL DEFAULT 100,
    total_calls BIGINT NOT NULL DEFAULT 0,
    circuit_state VARCHAR(20) NOT NULL DEFAULT 'CLOSED',
    circuit_opened_at TIMESTAMP WITH TIME ZONE,
    half_open_calls INTEGER NOT NULL DEFAULT 0
);

INSERT INTO webhook_circuit_breakers (subscription_id, success_score, total_calls, circuit_state, circuit_opened_at, half_open_calls)
SELECT subscription_id, success_score, total_calls, circuit_state, circuit_opened_at, half_open_calls
FROM subscriptions;

ALTER TABLE subscriptions DROP COLUMN success_score;
ALTER TABLE subscriptions DROP COLUMN total_calls;
ALTER TABLE subscriptions DROP COLUMN circuit_state;
ALTER TABLE subscriptions DROP COLUMN circuit_opened_at;
ALTER TABLE subscriptions DROP COLUMN half_open_calls;
