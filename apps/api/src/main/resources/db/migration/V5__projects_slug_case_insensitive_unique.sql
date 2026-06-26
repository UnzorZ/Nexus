-- Project slugs are treated as case-insensitive by the application
-- (findBySlugIgnoreCase / existsBySlugIgnoreCase). The column-level UNIQUE
-- constraint from V2 is case-sensitive, so "MyProj" and "myproj" could coexist.
-- This functional unique index enforces case-insensitive uniqueness at the DB,
-- matching the application's intended invariant.
--
-- NOTE: this migration fails if the database already contains case-colliding
-- slugs (e.g. both "F-Shop" and "f-shop"). On existing databases, deduplicate
-- slugs before applying. Fresh databases are unaffected.
CREATE UNIQUE INDEX uk_projects_slug_lower
    ON projects (LOWER(slug));
