# 🚀 Job Portal Backend System

A scalable backend system for a Job Portal platform built using Spring Boot, designed with secure authentication, role-based access control, caching, and event-driven architecture using Kafka.

The system supports job posting, job applications, user authentication, and asynchronous event processing, following clean layered architecture principles.

---

## 🧠 Key Highlights

- JWT-based authentication with Spring Security
- Role-Based Access Control (User, Recruiter, Admin)
- Redis caching for performance optimization
- Kafka-based event-driven architecture
- Clean layered architecture (Controller → Service → Repository)
- Global exception handling for consistent API responses
- Secure password storage using BCrypt

---

## 🧰 Tech Stack

### Backend
- Java 17
- Spring Boot
- Spring Security (JWT + RBAC)
- Spring Data JPA (Hibernate)

### Database
- MySQL / PostgreSQL

### Caching
- Redis (Token storage, performance optimization)

### Messaging / Event System
- Apache Kafka (Producer–Consumer architecture)

### Build Tool
- Maven

### DevOps
- Docker (Containerization)

---

## 🏗️ System Architecture

Layered Architecture:

Controller → Service → Repository → Database

Additional Components:
- JWT Filter for authentication
- Redis caching layer for fast access
- Kafka event-driven processing layer

---

## 🔐 Authentication Flow

1. User registers or logs in
2. Server validates credentials
3. JWT token is generated
4. Token is sent to client
5. Token is validated using JwtFilter
6. Role-based access enforced via Spring Security

---

## 📡 Kafka Event Flow

- User action triggers event (Job posted / Application submitted)
- Producer publishes event to Kafka topic
- Consumer processes event asynchronously
- Enables loose coupling and scalability

---

## ⚡ Features

### 👤 User Module
- Registration & login
- JWT authentication
- Role-based access control

### 💼 Job Module
- Create job postings (Recruiter/Admin)
- View and search jobs (User)

### 📄 Application Module
- Apply for jobs
- Track application status
- Prevent duplicate applications

### ⚡ Performance
- Redis caching for frequently accessed data
- Reduced database load

### ⚠️ Exception Handling
- Global exception handling using @ControllerAdvice
- Standardized API error responses

---

## 📂 Project Structure

```bash
src/main/java
 ├── controller
 ├── service
 ├── repository
 ├── entity
 ├── security
 │    ├── JwtFilter
 │    ├── SecurityConfig
 │    ├── JwtUtil
 ├── kafka
 │    ├── producer
 │    ├── consumer
 ├── config
 ├── exception
