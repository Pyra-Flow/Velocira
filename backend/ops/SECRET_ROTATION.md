# Secret rotation plan

All runtime secrets are supplied by the deployment secret store, never by Git. The required set is `DB_PASSWORD`, `POSTGRES_PASSWORD`, `JWT_SECRET`, mail credentials, and any OAuth client secret introduced later.

Rotate a suspected or expired secret immediately; otherwise rotate database and mail credentials at least every 90 days and signing keys at least every 180 days. Record the owner, rotation date, expiry, and incident reference in the deployment system—not in this repository.

For JWT signing-key rotation, deploy support for both the current and previous key first, issue new tokens with the current key, wait longer than the maximum refresh-token lifetime, then remove the previous key. Until multi-key verification is implemented, a JWT-secret rotation deliberately invalidates all sessions and must be announced as a maintenance event.

After every rotation: deploy to staging, verify login and health checks, deploy production, verify the previous secret no longer works where applicable, and remove the old secret from the secret store. If a secret was exposed, also revoke affected provider credentials and review audit logs.
