# Roadmap

Where Qoin is today, and what it takes to earn the kind of trust Monero has.
Monero is trusted because its code is open, it launched with no premine and no sale, anyone with a CPU can mine it, and its privacy has been reviewed for years. Each phase below moves Qoin toward that.

## Now: proof of concept (v0.4.2)

- The v0.4 chain, relaunched 8 October 2026 with a fair block 1 and three mining tones ([plan](docs/v0.4-plan.md)).
- Tone-gated mining on phones, blocks every 5 minutes.
- Public accounts with `.frostchain` names and LMS post-quantum signatures.
- frostnode with FrostExplorer, and a one-line server installer at [get.finux.tech/node](https://get.finux.tech/node/).
- Next: the QNR parameters, taking from Monero, Zcash, Dash and Steem: tail emission, view keys, node rewards and governance.

## 1. Open and reachable

- [ ] App source in this repository, buildable by anyone.
- [ ] Public headless node at a fixed address, listed in the README and the app.
- [ ] Signed GitHub Releases with SHA-256 checksums.
- [ ] Discussions open for miners, node operators and contributors.

## 2. Fair and decentralized

- [ ] Explain the block 1 reward. It was mined, but it pays about 50 times a normal block, and fair launch is the first thing Monero users will ask about.
- [ ] Review proof of work. Double SHA-256 favors GPUs and ASIC-style hardware over phones. Monero uses RandomX to keep CPUs competitive.
- [ ] Ten or more independent nodes run by people other than the founder.
- [ ] Seed node list in the app, so new phones can join without typing an address.

## 3. Private

- [ ] A private-send design with real ring signatures and stealth addresses, written up for outside review. Post-quantum ring signatures are still a research problem, so this needs careful work.
- [ ] Until private send is real, the app refuses a private send instead of sending publicly with a tag.
- [ ] No privacy claims anywhere until the design has been reviewed and shipped.

## 4. Hardened

- [ ] A separate testnet for new features, so mainnet coins are never at risk from experiments.
- [ ] Reproducible builds.
- [ ] Outside security review of consensus, LMS key handling and the wallet.
- [ ] Bug reporting process and a security contact.

## 5. Ecosystem

- [ ] **Oofcoins**: reward points for finux games like FrostGames and FrostMines. They're earned and spent inside games only. They're never sold and never swap for QNR, which keeps them safe for kids. Start them off-chain, then move them onto Frostchain as a balance that can't be transferred.
- [ ] **GCII, wrapped QNR on Ethereum Classic**, once Frostchain has outside users. It's already designed as a 1:1 wrapper and stays the only one. A wrapper is a vault: every wrapped coin must be backed by QNR locked at a public Frostchain address, and the key that mints wrapped coins is the most valuable key in the project.

## How to help

Try the app, run a node, and post your setup in [Discussions](https://github.com/FrosTether/Graysons-Wallet/discussions). Issues and pull requests are welcome.
