package com.projecthero.mod.darkseid.entity;

import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.DarkseidSounds;
import com.projecthero.mod.darkseid.raid.DarkseidRaid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A Parademon: Apokolips' winged shock trooper. One entity type, four variants (synced, so the renderer picks the
 * texture and the Brute's bulk):
 * <ul>
 *   <li>{@link Variant#STANDARD} -- melee, takes to the air after a flying target.</li>
 *   <li>{@link Variant#RANGED} -- keeps its distance and fires energy bolts with a velocity lead; hovers level with
 *       a flying target, which is what makes it the flyer's problem.</li>
 *   <li>{@link Variant#ELITE} -- a tougher, faster, harder-hitting flyer.</li>
 *   <li>{@link Variant#BRUTE} -- a 1.45x ground-bound bruiser; too heavy for its wings.</li>
 * </ul>
 * Flight is a small hand-rolled steer in {@link #aiStep} rather than a flying move-control: they walk (and
 * path-find) like any mob until their target is well above them, then fly straight at it, and drop back to the
 * ground once it lands. Raid-owned Parademons target raid participants first and remove themselves if their raid
 * no longer exists (see {@link #checkRaid}).
 */
public class ParademonEntity extends Monster implements GeoEntity {
	public enum Variant {
		STANDARD, RANGED, ELITE, BRUTE;

		public boolean canFly() {
			return this != BRUTE;
		}

		public static Variant byId(int id) {
			return id >= 0 && id < values().length ? values()[id] : STANDARD;
		}
	}

	public static final float BRUTE_SCALE = 1.45f;

	private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(ParademonEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(ParademonEntity.class, EntityDataSerializers.BOOLEAN);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private UUID raidId;
	private int shootCooldown = 40;
	private int airAttackCooldown;
	private int wingSoundCooldown;
	private int repositionCooldown;

	public ParademonEntity(EntityType<? extends ParademonEntity> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		DarkseidConfig.Parademons cfg = DarkseidConfig.parademons();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.standardHealth)
				.add(Attributes.ATTACK_DAMAGE, cfg.standardDamage)
				.add(Attributes.MOVEMENT_SPEED, 0.30)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 32.0);
	}

	/** Configure a freshly created Parademon (stats per variant, full health, raid link). */
	public void setup(Variant variant, UUID raidId) {
		this.raidId = raidId;
		entityData.set(DATA_VARIANT, (byte) variant.ordinal());
		DarkseidConfig.Parademons cfg = DarkseidConfig.parademons();
		double health;
		double damage;
		double armor;
		double speed;
		double knockback;
		switch (variant) {
			case RANGED -> {
				health = cfg.rangedHealth;
				damage = cfg.standardDamage * 0.7;
				armor = 2.0;
				speed = 0.30;
				knockback = 0.0;
			}
			case ELITE -> {
				health = cfg.eliteHealth;
				damage = cfg.eliteDamage;
				armor = 10.0;
				speed = 0.33;
				knockback = 0.3;
			}
			case BRUTE -> {
				health = cfg.bruteHealth;
				damage = cfg.bruteDamage;
				armor = 12.0;
				speed = 0.26;
				knockback = 0.75;
			}
			default -> {
				health = cfg.standardHealth;
				damage = cfg.standardDamage;
				armor = 4.0;
				speed = 0.30;
				knockback = 0.0;
			}
		}
		setBase(Attributes.MAX_HEALTH, health);
		setBase(Attributes.ATTACK_DAMAGE, damage);
		setBase(Attributes.ARMOR, armor);
		setBase(Attributes.MOVEMENT_SPEED, speed);
		setBase(Attributes.KNOCKBACK_RESISTANCE, knockback);
		setHealth((float) health);
		refreshDimensions();
		this.xpReward = switch (variant) {
			case ELITE -> 20;
			case BRUTE -> 25;
			default -> 8;
		};
	}

	private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		var inst = getAttribute(attribute);
		if (inst != null) {
			inst.setBaseValue(value);
		}
	}

	public Variant variant() {
		return Variant.byId(entityData.get(DATA_VARIANT));
	}

	public boolean isFlying() {
		return entityData.get(DATA_FLYING);
	}

	public UUID raidId() {
		return raidId;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, (byte) Variant.STANDARD.ordinal());
		builder.define(DATA_FLYING, false);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_VARIANT.equals(accessor)) {
			refreshDimensions();
		}
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		EntityDimensions base = super.getDefaultDimensions(pose);
		return variant() == Variant.BRUTE ? base.scale(BRUTE_SCALE) : base;
	}

	// ---------------------------------------------------------------- goals

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true) {
			@Override
			public boolean canUse() {
				return variant() != Variant.RANGED && !isFlying() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return variant() != Variant.RANGED && !isFlying() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9) {
			@Override
			public boolean canUse() {
				return !isFlying() && getTarget() == null && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ParademonEntity.class, DarkseidEntity.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
				this::isPreferredTarget));
	}

	/** Raid Parademons go for the raid's own participants; a stray one attacks any player. */
	private boolean isPreferredTarget(LivingEntity e) {
		if (!(e instanceof Player p) || p.isCreative() || p.isSpectator()) {
			return false;
		}
		if (raidId != null && level() instanceof ServerLevel server) {
			DarkseidRaid raid = DarkseidRaid.find(server, raidId);
			return raid == null || raid.isParticipant(p.getUUID()) || raid.isInArena(p);
		}
		return true;
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit) {
			triggerAnim("action", "attack");
		}
		return hit;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (tickCount % 40 == 0 && !checkRaid(server)) {
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && (!target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			setTarget(null);
			target = null;
		}
		tickFlight(server, target);
		if (variant() == Variant.RANGED) {
			tickRanged(server, target);
		}
	}

	/** A raid Parademon whose raid is gone (ended while its chunk was unloaded) removes itself. */
	private boolean checkRaid(ServerLevel server) {
		if (raidId != null && DarkseidRaid.find(server, raidId) == null) {
			discard();
			return false;
		}
		return true;
	}

	private void tickFlight(ServerLevel server, LivingEntity target) {
		if (!variant().canFly()) {
			return;
		}
		boolean targetUp = target != null && !target.onGround() && target.getY() - getY() > 3.0;
		boolean flying = isFlying();
		if (!flying && targetUp) {
			setFlying(true);
			flying = true;
			setDeltaMovement(getDeltaMovement().add(0, 0.5, 0));
		}
		if (!flying) {
			return;
		}
		if (target == null || (target.onGround() && getY() - target.getY() < 2.5)) {
			// the target landed (or is gone): glide down and walk again
			setNoGravity(false);
			if (onGround() || target == null) {
				setFlying(false);
			}
			return;
		}
		setNoGravity(true);
		getNavigation().stop();
		double speed = DarkseidConfig.parademons().flightSpeed * (variant() == Variant.ELITE ? 1.25 : 1.0);
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
		Vec3 want;
		if (variant() == Variant.RANGED) {
			// hold station off to one side of the flyer, slightly above, and shoot
			Vec3 away = position().subtract(aim);
			Vec3 flat = new Vec3(away.x, 0, away.z);
			flat = flat.lengthSqr() < 1.0e-3 ? new Vec3(1, 0, 0) : flat.normalize();
			want = aim.add(flat.scale(9.0)).add(0, 2.5, 0);
		} else {
			want = aim;
		}
		Vec3 dir = want.subtract(position());
		double len = dir.length();
		Vec3 desired = len < 0.5 ? Vec3.ZERO : dir.scale(Math.min(speed, len * 0.4) / len);
		setDeltaMovement(getDeltaMovement().lerp(desired, 0.25));
		getLookControl().setLookAt(target, 30.0f, 30.0f);
		float yaw = (float) (Math.atan2(aim.z - getZ(), aim.x - getX()) * (180.0 / Math.PI)) - 90.0f;
		setYRot(yaw);
		yBodyRot = yaw;
		if (variant() != Variant.RANGED) {
			if (airAttackCooldown > 0) {
				airAttackCooldown--;
			} else if (distanceTo(target) < 2.4 + target.getBbWidth() * 0.5) {
				swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				doHurtTarget(target);
				airAttackCooldown = 20;
			}
		}
		if (--wingSoundCooldown <= 0) {
			wingSoundCooldown = 10;
			playSound(DarkseidSounds.PARADEMON_WINGS, 0.6f, 0.9f + random.nextFloat() * 0.3f);
		}
	}

	private void setFlying(boolean flying) {
		entityData.set(DATA_FLYING, flying);
		if (!flying) {
			setNoGravity(false);
		}
	}

	private void tickRanged(ServerLevel server, LivingEntity target) {
		if (target == null) {
			return;
		}
		double d = distanceTo(target);
		if (!isFlying()) {
			getLookControl().setLookAt(target, 30.0f, 30.0f);
			if (--repositionCooldown <= 0) {
				repositionCooldown = 20;
				if (d < 7.0) {
					Vec3 away = position().subtract(target.position()).normalize().scale(8.0).add(position());
					getNavigation().moveTo(away.x, away.y, away.z, 1.15);
				} else if (d > 18.0 || !hasLineOfSight(target)) {
					getNavigation().moveTo(target, 1.0);
				} else {
					getNavigation().stop();
				}
			}
		}
		if (--shootCooldown > 0) {
			return;
		}
		if (d > 28.0 || !hasLineOfSight(target)) {
			shootCooldown = 10;
			return;
		}
		shootCooldown = 34 + random.nextInt(14);
		Vec3 from = position().add(0, getBbHeight() * 0.75, 0);
		double speed = 1.15;
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
		// lead the target by its current velocity -- straight-line flyers get hit, weaving ones do not
		double flight = aim.distanceTo(from) / speed;
		Vec3 lead = target.getDeltaMovement().scale(Math.min(20.0, flight));
		if (target.onGround()) {
			lead = new Vec3(lead.x, 0.0, lead.z);
		}
		triggerAnim("action", "shoot");
		ParademonBoltEntity.fire(server, this, from, aim.add(lead).subtract(from), DarkseidConfig.parademons().rangedBoltDamage, speed);
	}

	// ---------------------------------------------------------------- misc

	@Override
	public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
		return false; // wings
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.getEntity() instanceof ParademonEntity || source.getEntity() instanceof DarkseidEntity) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return DarkseidSounds.PARADEMON_SCREECH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return DarkseidSounds.PARADEMON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return DarkseidSounds.PARADEMON_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return variant() == Variant.BRUTE ? 0.7f : super.getVoicePitch();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("Variant", (byte) variant().ordinal());
		if (raidId != null) {
			tag.putUUID("DarkseidRaid", raidId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_VARIANT, tag.getByte("Variant"));
		raidId = tag.hasUUID("DarkseidRaid") ? tag.getUUID("DarkseidRaid") : null;
		setNoGravity(false);
		entityData.set(DATA_FLYING, false);
		refreshDimensions();
	}

	/** For the victim predicate shared with the Omega weapons. */
	public static boolean isRaidEnemy(Entity e) {
		return e instanceof ParademonEntity || e instanceof DarkseidEntity;
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<ParademonEntity> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
		action.triggerableAnim("attack", RawAnimation.begin().thenPlay("animation.parademon.attack"));
		action.triggerableAnim("shoot", RawAnimation.begin().thenPlay("animation.parademon.shoot"));
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", 3, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<ParademonEntity> state) {
		if (isFlying()) {
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.parademon.fly"));
		}
		if (state.getLimbSwingAmount() > 0.05f) {
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.parademon.walk"));
		}
		return state.setAndContinue(RawAnimation.begin().thenLoop("animation.parademon.idle"));
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return isRaidEnemy(other) || super.isAlliedTo(other);
	}
}
