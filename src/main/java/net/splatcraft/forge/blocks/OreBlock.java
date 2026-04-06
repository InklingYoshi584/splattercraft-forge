package net.splatcraft.forge.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public class OreBlock extends Block
{
    public OreBlock()
    {
        super(Properties.of().instrument(NoteBlockInstrument.BASEDRUM).mapColor(MapColor.STONE).strength(3.0F, 3.0F).requiresCorrectToolForDrops());
    }
}