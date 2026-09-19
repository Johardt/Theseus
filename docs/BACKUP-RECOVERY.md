# Backup and recovery

Theseus does not make scheduled backups or remove old backups. Back up files
before changing a live pack, updating the mod, or running a bulk import.

## Files to back up

For quest content and chapter layout, back up the full folder:

```text
<instance>/config/theseus/
```

This includes quest JSON, `groups.txt`, and `group_settings.json`.

For player progress, back up:

```text
<world>/data/theseus_progress.json
```

`<world>` is the folder that contains that world's `level.dat`. Progress is
stored per world. Also back up the whole world with your normal Minecraft
backup process. Client preferences are stored in
`<instance>/config/theseus_options.jsonc`; these preferences do not contain
quest content or player progress.

## Make a backup

Stop the game or server before copying files. Replace the example paths with
the actual instance and world folders. Keep the backup in a dated folder on a
different disk or backup service.

```sh
cp -a /path/to/instance/config/theseus /path/to/backup/theseus-YYYY-MM-DD
cp -a /path/to/world/data/theseus_progress.json /path/to/backup/theseus_progress-YYYY-MM-DD.json
```

If a progress file does not exist yet, the world has no saved Theseus progress
to copy.

## Restore

1. Stop the game or server.
2. Copy the current `config/theseus` folder and progress file to a separate
   recovery folder. This preserves the current state in case the backup is
   wrong.
3. Restore `config/theseus` from the chosen backup.
4. Restore `theseus_progress.json` from the same backup date if progress also
   needs recovery.
5. Start the world and run `/theseus validate` as a game master.
6. Open the quest screen and check the restored quests and progress.

Restore quest files without restoring progress when only the content is wrong
and the current player progress is still valid. Restoring progress from a
different set of quest files can leave progress that does not match the quest
definitions.

If the quest folder is empty after recovery, Theseus may install the demo pack
on the next load. Restore the intended JSON files before starting the world.
