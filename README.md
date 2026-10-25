StockPilot — Smart Inventory and Supply Chain Management Platform
A full-stack inventory, purchasing and sales system: Java 21 / Spring Boot 3.5 REST API, PostgreSQL with Flyway

migrations, and a React + TypeScript + Tailwind single-page app. Every screen is backed by real API calls and

database queries — there is no mock data in the application.
Main features
| Area | What it does |

|---|---|

| Auth & roles | Registration, login, JWT access tokens, rotating refresh tokens, logout, password change, rate-limited auth endpoints. Roles: ADMIN, INVENTORY_MANAGER, SALES_MANAGER, VIEWER, enforced on the backend. |

| Dashboard | Inventory value, product/category counts, low/out-of-stock, pending POs and SOs, monthly sales vs. purchases, top sellers, stock by category, recent movements; date filters and drill-down links. |

| Catalog | Products (create/edit/archive/restore, search, filter, sort, paginate), SKU generation + duplicate prevention, image URLs, optimistic locking; categories. |

| Inventory | Multi-warehouse balances (on hand / reserved / available), receipts, issues, transfers, adjustments with mandatory reasons, immutable movement ledger, low/out-of-stock alerts, valuation. |

| Procurement | Suppliers and supplier catalogues, purchase orders DRAFT → SUBMITTED → APPROVED → PARTIALLY_RECEIVED → RECEIVED, approval/rejection, cancellation rules, partial receipts, idempotent goods receipts. |

| Sales | Customers, sales orders DRAFT → CONFIRMED → SHIPPED → DELIVERED, stock reservation on confirm, shipping with tracking, cancellation with stock release. |

| Notifications | In-app notification centre (read/unread, filters), low-stock / out-of-stock / approval / receipt / shipped alerts, optional SMTP email. |

| Reports | 8 reports (inventory, low stock, valuation, movements, sales, purchases by supplier, product performance, warehouse performance) with filters, sorting, pagination and CSV export. |

| Audit log | Logins (success/failure), token reuse, user/role changes, product changes, stock operations, PO approvals, receipts, SO status changes. Admin only. |
Architecture

stockpilot/

├── backend/                 Spring Boot REST API (Maven, Java 21)

│   └── src/main/java/com/stockpilot/

│       ├── config/          Security, OpenAPI, typed properties

│       ├── security/        JWT filter, rate limiter, permissions, refresh cookie

│       ├── controller/      REST endpoints under /api/v1 (thin, no business logic)

│       ├── service/         Business rules + transactions

│       ├── repository/      Spring Data JPA (+ row-lock queries)

│       ├── entity/          JPA entities and status enums (with transition rules)

│       ├── dto/  mapper/    Request/response records and entity→DTO mapping

│       ├── exception/       Global handler, consistent ApiError responses

│       ├── validation/      @StrongPassword, @HttpUrl

│       └── scheduler/       Expired refresh-token cleanup

│   └── src/main/resources/db/

│       ├── migration/       V1 schema (always applied)

│       └── seed/            V1.1 demo data (dev profile only)

├── frontend/                React 19 + Vite + TypeScript + Tailwind v4 + Recharts

│   └── src/

│       ├── api/             Axios client (token handling), endpoints, DTO types

│       ├── auth/            AuthContext, ProtectedRoute, permission map

│       ├── components/      Reusable UI (DataTable, Modal, ConfirmDialog, Toast, …) and layout

│       ├── pages/           22 pages, code-split per route

│       └── test/            Vitest + Testing Library tests

├── docs/ER-DIAGRAM.md       Entity relationship diagram

├── docker-compose.yml       PostgreSQL 16 (+ optional Mailpit SMTP)

└── .env.example             Configuration template (placeholders only)

Request flow: Controller → Service → Repository → PostgreSQL. Write operations use JPA inside

@Transactional services; dashboards and reports use plain SQL (NamedParameterJdbcTemplate) because

aggregations are clearer and faster in SQL than in JPA.
Key technical decisions
- Stock consistency. InventoryService is the only code that changes stock. Every change locks the

  inventory_balances row (SELECT … FOR UPDATE), writes an inventory_movements row in the same

  transaction, and multi-row operations lock rows in a fixed order to avoid deadlocks. Database CHECK

  constraints (quantity_on_hand >= 0, reserved <= on_hand) are the last line of defence.

