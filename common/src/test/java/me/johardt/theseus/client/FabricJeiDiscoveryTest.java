package me.johardt.theseus.client;

import com.google.gson.JsonParser;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class FabricJeiDiscoveryTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @AfterEach
    void resetRegistry() {
        RecipeViewer.resetForTests();
    }

    @Test
    void fabricDiscoveredPluginEnablesTooltipAndBothClickActions() throws Exception {
        RecipeViewer.resetForTests();
        var metadata = JsonParser.parseString(Files.readString(Path.of("fabric/src/main/resources/fabric.mod.json"))).getAsJsonObject();
        var entries = metadata.getAsJsonObject("entrypoints").getAsJsonArray("jei_mod_plugin");
        List<IModPlugin> plugins = new ArrayList<>();
        if (entries != null) {
            for (var entry : entries) {
                plugins.add((IModPlugin) Class.forName(entry.getAsString()).getConstructor().newInstance());
            }
        }

        List<RecipeIngredientRole> roles = new ArrayList<>();
        List<IFocus<?>> shown = new ArrayList<>();
        IFocusFactory focuses = stub(IFocusFactory.class, (method, args) -> {
            assertEquals("createFocus", method);
            roles.add((RecipeIngredientRole) args[0]);
            assertEquals(Items.DIAMOND, ((ItemStack) args[2]).getItem());
            return stub(IFocus.class, (name, values) -> null);
        });
        IJeiHelpers helpers = stub(IJeiHelpers.class, (method, args) -> {
            assertEquals("getFocusFactory", method);
            return focuses;
        });
        IRecipesGui gui = stub(IRecipesGui.class, (method, args) -> {
            assertEquals("show", method);
            shown.add((IFocus<?>) args[0]);
            return null;
        });
        IJeiRuntime runtime = stub(IJeiRuntime.class, (method, args) -> switch (method) {
            case "getJeiHelpers" -> helpers;
            case "getRecipesGui" -> gui;
            default -> throw new AssertionError("Unexpected runtime call: " + method);
        });
        assertFalse(RecipeViewer.isAvailable());
        plugins.forEach(plugin -> plugin.onRuntimeAvailable(runtime));

        assertTrue(RecipeViewer.isAvailable(), "The tooltip requires a ready adapter discovered by Fabric JEI");
        assertTrue(RecipeViewer.showRecipes(new ItemStack(Items.DIAMOND)));
        assertTrue(RecipeViewer.showUses(new ItemStack(Items.DIAMOND)));
        assertEquals(List.of(RecipeIngredientRole.OUTPUT, RecipeIngredientRole.INPUT), roles);
        assertEquals(2, shown.size());
        plugins.forEach(IModPlugin::onRuntimeUnavailable);
        assertFalse(RecipeViewer.isAvailable());
    }

    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> handler.apply(method.getName(), args)));
    }
}
