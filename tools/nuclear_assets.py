#!/usr/bin/env python3
"""Generates the assets of Nuclear TNT: block and item textures, models, blockstates,
item definitions, translations, loot tables and recipes of the nuclear part of TNT
Arsenal: the three bombs, the detonator, the Geiger counter and the contamination.
Run by tools/textures.py after its own assets; the translations are merged into
the ones it wrote.

    python3 tools/assets.py

Painting reuses the bundled-sticks style of TNT Arsenal (tools/pixelart.py)."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nuclear_pixelart as P  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.join(REPO, "src", "main", "resources")
NS = "tnt_arsenal"

P.ICONS["star"] = ["...a....", "...a....", "..aaa...", "aaabaaa.", ".aabaa..", "..aaa...", ".aa.aa..", ".a...a.."]
P.ICONS["trefoil_s"] = ["aa..aa", "aa..aa", "..aa..", "..aa..", "......", ".aaaa."]

BOMBS = [
    dict(id="mini_nuke", en="Mini Nuke", de="Mini-Nuke", desc_en="Small, but nuclear", desc_de="Klein, aber nuklear",
         body="#c9a21a", strap=("hazard",), label="#1a1a1a", icon="trefoil_s", pal={"a": "#f2c81b"},
         pattern="rivets", pcol=[], recipe=["minecraft:tnt"] * 4 + ["minecraft:redstone_block", "minecraft:blaze_powder"]),
    dict(id="nuke", en="Nuke", de="Atombombe", desc_en="A city-sized crater", desc_de="Ein Krater so groß wie eine Stadt",
         body="#4a5320", strap=("hazard",), label="#f2c81b", icon="trefoil", pal={"a": "#1a1a1a"},
         pattern="rivets", pcol=[], recipe=["tnt_arsenal:mini_nuke"] * 4 + ["minecraft:nether_star"]),
    dict(id="tsar_bomba", en="Tsar Bomba", de="Zar-Bombe", desc_en="The biggest bomb ever built", desc_de="Die größte Bombe, die je gebaut wurde",
         body="#2a2a2e", strap=("metal", "#c9a227"), label="#8a1010", icon="star", pal={"a": "#ffd23a", "b": "#ff8a1a"},
         pattern="rivets", pcol=[], recipe=["tnt_arsenal:nuke"] * 4 + ["minecraft:nether_star", "minecraft:netherite_ingot"]),
]

DETONATOR_ART = [
    "......o.........",
    "......o.........",
    "......o.........",
    ".....ooo........",
    "....oaaao.......",
    "...oabbbao......",
    "...oabrrbao.....",
    "...oabrRbao.....",
    "...oabbbbao.....",
    "...oaccccao.....",
    "...oacgcgao.....",
    "...oaccccao.....",
    "...oaaaaaao.....",
    "....oooooo......",
    "................",
    "................",
]
DETONATOR_PAL = {"o": "#1a1c20", "a": "#5a6068", "b": "#8a929c", "r": "#c81e1e", "R": "#ff6a5a", "c": "#3a3e44", "g": "#5fd36a"}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def noise_texture(seed, colours, specks=(), speck_count=0):
    import random
    rnd = random.Random(seed)
    c = P.Canvas()
    cols = [P.hexc(x) for x in colours]
    for y in range(16):
        for x in range(16):
            c.set(x, y, cols[rnd.randrange(len(cols))])
    for _ in range(speck_count):
        c.set(rnd.randrange(16), rnd.randrange(16), P.hexc(rnd.choice(specks)))
    return c


def contamination(lang_en, lang_de):
    tex = f"{ROOT}/assets/{NS}/textures"
    # fallout: grey-green ash with glowing specks
    P.write_png(f"{tex}/block/fallout.png", 16, 16,
                noise_texture("fallout", ("#5d6455", "#6b7360", "#535a4c", "#767e6a"), ("#9dff6a", "#c8ff8a", "#7ae04a"), 14).rgba())
    for layers in range(1, 9):
        name = "fallout" if layers == 8 else f"fallout_height{layers * 2}"
        parent = "minecraft:block/snow_block" if layers == 8 else f"minecraft:block/snow_height{layers * 2}"
        textures = {"all": f"{NS}:block/fallout", "particle": f"{NS}:block/fallout"} if layers == 8 else \
            {"texture": f"{NS}:block/fallout", "particle": f"{NS}:block/fallout"}
        if layers == 8:
            write_json(f"{ROOT}/assets/{NS}/models/block/fallout_block.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/fallout"}})
        else:
            write_json(f"{ROOT}/assets/{NS}/models/block/{name}.json", {"parent": parent, "textures": textures})
    write_json(f"{ROOT}/assets/{NS}/blockstates/fallout.json", {"variants": {
        f"layers={n}": {"model": f"{NS}:block/" + ("fallout_block" if n == 8 else f"fallout_height{n * 2}")} for n in range(1, 9)}})
    write_json(f"{ROOT}/assets/{NS}/items/fallout.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/fallout_height2"}})
    # irradiated earth: grey dead soil, a few sickly green flecks on top
    P.write_png(f"{tex}/block/irradiated_earth_top.png", 16, 16,
                noise_texture("earth_top", ("#5e5a52", "#6a655b", "#514d46", "#75705f"), ("#8fae5a", "#3e3a34"), 10).rgba())
    P.write_png(f"{tex}/block/irradiated_earth_side.png", 16, 16,
                noise_texture("earth_side", ("#5a4a3a", "#4e4032", "#665443", "#574738"), ("#6a655b", "#3a3028"), 18).rgba())
    write_json(f"{ROOT}/assets/{NS}/models/block/irradiated_earth.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"{NS}:block/irradiated_earth_top", "side": f"{NS}:block/irradiated_earth_side", "bottom": "minecraft:block/dirt"}})
    # trinitite: green glassy slag with darker swirls and bubbles
    import math
    c = P.Canvas()
    for y in range(16):
        for x in range(16):
            v = math.sin(x * 0.7 + math.sin(y * 0.5) * 2.2) + math.cos(y * 0.6 - x * 0.2)
            base = P.hexc("#4f8a3a") if v > 0.6 else P.hexc("#3d7230") if v > -0.4 else P.hexc("#2c5524")
            c.set(x, y, base)
    for bx, by in ((3, 4), (11, 2), (7, 10), (13, 12), (2, 13)):
        c.set(bx, by, P.hexc("#9ad86a"))
        c.set(bx + 1, by, P.hexc("#1f3d1a"))
    P.write_png(f"{tex}/block/trinitite.png", 16, 16, c.rgba())
    write_json(f"{ROOT}/assets/{NS}/models/block/trinitite.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/trinitite"}})
    for name in ("irradiated_earth", "trinitite"):
        write_json(f"{ROOT}/assets/{NS}/blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write_json(f"{ROOT}/assets/{NS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    # contaminated water: the vanilla water models, the colour comes from the client's fluid model
    write_json(f"{ROOT}/assets/{NS}/blockstates/contaminated_water.json", {"variants": {"": {"model": "minecraft:block/water"}}})
    bucket = [
        "................",
        "................",
        "................",
        "...oooooooooo...",
        "..o.gGgggGgg.o..",
        "..oggGgggggGgo..",
        "..oaggggGgggao..",
        "...oaaaaaaaao...",
        "...obbbbbbbbo...",
        "...oabbbbbbao...",
        "....obbbbbbo....",
        "....oabbbbao....",
        "....obbbbbbo....",
        ".....oooooo.....",
        "................",
        "................"]
    pal = {"o": "#2a2c30", "a": "#8a8f96", "b": "#c4c8cc", "g": "#6fb83a", "G": "#b4ff6a"}
    px = [P.hexc(pal[ch]) + (255,) if ch != "." else (0, 0, 0, 0) for row in bucket for ch in row]
    P.write_png(f"{tex}/item/contaminated_water_bucket.png", 16, 16, px)
    write_json(f"{ROOT}/assets/{NS}/models/item/contaminated_water_bucket.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/contaminated_water_bucket"}})
    write_json(f"{ROOT}/assets/{NS}/items/contaminated_water_bucket.json",
               {"model": {"type": "minecraft:model", "model": f"{NS}:item/contaminated_water_bucket"}})
    # loot: fallout and irradiated earth crumble, trinitite keeps
    def loot(name, item):
        write_json(f"{ROOT}/data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block", "pools": [] if item is None else [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": item}],
                                                                           "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{name}"})
    loot("fallout", None)
    loot("irradiated_earth", "minecraft:dirt")
    loot("trinitite", f"{NS}:trinitite")
    write_json(f"{ROOT}/data/minecraft/tags/fluid/water.json", {"replace": False, "values": [
        f"{NS}:contaminated_water", f"{NS}:flowing_contaminated_water"]})
    write_json(f"{ROOT}/data/minecraft/tags/block/mineable/shovel.json", {"replace": False, "values": [f"{NS}:fallout", f"{NS}:irradiated_earth"]})
    write_json(f"{ROOT}/data/minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": [f"{NS}:trinitite"]})
    for key, en, de in (("block.tnt_arsenal.fallout", "Fallout", "Fallout"),
                        ("block.tnt_arsenal.irradiated_earth", "Irradiated Earth", "Verstrahlte Erde"),
                        ("block.tnt_arsenal.trinitite", "Trinitite", "Trinitit"),
                        ("block.tnt_arsenal.contaminated_water", "Contaminated Water", "Verseuchtes Wasser"),
                        ("item.tnt_arsenal.contaminated_water_bucket", "Bucket of Contaminated Water", "Eimer mit verseuchtem Wasser")):
        lang_en[key] = en
        lang_de[key] = de


# Geiger counter: a yellow housing with a dial and a probe on a cable.
GEIGER_ART = [
    "................",
    "..........kk....",
    ".........kggk...",
    "........kgggk...",
    ".......kgggk....",
    "..kkkkkkc.k.....",
    ".kyyyyyyyk......",
    ".kyKKKKKyk......",
    ".kyKwwwwKyk.....",
    ".kyKwrwwKyk.....",
    ".kyKwwrwKyk.....",
    ".kyKKKKKyk......",
    ".kyyybyyyk......",
    ".kYYYYYYYk......",
    "..kkkkkkk.......",
    "................",
]
GEIGER_PAL = {"k": "#1f1d1a", "y": "#e8c13a", "Y": "#a8861f", "K": "#3a3a3a", "w": "#d9e8c9",
              "r": "#c0392b", "b": "#2f7d32", "g": "#8a8f96", "c": "#5a5a5a"}


def main():
    lang_en = {"itemGroup.tnt_arsenal.nuclear": "TNT Arsenal: Nuclear", "item.tnt_arsenal.detonator": "Detonator",
               "item.tnt_arsenal.detonator.bound": "Bound to the bomb at %s, %s, %s",
               "item.tnt_arsenal.detonator.unbound": "Not bound — right-click a bomb first",
               "item.tnt_arsenal.detonator.other_dimension": "The bomb is in another dimension",
               "item.tnt_arsenal.detonator.not_loaded": "No signal — the bomb is too far away",
               "item.tnt_arsenal.detonator.gone": "The bomb is gone",
               "item.tnt_arsenal.detonator.fired": "Detonation sequence started"}
    lang_de = {"itemGroup.tnt_arsenal.nuclear": "TNT-Arsenal: Atom", "item.tnt_arsenal.detonator": "Fernzünder",
               "item.tnt_arsenal.detonator.bound": "Verbunden mit der Bombe bei %s, %s, %s",
               "item.tnt_arsenal.detonator.unbound": "Nicht verbunden – erst eine Bombe rechtsklicken",
               "item.tnt_arsenal.detonator.other_dimension": "Die Bombe ist in einer anderen Dimension",
               "item.tnt_arsenal.detonator.not_loaded": "Kein Signal – die Bombe ist zu weit weg",
               "item.tnt_arsenal.detonator.gone": "Die Bombe ist nicht mehr da",
               "item.tnt_arsenal.detonator.fired": "Zündsequenz gestartet"}
    faces = {}
    for b in BOMBS:
        k = dict(b)
        side, top, bottom = P.paint(k)
        faces[b["id"]] = (side, top)
        base = f"{ROOT}/assets/{NS}/textures/block/{b['id']}"
        P.write_png(base + "_side.png", 16, 16, side.rgba())
        P.write_png(base + "_top.png", 16, 16, top.rgba())
        P.write_png(base + "_bottom.png", 16, 16, bottom.rgba())
        write_json(f"{ROOT}/assets/{NS}/models/block/{b['id']}.json", {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": f"{NS}:block/{b['id']}_top", "bottom": f"{NS}:block/{b['id']}_bottom",
                         "side": f"{NS}:block/{b['id']}_side"}})
        write_json(f"{ROOT}/assets/{NS}/blockstates/{b['id']}.json", {"variants": {"": {"model": f"{NS}:block/{b['id']}"}}})
        write_json(f"{ROOT}/assets/{NS}/items/{b['id']}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{b['id']}"}})
        write_json(f"{ROOT}/data/{NS}/loot_table/blocks/{b['id']}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b['id']}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{b['id']}"})
        write_json(f"{ROOT}/data/{NS}/recipe/{b['id']}.json", {
            "type": "minecraft:crafting_shapeless", "category": "redstone",
            "ingredients": b["recipe"], "result": {"id": f"{NS}:{b['id']}", "count": 1}})
        lang_en[f"block.{NS}.{b['id']}"] = b["en"]
        lang_de[f"block.{NS}.{b['id']}"] = b["de"]
        lang_en[f"block.{NS}.{b['id']}.desc"] = b["desc_en"]
        lang_de[f"block.{NS}.{b['id']}.desc"] = b["desc_de"]
    # charred log: black bark with glowing cracks, burnt rings on the ends
    import random
    rnd = random.Random("charred")
    bark = [P.hexc(c) for c in ("#141210", "#1e1a17", "#2a2420", "#383029")]
    side, end = P.Canvas(), P.Canvas()
    for y in range(16):
        for x in range(16):
            c = bark[(x * 7 + y * 3 + rnd.randrange(3)) % 4] if x % 4 else bark[0]
            side.set(x, y, c)
    for _ in range(5):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for step in range(rnd.randint(3, 6)):
            side.set(x % 16, y % 16, P.hexc("#ff6a1a") if step % 2 == 0 else P.hexc("#b8320a"))
            y += 1
            x += rnd.choice((-1, 0, 0, 1))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            ring = int(d) % 3
            c = bark[0] if d > 7 else [P.hexc("#2a2420"), P.hexc("#3a302a"), P.hexc("#221d1a")][ring]
            if d < 2.2:
                c = P.hexc("#c8461a") if d < 1.2 else P.hexc("#6a2a12")
            end.set(x, y, c)
    P.write_png(f"{ROOT}/assets/{NS}/textures/block/charred_log.png", 16, 16, side.rgba())
    P.write_png(f"{ROOT}/assets/{NS}/textures/block/charred_log_top.png", 16, 16, end.rgba())
    write_json(f"{ROOT}/assets/{NS}/models/block/charred_log.json", {"parent": "minecraft:block/cube_column",
               "textures": {"end": f"{NS}:block/charred_log_top", "side": f"{NS}:block/charred_log"}})
    write_json(f"{ROOT}/assets/{NS}/models/block/charred_log_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal",
               "textures": {"end": f"{NS}:block/charred_log_top", "side": f"{NS}:block/charred_log"}})
    write_json(f"{ROOT}/assets/{NS}/blockstates/charred_log.json", {"variants": {
        "axis=y": {"model": f"{NS}:block/charred_log"},
        "axis=z": {"model": f"{NS}:block/charred_log_horizontal", "x": 90},
        "axis=x": {"model": f"{NS}:block/charred_log_horizontal", "x": 90, "y": 90}}})
    write_json(f"{ROOT}/assets/{NS}/items/charred_log.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/charred_log"}})
    write_json(f"{ROOT}/data/{NS}/loot_table/blocks/charred_log.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:charcoal"}]}],
        "random_sequence": f"{NS}:blocks/charred_log"})
    lang_en["death.attack.tnt_arsenal.radiation"] = "%1$s died of radiation sickness"
    lang_de["death.attack.tnt_arsenal.radiation"] = "%1$s ist an der Strahlenkrankheit gestorben"
    lang_en["block.tnt_arsenal.charred_log"] = "Charred Log"
    lang_de["block.tnt_arsenal.charred_log"] = "Verkohlter Stamm"
    contamination(lang_en, lang_de)
    # detonator
    px = []
    for row in DETONATOR_ART:
        for ch in row:
            px.append(P.hexc(DETONATOR_PAL[ch]) + (255,) if ch != "." else (0, 0, 0, 0))
    P.write_png(f"{ROOT}/assets/{NS}/textures/item/detonator.png", 16, 16, px)
    write_json(f"{ROOT}/assets/{NS}/models/item/detonator.json",
               {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/detonator"}})
    write_json(f"{ROOT}/assets/{NS}/items/detonator.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/detonator"}})
    write_json(f"{ROOT}/data/{NS}/recipe/detonator.json", {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:redstone", "minecraft:lever", "minecraft:ender_pearl"],
        "result": {"id": f"{NS}:detonator", "count": 1}})
    # Geiger counter
    px = []
    for row in GEIGER_ART:
        for ch in row:
            px.append(P.hexc(GEIGER_PAL[ch]) + (255,) if ch != "." else (0, 0, 0, 0))
    P.write_png(f"{ROOT}/assets/{NS}/textures/item/geiger_counter.png", 16, 16, px)
    write_json(f"{ROOT}/assets/{NS}/models/item/geiger_counter.json",
               {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/geiger_counter"}})
    write_json(f"{ROOT}/assets/{NS}/items/geiger_counter.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/geiger_counter"}})
    write_json(f"{ROOT}/data/{NS}/recipe/geiger_counter.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" G ", "IRI", "ICI"],
        "key": {"G": "minecraft:glass_pane", "I": "minecraft:iron_ingot", "R": "minecraft:redstone", "C": "minecraft:copper_ingot"},
        "result": {"id": f"{NS}:geiger_counter", "count": 1}})
    lang_en["item.tnt_arsenal.geiger_counter"] = "Geiger Counter"
    lang_de["item.tnt_arsenal.geiger_counter"] = "Geigerzähler"
    lang_en["item.tnt_arsenal.geiger_counter.desc"] = "Held in either hand: shows the dose rate and your dose, and ticks"
    lang_de["item.tnt_arsenal.geiger_counter.desc"] = "In einer Hand gehalten: zeigt Dosisleistung und deine Dosis an und knackt"
    lang_en["item.tnt_arsenal.geiger_counter.reading"] = "☢ %s mSv/s · dose %s mSv"
    lang_de["item.tnt_arsenal.geiger_counter.reading"] = "☢ %s mSv/s · Dosis %s mSv"
    for code, extra in (("en_us", lang_en), ("de_de", lang_de)):
        path = f"{ROOT}/assets/{NS}/lang/{code}.json"
        with open(path, encoding="utf-8") as f:
            merged = json.load(f)
        merged.update(extra)
        write_json(path, merged)
    # preview for the README
    w, h = 3 * 150, 160
    sheet = [(0, 0, 0, 0)] * (w * h)
    for i, b in enumerate(BOMBS):
        side, top = faces[b["id"]]
        cube = P.iso(top, side, 4)
        cx = i * 150 + 11
        for y in range(128):
            for x in range(128):
                if cube[y * 128 + x][3]:
                    sheet[(6 + y) * w + cx + x] = cube[y * 128 + x]
        P.text(sheet, w, i * 150 + (150 - P.text_width(b["en"])) // 2, 142, b["en"], (140, 140, 140, 255))
    P.write_png(os.path.join(REPO, "docs", "nuclear.png"), w, h, sheet)
    print("ok")


if __name__ == "__main__":
    main()
