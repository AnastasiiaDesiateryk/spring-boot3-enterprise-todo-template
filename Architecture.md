#  Architecture & Next-Step Evolution (Hexagonal Upgrade Roadmap)

> *This document summarizes the current backend architecture of the BFH Task Manager project and outlines the planned transition toward a clean Hexagonal Architecture (Ports & Adapters) in the next major version.*

---

## 1. Current Architecture (v1) — Clean, Service-Centric, Modular

<img width="1147" height="453" alt="Screenshot 2025-12-11 at 10 45 40" src="https://github.com/user-attachments/assets/c5ae88b2-ae59-46ff-b0f5-93312de15a49" />


### Overview

The current implementation uses a **layered architecture**, optimized for clarity, maintainability, and strong security boundaries:

* **Web Layer:** REST controllers (`AuthController`, `TaskController`, `UserController`)
* **Security Layer:** Spring Security filter chain, JWT issuing/verification, Google Identity integration
* **Service Layer:** Business logic (task lifecycle, sharing, optimistic locking)
* **Repository Layer:** JPA repositories with custom SQL for access-controlled queries
* **Domain Layer:** Entities (`Task`, `TaskShare`, `AppUser`) with versioning, enums, metadata
* **Utility Layer:** Task mapping (MapStruct), ETag handling, JSON metadata helpers
* **Infrastructure:** PostgreSQL + Flyway, Spring Boot 3, Java 21

This structure already delivers:

* Strong isolation of concerns
* Clear access-control boundaries (service + repository enforcement)
* High testability (PIT mutation tests protecting critical paths)
* Secure auth pipeline (JWT, strict issuer & secret checks, cookie fallback)

---

## 2. V.2 Evolve to Hexagonal Architecture (Ports & Adapters)

> **“Hexagonal Architecture separates business logic from delivery and technical infrastructure.
> The domain becomes completely independent from frameworks, HTTP, databases, and external services.
> Controllers, repositories, and JWT become replaceable adapters.
> This makes the system easier to test, evolve, and scale.”**

### Benefits for Our Project

| Benefit                    | Why It Matters for Task Manager                                                              |
| -------------------------- | -------------------------------------------------------------------------------------------- |
| **Domain at the center**   | Sharing rules, versioning, authorization become pure logic, not tied to Spring/JPA           |
| **Replaceable adapters**   | Can swap PostgreSQL → DynamoDB, REST → GraphQL, Firebase → SwissID, without rewriting domain |
| **Better testability**     | Domain tests run without Spring context or database                                          |
| **Longevity**              | Ideal for an codebase that evolves over years                                                |
| **Multi-frontend support** | Same domain can power web, mobile, CLI, or internal automations                              |

---

## 3. Planned Hexagonal Architecture (v2) — Conceptual Overview

### Domain (Core)

* **Entities:** `Task`, `TaskShare`, `AppUser`
* **Value Objects:** `TaskId`, `UserId`, `ShareRole`, `Priority`, `Status`
* **Domain Services:**

  * Task sharing policy
  * Access-control rule evaluation
  * Versioning & conflict resolution
* **Events (Future):**

  * `TaskUpdated`
  * `TaskShared`

The domain has **no Spring, no JPA, no web annotations**.

---

### Ports (Interfaces)

**Inbound Ports (Driving adapters):**

* `CreateTaskUseCase`
* `ModifyTaskUseCase`
* `ShareTaskUseCase`
* `ListTasksQuery`
* `AuthenticateUserUseCase`

These define **what the system can do** — independent of REST or HTTP.

**Outbound Ports (Driven adapters):**

* `TaskRepositoryPort`
* `UserRepositoryPort`
* `ShareRepositoryPort`
* `TokenIssuerPort`
* `TokenValidatorPort`
* `IdentityProviderPort`

These describe **what the domain needs**, without specifying how it is implemented.

---

### Adapters (Implementation)

**Inbound Adapters:**

* REST controllers (Spring MVC)
* CLI tool (future)
* Event listeners (future)

**Outbound Adapters:**

* JPA implementation of repositories
* JWT implementation of token ports
* Firebase adapter for identity
* PostgreSQL persistence layer
* Caching adapter (in future)

Everything here becomes replaceable.

---

## 4. Proposed Folder Structure (v2)

```
/src/main/java/com.example.todo
  /domain
    /model
    /service
    /event
    /ports
      inbound/
      outbound/
  /application
    /usecases
    /dto
  /adapters
    /web
    /security
    /persistence
    /firebase
  /config
```

This structure clearly divides **core → application → adapters**.

---

## 5. Migration Plan (Incremental, Safe)

### Phase 1 — Introduce Ports

* Extract interfaces for repositories & token services
* Services depend on ports instead of JPA

### Phase 2 — Move Business Logic to Domain Services

* Access-control rules
* Sharing logic
* Versioning checks
* Validation policies

### Phase 3 — Split Controllers into Use-Cases

* Introduce `application` layer
* Remove business logic from controllers

### Phase 4 — Isolate Adapters

* Move JPA, JWT, Firebase, REST into `adapters/*`

### Phase 5 — Domain Purification

* Remove all Spring/JPA annotations from domain
* Convert entities into pure objects
* Replace MapStruct usage with explicit mappers or a dedicated mapping layer

---

## 6. Description

> Our current architecture uses layered design with a strong service and repository boundary.
> In the next version, we are evolving toward Hexagonal Architecture so the domain becomes independent from Spring, JPA, and external systems.
> Controllers, repositories, JWT, and Firebase will all become adapters implementing domain ports.
> This increases testability, scalability, and maintainability, and lets the same domain power multiple frontends or integrations.

---

## 7. Expected Result (v2)

* Cleaner domain logic
* Replaceable infrastructure
* Simplified testing
* Easier onboarding for new BFH developers
* Architecture that matches enterprise standards (Swiss fintech / insurance grade)

