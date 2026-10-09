# Linking Qoin to Monero: merged mining

A design note for review. No code yet. Nothing here changes the chain until it's built, tested on a testnet and shipped as a planned upgrade.

Two rules from the start:

1. **The tones stay the reward.** A phone that proves it heard a mining tone is who gets paid.
2. **Spending stays on LMS signatures** (RFC 8554). That is what keeps Qoin's accounts quantum-safe.

## What merged mining is

A Monero miner is already hashing. In merged mining it also commits a Qoin block's hash inside the Monero block it is working on. If that Monero hash is good enough for Qoin's difficulty, Qoin accepts it as its own proof of work. The miner earns Qoin on top of Monero without hashing twice. Namecoin and Dogecoin do this with Bitcoin and Litecoin, and Tari does it with Monero.

What Qoin borrows is Monero's *hashpower*, which is what protects a small chain from being rewritten. It does not borrow Monero's signatures, so rule 2 isn't touched.

## The shape of it

Today a phone does both jobs: it proves a tone and it hashes. Merged mining splits them.

| Job | Who | What they do |
|---|---|---|
| **Propose** | A phone that hears a tone | Builds a block template: transactions, its own address as `miner`, and its resonance proof. Publishes it. |
| **Seal** | A Monero miner or pool | Commits the template's hash in its Monero block. If the RandomX hash meets Qoin's target, the Qoin block is sealed. |

A Qoin block would carry the template, plus the proof that a Monero block committed to it: the Monero header, the miner transaction with its merge-mining tag, and the Merkle paths linking them. A node checks the paths, then recomputes the RandomX hash of the Monero header and compares it with Qoin's target (not Monero's).

The reward splits in the block itself: most to the proposing phone (the tone), a set share to the sealing miner's address. The share is what makes merged mining worth a Monero miner's trouble.

## What this does for the tones

The tone stays the claim ticket. One honest limit applies today and would still apply: the chain checks that a resonance proof is *consistent* (peak, spectrum, samples), but nothing proves a microphone heard anything, so anyone can produce one in software. The tone is a reward rule, not a security guarantee. Merged mining doesn't fix that or make it worse; it moves the security work to Monero's hashpower.

## Quantum safety

- Account signatures: LMS, hash-based, unchanged.
- Proof of work: RandomX is a hash function. A quantum computer speeds up any hash search a little (Grover), which is true of every proof-of-work chain.
- Monero's own signatures never enter Qoin.

## Open problems

1. **Who picks the proposal?** Many phones propose for the same height, and a Monero miner decides which one to seal. If the miner chooses freely, the miner decides who is paid, not the tone. The chain needs a rule that picks the winner, for example the valid proposal with the lowest hash of its template, so any other seal is invalid. Miners then have to see all the proposals, which means a gossip channel for them.
2. **Why would Monero miners opt in?** Not until Qoin has value. Tari ships merge-mining tools for pools; Qoin would need its own proxy or pool support. Until miners show up, the security gain is on paper.
3. **Phones checking RandomX.** RandomX needs about 256 MiB of memory in its light mode (2 GiB in fast mode) and is slow to verify in light mode. Options: phones verify only recent blocks, or they run as light clients that trust a node they chose. This is the biggest decision.
4. **Java.** There is no RandomX in Java that I'd trust. The C library (BSD-3) can be called through JNI from Android and frostnode, which is real build work.
5. **Block timing.** Qoin's 5-minute spacing and 270-second minimum would have to fit work that arrives on Monero's 2-minute schedule.
6. **Transition.** This is a rule change and needs a planned upgrade or a relaunch. The chain is days old, so doing it now is far cheaper than later.

## The cheaper cousin: Bitcoin-style merged mining

Qoin's proof of work is double SHA-256, the same as Bitcoin's. That means Bitcoin-style merged mining (AuxPoW, as Namecoin does it) works without changing Qoin's hash function, and checking a Bitcoin header is cheap enough for any phone. It has the same proposer/sealer split and the same opt-in problem, but no RandomX problem. It also doesn't tie Qoin to Monero's community, which is the point of your idea.

| | Monero (RandomX) | Bitcoin (SHA-256d AuxPoW) |
|---|---|---|
| Changes Qoin's hash | Yes, to RandomX | No |
| Phones can verify | Hard (256 MiB or more) | Easy |
| New code | JNI, Monero header and tag parsing | Header and Merkle checks |
| Monero community link | Yes | No |
| Miners who could opt in | Monero miners | Bitcoin miners (mostly ASICs) |

## Suggested order

1. Reorg limit and checkpoints (a separate branch, `checkpoints-reorg-limit`). Any merged-mining design needs them as a safety net.
2. A **testnet**, as the roadmap already says. New consensus ideas shouldn't run on mainnet coins.
3. On the testnet, prototype the propose/seal split with Bitcoin-style AuxPoW first. It proves the winner-picking rule and the reward split cheaply.
4. Then add RandomX and Monero's merge-mining tag, once the phone-verification question has an answer.

## To check before building

These details are from memory of the specs and need confirming against the source: the exact layout of Monero's merge-mining tag, the RandomX seed-block schedule, and how Tari builds and checks its proof.

- Monero: `src/cryptonote_basic/tx_extra.h` (the merge-mining tag), and the RandomX specification (`doc/specs.md` in the RandomX repository)
- Tari's RFCs on merge mining Monero
- Bitcoin wiki: *Merged mining specification*
