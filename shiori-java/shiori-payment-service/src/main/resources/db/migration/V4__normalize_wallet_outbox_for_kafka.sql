ALTER TABLE p_wallet_balance_outbox
    ADD COLUMN aggregate_type VARCHAR(64) NOT NULL DEFAULT 'wallet' AFTER event_id,
    ADD COLUMN aggregate_id VARCHAR(64) NOT NULL DEFAULT '' AFTER aggregate_type,
    ADD COLUMN message_key VARCHAR(128) NOT NULL DEFAULT '' AFTER aggregate_id;

UPDATE p_wallet_balance_outbox
SET aggregate_type = 'wallet',
    aggregate_id = CAST(user_id AS CHAR),
    message_key = CAST(user_id AS CHAR)
WHERE aggregate_type = 'wallet'
  AND aggregate_id = ''
  AND message_key = '';
