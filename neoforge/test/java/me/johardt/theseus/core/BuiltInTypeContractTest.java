package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.johardt.theseus.client.QuestIconRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInTypeContractTest {
    @Test
    void everyBuiltInTaskHasAnEditorDescriptorAndAnExecutionPath() throws Exception {
        Set<String> editorTypes = ids(EditorTypeRegistry.defaults(), EditorTypeRegistry.Kind.TASK);
        Set<String> executable = new LinkedHashSet<>(TaskEngine.defaults().types());
        executable.add("theseus:composite"); // QuestRuntime evaluates composite children recursively.

        JsonObject fixture = currentFixture();
        QuestDefinition definition = QuestDefinition.parse("current_builtins", fixture);
        Set<String> fixtureTypes = taskTypes(definition.tasks());

        assertEquals(editorTypes, executable);
        assertEquals(editorTypes, fixtureTypes);
        assertTrue(definition.issues().isEmpty(), () -> definition.issues().toString());
    }

    @Test
    void everyBuiltInRewardHasAnEditorDescriptorRuntimeModelAndFixture() throws Exception {
        Set<String> expected = Set.of(
            "theseus:xp",
            "theseus:item",
            "theseus:loottable",
            "theseus:command",
            "theseus:selectable"
        );
        Set<String> editorTypes = ids(EditorTypeRegistry.defaults(), EditorTypeRegistry.Kind.REWARD);
        QuestDefinition definition = QuestDefinition.parse("current_builtins", currentFixture());
        Set<String> fixtureTypes = rewardTypes(definition.rewards());

        assertEquals(expected, editorTypes);
        assertEquals(expected, fixtureTypes);
        assertTrue(definition.issues().isEmpty(), () -> definition.issues().toString());
    }

    @Test
    void builtInIconTypesMatchTheEditorServerAndClientRendererRegistries() {
        Set<String> editorTypes = ids(EditorTypeRegistry.defaults(), EditorTypeRegistry.Kind.ICON);

        assertEquals(Set.of(QuestIconDefinition.ITEM_TYPE), editorTypes);
        assertEquals(editorTypes, namespace(QuestIconTypes.types(), "theseus:"));
        assertEquals(editorTypes, namespace(QuestIconRegistry.descriptors().keySet(), "theseus:"));
    }

    private static Set<String> ids(EditorTypeRegistry registry, EditorTypeRegistry.Kind kind) {
        return registry.descriptors(kind).stream().map(EditorTypeRegistry.Descriptor::id).collect(Collectors.toSet());
    }

    private static Set<String> namespace(Set<String> types, String namespace) {
        return types.stream().filter(type -> type.startsWith(namespace)).collect(Collectors.toSet());
    }

    private static Set<String> taskTypes(java.util.Map<String, QuestDefinition.Task> tasks) {
        Set<String> types = new LinkedHashSet<>();
        tasks.values().forEach(task -> {
            types.add(task.type());
            types.addAll(taskTypes(task.tasks()));
        });
        return types;
    }

    private static Set<String> rewardTypes(java.util.Map<String, QuestDefinition.Reward> rewards) {
        Set<String> types = new LinkedHashSet<>();
        rewards.values().forEach(reward -> {
            types.add(reward.type());
            types.addAll(rewardTypes(reward.rewards()));
        });
        return types;
    }

    private static JsonObject currentFixture() throws Exception {
        try (InputStream stream = BuiltInTypeContractTest.class.getResourceAsStream("/fixtures/compatibility/current_builtins.json")) {
            assertTrue(stream != null, "Missing current built-in fixture");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
