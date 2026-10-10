# Design note: masternodes for Qoin, the Dash way

Status: proposal. Nothing here is in consensus yet. It changes the block reward, so every node and phone has to update
before it turns on.

## What Dash does

A Dash masternode locks 1,000 DASH in one output, runs a full node at a fixed IP, and in return takes a share of every
block reward. Payments go round-robin: the masternode that has waited longest is paid next. Nodes that stop serving get
banned from payment (proof of service). Dash's collateral is about 0.005% of its 18.9 million maximum supply.

## Proposal for Qoin

| | |
|---|---|
| Collateral | **1,337 QNR**, locked, not spent |
| Reward split | **60% to the miner, 40% to one masternode** per block. No treasury |
| Who is paid | The registered masternode that has waited longest since it was last paid (or since it registered) |
| Eligible after | 288 blocks (one day) after registering, so nobody registers just before their turn |
| Staying eligible | A heartbeat transaction at least every 288 blocks, or it's skipped until the next one |
| Unlocking | A retire transaction. The collateral can be spent 12 blocks later, the same as mined coins |
| Turns on at | A fixed height, at least two weeks after the release that carries it |

**Why 1,337 QNR.** It's exactly 100 blocks of first-year reward, about 8 hours of the whole network's mining. Taken
against Qoin's supply at the tail (about 29.9 million QNR), Dash's ratio gives about 1,580 QNR, so 1,337 is in the same
place and matches the 13.37 theme. By the end of the first year (about 1.4 million QNR mined) there's room for at most
about 1,050 masternodes. A higher bar like 5,000 QNR would allow only about 280.

**Why 60/40 with no treasury.** Qoin is tone-mined on phones, so the miner keeps most of each block. Dash's 10% treasury
is left out on purpose: the [roadmap](../ROADMAP.md) aims for Monero-style trust, and a fund someone controls works
against that.

## How it fits the chain

Qoin uses accounts, not outputs, so the collateral is a locked balance on the owner's account:

- **Register** (new transaction type 4): locks 1,337 QNR from the sender's balance and records the node's `host:port`.
  One masternode per account; a second one needs a second account.
- **Heartbeat** (type 5): fee only. It proves the owner's key is still in use and keeps the node in the payment queue.
- **Retire** (type 6): removes the masternode from the queue and unlocks the collateral after 12 blocks.

The payee isn't written into the block. Every node works it out from the chain state while applying the block and
credits 40% of the reward to it, so the block format and the miners' work stay the same. Undoing a block in a reorg
undoes the payment and the queue move with it.

## What it doesn't do

- **Proof of service is weak.** The heartbeat proves the key holder is around, not that the node serves anyone. Dash
  checks service with quorums of masternodes, which is a lot more machinery. A start here would be nodes testing each
  masternode's `host:port` and sharing what they find, outside consensus.
- **Early holders get the first masternodes.** About 5,000 QNR has been mined so far (height ~384), enough for three.
  Whoever holds coins when it turns on is paid first, which is the same complaint people make about Dash's launch.
  Turning it on later, when more people mine, spreads it out.
- **No InstantSend, PrivateSend or governance.** Those are Dash features built on top of masternodes. They'd come later,
  if at all.

## What it takes

Changes to `Consensus`, `Tx`, `ChainState` and `Chain` in `core/` (the new transaction types, the locked balance, the
payment queue and reorg undo), the wallet screens for register, heartbeat and retire, a Masternodes view in FrostExplorer,
and tests for payment order, reorgs and the activation height. Phones and servers both need the update before the
activation height, or they split from the chain.
