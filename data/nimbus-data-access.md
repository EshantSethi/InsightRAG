# Nimbus Data Access

Nimbus provides a thin data-access layer over JDBC with declarative repositories and transaction
management.

## Repositories

Declare a repository by annotating an interface with `@Repository`. Nimbus generates an
implementation at startup. Methods whose names start with `find` are treated as queries.

```java
@Repository
public interface UserRepository {
    Optional<User> findById(long id);
    List<User> findAll();
}
```

## Connection Pool

Nimbus manages a connection pool for each configured data source. The **default pool size is 10**
connections. Tune it with `nimbus.datasource.pool-size`. Connections are validated before use and
the default connection acquisition timeout is **30 seconds**, after which a `PoolTimeoutException`
is thrown.

## Transactions

Annotate a method with `@Transactional` to run it inside a database transaction. The **default
propagation is REQUIRED**: the method joins an existing transaction if one is active, otherwise it
starts a new one. A transaction commits when the method returns normally and **rolls back on any
unchecked exception**. Checked exceptions do not trigger a rollback unless listed in
`@Transactional(rollbackOn = ...)`.

## Isolation

The default transaction isolation level is **READ_COMMITTED**. Override it per method with
`@Transactional(isolation = SERIALIZABLE)`. Nimbus does not change the database's global isolation
setting; it applies the level to the current transaction only.

## Batch Writes

For bulk inserts, use `repository.saveAll(list)`, which groups writes into batches of **500 rows**
by default to balance memory use against round trips. The batch size is configurable with
`nimbus.datasource.batch-size`.
