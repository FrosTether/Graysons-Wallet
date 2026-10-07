# Graysons Wallet ⚡

Android wallet, node and miner for **Qoin (QOIN)** on Frostchain, part of [Finux](https://finux.tech).
One install gives you three apps:

- **Graysons Wallet**: create or restore a wallet, claim a `.frostchain` name, run your node
- **Frostoise**: the tone-gated miner. It only hashes while your phone hears 7.83 Hz
- **MyFrost**: send and receive QOIN by `@name`

> **Proof of concept (v0.3.0).** QOIN has no guaranteed value. Expect bugs and breaking changes.

## Download

**[Download GraysonsWallet-0.3.0-poc.apk](https://github.com/FrosTether/Graysons-Wallet/raw/main/releases/GraysonsWallet-0.3.0-poc.apk)** (Android 8.0 or newer).
Your phone will ask you to allow installs from your browser, since the app isn't on the Play Store.

SHA-256: `5594c078584f0e2a0edc6037b7f87f4044a89cd085eedae850cdfb436ca0694f`

## Tone mining

Frostoise mines only while it is locked on a 7.83 Hz signal, the Schumann resonance. You don't need special hardware:

1. Play a pulsed 7.83 Hz tone on any speaker near your phone.
2. Open **Frostoise**, allow the microphone and wait for **LOCKED · 7.83 Hz**.
3. Tap **Start mining**.

The microphone path needs the tone to pulse at least 5% deep. A coil generator works too, through the magnetometer.

Every block carries a *resonance proof* of the reading, and nodes reject blocks without one. The proof is the phone's own sensor report, so it is a ritual and a speed bump, not a security guarantee. Blocks are secured by proof of work.

## Join the network

Phones on the same Wi-Fi find each other automatically. To reach anyone else, open **Graysons Wallet → Node → Add** and enter a public node's address. Public nodes are listed in [Discussions](https://github.com/FrosTether/Graysons-Wallet/discussions).

## Chain parameters (as built in v0.3.0)

| | |
|---|---|
| Coin | QOIN, 11 decimal places |
| Block time | 5 minutes, LWMA difficulty over 60 blocks |
| Proof of work | double SHA-256 |
| Block 1 | 13,370.08241991 QOIN premine to `jacobfrost.frostchain` |
| Block reward | (2⁶⁴ − coins mined so far) ÷ 2²⁰ × 1.5, about 263.86 QOIN at launch |
| Mined coins unlock | after 12 blocks |
| Minimum fee | 0.0001 QOIN |
| Signatures | LMS_SHA256_M32_H10 with LMOTS_SHA256_N32_W4 (RFC 8554), 1,024 per key, automatic key rotation |
| Accounts | public, with optional `.frostchain` names |
| Resonance proof | 7.50–8.20 Hz, peak ≥ 10× noise, 4–60 s window, ≥ 200 samples at ≥ 16 Hz, at most 15 minutes old |
| P2P | HTTP on TCP 7830, LAN discovery on UDP 7830 |
| Genesis | 3 October 2026, 13:37 Eastern |

The emission curve follows CryptoNote and Wownero. Signatures and accounts do not: there are no ring signatures or stealth addresses, so transfers are public.

## Source

The app source is being moved into this repository. Until then, the release APK is the reference build.

## License

GPL-3.0. See [LICENSE](LICENSE).
