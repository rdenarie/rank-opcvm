/* The ranking page: one import, filtered by personal category, search and criteria, sortable, paginated. */
'use strict';

const PAGE = 100;
const ALL = 'Tout';
const PARTNERS = 'Partenaires';
const CATEGORIES = 'Catégories';
const params = new URLSearchParams(location.search);
const state = {
  index: null,
  data: null,
  categories: new Map(),
  tab: params.get('tab') || ALL,
  category: params.get('cat') || null,
  search: params.get('q') || '',
  top: params.get('top') !== '0',
  stars: Number(params.get('stars') || 0),
  fees: params.get('fees') || '',
  sort: { key: 'score', asc: false },
  shown: PAGE,
};

const $ = id => document.getElementById(id);

/** Sortable columns of the fund table: header, value to sort on, cell. */
const FUND_COLUMNS = [
  { key: 'rank', label: 'Rang', num: true, value: f => f.rank / f.n, cell: f => '<td class="num">' + rankBadge(f.rank, f.n) + '</td>' },
  { key: 'name', label: 'Fonds', value: f => f.name, cell: f =>
      '<td class="name"><a href="' + withData('fund.html?isin=' + f.isin + '&date=' + state.data.date) + '">' + escapeHtml(f.name)
      + '</a> ' + typeBadge(f.type) + '<small>' + f.isin + ' · ' + escapeHtml(f.currency || '') + '</small></td>' },
  { key: 'cat', label: 'Catégorie', value: f => categoryOf(f).name, cell: f => {
      const c = categoryOf(f);
      return '<td class="cat"><button class="linkish" data-cat="' + escapeHtml(c.id) + '">' + escapeHtml(c.name) + '</button>'
             + '<small>' + escapeHtml(c.perso || '') + ' · ' + (c.rank ? c.rank + 'e / ' + state.data.categories.length : '') + '</small></td>';
    } },
  { key: 'score', label: 'Score', num: true, value: f => f.score, cell: f =>
      '<td class="num"><div class="score">' + fmt(f.score) + '<div class="track"><div class="fill" style="width:'
      + Math.max(0, Math.min(100, f.score / state.maxScore * 100)) + '%"></div></div></div></td>' },
  { key: 'p1', label: '1 an', num: true, value: f => f.perf[3], cell: f => perfCell(f.perf[3]) },
  { key: 'p3', label: '3 ans', num: true, value: f => f.perf[4], cell: f => perfCell(f.perf[4]) },
  { key: 'p5', label: '5 ans', num: true, value: f => f.perf[5], cell: f => perfCell(f.perf[5]) },
  { key: 'stars', label: 'Étoiles', value: f => f.stars || 0, cell: f => '<td>' + stars(f.stars) + '</td>' },
  { key: 'fees', label: 'Frais cour.', num: true, value: f => f.courant, cell: f => '<td class="num">' + fmt(f.courant, ' %') + '</td>' },
  { key: 'entry', label: 'Frais entrée', num: true, value: f => f.entree, cell: f => '<td class="num">' + fmt(f.entree, ' %') + '</td>' },
  { key: 'ticket', label: 'Ticket', value: f => f.ticket, cell: f => '<td>' + escapeHtml(f.ticket || 'ND') + '</td>' },
];

const CATEGORY_COLUMNS = [
  { key: 'rank', label: 'Rang', num: true, value: c => c.rank, cell: c => '<td class="num">' + (c.rank || '–') + '</td>' },
  { key: 'name', label: 'Catégorie', value: c => c.name, cell: c =>
      '<td class="cat"><button class="linkish" data-cat="' + escapeHtml(c.id) + '">' + escapeHtml(c.name) + '</button>'
      + '<small>' + escapeHtml(c.perso || '') + '</small></td>' },
  { key: 'score', label: 'Score', num: true, value: c => c.score, cell: c => '<td class="num">' + fmt(c.score) + '</td>' },
  { key: 'p0', label: '1er janv.', num: true, value: c => c.perf[0], cell: c => perfCell(c.perf[0]) },
  { key: 'p1', label: '1 an', num: true, value: c => c.perf[3], cell: c => perfCell(c.perf[3]) },
  { key: 'p3', label: '3 ans', num: true, value: c => c.perf[4], cell: c => perfCell(c.perf[4]) },
  { key: 'p5', label: '5 ans', num: true, value: c => c.perf[5], cell: c => perfCell(c.perf[5]) },
  { key: 'funds', label: 'Fonds', num: true, value: c => c.funds, cell: c => '<td class="num">' + c.funds + '</td>' },
];

