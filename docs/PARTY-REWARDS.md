# Party rewards

Install Open Parties and Claims 0.31.6 or newer for Minecraft 26.1.2 on the
server to integrate its built-in parties. Theseus clients do not need OPAC.
Theseus does not manage party invitations, ranks, membership, or claims.

In the editor, newly created quests default to **Party members** when the
server integration is available, and **Individual** otherwise. The choice is
saved explicitly. Imported and existing quests without a choice remain
individual; editing or duplicating a quest preserves its audience. There is
no pack-wide default override in this release.

The JSON setting is:

```json
"settings": { "reward_audience": "party" }
```

Use `"self"` for individual rewards. An unknown value is a validation error.

When a member completes a party-audience quest for the first time, everyone
in that party at completion earns eligibility for the quest's rewards. This
includes offline members and the owner, but excludes invitees and allies.
Each player claims the full configured reward and makes their own selectable
reward choices. Rewards are not delivered to everybody when one member claims.

Task counters, prerequisites, quest completion, and pins remain personal.
The quest graph retains personal lock/completion states and shows a small
party badge. **Available rewards only** in the display menu includes rewards
earned through a party, even if the recipient's personal quest is locked.
The reward tab distinguishes eligibility from personal completion and shows
the recorded source party. The header and display menu show current-party
context when available.

Joining later does not grant historical rewards. Another member's later
first completion can earn eligibility for the current roster. Leaving, being
kicked, changing the party's name or owner, or disbanding does not revoke
earned eligibility. Creating a party after solo completion does not share that
old completion. Existing completed quests are baselined on login; installing
OPAC does not automatically distribute historical rewards.

A player can claim each quest/reward ID once across personal and party
completion and across different parties. Repeating signals, changing party,
resetting tasks, or editing quest tasks does not erase reward receipts or the
recorded completion. Temporarily removed quest/reward IDs retain receipts so
reintroducing them cannot grant duplicate rewards after a restart.

Commands and add-on rewards execute separately for each claiming recipient.
Party-audience quests containing them produce an authoring validation warning:
global commands can repeat for every member. Repeatable and auto-claim settings
retain their current behavior; party integration does not implement automatic
or repeatable distribution.

If OPAC is removed, solo completion and previously earned eligibility still
work. A saved party audience remains visible with an integration warning and
is preserved when editing. The current adapter uses OPAC's built-in party
manager; external party providers such as FTB Teams or Argonauts are not
supported by this integration. A disabled/empty built-in party system has no
members to distribute to, so completion remains personal.

## Operator inspection and recovery

These commands require game-master permission and accept a player UUID, so
offline recipients can be inspected and repaired:

```text
/theseus rewards inspect <player-uuid> <quest>
/theseus rewards repair <player-uuid> <quest>
/theseus rewards reset <player-uuid> <quest> <reward-id>
/theseus rewards acknowledge <player-uuid> <quest> <reward-id>
```

`inspect` reports earned eligibility, completion history, claimed reward IDs,
and interrupted grants. `repair` gives that player eligibility without
changing task progress or erasing claims, recording operator provenance. Use
it for a missed distribution after investigating a provider failure; do not
assume the party's current roster is the roster at the original completion.

`reset` removes that reward's receipt and interrupted-grant marker. If eligible,
the player can receive it again. This is an explicit operator action with
duplicate-payout consequences. Existing editor reward-reset controls have the
same meaning. Ordinary player, quest, and task resets preserve eligibility,
receipts, and completion history.

Reward grants write a durable reservation before running side effects and a
receipt after successful delivery. If a grant is interrupted or its final
save fails, retries are blocked for operator review. Determine what was
actually delivered before using `acknowledge` to record delivery without
granting it again, or `reset` to allow retry. Arbitrary commands and add-on
effects cannot be made transactional with the world save; do not blindly
replay an interrupted selectable reward that may have delivered some choices.

Progress is saved in the world in a version-2 document with `version` and
`players` fields. The previous UUID-keyed root migrates automatically. Back
up the entire file, including earned eligibility, reservations, receipts,
completion history, pins, and deferred records.

## Development

`./gradlew runClient -Popac` and `./gradlew runServer -Popac` include the pinned
OPAC dependency at runtime. Without `-Popac`, OPAC remains a compile-only
optional integration. It is not bundled inside the Theseus release jar.
