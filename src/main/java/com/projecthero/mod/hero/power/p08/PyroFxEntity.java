package com.projecthero.mod.hero.power.p08;

import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Pyrokinesis: a short-lived fire shape with a model of its own ({@code PyroFxRenderer}) -- never saved, no
 * physics. {@link #WALL} is the V Flame Wall (8 wide, 3 tall, along its yaw; it burns whatever crosses it and burns up
 * arrows), {@link #NOVA} the ring of fire of an overheat / Inferno (radius {@link #size()}), {@link #WAVE} the C Heat
 * Wave's rolling cone of heat and {@link #BLAST} a Meteor's fireball blast; v0.16.6 adds {@link #RING} (hold V) and
 * {@link #FLARE} (hold C).
 */
public class PyroFxEntity extends Entity {
	public static final int WALL = 0;
	public static final int NOVA = 1;
	public static final int WAVE = 2;
	public static final int BLAST = 3;
	/** v0.16.6 hold V: the Ring of Fire (radius {@link #size()}, burns what crosses it). */
	public static final int RING = 4;
	/** v0.16.6 hold C: the Solar Flare's sun burst (and the white flash it puts on nearby screens). */
	public static final int FLARE = 5;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(PyroFxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(PyroFxEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(PyroFxEntity.class, EntityDataSerializers.INT);
	/** v0.16.6: the caster's entity id (the Solar Flare's flash is softer on their own screen). */
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PyroFxEntity.class, EntityDataSerializers.INT);

	private UUID ownerId;
	private int age;
	private float wallDamage;

	public PyroFxEntity(EntityType<? extends PyroFxEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static PyroFxEntity spawn(ServerLevel level, ServerPlayer owner, int kind, Vec3 at, float yaw, float size, int life) {
		return spawn(level, owner, kind, at, yaw, 0.0f, size, life);
	}

	public static PyroFxEntity spawn(ServerLevel level, ServerPlayer owner, int kind, Vec3 at, float yaw, float pitch, float size,
			int life) {
		PyroFxEntity e = new PyroFxEntity(PyroEntities.FX, level);
		e.moveTo(at.x, at.y, at.z, yaw, pitch);
		e.ownerId = owner == null ? null : owner.getUUID();
		e.entityData.set(KIND, kind);
		e.entityData.set(SIZE, size);
		e.entityData.set(LIFE, life);
		e.entityData.set(OWNER, owner == null ? -1 : owner.getId());
		level.addFreshEntity(e);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, WALL);
		builder.define(SIZE, 1.0f);
		builder.define(LIFE, 20);
		builder.define(OWNER, -1);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public float size() {
		return entityData.get(SIZE);
	}

	public int life() {
		return entityData.get(LIFE);
	}

	/** The caster's entity id, or -1. */
	public int ownerEntityId() {
		return entityData.get(OWNER);
	}

	/** Ticks lived (client: counted from when it appeared). */
	public int age() {
		return tickCount;
	}

	void setWallDamage(float d) {
		wallDamage = d;
	}

	/** The Flame Wall's footprint: {@link PyrokinesisHandlers#WALL_WIDTH} along its yaw, 1 thick, 3 tall. */
	public AABB wallBox() {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		// the wall runs across the yaw direction (sideways to where it was cast)
		double ax = Math.cos(yaw);
		double az = Math.sin(yaw);
		double half = PyrokinesisHandlers.WALL_WIDTH * 0.5;
		Vec3 a = position().add(ax * half, 0, az * half);
		Vec3 b = position().subtract(ax * half, 0, az * half);
		return new AABB(Math.min(a.x, b.x) - 0.5, getY(), Math.min(a.z, b.z) - 0.5, Math.max(a.x, b.x) + 0.5, getY() + 3.0,
				Math.max(a.z, b.z) + 0.5);
	}

	/** Distance (blocks) of {@code p} from the wall's centre line, across it -- for the "it crossed" test. */
	public double acrossWall(Vec3 p) {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		double nx = -Math.sin(yaw);
		double nz = Math.cos(yaw);
		return (p.x - getX()) * nx + (p.z - getZ()) * nz;
	}

	/** Distance along the wall from its middle. */
	public double alongWall(Vec3 p) {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		return (p.x - getX()) * Math.cos(yaw) + (p.z - getZ()) * Math.sin(yaw);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel sl)) {
			return;
		}
		age++;
		if (age > life()) {
			discard();
			return;
		}
		if (kind() == WALL) {
			ServerPlayer owner = ownerId == null ? null : sl.getServer().getPlayerList().getPlayer(ownerId);
			wallTick(sl, owner);
		} else if (kind() == RING) {
			ringTick(sl);
		}
	}

	/** v0.16.6: the Ring of Fire burns every half second; it goes out with its caster (logout, death, another power). */
	private void ringTick(ServerLevel sl) {
		ServerPlayer owner = ownerId == null ? null : sl.getPlayerByUUID(ownerId) instanceof ServerPlayer sp ? sp : null;
		com.projecthero.mod.hero.Power active = owner == null ? null : com.projecthero.mod.hero.ExperimentalPowers.getActive(owner);
		if (owner == null || !owner.isAlive() || active == null || !PyrokinesisHandlers.KEY.equals(active.key())) {
			discard();
			return;
		}
		if (age % 10 == 0) {
			PyrokinesisHandlers.ringBurn(owner, sl, this, wallDamage);
		}
		if (age % 16 == 0) {
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.7f);
		}
		if (age % 3 == 0) {
			double a = random.nextDouble() * Math.PI * 2.0;
			sl.sendParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * size(), getY() + 0.4, getZ() + Math.sin(a) * size(), 2, 0.2, 0.3, 0.2, 0.02);
		}
	}

	private void wallTick(ServerLevel sl, ServerPlayer owner) {
		AABB box = wallBox();
		// arrows and other shots burn up in it
		for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, box.inflate(0.5), pr -> pr.isAlive() && !(pr instanceof PyroBoltEntity)
				&& (owner == null || pr.getOwner() != owner))) {
			if (Math.abs(acrossWall(pr.position())) < 1.2 && Math.abs(alongWall(pr.position())) < PyrokinesisHandlers.WALL_WIDTH * 0.5 + 0.5) {
				sl.sendParticles(ParticleTypes.LARGE_SMOKE, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
				sl.sendParticles(ParticleTypes.FLAME, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.04);
				sl.playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.6f);
				pr.discard();
			}
		}
		if (owner != null && age % 10 == 0) {
			PyrokinesisHandlers.wallBurn(owner, sl, this, wallDamage);
		}
		if (age % 10 == 0) {
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.9f, 0.8f);
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
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128.0 * 128.0;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
