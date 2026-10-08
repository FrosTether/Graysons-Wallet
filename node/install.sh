#!/bin/sh
# Installs frostnode as an always-on service on a Linux server with systemd (Ubuntu, Debian, Oracle Linux, ...):
# Java, the latest frostnode.zip checked against the release's SHA256SUMS, a frostnode user, a systemd service
# that starts on boot, and a firewall rule for port 7830. Run it again to update; the chain data stays.
#
#   curl -fsSL https://get.finux.tech/node/install.sh | sudo sh
#
# FrostExplorer comes up on port 7831. This script leaves 7831 closed: put the explorer on the web with a
# Cloudflare Tunnel (see node/README.md), or open 7831 yourself the same way as 7830.
set -eu

RELEASE=https://github.com/FrosTether/Graysons-Wallet/releases/latest/download
DIR=/opt/frostnode

say() { printf '\n== %s\n' "$*"; }

[ "$(id -u)" = 0 ] || { echo "Run it as root: curl -fsSL https://get.finux.tech/node/install.sh | sudo sh"; exit 1; }
command -v systemctl >/dev/null 2>&1 || { echo "This needs Linux with systemd. On a phone, use the Termux steps at https://get.finux.tech/node/"; exit 1; }

say "1/5 Java 17 and unzip"
need=""
command -v java >/dev/null 2>&1 || need="java"
command -v unzip >/dev/null 2>&1 || need="$need unzip"
command -v curl >/dev/null 2>&1 || need="$need curl"
if [ -n "$need" ]; then
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jre-headless unzip curl
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y -q java-17-openjdk-headless unzip curl
  else
    echo "Install Java 17, unzip and curl, then run this again."; exit 1
  fi
fi
java -version 2>&1 | head -1

say "2/5 frostnode, checked against the release checksums"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
curl -fsSL "$RELEASE/frostnode.zip" -o "$TMP/frostnode.zip"
curl -fsSL "$RELEASE/SHA256SUMS" -o "$TMP/SHA256SUMS"
if ! (cd "$TMP" && grep '  frostnode\.zip$' SHA256SUMS | sha256sum -c -); then
  echo "frostnode.zip doesn't match the release checksum. Nothing was installed."; exit 1
fi
unzip -q "$TMP/frostnode.zip" -d "$TMP"

say "3/5 the frostnode user and $DIR"
id frostnode >/dev/null 2>&1 || useradd --system --home "$DIR" --shell /usr/sbin/nologin frostnode
mkdir -p "$DIR/data"
rm -rf "$DIR/bin" "$DIR/lib"
cp -r "$TMP/frostnode/bin" "$TMP/frostnode/lib" "$DIR/"
chown -R frostnode:frostnode "$DIR"

say "4/5 the service"
cat > /etc/systemd/system/frostnode.service <<'UNIT'
[Unit]
Description=Frostchain node for Qoin (frostnode)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=frostnode
WorkingDirectory=/opt/frostnode
ExecStart=/opt/frostnode/bin/frostnode --data /opt/frostnode/data
Restart=on-failure
RestartSec=10
NoNewPrivileges=true
ProtectSystem=full
ReadWritePaths=/opt/frostnode/data

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable frostnode >/dev/null 2>&1
systemctl restart frostnode

say "5/5 port 7830 in this server's firewall"
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
  ufw allow 7830/tcp
elif command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
  firewall-cmd --permanent --add-port=7830/tcp >/dev/null && firewall-cmd --reload >/dev/null
elif command -v iptables >/dev/null 2>&1; then
  iptables -C INPUT -p tcp --dport 7830 -j ACCEPT 2>/dev/null || iptables -I INPUT -p tcp --dport 7830 -j ACCEPT
  if command -v netfilter-persistent >/dev/null 2>&1; then netfilter-persistent save >/dev/null 2>&1 || true; fi
fi

sleep 3
echo
journalctl -u frostnode -n 4 --no-pager 2>/dev/null | grep -E "listening|FrostExplorer" || systemctl --no-pager status frostnode | head -5
echo
echo "frostnode is running and starts on boot."
echo "- Cloud servers also block ports outside the machine. On Oracle Cloud, add an ingress rule for TCP 7830"
echo "  in your VCN's security list. Then check http://<this server's IP>:7830/p2p/info from your phone."
echo "- On each phone: Graysons Vault > Node > Add > this server's IP."
echo "- FrostExplorer is on port 7831 of this server. Logs: journalctl -u frostnode -f"
