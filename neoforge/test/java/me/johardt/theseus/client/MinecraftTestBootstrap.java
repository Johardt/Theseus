package me.johardt.theseus.client;

import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class MinecraftTestBootstrap {
    private static boolean bootstrapped;

    private MinecraftTestBootstrap() {}

    public static synchronized void ensureBootstrapped() {
        if (bootstrapped) return;
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeType.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            Object unsafe = singleton.get(null);
            Method allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
            Method objectFieldOffset = unsafeType.getMethod("objectFieldOffset", Field.class);
            Method putObject = unsafeType.getMethod("putObject", Object.class, long.class, Object.class);

            FMLLoader loader = (FMLLoader) allocateInstance.invoke(unsafe, FMLLoader.class);
            Field loadingModList = FMLLoader.class.getDeclaredField("loadingModList");
            putObject.invoke(
                unsafe,
                loader,
                objectFieldOffset.invoke(unsafe, loadingModList),
                LoadingModList.of(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of())
            );
            Field current = FMLLoader.class.getDeclaredField("current");
            current.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicReference<FMLLoader> reference = (AtomicReference<FMLLoader>) current.get(null);
            reference.set(loader);

            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            BuiltInRegistries.ITEM.listElements().forEach(holder -> holder.bindComponents(DataComponentMap.EMPTY));
            bootstrapped = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not bootstrap Minecraft test registries", exception);
        }
    }
}
