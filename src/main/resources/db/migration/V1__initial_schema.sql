-- Trade order matching engine, initial schema.
--
-- Money columns are NUMERIC, never DOUBLE PRECISION. A binary float cannot represent 0.10
-- exactly, so repeated addition of prices and P&L drifts; NUMERIC is base-10 and exact, and
-- it is the type an auditor expects to find under a money column.
--
--   price / realised_pnl  NUMERIC(19,4)  - prices and cash, four decimal places
--   average_cost          NUMERIC(19,8)  - a quotient, so it needs headroom beyond 4dp or a
--                                          fraction of a cent leaks into P&L on every
--                                          partial fill and compounds over a day's trading


-- One row per tradable symbol. Its only job is to be the lock target that serialises
-- matching per symbol; see InstrumentRepository.findAndLockBySymbol.
CREATE TABLE instruments (
    symbol     VARCHAR(32)  PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL
);


CREATE TABLE orders (
    id                 UUID          PRIMARY KEY,
    account_id         UUID          NOT NULL,
    symbol             VARCHAR(32)   NOT NULL REFERENCES instruments (symbol),
    side               VARCHAR(4)    NOT NULL,
    order_type         VARCHAR(8)    NOT NULL,
    price              NUMERIC(19,4),
    quantity           BIGINT        NOT NULL,
    remaining_quantity BIGINT        NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,

    -- Time priority. createdAt is a wall clock reading and two orders can share one;
    -- an identity column is monotonic and total, so the book has a deterministic queue.
    sequence_number    BIGINT        GENERATED ALWAYS AS IDENTITY UNIQUE,

    CONSTRAINT orders_side_chk      CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT orders_type_chk      CHECK (order_type IN ('LIMIT', 'MARKET')),
    CONSTRAINT orders_status_chk    CHECK (status IN ('OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED')),
    CONSTRAINT orders_quantity_chk  CHECK (quantity > 0),
    CONSTRAINT orders_remaining_chk CHECK (remaining_quantity >= 0 AND remaining_quantity <= quantity),

    -- A LIMIT order is meaningless without a price and a MARKET order is meaningless with
    -- one. Enforced here as well as in the API layer, because the database is the last line
    -- of defence and outlives any particular application version.
    CONSTRAINT orders_price_chk CHECK (
        (order_type = 'LIMIT'  AND price IS NOT NULL AND price > 0) OR
        (order_type = 'MARKET' AND price IS NULL)
    )
);

-- The book query. Partial index: only resting orders are ever scanned for a match, so
-- filled and cancelled orders, which are the overwhelming majority over time, stay out of
-- the index entirely. Column order mirrors the ORDER BY in OrderRepository.
CREATE INDEX idx_orders_book
    ON orders (symbol, side, price, sequence_number)
    WHERE status IN ('OPEN', 'PARTIALLY_FILLED');

CREATE INDEX idx_orders_account
    ON orders (account_id, sequence_number DESC);


CREATE TABLE trades (
    id              UUID         PRIMARY KEY,
    symbol          VARCHAR(32)  NOT NULL REFERENCES instruments (symbol),
    buy_order_id    UUID         NOT NULL REFERENCES orders (id),
    sell_order_id   UUID         NOT NULL REFERENCES orders (id),
    price           NUMERIC(19,4) NOT NULL,
    quantity        BIGINT       NOT NULL,
    executed_at     TIMESTAMPTZ  NOT NULL,
    sequence_number BIGINT       GENERATED ALWAYS AS IDENTITY UNIQUE,

    CONSTRAINT trades_price_chk    CHECK (price > 0),
    CONSTRAINT trades_quantity_chk CHECK (quantity > 0)
);

CREATE INDEX idx_trades_symbol ON trades (symbol, sequence_number DESC);


CREATE TABLE positions (
    account_id   UUID          NOT NULL,
    symbol       VARCHAR(32)   NOT NULL REFERENCES instruments (symbol),

    -- Signed: positive is long, negative is short, zero is flat.
    quantity     BIGINT        NOT NULL,
    average_cost NUMERIC(19,8) NOT NULL,
    realised_pnl NUMERIC(19,4) NOT NULL,

    PRIMARY KEY (account_id, symbol),
    CONSTRAINT positions_average_cost_chk CHECK (average_cost >= 0)
);
