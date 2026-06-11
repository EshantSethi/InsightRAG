# Nimbus Dependency Injection

Nimbus includes a built-in dependency injection (DI) container. Components are discovered during
the scan phase and instantiated during the wire phase.

## Declaring Components

Annotate a class with `@Component` to register it. Nimbus creates one instance and manages its
lifecycle.

```java
@Component
public class ClockService {
    public Instant now() {
        return Instant.now();
    }
}
```

## Constructor Injection

Nimbus uses **constructor injection by default**. Dependencies are declared as constructor
parameters and supplied automatically during wiring. Field injection is not supported, because
constructor injection keeps components immutable and makes their dependencies explicit and
testable.

```java
@Component
public class ReportService {
    private final ClockService clock;
    public ReportService(ClockService clock) {
        this.clock = clock;
    }
}
```

## Scopes

The **default scope is singleton**: one shared instance per application. Annotate a component with
`@Scope("request")` to get a new instance per HTTP request. Request-scoped components must not be
injected directly into singletons; inject a `Provider<T>` instead so the instance is resolved
lazily on each request.

## Resolving Cycles

If two components depend on each other, wiring fails fast with a `CircularDependencyException`
that names the cycle. Nimbus does not break cycles with proxies — the recommended fix is to extract
the shared logic into a third component.

## Qualifiers

When multiple components implement the same interface, mark one as `@Primary` or disambiguate
injection points with `@Named("...")`. Without a qualifier and with more than one candidate,
wiring fails at startup.
