**Graysons Vault 0.6.0** comes to Windows and Linux, and adds **Remix**.

- **Desktop app.** The same wallet, node and MyFrost as the phone, for Windows and Linux. It doesn't mine: Frostoise stays on phones. Its node keeps running after you close the window, so your computer keeps serving the network until you choose **Quit** (in **More**, or the tray icon). It opens in its own window when Edge, Chrome or Chromium is installed, otherwise in your browser.
- **Remix**, on phones and computers: blend pink noise with the nine solfeggio tones (174 to 963 Hz) and the found wave, Frostoise's hum pulsing at 4.0, 7.83 or 11.11 Hz, and watch the mix as a wireframe tunnel. **This block** plays the newest block's tone, picked from its hash, and **Follow new blocks** changes the mix each time a block lands. Nothing on screen flashes at the pulse rate.
- **Fix:** the recent blocks under **Node** and on the Frostoise screen failed to load in 0.5.5. They show again.

Nothing about the chain changes: same chain (`chain b8b1ec3fefb96834`), same rules, same coin, QNR.

### Install

- **Android, on 0.4.3 or newer:** install `GraysonsVault.apk` over it. Your wallet, words and name stay.
- **Android, on 0.4.2 or older:** uninstall it first, install this, then tap **Restore** and enter your 25 words.
- **Windows:** run the `.msi` installer, or unzip `GraysonsVault-windows-x64.zip` anywhere and run `GraysonsVault.exe`. Windows may warn about an unknown publisher, since the app isn't code-signed yet: choose **More info → Run anyway**. Allow it through the firewall so other nodes can reach yours.
- **Linux:** install the `.deb` (`sudo apt install ./graysons-vault_*.deb`), or unpack `GraysonsVault-linux-x64.tar.gz` and run `GraysonsVault/bin/GraysonsVault`.
- **Moving a wallet to the computer:** tap **Restore** and enter your 25 words. Don't use the same wallet on two devices at once: both would spend the same one-time keys.

Check every file against `SHA256SUMS`. `GraysonsWallet.apk` is the same file under the old name, for old links. `frostnode.zip` is the server node. See [get.finux.tech/node](https://get.finux.tech/node/).

QNR has no guaranteed value. This is a proof of concept: expect bugs.
