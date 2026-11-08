'use strict';

const EXAMPLES = [
    'Top 5 customers by total spend in 2025',
    'Revenue by category per month for 2025',
    "Show each employee with their manager's name",
    'Customers who have never ordered',
    'Increase stock of product EL-1004 by 25',
    'Add a new customer Jane Doe from Dallas, TX',
    'Delete cancelled orders',
];

const HISTORY_KEY = 'query-history';
const MAX_HISTORY = 30;

const el = (id) => document.getElementById(id);
const meta = (name) => document.querySelector(`meta[name="${name}"]`).content;

let currentQuestion = '';
let historyId = null;

class ApiFailure extends Error {
    constructor(error) {
        super(error.message);
        this.code = error.code;
        this.ruleId = error.ruleId;
    }
}

async function api(path, body) {
    const options = body === undefined ? {} : {
        method: 'POST',
        headers: {'Content-Type': 'application/json', [meta('csrf-header')]: meta('csrf-token')},
        body: JSON.stringify(body),
    };
    let response;
    try {
        response = await fetch(path, options);
    } catch {
        throw new ApiFailure({code: 'NETWORK', message: 'Could not reach the application.'});
    }
    if (response.status === 401) {
        window.location.reload();
        throw new ApiFailure({code: 'SIGNED_OUT', message: 'Your session has ended.'});
    }
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        throw new ApiFailure(payload ?? {code: 'ERROR', message: `The request failed (${response.status}).`});
    }
    return payload;
}

function make(tag, className, text) {
    const node = document.createElement(tag);
    if (className) {
        node.className = className;
    }
    if (text !== undefined) {
        node.textContent = text;
    }
    return node;
}

function showResult(...nodes) {
    el('result').replaceChildren(...nodes);
    el('result-card').hidden = nodes.length === 0;
    if (nodes.length > 0) {
        el('result-card').scrollIntoView({behavior: 'smooth', block: 'nearest'});
    }
}

function notice(kind, message, detail) {
    const box = make('div', `notice ${kind}`);
    box.append(make('span', '', message));
    if (detail) {
        box.append(make('span', 'rule', detail));
    }
    return box;
}

function errorNotice(failure) {
    const detail = failure.ruleId ? `Rule: ${failure.ruleId}` : failure.code;
    return notice('error', failure.message, detail);
}

function rowsTable(result) {
    const header = make('tr');
    result.columns.forEach((name) => header.append(make('th', '', name)));
    const thead = make('thead');
    thead.append(header);

    const tbody = make('tbody');
    result.rows.forEach((row) => {
        const tr = make('tr');
        row.forEach((value) => {
            const isNull = value === null;
            const text = isNull ? 'NULL' : typeof value === 'object' ? JSON.stringify(value) : String(value);
            tr.append(make('td', isNull ? 'null' : '', text));
        });
        tbody.append(tr);
    });

    const table = make('table');
    table.append(thead, tbody);
    const scroll = make('div', 'table-scroll');
    scroll.append(table);
    return scroll;
}

function rowCountText(count) {
    return `${count} ${count === 1 ? 'row' : 'rows'}`;
}

function selectNodes(result) {
    const nodes = [make('p', 'meta', `${rowCountText(result.rowCount)} · ${result.elapsedMs} ms`)];
    if (result.truncated) {
        nodes.push(notice('warning', `Results were cut off at ${rowCountText(result.rowCount)}. More rows exist.`));
    }
    if (result.columns.length > 0) {
        nodes.push(rowsTable(result));
    }
    return nodes;
}

function setBusy(button, busy, label) {
    button.disabled = busy;
    if (label) {
        button.dataset.label ??= button.textContent;
        button.textContent = busy ? label : button.dataset.label;
    }
}

function setSqlControls() {
    el('run').disabled = el('sql').value.trim() === '';
}

async function generate(question) {
    const button = el('generate');
    setBusy(button, true, 'Generating…');
    el('regenerate').disabled = true;
    currentQuestion = question;
    historyId = null;
    showResult();
    try {
        const generated = await api('/api/sql/generate', {question});
        showGenerated(generated);
        const unsupported = generated.kind === 'UNSUPPORTED';
        if (unsupported) {
            showResult(notice('error', generated.explanation || 'That request is not supported.', 'Not supported'));
        }
        historyId = addHistory(question, generated.sql, unsupported ? 'Not supported' : 'Generated');
    } catch (failure) {
        showResult(errorNotice(failure));
    } finally {
        setBusy(button, false, 'Generating…');
        el('regenerate').disabled = false;
    }
}

function showGenerated({sql, kind, explanation, tablesUsed}) {
    el('sql').value = sql;
    el('kind').textContent = kind;
    el('explanation').textContent = explanation ?? '';
    el('tables-used').textContent = tablesUsed.length ? `Tables: ${tablesUsed.join(', ')}` : '';
    el('sql-card').hidden = false;
    setSqlControls();
}

