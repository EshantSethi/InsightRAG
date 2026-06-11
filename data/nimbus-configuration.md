# Nimbus Configuration

Nimbus reads configuration from files, environment variables, and command-line arguments, merged
in a defined order of precedence.

## Configuration File

By default Nimbus loads configuration from a file named **`nimbus.yaml`** on the classpath. Point
to a different file with the `--nimbus.config` command-line argument.

```yaml
nimbus:
  server:
    port: 8080
  datasource:
    pool-size: 20
```

## Precedence

When the same property is set in multiple places, Nimbus applies this precedence, highest first:
**command-line arguments, then environment variables, then the configuration file, then built-in
defaults**. This lets you bake defaults into `nimbus.yaml` and override them per environment
without rebuilding.

## Environment Variables

Any property can be set via an environment variable by uppercasing it and replacing dots with
underscores. For example, `nimbus.server.port` becomes `NIMBUS_SERVER_PORT`. This mapping is how
containerized deployments override configuration.

## Profiles

A profile is a named set of overrides. Activate one with the **`NIMBUS_PROFILE` environment
variable** (for example `NIMBUS_PROFILE=prod`). Nimbus then also loads `nimbus-prod.yaml` and
layers it on top of the base `nimbus.yaml`. Only one profile may be active at a time; setting
multiple comma-separated profiles raises a configuration error at startup.

## Secrets

Nimbus never logs configuration values whose key contains `password`, `secret`, or `token`. These
are masked as `****` in startup logs and in the `/healthz` diagnostics output, so credentials are
not leaked through logging.
