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
	/** v0.16.6 G hold: the collapsing bubble of air round a Suffocate victim's head (it follows the victim). */
	public static final int SUFFOCATE = 5;
	/** v0.16.6 X hold: a Sky Step ring of air left under the feet. */
	public static final int STEP = 6;
	/** v0.16.6 C hold: the Downburst's down-rushing column and the ring it leaves on the ground. */
	public static final int DOWNBURST = 7;

	/** Vacuum: the pull, then the hold (1 s), then a short fade. */
	public static final int VACUUM_PULL_TICKS = 10;
	public static final int VACUUM_HOLD_TICKS = 20;
	/** Downburst: the column takes this long to come down before it lands. */
	public static final int DOWNBURST_DROP_TICKS = 8;

	private static final net.minecraft.resources.ResourceLocation SUFFOCATE_SLOW = com.projecthero.mod.ProjectHeroMod.id("wind_suffocate_slow");
	private static final net.minecraft.resources.ResourceLocation SUFFOCATE_JUMP = com.projecthero.mod.ProjectHeroMod.id("wind_suffocate_jump");
	/** Ids of creatures being suffocated right now (a player victim's client stops sprinting off the synced flag). */
	private static final java.util.Set<Integer> SUFFOCATED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Suffocate: the victim, and whether it is only slowed (a boss / anything the wind cannot carry). */
	private LivingEntity victim;
	private boolean victimOnlySlowed;

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

	// ---------------- v0.16.6 Suffocate ----------------

	/** Suffocate takes hold of {@code target}: this effect follows it, its jumps go and it slows (a boss only slows). */
	public void startSuffocate(LivingEntity target) {
		victim = target;
		victimOnlySlowed = !WindHandlers.movable(target);
		entityData.set(FOLLOW, target.getId());
		SUFFOCATED.add(target.getId());
		net.minecraft.world.entity.ai.attributes.AttributeInstance speed = target.getAttribute(
				net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SUFFOCATE_SLOW,
					victimOnlySlowed ? -0.35 : -0.25, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (!victimOnlySlowed) {
			net.minecraft.world.entity.ai.attributes.AttributeInstance jump = target.getAttribute(
					net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
			if (jump != null) {
				jump.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SUFFOCATE_JUMP, -1.0,
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			target.setSprinting(false);
		}
	}

	/** Whose effect this is (server side; gametests). */
	public UUID ownerId() {
		return owner;
	}

	/** Suffocate's victim (gametests), or null. */
	public LivingEntity victim() {
		return victim;
	}

	/** Whether {@code e} is being suffocated right now (synced as p24.suffocated for a player victim). */
	public static boolean isSuffocated(Entity e) {
		return SUFFOCATED.contains(e.getId());
	}

	public static void clearSessionState() {
		SUFFOCATED.clear();
	}

	private void releaseVictim() {
		if (victim == null) {
			return;
		}
		LivingEntity v = victim;
		victim = null;
		SUFFOCATED.remove(v.getId());
		net.minecraft.world.entity.ai.attributes.AttributeInstance speed = v.getAttribute(
				net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SUFFOCATE_SLOW);
		}
		net.minecraft.world.entity.ai.attributes.AttributeInstance jump = v.getAttribute(
				net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(SUFFOCATE_JUMP);
		}
	}

	private void suffocateTick(ServerLevel level) {
		LivingEntity v = victim;
		ServerPlayer p = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
		if (v == null || !v.isAlive() || v.level() != level || p == null) {
			discard();
			return;
		}
		if (!victimOnlySlowed) {
			v.setSprinting(false);
			if (v instanceof Mob m) {
				m.setJumping(false);
			}
		}
		if (tickCount % 20 == 0) {
			AbilityHelpers.hurtBurst(p, v, WindHandlers.SUFFOCATE_DPS);
			level.sendParticles(ParticleTypes.CLOUD, v.getX(), v.getEyeY(), v.getZ(), 4, 0.25, 0.15, 0.25, 0.01);
		}
		if (tickCount % 8 == 0) {
			// the air being drawn out of the bubble
			level.sendParticles(ParticleTypes.POOF, v.getX(), v.getEyeY(), v.getZ(), 1, 0.3, 0.2, 0.3, 0.01);
		}
	}

	// ---------------- v0.16.6 Downburst ----------------

	private void downburstTick(ServerLevel level) {
		if (tickCount != DOWNBURST_DROP_TICKS) {
			return;
		}
		ServerPlayer p = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
		if (p != null) {
			WindHandlers.downburstImpact(p, position());
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		releaseVictim();
		super.remove(reason);
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
		} else if (kind() == SUFFOCATE) {
			suffocateTick(level);
		} else if (kind() == DOWNBURST) {
			downburstTick(level);
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

	/** The Downburst column stands 16 blocks over its little entity: keep it drawn while any of it is on screen. */
	@Override
	public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
		if (kind() == DOWNBURST) {
			return getBoundingBox().inflate(4.0, 0.0, 4.0).expandTowards(0, 17.0, 0);
		}
		return super.getBoundingBoxForCulling();
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
