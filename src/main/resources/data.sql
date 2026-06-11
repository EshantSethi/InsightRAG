-- Seed rows. Exact facts used both by the SQL tool at runtime and as ground truth in the eval.
INSERT INTO nimbus_release (component, version, release_date, status, min_java) VALUES
    ('nimbus-core',  '3.2.0', DATE '2024-11-05', 'stable', 17),
    ('nimbus-core',  '3.1.4', DATE '2024-06-18', 'stable', 17),
    ('nimbus-core',  '2.9.0', DATE '2023-02-10', 'lts',    11),
    ('nimbus-data',  '1.5.0', DATE '2024-09-22', 'stable', 17),
    ('nimbus-data',  '1.4.2', DATE '2024-03-30', 'stable', 17),
    ('nimbus-cache', '0.9.1', DATE '2024-08-14', 'beta',   17),
    ('nimbus-web',   '2.0.0', DATE '2024-10-01', 'stable', 17),
    ('nimbus-web',   '1.8.3', DATE '2024-02-12', 'stable', 17);
