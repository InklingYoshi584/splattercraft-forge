package net.splatcraft.forge.registries;

import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegistryObject;
import net.splatcraft.forge.blocks.BarrierBarBlock;
import net.splatcraft.forge.blocks.CanvasBlock;
import net.splatcraft.forge.blocks.ColoredBarrierBlock;
import net.splatcraft.forge.blocks.CrateBlock;
import net.splatcraft.forge.blocks.DebrisBlock;
import net.splatcraft.forge.blocks.EmptyInkwellBlock;
import net.splatcraft.forge.blocks.GrateBlock;
import net.splatcraft.forge.blocks.GrateRampBlock;
import net.splatcraft.forge.blocks.InkStainedBlock;
import net.splatcraft.forge.blocks.InkStainedSlabBlock;
import net.splatcraft.forge.blocks.InkStainedStairBlock;
import net.splatcraft.forge.blocks.InkVatBlock;
import net.splatcraft.forge.blocks.InkedBlock;
import net.splatcraft.forge.blocks.InkedCarpetBlock;
import net.splatcraft.forge.blocks.InkedGlassBlock;
import net.splatcraft.forge.blocks.InkedGlassPaneBlock;
import net.splatcraft.forge.blocks.InkwellBlock;
import net.splatcraft.forge.blocks.MetalBlock;
import net.splatcraft.forge.blocks.OreBlock;
import net.splatcraft.forge.blocks.RemotePedestalBlock;
import net.splatcraft.forge.blocks.SpawnPadBlock;
import net.splatcraft.forge.blocks.SplatSwitchBlock;
import net.splatcraft.forge.blocks.StageBarrierBlock;
import net.splatcraft.forge.blocks.TarpBlock;
import net.splatcraft.forge.blocks.WeaponWorkbenchBlock;

