package com.projecthero.mod.vaultdungeon.block;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the turret's brain. Every {@link #SCAN_TICKS} it looks for the nearest player (not creative / spectator)
 * within {@link #RANGE} with a clear line from its lens; on a find it fires a burst of {@link #BURST} stun darts
 * {@link #SHOT_GAP} ticks apart, then rests {@link #REST_TICKS}. Nothing is saved (a reloaded turret just starts resting).
 * No static state.
 */
public class VaultTurretBlockEntity extends BlockEntity {
	public static final double RANGE = 14.0;
	public static final int SCAN_TICKS = 5;
	public static final int BURST = 3;
	public static final int SHOT_GAP = 4;
	public static final int REST_TICKS = 36;
	public static final double DART_DAMAGE = 1.5;
	public static final float DART_SPEED = 2.4f;

	private int cooldown = 20;
	private int shotsLeft;
	private int shotTimer;
	private ServerPlayer target;
	private int shotsFired;

	public VaultTurretBlockEntity(BlockPos pos, BlockState state) {
		super(VaultBlocks.TURRET_BE, pos, state);
	}

	/** Test seam: darts fired since this turret loaded. */
	public int shotsFired() {
		return shotsFired;
	}

	/** Where the darts leave: just under the turret (it hangs from the ceiling) or above it on a floor mount. */
	public Vec3 lens(Level level) {
		boolean ceiling = !level.getBlockState(worldPosition.above()).isAir();
		return Vec3.atCenterOf(worldPosition).add(0, ceiling ? -0.62 : 0.62, 0);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, VaultTurretBlockEntity be) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		if (be.shotsLeft > 0) {
			if (--be.shotTimer <= 0) {
				if (be.target != null && be.target.isAlive() && !be.target.isRemoved() && be.canSee(server, be.target)) {
					be.fire(server, be.target);
					be.shotsLeft--;
					be.shotTimer = SHOT_GAP;
				} else {
					be.shotsLeft = 0;
				}
				if (be.shotsLeft == 0) {
					be.cooldown = REST_TICKS;
					be.target = null;
				}
			}
			return;
		}
		if (be.cooldown > 0) {
			be.cooldown--;
			return;
		}
		if (server.getGameTime() % SCAN_TICKS != 0) {
			return;
		}
		ServerPlayer best = null;
		double bestSq = RANGE * RANGE;
		Vec3 lens = be.lens(server);
		for (ServerPlayer p : server.players()) {
			if (!p.gameMode.isSurvival() || !p.isAlive()) { // survival or adventure only
				continue;
			}
			double d = p.getEyePosition().distanceToSqr(lens);
			if (d < bestSq && be.canSee(server, p)) {
				bestSq = d;
				best = p;
			}
		}
		if (best != null) {
			be.target = best;
			be.shotsLeft = BURST;
			be.shotTimer = 6; // a short warning beep first
			server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.2f, 1.8f);
		}
	}

	boolean canSee(ServerLevel level, ServerPlayer p) {
		Vec3 from = lens(level);
		Vec3 to = p.getEyePosition().add(0, -0.4, 0);
		if (from.distanceToSqr(to) > RANGE * RANGE) {
			return false;
		}
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
	}

	void fire(ServerLevel level, ServerPlayer p) {
		Vec3 from = lens(level);
		Vec3 aim = p.position().add(p.getDeltaMovement().scale(3)).add(0, p.getBbHeight() * 0.55, 0);
		Vec3 dir = aim.subtract(from);
		Arrow dart = new Arrow(EntityType.ARROW, level);
		dart.setPos(from.x, from.y, from.z);
		dart.setBaseDamage(DART_DAMAGE);
		dart.pickup = AbstractArrow.Pickup.DISALLOWED;
		dart.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
		dart.shoot(dir.x, dir.y, dir.z, DART_SPEED, 2.5f);
		level.addFreshEntity(dart);
		shotsFired++;
		level.playSound(null, worldPosition, SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0f, 1.6f);
		level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 3, 0.05, 0.05, 0.05, 0.01);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 4, 0.08, 0.08, 0.08, 0.05);
	}
}
