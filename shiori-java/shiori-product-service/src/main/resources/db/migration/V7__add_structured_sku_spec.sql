ALTER TABLE p_sku
    ADD COLUMN display_name VARCHAR(255) NULL AFTER sku_no,
    ADD COLUMN spec_items_json VARCHAR(2048) NULL AFTER display_name,
    ADD COLUMN spec_signature CHAR(64) NULL AFTER spec_items_json;

UPDATE p_sku
SET display_name = NULLIF(TRIM(sku_name), '')
WHERE display_name IS NULL;

UPDATE p_sku s
SET s.spec_items_json = (
    CASE
        WHEN s.spec_json IS NOT NULL
            AND JSON_VALID(s.spec_json) = 1
            AND JSON_TYPE(CAST(s.spec_json AS JSON)) = 'OBJECT'
            AND JSON_LENGTH(CAST(s.spec_json AS JSON)) > 0
            THEN (
            SELECT JSON_ARRAYAGG(
                           JSON_OBJECT(
                                   'name', t.key_name,
                                   'value', JSON_UNQUOTE(JSON_EXTRACT(CAST(s.spec_json AS JSON), CONCAT('$.', t.key_name)))
                           )
                           ORDER BY t.key_name
                   )
            FROM JSON_TABLE(
                         JSON_KEYS(CAST(s.spec_json AS JSON)),
                         '$[*]' COLUMNS (
                             key_name VARCHAR(64) PATH '$'
                             )
                 ) t
        )
        ELSE JSON_ARRAY(
                JSON_OBJECT(
                        'name', '规格',
                        'value', COALESCE(NULLIF(TRIM(s.display_name), ''), '默认规格')
                )
             )
        END
    )
WHERE s.spec_items_json IS NULL;

UPDATE p_sku
SET spec_signature = SHA2(COALESCE(spec_items_json, '[]'), 256)
WHERE spec_signature IS NULL;

UPDATE p_sku
SET display_name = COALESCE(display_name, sku_name, '默认规格');

CREATE UNIQUE INDEX uk_p_sku_product_spec_signature_deleted
    ON p_sku (product_id, spec_signature, is_deleted);
