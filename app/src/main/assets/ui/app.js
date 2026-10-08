/* Graysons Wallet + Frostoise + MyFrost UI. Talks to the Frostchain node through
   window.FrostBridge on Android, or POST /wallet/api on desktop. No external libraries. */
(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var qsa = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

  // ---------------- transport ----------------
  var seq = 0, waiting = {};
  var token = (location.search.match(/[?&]t=([0-9a-f]+)/) || [])[1] || '';
  window.__frostReply = function (id, json) {
    var w = waiting[id]; if (!w) return;
    delete waiting[id];
    var r; try { r = JSON.parse(json); } catch (e) { r = { error: 'bad reply' }; }
    if (r.error) w.reject(new Error(r.error)); else w.resolve(r.result);
  };
  function call(method, params) {
    if (window.FrostBridge) {
      return new Promise(function (resolve, reject) {
        var id = String(++seq);
        waiting[id] = { resolve: resolve, reject: reject };
        window.FrostBridge.call(id, method, JSON.stringify(params || {}));
      });
    }
    return fetch('/wallet/api', {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Frost-Token': token },
      body: JSON.stringify({ method: method, params: params || {} })
    }).then(function (r) { return r.json(); }).then(function (r) {
      if (r.error) throw new Error(r.error);
      return r.result;
    });
  }

  // ---------------- helpers ----------------
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function msg(el, text, kind) { el = typeof el === 'string' ? $(el) : el; el.className = 'msg' + (kind ? ' ' + kind : ''); el.textContent = text || ''; }
  function busy(btn, on, label) {
    if (on) { btn.dataset.label = btn.textContent; btn.disabled = true; btn.innerHTML = '<span class="spin"></span> ' + esc(label || 'Working…'); }
    else { btn.disabled = false; if (btn.dataset.label) btn.textContent = btn.dataset.label; }
  }
  var toastTimer;
  function toast(t) { var el = $('toast'); el.textContent = t; el.classList.remove('hidden'); clearTimeout(toastTimer); toastTimer = setTimeout(function () { el.classList.add('hidden'); }, 2200); }
  function copy(text) {
    if (window.FrostBridge && window.FrostBridge.copy) { window.FrostBridge.copy(text); toast('Copied'); return; }
    if (navigator.clipboard) navigator.clipboard.writeText(text).then(function () { toast('Copied'); }, function () { toast(text); });
    else toast(text);
  }
  function share(text) {
    if (window.FrostBridge && window.FrostBridge.share) window.FrostBridge.share(text);
    else if (navigator.share) navigator.share({ text: text }).catch(function () {});
    else copy(text);
  }
  function openUrl(u) {
    if (window.FrostBridge && window.FrostBridge.openUrl) window.FrostBridge.openUrl(u);
    else window.open(u, '_blank', 'noopener');
  }
  var RAW_RE = /^fc[a-z2-7]{36}(\.frostchain)?$/;
  /** "grayson.frostchain" -> "@grayson"; a raw fc… address is shortened for display only. */
  function atName(t) {
    t = String(t || '');
    if (RAW_RE.test(t)) return t.slice(0, 8) + '…' + t.slice(34, 38);
    return '@' + t.replace(/\.frostchain$/, '');
  }
  /** For confirmations: @name, or the full raw address (never shortened). */
  function who(t) { return RAW_RE.test(t || '') ? t : atName(t); }
  /** "1,000.50" -> "1000.5"; null if it isn't a positive QOIN amount (max 11 decimals). */
  function cleanAmount(v) {
    var t = String(v == null ? '' : v).replace(/[\s,_]/g, '');
    if (!/^(\d+(\.\d{0,11})?|\.\d{1,11})$/.test(t)) return null;
    var p = t.split('.'), w = (p[0] || '0').replace(/^0+(?=\d)/, ''), f = (p[1] || '').replace(/0+$/, '');
    if (!/[1-9]/.test(w + f)) return null;
    return w + (f ? '.' + f : '');
  }
  /** "frostchain:grayson.frostchain?amount=25&memo=lunch" -> {to, amount, memo}; null if not a link. */
  function parsePay(text) {
    var m = /^frostchain:(?:\/\/)?([^?#\s]+)(?:\?([^#\s]*))?$/i.exec(String(text || '').trim());
    if (!m) return null;
    try {
      var out = { to: decodeURIComponent(m[1]), amount: '', memo: '' };
      (m[2] || '').split('&').forEach(function (kv) {
        var i = kv.indexOf('='); if (i < 1) return;
        var k = kv.slice(0, i).toLowerCase(), v = decodeURIComponent(kv.slice(i + 1).replace(/\+/g, ' '));
        if (k === 'amount') out.amount = cleanAmount(v) || '';
        else if (k === 'memo' || k === 'note' || k === 'message') out.memo = v.slice(0, 64);
      });
      return out;
    } catch (e) { return null; }
  }
  function ago(t) {
    var s = Math.max(0, Math.floor(Date.now() / 1000) - t);
    if (s < 60) return 'just now';
    if (s < 3600) return Math.floor(s / 60) + ' min ago';
    if (s < 86400) return Math.floor(s / 3600) + ' h ago';
    return new Date(t * 1000).toLocaleDateString();
  }
  function rate(h) {
    if (h >= 1e6) return (h / 1e6).toFixed(2) + ' MH/s';
    if (h >= 1e3) return (h / 1e3).toFixed(1) + ' kH/s';
    return Math.round(h) + ' H/s';
  }
  function dur(s) {
    if (s < 0) return '–';
    if (s < 90) return Math.round(s) + ' s';
    if (s < 5400) return Math.round(s / 60) + ' min';
    if (s < 172800) return (s / 3600).toFixed(1) + ' h';
    return Math.round(s / 86400) + ' days';
  }
  /** "263.85800276661" -> "263.858002" (display only; never used for amounts sent) */
  function short(t) {
    var p = String(t).split('.');
    if (!p[1] || p[1].length <= 6) return t;
    var f = p[1].slice(0, 6).replace(/0+$/, '');
    return p[0] + (f ? '.' + f : '');
  }
  /** locked: can't be dismissed by tapping outside or Back (the fresh 25-word screen). */
  function modal(html, locked) { $('modal-body').innerHTML = html; $('modal').dataset.locked = locked ? '1' : ''; $('modal').classList.remove('hidden'); }
  function closeModal() { $('modal').classList.add('hidden'); $('modal').dataset.locked = ''; $('modal-body').innerHTML = ''; }
  $('modal').addEventListener('click', function (e) { if (e.target.id === 'modal' && !$('modal').dataset.locked) closeModal(); });

  // ---------------- state ----------------
  var S = { status: null, wallet: null, page: 'home', lastHeight: -1, sensor: 'mag', review: null, cores: 4 };

  // ---------------- routing: one page, three apps ----------------
  var TITLES = { wallet: 'Graysons Wallet', frostoise: 'Frostoise', myfrost: 'MyFrost' };
  function mode() {
    var h = location.hash || '#wallet';
    return h.indexOf('#frostoise') === 0 ? 'frostoise' : h.indexOf('#myfrost') === 0 ? 'myfrost' : 'wallet';
  }
  function route() {
    var m = mode();
    document.title = TITLES[m];
    if (window.FrostBridge && window.FrostBridge.mode) window.FrostBridge.mode(m);
    show(m);
    if (m === 'myfrost') mfFromHash();
  }
  function show(m) {
    var open = S.wallet && S.wallet.open;
    $('frostoise').classList.toggle('hidden', m !== 'frostoise');
    $('wallet').classList.toggle('hidden', m !== 'wallet' || !open);
    $('gate').classList.toggle('hidden', m !== 'wallet' || !!open);
    $('myfrost').classList.toggle('hidden', m !== 'myfrost');
    if (m === 'wallet' && !open) loadWalletList();
    if (m === 'myfrost') mfShow();
  }
  /** Switch app. On Android each app is its own launcher icon and task, so open that one. */
  function goApp(target) {
    var m = target.replace(/^#/, '').split('?')[0];
    if (window.FrostBridge && window.FrostBridge.openApp && m !== mode()) { window.FrostBridge.openApp(m); return; }
    location.hash = target;
  }
  window.addEventListener('hashchange', route);
  qsa('[data-goto]').forEach(function (b) { b.addEventListener('click', function () { goApp(b.dataset.goto); }); });
  document.addEventListener('click', function (e) {
    var a = e.target.closest('a[href^="#"]');
    if (a && window.FrostBridge && window.FrostBridge.openApp) { e.preventDefault(); goApp(a.getAttribute('href')); }
  });

  function go(page) {
    S.page = page;
    qsa('[data-page]').forEach(function (p) { p.classList.toggle('hidden', p.dataset.page !== page); });
    qsa('#wallet .tabbar a').forEach(function (a) { a.classList.toggle('on', a.dataset.go === page); });
    window.scrollTo(0, 0);
    if (page === 'history') loadHistory();
    if (page === 'receive') drawReceive();
    if (page === 'more') loadMore();
    if (page === 'node') loadNode();
    if (page === 'send') { $('review').classList.add('hidden'); $('f-send').classList.remove('hidden'); }
  }
  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-go]');
    if (t) { e.preventDefault(); go(t.dataset.go); }
  });

  // ---------------- gate ----------------
  qsa('[data-gate]').forEach(function (b) {
    b.addEventListener('click', function () {
      qsa('[data-gate]').forEach(function (x) { x.classList.toggle('on', x === b); });
      qsa('[data-pane]').forEach(function (p) { p.classList.toggle('hidden', p.dataset.pane !== b.dataset.gate); });
      msg('gate-msg', '');
    });
  });
  function loadWalletList() {
    call('wallets.list').then(function (list) {
      var sel = $('o-file');
      sel.innerHTML = list.map(function (w) {
        return '<option value="' + esc(w.file) + '">' + esc(w.label) + (w.name ? ' · ' + esc(w.name) : '') + (w.watch ? ' (watch-only)' : '') + '</option>';
      }).join('');
      $('o-none').classList.toggle('hidden', list.length > 0);
      $('f-open').querySelector('button').disabled = list.length === 0;
      if (!list.length && !$('f-open').classList.contains('hidden')) {
        qsa('[data-gate="create"]')[0].click();
      }
    }).catch(function () {});
  }
  $('f-open').addEventListener('submit', function (e) {
    e.preventDefault();
    var btn = e.target.querySelector('button');
    busy(btn, true, 'Opening…');
    call('wallet.open', { file: $('o-file').value, password: $('o-pass').value }).then(function (w) {
      $('o-pass').value = '';
      opened(w);
    }).catch(function (err) { msg('gate-msg', err.message, 'err'); }).then(function () { busy(btn, false); });
  });
  $('f-create').addEventListener('submit', function (e) {
    e.preventDefault();
    if ($('c-pass').value !== $('c-pass2').value) { msg('gate-msg', "The passwords don't match.", 'err'); return; }
    var btn = e.target.querySelector('button');
    busy(btn, true, 'Making your quantum-safe key…');
    call('wallet.create', { label: $('c-label').value, password: $('c-pass').value }).then(function (w) {
      $('c-pass').value = $('c-pass2').value = '';
      showSeed(w.seed, true, function () { opened(w); });
    }).catch(function (err) { msg('gate-msg', err.message, 'err'); }).then(function () { busy(btn, false); });
  });
  $('f-restore').addEventListener('submit', function (e) {
    e.preventDefault();
    var btn = e.target.querySelector('button');
    busy(btn, true, 'Restoring…');
    call('wallet.restore', { label: $('r-label').value, password: $('r-pass').value, seed: $('r-seed').value }).then(function (w) {
      $('r-seed').value = $('r-pass').value = '';
      opened(w);
    }).catch(function (err) { msg('gate-msg', err.message, 'err'); }).then(function () { busy(btn, false); });
  });
  $('f-watch').addEventListener('submit', function (e) {
    e.preventDefault();
    var btn = e.target.querySelector('button');
    busy(btn, true);
    call('wallet.watch', { label: $('w-label').value, address: $('w-addr').value }).then(opened)
      .catch(function (err) { msg('gate-msg', err.message, 'err'); }).then(function () { busy(btn, false); });
  });
  function opened(w) {
    S.wallet = w;
    msg('gate-msg', '');
    renderWallet();
    show(mode());
    go('home');
    refresh();
  }
  function showSeed(seed, fresh, done) {
    var words = seed.split(' ');
    modal('<h2>' + (fresh ? 'Write down your 25 words' : 'Your 25 words') + '</h2>' +
      '<p class="small">These words are the wallet. Anyone who has them can spend your QOIN; if you lose them and this phone, the QOIN is gone.</p>' +
      '<div class="seedgrid">' + words.map(function (w) { return '<span>' + esc(w) + '</span>'; }).join('') + '</div>' +
      '<div class="warnbox">Write them on paper. Don\'t screenshot them or paste them into chats.</div>' +
      (fresh ? '<label class="check"><input type="checkbox" id="seed-ok"> I wrote all 25 words down</label>' : '') +
      '<button class="btn primary wide" id="seed-done"' + (fresh ? ' disabled' : '') + '>' + (fresh ? 'Continue' : 'Done') + '</button>', fresh);
    if (fresh) $('seed-ok').addEventListener('change', function () { $('seed-done').disabled = !this.checked; });
    $('seed-done').addEventListener('click', function () { closeModal(); if (done) done(); });
  }

  // ---------------- wallet: render ----------------
  function walletAddr(w) { return w.name || w.raw; }
  function renderWallet() {
    var w = S.wallet; if (!w || !w.open) return;
    $('w-label-top').textContent = w.label + (w.watch ? ' (watch-only)' : '');
    $('w-name-top').textContent = w.name || w.pendingName || 'no name yet';
    var parts = w.balanceText.split('.');
    $('bal').innerHTML = esc(parts[0]) + (parts[1] ? '<span class="dec">.' + esc(parts[1]) + '</span>' : '');
    var sub = [];
    if (w.immature !== '0') sub.push(w.immatureText + ' locked from mining');
    if (w.pendingIn !== '0') sub.push('+' + w.pendingInText + ' incoming');
    if (w.pendingOut !== '0') sub.push('−' + w.pendingOutText + ' sending');
    $('bal-sub').textContent = sub.join(' · ');
    $('home-addr').textContent = walletAddr(w);
    $('claim').classList.toggle('hidden', !w.canRegister || !!w.pendingName);
    $('claim-pending').classList.toggle('hidden', !w.pendingName);
    $('claim-pending').textContent = w.pendingName ? w.pendingName + ' is being registered: it appears with the next block.' : '';
    var used = w.leavesUsed, total = w.leaves;
    $('key-fill').style.width = Math.max(1, Math.round(100 * used / total)) + '%';
    $('key-text').textContent = w.watch ? 'Watch-only: this phone holds no keys for this address.' :
      used + ' of ' + total + ' one-time signatures used on key #' + w.keyIndex + '. The wallet switches to a fresh key automatically before they run out.';
    qsa('[data-go="send"]').forEach(function (b) { b.classList.toggle('hidden', !!w.watch); });
    $('rc-name').textContent = w.name || '';
    $('rc-raw').textContent = w.raw + '.frostchain';
  }
  function refresh() {
    return call('status').then(function (s) {
      S.status = s;
      var prev = S.wallet;
      S.wallet = s.wallet;
      if (!!(prev && prev.open) !== !!s.wallet.open || (prev && prev.open && s.wallet.open && prev.file !== s.wallet.file)) show(mode());
      renderNode(s);
      if (mode() === 'wallet' && s.wallet.open) {
        renderWallet();
        if (s.node.height !== S.lastHeight || (prev && (prev.pendingIn !== s.wallet.pendingIn || prev.pendingOut !== s.wallet.pendingOut))) {
          S.lastHeight = s.node.height;
          loadRecent();
          if (S.page === 'history') loadHistory();
          if (S.page === 'more') loadMore();
          if (S.page === 'node') loadNode();
        }
      }
      if (mode() === 'myfrost' && s.wallet.open) mfRefresh(s);
      if (mode() === 'frostoise') renderFrostoise(s);
    }).catch(function () {
      $('node-text').textContent = $('f-node-text').textContent = $('mf-node-text').textContent = 'no node';
    });
  }
  function renderNode(s) {
    var n = s.node, ok = !n.offline && n.peers > 0 && n.sync.indexOf('0 of') !== 0;
    var cls = n.offline ? 'warn' : (ok ? 'good' : 'warn');
    var text = n.offline ? 'offline · #' + n.height : (n.peers ? n.peers + ' peer' + (n.peers > 1 ? 's' : '') + ' · #' + n.height : 'solo · #' + n.height);
    ['node', 'f-node', 'mf-node'].forEach(function (p) { $(p + '-dot').className = 'dot ' + cls; $(p + '-text').textContent = text; });
    $('gate-node').textContent = 'Node: block ' + n.height + ' · ' + (n.offline ? 'offline' : n.sync) + ' · chain ' + n.chain;
  }

  // ---------------- home: claim name ----------------
  var claimTimer;
  $('claim-name').addEventListener('input', function () {
    clearTimeout(claimTimer);
    var v = this.value.trim().toLowerCase().replace(/^@/, '').replace(/\.frostchain$/, '');
    if (!v) { msg('claim-hint', ''); return; }
    if (!/^[a-z0-9](?:[a-z0-9-]{1,22})[a-z0-9]$/.test(v) || v.indexOf('--') >= 0) { msg('claim-hint', '3-24 letters, digits or inner hyphens.', 'err'); return; }
    claimTimer = setTimeout(function () {
      call('resolve', { addr: v }).then(function () { msg('claim-hint', v + '.frostchain is taken.', 'err'); })
        .catch(function (e) { msg('claim-hint', /isn't registered/.test(e.message) ? v + '.frostchain is available.' : e.message, /isn't registered/.test(e.message) ? 'ok' : 'err'); });
    }, 350);
  });
  $('claim-btn').addEventListener('click', function () {
    var btn = this;
    busy(btn, true, 'Signing…');
    call('wallet.register', { name: $('claim-name').value }).then(function (r) {
      toast(r.name + ' registered: it appears with the next block');
      $('claim-name').value = ''; msg('claim-hint', '');
      refresh();
    }).catch(function (e) { msg('claim-hint', e.message, 'err'); }).then(function () { busy(btn, false); });
  });
  $('home-addr').addEventListener('click', function () { copy(this.textContent); });

  // ---------------- history ----------------
  /** One activity row. MyFrost passes atName to show people as @name and to label DOGE swaps. */
  function txItem(e, nameFn) {
    if (typeof nameFn !== 'function') nameFn = null;   // Array.map passes the index here
    var k = e.kind, inc = k === 'received' || k === 'mined', icon = { sent: '↗', received: '↙', mined: '❄', name: '@', rekey: '⟳' }[k] || '•';
    var other = nameFn ? nameFn(e.otherText) : e.otherText;
    var title = { sent: 'To ' + other, received: 'From ' + other, mined: 'Mined block ' + e.height,
      name: (e.height === 1 && e.name === 'jacobfrost' ? 'Got ' : 'Claimed ') + (nameFn ? '@' + e.name : e.name + '.frostchain'), rekey: 'Rotated to a fresh key' }[k];
    var sw = nameFn && (k === 'sent' || k === 'received') ? /^swap ([0-9.]+) DOGE/.exec(e.memo || '') : null;
    if (sw) { icon = '⇄'; title = k === 'received' ? 'Bought with ' + sw[1] + ' DOGE' : 'Swap: ' + sw[1] + ' DOGE from ' + other; }
    var when = e.pending ? 'pending' : ago(e.time);
    if (k === 'mined' && S.status && e.unlocksAt > S.status.node.height) when += ' · unlocks at block ' + e.unlocksAt;
    if (e.memo) when += ' · “' + e.memo + '”';
    var amt = (k === 'name' || k === 'rekey') ? '' : (inc ? '+' : '−') + short(e.amountText);
    return '<li><span class="ic ' + (k === 'mined' ? 'mined' : inc ? 'in' : '') + '">' + icon + '</span><span class="t"><b>' + esc(title) + '</b><span>' + esc(when) +
      '</span></span><span class="amt' + (inc ? ' in' : '') + '">' + esc(amt) + '</span></li>';
  }
  function loadHistory() {
    if (!S.wallet || !S.wallet.open) return;
    call('wallet.history').then(function (l) {
      $('history').innerHTML = l.length ? l.map(txItem).join('') : '<li class="empty">Nothing yet. Mine with Frostoise or share your address to get paid.</li>';
    });
  }
  function loadRecent() {
    call('wallet.history').then(function (l) {
      $('recent').innerHTML = l.length ? l.slice(0, 4).map(txItem).join('') : '<li class="empty">No activity yet.</li>';
    }).catch(function () {});
  }

  // ---------------- send ----------------
  var toTimer;
  $('s-to').addEventListener('input', function () {
    clearTimeout(toTimer);
    var v = this.value.trim(), link = parsePay(v);
    if (link) { this.value = v = link.to; if (link.amount) $('s-amount').value = link.amount; if (link.memo) $('s-memo').value = link.memo; }
    if (!v) { msg('s-to-hint', ''); return; }
    toTimer = setTimeout(function () {
      call('resolve', { addr: v }).then(function (r) { msg('s-to-hint', '✓ ' + (r.name ? r.name + ' · ' : '') + r.raw.slice(0, 14) + '…', 'ok'); })
        .catch(function (e) { msg('s-to-hint', e.message, 'err'); });
    }, 350);
  });
  $('s-max').addEventListener('click', function () {
    call('wallet.max').then(function (r) {
      if (r.amount === '0') { msg('s-msg', 'Nothing unlocked to send yet.', 'err'); return; }
      msg('s-msg', '');
      $('s-amount').value = r.amountText;
    }).catch(function (e) { msg('s-msg', e.message, 'err'); });
  });
  $('f-send').addEventListener('submit', function (e) {
    e.preventDefault();
    var btn = e.target.querySelector('button[type=submit]');
    var p = { to: $('s-to').value, amount: $('s-amount').value, memo: $('s-memo').value, dry: true };
    busy(btn, true, 'Checking…');
    call('wallet.send', p).then(function (r) {
      S.review = p;
      $('rv-to').textContent = r.to;
      $('rv-amount').textContent = r.amountText + ' QOIN';
      $('rv-fee').textContent = r.feeText + ' QOIN' + (r.rekey ? ' (includes rotating to a fresh key)' : '');
      $('rv-total').textContent = r.totalText + ' QOIN';
      $('rv-after').textContent = r.after + ' QOIN';
      $('rv-memo').textContent = r.memo || '—';
      $('rv-key').textContent = 'LMS key #' + r.keyIndex + ', one-time signature ' + (r.leaf + 1) + ' of 1024';
      msg('rv-msg', '');
      $('f-send').classList.add('hidden');
      $('review').classList.remove('hidden');
    }).catch(function (err) { msg('s-msg', err.message, 'err'); }).then(function () { busy(btn, false); });
  });
  $('rv-back').addEventListener('click', function () { $('review').classList.add('hidden'); $('f-send').classList.remove('hidden'); });
  $('rv-send').addEventListener('click', function () {
    var btn = this, p = S.review; if (!p) return;
    busy(btn, true, 'Signing…');
    call('wallet.send', { to: p.to, amount: p.amount, memo: p.memo }).then(function (r) {
      S.review = null;
      $('s-to').value = $('s-amount').value = $('s-memo').value = '';
      msg('s-to-hint', ''); msg('s-msg', '');
      $('review').classList.add('hidden'); $('f-send').classList.remove('hidden');
      toast('Sent ' + r.amountText + ' QOIN to ' + r.to);
      refresh().then(function () { go('home'); });
    }).catch(function (err) { msg('rv-msg', err.message, 'err'); }).then(function () { busy(btn, false); });
  });

  // ---------------- receive ----------------
  function drawReceive() {
    var w = S.wallet; if (!w || !w.open) return;
    var text = w.name || (w.raw + '.frostchain');
    try { window.QR.draw($('qr'), text); } catch (e) { }
  }
  $('rc-copy').addEventListener('click', function () { var w = S.wallet; copy(w.name || w.raw + '.frostchain'); });
  $('rc-name').addEventListener('click', function () { copy(this.textContent); });
  $('rc-raw').addEventListener('click', function () { copy(this.textContent); });
  $('rc-share').addEventListener('click', function () {
    var w = S.wallet;
    share('Pay me QOIN at ' + (w.name || w.raw + '.frostchain'));
  });

  // ---------------- more ----------------
  function loadMore() {
    var s = S.status; if (!s) return;
    $('about-platform').textContent = s.platform;
    call('node.blocks', { count: 1 }).then(function (l) {
      if (l.length) $('live-reward').textContent = 'Monero-style smooth emission · block #' + l[0].height + ' paid ' + short(l[0].paid) + ' QOIN';
    }).catch(function () {});
  }
  function loadNode() {
    var s = S.status; if (!s) return;
    var n = s.node;
    $('chain-tag').textContent = 'chain ' + n.chain;
    $('node-kv').innerHTML = [['Block height', n.height], ['Network', n.offline ? 'offline' : n.sync], ['Listening on port', n.port],
      ['Waiting transactions', n.mempool], ['Next difficulty', Number(n.difficulty).toLocaleString()], ['QOIN mined so far', n.generated],
      ['Accounts', n.accounts]].map(function (r) { return '<dt>' + esc(r[0]) + '</dt><dd>' + esc(r[1]) + '</dd>'; }).join('');
    $('n-offline').checked = n.offline;
    $('n-lan').checked = n.discovery;
    call('node.peers').then(function (l) {
      $('peers').innerHTML = l.length ? l.map(function (p) {
        var st = p.ok ? '#' + p.height : (p.error ? esc(p.error) : 'not tried yet');
        return '<li><span><span class="dot ' + (p.ok ? 'good' : 'warn') + '" style="display:inline-block;margin-right:6px"></span>' + esc(p.addr) +
          ' <span class="muted small">' + st + '</span></span><button class="btn small" data-rm="' + esc(p.addr) + '">Remove</button></li>';
      }).join('') : '<li class="muted small">No other nodes yet. Phones on the same Wi-Fi find each other; or add one by address.</li>';
    });
    call('node.blocks', { count: 8 }).then(function (l) {
      $('blocks').innerHTML = l.length ? l.map(function (b) {
        return '<li><span>#' + b.height + ' · ' + esc(b.miner) + '</span><span>' + esc(b.paid) + ' QOIN · ' + b.hz.toFixed(2) + ' Hz ' + esc(b.sensor) + '<br>' + ago(b.time) + '</span></li>';
      }).join('') : '<li class="muted small">No blocks yet. Block 1 pays the normal reward and gives its miner the name jacobfrost.frostchain.</li>';
    });
    loadExplorer();
  }
  $('peers').addEventListener('click', function (e) {
    var b = e.target.closest('[data-rm]'); if (!b) return;
    call('node.removePeer', { addr: b.dataset.rm }).then(loadNode);
  });
  $('n-add').addEventListener('click', function () {
    var v = $('n-peer').value.trim(); if (!v) return;
    call('node.addPeer', { addr: v }).then(function () { $('n-peer').value = ''; toast('Added: syncing'); setTimeout(loadNode, 1500); })
      .catch(function (e) { toast(e.message); });
  });

  // ---------------- Explorer (v0.4): the newest blocks as a Rez-style wireframe tunnel ----------------
  // Blocks glow slower than once a second, so nothing flashes, and motion stops for reduce-motion users.
  var REZ = { blocks: [], sel: null, moving: true, t: 0, last: 0, raf: 0, hit: [] };
  var REZ_COLOR = { delta: '#ffb547', theta: '#3fe9ff', alpha: '#ff52d9', '': '#9c96cb' };
  var REZ_HZ = { delta: 4.0, theta: 7.83, alpha: 11.11, '': 7.83 };
  if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    REZ.moving = false; $('x-motion').textContent = 'Start motion'; $('x-motion').setAttribute('aria-pressed', 'true');
  }
  function rezBand(b) {
    if (b.band) return b.band;
    return Math.abs(b.hz - 4.0) <= 0.3 ? 'delta' : Math.abs(b.hz - 11.11) <= 0.35 ? 'alpha' : Math.abs(b.hz - 7.83) <= 0.4 ? 'theta' : '';
  }
  function loadExplorer() {
    call('node.blocks', { count: 26 }).then(function (l) {
      REZ.blocks = l.slice().reverse();
      if (REZ.sel == null || !l.some(function (b) { return b.height === REZ.sel; })) REZ.sel = l.length ? l[0].height : null;
      rezDetail(); rezStart();
    }).catch(function () { });
  }
  function rezDetail() {
    var b = REZ.blocks.filter(function (x) { return x.height === REZ.sel; })[0];
    $('x-detail').innerHTML = b
      ? '<dt>Block</dt><dd>#' + b.height + ' · ' + ago(b.time) + '</dd>' +
        '<dt>Miner</dt><dd>' + esc(b.miner) + '</dd>' +
        '<dt>Tone</dt><dd>' + b.hz.toFixed(2) + ' Hz ' + esc(rezBand(b)) + ' · ' + esc(b.sensor) + '</dd>' +
        '<dt>Reward</dt><dd>' + esc(b.paid) + ' QOIN</dd>' +
        '<dt>Transactions</dt><dd>' + b.txs + '</dd>' +
        '<dt>Hash</dt><dd class="mono">' + esc(b.hash) + '</dd>'
      : '<dt>Blocks</dt><dd>None yet. Block 1 pays the normal reward and gives its miner the name jacobfrost.frostchain.</dd>';
  }
  function rezStart() { if (!REZ.raf) REZ.raf = requestAnimationFrame(rezFrame); }
  function rezFrame(now) {
    REZ.raf = 0;
    var c = $('rez');
    if (!c || !c.offsetParent) return; // the Node page is hidden: stop until it's shown again
    if (REZ.moving) REZ.t += Math.min(0.05, (now - (REZ.last || now)) / 1000);
    REZ.last = now;
    rezDraw(c);
    if (REZ.moving) REZ.raf = requestAnimationFrame(rezFrame);
  }
  var REZ_EDGES = [[0,1],[1,2],[2,3],[3,0],[4,5],[5,6],[6,7],[7,4],[0,4],[1,5],[2,6],[3,7]];
  var REZ_CORNERS = [[-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1],[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]];
  function rezDraw(c) {
    var dpr = Math.min(window.devicePixelRatio || 1, 2), w = c.clientWidth, h = c.clientHeight;
    if (c.width !== Math.round(w * dpr) || c.height !== Math.round(h * dpr)) { c.width = Math.round(w * dpr); c.height = Math.round(h * dpr); }
    var ctx = c.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    var t = REZ.t, ph = t * 0.09, f = Math.min(w, h) * 0.95, cx = w / 2, cy = h * 0.46;
    function px(z) { return Math.sin(z * 0.075 + ph) * 3.2; }
    function py(z) { return Math.cos(z * 0.052 + ph * 0.7) * 1.1 - 0.4; }
    var camX = px(0) * 0.7, camY = py(0) * 0.7;
    function proj(x, y, z) { return [cx + (x - camX) * f / z, cy + (y - camY) * f / z]; }
    ctx.lineWidth = 1; ctx.strokeStyle = '#2b2654';
    var off = (t * 1.1) % 6, z, a, b2;
    for (z = 2 + (6 - off); z < 150; z += 6) { a = proj(-40, 3, z); b2 = proj(40, 3, z); ctx.globalAlpha = Math.max(0, 1 - z / 150) * 0.8; ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(b2[0], b2[1]); ctx.stroke(); }
    for (var gx = -40; gx <= 40; gx += 4) { a = proj(gx, 3, 2); b2 = proj(gx, 3, 150); ctx.globalAlpha = 0.5; ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(b2[0], b2[1]); ctx.stroke(); }
    var list = REZ.blocks, tipH = list.length ? list[list.length - 1].height : 0, SP = 4.2, NEAR = 5.5;
    REZ.hit = [];
    if (!list.length) { ctx.globalAlpha = 1; ctx.fillStyle = '#9c96cb'; ctx.font = '13px ' + getComputedStyle(document.body).fontFamily; ctx.textAlign = 'center'; ctx.fillText('No blocks yet', cx, cy); return; }
    ctx.globalAlpha = 0.55; ctx.strokeStyle = '#9c96cb'; ctx.beginPath();
    list.forEach(function (b, i) { var zz = NEAR + (tipH - b.height) * SP, p = proj(px(zz), py(zz), zz); if (i) ctx.lineTo(p[0], p[1]); else ctx.moveTo(p[0], p[1]); });
    ctx.stroke();
    list.forEach(function (b) {
      var zz = NEAR + (tipH - b.height) * SP, x = px(zz), y = py(zz);
      var depth = Math.max(0, 1 - (zz - NEAR) / (list.length * SP)), sel = b.height === REZ.sel, band = rezBand(b), col = REZ_COLOR[band];
      var glow = 0.55 + 0.45 * (0.5 + 0.5 * Math.sin(2 * Math.PI * t * (REZ_HZ[band] / 16) + b.height));
      var ang = t * 0.35 + b.height * 0.6, ca = Math.cos(ang), sa = Math.sin(ang), ct = Math.cos(0.45), st = Math.sin(0.45), s = sel ? 0.75 : 0.55;
      var pts = REZ_CORNERS.map(function (k) {
        var X = k[0] * s, Y = k[1] * s, Z = k[2] * s, X1 = X * ca + Z * sa, Z1 = -X * sa + Z * ca;
        return proj(x + X1, y + Y * ct - Z1 * st, zz + Y * st + Z1 * ct);
      });
      ctx.globalAlpha = Math.max(0.12, depth) * (sel ? 1 : 0.85);
      ctx.strokeStyle = col; ctx.shadowColor = col; ctx.shadowBlur = (sel ? 18 : 10) * glow * depth;
      ctx.lineWidth = (sel ? 2.4 : 1.4) * (0.5 + depth);
      ctx.beginPath();
      REZ_EDGES.forEach(function (e) { ctx.moveTo(pts[e[0]][0], pts[e[0]][1]); ctx.lineTo(pts[e[1]][0], pts[e[1]][1]); });
      ctx.stroke(); ctx.shadowBlur = 0;
      var cpt = proj(x, y, zz);
      REZ.hit.push({ h: b.height, x: cpt[0], y: cpt[1], r: Math.max(16, s * f * 1.6 / zz) });
      if (sel || b.height === tipH) {
        ctx.globalAlpha = 1; ctx.fillStyle = sel ? '#ece9ff' : '#9c96cb';
        ctx.font = '12px ' + getComputedStyle(document.documentElement).getPropertyValue('--mono'); ctx.textAlign = 'center';
        ctx.fillText('#' + b.height, cpt[0], cpt[1] - s * f * 1.9 / zz);
      }
    });
    ctx.globalAlpha = 1;
  }
  $('rez').addEventListener('click', function (e) {
    var r = this.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top, best = null, bd = 1e9;
    REZ.hit.forEach(function (c) { var d = Math.hypot(c.x - x, c.y - y); if (d < c.r && d < bd) { bd = d; best = c; } });
    if (best) { REZ.sel = best.h; rezDetail(); if (!REZ.moving) rezDraw(this); }
  });
  $('x-motion').addEventListener('click', function () {
    REZ.moving = !REZ.moving;
    this.textContent = REZ.moving ? 'Pause motion' : 'Start motion';
    this.setAttribute('aria-pressed', String(!REZ.moving));
    REZ.last = 0; rezStart();
  });
  window.addEventListener('resize', function () { var c = $('rez'); if (c && c.offsetParent && !REZ.moving) rezDraw(c); });

  // ---------------- Tone (v0.4 sound layer) ----------------
  var TONE = { hz: 7.83, mode: 'pulse', ac: null, master: null, nodes: [], on: false };
  function toneStopNodes() { TONE.nodes.forEach(function (n) { try { if (n.stop) n.stop(); } catch (e) { } try { n.disconnect(); } catch (e) { } }); TONE.nodes = []; }
  function toneBuild() {
    toneStopNodes();
    var ac = TONE.ac, out = TONE.master;
    if (TONE.mode === 'pulse') {
      var carrier = ac.createOscillator(); carrier.frequency.value = 220;
      var amp = ac.createGain(); amp.gain.value = 0.5;
      var lfo = ac.createOscillator(); lfo.frequency.value = TONE.hz;
      var depth = ac.createGain(); depth.gain.value = 0.5;
      lfo.connect(depth); depth.connect(amp.gain); carrier.connect(amp); amp.connect(out);
      carrier.start(); lfo.start();
      TONE.nodes.push(carrier, lfo, amp, depth);
    } else {
      var merger = ac.createChannelMerger(2);
      var l = ac.createOscillator(); l.frequency.value = 200;
      var r = ac.createOscillator(); r.frequency.value = 200 + TONE.hz;
      l.connect(merger, 0, 0); r.connect(merger, 0, 1); merger.connect(out);
      l.start(); r.start();
      TONE.nodes.push(l, r, merger);
    }
    if ($('tone-40').checked) {
      var c2 = ac.createOscillator(); c2.frequency.value = 400;
      var g2 = ac.createGain(); g2.gain.value = 0.2;
      var l2 = ac.createOscillator(); l2.frequency.value = 40;
      var d2 = ac.createGain(); d2.gain.value = 0.2;
      l2.connect(d2); d2.connect(g2.gain); c2.connect(g2); g2.connect(out);
      c2.start(); l2.start();
      TONE.nodes.push(c2, g2, l2, d2);
    }
  }
  function toneTag() {
    $('tone-tag').textContent = TONE.on ? (TONE.hz === 4 ? '4.0' : TONE.hz) + ' Hz ' + (TONE.mode === 'pulse' ? 'pulse' : 'binaural') + ($('tone-40').checked ? ' + 40 Hz' : '') : 'off';
    $('tone-tag').className = 'tag' + (TONE.on ? ' frost' : '');
    $('tone-btn').textContent = TONE.on ? 'Stop tone' : 'Play tone';
  }
  function toneRetune() { if (TONE.on && TONE.ac) toneBuild(); toneTag(); }
  qsa('[data-tone]').forEach(function (b) {
    b.addEventListener('click', function () {
      TONE.hz = Number(b.dataset.tone);
      qsa('[data-tone]').forEach(function (x) { x.classList.toggle('on', x === b); });
      toneRetune();
    });
  });
  qsa('[data-tmode]').forEach(function (b) {
    b.addEventListener('click', function () {
      TONE.mode = b.dataset.tmode;
      qsa('[data-tmode]').forEach(function (x) { x.classList.toggle('on', x === b); });
      toneRetune();
    });
  });
  $('tone-40').addEventListener('change', toneRetune);
  $('tone-vol').addEventListener('input', function () { if (TONE.master) TONE.master.gain.setTargetAtTime(Number(this.value) / 100, TONE.ac.currentTime, 0.05); });
  $('tone-btn').addEventListener('click', function () {
    if (TONE.on) {
      TONE.on = false;
      if (TONE.master) TONE.master.gain.setTargetAtTime(0, TONE.ac.currentTime, 0.05);
      setTimeout(toneStopNodes, 250);
      toneTag(); return;
    }
    try {
      if (!TONE.ac) {
        var AC = window.AudioContext || window.webkitAudioContext;
        TONE.ac = new AC(); TONE.master = TONE.ac.createGain(); TONE.master.gain.value = 0; TONE.master.connect(TONE.ac.destination);
      }
      var go = function () {
        toneBuild(); TONE.on = true;
        TONE.master.gain.setTargetAtTime(Number($('tone-vol').value) / 100, TONE.ac.currentTime, 0.08);
        toneTag();
      };
      if (TONE.ac.state === 'suspended') TONE.ac.resume().then(go); else go();
    } catch (e) { msg('f-msg', 'This phone can’t play the tone here: ' + e.message, 'err'); }
  });
  document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-copy]'); if (!b) return;
    e.preventDefault(); copy(b.dataset.copy);
  });
  $('mm-open').addEventListener('click', function () { openUrl('https://metamask.app.link/'); });
  $('n-offline').addEventListener('change', function () { call('node.setOffline', { offline: this.checked }).then(refresh); });
  $('n-lan').addEventListener('change', function () { call('node.setDiscovery', { on: this.checked }).then(refresh); });
  $('m-seed').addEventListener('click', function () {
    modal('<h2>Show your 25 words</h2><label for="sp">Wallet password</label><input id="sp" type="password" autocomplete="current-password">' +
      '<div class="msg" id="sp-msg"></div><div class="actions"><button class="btn" id="sp-cancel">Cancel</button><button class="btn primary" id="sp-go">Show</button></div>');
    $('sp-cancel').addEventListener('click', closeModal);
    $('sp-go').addEventListener('click', function () {
      call('wallet.seed', { password: $('sp').value }).then(function (r) { showSeed(r.seed, false); })
        .catch(function (e) { msg('sp-msg', e.message, 'err'); });
    });
  });
  $('m-rekey').addEventListener('click', function () {
    var btn = this;
    busy(btn, true, 'Preparing the next key…');
    call('wallet.rekey').then(function (r) { msg('m-msg', 'Switching to key #' + r.keyIndex + ' with the next block.', 'ok'); refresh(); })
      .catch(function (e) { msg('m-msg', e.message, 'err'); }).then(function () { busy(btn, false); });
  });
  $('m-close').addEventListener('click', function () {
    call('wallet.close').then(function () { S.wallet = { open: false }; show(mode()); });
  });

  // ---------------- Frostoise ----------------
  (function buildSpectrum() {
    var html = '';
    for (var i = 0; i < 33; i++) {
      var hz = 2 + i * 0.4, band = Math.abs(hz - 4.0) <= 0.2 || Math.abs(hz - 7.83) <= 0.2 || Math.abs(hz - 11.11) <= 0.2;
      html += '<i class="' + (band ? 'band' : '') + '" style="height:2%"></i>';
    }
    $('spectrum').innerHTML = html;
  })();
  qsa('[data-sensor]').forEach(function (b) {
    b.addEventListener('click', function () {
      S.sensor = b.dataset.sensor;
      qsa('[data-sensor]').forEach(function (x) { x.classList.toggle('on', x === b); });
      var s = S.status;
      if (s && s.sensors.running && s.sensors.running !== S.sensor) startSensor();
    });
  });
  /** Android: ask for every permission in `list` in one dialog. Resolves to {mic:true, ...}. */
  var permWaiter = null;
  window.__frostPermission = function (json) {
    var r = {}; try { r = JSON.parse(json); } catch (e) { }
    var w = permWaiter; permWaiter = null;
    if (w) w(r);
  };
  function ensurePerms(list) {
    if (!window.FrostBridge || !window.FrostBridge.requestPermissions || !list.length) return Promise.resolve({ mic: true, notify: true });
    return new Promise(function (resolve) { permWaiter = resolve; window.FrostBridge.requestPermissions(list.join(',')); });
  }
  var MIC_DENIED = 'Frostoise needs the microphone permission to listen for the resonator. You can allow it in Android Settings → Apps → Graysons Wallet → Permissions.';
  function startSensor() {
    var btn = $('sensor-btn');
    busy(btn, true, 'Starting…');
    return ensurePerms(S.sensor === 'mic' ? ['mic'] : []).then(function (p) {
      if (S.sensor === 'mic' && !p.mic) throw new Error(MIC_DENIED);
      return call('sensor.start', { sensor: S.sensor });
    }).then(function () { msg('f-msg', ''); return refresh(); })
      .catch(function (e) { msg('f-msg', e.message, 'err'); })
      .then(function () { busy(btn, false); });
  }
  $('sensor-btn').addEventListener('click', function () {
    var s = S.status;
    if (s && s.sensors.running) call('sensor.stop').then(refresh); else startSensor();
  });
  $('m-threads').addEventListener('input', function () { $('m-threads-v').textContent = this.value; });
  $('mine-btn').addEventListener('click', function () {
    var s = S.status, btn = this;
    if (s && s.miner.running) {
      if (window.FrostBridge && window.FrostBridge.keepScreenOn) window.FrostBridge.keepScreenOn(false);
      call('miner.stop').then(refresh); return;
    }
    busy(btn, true, 'Starting…');
    ensurePerms(S.sensor === 'mic' ? ['notify', 'mic'] : ['notify']).then(function (p) {
      if (S.sensor === 'mic' && !p.mic) throw new Error(MIC_DENIED);
      return call('miner.start', { threads: Number($('m-threads').value), payout: $('m-payout').value, sensor: S.sensor });
    }).then(function () { msg('f-msg', ''); if (window.FrostBridge && window.FrostBridge.keepScreenOn) window.FrostBridge.keepScreenOn(true); return refresh(); })
      .catch(function (e) { msg('f-msg', e.message, 'err'); })
      .then(function () { busy(btn, false); });
  });
  function renderFrostoise(s) {
    var r = s.resonance, sens = s.sensors || {};
    var running = !!sens.running;
    if (running && sens.running !== S.sensor) {
      S.sensor = sens.running;
      qsa('[data-sensor]').forEach(function (x) { x.classList.toggle('on', x.dataset.sensor === S.sensor); });
    }
    qsa('[data-sensor]').forEach(function (x) { x.disabled = sens[x.dataset.sensor] === false; });
    $('sensor-btn').textContent = running ? 'Stop sensor' : 'Start sensor';
    var badge = $('lock-badge');
    if (!running || !r) {
      badge.className = 'lock'; badge.textContent = running ? 'Listening…' : 'Sensor off';
      $('hz').textContent = '–';
      $('reso-why').textContent = sens.error || (running ? 'Collecting a few seconds of samples…' : 'Turn on a sensor and put the phone on your resonator.');
      $('reso-rate').textContent = '';
      qsa('#spectrum i').forEach(function (b) { b.style.height = '2%'; b.classList.remove('hit'); });
      $('reso-kv').innerHTML = '';
    } else {
      badge.className = 'lock ' + (r.locked ? 'on' : 'search');
      badge.textContent = r.locked ? 'Locked · ' + (r.bandHz === 4 ? '4.0' : r.bandHz) + ' Hz ' + r.band : 'Searching';
      $('hz').textContent = r.snr > 3 ? r.hz.toFixed(2) : '–';
      $('reso-why').textContent = r.why;
      $('reso-rate').textContent = (r.sensor === 'mag' ? 'magnetometer ' : 'microphone ') + Math.round(r.rate) + ' Hz';
      var bars = qsa('#spectrum i');
      r.spectrum.forEach(function (v, i) {
        bars[i].style.height = Math.max(2, Math.round(v / 255 * 100)) + '%';
        bars[i].classList.toggle('hit', r.locked && bars[i].classList.contains('band'));
      });
      $('reso-kv').innerHTML = '<dt>Peak strength</dt><dd>' + r.snr.toFixed(1) + '× noise (needs 12×)</dd>' +
        (r.sensor === 'mag' ? '<dt>Field at the peak</dt><dd>' + (r.amplitude * 1000).toFixed(0) + ' nT</dd>' : '<dt>Pulse depth</dt><dd>' + (r.depth * 100).toFixed(1) + '%</dd>') +
        '<dt>Samples</dt><dd>' + r.samples + '</dd>';
    }
    var m = s.miner;
    if (S.lastAccepted != null && m.accepted > S.lastAccepted) toast('❄ Found block ' + s.node.height + '!');
    S.lastAccepted = m.accepted;
    $('mine-tag').textContent = m.running ? (m.mining ? 'mining' + (m.level ? ' · ' + m.level + ' (' + m.active + ' of ' + m.threads + ')' : '') + (m.heat > 0 ? ' · cooling' : '') : (m.heat >= 3 ? 'cooling down' : 'waiting for a tone')) : 'stopped';
    $('mine-tag').className = 'tag' + (m.mining ? ' frost' : '');
    $('mine-btn').textContent = m.running ? 'Stop mining' : 'Start mining';
    $('m-rate').textContent = rate(m.mining ? m.hashrate : 0);
    $('m-eta').textContent = m.mining && m.expectedSeconds > 0 ? dur(m.expectedSeconds) : '–';
    $('m-found').textContent = m.accepted + (m.found > m.accepted ? ' (' + (m.found - m.accepted) + ' late)' : '');
    $('m-height').textContent = s.node.height;
    $('m-gate').textContent = m.running ? m.gate : '';
    var w = s.wallet;
    if (m.running && m.payout) $('m-payout').placeholder = 'paying ' + (w.open && w.raw === m.payout ? walletAddr(w) : m.payout);
    else $('m-payout').placeholder = w.open ? 'your wallet: ' + walletAddr(w) : 'name.frostchain to pay';
    $('f-wallet').innerHTML = w.open
      ? esc(w.label) + ' · <b>' + esc(walletAddr(w)) + '</b><br>' + esc(w.balanceText) + ' QOIN' + (w.immature !== '0' ? ' + ' + esc(w.immatureText) + ' locked from mining' : '')
      : 'No wallet open. Open Graysons Wallet so Frostoise can pay you, or type a .frostchain name above.';
  }

  // ---------------- MyFrost: send and receive by @name, buy QOIN with DOGE ----------------
  var MF = { page: 'home', review: null, lastKey: '', pendingPay: null };
  function mfMyAddr(w) { return w.name || (w.raw + '.frostchain'); }
  function mfHandle(w) { return w.name ? atName(w.name) : w.pendingName ? atName(w.pendingName) + ' (registering)' : atName(w.raw); }
  function kvHtml(rows) { return rows.map(function (r) { return '<dt>' + esc(r[0]) + '</dt><dd>' + esc(r[1]) + '</dd>'; }).join(''); }

  function mfShow() {
    var open = !!(S.wallet && S.wallet.open);
    $('mf-gate').classList.toggle('hidden', open);
    $('mf-main').classList.toggle('hidden', !open);
    $('mf-tabbar').classList.toggle('hidden', !open);
    if (!open) { mfLoadWallets(); return; }
    MF.lastKey = '';
    mfRender();
    mfGo(MF.page);
    mfApplyPending();
  }
  function mfGo(page) {
    var w = S.wallet;
    if (page === 'send' && w && w.watch) page = 'home';
    MF.page = page;
    qsa('[data-mf]').forEach(function (p) { p.classList.toggle('hidden', p.dataset.mf !== page); });
    qsa('#mf-tabbar a').forEach(function (a) { a.classList.toggle('on', a.dataset.mfgo === page); });
    window.scrollTo(0, 0);
    if (page === 'home') mfLoadActivity();
    if (page === 'send') { mfShowForm(); mfLoadRecent(); }
    if (page === 'receive') mfDrawReceive();
    if (page === 'swap') mfRenderSwap(true);
  }
  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-mfgo]');
    if (t) { e.preventDefault(); mfGo(t.dataset.mfgo); }
  });
  /** Called by refresh() every 1.5 s while MyFrost is showing an open wallet. */
  function mfRefresh(s) {
    mfRender();
    var w = s.wallet, key = s.node.height + '/' + w.balance + '/' + w.pendingIn + '/' + w.pendingOut + '/' + w.name;
    if (MF.page === 'swap') mfRenderSwap(false);
    if (key === MF.lastKey) return;
    MF.lastKey = key;
    if (MF.page === 'home') mfLoadActivity();
    if (MF.page === 'swap' && s.swap && s.swap.isDesk) mfLoadDeskLog();
  }

  // unlock (wallets are created in Graysons Wallet)
  function mfLoadWallets() {
    call('wallets.list').then(function (list) {
      $('mf-file').innerHTML = list.map(function (w) {
        return '<option value="' + esc(w.file) + '">' + esc(w.label) + (w.name ? ' · ' + esc(atName(w.name)) : '') + (w.watch ? ' (watch-only)' : '') + '</option>';
      }).join('');
      $('mf-unlock').classList.toggle('hidden', !list.length);
      if (!list.length) msg('mf-gate-msg', 'No wallets on this phone yet. Make one in Graysons Wallet, then come back.');
    }).catch(function () {});
  }
  $('mf-unlock').addEventListener('submit', function (e) {
    e.preventDefault();
    var btn = $('mf-open');
    busy(btn, true, 'Unlocking…');
    call('wallet.open', { file: $('mf-file').value, password: $('mf-pass').value }).then(function (w) {
      $('mf-pass').value = '';
      msg('mf-gate-msg', '');
      S.wallet = w;
      MF.page = 'home';
      mfShow();
      refresh();
    }).catch(function (err) { msg('mf-gate-msg', err.message, 'err'); }).then(function () { busy(btn, false); });
  });
  $('mf-lock').addEventListener('click', function () {
    call('wallet.close').then(function () { S.wallet = { open: false }; MF.page = 'home'; show(mode()); });
  });

  // home
  function mfRender() {
    var w = S.wallet; if (!w || !w.open) return;
    $('mf-handle').textContent = mfHandle(w);
    $('mf-handle-top').textContent = w.label + (w.watch ? ' · watch-only' : '');
    var parts = w.balanceText.split('.');
    $('mf-bal').innerHTML = esc(parts[0]) + (parts[1] ? '<span class="dec">.' + esc(parts[1]) + '</span>' : '');
    var sub = [];
    if (w.pendingIn !== '0') sub.push('+' + short(w.pendingInText) + ' on the way');
    if (w.pendingOut !== '0') sub.push('−' + short(w.pendingOutText) + ' sending');
    if (w.immature !== '0') sub.push(short(w.immatureText) + ' locked from mining');
    $('mf-sub').textContent = sub.join(' · ');
    $('mf-noname').classList.toggle('hidden', !!(w.name || w.pendingName || w.watch));
    qsa('[data-mfgo="send"]').forEach(function (b) { b.classList.toggle('hidden', !!w.watch); });
  }
  function mfLoadActivity() {
    if (!S.wallet || !S.wallet.open) return;
    call('wallet.history').then(function (l) {
      $('mf-activity').innerHTML = l.length ? l.slice(0, 25).map(function (e) { return txItem(e, atName); }).join('')
        : '<li class="empty">Nothing yet. Tap Receive for your QR code, or Swap to buy QOIN with DOGE.</li>';
    }).catch(function () {});
  }

  // send
  function mfShowForm() { $('mf-review').classList.add('hidden'); $('mf-send').classList.remove('hidden'); }
  function mfLoadRecent() {
    call('wallet.history').then(function (l) {
      var seen = {}, people = [];
      l.forEach(function (e) {
        var o = e.otherText;
        if ((e.kind === 'sent' || e.kind === 'received') && o && !seen[o] && people.length < 6) { seen[o] = 1; people.push(o); }
      });
      $('mf-recent').innerHTML = people.map(function (o) {
        return '<button type="button" data-to="' + esc(RAW_RE.test(o) ? o : atName(o)) + '">' + esc(atName(o)) + '</button>';
      }).join('');
    }).catch(function () {});
  }
  $('mf-recent').addEventListener('click', function (e) {
    var b = e.target.closest('[data-to]'); if (!b) return;
    $('mf-to').value = b.dataset.to;
    mfCheckTo();
    $('mf-amount').focus();
  });
  var mfToTimer;
  function mfCheckTo() {
    clearTimeout(mfToTimer);
    var v = $('mf-to').value.trim(), link = parsePay(v);
    if (link) {
      $('mf-to').value = v = link.to;
      if (link.amount) $('mf-amount').value = link.amount;
      if (link.memo) $('mf-memo').value = link.memo;
    }
    if (!v) { msg('mf-to-hint', ''); return; }
    mfToTimer = setTimeout(function () {
      call('resolve', { addr: v }).then(function (r) {
        if ($('mf-to').value.trim() !== v) return;
        msg('mf-to-hint', '✓ ' + (r.name ? atName(r.name) + ' · ' : '') + r.raw.slice(0, 14) + '…', 'ok');
      }).catch(function (e) { if ($('mf-to').value.trim() === v) msg('mf-to-hint', e.message, 'err'); });
    }, 300);
  }
  $('mf-to').addEventListener('input', mfCheckTo);
  $('mf-max').addEventListener('click', function () {
    call('wallet.max').then(function (r) {
      if (r.amount === '0') { msg('mf-send-msg', 'Nothing unlocked to send yet.', 'err'); return; }
      msg('mf-send-msg', '');
      $('mf-amount').value = r.amountText;
    }).catch(function (e) { msg('mf-send-msg', e.message, 'err'); });
  });
  $('mf-send').addEventListener('submit', function (e) {
    e.preventDefault();
    var btn = e.target.querySelector('button[type=submit]');
    var p = { to: $('mf-to').value.trim(), amount: $('mf-amount').value, memo: $('mf-memo').value, dry: true };
    busy(btn, true, 'Checking…');
    call('wallet.send', p).then(function (r) {
      MF.review = p;
      $('mf-review-kv').innerHTML = kvHtml([['To', who(r.to)], ['Amount', r.amountText + ' QOIN'],
        ['Network fee', r.feeText + ' QOIN' + (r.rekey ? ' (includes switching to a fresh key)' : '')],
        ['Total', r.totalText + ' QOIN'], ['Left after', r.after + ' QOIN'], ['Note', r.memo || '—']]);
      msg('mf-review-msg', '');
      msg('mf-send-msg', '');
      $('mf-send').classList.add('hidden');
      $('mf-review').classList.remove('hidden');
    }).catch(function (err) { msg('mf-send-msg', err.message, 'err'); }).then(function () { busy(btn, false); });
  });
  $('mf-review-back').addEventListener('click', mfShowForm);
  $('mf-review-send').addEventListener('click', function () {
    var btn = this, p = MF.review; if (!p) return;
    busy(btn, true, 'Signing…');
    call('wallet.send', { to: p.to, amount: p.amount, memo: p.memo }).then(function (r) {
      MF.review = null;
      $('mf-to').value = $('mf-amount').value = $('mf-memo').value = '';
      msg('mf-to-hint', '');
      mfShowForm();
      toast('Sent ' + r.amountText + ' QOIN to ' + who(r.to));
      return refresh().then(function () { mfGo('home'); });
    }).catch(function (err) { msg('mf-review-msg', err.message, 'err'); }).then(function () { busy(btn, false); });
  });

  // receive: a QR of a frostchain: payment link, optionally with an amount
  function mfReqLink() {
    var amt = cleanAmount($('mf-req').value);
    return 'frostchain:' + mfMyAddr(S.wallet) + (amt ? '?amount=' + amt : '');
  }
  function mfDrawReceive() {
    var w = S.wallet; if (!w || !w.open) return;
    var raw = $('mf-req').value.trim(), bad = raw && !cleanAmount(raw);
    msg('mf-req-msg', bad ? 'Use a number like 25 or 0.5 (up to 11 decimals).' : '', bad ? 'err' : '');
    var link = mfReqLink();
    $('mf-rc-name').textContent = mfHandle(w);
    $('mf-rc-full').textContent = mfMyAddr(w);
    $('mf-req-link').textContent = link;
    try { window.QR.draw($('mf-qr'), link); } catch (e) { }
  }
  $('mf-req').addEventListener('input', mfDrawReceive);
  $('mf-rc-name').addEventListener('click', function () { copy(mfMyAddr(S.wallet)); });
  $('mf-req-link').addEventListener('click', function () { copy(this.textContent); });
  $('mf-rc-copy').addEventListener('click', function () { copy(mfReqLink()); });
  $('mf-rc-share').addEventListener('click', function () {
    var w = S.wallet, amt = cleanAmount($('mf-req').value);
    share((amt ? 'Pay me ' + amt + ' QOIN' : 'Pay me QOIN') + ' at ' + (w.name ? atName(w.name) : mfMyAddr(w)) + ' in MyFrost: ' + mfReqLink());
  });

  // swap: the offer lives on finux.tech/getqoin; the PiDoge desk pays buyers from here
  var GET_QOIN_URL = 'https://finux.tech/getqoin';
  function mfRenderSwap(fresh) {
    var s = S.status, w = S.wallet; if (!s || !s.swap || !w || !w.open) return;
    var sw = s.swap;
    $('mf-rate-tag').textContent = sw.rateText;
    $('mf-your-name').textContent = mfMyAddr(w);
    $('mf-get').classList.toggle('hidden', !!sw.isDesk);
    $('mf-desk').classList.toggle('hidden', !sw.isDesk);
    if (sw.isDesk && fresh) mfLoadDeskLog();
  }
  function mfQuote(inputId, outId, msgId) {
    var v = $(inputId).value.trim();
    if (!v) { $(outId).textContent = '–'; msg(msgId, ''); return; }
    call('swap.quote', { doge: v }).then(function (r) {
      if ($(inputId).value.trim() !== v) return;
      $(outId).textContent = r.qoinText;
      msg(msgId, '');
    }).catch(function (e) {
      if ($(inputId).value.trim() !== v) return;
      $(outId).textContent = '–';
      msg(msgId, e.message, 'err');
    });
  }
  var mfQ2;
  $('mf-desk-doge').addEventListener('input', function () { clearTimeout(mfQ2); mfQ2 = setTimeout(function () { mfQuote('mf-desk-doge', 'mf-desk-quote', 'mf-desk-msg'); }, 200); });
  $('mf-your-name').addEventListener('click', function () { copy(this.textContent); });
  $('mf-open-get').addEventListener('click', function () { copy(mfMyAddr(S.wallet)); openUrl(GET_QOIN_URL); });

  // the desk (only the wallet holding the desk's name sees this)
  function mfLoadDeskLog() {
    call('swap.history').then(function (l) {
      $('mf-desk-log').innerHTML = l.length ? l.slice(0, 15).map(function (e) { return txItem(e, atName); }).join('')
        : '<li class="empty">No swaps paid yet.</li>';
    }).catch(function () {});
  }
  $('mf-desk-review').addEventListener('click', function () {
    var btn = this, p = { to: $('mf-desk-to').value.trim(), doge: $('mf-desk-doge').value.trim(), ref: $('mf-desk-ref').value.trim() };
    busy(btn, true, 'Checking…');
    call('swap.pay', { to: p.to, doge: p.doge, ref: p.ref, dry: true }).then(function (r) {
      msg('mf-desk-msg', '');
      modal('<h2>Pay this swap?</h2><dl class="kv">' + kvHtml([['Buyer', who(r.to)], ['DOGE received', r.doge + ' DOGE'],
        ['Pays', r.amountText + ' QOIN'], ['Network fee', r.feeText + ' QOIN'], ['Note on the payment', r.memo], ['Left after', r.after + ' QOIN']]) + '</dl>' +
        '<p class="small muted">Only pay once the DOGE has really arrived in MyDoge.</p><div class="msg" id="dk-msg"></div>' +
        '<div class="actions"><button class="btn" id="dk-cancel">Cancel</button><button class="btn mf-primary" id="dk-pay">Pay now</button></div>');
      $('dk-cancel').addEventListener('click', closeModal);
      $('dk-pay').addEventListener('click', function () {
        var b2 = this;
        busy(b2, true, 'Signing…');
        call('swap.pay', p).then(function (r2) {
          closeModal();
          $('mf-desk-to').value = $('mf-desk-doge').value = $('mf-desk-ref').value = '';
          $('mf-desk-quote').textContent = '–';
          toast('Paid ' + r2.amountText + ' QOIN to ' + who(r2.to));
          mfLoadDeskLog();
          refresh();
        }).catch(function (e) { msg('dk-msg', e.message, 'err'); busy(b2, false); });
      });
    }).catch(function (e) { msg('mf-desk-msg', e.message, 'err'); }).then(function () { busy(btn, false); });
  });

  // frostchain: payment links (camera apps, other apps, or #myfrost?pay=… from Android)
  function mfFromHash() {
    var h = location.hash, i = h.indexOf('?');
    if (i < 0) return;
    var m = /(?:^|&)pay=([^&]*)/.exec(h.slice(i + 1));
    try { history.replaceState(null, '', location.pathname + location.search + '#myfrost'); } catch (e) { }
    if (!m) return;
    var p = null;
    try { p = parsePay(decodeURIComponent(m[1])); } catch (e) { }
    if (!p) { toast("That isn't a frostchain: payment link"); return; }
    MF.pendingPay = p;
    mfApplyPending();
  }
  function mfApplyPending() {
    var p = MF.pendingPay, w = S.wallet;
    if (!p || !w || !w.open) return;
    MF.pendingPay = null;
    if (w.watch) { toast("This wallet is watch-only: it can't pay"); return; }
    mfGo('send');
    $('mf-to').value = p.to;
    $('mf-amount').value = p.amount;
    $('mf-memo').value = p.memo;
    mfCheckTo();
    toast('Payment link opened: check it, then tap Review');
  }

  // ---------------- start ----------------
  function start() {
    call('status').then(function (s) {
      S.status = s; S.wallet = s.wallet;
      var cores = (window.FrostBridge && window.FrostBridge.cores) ? window.FrostBridge.cores() : (navigator.hardwareConcurrency || 4);
      $('m-threads').max = Math.max(1, cores);
      $('m-threads').value = Math.max(1, Math.min(2, cores));
      $('m-threads-v').textContent = $('m-threads').value;
      route();
      if (s.wallet.open) { renderWallet(); go('home'); }
      renderNode(s);
      if (mode() === 'frostoise') renderFrostoise(s);
      refresh();
    }).catch(function (e) {
      document.body.innerHTML = '<p style="padding:24px">Can\'t reach the Frostchain node: ' + esc(e.message) + '</p>';
    });
    setInterval(function () { if (!document.hidden) refresh(); }, 1500);
  }
  /** Android back button: close a dialog, then go back to Home, then leave. */
  function back() {
    if (!$('modal').classList.contains('hidden')) { if (!$('modal').dataset.locked) closeModal(); return true; }
    if (mode() === 'wallet' && S.wallet && S.wallet.open && S.page !== 'home') { go('home'); return true; }
    if (mode() === 'myfrost' && S.wallet && S.wallet.open && MF.page !== 'home') { mfGo('home'); return true; }
    return false;
  }
  window.FrostUI = { refresh: refresh, route: route, back: back };
  start();
})();
