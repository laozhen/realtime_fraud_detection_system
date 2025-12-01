# Contributing to Fraud Detection System

First off, thank you for considering contributing to the Fraud Detection System! 

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)

## Code of Conduct

This project adheres to a code of conduct. By participating, you are expected to uphold this code.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues. When creating a bug report, include:

- **Clear title and description**
- **Steps to reproduce**
- **Expected vs actual behavior**
- **Environment details** (Java version, Kubernetes version, cloud provider)
- **Logs and stack traces**

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. Include:

- **Clear title and description**
- **Use case and motivation**
- **Proposed solution**
- **Alternatives considered**

### Pull Requests

1. Fork the repo and create your branch from `main`
2. Make your changes
3. Add tests for new functionality
4. Ensure all tests pass
5. Update documentation
6. Submit a pull request

## Development Setup

### Prerequisites

- Java 21
- Docker Desktop
- kubectl and Helm
- IDE (IntelliJ IDEA recommended)

### Initial Setup

```bash
# Clone the repository
git clone https://github.com/hsbc/fraud-detection-system.git
cd fraud-detection-system

# Build the project
./gradlew build

# Run tests
./gradlew test

# Start local environment
docker-compose up -d
```

### Running Locally

```bash
# Start fraud detection service
./gradlew fraud-detection-service:bootRun

# Start transaction producer
./gradlew transaction-producer:bootRun
```

## Pull Request Process

1. **Branch Naming**:
   - Feature: `feature/description`
   - Bug fix: `fix/description`
   - Docs: `docs/description`

2. **Commit Messages**:
   - Use present tense ("Add feature" not "Added feature")
   - Use imperative mood ("Move cursor to..." not "Moves cursor to...")
   - Limit first line to 72 characters
   - Reference issues and PRs

3. **Testing**:
   - Write unit tests for new code
   - Add integration tests for new features
   - Ensure >80% code coverage

4. **Documentation**:
   - Update README.md if needed
   - Add JavaDoc for public APIs
   - Update ARCHITECTURE.md for design changes

5. **Review Process**:
   - At least one approval required
   - All CI checks must pass
   - No merge conflicts

## Coding Standards

### Java Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use Lombok annotations judiciously
- Prefer composition over inheritance
- Write immutable objects when possible

### Package Structure

```
com.hsbc.fraud.detection
├── config          # Configuration classes
├── controller      # REST controllers
├── model           # Domain models
├── rule            # Fraud detection rules
├── service         # Business logic
├── messaging       # MQ abstraction
└── exception       # Custom exceptions
```

### Naming Conventions

- **Classes**: PascalCase (e.g., `FraudDetectionEngine`)
- **Methods**: camelCase (e.g., `analyzeTransaction`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_COUNT`)
- **Packages**: lowercase (e.g., `com.hsbc.fraud.detection`)

### Code Examples

**Good**:
```java
@Service
@RequiredArgsConstructor
public class FraudDetectionEngine {
    
    private final List<FraudRule> fraudRules;
    
    public FraudAlert analyzeTransaction(Transaction transaction) {
        // Clear, focused method
    }
}
```

**Bad**:
```java
public class FraudDetectionEngine {
    // Don't use raw types
    private List rules;
    
    // Don't use abbreviations
    public FraudAlert anlyz(Transaction tx) {
        // Unclear method name and parameter
    }
}
```

## Testing Guidelines

### Unit Tests

- Use JUnit 5
- Follow AAA pattern (Arrange, Act, Assert)
- One assertion per test (when practical)
- Use descriptive test names

```java
@Test
@DisplayName("Should flag transaction exceeding threshold")
void shouldFlagLargeTransaction() {
    // Arrange
    Transaction transaction = createTransaction("12000.00");
    
    // Act
    boolean result = rule.isFraudulent(transaction);
    
    // Assert
    assertTrue(result);
}
```

### Integration Tests

- Use Testcontainers for external dependencies
- Test full workflows end-to-end
- Clean up resources in `@AfterEach`

```java
@SpringBootTest
@Testcontainers
class SqsIntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(...)
    
    @Test
    void shouldProcessMessage() {
        // Test with real SQS
    }
}
```

### Test Coverage

- Minimum 80% line coverage
- 100% coverage for critical paths
- Test edge cases and error handling

## Documentation

### JavaDoc

Required for:
- All public classes
- All public methods
- Complex private methods

```java
/**
 * Analyzes a financial transaction for potential fraud.
 * 
 * @param transaction The transaction to analyze
 * @return FraudAlert if fraud detected, null otherwise
 * @throws IllegalArgumentException if transaction is null
 */
public FraudAlert analyzeTransaction(Transaction transaction) {
    // ...
}
```

### Markdown

- Use headings consistently
- Add code blocks with language tags
- Include examples
- Keep line length <120 characters

## Questions?

Feel free to open an issue with the `question` label.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

