-- Per-webhook circuit breaker state (see WebhookCircuitBreakerPort /
-- WebhookCircuitBreakerJpaAdapter). Lives on the subscription row itself:
-- one webhook's reliability score never affects any other client's.
ALTER TABLE subscriptions ADD COLUMN success_score INTEGER NOT NULL DEFAULT 100;
ALTER TABLE subscriptions ADD COLUMN total_calls BIGINT NOT NULL DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN circuit_state VARCHAR(20) NOT NULL DEFAULT 'CLOSED';
ALTER TABLE subscriptions ADD COLUMN circuit_opened_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE subscriptions ADD COLUMN half_open_calls INTEGER NOT NULL DEFAULT 0;
