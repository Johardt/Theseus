package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorTypeRegistryTest {
    @Test
    void defaultsExposeBuiltInTypesAndExplicitUnknowns() {
        EditorTypeRegistry registry = EditorTypeRegistry.defaults();

        assertTrue(registry.find(EditorTypeRegistry.Kind.TASK, "theseus:composite").orElseThrow().allowsNested());
        assertTrue(registry.find(EditorTypeRegistry.Kind.ICON, "theseus:item").orElseThrow().editable());
        assertTrue(registry.resolve(EditorTypeRegistry.Kind.REWARD, "theseus:item").editable());
        assertFalse(registry.resolve(EditorTypeRegistry.Kind.TASK, "other:custom").editable());
        assertEquals("No editor is registered for this type", registry.resolve(EditorTypeRegistry.Kind.TASK, "other:custom").availabilityReason());
    }

    @Test
    void duplicateDescriptorsAreRejected() {
        EditorTypeRegistry.Builder builder = EditorTypeRegistry.builder();
        var descriptor = EditorTypeRegistry.Descriptor.builtIn(EditorTypeRegistry.Kind.TASK, "example:test", "Test");
        builder.register(descriptor);
        assertThrows(IllegalArgumentException.class, () -> builder.register(descriptor));
    }

    @Test
    void serverAdvertisementControlsEditorAvailability() {
        EditorTypeRegistry registry = EditorTypeRegistry.defaults();

        assertEquals(EditorTypeRegistry.Availability.EXECUTABLE_EDITABLE,
            registry.resolve(EditorTypeRegistry.Kind.TASK, "theseus:item", Set.of("theseus:item")).availability());
        assertEquals(EditorTypeRegistry.Availability.UNAVAILABLE_ON_SERVER,
            registry.resolve(EditorTypeRegistry.Kind.TASK, "theseus:item", Set.of()).availability());
        assertEquals(EditorTypeRegistry.Availability.EXECUTABLE_READ_ONLY,
            registry.resolve(EditorTypeRegistry.Kind.TASK, "addon:task", Set.of("addon:task")).availability());
        assertEquals(EditorTypeRegistry.Availability.UNKNOWN_CONFIGURATION,
            registry.resolve(EditorTypeRegistry.Kind.TASK, "addon:task", Set.of()).availability());
    }

    @Test
    void addonDescriptorCannotMakeTypeExecutableWithoutServer() {
        EditorTypeRegistry registry = EditorTypeRegistry.builder()
            .register(EditorTypeRegistry.Descriptor.editor(EditorTypeRegistry.Kind.TASK, "addon:task", "Addon task", true, false))
            .build();

        assertFalse(registry.resolve(EditorTypeRegistry.Kind.TASK, "addon:task", Set.of()).executable());
        assertTrue(registry.resolve(EditorTypeRegistry.Kind.TASK, "addon:task", Set.of("addon:task")).editable());
    }
}
