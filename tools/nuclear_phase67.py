"""Assets for the protective gear and the alarms of the nuclear arsenal: iodine
tablets, RadAway, hazmat suit, lead block, timer fuse, siren, bunker door, the
iodine effect icon, the irradiated zombie's name and the advancements.

Called by tools/nuclear_assets.py; writes into the same lang dictionaries.
"""
import json
import random

import nuclear_pixelart as P

TRANSPARENT = (0, 0, 0, 0)


def art(rows, pal):
    out = []
    for row in rows:
        for ch in row:
            out.append(P.hexc(pal[ch]) + (255,) if ch != "." else TRANSPARENT)
    return out


IODINE_ART = [
    "................",
    "................",
    "..kkkkkkkkkkkk..",
    ".kssssssssssssk.",
    ".kswwssswwssssk.",
    ".kwvvwswvvwsssk.",
    ".kwvvwswvvwsssk.",
    ".kswwssswwssssk.",
    ".kssssssssssssk.",
    ".kswwssswwssssk.",
    ".kwvvwswvvwsssk.",
    ".kwvvwswvvwsssk.",
    ".kswwssswwssssk.",
    ".kssssssssssssk.",
    "..kkkkkkkkkkkk..",
    "................",
]
IODINE_PAL = {"k": "#4a4f57", "s": "#c3c9d1", "w": "#eef1f4", "v": "#8e5ab8"}

RADAWAY_ART = [
    "......kkkk......",
    "......kggk......",
    "....kkkkkkkk....",
    "...koooooooook..",
    "...koOOOOOOook..",
    "...koOwwwwwOok..",
    "...koOwbbbwOok..",
    "...koOwwwwwOok..",
    "...koooooooook..",
    "...koooooooook..",
    "...kOooooooOok..",
    "....kOOOOOOOk...",
    ".....kkkkkkk....",
    "........k.......",
    "........k.......",
    "........k.......",
]
RADAWAY_PAL = {"k": "#2c2420", "g": "#9aa0a6", "o": "#d9822b", "O": "#a85a17", "w": "#f2ede4", "b": "#2b6cb0"}

TIMER_ART = [
    "................",
    "....kk....kk....",
    "...krrk..krrk...",
    "..kkkkkkkkkkkk..",
    "..kaaaaaaaaaak..",
    "..kaKKKKKKKKak..",
    "..kaKrrKrrKKak..",
    "..kaKrKKKrKKak..",
    "..kaKrrKrrKKak..",
    "..kaKKKKKKKKak..",
    "..kaaaaaaaaaak..",
    "..kabbaaaaggak..",
    "..kaaaaaaaaaak..",
    "..kkkkkkkkkkkk..",
    "....y......y....",
    "...y........y...",
]
TIMER_PAL = {"k": "#1b1d21", "a": "#6a717b", "K": "#101214", "r": "#ff4a3a", "b": "#3a6fd8", "g": "#3ac16a", "y": "#d8c23a"}

