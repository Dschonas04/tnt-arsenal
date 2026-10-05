package io.github.dschonas04.tntarsenal;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What each kind does when its fuse runs out. Every method gets the level and
 * the primed entity, which is already discarded but still knows its position,
 * its owner and the block state it carried.
 *
 * <p>Kinds that remove or replace blocks directly, without an explosion, all go
 * through {@link #removable}: bedrock, obsidian and anything else an explosion
 * could not break stays, and so does every block with contents.
 */
final class Detonations {
    private Detonations() {
    }

    private static final int FLAGS = Block.UPDATE_ALL;
    /** Bulk edits skip neighbour updates: no sand avalanches, no lag spike. */
    private static final int QUIET = Block.UPDATE_CLIENTS;
    /** Ores as every mod tags them; the tunnel and drill drop these instead of deleting them. */
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
    private static final TagKey<Block> GLASS_PANES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "glass_panes"));

    // --- helpers -------------------------------------------------------

    private static double y(PrimedTnt t) {
        return t.getY(0.0625);
    }

    static void blast(ServerLevel level, PrimedTnt t, float power, boolean fire) {
        level.explode(t, t.getX(), y(t), t.getZ(), power, fire, Level.ExplosionInteraction.TNT);
    }

    /** Bang and smoke without breaking anything. */
    private static void pop(ServerLevel level, PrimedTnt t, float power) {
        level.explode(t, t.getX(), y(t), t.getZ(), power, false, Level.ExplosionInteraction.NONE);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void particles(ServerLevel level, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    private static <T extends Entity> List<T> around(ServerLevel level, Vec3 center, Class<T> type, double radius) {
        AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
        return level.getEntitiesOfClass(type, box,
                e -> e.isAlive() && !e.isSpectator() && e.distanceToSqr(center) <= radius * radius);
    }

    private static <T extends Entity> List<T> around(ServerLevel level, PrimedTnt t, Class<T> type, double radius) {
        return around(level, t.position(), type, radius);
    }

    /**
     * Whether a kind may delete or swap this block. No for air, for bedrock and
     * anything else unbreakable, for obsidian-hard blocks (blast resistance 600
     * and up: obsidian, crying obsidian, ancient debris, netherite, anvils,
     * reinforced deepslate), for anything with a block entity (chests, furnaces,
     * spawners, signs) and for TNT, which should go off rather than vanish.
     */
    static boolean removable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) return false;
        if (state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0) return false;
        if (state.getBlock().getExplosionResistance() >= 600) return false;
        return !(state.getBlock() instanceof TntBlock);
    }

    /** Plain ground: stone of every dimension, dirt, sand, gravel. */
    private static boolean ground(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.CALCITE) || state.is(Blocks.CLAY)
                || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.END_STONE)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL);
    }

    private static List<BlockPos> sphere(BlockPos center, int radius) {
        return BlockPos.betweenClosedStream(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))
                .filter(p -> p.distSqr(center) <= (double) radius * radius)
                .map(BlockPos::immutable)
                .toList();
    }

    /** The blocks between radius - 1 (exclusive) and radius (inclusive). */
    private static List<BlockPos> shell(BlockPos center, int radius) {
        double inner = (radius - 1.0) * (radius - 1.0);
        double outer = (double) radius * radius;
        return BlockPos.betweenClosedStream(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))
                .filter(p -> {
                    double d = p.distSqr(center);
                    return d > inner && d <= outer;
                })
                .map(BlockPos::immutable)
                .toList();
    }

    private static Direction facing(PrimedTnt t) {
        BlockState state = t.getBlockState();
        return state.hasProperty(ArsenalTntBlock.FACING) ? state.getValue(ArsenalTntBlock.FACING) : Direction.NORTH;
    }

    /** Puts a block where there is only air, liquid or a plant. */
    private static boolean place(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()) return false;
        level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), FLAGS);
        return true;
    }

    /** Liquids next to a dug passage turn to cobblestone so nothing floods in. */
    private static void seal(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LiquidBlock) level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), FLAGS);
    }

    /** Digs one block out of a passage: ores drop, liquids go, protected blocks stay. */
    private static void dig(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!removable(level, pos, state)) return;
        if (state.is(ORES)) Block.dropResources(state, level, pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    /** Collects drops in one pile instead of a hundred scattered items. */
    private static void addDrops(List<ItemStack> pile, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            for (ItemStack stack : pile) {
                if (drop.isEmpty()) break;
                if (ItemStack.isSameItemSameComponents(stack, drop) && stack.getCount() < stack.getMaxStackSize()) {
                    int move = Math.min(drop.getCount(), stack.getMaxStackSize() - stack.getCount());
                    stack.grow(move);
                    drop.shrink(move);
                }
            }
            if (!drop.isEmpty()) pile.add(drop.copy());
        }
    }

    private static void dropPile(ServerLevel level, Vec3 at, List<ItemStack> pile) {
        for (ItemStack stack : pile) {
            ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, stack);
            item.setDeltaMovement(0, 0.15, 0);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

    private static void pull(Entity entity, Vec3 center, double strength) {
        Vec3 to = center.subtract(entity.position());
        if (to.lengthSqr() < 0.25) return;
        entity.setDeltaMovement(entity.getDeltaMovement().scale(0.7).add(to.normalize().scale(strength)).add(0, 0.03, 0));
        entity.hurtMarked = true;
    }

    // --- raw power -----------------------------------------------------

    /**
     * Four times TNT. Big blasts use block-explosion rules, under which only a
     * share of the broken blocks drops: a giga crater would otherwise leave
     * thousands of items lying around and drag the server down.
     */
    static void giga(ServerLevel level, PrimedTnt t) {
        level.explode(t, t.getX(), y(t), t.getZ(), 16, false, Level.ExplosionInteraction.BLOCK);
    }

    /**
     * A core blast and a ring of eight around it, so the crater comes out round
     * instead of ragged, then a mushroom cloud that rises for three seconds.
     */
    static void nuke(ServerLevel level, PrimedTnt t) {
        double x = t.getX();
        double y = y(t);
        double z = t.getZ();
        level.explode(t, x, y, z, 20, true, Level.ExplosionInteraction.BLOCK);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            level.explode(t, x + Math.cos(angle) * 10, y, z + Math.sin(angle) * 10, 10, true, Level.ExplosionInteraction.BLOCK);
        }
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 16.0f, 0.5f);
        Tasks.start(age -> {
            double stem = Math.min(age, 40) * 0.75;
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + stem, z, 12, 1.5, 1.0, 1.5, 0.01);
            level.sendParticles(ParticleTypes.FLAME, x, y + stem * 0.5, z, 6, 1.2, stem * 0.3, 1.2, 0.02);
            if (age >= 20) {
                double cap = y + 30;
                double spread = 4 + (age - 20) * 0.25;
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, cap, z, 40, spread, 2.0, spread, 0.02);
                level.sendParticles(ParticleTypes.EXPLOSION, x, cap, z, 2, spread, 1.5, spread, 0);
            }
            if (age == 10 || age == 25) {
                level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 12.0f, 0.4f);
            }
            return age >= 60;
        });
    }

    /** A normal explosion that also sets the surroundings alight. */
    static void fire(ServerLevel level, PrimedTnt t) {
        blast(level, t, 4, true);
        RandomSource random = level.getRandom();
        for (BlockPos pos : sphere(t.blockPosition(), 8)) {
            if (random.nextFloat() < 0.35f && level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), FLAGS);
            }
        }
    }

    /** A ring of lightning around a small blast. */
    static void lightning(ServerLevel level, PrimedTnt t) {
        blast(level, t, 2, false);
        for (int i = 0; i <= 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            double r = i == 8 ? 0 : 5;
            strike(level, t.getX() + Math.cos(angle) * r, t.getY(), t.getZ() + Math.sin(angle) * r);
        }
    }

    private static void strike(ServerLevel level, double x, double y, double z) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.setPos(x, y, z);
        level.addFreshEntity(bolt);
    }

    /** Bursts and flings eight ordinary TNT in all directions. */
    static void cluster(ServerLevel level, PrimedTnt t) {
        blast(level, t, 2, false);
        RandomSource random = level.getRandom();
        LivingEntity owner = t.getOwner();
        for (int i = 0; i < 8; i++) {
            PrimedTnt child = new PrimedTnt(level, t.getX(), t.getY() + 0.5, t.getZ(), owner);
            double angle = Math.PI * 2 * i / 8 + random.nextDouble() * 0.4;
            child.setDeltaMovement(Math.cos(angle) * 0.6, 0.55 + random.nextDouble() * 0.3, Math.sin(angle) * 0.6);
            child.setFuse(25 + random.nextInt(25));
            level.addFreshEntity(child);
        }
    }

    // --- digging ---------------------------------------------------------

    /**
     * A clean 3×3 shaft straight down, up to 64 deep and never into bedrock,
     * with a ladder on the side the player faced. Liquids in the walls become
     * cobblestone, ores drop, protected blocks stay where they are.
     */
    static void drill(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        BlockPos top = t.blockPosition();
        Direction wall = facing(t);
        int bottom = Math.max(level.getMinY() + 5, top.getY() - 64);
        Tasks.start(age -> {
            for (int step = 0; step < 2; step++) {
                int y = top.getY() - 1 - (age * 2 + step);
                if (y < bottom) return true;
                BlockPos center = new BlockPos(top.getX(), y, top.getZ());
                if (!level.isLoaded(center)) return true;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = center.offset(dx, 0, dz);
                        if (Math.abs(dx) == 2 || Math.abs(dz) == 2) seal(level, pos);
                        else dig(level, pos);
                    }
                }
                seal(level, center.below());
                BlockPos ladder = center.relative(wall);
                BlockPos behind = ladder.relative(wall);
                if (level.getBlockState(behind).canBeReplaced()) place(level, behind, Blocks.COBBLESTONE.defaultBlockState());
                if (level.getBlockState(ladder).isAir()) {
                    level.setBlock(ladder, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, wall.getOpposite()), FLAGS);
                }
            }
            if (age % 4 == 0) sound(level, Vec3.atCenterOf(top.below(age * 2)), SoundEvents.STONE_BREAK, 1.0f, 0.8f);
            return false;
        });
    }

    /**
     * A clean tunnel, three wide and three high, 48 long, the way the player faced
     * when placing it. A torch every eight blocks; ores drop, liquids are sealed.
     */
    static void tunnel(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        BlockPos start = t.blockPosition();
        Direction ahead = facing(t);
        Direction left = ahead.getCounterClockWise();
        Tasks.start(age -> {
            if (age >= 48) return true;
            BlockPos floor = start.relative(ahead, age);
            if (!level.isLoaded(floor)) return true;
            for (int w = -2; w <= 2; w++) {
                for (int h = -1; h <= 3; h++) {
                    BlockPos pos = floor.relative(left, w).above(h);
                    boolean inside = Math.abs(w) <= 1 && h >= 0 && h <= 2;
                    if (inside) dig(level, pos);
                    else seal(level, pos);
                }
            }
            if (age % 8 == 4) {
                BlockPos torch = floor.relative(left).above();
                BlockPos wall = torch.relative(left);
                if (level.getBlockState(torch).isAir() && level.getBlockState(wall).isFaceSturdy(level, wall, left.getOpposite())) {
                    level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, left.getOpposite()), FLAGS);
                }
            }
            if (age % 3 == 0) sound(level, Vec3.atCenterOf(floor), SoundEvents.STONE_BREAK, 1.0f, 0.8f);
            return false;
        });
    }

    /**
     * Clears a round building plot from the TNT's height up: radius 10, sixteen
     * high, one layer per tick from the top. Nothing below the TNT is touched.
     */
    static void leveler(ServerLevel level, PrimedTnt t) {
        pop(level, t, 2);
        BlockPos center = t.blockPosition();
        Tasks.start(age -> {
            int dy = 16 - age;
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    if (dx * dx + dz * dz > 100) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) continue;
                    if (removable(level, pos, level.getBlockState(pos))) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
                    }
                }
            }
            if (age % 4 == 0) {
                level.sendParticles(ParticleTypes.POOF, center.getX() + 0.5, center.getY() + dy, center.getZ() + 0.5, 30, 6, 0.5, 6, 0.02);
            }
            return dy <= 0;
        });
    }

    /** Removes the stone, dirt and gravel around it and leaves the ores standing. */
    static void miner(ServerLevel level, PrimedTnt t) {
        pop(level, t, 2);
        for (BlockPos pos : sphere(t.blockPosition(), 7)) {
            BlockState state = level.getBlockState(pos);
            if (ground(state) && removable(level, pos, state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            }
        }
    }

    /**
     * Turns stone and dirt into glass for thirty seconds, so every ore and cave
     * around shows through, then puts the original blocks back.
     */
    static void xray(ServerLevel level, PrimedTnt t) {
        sound(level, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 3.0f, 0.7f);
        Map<BlockPos, BlockState> swapped = new HashMap<>();
        for (BlockPos pos : sphere(t.blockPosition(), 8)) {
            BlockState state = level.getBlockState(pos);
            if (ground(state) && removable(level, pos, state)) {
                swapped.put(pos, state);
                level.setBlock(pos, Blocks.GLASS.defaultBlockState(), QUIET);
            }
        }
        Vec3 at = t.position();
        Tasks.start(age -> {
            if (age < 600) return false;
            // Chunks out of range keep their glass until they are loaded again (for an hour at most).
            swapped.entrySet().removeIf(entry -> {
                BlockPos pos = entry.getKey();
                if (!level.isLoaded(pos)) return false;
                if (level.getBlockState(pos).is(Blocks.GLASS)) level.setBlock(pos, entry.getValue(), QUIET);
                return true;
            });
            if (age == 600) sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 3.0f, 0.5f);
            return swapped.isEmpty() || age > 72000;
        });
    }

    // --- liquids and weather -------------------------------------------

    /** Explodes and floods the crater. */
    static void water(ServerLevel level, PrimedTnt t) {
        blast(level, t, 3, false);
        fill(level, t, Blocks.WATER.defaultBlockState());
    }

    /** Explodes and fills the crater with lava. */
    static void lava(ServerLevel level, PrimedTnt t) {
        blast(level, t, 3, false);
        fill(level, t, Blocks.LAVA.defaultBlockState());
    }

    private static void fill(ServerLevel level, PrimedTnt t, BlockState liquid) {
        BlockPos center = t.blockPosition();
        for (BlockPos pos : sphere(center, 3)) {
            if (pos.getY() <= center.getY() && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, liquid, FLAGS);
            }
        }
    }

    /** Soaks up all water and lava within ten blocks, waterlogged blocks included. */
    static void sponge(ServerLevel level, PrimedTnt t) {
        for (BlockPos pos : sphere(t.blockPosition(), 10)) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LiquidBlock || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
                    || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS) || state.is(Blocks.BUBBLE_COLUMN)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            } else if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), QUIET);
            }
        }
        sound(level, t.position(), SoundEvents.SPONGE_ABSORB, 3.0f, 0.8f);
        particles(level, ParticleTypes.SPLASH, t.position(), 400, 5, 0.3);
    }

    /** Freezes water and lava, drops snow and chills every creature around. */
    static void frost(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        BlockPos center = t.blockPosition();
        for (BlockPos pos : sphere(center, 8)) {
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(Fluids.WATER) && state.is(Blocks.WATER)) {
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), FLAGS);
            } else if (state.is(Blocks.LAVA)) {
                level.setBlock(pos, state.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState(), FLAGS);
            } else if (state.isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                level.setBlock(pos, Blocks.SNOW.defaultBlockState(), FLAGS);
            }
        }
        for (LivingEntity entity : around(level, t, LivingEntity.class, 9)) {
            entity.setTicksFrozen(entity.getTicksRequiredToFreeze() + 200);
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 3));
            entity.clearFire();
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, t.getX(), y(t) + 1, t.getZ(), 400, 5, 2, 5, 0.05);
    }

    /** Eight seconds of thunderstorm: twenty lightning strikes within twenty blocks. */
    static void storm(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        Vec3 c = t.position();
        RandomSource random = level.getRandom();
        Tasks.start(age -> {
            level.sendParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 14, c.z, 40, 14, 1, 14, 0);
            if (age % 8 == 0) {
                double x = c.x + (random.nextDouble() - 0.5) * 40;
                double z = c.z + (random.nextDouble() - 0.5) * 40;
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, c.y, z));
                if (level.isLoaded(ground)) strike(level, x, ground.getY(), z);
            }
            return age >= 160;
        });
    }

    // --- creatures -------------------------------------------------------

    /** A small bang that leaves a lingering cloud of poison. */
    static void poison(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        cloud(level, t, 6, 600, new MobEffectInstance(MobEffects.POISON, 200, 1));
    }

    /** A cloud that heals and cures poison and wither — nothing breaks. */
    static void healing(ServerLevel level, PrimedTnt t) {
        sound(level, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 1.0f);
        cloud(level, t, 6, 400, new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        for (LivingEntity entity : around(level, t, LivingEntity.class, 6)) {
            entity.heal(8);
            entity.removeEffect(MobEffects.POISON);
            entity.removeEffect(MobEffects.WITHER);
        }
        level.sendParticles(ParticleTypes.HEART, t.getX(), y(t) + 1, t.getZ(), 40, 3, 1, 3, 0.1);
    }

    /** A thick smoke screen: everyone inside is blinded and slowed for twenty seconds. */
    static void smoke(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        cloud(level, t, 7, 400, new MobEffectInstance(MobEffects.BLINDNESS, 60, 0),
                new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
        Vec3 c = t.position();
        Tasks.start(age -> {
            if (age % 4 == 0) level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 1, c.z, 25, 4, 1.5, 4, 0.01);
            return age >= 300;
        });
    }

    private static void cloud(ServerLevel level, PrimedTnt t, float radius, int duration, MobEffectInstance... effects) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, t.getX(), t.getY(), t.getZ());
        cloud.setRadius(radius);
        cloud.setDuration(duration);
        cloud.setRadiusPerTick(-radius / duration);
        cloud.setWaitTime(0);
        for (MobEffectInstance effect : effects) cloud.addEffect(effect);
        LivingEntity owner = t.getOwner();
        if (owner != null) cloud.setOwner(owner);
        level.addFreshEntity(cloud);
    }

    /** Five zombies and three skeletons climb out of the smoke. */
    static void horde(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        RandomSource random = level.getRandom();
        BlockPos center = t.blockPosition();
        for (int i = 0; i < 8; i++) {
            BlockPos pos = center.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
            for (int up = 0; up < 4 && !level.getBlockState(pos).isAir(); up++) pos = pos.above();
            if (i < 5) EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.TRIGGERED);
            else EntityTypes.SKELETON.spawn(level, pos, EntitySpawnReason.TRIGGERED);
        }
        sound(level, t.position(), SoundEvents.ZOMBIE_AMBIENT, 3.0f, 0.7f);
        particles(level, ParticleTypes.LARGE_SMOKE, t.position(), 80, 2, 0.05);
    }

    /** Scatters every creature around it like a chorus fruit, just stronger. */
    static void ender(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        RandomSource random = level.getRandom();
        for (LivingEntity entity : around(level, t, LivingEntity.class, 10)) {
            for (int attempt = 0; attempt < 16; attempt++) {
                double x = entity.getX() + (random.nextDouble() - 0.5) * 48;
                double y = entity.getY() + random.nextInt(16) - 8;
                double z = entity.getZ() + (random.nextDouble() - 0.5) * 48;
                if (entity.randomTeleport(x, y, z, true)) break;
            }
        }
        sound(level, t.position(), SoundEvents.ENDERMAN_TELEPORT, 2.0f, 0.8f);
        level.sendParticles(ParticleTypes.PORTAL, t.getX(), y(t) + 1, t.getZ(), 400, 4, 2, 4, 0.5);
    }

    // --- forces ----------------------------------------------------------

    /** Pulls everything within 16 blocks in for two seconds, then explodes. */
    static void gravity(ServerLevel level, PrimedTnt t) {
        Vec3 c = t.position();
        Tasks.start(age -> {
            for (Entity entity : around(level, c, Entity.class, 16)) pull(entity, c, 0.35);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 40, 6, 3, 6, 0.4);
            if (age < 40) return false;
            level.explode(t, c.x, c.y, c.z, 5, false, Level.ExplosionInteraction.TNT);
            return true;
        });
    }

    /**
     * Five seconds of vortex: pulls everything within 24 blocks, hurts what it
     * catches, then collapses and swallows a ball of blocks fifteen wide.
     */
    static void blackHole(ServerLevel level, PrimedTnt t) {
        Vec3 c = t.position().add(0, 0.5, 0);
        BlockPos center = t.blockPosition();
        sound(level, c, SoundEvents.BEACON_ACTIVATE, 4.0f, 0.5f);
        Tasks.start(age -> {
            double strength = 0.15 + age * 0.003;
            for (Entity entity : around(level, c, Entity.class, 24)) {
                pull(entity, c, strength);
                if (age % 10 == 0 && entity instanceof LivingEntity living && living.distanceToSqr(c) < 4) {
                    living.hurtServer(level, level.damageSources().magic(), 3);
                }
            }
            for (int arm = 0; arm < 4; arm++) {
                double angle = age * 0.35 + arm * Math.PI / 2;
                double r = 7 - (age % 20) * 0.3;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(angle) * r, c.y, c.z + Math.sin(angle) * r, 4, 0.2, 0.2, 0.2, 0);
            }
            level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 6, 0.4, 0.4, 0.4, 0.01);
            if (age % 20 == 0) sound(level, c, SoundEvents.PORTAL_AMBIENT, 3.0f, 0.5f);
            if (age < 100) return false;
            for (BlockPos pos : sphere(center, 7)) {
                if (level.isLoaded(pos) && removable(level, pos, level.getBlockState(pos))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
                }
            }
            level.explode(t, c.x, c.y, c.z, 4, false, Level.ExplosionInteraction.NONE);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 3, 1, 1, 1, 0);
            return true;
        });
    }

    /**
     * Wipes a ball of blocks twenty wide out of existence, from the inside
     * outwards, without a single drop. Protected blocks survive it.
     */
    static void antimatter(ServerLevel level, PrimedTnt t) {
        BlockPos center = t.blockPosition();
        Vec3 c = t.position();
        sound(level, c, SoundEvents.BEACON_DEACTIVATE, 6.0f, 0.5f);
        Tasks.start(age -> {
            int radius = age + 1;
            for (BlockPos pos : shell(center, radius)) {
                if (level.isLoaded(pos) && removable(level, pos, level.getBlockState(pos))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
                }
            }
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, radius * 12, radius * 0.6, radius * 0.6, radius * 0.6, 0.02);
            if (radius < 10) return false;
            for (LivingEntity entity : around(level, c, LivingEntity.class, 10)) {
                entity.hurtServer(level, level.damageSources().magic(), 8);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 1, 1, 1, 0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 6.0f, 1.6f);
            return true;
        });
    }

    /** Lifts the blocks around it into the air; creatures nearby float up and glide down. */
    static void antigravity(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        RandomSource random = level.getRandom();
        for (BlockPos pos : sphere(t.blockPosition(), 4)) {
            BlockState state = level.getBlockState(pos);
            if (!removable(level, pos, state) || !state.getFluidState().isEmpty() || !state.isCollisionShapeFullBlock(level, pos)) continue;
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            block.setDeltaMovement((random.nextDouble() - 0.5) * 0.15, 0.7 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.15);
        }
        for (LivingEntity entity : around(level, t, LivingEntity.class, 10)) {
            entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 80, 1));
            entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
        }
        particles(level, ParticleTypes.END_ROD, t.position(), 120, 3, 0.15);
    }

    /** Launches everything around it into the air without breaking a block — and lets it glide down. */
    static void bounce(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        for (Entity entity : around(level, t, Entity.class, 8)) {
            Vec3 away = entity.position().subtract(t.position()).multiply(1, 0, 1);
            Vec3 push = away.lengthSqr() < 0.01 ? Vec3.ZERO : away.normalize().scale(0.8);
            entity.setDeltaMovement(push.x, 2.4, push.z);
            entity.hurtMarked = true;
            if (entity instanceof LivingEntity living) living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
        }
        sound(level, t.position(), SoundEvents.SLIME_BLOCK_FALL, 2.0f, 0.6f);
    }

    /**
     * A flat blast wave: throws everything within 14 blocks outwards, hurts a
     * little, shatters glass and leaves — and breaks nothing else.
     */
    static void shockwave(ServerLevel level, PrimedTnt t) {
        Vec3 c = t.position();
        LivingEntity owner = t.getOwner();
        for (Entity entity : around(level, c, Entity.class, 14)) {
            Vec3 away = entity.position().subtract(c).multiply(1, 0, 1);
            double near = 1 - Math.sqrt(entity.distanceToSqr(c)) / 14;
            Vec3 dir = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
            entity.setDeltaMovement(dir.scale(0.5 + near * 3).add(0, 0.4 + near * 0.4, 0));
            entity.hurtMarked = true;
            if (entity instanceof LivingEntity living) {
                living.hurtServer(level, level.damageSources().explosion(t, owner), (float) (1 + near * 4));
            }
        }
        for (BlockPos pos : sphere(t.blockPosition(), 8)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.IMPERMEABLE) || state.is(GLASS_PANES) || state.is(BlockTags.LEAVES)) {
                level.destroyBlock(pos, true);
            }
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, 0.6f);
        Tasks.start(age -> {
            double r = 1 + age * 1.5;
            int points = (int) (r * 6);
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points;
                level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(angle) * r, c.y + 0.3, c.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
            }
            return age >= 9;
        });
    }

    /**
     * The warden's scream: charges for a second, then a ring of sonic booms runs
     * out to 16 blocks. It goes through walls and ignores armour.
     */
    static void sonic(ServerLevel level, PrimedTnt t) {
        Vec3 c = t.position().add(0, 1, 0);
        Set<Entity> hit = new HashSet<>();
        sound(level, c, SoundEvents.WARDEN_SONIC_CHARGE, 3.0f, 1.0f);
        Tasks.start(age -> {
            if (age < 20) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 4, 0.5, 0.5, 0.5, 0.05);
                return false;
            }
            if (age == 20) sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 5.0f, 1.0f);
            double r = (age - 20) * 1.6 + 1;
            int points = Math.max(8, (int) (r * 2.5));
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points;
                level.sendParticles(ParticleTypes.SONIC_BOOM, c.x + Math.cos(angle) * r, c.y, c.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
            }
            for (LivingEntity entity : around(level, c, LivingEntity.class, r)) {
                if (!hit.add(entity)) continue;
                double dist = Math.sqrt(entity.distanceToSqr(c));
                entity.hurtServer(level, level.damageSources().sonicBoom(t), (float) Math.max(4, 14 * (1 - dist / 18)));
                Vec3 away = entity.position().subtract(c).multiply(1, 0, 1);
                if (away.lengthSqr() > 0.01) {
                    entity.setDeltaMovement(entity.getDeltaMovement().add(away.normalize().scale(1.2)).add(0, 0.3, 0));
                    entity.hurtMarked = true;
                }
            }
            return r >= 16;
        });
    }

    /** Pulls every dropped item and experience orb within 40 blocks into one pile. */
    static void magnet(ServerLevel level, PrimedTnt t) {
        Vec3 c = t.position().add(0, 0.5, 0);
        sound(level, c, SoundEvents.BEACON_ACTIVATE, 2.0f, 1.6f);
        Tasks.start(age -> {
            for (Entity entity : around(level, c, Entity.class, 40)) {
                if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) continue;
                if (age >= 60) {
                    // Whatever got stuck behind a wall comes along anyway.
                    entity.teleportTo(c.x, c.y, c.z);
                    entity.setDeltaMovement(Vec3.ZERO);
                } else {
                    Vec3 to = c.subtract(entity.position());
                    double dist = to.length();
                    entity.setDeltaMovement(dist < 1 ? Vec3.ZERO : to.normalize().scale(Math.min(1.2, 0.2 + dist * 0.08)));
                }
                entity.hurtMarked = true;
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 1.5, 1.5, 1.5, 0.1);
            return age >= 60;
        });
    }

    // --- building ----------------------------------------------------------

    /**
     * A stone-brick bridge 40 blocks long, three wide, with railings and a
     * lantern every eight blocks, laid out the way the player faced. It only
     * fills air, liquids and plants.
     */
    static void bridge(ServerLevel level, PrimedTnt t) {
        BlockPos start = t.blockPosition().below();
        Direction ahead = facing(t);
        Direction side = ahead.getClockWise();
        Tasks.start(age -> {
            for (int step = 0; step < 2; step++) {
                int i = age * 2 + step + 1;
                if (i > 40) return true;
                BlockPos mid = start.relative(ahead, i);
                if (!level.isLoaded(mid)) return true;
                for (int w = -1; w <= 1; w++) place(level, mid.relative(side, w), Blocks.STONE_BRICKS.defaultBlockState());
                for (int w = -1; w <= 1; w += 2) {
                    BlockPos rail = mid.relative(side, w).above();
                    place(level, rail, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    if (i % 8 == 0) place(level, rail.above(), Blocks.LANTERN.defaultBlockState());
                }
                sound(level, Vec3.atCenterOf(mid), SoundEvents.STONE_PLACE, 1.0f, 0.9f);
            }
            return false;
        });
    }

    /** A round stone-brick wall with battlements, radius 8, rising out of the ground layer by layer. */
    static void wall(ServerLevel level, PrimedTnt t) {
        BlockPos center = t.blockPosition();
        List<BlockPos> ring = new ArrayList<>();
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                int d = dx * dx + dz * dz;
                if (d > 56 && d <= 72) ring.add(center.offset(dx, 0, dz));
            }
        }
        Tasks.start(age -> {
            if (age % 2 != 0) return false;
            int layer = age / 2;
            for (BlockPos base : ring) {
                boolean merlon = Math.floorMod(base.getX() + base.getZ(), 2) == 0;
                if (layer < 6 || merlon) place(level, base.above(layer), Blocks.STONE_BRICKS.defaultBlockState());
            }
            sound(level, Vec3.atCenterOf(center.above(layer)), SoundEvents.STONE_PLACE, 2.0f, 0.7f);
            level.sendParticles(ParticleTypes.DUST_PLUME, center.getX() + 0.5, center.getY() + layer, center.getZ() + 0.5, 30, 6, 0.2, 6, 0.01);
            return layer >= 6;
        });
    }

    /** A glass dome, radius 7, built from the ground up — instant shelter. */
    static void dome(ServerLevel level, PrimedTnt t) {
        BlockPos center = t.blockPosition();
        sound(level, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 1.2f);
        Tasks.start(age -> {
            int dy = age;
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d > 42.25 && d <= 56.25) place(level, center.offset(dx, dy, dz), Blocks.GLASS.defaultBlockState());
                }
            }
            sound(level, Vec3.atCenterOf(center.above(dy)), SoundEvents.GLASS_PLACE, 1.0f, 1.0f);
            return dy >= 7;
        });
    }

    /** Spins cobwebs into the air around it — a trap for anything that follows. */
    static void cobweb(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        RandomSource random = level.getRandom();
        for (BlockPos pos : sphere(t.blockPosition(), 5)) {
            if (random.nextFloat() < 0.3f && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), FLAGS);
            }
        }
    }

    /** Lights up the dark: a torch every six blocks within twenty, wherever the ground is dark. */
    static void torch(ServerLevel level, PrimedTnt t) {
        BlockPos center = t.blockPosition();
        int placed = 0;
        for (int dx = -18; dx <= 18; dx += 6) {
            for (int dz = -18; dz <= 18; dz += 6) {
                if (dx * dx + dz * dz > 400) continue;
                for (int dy = 6; dy >= -6; dy--) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
                    if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                    if (level.getBrightness(LightLayer.BLOCK, pos) < 8) {
                        level.setBlock(pos, Blocks.TORCH.defaultBlockState(), FLAGS);
                        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
                        placed++;
                    }
                    break;
                }
            }
        }
        sound(level, t.position(), SoundEvents.FIRECHARGE_USE, 1.5f, placed > 0 ? 1.2f : 0.6f);
    }

    // --- nature --------------------------------------------------------

    /** Bone meal for everything within eight blocks: saplings grow, crops ripen, dirt turns green. */
    static void nature(ServerLevel level, PrimedTnt t) {
        RandomSource random = level.getRandom();
        for (BlockPos pos : sphere(t.blockPosition(), 8)) {
            BlockState state = level.getBlockState(pos);
            if ((state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)) && level.getBlockState(pos.above()).isAir()) {
                level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), FLAGS);
                continue;
            }
            if (!(state.getBlock() instanceof BonemealableBlock)) continue;
            // Grass spreads plants all around itself; a few blocks of it are plenty.
            if (state.is(Blocks.GRASS_BLOCK) && random.nextFloat() > 0.12f) continue;
            for (int round = 0; round < 4; round++) {
                BlockState now = level.getBlockState(pos);
                if (!(now.getBlock() instanceof BonemealableBlock grow) || !grow.isValidBonemealTarget(level, pos, now)) break;
                grow.performBonemeal(level, random, pos, now);
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
        }
        sound(level, t.position(), SoundEvents.BONE_MEAL_USE, 2.0f, 0.8f);
    }

    /** Harvests ripe crops within twelve blocks, replants them and drops the yield in one pile. */
    static void harvest(ServerLevel level, PrimedTnt t) {
        BlockPos center = t.blockPosition();
        List<ItemStack> pile = new ArrayList<>();
        List<BlockPos> area = BlockPos.betweenClosedStream(center.offset(-12, -3, -12), center.offset(12, 4, 12))
                .map(BlockPos::immutable)
                // From the top down, so a sugar cane is cut above its lowest block.
                .sorted(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed())
                .toList();
        for (BlockPos pos : area) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                addDrops(pile, Block.getDrops(state, level, pos, null));
                level.setBlock(pos, crop.getStateForAge(0), FLAGS);
            } else if (state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE) {
                addDrops(pile, Block.getDrops(state, level, pos, null));
                level.setBlock(pos, state.setValue(NetherWartBlock.AGE, 0), FLAGS);
            } else if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)
                    || (state.is(Blocks.SUGAR_CANE) && level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE))) {
                addDrops(pile, Block.getDrops(state, level, pos, null));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            }
        }
        dropPile(level, t.position(), pile);
        sound(level, t.position(), SoundEvents.CROP_BREAK, 2.0f, 1.0f);
        particles(level, ParticleTypes.HAPPY_VILLAGER, t.position(), 60, 6, 0);
    }

    /**
     * Fells the trees around it: every natural leaf within ten blocks and every
     * log connected to one. Logs in buildings have no natural leaves next to
     * them and stay. Wood, saplings and apples land in one pile.
     */
    static void lumber(ServerLevel level, PrimedTnt t) {
        BlockPos center = t.blockPosition();
        BlockPos min = center.offset(-10, -3, -10);
        BlockPos max = center.offset(10, 32, 10);
        AABB box = AABB.encapsulatingFullBlocks(min, max);
        Set<BlockPos> leaves = new LinkedHashSet<>();
        Set<BlockPos> logs = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
                BlockPos leaf = pos.immutable();
                leaves.add(leaf);
                queue.add(leaf);
            }
        }
        // Walk from the leaves into the logs they touch, and on through connected logs.
        while (!queue.isEmpty() && logs.size() < 3000) {
            BlockPos from = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(from.offset(-1, -1, -1), from.offset(1, 1, 1))) {
                if (!box.contains(Vec3.atCenterOf(next)) || logs.contains(next)) continue;
                if (level.getBlockState(next).is(BlockTags.LOGS)) {
                    BlockPos log = next.immutable();
                    logs.add(log);
                    queue.add(log);
                }
            }
        }
        List<ItemStack> pile = new ArrayList<>();
        for (Set<BlockPos> set : List.of(logs, leaves)) {
            for (BlockPos pos : set) {
                BlockState state = level.getBlockState(pos);
                if (!removable(level, pos, state)) continue;
                addDrops(pile, Block.getDrops(state, level, pos, null));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            }
        }
        dropPile(level, t.position(), pile);
        sound(level, t.position(), SoundEvents.WOOD_BREAK, 3.0f, 0.7f);
        particles(level, ParticleTypes.POOF, t.position().add(0, 4, 0), 80, 4, 0.02);
    }

    /** Turns the land around it into the Nether: netherrack, soul soil, crimson trees and fire. */
    static void nether(ServerLevel level, PrimedTnt t) {
        pop(level, t, 2);
        RandomSource random = level.getRandom();
        List<BlockPos> area = sphere(t.blockPosition(), 8);
        for (BlockPos pos : area) {
            BlockState state = level.getBlockState(pos);
            if (!removable(level, pos, state)) continue;
            BlockState into = null;
            if (state.is(Blocks.GRASS_BLOCK)) {
                into = Blocks.CRIMSON_NYLIUM.defaultBlockState();
            } else if (state.is(BlockTags.DIRT)) {
                into = Blocks.SOUL_SOIL.defaultBlockState();
            } else if (state.is(BlockTags.SAND)) {
                into = Blocks.SOUL_SAND.defaultBlockState();
            } else if (state.is(BlockTags.LOGS)) {
                into = Blocks.CRIMSON_STEM.defaultBlockState();
                if (state.hasProperty(BlockStateProperties.AXIS)) {
                    into = into.setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS));
                }
            } else if (state.is(BlockTags.LEAVES)) {
                into = Blocks.NETHER_WART_BLOCK.defaultBlockState();
            } else if (ground(state)) {
                float roll = random.nextFloat();
                into = (roll < 0.05f ? Blocks.MAGMA_BLOCK : roll < 0.08f ? Blocks.NETHER_QUARTZ_ORE : Blocks.NETHERRACK).defaultBlockState();
            } else if (state.getBlock() instanceof LiquidBlock && state.getFluidState().is(Fluids.WATER)) {
                into = Blocks.AIR.defaultBlockState();
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.02);
            } else if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
                into = Blocks.AIR.defaultBlockState();
            }
            if (into != null) level.setBlock(pos, into, QUIET);
        }
        for (BlockPos pos : area) {
            if (random.nextFloat() < 0.12f && level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.NETHERRACK)) {
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), FLAGS);
            }
        }
        particles(level, ParticleTypes.ASH, t.position(), 300, 6, 0.02);
    }

    // --- from the sky ----------------------------------------------------

    /** Six burning meteors come down within ten blocks, one after another. */
    static void meteor(ServerLevel level, PrimedTnt t) {
        Vec3 c = t.position();
        RandomSource random = level.getRandom();
        sound(level, c, SoundEvents.FIRECHARGE_USE, 3.0f, 0.5f);
        int height = Math.min(level.getMaxY() - 2, (int) c.y + 50);
        for (int i = 0; i < 6; i++) {
            int delay = i * 12;
            Tasks.start(new Tasks.Task() {
                FallingBlockEntity rock;
                Vec3 last;

                @Override
                public boolean tick(int age) {
                    if (age < delay) return false;
                    if (rock == null) {
                        BlockPos from = BlockPos.containing(c.x + (random.nextDouble() - 0.5) * 20, height, c.z + (random.nextDouble() - 0.5) * 20);
                        if (!level.isLoaded(from) || !level.getBlockState(from).isAir()) return true;
                        rock = FallingBlockEntity.fall(level, from, Blocks.MAGMA_BLOCK.defaultBlockState());
                        rock.disableDrop();
                        rock.setHurtsEntities(2.0f, 40);
                        rock.setDeltaMovement((random.nextDouble() - 0.5) * 0.4, -1.5, (random.nextDouble() - 0.5) * 0.4);
                        last = rock.position();
                        return false;
                    }
                    if (rock.isAlive() && age - delay < 200) {
                        last = rock.position();
                        level.sendParticles(ParticleTypes.FLAME, last.x, last.y + 0.5, last.z, 8, 0.3, 0.3, 0.3, 0.02);
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, last.x, last.y + 1, last.z, 4, 0.2, 0.2, 0.2, 0.01);
                        return false;
                    }
                    rock.discard();
                    level.explode(t, last.x, last.y, last.z, 4, true, Level.ExplosionInteraction.TNT);
                    return true;
                }
            });
        }
    }

    /** Two seconds of arrows raining down on a circle 24 blocks wide. */
    static void arrowRain(ServerLevel level, PrimedTnt t) {
        Vec3 c = t.position();
        RandomSource random = level.getRandom();
        LivingEntity owner = t.getOwner();
        sound(level, c, SoundEvents.ARROW_SHOOT, 3.0f, 0.6f);
        Tasks.start(age -> {
            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(random.nextDouble()) * 12;
                Arrow arrow = new Arrow(level, c.x + Math.cos(angle) * r, c.y + 22, c.z + Math.sin(angle) * r,
                        new ItemStack(Items.ARROW), null);
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                if (owner != null) arrow.setOwner(owner);
                arrow.setDeltaMovement((random.nextDouble() - 0.5) * 0.1, -2.2, (random.nextDouble() - 0.5) * 0.1);
                level.addFreshEntity(arrow);
            }
            return age >= 40;
        });
    }

    /** Lights the fuse of every TNT within 16 blocks, the nearest first — a chain reaction. */
    static void chain(ServerLevel level, PrimedTnt t) {
        pop(level, t, 2);
        BlockPos center = t.blockPosition();
        LivingEntity owner = t.getOwner();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -16, -16), center.offset(16, 16, 16))) {
            if (pos.distSqr(center) > 256 || !level.isLoaded(pos)) continue;
            if (level.getBlockState(pos).getBlock() instanceof TntBlock) found.add(pos.immutable());
            if (found.size() >= 256) break;
        }
        for (BlockPos pos : found) {
            BlockState state = level.getBlockState(pos);
            int fuse = 5 + (int) (Math.sqrt(pos.distSqr(center)) * 2);
            if (state.getBlock() instanceof ArsenalTntBlock tnt) {
                tnt.ignite(level, pos, state, owner, fuse);
            } else {
                PrimedTnt primed = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, owner);
                primed.setFuse(fuse);
                level.addFreshEntity(primed);
            }
            level.removeBlock(pos, false);
        }
    }

    /** All show, no damage: three waves of real fireworks in every colour. */
    static void firework(ServerLevel level, PrimedTnt t) {
        Vec3 c = t.position();
        RandomSource random = level.getRandom();
        int[] colours = {0xFF3B3B, 0xFFB12B, 0xFFE94A, 0x4BE36B, 0x3BC8FF, 0x6B5BFF, 0xE35BFF, 0xFFFFFF};
        FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
        Tasks.start(age -> {
            if (age % 10 == 0) {
                for (int i = 0; i < 4; i++) {
                    List<FireworkExplosion> bursts = new ArrayList<>();
                    for (int b = 0; b < 2; b++) {
                        int one = colours[random.nextInt(colours.length)];
                        int two = colours[random.nextInt(colours.length)];
                        bursts.add(new FireworkExplosion(shapes[random.nextInt(shapes.length)], IntList.of(one, two),
                                IntList.of(colours[random.nextInt(colours.length)]), random.nextBoolean(), random.nextBoolean()));
                    }
                    ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
                    rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + random.nextInt(2), bursts));
                    double x = c.x + (random.nextDouble() - 0.5) * 6;
                    double z = c.z + (random.nextDouble() - 0.5) * 6;
                    level.addFreshEntity(new FireworkRocketEntity(level, x, c.y + 0.5, z, rocket));
                }
            }
            return age >= 20;
        });
        level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 1, c.z, 60, 1, 1, 1, 0.2);
    }
}
