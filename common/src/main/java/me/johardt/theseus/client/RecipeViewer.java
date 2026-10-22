package me.johardt.theseus.client;

import me.johardt.theseus.Theseus;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Small client-only seam for optional recipe viewers.
 *
 * <p>Viewer-specific classes register adapters when their own runtime is ready.
 * The quest UI only knows this interface, so an ordinary Theseus client does
 * not link against any optional viewer API.</p>
 */
public final class RecipeViewer {
    private static final List<Adapter> ADAPTERS = new ArrayList<>();
    private static final Comparator<Adapter> PRECEDENCE = Comparator
        .comparingInt((Adapter adapter) -> switch (adapter.id()) {
            case "rei" -> 0;
            case "jei" -> 1;
            case "emi" -> 2;
            default -> 100;
        })
        .thenComparing(Adapter::id);

    private RecipeViewer() {}

    public static synchronized Registration register(Adapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        if (adapter.id() == null || adapter.id().isBlank()) {
            throw new IllegalArgumentException("Recipe viewer adapter id is required");
        }
        ADAPTERS.removeIf(existing -> existing.id().equals(adapter.id()));
        ADAPTERS.add(adapter);
        return () -> unregister(adapter);
    }

    public static synchronized boolean isAvailable() {
        return selected() != null;
    }

    public static synchronized String activeViewerId() {
        Adapter adapter = selected();
        return adapter == null ? null : adapter.id();
    }

    public static boolean showRecipes(ItemStack stack) {
        return dispatch(stack, Operation.RECIPES);
    }

    public static boolean showUses(ItemStack stack) {
        return dispatch(stack, Operation.USES);
    }

    private static boolean dispatch(ItemStack stack, Operation operation) {
        if (stack == null || stack.isEmpty()) return false;
        Adapter adapter;
        synchronized (RecipeViewer.class) {
            adapter = selected();
        }
        if (adapter == null) return false;
        try {
            return adapter.show(stack.copy(), operation);
        } catch (RuntimeException | LinkageError exception) {
            Theseus.LOGGER.error(
                "Recipe viewer '{}' failed to open {} for {}",
                adapter.id(),
                operation.name().toLowerCase(java.util.Locale.ROOT),
                stack,
                exception
            );
            return false;
        }
    }

    private static synchronized Adapter selected() {
        return ADAPTERS.stream()
            .filter(adapter -> {
                try {
                    return adapter.isReady();
                } catch (RuntimeException | LinkageError exception) {
                    return false;
                }
            })
            .sorted(PRECEDENCE)
            .findFirst()
            .orElse(null);
    }

    private static synchronized void unregister(Adapter adapter) {
        ADAPTERS.remove(adapter);
    }

    static synchronized void resetForTests() {
        ADAPTERS.clear();
    }

    public enum Operation {
        RECIPES,
        USES
    }

    public interface Adapter {
        String id();

        boolean isReady();

        boolean show(ItemStack stack, Operation operation);
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
