# frostnode

The headless Frostchain node. It runs on a server, stores the chain and relays blocks and transactions between phones.
It has no wallet and never holds anyone's 25 words. It stays up when the phones sleep.

Phones on the same Wi-Fi find each other on their own. Everyone else needs a node like this one to connect through.

## Build

On any machine with Java 11+ (no Android SDK needed):

```bash
./gradlew :node:installDist
```

That makes `node/build/install/frostnode/` with `bin/frostnode` and `lib/`.

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

The first log line shows the chain ID prefix. For v0.4 it's `5f8a7ec5b9db1444`, and the phones' Node screen must show the same.

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

## Options

```
frostnode [--data DIR] [--port 7830] [--peer HOST[:PORT]]...
```

`--peer` adds a node to sync from at startup. Every 5 minutes the node logs its height, how many peers it reached and its mempool size.
