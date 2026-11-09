/* ===========================================================================
 * features2.js — Dawah: page-wise Mushaf · Juz index · recitation checker · hifz tracker
 * Loaded after features.js (uses its SKX helpers). All data is on the device.
 * ======================================================================== */
(function () {
  'use strict';
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => [...(r || document).querySelectorAll(s)];
  const esc = (s) => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const NAT = () => window.AndroidNative || null;
  const N2 = () => window.AndroidNative2 || null;
  const S = () => STATE.settings;
  const save = () => scheduleSave();
  const vib = (p) => { try { navigator.vibrate && navigator.vibrate(p); } catch (e) {} };
  const jc = {};
  const jget = (u) => jc[u] || (jc[u] = fetch(u).then((r) => { if (!r.ok) throw new Error('missing ' + u); return r.json(); }).catch((e) => { delete jc[u]; throw e; }));
  const sName = (n) => SURAHS[n - 1][2], sAr = (n) => SURAHS[n - 1][1], sCount = (n) => SURAHS[n - 1][4];
  const gid = (s, a) => AYAH_BASE[s - 1] + a;
  const refOfG = (g) => { let s = 114; while (AYAH_BASE[s - 1] >= g) s--; return [s, g - AYAH_BASE[s - 1]]; };
  const lang = () => (S().translationLang === 'ur' ? 'ur' : 'en');
  const indic = (n) => String(n).replace(/\d/g, (d) => '٠١٢٣٤٥٦٧٨٩'[d]);
  const ayahOf = async (s, a) => { const t = await fetchLocalSurahText(s); return t && t.ayahs[a - 1]; };
  const dayNum = () => { const d = new Date(); return Math.floor((d.getTime() - d.getTimezoneOffset() * 60000) / 864e5); };
  function makeTab(id, title) {
    let el = document.getElementById('tab-' + id);
    if (!el) { el = document.createElement('section'); el.className = 'section'; el.id = 'tab-' + id; $('.content').appendChild(el); }
    TAB_TITLES[id] = title; return el;
  }
  const back = (to) => `<button class="btn sk-back" onclick="SKX.back('${to || 'home'}')">‹ Back</button>`;
  function actionSheet(title, items) {
    const bd = document.createElement('div'); bd.className = 'sheet-backdrop open'; bd.style.zIndex = 96;
    bd.innerHTML = `<div class="sheet"><div class="sheet-handle"></div><div class="sk-h">${title}</div><div class="sheet-grid ac-grid"></div></div>`;
    items.forEach((it) => { const b = document.createElement('button'); b.className = 'tile'; b.innerHTML = `<span style="font-size:22px">${it.icon || ''}</span>${esc(it.label)}`; b.onclick = () => { bd.remove(); it.fn && it.fn(); }; $('.ac-grid', bd).appendChild(b); });
    bd.addEventListener('click', (e) => { if (e.target === bd) bd.remove(); }); document.body.appendChild(bd);
  }

  /* ============================== PAGE-WISE MUSHAF ============================== */
  const mp = { page: 0, tr: false };
  const mdata = () => jget('./data/mushaf.json');
  async function pageRange(p) { const d = await mdata(); const st = d.pages[p - 1], nx = d.pages[p]; return [gid(st[0], st[1]), nx ? gid(nx[0], nx[1]) - 1 : 6236]; }
  async function pageOf(s, a) { const d = await mdata(); let lo = 0, hi = 603; const g = gid(s, a); while (lo < hi) { const m = (lo + hi + 1) >> 1, st = d.pages[m]; if (gid(st[0], st[1]) <= g) lo = m; else hi = m - 1; } return lo + 1; }
  const upper = (arr, g, d) => { let lo = 0, hi = arr.length - 1; while (lo < hi) { const m = (lo + hi + 1) >> 1; if (gid(arr[m][0], arr[m][1]) <= g) lo = m; else hi = m - 1; } return lo + 1; };
  function setAb(title, sub) { const t = $('#abTitle'), d = $('.ab-sub'); if (t) t.textContent = title; let c = $('#abCtx'); if (!c && d) { c = document.createElement('div'); c.id = 'abCtx'; c.className = 'ab-sub'; d.after(c); } if (c) { c.textContent = sub || ''; c.style.display = sub ? '' : 'none'; } if (d) d.style.display = sub ? 'none' : ''; }

  /* ---- printed-mushaf layout: 15 justified lines per page, like the Madinah Qur'an ---- */
  async function layoutMushaf(card) {
    if (!card) return;
    try { await Promise.all([document.fonts.load('40px "KFGQPC Uthmanic Script HAFS"', 'بسم'), document.fonts.load('40px "Scheherazade New"', 'بسم'), document.fonts.load('40px Amiri', 'بسم')]); } catch (e) {}
    const mk = (s, a) => { const u = document.createElement('span'); u.className = 'mp-u'; u.dataset.s = s; u.dataset.a = a; return u; };
    const blocks = [];
    Array.from(card.children).forEach((ch) => {
      if (ch.classList.contains('mp-text')) {
        const units = [];
        Array.from(ch.children).forEach((ay) => {
          if (!ay.classList.contains('mp-ay')) return; const s = ay.dataset.s, a = ay.dataset.a; let last = null;
          Array.from(ay.childNodes).forEach((n) => {
            if (n.nodeType === 1 && n.classList.contains('rw')) { last = mk(s, a); last.appendChild(n); units.push(last); }
            else if (n.nodeType === 1 && n.classList.contains('mp-n')) { if (!last) { last = mk(s, a); units.push(last); } last.appendChild(n); }
            else if (n.nodeType === 3) { const t = n.textContent.trim(); if (t) { if (last) { const g = document.createElement('span'); g.className = 'mp-sg'; g.textContent = t; last.appendChild(g); } else { last = mk(s, a); last.textContent = t; units.push(last); } } }
          });
        });
        blocks.push({ t: 'text', units, el: ch });
      } else if (ch.classList.contains('mp-sh') || ch.classList.contains('mp-bs')) blocks.push({ t: 'fixed', el: ch });
    });
    if (!blocks.length) return;
    // measure every word once at 40px (widths scale linearly with font size)
    const meas = document.createElement('div'); meas.className = 'mp-line mp-meas'; meas.style.setProperty('--f', '40px'); document.body.appendChild(meas);
    blocks.forEach((b) => b.units && b.units.forEach((u) => meas.appendChild(u)));
    blocks.forEach((b) => b.units && b.units.forEach((u) => { u._w = u.getBoundingClientRect().width / 40; }));
    const cs = getComputedStyle(card), W = card.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight), GAP = 0.34, SC = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--ar-scale')) || 1;
    const widest = Math.max.apply(null, blocks.map((b) => (b.units ? Math.max.apply(null, b.units.map((u) => u._w)) : 0)).concat([1]));
    // font size follows the width of the screen (and the user's size setting); lines and words-per-line then fall out naturally
    let f = Math.min(34, Math.max(21, W * 0.068)) * SC; f = Math.min(f, W / widest * 0.98);
    const pack = (f) => { let total = 0; blocks.forEach((b) => { if (b.t === 'fixed') { total++; return; } const L = []; let cur = [], w = 0; b.units.forEach((u) => { const uw = u._w * f, add = (cur.length ? GAP * f : 0) + uw; if (cur.length && w + add > W) { L.push(cur); cur = [u]; w = uw; } else { cur.push(u); w += add; } }); if (cur.length) L.push(cur); b.lines = L; total += L.length; }); return total; };
    f = Math.floor(f * 10) / 10; const total = pack(f), H = Math.round(f * 2);
    const page = document.createElement('div'); page.className = 'mp-page'; page.style.setProperty('--f', f + 'px'); page.style.setProperty('--H', H + 'px'); if (total < 10) { page.classList.add('few'); page.style.minHeight = (Math.max(total + 2, 11) * H) + 'px'; }
    blocks.forEach((b) => {
      if (b.t === 'fixed') { page.appendChild(b.el); return; }
      b.lines.forEach((ln, li) => {
        const d = document.createElement('div'); d.className = 'mp-line'; let nat = 0; ln.forEach((u) => (nat += u._w * f)); nat += (ln.length - 1) * GAP * f;
        if (li === b.lines.length - 1 && nat < W * 0.8) d.classList.add('short');
        let seg = null; ln.forEach((u) => { if (!seg || seg.dataset.s !== u.dataset.s || seg.dataset.a !== u.dataset.a) { seg = document.createElement('span'); seg.className = 'mp-ay'; seg.dataset.s = u.dataset.s; seg.dataset.a = u.dataset.a; d.appendChild(seg); } seg.appendChild(u); });
        page.appendChild(d);
      });
    });
    meas.remove(); blocks.forEach((b) => b.t === 'text' && b.el.remove());
    card.insertBefore(page, $('.mp-foot', card));
  }
  async function renderMushaf(page, ro) {
    const el = makeTab('mushaf', 'Qur’an'); const d = await mdata();
    mp.page = Math.max(1, Math.min(604, page || mp.page || (STATE.mushaf && STATE.mushaf.page) || 1));
    const keepRec = !!(ro && ro.keepRec && rc.live);
    if (!keepRec) { if (rc.live) { rc.live = false; stopEngine(); } hideRecBar(); setFab(false); rc.page = false; rc.res = null; rc.words = []; rc.layout = null; }
    const [g0, g1] = await pageRange(mp.page), juz = upper(d.juz, g0), rub = upper(d.rub, g0), hizb = Math.floor((rub - 1) / 4) + 1;
    mp.words = []; let html = '', para = '', trs = '', wi = 0;
    const flush = () => { if (para) { html += `<div class="mp-text">${para}</div>`; para = ''; } };
    for (let g = g0; g <= g1; g++) {
      const [s, a] = refOfG(g), tx = await fetchLocalSurahText(s); if (!tx) continue; const ay = tx.ayahs[a - 1];
      if (a === 1) { flush(); html += `<div class="mp-sh"><span class="o"></span><b>سُورَةُ ${esc(sAr(s))}</b><span class="o"></span></div>`; if (s !== 1 && s !== 9) html += '<div class="mp-bs">بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ</div>'; }
      let inner = ''; ay.ar.split(/\s+/).forEach((w) => { const n = nrm(w); if (n.length) { inner += `<span class="rw" data-i="${wi}">${w}</span> `; mp.words.push({ t: w, n, s, a }); wi++; } else inner += w + ' '; });
      para += `<span class="mp-ay" data-s="${s}" data-a="${a}">${inner}<span class="mp-n">${indic(a)}</span></span> `;
      if (mp.tr) trs += `<div class="mt-row"><span class="ay-badge">${s}:${a}</span><div class="azkar-tr ${lang() === 'ur' ? 'ur-text' : ''}">${esc(lang() === 'ur' ? ay.ur : ay.en)}</div></div>`;
    }
    flush();
    const [fs, fa] = refOfG(g0);
    STATE.mushaf = { page: mp.page }; STATE.quranLastRead = Object.assign({}, STATE.quranLastRead, { surah: fs, ayah: fa, name: sName(fs) }); save();
    mp.info = { fs, fa, juz, hizb };
    setAb(sName(fs), `Page ${mp.page} | Juz ${juz} | Hizb ${hizb} | ${fs}:${fa}`);
    el.innerHTML = `<div class="mp-wrap" id="mpCard">${html}<div class="mp-foot">${indic(mp.page)}</div></div>${mp.tr ? `<div class="mp-trs">${trs}</div>` : ''}
      <div class="mp-dock" role="toolbar" aria-label="Reader tools"><button id="mpTr" class="${mp.tr ? 'on' : ''}" aria-label="Translation" title="Translation"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5h9M8.5 3v2M6 5c0 4 3 7 6.5 8M12 5c-.6 3.2-3 6.4-7 8.5M13.5 21l4-9 4 9M15 18h5"/></svg></button><button id="mpPlay" aria-label="Listen to this page" title="Listen"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 15v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="14" width="4" height="6.5" rx="1.6"/><rect x="17" y="14" width="4" height="6.5" rx="1.6"/></svg></button><i class="sep"></i><button id="mpNext" aria-label="Next page"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7"/></svg></button><button id="mpGo" class="pg" aria-label="Go to page">${mp.page}<small>/ 604</small></button><button id="mpPrev" aria-label="Previous page"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7"/></svg></button></div>`;
    await layoutMushaf($('#mpCard'));
    $('#mpPrev').onclick = () => go(mp.page - 1); $('#mpNext').onclick = () => go(mp.page + 1);
    $('#mpTr').onclick = () => { mp.tr = !mp.tr; renderMushaf(mp.page); };
    $('#mpGo').onclick = () => { if (window.SKX && SKX.jumpSheet) SKX.jumpSheet(); };
    $('#mpPlay').onclick = () => { const list = []; for (let g = g0; g <= g1; g++) { const [s, a] = refOfG(g); list.push({ s, a }); } Player.start(list, 0); };
    $$('.mp-ay', el).forEach((n) => (n.onclick = () => { if (rc.live) return; const same = $$('.mp-ay', el).filter((x) => x.dataset.s === n.dataset.s && x.dataset.a === n.dataset.a); if ($('#mpCard') && $('#mpCard').classList.contains('hide-text')) { const on = !n.classList.contains('reveal'); same.forEach((x) => x.classList.toggle('reveal', on)); return; } ayahActions(+n.dataset.s, +n.dataset.a, n); same.forEach((x) => x.classList.add('sel')); }));
    let x0 = null; const card = $('#mpCard');
    card.addEventListener('touchstart', (e) => { x0 = e.touches[0].clientX; }, { passive: true });
    card.addEventListener('touchend', (e) => { if (x0 == null) return; const dx = e.changedTouches[0].clientX - x0; x0 = null; if (Math.abs(dx) > 70) go(mp.page + (dx > 0 ? 1 : -1)); }, { passive: true });
    if (keepRec) { rc.words = mp.words; rc.layout = null; rc.res = null; rc.reveal = null; paintPage(); }
    updateFab(); window.scrollTo(0, 0);
  }
  async function jumpSheet() {
    const d = await mdata(), opt = (n, f) => Array.from({ length: n }, (_, i) => f(i + 1)).join('');
    const dlg = SKX.dialog('Go to', `<div class="hz-grid"><label>Surah<select id="jmS">${opt(114, (n) => `<option value="${n}" ${mp.info && n === mp.info.fs ? 'selected' : ''}>${n}. ${esc(sName(n))}</option>`)}</select></label><label>Juz<select id="jmJ">${opt(30, (j) => `<option value="${j}" ${mp.info && j === mp.info.juz ? 'selected' : ''}>Juz ${j}</option>`)}</select></label><label>Page<input type="number" id="jmP" min="1" max="604" value="${mp.page}"></label></div>`, [{ label: 'Cancel' }, { label: 'Go', primary: true, fn: (bd) => { go(+$('#jmP', bd).value || 1); } }]);
    $('#jmS', dlg.el).onchange = (e) => pageOf(+e.target.value, 1).then((p) => { $('#jmP', dlg.el).value = p; });
    $('#jmJ', dlg.el).onchange = (e) => { const st = d.juz[+e.target.value - 1]; pageOf(st[0], st[1]).then((p) => { $('#jmP', dlg.el).value = p; }); };
  }
  function go(p) { if (p < 1 || p > 604) return; renderMushaf(p); }
  window.SKX = window.SKX || {}; SKX.goPage = go;
  (function () { let w0 = innerWidth, t = null; window.addEventListener('resize', () => { clearTimeout(t); t = setTimeout(() => { if (Math.abs(innerWidth - w0) < 24) return; w0 = innerWidth; if (!rc.live && document.querySelector('#tab-mushaf.active #mpCard')) go(mp.page); }, 250); }); })();
  function ayahActions(s, a, node) {
    if (node) { $$('.mp-ay.sel').forEach((x) => x.classList.remove('sel')); node.classList.add('sel'); }
    actionSheet(`${sName(s)} · ${s}:${a}`, [
      { icon: '▶', label: 'Play', fn: () => playAyahAudio(s, a) }, { icon: '📖', label: 'Tafsir & ayah view', fn: () => openSurahAt(s, a) },
      { icon: isBm(s, a) ? '★' : '☆', label: isBm(s, a) ? 'Saved' : 'Bookmark', fn: () => SKX.toggleBm(s, a) }, { icon: '✎', label: 'Note', fn: () => SKX.editNote(s, a) },
      { icon: '⤴', label: 'Share', fn: () => SKX.share(s, a) }, { icon: '🧠', label: 'Memorize this', fn: () => SKX.hifz(s, a) }, { icon: '🎙', label: 'Recite from here', fn: () => reciteFrom(s, a) },
    ]);
  }
  const isBm = (s, a) => !!(STATE.bm || {})[s + ':' + a];

  /* ============================== RECITATION CHECKER ============================== */
  const AR_STRIP = /[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u0640\u08D3-\u08FF\u200C-\u200F\uFEFF]/g;
  const nrm = (w) => w.replace(AR_STRIP, '').replace(/[أإآٱ]/g, 'ا').replace(/[ىیي]/g, 'ي').replace(/[ةہھه]/g, 'ه').replace(/ؤ/g, 'و').replace(/ئ/g, 'ي').replace(/ء/g, '').replace(/[^\u0621-\u063A\u0641-\u064A\u0671-\u06D3]/g, '');
  const VOW = new Set(['ا', 'و', 'ي']); const CONF = {};
  ['ثسص', 'ذزظد', 'تط', 'كق', 'حه', 'عا', 'غخ', 'ضظ', 'نم'].forEach((g) => { for (const a of g) for (const b of g) if (a !== b) CONF[a + b] = 1; });
  const subC = (a, b) => (a === b ? 0 : VOW.has(a) && VOW.has(b) ? 0.25 : CONF[a + b] ? 0.5 : 1);
  const gapC = (c) => (VOW.has(c) ? 0.35 : 1);
  const TOL = { strict: 0.1, normal: 0.28, lenient: 0.45 };

  // char-level semi-global alignment: all heard letters must be explained; unspoken tail of the text is free
  function align(words, heard, tol) {
    const E = [], wi = []; words.forEach((w, k) => { for (const ch of w.n) { E.push(ch); wi.push(k); } });
    const R = []; heard.forEach((w) => { for (const ch of w) R.push(ch); });
    const m = R.length, nAll = E.length, n = Math.min(nAll, Math.ceil(m * 1.7) + 90);
    const W = m + 1, D = new Float32Array((n + 1) * W), O = new Uint8Array((n + 1) * W);
    for (let j = 1; j <= m; j++) { D[j] = D[j - 1] + gapC(R[j - 1]); O[j] = 2; }
    for (let i = 1; i <= n; i++) {
      D[i * W] = D[(i - 1) * W] + gapC(E[i - 1]); O[i * W] = 1;
      for (let j = 1; j <= m; j++) {
        const d = D[(i - 1) * W + j - 1] + subC(E[i - 1], R[j - 1]), u = D[(i - 1) * W + j] + gapC(E[i - 1]), l = D[i * W + j - 1] + gapC(R[j - 1]);
        if (d <= u && d <= l) { D[i * W + j] = d; O[i * W + j] = 0; } else if (u <= l) { D[i * W + j] = u; O[i * W + j] = 1; } else { D[i * W + j] = l; O[i * W + j] = 2; }
      }
    }
    let bi = 0, bc = Infinity; for (let i = 0; i <= n; i++) { const c = D[i * W + m]; if (c < bc || (c === bc && i > bi)) { bc = c; bi = i; } }
    if (m === 0) bi = 0;
    // quality gate: if the heard letters barely resemble the text, it is noise — don't accuse the reciter
    if (m >= 6 && bc / m > 0.62) return { words: words.map(() => ({ state: 'pend', err: 0, heard: '' })), cursor: 0, done: false, unclear: true };
    // backtrace → cost & heard letters per expected letter
    const cost = new Float32Array(n), hc = new Array(n).fill(''), st = new Uint8Array(n); // st: 1 matched/sub, 2 deleted
    let i = bi, j = m;
    while (i > 0 || j > 0) { const o = O[i * W + j]; if (i > 0 && j > 0 && o === 0) { cost[i - 1] = subC(E[i - 1], R[j - 1]); hc[i - 1] = R[j - 1]; st[i - 1] = 1; i--; j--; } else if (i > 0 && (o === 1 || j === 0)) { cost[i - 1] = gapC(E[i - 1]); st[i - 1] = 2; i--; } else { j--; } }
    const out = words.map((w) => ({ state: 'pend', err: 0, heard: '' }));
    let k = 0, p = 0, cur = -1;
    while (k < words.length) {
      const len = words[k].n.length; const a = p, b = p + len; p = b;
      if (a >= bi) break;                                        // not reached yet
      if (b > bi) { out[k].state = 'cur'; cur = k; break; }       // being recited right now
      let sum = 0, del = 0, h = ''; for (let x = a; x < b; x++) { sum += cost[x]; if (st[x] === 2) del++; h += hc[x] || ''; }
      const err = len ? sum / len : 0; out[k].err = err; out[k].heard = h;
      out[k].state = del === len ? 'miss' : err <= tol ? 'ok' : 'wrong'; k++;
    }
    if (cur < 0) cur = Math.min(k, words.length);
    return { words: out, cursor: cur, done: bi >= nAll && m > 0 };
  }

  const rc = { s: 1, from: 1, to: 7, list: null, mode: 'follow', tol: 'normal', words: [], heard: '', cur: '', live: false, res: null, hints: 0, t0: 0, engine: null, typed: false };
  const TK = window.SKTrack; let trk = null; const mkTrk = (anchor) => { trk = TK.create({ tol: rc.tol }); trk.setWords(rc.words, anchor || 0); rc.carry = rc.carry || []; return trk; };
  const rcS = () => (STATE.recite = STATE.recite || { hist: [] });

  async function loadRange(list) {
    const words = []; const items = list || (() => { const a = []; for (let x = rc.from; x <= rc.to; x++) a.push([rc.s, x]); return a; })();
    for (const [s, a] of items) { const ay = await ayahOf(s, a); if (!ay) continue; ay.ar.split(/\s+/).forEach((w) => { const n = nrm(w); if (n.length) words.push({ t: w, n, s, a }); }); words.push({ end: true, s, a }); }
    rc.words = words.filter((w) => !w.end).map((w) => w); rc.layout = words; rc.heard = ''; rc.cur = ''; rc.res = null; rc.hints = 0; rc.t0 = 0;
  }
  function paintText() {
    if (rc.page) { paintPage(); return; }
    const box = $('#rcText'); if (!box) return; const res = rc.res; let wi = 0, html = '';
    rc.layout.forEach((w) => {
      if (w.end) { html += `<span class="rw-end">﴿${indic(w.a)}﴾</span> `; return; }
      const r = res ? res.words[wi] : null, st = r ? r.state : 'pend', hide = rc.mode === 'memorize' && (st === 'pend' || st === 'cur') && !(rc.reveal && rc.reveal.has(wi));
      html += `<span class="rw ${st}${hide ? ' hid' : ''}" data-i="${wi}">${esc(w.t)}</span> `; wi++;
    });
    box.innerHTML = html;
    const c = $('.rw.cur', box) || $$('.rw.pend', box)[0]; if (c) { const r = c.getBoundingClientRect(); if (r.top < 120 || r.bottom > innerHeight - 220) c.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
    $$('.rw', box).forEach((n) => (n.onclick = () => { const i = +n.dataset.i, w = rc.words[i], r = res && res.words[i]; if (r && (r.state === 'wrong' || r.state === 'miss')) wordInfo(w, r); else { (rc.reveal = rc.reveal || new Set()).add(i); rc.hints++; paintText(); } }));
    const hv = $('#rcHeard'); if (hv) hv.textContent = res && res.unclear ? '🤔 Couldn’t make that out — please recite again, a little closer to the microphone.' : ((rc.heard + ' ' + rc.cur).trim() || 'Press the mic and start reciting…');
    const ev = (res ? res.words.filter((x) => x.state === 'ok' || x.state === 'wrong' || x.state === 'miss') : []), ok = ev.filter((x) => x.state === 'ok').length;
    const pr = $('#rcProg'); if (pr) pr.textContent = ev.length ? `${ok}/${ev.length} correct · ${rc.words.length - ev.length} to go` : `${rc.words.length} words`;
  }
  function wordInfo(w, r) {
    actionSheet(esc(w.t), [{ icon: '🔊', label: 'Hear this ayah', fn: () => playAyahAudio(w.s, w.a) }, { icon: '👁', label: 'Show word', fn: () => { (rc.reveal = rc.reveal || new Set()).add(rc.words.indexOf(w)); paintText(); } }]);
  }
  let jumping = false, lastGlobal = 0;
  function update(isFinal) {
    if (jumping) return;
    if (!trk || trk.words !== rc.words) mkTrk(0);
    const heard = (rc.heard + ' ' + rc.cur).split(/\s+/).filter(Boolean);
    const prevRes = rc.res;
    const now = Date.now(); trk.noGlobal = !isFinal && now - lastGlobal < 700; // throttle the heavy Qur'an-wide search on partials
    const res = trk.feed(heard);
    if (res.jump) { lastGlobal = now; doJump(res.jump); return; }
    if (res.unclear && prevRes) { rc.res = Object.assign({}, prevRes, { unclear: true }); } else rc.res = res;
    paintText();
    if (isFinal) trk.commit();
    if (res.done && res.words.every((x) => x.state !== 'pend' && x.state !== 'cur')) onPageDone();
  }
  // recitation belongs somewhere else in the Qur'an -> move the reader there and keep listening
  async function doJump(j) {
    jumping = true; const keep = rc.heard + ' ' + rc.cur;
    try {
      if (rc.page) { stashPage(); const pg = await pageOf(j.s, j.a); await renderMushaf(pg, { keepRec: true }); }
      else { rc.s = j.s; rc.from = j.a; rc.to = Math.min(sCount(j.s), j.a + 6); rc.list = null; await loadRange(null); rc.reveal = null; renderRecIfNeeded(); }
      mkTrk(0); trk.begin(); showToast(sName(j.s) + ' · ' + j.s + ':' + j.a);
    } catch (e) { console.error(e); }
    jumping = false;
    const heard = keep.split(/\s+/).filter(Boolean); if (heard.length) { trk.noGlobal = true; rc.res = trk.feed(heard); paintText(); }
  }
  function renderRecIfNeeded() { const sel = $('#rcS'); if (sel) { sel.value = rc.s; const f = $('#rcF'), t = $('#rcT'); if (f && t) { f.innerHTML = opts(1, sCount(rc.s), rc.from); t.innerHTML = opts(rc.from, sCount(rc.s), rc.to); } } }
  function stashPage() { if (!rc.res) return; rc.words.forEach((w, i) => { const x = rc.res.words[i]; if (x && (x.state === 'ok' || x.state === 'wrong' || x.state === 'miss')) rc.carry.push({ w, x }); }); }
  async function onPageDone() {
    if (rc.page && rc.live && mp.page < 604) { // flip to the next page and keep going, like turning the page
      jumping = true; stashPage(); await renderMushaf(mp.page + 1, { keepRec: true }); mkTrk(0); trk.begin(); jumping = false; return;
    }
    finish();
  }

  /* ---------------- inline page recitation (words highlighted on the Mushaf page itself) ---------------- */
  function paintPage() {
    const card = $('#mpCard'); if (!card) return; const res = rc.res, active = rc.page && (rc.live || res);
    card.classList.toggle('rec', !!active); card.classList.toggle('hifz', !!active && rc.mode === 'memorize');
    $$('.rw', card).forEach((n) => { const i = +n.dataset.i, r = res && res.words[i]; n.className = 'rw ' + (active ? (r ? r.state : 'pend') : '') + (rc.reveal && rc.reveal.has(i) ? ' rev' : ''); });
    const c = $('.rw.cur', card); if (c) { const r = c.getBoundingClientRect(); if (r.top < 110 || r.bottom > innerHeight - 190) c.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
    const hv = $('#recHeard'); if (hv) hv.textContent = res && res.unclear ? 'Couldn’t make that out — recite again' : '';
  }
  let recIv = null;
  function showRecBar() {
    let b = $('#recBar'); if (!b) { b = document.createElement('div'); b.id = 'recBar'; b.innerHTML = '<span class="dot"></span><b id="recT">00:00</b><span id="recHeard"></span><button id="rbEye" aria-label="Hide or show text">👁</button><button id="rbRestart" aria-label="Restart">↺</button><button id="rbDone" aria-label="Finish">✔</button>'; document.body.appendChild(b); }
    b.classList.add('show'); document.body.classList.add('recording'); clearInterval(recIv); recIv = setInterval(() => { const t = $('#recT'); if (!t || !rc.t0) return; const sec = Math.floor((Date.now() - rc.t0) / 1000); t.textContent = String(Math.floor(sec / 60)).padStart(2, '0') + ':' + String(sec % 60).padStart(2, '0'); }, 500);
    $('#rbEye').onclick = () => { rc.mode = rc.mode === 'memorize' ? 'follow' : 'memorize'; S().recMode = rc.mode; save(); showToast(rc.mode === 'memorize' ? 'Hidden — words appear as you recite them' : 'Text visible'); paintPage(); };
    $('#rbRestart').onclick = () => { rc.heard = ''; rc.cur = ''; rc.res = null; rc.reveal = null; rc.t0 = Date.now(); rc.carry = []; mkTrk(0); trk.begin(); stopEngine(); paintPage(); };
    $('#rbDone').onclick = () => { if (rc.res) finish(); else { rc.live = false; stopEngine(); hideRecBar(); setFab(false); paintPage(); } };
  }
  function hideRecBar() { clearInterval(recIv); document.body.classList.remove('recording'); const b = $('#recBar'); if (b) b.classList.remove('show'); }
  function togglePageRecite() {
    if (rc.live) { rc.live = false; stopEngine(); setFab(false); if (rc.res) finish(); else { hideRecBar(); paintPage(); } return; }
    if (engineKind() === 'none') { showToast('Speech recognition is not available on this device'); return; }
    if (!mp.words.length) return;
    rc.page = true; rc.words = mp.words; rc.layout = null; rc.heard = ''; rc.cur = ''; rc.res = null; rc.reveal = null; rc.hints = 0; rc.t0 = Date.now(); rc.mode = S().recMode || 'follow';
    rc.carry = []; mkTrk(0); trk.begin(); preIndex(); rc.live = true; showRecBar(); setFab(true); paintPage(); startEngine();
  }
  function setFab(on) { const f = $('#micFab'); if (f) { f.classList.toggle('on', !!on); f.innerHTML = on ? '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="7" y="7" width="10" height="10" rx="2"/></svg>' : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><rect x="9" y="3" width="6" height="11" rx="3" fill="currentColor"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg>'; } }
  function updateFab() {
    let f = $('#micFab'); if (!f) { f = document.createElement('button'); f.id = 'micFab'; f.setAttribute('aria-label', 'Recite'); document.body.appendChild(f); f.onclick = () => { if ($('#tab-mushaf.active')) togglePageRecite(); else if (lastOpenedSurah && $('#quranModal.open')) { const a = (STATE.quranLastRead && STATE.quranLastRead.surah === lastOpenedSurah.num ? STATE.quranLastRead.ayah : 1) || 1; closeModal('quranModal'); reciteFrom(lastOpenedSurah.num, a); } }; setFab(false); }
    f.classList.toggle('show', !!$('#tab-mushaf.active') || !!$('#quranModal.open'));
  }

  /* ---- speech engines: native Android recogniser, browser Web Speech, or typed text ---- */
  const SR_FATAL = new Set(['5', '9', '12', '13', '2', '3']);
  window.SKSR = {
    onEvent(type, text) {
      if (!rc.live) return;
      if (type === 'partial') { rc.cur = text; rc.heard = ''; update(false); }
      else if (type === 'final') { rc.cur = text; rc.heard = ''; update(true); rc.cur = ''; restart(); }
      else if (type === 'end') { if (rc.cur) { update(true); rc.cur = ''; } restart(); }
      else if (type.indexOf('error') === 0) { const code = type.split(':')[1]; if (SR_FATAL.has(code)) { if (trk) trk.commit(); stopEngine(); rc.live = false; setMic(false); showToast(code === '9' ? 'Microphone permission needed' : code === '2' || code === '3' ? 'Speech service unavailable (check internet or install the Arabic voice pack)' : 'Speech recognition error ' + code); } else { if (rc.cur) { update(true); rc.cur = ''; } restart(); } }
    },
  };
  let rcT = null; function restart() { if (!rc.live) return; clearTimeout(rcT); rcT = setTimeout(() => { if (rc.live) { if (trk) trk.begin(); startEngine(); } }, 40); }
  function engineKind() { const n = N2(); if (n && n.startListening) return 'native'; if (window.SpeechRecognition || window.webkitSpeechRecognition) return 'web'; return 'none'; }
  function startEngine() {
    const k = engineKind(), n = N2();
    if (k === 'native') { try { if (!n.hasMic()) { n.requestMic(); showToast('Allow the microphone when Android asks, then tap the mic again'); rc.live = false; setMic(false); setFab(false); hideRecBar(); setTimeout(() => { if (!n.hasMic()) SKX.dialog('Microphone permission', '<p class="sk-p">Android did not give this app microphone access. Open the app settings → Permissions → Microphone → Allow, then tap the mic again.</p>', [{ label: 'Not now' }, { label: 'Open settings', primary: true, fn: () => { if (n.appSettings) n.appSettings(); } }]); }, 2800); return; } n.startListening('ar-SA', !!S().recOffline); } catch (e) { showToast('Could not start speech recognition'); rc.live = false; setMic(false); setFab(false); } }
    else if (k === 'web') {
      const SR = window.SpeechRecognition || window.webkitSpeechRecognition; const r = rc.webrec = new SR(); r.lang = 'ar-SA'; r.interimResults = true; r.continuous = false;
      r.onresult = (e) => { let fin = '', tmp = ''; for (let i = e.resultIndex; i < e.results.length; i++) { const t = e.results[i][0].transcript; if (e.results[i].isFinal) fin += ' ' + t; else tmp += ' ' + t; } if (fin) window.SKSR.onEvent('final', fin.trim()); else window.SKSR.onEvent('partial', tmp.trim()); };
      r.onend = () => window.SKSR.onEvent('end', ''); r.onerror = (e) => window.SKSR.onEvent('error:' + (e.error === 'not-allowed' ? 9 : e.error === 'no-speech' ? 7 : 5), ''); try { r.start(); } catch (e) {}
    }
  }
  function preIndex() { TK.buildIndex((n) => fetchLocalSurahText(n)).catch(() => {}); }
  function stopEngine() { try { const n = N2(); if (n && n.stopListening) n.stopListening(); if (rc.webrec) { rc.webrec.onend = null; rc.webrec.abort(); } } catch (e) {} }
  function setMic(on) { const b = $('#rcMic'); if (!b) return; b.classList.toggle('on', on); b.innerHTML = on ? '⏹<small>Stop</small>' : '🎙<small>Start</small>'; const st = $('#rcState'); if (st) st.textContent = on ? 'Listening…' : 'Ready'; st && st.classList.toggle('on', on); }
  async function toggleMic() {
    if (!rc.words.length) await loadRange(rc.list);
    if (rc.live) { rc.live = false; stopEngine(); setMic(false); if (rc.res) finish(); return; }
    if (engineKind() === 'none') { showToast('Speech recognition is not available here — use the “type” box below to try it'); return; }
    rc.carry = []; if (!trk || trk.words !== rc.words) mkTrk(0); trk.begin(); preIndex(); rc.live = true; if (!rc.t0) rc.t0 = Date.now(); setMic(true); startEngine();
  }
  function finish() {
    const wasLive = rc.live, pageMode = rc.page; rc.live = false; stopEngine(); setMic(false); setFab(false); if (!rc.res) { if (pageMode) { hideRecBar(); paintPage(); } return; }
    if (trk) trk.commit(); const cw = []; const ev0 = rc.res.words.map((x, i) => ({ x, i })).filter((o) => o.x.state === 'ok' || o.x.state === 'wrong' || o.x.state === 'miss'); const carried = (rc.carry || []).map((c) => { cw.push(c.w); return { x: c.x, i: cw.length - 1 }; }); rc.words = cw.concat(rc.words); const ev = carried.concat(ev0.map((o) => ({ x: o.x, i: o.i + cw.length }))); rc.carry = [];
    if (!ev.length) { if (wasLive) showToast('Nothing was recognised — try again closer to the microphone'); return; }
    const ok = ev.filter((o) => o.x.state === 'ok').length, bad = ev.filter((o) => o.x.state !== 'ok'), acc = Math.round(ok / ev.length * 100);
    const byAyah = {}; bad.forEach((o) => { const w = rc.words[o.i]; (byAyah[w.s + ':' + w.a] = byAyah[w.s + ':' + w.a] || []).push({ w, o }); });
    const keys = Object.keys(byAyah);
    const h = rcS().hist; h.unshift({ d: Date.now(), s: rc.words[0].s, a: rc.words[0].a, words: ev.length, acc, bad: bad.length, mode: rc.mode }); h.length = Math.min(h.length, 40);
    hifzUpdate(rc.words.map((w) => w.s + ':' + w.a), acc); save();
    const rows = keys.map((k) => { const [s, a] = k.split(':').map(Number); return `<div class="rc-bad"><div class="ref">${esc(sName(s))} ${s}:${a}</div><div class="ar-sm" style="text-align:right">${byAyah[k].map((b) => `<span class="rw ${b.o.x.state}">${esc(b.w.t)}</span>`).join(' ')}</div></div>`; }).join('');
    const dlg = SKX.dialog(`${acc >= 90 ? '🌟' : acc >= 70 ? '👍' : '💪'} ${acc}% accuracy`, `<div class="rc-sum"><div><b>${ok}</b><small>correct</small></div><div><b>${bad.filter((o) => o.x.state === 'wrong').length}</b><small>wrong</small></div><div><b>${bad.filter((o) => o.x.state === 'miss').length}</b><small>skipped</small></div></div>${rows ? `<div class="sk-p" style="margin:8px 0">Words to practise (tap to hear):</div>${rows}` : '<p class="sk-p">No mistakes found. MashaAllah!</p>'}<p class="sk-p">This checks the words you said. It can’t judge tajweed (makharij, madd, ghunnah) — recite to a teacher for that.</p>`, [
      { label: 'Close' },
      ...(keys.length ? [{ label: 'Practise mistakes', primary: true, fn: () => { if (pageMode) { setTimeout(togglePageRecite, 200); return; } rc.list = keys.map((k) => k.split(':').map(Number)); loadRange(rc.list).then(() => { paintText(); toggleMic(); }); } }] : [{ label: 'Again', primary: true, fn: () => { loadRange(rc.list).then(paintText); } }]),
      ...(acc >= 90 && rc.mode === 'memorize' ? [{ label: 'Mark memorized', fn: () => { rc.words.forEach((w) => hifzAdd(w.s, w.a)); showToast('Added to your Hifz tracker'); } }] : []),
    ]);
    $$('.rc-bad', dlg.el).forEach((r, i) => (r.onclick = () => { const [s, a] = keys[i].split(':').map(Number); playAyahAudio(s, a); }));
    rc.res = null; rc.heard = ''; rc.cur = ''; rc.reveal = null; if (pageMode) { hideRecBar(); paintPage(); }
  }
  function reciteFrom(s, a) { rc.s = s; rc.from = a; rc.to = Math.min(sCount(s), a + 4); rc.list = null; setTab('recite'); }
  const opts = (from, to, sel) => { let h = ''; for (let i = from; i <= to; i++) h += `<option value="${i}" ${i === sel ? 'selected' : ''}>${i}</option>`; return h; };
  async function renderRecite() {
    const el = makeTab('recite', 'Recite & check'); const k = engineKind(); rc.page = false; hideRecBar(); setAb('Recite & check', 'Say it aloud — words are checked live');
    const lr = STATE.quranLastRead; if (!rc.words.length && lr && lr.surah && !rc.list) { rc.s = lr.surah; rc.from = lr.ayah || 1; rc.to = Math.min(sCount(rc.s), rc.from + 4); }
    el.innerHTML = `${back('home')}<div class="card"><div class="sk-h">🎙 Recite &amp; check</div>
      <p class="sk-p">Recite aloud. Correct words turn bright white, wrong words turn <b style="color:var(--danger)">red</b> and skipped words are <b style="color:var(--warning)">struck through in amber</b>. In Memorize mode the text is hidden and each word appears only when you recite it correctly.</p>
      <div class="hz-grid"><label>Surah<select id="rcS">${Array.from({ length: 114 }, (_, i) => `<option value="${i + 1}" ${i + 1 === rc.s ? 'selected' : ''}>${i + 1}. ${esc(sName(i + 1))}</option>`).join('')}</select></label>
        <label>Mode<select id="rcMode"><option value="follow" ${rc.mode === 'follow' ? 'selected' : ''}>Follow (text visible)</option><option value="memorize" ${rc.mode === 'memorize' ? 'selected' : ''}>Memorize (text hidden)</option></select></label>
        <label>From ayah<select id="rcF">${opts(1, sCount(rc.s), rc.from)}</select></label><label>To ayah<select id="rcT">${opts(1, sCount(rc.s), rc.to)}</select></label>
        <label>Sensitivity<select id="rcTol"><option value="strict" ${rc.tol === 'strict' ? 'selected' : ''}>Strict</option><option value="normal" ${rc.tol === 'normal' ? 'selected' : ''}>Normal</option><option value="lenient" ${rc.tol === 'lenient' ? 'selected' : ''}>Lenient</option></select></label>
        <label>Offline voice<select id="rcOff"><option value="0">If available</option><option value="1" ${S().recOffline ? 'selected' : ''}>Prefer offline</option></select></label></div>
      <div class="sk-p" id="rcEng">${k === 'native' ? '🎧 Using your phone’s speech recognition (Arabic). Works best in a quiet place; some phones need internet or the offline Arabic voice pack.' : k === 'web' ? '🎧 Using your browser’s speech recognition.' : '⚠️ No speech recognition on this device — you can still try the checker by typing what you recite below.'}</div></div>
      <div class="card rc-stage"><div class="rc-top"><span class="chip" id="rcState">Ready</span><span class="sk-p" id="rcProg"></span></div>
        <div class="rc-text" id="rcText">Loading…</div><div class="rc-heard" id="rcHeard"></div>
        <div class="rc-ctl"><button class="btn" id="rcListen">🔊 Listen</button><button class="rc-mic" id="rcMic">🎙<small>Start</small></button><button class="btn" id="rcFinish">✔ Finish</button></div>
        <div class="row-btns" style="justify-content:center"><button class="btn" id="rcReset">↺ Restart</button><button class="btn" id="rcHint">💡 Reveal next</button></div></div>
      <div class="card"><div class="sk-h" style="font-size:14px">Type instead (practice without a microphone)</div><textarea id="rcType" rows="2" dir="rtl" placeholder="اكتب ما تلوته هنا…" style="width:100%"></textarea><button class="btn" id="rcCheck" style="margin-top:8px">Check typed text</button></div>
      <div class="card"><div class="sk-h" style="font-size:14px">Recent sessions</div>${rcS().hist.length ? rcS().hist.slice(0, 6).map((h) => `<div class="pp-row" style="grid-template-columns:1fr 60px"><span>${esc(sName(h.s))} ${h.s}:${h.a} · ${h.words} words · ${new Date(h.d).toLocaleDateString()}</span><b>${h.acc}%</b></div>`).join('') : '<p class="sk-p">Your sessions will appear here.</p>'}</div>`;
    $('#rcS').onchange = (e) => { rc.s = +e.target.value; rc.from = 1; rc.to = Math.min(sCount(rc.s), 5); rc.list = null; renderRecite(); };
    $('#rcF').onchange = (e) => { rc.from = +e.target.value; if (rc.to < rc.from) rc.to = rc.from; rc.list = null; renderRecite(); };
    $('#rcT').onchange = (e) => { rc.to = Math.max(+e.target.value, rc.from); rc.list = null; renderRecite(); };
    $('#rcMode').onchange = (e) => { rc.mode = e.target.value; rc.reveal = null; paintText(); }; $('#rcTol').onchange = (e) => { rc.tol = e.target.value; if (rc.res) update(); };
    $('#rcOff').onchange = (e) => { S().recOffline = e.target.value === '1'; save(); };
    $('#rcMic').onclick = toggleMic; $('#rcFinish').onclick = () => { if (rc.res) finish(); else showToast('Recite something first'); };
    $('#rcReset').onclick = () => { rc.live = false; stopEngine(); setMic(false); loadRange(rc.list).then(paintText); };
    $('#rcListen').onclick = () => { const w = rc.words[rc.res ? rc.res.cursor : 0] || rc.words[0]; if (w) playAyahAudio(w.s, w.a); };
    $('#rcHint').onclick = () => { const i = rc.res ? rc.res.cursor : 0; (rc.reveal = rc.reveal || new Set()).add(i); rc.hints++; paintText(); };
    $('#rcCheck').onclick = () => { const t = $('#rcType').value.trim(); if (!t) return; rc.heard = t; rc.cur = ''; rc.live = true; update(true); rc.live = false; finish(); };
    await loadRange(rc.list); paintText();
  }

  /* ============================== HIFZ TRACKER (spaced revision) ============================== */
  const HM = () => ((STATE.hifz = STATE.hifz || { m: {} }).m);
  const LEV = [1, 3, 7, 14, 30, 60, 120];
  function hifzAdd(s, a) { HM()[s + ':' + a] = { v: 0, d: dayNum(), a: dayNum() }; save(); }
  function hifzUpdate(keys, acc) { const m = HM(), t = dayNum(); keys.forEach((k) => { const it = m[k]; if (!it) return; if (acc >= 90) it.v = Math.min(LEV.length - 1, (it.v || 0) + 1); else if (acc < 60) it.v = 0; it.d = t; }); }
  function hifzSegments() {
    const m = HM(), t = dayNum(), due = Object.keys(m).filter((k) => m[k].d + LEV[m[k].v || 0] <= t).map((k) => { const [s, a] = k.split(':').map(Number); return { s, a, over: t - (m[k].d + LEV[m[k].v || 0]) }; }).sort((x, y) => x.s - y.s || x.a - y.a);
    const segs = []; due.forEach((x) => { const l = segs[segs.length - 1]; if (l && l.s === x.s && x.a === l.to + 1 && x.a - l.from < 15) { l.to = x.a; l.over = Math.max(l.over, x.over); } else segs.push({ s: x.s, from: x.a, to: x.a, over: x.over }); });
    return segs.sort((x, y) => y.over - x.over);
  }
  const hf = { s: 1, f: 1, t: 7 };
  function renderHifz() {
    const el = makeTab('hifz', 'Hifz tracker'), m = HM(), total = Object.keys(m).length, segs = hifzSegments();
    const per = new Array(115).fill(0); Object.keys(m).forEach((k) => { per[+k.split(':')[0]]++; });
    el.innerHTML = `${back('home')}<div class="card kh-card"><div class="kh-top"><div><div class="kh-big">${total}</div><div class="sk-p">ayahs memorized · ${(total / 6236 * 100).toFixed(1)}% of the Qur’an</div></div><div class="kh-ring"><b>${segs.length}</b><small>due today</small></div></div><div class="az-prog" style="height:10px;margin-top:12px"><i style="width:${total / 6236 * 100}%"></i></div></div>
      <div class="card"><div class="sk-h">🔁 Revision due</div>${segs.length ? segs.slice(0, 12).map((g, i) => `<div class="sk-row" style="margin:6px 0;padding:10px" data-i="${i}"><div class="sk-mid"><b>${esc(sName(g.s))} ${g.s}:${g.from}${g.to > g.from ? '–' + g.to : ''}</b><small>${g.over > 0 ? g.over + ' day(s) overdue' : 'due today'}</small></div><button class="btn primary" style="padding:6px 12px">Revise</button></div>`).join('') : '<p class="sk-p">Nothing due. Add what you have memorized below and the app will schedule revision (1, 3, 7, 14, 30, 60, 120 days).</p>'}</div>
      <div class="card"><div class="sk-h">➕ Add what you have memorized</div><div class="hz-grid"><label>Surah<select id="hfS">${Array.from({ length: 114 }, (_, i) => `<option value="${i + 1}" ${i + 1 === hf.s ? 'selected' : ''}>${i + 1}. ${esc(sName(i + 1))}</option>`).join('')}</select></label><label>&nbsp;<span class="sk-p" id="hfCnt">${per[hf.s]}/${sCount(hf.s)} memorized</span></label>
        <label>From<select id="hfF">${opts(1, sCount(hf.s), hf.f)}</select></label><label>To<select id="hfT">${opts(1, sCount(hf.s), Math.min(sCount(hf.s), hf.t))}</select></label></div>
        <div class="row-btns"><button class="btn primary" id="hfAdd">Mark memorized</button><button class="btn" id="hfDel">Remove</button><button class="btn" id="hfFull">Whole surah</button></div></div>
      <div class="card"><div class="sk-h">Surah map</div><div class="hf-grid">${Array.from({ length: 114 }, (_, i) => { const n = i + 1, f = per[n] / sCount(n); return `<button class="hf-c" data-n="${n}" style="--f:${f.toFixed(2)}" title="${esc(sName(n))}">${n}</button>`; }).join('')}</div></div>`;
    $('#hfS').onchange = (e) => { hf.s = +e.target.value; hf.f = 1; hf.t = Math.min(sCount(hf.s), 7); renderHifz(); };
    $('#hfF').onchange = (e) => { hf.f = +e.target.value; if (hf.t < hf.f) hf.t = hf.f; }; $('#hfT').onchange = (e) => { hf.t = +e.target.value; };
    $('#hfAdd').onclick = () => { for (let a = Math.min(hf.f, hf.t); a <= Math.max(hf.f, hf.t); a++) hifzAdd(hf.s, a); renderHifz(); showToast('Saved'); };
    $('#hfDel').onclick = () => { for (let a = Math.min(hf.f, hf.t); a <= Math.max(hf.f, hf.t); a++) delete HM()[hf.s + ':' + a]; save(); renderHifz(); };
    $('#hfFull').onclick = () => { for (let a = 1; a <= sCount(hf.s); a++) hifzAdd(hf.s, a); renderHifz(); showToast('Whole surah added'); };
    $$('.hf-c', el).forEach((c) => (c.onclick = () => { hf.s = +c.dataset.n; hf.f = 1; hf.t = Math.min(sCount(hf.s), 7); renderHifz(); window.scrollTo(0, 0); }));
    $$('.sk-row[data-i]', el).forEach((r) => (r.onclick = () => { const g = segs[+r.dataset.i]; rc.s = g.s; rc.from = g.from; rc.to = g.to; rc.list = null; rc.mode = 'memorize'; setTab('recite'); }));
  }

  /* ============================== ENTRY POINTS (More sheet, Qur'an tab, reader) ============================== */
  async function openMushafAt(s, a) { mp.page = await pageOf(s, a); setTab('mushaf'); }
  const R2 = { mushaf: () => renderMushaf(), recite: renderRecite, hifz: () => (window.renderHifzHub ? window.renderHifzHub() : renderHifz()) };
  function addEntryPoints() {
    const grid = $('#moreSheet .sheet-grid'); if (grid && !grid.__f2) {
      grid.__f2 = 1; const ic = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${p}</svg>`;
      [['mushaf', 'Mushaf pages', '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/>'], ['recite', 'Recite & check', '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>'], ['hifz', 'Hifz tracker', '<path d="M12 3l9 5-9 5-9-5z"/><path d="M7 11v5c0 1.5 2.2 3 5 3s5-1.5 5-3v-5"/>']].forEach(([go, label, p]) => { const b = document.createElement('button'); b.className = 'tile'; b.dataset.go = go; b.innerHTML = ic(p) + label; b.onclick = () => { $('#moreSheet').classList.remove('open'); setTab(go); }; grid.appendChild(b); });
    }
    const sub = $('#sub-quran-surahs') || $('#tab-quran .subsection'); if (sub && !$('#qkActions')) {
      const d = document.createElement('div'); d.id = 'qkActions'; d.className = 'qk-actions'; d.innerHTML = '<button class="btn" data-q="mushaf">📄 Page view</button><button class="btn" data-q="recite">🎙 Recite &amp; check</button><button class="btn" data-q="hifz">🧠 Hifz</button>';
      sub.insertBefore(d, sub.firstChild); $$('[data-q]', d).forEach((b) => (b.onclick = () => { if (b.dataset.q === 'mushaf') { const l = STATE.quranLastRead; if (l && l.surah) openMushafAt(l.surah, l.ayah || 1); else setTab('mushaf'); } else setTab(b.dataset.q); }));
    }
    const pb = $('#quranPlayAllBtn'); if (pb && !$('#qmPageBtn')) {
      const b = document.createElement('button'); b.className = 'btn'; b.id = 'qmPageBtn'; b.style.cssText = 'width:100%;margin-bottom:8px;'; b.textContent = '📄 Open in page-wise view'; pb.parentNode.insertBefore(b, pb);
      b.onclick = () => { const l = lastOpenedSurah; closeModal('quranModal'); if (l) { const last = STATE.quranLastRead && STATE.quranLastRead.surah === l.num ? STATE.quranLastRead.ayah || 1 : 1; openMushafAt(l.num, last); } };
      const rb = document.createElement('button'); rb.className = 'btn'; rb.id = 'qmReciteBtn'; rb.style.cssText = 'width:100%;margin-bottom:8px;'; rb.textContent = '🎙 Recite this surah & check'; pb.parentNode.insertBefore(rb, pb);
      rb.onclick = () => { const l = lastOpenedSurah; closeModal('quranModal'); if (l) reciteFrom(l.num, 1); };
    }
  }

  const CSS = `
  .qk-actions{ display:flex; gap:8px; flex-wrap:wrap; margin:0 0 12px; } .qk-actions .btn{ flex:1; min-width:120px; }
  .mp-bar{ display:grid; grid-template-columns:1fr 1.5fr; gap:8px; margin-bottom:10px; } .mp-bar select{ width:100%; }
  .mp-nav{ display:flex; align-items:center; gap:8px; margin-bottom:10px; } .mp-nav .btn{ flex:1; } .mp-pg{ display:flex; align-items:center; gap:6px; } .mp-pg input{ width:78px !important; text-align:center; padding:9px 6px !important; } .mp-pg small{ color:var(--text-dim); }
  .mp-card{ padding:18px 14px 12px; border-radius:22px; background:var(--card); } [data-theme="dark"] .mp-card, :root:not([data-theme="light"]) .mp-card{ background:var(--card); }
  .mp-meta{ display:flex; justify-content:space-between; font-size:12px; color:var(--text-dim); margin-bottom:10px; border-bottom:1px solid var(--border); padding-bottom:8px; }
  .mp-text{ direction:rtl; text-align:justify; text-align-last:center; font-family:var(--ar-font,'Amiri'),serif; font-size:calc(27px * var(--ar-scale,1)); line-height:2.35; color:var(--text); word-spacing:2px; }
  .mp-ay{ cursor:pointer; border-radius:8px; transition:background .2s; } .mp-ay:active,.mp-ay.sel{ background:rgba(47,191,113,.22); } .mp-n{ color:var(--accent-bright); font-size:.8em; }
  .mp-sh{ margin:14px 0 6px; padding:10px; text-align:center; border:1px solid var(--accent); border-radius:14px; background:rgba(47,191,113,.10); } .mp-sh b{ display:block; font-family:'Amiri',serif; font-size:26px; color:var(--accent-bright); } .mp-sh small{ color:var(--text-dim); font-size:12px; }
  .mp-bs{ text-align:center; font-family:'Amiri',serif; font-size:calc(26px * var(--ar-scale,1)); margin:6px 0 4px; color:var(--text); } .mp-foot{ text-align:center; color:var(--text-dim); margin-top:8px; font-family:'Amiri',serif; font-size:18px; }
  .mp-trs p{ font-size:calc(14px * var(--tr-scale,1)); line-height:1.65; margin:0 0 10px; color:var(--text-dim); } .mp-trs b{ color:var(--accent-bright); font-family:'IBM Plex Mono',monospace; font-size:12px; margin-right:6px; }
  .rc-text{ direction:rtl; text-align:justify; font-family:var(--ar-font,'Amiri'),serif; font-size:calc(30px * var(--ar-scale,1)); line-height:2.4; padding:6px 2px; min-height:120px; }
  .rw{ display:inline-block; padding:0 3px; border-radius:8px; transition:color .2s, background .2s, filter .25s; cursor:pointer; } .rw.pend{ color:var(--text-dim); } .rw.cur{ color:var(--text); background:rgba(47,191,113,.22); }
  .rw.ok{ color:#35c76f; } .rw.wrong{ color:#ff5a4a; background:rgba(255,90,74,.14); } .rw.miss{ color:#ffb020; background:rgba(255,176,32,.14); text-decoration:underline dotted; } .rw.hid{ filter:blur(9px); }
  .rw-end{ color:var(--accent-bright); font-size:.8em; } .rc-heard{ font-size:12.5px; color:var(--text-dim); direction:rtl; text-align:right; min-height:20px; margin:4px 0 10px; border-top:1px dashed var(--border); padding-top:8px; }
  .rc-top{ display:flex; justify-content:space-between; align-items:center; margin-bottom:6px; } #rcState.on{ background:var(--accent); color:var(--on-accent); border-color:var(--accent); animation:pulse 1.2s infinite; } @keyframes pulse{ 50%{ opacity:.6; } }
  .rc-ctl{ display:flex; align-items:center; justify-content:space-between; gap:10px; margin:6px 0 10px; } .rc-ctl .btn{ flex:1; }
  .rc-mic{ width:88px; height:88px; border-radius:50%; border:none; background:var(--accent); color:var(--on-accent); font-size:30px; display:flex; flex-direction:column; align-items:center; justify-content:center; box-shadow:0 8px 24px rgba(47,191,113,.45); flex:0 0 auto; } .rc-mic small{ font-size:12px; font-weight:700; } .rc-mic.on{ background:#D64545; animation:pulse 1.2s infinite; }
  .rc-sum{ display:flex; justify-content:space-around; text-align:center; margin:6px 0; } .rc-sum b{ display:block; font:800 28px 'Onest',sans-serif; } .rc-sum small{ color:var(--text-dim); } .rc-bad{ padding:8px 10px; margin:6px 0; border:1px solid var(--border); border-radius:14px; cursor:pointer; }
  .hf-grid{ display:grid; grid-template-columns:repeat(8,1fr); gap:5px; } .hf-c{ aspect-ratio:1; border-radius:8px; border:1px solid var(--border); font:600 12px 'IBM Plex Mono',monospace; color:var(--text); background:var(--accent-soft); padding:0; }
  .ac-grid{ grid-template-columns:repeat(3,1fr); }
  `;

  function init() {
    const st = document.createElement('style'); st.textContent = CSS; document.head.appendChild(st);
    const prev = window.setTab;
    window.setTab = function (t) { if (rc.page && rc.live && t !== 'mushaf') { rc.live = false; stopEngine(); hideRecBar(); setFab(false); rc.page = false; } if (!document.getElementById('tab-' + t) && R2[t]) makeTab(t, { mushaf: 'Mushaf', recite: 'Recite & check', hifz: 'Hifz tracker' }[t]); prev(t); if (t !== 'mushaf' && t !== 'recite' && typeof renderHeaderDates === 'function') { try { renderHeaderDates(); } catch (e) {} } if (R2[t]) { try { R2[t](); } catch (e) { console.error(e); } } updateFab(); };
    Object.assign(TAB_TITLES, { mushaf: 'Mushaf', recite: 'Recite & check', hifz: 'Hifz tracker' });
    addEntryPoints(); updateFab(); const qm = $('#quranModal'); if (qm) new MutationObserver(updateFab).observe(qm, { attributes: true, attributeFilter: ['class'] });
    // home: show revision due
    const oh = SKX.homeExtras; SKX.homeExtras = async function () { await oh(); const box = $('#homeExtras'); if (!box) return; const segs = hifzSegments(); if (segs.length) { const d = document.createElement('div'); d.className = 'card hx-card'; d.style.cursor = 'pointer'; d.innerHTML = `<div class="hx-h">🧠 Hifz revision due</div><div class="sk-p">${segs.length} passage${segs.length > 1 ? 's' : ''} to revise — ${esc(sName(segs[0].s))} ${segs[0].s}:${segs[0].from}${segs[0].to > segs[0].from ? '–' + segs[0].to : ''} and more</div>`; d.onclick = () => setTab('hifz'); box.appendChild(d); } };
  }
  Object.assign(window.SKX, { hm: () => HM(), setAb, updateFab, togglePageRecite, jumpSheet, align, nrm, openMushafAt, reciteFrom, actionSheet, hifzAdd, hifzSegments, rcState: rc });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
