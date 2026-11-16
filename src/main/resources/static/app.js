const ACCOUNTS = [
  { id: "3f1b8c2e-0000-4000-8000-000000000001", label: "Account A" },
  { id: "3f1b8c2e-0000-4000-8000-000000000002", label: "Account B" },
  { id: "3f1b8c2e-0000-4000-8000-000000000003", label: "Account C" },
];
const state = {
  symbol: null,
  account: ACCOUNTS[0].id,
  side: "BUY",
  type: "LIMIT",
  refreshing: false,
  busy: false,
  book: null,
  trades: [],
  chart: "depth",
  demoStep: 0,
  demoSymbol: null,
  demoReceipts: [],
  connected: false,
};
const el = (id) => document.getElementById(id);
const escapeText = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const money = (value, places = 4) =>
  Number(value).toLocaleString("en-CA", {
    minimumFractionDigits: places,
    maximumFractionDigits: places,
  });
const qty = (value) => Number(value).toLocaleString("en-CA");
async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    signal: AbortSignal.timeout(15000),
    ...options,
  });
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => null);
  if (!response.ok) throw new ApiFailure(response.status, payload);
  return payload;
}
class ApiFailure extends Error {
  constructor(status, payload) {
    super((payload && payload.message) || `request failed with ${status}`);
    this.status = status;
    this.payload = payload;
  }

  detail() {
    const fields = this.payload && this.payload.fieldErrors;
    if (fields && Object.keys(fields).length > 0) {
      return Object.entries(fields)
        .map(([k, v]) => `${k}: ${v}`)
        .join("\n");
    }
    return `HTTP ${this.status}`;
  }
}

function describe(failure) {
  // fetch rejects with a plain TypeError when the server is unreachable, so this has
  // to survive an error that is not an ApiFailure and has no detail() on it.
  const detail =
    failure instanceof ApiFailure
      ? failure.detail()
      : "the server did not respond";
  return `${failure.message}\n${detail}`;
}

