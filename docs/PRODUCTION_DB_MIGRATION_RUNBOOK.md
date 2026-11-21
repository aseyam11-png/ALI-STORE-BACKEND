# Production database migration and role separation

## Verified starting point

The Neon `production` branch currently has an empty `alistore` schema. The full V1–V22 SQL chain was executed successfully on the isolated `prod-schema-rehearsal-20261121` branch; V23 was also applied successfully on a separate child of the existing Preview branch. These rehearsals executed SQL directly and are not proof of Flyway checksum validation; the protected workflow below performs the real Flyway run.

The current production login role `alistore_app` has `CREATEDB`, `CREATEROLE`, and `BYPASSRLS`. Do not keep using that role as the application runtime identity.

## Required roles

Create two separate login roles in the production Neon branch. Generate both passwords in a password manager and enter them only in the Neon SQL editor. Never commit or paste them into GitHub source.

```sql
-- Run these statements separately in the Neon SQL Editor, connected to the production
-- branch as the current owner (alistore_app). Do not place them in one transaction:
-- the database-owner handoff should be independently verified before continuing.

CREATE ROLE alistore_migrator
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS
    PASSWORD '<SET_MIGRATOR_PASSWORD_IN_NEON_ONLY>';

CREATE ROLE alistore_runtime
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS
    PASSWORD '<SET_RUNTIME_PASSWORD_IN_NEON_ONLY>';

GRANT CONNECT ON DATABASE alistore TO alistore_migrator, alistore_runtime;

-- The current owner already has CREATEDB and CREATEROLE. It creates the role,
-- then grants itself SET ROLE access so ownership can be transferred. The new
-- migrator must remain NOCREATEDB/NOCREATEROLE/NOBYPASSRLS.
GRANT alistore_migrator TO alistore_app;

-- The database owner remains alistore_app during the schema/object transfer.
ALTER SCHEMA public OWNER TO alistore_migrator;
GRANT USAGE, CREATE ON SCHEMA public TO alistore_migrator;
GRANT USAGE ON SCHEMA public TO alistore_runtime;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- Preview already has tables and Flyway history; Production is currently empty.
-- Transfer every existing public table and sequence so the migrator can validate
-- and continue the existing Flyway history on Preview, and own all new objects.
DO $
DECLARE
    object_row RECORD;
BEGIN
    FOR object_row IN
        SELECT tablename FROM pg_tables WHERE schemaname = 'public'
    LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO alistore_migrator', object_row.tablename);
    END LOOP;
END $;

DO $
DECLARE
    object_row RECORD;
BEGIN
    FOR object_row IN
        SELECT sequence_name
        FROM information_schema.sequences
        WHERE sequence_schema = 'public'
    LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO alistore_migrator', object_row.sequence_name);
    END LOOP;
END $;

-- Grant runtime DML access to the existing objects after ownership transfer.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO alistore_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO alistore_runtime;

-- Ensure objects created by Flyway as alistore_migrator automatically grant DML
-- and sequence access to alistore_runtime.
ALTER DEFAULT PRIVILEGES FOR ROLE alistore_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO alistore_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE alistore_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO alistore_runtime;

-- PostgreSQL requires the executing role to own the database, be able to SET ROLE
-- to the new owner, and itself have CREATEDB. alistore_app already has CREATEDB.
ALTER DATABASE alistore OWNER TO alistore_migrator;

-- Remove the temporary SET ROLE membership after all ownership changes.
REVOKE alistore_migrator FROM alistore_app;

-- These verification queries must show:
-- database owner=alistore_migrator, public schema owner=alistore_migrator,
-- runtime has USAGE on public, but no CREATE/TRUNCATE/CREATE DATABASE/CREATEROLE.
SELECT
    current_database() AS database_name,
    pg_get_userbyid(datdba) AS database_owner
FROM pg_database
WHERE datname = current_database();

SELECT
    nspname,
    pg_get_userbyid(nspowner) AS schema_owner,
    has_schema_privilege('alistore_runtime', nspname, 'USAGE') AS runtime_usage,
    has_schema_privilege('alistore_runtime', nspname, 'CREATE') AS runtime_create
FROM pg_namespace
WHERE nspname = 'public';

SELECT
    rolname,
    rolsuper,
    rolcreatedb,
    rolcreaterole,
    rolcanlogin,
    rolbypassrls
FROM pg_roles
WHERE rolname IN ('alistore_app', 'alistore_migrator', 'alistore_runtime')
ORDER BY rolname;
```

