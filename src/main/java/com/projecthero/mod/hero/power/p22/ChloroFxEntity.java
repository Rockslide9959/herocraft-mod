package com.projecthero.mod.hero.power.p22;

import java.util.UUID;

import org.joml.Vector3f;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Chlorokinesis: the short-lived plant effects, drawn from code geometry by {@code ChloroFxRenderer}. Never saved.
 * <ul>
 * <li>{@link #VINE} -- a vine from the owner's hand to a point or a creature (Vine Grapple; {@link #STYLE_LASH} = the
 * thorned whip of Vine Lash);</li>
 * <li>{@link #ROOTS} -- roots erupting round a Root Snare victim. This entity also does the holding: the victim is pinned
 * to the spot it was caught on and takes {@link PlantManipulationHandlers#SNARE_DPS} a second;</li>
 * <li>{@link #SPORES} -- the drifting Spore Cloud;</li>
 * <li>{@link #BLOOM} -- the ring of flowers of a Bloom Burst (it follows its owner);</li>
 * <li>{@link #OVERGROWTH} -- roots coiling up the caster during the gather, then a wave of root spikes out across the
 * radius;</li>
 * <li>{@link #SENSE} -- the green ring of a Root Sense pulse running out along the ground.</li>
 * </ul>
 */
public class ChloroFxEntity extends Entity {
	public static final int VINE = 1;
	public static final int ROOTS = 2;
	public static final int SPORES = 3;
	public static final int BLOOM = 4;
	public static final int OVERGROWTH = 5;
	public static final int SENSE = 6;

	public static final int STYLE_GRAPPLE = 0;
	public static final int STYLE_LASH = 1;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ChloroFxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MAX_LIFE = SynchedEntityData.defineId(ChloroFxEntity.class, EntityDataSerializers.INT);
	/** The entity this effect rides along with (the owner for vines / blooms / the gather), or 0. */
	private static final EntityDataAccessor<Integer> FOLLOW = SynchedEntityData.defineId(ChloroFxEntity.class, EntityDataSerializers.INT);
	/** The far end's creature (a lashed / reeled creature, a snare victim), or 0. */
	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(ChloroFxEntity.class, EntityDataSerializers.INT);
	/** The far end as a point, when there is no creature there. */
	private static final EntityDataAccessor<Vector3f> END = SynchedEntityData.defineId(ChloroFxEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(ChloroFxEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(ChloroFxEntity.class, EntityDataSerializers.INT);

	private UUID owner;
	/** Root Snare: the creature held, where it is held, whether it is only slowed (a boss). */
	private LivingEntity victim;
	private Vec3 anchor;
	private boolean onlySlowed;

	public ChloroFxEntity(EntityType<? extends ChloroFxEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public ChloroFxEntity(Level level, ServerPlayer owner, int kind, int life, Vec3 at) {
		this(PlantEntities.FX, level);
		this.owner = owner.getUUID();
		entityData.set(KIND, kind);
		entityData.set(MAX_LIFE, life);
		moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0f);
	}

	/** Spawns one effect and returns it. */
	public static ChloroFxEntity spawn(ServerPlayer owner, int kind, int life, Vec3 at) {
		ChloroFxEntity fx = new ChloroFxEntity(owner.level(), owner, kind, life, at);
		owner.level().addFreshEntity(fx);
		return fx;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
		builder.define(MAX_LIFE, 20);
		builder.define(FOLLOW, 0);
		builder.define(TARGET, 0);
		builder.define(END, new Vector3f());
		builder.define(RADIUS, 1.0f);
		builder.define(STYLE, 0);
	}

	// ---------------- setup ----------------

	public ChloroFxEntity follow(Entity e) {
		entityData.set(FOLLOW, e == null ? 0 : e.getId());
		return this;
	}

	public ChloroFxEntity target(Entity e) {
		entityData.set(TARGET, e == null ? 0 : e.getId());
		return this;
	}

	public ChloroFxEntity end(Vec3 v) {
		entityData.set(END, new Vector3f((float) v.x, (float) v.y, (float) v.z));
		return this;
	}

	public ChloroFxEntity radius(float r) {
		entityData.set(RADIUS, r);
		return this;
	}

	public ChloroFxEntity style(int s) {
		entityData.set(STYLE, s);
		return this;
	}

	// ---------------- synced reads (both sides) ----------------

	public int kind() {
		return entityData.get(KIND);
	}

	public int maxLife() {
		return entityData.get(MAX_LIFE);
	}

	public int followId() {
		return entityData.get(FOLLOW);
	}

	public int targetId() {
		return entityData.get(TARGET);
	}

	public Vector3f endPoint() {
		return entityData.get(END);
	}

	public float radius() {
		return entityData.get(RADIUS);
	}

	public int style() {
		return entityData.get(STYLE);
	}

	public UUID ownerId() {
		return owner;
	}

	/** Root Snare's victim (server side; gametests), or null. */
	public LivingEntity victim() {
		return victim;
	}

	// ---------------- Root Snare: the hold ----------------

	/** The roots take hold of {@code e} where it stands: pinned (a boss only slowed) for this effect's life. */
	public void hold(LivingEntity e) {
		victim = e;
		anchor = e.position();
		onlySlowed = !PlantManipulationHandlers.movable(e);
		target(e);
		if (onlySlowed) {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, maxLife(), 2);
		} else {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, maxLife(), 6);
			e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
			e.hurtMarked = true;
		}
	}

	/** Whether {@code e} is held by any live Root Snare right now. */
	public static boolean isHeld(LivingEntity e) {
		if (e.level().isClientSide()) {
			return false;
		}
		AABB box = e.getBoundingBox().inflate(2.0);
		for (ChloroFxEntity fx : e.level().getEntitiesOfClass(ChloroFxEntity.class, box, f -> f.kind() == ROOTS)) {
			if (fx.victim == e && !fx.onlySlowed) {
				return true;
			}
		}
		return false;
	}

	private void holdTick(ServerLevel level) {
		LivingEntity v = victim;
		if (v == null) {
			return;
		}
		if (!v.isAlive() || v.level() != level) {
			victim = null;
			return;
		}
		if (!onlySlowed) {
			v.setPos(anchor.x, Math.min(v.getY(), anchor.y + 0.05), anchor.z);
			v.setDeltaMovement(0, Math.min(0, v.getDeltaMovement().y), 0);
			v.hurtMarked = true;
			if (v instanceof Mob m) {
				m.getNavigation().stop();
				m.setJumping(false);
			}
		}
		if (tickCount % 20 == 10) {
			ServerPlayer p = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
			if (p != null) {
				AbilityHelpers.hurt(p, v, PlantManipulationHandlers.SNARE_DPS);
			}
			level.sendParticles(ChloroParticles.SAP, v.getX(), v.getY() + 0.3, v.getZ(), 3, 0.25, 0.2, 0.25, 0.0);
		}
	}

	// ---------------- tick ----------------

	@Override
	public void tick() {
		super.tick();
		int f = followId();
		if (f != 0) {
			Entity e = level().getEntity(f);
			if (e != null) {
				setPos(e.getX(), e.getY(), e.getZ());
			}
		}
		if (kind() == ROOTS && victim == null && targetId() != 0) {
			// client: sit at the victim's feet
			Entity t = level().getEntity(targetId());
			if (t != null && level().isClientSide()) {
				setPos(t.getX(), getY(), t.getZ());
			}
		}
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (tickCount > maxLife()) {
			discard();
			return;
		}
		if (kind() == ROOTS) {
			holdTick(level);
		} else if (kind() == OVERGROWTH) {
			ServerPlayer p = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
			if (p == null) {
				discard();
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		victim = null;
		super.remove(reason);
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

	/** Vines run far from this little entity and blooms / waves spread wide: keep them drawn while any part shows. */
	@Override
	public AABB getBoundingBoxForCulling() {
		if (kind() == VINE) {
			Vector3f e = endPoint();
			return getBoundingBox().minmax(new AABB(e.x - 1, e.y - 1, e.z - 1, e.x + 1, e.y + 1, e.z + 1)).inflate(2.0);
		}
		float r = radius() + 2.0f;
		return getBoundingBox().inflate(r, 3.0, r);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128 * 128;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