function toast(title, body = "", kind = "") {
  const node = document.createElement("div");
  node.className = `toast ${kind}`;
  const heading = document.createElement("div");
  heading.className = "t-title";
  heading.textContent = title;
  const text = document.createElement("div");
  text.className = "t-body";
  text.textContent = body;
  const dismiss = document.createElement("button");
  dismiss.className = "toast-dismiss";
  dismiss.type = "button";
  dismiss.setAttribute("aria-label", "Dismiss notification");
  dismiss.textContent = "×";
  dismiss.onclick = () => node.remove();
  node.append(heading, text, dismiss);
  el("toasts").append(node);
  while (el("toasts").children.length > 2)
    el("toasts").firstElementChild.remove();
  setTimeout(() => node.remove(), 4500);
}
function setStatus(kind, label) {
  el("status").className = `status ${kind}`;
  el("status").querySelector(".label").textContent = label;
  state.connected = kind === "live";
}
function setBusy(busy) {
  state.busy = busy;
  ["submit", "new-symbol", "symbol", "account", "demo-next"].forEach(
    (id) => (el(id).disabled = busy || (id === "submit" && !state.symbol)),
  );
  document
    .querySelectorAll(".cancel,.seg,.quantity-presets button")
    .forEach((button) => (button.disabled = busy));
}
function syncSubmitLabel() {
  el("submit").textContent =
    `${state.side === "BUY" ? "Buy" : "Sell"} ${state.symbol || ""}`.trim();
  el("submit").className = `primary ${state.side === "BUY" ? "buy" : "sell"}`;
  document.querySelectorAll("#side-group .seg").forEach((button) => {
    const active = button.dataset.side === state.side;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  document.querySelectorAll("#type-group .seg").forEach((button) => {
    const active = button.dataset.type === state.type;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  el("price-field").hidden = state.type === "MARKET";
  el("price").required = state.type === "LIMIT";
  el("market-hint").hidden = state.type !== "MARKET";
  updateEstimate();
}
function updateEstimate() {
  const price =
    state.type === "LIMIT"
      ? Number(el("price").value)
      : Number(
          state.side === "BUY"
            ? state.book?.asks[0]?.price
            : state.book?.bids[0]?.price,
        );
  const quantity = Number(el("quantity").value);
  el("notional").textContent =
    price > 0 && quantity > 0 && Number.isFinite(price * quantity)
      ? `≈ ${money(price * quantity, 2)}`
      : "—";
  el("estimate-note").textContent =
    state.type === "LIMIT"
      ? "Limit price × quantity. Display estimate only."
      : "Top-of-book estimate only. Actual fills may span multiple prices.";
}
function renderLadder(container, levels, maxQuantity, side) {
  container.replaceChildren();
  if (!levels.length) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = `No resting ${side === "BUY" ? "bids" : "asks"}`;
    container.append(empty);
    return;
  }
  levels.forEach((level) => {
    const row = document.createElement("button");
    row.className = "level";
    row.type = "button";
    row.setAttribute(
      "aria-label",
      `Prepare ${side === "BUY" ? "sell" : "buy"} limit at ${level.price}, ${level.quantity} resting units`,
    );
    row.innerHTML = `<div class="bar" style="width:${maxQuantity ? Math.min(100, (level.quantity / maxQuantity) * 100).toFixed(1) : 0}%"></div><span class="px">${money(level.price)}</span><span>${qty(level.quantity)}</span><span>${level.orderCount}</span>`;
    row.onclick = () => {
      if (state.busy) return;
      state.side = side === "BUY" ? "SELL" : "BUY";
      state.type = "LIMIT";
      el("price").value = String(level.price);
      syncSubmitLabel();
      el("quantity").focus();
      toast(
        "Price selected",
        `${state.side} limit at ${money(level.price)}. Review quantity before submitting.`,
      );
    };
    container.append(row);
  });
}
function renderBook(book) {
  state.book = book;
  const max = Math.max(
    1,
    ...book.bids.map((l) => l.quantity),
    ...book.asks.map((l) => l.quantity),
  );
  renderLadder(el("asks"), book.asks, max, "SELL");
  renderLadder(el("bids"), book.bids, max, "BUY");
  const bid = book.bids[0],
    ask = book.asks[0];
  el("best-bid").textContent = bid ? money(bid.price) : "—";
  el("best-ask").textContent = ask ? money(ask.price) : "—";
  el("bid-size").textContent = bid ? `${qty(bid.quantity)} units` : "No bids";
  el("ask-size").textContent = ask ? `${qty(ask.quantity)} units` : "No asks";
  const spread =
    bid && ask ? money(Number(ask.price) - Number(bid.price)) : "—";
  el("spread-value").textContent = spread;
  el("spread-stat").textContent = spread;
  const bidQty = book.bids.reduce((sum, l) => sum + l.quantity, 0),
    askQty = book.asks.reduce((sum, l) => sum + l.quantity, 0),
    total = bidQty + askQty;
  el("book-depth").textContent = qty(total);
  el("bid-share").textContent = total
    ? `Bids ${((bidQty / total) * 100).toFixed(0)}%`
    : "Bids —";
  el("ask-share").textContent = total
    ? `Asks ${((askQty / total) * 100).toFixed(0)}%`
    : "Asks —";
  el("imbalance-fill").style.width = total
    ? `${(bidQty / total) * 100}%`
    : "0%";
  renderChart();
  updateEstimate();
}
function svgChart(inner, label) {
  return `<svg viewBox="0 0 600 270" role="img" aria-label="${label}">${inner}</svg>`;
}
function renderChart() {
  const chart = el("market-chart");
  if (state.chart === "trades") {
    el("chart-caption").textContent =
      "Up to 100 executions, ordered by execution sequence";
    const trades = [...state.trades].reverse();
    if (!trades.length) {
      chart.innerHTML =
        '<div class="chart-empty"><span>⌁</span><strong>No executions yet.</strong><p>Cross a resting order to create a trade.</p></div>';
      return;
    }
    const prices = trades.map((t) => Number(t.price)),
      lo = Math.min(...prices),
      hi = Math.max(...prices),
      padding = Math.max((hi - lo) * 0.2, Math.abs(lo) * 0.0001, 0.0001),
      bottom = lo - padding,
      top = hi + padding;
    const x = (i) =>
        trades.length === 1 ? 300 : 45 + (i / (trades.length - 1)) * 530,
      y = (p) => 225 - ((p - bottom) / (top - bottom)) * 195;
    let line = trades
      .map((t, i) => `${i ? "L" : "M"}${x(i)},${y(Number(t.price))}`)
      .join(" ");
    chart.innerHTML = svgChart(
      `<path d="${line}" fill="none" stroke="#6084ac" stroke-width="2"/>${trades.map((t, i) => `<circle cx="${x(i)}" cy="${y(Number(t.price))}" r="4" fill="#426d9f"><title>${escapeText(new Date(t.executedAt).toLocaleTimeString("en-CA"))} · ${money(t.price)} × ${qty(t.quantity)}</title></circle>`).join("")}<text x="45" y="257" fill="#95a4b5" font-size="10">Earlier executions</text><text x="575" y="257" fill="#95a4b5" font-size="10" text-anchor="end">Latest</text><text x="45" y="18" fill="#8093a8" font-size="10">${money(hi)} high / ${money(lo)} low</text>`,
      "Execution prices from this engine",
    );
    return;
  }
  el("chart-caption").textContent = "Cumulative visible quantity by price";
  const book = state.book;
  if (!book || (!book.bids.length && !book.asks.length)) {
    chart.innerHTML =
      '<div class="chart-empty"><span>⌁</span><strong>A market starts with an order.</strong><p>Add a limit order or start the guided demo.</p></div>';
    return;
  }
  const prices = [...book.bids, ...book.asks].map((l) => Number(l.price));
  const low = Math.min(...prices),
    high = Math.max(...prices),
    padding = Math.max((high - low) * 0.08, Math.abs(low) * 0.0001, 0.0001),
    min = low - padding,
    max = high + padding;
  const total = (levels) => levels.reduce((sum, l) => sum + l.quantity, 0);
  const peak = Math.max(total(book.bids), total(book.asks), 1);
  const x = (p) => 45 + ((p - min) / (max - min)) * 530,
    y = (q) => 225 - (q / peak) * 190;
  let drawing = "";
  [0, 0.5, 1].forEach((f) => {
    drawing += `<text x="575" y="${y(peak * f) - 6}" fill="#95a4b5" font-size="9" text-anchor="end">${qty(peak * f)}</text>`;
  });
  for (const [levels, color] of [
    [book.bids, "#399675"],
    [book.asks, "#d48077"],
  ]) {
    if (!levels.length) continue;
    let amount = 0,
      path = `M${x(Number(levels[0].price))},225`;
    const nodes = [];
    levels.forEach((level) => {
      const xx = x(Number(level.price));
      path += ` H${xx}`;
      amount += level.quantity;
      path += ` V${y(amount)}`;
      nodes.push(
        `<circle cx="${xx}" cy="${y(amount)}" r="3" fill="${color}"><title>Price ${money(level.price)} · ${qty(level.quantity)} units at level · ${qty(amount)} cumulative</title></circle>`,
      );
    });
    const lastX = x(Number(levels[levels.length - 1].price));
    drawing += `<path d="${path} L${lastX},225 Z" fill="${color}" fill-opacity=".1"/><path d="${path}" fill="none" stroke="${color}" stroke-width="2"/>${nodes.join("")}`;
  }
  drawing += `<line x1="45" x2="575" y1="225" y2="225" stroke="#dce4ed"/><text x="45" y="253" fill="#8396aa" font-size="10">${money(low)}</text><text x="575" y="253" text-anchor="end" fill="#8396aa" font-size="10">${money(high)}</text>`;
  chart.innerHTML = svgChart(drawing, "Cumulative visible bid and ask depth");
}
function renderPositions(positions) {
  const body = el("positions").querySelector("tbody");
  el("position-count").textContent = positions.length;
  body.innerHTML = positions.length
    ? positions
        .map(
          (p) =>
            `<tr><td>${escapeText(p.symbol)}</td><td class="num ${p.quantity > 0 ? "pos" : p.quantity < 0 ? "neg" : ""}">${qty(p.quantity)}</td><td class="num">${money(p.averageCost, 8)}</td><td class="num ${Number(p.realisedPnl) > 0 ? "pos" : Number(p.realisedPnl) < 0 ? "neg" : ""}">${money(p.realisedPnl)}</td></tr>`,
        )
        .join("")
    : '<tr><td colspan="4" class="empty">No positions in this account yet.<br>A filled order creates a position.</td></tr>';
}
function renderWorking(orders, page) {
  const resting = orders.filter(
    (o) =>
      o.symbol === state.symbol &&
      (o.status === "OPEN" || o.status === "PARTIALLY_FILLED"),
  );
  el("working-count").textContent = resting.length;
  const body = el("working").querySelector("tbody");
  body.replaceChildren();
  if (!resting.length) {
    body.innerHTML =
      '<tr><td colspan="4" class="empty">No working orders for this symbol in the returned page.<br>Unfilled limit orders appear here.</td></tr>';
  }
  resting.forEach((order) => {
    const row = document.createElement("tr");
    row.innerHTML = `<td><span class="tag ${order.side}">${order.side}</span></td><td class="num">${order.price === null ? "MKT" : money(order.price)}</td><td class="num">${qty(order.remainingQuantity)}</td><td class="num"><button class="cancel" type="button" ${state.busy ? "disabled" : ""}>Cancel</button></td>`;
    row.querySelector("button").onclick = () => cancelOrder(order.orderId);
    body.append(row);
  });
  el("orders-scope").textContent =
    `Working orders for this symbol, filtered from the latest ${orders.length} account orders${page.totalElements > orders.length ? ` (${qty(page.totalElements)} total; older working orders may not be shown)` : ""}. Positions include all account symbols.`;
}
function renderTape(trades) {
  state.trades = trades;
  el("trade-count").textContent =
    `${Math.min(10, trades.length)} / ${trades.length} LOADED`;
  el("tape").querySelector("tbody").innerHTML = trades.length
    ? trades
        .slice(0, 10)
        .map(
          (t) =>
            `<tr><td>${escapeText(new Date(t.executedAt).toLocaleTimeString("en-CA", { hour12: false }))}</td><td class="num">${money(t.price)}</td><td class="num">${qty(t.quantity)}</td></tr>`,
        )
        .join("")
    : '<tr><td colspan="3" class="empty">No executions yet.</td></tr>';
  el("last-price").textContent = trades.length ? money(trades[0].price) : "—";
  el("last-size").textContent = trades.length
    ? `${qty(trades[0].quantity)} units · engine execution`
    : "No executions";
  if (state.chart === "trades") renderChart();
}
async function refresh() {
  if (!state.symbol || state.refreshing) return;
  const symbol = state.symbol,
    account = state.account;
  state.refreshing = true;
  try {
    const [book, trades, positions, orders] = await Promise.all([
      api(`/api/book/${encodeURIComponent(symbol)}?depth=12`),
      api(`/api/trades?symbol=${encodeURIComponent(symbol)}&size=100`),
      api(`/api/positions?accountId=${account}`),
      api(`/api/orders?accountId=${account}&size=100`),
    ]);
    if (symbol !== state.symbol || account !== state.account) return;
    renderBook(book);
    renderTape(trades.content);
    renderPositions(positions);
    renderWorking(orders.content, orders);
    setStatus("live", "Engine connected");
    el("connection-note").classList.remove("error");
    el("connection-note").textContent =
      `Updated ${new Date().toLocaleTimeString("en-CA")} · ${symbol} · Sandbox engine data. Polling every second; no external market feed.`;
  } catch (failure) {
    if (symbol === state.symbol && account === state.account) {
      setStatus("down", "Disconnected");
      el("connection-note").classList.add("error");
      el("connection-note").textContent =
        "Engine unavailable. Displayed data may be out of date. Retrying automatically.";
    }
  } finally {
    state.refreshing = false;
    if (symbol !== state.symbol || account !== state.account) refresh();
  }
}
function clearMarket() {
  state.book = null;
  state.trades = [];
  [
    "last-price",
    "best-bid",
    "best-ask",
    "spread-stat",
    "book-depth",
    "spread-value",
  ].forEach((id) => (el(id).textContent = "—"));
  ["asks", "bids"].forEach((id) => {
    el(id).innerHTML = '<div class="empty">Loading selected market…</div>';
  });
  ["positions", "working", "tape"].forEach((id) => {
    const columns = id === "tape" ? 3 : 4;
    el(id).querySelector("tbody").innerHTML =
      `<tr><td colspan="${columns}" class="empty">Loading selected account and market…</td></tr>`;
  });
  el("bid-size").textContent = "Loading";
  el("ask-size").textContent = "Loading";
  el("last-size").textContent = "Loading";
  el("working-count").textContent = "—";
  el("position-count").textContent = "—";
  el("bid-share").textContent = "Bids —";
  el("ask-share").textContent = "Asks —";
  el("imbalance-fill").style.width = "0%";
  renderChart();
  updateEstimate();
}
async function loadInstruments(preferred) {
  const instruments = await api("/api/instruments");
  el("symbol").replaceChildren();
  instruments.forEach((instrument) => {
    const option = document.createElement("option");
    option.value = instrument.symbol;
    option.textContent = instrument.symbol;
    el("symbol").append(option);
  });
  state.symbol = instruments.some((i) => i.symbol === preferred)
    ? preferred
    : instruments[0]?.symbol || null;
  el("symbol").value = state.symbol || "";
  el("book-symbol").textContent = state.symbol || "";
  clearMarket();
  syncSubmitLabel();
  setBusy(state.busy);
  if (!state.symbol) {
    setStatus("live", "Engine connected");
    el("connection-note").textContent =
      "No instruments yet. Create one or start the guided demo.";
    ["asks", "bids"].forEach(
      (id) =>
        (el(id).innerHTML =
          '<div class="empty">Create an instrument to begin.</div>'),
    );
    ["positions", "working", "tape"].forEach(
      (id) =>
        (el(id).querySelector("tbody").innerHTML =
          `<tr><td colspan="${id === "tape" ? 3 : 4}" class="empty">No instrument selected.</td></tr>`),
    );
  }
}
function receipt(result, context) {
  el("receipt-title").textContent =
    `${context.side} ${context.symbol} · ${result.status.replaceAll("_", " ").toLowerCase()}`;
  const fills = result.trades
    .map((t) => `${qty(t.quantity)} at ${money(t.price)}`)
    .join(", ");
  el("receipt-text").textContent =
    `${qty(result.filledQuantity)} filled · ${qty(result.remainingQuantity)} remaining${fills ? `. Executions: ${fills}.` : "."} ${result.status === "CANCELLED" ? "The unfilled market remainder was cancelled." : ""}`;
}
async function submitOrder(event) {
  event.preventDefault();
  if (state.busy) return;
  if (!state.symbol) {
    toast(
      "Choose an instrument",
      "Create one or start the guided demo.",
      "error",
    );
    return;
  }
  const quantity = Number(el("quantity").value),
    price = el("price").value.trim();
  if (!Number.isSafeInteger(quantity) || quantity <= 0) {
    toast(
      "Invalid quantity",
      "Enter a positive whole number within the browser safe integer range.",
      "error",
    );
    return;
  }
  if (
    state.type === "LIMIT" &&
    (!/^\d{1,15}(\.\d{1,4})?$/.test(price) || Number(price) <= 0)
  ) {
    toast(
      "Invalid price",
      "Use a positive price with at most four decimal places.",
      "error",
    );
    return;
  }
  const payload = {
    accountId: state.account,
    symbol: state.symbol,
    side: state.side,
    type: state.type,
    quantity,
  };
  if (state.type === "LIMIT") payload.price = price;
  setBusy(true);
  try {
    const result = await api("/api/orders", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    receipt(result, payload);
    toast(
      result.filledQuantity
        ? `Filled ${qty(result.filledQuantity)}`
        : result.status === "CANCELLED"
          ? "Remainder cancelled"
          : "Order resting",
      `${qty(result.remainingQuantity)} remaining.`,
      result.filledQuantity ? "fill" : "",
    );
  } catch (failure) {
    toast("Order rejected", describe(failure), "error");
  } finally {
    setBusy(false);
    await refresh();
  }
}
async function cancelOrder(orderId) {
  if (state.busy) return;
  setBusy(true);
  try {
    await api(`/api/orders/${encodeURIComponent(orderId)}`, {
      method: "DELETE",
    });
    toast("Order cancelled", "Remaining quantity removed from the book.");
  } catch (failure) {
    toast("Could not cancel", describe(failure), "error");
  } finally {
    setBusy(false);
    await refresh();
  }
}
function renderDemo() {
  const steps = [
    [
      "Build a market, one step at a time.",
      "Create a separate demo instrument, add resting orders, then send a crossing order. These actions write to this sandbox's real engine and database.",
      "1. Create demo market ↗",
    ],
    [
      "Set the price ladder.",
      `Market ${state.demoSymbol} is ready. Add bids and asks, including two sell orders at 150.1000: first 80 units, then 40. The earlier one should fill first.`,
      "2. Add resting orders ↗",
    ],
    [
      "Cross the spread.",
      "Send a 100-unit buy limit at 150.1500. It should fill the first 80-unit sell and 20 units of the second, both at the resting price of 150.1000.",
      "3. Submit crossing order ↗",
    ],
    [
      "Price first. Then time.",
      "Inspect the execution receipt: the earlier 80-unit sell filled before 20 units of the next. Both executed at 150.1000. Switch accounts or click a book price to keep exploring.",
      "Create another demo ↗",
    ],
  ];
  const [title, description, button] = steps[state.demoStep];
  el("demo-step-number").textContent = String(state.demoStep + 1).padStart(
    2,
    "0",
  );
  el("demo-title").textContent = title;
  el("demo-description").textContent = description;
  el("demo-next").textContent = button;
}
async function runDemoStep() {
  if (state.busy) return;
  setBusy(true);
  el("demo-result").textContent = "Working with the sandbox engine…";
  try {
    if (state.demoStep === 0 || state.demoStep === 3) {
      const symbol = `DEMO.${Date.now().toString(36).toUpperCase()}.${Math.random().toString(36).slice(2, 5).toUpperCase()}`;
      await api("/api/instruments", {
        method: "POST",
        body: JSON.stringify({ symbol }),
      });
      state.demoSymbol = symbol;
      state.demoReceipts = [];
      state.demoStep = 1;
      await loadInstruments(symbol);
      el("demo-result").textContent =
        `Created ${symbol}. Existing instruments are unchanged.`;
    } else if (state.demoStep === 1) {
      const seed = [
        ["SELL", "150.1000", 80],
        ["SELL", "150.1000", 40],
        ["SELL", "150.2000", 180],
        ["SELL", "150.3000", 240],
        ["SELL", "150.4000", 300],
        ["BUY", "149.9000", 120],
        ["BUY", "149.8000", 180],
        ["BUY", "149.7000", 240],
        ["BUY", "149.6000", 300],
      ];
      for (
        let index = state.demoReceipts.length;
        index < seed.length;
        index++
      ) {
        const [side, price, quantity] = seed[index];
        const result = await api("/api/orders", {
          method: "POST",
          body: JSON.stringify({
            accountId: ACCOUNTS[side === "BUY" ? 0 : index === 1 ? 2 : 1].id,
            symbol: state.demoSymbol,
            side,
            type: "LIMIT",
            price,
            quantity,
          }),
        });
        state.demoReceipts.push(result);
      }
      state.demoStep = 2;
      await loadInstruments(state.demoSymbol);
      el("demo-result").textContent =
        "Nine real resting orders added. Two sell orders share the best ask.";
    } else {
      const payload = {
        accountId: ACCOUNTS[0].id,
        symbol: state.demoSymbol,
        side: "BUY",
        type: "LIMIT",
        price: "150.1500",
        quantity: 100,
      };
      const result = await api("/api/orders", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      receipt(result, payload);
      state.demoResult = result;
      state.demoStep = 3;
      state.account = ACCOUNTS[0].id;
      el("account").value = state.account;
      el("account-label").textContent = "ACCOUNT A";
      await loadInstruments(state.demoSymbol);
      const expected =
        result.trades.length === 2 &&
        result.trades[0].quantity === 80 &&
        result.trades[1].quantity === 20 &&
        result.trades.every((t) => Number(t.price) === 150.1);
      el("demo-result").textContent = expected
        ? "Verified: 80 + 20 units filled at 150.1000. The remaining best ask is 20 units."
        : `Engine returned ${result.trades.length} trades and ${result.filledQuantity} filled units. Inspect the receipt; the book may have changed.`;
    }
    renderDemo();
  } catch (failure) {
    el("demo-result").textContent =
      `${failure.message}. ${state.demoStep === 1 ? `${state.demoReceipts.length} resting orders were confirmed.` : ""} The last request may have completed before the error; inspect this demo market before retrying.`;
    state.demoStep = 0;
    state.demoReceipts = [];
    renderDemo();
    toast(
      "Demo step interrupted",
      `${describe(failure)}\nStart a fresh demo market to avoid repeating an uncertain submission.`,
      "error",
    );
  } finally {
    setBusy(false);
    await refresh();
  }
}
function init() {
  ACCOUNTS.forEach((account) => {
    const option = document.createElement("option");
    option.value = account.id;
    option.textContent = account.label;
    el("account").append(option);
  });
  el("account").onchange = () => {
    state.account = el("account").value;
    el("account-label").textContent = ACCOUNTS.find(
      (a) => a.id === state.account,
    ).label.toUpperCase();
    clearMarket();
    refresh();
  };
  el("symbol").onchange = () => {
    state.symbol = el("symbol").value;
    el("book-symbol").textContent = state.symbol;
    clearMarket();
    syncSubmitLabel();
    refresh();
  };
  ["side", "type"].forEach(
    (attribute) =>
      (el(`${attribute}-group`).onclick = (event) => {
        const button = event.target.closest(".seg");
        if (!button || state.busy) return;
        state[attribute] = button.dataset[attribute];
        syncSubmitLabel();
      }),
  );
  el("price").oninput = updateEstimate;
  el("quantity").oninput = updateEstimate;
  document.querySelectorAll("[data-quantity]").forEach(
    (button) =>
      (button.onclick = () => {
        el("quantity").value = button.dataset.quantity;
        updateEstimate();
      }),
  );
  el("order-form").onsubmit = submitOrder;
  el("refresh-now").onclick = refresh;
  el("new-symbol").onclick = () => el("symbol-dialog").showModal();
  el("symbol-form").onsubmit = async (event) => {
    if (event.submitter?.value !== "create") return;
    const symbol = el("symbol-input").value.trim().toUpperCase();
    if (!symbol) {
      event.preventDefault();
      el("symbol-input").focus();
      return;
    }
    setBusy(true);
    try {
      await api("/api/instruments", {
        method: "POST",
        body: JSON.stringify({ symbol }),
      });
      await loadInstruments(symbol);
      toast("Instrument created", symbol);
      el("symbol-input").value = "";
    } catch (failure) {
      toast("Could not create instrument", describe(failure), "error");
    } finally {
      setBusy(false);
      await refresh();
    }
  };
  el("rules-open").onclick = () => el("rules-dialog").showModal();
  el("rules-close").onclick = () => el("rules-dialog").close();
  el("demo-toggle").onclick = () => {
    el("demo-panel").hidden = !el("demo-panel").hidden;
    renderDemo();
  };
  el("demo-close").onclick = () => (el("demo-panel").hidden = true);
  el("demo-next").onclick = runDemoStep;
  ["depth", "trades"].forEach(
    (chart) =>
      (el(`${chart}-tab`).onclick = () => {
        state.chart = chart;
        ["depth", "trades"].forEach((id) => {
          const active = id === chart;
          el(`${id}-tab`).classList.toggle("on", active);
          el(`${id}-tab`).setAttribute("aria-pressed", String(active));
        });
        renderChart();
      }),
  );
  document.querySelectorAll("[data-activity]").forEach(
    (button) =>
      (button.onclick = () => {
        document.querySelectorAll("[data-activity]").forEach((tab) => {
          const active = tab === button;
          tab.classList.toggle("on", active);
          tab.setAttribute("aria-pressed", String(active));
          el(`${tab.dataset.activity}-panel`).hidden = !active;
        });
      }),
  );
  setBusy(false);
  syncSubmitLabel();
  loadInstruments("AAPL")
    .then(refresh)
    .catch(() => {
      setStatus("down", "Disconnected");
      el("connection-note").textContent =
        "Engine unavailable. Check the server and use Refresh to retry.";
      el("connection-note").classList.add("error");
    });
  el("refresh-now").onclick = () =>
    state.symbol
      ? refresh()
      : loadInstruments("AAPL")
          .then(refresh)
          .catch(() =>
            toast(
              "Engine unavailable",
              "Could not load instruments. Retry shortly.",
              "error",
            ),
          );
  setInterval(() => {
    if (!state.symbol) {
      loadInstruments(null)
        .then(refresh)
        .catch(() => setStatus("down", "Disconnected"));
    } else refresh();
  }, 1000);
}
init();
