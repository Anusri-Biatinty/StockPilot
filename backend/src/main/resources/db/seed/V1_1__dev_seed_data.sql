-- =====================================================================
-- Development seed data (loaded only with the "dev" profile).
-- All demo users share the password: Password123!   (LOCAL DEVELOPMENT ONLY)
--
-- History is generated chronologically through seed_move(), so every
-- inventory balance equals the sum of its movements, exactly like the
-- application's own InventoryService would produce.
-- =====================================================================

INSERT INTO users (email, password_hash, full_name, role_id) VALUES
    ('admin@stockpilot.local',     '$2a$10$wVsu5pMHE9840rrK6LV7K.zAm3nU5bcLfSJzNmVK01GbHd.zHpOqC', 'Avery Admin',       (SELECT id FROM roles WHERE name = 'ADMIN')),
    ('inventory@stockpilot.local', '$2a$10$wVsu5pMHE9840rrK6LV7K.zAm3nU5bcLfSJzNmVK01GbHd.zHpOqC', 'Ivan Inventory',    (SELECT id FROM roles WHERE name = 'INVENTORY_MANAGER')),
    ('sales@stockpilot.local',     '$2a$10$wVsu5pMHE9840rrK6LV7K.zAm3nU5bcLfSJzNmVK01GbHd.zHpOqC', 'Sasha Sales',       (SELECT id FROM roles WHERE name = 'SALES_MANAGER')),
    ('viewer@stockpilot.local',    '$2a$10$wVsu5pMHE9840rrK6LV7K.zAm3nU5bcLfSJzNmVK01GbHd.zHpOqC', 'Vic Viewer',        (SELECT id FROM roles WHERE name = 'VIEWER'));

INSERT INTO categories (name, description) VALUES
    ('Electronics',       'Scanners, printers and handheld devices'),
    ('Office Supplies',   'Paper, labels and stationery'),
    ('Furniture',         'Shelving, seating and workbenches'),
    ('Packaging',         'Boxes, wraps and tapes'),
    ('Tools & Hardware',  'Material handling and power tools'),
    ('Safety Equipment',  'PPE and first aid');

