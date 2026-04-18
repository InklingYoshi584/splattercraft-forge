package net.splatcraft.forge.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.splatcraft.forge.registries.SplatcraftItems;
import net.splatcraft.forge.tileentities.InkRailTileEntity;
import net.splatcraft.forge.util.InkRailUtils;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("deprecation")
public abstract class AbstractInkRailBlock extends Block implements IColoredBlock, EntityBlock
{
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty FLASHING = BooleanProperty.create("flashing");
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");

    private final VoxelShape shape;
    private final VoxelShape occupiedShape;

    protected AbstractInkRailBlock(Properties properties, VoxelShape shape, VoxelShape occupiedShape)
    {
        super(properties);
        this.shape = shape;
        this.occupiedShape = occupiedShape;
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false).setValue(FLASHING, false).setValue(OCCUPIED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(ACTIVE, FLASHING, OCCUPIED);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context)
    {
        return state.getValue(OCCUPIED) ? occupiedShape : shape;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult result)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(SplatcraftItems.inkRailNode.get()) && player.isCrouching())
            return InkRailUtils.handleLinkUse(level, pos, player, stack) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.FAIL;

        return super.use(state, level, pos, player, hand, result);
    }

    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving)
    {
        if (!state.is(newState.getBlock()))
            InkRailUtils.onRailBlockRemoved(level, pos);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean canClimb()
    {
        return false;
    }

    @Override
    public boolean canSwim()
    {
        return true;
    }

    @Override
    public boolean canDamage()
    {
        return false;
    }

    @Override
    public int getColor(Level level, BlockPos pos)
    {
        InkRailTileEntity host = InkRailUtils.getHost(level, pos);
        return host != null && host.isActive() ? host.getColor() : -1;
    }

    @Override
    public boolean remoteColorChange(Level level, BlockPos pos, int newColor)
    {
        return false;
    }

    @Override
    public boolean remoteInkClear(Level level, BlockPos pos)
    {
        return false;
    }
}
