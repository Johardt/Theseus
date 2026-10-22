package me.johardt.theseus.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Keeps the Resourceful Config integration optional at runtime.
 *
 * <p>The integration class itself references Resourceful Config's API, so it
 * is loaded only after the optional mod has been detected. The rest of the
 * client can therefore run without that dependency installed.</p>
 */
public final class ResourcefulConfigBridge {
    private static final String MOD_ID = "resourcefulconfig";
    private static final String INTEGRATION_CLASS =
        "me.johardt.theseus.client.resourceful.ResourcefulClientOptions";
    private static final Logger LOGGER = Logger.getLogger(ResourcefulConfigBridge.class.getName());

    private static Method saveMethod;

    private ResourcefulConfigBridge() {}

    public static void registerIfAvailable() {
        if (!isAvailable()) return;

        try {
            Class<?> integration = Class.forName(INTEGRATION_CLASS);
            integration.getMethod("register").invoke(null);
            saveMethod = integration.getMethod(
                "saveFromPreferences",
                TheseusClientOptions.Preferences.class
            );
            LOGGER.info("Resourceful Config support enabled for Theseus client options");
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warning("Resourceful Config was detected but could not be integrated: " + exception);
        }
    }

    public static boolean save(TheseusClientOptions.Preferences preferences) {
        if (saveMethod == null) return false;
        try {
            saveMethod.invoke(null, preferences);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
            LOGGER.warning("Could not synchronize Theseus options with Resourceful Config: " + exception);
            saveMethod = null;
            return false;
        }
    }

    public static boolean isAvailable() {
        try {
            return me.johardt.theseus.platform.Platform.isModLoaded(MOD_ID);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.fine("Resourceful Config is not available during client initialization: " + exception);
            return false;
        }
    }
}
