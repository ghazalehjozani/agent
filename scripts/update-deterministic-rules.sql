BEGIN;

WITH strategy_mapping(family, strategy_key) AS (
    VALUES
        ('LAYER',       'LAYER'),
        ('SEC',         'SECURITY'),
        ('SECURITY',    'SECURITY'),
        ('PATTERN',     'PATTERN'),
        ('DEP',         'DEP'),
        ('NAME',        'NAME'),
        ('API',         'API'),
        ('URL',         'URL'),
        ('REST',        'URL'),
        ('DATA',        'DATA'),
        ('MSG',         'MESSAGING'),
        ('OBS',         'OBSERVABILITY'),
        ('TRACE',       'OBSERVABILITY'),
        ('IDEMPOTENCY', 'IDEMPOTENCY'),
        ('IDEM',        'IDEMPOTENCY'),
        ('PKG',         'PKG')
), resolved AS (
    SELECT ar.id,
           mapping.strategy_key
    FROM architecture_rule ar
    JOIN strategy_mapping mapping
      ON regexp_replace(
             regexp_replace(upper(ar.rule_code), '[^A-Z0-9]', '', 'g'),
             '^.*?([A-Z]+)[0-9]+$',
             '\1'
         ) = mapping.family
)
UPDATE architecture_rule ar
SET rule_type = 'DETERMINISTIC',
    strategy_key = resolved.strategy_key
FROM resolved
WHERE ar.id = resolved.id
  AND (ar.rule_type IS DISTINCT FROM 'DETERMINISTIC'
       OR ar.strategy_key IS DISTINCT FROM resolved.strategy_key);

DO $$
DECLARE
    missing_count integer;
BEGIN
    SELECT count(*) INTO missing_count
    FROM architecture_rule
    WHERE regexp_replace(upper(rule_code), '[^A-Z0-9]', '', 'g')
          ~ '(LAYER|SEC|SECURITY|PATTERN|DEP|NAME|API|URL|REST|DATA|MSG|OBS|TRACE|IDEMPOTENCY|IDEM|PKG)[0-9]+$'
      AND (rule_type <> 'DETERMINISTIC'
           OR strategy_key IS NULL
           OR strategy_key = '');

    IF missing_count > 0 THEN
        RAISE EXCEPTION '% deterministic rules were not mapped', missing_count;
    END IF;
END $$;

COMMIT;

SELECT strategy_key, count(*) AS rule_count
FROM architecture_rule
WHERE rule_type = 'DETERMINISTIC'
GROUP BY strategy_key
ORDER BY strategy_key;
