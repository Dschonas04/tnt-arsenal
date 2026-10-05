package io.github.dschonas04.tntarsenal;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registers one block and one block item per {@link Kind}, plus a creative tab
 * that lists them all. Everything else lives in the blocks themselves and in
 * the mixin that hands a detonation to the right kind.
 */
public final class TntArsenal implements ModInitializer {
    public static final String MOD_ID = "tnt_arsenal";
    public static final List<Item> ITEMS = new ArrayList<>();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Entry points for the server mixin; the task list itself stays package-private. */
    public static void tickTasks() {
        Tasks.tickAll();
    }

    public static void clearTasks() {
        Tasks.clear();
    }

    @Override
    public void onInitialize() {
        for (Kind kind : Kind.values()) {
            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id(kind.id()));
            Block block = new ArsenalTntBlock(kind, BlockBehaviour.Properties.ofFullCopy(Blocks.TNT).setId(blockKey));
            Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(kind.id()));
            // One line under the name says what this kind does.
            ItemLore lore = new ItemLore(List.of(Component.translatable("block.tnt_arsenal." + kind.id() + ".desc")
                    .withStyle(ChatFormatting.GRAY)));
            Item item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
                    .component(DataComponents.LORE, lore));
            Registry.register(BuiltInRegistries.ITEM, itemKey, item);
            ITEMS.add(item);
        }

        // The vanilla builder keeps its item output type protected, so the items
        // go in through Fabric's event for this tab instead.
        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("tnt"));
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.tnt_arsenal"))
                .icon(() -> new ItemStack(ITEMS.get(Kind.NUKE.ordinal())))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, tab);
        CreativeModeTabEvents.modifyOutputEvent(tabKey).register(output -> {
            // TabVisibility is protected as well; the stack lists are not.
            for (Item item : ITEMS) {
                output.getDisplayStacks().add(new ItemStack(item));
                output.getSearchTabStacks().add(new ItemStack(item));
            }
        });
    }
}
