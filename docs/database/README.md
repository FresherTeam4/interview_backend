# Liquibase ownership and execution order

The master changelog runs authentication before core database changes:

1. `001-create-user-accounts.sql` — Thái Văn Trường
2. `002-create-refresh-tokens.sql` — Thái Văn Trường
3. `010-create-tech-stacks.sql` — Nguyễn Xuân Tùng
4. `011-create-questions.sql` — Nguyễn Xuân Tùng
5. `012-create-interview-sessions.sql` — Nguyễn Xuân Tùng
6. `013-create-reports.sql` — Nguyễn Xuân Tùng
7. `014-seed-sample-questions.sql` — Nguyễn Xuân Tùng

Use a clean database for the first Liquibase-managed run. If Hibernate previously created
tables in `event_hub_db`, do not run these create-table changesets over that schema. Either
recreate the local database when it contains no valuable data, or agree on a Liquibase
baseline procedure for a shared database.

`spring.jpa.hibernate.ddl-auto=validate` ensures JPA entities match the Liquibase schema.
Do not switch it back to `update` while Liquibase owns schema evolution.

Google OAuth controller/service implementation is outside these database changes. The
schema and entity only provide the required fields for account creation/linking.
