# Diagnostics reference

Diagnostics appear in the editor's import and save dialogs. Each entry gives a
severity, quest ID, JSON path, message, and sometimes a suggested fix.

| Severity | Meaning |
| --- | --- |
| Error | The import or save is blocked until the issue is fixed. |
| Warning | The document can be saved or imported, but a feature may be missing or behave unexpectedly. |
| Info | Extra context. It does not block a change. |

The path points to the field in the JSON document. `$` means the whole file.
For example, `tasks.mine.item` points to the `item` value of the `mine` task.

## Common errors

| Code or message | Cause | Fix |
| --- | --- | --- |
| `malformed_json` | JSON syntax is invalid. | Fix the syntax, then import again. |
| `duplicate_json_key` | An object repeats a key. | Remove the repeated key. |
| `invalid_filename` | The file name is not a lowercase `.json` quest ID. | Rename the file. |
| `file_too_large` | The file is larger than 1 MiB. | Reduce the file size. |
| `duplicate_import_id` or `duplicate_catalog_id` | A quest with that ID already exists or appears twice in the batch. | Choose a unique ID. |
| `missing_title`, `invalid_structure`, or `invalid_*` | A required field has the wrong value or shape. | Use the path to find and correct the field. |
| `unknown_item`, `unknown_biome`, `unknown_recipe`, or another `unknown_*` registry code | The server does not have the identifier or tag. | Use a value registered on the server. |
| Missing quest or dependency cycle | A dependency names no quest or creates a cycle. | Add the missing quest or remove an edge in the cycle. |
| `nesting_too_deep` | Composite tasks are nested more than 32 levels. | Flatten the task structure. |

## Common warnings

- `empty_tasks`: the quest completes as soon as it unlocks.
- `empty_rewards`: the quest has no rewards. This is valid for progression-only
  quests.
- `unknown_task_type` or `unknown_reward_type`: the editor has no built-in
  support for that type. An add-on may provide runtime support.
- `unknown_icon_type`: the client has no renderer for the icon type.

Some codes include the field path in the code, such as `invalid_tasks_amount`.
Read the message and path as well as the code.

## Validate a loaded pack

Run these commands as a game master:

```text
/theseus validate
/theseus reload
```

`validate` reports issues in the loaded catalog. `reload` reads files again
and reports the quest and issue counts. Check the server log for file paths
and details if the screen only reports that an import failed. A failed batch
does not leave a partial set of imported files.
