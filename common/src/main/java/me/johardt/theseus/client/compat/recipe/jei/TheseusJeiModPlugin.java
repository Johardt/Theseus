package me.johardt.theseus.client.compat.recipe.jei;

import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.RecipeViewer;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Optional JEI bridge. JEI owns discovery and runtime lifecycle for this class. */
@JeiPlugin
public final class TheseusJeiModPlugin implements IModPlugin {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "jei");
    private final Adapter adapter = new Adapter();

    public TheseusJeiModPlugin() {
        RecipeViewer.register(adapter);
    }

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        adapter.runtime = runtime;
    }

    @Override
    public void onRuntimeUnavailable() {
        adapter.runtime = null;
    }

    private static final class Adapter implements RecipeViewer.Adapter {
        private IJeiRuntime runtime;

        @Override
        public String id() {
            return "jei";
        }

        @Override
        public boolean isReady() {
            return runtime != null;
        }

        @Override
        public boolean show(ItemStack stack, RecipeViewer.Operation operation) {
            if (runtime == null) return false;
            RecipeIngredientRole role = operation == RecipeViewer.Operation.RECIPES
                ? RecipeIngredientRole.OUTPUT
                : RecipeIngredientRole.INPUT;
            IFocus<ItemStack> focus = runtime.getJeiHelpers()
                .getFocusFactory()
                .createFocus(role, VanillaTypes.ITEM_STACK, stack);
            runtime.getRecipesGui().show(focus);
            return true;
        }
    }
}
