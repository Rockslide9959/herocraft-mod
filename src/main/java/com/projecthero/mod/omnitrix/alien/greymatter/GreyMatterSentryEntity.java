package com.projecthero.mod.omnitrix.alien.greymatter;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 round 3 (user: "make it so that when he presses Z on a block it he builds a different kind of turret on it please, make
 * it kinda powerful, 20 second cooldown, make the turret last 15 seconds"): the <b>Galvan Sentry</b> -- a knee-high tripod gun with
 * twin barrels, armoured in the block it was built on. A real creature: mobs it taunts walk up and hit it, anything can break it.
 * The aiming, the shots and the taunt are {@link GreyMatterKit}'s; the head's smooth tracking is client side, off the synced target.
 */
public class GreyMatterSentryEntity extends Mob {
	public static final EntityType<GreyMatterSentryEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("grey_matter_sentry")),
			EntityType.Builder.of(GreyMatterSentryEntity::new, MobCategory.MISC).sized(0.8f, 1.0f).fireImmune().noSave().clientTrackingRange(10).updateInterval(1)
					.build("grey_matter_sentry"));

	/** Height of the barrels' pivot above its feet. */
	public static final float HEAD_Y = 0.72f;
	/** He builds it over this many ticks, and it shoots only after. */
	public static final int DEPLOY_TICKS = 50; // v0.17.2 (user: "they should take a few seconds"): 2.5 s, was 0.5

	private static final EntityDataAccessor<BlockState> MATERIAL = SynchedEntityData.defineId(GreyMatterSentryEntity.class, EntityDataSerializers.BLOCK_STATE);
	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(GreyMatterSentryEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(GreyMatterSentryEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> SHOT_AT = SynchedEntityData.defineId(GreyMatterSentryEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Boolean> SHOT_LEFT = SynchedEntityData.defineId(GreyMatterSentryEntity.class, EntityDataSerializers.BOOLEAN);

	private int ownerMissing;
	long until;

	// client: the head's smoothed aim (degrees)
	public float aimYaw, aimYawO, aimPitch, aimPitchO;
	private boolean aimStarted;

	public GreyMatterSentryEntity(EntityType<? extends GreyMatterSentryEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, GreyMatterKit.SENTRY_HP).add(Attributes.MOVEMENT_SPEED, 0.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MATERIAL, Blocks.COBBLESTONE.defaultBlockState());
		builder.define(OWNER, Optional.empty());
		builder.define(TARGET, -1);
		builder.define(SHOT_AT, -1000L);
		builder.define(SHOT_LEFT, false);
	}

	void bind(UUID owner, BlockState material, long until) {
		entityData.set(OWNER, Optional.of(owner));
		entityData.set(MATERIAL, material);
		this.until = until;
	}

	public BlockState material() {
		return entityData.get(MATERIAL);
	}

	@Nullable
	public UUID ownerId() {
		return entityData.get(OWNER).orElse(null);
	}

	/** The creature it has locked, or null. */
	@Nullable
	public Entity target() {
		int id = entityData.get(TARGET);
		return id < 0 ? null : level().getEntity(id);
	}

	void target(@Nullable Entity e) {
		entityData.set(TARGET, e == null ? -1 : e.getId());
	}

	public long shotAt() {
		return entityData.get(SHOT_AT);
	}

	public boolean shotLeft() {
		return entityData.get(SHOT_LEFT);
	}

	void shot() {
		entityData.set(SHOT_LEFT, !entityData.get(SHOT_LEFT));
		entityData.set(SHOT_AT, level().getGameTime());
	}

	/** The world position of the gun's pivot. */
	public Vec3 headPos() {
		return position().add(0, HEAD_Y, 0);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
		return EntityDimensions.fixed(0.8f, 1.0f).withEyeHeight(HEAD_Y);
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide) {
			return false;
		}
		Entity attacker = source.getEntity();
		UUID o = ownerId();
		if (attacker != null && o != null && (o.equals(attacker.getUUID())
				|| level().getPlayerByUUID(o) instanceof net.minecraft.world.entity.player.Player owner && com.projecthero.mod.squad.Squads.areAllies(owner, attacker))) {
			return false; // his own side never breaks it
		}
		return super.hurt(source, amount);
	}

	@Override
	protected void tickDeath() {
		discard();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.COPPER_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.COPPER_BREAK;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			clientAim();
			return;
		}
		// the kit's tick runs it; with him gone it stands down
		UUID o = ownerId();
		if (o == null || level().getPlayerByUUID(o) == null || until > 0 && level().getGameTime() > until + 2) {
			if (++ownerMissing > 10) {
				discard();
			}
		} else {
			ownerMissing = 0;
		}
	}

	/** Slews to the locked target, or sweeps slowly left and right looking for one. */
	private void clientAim() {
		aimYawO = aimYaw;
		aimPitchO = aimPitch;
		float wantYaw, wantPitch;
		Entity t = target();
		if (t != null && t.isAlive()) {
			Vec3 d = t.position().add(0, t.getBbHeight() * 0.55, 0).subtract(headPos());
			wantYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
			wantPitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		} else {
			wantYaw = getYRot() + Mth.sin(tickCount * 0.07f) * 55f;
			wantPitch = 4f + Mth.sin(tickCount * 0.11f) * 3f;
		}
		if (!aimStarted) {
			aimStarted = true;
			aimYaw = aimYawO = wantYaw;
			aimPitch = aimPitchO = wantPitch;
			return;
		}
		float rate = t != null ? 0.45f : 0.12f;
		aimYaw += Mth.wrapDegrees(wantYaw - aimYaw) * rate;
		aimPitch += (wantPitch - aimPitch) * rate;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	/** A machine: no potion or poison takes on it. */
	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		return false;
	}
}
