# Nimbus Getting Started

Nimbus is a lightweight JVM web framework for building HTTP services and APIs. It focuses on
fast startup, minimal configuration, and a small dependency footprint.

## Requirements

Nimbus requires **Java 17 or later**. Earlier Java versions are not supported. The framework is
distributed as a single artifact and has no mandatory third-party runtime dependencies beyond the
JDK.

## Installation

Add the Nimbus dependency to your build. The current stable release line is the 3.x series.

```xml
<dependency>
  <groupId>io.nimbus</groupId>
  <artifactId>nimbus-core</artifactId>
  <version>3.2.0</version>
</dependency>
```

## Your First Application

A minimal Nimbus application is a single class with a `main` method. The `Nimbus.run` call boots
the embedded server.

```java
public class App {
    public static void main(String[] args) {
        Nimbus.run(App.class, args);
    }
}
```

By default the embedded server listens on **port 842**. You can change the port with the
`nimbus.server.port` configuration property or the `NIMBUS_SERVER_PORT` environment variable.

## Startup Lifecycle

On boot, Nimbus performs three phases in order: **scan** (discover components), **wire** (resolve
dependencies), and **listen** (bind the HTTP port). If wiring fails, the application exits before
binding the port, so a misconfigured app never serves traffic. Startup typically completes in under
one second for a small application because Nimbus avoids classpath scanning at request time.

## Health Endpoint

Every Nimbus application automatically exposes a health endpoint at `/healthz` that returns HTTP
200 with the body `{"status":"UP"}` once the listen phase has completed. This endpoint is enabled
by default and can be disabled with `nimbus.health.enabled=false`.
