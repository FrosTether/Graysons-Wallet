**Graysons Vault 0.4.3** is the first build signed with the permanent release key. Nothing else changes from 0.4.2.

From this build on, updates install over the app like any other: no more uninstalling, no more typing your 25 words.

- **Graysons Vault** (Graysons Wallet before 0.4.2): create or restore a wallet, claim a name, run your node.
- **Temporal:** seal a message for the future, or go back to any block. Each seal burns 1 QNR as gas.
- **QNR** is the ticker, and the Doge swap is gone.

The chain is the same v0.4 chain: Graysons Vault → Node shows `chain aa18513e5ee8db9f`.

### Install

1. Uninstall Graysons Vault 0.4.2 (or Graysons Wallet) one last time. Those builds were signed with test keys, so Android won't install over them.
2. Download `GraysonsVault.apk` below, open it, and allow installs from your browser when asked.
3. Open Graysons Vault, tap **Restore** and enter your 25 words. New here? Tap **Create wallet** and write down your new 25 words.

If you sealed Temporal capsules on 0.4.2, copy their capsule codes first (Temporal → Capsules → Copy capsule code), then add them back after the reinstall. The seals themselves are on the chain either way.

Check the file against `SHA256SUMS`. `GraysonsWallet.apk` is the same file under the old name, for old links. `frostnode.zip` is the server node; see [get.finux.tech/node](https://get.finux.tech/node/).

QNR has no guaranteed value. This is a proof of concept: expect bugs.
