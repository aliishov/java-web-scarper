ALTER TABLE core.admins DROP CONSTRAINT ck_admins_role;
ALTER TABLE core.admins
	ADD CONSTRAINT ck_admins_role CHECK (role IN ('ADMIN', 'SUPER_ADMIN'));

UPDATE core.admins
SET role = 'SUPER_ADMIN', updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT id FROM core.admins ORDER BY created_at ASC, id ASC LIMIT 1)
AND NOT EXISTS (SELECT 1 FROM core.admins WHERE role = 'SUPER_ADMIN');
