# Known limitations

- Theseus 1.0.0 targets Minecraft 26.2 on NeoForge. There is no Fabric or Forge
  build.
- The editor imports JSON quest files. It does not import quest-pack archives
  or convert FTB/HQM packs.
- There is no separate JSON export command. Copy quest files from the server
  config folder. The editor's **Open file** action works only in single-player.
- The editor clipboard holds one quest in memory. It is separate from the
  operating system clipboard and is not saved to disk.
- The built-in editor has forms for built-in types only. Add-on types remain
  read-only unless an add-on provides more authoring support.
- A task type without a registered server handler does not gain progress. A
  reward type without a registered handler cannot be claimed.
- Quest import is limited to 1 MiB per file. Composite tasks are limited to 32
  nested levels.
- Quest data is shared by the instance or server config folder. Player
  progress is stored in the world save.
- Theseus does not schedule or retain backups. Use the
  [backup and recovery guide](BACKUP-RECOVERY.md).
- Full compatibility with every Heracles release, add-on, and converter is not
  guaranteed. Validate imported quests and test them on a copy of the world.
