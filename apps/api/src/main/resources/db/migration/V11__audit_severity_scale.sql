-- La escala de severidad (INFO/WARNING/MODERATE/CRITICAL) reemplaza al binario
-- SUCCESS/FAILURE. Se renombra la columna y se convierten los valores previos:
-- SUCCESS -> INFO, FAILURE -> WARNING.
ALTER TABLE audit_log RENAME COLUMN outcome TO severity;

UPDATE audit_log SET severity = CASE
    WHEN severity = 'SUCCESS' THEN 'INFO'
    WHEN severity = 'FAILURE' THEN 'WARNING'
    ELSE 'INFO'
END;
