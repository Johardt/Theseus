# Shared quest progress and rewards

Install Open Parties and Claims 0.31.6 or newer for Minecraft 26.1.2 on the
server to integrate its built-in parties. Theseus clients do not need OPAC.
Theseus does not manage invitations, membership, ranks, or claims. External
OPAC party providers such as FTB Teams and Argonauts are not supported.

## Individual Progress

This is the only sharing setting. Its meaning follows Heracles:

- **On** (`"individual_progress": true`): only your actions advance your quests.
- **Off** (`false`, including when omitted): members of your OPAC party share
  task contributions and retain their own progress.

The button is disabled without server OPAC. Its saved value is preserved;
without OPAC or a party, contributions are personal. New quests use false,
matching the original format. Installing OPAC activates sharing for existing
quests with this setting omitted or false.

```json
"settings": { "individual_progress": false }
```

Reward audience has been removed. Its old JSON field is ignored and removed
when the quest is saved again; it is not sent in quest snapshots.

## Progress belongs to each player

Every player has their own task counters, completed tasks, pins, and reward
receipts. Contributions to shared quests copy to all actual party members,
including offline members and members whose prerequisites are not yet met.
Invitees and allied parties are excluded.

Leaving, being kicked, disbanding, or removing OPAC does not discard copied
progress. Joining or returning reconciles each shared task using the highest
counter among members, never adding totals that could duplicate earlier
contributions. Completed child tasks can therefore combine toward a composite.
Individual quests are never copied. Membership reconciliation runs on login,
normal one-second player updates, and quest interactions.

Event counters such as kills accumulate from members' future actions. Manual
item submissions accumulate and consume only the contributor's items.
Inventory checks use one member's observed inventory; separate inventories
are not summed. Shared counters keep their highest observed value, so another
member's empty inventory cannot undo progress. Location, advancement, check,
and statistic tasks can be satisfied by a member; statistic totals use the
highest observed personal value. XP submissions require one member to supply
the remaining amount. Composite children may be completed by different members.

## Prerequisites and locked rewards

Prerequisites gate **contributing** and **claiming**, not receiving shared
progress. Prerequisite checks include their own prerequisites, so a copied
completion cannot bypass an unfinished earlier quest.

For A → B → C, where B is individual and C is shared:

1. Alice finishes B and contributes to C.
2. Bob has not finished B. He sees C's copied counters but cannot contribute.
3. When C finishes, Bob retains its completed tasks and sees **Locked rewards**,
   with the prerequisite explanation. He cannot claim yet.
4. Finishing B immediately makes C's rewards claimable; C need not be repeated.
   This still works if Bob has left the party.

New members receive existing shared achievements, including completed quests.
They can claim their own rewards once their personal prerequisites are met.
Each player receives the full configured reward, chooses selectable rewards
independently, and can claim each quest/reward ID once. Changing parties or
receiving the same completion again never erases or duplicates claim receipts.
Commands and add-on rewards run separately for each claimant; global effects
can therefore repeat. The authoring validator warns about those effects.
Automatic and repeatable distribution are not implemented by this integration.

The quest tree exposes copied shared progress even when prerequisites are
locked. The details panel names the current sharing party and distinguishes
completed tasks from locked rewards. The Available rewards filter includes
only rewards that can currently be claimed.

## Operator recovery and resets

These commands require game-master permission and accept offline player UUIDs:

```text
/theseus rewards inspect <player-uuid> <quest>
/theseus rewards repair <player-uuid> <quest>
/theseus rewards reset <player-uuid> <quest> <reward-id>
/theseus rewards acknowledge <player-uuid> <quest> <reward-id>
```

`inspect` reports sharing provenance, prerequisite eligibility, completion
history, receipts, and interrupted grants. `repair` marks that player's quest
tasks complete while preserving receipts; prerequisites still gate rewards.
Those completed tasks can subsequently be shared with the player's party.

`reset` removes a reward receipt and interrupted-grant marker, permitting
another payout if the player is eligible. `acknowledge` records delivery
without running reward effects again. Investigate actual delivery first.

Task resets preserve receipts, pins, and completion history, but rewards
require the reset tasks to be completed again. Resetting shared tasks affects
current party members; individual tasks affect only the current player.
Retained achievements of former members can be reconciled again if they rejoin.
Quest task/reward edits reset affected counters across saved players while
retaining receipts. Renaming moves retained records to the new quest ID.

Reward grants persist a reservation before effects and a receipt afterward.
Interrupted grants block retries for operator review. Arbitrary commands and
add-on effects cannot be made transactional with the world save.

## Persistence and migration

Progress uses version 4 with a `players` object. Earlier UUID-root, version-2,
and version-3 files migrate automatically; older Theseus versions cannot read
version 4. Back up the full progress file before upgrading or downgrading.

Version-3 party-owned counters copy into current members' personal states when
that party is encountered. Known old reward recipients retain completed tasks
on load, including while offline or without OPAC. Unresolved old party records
remain under `legacy_parties` until their party/quest can be migrated, so missing
quests or unavailable membership are not discarded. Partial party counters
cannot identify former members who left before their membership was recorded.

## Development and reference

`./gradlew runClient -Popac` and `./gradlew runServer -Popac` include the pinned
OPAC dependency. It is not bundled in Theseus. The live multiplayer checklist
is in [the smoke-test kit](../smoke-test/README.md).

The original Individual Progress meaning was verified in
[Heracles' team synchronization](https://github.com/terrarium-earth/Heracles/blob/9c62ea01b6dd689645b4deac045d7ddbc80f40d8/common/src/main/java/earth/terrarium/heracles/common/handlers/progress/QuestsProgress.java#L125),
which skips individual quests and copies shared tasks while retaining each
member's reward receipts.
