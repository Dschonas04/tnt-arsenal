#!/usr/bin/env python3
"""Generates every asset of TNT Arsenal: block textures, models, blockstates, item
definitions, translations, loot tables, recipes, the mod icon and the preview sheet
in docs/. Pure Python 3 (zlib + struct), no image library needed.

    python3 tools/textures.py

All textures are original 16x16 pixel art in the spirit of vanilla TNT: bundled
sticks with a paper label. Every kind gets its own stick material, strap, label
colour, hand-drawn 8x8 emblem and surface pattern; colours are shaded along
hue-shifted ramps (shadows lean blue, highlights lean yellow).
"""
import colorsys
import json
import os
import random
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
ROOT = os.path.join(REPO, "src", "main", "resources")
NS = "tnt_arsenal"

# --- images -------------------------------------------------------------------


def write_png(path, w, h, px):
    """px: list of (r, g, b, a) rows-first."""
    raw = b"".join(b"\x00" + bytes(c for p in px[y * w:(y + 1) * w] for c in p) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def mul(c, f):
    return tuple(clamp(v * f) for v in c)


def shift(c, dl, toward_hue, dh, ds=0.0):
    """Lightness shift with the hue nudged toward a target (pixel-art ramp)."""
    h, l, s = colorsys.rgb_to_hls(*(v / 255 for v in c))
    diff = ((toward_hue - h + 0.5) % 1.0) - 0.5
    h = (h + max(-dh, min(dh, diff))) % 1.0
    l = max(0.0, min(1.0, l + dl))
    s = max(0.0, min(1.0, s + ds))
    return tuple(clamp(v * 255) for v in colorsys.hls_to_rgb(h, l, s))


BLUE, YELLOW = 0.66, 0.15


def ramp(c):
    """Five tones from deep shadow to highlight."""
    c = hexc(c) if isinstance(c, str) else c
    return [
        shift(c, -0.24, BLUE, 0.05, 0.05),
        shift(c, -0.11, BLUE, 0.025, 0.03),
        c,
        shift(c, 0.08, YELLOW, 0.025, -0.02),
        shift(c, 0.17, YELLOW, 0.05, -0.05),
    ]


class Canvas:
    def __init__(self, w=16, h=16, fill=(0, 0, 0)):
        self.w, self.h = w, h
        self.px = [[fill for _ in range(w)] for _ in range(h)]

    def get(self, x, y):
        return self.px[y][x]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c[:3]

    def rgba(self):
        return [p + (255,) for row in self.px for p in row]


# --- emblems (8x8) ------------------------------------------------------------
# '.' leaves the label showing; every other letter is looked up in the kind's palette.

ICONS = {
    "bomb": ["......cc", ".....o.c", "...oo...", "..oaao..", ".oabaao.", ".oaaaao.", ".oaaddo.", "..oooo.."],
    "burst": ["...c....", ".c.ac.c.", "..caac..", "caabbaac", ".caabac.", "..caac..", ".c.ca.c.", "....c..."],
    "trefoil": ["aaa..aaa", "aaa..aaa", ".aa..aa.", "...aa...", "...aa...", "........", "..aaaa..", ".aaaaaa."],
    "flame": ["...c....", "...cc...", "..ccc.c.", ".cccacc.", ".ccaacc.", "ccaabacc", "ccabbacc", ".cabbac."],
    "bolt": ["....aaa.", "...aaa..", "..aaa...", ".aaaaaa.", "....aaa.", "...aaa..", "..aa....", ".a......"],
    "cluster": ["oo...oo.", "ab...ab.", "........", "...oo...", "...ab...", "........", "oo...oo.", "ab...ab."],
    "drill": ["oaaaaaao", "oaabbaao", ".oaaaao.", ".obaaao.", "..oaao..", "..obao..", "...oo...", "...oo..."],
    "arch": ["..oooo..", ".oaaaao.", "oaaddaao", "oaddddao", "oaddddao", "oaddddao", "oaddddao", "oooddooo"],
    "shovel": ["......ob", ".....obo", "....obo.", "...obo..", ".ccbo...", "cccco...", "cccc....", ".cc....."],
    "gem": ["..oooo..", ".oabbao.", "oaabbaao", "oaaaaaao", ".oaaaao.", "..oaao..", "...oo...", "........"],
    "drop": ["...o....", "...oo...", "..oaao..", ".oabaao.", "oabaaaao", "oaaaaaao", ".oaaaao.", "..oooo.."],
    "volcano": ["c..c..c.", ".c.cc.c.", "...cc...", "..ocao..", "..oaao..", ".oaaado.", ".oaaddo.", "oaaaaddo"],
    "flake": ["...a...", ".a.a.a.", "..aba..", "aabbbaa", "..aba..", ".a.a.a.", "...a..."],
    "skull": ["..aaaa..", ".aaaaaa.", "aaaaaaaa", "addaadda", "addaadda", ".aaddaa.", "..aaaa..", "..a.a.a."],
    "pull": ["a......a", ".a....a.", "..a..a..", "...bb...", "...bb...", "..a..a..", ".a....a.", "a......a"],
    "eye": ["..oooo..", ".oaaaao.", "oaabbaao", "oabddbao", "oabddbao", "oaabbaao", ".oaaaao.", "..oooo.."],
    "spring": ["...aa...", "..aaaa..", ".aaaaaa.", "...aa...", "cccccc..", "..cccccc", "cccccc..", "..cccccc"],
    "sparkle": ["c..a..b.", ".c.a.b..", "..cab...", "aaawaaa.", "..bac...", ".b.a.c..", "b..a..c."],
    "heart": [".aa..aa.", "abaaaaaa", "abaaaaaa", "aaaaaaaa", ".aaaaaa.", "..aaaa..", "...aa..."],
    "hole": ["..cbbc..", ".cddddc.", "cddooddc", "bdoooodb", "bdoooodb", "cddooddc", ".cddddc.", "..cbbc.."],
    "rise": ["...aa...", "..aaaa..", ".aa..aa.", "...aa...", "..aaaa..", ".aa..aa.", "........", "cccccccc"],
    "bridge": ["b.b.b.b.", "b.b.b.b.", "aaaaaaaa", "dddddddd", "ad....da", "a......a", "a......a", "d......d"],
    "castle": ["aa.aa.aa", "aaaaaaaa", "adaaadaa", "dddddddd", "aaadaaad", "dddddddd", "adaaadaa", "aaaaaaaa"],
    "zombie": ["cccccccc", "caaaaaac", "aaaaaaaa", "addaadda", "aaaaaaaa", "aaaddaaa", "aaddddaa", "aaaaaaaa"],
    "waves": [".....a..", "..a...a.", "...a..a.", "bb.a...a", "bb.a...a", "...a..a.", "..a...a.", ".....a.."],
    "cloud": ["..aaa...", ".abbba..", ".abbbaaa", "aabbbbba", "abbbbbba", ".aaaaaa."],
    "lens": [".oooo...", "obbaao..", "obaaao..", "oaaaao..", ".oooo...", ".....c..", "......c.", ".......c"],
    "tree": ["..aaaa..", ".abbbba.", "abbbbbba", "abbbbbba", ".abbbba.", "...cc...", "...cc...", ".dddddd."],
    "void": ["..aaaa.a", ".a....a.", "a....a.a", "a...a..a", "a..a...a", "a.a....a", ".a....a.", "a.aaaa.."],
    "portal": [".oooooo.", ".oaabao.", ".oabaao.", ".oaaabo.", ".obaaao.", ".oaabao.", ".oabaao.", ".oooooo."],
    "comet": ["c.......", ".cc.....", "..ccc...", "...ccoo.", "....oabo", "....oaao", ".....oo."],
    "arrows": ["b.b..b.b", ".a....a.", ".a....a.", ".a....a.", ".a....a.", ".a....a.", "ccc..ccc", ".c....c."],
    "web": ["a..a..a", ".aaaaa.", ".a.a.a.", "aaa.aaa", ".a.a.a.", ".aaaaa.", "a..a..a"],
    "storm": ["..aaa...", ".abbbaa.", "abbbbbba", ".aacaaa.", "...cc...", "..cc....", "...cc...", "..c....."],
    "magnet": [".aaaaaa.", "abbbbbba", "ab....ba", "aa....aa", "aa....aa", "aa....aa", "cc....cc", "cc....cc"],
    "sponge": ["oooooooo", "oaadaaao", "oaaaadao", "odaaaaao", "oaaadaao", "oadaaaao", "oaaaadao", "oooooooo"],
    "torch": ["...cc...", "..cbbc..", "...bb...", "...aa...", "...aa...", "...aa...", "...aa...", "...aa..."],
    "push": [".a....a.", "aa....aa", "aaabbaaa", "aaabbaaa", "aa....aa", ".a....a."],
    "dome": ["..aaaa..", ".abbcba.", "abbbbcba", "abbbbbba", "abbbbbba", "dddddddd"],
    "chain": ["aaa....", "a.a....", "aabbb..", "..b.b..", "..bbaaa", "....a.a", "....aaa"],
    "log": ["..aaaa..", ".abbbba.", "abccccba", "abcbbcba", "abcbbcba", "abccccba", ".abbbba.", "..aaaa.."],
    "wheat": ["b..b..b", "bb.b.bb", ".bbbbb.", "..bbb..", "..cac..", "..aaa..", ".a.a.a.", "a..a..a"],
    "sculk": ["a.a..a.a", ".a.aa.a.", "..abba..", ".abccba.", ".abccba.", "..abba..", ".a.aa.a.", "a.a..a.a"],
    "cactus": ["...ab...", "...ab...", "a..ab...", "ab.ab..a", ".aaab.ab", "...abaa.", "...ab...", "cccccccc"],
    "island": ["......b.", ".....bbb", "......b.", "..aaaa..", ".acaaaa.", "aaaacaaa", ".aaaaaa.", "...aa..."],
    "rainbow": ["..aaaa..", ".aabbaa.", "aabccbaa", "abccccba", "abc..cba", "ab....ba"],
    "quake": [".c....c.", "...c....", "aaaadaaa", "aaadaaaa", "aaaadaaa", "aadaaaaa", "aaadaaaa", "aaaadaaa"],
    "eruption": [".c.bb.c.", "..cbbc..", "...bb...", "...oo...", "..oaao..", ".oaaado.", "oaaaaddo", "oaaaaddo"],
    "stairs": ["......bb", "......aa", "....bbaa", "....aaaa", "..bbaaaa", "..aaaaaa", "bbaaaaaa", "aaaaaaaa"],
    "bunker": ["cccccccc", "dddddddd", "daaaaaad", "da....ad", "da.b..ad", "da....ad", "daaaaaad", "dddddddd"],
    "platform": ["...cc...", "...cc...", "........", "aaaaaaaa", "bbbbbbbb", ".d....d.", ".d....d.", ".d....d."],
    "tower": ["a.aa.a", "aaaaaa", ".abba.", ".abba.", ".aaaa.", ".adda.", ".adda.", ".aaaa."],
    "broken": [".aa..aa.", "aaaa.aaa", "aaa.aaaa", "aaaa.aaa", ".aa.aaa.", "..aa.a..", "...aa..."],
    "sun": ["...a....", ".a.a.a..", "..bbb...", "aabbbaa.", "..bbb...", ".a.a.a..", "...a...."],
    "hourglass": ["aaaaaaa", ".abbba.", "..aba..", "...a...", "..a.a..", ".a.b.a.", "aaaaaaa"],
    "bee": ["..bb.bb.", "..bbbbb.", ".adadad.", "dadadadc", "dadadad.", ".adadad."],
    "wolf": ["a......a", "aa....aa", "aaaaaaaa", "adaaaada", "aaaaaaaa", ".aabbaa.", "..adda..", "..cccc.."],
    "golem": [".aaaaaa.", ".avbbba.", ".adbbda.", ".abccba.", ".vbccba.", ".abbbva.", "..aaaa.."],
    "rocket": ["...a....", "..aaa...", "..aba...", "..aaa...", "..aaa...", ".caaac..", ".c.c.c..", "..c.c..."],
    "pumpkin": ["...v....", "..aaaa..", ".aaaaaa.", "adaadada", "aaaaaaaa", "adaddaad", ".adaada.", "..aaaa.."],
}

for name, rows in ICONS.items():
    assert all(len(r) == len(rows[0]) for r in rows), name

# --- kinds --------------------------------------------------------------------
# id, English name, German name, English line, German line,
# stick colour, strap style, label colour, emblem, emblem palette, pattern, recipe

K = []


def kind(kid, en, de, desc_en, desc_de, body, strap, label, icon, pal, pattern, recipe, pattern_colours=None):
    K.append(dict(id=kid, en=en, de=de, desc_en=desc_en, desc_de=desc_de, body=body, strap=strap, label=label,
                  icon=icon, pal=pal, pattern=pattern, pcol=pattern_colours or [], recipe=recipe))


T = "minecraft:tnt"

kind("mega_tnt", "Mega TNT", "Mega-TNT", "Twice the power of TNT", "Doppelte Sprengkraft",
     "#c8321f", ("rope",), "#f1e6c8", "bomb", {"a": "#2b2b33", "b": "#77778a", "d": "#17171c", "o": "#0c0c10", "c": "#ffb300"},
     None, [T] * 4)
kind("giga_tnt", "Giga TNT", "Giga-TNT", "Four times the power of TNT", "Vierfache Sprengkraft",
     "#8d1a1a", ("metal", "#8a8f96"), "#f1e6c8", "burst", {"a": "#ff7a1a", "b": "#fff2a8", "c": "#c81e1e"},
     "rivets", ["tnt_arsenal:mega_tnt"] * 4 + ["minecraft:blaze_powder"])
kind("nuke_tnt", "Nuke TNT", "Atom-TNT", "Heat flash, a crater 110 wide, blast wave, mushroom cloud and five minutes of fallout", "Hitzeblitz, 110 Blöcke breiter Krater, Druckwelle, Atompilz und fünf Minuten Fallout",
     "#4a5320", ("hazard",), "#f2c81b", "trefoil", {"a": "#1a1a1a"},
     "rivets", ["tnt_arsenal:giga_tnt"] * 4 + ["minecraft:nether_star"])
kind("fire_tnt", "Fire TNT", "Feuer-TNT", "Sets everything around alight", "Setzt die Umgebung in Brand",
     "#d9501a", ("metal", "#3a2a22"), "#fff1cf", "flame", {"c": "#d42a0c", "a": "#ff8a1a", "b": "#ffe066"},
     "embers", [T, "minecraft:fire_charge"], ["#ffcc33", "#ff7a1a"])
kind("lightning_tnt", "Lightning TNT", "Blitz-TNT", "A ring of lightning", "Ein Ring aus Blitzen",
     "#2a3a8c", ("metal", "#b8683c"), "#1b2440", "bolt", {"a": "#ffe14d"},
     "stars", [T, "minecraft:lightning_rod"], ["#9fe8ff", "#ffffff"])
kind("cluster_tnt", "Cluster TNT", "Cluster-TNT", "Bursts into eight TNT", "Zerplatzt in acht TNT",
     "#b5452a", ("rope",), "#efe3cb", "cluster", {"a": "#2b2b33", "b": "#8a8a9a", "o": "#c83a1f"},
     None, [T] * 3 + ["minecraft:gunpowder"] * 2)
kind("drill_tnt", "Drill TNT", "Bohr-TNT", "A clean 3×3 shaft up to 64 deep, with a ladder", "Sauberer 3×3-Schacht bis 64 tief, mit Leiter",
     "#5d6870", ("metal", "#3a4248"), "#e6e2d9", "drill", {"a": "#9aa4ab", "b": "#e6eef2", "o": "#2a3136"},
     "rivets", [T, "minecraft:iron_pickaxe"])
kind("tunnel_tnt", "Tunnel TNT", "Tunnel-TNT", "A clean 3×3 tunnel, 48 long, with torches", "Sauberer 3×3-Tunnel, 48 lang, mit Fackeln",
     "#6b4d30", ("metal", "#5a5f66"), "#ecdcbd", "arch", {"a": "#8e9296", "o": "#3b3f44", "d": "#1a1a1a"},
     "wood", [T, T, "minecraft:rail"])
kind("leveler_tnt", "Leveler TNT", "Planier-TNT", "Clears a plot: radius 10, 16 high", "Räumt eine Fläche frei: Radius 10, 16 hoch",
     "#4f7a2c", ("rope",), "#eef2d9", "shovel", {"b": "#8a5a2b", "o": "#2d1d0e", "c": "#aeb7bd"},
     "moss", [T, "minecraft:iron_shovel"], ["#6f9a3c", "#3b5e20"])
kind("miner_tnt", "Miner TNT", "Bergbau-TNT", "Removes stone and dirt, leaves the ores", "Entfernt Stein und Erde, lässt die Erze stehen",
     "#3d3d45", ("metal", "#7a7f86"), "#d9d6cf", "gem", {"a": "#3ec6d6", "b": "#c8f6fb", "o": "#135a66"},
     "ore", [T, "minecraft:stone_pickaxe", "minecraft:redstone"])
kind("water_tnt", "Water TNT", "Wasser-TNT", "Floods its crater", "Flutet den Krater",
     "#1f6fb5", ("metal", "#8a9aa6"), "#dff0fb", "drop", {"a": "#2f8be0", "b": "#d6f0ff", "o": "#0d3f73"},
     "drip", [T, "minecraft:water_bucket"], ["#7cc4ff", "#4aa3f0"])
kind("lava_tnt", "Lava TNT", "Lava-TNT", "Fills its crater with lava", "Füllt den Krater mit Lava",
     "#3a2a26", ("metal", "#2a2422"), "#ffe0b3", "drop", {"a": "#ff7a00", "b": "#ffd23a", "o": "#8a2400"},
     "cracks", [T, "minecraft:lava_bucket"], ["#ffd23a", "#ff7a00", "#c23a00"])
kind("frost_tnt", "Frost TNT", "Frost-TNT", "Freezes water, lava and creatures", "Friert Wasser, Lava und Lebewesen ein",
     "#6fb8de", ("metal", "#4a86a8"), "#eef8ff", "flake", {"a": "#3a8fc0", "b": "#a8e2ff"},
     "frost", [T, "minecraft:packed_ice", "minecraft:snowball"], ["#ffffff", "#e2f6ff"])
kind("poison_tnt", "Poison TNT", "Gift-TNT", "Leaves a cloud of poison", "Hinterlässt eine Giftwolke",
     "#4e8a2a", ("rope", "#5a4a2a"), "#d8efc0", "skull", {"a": "#f2f2e4", "d": "#1f3d10"},
     "drip", [T, "minecraft:fermented_spider_eye"], ["#b4ff4a", "#7ad62a"])
kind("gravity_tnt", "Gravity TNT", "Schwerkraft-TNT", "Tears the ground loose, whirls it up and hurls it away", "Reißt Blöcke los, wirbelt sie hoch und schleudert sie weg",
     "#4b2a6b", ("metal", "#8a8f96"), "#e6daf2", "pull", {"a": "#3a1a5a", "b": "#b06cff"},
     "stars", [T, "minecraft:iron_block"], ["#c9a0ff", "#8a5ad6"])
kind("ender_tnt", "Ender TNT", "Ender-TNT", "Scatters everyone nearby", "Teleportiert alle in der Nähe weg",
     "#133f38", ("metal", "#c9a227"), "#c9f2e6", "eye", {"a": "#2aa58a", "b": "#8ef0d4", "d": "#0b0b0b", "o": "#0b2a24"},
     "stars", [T, "minecraft:ender_pearl"], ["#d07cff", "#7a3ac9"])
kind("bounce_tnt", "Bounce TNT", "Sprung-TNT", "Launches everything into the air, soft landing", "Schleudert alles in die Luft, sanfte Landung",
     "#62a844", ("rope",), "#f0fbe6", "spring", {"a": "#2f5f1d", "c": "#7fc85a"},
     "slime", [T, "minecraft:slime_ball"], ["#a6e884", "#8fd66a"])
kind("firework_tnt", "Firework TNT", "Feuerwerk-TNT", "Real fireworks, no damage", "Echtes Feuerwerk, kein Schaden",
     "#9b2d8f", ("metal", "#d9b23a"), "#2a1030", "sparkle", {"a": "#ffd23f", "b": "#ff5fa2", "c": "#5fd3ff", "w": "#ffffff"},
     "stars", [T, "minecraft:firework_rocket"], ["#ffd23f", "#5fd3ff", "#ff5fa2"])
kind("healing_tnt", "Healing TNT", "Heil-TNT", "Heals and cures poison and wither", "Heilt und entfernt Gift und Wither",
     "#e84a8a", ("metal", "#e8c24a"), "#ffffff", "heart", {"a": "#d81b60", "b": "#ff9ec2"},
     "stars", [T, "minecraft:glistering_melon_slice"], ["#ffffff", "#ffd0e2"])
kind("black_hole_tnt", "Black Hole TNT", "Schwarzes-Loch-TNT", "Five seconds of vortex that swallows creatures and blocks", "Fünf Sekunden Sog, der Lebewesen und Blöcke verschluckt",
     "#140b1f", ("metal", "#3a3348"), "#2a1840", "hole", {"c": "#9b5cff", "b": "#e6d2ff", "d": "#3b1d66", "o": "#020104"},
     "stars", ["tnt_arsenal:gravity_tnt", "minecraft:ender_eye", "minecraft:crying_obsidian"], ["#ffffff", "#b07cff", "#6a3ad6"])
kind("antigravity_tnt", "Antigravity TNT", "Antigravitations-TNT", "Blocks and creatures float up", "Lässt Blöcke und Lebewesen aufsteigen",
     "#2f8f9c", ("metal", "#c8d6da"), "#e8fbfb", "rise", {"a": "#1f6f7a", "c": "#7a8a90"},
     "stars", [T, "minecraft:phantom_membrane", "minecraft:feather"], ["#c8ffff", "#ffffff"])
kind("bridge_tnt", "Bridge TNT", "Brücken-TNT", "Builds a 40-block stone bridge the way you face", "Baut eine 40 Blöcke lange Steinbrücke in Blickrichtung",
     "#7a7a7a", ("metal", "#5a5f66"), "#efe8d8", "bridge", {"a": "#8f8f8f", "b": "#5a5a5a", "d": "#4a4a4a"},
     "bricks", [T] + ["minecraft:stone_bricks"] * 4)
kind("wall_tnt", "Wall TNT", "Mauer-TNT", "Raises a round castle wall", "Zieht eine runde Burgmauer hoch",
     "#6a6a6a", ("rope",), "#e9e4d8", "castle", {"a": "#9a9a9a", "d": "#555555"},
     "bricks", [T, "minecraft:cobblestone_wall", "minecraft:cobblestone_wall"])
kind("horde_tnt", "Horde TNT", "Horden-TNT", "Summons zombies and skeletons", "Ruft Zombies und Skelette herbei",
     "#3f6b3a", ("rope", "#4a3a2a"), "#dce8d4", "zombie", {"a": "#5d9a4a", "c": "#2f4f28", "d": "#121212"},
     "moss", [T, "minecraft:rotten_flesh", "minecraft:rotten_flesh", "minecraft:bone"], ["#2a4a26", "#6a8a5a"])
kind("sonic_tnt", "Sonic TNT", "Schallwellen-TNT", "The warden's sonic boom, through walls", "Schallwelle des Wardens, geht durch Wände",
     "#0d3a44", ("metal", "#1f2a30"), "#0f1f24", "waves", {"a": "#38e0e8", "b": "#a8f8ff"},
     "stars", [T, "minecraft:echo_shard"], ["#38e0e8", "#0f8a94"])
kind("smoke_tnt", "Smoke TNT", "Nebel-TNT", "A smoke screen: blindness and slowness", "Nebelwand: Blindheit und Langsamkeit",
     "#5a5f66", ("rope",), "#e4e6e8", "cloud", {"a": "#6c737a", "b": "#d4d8dc"},
     "speck", [T, "minecraft:campfire"], ["#8a9097", "#45494e"])
kind("xray_tnt", "X-Ray TNT", "Röntgen-TNT", "Stone turns to glass for 30 seconds, ores show", "Stein wird 30 Sekunden zu Glas, Erze sichtbar",
     "#9fd2dc", ("metal", "#6a7a80"), "#ffffff", "lens", {"o": "#2a3a40", "a": "#bfeeff", "b": "#ffffff", "c": "#7a4a20"},
     "glassy", [T, "minecraft:glass", "minecraft:glass"])
kind("nature_tnt", "Nature TNT", "Natur-TNT", "Bone meal for everything around", "Knochenmehl für alles im Umkreis",
     "#3c7a2a", ("rope",), "#f2ecd6", "tree", {"a": "#1e4d15", "b": "#4fa83a", "c": "#7a4a20", "d": "#6b8f3a"},
     "flowers", [T] + ["minecraft:bone_meal"] * 3, ["#5fa83c", "#2f6a20", "#ff7ab8", "#ffe14d"])
kind("antimatter_tnt", "Antimatter TNT", "Antimaterie-TNT", "Erases a ball of blocks without a trace", "Löscht eine Kugel aus Blöcken spurlos",
     "#e4e4ee", ("metal", "#2a2a36"), "#101018", "void", {"a": "#bff0ff"},
     "stars", [T, "minecraft:dragon_breath", "minecraft:ender_eye"], ["#7a6aff", "#b0a8ff"])
kind("nether_tnt", "Nether TNT", "Nether-TNT", "Turns the land around into the Nether", "Verwandelt die Umgebung in den Nether",
     "#6b1c1c", ("metal", "#d9a520"), "#2a0f12", "portal", {"o": "#120c1a", "a": "#7a2cd6", "b": "#c48aff"},
     "cracks", [T, "minecraft:netherrack", "minecraft:blaze_powder"], ["#ffb84a", "#ff5a1a", "#a8200c"])
kind("meteor_tnt", "Meteor TNT", "Meteor-TNT", "Six burning meteors fall from the sky", "Sechs brennende Meteore fallen vom Himmel",
     "#38322f", ("metal", "#5a524c"), "#ffe9cc", "comet", {"c": "#ff7a1a", "a": "#5a4a44", "b": "#ffcc66", "o": "#1a1412"},
     "cracks", [T, T, "minecraft:magma_block", "minecraft:fire_charge"], ["#ffe066", "#ff8a1a", "#b8400a"])
kind("arrow_rain_tnt", "Arrow Rain TNT", "Pfeilhagel-TNT", "Arrows rain down from the sky", "Lässt Pfeile regnen",
     "#8a6a3a", ("rope",), "#f0e6d0", "arrows", {"b": "#f2f2f2", "a": "#6b4a2a", "c": "#8a949b"},
     "wood", [T] + ["minecraft:arrow"] * 4)
kind("cobweb_tnt", "Cobweb TNT", "Spinnennetz-TNT", "Spins cobwebs into the air", "Spinnt Netze in die Luft",
     "#4a4a52", ("rope", "#8a8a8a"), "#24242a", "web", {"a": "#f0f0f0"},
     "speck", [T] + ["minecraft:string"] * 3, ["#d8d8d8", "#8a8a92"])
kind("storm_tnt", "Storm TNT", "Gewitter-TNT", "Eight seconds of storm, twenty lightning strikes", "Acht Sekunden Gewitter mit 20 Blitzen",
     "#3a4458", ("metal", "#b8683c"), "#e8ecf2", "storm", {"a": "#5a6478", "b": "#c9d0dc", "c": "#ffd84a"},
     "drip", ["tnt_arsenal:lightning_tnt", "minecraft:lightning_rod", "minecraft:lightning_rod"], ["#9cc8ff", "#6a9ad6"])
kind("magnet_tnt", "Magnet TNT", "Magnet-TNT", "Pulls all items and XP within 40 blocks", "Zieht alle Items und XP im Umkreis von 40 heran",
     "#b8262a", ("metal", "#9aa3aa"), "#f2f2f2", "magnet", {"a": "#c62828", "b": "#ff7070", "c": "#c9d0d6"},
     "rivets", [T, "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:redstone"])
kind("sponge_tnt", "Sponge TNT", "Schwamm-TNT", "Soaks up water and lava within 10 blocks", "Saugt Wasser und Lava im Umkreis von 10 auf",
     "#c9b23a", ("rope",), "#fff8dc", "sponge", {"o": "#6a5a10", "a": "#e3cc4a", "d": "#8a7a20"},
     "speck", [T, "minecraft:sponge"], ["#9a8420", "#e8d468"])
kind("torch_tnt", "Torch TNT", "Fackel-TNT", "Places torches wherever it is dark", "Setzt Fackeln überall, wo es dunkel ist",
     "#5a3d22", ("metal", "#7a7f86"), "#2a2018", "torch", {"c": "#ff8a1a", "b": "#ffe066", "a": "#9a6a3b"},
     "embers", [T] + ["minecraft:torch"] * 4, ["#ffcc33", "#ff8a1a"])
kind("shockwave_tnt", "Shockwave TNT", "Druckwellen-TNT", "A blast wave: throws everything away, shatters glass", "Druckwelle: wirft alles weg, zerbricht Glas",
     "#c27a2a", ("metal", "#5a5f66"), "#fff3e0", "push", {"a": "#8a3a0a", "b": "#ff9a3a"},
     "rivets", [T, "minecraft:piston"])
kind("dome_tnt", "Dome TNT", "Kuppel-TNT", "Builds a glass dome for shelter", "Baut eine Glaskuppel als Schutz",
     "#7fb4c6", ("metal", "#d8e4e8"), "#ffffff", "dome", {"a": "#2a6a80", "b": "#c4f0ff", "c": "#ffffff", "d": "#5a8a3a"},
     "glassy", [T] + ["minecraft:glass_pane"] * 4)
kind("chain_tnt", "Chain Reaction TNT", "Kettenreaktions-TNT", "Lights every TNT within 16 blocks", "Zündet alle TNT im Umkreis von 16",
     "#d2452a", ("chain",), "#f1e6c8", "chain", {"a": "#5a6168", "b": "#c8d0d8"},
     None, [T, T, "minecraft:redstone_torch"])
kind("lumber_tnt", "Lumber TNT", "Holzfäller-TNT", "Fells every tree around, wood in one pile", "Fällt alle Bäume im Umkreis, Holz auf einen Haufen",
     "#7a5230", ("rope",), "#f0e2c8", "log", {"a": "#4a2e18", "b": "#c9a06a", "c": "#8a6038"},
     "wood", [T, "minecraft:iron_axe"])
kind("harvest_tnt", "Harvest TNT", "Ernte-TNT", "Harvests ripe fields and replants them", "Erntet reife Felder und pflanzt neu",
     "#c9a03a", ("rope", "#7a5a2a"), "#fff6dc", "wheat", {"a": "#6a8a2a", "b": "#e8c050", "c": "#8a5a2b"},
     "moss", [T, "minecraft:iron_hoe"], ["#e8c860", "#9a7a20"])

kind("sculk_tnt", "Sculk TNT", "Sculk-TNT", "Spreads sculk over the ground", "Überzieht den Boden mit Sculk",
     "#0b2e3a", ("metal", "#1f3a44"), "#0a1a20", "sculk", {"a": "#19a3b0", "b": "#6ff4ff", "c": "#e0ffff"},
     "stars", [T, "minecraft:sculk", "minecraft:sculk"], ["#19d3e0", "#0f6a74"])
kind("desert_tnt", "Desert TNT", "Wüsten-TNT", "Turns the land into desert", "Verwandelt die Umgebung in Wüste",
     "#d8c27a", ("rope", "#9a7a4a"), "#fff4d8", "cactus", {"a": "#2f6b2a", "b": "#5fa83c", "c": "#c9a85a"},
     "speck", [T, "minecraft:cactus", "minecraft:sand"], ["#c9b06a", "#efdca0"])
kind("end_tnt", "End TNT", "End-TNT", "A piece of the End: end stone, purpur and chorus", "Ein Stück End: Endstein, Purpur und Chorus",
     "#d6cf8e", ("metal", "#6a4a8a"), "#1c1426", "island", {"a": "#e6dfa0", "c": "#a8a060", "b": "#c58cff"},
     "speck", [T, "minecraft:end_stone", "minecraft:chorus_fruit"], ["#b8b070", "#efe8b0"])
kind("rainbow_tnt", "Rainbow TNT", "Regenbogen-TNT", "Paints the ground in rainbow rings", "Malt den Boden in Regenbogenringen an",
     "#f2f2f2", ("rope", "#c95ad6"), "#ffffff", "rainbow", {"a": "#e8343a", "b": "#ffd23a", "c": "#3a8cff"},
     "flowers", [T, "minecraft:red_dye", "minecraft:yellow_dye", "minecraft:blue_dye"], ["#e8343a", "#3a8cff", "#ffd23a", "#4be36b"])
kind("earthquake_tnt", "Earthquake TNT", "Erdbeben-TNT", "Shakes the ground and tears fissures down to bedrock", "Lässt die Erde beben und reißt Spalten bis kurz vor Bedrock",
     "#6b5640", ("metal", "#4a4f55"), "#efe4d0", "quake", {"a": "#8a6a4a", "d": "#1a1410", "c": "#a89070"},
     "cracks", [T, "minecraft:gravel", "minecraft:gravel", "minecraft:gravel"], ["#1a120c", "#2a2018", "#3a2c20"])
kind("volcano_tnt", "Volcano TNT", "Vulkan-TNT", "Raises a volcano that erupts for fifteen seconds", "Lässt einen Vulkan wachsen, der 15 Sekunden ausbricht",
     "#2b2422", ("metal", "#3a302c"), "#ffe0b3", "eruption", {"a": "#5a4a44", "d": "#2a1e1b", "c": "#ff7a00", "b": "#ffd23a", "o": "#1a1211"},
     "cracks", [T, "minecraft:magma_block", "minecraft:basalt", "minecraft:basalt"], ["#ffd23a", "#ff7a00", "#c23a00"])
kind("staircase_tnt", "Staircase TNT", "Wendeltreppen-TNT", "A spiral staircase 48 blocks down", "Eine Wendeltreppe 48 Blöcke in die Tiefe",
     "#8a8a8a", ("metal", "#5a5f66"), "#efe8d8", "stairs", {"a": "#7a7a7a", "b": "#b8b8b8"},
     "bricks", [T, "minecraft:stone_brick_stairs", "minecraft:stone_brick_stairs"])
kind("bunker_tnt", "Bunker TNT", "Bunker-TNT", "Carves a lit bunker under the TNT", "Gräbt einen beleuchteten Bunker unter das TNT",
     "#5a6a4a", ("metal", "#3a3f44"), "#e8e4d4", "bunker", {"c": "#5fa83c", "d": "#7a5a3a", "a": "#8a8a8a", "b": "#ffd23a"},
     "rivets", [T, "minecraft:iron_door"])
kind("platform_tnt", "Platform TNT", "Plattform-TNT", "A 15×15 stone floor — over water, air or lava", "Eine 15×15-Steinfläche — über Wasser, Luft oder Lava",
     "#9a9a9a", ("rope",), "#efe8d8", "platform", {"a": "#8a8a8a", "b": "#5a5a5a", "c": "#c8322f", "d": "#6b4a2a"},
     "bricks", [T, "minecraft:stone_brick_slab", "minecraft:stone_brick_slab", "minecraft:stone_brick_slab"])
kind("tower_tnt", "Tower TNT", "Turm-TNT", "A 24-block watchtower with ladder and battlements", "Ein 24 Blöcke hoher Wachturm mit Leiter und Zinnen",
     "#707070", ("metal", "#4a4f55"), "#ece6d6", "tower", {"a": "#8f8f8f", "b": "#c9a06a", "d": "#2a2a2a"},
     "bricks", [T, "minecraft:ladder", "minecraft:ladder", "minecraft:stone_bricks"])
kind("curse_tnt", "Curse TNT", "Fluch-TNT", "Curses every creature nearby except you", "Verflucht alle Lebewesen in der Nähe außer dir",
     "#3a1a4a", ("metal", "#2a1a30"), "#1a0e20", "broken", {"a": "#b04aff"},
     "stars", [T, "minecraft:wither_rose"], ["#b04aff", "#5a1a8a"])
kind("blessing_tnt", "Blessing TNT", "Segen-TNT", "Strength, speed, haste and more for every player nearby", "Stärke, Tempo, Eile und mehr für alle Spieler in der Nähe",
     "#e8c25a", ("metal", "#c99a1a"), "#fffbe8", "sun", {"a": "#ffb300", "b": "#ff7a1a"},
     "stars", [T, "minecraft:golden_apple"], ["#ffffff", "#fff2a8"])
kind("stasis_tnt", "Stasis TNT", "Stasis-TNT", "Freezes every mob nearby for ten seconds", "Friert alle Mobs in der Nähe zehn Sekunden ein",
     "#2a4a6a", ("metal", "#c8d6e0"), "#e8f4ff", "hourglass", {"a": "#2a4a6a", "b": "#7ad6ff"},
     "stars", [T, "minecraft:clock"], ["#7ad6ff", "#c8f0ff"])
kind("bee_tnt", "Bee TNT", "Bienen-TNT", "Eight bees that hunt the monsters around", "Acht Bienen, die die Monster ringsum jagen",
     "#e8b81a", ("rope", "#3a2a10"), "#fffbe8", "bee", {"a": "#f2c21a", "d": "#1a1a1a", "b": "#dff6ff", "c": "#3a3a3a"},
     "speck", [T, "minecraft:honeycomb", "minecraft:honeycomb"], ["#1a1a1a", "#a8800a"])
kind("wolf_tnt", "Wolf Pack TNT", "Wolfsrudel-TNT", "Four wolves, tamed to you", "Vier Wölfe, die dir gehören",
     "#8a8a8a", ("rope", "#c83a2a"), "#f2f2f2", "wolf", {"a": "#c8c8c8", "d": "#1a1a1a", "b": "#ffffff", "c": "#c83a2a"},
     "moss", [T, "minecraft:bone", "minecraft:bone", "minecraft:bone"], ["#a8a8a8", "#6a6a6a"])
kind("golem_tnt", "Golem TNT", "Golem-TNT", "Two iron golems guard the area", "Zwei Eisengolems bewachen die Gegend",
     "#b8b0a4", ("metal", "#6a6a6a"), "#f2f0ea", "golem", {"a": "#c9c2b8", "b": "#e2dcd2", "d": "#8a1a1a", "c": "#9a9288", "v": "#4a8a2a"},
     "moss", [T, "minecraft:iron_block", "minecraft:carved_pumpkin"], ["#4a8a2a", "#2f6a1a"])
kind("flak_tnt", "Flak TNT", "Flak-TNT", "Shoots up 40 blocks and bursts — anti-air", "Schießt 40 Blöcke hoch und platzt — gegen Phantome",
     "#4a5a3a", ("hazard",), "#e8e4d4", "rocket", {"a": "#8a949b", "b": "#2a2a2a", "c": "#ff7a1a"},
     "rivets", [T, "minecraft:firework_rocket", "minecraft:gunpowder"])
kind("halloween_tnt", "Halloween TNT", "Halloween-TNT", "A ring of jack o'lanterns and a cloud of bats", "Ein Ring aus Kürbislaternen und eine Wolke Fledermäuse",
     "#2a1a2e", ("rope", "#e07a1a"), "#1a1018", "pumpkin", {"a": "#e07a1a", "d": "#ffd23a", "v": "#3a6a1a"},
     "embers", [T, "minecraft:carved_pumpkin", "minecraft:torch"], ["#ff8a1a", "#8a3ad6"])

assert len({k["id"] for k in K}) == len(K)
assert len({tuple(sorted(k["recipe"])) for k in K}) == len(K), "two kinds share a recipe"

# --- texture painting -------------------------------------------------------

STICK_TONE = [0, 3, 2, 1]  # gap, highlight, base, shade across each 4-px stick
STICK_ROWS = [0, 1, 2, 13, 14, 15]
STRAP_ROWS = [3, 12]
LABEL_ROWS = range(4, 12)


def strap_colour(style, x, y):
    name = style[0]
    if name == "hazard":
        return hexc("#f2c81b") if (x + y) % 4 < 2 else hexc("#1a1a1a")
    if name == "rope":
        r = ramp(style[1] if len(style) > 1 else "#b8935a")
        return r[[1, 3, 2][(x + (y == 12)) % 3]]
    if name == "chain":
        r = ramp("#8a9198")
        return r[[0, 3, 4, 2][x % 4]]
    r = ramp(style[1])
    if x in (2, 13):
        return r[4]  # rivet
    if x in (1, 3, 12, 14):
        return r[1]
    return r[2]


def paint(k):
    rnd = random.Random(k["id"])
    body = ramp(k["body"])
    side = Canvas()
    top = Canvas()
    bottom = Canvas()
    # sticks on the side
    for y in STICK_ROWS:
        for x in range(16):
            t = STICK_TONE[x % 4]
            if t:
                if y == 0:
                    t = min(4, t + 1)
                elif y in (2, 13, 15):
                    t = max(1, t - 1)
            c = body[t]
            c = mul(c, 1 + (rnd.random() - 0.5) * 0.05)
            side.set(x, y, c)
    for y in STRAP_ROWS:
        for x in range(16):
            side.set(x, y, strap_colour(k["strap"], x, y))
    # label
    paper = hexc(k["label"])
    dark_label = sum(paper) < 300
    for y in LABEL_ROWS:
        for x in range(16):
            c = mul(paper, 1 + (rnd.random() - 0.5) * (0.06 if dark_label else 0.035))
            if y == 4:
                c = mul(c, 0.86)  # shadow under the strap
            if y == 11:
                c = mul(c, 0.93)
            side.set(x, y, c)
    # emblem
    pal = {key: hexc(v) for key, v in k["pal"].items()}
    first = next(iter(pal.values()))
    pal.setdefault("o", mul(first, 0.4))
    rows = ICONS[k["icon"]]
    pts = [(x, y, ch) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch != "."]
    minx = min(p[0] for p in pts)
    maxx = max(p[0] for p in pts)
    miny = min(p[1] for p in pts)
    maxy = max(p[1] for p in pts)
    ox = 4 + (8 - (maxx - minx + 1)) // 2 - minx
    oy = 4 + (8 - (maxy - miny + 1)) // 2 - miny
    occupied = {(x + ox, y + oy) for x, y, _ in pts}
    for x, y, _ in pts:  # soft drop shadow down-right
        sx, sy = x + ox + 1, y + oy + 1
        if (sx, sy) not in occupied and sy in LABEL_ROWS and sx < 16:
            side.set(sx, sy, mix(side.get(sx, sy), (0, 0, 0), 0.22 if not dark_label else 0.45))
    for x, y, ch in pts:
        side.set(x + ox, y + oy, pal[ch])
    # little corner marks on the label in the emblem colour
    mark = mix(paper, first, 0.55)
    for x, y in ((1, 5), (14, 5), (1, 10), (14, 10)):
        side.set(x, y, mark)

    # stick ends on top and bottom
    powder = mix(body[1], (52, 50, 48), 0.55)
    cell = ["gaag", "ahpa", "apps", "gssg"]
    tones = {"g": body[0], "a": body[3], "h": body[4], "p": powder, "s": body[1]}
    for y in range(16):
        for x in range(16):
            c = tones[cell[y % 4][x % 4]]
            c = mul(c, 1 + (rnd.random() - 0.5) * 0.05)
            top.set(x, y, c)
            bottom.set(x, y, mul(c, 0.78))
    # cap with fuse on top
    for y in range(5, 11):
        for x in range(5, 11):
            if (x in (5, 10)) and (y in (5, 10)):
                continue
            c = strap_colour(k["strap"], x, 3 if y < 8 else 12)
            if x == 5 or y == 5:
                c = mix(c, (255, 255, 255), 0.12)
            if x == 10 or y == 10:
                c = mul(c, 0.72)
            top.set(x, y, c)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        top.set(x, y, (28, 26, 26))
    for x, y, c in ((8, 6, (150, 140, 126)), (9, 5, (214, 204, 186)), (10, 4, (236, 228, 212)), (11, 3, (62, 58, 54))):
        top.set(x, y, c)
    # a plain plate underneath
    for y in range(6, 10):
        for x in range(6, 10):
            bottom.set(x, y, mul(strap_colour(k["strap"], x, 3), 0.7 if x in (6, 9) or y in (6, 9) else 0.82))

    pattern(k, side, top, bottom, rnd, body)
    return side, top, bottom


def stick_pixels(face):
    if face == "side":
        return [(x, y) for y in STICK_ROWS for x in range(16)]
    return [(x, y) for y in range(16) for x in range(16)
            if not (4 <= x <= 11 and 3 <= y <= 11)]


def pattern(k, side, top, bottom, rnd, body):
    p = k["pattern"]
    cols = [hexc(c) for c in k["pcol"]]
    faces = (("side", side), ("top", top), ("bottom", bottom))
    if p is None:
        return
    if p == "rivets":
        for name, img in faces[1:]:
            for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
                img.set(x, y, mix(body[4], (255, 255, 255), 0.35))
                img.set(x + 1, y + 1 if y < 8 else y - 1, body[0])
        return
    if p in ("stars", "embers", "ore"):
        count = {"stars": 6, "embers": 7, "ore": 7}[p]
        ore_cols = [hexc(c) for c in ("#6ee8f0", "#f2c84a", "#e8342a", "#3ad672", "#e0e0e0")]
        for name, img in faces:
            spots = rnd.sample(stick_pixels(name), count + (3 if name != "side" else 0))
            for i, (x, y) in enumerate(spots):
                c = (ore_cols if p == "ore" else cols)[i % len(ore_cols if p == "ore" else cols)]
                img.set(x, y, c)
                if p == "ore":
                    img.set(min(15, x + 1), y, mul(c, 0.55))
                elif p == "embers" and y + 1 < 16 and (x, y + 1) in stick_pixels(name):
                    img.set(x, y + 1, mix(c, img.get(x, y + 1), 0.6))
        return
    if p == "cracks":
        for name, img in faces:
            pix = set(stick_pixels(name))
            for _ in range(3 if name == "side" else 4):
                x, y = rnd.choice(sorted(pix))
                for step in range(rnd.randint(3, 6)):
                    if (x, y) in pix:
                        img.set(x, y, cols[0] if step % 3 == 0 else cols[1])
                        for nx, ny in ((x + 1, y), (x, y + 1)):
                            if (nx, ny) in pix:
                                img.set(nx, ny, mix(img.get(nx, ny), cols[2], 0.6))
                    x += rnd.choice((-1, 0, 1))
                    y += rnd.choice((0, 1)) if name == "side" else rnd.choice((-1, 0, 1))
        return
    if p == "frost":
        for x in range(16):
            side.set(x, 0, cols[0])
            if rnd.random() < 0.55:
                side.set(x, 1, cols[1])
            if rnd.random() < 0.3:
                side.set(x, 13, mix(side.get(x, 13), cols[1], 0.7))
        for x, y in rnd.sample(stick_pixels("top"), 18):
            top.set(x, y, mix(top.get(x, y), cols[0], 0.75))
        for x, y in rnd.sample(stick_pixels("side"), 4):
            side.set(x, y, cols[0])
        return
    if p == "drip":
        for x in range(16):
            if rnd.random() < 0.4:
                length = rnd.choice((1, 1, 2, 2, 3))
                for d in range(length):
                    side.set(x, 13 + d, cols[0] if d < length - 1 else cols[1])
        for x, y in rnd.sample(stick_pixels("top"), 8):
            top.set(x, y, cols[0])
        return
    if p in ("moss", "flowers", "speck"):
        share = {"moss": 0.22, "flowers": 0.22, "speck": 0.16}[p]
        for name, img in faces:
            for x, y in stick_pixels(name):
                if rnd.random() < share:
                    img.set(x, y, cols[rnd.randrange(2)])
            if p == "flowers":
                for x, y in rnd.sample(stick_pixels(name), 4):
                    img.set(x, y, cols[2 + rnd.randrange(2)])
        return
    if p == "slime":
        for name, img in faces:
            for x, y in stick_pixels(name):
                if x % 4 in (1, 2) and (y % 4 in (1, 2) or name == "side"):
                    img.set(x, y, mix(img.get(x, y), cols[0], 0.45))
        return
    if p == "wood":
        for name, img in faces:
            for x, y in stick_pixels(name):
                if name == "side" and x % 4 and rnd.random() < 0.18:
                    img.set(x, y, mul(img.get(x, y), 0.82))
            if name != "side":
                for y in range(16):
                    for x in range(16):
                        if (x, y) in stick_pixels(name) and (x % 4, y % 4) in ((1, 1), (2, 2)):
                            img.set(x, y, body[1])
        return
    if p == "bricks":
        for y in STICK_ROWS:
            for x in range(16):
                off = 0 if y < 8 else 4
                if (x + off) % 8 == 0 or y in (2, 13):
                    side.set(x, y, body[0])
                else:
                    side.set(x, y, mul(body[3] if y in (0, 14) else body[2], 1 + (rnd.random() - 0.5) * 0.08))
        for name, img in faces[1:]:
            for y in range(16):
                for x in range(16):
                    if (x, y) not in stick_pixels(name):
                        continue
                    off = 0 if (y // 4) % 2 == 0 else 4
                    if y % 4 == 3 or (x + off) % 8 == 7:
                        img.set(x, y, mul(body[0], 0.9 if name == "top" else 0.75))
                    else:
                        img.set(x, y, mul(body[2] if name == "top" else body[1], 1 + (rnd.random() - 0.5) * 0.08))
        return
    if p == "glassy":
        for name, img in faces:
            for x, y in stick_pixels(name):
                if (x - y) % 7 == 0 or (x - y) % 7 == 1 and rnd.random() < 0.5:
                    img.set(x, y, mix(img.get(x, y), (255, 255, 255), 0.6))
        return
    raise ValueError(p)


# --- isometric preview -----------------------------------------------------


def iso(top, side, k=4):
    """A 2:1 isometric cube, 32k x 32k, transparent around it."""
    n = 32 * k
    out = [(0, 0, 0, 0)] * (n * n)
    for py in range(n):
        for px in range(n):
            u = px + 0.5 - 16 * k
            v = py + 0.5 - 16 * k
            a = u / k
            b = (v + 16 * k) * 2 / k
            x, z = (a + b) / 2, (b - a) / 2
            if 0 <= x < 16 and 0 <= z < 16:
                c = top.get(int(x), int(z))
                out[py * n + px] = c + (255,)
                continue
            if u < 0:
                x = u / k + 16
                y = ((x + 16) * k / 2 - v) / k
                if 0 <= x < 16 and 0 <= y < 16:
                    out[py * n + px] = mul(side.get(int(x), 15 - int(y)), 0.84) + (255,)
            else:
                z = 16 - u / k
                y = ((16 + z) * k / 2 - v) / k
                if 0 <= z < 16 and 0 <= y < 16:
                    out[py * n + px] = mul(side.get(int(u / k), 15 - int(y)), 0.66) + (255,)
    return out


FONT = {
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "B": ["##.", "#.#", "##.", "#.#", "##."],
    "C": [".##", "#..", "#..", "#..", ".##"], "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "E": ["###", "#..", "##.", "#..", "###"], "F": ["###", "#..", "##.", "#..", "#.."],
    "G": [".##", "#..", "#.#", "#.#", ".##"], "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "I": ["###", ".#.", ".#.", ".#.", "###"], "J": ["..#", "..#", "..#", "#.#", ".#."],
    "K": ["#.#", "#.#", "##.", "#.#", "#.#"], "L": ["#..", "#..", "#..", "#..", "###"],
    "M": ["#.#", "###", "###", "#.#", "#.#"], "N": ["##.", "#.#", "#.#", "#.#", "#.#"],
    "O": [".#.", "#.#", "#.#", "#.#", ".#."], "P": ["##.", "#.#", "##.", "#..", "#.."],
    "Q": [".#.", "#.#", "#.#", "##.", ".##"], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "S": [".##", "#..", ".#.", "..#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "U": ["#.#", "#.#", "#.#", "#.#", "###"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "W": ["#.#", "#.#", "###", "###", "#.#"], "X": ["#.#", "#.#", ".#.", "#.#", "#.#"],
    "Y": ["#.#", "#.#", ".#.", ".#.", ".#."], "Z": ["###", "..#", ".#.", "#..", "###"],
    " ": ["...", "...", "...", "...", "..."], "-": ["...", "...", "###", "...", "..."],
}


def text(buf, w, x0, y0, s, colour, scale=2):
    x = x0
    for ch in s.upper():
        glyph = FONT.get(ch, FONT[" "])
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            buf[(y0 + gy * scale + sy) * w + x + gx * scale + sx] = colour
        x += 4 * scale


def text_width(s, scale=2):
    return len(s) * 4 * scale - scale


# --- data files ---------------------------------------------------------------


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    lang_en = {"itemGroup.tnt_arsenal": "TNT Arsenal"}
    lang_de = {"itemGroup.tnt_arsenal": "TNT-Arsenal"}
    faces = {}
    for k in K:
        kid = k["id"]
        side, top, bottom = paint(k)
        faces[kid] = (side, top)
        base = f"{ROOT}/assets/{NS}/textures/block/{kid}"
        write_png(base + "_side.png", 16, 16, side.rgba())
        write_png(base + "_top.png", 16, 16, top.rgba())
        write_png(base + "_bottom.png", 16, 16, bottom.rgba())
        write_json(f"{ROOT}/assets/{NS}/models/block/{kid}.json", {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": f"{NS}:block/{kid}_top", "bottom": f"{NS}:block/{kid}_bottom",
                         "side": f"{NS}:block/{kid}_side"},
        })
        # Multipart without a condition: the same model for every facing.
        write_json(f"{ROOT}/assets/{NS}/blockstates/{kid}.json", {"multipart": [{"apply": {"model": f"{NS}:block/{kid}"}}]})
        write_json(f"{ROOT}/assets/{NS}/items/{kid}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{kid}"}})
        lang_en[f"block.{NS}.{kid}"] = k["en"]
        lang_de[f"block.{NS}.{kid}"] = k["de"]
        lang_en[f"block.{NS}.{kid}.desc"] = k["desc_en"]
        lang_de[f"block.{NS}.{kid}.desc"] = k["desc_de"]
        write_json(f"{ROOT}/data/{NS}/loot_table/blocks/{kid}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{kid}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{kid}",
        })
        write_json(f"{ROOT}/data/{NS}/recipe/{kid}.json", {
            "type": "minecraft:crafting_shapeless",
            "category": "redstone",
            "ingredients": k["recipe"],
            "result": {"id": f"{NS}:{kid}", "count": 1},
        })
    write_json(f"{ROOT}/assets/{NS}/lang/en_us.json", lang_en)
    write_json(f"{ROOT}/assets/{NS}/lang/de_de.json", lang_de)

    # mod icon: the nuke as a cube
    side, top = faces["nuke_tnt"]
    write_png(f"{ROOT}/assets/{NS}/icon.png", 128, 128, iso(top, side, 4))

    # preview sheet: every kind as a cube with its name
    cols, cell_w, cell_h = 7, 150, 160
    rows = (len(K) + cols - 1) // cols
    w, h = cols * cell_w, rows * cell_h
    sheet = [(0, 0, 0, 0)] * (w * h)
    grey = (140, 140, 140, 255)
    for i, k in enumerate(K):
        side, top = faces[k["id"]]
        cube = iso(top, side, 4)
        cx = (i % cols) * cell_w + (cell_w - 128) // 2
        cy = (i // cols) * cell_h + 6
        for y in range(128):
            for x in range(128):
                p = cube[y * 128 + x]
                if p[3]:
                    sheet[(cy + y) * w + cx + x] = p
        name = k["en"].replace(" TNT", "")
        tx = (i % cols) * cell_w + (cell_w - text_width(name)) // 2
        text(sheet, w, tx, cy + 136, name, grey)
    write_png(os.path.join(REPO, "docs", "textures.png"), w, h, sheet)
    print("ok", len(K), "kinds")


if __name__ == "__main__":
    main()
