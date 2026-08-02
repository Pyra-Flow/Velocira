# PostgreSQL backup and restore rehearsal

Run this procedure from a secured operations host. Backups must be encrypted at rest, retained according to the product data-retention policy, and tested at least quarterly.

Create a backup:

```sh
pg_dump --format=custom --no-owner --file=velocira-YYYYMMDD.dump "$DATABASE_URL"
```

Restore only into an empty, non-production rehearsal database:

```sh
createdb velocira_restore_check
pg_restore --clean --if-exists --no-owner --dbname=velocira_restore_check velocira-YYYYMMDD.dump
```

The rehearsal passes only after Flyway reports the expected schema version, a restricted test account can read its own project and document data, row counts are plausible, and the application health endpoint is green against the restored database. Record the backup identifier, duration, recovery-point age, result, and operator in the operations log. Never use production credentials in local shells or CI logs.
