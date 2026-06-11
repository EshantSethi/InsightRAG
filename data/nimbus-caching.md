# Nimbus Caching

Nimbus offers a declarative caching abstraction so expensive method results can be reused without
hand-written cache code.

## Enabling Caching

Annotate a method with `@Cacheable("name")` to cache its return value. The cache key is derived
from the method arguments. The first call computes and stores the value; later calls with the same
arguments return the cached value.

```java
@Component
public class RatesService {
    @Cacheable("rates")
    public Rate lookup(String currency) {
        return expensiveLookup(currency);
    }
}
```

## Time To Live

Cached entries have a **default time-to-live (TTL) of 300 seconds**. After the TTL elapses the
entry is evicted and the next call recomputes it. Override the TTL per cache with
`nimbus.cache.<name>.ttl-seconds`.

## Eviction Policy

When a cache reaches its maximum size, Nimbus evicts entries using a **least-recently-used (LRU)**
policy. The **default maximum size is 1,000 entries** per cache, configurable with
`nimbus.cache.<name>.max-size`.

## Manual Invalidation

Annotate a method with `@CacheEvict("name")` to remove entries. By default it evicts the entry for
the matching key; use `@CacheEvict(value = "name", allEntries = true)` to clear the whole cache.
Eviction runs **after** the annotated method returns successfully, so a failed update does not
discard valid cached data.

## Null Handling

By default Nimbus does **not** cache `null` return values, to avoid masking transient failures as
cached misses. Enable null caching explicitly with `nimbus.cache.<name>.cache-null=true`.
