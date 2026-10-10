# Plan 001: Enable individual reward claims from OPAC party completions

Status: PROPOSED; direction; P1; effort L; risk medium; no dependencies.
Planned against commit `8e742e9` on 2026-10-01. The user favors Party members
as the authoring default when OPAC is available, with no pack-wide override in
v1. Remaining product rules below are recommendations.

## Goal and boundaries

Let a pack author enable party rewards on a quest: when a member completes it,
every actual member of that OPAC party at that moment earns eligibility to
claim their own rewards. Each member receives the full configured reward.
Members choose selectable rewards independently. Offline members claim on
their next login. Solo players keep existing behavior.

This first release shares reward eligibility, not task counters, quest
completion, or prerequisite unlocks. The UI must explain that distinction.
OPAC remains responsible for creating parties, invitations, membership, ranks,
and claims. Do not create a second party system or include allied parties.
Do not add claim-area tasks, party currencies, automatic bulk distribution,
or once-per-party rewards in this release.

Do not commit unless the user explicitly requests it. Do not edit AGENTS.md or
ROADMAP.md. Preserve unrelated working changes in QuestScreenRenderer.java,
DependencyArrowLayout.java, and DependencyArrowLayoutTest.java.

## Current state and conventions

Active Java 25 / Minecraft 26.1.2 / NeoForge code is under `neoforge/`;
assets are under `common/src/main/resources/assets/`. Build configuration
already defines JUnit 6 and `useJUnitPlatform()`.

- `core/QuestRuntime.java:41`: progress is
  `Map<UUID, Map<String, QuestProgressState>>`, keyed by player UUID.
- `core/QuestProgressState.java`: task counters, claimed reward IDs, and pins
  currently share one per-player quest state. Claims are per reward ID.
- `core/QuestRuntimeProgression.java:367`: `claim` currently requires
  `isComplete(player, quest)` and skips already claimed rewards. It validates
  all missing rewards, grants each, then marks its ID claimed.
- `core/QuestRuntimeProgression.java:903`: built-in rewards and extension
  handlers grant to a live ServerPlayer. Keep that recipient-specific contract.
- `core/QuestRuntime.java:633`: `changed` compares previous completion state
  for notifications; eligibility creation must happen before persistence and
  independently of notification suppression.
- `core/QuestRuntime.java:696`: snapshots currently expose personal completion,
  progress, claimed IDs, and pins. Add explicit reward eligibility rather than
  pretending the recipient completed the quest.
- `core/QuestRuntime.java:777,867`: load/save use a legacy UUID-keyed JSON root
  and preserve deferred quest states. New persisted fields must round-trip
  without destroying these records.
- `core/QuestDraft.java`: snapshot-only fields are explicitly stripped when
  saving editor documents. Add new runtime metadata to this exclusion list.
- `client/QuestClientSnapshot.java`, `QuestDetailsPanel.java`,
  `QuestAuthoringPanelRewardEditor.java`, and `QuestAuthoringPanelDrafts.java`
  are the client model, reward display, and authoring entry points.

Paths above are relative to `neoforge/main/java/me/johardt/theseus/`.
Follow existing injected ports such as QuestWorld and QuestSync. Model tests
after `neoforge/test/java/me/johardt/theseus/core/QuestRuntimeSeamTest.java`,
which uses an in-memory store, fake world, and recording sync.

## OPAC integration facts and initial verification

Official references:

- https://github.com/thexaero/open-parties-and-claims#developing
- https://thexaero.github.io/open-parties-and-claims/javadoc/xaero/pac/common/server/api/OpenPACServerAPI.html
- https://thexaero.github.io/open-parties-and-claims/javadoc/xaero/pac/common/server/parties/party/api/IPartyManagerAPI.html
- https://thexaero.github.io/open-parties-and-claims/javadoc/xaero/pac/common/server/parties/party/api/IServerPartyAPI.html

Documented entry points are OpenPACServerAPI.get(server).getPartyManager(),
getPartyByMember(UUID), party.getId(), and getMemberInfoStream(). OPAC exposes
online members separately; do not use only that stream for eligibility.
Use the party UUID, not the owner's UUID, for provenance. Include the owner
once and verify the targeted version's member-stream behavior.

The public Javadoc is not a pinned 26.1.2 contract. Before implementing, select
and verify an actual NeoForge 26.1.2 artifact from the author's Maven repository,
confirm its mod ID and registration/lifecycle APIs from its source, and verify
offline roster support. Do not use private implementation classes or reflection.
Stop and report if offline members or stable party identity cannot be obtained
through the supported API. The API spike must also establish whether configured
external party providers work through this API; promise only tested providers.

## Recommended product contract

