/* Cyber Tazkiah UI — vanilla JS, no build step. Talks to Android through window.AndroidNative. */
(function () {
  'use strict';

  var NB = window.AndroidNative || null;
  function call(name) {
    var args = Array.prototype.slice.call(arguments, 1);
    try { return NB && NB[name] ? NB[name].apply(NB, args) : null; } catch (e) { return null; }
  }
  function json(name, fallback) {
    var r = call(name);
    try { return r ? JSON.parse(r) : fallback; } catch (e) { return fallback; }
  }

  var S = { status: {}, set: {}, stats: {}, rem: [], filter: 'all', open: {}, apps: null, tab: 'home', cur: null };
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var esc = function (t) { return String(t == null ? '' : t).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); };

  var ICON = {
    eye: '<path d="M2 12s3.600-6.500 10-6.500S22 12 22 12s-3.600 6.500-10 6.500S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"/>',
    bell: '<path d="M6 17V11a6 6 0 0 1 12 0v6l1.500 2h-15zM10 21h4"/>',
    lock: '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    bolt: '<path d="M13 3 5 14h6l-1 7 8-11h-6z"/>',
    chev: '<path d="m6 9 6 6 6-6"/>'
  };
  function ico(n) { return '<svg viewBox="0 0 24 24">' + ICON[n] + '</svg>'; }

  /* ------------------------------------------------------------ data */
  function load() {
    S.status = json('getStatus', {}) || {};
    S.set = json('getSettings', { protection: true, sensitivity: 'hyper', lang: 'both', guard: false, visual: true, daily: true, dailyHour: 21, dailyMin: 0, custom: '', trusted: [] }) || {};
    S.stats = json('getStats', { today: 0, total: 0, streak: 0, istighfar: 0, week: [], cats: {} }) || {};
    if (!S.rem.length) {
      var r = json('getReminders', { items: [] });
      S.rem = (r && r.items) || [];
    }
  }
  function pick(hope) {
    var pool = S.rem.filter(function (r) { return (r.cats.indexOf('tawbah') >= 0) === !!hope && (!S.cur || r.id !== S.cur.id); });
    if (!pool.length) pool = S.rem;
    return pool[Math.floor(Math.random() * pool.length)];
  }
  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  /* ------------------------------------------------------------ pieces */
  function remBody(r, lang) {
    var ur = lang !== 'en', en = lang !== 'ur';
    return '<div class="ar selectable">' + esc(r.ar) + '</div>' +
      (ur ? '<div class="ur tr selectable">' + esc(r.ur) + '</div>' : '') +
      (en ? '<div class="en tr selectable">' + esc(r.en) + '</div>' : '') +
      '<div class="ref">' + esc(r.ref) + (r.grade && r.grade !== "Qur'an" ? ' · ' + esc(r.grade) : '') + '</div>';
  }
  function hookOf(r, lang) {
    return lang === 'en' ? '<div class="hook en">' + esc(r.hook_en) + '</div>'
      : '<div class="hook ur">' + esc(r.hook_ur) + '</div>' + (lang === 'both' ? '<div class="en tr" style="margin-top:0">' + esc(r.hook_en) + '</div>' : '');
  }
  function shareText(r) { return r.ar + '\n\n' + r.ur + '\n' + r.en + '\n— ' + r.ref + '\n\nCyber Tazkiah · Cyber Hayat PK'; }

  function ring(done, total) {
    var R = 40, C = 2 * Math.PI * R, off = C * (1 - done / total);
    var col = done >= 2 ? 'var(--sage)' : 'var(--coral)';
    return '<div class="ring"><svg width="96" height="96" viewBox="0 0 96 96"><circle cx="48" cy="48" r="' + R + '" fill="none" stroke="#F6E2DA" stroke-width="9"/>' +
      '<circle cx="48" cy="48" r="' + R + '" fill="none" stroke="' + col + '" stroke-width="9" stroke-linecap="round" stroke-dasharray="' + C.toFixed(1) + '" stroke-dashoffset="' + off.toFixed(1) + '"/></svg>' +
      '<div class="num"><div>' + done + '/' + total + '<small>layers</small></div></div></div>';
  }

  function layers() {
    var st = S.status;
    return [
      { id: 'acc', ic: 'eye', on: !!st.accessibility, core: true, t: 'Guard (Accessibility)', d: 'Notices adult searches, links and on-screen content, then shows the reminder.', b: 'Enable', act: 'acc' },
      { id: 'dns', ic: 'globe', on: !!st.dns, core: true, t: 'DNS Shield', d: 'Blocks adult websites in every browser and app. Only DNS lookups pass through it.', b: st.dns ? 'Turn off' : 'Turn on', act: st.dns ? 'dns-off' : 'dns-on' },
      { id: 'not', ic: 'bell', on: !!st.notifications, t: 'Daily reminder', d: 'One ayah or hadith a day at the time you choose.', b: 'Allow', act: 'notif' },
      { id: 'adm', ic: 'lock', on: !!st.admin, t: 'Uninstall protection', d: 'Makes removal harder in a weak moment (device admin, no extra rights).', b: st.admin ? 'Manage' : 'Activate', act: 'admin' },
      { id: 'bat', ic: 'bolt', on: !!st.battery, t: 'Keep running', d: 'Stops the phone from putting the guard to sleep.', b: 'Allow', act: 'battery' }
    ];
  }

  /* ------------------------------------------------------------ tabs */
  function renderHome() {
    var L = layers(), done = L.filter(function (x) { return x.on; }).length;
    var core = L.filter(function (x) { return x.core && x.on; }).length;
    var label = core === 2 && S.set.protection ? ['Fully protected', 'ok'] : core >= 1 ? ['Partly protected', 'warn'] : ['Not protected yet', ''];
    if (!S.set.protection) label = ['Protection paused', 'warn'];
    var cur = S.cur || (S.cur = pick(false));
    var week = (S.stats.week || []), mx = Math.max(1, Math.max.apply(null, week.map(function (w) { return w.n; }).concat([1])));
    var h = '';
    h += '<div class="card hero">' + ring(done, L.length) + '<div class="grow"><h3>' + label[0] + '</h3><span class="pill ' + label[1] + '">' + done + ' of ' + L.length + ' layers on</span>' +
      '<p class="muted small" style="margin:8px 0 0">' + (core < 2 ? 'Turn on the Guard and the DNS Shield for full protection.' : 'Alhamdulillah. Keep your device updated and the guard on.') + '</p></div></div>';
    h += '<div class="stats"><div class="stat"><b>' + (S.stats.today || 0) + '</b><span>Blocked today</span></div><div class="stat"><b>' + (S.stats.streak || 0) + '</b><span>Days since last</span></div><div class="stat"><b>' + (S.stats.istighfar || 0) + '</b><span>Turned back</span></div></div>';
    h += '<h2>Reminder for you</h2><div class="card rem"><span class="tag">' + (cur.type === 'hadith' ? 'Hadith' : "Qur'an") + '</span>' + hookOf(cur, S.set.lang) + '<hr>' + remBody(cur, S.set.lang) +
      '<div class="actions"><button class="btn ghost sm" data-a="next">Another</button><button class="btn line sm" data-a="copy" data-id="' + esc(cur.id) + '">Copy</button><button class="btn line sm" data-a="share" data-id="' + esc(cur.id) + '">Share</button></div></div>';
    h += '<h2>Setup</h2><div class="card" style="padding:4px 16px">';
    L.forEach(function (x) {
      h += '<div class="item ' + (x.on ? 'done' : '') + '"><div class="ico">' + ico(x.ic) + '</div><div class="grow"><h4>' + x.t + '</h4><p>' + x.d + '</p></div>' +
        (x.on && x.id !== 'dns' && x.id !== 'adm' ? '<span class="pill ok">On</span>' : '<button class="btn sm ' + (x.on ? 'line' : '') + '" data-a="' + x.act + '">' + x.b + '</button>') + '</div>';
    });
    h += '</div>';
    if (!S.status.accessibility) {
      h += '<div class="note"><b>Switch greyed out?</b> Android blocks apps installed from a file at first. Open <b>App info → ⋮ (top right) → Allow restricted settings</b>, then enable the Guard again. <button class="btn sm ghost" style="margin-top:8px" data-a="appinfo">Open App info</button></div>';
    }
    h += '<h2>Last 7 days</h2><div class="card"><div class="week">' + week.map(function (w) {
      return '<div class="bar"><i class="' + (w.n ? 'hi' : '') + '" style="height:' + Math.max(4, Math.round(w.n / mx * 64)) + 'px"></i><span>' + esc(w.d) + '</span></div>';
    }).join('') + '</div><p class="muted small" style="margin:10px 0 0">Only counts are kept. The app never saves what you searched or viewed.</p></div>';
    $('#tab-home').innerHTML = h;
  }

  var FILTERS = [['all', 'All'], ['ayah', "Qur'an"], ['hadith', 'Hadith'], ['gaze', 'Gaze'], ['allah_sees', 'Allah sees'], ['hereafter', 'Hereafter'], ['shame', 'Haya'], ['fitna', 'Temptation'], ['heart', 'Heart'], ['tawbah', 'Tawbah'], ['remedy', 'Remedy']];
  function renderReminders() {
    var f = S.filter;
    var list = S.rem.filter(function (r) { return f === 'all' || r.type === f || r.cats.indexOf(f) >= 0; });
    var h = '<h2 style="margin-top:14px">Reminder library</h2><p class="muted small" style="margin:-4px 4px 8px">' + S.rem.length + ' ayat and ahadith. Tap one to read it in full.</p>';
    h += '<div class="chips">' + FILTERS.map(function (c) { return '<button class="chip ' + (c[0] === f ? 'on' : '') + '" data-a="filter" data-f="' + c[0] + '">' + c[1] + '</button>'; }).join('') + '</div>';
    list.forEach(function (r) {
      var open = !!S.open[r.id], ur = S.set.lang !== 'en';
      h += '<div class="li ' + (open ? 'open' : '') + '"><div class="head" data-a="toggle" data-id="' + esc(r.id) + '"><div class="grow"><div class="hk ' + (ur ? 'ur' : '') + '">' + esc(ur ? r.hook_ur : r.hook_en) + '</div><div class="meta">' + esc(r.ref) + '</div></div><svg class="chev" viewBox="0 0 24 24">' + ICON.chev + '</svg></div>' +
        '<div class="body">' + remBody(r, S.set.lang) + '<div class="actions"><button class="btn sm" data-a="preview" data-id="' + esc(r.id) + '">Preview block screen</button><button class="btn line sm" data-a="copy" data-id="' + esc(r.id) + '">Copy</button><button class="btn line sm" data-a="share" data-id="' + esc(r.id) + '">Share</button></div></div></div>';
    });
    if (!list.length) h += '<div class="card muted">Nothing here yet.</div>';
    $('#tab-reminders').innerHTML = h;
  }

  var SENS = { hyper: 'Maximum. Reacts instantly to any haram word, link or exposed-body image. May interrupt more; drop to Balanced if it does.', gentle: 'Only clear links and searches trigger the screen.', balanced: 'Recommended. Clear searches, links and strongly explicit pages.', strict: 'Also reacts to milder words. May interrupt more often.' };
  function sw(on, a) { return '<button class="sw ' + (on ? 'on' : '') + '" data-a="' + a + '" aria-pressed="' + (on ? 'true' : 'false') + '"></button>'; }
  function renderShield() {
    var s = S.set, nTrusted = (s.trusted || []).length;
    var h = '<h2 style="margin-top:14px">Shield settings</h2><div class="card"><div class="set"><div class="t"><h4>Protection</h4><p>Master switch for the Guard and DNS Shield.</p></div>' + sw(s.protection, 'tog-protection') + '</div>';
    h += '<div style="margin-top:6px"><h4 style="margin:12px 0 0;font:600 14.5px var(--f-ui)">Sensitivity</h4><div class="seg">' + ['hyper', 'strict', 'balanced', 'gentle'].map(function (k) { return '<button class="' + (s.sensitivity === k ? 'on' : '') + '" data-a="sens" data-v="' + k + '">' + k[0].toUpperCase() + k.slice(1) + '</button>'; }).join('') + '</div><p class="muted small" style="margin:8px 2px 0">' + SENS[s.sensitivity] + '</p></div></div>';
    var vis = (S.status.sdk || 30) >= 30;
    h += '<div class="card"><div class="set"><div class="t"><h4>Image guard</h4><p>' + (vis ? 'Looks at what is on screen (photos, reels, videos) with a small on-device model and blocks exposed bodies. Nothing is saved or sent.' : 'Needs Android 11 or newer. Not available on this phone.') + '</p></div>' + (vis ? sw(s.visual, 'tog-visual') : '') + '</div></div>';
    h += '<div class="card" id="diag"><h4 style="margin:0 0 6px;font:600 14.5px var(--f-ui)">Live status</h4><div id="diagBody" class="muted small">Loading…</div><div class="actions" style="justify-content:flex-start"><button class="btn ghost sm" data-a="diag">Refresh</button><button class="btn sm" data-a="selftest">Run self-test</button></div></div>';
    h += '<div class="card"><div class="set"><div class="t"><h4>Think-twice guard</h4><p>If you try to switch the app off or uninstall it, a reminder and a 20-second pause appear first.</p></div>' + sw(s.guard, 'tog-guard') + '</div></div>';
    h += '<div class="card"><div class="row"><div class="grow"><h4 style="margin:0;font:600 14.5px var(--f-ui)">Trusted apps</h4><p class="muted small" style="margin:2px 0 0">' + (nTrusted ? nTrusted + ' app' + (nTrusted > 1 ? 's' : '') + ' are never scanned (e.g. a Qur\'an or study app).' : 'Pick apps that should never be scanned, like a Qur\'an or study app.') + '</p></div><button class="btn ghost sm" data-a="apps">Choose</button></div></div>';
    h += '<div class="card"><h4 style="margin:0 0 6px;font:600 14.5px var(--f-ui)">Your own blocked words</h4><textarea id="custom" class="selectable" placeholder="One word or phrase per line">' + esc(s.custom || '') + '</textarea><div class="actions" style="justify-content:flex-end"><button class="btn sm" data-a="save-custom">Save words</button></div></div>';
    h += '<h2>Make it stronger</h2><div class="card"><ul class="ul" style="margin:0;padding-left:18px">' +
      '<li><b>Always-on VPN.</b> In VPN settings, tap the gear next to Cyber Tazkiah and switch on <i>Always-on VPN</i> so the DNS Shield restarts by itself.</li>' +
      '<li><b>Private DNS.</b> Settings → Network → Private DNS → <code>family.cloudflare-dns.com</code> adds a second filter that works even if the app is off.</li>' +
      '<li><b>Browser “Secure DNS”.</b> Turn it off in Chrome so lookups use the Shield.</li></ul>' +
      '<div class="actions" style="justify-content:flex-start"><button class="btn line sm" data-a="vpnsettings">VPN settings</button><button class="btn line sm" data-a="copydns">Copy DNS name</button></div></div>';
    $('#tab-shield').innerHTML = h;
  }

  function renderMore() {
    var s = S.set, st = S.status, c = S.stats.cats || {};
    var h = '<h2 style="margin-top:14px">Settings</h2><div class="card"><h4 style="margin:0;font:600 14.5px var(--f-ui)">Reminder language</h4><div class="seg">' +
      [['both', 'Urdu + English'], ['ur', 'Urdu'], ['en', 'English']].map(function (x) { return '<button class="' + (s.lang === x[0] ? 'on' : '') + '" data-a="lang" data-v="' + x[0] + '">' + x[1] + '</button>'; }).join('') + '</div>' +
      '<div class="set" style="margin-top:12px"><div class="t"><h4>Daily reminder</h4><p>A notification with one ayah or hadith.</p></div>' + sw(s.daily, 'tog-daily') + '</div>' +
      '<div class="set"><div class="t"><h4>Time</h4></div><input type="time" id="dtime" value="' + pad2(s.dailyHour || 0) + ':' + pad2(s.dailyMin || 0) + '"></div></div>';
    h += '<h2>Where blocks came from</h2><div class="card"><div class="stats" style="margin:0;grid-template-columns:repeat(5,1fr)">' +
      [['browser', 'Sites'], ['search', 'Searches'], ['screen', 'Text'], ['visual', 'Images'], ['dns', 'DNS']].map(function (x) { return '<div class="stat" style="padding:10px 4px"><b style="font-size:19px">' + (c[x[0]] || 0) + '</b><span>' + x[1] + '</span></div>'; }).join('') +
      '</div><div class="actions" style="justify-content:flex-start"><button class="btn line sm" data-a="reset">Reset counters</button></div></div>';
    h += '<h2>How it works &amp; privacy</h2><div class="card"><ul class="ul" style="margin-top:0">' +
      '<li><b>Guard:</b> reads on-screen text on your phone to score it against a keyword list. The text is not saved or sent anywhere. Password fields are skipped.</li><li><b>Image guard:</b> a small AI model on your phone checks screenshots for exposed body parts. Pictures are never stored or sent. It cannot catch everything, such as clothed but suggestive clips.</li>' +
      '<li><b>DNS Shield:</b> a local filter that sees website <i>names</i> only (never pages, passwords or messages). Unblocked names go to Cloudflare Family DNS (1.1.1.3). Nothing is logged.</li>' +
      '<li><b>Stored on your phone:</b> your settings and counters. No account, no analytics, no ads.</li>' +
      '<li><b>No filter is perfect.</b> Pair it with du\'a, good company and fixed screen habits.</li></ul></div>';
    h += '<div class="card" style="text-align:center"><div class="bism">بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ</div><p class="muted small" style="margin:4px 0 0">Cyber Tazkiah ' + esc(st.version || '1.0') + ' · by Cyber Hayat PK<br>Safer internet · Better lives</p></div>';
    $('#tab-more').innerHTML = h;
  }

  function render() {
    renderHome(); renderReminders(); renderShield(); renderMore();
  }

  /* ------------------------------------------------------------ sheets */
  var sheetOpen = false;
  function openSheet(html) { $('#sheet-body').innerHTML = html; $('#sheet').classList.add('on'); $('#scrim').classList.add('on'); sheetOpen = true; $('#sheet').scrollTop = 0; }
  function closeSheet() { $('#sheet').classList.remove('on'); $('#scrim').classList.remove('on'); sheetOpen = false; }

  function welcome() {
    openSheet('<div class="bism">بِسْمِ اللَّهِ</div><h3 style="text-align:center">Welcome to Cyber Tazkiah</h3>' +
      '<p class="muted" style="text-align:center;margin:4px 0 12px">Tazkiah means purifying the self. This app helps you guard your eyes and heart.</p>' +
      '<div class="card" style="margin-top:6px"><ul class="ul" style="margin:0;padding-left:18px;color:var(--ink)">' +
      '<li><b>Guard</b> watches for adult searches and pages, then covers the screen with an ayah or hadith.</li>' +
      '<li><b>DNS Shield</b> blocks adult websites in every app.</li>' +
      '<li>Everything stays on your phone. Nothing you view is stored or sent.</li></ul></div>' +
      '<button class="btn block" style="margin-top:14px" data-a="begin">Begin setup</button>');
  }

  function previewHtml(r) {
    var lang = S.set.lang;
    return '<h3>Block screen preview</h3><p class="muted small" style="margin:0 0 6px">This is what appears when something haram is detected.</p><div class="pv"><div class="big">Pause — Allah is watching</div>' +
      (lang !== 'en' ? '<div class="ur" style="text-align:center;font-size:17px;line-height:2.1">رک جائیے — اللہ دیکھ رہا ہے</div>' : '') +
      '<hr>' + hookOf(r, lang) + '<hr>' + remBody(r, lang) + '</div><button class="btn block" style="margin-top:12px" data-a="close">Close</button>';
  }

  function appsSheet() {
    if (!S.apps) S.apps = json('getApps', []) || [];
    var trusted = S.set.trusted || [];
    var h = '<h3>Trusted apps</h3><p class="muted small" style="margin:0 0 8px">Checked apps are never scanned by the Guard. Only trust apps you are sure about.</p><input type="search" id="appq" placeholder="Search apps"><div class="apps" id="applist">';
    S.apps.forEach(function (a) {
      h += '<label class="app" data-n="' + esc(a.n.toLowerCase()) + '"><div class="nm">' + esc(a.n) + '<small>' + esc(a.p) + '</small></div><button class="sw ' + (trusted.indexOf(a.p) >= 0 ? 'on' : '') + '" data-a="trust" data-p="' + esc(a.p) + '"></button></label>';
    });
    if (!S.apps.length) h += '<p class="muted">No apps found.</p>';
    h += '</div><button class="btn block" style="margin-top:12px" data-a="close">Done</button>';
    openSheet(h);
    $('#appq').addEventListener('input', function (e) {
      var q = e.target.value.toLowerCase();
      document.querySelectorAll('#applist .app').forEach(function (el) { el.style.display = el.getAttribute('data-n').indexOf(q) >= 0 ? '' : 'none'; });
    });
  }

  /* ------------------------------------------------------------ actions */
  var toastT;
  function toast(t) { var el = $('#toast'); el.textContent = t; el.classList.add('on'); clearTimeout(toastT); toastT = setTimeout(function () { el.classList.remove('on'); }, 1800); }
  function setting(k, v) { call('setSetting', k, String(v)); }
  function byId(id) { return S.rem.filter(function (r) { return r.id === id; })[0]; }
  function refresh() { load(); render(); }

  function act(a, el) {
    var id = el.getAttribute('data-id'), r = id ? byId(id) : null;
    switch (a) {
      case 'acc': call('openAccessibility'); break;
      case 'dns-on': call('startDns'); break;
      case 'dns-off': call('stopDns'); break;
      case 'notif': call('requestNotifications'); break;
      case 'admin': call('requestAdmin'); break;
      case 'battery': call('requestBattery'); break;
      case 'appinfo': call('openAppSettings'); break;
      case 'vpnsettings': call('openVpnSettings'); break;
      case 'copydns': call('copy', 'family.cloudflare-dns.com'); if (!NB) toast('Copied'); break;
      case 'next': S.cur = pick(false); renderHome(); break;
      case 'copy': if (r) { call('copy', shareText(r)); if (!NB) toast('Copied'); } break;
      case 'share': if (r) call('share', shareText(r)); break;
      case 'filter': S.filter = el.getAttribute('data-f'); renderReminders(); break;
      case 'toggle': S.open[id] = !S.open[id]; renderReminders(); break;
      case 'preview':
        if (r && call('previewOverlay', r.id) !== true) openSheet(previewHtml(r));
        break;
      case 'tog-protection': S.set.protection = !S.set.protection; setting('protection', S.set.protection ? 1 : 0); render(); break;
      case 'tog-visual': S.set.visual = !S.set.visual; setting('visual', S.set.visual ? 1 : 0); render(); break;
      case 'tog-guard': S.set.guard = !S.set.guard; setting('guard', S.set.guard ? 1 : 0); render(); break;
      case 'tog-daily': S.set.daily = !S.set.daily; setting('daily', S.set.daily ? 1 : 0); render(); break;
      case 'sens': S.set.sensitivity = el.getAttribute('data-v'); setting('sensitivity', S.set.sensitivity); renderShield(); break;
      case 'lang': S.set.lang = el.getAttribute('data-v'); setting('lang', S.set.lang); render(); break;
      case 'save-custom': setting('custom', $('#custom').value); S.set.custom = $('#custom').value; toast('Saved'); break;
      case 'apps': appsSheet(); break;
      case 'diag': diag(); break;
      case 'selftest': selfTest(); break;
      case 'trust':
        var p = el.getAttribute('data-p'), t = S.set.trusted || [], i = t.indexOf(p);
        if (i >= 0) t.splice(i, 1); else t.push(p);
        S.set.trusted = t; setting('trusted', JSON.stringify(t)); el.classList.toggle('on'); renderShield(); break;
      case 'reset': call('resetStats'); refresh(); toast('Counters reset'); break;
      case 'begin': closeSheet(); try { localStorage.setItem('tz_welcomed', '1'); } catch (e) { } window.scrollTo(0, 0); break;
      case 'close': closeSheet(); break;
    }
  }

  document.addEventListener('click', function (e) {
    var el = e.target.closest('[data-a]');
    if (el) { act(el.getAttribute('data-a'), el); return; }
    var nb = e.target.closest('#nav button');
    if (nb) setTab(nb.getAttribute('data-tab'));
    if (e.target.id === 'scrim') closeSheet();
  });
  document.addEventListener('change', function (e) {
    if (e.target.id === 'dtime' && e.target.value) { setting('dailyTime', e.target.value); var p = e.target.value.split(':'); S.set.dailyHour = +p[0]; S.set.dailyMin = +p[1]; toast('Reminder time saved'); }
  });

  function ago(v) { return v < 0 ? 'never' : v + 's ago'; }
  function diag() {
    var b = $('#diagBody'); if (!b) return;
    var d = json('getDiag', null);
    if (!d) { b.textContent = 'Live status is available inside the app.'; return; }
    var vis = d.sdk < 30 ? 'needs Android 11+' : d.visState + (d.visScans ? ' · ' + d.visScans + ' looks, last risk ' + d.visScore + '%' : '');
    var hint = '';
    if (d.shotError === 2) hint = ' (no accessibility access: turn the Guard off and on again)';
    if (d.shotError === 3) hint = ' (too frequent, will retry)';
    b.innerHTML = '<div>Guard: <b>' + (d.connected ? 'connected' : 'NOT running — enable it in Accessibility') + '</b></div>' +
      '<div>Last event: ' + ago(d.eventAgo) + ' · ' + d.events + ' events</div>' +
      '<div>Text scans: ' + d.scans + ' (last ' + ago(d.scanAgo) + ', score ' + d.scanScore + ')</div>' +
      '<div>Image guard: ' + esc(vis) + esc(hint) + '</div>' +
      (d.modelError ? '<div>Model error: ' + esc(d.modelError) + '</div>' : '') +
      '<div>Blocks shown: ' + d.triggers + '</div>';
  }
  function selfTest() {
    var b = $('#diagBody'); if (b) b.textContent = 'Running self-test…';
    setTimeout(function () {
      var r = json('selfTest', null);
      if (!r) { toast('Self-test only runs inside the app'); return; }
      if (b) b.innerHTML = '<div>Text engine: ' + (r.textTyped && r.textScreen && r.textClean ? 'OK' : 'PROBLEM') + '</div>' +
        '<div>Image model: ' + (r.modelLoaded ? 'loaded in ' + r.loadMs + ' ms, ' + r.inferMs + ' ms per look' : 'FAILED — ' + esc(r.error || '')) + '</div>' +
        '<div>Guard service: ' + (r.serviceConnected ? 'connected' : 'not connected') + '</div>';
    }, 50);
  }
  setInterval(function () { if (S.tab === 'shield') diag(); }, 2000);

  function setTab(t) {
    S.tab = t;
    if (t === 'shield') setTimeout(diag, 50);
    document.querySelectorAll('.tab').forEach(function (x) { x.classList.toggle('on', x.id === 'tab-' + t); });
    document.querySelectorAll('#nav button').forEach(function (x) { x.classList.toggle('on', x.getAttribute('data-tab') === t); });
    window.scrollTo(0, 0);
  }

  window.TZ = {
    refresh: function () { load(); render(); },
    back: function () {
      if (sheetOpen) { closeSheet(); return true; }
      if (S.tab !== 'home') { setTab('home'); return true; }
      return false;
    },
    _tab: setTab, _welcome: welcome
  };

  load(); render();
  var seen = false; try { seen = localStorage.getItem('tz_welcomed') === '1'; } catch (e) { }
  if (!seen) welcome();
})();
