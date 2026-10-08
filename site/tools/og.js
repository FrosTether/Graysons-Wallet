// Renders site/getqoin/og.html to a 1200x630 PNG for link previews.
// Usage: node site/tools/og.js <out.png>   (needs Playwright with a Chromium)
const path = require('path');
const fs = require('fs');
let chromium;
try { ({ chromium } = require('playwright')); } catch (e) { ({ chromium } = require('/opt/npm-tools/node_modules/playwright')); }

const site = path.resolve(__dirname, '..');
const types = { '.html': 'text/html', '.woff': 'font/woff', '.css': 'text/css', '.js': 'text/javascript' };

(async () => {
  const browser = await chromium.launch(fs.existsSync('/opt/pw-browsers/chromium') ? { executablePath: '/opt/pw-browsers/chromium' } : {});
  const page = await browser.newPage({ viewport: { width: 1200, height: 630 } });
  // Serve the site folder from a made-up origin, so the fonts load like they do on the web.
  await page.route('http://site.local/**', (route) => {
    const file = path.join(site, decodeURIComponent(new URL(route.request().url()).pathname));
    if (!file.startsWith(site) || !fs.existsSync(file)) return route.fulfill({ status: 404, body: 'not found' });
    route.fulfill({ status: 200, contentType: types[path.extname(file)] || 'application/octet-stream', body: fs.readFileSync(file) });
  });
  await page.goto('http://site.local/getqoin/og.html');
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: process.argv[2] || 'og.png' });
  await browser.close();
})();