function categoryOf(fund) {
  return state.categories.get(fund.cat) || { id: fund.cat, name: fund.cat, perso: '' };
}

function isTop(fund) {
  return (fund.n <= 500 && fund.rank <= 100) || fund.rank <= fund.n * 0.2;
}

function filteredFunds() {
  const search = state.search.trim().toLowerCase();
  const maxFees = state.fees === '' ? null : Number(state.fees);
  return state.data.funds.filter(f => {
    if (state.tab === PARTNERS ? !f.type : state.tab !== ALL && categoryOf(f).perso !== state.tab) return false;
    if (state.category && f.cat !== state.category) return false;
    //as on the former site, the partner tab lists every fund BoursoBank sells, not only the top ones
    if (state.top && state.tab !== PARTNERS && !isTop(f)) return false;
    if (state.stars && (f.stars || 0) < state.stars) return false;
    if (maxFees !== null && (f.courant === undefined || f.courant > maxFees)) return false;
    return !search || f.name.toLowerCase().includes(search) || f.isin.toLowerCase().includes(search);
  });
}

function sortRows(rows, columns) {
  const column = columns.find(c => c.key === state.sort.key) || columns[0];
  const dir = state.sort.asc ? 1 : -1;
  return rows.slice().sort((a, b) => {
    const va = column.value(a);
    const vb = column.value(b);
    if (va === vb) return 0;
    if (va === null || va === undefined) return 1;
    if (vb === null || vb === undefined) return -1;
    return (va < vb ? -1 : 1) * dir;
  });
}

function renderTable(rows, columns, total) {
  const head = columns.map(c => '<th data-sort="' + c.key + '" class="' + (c.num ? 'num ' : '')
    + (state.sort.key === c.key ? 'sorted' + (state.sort.asc ? ' asc' : '') : '') + '">' + c.label + '</th>').join('');
  const body = rows.slice(0, state.shown).map(r => '<tr>' + columns.map(c => c.cell(r)).join('') + '</tr>').join('');
  $('table').innerHTML = rows.length
    ? '<table><thead><tr>' + head + '</tr></thead><tbody>' + body + '</tbody></table>'
    : '<div class="empty">Aucun fonds ne correspond à ces critères.</div>';
  $('more').hidden = rows.length <= state.shown;
  $('more').textContent = 'Afficher plus (' + (rows.length - state.shown) + ' restants)';
  $('summary').textContent = total;
}

function render() {
  renderTabs();
  const chip = state.category && state.categories.get(state.category);
  $('categoryChip').innerHTML = chip
    ? '<span class="chip">' + escapeHtml(chip.name) + ' <button id="clearCat" title="Toutes les catégories">×</button></span>' : '';
  $('filters').querySelectorAll('label').forEach(l => { l.hidden = state.tab === CATEGORIES; });
  if (state.tab === CATEGORIES) {
    const rows = sortRows(state.data.categories, CATEGORY_COLUMNS);
    renderTable(rows, CATEGORY_COLUMNS, rows.length + ' catégories');
  } else {
    const rows = sortRows(filteredFunds(), FUND_COLUMNS);
    state.maxScore = Math.max(1, ...rows.slice(0, 2000).map(f => f.score));
    renderTable(rows, FUND_COLUMNS, numberFormat.format(rows.length) + ' fonds sur ' + numberFormat.format(state.data.funds.length));
  }
  saveUrl();
}

