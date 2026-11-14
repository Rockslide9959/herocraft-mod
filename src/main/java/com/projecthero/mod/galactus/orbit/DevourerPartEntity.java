package com.projecthero.mod.galactus.orbit;

import java.util.UUID;

import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusFx;
import com.projecthero.mod.galactus.entity.GalactusMob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2: one of the Devourer's separate hit-boxes (the friend's idea: "his arms and head have diff
 * hitboxes"). A cube of {@link #size()} the Devourer moves onto his skeleton every tick; it never loses health itself --
 * every hit is handed to him ({@link DevourerEntity#onPartHit}) and the raid decides what it is worth (the Stage 1 stagger
 * bar, the Stage 3 multipliers, the sealed core). A living entity so every power, arrow and sword in the mod can hit it.
 * Never saved.
 *
 * <p>v0.17.4: a {@link com.projecthero.mod.entity.BigHitboxes.Big} entity -- its box is far wider than an entity section,
 * so area searches (the crosshair pick, arrows, powers) find it through the per-level list instead of the section lookup
 * that kept missing it. On the client it snaps to each position the server sends (no 3-tick glide), so the box stays on
 * the fist the renderer draws while it moves. A hit that lands flashes him red, thuds and throws sparks where it struck.
 */
public class DevourerPartEntity extends GalactusMob implements com.projecthero.mod.entity.BigHitboxes.Big {
	public enum Kind {
		HEAD, CHEST, UPPER_R, FORE_R, HAND_R, UPPER_L, FORE_L, HAND_L;

		public boolean rightArm() {
			return this == UPPER_R || this == FORE_R || this == HAND_R;
		}

		public boolean leftArm() {
			return this == UPPER_L || this == FORE_L || this == HAND_L;
		}

		public boolean hand() {
			return this == HAND_R || this == HAND_L;
		}

		public boolean arm() {
			return this == UPPER_R || this == FORE_R || this == UPPER_L || this == FORE_L;
		}

		/** Where it sits on the solved skeleton (world). */
		public Vec3 centre(GalactusRig.Pose p) {
			return switch (this) {
				case HEAD -> p.head.lerp(p.face, 0.35).add(0, 9 * p.k, 0);
				case CHEST -> p.chest;
				case UPPER_R -> p.shoulderR.lerp(p.elbowR, 0.5);
				case FORE_R -> p.elbowR.lerp(p.fistR, 0.45);
				case HAND_R -> p.fistR;
				case UPPER_L -> p.shoulderL.lerp(p.elbowL, 0.5);
				case FORE_L -> p.elbowL.lerp(p.fistL, 0.45);
				case HAND_L -> p.fistL;
			};
		}

		/** The cube's edge (blocks) at {@code k} blocks per model unit. */
		public float size(float k) {
			return switch (this) {
				case HEAD -> 22f * k;
				case CHEST -> 30f * k;
				case UPPER_R, UPPER_L -> 15f * k;
				case FORE_R, FORE_L -> 15f * k;
				case HAND_R, HAND_L -> 16f * k;
			};
		}
	}

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(DevourerPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(DevourerPartEntity.class, EntityDataSerializers.FLOAT);

	private UUID ownerId;

	public DevourerPartEntity(EntityType<? extends DevourerPartEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		noPhysics = true;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 1000.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KIND, 0);
		builder.define(DATA_SIZE, 4f);
	}

	@Override
	public String model() {
		return null;
	}

	public void configure(UUID owner, Kind kind) {
		this.ownerId = owner;
		entityData.set(DATA_KIND, kind.ordinal());
	}

	public Kind kind() {
		int k = entityData.get(DATA_KIND);
		return Kind.values()[Math.max(0, Math.min(Kind.values().length - 1, k))];
	}

	public float size() {
		return entityData.get(DATA_SIZE);
	}

	public void setSize(float s) {
		entityData.set(DATA_SIZE, s);
		refreshDimensions();
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_SIZE.equals(key)) {
			refreshDimensions();
		}
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		float s = entityData == null ? 4f : size();
		return EntityDimensions.scalable(s, s);
	}

	public DevourerEntity owner() {
		return ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof DevourerEntity d ? d : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel && tickCount > 5) {
			DevourerEntity d = owner();
			if (d == null || !d.ownsPart(this)) {
				discard();
			}
		}
	}

	/** Client: straight to the server's position (the default glide would trail the drawn fist by three ticks). */
	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		setPos(x, y, z);
	}

	@Override
	public void aiStep() {
		if (level().isClientSide) {
			// the client still has to follow the server (position interpolation lives in LivingEntity.aiStep)
			super.aiStep();
			return;
		}
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!(level() instanceof ServerLevel server) || isRemoved()) {
			return false;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (source.getEntity() instanceof LivingEntity a && GalactusCombat.isCosmic(a)) {
			return false;
		}
		DevourerEntity d = owner();
		if (d == null || amount <= 0f) {
			return false;
		}
		invulnerableTime = 0;
		boolean took = d.onPartHit(server, this, source, amount);
		if (took) {
			hurtTime = 10;
			hurtDuration = 10;
			hitFeedback(server, d, source);
		}
		setHealth(getMaxHealth());
		return took;
	}

	/** Where a hit struck: the point of the box nearest the attacker (the box centre for sourceless hits). */
	public Vec3 struckAt(DamageSource source) {
		net.minecraft.world.phys.AABB b = getBoundingBox();
		Vec3 from = source.getSourcePosition();
		if (source.getEntity() instanceof LivingEntity a && source.getDirectEntity() == a) {
			from = a.getEyePosition();
		}
		if (from == null) {
			return b.getCenter();
		}
		return new Vec3(Mth.clamp(from.x, b.minX, b.maxX), Mth.clamp(from.y, b.minY, b.maxY), Mth.clamp(from.z, b.minZ, b.maxZ));
	}

	/** A hit that counted: he flashes red (the client's hurt tint on the whole model), a heavy thud and sparks at the spot. */
	private void hitFeedback(ServerLevel server, DevourerEntity d, DamageSource source) {
		Vec3 at = struckAt(source);
		server.broadcastDamageEvent(d, source);
		GalactusFx.forced(server, ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.6, 0.6, 0.6, 0.45);
		GalactusFx.forced(server, ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.5, 0.5, 0.5, 0.25);
		GalactusFx.forced(server, GalactusFx.COSMIC_SMALL, at.x, at.y, at.z, 6, 0.8, 0.8, 0.8, 0.05);
		server.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 1.6f, 0.55f + server.random.nextFloat() * 0.15f);
		server.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.35f, 0.4f);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}
}
