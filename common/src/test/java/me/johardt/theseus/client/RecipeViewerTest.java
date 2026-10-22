package me.johardt.theseus.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeViewerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        RecipeViewer.resetForTests();
    }

    @Test
    void noAdapterOrInvalidStackIsUnavailableAndDoesNotDispatch() {
        assertFalse(RecipeViewer.isAvailable());
        assertNull(RecipeViewer.activeViewerId());
        assertFalse(RecipeViewer.showRecipes(null));
        assertFalse(RecipeViewer.showUses(new ItemStack(Items.AIR)));

        AtomicInteger calls = new AtomicInteger();
        RecipeViewer.register(adapter("jei", true, (stack, operation) -> {
            calls.incrementAndGet();
            return true;
        }));

        assertFalse(RecipeViewer.showRecipes(null));
        assertFalse(RecipeViewer.showUses(new ItemStack(Items.AIR)));
        assertEquals(0, calls.get());
    }

    @Test
    void unreadyAdaptersAreIgnored() {
        RecipeViewer.register(adapter("jei", false, (stack, operation) -> true));

        assertFalse(RecipeViewer.isAvailable());
        assertNull(RecipeViewer.activeViewerId());
        assertFalse(RecipeViewer.showRecipes(new ItemStack(Items.DIAMOND)));
    }

    @Test
    void readyAdapterReceivesOperationAndDefensiveStackCopy() {
        AtomicReference<ItemStack> received = new AtomicReference<>();
        AtomicReference<RecipeViewer.Operation> operation = new AtomicReference<>();
        RecipeViewer.register(adapter("jei", true, (stack, value) -> {
            received.set(stack);
            operation.set(value);
            stack.setCount(1);
            return true;
        }));

        ItemStack original = new ItemStack(Items.DIAMOND, 3);
        assertTrue(RecipeViewer.showRecipes(original));
        assertNotSame(original, received.get());
        assertEquals(3, original.getCount());
        assertEquals(RecipeViewer.Operation.RECIPES, operation.get());

        assertTrue(RecipeViewer.showUses(original));
        assertEquals(RecipeViewer.Operation.USES, operation.get());
    }

    @Test
    void duplicateIdsReplacePredictablyAndRegistrationsCloseIndependently() {
        List<String> calls = new ArrayList<>();
        RecipeViewer.Registration first = RecipeViewer.register(adapter("jei", true, (stack, operation) -> {
            calls.add("first");
            return true;
        }));
        RecipeViewer.Registration replacement = RecipeViewer.register(adapter("jei", true, (stack, operation) -> {
            calls.add("replacement");
            return true;
        }));

        first.close();
        assertEquals("jei", RecipeViewer.activeViewerId());
        assertTrue(RecipeViewer.showRecipes(new ItemStack(Items.DIAMOND)));
        assertEquals(List.of("replacement"), calls);

        replacement.close();
        assertFalse(RecipeViewer.isAvailable());
        replacement.close();
    }

    @Test
    void precedenceIsStableAcrossKnownAndUnknownReadyAdapters() {
        RecipeViewer.register(adapter("unknown", true, (stack, operation) -> true));
        RecipeViewer.register(adapter("jei", true, (stack, operation) -> true));
        RecipeViewer.register(adapter("rei", true, (stack, operation) -> true));

        assertEquals("rei", RecipeViewer.activeViewerId());
    }

    @Test
    void failedDispatchDoesNotCascadeToAnotherAdapter() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        RecipeViewer.register(adapter("rei", true, (stack, operation) -> false));
        RecipeViewer.register(adapter("jei", true, (stack, operation) -> {
            fallbackCalls.incrementAndGet();
            return true;
        }));

        assertFalse(RecipeViewer.showUses(new ItemStack(Items.DIAMOND)));
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void readinessAndDispatchFailuresAreContained() {
        RecipeViewer.register(adapter("broken-ready", () -> {
            throw new IllegalStateException("not ready");
        }, (stack, operation) -> true));
        RecipeViewer.register(adapter("broken-linkage", () -> {
            throw new LinkageError("missing optional runtime");
        }, (stack, operation) -> true));
        RecipeViewer.register(adapter("rei", true, (stack, operation) -> {
            throw new LinkageError("viewer failure");
        }));

        assertTrue(RecipeViewer.isAvailable());
        assertEquals("rei", RecipeViewer.activeViewerId());
        assertFalse(RecipeViewer.showRecipes(new ItemStack(Items.DIAMOND)));
    }

    private static RecipeViewer.Adapter adapter(
        String id,
        boolean ready,
        AdapterAction action
    ) {
        return adapter(id, () -> ready, action);
    }

    private static RecipeViewer.Adapter adapter(
        String id,
        Readiness readiness,
        AdapterAction action
    ) {
        return new RecipeViewer.Adapter() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean isReady() {
                return readiness.ready();
            }

            @Override
            public boolean show(ItemStack stack, RecipeViewer.Operation operation) {
                return action.show(stack, operation);
            }
        };
    }

    @FunctionalInterface
    private interface Readiness {
        boolean ready();
    }

    @FunctionalInterface
    private interface AdapterAction {
        boolean show(ItemStack stack, RecipeViewer.Operation operation);
    }
}
