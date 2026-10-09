**Graysons Vault 0.5.5** fixes syncing with the public node. A phone behind a home router or mobile data can reach the public node (`149.28.63.221`, port 7830), but the node can't reach the phone. Before 0.5.5 the phone only fetched blocks from the node and never sent its own chain, so the node stayed behind. Now a phone (or any node) that has more blocks than a peer sends that peer the blocks it's missing, and the peer switches over only if they validate and carry more work, the same check as before.

Nothing else changes: same chain (`chain b8b1ec3fefb96834` under Graysons Vault → Node), same block rules, same coin, QNR.

### Install

- **On 0.4.3 or newer?** Install `GraysonsVault.apk` over it. Your wallet, words and name stay.
- **On 0.4.2 or older?** Uninstall it first, install this, then tap **Restore** and enter your 25 words.
- **New here?** Install, tap **Create wallet** and write down your 25 words.

Check the file against `SHA256SUMS`. `GraysonsWallet.apk` is the same file under the old name, for old links. `frostnode.zip` is the server node; servers should update it too. See [get.finux.tech/node](https://get.finux.tech/node/).

This is a proof of concept: expect bugs.
