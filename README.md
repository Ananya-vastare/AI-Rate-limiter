# AI-Aware Rate Limiter

A production-style **AI-Aware Rate Limiter** built using **Java 21**, **Spring Boot 3**, and **Maven**. The application enforces both **Requests Per Minute (RPM)** and **Tokens Per Minute (TPM)** limits for AI/LLM APIs, ensuring fair resource allocation and preventing excessive usage. It uses a thread-safe in-memory implementation, making it lightweight, fast, and easy to deploy without requiring external services.


## Features

- AI-aware rate limiting using both **Requests Per Minute (RPM)** and **Tokens Per Minute (TPM)**.
- Thread-safe in-memory implementation using `ConcurrentHashMap`.
- Independent rate-limiting windows maintained for every client.
- Configurable rate limit policies for different clients.
- RESTful APIs for rate limit validation and quota status.
- Real-time responses with remaining request quota, remaining token quota, and window reset time.
- Automatic expiration and reset of rate-limiting windows.
- Input validation and centralized exception handling.
- Clean, modular architecture following SOLID principles.
- Unit and integration testing for business logic and API endpoints.
- Lightweight implementation with no SQL database or external storage dependencies.
- Docker-ready architecture for easy deployment.


## Why RPM and TPM?

Traditional rate limiters only count the number of requests. However, AI and Large Language Model (LLM) APIs consume varying numbers of tokens per request. A single large request may consume significantly more resources than several smaller ones. This project combines **Requests Per Minute (RPM)** and **Tokens Per Minute (TPM)** limiting to provide a more accurate and efficient mechanism for protecting AI services from abuse while ensuring fair resource utilization.


## Architecture

```
controller/
service/
storage/
model/
config/
exception/
util/
```

### Layer Responsibilities

- **Controller** – Handles incoming HTTP requests and responses.
- **Service** – Contains the core rate-limiting business logic.
- **Storage** – Manages rate-limit counters using a thread-safe in-memory store.
- **Model** – Contains request, response, and domain models.
- **Configuration** – Stores application configuration and policies.
- **Exception** – Provides centralized exception handling.
- **Utility** – Shared helper classes and utilities.



## How It Works

For every incoming request, the application:

1. Identifies the client.
2. Retrieves or creates the client's active rate-limiting window.
3. Tracks both request count and estimated token usage.
4. Checks the configured RPM and TPM limits.
5. Allows or rejects the request based on both limits.
6. Returns the remaining request quota, remaining token quota, and reset time.



## Design Highlights

- Fixed-window rate limiting algorithm.
- O(1) lookup and update using `ConcurrentHashMap`.
- Thread-safe concurrent request handling.
- Configurable policies through application properties.
- Modular and extensible architecture.
- Separation of concerns between API, business logic, and storage.
- Production-style code structure suitable for real-world backend applications.

## Tech Stack

- Java 21
- Spring Boot 3
- Maven
- ConcurrentHashMap
- JUnit
- Docker
- Postman


## Project Goals

This project demonstrates the implementation of a scalable, maintainable, and AI-aware backend rate-limiting system capable of controlling both request frequency and token consumption. It showcases backend architecture, concurrent programming, REST API development, clean code practices, and production-oriented software design principles.
