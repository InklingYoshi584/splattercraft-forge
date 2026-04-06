package net.splatcraft.forge.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.splatcraft.forge.tileentities.container.WeaponWorkbenchContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class WeaponWorkbenchBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock
{

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape BOTTOM_LEFT = box(2, 0, 0, 5, 4, 16);
    protected static final VoxelShape BOTTOM_RIGHT = box(11, 0, 0, 14, 4, 16);
    protected static final VoxelShape BASE = box(1, 1, 1, 15, 16, 15);
    protected static final VoxelShape DETAIL = box(0, 8, 0, 16, 10, 16);
    protected static final VoxelShape HANDLE = box(5, 11, 0, 11, 12, 1);
    public static final VoxelShape[] SHAPES = createVoxelShapes(BOTTOM_LEFT, BOTTOM_RIGHT, BASE, DETAIL, HANDLE);
    private static final MutableComponent CONTAINER_NAME = Component.translatable("container.ammo_knights_workbench");

    public WeaponWorkbenchBlock()
    {
        super(Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).strength(2.0f).requiresCorrectToolForDrops());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    protected static VoxelShape modifyShapeForDirection(Direction facing, VoxelShape shape)
    {
        AABB bb = shape.bounds();

        return switch (facing) {
            case EAST -> Shapes.create(new AABB(1 - bb.minZ, bb.minY, 1 - bb.minX, 1 - bb.maxZ, bb.maxY, 1 - bb.maxX));
            case SOUTH -> Shapes.create(new AABB(1 - bb.maxX, bb.minY, 1 - bb.maxZ, 1 - bb.minX, bb.maxY, 1 - bb.minZ));
            case WEST -> Shapes.create(new AABB(bb.minZ, bb.minY, bb.minX, bb.maxZ, bb.maxY, bb.maxX));
            default -> shape;
        };
    }

    public static VoxelShape[] createVoxelShapes(VoxelShape... shapes)
    {
        VoxelShape[] result = new VoxelShape[4];

        for (int i = 0; i < 4; i++)
        {
            result[i] = Shapes.empty();
            for (VoxelShape shape : shapes)
            {
                result[i] = Shapes.or(result[i], modifyShapeForDirection(Direction.from2DDataValue(i), shape));
            }

        }

        return result;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level levelIn, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand handIn, @NotNull BlockHitResult hit)
    {
        if (levelIn.isClientSide())
        {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(getMenuProvider(state, levelIn, pos));
        return InteractionResult.CONSUME;

    }

    @Override
    public MenuProvider getMenuProvider(@NotNull BlockState state, @NotNull Level levelIn, @NotNull BlockPos pos)
    {
        return new SimpleMenuProvider((id, inventory, player) -> new WeaponWorkbenchContainer(inventory, ContainerLevelAccess.create(levelIn, pos), id), CONTAINER_NAME);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockPos blockpos = context.getClickedPos();
        FluidState fluidstate = context.getLevel().getFluidState(blockpos);
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    @Override
    public @NotNull FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter levelIn, @NotNull BlockPos pos, @NotNull CollisionContext context)
    {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }
}