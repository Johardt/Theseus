# Theseus

Theseus is a tree-style quest mod for Minecraft 26.1.2 on Fabric and NeoForge. It is an
independent fork of Heracles. It is not affiliated with or supported by the
original Heracles maintainers.

## Run from source

Install a Java 25 JDK and select it as the Minecraft instance's Java
executable. Check the terminal version with `java -version`; it must report
25. The Gradle build declares Java 25 as its toolchain, and Gradle can download
that toolchain if it is not installed.

```sh
java -version
./gradlew clean build
```

The build produces both release jars:

- NeoForge: `build/libs/theseus-neoforge-26.1.2-1.1.0.jar`
- Fabric: `fabric/build/libs/theseus-fabric-26.1.2-1.1.0.jar`

Install the jar for your loader along with Resourceful Lib. Fabric also requires
Fabric API. Olympus is bundled in both jars; Resourceful Config, OPAC, JEI, and
REI are optional integrations. The separate `-sources.jar` files are not game mods.

To start a development client, choose a loader:

```sh
./gradlew :runClient          # NeoForge
./gradlew :fabric:runClient   # Fabric
```

## Use in game

Press **H** or run `/theseus open` to open the quest screen. A six-quest demo
pack is copied to `config/theseus/quests` when that folder has no quest files.
Use the editor as an operator or a player with game-master permissions.

Useful commands:

```text
/theseus                         Show status
/theseus open                    Open the quest screen
/theseus demo                    Complete the demo task
/theseus dummy <value>           Complete a matching dummy task
/theseus claim <quest>           Claim a completed quest's rewards
/theseus submit <quest> <task>   Submit a manual task
/theseus reset                   Reset your progress
/theseus reload                  Reload quest JSON (game masters)
/theseus validate                Report quest issues (game masters)
```

The editor stores quest definitions on the server. Player progress is saved
with the world. The quest screen is built into Theseus; Hermes is not required.

## Documentation

- [Compatibility and intentional changes](docs/COMPATIBILITY.md)
- [Add-on extension guide](docs/EXTENSIONS.md)
- [Backup and recovery](docs/BACKUP-RECOVERY.md)
- [Known limitations](docs/LIMITATIONS.md)
- [OPAC party rewards](docs/PARTY-REWARDS.md)
- [Manual smoke test](smoke-test/README.md)

## Mod developers

The project does not configure a Maven publishing repository. Use the release
jar as a mod in a development instance for the corresponding loader. The public extension points
and a registration example are in the [add-on guide](docs/EXTENSIONS.md).

Odysseus is a separate Project Odyssey tool for converting FTB and HQM quest
packs to the original Heracles format. Conversion to Theseus may need manual
changes.

## Source layout

`common/src/main/java` contains the quest engine, payloads, UI, and integrations
shared by both loaders. Assets stay in `common/src/main/resources/assets`.
`neoforge/main` and `fabric/src/main` contain loader entrypoints, event hooks,
registration, and platform adapters. Both builds compile the shared sources
against their own Minecraft environment. Shared tests run with the NeoForge
test bootstrap through the root `test` task.

`./gradlew build` builds both loaders and runs the shared tests. Use
`./gradlew :fabric:build` for only the Fabric jar. `-Popac` and
`-PrecipeViewer=rei` (or `jei`) enable optional integrations in development runs.
