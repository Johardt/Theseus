# Import, clipboard, and export

## Import JSON files

1. Open the editor with **H** or `/theseus open`.
2. Choose **Import** and select one or more `.json` quest files.
3. Review each file's proposed ID and diagnostics. Change the ID if needed.
4. Choose **Import** after all blocking errors are fixed.

The proposed quest ID comes from the file name without `.json`. IDs must use
lowercase letters, numbers, dots, underscores, and hyphens. Each quest file is
limited to 1 MiB. Import checks JSON syntax, duplicate keys, quest structure,
IDs, dependencies, and registry values on the server.

Import is atomic. If any selected file is invalid or any target ID already
exists, Theseus writes none of the files. A warning does not block import, but
it may mean that an add-on is required to run or edit a type.

The file picker reads files on the client and sends the selected documents to
the server. The server writes them to `config/theseus/quests`. Back up that
folder before importing into a live world or server.

## Use the quest clipboard

The editor's quest clipboard is separate from the operating system clipboard.
It holds one quest in memory and is not saved to disk.

| Shortcut | Action |
| --- | --- |
| **Ctrl+C** | Copy the selected quest into the editor clipboard. |
| **Ctrl+X** | Cut the selected quest. Paste to finish the move. |
| **Ctrl+V** | Paste a copy. Enter a new quest ID when asked. |
| **Ctrl+Shift+V** | Add the copied quest to the current chapter. |

Right-click a quest to find the same actions. A copied quest keeps its raw JSON
fields. Theseus rejects a duplicate ID. A cut quest remains in place until the
move succeeds. **Copy quest ID** copies only the ID to the system clipboard.

## Export a quest

The editor has no separate JSON export command. Quest files are plain JSON.
Use one of these methods:

- In a single-player world, open a quest context menu and choose **Open file**.
  This opens the quest's JSON file from the local instance folder. Copy the
  file to your pack or export folder.
- On a dedicated server, copy the JSON file from the server's
  `config/theseus/quests` folder.
- To export multiple quests, copy the selected JSON files and keep their file
  names. The file name supplies each quest ID when you import the files again.

The local **Open file** action is not available for a dedicated server. Stop
and back up the world before replacing quest files by hand. See
[backup and recovery](BACKUP-RECOVERY.md).
