/* Minimal QR code encoder: byte mode, error correction level M, versions 1-10.
   Follows ISO/IEC 18004 (structure after Project Nayuki's reference implementation). */
(function () {
  'use strict';
  var ECC = [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26];   // EC codewords per block (level M)
  var BLOCKS = [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5];         // number of blocks (level M)

  function rawModules(v) {
    var r = (16 * v + 128) * v + 64;
    if (v >= 2) { var n = Math.floor(v / 7) + 2; r -= (25 * n - 10) * n - 55; if (v >= 7) r -= 36; }
    return r;
  }
  function dataCodewords(v) { return Math.floor(rawModules(v) / 8) - ECC[v] * BLOCKS[v]; }

  function gfMul(x, y) {
    var z = 0;
    for (var i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11D); z ^= ((y >>> i) & 1) * x; }
    return z & 0xFF;
  }
  function rsDivisor(deg) {
    var r = []; for (var i = 0; i < deg; i++) r.push(0); r[deg - 1] = 1;
    var root = 1;
    for (i = 0; i < deg; i++) {
      for (var j = 0; j < r.length; j++) { r[j] = gfMul(r[j], root); if (j + 1 < r.length) r[j] ^= r[j + 1]; }
      root = gfMul(root, 2);
    }
    return r;
  }
  function rsRemainder(data, div) {
    var r = div.map(function () { return 0; });
    data.forEach(function (b) {
      var f = b ^ r.shift(); r.push(0);
      for (var i = 0; i < r.length; i++) r[i] ^= gfMul(div[i], f);
    });
    return r;
  }
  function utf8(s) {
    var out = [];
    var e = unescape(encodeURIComponent(s));
    for (var i = 0; i < e.length; i++) out.push(e.charCodeAt(i));
    return out;
  }

  function encode(text) {
    var bytes = utf8(text), v, dx, dy;
    for (v = 1; v <= 10; v++) {
      var cap = dataCodewords(v) * 8, need = 4 + (v < 10 ? 8 : 16) + bytes.length * 8;
      if (need <= cap) break;
    }
    if (v > 10) throw new Error('text too long for QR');
    var bits = [];
    function put(val, len) { for (var i = len - 1; i >= 0; i--) bits.push((val >>> i) & 1); }
    put(4, 4); put(bytes.length, v < 10 ? 8 : 16);
    bytes.forEach(function (b) { put(b, 8); });
    var capBits = dataCodewords(v) * 8;
    put(0, Math.min(4, capBits - bits.length));
    while (bits.length % 8) bits.push(0);
    var data = [];
    for (var i = 0; i < bits.length; i += 8) { var b = 0; for (var j = 0; j < 8; j++) b = (b << 1) | bits[i + j]; data.push(b); }
    for (var pad = 0xEC; data.length < dataCodewords(v); pad ^= 0xEC ^ 0x11) data.push(pad);

    // split into blocks, add RS, interleave
    var nb = BLOCKS[v], ecl = ECC[v], raw = Math.floor(rawModules(v) / 8);
    var nShort = nb - raw % nb, shortLen = Math.floor(raw / nb), div = rsDivisor(ecl), blocks = [], k = 0;
    for (i = 0; i < nb; i++) {
      var dat = data.slice(k, k + shortLen - ecl + (i < nShort ? 0 : 1)); k += dat.length;
      var blk = dat.slice(); if (i < nShort) blk.push(0);
      blocks.push(blk.concat(rsRemainder(dat, div)));
    }
    var seq = [];
    for (i = 0; i < blocks[0].length; i++)
      for (j = 0; j < nb; j++)
        if (i !== shortLen - ecl || j >= nShort) seq.push(blocks[j][i]);

    var size = 17 + 4 * v, m = [], fn = [];
    for (i = 0; i < size; i++) { m.push(new Array(size).fill(false)); fn.push(new Array(size).fill(false)); }
    function set(x, y, dark) { m[y][x] = dark; fn[y][x] = true; }
    for (i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0); }
    [[3, 3], [size - 4, 3], [3, size - 4]].forEach(function (c) {
      for (var dy = -4; dy <= 4; dy++) for (var dx = -4; dx <= 4; dx++) {
        var d = Math.max(Math.abs(dx), Math.abs(dy)), xx = c[0] + dx, yy = c[1] + dy;
        if (xx >= 0 && xx < size && yy >= 0 && yy < size) set(xx, yy, d !== 2 && d !== 4);
      }
    });
    if (v >= 2) {
      var na = Math.floor(v / 7) + 2, step = Math.floor((v * 4 + na * 2 + 1) / (na * 2 - 2)) * 2, pos = [6];
      for (var p = size - 7, t = []; t.length < na - 1; p -= step) t.unshift(p);
      pos = pos.concat(t);
      for (i = 0; i < na; i++) for (j = 0; j < na; j++) {
        if ((i === 0 && j === 0) || (i === 0 && j === na - 1) || (i === na - 1 && j === 0)) continue;
        for (dy = -2; dy <= 2; dy++) for (dx = -2; dx <= 2; dx++) set(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
      }
    }
    function format(mask) {
      var d = (0 << 3) | mask, rem = d;                  // level M = 00
      for (var i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
      var b = ((d << 10) | rem) ^ 0x5412;
      function bit(i) { return ((b >>> i) & 1) !== 0; }
      for (i = 0; i <= 5; i++) set(8, i, bit(i));
      set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8));
      for (i = 9; i < 15; i++) set(14 - i, 8, bit(i));
      for (i = 0; i < 8; i++) set(size - 1 - i, 8, bit(i));
      for (i = 8; i < 15; i++) set(8, size - 15 + i, bit(i));
      set(8, size - 8, true);
    }
    format(0);
    if (v >= 7) {
      var rem = v;
      for (i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
      var vb = v * 4096 + rem;
      for (i = 0; i < 18; i++) {
        var bt = Math.floor(vb / Math.pow(2, i)) % 2 === 1, a = size - 11 + i % 3, c = Math.floor(i / 3);
        set(a, c, bt); set(c, a, bt);
      }
    }
    // place data
    var bi = 0, total = seq.length * 8;
    for (var right = size - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5;
      for (var vert = 0; vert < size; vert++) for (j = 0; j < 2; j++) {
        var x = right - j, up = ((right + 1) & 2) === 0, y = up ? size - 1 - vert : vert;
        if (!fn[y][x] && bi < total) { m[y][x] = ((seq[bi >>> 3] >>> (7 - (bi & 7))) & 1) === 1; bi++; }
      }
    }
    var masks = [
      function (x, y) { return (x + y) % 2 === 0; }, function (x, y) { return y % 2 === 0; },
      function (x) { return x % 3 === 0; }, function (x, y) { return (x + y) % 3 === 0; },
      function (x, y) { return (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0; },
      function (x, y) { return x * y % 2 + x * y % 3 === 0; },
      function (x, y) { return (x * y % 2 + x * y % 3) % 2 === 0; },
      function (x, y) { return ((x + y) % 2 + x * y % 3) % 2 === 0; }];
    function applyMask(k) { for (var y = 0; y < size; y++) for (var x = 0; x < size; x++) if (!fn[y][x] && masks[k](x, y)) m[y][x] = !m[y][x]; }
    function penalty() {
      var s = 0, x, y, run, dark = 0;
      for (var pass = 0; pass < 2; pass++) for (y = 0; y < size; y++) {
        run = 1;
        for (x = 1; x <= size; x++) {
          var a = pass ? m[x - 1] && m[x - 1][y] : m[y][x - 1], b = x < size ? (pass ? m[x][y] : m[y][x]) : null;
          if (b === a) run++; else { if (run >= 5) s += 3 + run - 5; run = 1; }
        }
        for (x = 0; x + 10 < size; x++) {
          var str = '';
          for (var q = 0; q < 11; q++) str += (pass ? m[x + q][y] : m[y][x + q]) ? '1' : '0';
          if (str === '10111010000' || str === '00001011101') s += 40;
        }
      }
      for (y = 0; y + 1 < size; y++) for (x = 0; x + 1 < size; x++) {
        var c = m[y][x];
        if (c === m[y][x + 1] && c === m[y + 1][x] && c === m[y + 1][x + 1]) s += 3;
      }
      for (y = 0; y < size; y++) for (x = 0; x < size; x++) if (m[y][x]) dark++;
      s += Math.floor(Math.abs(dark * 20 - size * size * 10) / (size * size)) * 10;
      return s;
    }
    var best = 0, bestScore = Infinity;
    for (k = 0; k < 8; k++) {
      applyMask(k); format(k);
      var sc = penalty();
      if (sc < bestScore) { bestScore = sc; best = k; }
      applyMask(k);
    }
    applyMask(best); format(best);
    return m;
  }

  function draw(canvas, text, dark, light) {
    var m = encode(text), n = m.length, quiet = 4, scale = Math.floor(canvas.width / (n + 2 * quiet));
    var ctx = canvas.getContext('2d'), off = Math.floor((canvas.width - scale * n) / 2);
    ctx.fillStyle = light || '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = dark || '#000';
    for (var y = 0; y < n; y++) for (var x = 0; x < n; x++) if (m[y][x]) ctx.fillRect(off + x * scale, off + y * scale, scale, scale);
  }

  window.QR = { encode: encode, draw: draw };
})();
