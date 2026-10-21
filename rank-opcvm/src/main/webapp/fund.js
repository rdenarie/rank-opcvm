/* The fund page: its figures in one import, how its score is computed, and its rank across imports. */
'use strict';

const params = new URLSearchParams(location.search);
const $ = id => document.getElementById(id);

/** Mirrors Scoring.fillGaps and Scoring.score: returns the values used and the steps, for the explanation. */
function explainScore(perf, categoryPerf) {
  const firstMissing = perf.findIndex(v => v === null || v === undefined);
  let lastKnown = -1;
  perf.forEach((v, i) => { if (v !== null && v !== undefined) lastKnown = i; });
  const gaps = firstMissing !== -1 && firstMissing < lastKnown;
  const used = perf.map((v, i) => (gaps && i < lastKnown && (v === null || v === undefined)) ? categoryPerf[i] : v);
  const filled = used.map((v, i) => v !== perf[i]);
  const known = used.filter(v => v !== null && v !== undefined);
  const mean = known.reduce((a, b) => a + b, 0) / known.length;
  let bonus;
  let bonusLabel;
  if (known.length > 4) {
    const longIndex = [4, 5, 6].find(i => used[i] !== null && used[i] !== undefined);
    bonus = used[longIndex];
    bonusLabel = 'la performance à ' + PERIOD_LABELS[longIndex];
  } else {
    bonus = known[known.length - 1];
    bonusLabel = 'la performance la plus longue connue';
  }
  return { used, filled, count: known.length, mean, bonus, bonusLabel };
}

function rankChart(points) {
  if (points.length < 2) {
    return '<div class="explain">Un seul import pour ce fonds : pas encore d\'historique.</div>';
  }
  const width = 800;
  const height = 240;
  const pad = { left: 44, right: 12, top: 12, bottom: 28 };
  const x = i => pad.left + i * (width - pad.left - pad.right) / (points.length - 1);
  //the share of funds of the category ranked better, 0 % at the top
  const y = share => pad.top + share * (height - pad.top - pad.bottom);
  const shares = points.map(p => (p[1] - 1) / Math.max(1, p[2] - 1));
  const path = shares.map((s, i) => (i ? 'L' : 'M') + x(i).toFixed(1) + ',' + y(s).toFixed(1)).join(' ');
  const ticks = [0, 0.2, 0.5, 1].map(t => '<line class="axis" x1="' + pad.left + '" x2="' + (width - pad.right) + '" y1="' + y(t)
    + '" y2="' + y(t) + '"/><text class="label" x="' + (pad.left - 6) + '" y="' + (y(t) + 4) + '" text-anchor="end">' + (t * 100) + ' %</text>').join('');
  const step = Math.max(1, Math.ceil(points.length / 8));
  const labels = points.map((p, i) => i % step && i !== points.length - 1 ? ''
    : '<text class="label" x="' + x(i) + '" y="' + (height - 8) + '" text-anchor="' + (i === points.length - 1 ? 'end' : 'middle') + '">'
      + p[0].slice(0, 7) + '</text>').join('');
  const dots = points.map((p, i) => '<circle class="dot" r="3" cx="' + x(i) + '" cy="' + y(shares[i]) + '"><title>' + fmtDate(p[0])
    + ' : ' + p[1] + ' / ' + p[2] + ', score ' + fmt(p[3]) + '</title></circle>').join('');
  return '<svg viewBox="0 0 ' + width + ' ' + height + '" preserveAspectRatio="none" role="img" aria-label="Rang dans la catégorie au fil des imports">'
    + '<rect class="band" x="' + pad.left + '" y="' + y(0) + '" width="' + (width - pad.left - pad.right) + '" height="' + (y(0.2) - y(0)) + '"/>'
    + ticks + labels + '<path class="line" d="' + path + '"/>' + dots + '</svg>';
}

function kpi(label, value, sub) {
  return '<div class="kpi"><div class="label">' + label + '</div><div class="value">' + value + '</div>'
    + (sub ? '<div class="sub">' + sub + '</div>' : '') + '</div>';
}

