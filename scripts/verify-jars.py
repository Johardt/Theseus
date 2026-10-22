#!/usr/bin/env python3
"""Check that both release jars contain the shared mod and correct loader metadata."""
import json
from pathlib import Path
import sys
import tomllib
import zipfile

root = Path(__file__).resolve().parents[1]
properties = dict(
    line.split("=", 1)
    for line in (root / "gradle.properties").read_text().splitlines()
    if "=" in line and not line.startswith("#")
)
version = properties["modVersion"]
minecraft = properties["minecraftVersion"]
output = Path(sys.argv[1]) if len(sys.argv) > 1 else None
shared = {
    "me/johardt/theseus/Theseus.class",
    "me/johardt/theseus/core/QuestRuntime.class",
    "me/johardt/theseus/client/QuestScreen.class",
    "assets/theseus/lang/en_us.json",
    "assets/theseus/items/quest_book.json",
    "config/theseus/groups.txt",
    "config/theseus/quests/getting_started/welcome.json",
}
for loader, directory in [("neoforge", root / "build/libs"), ("fabric", root / "fabric/build/libs")]:
    jar = (output or directory) / f"theseus-{loader}-{minecraft}-{version}.jar"
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        missing = shared - names
        assert not missing, f"{jar}: missing {sorted(missing)}"
        if loader == "neoforge":
            metadata = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode())
            mod = next(mod for mod in metadata["mods"] if mod["modId"] == "theseus")
            assert mod["version"] == version
            dependencies = {entry["modId"]: entry for entry in metadata["dependencies"]["theseus"]}
            assert dependencies["minecraft"]["versionRange"] == f"[{minecraft}]"
            assert dependencies["neoforge"]["versionRange"] == f'[{properties["neoForgeVersion"]},)'
            assert dependencies["resourcefullib"]["type"] == "required"
            assert "me/johardt/theseus/TheseusNeoForge.class" in names
            assert "fabric.mod.json" not in names
            assert not any(name.startswith("me/johardt/theseus/mixin/") for name in names)
        else:
            metadata = json.loads(archive.read("fabric.mod.json"))
            assert metadata["id"] == "theseus" and metadata["version"] == version
            assert metadata["depends"]["minecraft"] == minecraft
            assert metadata["depends"]["fabricloader"] == f'>={properties["fabricLoaderVersion"]}'
            assert metadata["depends"]["fabric-api"] == f'>={properties["fabricApiVersion"]}'
            assert "resourcefullib" in metadata["depends"]
            assert "META-INF/neoforge.mods.toml" not in names
            for kind in ["main", "client", "rei_client", "jei_mod_plugin"]:
                for entrypoint in metadata["entrypoints"][kind]:
                    assert entrypoint.replace(".", "/") + ".class" in names
            for configuration in metadata["mixins"]:
                mixins = json.loads(archive.read(configuration))
                for mixin in mixins["mixins"]:
                    assert (mixins["package"] + "." + mixin).replace(".", "/") + ".class" in names
        bundled = [name for name in names if name.endswith(".jar") and "olympus" in name]
        assert bundled, f"{jar}: Olympus is not bundled"
    print(f"Verified {jar}")
