package me.johardt.theseus.client.compat.recipe.rei;

import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.RecipeViewer;
import me.shedaniel.rei.api.client.favorites.FavoriteEntryType;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Optional REI bridge and Project Odyssey favorite entry. */
@SuppressWarnings("UnstableApiUsage")
public class TheseusReiClientPlugin implements REIClientPlugin {
    private final RecipeViewer.Adapter adapter = new Adapter();

    public TheseusReiClientPlugin() {
        RecipeViewer.register(adapter);
    }

    @Override
    public void registerFavorites(FavoriteEntryType.Registry registry) {
        registry.register(TheseusFavoriteEntry.ID, TheseusFavoriteEntry.Type.INSTANCE);
        registry.getOrCrateSection(Component.translatable("rei.sections.odyssey"))
            .add(new TheseusFavoriteEntry());
    }

    private static final class Adapter implements RecipeViewer.Adapter {
        @Override
        public String id() {
            return "rei";
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean show(ItemStack stack, RecipeViewer.Operation operation) {
            EntryStack<ItemStack> entry = EntryStack.of(VanillaEntryTypes.ITEM, stack);
            ViewSearchBuilder builder = ViewSearchBuilder.builder();
            if (operation == RecipeViewer.Operation.RECIPES) {
                builder.addRecipesFor(entry);
            } else {
                builder.addUsagesFor(entry);
            }
            return builder.open();
        }
    }
}
