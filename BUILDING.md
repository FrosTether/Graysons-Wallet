# Building Graysons Wallet

This repository holds the full source of the Android app and the Frostchain node.
It was rebuilt from the released 0.3.0 APK (see [Where this source came from](#where-this-source-came-from)).

## Layout

| Folder | What it is |
|---|---|
| `core/` | Frostchain itself in plain Java: consensus rules, LMS keys, wallets, the node and its peer-to-peer HTTP. No Android code. |
| `node/` | `frostnode`, the headless server node. See [node/README.md](node/README.md). |
| `app/` | The Android app: Graysons Wallet, Frostoise and MyFrost. Its screens are web pages in `app/src/main/assets/ui`. |

## The Android app

You need the Android SDK. The simplest route is Android Studio: open this folder and let it sync.
Without Android Studio, install the Android command-line tools and set `ANDROID_HOME`.

```bash
./gradlew :app:assembleDebug      # unsigned debug build
./gradlew :app:assembleRelease    # signed release build, needs keystore.properties
```

The APK lands in `app/build/outputs/apk/`.

### Signing

Phones only accept an update signed with the same key as the version already installed.
Create `keystore.properties` next to `settings.gradle`, and never commit it:

```properties
storeFile=/home/you/keys/graysons-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Keep a backup of the keystore somewhere other than the build machine. Losing it means every phone has to uninstall before it can update.

## The chain core and its tests

The core builds and tests without the Android SDK. Java 11 or newer is enough.

```bash
./gradlew :core:test
```

The suite takes a few minutes, because several tests mine real blocks:

| Test | Covers |
|---|---|
| `ConsensusTest` | Rewards, 11 decimals, difficulty, the fixed genesis |
| `LmsTest` | RFC 8554 keys, signing and verifying |
| `ResonanceTest` | Every resonance proof rule |
| `ChainStateTest` | The transaction rule: sends, fees, one-time keys, names, key rotation |
| `JsonTest` | The JSON used by every peer message and stored block |
| `WalletTest` | 25 seed words, encrypted wallet files, passwords, restore |
| `ChainTest` | Mining, signed sends, name claims, forged signatures, reload from disk |
| `NodeSyncTest` | Two nodes syncing over HTTP |
| `ReorgTest` | Two forks meeting, with the lighter one switching over |
| `WalletFlowTest` | Mine into a wallet, send by address and by name, claim a name |

## The server node

```bash
./gradlew :node:installDist
```

This makes `node/build/install/frostnode/`, which is the folder that goes to `/opt/frostnode` on the server.

## Checking the rebuild against the phones

The chain ID is the hash of the genesis block, so it only matches when consensus code matches byte for byte.
In the app, open **Graysons Wallet → Node**. It shows `chain 98ede167f7885dae`, and `frostnode` prints the same prefix when it starts.
If they differ, the rebuilt code and the phones are on different chains.

## Where this source came from

The original source wasn't available, so this code was rebuilt from the 0.3.0 APK (SHA-256 `5594c078…0694f`):

- The screens in `app/src/main/assets/ui` are the original files, unchanged.
- The manifest and resources were decoded from the APK.
- The Java code was decompiled with jadx. Every place where the decompiler produced wrong or uncompilable code was rewritten from the original bytecode. The fixes that change behavior are marked with a comment saying so: the transaction rule (`ChainState.apply`), the JSON reader, the API entry point, the HTTP connection handler, the sync loop, chain loading, chain reorgs, the miner's hash loop, seed-word encoding and the unsigned 32-bit reader.
- The tests above check the result end to end.
