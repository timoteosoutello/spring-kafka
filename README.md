# spring-kafka

Spring Boot 4 sample on Gradle 9 with a version catalog (`gradle/libs.versions.toml`).
Two slices:

- **Hello** — minimal Kafka produce/consume round-trip.
- **Orders** — REST → Redis idempotency → `ReentrantLock` → pessimistic-locked PostgreSQL
  transaction → Kafka event, with Flyway owning the schema.

## Stack

| Piece | Version |
| --- | --- |
| Gradle | 9.6.1 (wrapper) |
| Java toolchain | 21 |
| Spring Boot | 4.1.0 (Spring Framework 7) |
| PostgreSQL | 17 (Docker) |
| Redis | 7 (Docker) |
| Apache Kafka | 4.0 KRaft (Docker) |

> Spring Boot 4 moved auto-configuration into per-technology modules. Depend on
> `spring-boot-starter-<tech>`, **not** the raw library artifact — `spring-kafka` or
> `flyway-core` alone compile fine and then fail at runtime with missing beans.

## Run

```bash
docker compose up -d          # kafka + postgres + redis, all with healthchecks
gradlew.bat bootRun           # ./gradlew bootRun on Linux/macOS
```

Flyway applies `V1`–`V3` on startup. Hibernate then runs with `ddl-auto: validate`,
so entity/schema drift fails the boot instead of silently corrupting data.

## Try it

```bash
# Kafka round-trip
curl -X POST "http://localhost:8080/hello?message=ola"
curl http://localhost:8080/hello/last

# Create an order (Idempotency-Key is mandatory)
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
  -d '{"customerId":"cust-1","product":"widget","quantity":2,"amount":19.98}'

# Same key again -> 200 with replayed:true and the SAME orderRef, no second row
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
  -d '{"customerId":"cust-1","product":"widget","quantity":2,"amount":19.98}'

# 'gizmo' is seeded with 3 units - this trips the stock check under the row lock
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" -H "Idempotency-Key: demo-002" \
  -d '{"customerId":"cust-1","product":"gizmo","quantity":999,"amount":999.00}'
```

## How order creation is guarded

Three mechanisms, each doing a different job:

| Layer | Where | Protects against | Scope |
| --- | --- | --- | --- |
| Redis `SET NX` on `Idempotency-Key` | `IdempotencyService` | client retries / double-submits | cluster-wide |
| `ReentrantLock` per product | `ProductLockRegistry` | same-product threads piling onto the DB lock | **this JVM only** |
| `SELECT … FOR UPDATE` | `ProductStockRepository.findByProductForUpdate` | overselling across instances | cluster-wide |
| `uk_orders_idempotency_key` | `V1` migration | duplicates when Redis is flushed | durable |

Sequence in `OrderService.createOrder`:

```
1. Redis SET NX on the key          -> replay? 200. in flight? 409.
2. ReentrantLock.tryLock(product)   -> 503 on timeout
3. TX (REQUIRES_NEW):
     SELECT ... FOR UPDATE on product_stock
     check + decrement stock
     INSERT order
4. commit, then unlock
5. Redis key -> orderRef (24h)
6. publish OrderCreatedEvent to order-events
```

Two details that are easy to get wrong and are called out in the code:

- **The lock wraps the transaction, not the other way round.** Lock inside the
  transactional method and it is released before the commit is visible, so a waiting
  thread reads stale stock.
- **The transaction lives in a separate bean** (`OrderTransactionalService`).
  `@Transactional` works through a proxy, so a self-invocation would run with *no*
  transaction — and a pessimistic lock outside a transaction is released immediately
  and silently does nothing.

Kafka publishing happens **after** commit, so a rollback cannot leave a phantom event.
The inverse risk (commit succeeds, publish fails) is logged rather than retried; the
production answer is a transactional outbox.

`lock_timeout = '5s'` is set on every Hikari connection, so a blocked
`FOR UPDATE` raises `CannotAcquireLockException` → **503** instead of hanging.

## Schema

`src/main/resources/db/schema.mmd` is generated from the live post-migration schema
(never hand-edited) and renders on GitHub. Regenerate it in the same commit as any
new migration.

```
db/migration/V1__create_orders_table.sql        orders (+ unique idempotency_key)
db/migration/V2__create_product_stock_table.sql product_stock (+ seed: widget/gadget/gizmo)
db/migration/V3__orders_stock_fk.sql            orders -> product_stock FK
db/schema.mmd                                   generated ER diagram
```

## Entity graphs

`OrderEntity.productStock` is `LAZY` and `spring.jpa.open-in-view` is `false`, so
touching the association after the transaction closes throws
`LazyInitializationException`. Entity graphs say "for *this* query, fetch these
associations in the same SELECT" — without making them eager everywhere.

`OrderRepository` shows four variants side by side:

| # | Method | Form | Why |
| --- | --- | --- | --- |
| 1 | `findByOrderRef` | no graph | cheapest read; association stays a proxy |
| 2 | `findWithStockByOrderRef` | **named** — `@EntityGraph("OrderEntity.withProductStock")` | reusable fetch plan declared next to the mapping |
| 3 | `findTop20ByCustomerId…` | **ad-hoc** — `@EntityGraph(attributePaths = "productStock")` | kills N+1: 1 query for 20 rows, not 21 |
| 4 | `findByIdempotencyKey` | ad-hoc on an explicit `@Query` | graphs compose with `@Query` |