HAZMAT_PAL = {"k": "#2a2416", "y": "#e8c13a", "Y": "#b8901e", "g": "#1e3a40", "G": "#3f7f88", "b": "#2a2a2a"}
HAZMAT_HELMET = [
    "................",
    "................",
    "....kkkkkkkk....",
    "...kyyyyyyyyk...",
    "..kyyyyyyyyyyk..",
    "..kyggggggggyk..",
    "..kygGGGGGGgyk..",
    "..kygGggggGgyk..",
    "..kyggggggggyk..",
    "..kyyyyyyyyyyk..",
    "..kYyybbbbyyYk..",
    "...kYYYYYYYYk...",
    "....kkkkkkkk....",
    "................",
    "................",
    "................",
]
HAZMAT_SUIT = [
    "................",
    "..kkkk....kkkk..",
    ".kyyyyk..kyyyyk.",
    ".kyyyyykkyyyyyk.",
    ".kyyyyyyyyyyyyk.",
    ".kkkyyybbyyykkk.",
    "...kyyybbyyyk...",
    "...kyyyyyyyyk...",
    "...kyykkkkyyk...",
    "...kyykbbkyyk...",
    "...kyyyyyyyyk...",
    "...kyyyyyyyyk...",
    "...kYYYYYYYYk...",
    "...kkkkkkkkkk...",
    "................",
    "................",
]
HAZMAT_LEGS = [
    "................",
    "...kkkkkkkkkk...",
    "...kbbbbbbbbk...",
    "...kyyyyyyyyk...",
    "...kyyyyyyyyk...",
    "...kyyykkyyyk...",
    "...kyyyk.kyyk...",
    "...kyyyk.kyyk...",
    "...kyyyk.kyyk...",
    "...kyyyk.kyyk...",
    "...kyyyk.kyyk...",
    "...kYYYk.kYYk...",
    "...kkkkk.kkkk...",
    "................",
    "................",
    "................",
]
HAZMAT_BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...kkkk..kkkk...",
    "...kyyk..kyyk...",
    "...kyyk..kyyk...",
    "...kyyk..kyyk...",
    "...kyyk..kyyk...",
    "..kbbbk..kbbbk..",
    ".kbbbbk..kbbbbk.",
    ".kkkkkk..kkkkkk.",
    "................",
    "................",
    "................",
]

DOOR_ICON = [
    "....kkkkkkkk....",
    "....kaaaaaak....",
    "....kaGGGGak....",
    "....kaGGGGak....",
    "....kaaaaaak....",
    "....kaaaaaak....",
    "....kaaaaaak....",
    "....kaaaaRak....",
    "....kaaaaaak....",
    "....kaaaaaak....",
    "....kyykyykk....",
    "....kkyykyyk....",
    "....kyykyykk....",
    "....kaaaaaak....",
    "....kaaaaaak....",
    "....kkkkkkkk....",
]
DOOR_PAL = {"k": "#1f2226", "a": "#5d646d", "G": "#2f4f55", "R": "#c8a033", "y": "#e2b72c"}


def door_halves():
    """Bottom and top texture of the bunker door: riveted steel, a round window,
    a wheel lock and a hazard band at the foot."""
    edge, steel, dark, rivet = P.hexc("#1f2226"), P.hexc("#5d646d"), P.hexc("#4a5058"), P.hexc("#8a929c")
    glass, wheel, yel, blk = P.hexc("#2f4f55"), P.hexc("#c8a033"), P.hexc("#e2b72c"), P.hexc("#1f2226")
    top, bottom = P.Canvas(), P.Canvas()
    for c in (top, bottom):
        for y in range(16):
            for x in range(16):
                c.set(x, y, edge if x in (0, 15) or y in (0, 15) else (steel if (x + y) % 7 else dark))
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            c.set(x, y, rivet)
    top.set(0, 15, edge)
    for y in range(4, 11):
        for x in range(4, 12):
            if (x - 7.5) ** 2 + (y - 7) ** 2 <= 10:
                top.set(x, y, glass)
    for x in range(5, 11):
        bottom.set(x, 3, wheel)
        bottom.set(x, 8, wheel)
    for y in range(3, 9):
        bottom.set(5, y, wheel)
        bottom.set(10, y, wheel)
    bottom.set(7, 5, wheel)
    bottom.set(8, 6, wheel)
    for y in range(11, 15):
        for x in range(1, 15):
            bottom.set(x, y, yel if (x + y) % 4 < 2 else blk)
    return top, bottom


def siren_faces():
    side, top = P.Canvas(), P.Canvas()
    red, dark, grill, metal = P.hexc("#b3261e"), P.hexc("#6e1612"), P.hexc("#2a2a2a"), P.hexc("#8a929c")
    for y in range(16):
        for x in range(16):
            side.set(x, y, metal if y in (0, 15) else (red if x % 3 else dark))
            top.set(x, y, metal)
    for y in range(3, 13):
        for x in range(3, 13):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 22:
                top.set(x, y, grill if (x + y) % 2 else dark)
    return side, top