INSERT INTO products (sku, name, description, category_id, unit_price, cost_price, reorder_threshold, image_url)
SELECT p.sku, p.name, p.descr, c.id, p.price, p.cost, p.threshold, NULL
FROM (VALUES
    ('ELE-1001', 'Wireless Barcode Scanner',      'Bluetooth 1D/2D scanner with charging cradle', 'Electronics',      129.00,  78.00,  15),
    ('ELE-1002', 'Thermal Label Printer',         '4-inch direct thermal printer, 203 dpi',       'Electronics',      249.00, 162.00,   8),
    ('ELE-1003', 'Rugged Tablet 10"',             'IP65 rated warehouse tablet',                  'Electronics',      549.00, 380.00,   5),
    ('ELE-1004', 'USB-C Docking Station',         'Dual display dock with 100W passthrough',      'Electronics',       89.00,  52.00,  20),
    ('ELE-1005', 'RFID Tag Reader',               'UHF handheld RFID reader',                     'Electronics',      399.00, 255.00,   4),
    ('OFF-2001', 'A4 Copy Paper (500 sheets)',    '80 gsm bright white paper',                    'Office Supplies',    6.50,   3.20, 200),
    ('OFF-2002', 'Shipping Labels 4x6 (Roll)',    '500 thermal labels per roll',                  'Office Supplies',   18.00,   9.50,  60),
    ('OFF-2003', 'Gel Pens (12-pack)',            'Black 0.7 mm gel pens',                        'Office Supplies',    9.90,   4.10,  50),
    ('OFF-2004', 'Clipboard with Storage',        'Aluminium storage clipboard',                  'Office Supplies',   14.50,   7.25,  25),
    ('FUR-3001', 'Steel Shelving Unit 5-Tier',    'Boltless shelving, 400 kg per shelf',          'Furniture',        189.00, 118.00,  10),
    ('FUR-3002', 'Ergonomic Office Chair',        'Mesh back with lumbar support',                'Furniture',        279.00, 165.00,   6),
    ('FUR-3003', 'Packing Workbench',             'Heavy-duty bench with overhead shelf',         'Furniture',        459.00, 300.00,   3),
    ('PKG-4001', 'Corrugated Box Small (25-pack)','12x9x6 inch single wall boxes',                'Packaging',         24.00,  12.50,  80),
    ('PKG-4002', 'Corrugated Box Large (25-pack)','24x18x18 inch double wall boxes',              'Packaging',         38.00,  21.00,  60),
    ('PKG-4003', 'Stretch Wrap Film 18"',         '80 gauge, 1500 ft roll',                       'Packaging',         29.00,  16.40,  40),
    ('PKG-4004', 'Bubble Wrap Roll 50m',          '10 mm bubbles, perforated',                    'Packaging',         34.00,  18.75,  30),
    ('PKG-4005', 'Packing Tape (36-pack)',        'Clear acrylic tape 48 mm',                     'Packaging',         59.00,  31.00,  25),
    ('TLS-5001', 'Pallet Jack 2500kg',            'Manual hydraulic pallet truck',                'Tools & Hardware', 649.00, 420.00,   2),
    ('TLS-5002', 'Cordless Drill Kit',            '18V drill with two batteries',                 'Tools & Hardware', 159.00,  96.00,   8),
    ('TLS-5003', 'Box Cutter Safety Knife',       'Self-retracting blade',                        'Tools & Hardware',   7.50,   2.90, 100),
    ('SAF-6001', 'High-Visibility Vest',          'ANSI Class 2 reflective vest',                 'Safety Equipment',  12.00,   5.40,  80),
    ('SAF-6002', 'Safety Goggles',                'Anti-fog, indirect vent',                      'Safety Equipment',   9.00,   3.80,  60),
    ('SAF-6003', 'Steel-Toe Boots',               'Waterproof work boots',                        'Safety Equipment', 119.00,  68.00,  12),
    ('SAF-6004', 'First Aid Kit Industrial',      '100-person OSHA compliant kit',                'Safety Equipment',  74.00,  41.00,  10)
) AS p(sku, name, descr, category, price, cost, threshold)
JOIN categories c ON c.name = p.category;

INSERT INTO warehouses (code, name, address, city, country) VALUES
    ('WH-EAST', 'East Coast Distribution Center', '120 Port Street',      'Newark',      'USA'),
    ('WH-CENT', 'Central Fulfillment Hub',        '4500 Logistics Pkwy',  'Chicago',     'USA'),
    ('WH-WEST', 'West Coast Depot',               '88 Harbor Blvd',       'Los Angeles', 'USA');

INSERT INTO suppliers (code, name, contact_name, email, phone, address) VALUES
    ('SUP-001', 'TechSource Distribution', 'Priya Natarajan', 'orders@techsource.example',  '+1-555-0101', '1 Silicon Ave, San Jose, USA'),
    ('SUP-002', 'OfficeLine Wholesale',    'Marcus Reed',     'sales@officeline.example',   '+1-555-0102', '22 Paper Mill Rd, Albany, USA'),
    ('SUP-003', 'PackRight Industries',    'Elena Rossi',     'supply@packright.example',   '+1-555-0103', '9 Carton Way, Memphis, USA'),
    ('SUP-004', 'SteelFrame Furniture Co.','Tom Becker',      'b2b@steelframe.example',     '+1-555-0104', '500 Foundry St, Pittsburgh, USA'),
    ('SUP-005', 'SafeGuard Supply',        'Aisha Khan',      'orders@safeguard.example',   '+1-555-0105', '77 Shield Rd, Houston, USA');

INSERT INTO supplier_products (supplier_id, product_id, supplier_sku, unit_cost, lead_time_days)
SELECT s.id, p.id, 'V-' || p.sku, p.cost_price, m.lead
FROM (VALUES
    ('SUP-001', 'ELE', 10), ('SUP-002', 'OFF', 5), ('SUP-003', 'PKG', 4),
    ('SUP-004', 'FUR', 14), ('SUP-004', 'TLS', 14), ('SUP-005', 'SAF', 6), ('SUP-005', 'TLS-5003', 6)
) AS m(supplier, sku_prefix, lead)
JOIN suppliers s ON s.code = m.supplier
JOIN products p ON p.sku LIKE m.sku_prefix || '%'
ON CONFLICT (supplier_id, product_id) DO NOTHING;