`FETCH` vs `LOAD`: both make the named attributes eager, but `FETCH` (the default)
additionally forces *everything not named* to lazy, overriding the mapping, while
`LOAD` leaves unnamed attributes as mapped. Identical with one association; it starts
to matter once an entity has several.

Two things worth copying:

- **No graph on the locking query.** `ProductStockRepository.findByProductForUpdate`
  deliberately has none — a graph turns the query into a join, and `FOR UPDATE` over a
  join asks the DB to lock the joined rows too. Keep locking queries narrow.
- **Deeper trees use `@NamedSubgraph`.** This model is one level deep, so a plain
  `@NamedAttributeNode` suffices.

`GET /orders/{ref}` and `GET /orders?customerId=` return `OrderDetailResponse`, which
includes a `stock` block. That block is the proof the graph worked — remove the
`@EntityGraph` and those endpoints 500 instead.

## Endpoints

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/hello` | plain greeting |
| `POST` | `/hello?message=` | publish to `hello-topic` |
| `GET` | `/hello/last` | last message the consumer saw |
| `POST` | `/orders` | requires `Idempotency-Key`; 201 new / 200 replay |
| `GET` | `/orders/{orderRef}` | named entity graph; includes `stock`. 404 if unknown |
| `GET` | `/orders?customerId=` | 20 most recent, ad-hoc entity graph |
| `GET` | `/actuator/health` | includes db + redis + kafka |

Errors come back as RFC 9457 problem details: 400 validation, 409 stock / duplicate
in flight, 422 unknown product, 503 lock timeout.

## Postman

`postman/spring-kafka.postman_collection.json` + `postman/local.postman_environment.json`
— 14 requests, 39 assertions across two folders.

```bash
npm install -g newman
newman run postman/spring-kafka.postman_collection.json ^
  -e postman/local.postman_environment.json --delay-request 300
```

Run it as a *collection*, not as individual requests: the Kafka round-trip and the
idempotency replay both depend on requests running in order.

## Troubleshooting

**`Name for argument of type [java.lang.String] not specified ... use the '-parameters' flag`**

Spring infers `@RequestParam` / `@PathVariable` names from compiled parameter names,
which only exist if the compiler ran with `-parameters`. Gradle does this; Eclipse's
JDT compiler does not unless told to. Fixed two ways here:

1. Every binding annotation names its parameter explicitly. Do the same for new ones.
2. `.settings/org.eclipse.jdt.core.prefs` pins `methodParameters=generate`. If Eclipse
   ignores it: *Window > Preferences > Java > Compiler > "Store information about method
   parameters"*, then *Project > Clean*.

**`Schema-validation: missing table [orders]`** — Flyway did not run. Check
`/actuator/flyway` and that PostgreSQL is up.

**`FATAL: password authentication failed for user "app"` (SQLState 28P01)** — you are
almost certainly talking to a *different* PostgreSQL than the container. A native
install already holding 5432 means Docker cannot bind it, and the app connects to that
server instead, where the `app` role does not exist. PostgreSQL reports a missing role
with the same 28P01 as a wrong password, so the message is misleading.

Tell-tale sign: the error text is in your OS language. The container is initialised
with `--locale=C`, so *its* errors are always English.

Compose therefore publishes PostgreSQL on **5433**, and `application.yml` points there.
To confirm which server answers:

```bash
docker compose ps                          # is spring-kafka-postgres actually up?
docker compose logs postgres | tail -20
netstat -ano | findstr :5432               # Windows: who else holds the port?
```

To use a pre-existing local PostgreSQL instead, point the URL back at 5432 and create
the role and database it expects:

```sql
CREATE ROLE app LOGIN PASSWORD 'app';
CREATE DATABASE orders OWNER app;
```

Note that `POSTGRES_USER` / `POSTGRES_PASSWORD` only apply when the data directory is
**empty**. Changing them after the first run does nothing — `docker compose down -v`
to drop the volume and re-initialise.

**`required a bean of type '...' that could not be found`** — a Boot 4 modular starter
is missing. Add `spring-boot-starter-<tech>` to `libs.versions.toml`.

**`com.fasterxml.jackson cannot be resolved`** — Spring Boot 4 ships **Jackson 3**.
The group and package moved from `com.fasterxml.jackson` to `tools.jackson`,
`ObjectMapper` became `tools.jackson.databind.json.JsonMapper` (that is the bean Boot
auto-configures), and exceptions are now unchecked (`JacksonException extends
RuntimeException`), so `writeValueAsString` no longer needs a try/catch. Fix the
imports rather than adding `com.fasterxml.jackson:jackson-databind` — that pulls a
second, unmanaged JSON stack onto the classpath. Jackson 2 is still supported via the
deprecated `spring-boot-jackson2` module if you genuinely need it.

## Bumping versions

Everything version-related lives in `gradle/libs.versions.toml`. For Gradle itself:

```bash
./gradlew wrapper --gradle-version <version>
```
