(function () {
  'use strict';
  // Temporal v1, the same rules as core/src/main/java/com/frostether/frostchain/Temporal.java:
  //   fingerprint = first 20 bytes of SHA-256("temporal/v1\n" + opens + "\n" + salt + "\n" + text), in hex
  //   memo        = "T1 " + opens + " " + fingerprint, on a payment of at least 1 QNR to the burn address
  var BURN = '{{BURN_ADDRESS}}';
  var GENESIS = 1791481020;          // 13:37 Eastern, Thursday 8 October 2026
  var MIN_LEAD = 15 * 60;            // a seal needs a few blocks to land before it opens
  var MAX_TEXT = 4000;
  var STORE = 'temporal.capsules';
  var $ = function (id) { return document.getElementById(id); };
  var now = function () { return Math.floor(Date.now() / 1000); };

  // ---------- small helpers ----------
  function hex(bytes) { return Array.prototype.map.call(bytes, function (b) { return (b < 16 ? '0' : '') + b.toString(16); }).join(''); }
  function utf8(s) { return new TextEncoder().encode(s); }
  function fingerprint(opens, salt, text) {
    return crypto.subtle.digest('SHA-256', utf8('temporal/v1\n' + opens + '\n' + salt + '\n' + text))
      .then(function (h) { return hex(new Uint8Array(h).slice(0, 20)); });
  }
  function newSalt() { var b = new Uint8Array(16); crypto.getRandomValues(b); return hex(b); }
  function memoFor(c) { return 'T1 ' + c.opens + ' ' + c.fp; }
  function payLink(c) { return 'frostchain:' + BURN + '?amount=1&memo=' + encodeURIComponent(memoFor(c)); }
  function b64url(s) {
    var bin = ''; utf8(s).forEach(function (b) { bin += String.fromCharCode(b); });
    return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
  function unb64url(s) {
    var bin = atob(s.replace(/-/g, '+').replace(/_/g, '/'));
    var bytes = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return new TextDecoder().decode(bytes);
  }
  function when(t) {
    try { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(t * 1000)); }
    catch (e) { return new Date(t * 1000).toLocaleString(); }
  }
  function span(sec) {
    sec = Math.max(0, sec);
    var d = Math.floor(sec / 86400), h = Math.floor(sec % 86400 / 3600), m = Math.floor(sec % 3600 / 60);
    if (d >= 365) { var y = Math.floor(d / 365), rd = d - y * 365; return y + (y === 1 ? ' year' : ' years') + (rd ? ', ' + rd + (rd === 1 ? ' day' : ' days') : ''); }
    if (d > 0) return d + (d === 1 ? ' day' : ' days') + (h ? ', ' + h + ' h' : '');
    if (h > 0) return h + ' h ' + m + ' min';
    return m + ' min';
  }
  function localInput(t) {
    var d = new Date(t * 1000), p = function (n) { return (n < 10 ? '0' : '') + n; };
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + 'T' + p(d.getHours()) + ':' + p(d.getMinutes());
  }
  function fromInput(v) { var t = new Date(v).getTime(); return isNaN(t) ? NaN : Math.floor(t / 1000); }
  function say(id, text, bad) { var el = $(id); el.textContent = text || ''; el.classList.toggle('bad', !!bad); }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

  // ---------- capsules kept in this browser ----------
  function load() {
    try { var l = JSON.parse(localStorage.getItem(STORE) || '[]'); return Array.isArray(l) ? l : []; } catch (e) { return []; }
  }
  var capsules = load();
  function save() { try { localStorage.setItem(STORE, JSON.stringify(capsules)); } catch (e) { /* private window: the file is the copy */ } }
  function add(c) {
    if (!capsules.some(function (x) { return x.fp === c.fp; })) capsules.unshift(c);
    save(); render();
  }

  // ---------- a node, if one is in reach: ?node=… or the public explorer ----------
  var params = new URLSearchParams(location.search);
  var API = (params.get('node') || 'https://explorer.finux.tech').replace(/\/+$/, '');
  var live = false;
  function api(path) {
    var ctl = window.AbortController ? new AbortController() : null;
    var timer = setTimeout(function () { if (ctl) ctl.abort(); }, 6000);
    return fetch(API + path, { cache: 'no-store', signal: ctl ? ctl.signal : undefined }).then(function (r) {
      clearTimeout(timer);
      return r.json().then(function (body) {
        if (!r.ok) throw new Error(body && body.error ? body.error : 'the node said ' + r.status);
        return body;
      });
    }, function (e) { clearTimeout(timer); throw e; });
  }
  function probe() {
    api('/api/explorer/temporal').then(function (t) {
      live = true;
      $('burned').textContent = t.burnedText + ' QNR in ' + t.count + (t.count === 1 ? ' seal' : ' seals') + '.';
      render();
    }, function () {
      live = false;
      $('burned').innerHTML = 'Shows here when a live node is in reach. <a href="/node/">Run one</a>.';
    });
  }

  // ---------- seal ----------
  var sealOpen = $('seal-open');
  function defaultOpen() { var t = now() + 365 * 86400; return t - t % 60; }
  sealOpen.value = localInput(defaultOpen());
  sealOpen.min = localInput(now() + MIN_LEAD);
  function dial() {
    var t = fromInput(sealOpen.value);
    $('seal-dial').textContent = isNaN(t) ? '' : t <= now() ? 'That moment has passed.' : 'Opens in ' + span(t - now() + 59) + '.';
  }
  sealOpen.addEventListener('input', dial);
  dial();
  setInterval(dial, 30000);

  var current = null;
  $('seal-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var text = $('seal-text').value, opens = fromInput(sealOpen.value);
    if (!text.trim()) { say('seal-msg', 'Write a message first.', true); return; }
    if (text.length > MAX_TEXT) { say('seal-msg', 'Keep it under ' + MAX_TEXT + ' characters.', true); return; }
    if (isNaN(opens)) { say('seal-msg', 'Pick when it opens.', true); return; }
    if (opens < now() + MIN_LEAD) { say('seal-msg', 'Pick a time at least 15 minutes from now, so the seal lands in a block before it opens.', true); return; }
    if (!window.crypto || !crypto.subtle) { say('seal-msg', 'This browser can’t make fingerprints here. Try Chrome or Firefox.', true); return; }
    var c = { v: 1, opens: opens, salt: newSalt(), text: text, made: now() };
    fingerprint(c.opens, c.salt, c.text).then(function (fp) {
      c.fp = fp;
      add(c);
      showSealed(c);
      $('seal-text').value = '';
      say('seal-msg', '');
    });
  });

  function qrSvg(text) {
    if (!window.QR) return '';
    var m = window.QR.encode(text), n = m.length, q = 4, size = n + 2 * q, d = '';
    for (var y = 0; y < n; y++) {
      for (var x = 0; x < n; x++) {
        if (!m[y][x]) continue;
        var run = 1; while (x + run < n && m[y][x + run]) run++;
        d += 'M' + (x + q) + ' ' + (y + q) + 'h' + run + 'v1h-' + run + 'z'; x += run - 1;
      }
    }
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + size + ' ' + size + '" shape-rendering="crispEdges"><rect width="' + size + '" height="' + size + '" fill="#fff"/><path fill="#1a0f22" d="' + d + '"/></svg>';
  }

  function showSealed(c) {
    current = c;
    $('pay-link').href = payLink(c);
    $('pay-qr').innerHTML = qrSvg(payLink(c));
    $('burn-to').textContent = BURN;
    $('memo-out').textContent = memoFor(c);
    $('sealed').hidden = false;
    $('sealed').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function capsuleFile(c) {
    return JSON.stringify({ temporal: 1, opens: c.opens, salt: c.salt, text: c.text, fingerprint: c.fp, memo: memoFor(c), burnAddress: BURN }, null, 2) + '\n';
  }
  function download(c) {
    var blob = new Blob([capsuleFile(c)], { type: 'application/json' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'capsule-' + c.fp.slice(0, 8) + '.temporal.json';
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 2000);
  }
  function code(c) { return 'temporal1:' + b64url(JSON.stringify({ v: 1, opens: c.opens, salt: c.salt, text: c.text })); }
  $('save-file').addEventListener('click', function () { if (current) download(current); });
  $('copy-code').addEventListener('click', function () { if (current) window.copyText(code(current), this); });

  // ---------- your capsules ----------
  function render() {
    var ul = $('capsules');
    if (!capsules.length) { ul.innerHTML = '<li class="empty">None yet. Seal one above, or open a capsule file.</li>'; return; }
    ul.innerHTML = capsules.map(function (c, i) {
      var open = now() >= c.opens;
      var head = '<div class="cap-head"><span class="cap-state ' + (open ? 'is-open' : '') + '">' + (open ? 'Open' : 'Shut') + '</span>' +
        '<span class="mono">' + c.fp.slice(0, 12) + '…</span></div>' +
        '<p class="cap-when">' + (open ? 'Opened ' + esc(when(c.opens)) : 'Opens ' + esc(when(c.opens)) + ', in ' + span(c.opens - now())) + '</p>';
      var body = c.shown ? '<blockquote class="cap-text">' + esc(c.text).replace(/\n/g, '<br>') + '</blockquote><p class="cap-proof" id="proof-' + i + '"></p>' : '';
      var buttons = open
        ? '<button type="button" class="btn small" data-act="show" data-i="' + i + '">' + (c.shown ? 'Close it' : 'Read it') + '</button>' +
          (live ? '<button type="button" class="btn quiet small" data-act="check" data-i="' + i + '">Check the seal</button>' : '')
        : '<button type="button" class="btn quiet small" data-act="pay" data-i="' + i + '">Pay the gas</button>';
      buttons += '<button type="button" class="btn quiet small" data-act="file" data-i="' + i + '">Save file</button>' +
        '<button type="button" class="btn quiet small" data-act="forget" data-i="' + i + '">Forget</button>';
      return '<li class="capsule">' + head + body + '<div class="cta">' + buttons + '</div></li>';
    }).join('');
  }
  $('capsules').addEventListener('click', function (e) {
    var b = e.target.closest('button[data-act]'); if (!b) return;
    var i = Number(b.getAttribute('data-i')), c = capsules[i], act = b.getAttribute('data-act');
    if (!c) return;
    if (act === 'show') { c.shown = !c.shown; render(); }
    else if (act === 'pay') showSealed(c);
    else if (act === 'file') download(c);
    else if (act === 'forget') {
      if (b.getAttribute('data-sure')) { capsules.splice(i, 1); save(); render(); }
      else { b.setAttribute('data-sure', '1'); b.textContent = 'Tap again to forget it'; }
    } else if (act === 'check') {
      c.shown = true; render();
      var out = $('proof-' + i);
      out.textContent = 'Looking for the seal…';
      api('/api/explorer/temporal?fp=' + c.fp).then(function (s) {
        if (!s.seal) { out.textContent = 'No seal with this fingerprint on the chain yet.'; return; }
        var late = s.seal.time > c.opens;
        out.textContent = (late ? 'Sealed after it opened: ' : 'Proven: sealed in block #') + (late ? 'block #' : '') + s.seal.height + ', ' + when(s.seal.time) + '.';
      }, function (err) { out.textContent = 'Couldn’t reach the node: ' + err.message; });
    }
  });

  function importCapsule(obj) {
    if (!obj || typeof obj.text !== 'string' || !/^[0-9a-f]{32}$/.test(String(obj.salt)) || !isFinite(obj.opens)) {
      throw new Error('That isn’t a Temporal capsule.');
    }
    var c = { v: 1, opens: Math.floor(obj.opens), salt: obj.salt, text: obj.text, made: now() };
    return fingerprint(c.opens, c.salt, c.text).then(function (fp) {
      if (obj.fingerprint && obj.fingerprint !== fp) throw new Error('This capsule was changed after it was sealed: its fingerprint doesn’t match.');
      c.fp = fp; add(c); say('mine-msg', 'Added. It opens ' + when(c.opens) + '.');
    });
  }
  $('load-file').addEventListener('change', function () {
    var f = this.files && this.files[0]; if (!f) return;
    f.text().then(function (t) { return importCapsule(JSON.parse(t)); })
      .catch(function (e) { say('mine-msg', e.message || 'That file isn’t a capsule.', true); });
    this.value = '';
  });
  $('paste-open').addEventListener('click', function () { $('paste-box').hidden = false; $('paste-text').focus(); });
  $('paste-go').addEventListener('click', function () {
    var v = $('paste-text').value.trim();
    try {
      if (v.indexOf('temporal1:') !== 0) throw new Error('A capsule code starts with temporal1:');
      importCapsule(JSON.parse(unb64url(v.slice(10)))).then(function () { $('paste-text').value = ''; $('paste-box').hidden = true; },
        function (e) { say('mine-msg', e.message, true); });
    } catch (e) { say('mine-msg', e.message || 'That code didn’t read as a capsule.', true); }
  });

  // ---------- go back ----------
  var backTime = $('back-time');
  backTime.min = localInput(GENESIS);
  backTime.max = localInput(now());
  backTime.value = localInput(now() - 3600);
  var arrived = null;
  $('back-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var t = fromInput(backTime.value);
    if (isNaN(t)) { say('back-msg', 'Pick a moment.', true); return; }
    if (!live) {
      $('back-msg').innerHTML = 'Going back needs a live node, and none is in reach yet. <a href="/node/">Run one</a>, or open Temporal in Graysons Vault.';
      return;
    }
    say('back-msg', 'Travelling…');
    api('/api/explorer/at?t=' + Math.max(GENESIS, t)).then(function (r) {
      arrived = r.block;
      var b = r.block;
      $('arr-when').textContent = when(b.time);
      $('arr-title').textContent = b.height === 0 ? 'The start of the chain' : 'Block #' + b.height;
      $('arr-miner').textContent = b.height === 0 ? 'Nobody: genesis is fixed in the code' : (b.minerName ? '@' + b.minerName.replace(/\.frostchain$/, '') : b.miner);
      $('arr-tone').textContent = b.reso ? (b.reso.hzMilli / 1000).toFixed(2) + ' Hz, ' + b.reso.band : 'None';
      $('arr-mined').textContent = r.minedText + ' QNR';
      $('arr-seals').textContent = String(r.seals);
      $('arr-play').disabled = !b.reso;
      $('arrival').hidden = false;
      say('back-msg', t < GENESIS ? 'That’s before the chain started, so here’s where it began.' : '');
    }, function (err) { say('back-msg', 'The node didn’t answer: ' + err.message, true); });
  });

  // The same pulse Frostoise listens for: an 880 Hz hum swelling and fading at the block's tone.
  var audio = { ac: null, master: null, nodes: null };
  function pulse(hz, seconds) {
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return Promise.reject(new Error('this browser can’t play sound'));
    if (!audio.ac) { audio.ac = new AC(); audio.master = audio.ac.createGain(); audio.master.gain.value = 0; audio.master.connect(audio.ac.destination); }
    var ac = audio.ac;
    return (ac.state === 'suspended' ? ac.resume() : Promise.resolve()).then(function () {
      stopPulse();
      var carrier = ac.createOscillator(); carrier.frequency.value = 880;
      var amp = ac.createGain(); amp.gain.value = 0.5;
      var lfo = ac.createOscillator(); lfo.frequency.value = hz;
      var depth = ac.createGain(); depth.gain.value = 0.5;
      lfo.connect(depth); depth.connect(amp.gain); carrier.connect(amp); amp.connect(audio.master);
      carrier.start(); lfo.start();
      audio.nodes = [carrier, lfo, amp, depth];
      audio.master.gain.setTargetAtTime(0.5, ac.currentTime, 0.05);
      return new Promise(function (res) { setTimeout(function () { stopPulse(); res(); }, seconds * 1000); });
    });
  }
  function stopPulse() {
    if (audio.master) audio.master.gain.setTargetAtTime(0, audio.ac.currentTime, 0.03);
    var n = audio.nodes; audio.nodes = null;
    if (n) setTimeout(function () { n.forEach(function (x) { try { if (x.stop) x.stop(); } catch (e) { } try { x.disconnect(); } catch (e) { } }); }, 150);
  }
  $('arr-play').addEventListener('click', function () {
    if (!arrived || !arrived.reso) return;
    pulse(arrived.reso.hzMilli / 1000, 4).catch(function (e) { say('back-msg', 'No sound: ' + e.message, true); });
  });
  var riding = false;
  $('arr-ride').addEventListener('click', function () {
    if (!arrived || riding) return;
    riding = true;
    var from = arrived.height;
    api('/api/explorer/blocks?before=' + (from + 13) + '&count=12').then(function (list) {
      var blocks = list.slice().reverse().filter(function (b) { return b.height > from; });
      if (!blocks.length) { $('arr-ride-now').textContent = 'Nothing after this yet: you’re at the newest block.'; riding = false; return; }
      var i = 0;
      (function next() {
        if (i >= blocks.length) { $('arr-ride-now').textContent = 'Back to #' + blocks[blocks.length - 1].height + ', ' + when(blocks[blocks.length - 1].time) + '.'; riding = false; return; }
        var b = blocks[i++];
        $('arr-ride-now').textContent = '#' + b.height + '  ' + when(b.time) + '  ' + (b.reso ? (b.reso.hzMilli / 1000).toFixed(2) + ' Hz' : '');
        (b.reso ? pulse(b.reso.hzMilli / 1000, 1.5) : new Promise(function (r) { setTimeout(r, 600); })).then(next, function () { riding = false; });
      })();
    }, function (err) { riding = false; say('back-msg', 'The node didn’t answer: ' + err.message, true); });
  });

  render();
  probe();
  setInterval(render, 60000);
})();
