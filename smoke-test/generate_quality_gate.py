#!/usr/bin/env python3
"""Generate the deterministic 700-quest Theseus compatibility/performance pack."""

from __future__ import annotations

import json
import shutil
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
RUN_CONFIG = ROOT / "run/config/theseus"
FIXTURE_CONFIG = ROOT / "smoke-test/fixtures/quality_gate/config/theseus"
PACK_RELATIVE = Path("quests/quality_gate")
CHAPTER_COUNT = 10
QUESTS_PER_CHAPTER = 70

BACKGROUNDS = [
    "default",
    "circles",
    "diamonds",
    "gears",
    "hearts",
    "hexagons",
    "octagons",
    "pentagons",
    "rounded_squares",
]

ICONS = [
    "minecraft:map",
    "minecraft:compass",
    "minecraft:oak_log",
    "minecraft:crafting_table",
    "minecraft:bread",
    "minecraft:stone_pickaxe",
    "minecraft:iron_ingot",
    "minecraft:shield",
    "minecraft:bow",
    "minecraft:lantern",
    "minecraft:book",
    "minecraft:enchanted_book",
    "minecraft:glass_bottle",
    "minecraft:blaze_rod",
    "minecraft:ender_pearl",
    "minecraft:redstone",
    "minecraft:piston",
    "minecraft:fishing_rod",
    "minecraft:prismarine_shard",
    "minecraft:trident",
    "minecraft:diamond",
    "minecraft:emerald",
    "minecraft:amethyst_shard",
    "minecraft:echo_shard",
    "minecraft:elytra",
    "minecraft:beacon",
    "minecraft:nether_star",
    "minecraft:golden_apple",
    "minecraft:spyglass",
    "minecraft:recovery_compass",
]

ITEMS = [
    "minecraft:oak_log",
    "minecraft:oak_planks",
    "minecraft:bread",
    "minecraft:torch",
    "minecraft:stone_pickaxe",
    "minecraft:iron_ingot",
    "minecraft:shield",
    "minecraft:bow",
    "minecraft:fishing_rod",
    "minecraft:redstone",
    "minecraft:bucket",
    "minecraft:glass_bottle",
    "minecraft:diamond",
    "minecraft:emerald",
    "minecraft:arrow",
    "minecraft:bone",
    "minecraft:string",
    "minecraft:leather",
    "minecraft:wheat_seeds",
    "minecraft:carrot",
    "minecraft:potato",
    "minecraft:golden_apple",
    "minecraft:amethyst_shard",
    "minecraft:prismarine_shard",
    "minecraft:blaze_rod",
    "minecraft:ender_pearl",
    "minecraft:echo_shard",
    "minecraft:spyglass",
]

ENTITIES = [
    "minecraft:pig",
    "minecraft:cow",
    "minecraft:sheep",
    "minecraft:chicken",
    "minecraft:zombie",
    "minecraft:skeleton",
    "minecraft:spider",
    "minecraft:creeper",
    "minecraft:enderman",
    "minecraft:blaze",
    "minecraft:drowned",
    "minecraft:guardian",
    "minecraft:iron_golem",
    "minecraft:villager",
]

INTERACTABLE_ENTITIES = [
    "minecraft:pig",
    "minecraft:cow",
    "minecraft:sheep",
    "minecraft:chicken",
    "minecraft:villager",
    "minecraft:horse",
    "minecraft:donkey",
    "minecraft:cat",
    "minecraft:wolf",
    "minecraft:fox",
    "minecraft:bee",
    "minecraft:mooshroom",
]

INTERACTION_ITEMS = [
    "minecraft:bone_meal",
    "minecraft:flint_and_steel",
    "minecraft:spyglass",
    "minecraft:fishing_rod",
    "minecraft:shears",
    "minecraft:carrot_on_a_stick",
    "minecraft:brush",
    "minecraft:water_bucket",
    "minecraft:bow",
    "minecraft:shield",
]

CONSUMABLE_ITEMS = [
    "minecraft:bread",
    "minecraft:golden_apple",
    "minecraft:honey_bottle",
    "minecraft:milk_bucket",
    "minecraft:potion",
    "minecraft:chorus_fruit",
    "minecraft:cookie",
    "minecraft:mushroom_stew",
    "minecraft:rabbit_stew",
    "minecraft:baked_potato",
]

BLOCKS = [
    "minecraft:oak_log",
    "minecraft:crafting_table",
    "minecraft:stone",
    "minecraft:furnace",
    "minecraft:barrel",
    "minecraft:campfire",
    "minecraft:chest",
    "minecraft:lectern",
    "minecraft:bell",
    "minecraft:bee_nest",
    "minecraft:redstone_lamp",
    "minecraft:piston",
    "minecraft:sea_lantern",
    "minecraft:oak_door",
    "minecraft:lever",
]

BIOMES = [
    "minecraft:plains",
    "minecraft:forest",
    "minecraft:meadow",
    "minecraft:taiga",
    "minecraft:desert",
    "minecraft:savanna",
    "minecraft:ocean",
    "minecraft:river",
    "minecraft:deep_dark",
    "minecraft:nether_wastes",
    "minecraft:warped_forest",
]

