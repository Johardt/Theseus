# Compatibility and intentional changes

## Runtime compatibility

| Part | Supported target | Notes |
| --- | --- | --- |
| Minecraft | 26.2 | The mod metadata accepts this version only. |
| Loader | NeoForge 26.2.0.86 or newer | The release is built against 26.2.0.86. |
| Java | 25 | Use Java 25 for Gradle and the game. |
| Resourceful Lib | 5.0.0 or newer | Required on the client and server. The build uses 5.0.4. |
| Olympus | 1.9.4 or newer | Required on the client and included in the release jar. The build uses 1.9.4. |
| Resourceful Config | 5.0.0 or newer | Optional client config screen. |
| JEI | 30.0 or newer | Optional client integration. The build uses 30.32.0.221. |
| REI | 26.2 or newer | Optional client integration. The build uses 26.2.821. |

The release file is
`theseus-neoforge-26.2-1.0.0.jar`. Do not install the separate
`-sources.jar` file as a mod. Theseus has no Fabric or Forge build.

## Quest data compatibility

Theseus reads JSON quest files in the format used by the bundled demo. It also
reads selected camelCase and snake_case setting names. The editor preserves
unknown JSON fields when it saves a document.

Theseus does not promise complete compatibility with every Heracles release,
third-party task type, reward type, icon type, or quest-pack converter. Check
each imported pack with `/theseus validate` and test it on a copy of the world.
Unknown runtime task or reward types need a Theseus add-on handler.

## Intentional changes in this fork

- The mod ID and artifact name use `theseus`.
- This release targets Minecraft 26.2 on NeoForge and uses Java 25.
- The quest screen is part of Theseus. Hermes is not required.
- Quest definitions live under `config/theseus/quests`. Player progress lives
  in the Minecraft world's `data/theseus_progress.json` file.
- Theseus installs its demo quest pack when the quest folder has no JSON files.
- The editor checks imports and saves against the server's registries and
  permissions.
- Saving changes to a quest's tasks or rewards can reset that quest's player
  progress. Renaming a quest without changing its tasks or rewards moves its
  progress to the new ID. Treat a quest definition as part of the live pack
  data.

Use the [backup and recovery guide](BACKUP-RECOVERY.md) before a migration or
bulk edit.