1. Quest setting: reward audience = Self or Party members. New quests created
   with the server integration available default to Party members; otherwise
   they default to Self. Save the chosen audience explicitly in each new quest.
   Existing/imported quests missing the field retain legacy Self behavior;
   installing OPAC must not silently change their reward semantics. Cloning or
   editing preserves an existing quest's audience, including legacy Self.
   Keep it quest-wide in v1; omit a pack-wide default override and inheritance
   until needed. A later authoring default can change new-quest selection without
   rewriting existing quests.
2. Snapshot membership on the first qualifying incomplete-to-complete
   transition after the feature is enabled. No retroactive scan of old completed
   quests, no eligibility on party join, and no eligibility from creating a party
   after solo completion. Suppressing notifications must not suppress eligibility.
3. Include offline members, exclude invitees and allies. Resolve membership on
   the server, never accept recipient UUIDs from the client.
4. Eligibility is earned and durable. Leaving, being kicked, disbanding, renaming,
   or transferring ownership does not revoke it. Late joiners must earn their own
   completion or qualify in a later legitimate completion by another member.
5. A player can claim each quest reward ID once across all parties and personal
   completion. Existing claimed IDs remain authoritative; switching parties,
   restarting, or another member completing cannot reset them.
6. Party eligibility bypasses personal completion and dependency checks only for
   claiming the eligible rewards. It does not unlock descendant quests. Eligible
   rewards must remain discoverable even if their quest is personally locked.
7. Pins remain personal. Expose personal completion and party-earned reward
   availability separately in the graph, details, HUD, and commands.
8. Commands and add-on rewards keep their existing per-recipient execution
   semantics. Warn authors that a party audience executes a command once for each
   claiming recipient; never imply arbitrary global commands are safe to repeat.
9. Normal rewards remain manually claimed. Existing auto-claim settings must
   not silently gain new behavior; verify whether auto-claim is implemented today.
   If implementing it later, selectable rewards still need each member's choice.
10. When OPAC is absent or disabled, new solo completions still reward the player;
    saved party-earned eligibility stays claimable. A provider failure must not be
    mistaken for a successful empty roster: retain personal completion, report
    the problem, and require operator recovery for missed distribution.
11. Normal player progress reset must not become a way to erase reward receipts
    or redistribute party rewards. Specify a separate permission-gated reward
    reset, with explicit consequences. Never revoke another member's entitlement
    as a side effect of resetting one player's counters.

## Necessary UI

**Authoring:** add Reward audience to quest settings, with help text: “Party
members present at completion can each claim the full reward, including offline
members.” Show the selector when the server integration is available, defaulting
to Party members for new quests. If unavailable, hide the selector for ordinary
Self quests; for saved Party members quests, display the saved audience and an
integration warning so the behavior is understandable and round-trips intact.
Use server capability, not client-side mod installation, to decide availability.
Add a command/add-on repetition warning.

**Quest browser:** a small party reward badge; distinguish “You completed this”
from “Rewards earned through your party.” Add an Available rewards filter that
includes party eligibility on personally locked quests. Personal graph unlock
status must remain accurate.

**Quest details:** show audience, source of eligibility, and “Claim your rewards.”
Keep individual selectable reward controls. Clear states: not yet eligible,
available, claimed, unsupported reward, and party integration unavailable.
Member claim counts and a roster are optional; v1 does not require tracking the
whole party's claims in the client UI.

**Party context:** a compact current-party name/member count in the quest screen
when the server can provide it. A party-management shortcut is optional and only
enabled if the installed client exposes a supported entry point. Do not require
OPAC on the client just to display Theseus's server-provided party context.

**Notifications:** “Alex completed [quest]. Your rewards are available.” Notify
online recipients once; offline recipients see saved availability on login.
Avoid one notification per reward or replaying every historical toast on login.

**Administration:** extend status/diagnostics and reset commands to explain
eligibility, provenance, and claim receipts. Provide a targeted way to repair
missing eligibility without deleting task progress or granting duplicate items.

## Implementation sequence and verification gates

Before starting: `git diff --stat 8e742e9..HEAD -- neoforge build.gradle.kts` and
`git status --short`; compare the current-state facts above with live code.
Stop if runtime architecture has changed enough to invalidate this plan.

1. **Verify optional integration.** Add the verified compile-only dependency and
   optional metadata in build.gradle.kts and neoforge.mods.toml. Create a small
   injected PartyLookup port and isolated OPAC adapter with an absent-provider
   implementation. Return immutable UUID/name/member snapshots. Verify
   `./gradlew compileJava` exits 0; smoke-start dedicated servers both with and
   without OPAC and confirm no missing-class startup failures.
