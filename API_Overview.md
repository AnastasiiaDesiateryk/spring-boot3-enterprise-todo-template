# **Frontend API Summary (To-Do List Application)**

This document provides a concise overview of all backend API endpoints used by the frontend (React + Vite).
Only endpoints effectively invoked by the client are listed.

---

## **1. Task API**

### **1.1 List Tasks**

**GET `/api/tasks`**

Used by:

* `api.listTasks()`
* `useTasks()` hook (initial load & refreshing) - The frontend uses a custom React hook useTasks() created in the project to load tasks, synchronize them with the backend, and manage all task-related operations.

Returns the full list of tasks belonging to the authenticated user (or shared with them).

---

### **1.2 Create Task**

**POST `/api/tasks`**

Used by:

* `api.createTask(payload)`
* `TaskForm` when adding a new task

Payload includes:
title, description, priority, category, dueDate (normalized to ISO), tags, metadata, etc.

---

### **1.3 Edit Task (Partial Update)**

**PATCH `/api/tasks/{id}`**

Used by:

* `api.patchTask(id, version, patch)`
* Editing tasks inside dialogs

Includes `If-Match: <version>` for optimistic locking.

---

### **1.4 Delete Task**

**DELETE `/api/tasks/{id}`**

Used by:

* `api.deleteTask(id)`
* TaskList → delete button

---

## **2. Authentication API**

### **2.1 Firebase Token Exchange**

**POST `/auth/google`**

Used in two places:

* `exchangeAndFetchMe(idToken)`
* `useEffect` inside `AuthContext`

Purpose:
Exchanges the Firebase ID token for a backend-managed session cookie.

---

### **2.2 Logout**

**POST `/auth/logout`**

Used by:

* `logout()` inside `AuthContext`

Clears the backend session cookie and signs out the Firebase client.

---

## **3. User Info (Me)**

### **3.1 Get Current User**

**GET `/api/me`**

Used by:

* `exchangeAndFetchMe()`
* `refreshMe()`

Provides backend user identity (UUID, email, displayName).

---

## **Summary Table**

| Method | Endpoint          | Purpose                             |
| ------ | ----------------- | ----------------------------------- |
| GET    | `/api/tasks`      | Fetch all tasks                     |
| POST   | `/api/tasks`      | Create a new task                   |
| PATCH  | `/api/tasks/{id}` | Update an existing task             |
| DELETE | `/api/tasks/{id}` | Delete a task                       |
| POST   | `/auth/google`    | Exchange Firebase token for session |
| GET    | `/api/me`         | Fetch current authenticated user    |
| POST   | `/auth/logout`    | Terminate session                   |

---

Нормально, теперь у нас есть полный бэкенд. Соберу из этого внятный мини-отчёт в формате README с пометками, как это связано с фронтом.

---

# Backend API Overview & Frontend Integration

This document describes the main backend HTTP APIs exposed by the Spring Boot application and how they are used by the React frontend.

---

## 1. Authentication API (`/auth`)

### 1.1 `POST /auth/google`

**Purpose**

Exchange a Firebase ID token for a backend-issued JWT stored in an HTTP-only cookie (`APP_AUTH`).

**Controller**

```java
@PostMapping("/google")
public ResponseEntity<Void> google(@Valid @RequestBody AuthRequest request) { ... }
```

* Request body: `AuthRequest { String idToken }`
* Flow:

  1. `FirebaseIdTokenVerifier.verify(idToken)` → verifies token.
  2. Rejects if `emailVerified == false` → `401`.
  3. `userService.upsertGoogleUser(email, name)` → creates/updates user.
  4. `jwtService.issueToken(userId, email, displayName)` → creates JWT.
  5. Writes cookie `APP_AUTH` (HTTP-only, `SameSite` + `secure` depend on profile).
  6. Returns `204 No Content`.

**Cookie settings**

* Name: `APP_AUTH`
* Lifetime: 7 days (`maxAge(Duration.ofDays(7))`)
* `httpOnly: true`
* `secure: isProd` (only HTTPS in non-local profiles)
* `sameSite: "None"` in prod, `"Lax"` in local
* `path: "/"`

**Frontend integration**

* Used in `AuthContext`:

  ```ts
  const API = import.meta.env.VITE_API_URL;

  async function exchangeAndFetchMe(idToken: string) {
    const ok = await fetch(`${API}/auth/google`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken }),
      credentials: "include",
    });
    ...
  }
  ```

* `signInWithPopup` (Firebase) → get `idToken` → call `/auth/google` → backend sets cookie → subsequent API calls use this cookie for authentication.


