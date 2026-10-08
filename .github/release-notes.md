**Graysons Wallet 0.4.1** paces the chain and fixes the tone.

- **Blocks every 5 minutes:** a block can't come sooner than 4½ minutes after the last one, and the difficulty aims for 5 minutes on average. Frostoise shows "next block in m:ss" while it waits.
- **Louder tone:** the pulse now rides on an 880 Hz hum instead of 220 Hz, which phone and laptop speakers play loudly, so a phone's own speaker can unlock Frostoise.
- **Chain restart:** these are new chain rules, so the chain started over. Blocks mined on 0.4.0 don't carry over.

v0.4 is the relaunch: a new chain where block 1 pays the normal reward, and mining runs on three tones.
4.0 Hz mines on low, 7.83 Hz on medium and 11.11 Hz on high.

- **Mining is open:** the chain started Thursday 8 October 2026, 13:37 Eastern.
- **New chain:** Graysons Wallet → Node shows `chain aa18513e5ee8db9f`. Coins from 0.3.x don't carry over to it.
- **New screens:** a tone player in Frostoise (pulse or binaural, with an optional 40 Hz layer) and a Rez-style block explorer on the Node screen.

### Install

1. Uninstall any older Graysons Wallet. Android won't install over it, because it's signed with a different key.
2. Download `GraysonsWallet.apk` below, open it, and allow installs from your browser when asked.
3. Open Graysons Wallet. If you made a wallet on 0.4.0, tap **Restore** and enter its 25 words. If you're new or coming from 0.3, tap **Create wallet** and write down your new 25 words.

Nothing from 0.3.x carries over. If you restore your 0.3.x words anyway, you get a new, empty account: 0.4 derives fresh signing keys from them, so the old chain's one-time keys never sign again.

Check the file against `SHA256SUMS`. `frostnode.zip` is the server node; see `node/README.md`.

QOIN has no guaranteed value. This is a proof of concept: expect bugs.
