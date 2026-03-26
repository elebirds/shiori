ALTER TABLE o_outbox_event
    ADD COLUMN aggregate_type VARCHAR(64) NOT NULL DEFAULT 'order' AFTER event_id,
    ADD COLUMN message_key VARCHAR(128) NOT NULL DEFAULT '' AFTER aggregate_id;

UPDATE o_outbox_event
SET aggregate_type = 'order',
    message_key = aggregate_id
WHERE aggregate_type = 'order'
  AND message_key = '';