import static net.splatcraft.forge.Splatcraft.MODID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class SplatcraftBlocks
{
    protected static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    public static final ArrayList<Block> inkColoredBlocks = new ArrayList<>();

    public static final RegistryObject<InkedBlock> inkedBlock = REGISTRY.register("inked_block", InkedBlock::new);
    public static final RegistryObject<InkedBlock> glowingInkedBlock = REGISTRY.register("glowing_inked_block", InkedBlock::glowing);
    public static final RegistryObject<InkedBlock> clearInkedBlock = REGISTRY.register("clear_inked_block", InkedBlock::new);

    public static final RegistryObject<Block> sardiniumBlock = REGISTRY.register("sardinium_block", () -> new MetalBlock(MapColor.TERRACOTTA_WHITE));
    public static final RegistryObject<Block> rawSardiniumBlock = REGISTRY.register("raw_sardinium_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).requiresCorrectToolForDrops().strength(5, 6)));
    public static final RegistryObject<Block> sardiniumOre = REGISTRY.register("sardinium_ore", OreBlock::new);
    public static final RegistryObject<Block> powerEggBlock = REGISTRY.register("power_egg_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(DyeColor.ORANGE).pushReaction(PushReaction.DESTROY).sound(SoundType.SLIME_BLOCK).strength(0.2f, 0).lightLevel((state) -> 9)));
    public static final RegistryObject<CrateBlock> crate = REGISTRY.register("crate", () -> new CrateBlock(false));
    public static final RegistryObject<CrateBlock> sunkenCrate = REGISTRY.register("sunken_crate", () -> new CrateBlock(true));
    public static final RegistryObject<Block> ammoKnightsDebris = REGISTRY.register("ammo_knights_debris", () -> new DebrisBlock(MapColor.EMERALD));
    public static final RegistryObject<Block> coralite = REGISTRY.register("coralite", () -> new InkStainedBlock.WithUninkedVariant(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.BASEDRUM).mapColor(MapColor.CLAY).strength(3, 3).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> coraliteSlab = REGISTRY.register("coralite_slab", () -> new InkStainedSlabBlock.WithUninkedVariant(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.BASEDRUM).mapColor(MapColor.CLAY).strength(3, 3).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> coraliteStairs = REGISTRY.register("coralite_stairs", () -> new InkStainedStairBlock.WithUninkedVariant(() -> coralite.get().defaultBlockState(), BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.BASEDRUM).mapColor(MapColor.CLAY).strength(3, 3).requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> inkVat = REGISTRY.register("ink_vat", InkVatBlock::new);
    public static final RegistryObject<Block> weaponWorkbench = REGISTRY.register("ammo_knights_workbench", () -> new WeaponWorkbenchBlock());
    public static final RegistryObject<Block> remotePedestal = REGISTRY.register("remote_pedestal", RemotePedestalBlock::new);

    public static final RegistryObject<Block> emptyInkwell = REGISTRY.register("empty_inkwell", () -> new EmptyInkwellBlock(Block.Properties.of().instrument(NoteBlockInstrument.HAT).isRedstoneConductor((blockState, blockGetter, blockPos) -> false).strength(0.3F).sound(SoundType.GLASS)));
    public static final RegistryObject<Block> inkwell = REGISTRY.register("inkwell", InkwellBlock::new);

    public static final RegistryObject<Block> inkedWool = REGISTRY.register("ink_stained_wool", () -> new InkStainedBlock(BlockBehaviour.Properties.of().ignitedByLava().mapColor(MapColor.WOOL).strength(0.8F).sound(SoundType.WOOL)));
    public static final RegistryObject<Block> inkedCarpet = REGISTRY.register("ink_stained_carpet", () -> new InkedCarpetBlock());
    public static final RegistryObject<Block> inkedGlass = REGISTRY.register("ink_stained_glass", () -> new InkedGlassBlock());
    public static final RegistryObject<Block> inkedGlassPane = REGISTRY.register("ink_stained_glass_pane", InkedGlassPaneBlock::new);
    public static final RegistryObject<Block> canvas = REGISTRY.register("canvas", CanvasBlock::new);
    public static final RegistryObject<Block> splatSwitch =  REGISTRY.register("splat_switch", SplatSwitchBlock::new);
    public static final RegistryObject<SpawnPadBlock> spawnPad =  REGISTRY.register("spawn_pad", SpawnPadBlock::new);
    public static final RegistryObject<Block> spawnPadEdge =  REGISTRY.register("spawn_pad_edge", () -> new SpawnPadBlock.Aux(spawnPad.get()));

    public static final RegistryObject<Block> grate =  REGISTRY.register("grate", GrateBlock::new);
    public static final RegistryObject<Block> grateRamp =  REGISTRY.register("grate_ramp", GrateRampBlock::new);
    public static final RegistryObject<Block> barrierBar =  REGISTRY.register("barrier_bar", BarrierBarBlock::new);
    public static final RegistryObject<Block> cautionBarrierBar =  REGISTRY.register("caution_barrier_bar", BarrierBarBlock::new);
    public static final RegistryObject<Block> platedBarrierBar =  REGISTRY.register("plated_barrier_bar", BarrierBarBlock::new);
    public static final RegistryObject<Block> tarp =  REGISTRY.register("tarp", TarpBlock::new);
    public static final RegistryObject<Block> glassCover =  REGISTRY.register("glass_cover", TarpBlock.Seethrough::new);
    public static final RegistryObject<Block> stageBarrier =  REGISTRY.register("stage_barrier", () -> new StageBarrierBlock(false));
    public static final RegistryObject<Block> stageVoid =  REGISTRY.register("stage_void", () -> new StageBarrierBlock(true));
    public static final RegistryObject<Block> allowedColorBarrier =  REGISTRY.register("allowed_color_barrier", () -> new ColoredBarrierBlock(false));
    public static final RegistryObject<Block> deniedColorBarrier =  REGISTRY.register("denied_color_barrier", () -> new ColoredBarrierBlock(true));


    public static void setRenderLayers()
    {
        ItemBlockRenderTypes.setRenderLayer(inkedGlass.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(inkedGlassPane.get(), RenderType.translucent());

        ItemBlockRenderTypes.setRenderLayer(emptyInkwell.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(inkwell.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(spawnPad.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(grate.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(grateRamp.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(crate.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(sunkenCrate.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ammoKnightsDebris.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(remotePedestal.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(splatSwitch.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(glassCover.get(), RenderType.cutout());
    }


    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class MissingMappingsEventSubscriber
    {
        private static final HashMap<String, RegistryObject<? extends Block>> remaps = new HashMap<>() {{
            put("inked_wool", inkedWool);
            put("inked_carpet", inkedCarpet);
            put("inked_glass", inkedGlass);
            put("inked_glass_pane", inkedGlassPane);
            put("weapon_workbench", weaponWorkbench);

            put("inked_stairs", inkedBlock);
            put("inked_slab", inkedBlock);
            put("tall_inked_block", inkedBlock);
            put("glowing_inked_stairs", inkedBlock);
            put("glowing_inked_slab", inkedBlock);
            put("tall_glowing_inked_block", inkedBlock);
            put("tall_clear_inked_block", inkedBlock);
        }};

        @SubscribeEvent
        public static void onMissingMappings(final MissingMappingsEvent event)
        {
            for(MissingMappingsEvent.Mapping<Block> block : event.getMappings(REGISTRY.getRegistryKey(), MODID))
            {
                String key = block.getKey().getPath();
                if(remaps.containsKey(key))
                    block.remap(remaps.get(key).get());
            }
        }
    }
}