package com.projecthero.mod.sentinel.entity;

import com.projecthero.mod.darkseid.entity.EnergyProjectile;
import com.projecthero.mod.sentinel.SentinelTargets;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.15.1: a Sentinel Program energy bolt -- the Drone's red laser, the Sentinel's chest beam and palm blast. A straight,
 * fast {@link EnergyProjectile} (so it is drawn as a glowing ribbon by the shared trail renderer and stopped by cover like
 * the Parademon bolts). Its {@link Kind} is synced so the client knows the colour and width.
 */
public class SentinelBeamEntity extends EnergyProjectile {
	public enum Kind {
		/** A Drone's thin red laser. */
		LASER(0xFF3020, 0xFFC0A0, 0.07f),
		/** A Sentinel's chest beam or palm blast. */
		BEAM(0xE040C0, 0xFFE0FF, 0.16f);

		final int color;
		final int core;
		final float width;

		Kind(int color, int core, float width) {
			this.color = color;
			this.core = core;
			this.width = width;
		}

		public int glowColor() {
			return color;
		}

		public int hotColor() {
			return core;
		}

		public float beamWidth() {
			return width;
		}
	}

	private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(SentinelBeamEntity.class, EntityDataSerializers.BYTE);
	private static final DustParticleOptions MAGENTA = new DustParticleOptions(new Vector3f(1.0f, 0.3f, 0.85f), 1.2f);

	private float damage = 4.0f;

	public SentinelBeamEntity(EntityType<? extends SentinelBeamEntity> type, Level level) {
		super(type, level);
	}

	/** Fires a bolt from {@code from} along {@code dir}. */
	public static SentinelBeamEntity fire(ServerLevel level, LivingEntity shooter, Kind kind, Vec3 from, Vec3 dir, float damage, double speed) {
		SentinelBeamEntity bolt = new SentinelBeamEntity(SentinelEntityTypes.SENTINEL_BEAM, level);
		bolt.setOwner(shooter);
		bolt.damage = damage;
		bolt.entityData.set(DATA_KIND, (byte) kind.ordinal());
		bolt.setPos(from.x, from.y, from.z);
		bolt.setDeltaMovement(dir.normalize().scale(speed));
		bolt.faceAlong(bolt.getDeltaMovement());
		level.addFreshEntity(bolt);
		level.playSound(null, from.x, from.y, from.z, kind == Kind.LASER ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_POWER_SELECT,
				SoundSource.HOSTILE, kind == Kind.LASER ? 0.5f : 1.0f, kind == Kind.LASER ? 2.0f : 1.6f);
		return bolt;
	}

	/** The point {@code target} will be at when a bolt of {@code speed} from {@code from} reaches it. */
	public static Vec3 leadAim(LivingEntity target, Vec3 from, double speed) {
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0);
		double flight = aim.distanceTo(from) / speed;
		Vec3 lead = target.getDeltaMovement().scale(Math.min(16.0, flight));
		if (target.onGround()) {
			lead = new Vec3(lead.x, 0.0, lead.z);
		}
		return aim.add(lead);
	}

	public Kind kind() {
		byte k = entityData.get(DATA_KIND);
		return k >= 0 && k < Kind.values().length ? Kind.values()[k] : Kind.BEAM;
	}

	public float damage() {
		return damage;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_KIND, (byte) Kind.BEAM.ordinal());
	}

	@Override
	protected int maxLife() {
		return 60;
	}

	@Override
	protected void steer(ServerLevel level) {
		// straight line: the lead at launch is all the aiming it gets
	}

	@Override
	protected boolean canHit(Entity target) {
		return target != getOwner() && !(target instanceof EnergyProjectile) && SentinelTargets.canTarget(target);
	}

	@Override
	protected void onStrikeEntity(ServerLevel level, Entity target) {
		if (target instanceof LivingEntity living) {
			Entity owner = getOwner();
			living.hurt(level.damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), damage);
		}
		pop(level);
	}

	@Override
	protected void onStrikeBlock(ServerLevel level, Vec3 at) {
		setPos(at.x, at.y, at.z);
		pop(level);
	}

	private void pop(ServerLevel level) {
		if (kind() == Kind.LASER) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 5, 0.1, 0.1, 0.1, 0.05);
		} else {
			level.sendParticles(MAGENTA, getX(), getY(), getZ(), 10, 0.25, 0.25, 0.25, 0.05);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.1);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.6f, 1.7f);
		}
	}

	@Override
	public int trailColor() {
		return kind().color;
	}

	@Override
	public int coreColor() {
		return kind().core;
	}

	@Override
	public float trailWidth() {
		return kind().width;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
