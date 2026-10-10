/* Remix: blend pink noise, the nine solfeggio tones and the chain's found waves, and watch them as a wireframe
   tunnel. Everything runs in this page with the Web Audio API and a 2D canvas: no libraries, no network.

   Photosensitivity: nothing on screen changes brightness faster than about twice a second. Every value the
   picture reads is smoothed over ~0.35 s, so the 4-11 Hz mining pulse is heard, never flashed. */
(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };

  // Low to high frequency, coloured low to high like light: red through violet.
  var TONES = [
    { hz: 174, color: '#ff3b5c' }, { hz: 285, color: '#ff7a2f' }, { hz: 396, color: '#ffc53d' },
    { hz: 417, color: '#b6f03c' }, { hz: 528, color: '#2ee6a0' }, { hz: 639, color: '#22d3f5' },
    { hz: 741, color: '#3d8bff' }, { hz: 852, color: '#7b5cff' }, { hz: 963, color: '#c04dff' }
  ];
  var PINK = '#ff6fb5', ICE = '#bff7ff';
  var PULSES = [4.0, 7.83, 11.11];

  /** A block's solfeggio tone: its hash, read as a number, picks one of the nine. Anyone can check it. */
  function toneOfHash(hex) {
    var n = parseInt(String(hex || '').slice(-8), 16);
    return isNaN(n) ? -1 : n % 9;
  }
  window.RemixToneOfHash = toneOfHash;

  // ---------------- settings ----------------
  var DEFAULTS = { noise: 0.35, tones: [0, 0, 0, 0, 0.6, 0, 0, 0, 0], pulse: 0.25, pulseHz: 7.83, blend: 0.55, vol: 0.5, follow: true };
  var M = load();
  function load() {
    try {
      var s = JSON.parse(localStorage.getItem('remix.mix') || 'null');
      if (s && s.tones && s.tones.length === 9) return s;
    } catch (e) { }
    return JSON.parse(JSON.stringify(DEFAULTS));
  }
  function save() { try { localStorage.setItem('remix.mix', JSON.stringify(M)); } catch (e) { } }

  // ---------------- audio ----------------
  var A = null; // the running graph
  function pinkBuffer(ctx, seconds) {
    var len = Math.floor(ctx.sampleRate * seconds), buf = ctx.createBuffer(2, len, ctx.sampleRate);
    for (var ch = 0; ch < 2; ch++) {
      var d = buf.getChannelData(ch), b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0;
      for (var i = 0; i < len; i++) { // Paul Kellet's pink filter over white noise
        var w = Math.random() * 2 - 1;
        b0 = 0.99886 * b0 + w * 0.0555179; b1 = 0.99332 * b1 + w * 0.0750759; b2 = 0.96900 * b2 + w * 0.1538520;
        b3 = 0.86650 * b3 + w * 0.3104856; b4 = 0.55000 * b4 + w * 0.5329522; b5 = -0.7616 * b5 - w * 0.0168980;
        d[i] = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362) * 0.11;
        b6 = w * 0.115926;
      }
    }
    return buf;
  }
  function start() {
    var Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) { note('This browser can\'t play the mix.'); return; }
    var ctx = new Ctx();
    var master = ctx.createGain(), comp = ctx.createDynamicsCompressor(), scope = ctx.createAnalyser();
    comp.threshold.value = -14; comp.ratio.value = 4;
    master.connect(comp); comp.connect(scope); scope.connect(ctx.destination);
    scope.fftSize = 2048;
    master.gain.value = 0;

    var noiseBus = ctx.createGain(), toneBus = ctx.createGain();
    noiseBus.connect(master); toneBus.connect(master);

    var src = ctx.createBufferSource();
    src.buffer = pinkBuffer(ctx, 8); src.loop = true;
    var noiseGain = ctx.createGain(); noiseGain.gain.value = 0;
    src.connect(noiseGain); noiseGain.connect(noiseBus); src.start();

    var tones = TONES.map(function (t, i) {
      var o = ctx.createOscillator(), g = ctx.createGain();
      o.type = 'sine'; o.frequency.value = t.hz; g.gain.value = 0;
      var out = g;
      if (ctx.createStereoPanner) { var p = ctx.createStereoPanner(); p.pan.value = -0.6 + i * 0.15; g.connect(p); out = p; }
      o.connect(g); out.connect(toneBus); o.start();
      return g;
    });

    // The found wave: Frostoise's 880 Hz hum, its loudness swelling at the mining tone.
    var hum = ctx.createOscillator(), am = ctx.createGain(), lfo = ctx.createOscillator(), depth = ctx.createGain(), pulseGain = ctx.createGain();
    hum.frequency.value = 880; am.gain.value = 0.5; lfo.frequency.value = M.pulseHz; depth.gain.value = 0.5; pulseGain.gain.value = 0;
    hum.connect(am); lfo.connect(depth); depth.connect(am.gain); am.connect(pulseGain); pulseGain.connect(master);
    hum.start(); lfo.start();

    A = { ctx: ctx, master: master, scope: scope, noiseBus: noiseBus, toneBus: toneBus, noiseGain: noiseGain, tones: tones, lfo: lfo, pulseGain: pulseGain, wave: new Float32Array(scope.fftSize) };
    apply();
    $('rx-play').classList.add('hidden');
    $('rx-stop').classList.remove('hidden');
  }
  function stop() {
    if (!A) return;
    var a = A; A = null;
    a.master.gain.setTargetAtTime(0, a.ctx.currentTime, 0.05);
    setTimeout(function () { try { a.ctx.close(); } catch (e) { } }, 300);
    $('rx-play').classList.remove('hidden');
    $('rx-stop').classList.add('hidden');
  }
  /** Sends the faders to the sound, gliding over ~80 ms so nothing clicks. */
  function apply() {
    save();
    if (!A) return;
    var t = A.ctx.currentTime, k = 0.08;
    // Equal-power blend: the middle keeps the same loudness as either end.
    A.noiseBus.gain.setTargetAtTime(Math.cos(M.blend * Math.PI / 2), t, k);
    A.toneBus.gain.setTargetAtTime(Math.sin(M.blend * Math.PI / 2), t, k);
    A.noiseGain.gain.setTargetAtTime(M.noise * 0.9, t, k);
    for (var i = 0; i < 9; i++) A.tones[i].gain.setTargetAtTime(M.tones[i] * 0.11, t, k);
    A.pulseGain.gain.setTargetAtTime(M.pulse * 0.22, t, k);
    A.lfo.frequency.setTargetAtTime(M.pulseHz, t, k);
    A.master.gain.setTargetAtTime(M.vol * M.vol * 0.9, t, k);
  }

  // ---------------- the desk ----------------
  var built = false;
  function build() {
    if (built) return;
    built = true;
    var strips = $('rx-tones');
    TONES.forEach(function (t, i) {
      var s = document.createElement('label');
      s.className = 'rx-strip';
      s.style.setProperty('--c', t.color);
      s.innerHTML = '<input class="rx-fader" type="range" min="0" max="1" step="0.01" aria-label="' + t.hz + ' hertz">' +
        '<b>' + t.hz + '</b><small>Hz</small>';
      var input = s.querySelector('input');
      input.value = M.tones[i];
      input.addEventListener('input', function () { M.tones[i] = +input.value; apply(); });
      strips.appendChild(s);
    });
    bindRange('rx-noise', 'noise');
    bindRange('rx-pulse', 'pulse');
    bindRange('rx-blend', 'blend');
    bindRange('rx-vol', 'vol');
    $('rx-pulse-hz').addEventListener('click', function () {
      // Cycles the three mining tones: 4.0, 7.83, 11.11 Hz.
      var i = PULSES.indexOf(M.pulseHz);
      M.pulseHz = PULSES[(i + 1) % PULSES.length];
      showRate(); apply();
    });
    showRate();
    var f = $('rx-follow');
    f.checked = !!M.follow;
    f.addEventListener('change', function () { M.follow = f.checked; save(); if (M.follow) layerBlock(); });
    $('rx-play').addEventListener('click', start);
    $('rx-stop').addEventListener('click', stop);
    Array.prototype.forEach.call(document.querySelectorAll('[data-rx-preset]'), function (b) {
      b.addEventListener('click', function () { preset(b.dataset.rxPreset); });
    });
  }
  function bindRange(id, key) {
    var el = $(id);
    el.value = M[key];
    el.addEventListener('input', function () { M[key] = +el.value; apply(); });
  }
  function syncDesk() {
    var faders = document.querySelectorAll('#rx-tones input');
    for (var i = 0; i < faders.length; i++) faders[i].value = M.tones[i];
    $('rx-noise').value = M.noise; $('rx-pulse').value = M.pulse; $('rx-blend').value = M.blend;
    $('rx-follow').checked = !!M.follow;
    showRate();
  }
  function showRate() {
    var b = $('rx-pulse-hz');
    b.textContent = M.pulseHz === 4 ? '4.0' : String(M.pulseHz);
    b.setAttribute('aria-label', 'Pulse rate ' + b.textContent + ' hertz: press to change');
  }
  function preset(name) {
    if (name === 'block') { M.follow = true; layerBlock(true); return; }
    M.follow = false;
    if (name === 'last9') {
      M.tones = [0, 0, 0, 0, 0, 0, 0, 0, 0];
      chain.recent.forEach(function (b) { var i = toneOfHash(b.hash); if (i >= 0) M.tones[i] = Math.min(1, M.tones[i] + 0.3); });
      M.blend = 0.7; M.noise = 0.25; M.pulse = 0.2;
    } else if (name === 'spectrum') {
      M.tones = [0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5]; M.blend = 0.75; M.noise = 0.2; M.pulse = 0;
    } else if (name === 'schumann') {
      M.tones = [0, 0, 0, 0, 0, 0, 0, 0, 0]; M.pulseHz = 7.83; M.pulse = 0.7; M.noise = 0.45; M.blend = 0.3;
    } else if (name === 'silence') {
      M.tones = [0, 0, 0, 0, 0, 0, 0, 0, 0]; M.noise = 0; M.pulse = 0;
    }
    syncDesk(); apply();
  }

  // ---------------- the chain's found waves ----------------
  var chain = { height: -1, hash: '', hz: 0, tone: -1, recent: [] };
  var pollTimer = null;
  function poll() {
    if (!window.FrostCall) return;
    window.FrostCall('node.blocks', { count: 9 }).then(function (rows) {
      chain.recent = rows || [];
      var tip = chain.recent[0];
      if (!tip) { showChain(); return; }
      var changed = tip.height !== chain.height;
      chain.height = tip.height; chain.hash = tip.hash; chain.hz = tip.hz; chain.tone = toneOfHash(tip.hash);
      showChain();
      if (changed && M.follow) layerBlock();
    }).catch(function () { showChain(); });
  }
  function showChain() {
    if (chain.height < 0) {
      $('rx-block').textContent = 'No blocks yet';
      $('rx-found').textContent = 'Syncing';
      $('rx-its').classList.add('hidden');
      return;
    }
    $('rx-block').textContent = 'Block ' + chain.height.toLocaleString();
    $('rx-found').textContent = 'Found at ' + (chain.hz ? chain.hz.toFixed(2).replace(/\.?0+$/, '') : '–') + ' Hz';
    var its = $('rx-its');
    its.classList.toggle('hidden', chain.tone < 0);
    if (chain.tone >= 0) {
      its.textContent = 'Its tone ' + TONES[chain.tone].hz + ' Hz';
      its.style.setProperty('--c', TONES[chain.tone].color);
    }
  }
  /** Brings the newest block's tone up and its found pulse in, keeping the rest of the mix. */
  function layerBlock(fromPreset) {
    if (chain.tone < 0) return;
    for (var i = 0; i < 9; i++) M.tones[i] = i === chain.tone ? Math.max(0.65, M.tones[i]) : M.tones[i] * 0.6;
    var near = PULSES.reduce(function (a, b) { return Math.abs(b - chain.hz) < Math.abs(a - chain.hz) ? b : a; }, 7.83);
    if (chain.hz) M.pulseHz = near;
    if (fromPreset) { M.pulse = Math.max(M.pulse, 0.3); M.blend = 0.6; }
    syncDesk(); apply();
  }
  function note(t) { var el = $('rx-note'); el.textContent = t; el.classList.toggle('hidden', !t); }

  // ---------------- the picture ----------------
  var cv, g, W = 0, H = 0, dpr = 1, raf = 0, last = 0, z0 = 0, spin = 0;
  var sm = { noise: 0, pulse: 0, tones: [0, 0, 0, 0, 0, 0, 0, 0, 0], level: 0 }; // smoothed
  var stars = [];
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = cv.clientWidth; H = cv.clientHeight;
    cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr);
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  function hexA(hex, a) {
    var n = parseInt(hex.slice(1), 16);
    return 'rgba(' + (n >> 16) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + a.toFixed(3) + ')';
  }
  function frame(now) {
    raf = requestAnimationFrame(frame);
    var dt = Math.min(0.05, (now - (last || now)) / 1000); last = now;
    var on = !!A, k = 1 - Math.exp(-dt / 0.35), blendT = Math.sin(M.blend * Math.PI / 2), blendN = Math.cos(M.blend * Math.PI / 2);
    sm.noise += ((on ? M.noise * blendN : 0) - sm.noise) * k;
    sm.pulse += ((on ? M.pulse : 0) - sm.pulse) * k;
    var lv = sm.noise * 0.6 + sm.pulse * 0.4;
    for (var i = 0; i < 9; i++) { sm.tones[i] += ((on ? M.tones[i] * blendT : 0) - sm.tones[i]) * k; lv += sm.tones[i] * 0.25; }
    sm.level += (Math.min(1, lv) - sm.level) * k;

    var speed = reduce ? 0.15 : 0.35 + sm.level * 1.6;
    z0 = (z0 + dt * speed) % 1;
    spin += dt * (reduce ? 0.02 : 0.08 + sm.pulse * 0.25);

    // The void: a fixed gradient, never pulsed.
    var bg = g.createRadialGradient(W / 2, H * 0.45, 0, W / 2, H * 0.45, Math.max(W, H) * 0.75);
    bg.addColorStop(0, '#160c3d'); bg.addColorStop(1, '#07051a');
    g.globalCompositeOperation = 'source-over';
    g.fillStyle = bg; g.fillRect(0, 0, W, H);
    g.globalCompositeOperation = 'lighter';

    var cx = W / 2, cy = H * 0.42, R = Math.min(W, H) * 0.62, rings = 22;
    // Tunnel: nonagon rings, one corner per tone, flying toward you; the strongest tones colour it.
    var lead = strongest();
    var col = lead >= 0 ? TONES[lead].color : '#6f63c9';
    var prev = null;
    for (var r = rings - 1; r >= 0; r--) {
      var z = (r + 1 - z0) / rings; // 1 far .. 0 near
      var scale = 0.06 / (z * z + 0.02);
      var rad = R * scale * 0.35;
      if (rad > Math.max(W, H) * 1.6) continue;
      var a = Math.max(0, Math.min(1, (1 - z) * 1.2)) * 0.55;
      var tw = spin + z * 1.4; // twist set by depth, so rings hand over seamlessly as they fly in
      var pts = [];
      for (var v = 0; v < 9; v++) {
        var ang = tw + v * Math.PI * 2 / 9 - Math.PI / 2;
        pts.push([cx + Math.cos(ang) * rad, cy + Math.sin(ang) * rad * 0.82]);
      }
      g.strokeStyle = hexA(col, a); g.lineWidth = 1.1;
      g.beginPath();
      for (v = 0; v <= 9; v++) { var p = pts[v % 9]; if (v) g.lineTo(p[0], p[1]); else g.moveTo(p[0], p[1]); }
      g.stroke();
      if (prev) {
        for (v = 0; v < 9; v++) {
          var tv = sm.tones[v];
          g.strokeStyle = hexA(tv > 0.02 ? TONES[v].color : col, a * (0.35 + tv * 0.9));
          g.lineWidth = 0.8 + tv * 1.6;
          g.beginPath(); g.moveTo(prev[v][0], prev[v][1]); g.lineTo(pts[v][0], pts[v][1]); g.stroke();
        }
      }
      prev = pts;
    }

    // Pink noise: a stream of sparks, as many as the noise is loud.
    var want = Math.round(sm.noise * 260);
    while (stars.length < want) stars.push(newStar());
    if (stars.length > want) stars.length = want;
    g.fillStyle = hexA(PINK, 0.7);
    for (i = 0; i < stars.length; i++) {
      var s = stars[i];
      s.z -= dt * (reduce ? 0.05 : 0.25 + sm.level * 0.5);
      if (s.z <= 0.03) { stars[i] = s = newStar(); s.z = 1; }
      var sx = cx + s.x / s.z * R * 0.12, sy = cy + s.y / s.z * R * 0.12;
      var size = Math.max(0.6, 1.8 * (1 - s.z));
      g.fillRect(sx, sy, size, size);
    }

    // Tone orbits: each live tone is a nonagon turning at a speed set by its pitch.
    for (i = 0; i < 9; i++) {
      var amt = sm.tones[i];
      if (amt < 0.01) continue;
      var orad = Math.min(W, H) * (0.07 + i * 0.022);
      var rot = spin * (0.4 + i * 0.18) * (i % 2 ? -1 : 1);
      g.strokeStyle = hexA(TONES[i].color, 0.25 + amt * 0.6);
      g.lineWidth = 1 + amt * 2;
      g.beginPath();
      for (v = 0; v <= 9; v++) {
        var an = rot + v * Math.PI * 2 / 9;
        var px = cx + Math.cos(an) * orad, py = cy + Math.sin(an) * orad;
        if (v) g.lineTo(px, py); else g.moveTo(px, py);
      }
      g.stroke();
    }

    // The found wave: an ice lattice that turns faster as the pulse comes up.
    if (sm.pulse > 0.01) {
      var lr = Math.min(W, H) * 0.3;
      g.strokeStyle = hexA(ICE, 0.12 + sm.pulse * 0.3); g.lineWidth = 1;
      g.beginPath();
      for (v = 0; v < 9; v++) {
        var a1 = -spin * 0.6 + v * Math.PI * 2 / 9, a2 = -spin * 0.6 + ((v + 4) % 9) * Math.PI * 2 / 9;
        g.moveTo(cx + Math.cos(a1) * lr, cy + Math.sin(a1) * lr); g.lineTo(cx + Math.cos(a2) * lr, cy + Math.sin(a2) * lr);
      }
      g.stroke();
    }

    // The scope: the mix itself, drawn as a ring around the centre.
    if (A) {
      A.scope.getFloatTimeDomainData(A.wave);
      var n = 256, step = Math.floor(A.wave.length / n), base = Math.min(W, H) * 0.045 + sm.level * 8;
      g.strokeStyle = 'rgba(236,233,255,0.75)'; g.lineWidth = 1.2;
      g.beginPath();
      for (v = 0; v <= n; v++) {
        var sample = A.wave[(v % n) * step] || 0, ang2 = v / n * Math.PI * 2 - Math.PI / 2;
        var rr = base + sample * base * 1.8;
        var qx = cx + Math.cos(ang2) * rr, qy = cy + Math.sin(ang2) * rr;
        if (v) g.lineTo(qx, qy); else g.moveTo(qx, qy);
      }
      g.stroke();
    } else {
      g.strokeStyle = 'rgba(236,233,255,0.35)'; g.lineWidth = 1;
      g.beginPath(); g.arc(cx, cy, Math.min(W, H) * 0.045, 0, Math.PI * 2); g.stroke();
    }
    g.globalCompositeOperation = 'source-over';
  }
  function strongest() {
    var best = -1, v = 0.03;
    for (var i = 0; i < 9; i++) if (sm.tones[i] > v) { v = sm.tones[i]; best = i; }
    return best;
  }
  function newStar() {
    var a = Math.random() * Math.PI * 2, d = 0.2 + Math.random() * 1.6;
    return { x: Math.cos(a) * d, y: Math.sin(a) * d * 0.8, z: 0.2 + Math.random() * 0.8 };
  }

  // ---------------- screen ----------------
  window.Remix = {
    show: function () {
      build();
      if (!cv) { cv = $('rx-stage'); g = cv.getContext('2d'); window.addEventListener('resize', function () { if (raf) resize(); }); }
      resize();
      if (!raf) { last = 0; raf = requestAnimationFrame(frame); }
      poll();
      clearInterval(pollTimer);
      pollTimer = setInterval(poll, 15000);
    },
    hide: function () {
      stop();
      clearInterval(pollTimer); pollTimer = null;
      if (raf) { cancelAnimationFrame(raf); raf = 0; }
    }
  };
  document.addEventListener('visibilitychange', function () {
    if (!raf && !document.hidden && cv && !$('remix').classList.contains('hidden')) { last = 0; raf = requestAnimationFrame(frame); }
    else if (raf && document.hidden) { cancelAnimationFrame(raf); raf = 0; }
  });
})();