function renderTabs() {
  const persos = [...new Set(state.data.categories.map(c => c.perso).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'fr'));
  $('tabs').innerHTML = [ALL, ...persos, PARTNERS, CATEGORIES]
    .map(t => '<button data-tab="' + escapeHtml(t) + '" class="' + (t === state.tab ? 'active' : '') + '">' + escapeHtml(t) + '</button>')
    .join('');
}

function saveUrl() {
  const p = new URLSearchParams();
  if (params.get('data')) p.set('data', params.get('data'));
  if (state.data && state.index && state.data.date !== state.index.imports[0].date) p.set('date', state.data.date);
  if (state.tab !== ALL) p.set('tab', state.tab);
  if (state.category) p.set('cat', state.category);
  if (state.search) p.set('q', state.search);
  if (!state.top) p.set('top', '0');
  if (state.stars) p.set('stars', state.stars);
  if (state.fees !== '') p.set('fees', state.fees);
  history.replaceState(null, '', location.pathname + (p.toString() ? '?' + p : ''));
}

async function showImport(date) {
  $('table').innerHTML = '<div class="loading">Chargement de l\'import du ' + fmtDate(date) + '…</div>';
  state.data = await loadImport(date);
  //the first imports stored no category rank: rank them by score as the import does
  if (state.data.categories.some(c => !c.rank)) {
    state.data.categories.forEach(c => { c.rank = 1 + state.data.categories.filter(o => o.score > c.score).length; });
  }
  state.categories = new Map(state.data.categories.map(c => [c.id, c]));
  if (state.category && !state.categories.has(state.category)) state.category = null;
  state.shown = PAGE;
  $('source').textContent = (SOURCE_LABELS[state.data.source] || '') + ' · ' + numberFormat.format(state.data.funds.length) + ' fonds classés';
  render();
}

function bindEvents() {
  $('date').addEventListener('change', e => showImport(e.target.value));
  $('search').addEventListener('input', e => { state.search = e.target.value; state.shown = PAGE; render(); });
  $('top').addEventListener('change', e => { state.top = e.target.checked; state.shown = PAGE; render(); });
  $('stars').addEventListener('change', e => { state.stars = Number(e.target.value); state.shown = PAGE; render(); });
  $('fees').addEventListener('input', e => { state.fees = e.target.value; state.shown = PAGE; render(); });
  $('more').addEventListener('click', () => { state.shown += PAGE; render(); });
  $('tabs').addEventListener('click', e => {
    const tab = e.target.dataset.tab;
    if (!tab) return;
    state.tab = tab;
    state.category = null;
    state.sort = tab === CATEGORIES ? { key: 'rank', asc: true } : { key: 'score', asc: false };
    state.shown = PAGE;
    render();
  });
  document.addEventListener('click', e => {
    if (e.target.id === 'clearCat') { state.category = null; render(); return; }
    const cat = e.target.dataset && e.target.dataset.cat;
    if (cat) {
      state.category = cat;
      if (state.tab === CATEGORIES || state.tab === PARTNERS) state.tab = ALL;
      state.sort = { key: 'score', asc: false };
      state.shown = PAGE;
      render();
      window.scrollTo(0, 0);
      return;
    }
    const sort = e.target.dataset && e.target.dataset.sort;
    if (sort) {
      state.sort = state.sort.key === sort ? { key: sort, asc: !state.sort.asc } : { key: sort, asc: sort === 'name' || sort === 'cat' };
      render();
    }
  });
}

async function init() {
  $('search').value = state.search;
  $('top').checked = state.top;
  $('stars').value = String(state.stars);
  $('fees').value = state.fees;
  bindEvents();
  try {
    state.index = await loadIndex();
    $('date').innerHTML = state.index.imports
      .map(i => '<option value="' + i.date + '">' + fmtDate(i.date) + ' · ' + numberFormat.format(i.funds) + ' fonds</option>').join('');
    const wanted = params.get('date');
    const date = state.index.imports.some(i => i.date === wanted) ? wanted : state.index.imports[0].date;
    $('date').value = date;
    await showImport(date);
  } catch (error) {
    $('table').innerHTML = '<div class="empty">Données indisponibles : ' + escapeHtml(error.message) + '</div>';
  }
}

init();