INSERT INTO customers (code, name, email, phone, address, city, country) VALUES
    ('CUS-0001', 'Brightline Retail',      'purchasing@brightline.example', '+1-555-0201', '10 Market St',     'Boston',        'USA'),
    ('CUS-0002', 'Harbor Logistics',       'ops@harborlog.example',         '+1-555-0202', '200 Dock Rd',      'Seattle',       'USA'),
    ('CUS-0003', 'Summit Office Group',    'buy@summitoffice.example',      '+1-555-0203', '45 Peak Ave',      'Denver',        'USA'),
    ('CUS-0004', 'Northfield Traders',     'orders@northfield.example',     '+1-555-0204', '8 Trade Ln',       'Minneapolis',   'USA'),
    ('CUS-0005', 'Pioneer Manufacturing',  'supply@pioneermfg.example',     '+1-555-0205', '300 Factory Dr',   'Detroit',       'USA'),
    ('CUS-0006', 'Metro Health Clinics',   'facilities@metrohealth.example','+1-555-0206', '12 Wellness Blvd', 'Atlanta',       'USA'),
    ('CUS-0007', 'Coastal Hardware',       'store@coastalhw.example',       '+1-555-0207', '77 Ocean Dr',      'San Diego',     'USA'),
    ('CUS-0008', 'Urban Desk Co.',         'hello@urbandesk.example',       '+1-555-0208', '5 Loft St',        'Austin',        'USA');

-- ---------------------------------------------------------------------
-- Helper used only while seeding: applies one stock movement.
-- ---------------------------------------------------------------------
CREATE FUNCTION seed_move(p_product BIGINT, p_wh BIGINT, p_type TEXT, p_qty INT,
                          p_ref_type TEXT, p_ref_id BIGINT, p_reason TEXT,
                          p_user BIGINT, p_ts TIMESTAMPTZ) RETURNS VOID AS $$
DECLARE
    v_balance INT;
BEGIN
    INSERT INTO inventory_balances (product_id, warehouse_id, quantity_on_hand, updated_at)
    VALUES (p_product, p_wh, 0, p_ts)
    ON CONFLICT (product_id, warehouse_id) DO NOTHING;

    UPDATE inventory_balances
    SET quantity_on_hand = quantity_on_hand + p_qty, updated_at = p_ts
    WHERE product_id = p_product AND warehouse_id = p_wh
    RETURNING quantity_on_hand INTO v_balance;

    INSERT INTO inventory_movements (product_id, warehouse_id, movement_type, quantity, balance_after,
                                     unit_cost, reference_type, reference_id, reason, performed_by, created_at)
    SELECT p_product, p_wh, p_type, p_qty, v_balance, p.cost_price, p_ref_type, p_ref_id, p_reason, p_user, p_ts
    FROM products p WHERE p.id = p_product;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    v_admin     BIGINT := (SELECT id FROM users WHERE email = 'admin@stockpilot.local');
    v_inv       BIGINT := (SELECT id FROM users WHERE email = 'inventory@stockpilot.local');
    v_sales     BIGINT := (SELECT id FROM users WHERE email = 'sales@stockpilot.local');
    v_goggles   BIGINT := (SELECT id FROM products WHERE sku = 'SAF-6002');
    v_start     TIMESTAMPTZ := date_trunc('day', now()) - INTERVAL '185 days';
    v_day       INT;
    v_ts        TIMESTAMPTZ;
    r           RECORD;
    s           RECORD;
    v_po        BIGINT;
    v_gr        BIGINT;
    v_so        BIGINT;
    v_item      BIGINT;
    v_qty       INT;
    v_avail     INT;
    v_status    TEXT;
    v_customer  BIGINT;
    v_wh        BIGINT;
    v_items     INT;
    v_age       INT;
