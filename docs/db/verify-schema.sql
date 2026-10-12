-- Read-only checks to run against the EXISTING Neon database BEFORE the one-time Flyway baseline.
-- Every query only reads catalog views. Expected result is stated above each query.

-- 1. All nine tables exist (expect 9 rows).
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('users','projects','project_members','issues','issue_comments',
                     'issue_assignees','labels','issue_labels','issue_history')
ORDER BY table_name;

-- 2. Column names, types and nullability the application's JPA mappings depend on.
--    Compare with the schema in task.txt; Hibernate (ddl-auto=validate) fails at startup on a mismatch.
SELECT table_name, column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('users','projects','project_members','issues','issue_comments',
                     'issue_assignees','labels','issue_labels','issue_history')
ORDER BY table_name, ordinal_position;

-- 3. Constraints (primary/unique/foreign/check). issue_labels must have two composite FKs with ON DELETE CASCADE.
SELECT conrelid::regclass AS table_name, conname, contype, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
ORDER BY conrelid::regclass::text, contype, conname;

-- 4. Indexes already present (the baseline must not recreate these).
SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public' ORDER BY tablename, indexname;

-- 5. set_updated_at() exists and is attached to users, projects, issues, issue_comments (expect 4 rows).
SELECT tgrelid::regclass AS table_name, tgname
FROM pg_trigger
WHERE NOT tgisinternal AND tgfoid = 'set_updated_at'::regproc
ORDER BY 1;

-- 6. No Flyway history yet (expect NULL). If it is not NULL, the database is already under Flyway management.
SELECT to_regclass('public.flyway_schema_history');

-- 7. Data that could violate rules the application assumes (all three should return 0).
SELECT count(*) AS users_with_uppercase_email FROM users WHERE email <> lower(email);
SELECT count(*) AS duplicate_emails_ignoring_case FROM (SELECT lower(email) FROM users GROUP BY 1 HAVING count(*) > 1) d;
SELECT count(*) AS projects_with_lowercase_key FROM projects WHERE project_key <> upper(project_key);
