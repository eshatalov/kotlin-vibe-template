# Template

A vibecoded template for coding agents to use as a starting point for backend services.
The project emphasizes testing and strictness, which may result in higher token consumption.

## Tech Stack

- **Spring Boot 4** with WebFlux
- **Kotlin 2.3** with Coroutines
- **Java 25** with GraalVM Native Image
- **jOOQ** for type-safe SQL
- **R2DBC** for reactive database access
- **PostgreSQL** database
- **Redis Streams** for event publishing
- **Flyway** for database migrations
- **OpenAPI** for external contracts

### Testing

- **JUnit 5** with Spring Boot Test
- **MockK** for mocking
- **Database Rider** for declarative database testing
- **Zonky Embedded PostgreSQL** for integration tests

## License

[MIT](LICENSE)
