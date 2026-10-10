#!/bin/sh
# Builds graysons-vault_<version>_all.deb for Ubuntu, Xubuntu and Debian: Graysons Vault, Frostoise and MyFrost
# in the app menu, one program (/usr/bin/graysons-vault) that runs a Frostchain node and shows the app's screens.
#
#   desktop/linux/build-deb.sh        (from the repository root, needs Java 11+ and dpkg-deb)
#
# The .deb lands in desktop/build/. Install it with: sudo apt install ./desktop/build/graysons-vault_*_all.deb
set -eu
cd "$(dirname "$0")/../.."

VERSION=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)
./gradlew -q :desktop:installDist

PKG=desktop/build/deb
rm -rf "$PKG"
mkdir -p "$PKG/DEBIAN" "$PKG/opt" "$PKG/usr/bin" "$PKG/usr/share/applications" "$PKG/usr/share/icons/hicolor/scalable/apps"
cp -r desktop/build/install/graysons-vault "$PKG/opt/graysons-vault"
rm -f "$PKG/opt/graysons-vault/bin/graysons-vault.bat"
ln -s /opt/graysons-vault/bin/graysons-vault "$PKG/usr/bin/graysons-vault"
cp desktop/linux/*.desktop "$PKG/usr/share/applications/"
UI=app/src/main/assets/ui
cp "$UI/graysons.svg" "$PKG/usr/share/icons/hicolor/scalable/apps/graysons-vault.svg"
cp "$UI/frostoise.svg" "$PKG/usr/share/icons/hicolor/scalable/apps/frostoise.svg"
cp "$UI/myfrost.svg" "$PKG/usr/share/icons/hicolor/scalable/apps/myfrost.svg"

# The full JRE, not -headless: Frostoise's microphone needs Java Sound.
cat > "$PKG/DEBIAN/control" <<CONTROL
Package: graysons-vault
Version: $VERSION
Section: net
Priority: optional
Architecture: all
Depends: openjdk-21-jre | openjdk-17-jre | default-jre
Suggests: chromium | chromium-browser
Maintainer: FrosTether <https://github.com/FrosTether/Graysons-Wallet>
Homepage: https://get.finux.tech
Description: Graysons Vault, Frostoise and MyFrost for Qoin (QNR)
 The Graysons Vault apps for Linux desktops. Each runs a full Frostchain node,
 like the Android app: create or restore a wallet, claim a .frostchain name,
 send QNR by @name, and tone-mine with Frostoise through the microphone.
CONTROL
cat > "$PKG/DEBIAN/postinst" <<'POSTINST'
#!/bin/sh
set -e
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q /usr/share/applications || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q /usr/share/icons/hicolor || true
POSTINST
chmod 755 "$PKG/DEBIAN/postinst"

OUT="desktop/build/graysons-vault_${VERSION}_all.deb"
dpkg-deb --root-owner-group --build "$PKG" "$OUT" >/dev/null
echo "$OUT"
