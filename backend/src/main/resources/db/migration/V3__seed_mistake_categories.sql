-- =====================================================================
-- V3: seed the SGI mistake catalogue.  ICBC / MTO / DMV rows are
-- intentionally EMPTY for now — we'll add them when we start supporting
-- those jurisdictions.  The application reads from this table at
-- runtime; no hot-updates needed.
--
-- Severity scale per SGI:
--   MINOR  — demerit only, doesn't fail the test by itself
--   MAJOR  — multiple = automatic fail
--   FAIL   — immediate fail (e.g. dangerous action, collision)
-- =====================================================================

-- ------------------------- SGI — Saskatchewan -------------------------
INSERT INTO mistake_categories (jurisdiction, category_name, severity, display_order, active) VALUES
  ('SGI', 'Failure to signal',                      'MINOR', 10, true),
  ('SGI', 'Improper lane position',                 'MINOR', 20, true),
  ('SGI', 'Rolling through stop sign',              'MINOR', 30, true),
  ('SGI', 'Speed variance (±10 km/h)',              'MINOR', 40, true),
  ('SGI', 'Weak mirror check',                      'MINOR', 50, true),
  ('SGI', 'Failure to shoulder check',              'MAJOR', 60, true),
  ('SGI', 'Unsafe lane change',                     'MAJOR', 70, true),
  ('SGI', 'Failure to yield right-of-way',          'MAJOR', 80, true),
  ('SGI', 'Improper turn',                          'MAJOR', 90, true),
  ('SGI', 'Missed stop sign / red light',           'FAIL',  100, true),
  ('SGI', 'Collision / near-miss',                  'FAIL',  110, true),
  ('SGI', 'Requires examiner intervention',         'FAIL',  120, true),
  ('SGI', 'Dangerous action',                       'FAIL',  130, true);

-- ------------------ ICBC — British Columbia (placeholder) ------------
-- Category names are the real SGI equivalents; replace / expand when we
-- ship the ICBC adapter.  Leaving the rows empty for now intentionally.
-- INSERT INTO mistake_categories (jurisdiction, ...) VALUES ('ICBC', ...);

-- ------------------ MTO — Ontario (placeholder) ----------------------
-- INSERT INTO mistake_categories (jurisdiction, ...) VALUES ('MTO', ...);

-- ------------------ DMV — US state (placeholder) ---------------------
-- INSERT INTO mistake_categories (jurisdiction, ...) VALUES ('DMV', ...);
