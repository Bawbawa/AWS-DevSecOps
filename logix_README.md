# LogiX

A cloud-native logistics and warehouse operations platform that helps businesses track products from the moment they arrive at a warehouse until they are shipped to a customer.

This directory contains the **application code**. The AWS deployment, infrastructure and security work are documented in the [root README](../README.md).

## Architecture

LogiX is built as a set of microservices:

- **Auth Service:** user authentication and authorization using JWT.
- **Order Service:** customer orders and their lifecycles.
- **Inventory Service:** product stock, allocations and shipments.
- **Warehouse Service:** warehouse operations such as picking and shipping.
- **Frontend:** a React application built with Vite that talks to the backend services.

Services communicate asynchronously through events using **AWS SNS and SQS**.

## Tech stack

- **Backend:** Spring Boot 4 (Java 25)
- **Database:** PostgreSQL
- **Frontend:** React, Vite
- **Messaging:** Spring Cloud AWS (SNS and SQS)
- **Security:** Spring Security (JWT)
- **Containerization:** Docker, Docker Compose

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/), or Docker Engine with Docker Compose
- [Java 25](https://jdk.java.net/25/) (only if running services without Docker)
- [Node.js](https://nodejs.org/) and npm (only if running the frontend outside Docker)

## Environment configuration

A `.env.example` file is provided in this directory. Create your own `.env` from it:

```sh
cp .env.example .env
```

Then set every variable, especially the database credentials, the JWT secret and the AWS configuration.

- Use strong, unique values. Do not reuse the example values.
- `.env` is for local development only and must stay out of version control (confirm it is listed in `.gitignore`).
- In AWS, these values come from Secrets Manager, not from a file.

## Running locally

1. Open a terminal in this directory.
2. Run:

```sh
docker-compose up --build
```

This starts the frontend, all backend services and their PostgreSQL databases.

### Service ports (local development only)

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Auth Service | http://localhost:8081 |
| Order Service | http://localhost:8082 |
| Inventory Service | http://localhost:8083 |
| Warehouse Service | http://localhost:8084 |

When deployed to AWS, services are not exposed on individual ports. All traffic enters through a single HTTPS endpoint. See the [root README](../README.md) for the deployment architecture.
