# Trading workspace review, November 16, 2026

Rebuilt the existing static browser interface. Matching, accounting, API controllers, schema and dependencies remain unchanged. The UI labels all activity as sandbox data, with one-second polling and no external market feed.

Local preview: http://127.0.0.1:8080. The local PostgreSQL Compose service is running. The preview serves the current static source directory through Spring's resource location setting.

## Checked

- 59 Java unit tests passed with zero failures/errors. Maven packaging passed during implementation. The full Testcontainers verify suite was not rerun; browser tests exercised the real local API/database.
- Real browser tests used the local Spring API and PostgreSQL. Created separate DEMO.*, UITEST.* and RACEA./RACEB.* sandbox symbols; existing instruments were not reset. Test records remain in the local database.
- Guided demo created nine resting orders. A crossing 100-unit buy generated two actual executions: 80 units followed by 20 units, both at 150.1000. Validated the returned trades, receipt and remaining best ask.
- Tested depth/execution charts, exact four-decimal price selection, quantity presets, order entry, cancellation of the specific newly submitted order, account positions, market-order unfilled-remainder cancellation and instrument creation.
- A delayed response for a previous symbol cannot overwrite the newly selected book. Tested at 768/1024-pixel widths; fifth-decimal limit inputs are rejected.
- Desktop and 390-pixel mobile layout checks passed with no horizontal page overflow or JavaScript page errors. Inspected screenshots. Positions remain horizontally scrollable inside their table on narrow screens.
- Simulated book API failure and confirmed the disconnected/stale message. Notifications are dismissible and limited to two.
- Cancellation checks wait for the specific submitted-price row before cancelling and verify that row disappears after the refresh.

## Boundaries

The demo persists sandbox orders and positions. It does not reset existing markets. After an interrupted demo request, the next action creates a new market rather than automatically repeating an uncertain POST. Account activity reports its latest-100-orders scope; the chart loads at most 100 executions and the tape shows ten. Browser estimates use approximate arithmetic; Java remains authoritative for matching and accounting. No authentication, external prices, brokerage execution or production deployment was added. Existing untracked lab44-report files were left untouched.

## Plain-language entry, November 16

The default experience now introduces two sellers and a buyer, then submits actual sandbox orders through the existing engine. The first and second sell orders use separate seller accounts (B and C); account A buys. Cards explain the observed 80-unit and 20-unit matches and why the waiting offer's price is used. The story retains its own execution result when the full workspace is explored. The illustration shows the two relevant offers, not the complete multi-level book.

Browser checks passed the complete story, actual engine matching, correct outcome cards, final totals, navigation between simple/full views, and mobile fit. Repeated the original full-workspace checks with account B's new -80-share result; they passed. Fixed the added Simple view control's mobile header wrapping. No Java/domain/schema changes were needed.
