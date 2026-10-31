-- =====================================================================
-- V2__seed_data.sql
-- Sample records. Explicit IDs keep FK references readable;
-- identity sequences are re-synced at the end.
-- =====================================================================

-- employees: 1 CEO → 2 managers → 4 sales reps
INSERT INTO shop.employees (employee_id, first_name, last_name, email, job_title, manager_id, hire_date, salary) VALUES
 (1, 'Grace',   'Hopper',   'grace.hopper@shop.example',   'CEO',                 NULL, '2018-01-15', 250000.00),
 (2, 'Alan',    'Turing',   'alan.turing@shop.example',    'Sales Manager East',  1,    '2019-03-01', 140000.00),
 (3, 'Ada',     'Lovelace', 'ada.lovelace@shop.example',   'Sales Manager West',  1,    '2019-06-10', 138000.00),
 (4, 'Linus',   'Park',     'linus.park@shop.example',     'Sales Rep',           2,    '2021-02-01',  72000.00),
 (5, 'Maria',   'Santos',   'maria.santos@shop.example',   'Sales Rep',           2,    '2022-08-15',  68000.00),
 (6, 'Kenji',   'Watanabe', 'kenji.watanabe@shop.example', 'Sales Rep',           3,    '2020-11-20',  75000.00),
 (7, 'Priya',   'Nair',     'priya.nair@shop.example',     'Sales Rep',           3,    '2023-04-03',  65000.00);

-- customers
INSERT INTO shop.customers (customer_id, first_name, last_name, email, phone, city, state, country, loyalty_tier, created_at) VALUES
 (1,  'John',    'Miller',    'john.miller@mail.example',    '312-555-0101', 'Chicago',       'IL', 'USA',    'GOLD',     '2023-01-10 09:15:00-06'),
 (2,  'Emma',    'Johnson',   'emma.johnson@mail.example',   '212-555-0102', 'New York',      'NY', 'USA',    'PLATINUM', '2022-11-02 14:30:00-05'),
 (3,  'Liam',    'Brown',     'liam.brown@mail.example',     '512-555-0103', 'Austin',        'TX', 'USA',    'SILVER',   '2023-05-21 11:00:00-05'),
 (4,  'Olivia',  'Davis',     'olivia.davis@mail.example',   '206-555-0104', 'Seattle',       'WA', 'USA',    'BRONZE',   '2024-02-14 08:45:00-08'),
 (5,  'Noah',    'Wilson',    'noah.wilson@mail.example',    '415-555-0105', 'San Francisco', 'CA', 'USA',    'GOLD',     '2023-07-30 16:20:00-07'),
 (6,  'Ava',     'Martinez',  'ava.martinez@mail.example',   '305-555-0106', 'Miami',         'FL', 'USA',    'BRONZE',   '2024-06-01 10:10:00-04'),
 (7,  'Lucas',   'Anderson',  'lucas.anderson@mail.example', '617-555-0107', 'Boston',        'MA', 'USA',    'SILVER',   '2023-09-12 13:05:00-04'),
 (8,  'Sophia',  'Thomas',    'sophia.thomas@mail.example',  NULL,           'Denver',        'CO', 'USA',    'BRONZE',   '2025-01-05 12:00:00-07'),
 (9,  'Mia',     'Chen',      'mia.chen@mail.example',       '416-555-0109', 'Toronto',       'ON', 'Canada', 'GOLD',     '2023-03-18 15:40:00-05'),
 (10, 'Ethan',   'Clarke',    'ethan.clarke@mail.example',   NULL,           'London',        NULL, 'UK',     'SILVER',   '2024-10-22 09:30:00+01'),
 (11, 'Isabella','Garcia',    'isabella.garcia@mail.example','312-555-0111', 'Chicago',       'IL', 'USA',    'BRONZE',   '2025-04-11 17:25:00-05'),
 (12, 'James',   'Lee',       'james.lee@mail.example',      '512-555-0112', 'Austin',        'TX', 'USA',    'BRONZE',   '2025-08-19 10:00:00-05');

-- categories
INSERT INTO shop.categories (category_id, name, description) VALUES
 (1, 'Electronics',  'Phones, laptops, audio and accessories'),
 (2, 'Books',        'Printed and e-books'),
 (3, 'Home & Kitchen','Appliances and kitchenware'),
 (4, 'Sports',       'Fitness and outdoor gear'),
 (5, 'Office',       'Office supplies and furniture');

