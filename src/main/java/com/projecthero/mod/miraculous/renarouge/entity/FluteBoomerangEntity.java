package com.projecthero.mod.miraculous.renarouge.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.renarouge.RenaRougeCombat;
import com.projecthero.mod.miraculous.renarouge.RenaRougeConfig;
import com.projecthero.mod.miraculous.renarouge.RenaRougeMoves;

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
 * v0.16.6, G: the thrown Flute, spinning like a boomerang. Outbound it follows a bowed arc ({@link RenaRougeConfig#THROW_RANGE}
 * out, {@link RenaRougeConfig#THROW_CURVE} to the side at its widest) over {@link RenaRougeConfig#THROW_OUT_TICKS}, then
 * homes back to her hand; every creature it passes through takes {@link RenaRougeConfig#THROW_DAMAGE} once on the way out
 * and once on the way back. A wall turns it back early. Caught: {@link RenaRougeMoves#fluteCaught}.
 */
public class FluteBoomerangEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(FluteBoomerangEntity.class, EntityDataSerializers.INT);

	private @Nullable UUID owner;
	private Vec3 start = Vec3.ZERO;
	/** Where the outbound arc ends: the locked creature's centre (followed each tick) or the aimed spot. */
	private Vec3 aim = Vec3.ZERO;
	/** The creature the throw was aimed at (-1 = none). */
	private int lockId = -1;
	private Vec3 side = new Vec3(1, 0, 0);
	private int outTicks = RenaRougeConfig.THROW_OUT_TICKS;
	private int age;
	private boolean returning;
	private final Set<Integer> hitOut = new HashSet<>();
	private final Set<Integer> hitBack = new HashSet<>();

	public FluteBoomerangEntity(EntityType<? extends FluteBoomerangEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER_ID, -1);
	}

	/**
	 * Thrown by {@code thrower} from {@code from} at {@code aimAt} (v0.16.6: the arc ENDS there, so what is under the
	 * crosshair is hit), curving to her right; {@code lock} (nullable) is followed if it moves.
	 */
	public void launch(ServerPlayer thrower, Vec3 from, Vec3 aimAt, @Nullable LivingEntity lock) {
		owner = thrower.getUUID();
		entityData.set(OWNER_ID, thrower.getId());
		start = from;
		aim = aimAt;
		lockId = lock == null ? -1 : lock.getId();
		Vec3 dir = aimAt.subtract(from);
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		side = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(-flat.z, 0, flat.x).normalize();
		// the same speed as a full-range throw, so a near target is reached sooner
		outTicks = Math.max(5, (int) Math.round(RenaRougeConfig.THROW_OUT_TICKS * Math.min(1.0, dir.length() / RenaRougeConfig.THROW_RANGE)));
		setPos(from.x, from.y, from.z);
	}

	public int ownerEntityId() {
		return entityData.get(OWNER_ID);
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
		Vec3 prev = position();
		Vec3 next;
		if (!returning) {
			// follow a locked target that moves
			if (lockId >= 0 && level().getEntity(lockId) instanceof LivingEntity le && le.isAlive()) {
				aim = le.position().add(0, le.getBbHeight() * 0.5, 0);
			}
			// a quadratic arc from the hand that lands exactly on the aim point, bowed to the right mid-flight
			double t = Math.min(1.0, age / (double) outTicks);
			double e = Math.sin(t * Math.PI / 2.0);
			double dist = aim.distanceTo(start);
			double bow = Math.min(RenaRougeConfig.THROW_CURVE, dist * RenaRougeConfig.THROW_CURVE_PER_BLOCK);
			Vec3 control = start.add(aim).scale(0.5).add(side.scale(bow * 2.0));
			next = start.scale((1 - e) * (1 - e)).add(control.scale(2 * (1 - e) * e)).add(aim.scale(e * e));
			HitResult wall = level().clip(new ClipContext(prev, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (wall.getType() != HitResult.Type.MISS) {
				next = wall.getLocation().add(prev.subtract(next).normalize().scale(0.2));
				returning = true;
				level().playSound(null, next.x, next.y, next.z, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.7f, 1.4f);
			} else if (t >= 1.0) {
				returning = true;
			}
		} else {
			Vec3 hand = p.getEyePosition().add(0, -0.35, 0);
			Vec3 to = hand.subtract(prev);
			double d = to.length();
			if (d < 1.4 || age > RenaRougeConfig.THROW_MAX_TICKS) {
				RenaRougeMoves.fluteCaught(p, this);
				discard();
				return;
			}
			next = prev.add(to.scale(Math.min(1.0, RenaRougeConfig.THROW_RETURN_SPEED / d)));
		}
		setPos(next.x, next.y, next.z);
		// everything along this tick's stretch of the flight
		AABB sweep = new AABB(prev, next).inflate(0.7);
		Set<Integer> hit = returning ? hitBack : hitOut;
		for (Entity e : level().getEntities(this, sweep, e -> AbilityHelpers.asLiving(e) != null && e != p)) {
			LivingEntity le = AbilityHelpers.asLiving(e);
			if (le == null || hit.contains(le.getId()) || !RenaRougeCombat.isTarget(p, le)) {
				continue;
			}
			if (e.getBoundingBox().inflate(0.5).clip(prev, next).isEmpty() && !e.getBoundingBox().inflate(0.5).contains(next)) {
				continue;
			}
			hit.add(le.getId());
			if (RenaRougeCombat.strike(p, le, prev, RenaRougeConfig.THROW_DAMAGE, 0.35, 0.05)) {
				level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.3f);
			}
		}
		if (age % 3 == 0) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.25f, 1.9f);
		}
		if (level() instanceof ServerLevel sl && age % 2 == 0) {
			sl.sendParticles(RenaRougeCombat.ORANGE, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
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
