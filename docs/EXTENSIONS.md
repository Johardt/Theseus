# Add-on extension guide

Theseus add-ons are NeoForge mods. Register runtime handlers during common mod
initialization, before a server starts. Use namespaced IDs owned by the add-on.

## Register task and reward handlers

```java
QuestRuntime.registerTaskHandler("exampleaddon:deliver_package", taskHandler);
QuestRuntime.registerRewardHandler("exampleaddon:coins", rewardHandler);
QuestIconTypes.register("exampleaddon:badge");
```

Theseus locks registration when a server starts. Registration after that point
throws an exception. Task handlers receive a parsed
`QuestDefinition.Task`, the current progress, and a task signal. Return the new
progress and the amount to consume when the task uses inventory or experience.

Reward handlers implement `canClaim` and `grant`. `canClaim` checks whether the
player can claim the reward. `grant` performs the reward and returns an
optional notification string. Keep validation and execution rules in the
add-on. Theseus does not define fields for add-on-owned JSON.

The task and reward interfaces are in
[`TaskEngine.java`](../neoforge/main/java/me/johardt/theseus/core/TaskEngine.java)
and [`RewardEngine.java`](../neoforge/main/java/me/johardt/theseus/core/RewardEngine.java).

## Register editor metadata

Register client-side labels and icon rendering during client initialization:

```java
EditorTypeRegistry.register(EditorTypeRegistry.Descriptor.editor(
    EditorTypeRegistry.Kind.TASK,
    "exampleaddon:deliver_package",
    "Deliver Package",
    true,
    false
));
EditorTypeRegistry.register(EditorTypeRegistry.Descriptor.editor(
    EditorTypeRegistry.Kind.REWARD,
    "exampleaddon:coins",
    "Example Coins",
    true,
    false
));
EditorTypeRegistry.register(EditorTypeRegistry.Descriptor.editor(
    EditorTypeRegistry.Kind.ICON,
    "exampleaddon:badge",
    "Example Badge",
    true,
    false
));
QuestIconRegistry.register(new QuestIconRegistry.Descriptor(
    "exampleaddon:badge",
    "Example Badge",
    ExampleAddonClient::defaultIcon,
    ExampleAddonClient::renderBadge
));
```

An editor descriptor only describes a type. It does not register server
behavior. The built-in editor has no structured form for add-on fields. Unknown
JSON stays read-only in the editor and can be authored in a file or by the
add-on.

The runtime advertises task and reward handlers to clients. All clients that
need to display or use an add-on feature must have compatible add-on code.
Reward claims require the server to have the reward handler.

## Example

The repository contains a registration example and contract test under
[`neoforge/test/java/me/johardt/theseus/addonexample`](../neoforge/test/java/me/johardt/theseus/addonexample/).
See also the [example README](../examples/third-party-addon/README.md).
