-- =====================================================================
-- StockPilot initial schema
-- Monetary values use NUMERIC(14,2). Quantities are whole units (INTEGER).
-- =====================================================================

-- ---------- Security ----------
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(40)  NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role_id       BIGINT       NOT NULL REFERENCES roles (id),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0
);
-- Emails are compared case-insensitively.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
CREATE INDEX ix_users_role ON users (role_id);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 hex of the opaque token
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- ---------- Catalog ----------
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_categories_name_lower ON categories (lower(name));

CREATE TABLE products (
    id                BIGSERIAL PRIMARY KEY,
    sku               VARCHAR(40)    NOT NULL UNIQUE,
    name              VARCHAR(160)   NOT NULL,
    description       TEXT,
    category_id       BIGINT         NOT NULL REFERENCES categories (id),
    unit_price        NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    cost_price        NUMERIC(14, 2) NOT NULL CHECK (cost_price >= 0),
    reorder_threshold INTEGER        NOT NULL DEFAULT 0 CHECK (reorder_threshold >= 0),
    image_url         VARCHAR(500),
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_status ON products (status);
CREATE INDEX ix_products_name_lower ON products (lower(name));

-- ---------- Warehouses & inventory ----------
CREATE TABLE warehouses (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(120) NOT NULL,
    address    VARCHAR(255),
    city       VARCHAR(80),
    country    VARCHAR(80),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One row per product per warehouse. Rows are locked (SELECT ... FOR UPDATE)
-- by every stock-changing operation; the CHECKs are the last line of defence
-- against negative or over-reserved stock.
CREATE TABLE inventory_balances (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT      NOT NULL REFERENCES products (id),
    warehouse_id      BIGINT      NOT NULL REFERENCES warehouses (id),
    quantity_on_hand  INTEGER     NOT NULL DEFAULT 0 CHECK (quantity_on_hand >= 0),
    quantity_reserved INTEGER     NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_product_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT ck_inventory_reserved_le_on_hand CHECK (quantity_reserved <= quantity_on_hand)
);
CREATE INDEX ix_inventory_warehouse ON inventory_balances (warehouse_id);

CREATE TABLE stock_transfers (
    id                BIGSERIAL PRIMARY KEY,
    transfer_number   VARCHAR(30) NOT NULL UNIQUE,
    product_id        BIGINT      NOT NULL REFERENCES products (id),
    from_warehouse_id BIGINT      NOT NULL REFERENCES warehouses (id),
    to_warehouse_id   BIGINT      NOT NULL REFERENCES warehouses (id),
    quantity          INTEGER     NOT NULL CHECK (quantity > 0),
    notes             VARCHAR(500),
    created_by        BIGINT      REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_transfer_distinct_warehouses CHECK (from_warehouse_id <> to_warehouse_id)
);

-- Append-only audit trail of every stock change. quantity is signed.
CREATE TABLE inventory_movements (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT         NOT NULL REFERENCES products (id),
    warehouse_id   BIGINT         NOT NULL REFERENCES warehouses (id),
    movement_type  VARCHAR(30)    NOT NULL CHECK (movement_type IN
                       ('RECEIPT', 'ISSUE', 'TRANSFER_IN', 'TRANSFER_OUT', 'ADJUSTMENT',
                        'PURCHASE_RECEIPT', 'SALES_SHIPMENT')),
    quantity       INTEGER        NOT NULL CHECK (quantity <> 0),
    balance_after  INTEGER        NOT NULL CHECK (balance_after >= 0),
    unit_cost      NUMERIC(14, 2) NOT NULL,
    reference_type VARCHAR(30),
    reference_id   BIGINT,
    reason         VARCHAR(500),
    performed_by   BIGINT         REFERENCES users (id),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_movements_product ON inventory_movements (product_id, created_at DESC);
CREATE INDEX ix_movements_warehouse ON inventory_movements (warehouse_id, created_at DESC);
CREATE INDEX ix_movements_created ON inventory_movements (created_at DESC);
CREATE INDEX ix_movements_type ON inventory_movements (movement_type);
CREATE INDEX ix_movements_reference ON inventory_movements (reference_type, reference_id);

-- ---------- Procurement ----------
CREATE TABLE suppliers (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,
    name         VARCHAR(160) NOT NULL,
    contact_name VARCHAR(120),
    email        VARCHAR(160),
    phone        VARCHAR(40),
    address      VARCHAR(255),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_suppliers_name_lower ON suppliers (lower(name));

CREATE TABLE supplier_products (
    id             BIGSERIAL PRIMARY KEY,
    supplier_id    BIGINT         NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    product_id     BIGINT         NOT NULL REFERENCES products (id),
    supplier_sku   VARCHAR(60),
    unit_cost      NUMERIC(14, 2) NOT NULL CHECK (unit_cost >= 0),
    lead_time_days INTEGER        NOT NULL DEFAULT 7 CHECK (lead_time_days >= 0),
    CONSTRAINT uq_supplier_product UNIQUE (supplier_id, product_id)
);
CREATE INDEX ix_supplier_products_product ON supplier_products (product_id);

CREATE TABLE purchase_orders (
    id               BIGSERIAL PRIMARY KEY,
    po_number        VARCHAR(30)    NOT NULL UNIQUE,
    supplier_id      BIGINT         NOT NULL REFERENCES suppliers (id),
    warehouse_id     BIGINT         NOT NULL REFERENCES warehouses (id),
    status           VARCHAR(30)    NOT NULL CHECK (status IN
                         ('DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')),
    order_date       DATE           NOT NULL,
    expected_date    DATE,
    notes            VARCHAR(1000),
    total_amount     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    rejection_reason VARCHAR(500),
    created_by       BIGINT         REFERENCES users (id),
    submitted_at     TIMESTAMPTZ,
    approved_by      BIGINT         REFERENCES users (id),
    approved_at      TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version          BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_po_supplier ON purchase_orders (supplier_id);
CREATE INDEX ix_po_status ON purchase_orders (status);
CREATE INDEX ix_po_order_date ON purchase_orders (order_date);

CREATE TABLE purchase_order_items (
    id                BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    product_id        BIGINT         NOT NULL REFERENCES products (id),
    quantity_ordered  INTEGER        NOT NULL CHECK (quantity_ordered > 0),
    quantity_received INTEGER        NOT NULL DEFAULT 0 CHECK (quantity_received >= 0),
    unit_cost         NUMERIC(14, 2) NOT NULL CHECK (unit_cost >= 0),
    line_total        NUMERIC(14, 2) NOT NULL,
    CONSTRAINT uq_po_item_product UNIQUE (purchase_order_id, product_id),
    CONSTRAINT ck_po_item_received_le_ordered CHECK (quantity_received <= quantity_ordered)
);
CREATE INDEX ix_po_items_product ON purchase_order_items (product_id);

CREATE TABLE goods_receipts (
    id                BIGSERIAL PRIMARY KEY,
    receipt_number    VARCHAR(30)  NOT NULL UNIQUE,
    purchase_order_id BIGINT       NOT NULL REFERENCES purchase_orders (id),
    warehouse_id      BIGINT       NOT NULL REFERENCES warehouses (id),
    received_by       BIGINT       REFERENCES users (id),
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    notes             VARCHAR(1000),
    -- Optional client-supplied key: replaying the same request returns the
    -- original receipt instead of receiving the goods twice.
    idempotency_key   VARCHAR(80)  UNIQUE
);
CREATE INDEX ix_receipts_po ON goods_receipts (purchase_order_id);
CREATE INDEX ix_receipts_received_at ON goods_receipts (received_at);

CREATE TABLE goods_receipt_items (
    id                     BIGSERIAL PRIMARY KEY,
    goods_receipt_id       BIGINT  NOT NULL REFERENCES goods_receipts (id) ON DELETE CASCADE,
    purchase_order_item_id BIGINT  NOT NULL REFERENCES purchase_order_items (id),
    product_id             BIGINT  NOT NULL REFERENCES products (id),
    quantity_received      INTEGER NOT NULL CHECK (quantity_received > 0)
);
CREATE INDEX ix_receipt_items_receipt ON goods_receipt_items (goods_receipt_id);

-- ---------- Sales ----------
CREATE TABLE customers (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(160) NOT NULL,
    email      VARCHAR(160),
    phone      VARCHAR(40),
    address    VARCHAR(255),
    city       VARCHAR(80),
    country    VARCHAR(80),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_customers_name_lower ON customers (lower(name));

CREATE TABLE sales_orders (
    id               BIGSERIAL PRIMARY KEY,
    order_number     VARCHAR(30)    NOT NULL UNIQUE,
    customer_id      BIGINT         NOT NULL REFERENCES customers (id),
    warehouse_id     BIGINT         NOT NULL REFERENCES warehouses (id),
    status           VARCHAR(30)    NOT NULL CHECK (status IN
                         ('DRAFT', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    order_date       DATE           NOT NULL,
    shipping_address VARCHAR(500),
    notes            VARCHAR(1000),
    total_amount     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_by       BIGINT         REFERENCES users (id),
    confirmed_at     TIMESTAMPTZ,
    shipped_at       TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version          BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_so_customer ON sales_orders (customer_id);
CREATE INDEX ix_so_status ON sales_orders (status);
CREATE INDEX ix_so_order_date ON sales_orders (order_date);

CREATE TABLE sales_order_items (
    id             BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT         NOT NULL REFERENCES sales_orders (id) ON DELETE CASCADE,
    product_id     BIGINT         NOT NULL REFERENCES products (id),
    quantity       INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    line_total     NUMERIC(14, 2) NOT NULL,
    CONSTRAINT uq_so_item_product UNIQUE (sales_order_id, product_id)
);
CREATE INDEX ix_so_items_product ON sales_order_items (product_id);

CREATE TABLE stock_reservations (
    id                  BIGSERIAL PRIMARY KEY,
    sales_order_item_id BIGINT      NOT NULL REFERENCES sales_order_items (id) ON DELETE CASCADE,
    product_id          BIGINT      NOT NULL REFERENCES products (id),
    warehouse_id        BIGINT      NOT NULL REFERENCES warehouses (id),
    quantity            INTEGER     NOT NULL CHECK (quantity > 0),
    status              VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reservations_item ON stock_reservations (sales_order_item_id);
CREATE INDEX ix_reservations_active ON stock_reservations (product_id, warehouse_id) WHERE status = 'ACTIVE';

CREATE TABLE shipments (
    id              BIGSERIAL PRIMARY KEY,
    shipment_number VARCHAR(30)  NOT NULL UNIQUE,
    sales_order_id  BIGINT       NOT NULL UNIQUE REFERENCES sales_orders (id),
    carrier         VARCHAR(80)  NOT NULL,
    tracking_number VARCHAR(80),
    status          VARCHAR(30)  NOT NULL CHECK (status IN ('IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED')),
    shipped_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    delivered_at    TIMESTAMPTZ,
    created_by      BIGINT       REFERENCES users (id),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_shipments_status ON shipments (status);

-- ---------- Notifications & audit ----------
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40)  NOT NULL,
    severity   VARCHAR(20)  NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    title      VARCHAR(200) NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    link       VARCHAR(255),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    actor_email VARCHAR(160),
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id   VARCHAR(60),
    details     TEXT,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_created ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_actor ON audit_logs (actor_id);
CREATE INDEX ix_audit_action ON audit_logs (action);

-- ---------- Reference data ----------
INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'Full access, including users, roles and audit logs'),
    ('INVENTORY_MANAGER', 'Manages catalog, warehouses, stock, suppliers and purchasing'),
    ('SALES_MANAGER', 'Manages customers, sales orders and shipments'),
    ('VIEWER', 'Read-only access to operational data and reports');

-- ---------- Document number sequences ----------
CREATE SEQUENCE product_sku_seq START WITH 7001;
CREATE SEQUENCE po_number_seq;
CREATE SEQUENCE receipt_number_seq;
CREATE SEQUENCE so_number_seq;
CREATE SEQUENCE shipment_number_seq;
CREATE SEQUENCE transfer_number_seq;
CREATE SEQUENCE supplier_code_seq START WITH 100;
CREATE SEQUENCE customer_code_seq START WITH 100;
