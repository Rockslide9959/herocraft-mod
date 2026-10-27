package com.projecthero.mod.quirk.fx;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirk framework: ONE short-lived, never-saved effect entity for every Quirk VFX ({@link QuirkFx.Kind}). It
 * does nothing in the world -- it is a carrier for a handful of synced numbers that {@code QuirkFxRenderer} turns into
 * code-drawn geometry on every client. Everything is set before it is added to the level, so the whole effect travels in
 * the one spawn + entity-data packet (no per-tick traffic: {@code updateInterval} is long and the client runs the
 * animation off its own age).
 *
 * <p>Build one with {@link QuirkFx} -- never construct it directly from a kit.
 */
public class QuirkFxEntity extends Entity {
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.INT);
	/** Start / end colour of the ramp (RGB; the alpha byte is unused). */
	private static final EntityDataAccessor<Integer> COLOUR_A = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> COLOUR_B = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.INT);
	/** Size: radius (blast, ring, decal, shards), width (ribbon, cone base) or height (spike). */
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.FLOAT);
	/** A second size: cone / beam / spike length, the ring's thickness, the flipbook's frame count... */
	private static final EntityDataAccessor<Float> SIZE2 = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.FLOAT);
	/** Packed: bits 0-3 easing, 4-7 style, 8-15 count, 16+ flags (see {@link QuirkFx}). */
	private static final EntityDataAccessor<Integer> PACKED = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.INT);
	/** A direction (cone, beam, spike tilt) or an end point (ribbon from A to B), world space. */
	private static final EntityDataAccessor<Vector3f> VEC = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.VECTOR3);
	/** An entity the effect rides along with (a trail ribbon records its path), or 0. */
	private static final EntityDataAccessor<Integer> FOLLOW = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.INT);
	/** A block state id (spikes and shards are textured from it), or 0 for the effect's own colour. */
	private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(QuirkFxEntity.class, EntityDataSerializers.INT);

	public QuirkFxEntity(EntityType<? extends QuirkFxEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
		builder.define(LIFE, 20);
		builder.define(COLOUR_A, 0xFFFFFF);
		builder.define(COLOUR_B, 0xFF8030);
		builder.define(SIZE, 1.0f);
		builder.define(SIZE2, 1.0f);
		builder.define(PACKED, 0);
		builder.define(VEC, new Vector3f(0, 1, 0));
		builder.define(FOLLOW, 0);
		builder.define(BLOCK, 0);
	}

	// ---------------------------------------------------------------- set before spawning (QuirkFx)

	void setup(int kind, int life, int colourA, int colourB, float size, float size2, int packed, Vec3 vec, int follow, int block) {
		entityData.set(KIND, kind);
		entityData.set(LIFE, Math.max(1, life));
		entityData.set(COLOUR_A, colourA & 0xFFFFFF);
		entityData.set(COLOUR_B, colourB & 0xFFFFFF);
		entityData.set(SIZE, size);
		entityData.set(SIZE2, size2);
		entityData.set(PACKED, packed);
		entityData.set(VEC, new Vector3f((float) vec.x, (float) vec.y, (float) vec.z));
		entityData.set(FOLLOW, follow);
		entityData.set(BLOCK, block);
	}

	// ---------------------------------------------------------------- synced reads (both sides)

	public QuirkFx.Kind kind() {
		return QuirkFx.Kind.byId(entityData.get(KIND));
	}

	public int life() {
		return entityData.get(LIFE);
	}

	public int colourA() {
		return entityData.get(COLOUR_A);
	}

	public int colourB() {
		return entityData.get(COLOUR_B);
	}

	public float size() {
		return entityData.get(SIZE);
	}

	public float size2() {
		return entityData.get(SIZE2);
	}

	public QuirkFx.Ease ease() {
		return QuirkFx.Ease.byId(entityData.get(PACKED) & 0xF);
	}

	public int style() {
		return (entityData.get(PACKED) >> 4) & 0xF;
	}

	public int count() {
		return (entityData.get(PACKED) >> 8) & 0xFF;
	}

	public boolean flag(int flag) {
		return (entityData.get(PACKED) & flag) != 0;
	}

	public Vector3f vec() {
		return entityData.get(VEC);
	}

	public int followId() {
		return entityData.get(FOLLOW);
	}

	public int blockStateId() {
		return entityData.get(BLOCK);
	}

	/** 0..1 through the effect's life at {@code partial}. */
	public float progress(float partial) {
		return Math.min(1f, (tickCount + partial) / (float) life());
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		int f = followId();
		if (f != 0) {
			Entity e = level().getEntity(f);
			if (e != null) {
				setPos(e.getX(), e.getY(), e.getZ());
			} else if (!level().isClientSide()) {
				discard();
				return;
			}
		}
		if (level().isClientSide() && tickCount == 1) {
			clientBurst();
		}
		if (!level().isClientSide() && tickCount > life() + 2) {
			discard();
		}
	}

	/** Client: the one-off particle burst some kinds add on their first tick (sparks, embers, steam, grit). */
	private void clientBurst() {
		Level l = level();
		var r = l.getRandom();
		switch (kind()) {
			case BLAST -> {
				float s = size();
				int n = Math.min(40, (int) (12 + s * 6));
				for (int i = 0; i < n; i++) {
					Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6 + 0.4, r.nextGaussian()).normalize().scale(0.08 + r.nextDouble() * 0.07 * s);
					l.addParticle(i % 3 == 0 ? QuirkParticles.EMBER : QuirkParticles.SPARK, getX(), getY(), getZ(), d.x, d.y, d.z);
				}
			}
			case RING -> {
				if (style() == QuirkFx.RING_STEAM) {
					for (int i = 0; i < 16; i++) {
						double a = i / 16.0 * Math.PI * 2;
						l.addParticle(QuirkParticles.STEAM, getX() + Math.cos(a) * 0.6, getY() + 0.2, getZ() + Math.sin(a) * 0.6, Math.cos(a) * 0.2,
								0.08, Math.sin(a) * 0.2);
					}
				}
			}
			case SHARDS, SPIKE -> {
				for (int i = 0; i < 8; i++) {
					l.addParticle(QuirkParticles.DUST_CHUNK, getX() + r.nextGaussian() * 0.4, getY() + 0.2, getZ() + r.nextGaussian() * 0.4,
							r.nextGaussian() * 0.08, 0.18 + r.nextDouble() * 0.15, r.nextGaussian() * 0.08);
				}
			}
			case CONE -> {
				if (style() == QuirkFx.CONE_FLAME) {
					Vector3f d = vec();
					for (int i = 0; i < 10; i++) {
						double sp = 0.3 + r.nextDouble() * 0.6;
						l.addParticle(QuirkParticles.EMBER, getX(), getY(), getZ(), d.x * sp + r.nextGaussian() * 0.06, d.y * sp + r.nextGaussian() * 0.06,
								d.z * sp + r.nextGaussian() * 0.06);
					}
				}
			}
			default -> {
			}
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
	public AABB getBoundingBoxForCulling() {
		float r = Math.max(size(), size2()) + 2.0f;
		AABB box = getBoundingBox().inflate(r, r, r);
		if (kind() == QuirkFx.Kind.RIBBON && !flag(QuirkFx.F_TRAIL)) {
			Vector3f e = vec();
			box = box.minmax(new AABB(e.x - 1, e.y - 1, e.z - 1, e.x + 1, e.y + 1, e.z + 1));
		}
		if (kind() == QuirkFx.Kind.RIBBON && flag(QuirkFx.F_TRAIL)) {
			box = box.inflate(16.0);
		}
		return box;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 160 * 160;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
