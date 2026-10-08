**Graysons Wallet 0.4.0** is the relaunch: a new chain where block 1 pays the normal reward, and mining runs on three tones.
4.0 Hz mines on low, 7.83 Hz on medium and 11.11 Hz on high.

- **Mining is open:** the chain started Thursday 8 October 2026, 13:37 Eastern.
- **New chain:** Graysons Wallet → Node shows `chain 7cf2e3422f69c1e7`. Coins from 0.3.x don't carry over to it.
- **New screens:** a tone player in Frostoise (pulse or binaural, with an optional 40 Hz layer) and a Rez-style block explorer on the Node screen.

### Install

1. Uninstall 0.3.x. Android won't install over it, because it's signed with a different key.
2. Download `GraysonsWallet.apk` below, open it, and allow installs from your browser when asked.
3. Open Graysons Wallet, tap **Create wallet**, and write down your new 25 words.

Nothing from 0.3.x carries over. If you restore your 0.3.x words anyway, you get a new, empty account: 0.4 derives fresh signing keys from them, so the old chain's one-time keys never sign again.

Check the file against `SHA256SUMS`. `frostnode.zip` is the server node; see `node/README.md`.

QOIN has no guaranteed value. This is a proof of concept: expect bugs.
