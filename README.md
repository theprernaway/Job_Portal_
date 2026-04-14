# 🏢 Job Portal REST API

> A production-grade backend built with Spring Boot 3.2 — connecting employers with job seekers through a secure, event-driven REST API.

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.0-green?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=flat-square)
![Redis](https://img.shields.io/badge/Redis-7.0-red?style=flat-square)
![Kafka](https://img.shields.io/badge/Apache_Kafka-3.6-black?style=flat-square)
![JWT](https://img.shields.io/badge/JWT-Auth-purple?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

---
---

## 🧾 About the Project

The **Job Portal REST API** is a fully backend-only RESTful service with no frontend. It allows:

- **Employers** to register, post jobs, review candidates, and update application statuses
- **Job Seekers** to browse open jobs, apply with cover letters, and track their applications
- **Admins** to manage all users and applications across the platform

Every action in the system is secured — login issues a JWT token, every request is verified, and roles control exactly what each user can do. Applying to a job fires a Kafka event that asynchronously notifies both the applicant and the employer. Job listings are cached in Redis to reduce database load.

---

## ✨ Key Features

| Feature | Description |
|---|---|
| **JWT Authentication** | Stateless login/logout with signed tokens (HMAC-SHA256, 24hr expiry) |
| **BCrypt Passwords** | Passwords are hashed and salted — never stored in plain text |
| **RBAC** | 3 roles: `EMPLOYER`, `JOB_SEEKER`, `ADMIN` — each sees only what they're allowed |
| **Redis Token Blacklist** | Logout invalidates the token immediately via Redis |
| **Redis Cache** | Job listings cached for 60 seconds — auto-evicted on any write |
| **Kafka Events** | 3 async event topics fire on apply, job status change, and application status change |
| **PostgreSQL** | Relational data with full integrity — unique constraints enforced at DB level |
| **Global Error Handling** | Every error returns a consistent JSON body — no HTML error pages |
| **Input Validation** | All DTOs validated with `@NotBlank`, `@Email`, `@Positive` annotations |
| **Layered Architecture** | Clean separation: Controller → Service → Repository → Model |

---

## 🛠️ Tech Stack

```
Backend Framework  :  Spring Boot 3.2.0 (Java 17)
Database           :  PostgreSQL 15
Cache / Session    :  Redis 7.0
Message Broker     :  Apache Kafka 3.6
Authentication     :  JWT (jjwt 0.11.5) + Spring Security
Password Hashing   :  BCrypt
ORM                :  Spring Data JPA + Hibernate
Build Tool         :  Maven 3.9+
Boilerplate        :  Lombok
```

---

## 📁 Project Structure

```
job-portal/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── resources/
        │   └── application.properties
        └── java/com/jobportal/
            │
            ├── JobPortalApplication.java        ← Entry point
            │
            ├── config/                          ← Layer 1: Configuration
            │   ├── SecurityConfig.java          ← RBAC rules + JWT filter setup
            │   ├── RedisConfig.java             ← Redis connection + serialization
            │   └── KafkaTopicConfig.java        ← Auto-creates 3 Kafka topics
            │
            ├── security/                        ← Layer 2: Security
            │   ├── JwtUtil.java                 ← Generate + validate tokens
            │   ├── JwtFilter.java               ← Intercepts every HTTP request
            │   ├── UserDetailsServiceImpl.java  ← Load user from DB for Spring
            │   └── RedisTokenService.java       ← Blacklist + cache helper
            │
            ├── controller/                      ← Layer 3: REST Endpoints
            │   ├── AuthController.java          ← /api/auth/**
            │   ├── JobController.java           ← /api/jobs/**
            │   └── ApplicationController.java   ← /api/applications/**
            │
            ├── dto/                             ← Layer 4: Data Transfer Objects
            │   ├── LoginRequestDto.java
            │   ├── LoginResponseDto.java
            │   ├── RegisterRequestDto.java
            │   ├── JobRequestDto.java
            │   ├── JobResponseDto.java
            │   ├── ApplicationRequestDto.java
            │   ├── ApplicationResponseDto.java
            │   └── StatusUpdateDto.java
            │
            ├── service/                         ← Layer 5: Business Logic
            │   ├── AuthService.java             ← Register, login, logout
            │   ├── JobService.java              ← CRUD + Redis cache + Kafka
            │   └── ApplicationService.java      ← Apply + Kafka events
            │
            ├── kafka/                           ← Layer 6: Messaging
            │   ├── KafkaEventDto.java           ← Event payload shape
            │   ├── KafkaProducerService.java    ← Publishes events to topics
            │   └── KafkaConsumerService.java    ← Listens + processes events
            │
            ├── repository/                      ← Layer 7: Database Access
            │   ├── UserRepository.java
            │   ├── JobRepository.java
            │   └── ApplicationRepository.java
            │
            ├── model/                           ← Layer 8: DB Entities
            │   ├── User.java                    ← users table
            │   ├── Job.java                     ← jobs table
            │   └── Application.java             ← applications table
            │
            └── exception/                       ← Layer 9: Error Handling
                └── GlobalExceptionHandler.java
```

---

## 🏗️ Architecture

```
Client (Postman / Frontend)
         │
         │  Authorization: Bearer <JWT token>
         ▼
   ┌─────────────┐
   │  JwtFilter  │ ← runs on EVERY request
   │             │   1. Check Redis blacklist (logged out?)
   │             │   2. Validate token signature + expiry
   │             │   3. Load user from PostgreSQL
   │             │   4. Set Spring Security context
   └──────┬──────┘
          │
   ┌──────▼──────┐
   │  RBAC Check │ ← SecurityConfig + @PreAuthorize
   │             │   Wrong role? → 403 Forbidden
   └──────┬──────┘
          │
   ┌──────▼──────┐     ┌─────────────┐
   │ Controller  │────▶│   Redis     │  cache hit → skip DB
   └──────┬──────┘     │   Cache     │  cache miss → query DB, store result
          │            └─────────────┘
   ┌──────▼──────┐
   │   Service   │ ← business logic, validation, rules
   └──────┬──────┘
          │
   ┌──────▼──────┐     ┌─────────────┐
   │ Repository  │────▶│ PostgreSQL  │  primary data store
   └──────┬──────┘     └─────────────┘
          │
   ┌──────▼──────┐     ┌─────────────┐     ┌──────────────┐
   │   Service   │────▶│    Kafka    │────▶│   Consumer   │
   │  (post DB)  │     │   Topics    │     │ Email notify │
   └─────────────┘     └─────────────┘     └──────────────┘
```

---

## ✅ Prerequisites

Make sure all of these are installed before running the app:

| Tool | Version | Download |
|---|---|---|
| Java JDK | 17 LTS | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org/download.cgi |
| PostgreSQL | 15+ | https://www.postgresql.org/download |
| Redis | 7.0+ | https://redis.io/download |
| Apache Kafka | 3.6+ | https://kafka.apache.org/downloads |
| IntelliJ IDEA | Community | https://www.jetbrains.com/idea/download |
| Postman | Any | https://www.postman.com/downloads |

---

## ⚙️ Installation & Setup

### Step 1 — Clone the Repository

```bash
git clone https://github.com/yourusername/job-portal.git
cd job-portal
```

### Step 2 — Set Up PostgreSQL

Open **pgAdmin** or **psql** and run:

```sql
CREATE DATABASE jobportaldb;
```

### Step 3 — Start Redis

**Windows:**
```bash
# Download from: https://github.com/microsoftarchive/redis/releases
redis-server.exe
```

**Mac / Linux:**
```bash
brew install redis        # Mac only
brew services start redis # Mac only

# Linux:
sudo apt install redis-server
sudo systemctl start redis
```

**Verify Redis is running:**
```bash
redis-cli ping
# Expected output: PONG
```

### Step 4 — Start Apache Kafka

**Step 4a — Start Zookeeper first (Terminal 1):**
```bash
# Mac / Linux:
bin/zookeeper-server-start.sh config/zookeeper.properties

# Windows:
bin\windows\zookeeper-server-start.bat config\zookeeper.properties
```

**Step 4b — Start Kafka broker (Terminal 2):**
```bash
# Mac / Linux:
bin/kafka-server-start.sh config/server.properties

# Windows:
bin\windows\kafka-server-start.bat config\server.properties
```

> **Note:** Keep both terminals open while running the app. Kafka topics (`job.applied`, `job.status.changed`, `application.status.changed`) are created automatically on first startup.

---

## 🔧 Configuration

Open `src/main/resources/application.properties` and update these values:

```properties
# ── PostgreSQL ────────────────────────────────────────────────
spring.datasource.url=jdbc:postgresql://localhost:5432/jobportaldb
spring.datasource.username=postgres
spring.datasource.password=YOUR_POSTGRES_PASSWORD_HERE

# ── JWT ──────────────────────────────────────────────────────
# Change this to any long random string (min 32 characters)
jwt.secret=jobPortalSuperSecretKeyForJwtTokenGenerationMinimum256Bits!
jwt.expiration.ms=86400000     # 24 hours

# ── Redis ────────────────────────────────────────────────────
spring.data.redis.host=localhost
spring.data.redis.port=6379
# spring.data.redis.password=    # uncomment if Redis has a password

# ── Kafka ────────────────────────────────────────────────────
spring.kafka.bootstrap-servers=localhost:9092
```

> **Security tip:** Never commit real passwords or JWT secrets to GitHub. Use environment variables or a `.env` file in production.

---

## ▶️ Running the Application

**Option A — IntelliJ IDEA:**
1. Open the project folder in IntelliJ
2. Find `JobPortalApplication.java`
3. Click the green ▶ Play button

**Option B — Terminal:**
```bash
mvn spring-boot:run
```

**Expected console output:**
```
Started JobPortalApplication in 4.2 seconds
Kafka topics created: job.applied, job.status.changed, application.status.changed
Server running at: http://localhost:8080
```

> Tables are created automatically in PostgreSQL (`ddl-auto=update`). You do not need to run any SQL scripts.

---

## 📡 API Reference

### Base URL
```
http://localhost:8080
```

### Authentication Header (required for protected routes)
```
Authorization: Bearer <your_jwt_token>
```

---

### 🔐 Auth Endpoints — `/api/auth`

#### Register
```http
POST /api/auth/register
Content-Type: application/json

{
  "name": "Ravi Kumar",
  "email": "ravi@techcorp.in",
  "password": "ravi1234",
  "role": "EMPLOYER",
  "companyName": "TechCorp India",
  "companyWebsite": "https://techcorp.in"
}
```

**For Job Seeker:**
```json
{
  "name": "Arjun Das",
  "email": "arjun@gmail.com",
  "password": "arjun1234",
  "role": "JOB_SEEKER",
  "skills": "Java, Spring Boot, MySQL",
  "bio": "3 years backend experience"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "ravi@techcorp.in",
  "role": "EMPLOYER",
  "name": "Ravi Kumar",
  "expiresInMs": 86400000
}
```

---

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "ravi@techcorp.in",
  "password": "ravi1234"
}
```

---

#### Logout
```http
POST /api/auth/logout
Authorization: Bearer <your_token>
```

**Response:**
```json
{
  "message": "Logged out successfully"
}
```
> Token is immediately blacklisted in Redis. Any further use of this token returns `401 Unauthorized`.

---

### 💼 Job Endpoints — `/api/jobs`

| Method | Endpoint | Auth Required | Role |
|---|---|---|---|
| GET | `/api/jobs` | No | Public |
| GET | `/api/jobs?openOnly=true` | No | Public |
| GET | `/api/jobs/{id}` | No | Public |
| GET | `/api/jobs/search` | No | Public |
| GET | `/api/jobs/salary?min=X&max=Y` | No | Public |
| GET | `/api/jobs/employer/{id}` | Yes | Any |
| GET | `/api/jobs/stats` | Yes | EMPLOYER / ADMIN |
| POST | `/api/jobs` | Yes | EMPLOYER only |
| PUT | `/api/jobs/{id}` | Yes | EMPLOYER only |
| DELETE | `/api/jobs/{id}` | Yes | EMPLOYER only |
| PATCH | `/api/jobs/{id}/status` | Yes | EMPLOYER only |

#### Create a Job
```http
POST /api/jobs
Authorization: Bearer <employer_token>
Content-Type: application/json

{
  "title": "Senior Java Developer",
  "company": "TechCorp India",
  "location": "Bangalore / Remote",
  "description": "Build scalable microservices using Spring Boot and Kafka.",
  "requirements": "3+ years Java, Spring Boot, REST APIs, SQL.",
  "salary": 1200000,
  "jobType": "FULL_TIME",
  "category": "Engineering",
  "experienceLevel": "Senior",
  "deadline": "2026-06-30T23:59:59",
  "employerId": 1
}
```

**Job types:** `FULL_TIME` | `PART_TIME` | `CONTRACT` | `INTERNSHIP` | `REMOTE`

#### Search Jobs
```http
GET /api/jobs/search?keyword=java&location=remote&category=Engineering&jobType=FULL_TIME
```
> All parameters are optional and combinable.

#### Update Job Status
```http
PATCH /api/jobs/1/status?status=CLOSED
Authorization: Bearer <employer_token>
```
> **Triggers Kafka event:** `job.status.changed`

---

### 📝 Application Endpoints — `/api/applications`

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/applications` | JOB_SEEKER |
| GET | `/api/applications` | ADMIN |
| GET | `/api/applications/{id}` | Any authenticated |
| GET | `/api/applications/applicant/{id}` | JOB_SEEKER / ADMIN |
| GET | `/api/applications/job/{jobId}` | EMPLOYER / ADMIN |
| PATCH | `/api/applications/{id}/status` | EMPLOYER |
| DELETE | `/api/applications/{id}` | JOB_SEEKER / ADMIN |

#### Apply to a Job
```http
POST /api/applications
Authorization: Bearer <seeker_token>
Content-Type: application/json

{
  "jobId": 1,
  "applicantId": 2,
  "coverLetter": "I have 3 years of Java and Spring Boot experience and I am passionate about scalable systems.",
  "resumeUrl": "https://example.com/resumes/arjun-das.pdf"
}
```
> **Triggers Kafka event:** `job.applied` — notifies employer and confirms to applicant.

#### Update Application Status (Employer)
```http
PATCH /api/applications/1/status
Authorization: Bearer <employer_token>
Content-Type: application/json

{
  "status": "SHORTLISTED",
  "notes": "Strong candidate, schedule technical interview."
}
```

**Available statuses:** `PENDING` → `REVIEWING` → `SHORTLISTED` → `HIRED` or `REJECTED`

> **Triggers Kafka event:** `application.status.changed` — sends tailored email to applicant.

---

## 🔑 How Authentication Works

```
1. Client sends POST /api/auth/login with email + password

2. Spring Security loads user from PostgreSQL and checks BCrypt hash

3. If correct → JwtUtil generates a signed token:
   Header:  { alg: HS256 }
   Payload: { sub: "ravi@techcorp.in", role: "EMPLOYER", exp: <timestamp> }
   Signature: HMAC-SHA256(header.payload, secret)

4. Token returned to client as: eyJhbGci...

5. Client sends token in every future request:
   Authorization: Bearer eyJhbGci...

6. JwtFilter runs on every request:
   a. Extract token from Authorization header
   b. Check Redis — is token blacklisted? (logged out) → 401 if yes
   c. Validate signature + expiry → 401 if invalid
   d. Load user from PostgreSQL
   e. Set Spring Security context → @PreAuthorize works downstream

7. On logout → token stored in Redis with TTL = remaining lifetime
   → Any future use of that token → 401 immediately
```

---

## 🛡️ How RBAC Works

Access is controlled at two levels:

**Level 1 — URL rules (SecurityConfig.java):**
```
/api/auth/**              → Public (no token needed)
GET /api/jobs/**          → Public (anyone can browse jobs)
POST /api/jobs            → EMPLOYER only
POST /api/applications    → JOB_SEEKER only
PATCH .../status          → EMPLOYER only
/api/users/**             → ADMIN only
Everything else           → Must be logged in (any role)
```

**Level 2 — Method rules (@PreAuthorize):**
```java
@PreAuthorize("hasRole('EMPLOYER')")           // only employers
@PreAuthorize("hasRole('JOB_SEEKER')")         // only seekers
@PreAuthorize("hasAnyRole('EMPLOYER','ADMIN')") // either role
@PreAuthorize("isAuthenticated()")             // any logged-in user
```

**What each role can do:**

| Action | EMPLOYER | JOB_SEEKER | ADMIN |
|---|---|---|---|
| Browse jobs | ✅ | ✅ | ✅ |
| Post / edit / delete jobs | ✅ | ❌ | ❌ |
| Apply to jobs | ❌ | ✅ | ❌ |
| View own applications | ❌ | ✅ | ✅ |
| View candidates for a job | ✅ | ❌ | ✅ |
| Shortlist / Reject / Hire | ✅ | ❌ | ❌ |
| Manage all users | ❌ | ❌ | ✅ |

---

## ⚡ How Redis Works

Redis is used for two things:

**1. Token Blacklist (Security)**
```
User logs out
  → Token stored in Redis with key: "blacklist:<token>"
  → TTL set to token's remaining lifetime
  → JwtFilter checks Redis on every request
  → If key exists → 401 immediately, request stopped
  → After TTL expires → Redis auto-deletes the key (no cleanup needed)
```

**2. Job Listing Cache (Performance)**
```
GET /api/jobs
  → Check Redis key "cache:jobs:all"
  → HIT  → return cached JSON immediately (no DB query)
  → MISS → query PostgreSQL → store in Redis (TTL=60s) → return

POST/PUT/DELETE /api/jobs
  → Save to PostgreSQL
  → Evict "cache:jobs:all" and "cache:jobs:open" from Redis
  → Next GET will query PostgreSQL and re-warm the cache
```

---

## 📨 How Kafka Works

Three topics are auto-created on startup. Events fire after the database save succeeds — the HTTP response goes back to the client immediately. Consumers process events in background threads.

```
Topic: job.applied
  Fired when: JOB_SEEKER applies to a job
  Consumer does:
    → Email to employer: "New application received for: Java Developer"
    → Email to applicant: "Your application has been submitted"
    → Write audit log

Topic: job.status.changed
  Fired when: EMPLOYER changes job status (OPEN ↔ CLOSED)
  Consumer does:
    → If CLOSED: notify all pending applicants
    → Write audit log

Topic: application.status.changed
  Fired when: EMPLOYER updates application status
  Consumer does:
    → SHORTLISTED: "Great news! You've been shortlisted for Java Developer"
    → HIRED:       "Congratulations! You got the job"
    → REJECTED:    "Unfortunately your application was not selected"
    → Write audit log
```

---

## 🧪 Testing with Postman

### Full end-to-end test flow:

**Step 1 — Register as Employer**
```
POST http://localhost:8080/api/auth/register
Body: { "name":"Ravi","email":"ravi@co.in","password":"ravi123","role":"EMPLOYER","companyName":"TechCorp" }
→ Copy the token from response
```

**Step 2 — Post a Job (use employer token)**
```
POST http://localhost:8080/api/jobs
Authorization: Bearer <employer_token>
Body: { "title":"Java Developer","company":"TechCorp","location":"Remote","salary":1200000,"jobType":"FULL_TIME","employerId":1 }
```

**Step 3 — Register as Job Seeker**
```
POST http://localhost:8080/api/auth/register
Body: { "name":"Arjun","email":"arjun@gmail.com","password":"arjun123","role":"JOB_SEEKER" }
→ Copy the seeker token
```

**Step 4 — Apply to the Job (use seeker token)**
```
POST http://localhost:8080/api/applications
Authorization: Bearer <seeker_token>
Body: { "jobId":1,"applicantId":2,"coverLetter":"I have 3 years Java experience." }
→ Check console — Kafka event fires, email notifications logged
```

**Step 5 — Employer Shortlists (use employer token)**
```
PATCH http://localhost:8080/api/applications/1/status
Authorization: Bearer <employer_token>
Body: { "status":"SHORTLISTED","notes":"Strong candidate" }
→ Check console — Kafka fires "Great news! You've been shortlisted"
```

**Step 6 — Test RBAC (should fail)**
```
POST http://localhost:8080/api/jobs
Authorization: Bearer <seeker_token>   ← seeker trying to post a job
→ Expected: 403 Access denied
```

**Step 7 — Logout (token blacklisted)**
```
POST http://localhost:8080/api/auth/logout
Authorization: Bearer <employer_token>
→ Try using the same token again → 401 Token has been invalidated
```

---

## ❌ Error Responses

All errors return a consistent JSON body:

```json
{
  "status": 404,
  "message": "Job not found with id: 99",
  "timestamp": "2026-04-11T10:30:00"
}
```

Validation errors include a field-by-field map:

```json
{
  "status": 400,
  "message": "Validation failed",
  "timestamp": "2026-04-11T10:30:00",
  "validationErrors": {
    "email": "Invalid email format",
    "password": "Password must be at least 6 characters"
  }
}
```

| Status Code | Meaning | When it happens |
|---|---|---|
| 400 | Bad Request | Validation failed, job is closed |
| 401 | Unauthorized | Wrong password, expired or blacklisted token |
| 403 | Forbidden | Correct role but not allowed for this action |
| 404 | Not Found | Job / User / Application ID doesn't exist |
| 409 | Conflict | Email already registered, already applied to this job |
| 500 | Server Error | Unexpected internal error |

---

## 🚀 What to Add Next

- [ ] **Swagger UI** — Add `springdoc-openapi-starter-webmvc-ui` for auto-generated API docs at `/swagger-ui.html`
- [ ] **Real Email** — Replace console logs in `KafkaConsumerService` with Spring Mail + Gmail SMTP
- [ ] **Pagination** — Add `Pageable` to list endpoints — `GET /api/jobs?page=0&size=10`
- [ ] **File Upload** — Resume PDF uploads via Spring Multipart + AWS S3
- [ ] **Docker** — `Dockerfile` + `docker-compose.yml` to spin up all 5 services with one command
- [ ] **Deploy** — Railway.app or Render.com for free cloud hosting with PostgreSQL + Redis

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Commit your changes: `git commit -m "Add: your feature description"`
4. Push to branch: `git push origin feature/your-feature-name`
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License.

---

## 👨‍💻 Author

**Your Name**
- GitHub: [@yourusername](https://github.com/yourusername)
- LinkedIn: [linkedin.com/in/yourprofile](https://linkedin.com/in/yourprofile)
- Email: youremail@gmail.com

---

> ⭐ If this project helped you, please give it a star on GitHub!
