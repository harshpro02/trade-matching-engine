# Trade Order Matching Engine

A REST service that accepts buy and sell orders for financial instruments, maintains an
order book per symbol, matches orders by price-time priority, and tracks the resulting
positions and realised profit and loss per account.

This is the core of what every exchange does, reduced to the part that is actually hard:
getting the matching right, keeping it correct under concurrency, and never leaving the
book in a half-written state.

**Stack:** Java 21, Spring Boot 3.5.16, PostgreSQL 17, Flyway, JPA/Hibernate, JUnit 5,
Testcontainers, Docker.

---

## Status

| Area | State |
|---|---|
| Domain model, schema, repositories | Done |
| Flyway migrations, Hibernate schema validation | Done |
| Matching engine | In progress |
| Positions and realised P&L | Logic drafted on `Position`, not yet tested |
| REST API | Stub endpoint only |
| Per-symbol locking and concurrency test | Designed, not yet implemented |
| Dockerfile, CI, deployment | Not started |

Sections below marked **planned** describe decisions that are made but not yet in the code.
Everything else describes what is there now.

---

## Running it

Requires JDK 21 and Docker.

```bash
docker compose up -d          # PostgreSQL 17 on localhost:5432
./mvnw spring-boot:run        # app on localhost:8080
```

```bash
curl localhost:8080/actuator/health
curl localhost:8080/api/book/AAPL
```

Tests are split by what they need to run:

```bash
./mvnw test                   # unit tests only, no Docker required
./mvnw verify                 # adds Testcontainers integration tests against real Postgres
```

`./mvnw test` deliberately stays Docker-free so the fast feedback loop stays fast. The
integration tests start their own throwaway Postgres and ignore the compose container.

Coverage report after a test run: `target/site/jacoco/index.html`.

---

## Matching rules

**Price-time priority.** Among orders on the same side, the better price wins. At equal
price, the earlier order wins.

- Buy side: a higher price is better
- Sell side: a lower price is better

When a new order arrives it is matched against the opposite side of the book until it is
either fully filled or nothing crosses. A buy crosses a sell when `buyPrice >= sellPrice`;
a market order crosses whatever is available.

Each match executes at the **resting order's price**, fills
`min(incoming.remaining, resting.remaining)`, writes a trade, and updates both accounts'
positions. Any remainder of a limit order rests on the book. A market order with an
unfilled remainder is cancelled rather than rested, because it has no price at which it
could sit.

---

## Design decisions

### Money is NUMERIC, and not all money is the same NUMERIC

Every monetary column is `NUMERIC`, never `DOUBLE PRECISION`. Binary floating point cannot
represent `0.10` exactly, so error accumulates across a day of arithmetic and surfaces as a
P&L figure that does not reconcile. `NUMERIC` is base-10 and exact.

Prices and realised P&L are `NUMERIC(19,4)`. Average cost is `NUMERIC(19,8)`, and the extra
precision is deliberate. A price is an *input*, exact as quoted. An average cost is a
*quotient* — total notional over total quantity — and quotients do not terminate. Buy three
lots at 100.00 and the average is 33.333…; round that to 4dp and the discarded fraction of a
cent gets multiplied by the closed quantity on every later partial fill and booked straight
into realised P&L. Four extra digits absorb the division error so it never reaches the money.

Rounding is `HALF_EVEN`, which is what financial systems use — `HALF_UP` biases upward
across many roundings.

### Time priority rides on a sequence number, not a timestamp

`created_at` is a wall-clock reading, and two orders arriving in the same millisecond share
one. Time priority would then be a coin flip, and an order that arrived second could fill
first — a fairness bug, and on a real venue a regulatory one.

So `orders.sequence_number` is `GENERATED ALWAYS AS IDENTITY`: monotonic, total, and
impossible for two orders to share. The book sorts by `(price, sequence_number)`, so price
priority outranks time priority and the sequence breaks price ties deterministically.
`created_at` is kept for reporting and audit, but nothing depends on it for ordering.

### The database owns the schema, Hibernate only checks it

Flyway applies versioned migrations; Hibernate runs with `ddl-auto: validate` and is never
permitted to alter anything. A schema that mutates on deploy is how data quietly goes
missing, and `validate` turns a mismatch between entity and table into a startup failure
rather than a runtime surprise.

The book query is served by a **partial index**:

```sql
CREATE INDEX idx_orders_book
    ON orders (symbol, side, price, sequence_number)
    WHERE status IN ('OPEN', 'PARTIALLY_FILLED');
```

