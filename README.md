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
| Matching engine | Done: pure `OrderBookMatcher`, 21 unit tests |
| Positions and realised P&L | Done: 17 unit tests, including crossing through zero |
| REST API | Done: every endpoint below is wired |
| Per-symbol locking and concurrency test | Done: 8 concurrent buyers against one resting order |
| REST API paging and depth limits | Done: nothing returns an unbounded list |
| Web UI | Done: order entry, depth ladder, positions and tape at `/` |
| Dockerfile and CI | Done: multi-stage image, GitHub Actions runs the full suite |
| Publishing to a registry | Not started: CI builds the image but pushes nowhere |
| Authentication | Out of scope: see below |

78 tests pass: 59 unit tests that need no Docker, and 19 integration tests against a real
PostgreSQL 17 on `./mvnw verify`.

---

## Running it

Requires JDK 21 and Docker.

**For development**: database in Docker, application on the host so you keep a fast
restart loop:

```bash
docker compose up -d          # PostgreSQL 17 on localhost:5432
./mvnw spring-boot:run        # app on localhost:8080
```

**Everything in containers**: builds the image and wires it to the database:

```bash
docker compose --profile app up -d
```

The application is behind a compose profile so the plain `up -d` above still brings up only
the database; starting both by default would take port 8080 and collide with
`spring-boot:run`.

Then open **`http://localhost:8080/`** for the trading terminal.

A symbol needs a row in `instruments` before it can be traded; that row is what matching
locks, so there is nothing to serialise on without it. Use the **+ New** button in the UI,
or:

```bash
curl -X POST localhost:8080/api/instruments \
  -H 'Content-Type: application/json' -d '{"symbol":"AAPL"}'
```

```bash
curl localhost:8080/actuator/health
curl localhost:8080/api/book/AAPL

# rest an offer, then lift it
curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
  -d '{"accountId":"3f1b8c2e-0000-4000-8000-000000000001","symbol":"AAPL",
       "side":"SELL","type":"LIMIT","price":"150.00","quantity":100}'

curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
  -d '{"accountId":"3f1b8c2e-0000-4000-8000-000000000002","symbol":"AAPL",
       "side":"BUY","type":"LIMIT","price":"155.00","quantity":100}'
```

The second order fills at **150.00**, not the 155.00 it was willing to pay: the resting
order's price is the one that was advertised, and it was there first.

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
*quotient*, total notional over total quantity, and quotients do not terminate. Buy three
lots at 100.00 and the average is 33.333…; round that to 4dp and the discarded fraction of a
cent gets multiplied by the closed quantity on every later partial fill and booked straight
into realised P&L. Four extra digits absorb the division error so it never reaches the money.

Rounding is `HALF_EVEN`, which is what financial systems use; `HALF_UP` biases upward
across many roundings.

### Time priority rides on a sequence number, not a timestamp

`created_at` is a wall-clock reading, and two orders arriving in the same millisecond share
one. Time priority would then be a coin flip, and an order that arrived second could fill
first: a fairness bug, and on a real venue a regulatory one.

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
the matching engine stay a pure decision function: it decides *whether* and *how much* two
orders trade, and does not also police quantities.

### The matcher is pure, so the hard part is testable in milliseconds

`OrderBookMatcher.match()` takes the incoming order and an already-ordered list of resting
orders and returns a list of `Fill` records. No Spring, no database, no clock, and it
mutates nothing it is given; deciding is not doing. `MatchingService` then applies those
fills inside one transaction, holding the per-symbol lock.

That split is what makes the matching rules cheap to test exhaustively: 21 tests over every
crossing, pricing, quantity and stopping case, running with no container in well under a
second. Testing the same rules through the service would mean a Postgres round trip per
case, and the cases that matter (a market order sweeping three levels, a book that stops
crossing halfway down) are exactly the ones that are fiddliest to set up that way.

The matcher also **does not sort**. Price-time priority is expressed once, in the `order by`
of the repository book queries, and the matcher consumes what it is handed. Two definitions
of "best" could drift apart, and the database's is the one that has to be right, because it
is the one holding the rows. Trusting the order is also what lets matching stop at the first
non-crossing order rather than scanning the whole book: if the best remaining price is too
dear, everything behind it is worse.

### Realised P&L accrues on reduction, never on opening

Opening or adding to a position moves cash but does not create profit; it changes what you
own and at what average cost. Profit only becomes a fact once you have closed out at a known
price. Until then it is a mark-to-market opinion that changes every time the market moves.

So `Position.applyFill()` realises `(executionPrice - averageCost) * closedQuantity` for a
long, inverted for a short, and leaves `averageCost` untouched when a position is merely
reduced; the surviving lot keeps its original cost basis.

