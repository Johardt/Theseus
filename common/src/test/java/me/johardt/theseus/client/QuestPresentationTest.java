package me.johardt.theseus.client;

import com.google.gson.JsonParser;
import me.johardt.theseus.core.QuestDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestPresentationTest {
    @Test
    void partyRewardReceiptDoesNotColorIncompletePersonalProgressAsCompleted() {
        assertEquals(0xFF4C9AFF, QuestPresentation.nodeStateColor(true, true, false));
        assertEquals(0xFF737B87, QuestPresentation.nodeStateColor(false, true, false));
        assertEquals(0xFF55D86A, QuestPresentation.nodeStateColor(true, true, true));
        assertEquals(Component.translatable("quest.theseus.in_progress").getString(), QuestPresentation.status(true, true, false).getString());
    }
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void itemQuestIconsTasksAndRewardsExposeTheDisplayedStack() {
        QuestDefinition quest = parse("""
            {
              "display": {
                "icon": {"type":"theseus:item","item":"minecraft:diamond","count":3,"components":{"minecraft:damage":2}}
              },
              "tasks": {
                "collect": {"type":"theseus:item","item":{"id":"minecraft:diamond","count":3}},
                "use": {"type":"theseus:item_use","item":"minecraft:apple"},
                "interact": {"type":"theseus:item_interaction","item":"minecraft:iron_ingot"},
                "override": {"type":"theseus:check","icon":{"type":"theseus:item","item":"minecraft:gold_ingot","count":2}}
              },
              "rewards": {
                "item": {"type":"theseus:item","item":{"id":"minecraft:emerald","count":4}},
                "choice": {
                  "type":"theseus:selectable",
                  "amount":1,
                  "rewards": {
                    "diamond": {"type":"theseus:item","item":{"id":"minecraft:diamond","count":2}},
                    "xp": {"type":"theseus:xp","amount":3}
                  }
                }
              }
            }
            """);

        Optional<ItemStack> questIcon = QuestPresentation.questIconTarget(quest);
        assertStack(questIcon, Items.DIAMOND, 3);
        assertEquals(2, questIcon.orElseThrow().get(DataComponents.DAMAGE));
        ItemStack renderedQuestIcon = QuestPresentation.questIcon(quest);
        assertEquals(Items.DIAMOND, renderedQuestIcon.getItem());
        assertEquals(3, renderedQuestIcon.getCount());
        assertEquals(2, renderedQuestIcon.get(DataComponents.DAMAGE));
        assertStack(QuestPresentation.taskIconTarget(quest.tasks().get("collect")), Items.DIAMOND, 3);
        assertStack(QuestPresentation.taskIconTarget(quest.tasks().get("use")), Items.APPLE, 1);
        assertStack(QuestPresentation.taskIconTarget(quest.tasks().get("interact")), Items.IRON_INGOT, 1);
        assertStack(QuestPresentation.taskIconTarget(quest.tasks().get("override")), Items.GOLD_INGOT, 2);
        assertStack(QuestPresentation.rewardIconTarget(quest.rewards().get("item")), Items.EMERALD, 4);
        assertStack(QuestPresentation.rewardIconTarget(quest.rewards().get("choice").rewards().get("diamond")), Items.DIAMOND, 2);
    }

    @Test
    void semanticFallbacksAndExplicitUnknownIconsNeverBecomeRecipeTargets() {
        QuestDefinition quest = parse("""
            {
              "tasks": {
                "xp": {"type":"theseus:xp","amount":2},
                "advancement": {"type":"theseus:advancement","advancements":["minecraft:story/mine_stone"]},
                "stat": {"type":"theseus:stat","stat":"minecraft:mined"},
                "composite": {"type":"theseus:composite","amount":1,"tasks":{"check":{"type":"theseus:check"}}},
                "unsupported": {"type":"example:custom_task"},
                "unknownIcon": {"type":"theseus:item","item":"minecraft:diamond","icon":{"type":"example:machine"}}
              },
              "rewards": {
                "xp": {"type":"theseus:xp","amount":2},
                "command": {"type":"theseus:command","command":"say hi"},
                "loot": {"type":"theseus:loottable","loot_table":"minecraft:chests/simple_dungeon"},
                "unsupported": {"type":"example:currency_reward"},
                "unknownIcon": {"type":"theseus:command","command":"say hi","icon":{"type":"example:machine"}}
              }
            }
            """);

        for (String id : new String[] {"xp", "advancement", "stat", "composite", "unsupported", "unknownIcon"}) {
            assertTrue(QuestPresentation.taskIconTarget(quest.tasks().get(id)).isEmpty(), id);
        }
        for (String id : new String[] {"xp", "command", "loot", "unsupported", "unknownIcon"}) {
            assertTrue(QuestPresentation.rewardIconTarget(quest.rewards().get(id)).isEmpty(), id);
        }
    }

    @Test
    void invalidItemsAndEmptyTagsFailClosedInsteadOfExposingFallbackIcons() {
        QuestDefinition quest = parse("""
            {
              "display": {"icon":"minecraft:not_an_item"},
              "tasks": {
                "invalid": {"type":"theseus:item","item":"minecraft:not_an_item"},
                "tag": {"type":"theseus:item","item":"#minecraft:not_a_real_tag"}
              },
              "rewards": {
                "invalid": {"type":"theseus:item","item":"minecraft:not_an_item"}
              }
            }
            """);

        assertTrue(QuestPresentation.questIconTarget(quest).isEmpty());
        assertTrue(QuestPresentation.taskIconTarget(quest.tasks().get("invalid")).isEmpty());
        assertTrue(QuestPresentation.taskIconTarget(quest.tasks().get("tag")).isEmpty());
        assertTrue(QuestPresentation.rewardIconTarget(quest.rewards().get("invalid")).isEmpty());
        assertEquals(Items.PAPER, QuestPresentation.taskIcon(quest.tasks().get("invalid")).getItem());
    }

    @Test
    void builtInItemOverrideOnNonItemContentIsStillEligible() {
        QuestDefinition quest = parse("""
            {
              "tasks": {
                "xp": {"type":"theseus:xp","amount":2,"icon":"minecraft:diamond"}
              },
              "rewards": {
                "command": {"type":"theseus:command","command":"say hi","icon":{"type":"theseus:item","item":"minecraft:emerald"}}
              }
            }
            """);

        assertStack(QuestPresentation.taskIconTarget(quest.tasks().get("xp")), Items.DIAMOND, 1);
        assertStack(QuestPresentation.rewardIconTarget(quest.rewards().get("command")), Items.EMERALD, 1);
    }

    private static QuestDefinition parse(String json) {
        return QuestDefinition.parse("presentation", JsonParser.parseString(json).getAsJsonObject());
    }

    private static void assertStack(Optional<ItemStack> value, net.minecraft.world.item.Item item, int count) {
        assertTrue(value.isPresent());
        assertEquals(item, value.get().getItem());
        assertEquals(count, value.get().getCount());
        assertFalse(value.get().isEmpty());
    }
}
