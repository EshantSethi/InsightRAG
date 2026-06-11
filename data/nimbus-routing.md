# Nimbus Routing

Routing maps incoming HTTP requests to handler methods. Routes are declared with annotations on
methods inside a class annotated `@Controller`.

## Declaring Routes

Use `@Route` with an HTTP method and a path. The path is matched against the request URI.

```java
@Controller
public class GreetingController {
    @Route(method = GET, path = "/greet/{name}")
    public String greet(@Path String name) {
        return "Hello, " + name;
    }
}
```

## Path Variables

Path variables use the **`{name}` syntax** inside the path. Bind them to handler parameters with
the `@Path` annotation. If the parameter name differs from the placeholder, pass the name
explicitly: `@Path("name")`. Path variables are always treated as required; a request that does
not match the placeholder simply does not match the route.

## Query Parameters

Bind query-string parameters with `@Query`. A query parameter is **optional by default** and
resolves to `null` when absent. Mark it required with `@Query(required = true)`, which causes
Nimbus to return **HTTP 400** when the parameter is missing.

## Route Matching Order

When multiple routes could match a request, Nimbus chooses the **most specific** path. Static
segments outrank path variables, so `/greet/admin` is preferred over `/greet/{name}` when both
are defined. If two routes are equally specific, Nimbus raises an error at startup rather than
guessing, so ambiguous routing is caught during the wire phase.

## Content Negotiation

A handler that returns a `String` produces `text/plain` by default. Returning any other object
serializes it to JSON with content type `application/json`. To force a content type, annotate the
handler with `@Produces`.
