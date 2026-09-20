-- The service now calls each client's webhook directly (see WebhookHttpClient)
-- instead of going through an intermediate Notification Provider, so this
-- column holds the webhook's own response body instead of a provider-issued
-- reference. Widened to fit an arbitrary response (the application layer
-- still caps what it writes, see WebhookHttpAdapter#MAX_STORED_RESPONSE_LENGTH).
ALTER TABLE notification_events RENAME COLUMN provider_reference TO webhook_response;
ALTER TABLE notification_events ALTER COLUMN webhook_response TYPE VARCHAR(1000);
