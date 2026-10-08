package io.github.dschonas04.tntarsenal.nuclear.world;

import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;

/** What a detonation leaves behind: fallout, irradiated earth, trinitite and contaminated water. */
public final class Contamination {
    private Contamination() {
    }

    public static FlowingFluid WATER;
    public static FlowingFluid FLOWING_WATER;
    public static Block WATER_BLOCK;
    public static Item WATER_BUCKET;
    public static Block FALLOUT;
    public static Block IRRADIATED_EARTH;
    public static Block TRINITITE;

    public static void register() {
        WATER = Registry.register(BuiltInRegistries.FLUID, NuclearTnt.id("contaminated_water"), new ContaminatedWater.Source());
        FLOWING_WATER = Registry.register(BuiltInRegistries.FLUID, NuclearTnt.id("flowing_contaminated_water"), new ContaminatedWater.Flowing());
        ResourceKey<Block> waterKey = ResourceKey.create(Registries.BLOCK, NuclearTnt.id("contaminated_water"));
        WATER_BLOCK = Registry.register(BuiltInRegistries.BLOCK, waterKey,
                new LiquidBlock(WATER, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.COLOR_LIGHT_GREEN).setId(waterKey)) {
                });
        ResourceKey<Item> bucketKey = ResourceKey.create(Registries.ITEM, NuclearTnt.id("contaminated_water_bucket"));
        WATER_BUCKET = Registry.register(BuiltInRegistries.ITEM, bucketKey,
                new BucketItem(WATER, new Item.Properties().setId(bucketKey).craftRemainder(Items.BUCKET).stacksTo(1)));
        NuclearTnt.ITEMS.add(WATER_BUCKET);

        FALLOUT = block("fallout", key -> new FalloutBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW)
                .mapColor(MapColor.COLOR_LIGHT_GREEN).lightLevel(state -> 4).setId(key)));
        IRRADIATED_EARTH = block("irradiated_earth", key -> new IrradiatedEarthBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COARSE_DIRT)
                .mapColor(MapColor.COLOR_GRAY).randomTicks().setId(key)));
        TRINITITE = block("trinitite", key -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                .strength(3f, 6f).mapColor(MapColor.COLOR_GREEN).lightLevel(state -> 2).setId(key)));
    }

    private static Block block(String name, java.util.function.Function<ResourceKey<Block>, Block> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, NuclearTnt.id(name));
        Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(key));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, NuclearTnt.id(name));
        NuclearTnt.ITEMS.add(Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
        return block;
    }
}
