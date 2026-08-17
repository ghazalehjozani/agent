# Hello World Spring Boot Application

A simple Spring Boot application demonstrating basic REST API functionality.

## Prerequisites

- Java 17 or higher
- Maven 3.6+ (or use the Maven Wrapper)

## Project Structure

```
hello-world/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/helloworld/
│   │   │       ├── HelloWorldApplication.java
│   │   │       └── controller/
│   │   │           └── HelloController.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
│           └── com/example/helloworld/
│               ├── HelloWorldApplicationTests.java
│               └── controller/
│                   └── HelloControllerTests.java
└── README.md
```

## Running the Application

### Using Maven Wrapper (recommended):
```bash
./mvnw spring-boot:run
```

### Using Maven (if globally installed):
```bash
mvn spring-boot:run
```

### Building and Running:
```bash
mvn clean package
java -jar target/hello-world-0.0.1-SNAPSHOT.jar
```

## API Endpoints

- **GET /hello** - Returns "Hello World"

## Testing

### Running All Tests:
```bash
mvn test
```

### Running Specific Test:
```bash
mvn test -Dtest=HelloControllerTests
```

## Application Details

- **Port:** 8080
- **Logging Level:** DEBUG for application packages, INFO for root
- **Framework:** Spring Boot 3.2.0
- **Java Version:** 17

## Architecture Notes

- Follows Spring Boot conventions with minimal Maven layout
- Uses SLF4J for logging (no System.out)
- Tests follow the testing pyramid: fast unit tests with @WebMvcTest
- Immutable design principles where applicable
