# frostnode

The headless Frostchain node. It runs on a server, stores the chain and relays blocks and transactions between phones.
It has no wallet and never holds anyone's 25 words. It stays up when the phones sleep.
It also serves [FrostExplorer](#frostexplorer), the public block explorer.

Phones on the same Wi-Fi find each other on their own. Everyone else needs a node like this one to connect through.

## Build

On any machine with Java 11+ (no Android SDK needed):

```bash
./gradlew :node:installDist
```

That makes `node/build/install/frostnode/` with `bin/frostnode` and `lib/`.
The same folder, zipped, is `frostnode.zip` on the [latest release](https://github.com/FrosTether/Graysons-Wallet/releases/latest/download/frostnode.zip).

## Install on the server (Ubuntu, for example an Oracle Cloud VM)

```bash
# Java
sudo apt update && sudo apt install -y openjdk-17-jre-headless

# A user that owns the node, and the install folder
sudo useradd --system --home /opt/frostnode --shell /usr/sbin/nologin frostnode
sudo mkdir -p /opt/frostnode/data
sudo cp -r frostnode/bin frostnode/lib /opt/frostnode/
sudo chown -R frostnode:frostnode /opt/frostnode

# Run it as a service that starts on boot
sudo cp frostnode.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now frostnode
journalctl -u frostnode -f      # watch it start
```

The first log line shows the chain ID prefix. For v0.4 it's `aa18513e5ee8db9f`, and the phones' Node screen must show the same.

## Open port 7830

Phones reach the node on TCP 7830. On Oracle Cloud it's blocked in two places:

1. **Oracle's network:** Networking → Virtual cloud networks → your VCN → Security lists → Default → Add ingress rule. Source `0.0.0.0/0`, protocol TCP, destination port `7830`.
2. **The server's own firewall:** Oracle's Ubuntu images ship with strict iptables rules.

   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 7830 -j ACCEPT
   sudo netfilter-persistent save
   ```

Check it from your phone's browser: `http://<server-ip>:7830/p2p/info` should show the chain and height.

## Connect the phones

On each phone, open **Graysons Wallet → Node → Add** and enter the server's public IP. Port 7830 is the default, so `host` alone works.
A name like `node.finux.tech` pointing at the server is easier to share and survives an IP change.

## FrostExplorer

frostnode serves FrostExplorer on port 7831: the live chain in the same look as the app.

- Height, a countdown to the next block window (blocks every 5 minutes, never within 270 seconds), QNR mined so far, QNR burned by Temporal, and the difficulty.
- Every block's resonance proof: the tone it was mined on, its band, its spectrum, and a button that plays the tone back.
- Search by block number, block hash, address or `.frostchain` name. An account shows its balance and history.

Open `http://<server>:7831/` (on Oracle Cloud, open port 7831 the same way as 7830, or use the tunnel below). It only reads the chain, so it's safe on the open internet.
Other sites can use the same data, like [Temporal](https://get.finux.tech/temporal/) does. It's JSON with CORS open:

| Path | Returns |
|---|---|
| `/api/explorer/status` | Height, tip, seconds until the next block window, difficulty, QNR mined, QNR burned |
| `/api/explorer/blocks?before=HEIGHT&count=30` | The newest blocks, up to 100 at a time |
| `/api/explorer/block?h=HEIGHT` | One block with its transactions |
| `/api/explorer/search?q=TEXT` | A block, or an account with its balance and history |
| `/api/explorer/at?t=UNIX` | The chain at a moment: the block mined by then, QNR mined by then, capsules sealed by then |
| `/api/explorer/temporal` | Temporal's burn address, QNR burned and the newest seals. `?fp=FINGERPRINT` finds one capsule's seal |

### Put it on explorer.finux.tech

A Cloudflare Tunnel gives the explorer an https address on finux.tech without opening a port.
`cloudflared` runs on Linux, Windows, macOS and Termux (`pkg install cloudflared`).

To try it first, this prints a temporary `https://….trycloudflare.com` address. No account needed:

```bash
cloudflared tunnel --url http://localhost:7831
```

For the real name:

```bash
cloudflared tunnel login                                   # a browser opens: pick finux.tech
cloudflared tunnel create frostexplorer                    # prints the tunnel ID and its credentials file
cloudflared tunnel route dns frostexplorer explorer.finux.tech
cloudflared tunnel run --url http://localhost:7831 frostexplorer
```

To keep the tunnel up on a server after a reboot, save this as `~/.cloudflared/config.yml`:

```yaml
tunnel: <tunnel ID>
credentials-file: /home/<you>/.cloudflared/<tunnel ID>.json
ingress:
  - hostname: explorer.finux.tech
    service: http://localhost:7831
  - service: http_status:404
```

Then install it as a service:

```bash
sudo cloudflared --config ~/.cloudflared/config.yml service install
```

## Run it on a phone with Termux

Running a node on a phone is a quick way to see FrostExplorer before there's a server. It works while the phone stays awake.
Graysons Wallet already uses port 7830 on the phone, so give frostnode other ports and let it sync from the app:

```bash
pkg install openjdk-17 unzip
curl -LO https://github.com/FrosTether/Graysons-Wallet/releases/latest/download/frostnode.zip
unzip -o frostnode.zip
termux-wake-lock
frostnode/bin/frostnode --data ~/frostnode-data --port 7832 --explorer-port 7833 --peer 127.0.0.1:7830
```

Then open `http://localhost:7833` in the phone's browser.

## Options

```
frostnode [--data DIR] [--port 7830] [--explorer-port 7831] [--peer HOST[:PORT]]...
```

`--peer` adds a node to sync from at startup. `--explorer-port 0` turns FrostExplorer off.
Every 5 minutes the node logs its height, how many peers it reached and its mempool size.