The case worth calling out is **crossing through zero**. Selling 150 while long 100 realises
on 100 and opens a *new short 50 at the execution price*, not a short carrying the old
long's cost basis. Getting this wrong produces P&L that looks plausible and is silently
wrong from then on.

### Concurrency: lock the book, not the orders

Two orders arriving simultaneously for the same symbol must not both fill the same resting
order. The obvious approach, `PESSIMISTIC_WRITE` on the resting orders about to be matched,
has two problems. Two sessions locking overlapping sets of orders in different sequences
can deadlock, and `ORDER BY ... LIMIT n FOR UPDATE` re-evaluates rows *after* the lock is
granted, so two sessions can disagree about what "the best n" are.

Instead, each symbol has a row in `instruments`, and matching takes `SELECT ... FOR UPDATE`
on that row first. This serialises all matching for one symbol while leaving different
symbols fully parallel, and it cannot deadlock, because there is only ever one lock to take.

The tradeoff is explicit: throughput on a single symbol is capped at one order at a time.
That is the right trade here: correctness on one book matters more than parallelism within
it, and the parallelism that does matter, across symbols, is preserved.
`PESSIMISTIC_WRITE` is kept on the matching-path order query as defence in depth.

### Transaction boundary

`MatchingService.submitOrder()` is `@Transactional`. One order submission that produces
three fills must write all three trades and all the position updates, or none of them. A
partial write leaves the book inconsistent (quantity decremented with no trade to show for
it, or a trade with no matching position change), and there is no safe way to repair that
after the fact. If the process dies mid-match, the transaction rolls back and the order is
simply never acknowledged.

---

### The image ships a JRE, not a toolchain

The `Dockerfile` builds in two stages. The first has a JDK and Maven and produces the jar;
the second carries only a JRE and that jar. Nothing that compiled the code survives into the
image that runs in production: no compiler, no build cache, no source. It runs as an
unprivileged user for the same reason: this process never needs root, so it should never
have it.

Dependencies are resolved from the POM before any source is copied, so editing a Java file
costs a recompile rather than a re-download of every dependency.

The image build skips tests deliberately. The integration tests start a Docker container of
their own, and this build is already running inside one. CI runs `./mvnw verify` on the
host, where a daemon is actually available; see `.github/workflows/ci.yml`, which runs the
full suite on every push and then confirms the image still builds.

---

## API

```
POST   /api/instruments                       list a new tradable symbol
GET    /api/instruments                       every listed symbol

POST   /api/orders                            submit an order
DELETE /api/orders/{id}                       cancel a resting order
GET    /api/orders/{id}                       order status
GET    /api/orders?accountId=&page=&size=     orders for an account, paged

GET    /api/book/{symbol}?depth=              book, aggregated by price level
GET    /api/trades?symbol=&page=&size=        executed trades, paged

GET    /api/positions?accountId=              positions and realised P&L
```

Errors come back as a JSON body naming the failure: 404 for an unknown symbol or order id,
409 for cancelling an order that has already filled or listing a symbol twice, and 400 with
per-field messages for a request that fails validation.

**Nothing returns an unbounded list.** Orders and trades are paged, capped at 200 per page;
the book takes a `depth` and is capped at 50 price levels. Those two tables grow without
limit, so an endpoint that returned all of either would eventually load a whole day's
trading into memory to answer one request.

The book is aggregated **in the database**, with `group by price` and a row limit, rather
than by reading every resting order and summing them in Java. A symbol with fifty thousand
resting orders still answers in as many rows as the caller asked for.

## Web UI

The application serves a trading terminal at `http://localhost:8080/`: order entry, a live
depth ladder, positions, working orders and the tape, polling once a second. It is plain
HTML, CSS and JavaScript served from `src/main/resources/static`, with no build step and no
npm: it ships inside the jar and is available the moment the app starts.

Two demo accounts are preset so one browser can take both sides of a trade and watch the
position and realised P&L move on each.

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

No market data feed, no websockets, no order types beyond `LIMIT` and `MARKET`, no
microservices. Each of those turns a focused project into an unfinished one.

**No authentication.** `account_id` is taken from the request and trusted; there is no
accounts table and no foreign key behind it. The assumption is explicit: identity belongs to
an upstream system, and this service matches orders for an account it has already been told
about. Adding a login to a matching engine would not have made the matching any more
correct.

Two consequences worth naming rather than discovering later. An account can **trade with
itself**: the positions net out correctly, so it is not a correctness bug, but real venues
block it because that is the shape of wash trading. And submission is **not idempotent**: a
retried `POST /api/orders` creates a second order, where a real venue would require a
client-supplied order id and reject the duplicate.