- Overselling. Confirming a sales order reserves stock for all lines in one transaction (all or nothing).

  Reserved stock cannot be issued, transferred or sold again. A concurrency test fires 12 simultaneous

  confirmations at 20 units and asserts exactly 6 succeed.

- No double receiving. Goods receipts lock the purchase order row, reject quantities above the outstanding

  amount (also a DB constraint), and accept an optional Idempotency-Key header: replaying a request returns the

  original receipt instead of adding stock again. The UI sends a fresh key per receipt dialog.

- Server-side money. Order totals are always computed on the server from catalogue prices; any price or

  total sent by a client is ignored.

- Optimistic locking on products (version): editing a stale copy returns 409 CONCURRENT_MODIFICATION.
Token storage — approach and trade-offs
| Token | Where | Why |

|---|---|---|

| Access token (JWT, 15 min) | JavaScript memory only | Not in localStorage, so an XSS bug cannot read it from storage; it vanishes when the tab closes. Short lifetime limits damage if leaked. |

| Refresh token (random, 7 days) | HttpOnly, SameSite=Strict, Secure cookie scoped to /api/v1/auth | JavaScript cannot read it; it is not sent cross-site (CSRF protection) or to other API paths. Stored server-side only as a SHA-256 hash. |

Each refresh rotates the refresh token; presenting an already-used token is treated as theft and revokes all

of that user's sessions. On page load the app silently calls /auth/refresh to restore the session (so a 401 in

the browser console on the very first load, when no cookie exists yet, is expected). The user is re-loaded on

every request, so disabling an account or changing a role takes effect immediately.

Trade-off: XSS could still make requests while the page is open — the defence there is React's output escaping,

strict URL validation for images, and no dangerouslySetInnerHTML.
Technology stack
Backend: Java 21, Spring Boot 3.5.16 (Web, Data JPA/Hibernate, Security, Validation, Actuator, Mail), JJWT 0.12,

springdoc-openapi 2.8, Flyway, PostgreSQL 16, Lombok, Maven (wrapper included).

Frontend: React 19, TypeScript, Vite, Tailwind CSS v4, React Router, Axios, Recharts, lucide-react.

Testing: JUnit 5, Mockito, Spring Boot Test, MockMvc, Testcontainers (real PostgreSQL), Vitest, Testing Library.
Prerequisites
- Java 21 (java -version). On macOS: brew install openjdk@21.

- Node.js 20+ and npm.

- Docker (Docker Desktop) — for PostgreSQL and for the backend integration tests.

  No local Maven needed: use ./mvnw.
Setup and running locally
1. Configuration

cp .env.example .env

For local development the defaults work without editing (the dev profile supplies a development-only JWT secret

and database password). For any shared or production environment set real values for DB_PASSWORD and

JWT_SECRET (openssl rand -base64 48). .env is git-ignored — never commit it.

| Variable | Default | Purpose |

|---|---|---|

| DB_HOST / DB_PORT / DB_NAME | localhost / 5433 / stockpilot | Database location (host port 5433 avoids clashing with a local PostgreSQL) |

| DB_USERNAME / DB_PASSWORD | stockpilot / stockpilot_dev (dev) | Database credentials |

| SERVER_PORT | 8090 | Backend port |

| SPRING_PROFILES_ACTIVE | dev | dev loads demo data; anything else loads the schema only |

| JWT_SECRET | dev-only default | Base64, ≥ 32 bytes. Required outside the dev profile |

| JWT_ACCESS_TOKEN_MINUTES / JWT_REFRESH_TOKEN_DAYS | 15 / 7 | Token lifetimes |

| CORS_ALLOWED_ORIGINS | http\://localhost:5173 | Comma-separated allowed browser origins |

| COOKIE_SECURE | false in dev, true otherwise | Set true whenever served over HTTPS |

| AUTH_RATE_LIMIT_PER_MINUTE | 10 | Login/register/refresh attempts per IP per minute |

| MAIL_ENABLED, MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM | disabled | SMTP for warning/critical notifications |
2. Database
=======
StockPilot — Smart Inventory and Supply Chain Management
StockPilot is a full-stack inventory and supply chain management application designed to help businesses manage products, monitor stock levels, organize suppliers, and track purchasing and sales operations through a centralized platform.

The project combines a React-based frontend with a Java Spring Boot backend and PostgreSQL database.
Overview
Managing inventory across products, warehouses, suppliers, and orders can become difficult when information is scattered across different systems. StockPilot aims to bring these operations together in one place, making inventory information easier to access and manage.