2. **Add the contract and durable eligibility.** Update QuestDefinition settings,
   validation, QuestDraft, and persistence. Store earned eligibility per player
   and quest with minimal source-party/completer provenance; keep claimed reward
   IDs global per player/quest/reward. Version the progress envelope and migrate
   the legacy UUID root without losing pins or deferred data. Keep eligibility
   and receipts in the same atomic progress save, rather than loosely coupling
   two independently saved files. Verify targeted QuestProgressStateTest,
   QuestDefinitionCompatibilityTest, and new migration tests all pass.
3. **Create eligibility and use it in claims.** Change completion-transition and
   claim paths in QuestRuntime/QuestRuntimeProgression. Snapshot the roster once,
   deduplicate recipients, preserve personal state, then persist and sync affected
   online recipients. Validate claim requests against saved eligibility and
   receipts on the server thread. No loop that grants to everybody when one
   person presses Claim. Verify new PartyRewardEligibilityTest and
   QuestRuntimeSeamTest pass, including fake offline recipients.
4. **Synchronize and expose it.** Add explicit eligibility and integration metadata
   to snapshots and QuestClientSnapshot. Update draft field stripping, details,
   graph/HUD availability presentation, filtering, authoring, and translated
   assets. Limit client data to what the viewer needs. Update QuestNetwork and
   QuestCommands to use the same eligibility-aware claim service. Verify client
   snapshot, authoring, presentation, and draft round-trip tests pass.
5. **Verify lifecycle and recovery.** Add eligibility/receipt diagnostics and
   operator repair/reset behavior. Update docs/COMPATIBILITY.md,
   docs/BACKUP-RECOVERY.md, docs/LIMITATIONS.md, and smoke-test/README.md. Verify
   `./gradlew test build` exits 0. In a dedicated-server smoke test, one of three
   members completes, all three claim separately, an offline member rejoins, and
   a late joiner cannot claim. Restart, transfer ownership, leave/disband/switch
   parties, and retry claims: no second payout. Repeat without client-side OPAC.

These commands are derived from build configuration; no build or tests were run
during planning. Narrow JUnit runs can use `./gradlew test --tests '*ClassName'`.

## Required tests and done criteria

- Legacy progress migration preserves counters, pins, claimed IDs, deferred
  quests, and subsequent round-trips.
- Missing integration preserves old personal behavior and earned eligibility.
- Completion creates entitlement once; repeated signals and inventory refreshes
  do not create a new distribution.
- Roster includes owner/offline members once, excludes invitees/allies/late joins.
- Personal claim then party eligibility, and the reverse, each pay only once.
- Separate selectable choices; unsupported extension blocks only that recipient's
  claim and does not erase another member's eligibility.
- Stale or forged client claim attempts cannot select arbitrary recipients.
- Joining/leaving/ownership transfer/disbanding/restart preserve earned receipts.
- Suppressed login notifications do not bypass eligibility processing; already
  completed legacy quests do not trigger a retroactive party payout.
- Partial reward failure preserves successful receipts. Arbitrary command/add-on
  side effects cannot promise exactly-once delivery across a crash; test and
  document the limitation rather than blindly replaying interrupted grants.
- Runtime-only eligibility fields never appear in saved quest definitions.
- New quests default to Party members with server integration, Self without it,
  and serialize an explicit audience. Editing/cloning/importing legacy quests
  retains Self; existing explicit values survive integration removal/reinstall.
- Available-reward UI works even when the recipient has not unlocked the quest.
- All named tests and `./gradlew test build` pass; multiplayer smoke test passes;
  no unrelated existing edits are overwritten; no commit is made without request.

Stop and report if arbitrary side effects require stronger transactional
guarantees, offline roster lookup fails, an external provider changes party
identity semantics, or a scope change would silently grant historical rewards.

## Follow-up: cooperative quest progress

If the intended experience is a party progressing through a quest tree together,
design this separately before implementation: progress scope Personal/Party and
reward audience are independent concepts. Party counters use the stable party
UUID; personal claims and pins stay per player. Membership changes switch the
view without silently merging personal or former-party progress.

Specify task semantics individually: accumulate kill/craft events from members;
consume submitted items or XP only from the submitting player; define whether
inventory/location/advancement tasks mean any member or every member. Never sum
repeated inventory snapshots or mirror consumption across member inventories.
Also specify shared prerequisite evaluation, offline completion, late-joiner
catch-up, party disband retention, and explicit operator migration of existing
progress. Once-per-party command/claim-limit rewards can follow as a distinct
recipient model, not an accidental variation of per-player reward distribution.
Pack-wide authoring defaults and bulk audience editing are also deferred from v1.

This investigation covered quest progress, claims, persistence, sync, authoring
entry points, and public OPAC API documentation. It was not a whole-repository
audit or a binary integration test against a pinned OPAC artifact.
