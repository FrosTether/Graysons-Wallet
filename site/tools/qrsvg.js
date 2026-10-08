// Usage: node qrsvg.js <qr.js path> <text>  ->  prints an SVG (module path, 4-module quiet zone, viewBox in modules)
const fs = require('fs');
const src = fs.readFileSync(process.argv[2], 'utf8');
const window = {};
new Function('window', src)(window);
const m = window.QR.encode(process.argv[3]);
const n = m.length, q = 4, size = n + 2 * q;
let d = '';
for (let y = 0; y < n; y++) {
  let x = 0;
  while (x < n) {
    if (!m[y][x]) { x++; continue; }
    let run = 1;
    while (x + run < n && m[y][x + run]) run++;
    d += `M${x + q} ${y + q}h${run}v1h-${run}z`;
    x += run;
  }
}
process.stdout.write(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges" role="img" aria-label="QR code for the Graysons Vault download"><rect width="${size}" height="${size}" fill="#fff"/><path fill="#1a0f22" d="${d}"/></svg>`);
console.error(`modules ${n}x${n}, version ${(n - 17) / 4}`);