async function run() {
    const sql = el('sql').value;
    const button = el('run');
    setBusy(button, true, 'Running…');
    showResult();
    try {
        const response = await api('/api/sql/execute', {sql});
        if (response.kind === 'SELECT') {
            showResult(...selectNodes(response.select));
            updateHistory(sql, rowCountText(response.select.rowCount));
        } else {
            showPreview(sql, response);
            updateHistory(sql, 'Previewed');
        }
    } catch (failure) {
        showResult(errorNotice(failure));
        updateHistory(sql, failure.ruleId ? `Rejected: ${failure.ruleId}` : 'Failed');
    } finally {
        setBusy(button, false, 'Running…');
        setSqlControls();
    }
}

function showPreview(sql, {write, previewToken}) {
    const count = write.affectedRows;
    const box = notice('warning', `This will affect ${rowCountText(count)}.`, 'Nothing has changed yet.');
    const confirm = make('button', 'primary', 'Confirm');
    const cancel = make('button', '', 'Cancel');
    confirm.type = cancel.type = 'button';
    const actions = make('div', 'actions');
    actions.append(confirm, cancel);
    confirm.addEventListener('click', () => commit(sql, previewToken, confirm, cancel));
    cancel.addEventListener('click', () => {
        showResult(notice('warning', 'Cancelled. Nothing was changed.'));
        updateHistory(sql, 'Cancelled');
    });
    showResult(box, actions);
}

async function commit(sql, previewToken, confirm, cancel) {
    setBusy(confirm, true, 'Saving…');
    cancel.disabled = true;
    try {
        const result = await api('/api/sql/confirm', {sql, previewToken});
        const nodes = [notice('success', `Done. ${rowCountText(result.affectedRows)} changed.`)];
        if (result.returning) {
            nodes.push(...selectNodes(result.returning));
        }
        showResult(...nodes);
        updateHistory(sql, `${rowCountText(result.affectedRows)} changed`);
        loadTables();
    } catch (failure) {
        showResult(errorNotice(failure));
        updateHistory(sql, 'Failed');
    }
}

function readHistory() {
    try {
        return JSON.parse(sessionStorage.getItem(HISTORY_KEY)) ?? [];
    } catch {
        return [];
    }
}

function writeHistory(entries) {
    sessionStorage.setItem(HISTORY_KEY, JSON.stringify(entries.slice(0, MAX_HISTORY)));
    renderHistory();
}

function addHistory(question, sql, outcome) {
    const id = crypto.randomUUID();
    writeHistory([{id, question, sql, outcome}, ...readHistory()]);
    return id;
}

/** Keeps only summaries, never result rows. Hand-written SQL gets its own entry. */
function updateHistory(sql, outcome) {
    const entries = readHistory();
    const entry = entries.find((candidate) => candidate.id === historyId);
    if (entry) {
        entry.sql = sql;
        entry.outcome = outcome;
        writeHistory(entries);
    } else {
        historyId = addHistory(currentQuestion || 'Edited SQL', sql, outcome);
    }
}

function renderHistory() {
    const entries = readHistory();
    el('history-empty').hidden = entries.length > 0;
    el('history').replaceChildren(...entries.map((entry) => {
        const button = make('button', 'history-entry', entry.question);
        button.type = 'button';
        button.append(make('small', '', entry.outcome));
        button.addEventListener('click', () => reloadEntry(entry));
        const item = make('li');
        item.append(button);
        return item;
    }));
}

function reloadEntry(entry) {
    currentQuestion = entry.question;
    historyId = entry.id;
    el('question').value = entry.question;
    showGenerated({sql: entry.sql, kind: 'SQL', explanation: `Earlier outcome: ${entry.outcome}`, tablesUsed: []});
    showResult();
}

async function loadTables() {
    const status = el('tables-status');
    try {
        const tables = await api('/api/schema/tables');
        status.hidden = true;
        el('tables').replaceChildren(...tables.map((table) => {
            const item = make('li', '', table.table);
            item.title = table.comment ?? '';
            return item;
        }));
    } catch (failure) {
        status.hidden = false;
        status.textContent = failure.message;
    }
}

function submitQuestion() {
    const question = el('question').value.trim();
    if (question) {
        generate(question);
    }
}

el('generate').addEventListener('click', submitQuestion);
el('regenerate').addEventListener('click', submitQuestion);
el('run').addEventListener('click', run);
el('sql').addEventListener('input', () => {
    // A preview only covers the exact SQL it was made for
    showResult();
    el('kind').textContent = 'EDITED';
    setSqlControls();
});

EXAMPLES.forEach((question) => {
    const button = make('button', '', question);
    button.type = 'button';
    button.addEventListener('click', () => {
        el('question').value = question;
        generate(question);
    });
    el('examples').append(button);
});

renderHistory();
loadTables();
