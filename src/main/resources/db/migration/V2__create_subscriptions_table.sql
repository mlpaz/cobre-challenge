CREATE TABLE subscriptions (
    subscription_id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    web_hook_url VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One webhook per (user, event type): subscribing again updates the URL
-- instead of adding a second, ambiguous destination for the same event type.
CREATE UNIQUE INDEX uq_subscriptions_user_event ON subscriptions (user_id, event_type);