---

### 1.2 `POST /auth/logout`

**Purpose**

Invalidate backend session by clearing the `APP_AUTH` cookie.

**Controller**

```java
@PostMapping("/logout")
public ResponseEntity<Void> logout() { ... }
```

* Overwrites `APP_AUTH` with `maxAge(0)`, effectively deleting it.
* Returns `204 No Content`.

**Frontend integration**

* Used in `logout()` in `AuthContext`:

  ```ts
  const logout = async () => {
    try {
      await fetch(`${API}/auth/logout`, {
        method: "POST",
        credentials: "include",
      }).catch(() => {});
    } finally {
      await signOut(auth);
      setUser(null);
      window.location.href = "/login";
    }
  };
  ```

---

## 2. User API (`/api/me`)

### 2.1 `GET /api/me`

**Purpose**

Return the current authenticated user as seen by the backend.

**Controller**

```java
@RestController
@RequestMapping("/api")
public class UserController {
    @GetMapping("/me")
    public ResponseEntity<MeDto> me(Authentication authentication) { ... }
}
```

* Reads `UserPrincipal` from `Authentication`.
* Fetches `AppUser` using `userService.getById(principal.getId())`.
* Returns `MeDto`:

  ```java
  public class MeDto {
      public UUID id;
      public String email;
      public String displayName;
  }
  ```

**Frontend integration**

* Used in `exchangeAndFetchMe()` and `refreshMe()`:

  ```ts
  const meRes = await fetch(`${API}/api/me`, { credentials: "include" });
  const me = await meRes.json();
  ```

* Result mapped to frontend `User`:

  ```ts
  setUser({
    id: me.id,
    email: me.email,
    name: me.displayName ?? fbUser.displayName ?? "",
    avatarUrl: fbUser.photoURL ?? undefined,
    firebaseUid: fbUser.uid,
  });
  ```

---

## 3. Task API (`/api/tasks`)

Base path: `/api/tasks`.
All endpoints require authenticated user (`UserPrincipal` in `Authentication`).

### 3.1 `GET /api/tasks`

**Purpose**

List tasks visible to the current user (own + shared).

**Controller**

```java
@GetMapping
public ResponseEntity<List<TaskDto>> list(
    @RequestParam(required = false) String q,
    @RequestParam(required = false) TaskStatus status,
    @RequestParam(required = false) TaskPriority priority,
    Authentication auth
) { ... }
```

* Uses `taskService.listTasks(userId, q, status, priority)`.

**Frontend integration**

* Frontend currently calls it **without query params(only for MVP)**:

  ```ts
  listTasks: () => http<Task[]>("/api/tasks"),
  ```

---

### 3.2 `POST /api/tasks`

**Purpose**

Create a new task belonging to the authenticated user.

**Controller**

```java
@PostMapping
public ResponseEntity<TaskDto> create(
    @Valid @RequestBody TaskCreateDto dto,
    Authentication auth
) { ... }
```

* Delegates to `taskService.createTask(userId, dto)`.
* Builds ETag header from `version`:

  ```java
  String etag = ETagUtil.formatWeak(created.version);
  return ResponseEntity.created(URI.create("/api/tasks/" + created.id))
      .header(HttpHeaders.ETAG, etag)
      .body(created);
  ```

**Frontend integration**

* Used in `api.createTask`:

  ```ts
  createTask: (payload: CreateTaskPayload) =>
    http<Task>("/api/tasks", {
      method: "POST",
      body: JSON.stringify({
        ...payload,
        dueDate: normalizeDueDate(payload.dueDate),
      }),
    }),
  ```


---

### 3.3 `GET /api/tasks/{id}`

**Purpose**

Fetch a single task by ID (only if the user is owner or has access via share).

**Controller**

```java
@GetMapping("/{id}")
public ResponseEntity<TaskDto> get(@PathVariable UUID id, Authentication auth) { ... }
```

* `taskService.getTask(id, userId)` enforces permission.
* Returns `TaskDto` with ETag header for optimistic locking.

---

### 3.4 `PATCH /api/tasks/{id}`

**Purpose**

Partially update an existing task using optimistic locking.

**Controller**

```java
@PatchMapping("/{id}")
public ResponseEntity<TaskDto> patch(
    @PathVariable UUID id,
    @RequestHeader(value = "If-Match", required = true) String ifMatch,
    @Valid @RequestBody TaskPatchDto patch,
    Authentication auth
) { ... }
```

