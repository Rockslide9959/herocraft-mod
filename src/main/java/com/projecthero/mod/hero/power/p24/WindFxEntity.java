package com.projecthero.mod.hero.power.p24;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Short-lived wind effects of the v0.16.5 Wind Manipulation rework, drawn by {@code WindFxRenderer}:
 * <ul>
 * <li>{@link #VACUUM} (G): a vortex at the aim point. It drags every enemy within {@link WindHandlers#VACUUM_RADIUS} in
 * over half a second, then holds them spinning round its eye for a second.</li>
 * <li>{@link #PUSH} (Shift+G): the expanding cone of gust streaks of a Wind Push.</li>
 * <li>{@link #DASH} (X): streak lines trailing the hero through a Tailwind dash (it follows its owner).</li>
 * <li>{@link #UPDRAFT} (Shift+X): a column of rising rings under the launch.</li>
 * </ul>
 * The direction is the entity's own yaw / pitch.
 */
public class WindFxEntity extends Entity {
	public static final int VACUUM = 1;
	public static final int PUSH = 2;
	public static final int DASH = 3;
	public static final int UPDRAFT = 4;

	/** Vacuum: the pull, then the hold (1 s), then a short fade. */
	public static final int VACUUM_PULL_TICKS = 10;
	public static final int VACUUM_HOLD_TICKS = 20;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(WindFxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MAX_LIFE = SynchedEntityData.defineId(WindFxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FOLLOW = SynchedEntityData.defineId(WindFxEntity.class, EntityDataSerializers.INT);

	private UUID owner;
	/** Vacuum: the creatures caught, with where each started. */
	private final List<LivingEntity> caught = new ArrayList<>();
	private final List<Vec3> caughtFrom = new ArrayList<>();

	public WindFxEntity(EntityType<? extends WindFxEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public WindFxEntity(Level level, ServerPlayer owner, int kind, int life, Vec3 at, float yaw, float pitch) {
		this(WindEntities.FX, level);
		this.owner = owner.getUUID();
		entityData.set(KIND, kind);
		entityData.set(MAX_LIFE, life);
		if (kind == DASH) {
			entityData.set(FOLLOW, owner.getId());
		}
		moveTo(at.x, at.y, at.z, yaw, pitch);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
		builder.define(MAX_LIFE, 20);
		builder.define(FOLLOW, 0);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public int maxLife() {
		return entityData.get(MAX_LIFE);
	}

	/** The entity a DASH trail follows (its owner), or 0. */
	public int follow() {
		return entityData.get(FOLLOW);
	}

	/** Vacuum: everything it has hold of right now (gametests). */
	public List<LivingEntity> caught() {
		return caught;
	}

	/** Vacuum: grabs every enemy in reach (called once, when it opens). */
	public void openVacuum(ServerPlayer p) {
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, position(), WindHandlers.VACUUM_RADIUS)) {
			if (e == p || !WindHandlers.movable(e)) {
				continue;
			}
			caught.add(e);
			caughtFrom.add(e.position());
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (follow() != 0) {
			Entity f = level().getEntity(follow());
			if (f != null) {
				setPos(f.getX(), f.getY(), f.getZ());
			}
		}
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (tickCount > maxLife()) {
			discard();
			return;
		}
		if (kind() == VACUUM) {
			vacuumTick(level);
		}
	}

	private void vacuumTick(ServerLevel level) {
		Vec3 eye = position();
		int t = tickCount;
		boolean holding = t <= VACUUM_PULL_TICKS + VACUUM_HOLD_TICKS;
		for (int i = 0; i < caught.size(); i++) {
			LivingEntity e = caught.get(i);
			if (!e.isAlive() || e.level() != level) {
				continue;
			}
			if (!holding) {
				// let go: they drop out of the eye
				e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 0, 0.2));
				e.hurtMarked = true;
				continue;
			}
			// each one sits on a small ring round the eye, the ring turning
			double ang = i * (Mth.TWO_PI / Math.max(1, caught.size())) + t * 0.22;
			double ring = Math.min(1.6, 0.5 + caught.size() * 0.18);
			Vec3 slot = eye.add(Math.cos(ang) * ring, -e.getBbHeight() * 0.5 + Math.sin(t * 0.3 + i) * 0.12, Math.sin(ang) * ring);
			Vec3 at;
			if (t <= VACUUM_PULL_TICKS) {
				double f = t / (double) VACUUM_PULL_TICKS;
				f = f * f * (3 - 2 * f);
				at = caughtFrom.get(i).lerp(slot, f);
			} else {
				at = slot;
			}
			e.setPos(at.x, at.y, at.z);
			e.setDeltaMovement(Vec3.ZERO);
			e.resetFallDistance();
			e.hurtMarked = true;
			if (e instanceof Mob m) {
				m.getNavigation().stop();
			}
		}
		if (t % 3 == 0 && holding) {
			level.sendParticles(ParticleTypes.CLOUD, eye.x, eye.y, eye.z, 2, 1.2, 0.6, 1.2, 0.02);
		}
		if (t == VACUUM_PULL_TICKS + VACUUM_HOLD_TICKS + 1) {
			level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 96 * 96;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
