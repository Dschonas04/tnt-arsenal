# TNT Arsenal

Nineteen kinds of TNT for Minecraft 26.2 (Fabric), each with its own look and its
own way of going off. They are crafted at an ordinary crafting table, so **JEI and
REI show every recipe**.

![The nineteen kinds, side and top](docs/textures.png)

| Kind | What it does | Recipe (shapeless) |
|---|---|---|
| Mega TNT | twice the power of TNT | 4 × TNT |
| Giga TNT | four times the power | 4 × Mega TNT + blaze powder |
| Nuke TNT | six times the power, sets fire | 4 × Giga TNT + nether star |
| Fire TNT | normal blast, sets everything around alight | TNT + fire charge |
| Lightning TNT | a ring of nine lightning bolts | TNT + lightning rod |
| Cluster TNT | bursts and flings eight TNT in all directions | 3 × TNT + 2 × gunpowder |
| Drill TNT | blows a shaft 36 blocks straight down | TNT + iron pickaxe |
| Tunnel TNT | blows a 42-block tunnel the way you faced when placing it | 2 × TNT + rail |
| Leveler TNT | clears a round plot, radius 8, twelve blocks high, nothing below | TNT + iron shovel |
| Miner TNT | removes stone, dirt and gravel around it, leaves the ores | TNT + stone pickaxe + redstone |
| Water TNT | explodes and floods the crater | TNT + water bucket |
| Lava TNT | explodes and fills the crater with lava | TNT + lava bucket |
| Frost TNT | freezes water and lava, drops snow, chills creatures | TNT + packed ice + snowball |
| Poison TNT | small bang, then a cloud of poison | TNT + fermented spider eye |
| Gravity TNT | pulls everything within 16 blocks in, then explodes | TNT + iron block |
| Ender TNT | teleports every creature nearby somewhere else | TNT + ender pearl |
| Bounce TNT | launches everything into the air, breaks nothing | TNT + slime ball |
| Firework TNT | all show, no damage | TNT + firework rocket |
| Healing TNT | a cloud that heals, breaks nothing | TNT + glistering melon slice |

They light like TNT: redstone, flint and steel, fire charges, burning arrows and
other explosions. While the fuse burns they keep their own texture.

## Install

Put the jar into `mods/` on the server **and** on every client — the blocks are
registered, so both sides need them. Requires Fabric Loader 0.19+ and Fabric API.

## How it works

Minecraft 26.2 ships without obfuscation, so the mod is compiled straight against
the server jar — no mappings, no Loom, plain Gradle:

```
gradle build -Pminecraft_jar=/path/to/server-26.2.jar
```

Two Fabric API modules (`fabric-api-base`, `fabric-creative-tab-api-v1`) go into
`libs/` as compile-only dependencies; they are inside every `fabric-api` jar under
`META-INF/jars/`.

Each kind is an ordinary `TntBlock` subclass. When it primes, the primed entity
carries the block's state — vanilla renders it with that texture, so the mod needs
no client code. A single mixin on `PrimedTnt#explode` hands the detonation to the
kind whose block the entity carries; every other TNT explodes as before.

All textures are original pixel art made for this mod.

## License

MIT
