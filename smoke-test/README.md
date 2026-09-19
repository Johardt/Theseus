# Theseus 1.0.0 smoke-test kit

This directory is a disposable fixture pack for a real NeoForge client/server
run. It is intentionally outside `examples/` and is not copied into a shipped
mod or the dev profile automatically.

## Requirements

Use Java 25, Minecraft 26.2, and NeoForge 26.2.0.86 or newer. Resourceful Lib
5.0 or newer is required. The release jar includes Olympus. Use the same Theseus
jar on the client and server.

## 1. Build and start a real packaged run

Build the release artifact and use the non-sources JAR in a NeoForge 26.2
instance (client and, for the dedicated-server pass below, server):

```sh
./gradlew clean build
```

The artifact is:

`build/libs/theseus-neoforge-26.2-1.0.0.jar`

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

## 8. Cleanup

Delete the generated oversized file and remove the imported smoke quests from
the editor. If you moved the dev config at the start, close Minecraft and
restore it with:

```sh
mv run/config/theseus.backup run/config/theseus
```

The fixture files can remain in the repository; they are not runtime config.
