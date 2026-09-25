ALTER TABLE users ADD COLUMN experience INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT ck_users_experience_non_negative CHECK (experience >= 0);

ALTER TABLE tickets ADD COLUMN xp_awarded BOOLEAN NOT NULL DEFAULT FALSE;
