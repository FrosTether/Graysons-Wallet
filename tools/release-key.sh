#!/bin/sh
# Makes the Graysons Wallet release key on this phone (or computer) and gives it to GitHub, so every build
# after this one installs over the last as a normal update.
#
#   In Termux:
#   curl -fsSLO https://github.com/FrosTether/Graysons-Wallet/raw/main/tools/release-key.sh && sh release-key.sh
#
# The key never leaves this device except as encrypted GitHub secrets: the GitHub CLI encrypts each value with
# the repository's public key before it's sent. The keystore and its password are saved in ~/graysons-release-key/.
# Back that folder up somewhere off the phone. Losing the key means every phone has to uninstall again.
# Running it again reuses the same key, so it's safe to repeat.
set -eu

REPO=FrosTether/Graysons-Wallet
DIR="$HOME/graysons-release-key"
KS="$DIR/graysons-release.jks"
PW="$DIR/password.txt"
ALIAS=graysons

say() { printf '\n== %s\n' "$*"; }

say "1/4 Tools: keytool (from Java) and the GitHub CLI"
if ! command -v keytool >/dev/null 2>&1 || ! command -v gh >/dev/null 2>&1; then
  if command -v pkg >/dev/null 2>&1; then
    pkg install -y openjdk-17 gh
  else
    echo "Install a JDK (for keytool) and the GitHub CLI (gh), then run this again."; exit 1
  fi
fi

say "2/4 The key"
if [ -s "$KS" ] && [ -s "$PW" ]; then
  echo "Using the key you already made in $DIR."
  PASS=$(cat "$PW")
else
  mkdir -p "$DIR"
  chmod 700 "$DIR"
  PASS=$(head -c 24 /dev/urandom | base64 | tr -d '/+=\n' | cut -c1-28)
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass "$PASS" -alias "$ALIAS" \
    -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 36500 \
    -dname "CN=Graysons Wallet, OU=Finux, O=FrosTether" 2>/dev/null
  printf '%s\n' "$PASS" > "$PW"
  chmod 600 "$KS" "$PW"
  echo "Made a new key in $DIR."
fi
FINGERPRINT=$(keytool -list -v -keystore "$KS" -storepass "$PASS" -alias "$ALIAS" 2>/dev/null | grep -m1 "SHA256:" | sed 's/.*SHA256: *//')
echo "Its SHA-256 fingerprint: $FINGERPRINT"

say "3/4 GitHub sign-in"
if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  echo "GitHub shows a one-time code next. Open https://github.com/login/device, sign in as FrosTether and enter it."
  gh auth login --hostname github.com --git-protocol https --web
fi

say "4/4 The four secrets on $REPO"
base64 < "$KS" | tr -d '\n' | gh secret set SIGNING_KEYSTORE_B64 --repo "$REPO"
printf '%s' "$PASS" | gh secret set SIGNING_STORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set SIGNING_KEY_ALIAS --repo "$REPO"
printf '%s' "$PASS" | gh secret set SIGNING_KEY_PASSWORD --repo "$REPO"
gh secret list --repo "$REPO"

cat <<DONE

Done. GitHub signs every Graysons Wallet build with this key from now on.

Back it up now:
  - Copy the folder $DIR somewhere off this phone, like a private Google Drive folder.
    In Termux: termux-setup-storage, then cp -r $DIR ~/storage/downloads/
    Upload that copy from your Downloads, then delete it from Downloads.
  - Write the password on paper too, next to your 25 words:
    $(cat "$PW")
DONE
