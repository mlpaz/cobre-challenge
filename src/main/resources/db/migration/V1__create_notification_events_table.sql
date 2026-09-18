CREATE TABLE notification_events (
    notification_event_id UUID PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    event_delivery_date TIMESTAMP WITH TIME ZONE NOT NULL,
    delivery_status VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(255),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One current record per platform event per client: a replay updates the
-- existing row (see NotificationRecordJpaAdapter) instead of inserting a new
-- one, so this also guards against accidentally storing the same event twice.
CREATE UNIQUE INDEX uq_notification_events_client_event ON notification_events (client_id, event_id);

CREATE INDEX idx_notification_events_client_id ON notification_events (client_id);
CREATE INDEX idx_notification_events_delivery_status ON notification_events (delivery_status);
