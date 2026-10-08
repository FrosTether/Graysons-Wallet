# Graysons Wallet ⚡

Android wallet, node and miner for **Qoin (QNR)** on Frostchain, part of [Finux](https://finux.tech).
The ticker is QNR, for Qoin's CryptoNote roots. One install gives you four apps:

- **Graysons Wallet**: create or restore a wallet, claim a `.frostchain` name, run your node and watch blocks arrive in the explorer
- **Frostoise**: the tone-gated miner. It only hashes while your phone hears a mining tone
- **MyFrost**: send and receive QNR by `@name`
- **Temporal**: a time machine. Seal a message for the future, or go back to any block. Each seal burns 1 QNR as gas

> **Proof of concept (v0.4.2).** QNR has no guaranteed value. Expect bugs and breaking changes.

## Download

**[Download GraysonsWallet.apk](https://github.com/FrosTether/Graysons-Wallet/releases/latest/download/GraysonsWallet.apk)** (Android 8.0 or newer).
This link always serves the newest build. Checksums and notes are on the [release page](https://github.com/FrosTether/Graysons-Wallet/releases/latest).
To share the app, send people to **[get.finux.tech](https://get.finux.tech)**: the download, the mining tones and how to start, on one page.
Your phone will ask you to allow installs from your browser, since the app isn't on the Play Store.

**Coming from 0.3.x?** 0.4 runs a new chain, so you start fresh:

1. Uninstall 0.3.x. Android won't install 0.4 over it.
2. Install the latest version, tap **Create wallet** and write down your new 25 words.

Nothing from 0.3.x carries over: not coins, names or blocks. If you restore your 0.3.x words anyway, you get a new, empty account, because 0.4 derives fresh signing keys from them. That keeps the old chain's one-time keys from ever signing again.

**Updating from 0.4.0 or 0.4.1?** Those were signed with throwaway test keys, so uninstall first, install the new one,
and tap **Restore** with your 25 words. Once builds are signed with the permanent release key, updates install over the
app like any other. The release page says which key signed each build.

Old links to `releases/GraysonsWallet-0.3.0-poc.apk` now download the newest app. The original 0.3.0 APK, which runs the
old chain, is kept in [`releases/0.3.0/`](releases/0.3.0/) for the record.

## Tone mining

Mining opened at launch: **Thursday 8 October 2026, 13:37 Eastern**.

Frostoise mines only while it's locked on one of three tones. The tone sets how hard the phone works. The reward per block is the same.

| Tone | Band | Mining |
|---|---|---|
| 4.0 Hz | delta | Low: up to 2 threads |
| 7.83 Hz | theta, the Schumann resonance | Medium: half your threads |
| 11.11 Hz | alpha | High: all your threads |

You don't need special hardware:

1. Open **Frostoise** and pick a tone. Play it on a speaker near the phone, from the app's tone player or anything else that makes a pulsed tone.
2. Allow the microphone and wait for **LOCKED**.
3. Tap **Start mining**.

The microphone needs the tone to pulse at least 5% deep, so binaural beats (headphones) are for listening only. A coil generator works too, through the magnetometer.

Every block carries a *resonance proof* of the reading, and nodes reject blocks without one. The proof is the phone's own sensor report, so it's a ritual and a speed bump, not a security guarantee. Blocks are secured by proof of work.

## Join the network

Phones on the same Wi-Fi find each other automatically. To reach anyone else, open **Graysons Wallet → Node → Add** and enter a public node's address.
Public nodes are listed at **[get.finux.tech/node](https://get.finux.tech/node/)**, which also shows how to run one: a single command on a server.

## Temporal

Temporal seals capsules: a message with an opening date. Its fingerprint goes on the chain in the memo of a 1 QNR payment
to an address nobody can spend from, `fcn22knwovgbfytc6qyjczvb4kkxwjhqjmoaru` (the SHA-256 of the words
`frostchain/burn` and `temporal/v1`). The block that carries it proves the words existed by then. The words stay on the
phone, shut until the date. Temporal also goes back to any moment since genesis: the block mined then, the tone it was
mined on, and what you held.

It's not a consensus rule: a seal is an ordinary payment. The same rules run on the web at
[get.finux.tech/temporal](https://get.finux.tech/temporal/), in `Temporal.java`, and in FrostExplorer's API.

## Chain parameters (v0.4)

| | |
|---|---|
| Coin | Qoin, ticker QNR, 11 decimal places |
| Block time | 5 minutes on average, never sooner than 4½ minutes after the last block. LWMA difficulty over 60 blocks; blocks may be at most 30 s ahead of a node's clock |
| Proof of work | double SHA-256 |
| Block reward | (2⁶⁴ − coins mined so far) ÷ 2²⁰ × 1.5, about 263.88 QNR at launch. At most 184,467,440.737 QNR, half of it mined in about 4.6 years |
| Block 1 | Mined like any block, at the normal reward |
| Founder names | `jacobfrost.frostchain` and `agorajay.frostchain` belong to `fc3dlrkt7demghofiz7wzjz2st4vzvjiskxumv` from genesis, and the genesis block says so |
| Mined coins unlock | after 12 blocks |
| Minimum fee | 0.0001 QNR |
| Signatures | LMS_SHA256_M32_H10 with LMOTS_SHA256_N32_W4 (RFC 8554), 1,024 per key, automatic key rotation |
| Accounts | public, with optional `.frostchain` names |
| Resonance proof | 3.75–4.25, 7.50–8.20 or 10.80–11.40 Hz, peak ≥ 10× noise, 4–60 s window, ≥ 200 samples at ≥ 16 Hz and above twice the tone, at most 15 minutes old |
| P2P | HTTP on TCP 7830, LAN discovery on UDP 7830 |
| Genesis | 8 October 2026, 13:37 Eastern. Chain ID `aa18513e5ee8db9f…` |

The emission curve follows CryptoNote and Wownero. Signatures and accounts do not: there are no ring signatures or stealth addresses, so transfers are public.
[QNR vs XMR](https://get.finux.tech/qnr/) compares the two side by side.

## Roadmap

See [ROADMAP.md](ROADMAP.md) for what comes next and how to help.

## Source

The full source is here: the chain core in `core/`, the server node in `node/` and the Android app in `app/`.
It was rebuilt from the 0.3.0 APK, then changed for the v0.4 relaunch, and is covered by an end-to-end test suite.
See [BUILDING.md](BUILDING.md) to build the app, run the tests or set up a server node.

## License

GPL-3.0. See [LICENSE](LICENSE).
