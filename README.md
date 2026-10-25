# StockPilot — Smart Inventory and Supply Chain Management

StockPilot is a full-stack inventory and supply chain management application designed to help businesses manage products, monitor stock levels, organize suppliers, and track purchasing and sales operations through a centralized platform.

The project combines a React-based frontend with a Java Spring Boot backend and PostgreSQL database.

## Overview

Managing inventory across products, warehouses, suppliers, and orders can become difficult when information is scattered across different systems. StockPilot aims to bring these operations together in one place, making inventory information easier to access and manage.

The application is structured as a full-stack web project, with a frontend for user interaction, a backend for business logic and REST APIs, and a relational database for persistent storage.

## Technology Stack

**Frontend**
- React
- TypeScript
- Vite
- Tailwind CSS
- Axios
- React Router

**Backend**
- Java 21
- Spring Boot 3
- Spring Data JPA
- Spring Security
- JWT-based authentication

**Database and Development Tools**
- PostgreSQL
- Flyway database migrations
- Maven
- Docker Compose

## Key Functional Areas

- **Dashboard:** A central view for inventory and operational information.
- **Product and Category Management:** Organize and maintain product records.
- **Inventory and Warehouses:** Track stock quantities and warehouse-related information.
- **Supplier Management:** Maintain supplier information and support purchasing workflows.
- **Purchase Orders and Receipts:** Organize procurement and received-stock records.
- **Sales and Customers:** Manage customer information and sales-order workflows.
- **Reports and Exports:** Support inventory reporting and CSV export functionality.
- **Security:** Provide authentication and protected application access.
- **Audit and Notifications:** Support operational tracking and relevant system notifications.

*Note: The availability of each feature depends on its current implementation in the project. Verify each module in the running application before presenting it as completed.*

## Project Architecture

The application follows a frontend–backend architecture:

1. **React frontend:** Provides the user interface and communicates with backend endpoints.
2. **Spring Boot backend:** Handles API requests, application logic, validation, and data access.
3. **PostgreSQL database:** Stores application records.
4. **Flyway:** Manages database schema migrations.
5. **Docker Compose:** Helps run supporting development services, including PostgreSQL.

## Project Structure

```text
StockPilot/
├── backend/             # Java Spring Boot application
├── frontend/            # React and TypeScript application
├── docker-compose.yml   # Development services
└── README.md            # Project documentation
```

## Running the Project Locally

### Prerequisites

Install the following before running the application:

- Java 21
- Node.js and npm
- Docker Desktop
- Git

### 1. Start PostgreSQL

From the project root:

```bash
docker compose up -d postgres
```

### 2. Start the backend

Open a terminal and run:

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

The backend is configured to start on port `8090` in the current development setup.

### 3. Start the frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite in the terminal. It is commonly `http://localhost:5173`, depending on the frontend configuration.

### Configuration

Check the backend and frontend configuration files for database connection details, API base URLs, and required environment variables. Keep passwords, access tokens, private keys, and other secrets out of GitHub.

## Learning and Development

StockPilot is a full-stack development project for exploring frontend development, REST API integration, backend application design, relational database management, authentication, and local development workflows.

## Future Improvements

Potential improvements include more detailed analytics, automated low-stock alerts, expanded reporting, and additional test coverage.

## Author

Developed as a full-stack software development project.

## License

No license has been specified yet. Contact the repository owner before reusing or redistributing this project's code.
