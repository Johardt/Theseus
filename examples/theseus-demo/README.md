# Theseus demo quests

These files use the original Heracles 1.21 quest format and are migration
fixtures for the Theseus 26.1.2 port. Copy the contents of `config/` into the development run's
`run/config/` directory once the quest loader has been re-enabled.

The demo also includes a two-quest notification showcase:

1. `welcome` exercises a dummy task through `/theseus dummy demo_welcome`.
2. `gather_logs` depends on `welcome`, tracks eight oak logs, and rewards bread.
3. `craft_table` also depends on `welcome`, creating a second tree branch.
4. `combat` counts zombie kills after `gather_logs`.
5. `nether_trip` detects Overworld-to-Nether travel after `craft_table`.
6. `compatibility` exercises item tags, recipes, statistics, and structures.
7. `notification_unlock` unlocks after `welcome` and opts into the native unlock toast.
8. `notification_complete` demonstrates unlock, completion, and item/experience reward toasts.

They intentionally cover file loading, groups, dependencies, server-to-client
synchronization, commands, automatic task progress, registry predicates, and
reward claiming.
