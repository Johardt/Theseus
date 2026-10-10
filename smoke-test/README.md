# Theseus 1.1.0 smoke-test kit

## OPAC party reward pass

Use `./gradlew runServer -Popac` for the OPAC development dependency, or install
OPAC 0.31.6+ for 26.1.2 in a disposable dedicated server. Also run without OPAC
to verify optional loading. Full behavior is in [party rewards](../docs/PARTY-REWARDS.md).

1. With three members in an OPAC built-in party, create a new quest in Theseus.
   Reward audience must default to Party members; explicitly switch a second
   quest to Individual. Imported legacy quests must stay Individual.
2. Give the party quest a manual check task and a selectable XP reward. Take
   one member offline, then complete it with another member.
3. The online recipient sees available rewards without personal task completion
   or descendant unlocks. Each member chooses and claims separately. The offline
   member can claim after reconnecting. Each receives the full chosen reward.
4. Invite a late joiner and ally another party. Neither gets this old reward.
   Complete a different member's first completion after the late join: that
   completion can include the new roster, without paying existing claimants again.
5. Try duplicate claims, task resets, leaving/switching parties, renaming the
   party, transferring ownership, disbanding, and a server restart. Earned
   eligibility persists, while claimed reward IDs never pay again.
6. Remove OPAC from the server and from the Theseus client. Solo quests and saved
   eligibility still work; saved party audience stays visible with a warning.
   New quests without the integration default to Individual.
7. Use the display menu's Available rewards filter, including a personally
   locked quest earned through a party. Check personal graph colors, badge,
   rewards tab, current-party context, and individual selectable controls.
8. Inspect and repair an offline UUID with `/theseus rewards inspect` and
   `/theseus rewards repair`. Verify neither operation erases receipts or tasks.
   Explicit reward reset can allow payout again; acknowledgement runs no effects.
9. Test a failing add-on reward on a copy of the world: delivered receipts survive,
   interrupted grants block retry, and other members can still claim. Investigate
   delivery before retrying or acknowledging. Check the warning for global command
   rewards and verify malformed `reward_audience` values fail validation.

This directory is a disposable fixture pack for a real NeoForge client/server
run. It is intentionally outside `examples/` and is not copied into a shipped
mod or the dev profile automatically.

## Requirements

Use Java 25, Minecraft 26.1.2, and NeoForge 26.1.2.104 or newer. Resourceful Lib
4.0.1 or newer is required. The release jar includes Olympus. Use the same Theseus
jar on the client and server.

## 1. Build and start a real packaged run

Build the release artifact and use the non-sources JAR in a NeoForge 26.1.2
instance (client and, for the dedicated-server pass below, server):

```sh
./gradlew clean build
```

The artifact is:

`build/libs/theseus-neoforge-26.1.2-1.1.0.jar`

Copy that JAR and Resourceful Lib into the instance's `mods/` directory,
launch NeoForge, create a test world, and continue with the steps below. In a
packaged instance the runtime quest directory is
`<instance>/config/theseus/quests`; the fixtures can stay anywhere on your
desktop because the picker reads them directly.

## 2. Build and start a clean dev run

Use Java 25 and run the normal verification build:

```sh
./gradlew clean build
./gradlew runClient
```

For a clean profile, close Minecraft and move the existing dev config out of
the way first. The move is reversible:

```sh
mv run/config/theseus run/config/theseus.backup
```

Create or open a test world, then press **H** (or run `/theseus open`). The
demo pack may be installed automatically when the quest directory is empty.
Give yourself operator permissions so the editor and import actions are
available.

## 3. Import the happy-path fixtures

Open the editor and choose **Import**. Select these files together from
`smoke-test/fixtures/valid/`:

* `smoke_valid.json`
* `smoke_valid_second.json`
* `smoke_default_chapter.json`

Expected result:

* The native file picker shows the selected files. If the platform picker is
  unavailable, drag the same files onto the quest window.
* The import modal shows filename, byte size, proposed ID, and a **Details**
  action for each row.
* Import succeeds atomically. All three quests appear, and
  `smoke_default_chapter` is placed in the currently selected chapter because
  its document has no explicit group.
* Re-importing the same files is rejected as a duplicate quest ID and leaves
  the existing files unchanged.

## 4. Exercise the diagnostics modal

Open **Import** again and select every file in
`smoke-test/fixtures/invalid/`:

| Fixture | Expected diagnostic | Expected action |
| --- | --- | --- |
| `smoke_malformed.json` | Malformed JSON | Fix the syntax or remove the row |
| `smoke_duplicate_keys.json` | Duplicate JSON key at `$.tasks` | Remove the duplicate key |
| `smoke_invalid_registry.json` | Unknown biome, dimension, structure, stat, advancement, recipe, loot-table, and item IDs | Choose identifiers registered on this server |
| `smoke_cycle_a.json` + `smoke_cycle_b.json` | Dependency cycle with the exact cycle path | Remove or reorder the dependency edges |
| `smoke_unknown_type.json` | Unsupported task/reward type warning | It may be imported; the raw entry remains read-only in the editor |

