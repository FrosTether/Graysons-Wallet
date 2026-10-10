#!/bin/sh
# Packages the desktop app with its own Java runtime, so people don't need Java installed.
#   desktop/packaging/package.sh linux   -> out/GraysonsVault-linux-x64.tar.gz and out/graysons-vault_<v>_amd64.deb
#   desktop/packaging/package.sh windows -> out/GraysonsVault-windows-x64.zip (and out/GraysonsVault-<v>.msi with WiX)
# Run from the repository root after ./gradlew :desktop:installDist. Needs JDK 17+ (jpackage).
set -eu
OS="${1:?linux or windows}"
VERSION=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)
NUM=$(printf '%s' "$VERSION" | sed 's/[^0-9.].*//')
LIB=desktop/build/install/GraysonsVault/lib
ICONS=desktop/packaging
WORK=desktop/build/jpackage
rm -rf "$WORK"
mkdir -p "$WORK" out

DESC="Qoin wallet, node and masternode"
common="--name GraysonsVault --app-version $NUM --vendor Finux --copyright GPL-3.0
  --input $LIB --main-jar desktop.jar --main-class com.frostether.desktop.GraysonsDesktop
  --add-modules java.base,java.desktop,jdk.httpserver --jlink-options --strip-debug"

case "$OS" in
  linux)
    # shellcheck disable=SC2086
    jpackage --type app-image $common --description "$DESC" --icon "$ICONS/graysons.png" --dest "$WORK"
    tar -C "$WORK" -czf "out/GraysonsVault-linux-x64.tar.gz" GraysonsVault
    if command -v dpkg-deb >/dev/null 2>&1 && command -v fakeroot >/dev/null 2>&1; then
      jpackage --type deb --app-image "$WORK/GraysonsVault" --name GraysonsVault --app-version "$NUM" \
        --linux-package-name graysons-vault --linux-shortcut --linux-menu-group Finance \
        --description "$DESC" --icon "$ICONS/graysons.png" --dest "$WORK"
      cp "$WORK"/*.deb out/
    fi
    ;;
  windows)
    # shellcheck disable=SC2086
    jpackage --type app-image $common --description "$DESC" --icon "$ICONS/graysons.ico" --dest "$WORK"
    (cd "$WORK" && powershell -NoProfile -Command "Compress-Archive -Path GraysonsVault -DestinationPath GraysonsVault-windows-x64.zip -Force")
    cp "$WORK/GraysonsVault-windows-x64.zip" out/
    # The installer needs the WiX Toolset; the portable zip above doesn't.
    if jpackage --type msi --app-image "$WORK/GraysonsVault" --name GraysonsVault --app-version "$NUM" \
        --win-menu --win-menu-group "Graysons Vault" --win-shortcut --win-dir-chooser \
        --win-upgrade-uuid 6a0c1f63-5bd9-4a63-9a4e-7c83e0a9e2f1 --description "$DESC" \
        --dest "$WORK"; then
      cp "$WORK"/*.msi out/
    else
      echo "No MSI: WiX Toolset missing. The zip still works."
    fi
    ;;
  *)
    echo "linux or windows" >&2
    exit 2
    ;;
esac
ls -la out
