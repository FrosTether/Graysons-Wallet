#!/bin/sh
# Makes the Graysons Vault release key on this phone (or computer) and gives it to GitHub, so every build
# after this one installs over the last as a normal update.
#
#   In Termux:
#   curl -fsSLO https://github.com/FrosTether/Graysons-Wallet/raw/main/tools/release-key.sh && sh release-key.sh
#
# The key never leaves this device except as encrypted GitHub secrets: the GitHub CLI encrypts each value with
# the repository's public key before it's sent. The keystore and its password are saved in ~/graysons-release-key/.
# Back that folder up somewhere off the phone. Losing the key means every phone has to uninstall again.
#
# Running it again reuses the same key, so it's safe to repeat. Once GitHub has published a build signed with the
# key, .github/release-cert.sha256 records it, and this script refuses to give GitHub any other key: on a new
# device, copy the backed-up folder to ~/graysons-release-key first.
set -eu

REPO=FrosTether/Graysons-Wallet
PIN_URL="https://raw.githubusercontent.com/$REPO/main/.github/release-cert.sha256"
DIR="$HOME/graysons-release-key"
KS="$DIR/graysons-release.jks"
PW="$DIR/password.txt"
ALIAS=graysons
STEP="the start"
TMP=""
SENT=no

say() { STEP="$*"; printf '\n== %s\n' "$*"; }
norm() { tr -d ' :\r\n' | tr a-f A-F; }
finish() {
  code=$?
  [ -n "$TMP" ] && rm -f "$TMP"
  if [ "$code" -ne 0 ]; then
    printf '\nStopped at "%s". Fix what it says above, then run the script again.\n' "$STEP"
    [ "$SENT" = yes ] || echo "Nothing was sent to GitHub."
  fi
}
trap finish EXIT

say "1/5 Tools: keytool (from Java), curl and the GitHub CLI"
if ! command -v keytool >/dev/null 2>&1 || ! command -v gh >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
  if command -v pkg >/dev/null 2>&1; then
    pkg install -y openjdk-17 gh curl
  else
    echo "Install a JDK (for keytool), curl and the GitHub CLI (gh), then run this again."; exit 1
  fi
fi

say "2/5 The key GitHub signs with, if it has one"
TMP=$(mktemp)
HTTP=$(curl -sS -o "$TMP" -w '%{http_code}' "$PIN_URL") || HTTP=000
case "$HTTP" in
  200) PIN=$(norm < "$TMP"); echo "GitHub signs with the key whose SHA-256 fingerprint is $(tr -d ' \r\n' < "$TMP")" ;;
  404) PIN=""; echo "None yet." ;;
  *) echo "Couldn't reach GitHub to check (HTTP $HTTP). Check the connection."; exit 1 ;;
esac

say "3/5 The key on this device"
if [ -s "$KS" ] && [ -s "$PW" ]; then
  echo "Using the key you already made in $DIR."
  PASS=$(cat "$PW")
elif [ -n "$PIN" ]; then
  echo "GitHub already has a release key, and it isn't on this device. A new key here would stop every phone from"
  echo "updating. Copy your backup of the graysons-release-key folder to $DIR, then run this again."
  exit 1
else
  mkdir -p "$DIR"
  chmod 700 "$DIR"
  PASS=$(head -c 24 /dev/urandom | base64 | tr -d '/+=\n' | cut -c1-28)
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass "$PASS" -alias "$ALIAS" \
    -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 36500 \
    -dname "CN=Graysons Vault, OU=Finux, O=FrosTether"
  printf '%s\n' "$PASS" > "$PW"
  chmod 600 "$KS" "$PW"
  echo "Made a new key in $DIR."
fi
FINGERPRINT=$(keytool -list -v -keystore "$KS" -storepass "$PASS" -alias "$ALIAS" | grep -m1 "SHA256:" | sed 's/.*SHA256: *//')
if [ -z "$FINGERPRINT" ]; then echo "Couldn't read the key in $KS."; exit 1; fi
echo "Its SHA-256 fingerprint: $FINGERPRINT"
if [ -n "$PIN" ] && [ "$(printf '%s' "$FINGERPRINT" | norm)" != "$PIN" ]; then
  echo "That isn't the key GitHub signs with. Put the backup of graysons-release-key that matches it in $DIR,"
  echo "then run this again."
  exit 1
fi

say "4/5 GitHub sign-in"
if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  echo "GitHub shows a one-time code next. Open https://github.com/login/device, sign in as FrosTether and enter it."
  gh auth login --hostname github.com --git-protocol https --web
fi
echo "Signed in to GitHub as $(gh api user --jq .login)."

say "5/5 The four secrets on $REPO"
SENT=yes
base64 < "$KS" | tr -d '\n' | gh secret set SIGNING_KEYSTORE_B64 --repo "$REPO"
printf '%s' "$PASS" | gh secret set SIGNING_STORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set SIGNING_KEY_ALIAS --repo "$REPO"
printf '%s' "$PASS" | gh secret set SIGNING_KEY_PASSWORD --repo "$REPO"
gh secret list --repo "$REPO"

STEP="the build"
printf '\nBuild Graysons Vault with the key now? [Y/n] '
{ read -r ANSWER < /dev/tty; } 2>/dev/null || ANSWER=n
case "$ANSWER" in
  n*|N*) echo "Not now. Builds start on their own when the app changes." ;;
  *) if gh workflow run build.yml --repo "$REPO" --ref main; then
       echo "Started. It takes about 10 minutes: https://github.com/$REPO/actions/workflows/build.yml"
     else
       echo "Couldn't start it from here. On GitHub, open Actions, then Build, then Run workflow."
     fi ;;
esac

cat <<DONE

Done. GitHub signs every Graysons Vault build with this key from now on.

Back it up now:
  - Copy the folder ~/graysons-release-key somewhere off this phone, like a private Google Drive folder.
    In Termux: termux-setup-storage, then cp -r ~/graysons-release-key ~/storage/downloads/
    Upload that copy from your Downloads, then delete it from Downloads.
  - Write the password on paper too, next to your 25 words. To see it, run
      cat ~/graysons-release-key/password.txt
    Never paste it into a chat or take a screenshot of it.
DONE
