# Graysons Vault for Linux

Graysons Vault, Frostoise and MyFrost on a Linux desktop (Ubuntu, Xubuntu, Debian). They're the Android app's own
screens, run by one program, `graysons-vault`, that holds a full Frostchain node, just like the app does on a phone.

## Install

Download [GraysonsVault.deb](https://github.com/FrosTether/Graysons-Wallet/releases/latest/download/GraysonsVault.deb) and run:

```bash
sudo apt install ./GraysonsVault.deb
```

The apps show up in the menu under Office. They open in a Chromium app window if Chromium, Chrome, Brave or Edge is installed,
and in your default browser otherwise. For the cleanest window, `sudo apt install chromium-browser` (a snap on Ubuntu).

Your wallets and the chain live in `~/.local/share/graysons-vault`. Back up your 25 words: they restore the wallet on any
phone or computer.

## Make the laptop a node like the server

The desktop apps run a node only while they're open. To keep one running all the time, the way the server does, install
frostnode as a service with the same one-line installer:

```bash
curl -fsSL https://get.finux.tech/node/install.sh | sudo sh
```

frostnode then owns port 7830 and starts on boot. The desktop apps notice, run their own node on another port and sync from
it. FrostExplorer is at http://localhost:7831/.

Both find the public seed node (149.28.63.221) on their own. Phones on the same Wi-Fi find the laptop on their own too.
For phones elsewhere, your router has to forward TCP 7830 to the laptop. Set the laptop not to sleep while it's plugged in.

## Mining with Frostoise

A laptop has no magnetometer, so Frostoise listens through the microphone. Play a mining tone near it (the tone player in
Frostoise, or [get.finux.tech](https://get.finux.tech)), wait for **LOCKED**, then start mining. Frostoise backs off when
the CPU runs hot, as it does on a hot phone.

## Run it by hand

```bash
graysons-vault [wallet|frostoise|myfrost] [--data DIR] [--no-browser]
graysons-vault --quit
```

`--no-browser` prints the address to open instead. Like on a phone, the node keeps running after you close the windows,
until you log out or run `graysons-vault --quit`. The screens are served on 127.0.0.1 only, behind a random token that changes on every start, so
other websites and other computers can't reach the wallet.

## Build

On any machine with Java 11+ and `dpkg-deb`:

```bash
./gradlew :desktop:installDist        # desktop/build/install/graysons-vault/bin/graysons-vault
desktop/linux/build-deb.sh            # desktop/build/graysons-vault_<version>_all.deb
```

Every release build on GitHub also publishes the .deb as `GraysonsVault.deb`.
