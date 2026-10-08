# Get Qoin page

The download page for Graysons Wallet: the latest APK, a QR code for computers, the three mining tones as a
player, how to start, the starter bundle and the chain facts.

| File | What it is |
|---|---|
| `getqoin/body.html`, `getqoin/style.css`, `getqoin/script.js` | The page |
| `getqoin/og.html` | The link-preview image, rendered to `og.png` |
| `fonts/` | Archivo and Martian Mono, cut down to Latin (SIL Open Font License) |
| `tools/qrsvg.js` | Makes the QR code with the app's own encoder, `app/src/main/assets/ui/qr.js` |
| `build.py` | Puts it together |

```bash
python3 site/build.py site/out https://get.finux.tech/
```

`site/out/pages/` is the hosted page: the `gh-pages` branch, served at https://get.finux.tech through a CNAME record in finux.tech's Cloudflare DNS. `site/out/preview/getqoin.html` is the same page
as a fragment that loads its fonts from Google Fonts.

The tone player makes the same pulse as Frostoise's: a 220 Hz hum whose loudness swells and fades at the
mining tone. Run through Frostoise's own analysis, all three tones lock and give valid block proofs.
Nothing on the page flashes at the tone rate.
