**Test build of Graysons Wallet 0.4.0**, built automatically from `main`.

This is the v0.4 relaunch: a new chain where block 1 pays the normal reward, and mining runs on three tones.
4.0 Hz mines on low, 7.83 Hz on medium and 11.11 Hz on high.

- **Mining opens at launch:** Friday 9 October 2026, 13:37 Eastern. Until then Frostoise says so and doesn't hash.
- **New chain:** the Node screen shows `chain 303c4abcc5626d0b`. Coins from 0.3.x don't carry over.
- **Test key:** this build is debug-signed. The real release will be signed with a different key, so you'll uninstall once more then.

### Install

1. In 0.3.x, open Graysons Wallet and **write down your 25 words**.
2. Uninstall 0.3.x. Android won't install over it, because it's signed with a different key.
3. Download `GraysonsWallet-0.4.0-test.apk` below, open it, and allow installs from your browser when asked.
4. Open Graysons Wallet, tap **Restore**, and enter the same 25 words. It's the same wallet, not a new one.

Check the file against `SHA256SUMS`. `frostnode-0.4.0.zip` is the server node; see `node/README.md`.

QOIN has no guaranteed value. This is a proof of concept: expect bugs.