Do not run this block until the two passwords have been generated and the target is explicitly confirmed as the Neon `production` branch. If any statement fails, stop and inspect the actual role/schema/database ownership before retrying; do not continue with a partial role setup.

After role creation, verify:
- `alistore_migrator` owns the database and `public` schema and can create/alter objects.
- `alistore_runtime` has DML and sequence privileges only; it cannot CREATE, TRUNCATE, or create databases/roles.
- `alistore_app` is no longer the database owner.

## GitHub Actions migration workflow

The `.github/workflows/database-migrations.yml` workflow is manual-only and must be dispatched from `main`. Configure GitHub Environments named `preview` and `production`; require a reviewer for `production`.

Set these environment secrets:

| GitHub Environment | Secret | Value |
|---|---|---|
| preview | `NEON_PREVIEW_DIRECT_URL` | Direct (non-pooled) URL for the intended Preview branch |
| preview | `NEON_PREVIEW_MIGRATOR_PASSWORD` | Password for `alistore_migrator` on Preview |
| production | `NEON_PRODUCTION_DIRECT_URL` | Direct (non-pooled) URL for the Neon production branch |
| production | `NEON_PRODUCTION_MIGRATOR_PASSWORD` | Password for `alistore_migrator` on Production |

The workflow uses `FLYWAY_USER=alistore_migrator`, checks that the URL is not pooled, runs `flyway:validate`, then `flyway:migrate`, then `flyway:info`. It does not deploy the application and has no `clean`, `baseline`, or `repair` command.

The repository's `main` branch was observed without required status checks or branch protection during this review. Before enabling the Production environment, require PR review and the relevant required CI checks on `main`, and configure the GitHub `production` environment with required reviewers. The workflow's branch condition alone is not a substitute for branch protection.

Do not set the migration URL or migrator password in Vercel runtime environment variables.

## Vercel runtime configuration

After the roles are provisioned, point the Backend Vercel environment at the runtime role:

- `DB_USERNAME=alistore_runtime`
- `DB_PASSWORD`: the runtime password stored in Vercel as a sensitive value
- `DB_URL`: the normal Neon pooled runtime connection string (the application runtime may use the pooler)

The production Spring profile disables Flyway at application startup. Development and test profiles keep the existing Flyway behavior.

After the new runtime role is deployed and health/auth smoke tests pass, retire the old role in a separate controlled cutover. The current Neon catalog shows `alistore_app` is a member of `neon_superuser`, but it does not have `ADMIN OPTION` on itself. Therefore do not assume a SQL session authenticated as `alistore_app` can change its own role attributes. Use a Neon administrative principal that has authority to alter this role:

```sql
ALTER ROLE alistore_app NOLOGIN NOCREATEDB NOCREATEROLE NOBYPASSRLS;
```

Do not perform this step before Vercel has switched to `alistore_runtime` and the new deployment is verified. If a suitably privileged Neon administrative principal is not available, leave this as an explicit HIGH security follow-up; do not attempt to work around it by granting additional privileges to `alistore_runtime`.

## Release sequence

1. Provision roles and privileges on the intended Neon branch.
2. Add the appropriate GitHub Environment secrets and production reviewer requirement.
3. Dispatch the migration workflow against Preview and verify Flyway V1–V23.
4. Dispatch the workflow against Production only after reviewing the exact commit, target branch, backup/restore readiness, and approval.
5. Deploy Backend with `DB_USERNAME=alistore_runtime`; verify `/actuator/health`, authentication, and read/write paths.
6. Disable the old `alistore_app` login only after the runtime cutover succeeds.

Flyway migration files remain immutable after application. If validation reports a checksum mismatch, stop and investigate; do not use `repair` to suppress drift.
