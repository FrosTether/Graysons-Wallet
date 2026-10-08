(function () {
  'use strict';
  var LAUNCH = 1791481020 * 1000; // Thursday 8 October 2026, 13:37 Eastern: the v0.4 genesis
  var $ = function (id) { return document.getElementById(id); };
  var root = document.documentElement;

  // ---------- launch countdown ----------
  function two(n) { return (n < 10 ? '0' : '') + n; }
  function tick() {
    var left = LAUNCH - Date.now();
    var open = left <= 0;
    root.classList.toggle('is-open', open);
    if (open) { return false; }
    var s = Math.floor(left / 1000);
    var d = Math.floor(s / 86400); s -= d * 86400;
    var h = Math.floor(s / 3600); s -= h * 3600;
    var m = Math.floor(s / 60); s -= m * 60;
    $('countdown').textContent = (d > 0 ? d + (d === 1 ? ' day ' : ' days ') : '') + two(h) + ':' + two(m) + ':' + two(s);
    return true;
  }
  if (tick()) {
    var timer = setInterval(function () { if (!tick()) clearInterval(timer); }, 1000);
  }
  try {
    var opts = { weekday: 'long', hour: 'numeric', minute: '2-digit' };
    var here = new Intl.DateTimeFormat(undefined, opts).format(new Date(LAUNCH));
    var eastern = new Intl.DateTimeFormat(undefined, Object.assign({ timeZone: 'America/New_York' }, opts)).format(new Date(LAUNCH));
    if (here !== eastern && !root.classList.contains('is-open')) {
      $('local-time').textContent = 'That’s ' + here + ' where you are.';
    }
  } catch (e) { /* no Intl time zones: the Eastern time stands alone */ }

  // ---------- tone station ----------
  // The same pulse as Frostoise's own tone player: an 880 Hz hum (small speakers barely play lower) whose loudness swells and fades
  // at the mining tone, which is what the phone's microphone listens for.
  var tone = { hz: 7.83, ac: null, master: null, nodes: null, on: false };
  var station = $('station'), play = $('play'), vol = $('vol'), msg = $('tone-msg');

  function label(hz) { return (hz === 4 ? '4.0' : String(hz)) + ' Hz'; }

  function drawScope(hz) {
    // One second of the envelope, mirrored like an audio waveform. Static: nothing flashes at the tone rate.
    var top = [], bottom = [], steps = 400, mid = 104, amp = 96;
    for (var i = 0; i <= steps; i++) {
      var t = i / steps;
      var e = 0.06 + 0.94 * (0.5 - 0.5 * Math.cos(2 * Math.PI * hz * t));
      var x = (t * 1000).toFixed(1);
      top.push(x + ' ' + (mid - amp * e).toFixed(1));
      bottom.unshift(x + ' ' + (mid + amp * e).toFixed(1));
    }
    $('env').setAttribute('d', 'M' + top.join(' L') + ' L' + bottom.join(' L') + ' Z');
    $('scope-cap').textContent = 'One second of the ' + label(hz) + ' pulse: the sound swells and fades ' + (hz === 4 ? '4' : hz) + ' times.';
  }

  function stopNodes(nodes) {
    if (!nodes) return;
    nodes.forEach(function (n) {
      try { if (n.stop) n.stop(); } catch (e) { }
      try { n.disconnect(); } catch (e) { }
    });
  }

  function build() {
    var ac = tone.ac;
    var carrier = ac.createOscillator(); carrier.frequency.value = 880;
    var amp = ac.createGain(); amp.gain.value = 0.5;
    var lfo = ac.createOscillator(); lfo.frequency.value = tone.hz;
    var depth = ac.createGain(); depth.gain.value = 0.5;
    lfo.connect(depth); depth.connect(amp.gain); carrier.connect(amp); amp.connect(tone.master);
    carrier.start(); lfo.start();
    tone.nodes = [carrier, lfo, amp, depth];
    tone.lfo = lfo;
  }

  function setPlaying(on) {
    tone.on = on;
    station.classList.toggle('playing', on);
    play.setAttribute('aria-pressed', on ? 'true' : 'false');
    play.textContent = on ? 'Stop the tone' : 'Play ' + label(tone.hz);
  }

  function start() {
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) { msg.textContent = 'This browser can’t play the tone. Try Chrome, Firefox or Safari.'; return; }
    try {
      if (!tone.ac) {
        tone.ac = new AC();
        tone.master = tone.ac.createGain();
        tone.master.gain.value = 0;
        tone.master.connect(tone.ac.destination);
      }
      var go = function () {
        build();
        tone.master.gain.setTargetAtTime(Number(vol.value) / 100, tone.ac.currentTime, 0.08);
        setPlaying(true);
        msg.textContent = '';
      };
      if (tone.ac.state === 'suspended') tone.ac.resume().then(go); else go();
    } catch (e) {
      msg.textContent = 'The tone didn’t start: ' + e.message;
    }
  }

  function stop() {
    var nodes = tone.nodes;
    tone.nodes = null; tone.lfo = null;
    if (tone.master) tone.master.gain.setTargetAtTime(0, tone.ac.currentTime, 0.05);
    setTimeout(function () { stopNodes(nodes); }, 300);
    setPlaying(false);
  }

  play.addEventListener('click', function () { if (tone.on) stop(); else start(); });
  vol.addEventListener('input', function () {
    if (tone.master && tone.on) tone.master.gain.setTargetAtTime(Number(vol.value) / 100, tone.ac.currentTime, 0.05);
  });
  Array.prototype.forEach.call(document.querySelectorAll('input[name="tone"]'), function (r) {
    r.addEventListener('change', function () {
      if (!r.checked) return;
      tone.hz = Number(r.value);
      drawScope(tone.hz);
      if (tone.on && tone.lfo) tone.lfo.frequency.setTargetAtTime(tone.hz, tone.ac.currentTime, 0.05);
      setPlaying(tone.on);
    });
  });
  drawScope(tone.hz);
  setPlaying(false);
})();