BEGIN
    PERFORM setseed(0.42);

    -- Opening balances (goggles deliberately start, and stay, out of stock).
    FOR r IN SELECT p.id AS product_id, p.unit_price, w.id AS wh_id
             FROM products p CROSS JOIN warehouses w
             WHERE p.id <> v_goggles ORDER BY p.id, w.id LOOP
        IF random() < 0.85 THEN
            v_qty := CASE WHEN r.unit_price < 50 THEN 60 + floor(random() * 240)::INT
                          WHEN r.unit_price < 300 THEN 8 + floor(random() * 30)::INT
                          ELSE 2 + floor(random() * 8)::INT END;
            PERFORM seed_move(r.product_id, r.wh_id, 'RECEIPT', v_qty, 'OPENING_BALANCE', NULL,
                              'Opening balance', v_admin, v_start);
        END IF;
    END LOOP;

    FOR v_day IN REVERSE 180..1 LOOP
        v_ts := date_trunc('day', now()) - (v_day || ' days')::INTERVAL + INTERVAL '10 hours';

        -- Monthly replenishment: one received PO per supplier.
        IF v_day % 30 = 0 THEN
            FOR s IN SELECT id FROM suppliers ORDER BY id LOOP
                v_wh := (SELECT id FROM warehouses ORDER BY random() LIMIT 1);
                INSERT INTO purchase_orders (po_number, supplier_id, warehouse_id, status, order_date, expected_date,
                                             created_by, submitted_at, approved_by, approved_at, created_at, updated_at)
                VALUES ('PO-' || lpad(nextval('po_number_seq')::TEXT, 6, '0'), s.id, v_wh, 'RECEIVED',
                        (v_ts - INTERVAL '6 days')::DATE, v_ts::DATE, v_inv,
                        v_ts - INTERVAL '6 days', v_admin, v_ts - INTERVAL '5 days', v_ts - INTERVAL '6 days', v_ts)
                RETURNING id INTO v_po;

                INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity_ordered, quantity_received, unit_cost, line_total)
                SELECT v_po, sp.product_id, q.qty, q.qty, sp.unit_cost, sp.unit_cost * q.qty
                FROM supplier_products sp
                JOIN products p ON p.id = sp.product_id
                CROSS JOIN LATERAL (SELECT CASE WHEN p.unit_price < 50 THEN 80 + floor(random() * 200)::INT
                                                WHEN p.unit_price < 300 THEN 10 + floor(random() * 25)::INT
                                                ELSE 3 + floor(random() * 6)::INT END AS qty) q
                WHERE sp.supplier_id = s.id AND sp.product_id <> v_goggles AND random() < 0.75;

                IF NOT EXISTS (SELECT 1 FROM purchase_order_items WHERE purchase_order_id = v_po) THEN
                    DELETE FROM purchase_orders WHERE id = v_po;
                    CONTINUE;
                END IF;

                UPDATE purchase_orders SET total_amount = (SELECT sum(line_total) FROM purchase_order_items WHERE purchase_order_id = v_po)
                WHERE id = v_po;

                INSERT INTO goods_receipts (receipt_number, purchase_order_id, warehouse_id, received_by, received_at, notes)
                VALUES ('GR-' || lpad(nextval('receipt_number_seq')::TEXT, 6, '0'), v_po, v_wh, v_inv, v_ts, 'Full delivery')
                RETURNING id INTO v_gr;

                FOR r IN SELECT id, product_id, quantity_ordered FROM purchase_order_items WHERE purchase_order_id = v_po LOOP
                    INSERT INTO goods_receipt_items (goods_receipt_id, purchase_order_item_id, product_id, quantity_received)
                    VALUES (v_gr, r.id, r.product_id, r.quantity_ordered);
                    PERFORM seed_move(r.product_id, v_wh, 'PURCHASE_RECEIPT', r.quantity_ordered, 'GOODS_RECEIPT', v_gr,
                                      NULL, v_inv, v_ts);
                END LOOP;
            END LOOP;
        END IF;

        -- Occasional inter-warehouse transfer.
        IF v_day % 17 = 0 THEN
            SELECT b.product_id, b.warehouse_id, b.quantity_on_hand - b.quantity_reserved AS avail INTO r
            FROM inventory_balances b WHERE b.quantity_on_hand - b.quantity_reserved > 20 ORDER BY random() LIMIT 1;
            IF FOUND THEN
                v_wh := (SELECT id FROM warehouses WHERE id <> r.warehouse_id ORDER BY random() LIMIT 1);
                v_qty := 5 + floor(random() * 10)::INT;
                INSERT INTO stock_transfers (transfer_number, product_id, from_warehouse_id, to_warehouse_id, quantity, notes, created_by, created_at)
                VALUES ('TR-' || lpad(nextval('transfer_number_seq')::TEXT, 6, '0'), r.product_id, r.warehouse_id, v_wh, v_qty,
                        'Rebalancing stock', v_inv, v_ts)
                RETURNING id INTO v_item;
                PERFORM seed_move(r.product_id, r.warehouse_id, 'TRANSFER_OUT', -v_qty, 'STOCK_TRANSFER', v_item, 'Rebalancing stock', v_inv, v_ts);
                PERFORM seed_move(r.product_id, v_wh, 'TRANSFER_IN', v_qty, 'STOCK_TRANSFER', v_item, 'Rebalancing stock', v_inv, v_ts);
            END IF;
        END IF;

        -- Sales orders.
        CONTINUE WHEN random() > 0.75;
        v_customer := (SELECT id FROM customers ORDER BY random() LIMIT 1);
        v_wh := (SELECT id FROM warehouses ORDER BY random() LIMIT 1);
        v_age := v_day;
        v_status := CASE WHEN random() < 0.04 THEN 'CANCELLED'
                         WHEN v_age > 10 THEN 'DELIVERED'
                         WHEN v_age > 6 THEN (CASE WHEN random() < 0.5 THEN 'DELIVERED' ELSE 'SHIPPED' END)
                         WHEN v_age > 2 THEN 'CONFIRMED'
                         ELSE 'DRAFT' END;

        INSERT INTO sales_orders (order_number, customer_id, warehouse_id, status, order_date, shipping_address,
                                  created_by, created_at, updated_at)
        SELECT 'SO-' || lpad(nextval('so_number_seq')::TEXT, 6, '0'), c.id, v_wh, v_status, v_ts::DATE,
               c.address || ', ' || c.city, v_sales, v_ts, v_ts
        FROM customers c WHERE c.id = v_customer
        RETURNING id INTO v_so;

        v_items := 0;
        FOR r IN SELECT p.id, p.unit_price FROM products p WHERE p.id <> v_goggles ORDER BY random() LIMIT 1 + floor(random() * 3)::INT LOOP
            v_qty := 1 + floor(random() * CASE WHEN r.unit_price < 50 THEN 30 WHEN r.unit_price < 300 THEN 5 ELSE 2 END)::INT;
            SELECT coalesce(quantity_on_hand - quantity_reserved, 0) INTO v_avail
            FROM inventory_balances WHERE product_id = r.id AND warehouse_id = v_wh;
            CONTINUE WHEN coalesce(v_avail, 0) < v_qty;

            INSERT INTO sales_order_items (sales_order_id, product_id, quantity, unit_price, line_total)
            VALUES (v_so, r.id, v_qty, r.unit_price, r.unit_price * v_qty)
            RETURNING id INTO v_item;
            v_items := v_items + 1;

            IF v_status IN ('SHIPPED', 'DELIVERED') THEN
                INSERT INTO stock_reservations (sales_order_item_id, product_id, warehouse_id, quantity, status, created_at, updated_at)
                VALUES (v_item, r.id, v_wh, v_qty, 'CONSUMED', v_ts, v_ts + INTERVAL '1 day');
                PERFORM seed_move(r.id, v_wh, 'SALES_SHIPMENT', -v_qty, 'SALES_ORDER', v_so, NULL, v_sales, v_ts + INTERVAL '1 day');
            ELSIF v_status = 'CONFIRMED' THEN
                INSERT INTO stock_reservations (sales_order_item_id, product_id, warehouse_id, quantity, status, created_at, updated_at)
                VALUES (v_item, r.id, v_wh, v_qty, 'ACTIVE', v_ts, v_ts);
                UPDATE inventory_balances SET quantity_reserved = quantity_reserved + v_qty
                WHERE product_id = r.id AND warehouse_id = v_wh;
            END IF;
        END LOOP;

        IF v_items = 0 THEN
            DELETE FROM sales_orders WHERE id = v_so;
            CONTINUE;
        END IF;

        UPDATE sales_orders SET
            total_amount = (SELECT sum(line_total) FROM sales_order_items WHERE sales_order_id = v_so),
            confirmed_at = CASE WHEN v_status IN ('CONFIRMED', 'SHIPPED', 'DELIVERED') THEN v_ts END,
            shipped_at   = CASE WHEN v_status IN ('SHIPPED', 'DELIVERED') THEN v_ts + INTERVAL '1 day' END,
            delivered_at = CASE WHEN v_status = 'DELIVERED' THEN v_ts + INTERVAL '3 days' END,
            cancelled_at = CASE WHEN v_status = 'CANCELLED' THEN v_ts + INTERVAL '2 hours' END
        WHERE id = v_so;

        IF v_status IN ('SHIPPED', 'DELIVERED') THEN
            INSERT INTO shipments (shipment_number, sales_order_id, carrier, tracking_number, status, shipped_at, delivered_at, created_by, updated_at)
            VALUES ('SH-' || lpad(nextval('shipment_number_seq')::TEXT, 6, '0'), v_so,
                    (ARRAY['UPS', 'FedEx', 'DHL', 'USPS'])[1 + floor(random() * 4)::INT],
                    '1Z' || upper(substr(md5(v_so::TEXT), 1, 14)),
                    CASE WHEN v_status = 'DELIVERED' THEN 'DELIVERED' ELSE 'IN_TRANSIT' END,
                    v_ts + INTERVAL '1 day',
                    CASE WHEN v_status = 'DELIVERED' THEN v_ts + INTERVAL '3 days' END,
                    v_sales, v_ts + INTERVAL '1 day');
        END IF;
    END LOOP;

    -- A recent bulk customer order that leaves a few products below their reorder threshold.
    v_ts := now() - INTERVAL '4 days';
    FOR s IN SELECT id FROM warehouses ORDER BY id LOOP
        INSERT INTO sales_orders (order_number, customer_id, warehouse_id, status, order_date, shipping_address, notes,
                                  created_by, confirmed_at, shipped_at, delivered_at, created_at, updated_at)
        SELECT 'SO-' || lpad(nextval('so_number_seq')::TEXT, 6, '0'), c.id, s.id, 'DELIVERED', v_ts::DATE,
               c.address || ', ' || c.city, 'Bulk site fit-out', v_sales, v_ts, v_ts + INTERVAL '1 day', v_ts + INTERVAL '2 days', v_ts, v_ts
        FROM customers c WHERE c.code = 'CUS-0002'
        RETURNING id INTO v_so;

        FOR r IN SELECT p.id, p.unit_price, b.quantity_on_hand - b.quantity_reserved - (p.reorder_threshold / 3) AS qty
                 FROM products p JOIN inventory_balances b ON b.product_id = p.id AND b.warehouse_id = s.id
                 WHERE p.sku IN ('ELE-1005', 'OFF-2002', 'FUR-3002', 'SAF-6003') LOOP
            CONTINUE WHEN r.qty <= 0;
            INSERT INTO sales_order_items (sales_order_id, product_id, quantity, unit_price, line_total)
            VALUES (v_so, r.id, r.qty, r.unit_price, r.unit_price * r.qty)
            RETURNING id INTO v_item;
            INSERT INTO stock_reservations (sales_order_item_id, product_id, warehouse_id, quantity, status, created_at, updated_at)
            VALUES (v_item, r.id, s.id, r.qty, 'CONSUMED', v_ts, v_ts + INTERVAL '1 day');
            PERFORM seed_move(r.id, s.id, 'SALES_SHIPMENT', -r.qty, 'SALES_ORDER', v_so, NULL, v_sales, v_ts + INTERVAL '1 day');
        END LOOP;

        IF NOT EXISTS (SELECT 1 FROM sales_order_items WHERE sales_order_id = v_so) THEN
            DELETE FROM sales_orders WHERE id = v_so;
            CONTINUE;
        END IF;
        UPDATE sales_orders SET total_amount = (SELECT sum(line_total) FROM sales_order_items WHERE sales_order_id = v_so) WHERE id = v_so;
        INSERT INTO shipments (shipment_number, sales_order_id, carrier, tracking_number, status, shipped_at, delivered_at, created_by, updated_at)
        VALUES ('SH-' || lpad(nextval('shipment_number_seq')::TEXT, 6, '0'), v_so, 'FedEx', '7489' || lpad(v_so::TEXT, 10, '0'),
                'DELIVERED', v_ts + INTERVAL '1 day', v_ts + INTERVAL '2 days', v_sales, v_ts + INTERVAL '2 days');
    END LOOP;

    -- Open purchase orders in every workflow state.
    v_wh := (SELECT id FROM warehouses WHERE code = 'WH-EAST');

    INSERT INTO purchase_orders (po_number, supplier_id, warehouse_id, status, order_date, expected_date, notes, created_by, created_at, updated_at)
    VALUES ('PO-' || lpad(nextval('po_number_seq')::TEXT, 6, '0'), (SELECT id FROM suppliers WHERE code = 'SUP-002'), v_wh,
            'DRAFT', current_date, current_date + 7, 'Quarterly office restock', v_inv, now(), now())
    RETURNING id INTO v_po;
    INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity_ordered, unit_cost, line_total)
    SELECT v_po, p.id, 150, p.cost_price, p.cost_price * 150 FROM products p WHERE p.sku IN ('OFF-2001', 'OFF-2003');
    UPDATE purchase_orders SET total_amount = (SELECT sum(line_total) FROM purchase_order_items WHERE purchase_order_id = v_po) WHERE id = v_po;

    INSERT INTO purchase_orders (po_number, supplier_id, warehouse_id, status, order_date, expected_date, notes, created_by, submitted_at, created_at, updated_at)
    VALUES ('PO-' || lpad(nextval('po_number_seq')::TEXT, 6, '0'), (SELECT id FROM suppliers WHERE code = 'SUP-001'), v_wh,
            'SUBMITTED', current_date - 1, current_date + 10, 'Scanner fleet expansion', v_inv, now() - INTERVAL '1 day', now() - INTERVAL '1 day', now() - INTERVAL '1 day')
    RETURNING id INTO v_po;
    INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity_ordered, unit_cost, line_total)
    SELECT v_po, p.id, 20, p.cost_price, p.cost_price * 20 FROM products p WHERE p.sku IN ('ELE-1001', 'ELE-1002');
    UPDATE purchase_orders SET total_amount = (SELECT sum(line_total) FROM purchase_order_items WHERE purchase_order_id = v_po) WHERE id = v_po;

    INSERT INTO purchase_orders (po_number, supplier_id, warehouse_id, status, order_date, expected_date, notes, created_by, submitted_at,
                                 approved_by, approved_at, created_at, updated_at)
    VALUES ('PO-' || lpad(nextval('po_number_seq')::TEXT, 6, '0'), (SELECT id FROM suppliers WHERE code = 'SUP-005'), v_wh,
            'APPROVED', current_date - 3, current_date + 3, 'Urgent: goggles out of stock', v_inv, now() - INTERVAL '3 days',
            v_admin, now() - INTERVAL '2 days', now() - INTERVAL '3 days', now() - INTERVAL '2 days')
    RETURNING id INTO v_po;
    INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity_ordered, unit_cost, line_total)
    SELECT v_po, p.id, 200, p.cost_price, p.cost_price * 200 FROM products p WHERE p.sku IN ('SAF-6002', 'SAF-6001');
    UPDATE purchase_orders SET total_amount = (SELECT sum(line_total) FROM purchase_order_items WHERE purchase_order_id = v_po) WHERE id = v_po;

    -- Partially received PO.
    INSERT INTO purchase_orders (po_number, supplier_id, warehouse_id, status, order_date, expected_date, notes, created_by, submitted_at,
                                 approved_by, approved_at, created_at, updated_at)
    VALUES ('PO-' || lpad(nextval('po_number_seq')::TEXT, 6, '0'), (SELECT id FROM suppliers WHERE code = 'SUP-003'), v_wh,
            'PARTIALLY_RECEIVED', current_date - 5, current_date + 2, 'Peak season packaging', v_inv, now() - INTERVAL '5 days',
            v_admin, now() - INTERVAL '5 days', now() - INTERVAL '5 days', now() - INTERVAL '1 day')
    RETURNING id INTO v_po;
    INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity_ordered, quantity_received, unit_cost, line_total)
    SELECT v_po, p.id, 300, CASE WHEN p.sku = 'PKG-4001' THEN 120 ELSE 0 END, p.cost_price, p.cost_price * 300
    FROM products p WHERE p.sku IN ('PKG-4001', 'PKG-4002');
    UPDATE purchase_orders SET total_amount = (SELECT sum(line_total) FROM purchase_order_items WHERE purchase_order_id = v_po) WHERE id = v_po;
    INSERT INTO goods_receipts (receipt_number, purchase_order_id, warehouse_id, received_by, received_at, notes)
    VALUES ('GR-' || lpad(nextval('receipt_number_seq')::TEXT, 6, '0'), v_po, v_wh, v_inv, now() - INTERVAL '1 day', 'First truck, remainder backordered')
    RETURNING id INTO v_gr;
    FOR r IN SELECT id, product_id, quantity_received FROM purchase_order_items WHERE purchase_order_id = v_po AND quantity_received > 0 LOOP
        INSERT INTO goods_receipt_items (goods_receipt_id, purchase_order_item_id, product_id, quantity_received)
        VALUES (v_gr, r.id, r.product_id, r.quantity_received);
        PERFORM seed_move(r.product_id, v_wh, 'PURCHASE_RECEIPT', r.quantity_received, 'GOODS_RECEIPT', v_gr, NULL, v_inv, now() - INTERVAL '1 day');
    END LOOP;

    -- A recent manual adjustment.
    SELECT product_id, warehouse_id INTO r FROM inventory_balances
    WHERE quantity_on_hand - quantity_reserved > 5 ORDER BY product_id LIMIT 1;
    PERFORM seed_move(r.product_id, r.warehouse_id, 'ADJUSTMENT', -2, 'MANUAL', NULL, 'Damaged during cycle count', v_inv, now() - INTERVAL '2 hours');

    -- Stock alerts for the current low / out-of-stock products.
    INSERT INTO notifications (user_id, type, severity, title, message, link, created_at)
    SELECT u.id,
           CASE WHEN t.on_hand = 0 THEN 'OUT_OF_STOCK' ELSE 'LOW_STOCK' END,
           CASE WHEN t.on_hand = 0 THEN 'CRITICAL' ELSE 'WARNING' END,
           CASE WHEN t.on_hand = 0 THEN 'Out of stock: ' ELSE 'Low stock: ' END || t.name,
           t.sku || ' has ' || t.on_hand || ' units on hand (reorder threshold ' || t.reorder_threshold || ').',
           '/products/' || t.id, now() - INTERVAL '30 minutes'
    FROM (SELECT p.id, p.sku, p.name, p.reorder_threshold, coalesce(sum(b.quantity_on_hand), 0) AS on_hand
          FROM products p LEFT JOIN inventory_balances b ON b.product_id = p.id
          GROUP BY p.id) t
    CROSS JOIN users u
    WHERE t.on_hand <= t.reorder_threshold
      AND u.email IN ('admin@stockpilot.local', 'inventory@stockpilot.local');

    INSERT INTO notifications (user_id, type, severity, title, message, link, created_at)
    SELECT v_admin, 'PO_PENDING_APPROVAL', 'INFO', 'Purchase order awaiting approval: ' || po.po_number,
           'Submitted by Ivan Inventory for ' || po.total_amount || ' USD.', '/purchase-orders/' || po.id, po.submitted_at
    FROM purchase_orders po WHERE po.status = 'SUBMITTED';

    INSERT INTO audit_logs (actor_id, actor_email, action, entity_type, entity_id, details, created_at)
    VALUES (v_admin, 'admin@stockpilot.local', 'DATA_SEEDED', 'System', NULL, 'Development seed data loaded', now());
END $$;

DROP FUNCTION seed_move(BIGINT, BIGINT, TEXT, INT, TEXT, BIGINT, TEXT, BIGINT, TIMESTAMPTZ);
