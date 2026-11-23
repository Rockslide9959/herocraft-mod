package com.projecthero.mod.temple.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the Dart Trap's brain. Every {@link #SCAN} ticks it measures its line (out of the mouth, to the first block
 * with a collision shape, at most {@link #RANGE}) and looks for a survival player whose box crosses the line's height
 * ({@link #lineY}) above the trap's block. Then: click + puff ({@link #WINDUP} ticks), a dart (an arrow, no pickup,
 * Poison I 3 s) flies along the line at that height, and it rests {@link #COOLDOWN} ticks. Nothing is saved.
 */
public class DartTrapBlockEntity extends BlockEntity {
	public static final int RANGE = 10;
	public static final int SCAN = 4;
	public static final int WINDUP = 5;
	public static final int COOLDOWN = 30;
	/** Line height above a knee trap's block (in the feet block): jumping clears it. */
	public static final double LINE_LOW = 0.35;
	/** Above a head trap's block (one up): 1.95 over the feet, so a crouching player (1.5 tall, plus the 0.3 an arrow's
	 * hit test adds) stays under it. */
	public static final double LINE_HIGH = 0.95;

	public static double lineY(BlockState state) {
		return state.getValue(DartTrapBlock.HIGH) ? LINE_HIGH : LINE_LOW;
	}
	public static final float DART_SPEED = 2.2f;
	/** A dart's hit test pads the target's box by this much up and down (as an arrow's does), so the trigger matches the hit. */
	public static final double HIT_PAD = 0.3;
	public static final double DART_DAMAGE = 1.2;

	private int windup;
	private int cooldown;
	public int fired;

	public DartTrapBlockEntity(BlockPos pos, BlockState state) {
		super(TempleBlocks.DART_TRAP_BE, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, DartTrapBlockEntity be) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		if (be.windup > 0) {
			if (--be.windup == 0) {
				be.fire(server, pos, state);
				be.cooldown = COOLDOWN;
			}
			return;
		}
		if (be.cooldown > 0) {
			be.cooldown--;
			return;
		}
		if (server.getGameTime() % SCAN != 0) {
			return;
		}
		if (be.target(server, pos, state) != null) {
			be.windup = WINDUP;
			server.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 1.2f, 0.7f);
			Vec3 m = mouth(pos, state);
			server.sendParticles(ParticleTypes.SMOKE, m.x, m.y, m.z, 3, 0.05, 0.05, 0.05, 0.01);
		}
	}

	/** The point a dart leaves from. */
	public static Vec3 mouth(BlockPos pos, BlockState state) {
		Direction f = state.getValue(DartTrapBlock.FACING);
		return new Vec3(pos.getX() + 0.5 + f.getStepX() * 0.6, pos.getY() + lineY(state), pos.getZ() + 0.5 + f.getStepZ() * 0.6);
	}

	/** How many blocks the line runs before it hits something solid (0..RANGE). */
	public static int lineLength(Level level, BlockPos pos, BlockState state) {
		Direction f = state.getValue(DartTrapBlock.FACING);
		BlockPos.MutableBlockPos m = pos.mutable();
		for (int i = 1; i <= RANGE; i++) {
			m.move(f);
			if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
				return i - 1;
			}
		}
		return RANGE;
	}

	/** A survival player crossing the line, or null. */
	public ServerPlayer target(ServerLevel level, BlockPos pos, BlockState state) {
		int len = lineLength(level, pos, state);
		if (len <= 0) {
			return null;
		}
		Direction f = state.getValue(DartTrapBlock.FACING);
		double y = pos.getY() + lineY(state);
		BlockPos far = pos.relative(f, len);
		Vec3 a = Vec3.atCenterOf(pos.relative(f)), b = Vec3.atCenterOf(far);
		AABB line = new AABB(Math.min(a.x, b.x) - 0.4, y - 0.01, Math.min(a.z, b.z) - 0.4, Math.max(a.x, b.x) + 0.4, y + 0.01, Math.max(a.z, b.z) + 0.4);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, line.inflate(0.0, 2.5, 0.0))) {
			if (p.gameMode.isSurvival() && p.isAlive() && p.getBoundingBox().inflate(0.0, HIT_PAD, 0.0).intersects(line)) {
				return p;
			}
		}
		return null;
	}

	void fire(ServerLevel level, BlockPos pos, BlockState state) {
		Direction f = state.getValue(DartTrapBlock.FACING);
		Vec3 from = mouth(pos, state);
		Arrow dart = new Arrow(EntityType.ARROW, level);
		dart.setPos(from.x, from.y, from.z);
		dart.setBaseDamage(DART_DAMAGE);
		dart.pickup = AbstractArrow.Pickup.DISALLOWED;
		dart.setNoGravity(true);
		dart.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
		dart.shoot(f.getStepX(), 0, f.getStepZ(), DART_SPEED, 0.0f);
		level.addFreshEntity(dart);
		fired++;
		level.playSound(null, pos, SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0f, 1.8f);
		level.sendParticles(ParticleTypes.POOF, from.x, from.y, from.z, 2, 0.02, 0.02, 0.02, 0.01);
	}

	/** Test seam: fire once now. */
	public void fireNow(ServerLevel level) {
		fire(level, worldPosition, getBlockState());
	}
}
