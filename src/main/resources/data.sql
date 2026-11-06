INSERT INTO orders (id, customer_name, status, amount, order_date, expected_delivery_date) VALUES
(1001, 'Alfa S.r.l.', 'IN_PROGRESS', 12500.00, '2026-09-10', '2026-10-10'),
(1002, 'Beta S.p.A.', 'NEW', 4300.50, '2026-10-01', '2026-10-20'),
(1003, 'Gamma S.r.l.', 'SHIPPED', 890.00, '2026-09-15', '2026-09-30'),
(1004, 'Delta S.p.A.', 'IN_PROGRESS', 28750.00, '2026-09-20', '2026-10-08'),
(1005, 'Epsilon S.r.l.', 'CANCELLED', 1650.00, '2026-08-25', '2026-09-15'),
(1006, 'Zeta S.p.A.', 'IN_PROGRESS', 7200.00, '2026-10-02', '2026-10-25')
ON CONFLICT (id) DO NOTHING;