ADVANCEMENTS = [
    "minecraft:story/mine_stone",
    "minecraft:story/upgrade_tools",
    "minecraft:story/smelt_iron",
    "minecraft:story/obtain_armor",
    "minecraft:nether/enter_nether",
    "minecraft:nether/obtain_blaze_rod",
    "minecraft:adventure/kill_a_mob",
    "minecraft:adventure/trade",
    "minecraft:adventure/ol_betsy",
    "minecraft:husbandry/plant_seed",
    "minecraft:husbandry/balanced_diet",
]

RECIPES = [
    "minecraft:crafting_table",
    "minecraft:oak_planks",
    "minecraft:bread",
    "minecraft:torch",
    "minecraft:furnace",
    "minecraft:chest",
    "minecraft:shield",
    "minecraft:iron_pickaxe",
    "minecraft:bow",
    "minecraft:fishing_rod",
    "minecraft:piston",
    "minecraft:glass",
]

STATS = [
    "minecraft:jump",
    "minecraft:play_time",
    "minecraft:walk_one_cm",
    "minecraft:mob_kills",
    "minecraft:damage_dealt",
]

DIMENSIONS = [
    "minecraft:overworld",
    "minecraft:the_nether",
    "minecraft:the_end",
]

LOOT_TABLES = [
    "minecraft:chests/simple_dungeon",
    "minecraft:chests/abandoned_mineshaft",
    "minecraft:entities/zombie",
    "minecraft:entities/skeleton",
]

TASK_TYPES = [
    "theseus:dummy",
    "theseus:item",
    "theseus:xp",
    "theseus:kill_entity",
    "theseus:advancement",
    "theseus:biome",
    "theseus:block_interaction",
    "theseus:changed_dimension",
    "theseus:check",
    "theseus:entity_interaction",
    "theseus:item_interaction",
    "theseus:item_use",
    "theseus:location",
    "theseus:recipe",
    "theseus:stat",
    "theseus:structure",
]


