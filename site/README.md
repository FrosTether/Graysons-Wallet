# The Qoin site

get.finux.tech: four pages that share one look.

| Page | Folder | What it is |
|---|---|---|
| `/` | `getqoin/` | Get Qoin: the latest APK, a QR code for computers, the three mining tones as a player, how to start, the chain facts |
| `/node/` | `node/` | Run a node: what an always-on node does, and how to run one on a server, a laptop or a phone |
| `/qnr/` | `qnr/` | QNR vs XMR, and what QNR could take from Monero, Zcash, Dash and Steem |
| `/temporal/` | `temporal/` | Temporal, the time machine: seal a capsule for the future, open capsules, go back to any block |

Each folder holds `body.html` (the page's main content), and optionally `style.css` and `script.js`.

| Shared file | What it is |
|---|---|
| `common/base.css` | Colors for light and dark, type, header, footer, steps, facts, commands, tabs and tables |
| `common/site.js` | Copy buttons and tabs |
| `common/og.html` | The link-preview image, drawn per page by `tools/og.js` |
| `fonts/` | Archivo and Martian Mono, cut down to Latin (SIL Open Font License) |
| `tools/qrsvg.js` | Makes the download QR code with the app's own encoder, `app/src/main/assets/ui/qr.js` |
| `build.py` | Puts it together |

```bash
python3 site/build.py site/out https://get.finux.tech/
```

`site/out/pages/` is the hosted site: the `gh-pages` branch, served at https://get.finux.tech through a CNAME record in
finux.tech's Cloudflare DNS. The build also copies `node/install.sh` to `/node/install.sh`, the one-line server installer.

The tone players make the same pulse as Frostoise's: an 880 Hz hum whose loudness swells and fades at the mining tone.
Run through Frostoise's own analysis, all three tones lock and give valid block proofs. Nothing on the pages flashes at the
tone rate.

Temporal's page follows the same rules as `Temporal.java` in the chain core: the fingerprint, the memo and the burn address
match byte for byte, and `TemporalTest` pins a test vector the page reproduces. Going back in time needs a live node: the page
reads FrostExplorer's API at `https://explorer.finux.tech`, or at the address in `?node=`.