-- products (one inactive, one out of stock, one never ordered)
INSERT INTO shop.products (product_id, sku, name, category_id, unit_price, stock_qty, is_active) VALUES
 (1,  'EL-1001', 'Wireless Noise-Cancelling Headphones', 1, 199.99,  45, TRUE),
 (2,  'EL-1002', '14" Ultrabook Laptop',                 1, 1299.00, 12, TRUE),
 (3,  'EL-1003', 'USB-C Charging Hub',                   1,  49.50, 150, TRUE),
 (4,  'EL-1004', 'Smartwatch Series 5',                  1, 329.00,   0, TRUE),
 (5,  'BK-2001', 'Designing Data-Intensive Applications',2,  45.99,  80, TRUE),
 (6,  'BK-2002', 'Clean Code',                           2,  39.99,  60, TRUE),
 (7,  'BK-2003', 'The Pragmatic Programmer',             2,  42.00,  55, TRUE),
 (8,  'HK-3001', 'Espresso Machine',                     3, 449.00,  20, TRUE),
 (9,  'HK-3002', 'Chef''s Knife 8"',                     3,  89.95,  70, TRUE),
 (10, 'HK-3003', 'Air Fryer 5.8qt',                      3, 119.00,  35, TRUE),
 (11, 'SP-4001', 'Yoga Mat Pro',                         4,  68.00,  90, TRUE),
 (12, 'SP-4002', 'Adjustable Dumbbell Set',              4, 299.00,  15, TRUE),
 (13, 'SP-4003', 'Trail Running Shoes',                  4, 135.00,  40, FALSE),
 (14, 'OF-5001', 'Ergonomic Office Chair',               5, 379.00,  18, TRUE),
 (15, 'OF-5002', 'Standing Desk Converter',              5, 229.00,   0, TRUE),
 (16, 'OF-5003', 'Mechanical Keyboard',                  5, 129.00,  60, TRUE);

-- orders (2025–2026, mixed statuses; customers 8 and 12 have no orders)
INSERT INTO shop.orders (order_id, customer_id, sales_rep_id, order_date, status, ship_city, notes) VALUES
 (1,  1,  4,    '2025-01-12', 'DELIVERED', 'Chicago',       NULL),
 (2,  2,  4,    '2025-01-20', 'DELIVERED', 'New York',      'Gift wrap'),
 (3,  3,  6,    '2025-02-03', 'DELIVERED', 'Austin',        NULL),
 (4,  5,  6,    '2025-02-28', 'DELIVERED', 'San Francisco', NULL),
 (5,  2,  5,    '2025-03-15', 'DELIVERED', 'New York',      NULL),
 (6,  4,  7,    '2025-04-02', 'CANCELLED', 'Seattle',       'Customer changed mind'),
 (7,  9,  NULL, '2025-04-18', 'DELIVERED', 'Toronto',       'Web order'),
 (8,  1,  4,    '2025-05-09', 'DELIVERED', 'Chicago',       NULL),
 (9,  7,  5,    '2025-06-21', 'DELIVERED', 'Boston',        NULL),
 (10, 10, NULL, '2025-07-04', 'DELIVERED', 'London',        'International shipping'),
 (11, 5,  6,    '2025-08-14', 'SHIPPED',   'San Francisco', NULL),
 (12, 6,  7,    '2025-09-01', 'DELIVERED', 'Miami',         NULL),
 (13, 2,  4,    '2025-10-10', 'DELIVERED', 'New York',      NULL),
 (14, 3,  6,    '2025-11-25', 'DELIVERED', 'Austin',        'Black Friday'),
 (15, 11, 5,    '2026-01-08', 'PAID',      'Chicago',       NULL),
 (16, 9,  NULL, '2026-02-14', 'SHIPPED',   'Toronto',       NULL),
 (17, 1,  4,    '2026-03-03', 'PAID',      'Chicago',       NULL),
 (18, 7,  5,    '2026-04-19', 'NEW',       'Boston',        NULL),
 (19, 5,  7,    '2026-05-27', 'NEW',       'San Francisco', NULL),
 (20, 2,  4,    '2026-06-30', 'PAID',      'New York',      'Priority');

