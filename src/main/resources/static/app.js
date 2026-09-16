const ACCOUNTS = [
    {id: '3f1b8c2e-0000-4000-8000-000000000001', label: 'Account A'},
    {id: '3f1b8c2e-0000-4000-8000-000000000002', label: 'Account B'}
];

const state = {
    symbol: null,
    account: ACCOUNTS[0].id,
    side: 'BUY',
    type: 'LIMIT',
    lastTradeId: null,
    refreshing: false
};

const el = (id) => document.getElementById(id);

async function api(path, options) {
    const response = await fetch(path, {
        headers: {'Content-Type': 'application/json'},
        ...options
    });
    if (response.status === 204) {
        return null;
    }
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        throw new ApiFailure(response.status, payload);
    }
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
            return Object.entries(fields).map(([k, v]) => `${k}: ${v}`).join('\n');
        }
        return `HTTP ${this.status}`;
    }
}

function toast(title, body, kind) {
    const node = document.createElement('div');
    node.className = `toast ${kind || ''}`;
    node.innerHTML = `<div class="t-title"></div><div class="t-body"></div>`;
    node.querySelector('.t-title').textContent = title;
    node.querySelector('.t-body').textContent = body || '';
    el('toasts').append(node);
    setTimeout(() => node.remove(), 5200);
}

function setStatus(kind, label) {
    const node = el('status');
    node.className = `status ${kind}`;
    node.querySelector('.label').textContent = label;
}

const money = (value, places) =>
    Number(value).toLocaleString('en-CA', {minimumFractionDigits: places, maximumFractionDigits: places});

const qty = (value) => Number(value).toLocaleString('en-CA');

function renderLadder(container, levels, maxQuantity) {
    container.replaceChildren();
    if (levels.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'empty';
        empty.textContent = 'no resting orders';
        container.append(empty);
        return;
    }
    for (const level of levels) {
        const row = document.createElement('div');
        row.className = 'level';
        const width = maxQuantity > 0 ? (level.quantity / maxQuantity) * 100 : 0;
        row.innerHTML = `
            <div class="bar" style="width:${width.toFixed(1)}%"></div>
            <span class="px">${money(level.price, 2)}</span>
            <span>${qty(level.quantity)}</span>
            <span>${level.orderCount}</span>`;
        container.append(row);
    }
}

function renderBook(book) {
    const levels = [...book.bids, ...book.asks];
    const max = levels.reduce((m, l) => Math.max(m, l.quantity), 0);

    renderLadder(el('asks'), book.asks, max);
    renderLadder(el('bids'), book.bids, max);

    const bestBid = book.bids[0];
    const bestAsk = book.asks[0];
    el('spread-value').textContent = bestBid && bestAsk
        ? `${money(bestAsk.price - bestBid.price, 2)}  (${money(bestBid.price, 2)} × ${money(bestAsk.price, 2)})`
        : '—';
}

function renderPositions(positions) {
    const body = el('positions').querySelector('tbody');
    body.replaceChildren();
    if (positions.length === 0) {
        body.innerHTML = `<tr><td colspan="4" class="empty">nothing held</td></tr>`;
        return;
    }
    for (const position of positions) {
        const row = document.createElement('tr');
        const pnlClass = Number(position.realisedPnl) > 0 ? 'pos'
            : Number(position.realisedPnl) < 0 ? 'neg' : '';
        row.innerHTML = `
            <td>${position.symbol}</td>
            <td class="num ${position.quantity > 0 ? 'pos' : position.quantity < 0 ? 'neg' : ''}">${qty(position.quantity)}</td>
            <td class="num">${money(position.averageCost, 4)}</td>
            <td class="num ${pnlClass}">${money(position.realisedPnl, 2)}</td>`;
        body.append(row);
    }
}

