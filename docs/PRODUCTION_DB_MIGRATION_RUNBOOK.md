# Production database migration and role separation

## Verified Production state (2026-11-22)

The Neon `production` branch is `br-shy-cherry-b9qv102l`. The `alistore` database currently has zero public application tables/relations; no Flyway migration has been run on Production.

The existing login role `alistore_app` still owns the `alistore` database and remains a member of `neon_superuser`. Its catalog attributes include `CREATEDB`, `CREATEROLE`, `BYPASSRLS`, and `REPLICATION`. Do not use it as the application runtime identity after cutover, and do not disable it until the replacement runtime is deployed and verified.

The following Production roles were provisioned through Neon on 2026-11-22, scoped to database `alistore` and schema `public`:

| Role | Neon access preset | Login/authentication | Schema privileges |
|---|---|---|---|
| `alistore_migrator` | `read_only` plus explicit schema ownership/CREATE | LOGIN, password | USAGE, CREATE; owns `public` |
| `alistore_runtime` | `read_write` | LOGIN, password | USAGE only; no CREATE |

Both roles were verified with `rolcanlogin=true`, `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOINHERIT`, `NOBYPASSRLS`, and `NOREPLICATION`. Both can connect to `alistore`. Direct role-scoped database checks confirmed the runtime role cannot CREATE in `public`.

Default privileges for objects created by `alistore_migrator` in `public` grant `SELECT, INSERT, UPDATE, DELETE` on tables and `USAGE, SELECT` on sequences to `alistore_runtime`. The default ACL contains no `TRUNCATE` grant. No production application tables existed when these grants were established.

The `public` schema owner is now `alistore_migrator`; the database owner remains `alistore_app`. This is intentional: PostgreSQL requires the new database owner to have `CREATEDB` for `ALTER DATABASE ... OWNER TO`. Giving `CREATEDB` to the migrator would violate the least-privilege requirement. Do not run `ALTER DATABASE alistore OWNER TO alistore_migrator`. See https://www.postgresql.org/docs/current/sql-alterdatabase.html.

The Neon role-creation API also created provider-managed memberships from `alistore_app` to the two limited roles, `alistore_migrator` and `alistore_runtime`, each with `ADMIN OPTION=true`, `INHERIT=false`, and `SET=false`, granted by `cloud_admin`. These memberships do not let `alistore_app` inherit or assume either limited role, but do allow it to administer membership. The provider-managed migrator membership remained after an attempted revoke; the runtime membership was observed but was not separately revoked. Do not claim either membership has been removed.

A separate live PostgreSQL 17 catalog read on 2026-11-22 found that the `alistore_app` membership in provider-managed `neon_superuser` has `ADMIN OPTION=false`, **`INHERIT=true` and `SET=true`**. Consequently, `alistore_app` inherits that role's grants and can `SET ROLE neon_superuser`. This corrects earlier wording that treated this membership as non-inheritable and non-settable. Although `neon_superuser` is not necessarily a PostgreSQL `SUPERUSER`, it carries provider-managed elevated privileges; `alistore_app` also has `CREATEDB`, `CREATEROLE`, `BYPASSRLS`, and `REPLICATION`. Treat this as a **HIGH, cutover-blocking security finding**. Do not run the application using `alistore_app`. Do not attempt to revoke or alter this Production membership from an application-role session. Resolution requires a Neon administrative principal or provider-supported operation and must be separately reviewed after a tested runtime-role cutover.

The schema ACL already showed PUBLIC with USAGE but no CREATE, so no additional PUBLIC CREATE revocation was needed. The `alistore_app` login and its elevated attributes have not been disabled or modified.

## Production role verification

Run these read-only checks before migration dispatch:

```sql
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
    rolname, rolcanlogin, rolsuper, rolcreatedb, rolcreaterole,
    rolinherit, rolbypassrls, rolreplication
FROM pg_roles
WHERE rolname IN ('alistore_app', 'alistore_migrator', 'alistore_runtime')
ORDER BY rolname;

SELECT
    pg_get_userbyid(m.roleid) AS granted_role,
    pg_get_userbyid(m.member) AS member_role,
    pg_get_userbyid(m.grantor) AS grantor,
    m.admin_option, m.inherit_option, m.set_option
FROM pg_auth_members m
WHERE m.member = (SELECT oid FROM pg_roles WHERE rolname = 'alistore_app')
ORDER BY granted_role;
```

Required before Flyway:
- `alistore_migrator` and `alistore_runtime` both have LOGIN and password authentication.
- Both new roles have all elevated attributes listed above set to false.
- `alistore_migrator` owns `public` and has CREATE there.
- `alistore_runtime` has USAGE but not CREATE on `public`.
- Migrator-owned table/sequence default privileges grant runtime DML and sequence USAGE/SELECT only.
- Do not proceed to application cutover while `alistore_app` still supplies the runtime credentials. Its existing elevated role and Neon-managed memberships require separate retirement review.

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

After the new runtime role is deployed and health/auth smoke tests pass, retire the old role in a separate controlled cutover. The current Neon catalog shows that `alistore_app` has `CREATEDB`, `CREATEROLE`, `BYPASSRLS`, and `REPLICATION`, and its Neon-managed `neon_superuser` membership has `INHERIT=true`, `SET=true`, and `ADMIN OPTION=false`. This is a HIGH security blocker: the account inherits the provider role's grants and can assume it. Only a Neon administrative principal or provider-supported operation can resolve this managed membership. Do not attempt to self-revoke it, and do not disable the login until the application runtime-role cutover has been tested and separately authorized. The following role-retirement SQL is a future cutover step only; it is not authorized for execution in this task.

```sql
ALTER ROLE alistore_app NOLOGIN NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION;
```

Do not perform this step before Vercel has switched to `alistore_runtime`, connection pools have been recycled, and the new deployment is verified. Afterward, separately verify whether the `neon_superuser` membership can be revoked by the Neon administrative principal. Do not try to revoke it from `alistore_app` itself or grant additional privileges to `alistore_runtime`. If that membership cannot be safely revoked, record it as a HIGH security follow-up.

## Release sequence

1. Provision roles and privileges on the intended Neon branch.
2. Add the appropriate GitHub Environment secrets and production reviewer requirement.
3. Dispatch the migration workflow against Preview and verify Flyway V1–V23.
4. Dispatch the workflow against Production only after reviewing the exact commit, target branch, backup/restore readiness, and approval.
5. Deploy Backend with `DB_USERNAME=alistore_runtime`; verify `/actuator/health`, authentication, and read/write paths.
6. Disable the old `alistore_app` login only after the runtime cutover succeeds.

Flyway migration files remain immutable after application. If validation reports a checksum mismatch, stop and investigate; do not use `repair` to suppress drift.
