# Matching Engine: two-minute walkthrough

Use the public URL once verified, or http://127.0.0.1:8080 locally. Open the simple view. The demo persists fictional orders in a new practice instrument; use its generated symbol when inspecting positions.

## 0:00-0:20: problem

“This project models the part of a stock exchange that decides which buy and sell requests trade with each other. I wanted to make the outcome correct and explainable: which order went first, what price was used, and how the resulting position was recorded.”

## 0:20-1:00: demonstrate

Set up the practice market, then buy 100 practice shares.

“Seller A offered 80 shares at 150.10 first. Seller B offered 40 at the same price later. The buyer allows up to 150.15. The engine fills 80 from A, then 20 from B, both at 150.10. Better prices go first, and arrival order breaks ties. The price comes from the offer already waiting, so the buyer's maximum is not automatically what they pay.”

Open the workspace and inspect the receipt, working orders and account positions. Switch to the seller accounts if useful.

## 1:00-1:40: engineering decision

“The matching calculation is separate from the HTTP controller. I use decimal arithmetic for money and a database sequence to make time priority deterministic. A per-symbol database lock serialises matching for the same instrument. The order updates, trades and position changes belong in one transaction, so another request cannot observe a half-completed trade. Different instruments do not need the same lock.”

Point to one test in the repository: the concurrent buyers test or an average-cost crossing-through-zero case.

## 1:40-2:00: limitation

“This is a shared sandbox, not a brokerage. It has no external prices or real money. Identity is supplied by the caller, market orders cancel their unfilled remainder, and retries are not idempotent yet. The public demo has request limits; it is not an authenticated production exchange. A useful next requirement is client order IDs that make uncertain retries safe.”

## Questions to practise

- Why is a database sequence preferable to timestamps for equal-price priority?
- What fails if order, trade and position updates commit separately?
- How would you prevent a retried POST from creating a second order?

Show only measured results and explain the code rather than reciting the script.