function renderWorking(orders) {
    const body = el('working').querySelector('tbody');
    body.replaceChildren();
    const resting = orders.filter(o =>
        o.symbol === state.symbol && (o.status === 'OPEN' || o.status === 'PARTIALLY_FILLED'));

    if (resting.length === 0) {
        body.innerHTML = `<tr><td colspan="4" class="empty">no working orders</td></tr>`;
        return;
    }
    for (const order of resting) {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td><span class="tag ${order.side}">${order.side}</span></td>
            <td class="num">${order.price === null ? 'MKT' : money(order.price, 2)}</td>
            <td class="num">${qty(order.remainingQuantity)}</td>
            <td class="num"><button class="cancel" type="button">Cancel</button></td>`;
        row.querySelector('button').addEventListener('click', () => cancelOrder(order.orderId));
        body.append(row);
    }
}

function renderTape(trades) {
    const body = el('tape').querySelector('tbody');
    body.replaceChildren();
    if (trades.length === 0) {
        body.innerHTML = `<tr><td colspan="3" class="empty">no trades yet</td></tr>`;
        return;
    }
    for (const trade of trades) {
        const row = document.createElement('tr');
        const time = new Date(trade.executedAt).toLocaleTimeString('en-CA', {hour12: false});
        row.innerHTML = `
            <td>${time}</td>
            <td class="num">${money(trade.price, 2)}</td>
            <td class="num">${qty(trade.quantity)}</td>`;
        body.append(row);
    }
}

async function refresh() {
    if (!state.symbol || state.refreshing) {
        return;
    }
    state.refreshing = true;
    try {
        const [book, trades, positions, orders] = await Promise.all([
            api(`/api/book/${encodeURIComponent(state.symbol)}?depth=12`),
            api(`/api/trades?symbol=${encodeURIComponent(state.symbol)}&size=15`),
            api(`/api/positions?accountId=${state.account}`),
            api(`/api/orders?accountId=${state.account}&size=100`)
        ]);

        renderBook(book);
        renderTape(trades.content);
        renderPositions(positions);
        renderWorking(orders.content);
        setStatus('live', 'live');
    } catch (failure) {
        setStatus('down', 'disconnected');
    } finally {
        state.refreshing = false;
    }
}

async function loadInstruments(preferred) {
    const instruments = await api('/api/instruments');
    const picker = el('symbol');
    picker.replaceChildren();

    for (const instrument of instruments) {
        const option = document.createElement('option');
        option.value = instrument.symbol;
        option.textContent = instrument.symbol;
        picker.append(option);
    }

    if (instruments.length === 0) {
        state.symbol = null;
        el('book-symbol').textContent = '';
        toast('No instruments yet', 'Create one to start trading.');
        return;
    }

    state.symbol = instruments.some(i => i.symbol === preferred) ? preferred : instruments[0].symbol;
    picker.value = state.symbol;
    el('book-symbol').textContent = state.symbol;
    syncSubmitLabel();
}

async function submitOrder(event) {
    event.preventDefault();
    if (!state.symbol) {
        toast('No instrument selected', 'Create one first.', 'error');
        return;
    }

    const quantity = Number(el('quantity').value);
    const payload = {
        accountId: state.account,
        symbol: state.symbol,
        side: state.side,
        type: state.type,
        quantity: Number.isFinite(quantity) ? quantity : 0
    };
    if (state.type === 'LIMIT') {
        payload.price = el('price').value.trim();
    }

    const button = el('submit');
    button.disabled = true;
    try {
        const result = await api('/api/orders', {method: 'POST', body: JSON.stringify(payload)});
        const filled = result.filledQuantity;
        if (filled > 0) {
            const notional = result.trades.reduce((sum, t) => sum + t.price * t.quantity, 0);
            toast(`Filled ${qty(filled)}`,
                `${result.trades.length} trade(s), average ${money(notional / filled, 2)}`, 'fill');
        } else if (result.status === 'CANCELLED') {
            toast('Cancelled', 'A market order cannot rest, so the remainder was killed.');
        } else {
            toast('Resting', `${qty(result.remainingQuantity)} on the book.`);
        }
        await refresh();
    } catch (failure) {
        toast('Rejected', failure.message + '\n' + failure.detail(), 'error');
    } finally {
        button.disabled = false;
    }
}

async function cancelOrder(orderId) {
    try {
        await api(`/api/orders/${orderId}`, {method: 'DELETE'});
        toast('Cancelled', 'Order removed from the book.');
        await refresh();
    } catch (failure) {
        toast('Could not cancel', failure.message, 'error');
    }
}

function syncSubmitLabel() {
    const button = el('submit');
    button.textContent = `${state.side === 'BUY' ? 'Buy' : 'Sell'} ${state.symbol || ''}`.trim();
    button.className = `primary ${state.side === 'BUY' ? 'buy' : 'sell'}`;
}

function wireSegmented(groupId, attribute, onChange) {
    el(groupId).addEventListener('click', (event) => {
        const button = event.target.closest('.seg');
        if (!button) {
            return;
        }
        for (const seg of el(groupId).querySelectorAll('.seg')) {
            seg.classList.toggle('active', seg === button);
        }
        onChange(button.dataset[attribute]);
    });
}

function init() {
    const accountPicker = el('account');
    for (const account of ACCOUNTS) {
        const option = document.createElement('option');
        option.value = account.id;
        option.textContent = account.label;
        accountPicker.append(option);
    }

    accountPicker.addEventListener('change', (event) => {
        state.account = event.target.value;
        refresh();
    });

    el('symbol').addEventListener('change', (event) => {
        state.symbol = event.target.value;
        el('book-symbol').textContent = state.symbol;
        syncSubmitLabel();
        refresh();
    });

    wireSegmented('side-group', 'side', (side) => {
        state.side = side;
        syncSubmitLabel();
    });

    wireSegmented('type-group', 'type', (type) => {
        state.type = type;
        el('price-field').hidden = type === 'MARKET';
        el('market-hint').hidden = type !== 'MARKET';
    });

    el('order-form').addEventListener('submit', submitOrder);

    el('new-symbol').addEventListener('click', () => el('symbol-dialog').showModal());

    el('symbol-form').addEventListener('submit', async (event) => {
        const action = event.submitter && event.submitter.value;
        const symbol = el('symbol-input').value.trim().toUpperCase();
        el('symbol-input').value = '';
        if (action !== 'create' || !symbol) {
            return;
        }
        try {
            await api('/api/instruments', {method: 'POST', body: JSON.stringify({symbol})});
            toast('Instrument created', symbol);
            await loadInstruments(symbol);
            await refresh();
        } catch (failure) {
            toast('Could not create', failure.message + '\n' + failure.detail(), 'error');
        }
    });

    loadInstruments('AAPL').then(refresh).catch(() => setStatus('down', 'disconnected'));
    setInterval(refresh, 1000);
}

init();