Only resting orders are ever scanned for a match. Over time, filled and cancelled orders
become the overwhelming majority of the table, and the `WHERE` clause keeps every one of
them out of the index.

Constraints that matter are enforced in the database as well as in the API layer: a `LIMIT`
order must carry a positive price, a `MARKET` order must not carry one, and
`remaining_quantity` must stay within `[0, quantity]`. The database is the last line of
defence and it outlives any particular version of the application.

### Order is a state machine, so the matcher does not have to be

`Order.fill()` and `Order.cancel()` enforce their own transitions and throw on illegal ones:
an order cannot be overfilled, and one that is already filled cannot be cancelled. That lets
the matching engine stay a pure decision function — it decides *whether* and *how much* two
orders trade, and does not also police quantities.

### Realised P&L accrues on reduction, never on opening

Opening or adding to a position moves cash but does not create profit; it changes what you
own and at what average cost. Profit only becomes a fact once you have closed out at a known
price. Until then it is a mark-to-market opinion that changes every time the market moves.

So `Position.applyFill()` realises `(executionPrice - averageCost) * closedQuantity` for a
long, inverted for a short, and leaves `averageCost` untouched when a position is merely
reduced — the surviving lot keeps its original cost basis.

The case worth calling out is **crossing through zero**. Selling 150 while long 100 realises
on 100 and opens a *new short 50 at the execution price*, not a short carrying the old
long's cost basis. Getting this wrong produces P&L that looks plausible and is silently
wrong from then on.

### Concurrency: lock the book, not the orders *(planned)*

Two orders arriving simultaneously for the same symbol must not both fill the same resting
order. The obvious approach — `PESSIMISTIC_WRITE` on the resting orders about to be matched
— has two problems. Two sessions locking overlapping sets of orders in different sequences
can deadlock, and `ORDER BY ... LIMIT n FOR UPDATE` re-evaluates rows *after* the lock is
granted, so two sessions can disagree about what "the best n" are.

Instead, each symbol has a row in `instruments`, and matching takes `SELECT ... FOR UPDATE`
on that row first. This serialises all matching for one symbol while leaving different
symbols fully parallel, and it cannot deadlock, because there is only ever one lock to take.

The tradeoff is explicit: throughput on a single symbol is capped at one order at a time.
That is the right trade here — correctness on one book matters more than parallelism within
it, and the parallelism that does matter, across symbols, is preserved.
`PESSIMISTIC_WRITE` is kept on the matching-path order query as defence in depth.

### Transaction boundary *(planned)*

`MatchingService.submitOrder()` is `@Transactional`. One order submission that produces
three fills must write all three trades and all the position updates, or none of them. A
partial write leaves the book inconsistent — quantity decremented with no trade to show for
it, or a trade with no matching position change — and there is no safe way to repair that
after the fact. If the process dies mid-match, the transaction rolls back and the order is
simply never acknowledged.

---

## API

```
POST   /api/orders                submit an order
DELETE /api/orders/{id}           cancel a resting order
GET    /api/orders/{id}           order status
GET    /api/orders?accountId=     orders for an account

GET    /api/book/{symbol}         current book, aggregated by price level
GET    /api/trades?symbol=        executed trades

GET    /api/positions?accountId=  positions and realised P&L
```

Only `GET /api/book/{symbol}` exists so far, and it returns an empty book.

**Submit an order:**

```json
{
  "accountId": "3f1b8c2e-0000-4000-8000-000000000001",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "price": "150.25",
  "quantity": 100
}
```

**201 response:**

```json
{
  "orderId": "9a2c4d10-0000-4000-8000-000000000002",
  "status": "PARTIALLY_FILLED",
  "filledQuantity": 40,
  "remainingQuantity": 60,
  "trades": [
    { "tradeId": "e7d15b33-0000-4000-8000-000000000003", "price": "150.20", "quantity": 40 }
  ]
}
```

---

## Layout

```
controller/   HTTP only: validation, DTO mapping, status codes
service/      business logic and transaction boundaries
repository/   Spring Data JPA interfaces, including the book queries
domain/       entities, enums, and the behaviour that belongs on them
dto/          request and response records
```

Money and matching logic live in `domain/` and `service/`, never in a controller.

---

## Deliberately out of scope

No authentication, no UI, no market data feed, no websockets, no order types beyond `LIMIT`
and `MARKET`, no microservices. Each of those turns a focused project into an unfinished one.
