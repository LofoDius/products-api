# products-api

## Catalog workspace

The Android catalog uses personal server preferences (`GET/PUT /catalog/preferences/{pageId}`),
root-scoped field catalogs (`GET/POST /category/{id}/fields`, `PUT /category/{id}/fields/{fieldId}`),
and two-phase transfers (`POST /catalog/move/prepare`, `POST /catalog/move/confirm`).
Preferences are keyed by the authenticated user; clients cannot select another user's preferences.
Field changes require ownership of the root category. Members may read its catalog.
Existing category responses remain compatible; definitions are resolved from shared IDs.

Legacy definitions are imported additively and idempotently on access. No old field IDs or record
values are removed. Duplicate names remain distinct. A conflicting legacy definition for one ID
is rejected before its import; it must be inspected explicitly. Back up the database before deployment.
`Category.fieldIds` is the connected field order; old categories without it keep using legacy references.

Record transfers preserve hidden values, require a fresh preview token, and save both category
documents in a MongoDB transaction. Standalone MongoDB is rejected without changing the record.
For the supplied Docker deployment, enable a single-node authenticated replica set with:

```sh
docker compose -f docker-compose.yml -f docker-compose.transactions.yml up -d --build
```

This preserves the existing `mongo_data` volume and initializes `rs0` idempotently. Do not delete
volumes during the upgrade. Run the command on the deployment host only after backing up the
database. For external MongoDB, use its replica-set URI instead. Tests use an embedded replica set.

Transfers inside a root tree are supported. Moving across independent root catalogs (including
extracting a folder into a new root) needs a separate field/access policy and is currently rejected.
