# Hargos Auth Service (Backend)

Centralized authentication and authorization microservice for the Hargos platform, built with Spring Boot 3 and Java 21.

## Overview

Hargos Auth is a multi-tenant authentication service that handles user management, role-based access control, subscription billing via Stripe, and acts as the central identity provider for other microservices in the platform (e.g., RiTrack).

## Tech Stack

- **Java 21** / **Spring Boot 3.5**
- **Spring Security** + JWT authentication
- **Spring Data JPA** / Hibernate
- **PostgreSQL** (dedicated `auth` schema)
- **Stripe** payment processing and subscription management
- **Google OAuth2** sign-in
- **Resend** for transactional emails
- **Bucket4j** + **Caffeine** for rate limiting
- **Nimbus JOSE+JWT** for token generation/validation

## Key Features

- **Multi-tenant RBAC**: organizations, tenants, and role-based user management (SUPER_ADMIN, TENANT_ADMIN, USER)
- **JWT authentication**: access + refresh token flow with session tracking
- **Google OAuth2**: sign-in with Google integration
- **Stripe integration**: subscription management, checkout sessions, billing portal, payment history, webhook handling
- **Invitation system**: invite users to tenants via email with role assignment
- **Access codes**: self-service tenant enrollment with configurable usage limits
- **Session management**: device tracking, concurrent session limits, admin session overview
- **Email service**: password reset, email verification, invitation notifications
- **Rate limiting**: per-endpoint throttling to prevent abuse
- **Inter-service communication**: REST client for RiTrack tenant provisioning

## Project Structure

```
src/main/java/es/hargos/auth/
├── client/          # REST clients for other microservices
├── config/          # Security, async, REST template configs
├── controller/      # REST endpoints (7 controllers)
├── dto/
│   ├── request/     # Inbound DTOs (30+ request types)
│   └── response/    # Outbound DTOs
├── entity/          # JPA entities (16 entities)
├── event/           # Domain events (tenant limit updates)
├── exception/       # Custom exceptions + global handler
├── filter/          # JWT authentication filter
├── repository/      # Spring Data repositories (15 repos)
├── security/        # Authorization service
├── service/         # Business logic (16 services)
└── util/            # JWT utilities, password validation
```

## Database

The `init-db.sql` script sets up the `auth` schema with all required tables: users, tenants, organizations, refresh tokens, Stripe subscriptions, invitations, access codes, session tracking, and tenant configuration.
