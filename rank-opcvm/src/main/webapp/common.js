/* Shared by index.html and fund.html: where the data is, how to load and format it. */
'use strict';

/** The bucket the import publishes to (rank.bucket in appengine-web.xml); ?data=<url> overrides it for tests. */
const DATA_URL = new URLSearchParams(location.search).get('data') || 'https://storage.googleapis.com/rank-opcvm-data/';
const PERIOD_LABELS = ['1er janv.', '1 mois', '6 mois', '1 an', '3 ans', '5 ans', '10 ans'];
const SOURCE_LABELS = { morningstar: 'Données Morningstar', boursorama: 'Données Boursorama' };

async function loadJson(path) {
  const response = await fetch(DATA_URL + path);
  if (!response.ok) {
    throw new Error(path + ' : HTTP ' + response.status);
  }
  return response.json();
}

function loadIndex() {
  return loadJson('index.json');
}

/** The index entry's publication time versions the URL: a rerun of the same day must not be read from the cache. */
function loadImport(date, published) {
  return loadJson('imports/' + date + '.json' + (published ? '?v=' + encodeURIComponent(published) : ''));
}

function publishedOf(index, date) {
  const entry = index.imports.find(i => i.date === date);
  return entry && entry.published;
}

/** Must stay identical to ImportJson.shardOf. */
function shardOf(isin) {
  let sum = 0;
  for (const c of isin) {
    sum += c.charCodeAt(0);
  }
  return sum % 64;
}

const numberFormat = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });

function fmt(value, suffix) {
  return value === null || value === undefined ? '–' : numberFormat.format(value) + (suffix || '');
}

function fmtDate(iso) {
  const [y, m, d] = iso.split('-');
  return d + '/' + m + '/' + y;
}

/** A performance cell, green or red. */
function perfCell(value) {
  const cls = value === null || value === undefined ? '' : value >= 0 ? 'up' : 'down';
  return '<td class="num ' + cls + '">' + fmt(value, ' %') + '</td>';
}

function stars(count) {
  return count ? '<span class="stars" title="' + count + ' étoiles Morningstar">' + '★'.repeat(count) + '</span>' : '';
}

/** The fund's rank in its category, coloured when in the top 5 % or 20 %. */
function rankBadge(rank, total) {
  const share = rank / total;
  const cls = share <= 0.05 ? 'top5' : share <= 0.2 ? 'top20' : '';
  return '<span class="rank ' + cls + '" title="' + Math.round(share * 100) + ' % des fonds de la catégorie font mieux ou aussi bien">'
         + rank + ' / ' + total + '</span>';
}

function isEtf(fund) {
  return fund.kind === 'etf';
}

/** "35 / 1149": the rank a line would have among the other kind of its category. */
function vsLabel(fund) {
  return fund.vs ? fund.vs[0] + ' / ' + fund.vs[1] : '';
}

function typeBadge(type) {
  if (type === 'partenaire') {
    return '<span class="badge partenaire">Partenaire</span>';
  }
  return type === 'boursomarkets' ? '<span class="badge boursomarkets">Boursomarkets</span>' : '';
}

function escapeHtml(text) {
  return String(text === null || text === undefined ? '' : text)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Keeps the ?data= override on internal links. */
function withData(url) {
  const data = new URLSearchParams(location.search).get('data');
  return data ? url + (url.includes('?') ? '&' : '?') + 'data=' + encodeURIComponent(data) : url;
}