function render(fund, category, data, points) {
  const explain = explainScore(fund.perf, category.perf || []);
  const rows = PERIOD_LABELS.map((label, i) => '<tr><td>' + label + '</td>' + perfCell(fund.perf[i])
    + perfCell((category.perf || [])[i])
    + (explain.filled[i] ? '<td class="num filled">' + fmt(explain.used[i], ' %') + ' (moyenne catégorie)</td>' : '<td class="num">' + fmt(explain.used[i], ' %') + '</td>')
    + '</tr>').join('');
  const history = points.slice().reverse().map(p => '<tr><td><a href="' + withData('fund.html?isin=' + fund.isin + '&date=' + p[0]) + '">'
    + fmtDate(p[0]) + '</a></td><td class="num">' + rankBadge(p[1], p[2]) + '</td><td class="num">' + fmt(p[3]) + '</td></tr>').join('');
  document.title = fund.name + ' · Classement OPCVM';
  $('fund').innerHTML =
    '<div class="fund-head"><h2>' + escapeHtml(fund.name) + ' ' + (isEtf(fund) ? '<span class="badge etf">ETF</span> ' : '')
    + typeBadge(fund.type) + '</h2><div class="meta">'
    + '<span>' + fund.isin + '</span><span>' + escapeHtml(category.name) + ' (' + escapeHtml(category.perso || '') + ')</span>'
    + (fund.gerant ? '<span>Gérant : ' + escapeHtml(fund.gerant) + '</span>' : '')
    + '<span>Import du ' + fmtDate(data.date) + '</span>'
    + '<a href="https://www.boursorama.com/recherche/?query=' + fund.isin + '" target="_blank" rel="noopener">Voir sur Boursorama</a></div></div>'
    + '<div class="kpis">'
    + kpi(isEtf(fund) ? 'Rang parmi les ETF de la catégorie' : 'Rang dans la catégorie', rankBadge(fund.rank, fund.n),
          escapeHtml(category.name) + (fund.vs ? '<br>Il serait ' + fund.vs[0] + (fund.vs[0] === 1 ? 'er' : 'e') + ' sur ' + fund.vs[1]
          + (isEtf(fund) ? ' parmi les fonds' : ' parmi les ETF') : ''))
    + kpi('Score', fmt(fund.score), 'catégorie : ' + fmt(category.score) + (category.rank ? ', ' + category.rank + 'e sur ' + data.categories.length : ''))
    + kpi('Étoiles Morningstar', stars(fund.stars) || '–')
    + kpi('Frais courants', fmt(fund.courant, ' %'), 'entrée ' + fmt(fund.entree, ' %') + ' · sortie ' + fmt(fund.sortie, ' %'))
    + kpi('Ticket d\'entrée', escapeHtml(fund.ticket || 'ND'))
    + kpi('Cours', fmt(fund.priceEur, ' €'), escapeHtml(fund.currency || ''))
    + '</div>'
    + '<section><h3>Rang dans la catégorie au fil des imports</h3><div class="card chart">' + rankChart(points)
    + '</div><p class="legend">Part des fonds de la catégorie mieux classés : en haut, le fonds est en tête ; la bande verte est le top 20 %.</p></section>'
    + '<section><h3>Calcul du score</h3><div class="card"><table><thead><tr><th class="nosort">Période</th><th class="num nosort">' + (isEtf(fund) ? 'ETF' : 'Fonds') + '</th>'
    + '<th class="num nosort">Moyenne catégorie</th><th class="num nosort">Valeur retenue</th></tr></thead><tbody>' + rows + '</tbody></table>'
    + '<div class="explain">Moyenne des ' + explain.count + ' périodes retenues : ' + fmt(explain.mean) + ' ; plus ' + explain.bonusLabel
    + ' : ' + fmt(explain.bonus) + ' ; score = ' + fmt(explain.mean + explain.bonus) + '.'
    + (explain.filled.some(Boolean) ? ' Une période manquante entre deux périodes connues est remplacée par la moyenne de la catégorie.' : '')
    + '</div></div></section>'
    + '<section><h3>Historique</h3><div class="card"><table><thead><tr><th class="nosort">Import</th><th class="num nosort">Rang</th>'
    + '<th class="num nosort">Score</th></tr></thead><tbody>' + history + '</tbody></table></div></section>';
}

async function init() {
  $('home').href = withData('index.html');
  const isin = params.get('isin');
  if (!isin) {
    $('fund').innerHTML = '<div class="empty">Aucun fonds demandé.</div>';
    return;
  }
  try {
    const index = await loadIndex();
    const date = index.imports.some(i => i.date === params.get('date')) ? params.get('date') : index.imports[0].date;
    const [data, shard] = await Promise.all([loadImport(date, publishedOf(index, date)), loadJson('history/' + shardOf(isin) + '.json').catch(() => ({}))]);
    const fund = data.funds.find(f => f.isin === isin);
    if (!fund) {
      $('fund').innerHTML = '<div class="empty">Le fonds ' + escapeHtml(isin) + ' n\'est pas classé dans l\'import du ' + fmtDate(date) + '.</div>';
      return;
    }
    const category = data.categories.find(c => c.id === fund.cat) || { name: fund.cat, perf: [] };
    $('source').textContent = SOURCE_LABELS[data.source] || '';
    render(fund, category, data, shard[isin] || []);
  } catch (error) {
    $('fund').innerHTML = '<div class="empty">Données indisponibles : ' + escapeHtml(error.message) + '</div>';
  }
}

init();