def hazmat_layer(leggings):
    """A 64x32 armour layer in the humanoid layout: yellow rubberised fabric,
    darker seams, a dark visor on the helmet and black boots."""
    w, h = 64, 32
    px = [TRANSPARENT] * (w * h)
    yel, seam, visor, rubber = P.hexc("#e8c13a"), P.hexc("#b8901e"), P.hexc("#1e3a40"), P.hexc("#2a2a2a")

    def fill(x0, y0, x1, y1, colour):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[y * w + x] = colour + (255,)

    if not leggings:
        fill(0, 0, 32, 16, yel)                 # head
        fill(8, 10, 16, 14, visor)              # visor on the face
        fill(16, 16, 40, 32, yel)               # body
        fill(40, 16, 56, 32, yel)               # arms
        fill(0, 16, 16, 32, yel)                # legs, as boots
        fill(0, 27, 16, 32, rubber)
        for x in range(16, 40, 4):
            fill(x, 16, x + 1, 32, seam)
    else:
        fill(16, 16, 40, 32, yel)               # waist
        fill(0, 16, 16, 32, yel)                # legs
        fill(20, 20, 28, 22, rubber)            # belt
        for x in range(0, 16, 4):
            fill(x, 16, x + 1, 32, seam)
    return px


def iodine_icon():
    c = [TRANSPARENT] * (18 * 18)
    purple, light, edge = P.hexc("#8e5ab8"), P.hexc("#c9a0dc"), P.hexc("#4a2a66")
    for y in range(18):
        for x in range(18):
            d = (x - 8.5) ** 2 + (y - 8.5) ** 2
            if d <= 49:
                c[y * 18 + x] = (edge if d > 36 else (light if x < y else purple)) + (255,)
    return c


def door_blockstate(ns):
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, rot in base.items():
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    model = f"{ns}:block/bunker_door_{'bottom' if half == 'lower' else 'top'}_{hinge}" + ("_open" if opened else "")
                    y = (rot + ((90 if hinge == "left" else 270) if opened else 0)) % 360
                    v = {"model": model}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},half={half},hinge={hinge},open={str(opened).lower()}"] = v
    return {"variants": variants}


