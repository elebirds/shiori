ALTER TABLE p_outbox_event
    ADD COLUMN aggregate_type VARCHAR(64) NOT NULL DEFAULT 'product' AFTER event_id,
    ADD COLUMN message_key VARCHAR(128) NOT NULL DEFAULT '' AFTER aggregate_id;

UPDATE p_outbox_event
SET aggregate_type = 'product',
    message_key = aggregate_id
WHERE aggregate_type = 'product'
  AND message_key = '';
