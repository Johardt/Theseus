# Third-party addon registration example

The executable example lives in
[`ExampleAddon.java`](../../neoforge/test/java/me/johardt/theseus/addonexample/ExampleAddon.java)
and the client-only
[`ExampleAddonClient.java`](../../neoforge/test/java/me/johardt/theseus/addonexample/ExampleAddonClient.java).
`ThirdPartyAddonContractTest` exercises one task, one reward, and one icon
through the public extension points.

Common mod initialization registers server behavior before any server starts:

```java
QuestRuntime.registerTaskHandler("exampleaddon:deliver_package", taskHandler);
QuestRuntime.registerRewardHandler("exampleaddon:coins", rewardHandler);
QuestIconTypes.register("exampleaddon:badge");
```

The task handler receives a parsed `QuestDefinition.Task` and a task signal.
The reward handler validates the raw reward source in `canClaim`, then grants
the configured reward through `QuestWorld` and returns a notification detail.
The source JSON remains addon-owned, so use a namespaced type and document its
fields with the addon.

Client initialization registers labels and editor metadata, then supplies an
icon default and renderer:

```java
EditorTypeRegistry.register(EditorTypeRegistry.Descriptor.editor(
    EditorTypeRegistry.Kind.REWARD, "exampleaddon:coins", "Example Coins", true, false));
QuestIconRegistry.register(new QuestIconRegistry.Descriptor(
    "exampleaddon:badge", "Example Badge", ExampleAddonClient::defaultIcon, ExampleAddonClient::renderBadge));
```

The runtime advertises registered task and reward handlers to clients. The
client can claim an addon reward only when its type is present in that server
advertisement. An editor descriptor does not install server behavior. Theseus
does not provide addon-specific structured forms; existing addon JSON is kept
intact and can be authored in quest files or by the addon itself.
