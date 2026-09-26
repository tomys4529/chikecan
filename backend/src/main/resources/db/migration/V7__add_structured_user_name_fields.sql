-- 氏名入力の再設計: usersとpending_registrationsへ構造化された氏名項目を追加する。
-- 既存のnameカラムは削除・NULL化せず、LEGACYユーザー・仮登録データの表示名として
-- そのまま維持する。name_formatは新規登録時のみJAPANESE/INTERNATIONALが設定され、
-- 既存行はすべて安全なデフォルト値LEGACYとして扱う(表示時は従来通りnameをそのまま使う)。
ALTER TABLE users ADD COLUMN name_format VARCHAR(20) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE users ADD COLUMN family_name VARCHAR(30);
ALTER TABLE users ADD COLUMN given_name VARCHAR(30);
ALTER TABLE users ADD COLUMN middle_name VARCHAR(30);

ALTER TABLE pending_registrations ADD COLUMN name_format VARCHAR(20) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE pending_registrations ADD COLUMN family_name VARCHAR(30);
ALTER TABLE pending_registrations ADD COLUMN given_name VARCHAR(30);
ALTER TABLE pending_registrations ADD COLUMN middle_name VARCHAR(30);
