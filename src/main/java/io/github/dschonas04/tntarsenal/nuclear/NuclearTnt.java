package io.github.dschonas04.tntarsenal.nuclear;

import io.github.dschonas04.tntarsenal.nuclear.block.NukeBlock;
import io.github.dschonas04.tntarsenal.nuclear.block.Tier;
import io.github.dschonas04.tntarsenal.nuclear.config.NukeConfig;
import io.github.dschonas04.tntarsenal.nuclear.entity.PrimedNuke;
import io.github.dschonas04.tntarsenal.nuclear.explosion.Jobs;
import io.github.dschonas04.tntarsenal.nuclear.network.NukeDetonatedPayload;
import io.github.dschonas04.tntarsenal.nuclear.radiation.Radiation;
import io.github.dschonas04.tntarsenal.nuclear.radiation.RadiationCommand;
import io.github.dschonas04.tntarsenal.nuclear.world.Contamination;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import io.github.dschonas04.tntarsenal.nuclear.item.DetonatorItem;
import io.github.dschonas04.tntarsenal.nuclear.item.GeigerCounter;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.dschonas04.tntarsenal.nuclear.alarm.SirenBlock;
import io.github.dschonas04.tntarsenal.nuclear.item.TimerItem;
import io.github.dschonas04.tntarsenal.nuclear.mob.IrradiatedZombies;
import io.github.dschonas04.tntarsenal.nuclear.protection.Protection;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Registers the bombs, the lit bomb entity, the detonator and the creative tab. */
public final class NuclearTnt implements ModInitializer {
    public static final String MOD_ID = "tnt_arsenal";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static final Map<Tier, NukeBlock> BOMBS = new EnumMap<>(Tier.class);
    public static final List<Item> ITEMS = new ArrayList<>();
    public static EntityType<PrimedNuke> PRIMED_NUKE;
    public static Item DETONATOR;
    public static Item GEIGER_COUNTER;
    public static Item TIMER;
    public static Block SIREN;
    public static Block BUNKER_DOOR;
    public static Block CHARRED_LOG;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Registers an item under the mod's name and puts it into the creative tab. */
    public static <T extends Item> T item(String path, Function<Item.Properties, T> make, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
        T item = Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
        ITEMS.add(item);
        return item;
    }

    /** Registers a block and its item. */
    public static <T extends Block> T block(String path, Function<BlockBehaviour.Properties, T> make, BlockBehaviour.Properties properties) {
        return block(path, make, properties, BlockItem::new);
    }

    public static <T extends Block> T block(String path, Function<BlockBehaviour.Properties, T> make, BlockBehaviour.Properties properties,
                                            BiFunction<Block, Item.Properties, Item> itemMaker) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
        item(path, p -> itemMaker.apply(block, p), new Item.Properties().useBlockDescriptionPrefix());
        return block;
    }

    @Override
    public void onInitialize() {
        NukeConfig.load();
        Jobs.register();
        Radiation.register();
        RadiationCommand.register();
        PayloadTypeRegistry.clientboundPlay().register(NukeDetonatedPayload.TYPE, NukeDetonatedPayload.CODEC);

        ResourceKey<Block> charredKey = ResourceKey.create(Registries.BLOCK, id("charred_log"));
        CHARRED_LOG = Registry.register(BuiltInRegistries.BLOCK, charredKey,
                new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG).setId(charredKey)));
        ResourceKey<Item> charredItemKey = ResourceKey.create(Registries.ITEM, id("charred_log"));
        ITEMS.add(Registry.register(BuiltInRegistries.ITEM, charredItemKey,
                new BlockItem(CHARRED_LOG, new Item.Properties().setId(charredItemKey).useBlockDescriptionPrefix())));

        for (Tier tier : Tier.values()) {
            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id(tier.id()));
            NukeBlock block = new NukeBlock(tier, BlockBehaviour.Properties.ofFullCopy(Blocks.TNT).setId(blockKey));
            Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
            BOMBS.put(tier, block);
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(tier.id()));
            ItemLore lore = new ItemLore(List.of(Component.translatable("block.tnt_arsenal." + tier.id() + ".desc")
                    .withStyle(ChatFormatting.GRAY)));
            Item item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
                    .rarity(tier == Tier.TSAR ? Rarity.EPIC : tier == Tier.NUKE ? Rarity.RARE : Rarity.UNCOMMON)
                    .component(DataComponents.LORE, lore));
            Registry.register(BuiltInRegistries.ITEM, itemKey, item);
            ITEMS.add(item);
        }

        ResourceKey<EntityType<?>> entityKey = ResourceKey.create(Registries.ENTITY_TYPE, id("primed_nuke"));
        PRIMED_NUKE = Registry.register(BuiltInRegistries.ENTITY_TYPE, entityKey,
                EntityType.Builder.<PrimedNuke>of(PrimedNuke::new, MobCategory.MISC)
                        .fireImmune().sized(0.98f, 0.98f).clientTrackingRange(10).updateInterval(10)
                        .build(entityKey));

        ResourceKey<Item> detonatorKey = ResourceKey.create(Registries.ITEM, id("detonator"));
        DETONATOR = Registry.register(BuiltInRegistries.ITEM, detonatorKey,
                new DetonatorItem(new Item.Properties().setId(detonatorKey).stacksTo(1)));
        ITEMS.add(DETONATOR);

        ResourceKey<Item> geigerKey = ResourceKey.create(Registries.ITEM, id("geiger_counter"));
        GEIGER_COUNTER = Registry.register(BuiltInRegistries.ITEM, geigerKey,
                new Item(new Item.Properties().setId(geigerKey).stacksTo(1)
                        .component(DataComponents.LORE, new ItemLore(List.of(
                                Component.translatable("item.tnt_arsenal.geiger_counter.desc").withStyle(ChatFormatting.GRAY))))));
        ITEMS.add(GEIGER_COUNTER);
        GeigerCounter.register();

        TIMER = item("timer", TimerItem::new, new Item.Properties().stacksTo(16)
                .component(DataComponents.LORE, new ItemLore(List.of(
                        Component.translatable("item.tnt_arsenal.timer.desc").withStyle(ChatFormatting.GRAY)))));
        SIREN = block("siren", SirenBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3f, 6f));
        BUNKER_DOOR = block("bunker_door", p -> new DoorBlock(BlockSetType.COPPER, p) {
                }, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_DOOR).strength(10f, 1200f).noOcclusion(),
                DoubleHighBlockItem::new);
        Protection.register();
        IrradiatedZombies.register();
        Contamination.register();

        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("nuclear"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.tnt_arsenal.nuclear"))
                .icon(() -> new ItemStack(BOMBS.get(Tier.TSAR)))
                .build());
        CreativeModeTabEvents.modifyOutputEvent(tabKey).register(output -> {
            for (Item item : ITEMS) {
                output.getDisplayStacks().add(new ItemStack(item));
                output.getSearchTabStacks().add(new ItemStack(item));
            }
        });
    }
}
