package fr.raksrinana.itempiping.blocks.pipe.pipe;

import fr.raksrinana.itempiping.api.IWrenchable;
import fr.raksrinana.itempiping.blocks.pipe.routing.Network;
import fr.raksrinana.itempiping.blocks.pipe.routing.Router;
import fr.raksrinana.itempiping.registry.TileEntityRegistry;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.IFluidState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

public abstract class PipeBlock extends SixWayBlock implements IBucketPickupHandler, ILiquidContainer, IWrenchable{
	protected static final DyeColor UNIVERSAL_COLOR = DyeColor.WHITE;
	
	public PipeBlock(){
		super(0.125f, Block.Properties.from(Blocks.IRON_BLOCK));
	}
	
	@Override
	public boolean allowsMovement(@Nonnull BlockState state, @Nonnull IBlockReader worldIn, @Nonnull BlockPos pos, @Nonnull PathType type){
		return false;
	}
	
	@Override
	public boolean hasTileEntity(BlockState state){
		return true;
	}
	
	@Override
	public TileEntity createTileEntity(BlockState state, IBlockReader world){
		return TileEntityRegistry.PIPE.create();
	}
	
	@Override
	@Deprecated
	public void onReplaced(BlockState state, @Nonnull World world, @Nonnull BlockPos pos, BlockState newState, boolean isMoving){
		if(!state.getBlock().equals(newState.getBlock())){
			if(!world.isRemote){
				Router.removePipe(world, pos);
			}
		}
		super.onReplaced(state, world, pos, newState, isMoving);
	}
	