Click **Details** on individual rows and on the batch summary. The dedicated
diagnostics view must be scrollable and wrap long paths/messages. Invalid rows
must keep the **Import** action disabled. Remove the invalid rows, leaving only
`smoke_unknown_type.json`, and import it to verify that warnings do not block a
document.

To prove all-or-none behavior, select `smoke_valid.json` and
`smoke_invalid_registry.json` together and press **Import**. The request must
be rejected and the valid quest must not be written as a partial commit.

To exercise duplicate IDs within one batch, select
`smoke_valid.json` and `smoke_valid_second.json`, edit the second row's ID to
`smoke_valid`, and verify that both rows show a duplicate-ID error and the
button remains disabled. Clear the ID field temporarily, type
`smoke_valid_second_copy`, and verify that revalidation clears the transient
empty/duplicate errors. Restore the original ID before importing.

## 5. Exercise the 1 MiB guard

Generate the oversized fixture (it is generated on demand so the repository
does not carry a 1 MiB blob):

```sh
bash smoke-test/generate_oversized.sh
```

Select `smoke_oversized.json` in the picker. The row should show a
`file_too_large` error, the diagnostics action should explain the 1 MiB limit,
and **Import** must remain disabled. No target file should be created.

## 6. Exercise clipboard and interaction polish

Import `smoke_clipboard_source.json` from `smoke-test/fixtures/clipboard/`,
then:

1. Select the quest and press **Ctrl-C**, then **Ctrl-V**. Enter
   `smoke_clipboard_copy` as the new ID. The copy should preserve the custom
   `clipboard_marker` field, settings, and task/reward JSON.
2. Try pasting again with the existing ID. It must be rejected without
   deleting or changing the source quest.
3. Use **Ctrl-Shift-V** to add the existing quest to the current chapter, then
   use **Ctrl-X** and paste/move it to another chapter. A failed move must
   leave the source and target state intact.
4. Tab through the import and quest dialogs. Press **Escape** in the task
   chooser, reward chooser, and chapter editor; each nested layer should close
   before the parent. Make a chapter edit, press **Escape**, and verify the
   dirty-dismissal confirmation appears.
5. Use a long filename/ID (or resize the window) to verify card text clips
   with an ellipsis. Registry-backed pickers should show both the friendly
   name and the identifier.

## 7. Optional dedicated-server pass

Run a server and client from separate terminals:

```sh
./gradlew runServer
./gradlew runClient
```

Connect the client to the local server, repeat sections 2–5, and watch the
server log. Registry and dependency failures should be returned as structured
diagnostics to the client; there should be no stack trace and no half-written
import directory.

## 8. Quality-gate scale pack

`fixtures/quality_gate/config/theseus/` contains a deterministic, self-contained
700-quest pack: ten chapters with 70 quests each. The same generated quest
documents and chapter metadata are also written to `run/config/theseus/` by
`generate_quality_gate.py`. The six existing `Getting Started` dev quests are
left in place, so the current dev profile contains 706 quests in total.

The pack covers every built-in task type and reward type, different task and
reward bundles, selectable rewards, nested composite tasks, dependency
branches and merges, chapter transitions, all nine card backgrounds, varied
item icons, task settings, chapter textures, and extension fields. The quest
JSON payload is about 1.5 MB, spread across many independent files to exercise
directory loading as well as the graph UI.

To restore the quality pack if the dev `run/` directory is removed, copy the
fixture config back into the development profile:

```sh
mkdir -p run/config/theseus
cp -R smoke-test/fixtures/quality_gate/config/theseus/. run/config/theseus/
```

To regenerate both identical copies after editing the generator:

```sh
python3 smoke-test/generate_quality_gate.py
```

For the manual gate, start `runClient`, open Theseus, and inspect each of the
ten quality-gate chapters. Confirm the chapter order and themed backgrounds,
pan and zoom through the 70-quest graphs, follow the branch and merge arrows,
and open quests with multiple tasks, composite children, and selectable
rewards. The first chapter starts without prerequisites; each later chapter
is gated by the previous chapter's final quest. On a clean profile containing
only the restored fixture pack, the runtime should load 700 quests; with the
existing six dev quests it should load 706.

## 9. Cleanup

Delete the generated oversized file and remove the imported smoke quests from
the editor. If you moved the dev config at the start, close Minecraft and
restore it with:

```sh
mv run/config/theseus.backup run/config/theseus
```

The fixture files can remain in the repository; they are not runtime config.
