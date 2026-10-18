package com.projecthero.mod.miraculous.pegasus.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.pegasus.PegasusCombat;
import com.projecthero.mod.miraculous.pegasus.PegasusConfig;
import com.projecthero.mod.miraculous.pegasus.PegasusMoves;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: the thrown Horseshoe, spinning flat like a boomerang (R) -- out in a straight line to what he aimed at (a
 * locked creature is followed), {@link PegasusConfig#THROW_RANGE} at most, then home to his hand; everything it passes
 * through takes {@link PegasusConfig#THROW_DAMAGE} once out and once back; a wall turns it back early. Caught:
 * {@link PegasusMoves#shoeCaught}. Or one of the Volley's small golden horseshoes (Shift+R, {@link #SMALL}): straight
 * ahead, {@link PegasusConfig#VOLLEY_DAMAGE} to the first creature hit, gone on a hit, a wall or after
 * {@link PegasusConfig#VOLLEY_LIFE} ticks.
 */
public class HorseshoeEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HorseshoeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> SMALL = SynchedEntityData.defineId(HorseshoeEntity.class, EntityDataSerializers.BOOLEAN);

	private @Nullable UUID owner;
	private Vec3 start = Vec3.ZERO;
	private Vec3 aim = Vec3.ZERO;
	private Vec3 velocity = Vec3.ZERO;
	private int lockId = -1;
	private int outTicks = PegasusConfig.THROW_OUT_TICKS;
	private int age;
	private boolean returning;
	private final Set<Integer> hitOut = new HashSet<>();
	private final Set<Integer> hitBack = new HashSet<>();

	public HorseshoeEntity(EntityType<? extends HorseshoeEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER_ID, -1);
		builder.define(SMALL, false);
	}

	/** R: the boomerang throw from {@code from} at {@code aimAt} ({@code lock}, nullable, is followed). */
	public void launch(ServerPlayer thrower, Vec3 from, Vec3 aimAt, @Nullable LivingEntity lock) {
		owner = thrower.getUUID();
		entityData.set(OWNER_ID, thrower.getId());
		start = from;
		aim = aimAt;
		lockId = lock == null ? -1 : lock.getId();
		outTicks = Math.max(4, (int) Math.round(PegasusConfig.THROW_OUT_TICKS * Math.min(1.0, aimAt.distanceTo(from) / PegasusConfig.THROW_RANGE)));
		setPos(from.x, from.y, from.z);
	}

	/** Shift+R: one small horseshoe of the Volley, flying along {@code dir}. */
	public void launchSmall(ServerPlayer thrower, Vec3 from, Vec3 dir) {
		owner = thrower.getUUID();
		entityData.set(OWNER_ID, thrower.getId());
		entityData.set(SMALL, true);
		velocity = dir.normalize().scale(PegasusConfig.VOLLEY_SPEED);
		setDeltaMovement(velocity);
		setPos(from.x, from.y, from.z);
	}

	public int ownerEntityId() {
		return entityData.get(OWNER_ID);
	}

	public boolean small() {
		return entityData.get(SMALL);
	}

	public boolean returning() {
		return returning;
	}

	public int age() {
		return age;
	}

	private @Nullable ServerPlayer ownerPlayer() {
		if (owner == null || !(level() instanceof ServerLevel sl)) {
			return null;
		}
		return sl.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		ServerPlayer p = ownerPlayer();
		if (p == null || !p.isAlive() || p.level() != level()) {
			discard();
			return;
		}
		age++;
		if (small()) {
			tickSmall(p);
			return;
		}
		Vec3 prev = position();
		Vec3 next;
		if (!returning) {
			if (lockId >= 0 && level().getEntity(lockId) instanceof LivingEntity le && le.isAlive()) {
				aim = le.position().add(0, le.getBbHeight() * 0.5, 0);
			}
			double t = Math.min(1.0, age / (double) outTicks);
			double e = Math.sin(t * Math.PI / 2.0);
			next = start.lerp(aim, e);
			HitResult wall = level().clip(new ClipContext(prev, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (wall.getType() != HitResult.Type.MISS) {
				next = wall.getLocation().add(prev.subtract(next).normalize().scale(0.2));
				returning = true;
				level().playSound(null, next.x, next.y, next.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 1.9f);
			} else if (t >= 1.0) {
				returning = true;
			}
		} else {
			Vec3 hand = p.getEyePosition().add(0, -0.4, 0);
			Vec3 to = hand.subtract(prev);
			double d = to.length();
			if (d < 1.4 || age > PegasusConfig.THROW_MAX_TICKS) {
				PegasusMoves.shoeCaught(p);
				discard();
				return;
			}
			next = prev.add(to.scale(Math.min(1.0, PegasusConfig.THROW_RETURN_SPEED / d)));
		}
		setDeltaMovement(next.subtract(prev));
		setPos(next.x, next.y, next.z);
		hits(p, prev, next, returning ? hitBack : hitOut, PegasusConfig.THROW_DAMAGE, false);
		if (age % 3 == 0) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.25f, 1.8f);
		}
		if (level() instanceof ServerLevel sl) {
			sl.sendParticles(PegasusCombat.GOLD, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	private void tickSmall(ServerPlayer p) {
		Vec3 prev = position();
		Vec3 next = prev.add(velocity);
		HitResult wall = level().clip(new ClipContext(prev, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		boolean blocked = wall.getType() != HitResult.Type.MISS;
		if (blocked) {
			next = wall.getLocation();
		}
		setDeltaMovement(velocity);
		setPos(next.x, next.y, next.z);
		if (hits(p, prev, next, hitOut, PegasusConfig.VOLLEY_DAMAGE, true) > 0 || blocked || age > PegasusConfig.VOLLEY_LIFE) {
			if (level() instanceof ServerLevel sl) {
				sl.sendParticles(PegasusCombat.GOLD_BIG, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.0);
				sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.03);
			}
			discard();
			return;
		}
		if (level() instanceof ServerLevel sl && age % 2 == 0) {
			sl.sendParticles(PegasusCombat.GOLD, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** Everything along this tick's stretch of the flight; {@code first}: stop at the first hit. Returns how many. */
	private int hits(ServerPlayer p, Vec3 prev, Vec3 next, Set<Integer> hit, float damage, boolean first) {
		AABB sweep = new AABB(prev, next).inflate(small() ? 0.4 : 0.7);
		int n = 0;
		for (Entity e : level().getEntities(this, sweep, e -> AbilityHelpers.asLiving(e) != null && e != p)) {
			LivingEntity le = AbilityHelpers.asLiving(e);
			if (le == null || hit.contains(le.getId()) || !PegasusCombat.isTarget(p, le)) {
				continue;
			}
			AABB bb = e.getBoundingBox().inflate(small() ? 0.3 : 0.5);
			if (bb.clip(prev, next).isEmpty() && !bb.contains(next)) {
				continue;
			}
			hit.add(le.getId());
			if (PegasusCombat.strike(p, le, prev, damage, small() ? 0.2 : 0.4, 0.05)) {
				level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.2f, 2.0f);
				n++;
				if (first) {
					return n;
				}
			}
		}
		return n;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 96 * 96;
	}
}