def generate(root, ns, write_json, lang_en, lang_de):
    tex = f"{root}/assets/{ns}/textures"
    simple_items = {
        "iodine_tablets": (IODINE_ART, IODINE_PAL),
        "rad_away": (RADAWAY_ART, RADAWAY_PAL),
        "timer": (TIMER_ART, TIMER_PAL),
        "hazmat_helmet": (HAZMAT_HELMET, HAZMAT_PAL),
        "hazmat_suit": (HAZMAT_SUIT, HAZMAT_PAL),
        "hazmat_leggings": (HAZMAT_LEGS, HAZMAT_PAL),
        "hazmat_boots": (HAZMAT_BOOTS, HAZMAT_PAL),
        "bunker_door": (DOOR_ICON, DOOR_PAL),
    }
    for name, (rows, pal) in simple_items.items():
        P.write_png(f"{tex}/item/{name}.png", 16, 16, art(rows, pal))
        write_json(f"{root}/assets/{ns}/models/item/{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{ns}:item/{name}"}})
        write_json(f"{root}/assets/{ns}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{ns}:item/{name}"}})

    # hazmat suit as worn
    write_json(f"{root}/assets/{ns}/equipment/hazmat.json", {"layers": {
        "humanoid": [{"texture": f"{ns}:hazmat"}], "humanoid_leggings": [{"texture": f"{ns}:hazmat"}]}})
    P.write_png(f"{tex}/entity/equipment/humanoid/hazmat.png", 64, 32, hazmat_layer(False))
    P.write_png(f"{tex}/entity/equipment/humanoid_leggings/hazmat.png", 64, 32, hazmat_layer(True))
    write_json(f"{root}/data/{ns}/tags/item/repairs_hazmat_suit.json", {"values": ["minecraft:leather"]})

    # iodine effect
    P.write_png(f"{tex}/mob_effect/iodine.png", 18, 18, iodine_icon())

    # lead block
    lead = P.Canvas()
    rnd = random.Random("lead")
    shades = [P.hexc(c) for c in ("#5b6370", "#646d7b", "#545b67", "#6d7684")]
    for y in range(16):
        for x in range(16):
            lead.set(x, y, P.hexc("#3d434c") if x in (0, 15) or y in (0, 15) else rnd.choice(shades))
    P.write_png(f"{tex}/block/lead_block.png", 16, 16, lead.rgba())
    write_json(f"{root}/assets/{ns}/models/block/lead_block.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{ns}:block/lead_block"}})
    write_json(f"{root}/assets/{ns}/blockstates/lead_block.json", {"variants": {"": {"model": f"{ns}:block/lead_block"}}})
    write_json(f"{root}/assets/{ns}/items/lead_block.json", {"model": {"type": "minecraft:model", "model": f"{ns}:block/lead_block"}})

    # siren
    side, top = siren_faces()
    P.write_png(f"{tex}/block/siren_side.png", 16, 16, side.rgba())
    P.write_png(f"{tex}/block/siren_top.png", 16, 16, top.rgba())
    write_json(f"{root}/assets/{ns}/models/block/siren.json", {"parent": "minecraft:block/cube_column", "textures": {
        "side": f"{ns}:block/siren_side", "end": f"{ns}:block/siren_top"}})
    write_json(f"{root}/assets/{ns}/blockstates/siren.json", {"variants": {
        "powered=false": {"model": f"{ns}:block/siren"}, "powered=true": {"model": f"{ns}:block/siren"}}})
    write_json(f"{root}/assets/{ns}/items/siren.json", {"model": {"type": "minecraft:model", "model": f"{ns}:block/siren"}})

    # bunker door
    dtop, dbottom = door_halves()
    P.write_png(f"{tex}/block/bunker_door_top.png", 16, 16, dtop.rgba())
    P.write_png(f"{tex}/block/bunker_door_bottom.png", 16, 16, dbottom.rgba())
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for suffix in ("", "_open"):
                write_json(f"{root}/assets/{ns}/models/block/bunker_door_{half}_{hinge}{suffix}.json", {
                    "parent": f"minecraft:block/door_{half}_{hinge}{suffix}",
                    "textures": {"bottom": f"{ns}:block/bunker_door_bottom", "top": f"{ns}:block/bunker_door_top"}})
    write_json(f"{root}/assets/{ns}/blockstates/bunker_door.json", door_blockstate(ns))

    # loot
    for name in ("lead_block", "siren"):
        write_json(f"{root}/data/{ns}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{ns}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": f"{ns}:blocks/{name}"})
    write_json(f"{root}/data/{ns}/loot_table/blocks/bunker_door.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{ns}:bunker_door", "conditions": [{
            "condition": "minecraft:block_state_property", "block": f"{ns}:bunker_door", "properties": {"half": "lower"}}]}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": f"{ns}:blocks/bunker_door"})

    # recipes
    def shaped(name, pattern, key, count=1, category="misc"):
        write_json(f"{root}/data/{ns}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category,
                   "pattern": pattern, "key": key, "result": {"id": f"{ns}:{name}", "count": count}})

    def shapeless(name, ingredients, count=1, category="misc"):
        write_json(f"{root}/data/{ns}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": category,
                   "ingredients": ingredients, "result": {"id": f"{ns}:{name}", "count": count}})

    shapeless("iodine_tablets", ["minecraft:dried_kelp", "minecraft:dried_kelp", "minecraft:sugar", "minecraft:paper"], 4)
    shapeless("rad_away", ["minecraft:glass_bottle", "minecraft:ghast_tear", "minecraft:sugar", "minecraft:dried_kelp"])
    shapeless("timer", ["minecraft:clock", "minecraft:redstone", "minecraft:iron_ingot", "minecraft:string"], category="redstone")
    shaped("lead_block", ["III", "IOI", "III"], {"I": "minecraft:iron_ingot", "O": "minecraft:obsidian"}, 4, "building")
    shaped("siren", [" N ", "IRI", "III"], {"N": "minecraft:note_block", "R": "minecraft:redstone", "I": "minecraft:iron_ingot"},
           category="redstone")
    shaped("bunker_door", ["LL", "LL", "LL"], {"L": f"{ns}:lead_block"}, 3, "redstone")
    leather, dye, pane = "minecraft:leather", "minecraft:yellow_dye", "minecraft:glass_pane"
    shaped("hazmat_helmet", ["LYL", "LGL"], {"L": leather, "Y": dye, "G": pane}, category="equipment")
    shaped("hazmat_suit", ["L L", "LYL", "LLL"], {"L": leather, "Y": dye}, category="equipment")
    shaped("hazmat_leggings", ["LYL", "L L", "L L"], {"L": leather, "Y": dye}, category="equipment")
    shaped("hazmat_boots", ["Y Y", "L L"], {"L": leather, "Y": dye}, category="equipment")

    # advancements
    adv = f"{root}/data/{ns}/advancement/nuclear"

    def advancement(name, icon, parent, criteria, frame="task", hidden=False, requirements=None):
        display = {"icon": {"id": icon}, "title": {"translate": f"advancements.{ns}.nuclear.{name}.title"},
                   "description": {"translate": f"advancements.{ns}.nuclear.{name}.description"}, "frame": frame}
        if hidden:
            display["hidden"] = True
        if parent is None:
            display["background"] = "minecraft:gui/advancements/backgrounds/stone"
            display["announce_to_chat"] = False
            display["show_toast"] = False
        out = {"criteria": criteria, "display": display}
        if parent:
            out["parent"] = f"{ns}:nuclear/{parent}"
        if requirements:
            out["requirements"] = requirements
        write_json(f"{adv}/{name}.json", out)

    def has(*items):
        return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": i} for i in items]}}

    impossible = {"done": {"trigger": "minecraft:impossible"}}
    # any of the three bombs opens the tab
    advancement("root", f"{ns}:tsar_bomba", None, {"mini": has(f"{ns}:mini_nuke"), "nuke": has(f"{ns}:nuke"),
                "tsar": has(f"{ns}:tsar_bomba")}, requirements=[["mini", "nuke", "tsar"]])
    advancement("geiger", f"{ns}:geiger_counter", "root", {"geiger": has(f"{ns}:geiger_counter")})
    advancement("iodine", f"{ns}:iodine_tablets", "geiger", {"eat": {"trigger": "minecraft:consume_item",
                "conditions": {"item": {"items": f"{ns}:iodine_tablets"}}}})
    advancement("hazmat", f"{ns}:hazmat_helmet", "geiger", {"suit": has(f"{ns}:hazmat_helmet", f"{ns}:hazmat_suit",
                f"{ns}:hazmat_leggings", f"{ns}:hazmat_boots")}, "goal")
    advancement("bunker", f"{ns}:bunker_door", "root", {"door": has(f"{ns}:bunker_door")})
    advancement("destroyer", f"{ns}:tsar_bomba", "root", impossible, "challenge")
    advancement("survivor", f"{ns}:rad_away", "geiger", impossible, "goal")
    advancement("glowing", f"{ns}:fallout", "survivor", impossible, "challenge", hidden=True)

    # tags
    pick = f"{root}/data/minecraft/tags/block/mineable/pickaxe.json"
    with open(pick, encoding="utf-8") as f:
        current = json.load(f)
    for name in ("lead_block", "siren", "bunker_door"):
        if f"{ns}:{name}" not in current["values"]:
            current["values"].append(f"{ns}:{name}")
    write_json(pick, current)

    t = {
        "item.{ns}.iodine_tablets": ("Iodine Tablets", "Jodtabletten"),
        "item.{ns}.rad_away": ("RadAway", "RadAway"),
        "item.{ns}.timer": ("Timer Fuse", "Zeitzünder"),
        "item.{ns}.timer.desc": ("Sneak + right-click: set the time. Right-click a bomb: start", "Schleichen + Rechtsklick: Zeit stellen. Rechtsklick auf eine Bombe: starten"),
        "item.{ns}.timer.set": ("Timer: %s s", "Zeitzünder: %s s"),
        "item.{ns}.timer.started": ("Countdown started: %s s", "Countdown gestartet: %s s"),
        "item.{ns}.timer.countdown": ("☢ Detonation in %s s", "☢ Zündung in %s s"),
        "item.{ns}.timer.gone": ("Timer fuse: the bomb is gone", "Zeitzünder: Die Bombe ist weg"),
        "item.{ns}.hazmat_helmet": ("Hazmat Hood", "Strahlenschutz-Haube"),
        "item.{ns}.hazmat_suit": ("Hazmat Suit", "Strahlenschutzanzug"),
        "item.{ns}.hazmat_leggings": ("Hazmat Trousers", "Strahlenschutz-Hose"),
        "item.{ns}.hazmat_boots": ("Hazmat Boots", "Strahlenschutz-Stiefel"),
        "block.{ns}.lead_block": ("Lead Block", "Bleiblock"),
        "block.{ns}.siren": ("Siren", "Sirene"),
        "block.{ns}.bunker_door": ("Bunker Door", "Bunkertür"),
        "effect.{ns}.iodine": ("Iodine", "Jod"),
        "entity.{ns}.irradiated_zombie": ("Irradiated Zombie", "Verstrahlter Zombie"),
        "{ns}.emp": ("EMP! Electronics knocked out", "EMP! Elektronik ausgefallen"),
        "advancements.{ns}.nuclear.root.title": ("TNT Arsenal: Nuclear", "TNT-Arsenal: Atom"),
        "advancements.{ns}.nuclear.root.description": ("Get hold of a nuclear bomb", "Halte eine Atombombe in den Händen"),
        "advancements.{ns}.nuclear.geiger.title": ("Tick, Tick, Tick", "Tick, Tick, Tick"),
        "advancements.{ns}.nuclear.geiger.description": ("Get a Geiger counter", "Besorge dir einen Geigerzähler"),
        "advancements.{ns}.nuclear.iodine.title": ("Better Safe Than Sorry", "Sicher ist sicher"),
        "advancements.{ns}.nuclear.iodine.description": ("Take iodine tablets", "Nimm Jodtabletten"),
        "advancements.{ns}.nuclear.hazmat.title": ("Dressed for the Apocalypse", "Angezogen für die Apokalypse"),
        "advancements.{ns}.nuclear.hazmat.description": ("Have the whole hazmat suit", "Besitze den ganzen Strahlenschutzanzug"),
        "advancements.{ns}.nuclear.bunker.title": ("Duck and Cover", "Ducken und Deckung"),
        "advancements.{ns}.nuclear.bunker.description": ("Get a bunker door", "Besorge dir eine Bunkertür"),
        "advancements.{ns}.nuclear.destroyer.title": ("Now I Am Become Death", "Jetzt bin ich der Tod"),
        "advancements.{ns}.nuclear.destroyer.description": ("Witness a tsar bomba go off", "Erlebe die Zündung einer Zar-Bombe"),
        "advancements.{ns}.nuclear.survivor.title": ("Survivor", "Überlebender"),
        "advancements.{ns}.nuclear.survivor.description": ("Take in 500 mSv and live", "Nimm 500 mSv auf und überlebe"),
        "advancements.{ns}.nuclear.glowing.title": ("Glowing", "Glühend"),
        "advancements.{ns}.nuclear.glowing.description": ("Carry a dose of 1000 mSv", "Trage eine Dosis von 1000 mSv"),
    }
    for key, (en, de) in t.items():
        lang_en[key.format(ns=ns)] = en
        lang_de[key.format(ns=ns)] = de
