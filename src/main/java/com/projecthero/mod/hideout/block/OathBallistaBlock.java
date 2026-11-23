package com.projecthero.mod.hideout.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.alarm.AlarmState;
import com.projecthero.mod.alarm.AlarmZone;
import com.projecthero.mod.alarm.AlarmZones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: an <b>Oath Ballista</b> -- the Oathbreaker Keep's wall engines. Every {@link #RELOAD_TICKS} it looses a
 * heavy bolt (~9 damage) at the nearest player within {@link #RANGE} blocks in its 120-degree field of fire, in sight --
 * but only once the keep is on its guard: its alarm zone is Suspicious or Alert (a ballista outside any zone always
 * shoots). Sneak past unseen and the crews never man them. Wood: an axe breaks it (and shuts it up for good).
 */
public class OathBallistaBlock extends BaseEntityBlock {
	public static final MapCodec<OathBallistaBlock> CODEC = simpleCodec(OathBallistaBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final int RELOAD_TICKS = 60;
	public static final double RANGE = 28.0;
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

	public OathBallistaBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
	}

	@Override
	protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new Entity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, HideoutBlocks.BALLISTA_ENTITY, (l, p, s, be) -> be.serverTick((ServerLevel) l, p, s));
	}

	/** Whether a ballista at {@code pos} is manned right now (its zone is on guard, or it has no zone). */
	public static boolean manned(ServerLevel level, BlockPos pos) {
		AlarmZone z = AlarmZones.get(level).zoneAt(pos);
		return z == null || z.state != AlarmState.CALM;
	}

	/** The nearest player in its field of fire, in sight, or null. */
	public static Player target(ServerLevel level, BlockPos pos, Direction facing) {
		Vec3 eye = Vec3.atCenterOf(pos).add(0, 0.2, 0);
		Vec3 f = new Vec3(facing.getStepX(), 0, facing.getStepZ());
		Player best = null;
		double bestD = RANGE * RANGE;
		for (Player p : level.players()) {
			if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
				continue;
			}
			Vec3 to = p.getEyePosition().subtract(eye);
			double d2 = to.lengthSqr();
			if (d2 > bestD || d2 < 1.0) {
				continue;
			}
			Vec3 flat = new Vec3(to.x, 0, to.z).normalize();
			if (flat.dot(f) < 0.5) {
				continue; // outside the 120-degree arc
			}
			if (level.clip(new ClipContext(eye.add(to.normalize().scale(0.8)), p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
					.getType() != HitResult.Type.MISS) {
				continue;
			}
			best = p;
			bestD = d2;
		}
		return best;
	}

	/** Looses one bolt at {@code target} (test hook too). */
	public static Arrow fire(ServerLevel level, BlockPos pos, Player target) {
		Vec3 from = Vec3.atCenterOf(pos).add(0, 0.25, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from);
		from = from.add(to.normalize().scale(0.9));
		Arrow bolt = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
		bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
		bolt.setBaseDamage(2.8);
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		bolt.shoot(to.x, to.y + flat * 0.04, to.z, 3.2f, 1.5f);
		level.addFreshEntity(bolt);
		level.playSound(null, pos, SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.6f, 0.5f);
		return bolt;
	}

	/** The ballista's reload clock (nothing saved: a reload restarts on load). */
	public static class Entity extends BlockEntity {
		private int cooldown = 20;

		public Entity(BlockPos pos, BlockState state) {
			super(HideoutBlocks.BALLISTA_ENTITY, pos, state);
		}

		void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
			if (cooldown > 0) {
				cooldown--;
				return;
			}
			cooldown = 10;
			if (!manned(level, pos)) {
				return;
			}
			Player p = target(level, pos, state.getValue(FACING));
			if (p != null) {
				fire(level, pos, p);
				cooldown = RELOAD_TICKS;
			}
		}
	}
}