-- order_items (unit_price = price at time of order)
INSERT INTO shop.order_items (order_id, product_id, quantity, unit_price, discount_pct) VALUES
 (1,  1,  1, 199.99,  0),  (1,  3,  2,  49.50,  0),
 (2,  2,  1, 1249.00, 5),  (2,  5,  1,  45.99,  0),  (2,  6,  1,  39.99,  0),
 (3,  8,  1, 449.00,  0),  (3,  9,  1,  89.95,  0),
 (4,  12, 1, 299.00,  0),  (4,  11, 2,  68.00, 10),
 (5,  14, 1, 379.00,  0),  (5,  16, 1, 129.00,  0),
 (6,  4,  1, 329.00,  0),
 (7,  5,  2,  45.99,  0),  (7,  7,  1,  42.00,  0),
 (8,  10, 1, 119.00,  0),  (8,  3,  1,  49.50,  0),
 (9,  13, 1, 135.00,  0),  (9,  11, 1,  68.00,  0),
 (10, 6,  3,  39.99, 15),
 (11, 2,  1, 1299.00, 0),  (11, 1,  1, 189.99,  0),
 (12, 9,  2,  89.95,  0),
 (13, 15, 1, 229.00,  0),  (13, 14, 1, 359.00,  0),
 (14, 1,  2, 159.99,  0),  (14, 4,  1, 279.00,  0),  (14, 10, 1,  99.00,  0),
 (15, 16, 1, 129.00,  0),  (15, 3,  3,  49.50,  0),
 (16, 8,  1, 449.00,  0),
 (17, 2,  1, 1299.00, 0),
 (18, 7,  1,  42.00,  0),  (18, 5,  1,  45.99,  0),
 (19, 12, 1, 299.00,  0),
 (20, 1,  1, 199.99,  0),  (20, 16, 2, 129.00,  0);

-- payments (paid/shipped/delivered orders; order 2 paid in two parts)
INSERT INTO shop.payments (order_id, amount, method, paid_at) VALUES
 (1,  298.99, 'CARD',      '2025-01-12 10:05:00-06'),
 (2,  1000.00,'CARD',      '2025-01-20 11:00:00-05'),
 (2,  272.53, 'GIFT_CARD', '2025-01-20 11:02:00-05'),
 (3,  538.95, 'PAYPAL',    '2025-02-03 09:30:00-06'),
 (4,  421.40, 'CARD',      '2025-02-28 18:45:00-08'),
 (5,  508.00, 'BANK',      '2025-03-16 08:00:00-05'),
 (7,  133.98, 'PAYPAL',    '2025-04-18 20:10:00-05'),
 (8,  168.50, 'CARD',      '2025-05-09 12:00:00-06'),
 (9,  203.00, 'CARD',      '2025-06-21 15:15:00-05'),
 (10, 101.97, 'PAYPAL',    '2025-07-04 10:00:00+01'),
 (11, 1488.99,'CARD',      '2025-08-14 13:30:00-07'),
 (12, 179.90, 'CARD',      '2025-09-01 09:00:00-05'),
 (13, 588.00, 'BANK',      '2025-10-11 08:30:00-05'),
 (14, 697.98, 'CARD',      '2025-11-25 00:15:00-06'),
 (15, 277.50, 'CARD',      '2026-01-08 14:00:00-06'),
 (16, 449.00, 'PAYPAL',    '2026-02-14 19:45:00-05'),
 (17, 1299.00,'CARD',      '2026-03-03 10:20:00-06'),
 (20, 457.99, 'CARD',      '2026-06-30 16:00:00-05');

-- Re-sync identity sequences after explicit-ID inserts
SELECT setval(pg_get_serial_sequence('shop.employees',   'employee_id'),   (SELECT MAX(employee_id)   FROM shop.employees));
SELECT setval(pg_get_serial_sequence('shop.customers',   'customer_id'),   (SELECT MAX(customer_id)   FROM shop.customers));
SELECT setval(pg_get_serial_sequence('shop.categories',  'category_id'),   (SELECT MAX(category_id)   FROM shop.categories));
SELECT setval(pg_get_serial_sequence('shop.products',    'product_id'),    (SELECT MAX(product_id)    FROM shop.products));
SELECT setval(pg_get_serial_sequence('shop.orders',      'order_id'),      (SELECT MAX(order_id)      FROM shop.orders));
SELECT setval(pg_get_serial_sequence('shop.order_items', 'order_item_id'), (SELECT MAX(order_item_id) FROM shop.order_items));
SELECT setval(pg_get_serial_sequence('shop.payments',    'payment_id'),    (SELECT MAX(payment_id)    FROM shop.payments));
