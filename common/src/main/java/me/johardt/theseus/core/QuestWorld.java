package me.johardt.theseus.core;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** World and registry query port used by the quest runtime. */
public interface QuestWorld {

    QuestCatalog loadCatalog();

    UUID playerId(ServerPlayer player);

    List<ServerPlayer> onlinePlayers();

    boolean canEdit(ServerPlayer player);

    boolean isIntegratedServer();

    boolean containsRegistryTarget(
        RegistryValidation.Target target,
        String value
    );

    boolean advancementGranted(ServerPlayer player, String advancement);

    boolean hasLootTable(String id);

    void message(ServerPlayer player, String message);

    void grantExperience(ServerPlayer player, int amount, boolean points);

    void giveItem(ServerPlayer player, ItemStack stack);

    void runCommand(ServerPlayer player, String command);

    void generateLoot(
        ServerPlayer player,
        String lootTable,
        Consumer<ItemStack> receiver
    );

    Set<TaskEngine.Signal.RegistryEntry> structuresAt(ServerPlayer player);
}
