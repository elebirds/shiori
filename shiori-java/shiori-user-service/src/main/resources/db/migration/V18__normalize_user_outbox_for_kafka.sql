ALTER TABLE u_outbox_event
    ADD COLUMN aggregate_type VARCHAR(64) NOT NULL DEFAULT 'user' AFTER event_id,
    ADD COLUMN message_key VARCHAR(128) NOT NULL DEFAULT '' AFTER aggregate_id;

UPDATE u_outbox_event
SET aggregate_type = 'user',
    message_key = aggregate_id
WHERE aggregate_type = 'user'
  AND message_key = '';