	@Override
	public void onNeighborChange(BlockState state, IWorldReader world, BlockPos pos, BlockPos neighbor){
		if(!world.isRemote()){
			Router.neighborChange(world, pos, neighbor);
		}
		super.onNeighborChange(state, world, pos, neighbor);
	}
	
	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack){
		if(!world.isRemote){
			Router.attachPipe(world, pos);
		}
		super.onBlockPlacedBy(world, pos, state, placer, stack);
	}
	
	@Override
	protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder){
		super.fillStateContainer(builder);
		builder.add(BlockStateProperties.WATERLOGGED, NORTH, EAST, SOUTH, WEST, UP, DOWN);
	}
	
	@Override
	public BlockState getStateForPlacement(BlockItemUseContext context){
		BlockState state = super.getStateForPlacement(context);
		if(Objects.isNull(state)){
			state = this.getDefaultState();
		}
		state = preGetStateForPlacement(context, state);
		IBlockReader world = context.getWorld();
		BlockPos pos = context.getPos();
		IFluidState fluidState = context.getWorld().getFluidState(context.getPos());
		state = state.with(BlockStateProperties.WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
		for(Direction direction : FACING_TO_PROPERTY_MAP.keySet()){
			state = state.with(FACING_TO_PROPERTY_MAP.get(direction), canPipeConnect(world, pos, direction, state));
		}
		return state;
	}
	
	public BlockState preGetStateForPlacement(@Nonnull BlockItemUseContext context, @Nonnull BlockState state){
		return state;
	}
	
	public static boolean getConnection(@Nonnull BlockState state, @Nonnull Direction side){
		return state.get(FACING_TO_PROPERTY_MAP.get(side));
	}
	
	public boolean canPipeConnect(@Nonnull IBlockReader world, @Nonnull BlockPos pos, @Nonnull Direction direction, @Nonnull BlockState state){
		BlockPos otherPos = pos.offset(direction);
		BlockState otherState = world.getBlockState(otherPos);
		Block otherBlock = otherState.getBlock();
		if(otherBlock instanceof PipeBlock){
			final PipeBlock otherPipe = (PipeBlock) otherBlock;
			return canConnectOnSide(world, otherPos, direction.getOpposite()) && (isUniversalColor() || otherPipe.isUniversalColor() || Objects.equals(getColor(), otherPipe.getColor())) && !getPipeTileEntity(world, pos).map(te -> te.isSideDisabled(direction)).orElse(false);
		}
		TileEntity tileEntity = world.getTileEntity(pos.offset(direction));
		if(tileEntity != null){
			LazyOptional<IItemHandler> lazyOptional = tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite());
			return lazyOptional.isPresent();
		}
		return false;
	}
	
	protected static boolean canConnectOnSide(@Nonnull IBlockReader world, @Nonnull BlockPos pos, @Nonnull Direction side){
		return !getPipeTileEntity(world, pos).map(te -> te.isSideDisabled(side)).orElse(false);
	}
	
	protected boolean isUniversalColor(){
		return Objects.equals(getColor(), UNIVERSAL_COLOR);
	}
	
	public DyeColor getColor(){
		return UNIVERSAL_COLOR;
	}
	
	@Override
	@Nonnull
	public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos){
		if(stateIn.get(BlockStateProperties.WATERLOGGED)){
			worldIn.getPendingFluidTicks().scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickRate(worldIn));
		}
		BooleanProperty property = FACING_TO_PROPERTY_MAP.get(facing);
		return stateIn.with(property, canPipeConnect(worldIn, currentPos, facing, stateIn));
	}
	
	public static Optional<PipeTileEntity> getPipeTileEntity(@Nonnull IBlockReader blockReader, @Nonnull BlockPos pos){
		return Optional.ofNullable(blockReader.getTileEntity(pos)).filter(te -> te instanceof PipeTileEntity).map(PipeTileEntity.class::cast);
	}
	
	@Override
	public boolean canContainFluid(@Nonnull IBlockReader worldIn, @Nonnull BlockPos pos, BlockState state, @Nonnull Fluid fluidIn){
		return !state.get(BlockStateProperties.WATERLOGGED) && fluidIn == Fluids.WATER;
	}
	
	@Override
	public boolean receiveFluid(@Nonnull IWorld worldIn, @Nonnull BlockPos pos, BlockState state, @Nonnull IFluidState fluidStateIn){
		if(!state.get(BlockStateProperties.WATERLOGGED) && fluidStateIn.getFluid() == Fluids.WATER){
			if(!worldIn.isRemote()){
				worldIn.setBlockState(pos, state.with(BlockStateProperties.WATERLOGGED, true), 3);
				worldIn.getPendingFluidTicks().scheduleTick(pos, fluidStateIn.getFluid(), fluidStateIn.getFluid().getTickRate(worldIn));
			}
			return true;
		}
		else{
			return false;
		}
	}
	
	@Override
	@Nonnull
	public Fluid pickupFluid(@Nonnull IWorld worldIn, @Nonnull BlockPos pos, BlockState state){
		if(state.get(BlockStateProperties.WATERLOGGED)){
			worldIn.setBlockState(pos, state.with(BlockStateProperties.WATERLOGGED, false), 3);
			return Fluids.WATER;
		}
		else{
			return Fluids.EMPTY;
		}
	}
	
	@Override
	@Nonnull
	public IFluidState getFluidState(BlockState state){
		return state.get(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getStillFluidState(false) : super.getFluidState(state);
	}
	
	@Override
	public ActionResultType onWrench(ItemUseContext context){
		BlockPos pos = context.getPos();
		World world = context.getWorld();
		BlockState state = world.getBlockState(pos);
		Vec3d relative = context.getHitVec().subtract(pos.getX(), pos.getY(), pos.getZ());
		Direction side = getClickedConnection(relative);
		if(Objects.nonNull(side)){
			BlockPos otherPos = pos.offset(side);
			BlockState otherState = world.getBlockState(otherPos);
			Block otherBlock = otherState.getBlock();
			TileEntity otherPipeEntity = world.getTileEntity(otherPos);
			if(Objects.nonNull(otherPipeEntity) && otherPipeEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite()).isPresent()){
				disableConnection(world, pos, state, side);
				if(otherBlock instanceof PipeBlock){
					((PipeBlock) otherBlock).disableConnection(world, otherPos, otherState, side.getOpposite());
				}
				return ActionResultType.SUCCESS;
			}
		}
		return ActionResultType.PASS;
	}
	
	protected void disableConnection(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull Direction side){
		getPipeTileEntity(world, pos).ifPresent(te -> {
			te.disabledSide(side);
			BlockState newState = state.with(FACING_TO_PROPERTY_MAP.get(side), false);
			world.setBlockState(pos, newState, Constants.BlockFlags.BLOCK_UPDATE + Constants.BlockFlags.UPDATE_NEIGHBORS);
			Router.getNetworkFor(world, pos).ifPresent(Network::invalidate);
		});
	}
	
	@Nullable
	private static Direction getClickedConnection(Vec3d relative){
		if(relative.x < 0.25){
			return Direction.WEST;
		}
		if(relative.x > 0.75){
			return Direction.EAST;
		}
		if(relative.y < 0.25){
			return Direction.DOWN;
		}
		if(relative.y > 0.75){
			return Direction.UP;
		}
		if(relative.z < 0.25){
			return Direction.NORTH;
		}
		if(relative.z > 0.75){
			return Direction.SOUTH;
		}
		return null;
	}
}
