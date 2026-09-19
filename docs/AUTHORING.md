# Author quests

## Open the editor

Press **H** or run `/theseus open`. The server accepts edit actions from game
masters, including operators. In single-player, the integrated server checks
the same permission level.

The editor has four graph tools:

- **Select** moves and edits quests.
- **Hand** pans the graph.
- **Add** creates a quest.
- **Link** connects a prerequisite quest to a dependent quest.

Use quest and chapter context menus for more actions. Press **Ctrl+S** to save
the current draft. The server checks the change and returns a result. A dirty
editor has changes that are not saved.

## Quest files

The server loads JSON files under:

```text
config/theseus/quests/
```

The folder is scanned recursively. The file name without `.json` is the quest
ID. IDs use lowercase letters, numbers, dots, underscores, and hyphens. Each
ID must be unique across the full folder tree. For example,
`getting_started/welcome.json` has ID `welcome`.

Each quest document has `display`, `tasks`, and `rewards` objects. `settings`
and `dependencies` are optional. This is a small valid example:

```json
{
  "display": {
    "title": "A New Lead",
    "icon": {
      "type": "theseus:item",
      "item": "minecraft:book"
    }
  },
  "tasks": {
    "start": {
      "type": "theseus:dummy",
      "value": "start"
    }
  },
  "rewards": {}
}
```

An empty reward object is valid. Empty task objects complete as soon as the
quest unlocks, so add a task when that is not intended. See the
[demo quest files](../examples/theseus-demo/config/theseus/quests/getting_started/)
for larger examples.

## Chapters and dependencies

Quest placement is stored in `display.groups`. Each group name is a chapter.
The position is a pair of numbers:

```json
"groups": {
  "Getting Started": { "position": [0, 0] }
}
```

The editor can store chapter order in `config/theseus/groups.txt` and chapter
icons and backgrounds in `config/theseus/group_settings.json`. The editor
updates these files when you change chapter settings.

Use **Link** to set a dependency. A quest with a dependency stays locked until
the required quest completes. Theseus rejects missing dependencies and
dependency cycles. When you rename a quest in the editor, Theseus updates
references to its ID.

## Built-in task and reward types

Built-in tasks include item, experience, entity kill, advancement, biome,
block interaction, dimension change, check, composite, entity interaction,
item interaction, item use, location, recipe, statistic, structure, and dummy
tasks. Item tasks support automatic, consuming, and manual collection modes.
Experience tasks support automatic and manual collection.

Built-in rewards include items, experience, loot tables, commands, and
selectable rewards. A player can claim rewards after the quest completes.

Registry values must exist on the server. Item, block, entity, biome,
dimension, structure, statistic, advancement, recipe, and loot table IDs are
checked during import or save. Item and block tags use a leading `#`.

## Preserve data and check changes

The editor preserves JSON fields it does not edit. Unknown task and reward
types remain in the document, but the built-in editor treats them as read-only.
An add-on must register a runtime handler for those types. See the
[extension guide](EXTENSIONS.md).

Task or reward changes can reset the affected quest's player progress. Back up
quest files and world progress before changing a live pack. See
[backup and recovery](BACKUP-RECOVERY.md).

After editing JSON by hand, run `/theseus validate` as a game master. Run
`/theseus reload` to load the changed files without restarting the world.
