package me.johardt.theseus.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Minecraft server adapter for {@link QuestWorld}. */
public final class ServerQuestWorld implements QuestWorld {

    private final MinecraftServer server;
    private final Path configDirectory;

    public ServerQuestWorld(MinecraftServer server, Path configDirectory) {
        this.server = server;
        this.configDirectory = configDirectory;
    }

    @Override
    public QuestCatalog loadCatalog() {
        return QuestCatalog.load(configDirectory);
    }

    @Override
    public UUID playerId(ServerPlayer player) {
        return player.getUUID();
    }

    @Override
    public List<ServerPlayer> onlinePlayers() {
        return List.copyOf(server.getPlayerList().getPlayers());
    }

    @Override
    public boolean canEdit(ServerPlayer player) {
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    @Override
    public boolean isIntegratedServer() {
        return server.isSingleplayer();
    }

    @Override
    public boolean containsRegistryTarget(
        RegistryValidation.Target target,
        String value
    ) {
        try {
            boolean tag = value.startsWith("#");
            Identifier id = Identifier.parse(tag ? value.substring(1) : value);
            return switch (target) {
                case ITEM -> tag
                    ? registryTag(BuiltInRegistries.ITEM, Registries.ITEM, id)
                    : BuiltInRegistries.ITEM.containsKey(id);
                case BLOCK -> tag
                    ? registryTag(BuiltInRegistries.BLOCK, Registries.BLOCK, id)
                    : BuiltInRegistries.BLOCK.containsKey(id);
                case ENTITY -> tag
                    ? registryTag(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, id)
                    : BuiltInRegistries.ENTITY_TYPE.containsKey(id);
                case BIOME -> tag
                    ? registryTag(server.registryAccess().lookupOrThrow(Registries.BIOME), Registries.BIOME, id)
                    : server.registryAccess().lookupOrThrow(Registries.BIOME).containsKey(id);
                case STRUCTURE -> tag
                    ? registryTag(server.registryAccess().lookupOrThrow(Registries.STRUCTURE), Registries.STRUCTURE, id)
                    : server.registryAccess().lookupOrThrow(Registries.STRUCTURE).containsKey(id);
                case DIMENSION -> server.registryAccess().lookupOrThrow(Registries.DIMENSION).containsKey(id)
                    || server.levelKeys().stream().anyMatch(key -> key.identifier().equals(id));
                case STAT -> Stats.CUSTOM.getRegistry().containsKey(id);
                case ADVANCEMENT -> server.getAdvancements().get(id) != null;
                case RECIPE -> server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id)).isPresent();
                case LOOT_TABLE -> server.reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE)
                    .map(registry -> registry.listElementIds().anyMatch(key -> key.identifier().equals(id)))
                    .orElse(false);
            };
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public boolean advancementGranted(
        ServerPlayer player,
        String advancement
    ) {
        var holder = server.getAdvancements().get(Identifier.parse(advancement));
        return holder != null && player
            .getAdvancements()
            .getOrStartProgress(holder)
            .isDone();
    }

    @Override
    public boolean hasLootTable(String id) {
        ResourceKey<LootTable> key = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.parse(id)
        );
        return server.reloadableRegistries().getLootTable(key) != LootTable.EMPTY;
    }

    @Override
    public void message(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public void grantExperience(
        ServerPlayer player,
        int amount,
        boolean points
    ) {
        if (points) player.giveExperiencePoints(amount);
        else player.giveExperienceLevels(amount);
    }

    @Override
    public void giveItem(ServerPlayer player, ItemStack stack) {
        deliverItem(stack, player::addItem, remainder -> {
            var dropped = player.drop(remainder, false);
            if (dropped != null) {
                dropped.setNoPickUpDelay();
                dropped.setTarget(player.getUUID());
            }
        });
    }

    static void deliverItem(
        ItemStack reward,
        Predicate<ItemStack> addToInventory,
        Consumer<ItemStack> dropRemainder
    ) {
        ItemStack remainder = reward.copy();
        if (!addToInventory.test(remainder) && !remainder.isEmpty()) {
            dropRemainder.accept(remainder);
        }
    }

    @Override
    public void runCommand(ServerPlayer player, String command) {
        server.getCommands().performPrefixedCommand(
            player
                .createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(
                    net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS
                ),
            command
        );
    }

    @Override
    public void generateLoot(
        ServerPlayer player,
        String lootTable,
        Consumer<ItemStack> receiver
    ) {
        ResourceKey<LootTable> key = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.parse(lootTable)
        );
        LootParams params = new LootParams.Builder(player.level())
            .withParameter(LootContextParams.ORIGIN, player.position())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
            .create(LootContextParamSets.CHEST);
        server.reloadableRegistries().getLootTable(key).getRandomItems(
            params,
            receiver
        );
    }

    @Override
    public Set<TaskEngine.Signal.RegistryEntry> structuresAt(
        ServerPlayer player
    ) {
        var lookup = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        java.util.LinkedHashSet<TaskEngine.Signal.RegistryEntry> structures =
            new java.util.LinkedHashSet<>();
        lookup.listElements().forEach(holder -> {
            if (
                player.level().structureManager().getStructureWithPieceAt(
                    player.blockPosition(),
                    holder.value()
                ).isValid()
            ) {
                String id = holder.unwrapKey().orElseThrow().identifier().toString();
                structures.add(new TaskEngine.Signal.RegistryEntry(
                    id,
                    holder.tags().map(tag -> tag.location().toString()).collect(java.util.stream.Collectors.toSet()),
                    new com.google.gson.JsonObject(),
                    1
                ));
            }
        });
        return Set.copyOf(structures);
    }

    private static <T> boolean registryTag(
        Registry<T> registry,
        ResourceKey<? extends Registry<T>> key,
        Identifier id
    ) {
        return registry.getTagOrEmpty(TagKey.create(key, id)).iterator().hasNext();
    }
}
