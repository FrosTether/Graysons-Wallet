**Graysons Vault 0.5.2** uses about 30% less memory for the chain. Every phone keeps the whole chain in memory, and before this it grew by about 90 MB a year; now it's about 65 MB. Nothing else changes: same chain (`b8b1ec3fefb96834`), same 13.37 QNR a block.

- The block index is keyed by a number instead of a 64-character text copy of each block's hash.
- Each block shares its link to the previous block instead of keeping its own copy.
- Mining rewards in your activity no longer store the block's hash; the block is found by its height.

### Install

- **On 0.4.3 or newer?** Install `GraysonsVault.apk` over it. Your wallet, words and name stay.
- **On 0.4.2 or older?** Uninstall it first, install this, then tap **Restore** and enter your 25 words. That's the last time.
- **New here?** Install, tap **Create wallet** and write down your 25 words.

Check the file against `SHA256SUMS`. `GraysonsWallet.apk` is the same file under the old name, for old links. `frostnode.zip` is the server node; see [get.finux.tech/node](https://get.finux.tech/node/).

QNR has no guaranteed value. This is a proof of concept: expect bugs.