CHAPTERS = [
    {
        "name": "01 · Trailhead and Shelter",
        "slug": "chapter_01_trailhead",
        "summary": "Build a safe camp, mark a first route, and learn to read the land.",
        "lanes": ["Tent Lines", "Signal Fires", "River Stones", "Trail Rations", "Cairn Markers", "First Tools", "Map Notes"],
        "verbs": ["Mark", "Gather", "Chart", "Prepare", "Cross", "Secure", "Report", "Trace", "Test", "Finish"],
        "icons": ["minecraft:map", "minecraft:compass", "minecraft:oak_log", "minecraft:torch", "minecraft:bread", "minecraft:stone_pickaxe", "minecraft:campfire"],
        "chapter_icon": "minecraft:compass",
        "chapter_background": "minecraft:textures/block/oak_planks.png",
        "chapter_opacity": 22,
        "hooks": ["the weather is turning", "the river is rising", "the last torch is burning low", "a fresh trail marker has appeared", "the first night will be here soon", "a supply crate waits beyond camp", "the ridge is clear for a short time"],
    },
    {
        "name": "02 · Woodland Provisions",
        "slug": "chapter_02_woodland",
        "summary": "Turn forest paths into orchards, food stores, and a reliable harvest.",
        "lanes": ["Orchard Rows", "Bee Roads", "Seed Stores", "Milling Shed", "Hedge Paths", "Compost Yard", "Harvest Table"],
        "verbs": ["Prune", "Plant", "Tend", "Collect", "Mill", "Protect", "Trade", "Replant", "Measure", "Harvest"],
        "icons": ["minecraft:apple", "minecraft:beehive", "minecraft:wheat", "minecraft:bread", "minecraft:oak_sapling", "minecraft:composter", "minecraft:golden_carrot"],
        "chapter_icon": "minecraft:wheat",
        "chapter_background": "minecraft:textures/block/moss_block.png",
        "chapter_opacity": 23,
        "hooks": ["the orchard needs another season of care", "a bee route now crosses the footpath", "winter stores are running thin", "the mill wheel has started turning", "a villager asked for the first surplus", "young shoots need a safer fence", "the harvest table has room for one more dish"],
    },
    {
        "name": "03 · Stonework and Smithing",
        "slug": "chapter_03_foundry",
        "summary": "Follow ore from the quarry through the furnace to a working foundry.",
        "lanes": ["Copper Seam", "Kiln Yard", "Iron Works", "Stonecutters", "Furnace Hall", "Rail Spur", "Foundry Gate"],
        "verbs": ["Survey", "Quarry", "Smelt", "Shape", "Temper", "Stock", "Lay", "Inspect", "Light", "Commission"],
        "icons": ["minecraft:raw_copper", "minecraft:furnace", "minecraft:iron_ingot", "minecraft:stonecutter", "minecraft:blast_furnace", "minecraft:rail", "minecraft:anvil"],
        "chapter_icon": "minecraft:iron_ingot",
        "chapter_background": "minecraft:textures/block/polished_deepslate.png",
        "chapter_opacity": 20,
        "hooks": ["the quarry face has exposed a new layer", "the kiln crew needs fuel before dusk", "a damaged tool has reached the repair bench", "fresh stone is waiting at the cutting table", "the furnace has cooled between shifts", "the rail spur needs a final inspection", "the foreman has opened the foundry gate"],
    },
    {
        "name": "04 · Roads Beyond the Valley",
        "slug": "chapter_04_wayfinding",
        "summary": "Connect rivers, deserts, high passes, and the first portal route.",
        "lanes": ["River Crossing", "Desert Wells", "Taiga Pass", "Sailor's Route", "High Ridge", "Nether Link", "Portal Return"],
        "verbs": ["Sound", "Mark", "Provision", "Navigate", "Climb", "Open", "Cross", "Resupply", "Retrace", "Chart"],
        "icons": ["minecraft:boat", "minecraft:water_bucket", "minecraft:leather_boots", "minecraft:compass", "minecraft:spyglass", "minecraft:flint_and_steel", "minecraft:ender_pearl"],
        "chapter_icon": "minecraft:spyglass",
        "chapter_background": "minecraft:textures/block/sandstone.png",
        "chapter_opacity": 21,
        "hooks": ["the river ford has shifted overnight", "a dry well sits just beyond the dunes", "the snowline has moved higher", "a wreck marker appeared on the chart", "the summit path is clear at noon", "a portal frame is missing one route marker", "a return signal came from the other side"],
    },
    {
        "name": "05 · The Night Watch",
        "slug": "chapter_05_nightwatch",
        "summary": "Secure the roads against hostile mobs and keep the outposts supplied.",
        "lanes": ["Night Watch", "Zombie Trail", "Skeleton Bluff", "Creeper Ward", "Spider Warrens", "Nether Patrol", "Guardian Reef"],
        "verbs": ["Post", "Track", "Clear", "Fortify", "Sweep", "Signal", "Rescue", "Reinforce", "Escort", "Stand"],
        "icons": ["minecraft:shield", "minecraft:iron_sword", "minecraft:bow", "minecraft:crossbow", "minecraft:shield", "minecraft:blaze_rod", "minecraft:trident"],
        "chapter_icon": "minecraft:shield",
        "chapter_background": "minecraft:textures/block/deepslate_bricks.png",
        "chapter_opacity": 21,
        "hooks": ["an outpost lantern went dark", "tracks lead back toward the gate", "arrows struck the bluff's warning post", "a damaged wall needs one more brace", "webs have covered the lower passage", "the patrol missed its last check-in", "a reef beacon is blinking beneath the waves"],
    },
    {
        "name": "06 · The Arcane Workshop",
        "slug": "chapter_06_arcana",
        "summary": "Organize the library, brew useful mixtures, and put old knowledge to work.",
        "lanes": ["Librarian's Desk", "Enchantment Shelf", "Brewing Bench", "Trial Notes", "Ancient Script", "Archivist's Key", "Research Hall"],
        "verbs": ["Catalogue", "Copy", "Brew", "Translate", "Compare", "Restore", "Annotate", "Bind", "Test", "Publish"],
        "icons": ["minecraft:book", "minecraft:enchanted_book", "minecraft:glass_bottle", "minecraft:knowledge_book", "minecraft:written_book", "minecraft:tripwire_hook", "minecraft:enchanting_table"],
        "chapter_icon": "minecraft:enchanted_book",
        "chapter_background": "minecraft:textures/block/enchanting_table_side.png",
        "chapter_opacity": 18,
        "hooks": ["a loose page turned up in the map drawer", "the enchantment shelf is missing a label", "the brewing bench has a clean flask ready", "a trial note contradicts the old index", "an inscription is still legible under the dust", "the archive lock responds to a new key", "the research hall is ready for a final review"],
    },
    {
        "name": "07 · Machines and Materials",
        "slug": "chapter_07_machines",
        "summary": "Build dependable circuits, lifts, sorting lines, and workshop machinery.",
        "lanes": ["Piston Yard", "Redstone Circuit", "Rail Station", "Sorting Loft", "Signal Tower", "Quarry Lift", "Workshop Core"],
        "verbs": ["Wire", "Calibrate", "Assemble", "Route", "Power", "Test", "Balance", "Automate", "Repair", "Commission"],
        "icons": ["minecraft:piston", "minecraft:redstone", "minecraft:rail", "minecraft:hopper", "minecraft:redstone_lamp", "minecraft:lever", "minecraft:observer"],
        "chapter_icon": "minecraft:redstone",
        "chapter_background": "minecraft:textures/block/redstone_block.png",
        "chapter_opacity": 16,
        "hooks": ["a test pulse stopped short of the marker", "the circuit is receiving uneven power", "the station is ready for its first cart", "two storage lines are sharing a hopper", "the tower lamp needs a timing check", "the lift cable has reached the upper landing", "the workshop core is waiting for a stable signal"],
    },
    {
        "name": "08 · Tides and Open Water",
        "slug": "chapter_08_tides",
        "summary": "Read the coast, recover a ship's route, and work safely below the surface.",
        "lanes": ["Reed Delta", "Reef Shelf", "Shipwreck Chart", "Ice Floe", "Coastal Beacon", "Deep Current", "Open Water"],
        "verbs": ["Sound", "Dive", "Recover", "Follow", "Anchor", "Mark", "Repair", "Survey", "Rescue", "Return"],
        "icons": ["minecraft:fishing_rod", "minecraft:prismarine_shard", "minecraft:map", "minecraft:packed_ice", "minecraft:lantern", "minecraft:conduit", "minecraft:trident"],
        "chapter_icon": "minecraft:prismarine_shard",
        "chapter_background": "minecraft:textures/block/prismarine_bricks.png",
        "chapter_opacity": 18,
        "hooks": ["the delta has split into a new channel", "a reef shelf is visible at low tide", "the ship's chart survived the wreck", "a camp flag is frozen into the floe", "the coastal beacon lost its upper lamp", "the deep current has changed direction", "the horizon is clear enough for a long crossing"],
    },
    {
        "name": "09 · Relics Below the Surface",
        "slug": "chapter_09_relics",
        "summary": "Trace old routes through mineshafts, strongholds, ancient cities, and bastions.",
        "lanes": ["Mineshaft Seal", "Stronghold Ring", "Ancient City", "Bastion Archive", "End Portal", "Dragon Echo", "Relic Vault"],
        "verbs": ["Descend", "Trace", "Unseal", "Recover", "Map", "Listen", "Secure", "Compare", "Carry", "Archive"],
        "icons": ["minecraft:chest_minecart", "minecraft:ender_eye", "minecraft:echo_shard", "minecraft:gold_block", "minecraft:ender_pearl", "minecraft:dragon_breath", "minecraft:recovery_compass"],
        "chapter_icon": "minecraft:recovery_compass",
        "chapter_background": "minecraft:textures/block/ancient_debris_side.png",
        "chapter_opacity": 17,
        "hooks": ["a minecart track disappears beneath the seal", "the stronghold ring is missing one marker", "a quiet pulse came from the ancient city", "an archive shelf survived the bastion fire", "the portal room needs a verified route", "an old dragon record has surfaced", "the vault door has opened just wide enough"],
    },
    {
        "name": "10 · Wayfinder's Legacy",
        "slug": "chapter_10_legacy",
        "summary": "Bring the expedition together in a beacon summit and a lasting atlas.",
        "lanes": ["Wayfinder's Oath", "Banner Hall", "Beacon Summit", "Trade Concord", "Overworld Atlas", "Nether Accord", "Endward Door"],
        "verbs": ["Renew", "Raise", "Light", "Seal", "Complete", "Share", "Unite", "Witness", "Dedicate", "Remember"],
        "icons": ["minecraft:book", "minecraft:banner", "minecraft:beacon", "minecraft:emerald", "minecraft:map", "minecraft:nether_star", "minecraft:elytra"],
        "chapter_icon": "minecraft:beacon",
        "chapter_background": "minecraft:textures/block/end_stone_bricks.png",
        "chapter_opacity": 17,
        "hooks": ["the expedition's original map has one blank corner", "the banner is ready for its final pattern", "the beacon platform needs a last inspection", "traders from every road have arrived", "the atlas now contains the full route", "the Nether accord waits for a witness", "the final door is open and the route is clear"],
    },
]