The application is structured as a full-stack web project, with a frontend for user interaction, a backend for business logic and REST APIs, and a relational database for persistent storage.
Technology Stack
Frontend

- React

- TypeScript

- Vite

- Tailwind CSS

- Axios

- React Router

Backend

- Java 21

- Spring Boot 3

- Spring Data JPA

- Spring Security

- JWT-based authentication

Database and Development Tools

- PostgreSQL

- Flyway database migrations

- Maven

- Docker Compose
Key Functional Areas
- Dashboard: A central view for inventory and operational information.

- Product and Category Management: Organize and maintain product records.

- Inventory and Warehouses: Track stock quantities and warehouse-related information.

- Supplier Management: Maintain supplier information and support purchasing workflows.

- Purchase Orders and Receipts: Organize procurement and received-stock records.

- Sales and Customers: Manage customer information and sales-order workflows.

- Reports and Exports: Support inventory reporting and CSV export functionality.

- Security: Provide authentication and protected application access.

- Audit and Notifications: Support operational tracking and relevant system notifications.

Note: The availability of each feature depends on its current implementation in the project. Verify each module in the running application before presenting it as completed.
Project Architecture
The application follows a frontend–backend architecture:

1. React frontend: Provides the user interface and communicates with backend endpoints.

2. Spring Boot backend: Handles API requests, application logic, validation, and data access.

3. PostgreSQL database: Stores application records.

4. Flyway: Manages database schema migrations.

5. Docker Compose: Helps run supporting development services, including PostgreSQL.
Project Structure

StockPilot/

├── backend/             # Java Spring Boot application

├── frontend/            # React and TypeScript application

├── docker-compose.yml   # Development services

└── README.md            # Project documentation

Running the Project Locally
Prerequisites
Install the following before running the application:

- Java 21

- Node.js and npm

- Docker Desktop

- Git
1. Start PostgreSQL
From the project root:

docker compose up -d postgres

The schema is created automatically by Flyway when the backend starts — there is nothing to run by hand.

To start over from a clean database: docker compose down -v && docker compose up -d postgres.
3. Backend

cd backend

./mvnw spring-boot:run

Runs on http://localhost:8090. If java -version is not 21, point JAVA_HOME at a JDK 21 first

(e.g. export JAVA_HOME=$(/usr/libexec/java_home -v 21) on macOS, or

export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home with Homebrew).
4. Frontend
=======
2. Start the backend
Open a terminal and run:

cd backend

export JAVA_HOME=$(/usr/libexec/java_home -v 21)

export PATH="$JAVA_HOME/bin:$PATH"

./mvnw spring-boot:run

The backend is configured to start on port 8090 in the current development setup.
3. Start the frontend
Open a second terminal:

cd frontend

npm install

npm run dev

Open http://localhost:5173. The Vite dev server proxies /api to the backend, so browser and API share one

origin (VITE_API_PROXY_TARGET in frontend/.env changes the target).
Sample users (local development only)
The dev seed creates these accounts, all with password Password123!. They exist only when the dev

profile is active and must never be used outside a local machine.

| Email | Role | Can do |

|---|---|---|

| admin@stockpilot.local | ADMIN | Everything, incl. users/roles and audit logs |

| inventory@stockpilot.local | INVENTORY_MANAGER | Catalog, warehouses, stock operations, suppliers, purchase orders, approving others' POs |

| sales@stockpilot.local | SALES_MANAGER | Customers, sales orders, shipments |

| viewer@stockpilot.local | VIEWER | Read-only access to operational data and reports |

New self-registered accounts are always VIEWER; an admin can promote them under Users & roles.

The seed also generates 6 months of history (≈120 sales orders, 30 received POs, transfers, adjustments) plus open

POs in every state, so dashboards and reports have meaningful numbers immediately.
API documentation
- Swagger UI: http://localhost:8090/swagger-ui.html (log in via POST /api/v1/auth/login, then click

Authorize and paste the accessToken)

- OpenAPI JSON: http://localhost:8090/v3/api-docs

- Health: http://localhost:8090/actuator/health

Main resources under /api/v1: auth, users, roles, categories, products, warehouses, inventory

(+ /movements, /transfers, /receipts, /issues, /adjustments, /alerts), suppliers, purchase-orders,

goods-receipts, customers, sales-orders, shipments, notifications, dashboard, reports, audit-logs.

Errors always use one shape:

