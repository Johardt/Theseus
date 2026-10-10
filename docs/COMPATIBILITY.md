# Compatibility and intentional changes

## Runtime compatibility

| Part | Supported target | Notes |
| --- | --- | --- |
| Minecraft | 26.1.2 | The mod metadata accepts this version only. |
| Loader | NeoForge 26.1.2.104 or newer | The release is built against 26.1.2.104. |
| Java | 25 | Use Java 25 for Gradle and the game. |
| Resourceful Lib | 4.0.1 or newer | Required on the client and server. The build uses 4.0.1. |
| Olympus | 1.8.4 or newer | Required on the client and included in the release jar. The build uses 1.8.4. |
| Resourceful Config | 4.0.1 or newer | Optional client config screen. |
| JEI | 29.0 or newer | Optional client integration. The build uses 29.43.0.104. |
| REI | 26.1 or newer | Optional client integration. The build uses 26.1.819. |
| Open Parties and Claims | 0.31.6 or newer for 26.1.2 | Optional server integration for its built-in parties; not required on Theseus clients. |

The release file is
`theseus-neoforge-26.1.2-1.1.0.jar`. Do not install the separate
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
- This release targets Minecraft 26.1.2 on NeoForge and uses Java 25.
- The quest screen is part of Theseus. Hermes is not required.
- Quest definitions live under `config/theseus/quests`. Player progress lives
  in the Minecraft world's `data/theseus_progress.json` file.
- Theseus installs its demo quest pack when the quest folder has no JSON files.
- The editor checks imports and saves against the server's registries and
  permissions.
- Saving changes to a quest's tasks or rewards can reset task progress, while
  preserving receipts, pins, and completion history. Reset tasks must be
  completed again before claiming rewards.
  Renaming moves the retained state to the new ID. Explicit operator reward
  resets and explicit whole-quest resets allow another payout; single-task resets do not.
- Progress files migrate from the legacy UUID-keyed root to a version-4
  `version`/`players` document. Back up before upgrading or downgrading.
- `individual_progress` follows Heracles: false shares tasks through OPAC;
  true keeps tasks personal. Without OPAC or a party, tasks are personal.
  Existing quests with this field omitted or false become shared with OPAC.
- [Shared quests](PARTY-REWARDS.md) retain player-owned progress across party
  changes. Prerequisites gate contributions and claims, while copied progress
  remains visible. The Reward audience setting has been removed.

Use the [backup and recovery guide](BACKUP-RECOVERY.md) before a migration or
bulk edit.