* Reads current user from `Authentication`.
* Parses `If-Match` using `ETagUtil.parseIfMatch(ifMatch)` → gets `version`.
* Calls `taskService.patchTask(id, userId, version, patch)`.
* Returns updated `TaskDto` with new ETag.

**Frontend integration**

* Used in `api.patchTask`:

  ```ts
  patchTask: (id: string, version: number, patch: PatchTaskPayload) =>
    http<Task>(`/api/tasks/${id}`, {
      method: "PATCH",
      headers: { "If-Match": String(version) },
      body: JSON.stringify({
        ...patch,
        ...(patch.hasOwnProperty("dueDate")
          ? { dueDate: normalizeDueDate(patch.dueDate) ?? null }
          : {}),
      }),
    }),
  ```
---

### 3.5 `DELETE /api/tasks/{id}`

**Purpose**

Delete a task for the current user (only if they are the owner).

**Controller**

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) { ... }
```

* `taskService.deleteTask(id, userId)` checks permissions and deletes.

**Frontend integration**

* Used in `api.deleteTask`:

  ```ts
  deleteTask: (id: string) =>
    http<void>(`/api/tasks/${id}`, { method: "DELETE" }),
  ```
---

### 3.6 Sharing API

These endpoints handle task sharing with other users.

#### 3.6.1 `GET /api/tasks/{id}/share`

**Purpose**

List users who have access to a given task and their roles.

**Controller**

```java
@GetMapping("/{id}/share")
public ResponseEntity<List<SharedUserDto>> listShares(
    @PathVariable UUID id,
    Authentication auth
) { ... }
```

* Returns `List<SharedUserDto>` from `taskService.listShares`.

#### 3.6.2 `POST /api/tasks/{id}/share`

**Purpose**

Grant access to a given task for another user.

**Controller**

```java
@PostMapping("/{id}/share")
public ResponseEntity<Void> share(
    @PathVariable UUID id,
    @Valid @RequestBody ShareRequestDto req,
    Authentication auth
) { ... }
```

* `ShareRequestDto` contains `userEmail` and `role` (`ShareRole`).

#### 3.6.3 `DELETE /api/tasks/{id}/share`

**Purpose**

Revoke access to a given task for a user.

**Controller**

```java
@DeleteMapping("/{id}/share")
public ResponseEntity<Void> revoke(
    @PathVariable UUID id,
    @RequestParam String userEmail,
    Authentication auth
) { ... }
```

**Frontend integration**

* В `App.tsx`:

  ```tsx
  {isOwner && (
    <ShareDialog
      taskId={editingTask.id}
      open={shareOpen}
      onOpenChange={setShareOpen}
    />
  )}
  ```



  * `GET /api/tasks/{id}/share`
  * `POST /api/tasks/{id}/share` 
  * `DELETE /api/tasks/{id}/share` 

* Button "Manage access" we see - only if `isOwner === true`. 

  ```ts
  const isOwner =
    (!!meId && !!ownerId && meId === ownerId) ||
    (!!meEmail && !!ownerEmail && meEmail === ownerEmail);
  ```
---

## 4. High-level Frontend–Backend Flow

## **1. Login Flow**

* User clicks **“Login with Google”** → Firebase `signInWithPopup`.
* Frontend obtains the Firebase **ID token** → sends it to **`POST /auth/google`**.
* Backend:

  * verifies the token and email,
  * creates/updates the user record if needed,
  * issues a JWT stored in the **`APP_AUTH`** HTTP-only cookie.
* Frontend then calls **`GET /api/me`** to fetch the authenticated user profile.

---

## **2. Task Operations**

* The frontend uses the custom hook **`useTasks()`** to load tasks via **`GET /api/tasks`**.
* When creating, editing, or deleting tasks, the frontend invokes:

  * **`POST /api/tasks`**
  * **`PATCH /api/tasks/{id}`** (with `If-Match` for optimistic locking)
  * **`DELETE /api/tasks/{id}`**
* Due dates are normalized client-side using **`normalizeDueDate`** before being sent to the backend.

---

## **3. Sharing**

* Only the task owner can open the **ShareDialog**.
* The ShareDialog uses the sharing endpoints:

  * **`GET /api/tasks/{id}/share`**
  * **`POST /api/tasks/{id}/share`**
  * **`DELETE /api/tasks/{id}/share`**
* These endpoints manage access rights (grant, list, revoke).

---

## **4. Logout**

* Frontend triggers **`POST /auth/logout`**, which clears the `APP_AUTH` cookie.
* Then Firebase signs the user out locally.
* Finally, the UI redirects the user to **`/login`**.

---