{ "status": 409, "code": "INSUFFICIENT_STOCK", "message": "Insufficient stock for DESK-1 in MAIN: requested 4, available 3",

&#x20; "path": "/api/v1/sales-orders", "timestamp": "…", "fieldErrors": [ { "field": "…", "message": "…" } ] }

Status codes: 400 validation, 401 not signed in, 403 role not allowed, 404 not found,

409 conflicts (duplicates, invalid status transitions, insufficient stock, stale versions), 429 rate limited.
Tests

\# Backend: 15 unit tests + 59 integration tests against a real PostgreSQL container (Docker must be running)

cd backend && ./mvnw test



\# Frontend: 18 component/logic tests

cd frontend && npm test



\# Frontend type-check + production build

cd frontend && npm run build

Covered: registration/login/refresh rotation/reuse detection/logout, role-based access for every role, product

validation and duplicate SKUs, optimistic locking, stock receipt/issue/transfer/adjustment, negative-inventory

prevention, threshold notifications, PO approval (incl. self-approval block), partial/complete/idempotent receipts,

sales order totals, insufficient stock, reservation and release, shipping/delivery, concurrent confirmations and

transfers, report and dashboard arithmetic, CSV export and CSV formula-injection escaping.
Important business rules
1. Stock on hand can never be negative; outbound operations are limited to available = on hand − reserved.

2. Every stock change writes an immutable movement with type, signed quantity, balance after, unit cost, reason/reference and user.

3. Adjustments and manual issues require a reason. Transfers need different source and destination warehouses.

4. Low-stock means total on hand across warehouses ≤ reorder threshold (and > 0); alerts fire once when the threshold is crossed.

5. Purchase orders: only drafts are editable; approval requires SUBMITTED; a non-admin cannot approve a PO they created; rejection returns it to draft with a reason; cancellation is allowed only before anything is received.

6. Receipts can only be booked against approved/partially received orders and never above the outstanding quantity.

7. Sales orders: prices come from the catalogue at line creation; confirming reserves stock for all lines or none; shipping consumes reservations and issues stock; only drafts/confirmed orders can be cancelled (releasing reservations); delivery is recorded via shipment tracking, which only moves forward.

8. Archived products cannot be received or added to new orders; inactive suppliers/customers/warehouses/categories cannot be used for new documents; a warehouse must be empty before deactivation; a category with products cannot be deleted.

9. Dashboard definitions — sales revenue: CONFIRMED/SHIPPED/DELIVERED orders by order date; purchase spend: APPROVED or later POs; pending POs: SUBMITTED/APPROVED/PARTIALLY_RECEIVED; pending SOs: DRAFT/CONFIRMED.

10. Users cannot remove their own admin role or disable themselves, and at least one active admin must remain.
Screenshots
Add screenshots to docs/screenshots/ and reference them here, e.g.:

| Dashboard | Purchase order | Sales order |

|---|---|---|

| docs/screenshots/dashboard.png | docs/screenshots/purchase-order.png | docs/screenshots/sales-order.png |
ER diagram
See [ER-DIAGRAM.md](docs/ER-DIAGRAM.md) (Mermaid, renders on GitHub).
Known limitations
- Time zone: business dates (order dates, report ranges) are calculated in UTC. Around midnight in other time

  zones an order created "today" can show yesterday's date.

- Rate limiting is in-memory per instance; a multi-instance deployment should use a shared store (e.g. Redis) or the API gateway.

- Single currency (USD) and no tax/discount handling on orders.

- Product images are referenced by URL (validated http/https); there is no file upload.

- Purchase/sales orders support one line per product; units are whole numbers.

- Supplier and customer pickers in forms list the first 100 active records (fine for the demo; a searchable picker like the product picker would be needed at larger scale).

- No production deployment configuration (container image, HTTPS/reverse proxy) is included; docker-compose.yml provides development dependencies only.

=======

Open the local URL printed by Vite in the terminal. It is commonly http\://localhost:5173, depending on the frontend configuration.
Configuration
Check the backend and frontend configuration files for database connection details, API base URLs, and required environment variables. Keep passwords, access tokens, private keys, and other secrets out of GitHub.
Learning and Development
StockPilot is a full-stack development project for exploring frontend development, REST API integration, backend application design, relational database management, authentication, and local development workflows.
Future Improvements
Potential improvements include more detailed analytics, automated low-stock alerts, expanded reporting, and additional test coverage.
Author
Developed as a full-stack software development project.
License
No license has been specified yet. Contact the repository owner before reusing or redistributing this project's code