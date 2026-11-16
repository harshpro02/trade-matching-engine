let tradeStoryStep = 0;
let tradeStoryResult = null;
let tradeStoryBusy = false;
function openTradingWorkspace() {
  document.body.classList.remove("beginner-mode");
  window.scrollTo(0, 0);
  el("workspace").focus();
}
el("story-dashboard").onclick = openTradingWorkspace;
el("story-details").onclick = openTradingWorkspace;
el("simple-view").onclick = () => {
  document.body.classList.add("beginner-mode");
  window.scrollTo(0, 0);
};
function updateTradeStory() {
  document
    .querySelectorAll(".story-steps>span")
    .forEach((step, index) =>
      step.classList.toggle(
        "active",
        index === Math.max(0, tradeStoryStep - 1),
      ),
    );
  if (tradeStoryStep === 1) {
    el("story-kicker").textContent = "01 / YOUR PRACTICE MARKET IS READY";
    el("story-title").textContent =
      "The sellers are waiting. You choose when to buy.";
    el("story-text").textContent =
      "Seller A offered 80 shares first. Seller B offered 40 shares at exactly the same price, a moment later. Now send a request to buy 100 shares, paying no more than 150.15 each.";
    el("story-evidence").textContent =
      "Both offers are actual orders in the practice market. The cards show the two offers we are about to match.";
    el("story-next").textContent = "Buy 100 practice shares →";
    el("story-market-state").textContent =
      "Practice market ready · fictional shares";
  } else if (tradeStoryStep === 2) {
    const result = tradeStoryResult;
    const first = result?.trades[0],
      second = result?.trades[1];
    const expected =
      first &&
      second &&
      first.quantity === 80 &&
      second.quantity === 20 &&
      Number(first.price) === 150.1 &&
      Number(second.price) === 150.1 &&
      result.trades.length === 2;
    el("story-kicker").textContent = "02 / THE ENGINE HAS MATCHED YOUR ORDER";
    el("story-title").textContent = expected
      ? "80 from A. 20 from B. Your 100 are matched."
      : "The engine returned a different result.";
    el("story-text").textContent = expected
      ? "Seller A arrived first, so all 80 of their shares sold first. You still needed 20, so the engine took 20 from Seller B. Seller B has 20 left to sell."
      : "Someone may have changed the practice market. Open the workspace to inspect the actual receipt instead of assuming the planned result.";
    el("story-evidence").textContent = expected
      ? "Actual execution result: 80 + 20 shares, both at 150.10. The request allowed up to 150.15."
      : `${result?.filledQuantity || 0} shares matched across ${result?.trades.length || 0} trades.`;
    el("seller-first-outcome").textContent = expected
      ? "80 sold"
      : "See receipt";
    el("seller-second-outcome").textContent = expected
      ? "20 sold · 20 left"
      : "See receipt";
    el("seller-first").classList.toggle("matched", Boolean(expected));
    el("seller-second").classList.toggle("matched", Boolean(expected));
    el("story-match-label").textContent = expected
      ? "100 shares matched at 150.10 each"
      : "Actual receipt available in workspace";
    el("story-market-state").textContent = "Completed by the matching engine";
    el("story-next").textContent = "Why did I pay 150.10, not 150.15? →";
  } else if (tradeStoryStep >= 3) {
    el("story-kicker").textContent = "03 / THE PRICE WAS ALREADY ON THE TABLE";
    const result = tradeStoryResult;
    const expected =
      result?.trades.length === 2 &&
      result.trades.every((t) => Number(t.price) === 150.1) &&
      result.filledQuantity === 100;
    el("story-title").textContent = expected
      ? "Your maximum is a ceiling, not the bill."
      : "Every trade has an execution price.";
    el("story-text").textContent = expected
      ? "You were willing to pay up to 150.15. The sellers were already asking 150.10, so that is what you paid. Price priority picks the best available offer; arrival time breaks ties."
      : "The price comes from the order already waiting in the book. Open the actual receipt to inspect the prices and quantities returned by the engine.";
    el("story-evidence").textContent =
      "This is the same matching rule the engine uses for your own sandbox orders. All demo orders remain in this separate practice market.";
    el("story-next").textContent = "Open my market and try another order ↗";
    el("story-takeaway").hidden = false;
    el("story-takeaway").replaceChildren();
    const total =
      result?.trades.reduce(
        (sum, t) => sum + Number(t.price) * t.quantity,
        0,
      ) || 0;
    for (const [value, label] of [
      [result?.filledQuantity || 0, "shares matched"],
      [result?.trades.length || 0, "separate trades"],
      [total ? money(total, 2) : "—", "total price units paid"],
    ]) {
      const card = document.createElement("div");
      const strong = document.createElement("strong");
      strong.textContent = value;
      const text = document.createElement("span");
      text.textContent = label;
      card.append(strong, text);
      el("story-takeaway").append(card);
    }
  }
}
el("story-next").onclick = async () => {
  if (tradeStoryBusy || state.busy) return;
  if (tradeStoryStep >= 3) {
    openTradingWorkspace();
    return;
  }
  if (tradeStoryStep === 2) {
    tradeStoryStep = 3;
    updateTradeStory();
    return;
  }
  tradeStoryBusy = true;
  el("story-next").disabled = true;
  el("story-message").textContent =
    tradeStoryStep === 0
      ? "Preparing your practice market…"
      : "Sending your buy request to the matching engine…";
  try {
    if (tradeStoryStep === 0) {
      state.demoStep = 0;
      await runDemoStep();
      if (state.demoStep !== 1)
        throw new Error("The practice market could not be created.");
      await runDemoStep();
      if (state.demoStep !== 2)
        throw new Error("The sellers could not be added.");
      tradeStoryStep = 1;
    } else {
      if (state.demoStep !== 2)
        throw new Error(
          "This practice market has changed. Set up a fresh one.",
        );
      await runDemoStep();
      if (state.demoStep !== 3)
        throw new Error("The buy request did not complete.");
      tradeStoryResult = state.demoResult;
      tradeStoryStep = 2;
    }
    el("story-message").textContent =
      "Practice trade complete. No real money is involved.";
    updateTradeStory();
  } catch (error) {
    tradeStoryStep = 0;
    el("story-next").textContent = "Set up a fresh practice market →";
    el("story-title").textContent = "The demo could not finish.";
    el("story-text").textContent =
      "Your request may have reached the engine before the connection failed. Start a new practice market rather than repeat an uncertain order.";
    el("story-message").textContent = error.message;
  } finally {
    tradeStoryBusy = false;
    el("story-next").disabled = false;
  }
};