def pick(values: list[str], seed: int) -> str:
    return values[seed % len(values)]


def item_icon(item: str) -> dict[str, str]:
    return {"type": "theseus:item", "item": item}


def simple_task(task_type: str, chapter: int, quest_index: int, ordinal: int) -> dict[str, Any]:
    seed = chapter * 101 + quest_index * 17 + ordinal * 29
    item = pick(ITEMS, seed)
    entity = pick(ENTITIES, seed + 3)
    block = pick(BLOCKS, seed + 5)
    biome = pick(BIOMES, seed + 7)
    advancement = pick(ADVANCEMENTS, seed + 11)
    recipe = pick(RECIPES, seed + 13)
    icon = pick(ICONS, seed + 19)
    amount = 1 + seed % 6
    task: dict[str, Any] = {"type": task_type, "title": "", "description": ""}

    if task_type == "theseus:dummy":
        task.update(value=f"quality_gate_{chapter + 1:02d}_{quest_index + 1:02d}_{ordinal}")
        task["title"] = "Record the field marker"
        task["description"] = "Run the matching Theseus dummy trigger to close this report."
    elif task_type == "theseus:item":
        target_item = "#minecraft:logs" if seed % 5 == 0 else item
        task.update(item=target_item, amount=amount, collection=pick(["automatic", "consume", "manual"], seed // 3))
        if seed % 4 == 0:
            task["components"] = {}
        task["title"] = "Secure " + target_item.removeprefix("#minecraft:").removeprefix("minecraft:").replace("_", " ")
        task["description"] = "Bring the requested supply to camp; collection mode varies across the ledger."
    elif task_type == "theseus:xp":
        task.update(amount=amount, xpType=pick(["points", "levels"], seed), collectionType=pick(["automatic", "consume", "manual"], seed // 2))
        task["title"] = "Bank expedition experience"
        task["description"] = "Build the requested experience reserve before the next leg."
    elif task_type == "theseus:kill_entity":
        task.update(entity=entity, amount=1 + seed % 5)
        task["title"] = "Clear the " + entity.removeprefix("minecraft:").replace("_", " ") + " trail"
        task["description"] = "Defeat the listed creature to make the route safe for the next crew."
    elif task_type == "theseus:advancement":
        task.update(advancements=[advancement])
        task["title"] = "Log a milestone"
        task["description"] = "Earn the named vanilla advancement to verify the expedition record."
    elif task_type == "theseus:biome":
        task.update(biomes=biome)
        task["title"] = "Read the signs in " + biome.removeprefix("minecraft:").replace("_", " ")
        task["description"] = "Travel through the selected biome and update the field map."
    elif task_type == "theseus:block_interaction":
        block_target = "minecraft:lever" if seed % 3 == 0 else block
        task.update(block=block_target)
        if seed % 3 == 0:
            task["state"] = {"powered": "false"}
        task["title"] = "Inspect the " + block_target.removeprefix("minecraft:").replace("_", " ") + " station"
        task["description"] = "Interact with the marked block to verify this part of the route."
    elif task_type == "theseus:changed_dimension":
        from_dimension = DIMENSIONS[seed % len(DIMENSIONS)]
        to_dimension = DIMENSIONS[(seed + 1) % len(DIMENSIONS)]
        task.update(**{"from": from_dimension, "to": to_dimension})
        task["title"] = "Cross a dimensional boundary"
        task["description"] = "Travel from " + from_dimension.removeprefix("minecraft:").replace("_", " ") + " to " + to_dimension.removeprefix("minecraft:").replace("_", " ") + "."
    elif task_type == "theseus:check":
        task.update(components={})
        task["title"] = "Submit the field report"
        task["description"] = "Use the manual check action to sign off this expedition note."
    elif task_type == "theseus:entity_interaction":
        target_entity = pick(INTERACTABLE_ENTITIES, seed)
        task.update(entity=target_entity, components={})
        task["title"] = "Speak with the " + target_entity.removeprefix("minecraft:").replace("_", " ")
        task["description"] = "Interact with the listed character or creature and record the meeting."
    elif task_type == "theseus:item_interaction":
        interaction_item = pick(INTERACTION_ITEMS, seed)
        task.update(item=interaction_item, components={})
        task["title"] = "Present the " + interaction_item.removeprefix("minecraft:").replace("_", " ")
        task["description"] = "Interact while carrying the selected item to complete the handoff."
    elif task_type == "theseus:item_use":
        consumable = pick(CONSUMABLE_ITEMS, seed)
        task.update(item=consumable, components={})
        task["title"] = "Use the " + consumable.removeprefix("minecraft:").replace("_", " ") + " provision"
        task["description"] = "Consume the selected provision and report the result."
    elif task_type == "theseus:location":
        location_dimension = "minecraft:the_nether" if biome in {"minecraft:nether_wastes", "minecraft:warped_forest"} else "minecraft:overworld"
        task.update(predicate={
            "dimension": location_dimension,
            "biomes": biome,
            "position": {
                "x": {"min": -256 + seed % 64, "max": 256 + seed % 96},
                "y": {"min": 48, "max": 110 if location_dimension == "minecraft:the_nether" else 192},
                "z": {"min": -256, "max": 256},
            },
        })
        task["title"] = "Reach the mapped coordinates"
        task["description"] = "Visit the saved dimension, biome, and coordinate range on the expedition map."
    elif task_type == "theseus:recipe":
        task.update(recipes=[recipe])
        task["title"] = "Learn the " + recipe.removeprefix("minecraft:").replace("_", " ") + " pattern"
        task["description"] = "Unlock the listed recipe before the workshop closes for the night."
    elif task_type == "theseus:stat":
        stat = pick(STATS, seed)
        task.update(stat=stat, target=4 + seed % 97)
        task["title"] = "Record " + stat.removeprefix("minecraft:").replace("_", " ")
        task["description"] = "Reach the target statistic and add the result to the trip ledger."
    elif task_type == "theseus:structure":
        structure = "#minecraft:village" if seed % 3 else "minecraft:stronghold"
        task.update(structures=structure)
        task["title"] = "Find a landmark"
        task["description"] = "Locate the structure marker and confirm it on the route map."
    else:
        raise ValueError(f"Unknown task type: {task_type}")

    if (chapter + quest_index + ordinal) % 3 == 0:
        task["icon"] = item_icon(icon)
    if (chapter * 13 + quest_index + ordinal) % 31 == 0:
        task["fixture_extension"] = {"retained": True, "slot": ordinal}
    return task


def composite_task(chapter: int, quest_index: int, depth: int = 1) -> dict[str, Any]:
    seed = chapter * 83 + quest_index * 7 + depth
    child_count = 3 if seed % 2 else 4
    children: dict[str, Any] = {}
    for child_index in range(child_count):
        if depth > 1 and child_index == child_count - 1:
            children["branch_bundle"] = composite_task(chapter, quest_index + child_index + 1, depth - 1)
        else:
            task_type = pick(TASK_TYPES, seed + child_index * 5)
            children[f"trail_{child_index + 1}"] = simple_task(
                task_type,
                chapter,
                quest_index,
                child_index + depth,
            )
    return {
        "type": "theseus:composite",
        "title": "Complete the linked field bundle",
        "description": "Any mix of route, supply, and discovery notes can close this bundled task.",
        "amount": min(child_count, 2 + seed % 2),
        "tasks": children,
        **({"icon": item_icon(pick(ICONS, seed + 23))} if seed % 2 else {}),
    }


def icon_for(item: str, seed: int) -> Any:
    # Primitive strings exercise the older item-only icon form alongside the typed form.
    return item if seed % 7 == 0 else item_icon(item)


def reward_item(seed: int) -> dict[str, Any]:
    item = pick(ITEMS, seed + 9)
    reward: dict[str, Any] = {
        "type": "theseus:item",
        "title": "Supply crate: " + item.removeprefix("minecraft:").replace("_", " "),
        "item": {"id": item, "count": 1 + seed % 8},
    }
    if seed % 4 == 0:
        reward["icon"] = item_icon(item)
    return reward


def reward_xp(seed: int) -> dict[str, Any]:
    return {
        "type": "theseus:xp",
        "title": "Expedition experience",
        "xptype": pick(["points", "level"], seed),
        "amount": 3 + seed % 42,
    }


def reward_loot(seed: int) -> dict[str, Any]:
    loot = pick(LOOT_TABLES, seed)
    return {
        "type": "theseus:loottable",
        "title": "Field cache",
        "loot_table": loot,
        **({"icon": item_icon("minecraft:chest")} if seed % 2 == 0 else {}),
    }


def reward_command(quest_id: str) -> dict[str, Any]:
    return {
        "type": "theseus:command",
        "title": "Personal expedition notice",
        "command": f"tell @s Quality gate reward {quest_id}",
    }


def reward_selectable(seed: int, quest_id: str) -> dict[str, Any]:
    choices = {
        "field_xp": reward_xp(seed + 1),
        "ration_pack": reward_item(seed + 2),
        "recovered_cache": reward_loot(seed + 3),
        "trail_notice": reward_command(quest_id),
    }
    return {
        "type": "theseus:selectable",
        "title": "Choose a trail provision",
        "amount": 1 + seed % 3,
        "rewards": choices,
        **({"icon": item_icon("minecraft:bundle")} if seed % 2 == 0 else {}),
    }


def rewards_for(chapter: int, quest_index: int, quest_id: str) -> dict[str, Any]:
    seed = chapter * 97 + quest_index * 11
    bundles = [
        ["xp"],
        ["item"],
        ["xp", "item"],
        ["loot"],
        ["command"],
        ["xp", "loot"],
        ["item", "command"],
        ["xp", "item", "command"],
        ["selectable"],
        ["selectable", "xp"],
        ["loot", "item"],
        ["xp", "loot", "item", "command"],
    ]
    selected = bundles[seed % len(bundles)]
    result: dict[str, Any] = {}
    for ordinal, reward_type in enumerate(selected):
        reward_seed = seed + ordinal * 19
        if reward_type == "xp":
            reward = reward_xp(reward_seed)
        elif reward_type == "item":
            reward = reward_item(reward_seed)
        elif reward_type == "loot":
            reward = reward_loot(reward_seed)
        elif reward_type == "command":
            reward = reward_command(quest_id)
        else:
            reward = reward_selectable(reward_seed, quest_id)
        result[f"{reward_type}_{ordinal + 1}"] = reward
    return result


def dependencies_for(chapter: int, row: int, column: int) -> list[str]:
    dependencies: list[str] = []
    if row == 0:
        if column == 0:
            if chapter > 0:
                dependencies.append(f"qg_{chapter:02d}_09_06")
        elif column in (1, 2):
            dependencies.append(f"qg_{chapter + 1:02d}_00_00")
        elif column == 3:
            dependencies.extend([f"qg_{chapter + 1:02d}_00_01", f"qg_{chapter + 1:02d}_00_02"])
        elif column in (4, 5):
            dependencies.append(f"qg_{chapter + 1:02d}_00_03")
        else:
            dependencies.extend([f"qg_{chapter + 1:02d}_00_04", f"qg_{chapter + 1:02d}_00_05"])
        return dependencies

    if column == 0:
        dependencies.append(f"qg_{chapter + 1:02d}_{row - 1:02d}_06")
    elif column in (1, 2):
        dependencies.append(f"qg_{chapter + 1:02d}_{row:02d}_00")
        dependencies.append(f"qg_{chapter + 1:02d}_{row - 1:02d}_{column:02d}")
    elif column == 3:
        dependencies.extend([
            f"qg_{chapter + 1:02d}_{row:02d}_01",
            f"qg_{chapter + 1:02d}_{row:02d}_02",
            f"qg_{chapter + 1:02d}_{row - 1:02d}_03",
        ])
    elif column in (4, 5):
        dependencies.append(f"qg_{chapter + 1:02d}_{row:02d}_03")
        dependencies.append(f"qg_{chapter + 1:02d}_{row - 1:02d}_{column:02d}")
    else:
        dependencies.extend([
            f"qg_{chapter + 1:02d}_{row:02d}_04",
            f"qg_{chapter + 1:02d}_{row:02d}_05",
            f"qg_{chapter + 1:02d}_{row - 1:02d}_06",
        ])
    return list(dict.fromkeys(dependencies))


def settings_for(chapter: int, quest_index: int) -> dict[str, Any]:
    seed = chapter * 71 + quest_index
    visibility_variants = {
        17: "dependencies_visible",
        34: "in_progress",
        51: "completed",
        68: "never",
    }
    settings: dict[str, Any] = {
        "individual_progress": seed % 4 == 0,
        "hidden": visibility_variants.get(quest_index, "locked"),
        "unlockNotification": seed % 7 == 0,
        "showDependencyArrow": seed % 5 != 0,
        "repeatable": seed % 11 == 0,
        "autoClaimRewards": seed % 9 == 0,
    }
    # Exercise the accepted snake_case spellings on a regular subset.
    if seed % 6 == 0:
        settings["unlock_notification"] = settings.pop("unlockNotification")
    if seed % 8 == 0:
        settings["show_dependency_arrow"] = settings.pop("showDependencyArrow")
    if seed % 10 == 0:
        settings["auto_claim_rewards"] = settings.pop("autoClaimRewards")
    return settings


def make_quest(chapter: int, row: int, column: int) -> tuple[str, dict[str, Any]]:
    chapter_data = CHAPTERS[chapter]
    quest_index = row * 7 + column
    quest_id = f"qg_{chapter + 1:02d}_{row:02d}_{column:02d}"
    lane = chapter_data["lanes"][column]
    verb = chapter_data["verbs"][row]
    hook = chapter_data["hooks"][(row + column) % len(chapter_data["hooks"])]
    quest_title = f"{verb} {lane}"
    task_type = TASK_TYPES[(quest_index + chapter * 5) % len(TASK_TYPES)]
    tasks: dict[str, Any] = {
        "lead": simple_task(task_type, chapter, quest_index, 0),
    }
    if quest_index % 4 != 0:
        secondary_type = TASK_TYPES[(quest_index + chapter * 5 + 7) % len(TASK_TYPES)]
        if secondary_type == task_type:
            secondary_type = TASK_TYPES[(TASK_TYPES.index(secondary_type) + 1) % len(TASK_TYPES)]
        tasks["supporting_note"] = simple_task(secondary_type, chapter, quest_index, 1)
    if quest_index % 8 == 0:
        tasks["field_bundle"] = composite_task(
            chapter,
            quest_index,
            depth=2 if quest_index % 24 == 0 else 1,
        )

    icon_item = chapter_data["icons"][(row + column + chapter) % len(chapter_data["icons"])]
    description = [
        f"{hook.capitalize()}; the {lane.lower()} team needs this entry before it can move on.",
        f"Complete the {task_type.removeprefix('theseus:').replace('_', ' ')} objective and follow the connected route markers.",
    ]
    display: dict[str, Any] = {
        "icon": icon_for(icon_item, chapter * 71 + quest_index),
        "icon_background": f"theseus:textures/gui/quest_backgrounds/{BACKGROUNDS[(chapter + row + column) % len(BACKGROUNDS)]}.png",
        "icon_size": pick([12, 16, 16, 20, 24, 32, 48], chapter * 23 + quest_index),
        "title": {"text": quest_title, "color": pick(["white", "gold", "aqua", "green", "yellow"], chapter + quest_index)} if quest_index % 13 == 0 else quest_title,
        "subtitle": f"{chapter_data['name']} · field report {row + 1:02d}.{column + 1}",
        "description": description if quest_index % 3 else description[0],
        "groups": {chapter_data["name"]: {"position": [column * 180, row * 100]}},
    }
    quest: dict[str, Any] = {
        "display": display,
        "settings": settings_for(chapter, quest_index),
        "tasks": tasks,
        "rewards": rewards_for(chapter, quest_index, quest_id),
    }
    dependencies = dependencies_for(chapter, row, column)
    if dependencies:
        quest["dependencies"] = dependencies
    if (chapter * QUESTS_PER_CHAPTER + quest_index) % 23 == 0:
        quest["fixture_extension"] = {
            "source": "theseus-quality-gate",
            "ordinal": chapter * QUESTS_PER_CHAPTER + quest_index,
            "preserve": {"enabled": True, "labels": ["fixture", "compatibility", "performance"]},
        }
    return quest_id, quest


def chapter_settings() -> dict[str, Any]:
    settings: dict[str, Any] = {
        "Getting Started": {
            "icon": "minecraft:map",
            "iconEnabled": True,
            "background": "",
            "backgroundOpacity": 100,
        },
    }
    for index, chapter in enumerate(CHAPTERS):
        settings[chapter["name"]] = {
            "icon": chapter["chapter_icon"],
            "iconEnabled": index % 4 != 3,
            "background": chapter["chapter_background"],
            "backgroundOpacity": chapter["chapter_opacity"],
        }
    return settings


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def build_pack() -> dict[Path, bytes]:
    outputs: dict[Path, bytes] = {}
    ids: set[str] = set()
    quest_order: list[str] = []
    dependencies: dict[str, list[str]] = {}
    chapter_counts = [0] * CHAPTER_COUNT
    task_types_used: set[str] = set()
    reward_types_used: set[str] = set()
    backgrounds_used: set[str] = set()

    def collect_tasks(tasks: dict[str, Any]) -> None:
        for task in tasks.values():
            task_types_used.add(task["type"])
            if task["type"] == "theseus:composite":
                collect_tasks(task["tasks"])

    def collect_rewards(rewards: dict[str, Any]) -> None:
        for reward in rewards.values():
            reward_types_used.add(reward["type"])
            if reward["type"] == "theseus:selectable":
                collect_rewards(reward["rewards"])

    for chapter in range(CHAPTER_COUNT):
        chapter_dir = PACK_RELATIVE / CHAPTERS[chapter]["slug"]
        for row in range(10):
            for column in range(7):
                quest_id, quest = make_quest(chapter, row, column)
                if quest_id in ids:
                    raise ValueError(f"Duplicate generated quest ID: {quest_id}")
                ids.add(quest_id)
                quest_order.append(quest_id)
                dependencies[quest_id] = quest.get("dependencies", [])
                chapter_counts[chapter] += 1
                backgrounds_used.add(quest["display"]["icon_background"].rsplit("/", 1)[-1].removesuffix(".png"))
                collect_tasks(quest["tasks"])
                collect_rewards(quest["rewards"])
                outputs[chapter_dir / f"{quest_id}.json"] = json_bytes(quest)

    expected_count = CHAPTER_COUNT * QUESTS_PER_CHAPTER
    if len(ids) != expected_count:
        raise ValueError(f"Expected {expected_count} quests, generated {len(ids)}")
    if chapter_counts != [QUESTS_PER_CHAPTER] * CHAPTER_COUNT:
        raise ValueError(f"Unexpected chapter distribution: {chapter_counts}")
    expected_task_types = set(TASK_TYPES) | {"theseus:composite"}
    if task_types_used != expected_task_types:
        raise ValueError(f"Task-type coverage mismatch: {sorted(expected_task_types - task_types_used)}")
    expected_reward_types = {"theseus:xp", "theseus:item", "theseus:loottable", "theseus:command", "theseus:selectable"}
    if reward_types_used != expected_reward_types:
        raise ValueError(f"Reward-type coverage mismatch: {sorted(expected_reward_types - reward_types_used)}")
    if backgrounds_used != set(BACKGROUNDS):
        raise ValueError(f"Background coverage mismatch: {sorted(set(BACKGROUNDS) - backgrounds_used)}")
    order_index = {quest_id: index for index, quest_id in enumerate(quest_order)}
    for quest_id, prerequisites in dependencies.items():
        unknown = set(prerequisites) - ids
        if unknown:
            raise ValueError(f"{quest_id} refers to missing dependencies: {sorted(unknown)}")
        later = [dependency for dependency in prerequisites if order_index[dependency] >= order_index[quest_id]]
        if later:
            raise ValueError(f"{quest_id} has a cyclic or forward dependency: {later}")

    outputs[Path("groups.txt")] = ("\n".join(chapter["name"] for chapter in CHAPTERS) + "\n").encode("utf-8")
    outputs[Path("group_settings.json")] = json_bytes(chapter_settings())
    return outputs


def write_copy(config_root: Path, outputs: dict[Path, bytes]) -> None:
    pack_root = config_root / PACK_RELATIVE
    if pack_root.exists():
        shutil.rmtree(pack_root)
    for relative_path, contents in outputs.items():
        target = config_root / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(contents)


def main() -> None:
    outputs = build_pack()
    write_copy(RUN_CONFIG, outputs)
    write_copy(FIXTURE_CONFIG, outputs)

    quest_files = [path for path in outputs if path.suffix == ".json" and path.parts[0] == "quests"]
    total_bytes = sum(len(outputs[path]) for path in quest_files)
    for relative in outputs:
        if (RUN_CONFIG / relative).read_bytes() != (FIXTURE_CONFIG / relative).read_bytes():
            raise RuntimeError(f"Run and smoke-test copies differ: {relative}")
    print(f"Generated {len(quest_files)} quests in {CHAPTER_COUNT} chapters ({QUESTS_PER_CHAPTER} each).")
    print(f"Quest JSON payload: {total_bytes:,} bytes.")
    print(f"Run config: {RUN_CONFIG / PACK_RELATIVE}")
    print(f"Smoke-test copy: {FIXTURE_CONFIG / PACK_RELATIVE}")


if __name__ == "__main__":
    main()
