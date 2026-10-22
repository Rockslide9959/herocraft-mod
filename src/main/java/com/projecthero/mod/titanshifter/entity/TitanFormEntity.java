package com.projecthero.mod.titanshifter.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.mixin.LivingEntityAccessor;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.titanshifter.TitanCombat;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.TitanType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
 * The Titan a Titan Shifter turns into (v0.12.31): a real entity, not a scaled player. It is a
 * {@link LivingEntity} with its own 500 HP pool, armour, hit-box and GeckoLib model; the shifter rides it as its
 * (hidden, damage-proof) controlling passenger.
 *
 * <h2>Server-authoritative movement</h2>
 * {@link #isControlledByLocalInstance()} is {@code false} on the client, so the rider's client never
 * simulates or reports the Titan's position: the server reads the rider's input ({@code xxa/zza/jumping},
 * which vanilla already sends for any passenger), drives the Titan through the stock ridden-mob path
 * ({@code getRiddenInput / getRiddenSpeed / tickRidden}), and every client just interpolates the result.
 *
 * <h2>Never saved</h2>
 * {@code noSave}: a Titan never persists, so it cannot be duplicated by a reconnect or a chunk reload; the
 * owner's {@code TitanShifterState} decides what a returning player becomes.
 */
public class TitanFormEntity extends LivingEntity implements GeoEntity {
	public static final int FORM_NORMAL = 0;
	public static final int FORM_TRANSFORMING = 1;
	public static final int FORM_REVERTING = 2;
	public static final int FORM_DEFEATED = 3;

	private static final EntityDataAccessor<Integer> DATA_TYPE =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FORM_STATE =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_HARDENED =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_STEAMING =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.BOOLEAN);
	/** v0.12.34: the owner is synced so clients can tell the owner (hidden, 3rd-person camera) from a squad-mate on the shoulder (visible). */
	private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Boolean> DATA_RUNNING =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_HOLDING =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.BOOLEAN);
	/** v0.12.39: the pale, 7-block, 40%-weaker Emergency Titan. */
	private static final EntityDataAccessor<Boolean> DATA_EMERGENCY =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.BOOLEAN);
	/** v0.16.7 (user, Titan revamp): severed limbs ({@link #LIMB_R_ARM} ...), drawn missing until they regrow. */
	private static final EntityDataAccessor<Integer> DATA_SEVERED =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.INT);
	public static final int LIMB_R_ARM = 1, LIMB_L_ARM = 2, LIMB_R_LEG = 4, LIMB_L_LEG = 8;
	public static final int[] LIMBS = { LIMB_R_ARM, LIMB_L_ARM, LIMB_R_LEG, LIMB_L_LEG };
	/**
	 * v0.16.7 (user, Titan revamp gameplay): move states the visuals read (synced bit set, see {@link #moveFlags()}):
	 * {@link #FLAG_RAGING} Attack Titan Rage, {@link #FLAG_NAPE_GUARD} a hand held over the nape, {@link #FLAG_CRYSTAL}
	 * the Female Titan's crystal fists / shins (and hardened nape), {@link #FLAG_BRACING} the Armoured Titan's Brace,
	 * {@link #FLAG_CHARGING} the Armoured Titan's Shoulder Charge, {@link #FLAG_HOLDING_BOULDER} a boulder in the Beast's hand.
	 */
	private static final EntityDataAccessor<Integer> DATA_MOVE_FLAGS =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.INT);
	public static final int FLAG_RAGING = 1, FLAG_NAPE_GUARD = 2, FLAG_CRYSTAL = 4, FLAG_BRACING = 8, FLAG_CHARGING = 16,
			FLAG_HOLDING_BOULDER = 32;
	/** v0.16.7: how long (ticks) the current {@link #formState()} lasts -- TRANSFORMING (3 s, or a fast Bite Shift), REVERTING (3 s), DEFEATED. */
	private static final EntityDataAccessor<Integer> DATA_STATE_TICKS =
			SynchedEntityData.defineId(TitanFormEntity.class, EntityDataSerializers.INT);

	/** Every animation lives under this prefix in every Titan type's own animation file. */
	public static final String ANIM = "animation.titan.";

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop(ANIM + "run");
	private static final RawAnimation TRANSFORM = RawAnimation.begin().thenPlayAndHold(ANIM + "transformation");
	private static final RawAnimation REVERT = RawAnimation.begin().thenPlayAndHold(ANIM + "reversion");
	private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(ANIM + "death");
	private static final RawAnimation CARRY = RawAnimation.begin().thenLoop(ANIM + "carry");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private final List<Scheduled> scheduled = new ArrayList<>();

	/** Ticks the owner has held Sprint while walking forward (a run starts at runChargeTicks). */
	private int sprintTicks;

	// ---- the mob the Titan is carrying in its hand (server only) ----
	private UUID heldId;
	private boolean lowering;
	private double lowerX;
	private double lowerZ;
	private boolean heldNoAi;
	private boolean heldNoGravity;
	private boolean heldNoPhysics;
	/** True while the hit being applied is one from an ordinary mob, which ignores the Titan armour (see hurt). */
	private boolean mobHit;

	/** Movement and actions are locked until this game-time (roar, transformation ...). */
	private long lockedUntil;
	/** Ridden speed is scaled by {@link #slowFactor} until this game-time (Heavy Smash charge-up). */
	private long slowedUntil;
	private float slowFactor = 1.0f;
	private boolean leaping;
	private long leapStartedAt;
	/** v0.12.36: the Titan heals biteRegenPerSecond HP/s until this game-time (it just ate the mob it was holding). */
	public long biteRegenUntil;
	private boolean defeatStarted;
	private long lastHurtAnimAt;
	private long lastHurtAt = -1000;
	private double stride;
	private boolean rightFoot;
	private int ownerMissingTicks;
	private long lastTrampleAt;
	private int stateChangedTick;

	private record Scheduled(long dueTick, Runnable task) {
	}

	public TitanFormEntity(EntityType<? extends TitanFormEntity> type, Level level) {
		super(type, level);
		this.noCulling = true;
	}

	public static AttributeSupplier.Builder createAttributes() {
		TitanType t = TitanType.GENERIC_TITAN;
		return LivingEntity.createLivingAttributes()
				.add(Attributes.MAX_HEALTH, t.health())
				.add(Attributes.ARMOR, t.armorValue())
				.add(Attributes.ARMOR_TOUGHNESS, t.toughnessValue())
				.add(Attributes.KNOCKBACK_RESISTANCE, TitanShifterConfig.stats().knockbackResistance)
				.add(Attributes.MOVEMENT_SPEED, t.speedValue())
				.add(Attributes.STEP_HEIGHT, TitanShifterConfig.stats().stepHeight)
				.add(Attributes.ATTACK_DAMAGE, TitanShifterConfig.damage().punch)
				.add(Attributes.GRAVITY, 0.08)
				.add(Attributes.SAFE_FALL_DISTANCE, 8.0);
	}

	// ---------------- set-up / synced data ----------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TYPE, 0);
		builder.define(DATA_FORM_STATE, FORM_NORMAL);
		builder.define(DATA_HARDENED, false);
		builder.define(DATA_STEAMING, false);
		builder.define(DATA_OWNER, Optional.empty());
		builder.define(DATA_RUNNING, false);
		builder.define(DATA_HOLDING, false);
		builder.define(DATA_EMERGENCY, false);
		builder.define(DATA_SEVERED, 0);
		builder.define(DATA_MOVE_FLAGS, 0);
		builder.define(DATA_STATE_TICKS, 60);
	}

	/** Server: bind this Titan to its owner and stats. Call before adding to the world. */
	public void bind(ServerPlayer owner, TitanType type) {
		bind(owner, type, false);
	}

	/** v0.12.39: {@code emergency} makes it the pale, smaller, weaker Emergency Titan (must be set before the stats and hit-box are applied). */
	public void bind(ServerPlayer owner, TitanType type, boolean emergency) {
		this.entityData.set(DATA_EMERGENCY, emergency);
		this.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
		this.entityData.set(DATA_TYPE, type.ordinal());
		refreshDimensions();
		applyTypeStats(type);
		setHealth(getMaxHealth());
	}

	/** Debug: an ownerless Titan (no rider, no watchdog) for `/projecthero titanshifter spawn`. */
	public void bindDummy(TitanType type) {
		this.entityData.set(DATA_TYPE, type.ordinal());
		refreshDimensions();
		applyTypeStats(type);
		setHealth(getMaxHealth());
	}

	public boolean isEmergency() {
		return this.entityData.get(DATA_EMERGENCY);
	}

	/** 1.0 for a full Titan, the configured 0.6 for the Emergency Titan -- multiplies health, armour, speed and every hit it lands. */
	public float strengthFactor() {
		return isEmergency() ? (float) TitanShifterConfig.emergency().strengthFactor : 1.0f;
	}

	private void applyTypeStats(TitanType type) {
		double k = strengthFactor();
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(type.health() * k);
		getAttribute(Attributes.ARMOR).setBaseValue(type.armorValue() * k);
		getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(type.toughnessValue() * k);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(type.speedValue() * k);
		getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(TitanShifterConfig.stats().knockbackResistance);
		getAttribute(Attributes.STEP_HEIGHT).setBaseValue(TitanShifterConfig.stats().stepHeight);
	}

	/** v0.16.7: the severed-limb bits ({@link #LIMB_R_ARM}, {@link #LIMB_L_ARM}, {@link #LIMB_R_LEG}, {@link #LIMB_L_LEG}). */
	public int severedLimbs() {
		return this.entityData.get(DATA_SEVERED);
	}

	public void setSeveredLimbs(int bits) {
		this.entityData.set(DATA_SEVERED, bits);
	}

	public boolean hasLimb(int limb) {
		return (severedLimbs() & limb) == 0;
	}

	/** Arms still attached (0-2). */
	public int armsLeft() {
		return (hasLimb(LIMB_R_ARM) ? 1 : 0) + (hasLimb(LIMB_L_ARM) ? 1 : 0);
	}

	/** Legs still attached (0-2). Fewer than two: the Titan kneels and drags itself (and cannot leap, jump or run). */
	public int legsLeft() {
		return (hasLimb(LIMB_R_LEG) ? 1 : 0) + (hasLimb(LIMB_L_LEG) ? 1 : 0);
	}

	/** v0.16.7: synced -- a leg is missing, so the Titan is down on one knee (visuals: kneel / stumble pose). */
	public boolean isKneeling() {
		return legsLeft() < 2;
	}

	/** v0.16.7: the synced move-state bits ({@link #FLAG_RAGING} ...). */
	public int moveFlags() {
		return this.entityData.get(DATA_MOVE_FLAGS);
	}

	public boolean hasMoveFlag(int flag) {
		return (moveFlags() & flag) != 0;
	}

	private void setMoveFlag(int flag, boolean on) {
		int cur = moveFlags();
		int next = on ? cur | flag : cur & ~flag;
		if (next != cur) {
			this.entityData.set(DATA_MOVE_FLAGS, next);
		}
	}

	/** v0.16.7: synced -- the length in ticks of the current form state (see {@link #setFormState(int, int)}). */
	public int formStateTicks() {
		return this.entityData.get(DATA_STATE_TICKS);
	}

	/** v0.16.7: synced -- how far (0..1) the current TRANSFORMING / REVERTING / DEFEATED state has run. */
	public float formStateProgress(float partialTick) {
		return Math.min(1.0f, (tickCount - stateChangedTick + partialTick) / (float) Math.max(1, formStateTicks()));
	}

	// ---------------- v0.16.7: timed move states (server) ----------------

	private long rageUntil;
	private long napeGuardUntil;
	private long crystalUntil;
	private long braceUntil;
	private long chargeUntil;
	private long lastAttackAt = -1000;
	/** Game time each limb (index into {@link #LIMBS}) was severed; 0 = attached. */
	private final long[] severedAt = new long[4];

	public void startRage(int ticks) {
		rageUntil = level().getGameTime() + ticks;
		setMoveFlag(FLAG_RAGING, true);
	}

	public void startNapeGuard(int ticks) {
		napeGuardUntil = level().getGameTime() + ticks;
		setMoveFlag(FLAG_NAPE_GUARD, true);
	}

	public void startCrystal(int ticks) {
		crystalUntil = level().getGameTime() + ticks;
		setMoveFlag(FLAG_CRYSTAL, true);
	}

	public void startBrace(int ticks) {
		braceUntil = level().getGameTime() + ticks;
		setMoveFlag(FLAG_BRACING, true);
		lockFor(ticks);
	}

	public void startCharge(int ticks) {
		chargeUntil = level().getGameTime() + ticks;
		setMoveFlag(FLAG_CHARGING, true);
	}

	public void endCharge() {
		chargeUntil = 0L;
		setMoveFlag(FLAG_CHARGING, false);
	}

	public void setHoldingBoulder(boolean holding) {
		setMoveFlag(FLAG_HOLDING_BOULDER, holding);
	}

	public boolean isCharging() {
		return hasMoveFlag(FLAG_CHARGING);
	}

	public boolean isNapeGuarded() {
		return hasMoveFlag(FLAG_NAPE_GUARD);
	}

	public boolean isCrystal() {
		return hasMoveFlag(FLAG_CRYSTAL);
	}

	/**
	 * v0.16.7: the Attack Titan's Rage, 0..1 -- rises as it takes damage (1 - HP fraction), and is full while Rage (Shift+X)
	 * is active. Other types have none. Safe on both sides (health and the flag are synced).
	 */
	public float rage() {
		if (titanType() != TitanType.GENERIC_TITAN) {
			return 0f;
		}
		if (hasMoveFlag(FLAG_RAGING)) {
			return 1f;
		}
		float max = getMaxHealth();
		return max <= 0f ? 0f : Math.max(0f, Math.min(1f, 1f - getHealth() / max));
	}

	/** v0.16.7: every hit this Titan lands is multiplied by this (Rage up to +50%, crystal fists +50%). */
	public float outgoingDamageFactor() {
		float f = 1.0f + 0.5f * rage();
		if (isCrystal()) {
			f *= 1.5f;
		}
		return f;
	}

	/** v0.16.7: Rage also speeds the R combo up -- its recharge shrinks by up to 40%. */
	public double attackSpeedFactor() {
		return 1.0 - 0.4 * rage();
	}

	public void markAttacked() {
		lastAttackAt = level().getGameTime();
	}

	/** v0.16.7: no hit taken or dealt for {@code ticks}. */
	public boolean outOfCombat(int ticks) {
		long now = level().getGameTime();
		return now - lastHurtAt >= ticks && now - lastAttackAt >= ticks;
	}

	/** World position of a limb (for the sever burst and the regrowing steam). */
	public Vec3 limbPos(int limb) {
		Vec3 right = Vec3.directionFromRotation(0, getYRot() + 90.0f);
		double w = getBbWidth();
		double h = getBbHeight();
		return switch (limb) {
			case LIMB_R_ARM -> position().add(right.scale(w * 0.62)).add(0, h * 0.6, 0);
			case LIMB_L_ARM -> position().add(right.scale(-w * 0.62)).add(0, h * 0.6, 0);
			case LIMB_R_LEG -> position().add(right.scale(w * 0.2)).add(0, h * 0.2, 0);
			default -> position().add(right.scale(-w * 0.2)).add(0, h * 0.2, 0);
		};
	}

	/** v0.16.7: cuts a limb off (server). Returns false if it was already gone. */
	public boolean sever(int limb) {
		if (!hasLimb(limb) || !(level() instanceof ServerLevel sl)) {
			return false;
		}
		setSeveredLimbs(severedLimbs() | limb);
		for (int i = 0; i < LIMBS.length; i++) {
			if (LIMBS[i] == limb) {
				severedAt[i] = sl.getGameTime();
			}
		}
		Vec3 at = limbPos(limb);
		double h = getBbHeight();
		// the server-side event: a burst of blood-red mist and steam where the limb came off, and a heavy thud
		sl.sendParticles(SEVER_BLOOD, at.x, at.y, at.z, 70, h * 0.06, h * 0.08, h * 0.06, 0.25);
		sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 30, h * 0.05, h * 0.06, h * 0.05, 0.05);
		sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 25, h * 0.05, h * 0.05, h * 0.05, 0.08);
		sl.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 3.0f, 0.4f);
		sl.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 3.0f, 0.5f);
		sl.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 2.5f, 0.6f);
		if (limb == LIMB_R_LEG || limb == LIMB_L_LEG) {
			play("stumble");
			footstep(sl, 1.6f, false);
		} else {
			play("hurt");
		}
		releaseHeld();
		return true;
	}

	private static final net.minecraft.core.particles.DustParticleOptions SEVER_BLOOD =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.55f, 0.05f, 0.05f), 2.5f);

	/** Server: the move timers run out, and severed limbs regrow (steaming) while the shifter has Steam to spend. */
	private void tickMoves(ServerLevel sl, long now) {
		if (rageUntil != 0L && now >= rageUntil) {
			rageUntil = 0L;
			setMoveFlag(FLAG_RAGING, false);
		}
		if (napeGuardUntil != 0L && now >= napeGuardUntil) {
			napeGuardUntil = 0L;
			setMoveFlag(FLAG_NAPE_GUARD, false);
		}
		if (crystalUntil != 0L && now >= crystalUntil) {
			crystalUntil = 0L;
			setMoveFlag(FLAG_CRYSTAL, false);
		}
		if (braceUntil != 0L && now >= braceUntil) {
			braceUntil = 0L;
			setMoveFlag(FLAG_BRACING, false);
		}
		if (chargeUntil != 0L && now >= chargeUntil) {
			endCharge();
		}
		int bits = severedLimbs();
		if (bits == 0) {
			return;
		}
		var lc = TitanShifterConfig.limbs();
		ServerPlayer owner = owner();
		for (int i = 0; i < LIMBS.length; i++) {
			int limb = LIMBS[i];
			if ((bits & limb) == 0) {
				continue;
			}
			if (severedAt[i] == 0L) {
				severedAt[i] = now; // set from outside (a test, a command): start its clock now
			}
			if (tickCount % 4 == 0) {
				Vec3 at = limbPos(limb);
				sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0.02);
				sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0.01);
			}
			// the Regeneration ability (C) knits it twice as fast
			long need = owner != null && TitanShifter.state(owner).regenUntil > now ? lc.regrowTicks / 2 : lc.regrowTicks;
			if (now - severedAt[i] >= need && (owner == null || TitanShifter.spendSteam(owner, (float) lc.regrowSteamCost))) {
				severedAt[i] = 0L;
				setSeveredLimbs(severedLimbs() & ~limb);
				Vec3 at = limbPos(limb);
				sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 30, 0.8, 0.8, 0.8, 0.06);
				sl.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 2.0f, 0.7f);
			}
		}
	}

	/** v0.16.7: the hit zone of a hit at {@code p} -- a limb bit, {@link #ZONE_NAPE}, or 0 (the body). */
	public int zoneOf(Vec3 p) {
		Vec3 rel = p.subtract(position());
		Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
		Vec3 right = Vec3.directionFromRotation(0, getYRot() + 90.0f);
		double hf = rel.y / Math.max(0.1, getBbHeight());
		double lateral = rel.x * right.x + rel.z * right.z;
		double depth = rel.x * fwd.x + rel.z * fwd.z;
		if (hf >= TitanShifterConfig.nape().shoulderFraction && depth < -getBbWidth() * 0.05) {
			return ZONE_NAPE;
		}
		if (hf < 0.375) {
			return lateral >= 0 ? LIMB_R_LEG : LIMB_L_LEG;
		}
		if (hf < 0.75 && Math.abs(lateral) > getBbWidth() * 0.3) {
			return lateral >= 0 ? LIMB_R_ARM : LIMB_L_ARM;
		}
		return 0;
	}

	public static final int ZONE_NAPE = 16;

	/** Where a hit landed: the projectile / blast position, or the attacker's eyes clamped onto the body. Null if unknown. */
	private Vec3 hitPoint(DamageSource source) {
		Entity direct = source.getDirectEntity();
		if (direct instanceof net.minecraft.world.entity.projectile.Projectile) {
			return direct.position();
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getSourcePosition() != null) {
			return source.getSourcePosition();
		}
		Entity attacker = source.getEntity();
		if (attacker != null) {
			Vec3 eye = attacker.getEyePosition();
			// a rider (another Titan's shifter) strikes from its own Titan's eye height; clamp onto this body's height
			double y = Math.max(getY(), Math.min(getY() + getBbHeight(), eye.y));
			return new Vec3(eye.x, y, eye.z);
		}
		return source.getSourcePosition();
	}

	public TitanType titanType() {
		return TitanType.byOrdinal(this.entityData.get(DATA_TYPE));
	}

	/** Safe on both sides (synced). */
	public UUID ownerId() {
		return this.entityData.get(DATA_OWNER).orElse(null);
	}

	/** True if e is a player currently riding their OWN Titan (as opposed to a squad-mate on its shoulder). */
	public static boolean isOwnerRider(Entity e) {
		return e instanceof Player p && p.getVehicle() instanceof TitanFormEntity f && p.getUUID().equals(f.ownerId());
	}

	public boolean isRunning() {
		return this.entityData.get(DATA_RUNNING);
	}

	public boolean isHolding() {
		return this.entityData.get(DATA_HOLDING);
	}

	public ServerPlayer owner() {
		UUID id = ownerId();
		return id != null && level().getPlayerByUUID(id) instanceof ServerPlayer sp ? sp : null;
	}

	public int formState() {
		return this.entityData.get(DATA_FORM_STATE);
	}

	public void setFormState(int state) {
		var t = TitanShifterConfig.transformation();
		setFormState(state, switch (state) {
			case FORM_TRANSFORMING -> t.transformTicks;
			case FORM_REVERTING -> t.revertTicks;
			case FORM_DEFEATED -> t.defeatTicks;
			default -> 0;
		});
	}

	/** v0.16.7: with the state's length (synced as {@link #formStateTicks()}) -- a Bite Shift's fast transformation, say. */
	public void setFormState(int state, int ticks) {
		this.entityData.set(DATA_STATE_TICKS, Math.max(1, ticks));
		this.entityData.set(DATA_FORM_STATE, state);
	}

	public boolean isHardened() {
		return this.entityData.get(DATA_HARDENED);
	}

	public void setHardened(boolean hardened) {
		this.entityData.set(DATA_HARDENED, hardened);
	}

	public boolean isSteaming() {
		return this.entityData.get(DATA_STEAMING);
	}

	public boolean isDefeated() {
		return defeatStarted;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_TYPE.equals(key) || DATA_EMERGENCY.equals(key)) {
			refreshDimensions();
		} else if (DATA_FORM_STATE.equals(key)) {
			stateChangedTick = tickCount;
		}
	}

	/**
	 * How tall the Titan currently is relative to its full size: it swells up during the transformation and
	 * shrinks during reversion / defeat, and the rider's seat follows so the camera never floats over a small
	 * body. Derived from {@link #tickCount} on both sides, so client and server agree without any packet.
	 */
	public float visualScale() {
		int elapsed = tickCount - stateChangedTick;
		switch (formState()) {
			case FORM_TRANSFORMING: {
				float p = Math.min(1.0f, elapsed / (float) Math.max(1, formStateTicks()));
				// v0.16.7: mirrors the root scale of animation.titan.transformation (stretched to the shift: 0.86 crouched
				// -> 1.0 standing at 3.85 of its 4.5 s)
				return 0.86f + 0.14f * Math.min(1.0f, p / (3.85f / 4.5f));
			}
			case FORM_REVERTING:
				// v0.16.7: the body does not shrink while the shifter climbs out of the nape -- it steams where it stands
				return 1.0f;
			case FORM_DEFEATED:
				// v0.16.7: the body sinks to its hands (animation.titan.death) and the full-size corpse takes over from that pose
			default:
				return 1.0f;
		}
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		TitanType type = this.entityData == null ? TitanType.GENERIC_TITAN : titanType();
		// v0.16.7: the pale Emergency Titan is ~60% of the selected type's own height
		float shrink = this.entityData == null || !isEmergency() ? 1.0f : (float) TitanShifterConfig.emergency().heightFraction;
		return EntityDimensions.scalable(type.dimensionWidth() * shrink, type.dimensionHeight() * shrink)
				.withEyeHeight(type.dimensionHeight() * shrink * 0.9f); // a player's eyes sit at 0.9 of their height
	}

	// ---------------- riding ----------------

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
		float vs = visualScale();
		UUID owner = ownerId();
		if (owner == null || passenger.getUUID().equals(owner)) {
			// a riding player's feet sit 0.6 below the seat point and their eyes 1.62 above their feet, so seat = eye - 1.02
			return new Vec3(0.0, dims.eyeHeight() * vs - 1.02, 0.0);
		}
		// v0.12.34: squad-mates ride the shoulders -- the first on one side, the second on the other
		int index = 0;
		for (Entity other : getPassengers()) {
			if (other == passenger) {
				break;
			}
			if (!other.getUUID().equals(owner)) {
				index++;
			}
		}
		double side = (index % 2 == 0 ? 1.0 : -1.0) * dims.width() * 0.5;
		return new Vec3(side * 0.95, dims.height() * vs * 0.75 + 0.6, 0.0);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		if (!(passenger instanceof Player p)) {
			return false;
		}
		UUID owner = ownerId();
		if (p.getUUID().equals(owner)) {
			return getControllingPassenger() == null;
		}
		return owner != null && getControllingPassenger() != null
				&& getPassengers().size() < 1 + TitanShifterConfig.abilities().maxShoulderRiders;
	}

	/** The shifter (never a squad-mate on the shoulder) steers the Titan. */
	@Override
	public LivingEntity getControllingPassenger() {
		UUID owner = ownerId();
		for (Entity e : getPassengers()) {
			if (e instanceof Player p && p.getUUID().equals(owner)) {
				return p;
			}
		}
		return null;
	}

	/** v0.12.34: right-click a Titan as one of its owner's squad-mates and you climb onto its shoulder. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (level().isClientSide || !(player instanceof ServerPlayer sp) || formState() != FORM_NORMAL || defeatStarted) {
			return InteractionResult.PASS;
		}
		UUID owner = ownerId();
		if (owner == null || sp.getUUID().equals(owner) || sp.isPassenger() || sp.isSpectator() || getControllingPassenger() == null) {
			return InteractionResult.PASS;
		}
		var server = sp.getServer();
		if (server == null || !SquadManager.get(server).sameSquad(owner, sp.getUUID())) {
			return InteractionResult.PASS;
		}
		if (getPassengers().size() >= 1 + TitanShifterConfig.abilities().maxShoulderRiders) {
			sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.titan_shifter.shoulders_full"), true);
			return InteractionResult.SUCCESS;
		}
		if (sp.startRiding(this, true)) {
			sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.titan_shifter.shoulder"), true);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Server simulates the Titan; clients only interpolate it. */
	@Override
	public boolean isControlledByLocalInstance() {
		return !level().isClientSide;
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return true;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return true;
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
		if (isLocked()) {
			return Vec3.ZERO;
		}
		float strafe = player.xxa * 0.5f;
		float forward = player.zza;
		if (forward <= 0.0f) {
			forward *= 0.35f;
		}
		return new Vec3(strafe, 0.0, forward);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		double speed = getAttributeValue(Attributes.MOVEMENT_SPEED) * TitanShifterConfig.stats().riddenSpeedFactor;
		if (level().getGameTime() < slowedUntil) {
			speed *= slowFactor;
		}
		if (isRunning()) {
			speed *= TitanShifterConfig.stats().runSpeedMultiplier;
		}
		// v0.16.7: a missing leg -- down on one knee, dragging itself; both gone -- it cannot walk
		int legs = legsLeft();
		if (legs < 2) {
			speed *= legs == 1 ? TitanShifterConfig.limbs().oneLegSpeedFactor : 0.0;
		}
		return (float) speed;
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		float yaw = player.getYRot();
		this.setRot(yaw, Math.max(-30.0f, Math.min(30.0f, player.getXRot() * 0.4f)));
		this.yRotO = this.yBodyRot = this.yHeadRot = yaw;
		if (!level().isClientSide && !isLocked() && onGround() && legsLeft() == 2
				&& ((LivingEntityAccessor) player).projecthero$isJumping()) {
			Vec3 v = getDeltaMovement();
			setDeltaMovement(v.x, TitanShifterConfig.stats().jumpVelocity, v.z);
			this.hasImpulse = true;
			playSound(SoundEvents.IRON_GOLEM_STEP, 1.6f, 0.5f);
		}
	}

	public boolean isLocked() {
		return formState() != FORM_NORMAL || level().getGameTime() < lockedUntil || defeatStarted;
	}

	public void lockFor(int ticks) {
		lockedUntil = Math.max(lockedUntil, level().getGameTime() + ticks);
	}

	public void slowFor(int ticks, float factor) {
		slowedUntil = level().getGameTime() + ticks;
		slowFactor = factor;
	}

	public void clearSlow() {
		slowedUntil = 0L;
	}

	public boolean isSlowed() {
		return level().getGameTime() < slowedUntil;
	}

	// ---------------- scheduling ----------------

	/** Run {@code task} on the server after {@code delayTicks}; dropped if the Titan is gone. */
	public void schedule(int delayTicks, Runnable task) {
		scheduled.add(new Scheduled(level().getGameTime() + Math.max(0, delayTicks), task));
	}

	public void play(String anim) {
		triggerAnim("action", anim);
	}

	// ---------------- leaping ----------------

	public void beginLeap() {
		leaping = true;
		leapStartedAt = level().getGameTime();
	}

	public boolean isLeaping() {
		return leaping;
	}

	// ---------------- tick ----------------

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
			return;
		}
		long now = sl.getGameTime();
		if (!scheduled.isEmpty()) {
			for (Scheduled s : new ArrayList<>(scheduled)) {
				if (s.dueTick() <= now) {
					scheduled.remove(s);
					if (isAlive()) {
						s.task().run();
					}
				}
			}
		}
		if (ownerId() == null) {
			tickSteam(sl, now); // ownerless debug Titan
			return;
		}
		// watchdog: a Titan whose owner left (or who is no longer riding it) must not linger
		ServerPlayer owner = owner();
		if (owner == null || owner.isRemoved() || (tickCount > 5 && owner.getVehicle() != this)) {
			if (++ownerMissingTicks > 30) {
				discard();
			}
			return;
		}
		ownerMissingTicks = 0;
		if (formState() != FORM_NORMAL || defeatStarted) {
			releaseHeld();
			sprintTicks = 0;
			if (isRunning()) {
				this.entityData.set(DATA_RUNNING, false);
			}
		}
		tickMoves(sl, now);
		if (formState() == FORM_NORMAL) {
			tickRun(owner);
			tickHeld(sl);
			tickLeap(sl, now);
			tickFootsteps(sl);
			tickTrample(sl, now);
		}
		tickSteam(sl, now);
	}

	/** v0.12.34: hold Sprint while walking forward for runChargeTicks and the Titan starts running; let go or stop and it walks again. */
	private void tickRun(ServerPlayer owner) {
		boolean forward = owner.zza > 0.1f && !isLocked() && legsLeft() == 2;
		if (forward && TitanShifter.sprintHeld(owner.getUUID())) {
			sprintTicks = Math.min(sprintTicks + 1, 100000);
		} else {
			sprintTicks = 0;
		}
		boolean run = sprintTicks >= TitanShifterConfig.stats().runChargeTicks;
		if (run != isRunning()) {
			this.entityData.set(DATA_RUNNING, run);
		}
	}

	// ---------------- carrying a mob (N) ----------------

	/** The mob in the Titan's hand, or null. */
	public LivingEntity held() {
		if (heldId == null || !(level() instanceof ServerLevel sl)) {
			return null;
		}
		return sl.getEntity(heldId) instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** True while a mob is being set gently down. */
	public boolean isLowering() {
		return lowering;
	}

	/** Lifts target into the hand: it stops thinking, falling and colliding until it is put down or dies. */
	public void hold(LivingEntity target) {
		releaseHeld();
		heldId = target.getUUID();
		lowering = false;
		heldNoAi = false;
		if (target instanceof Mob mob) {
			heldNoAi = mob.isNoAi();
			mob.setNoAi(true);
			mob.setTarget(null);
		}
		heldNoGravity = target.isNoGravity();
		heldNoPhysics = target.noPhysics;
		target.setNoGravity(true);
		target.noPhysics = true;
		target.setDeltaMovement(Vec3.ZERO);
		this.entityData.set(DATA_HOLDING, true);
	}

	/** Starts setting the held mob gently down where it hangs. */
	public boolean startLowering() {
		LivingEntity h = held();
		if (h == null || lowering) {
			return false;
		}
		lowering = true;
		lowerX = h.getX();
		lowerZ = h.getZ();
		return true;
	}

	/** Lets go of the held mob right now, giving its normal physics and AI back. */
	public void releaseHeld() {
		if (heldId == null) {
			return;
		}
		if (level() instanceof ServerLevel sl && sl.getEntity(heldId) instanceof LivingEntity h) {
			h.setNoGravity(heldNoGravity);
			h.noPhysics = heldNoPhysics;
			if (h instanceof Mob mob) {
				mob.setNoAi(heldNoAi);
			}
			h.fallDistance = 0.0f;
			h.setDeltaMovement(Vec3.ZERO);
			h.hurtMarked = true;
		}
		heldId = null;
		lowering = false;
		this.entityData.set(DATA_HOLDING, false);
	}

	private void tickHeld(ServerLevel sl) {
		if (heldId == null) {
			return;
		}
		LivingEntity h = held();
		if (h == null) {
			releaseHeld();
			return;
		}
		h.fallDistance = 0.0f;
		h.setDeltaMovement(Vec3.ZERO);
		if (lowering) {
			double step = TitanShifterConfig.abilities().lowerSpeed;
			var below = h.getBoundingBox().move(0.0, -step - 0.05, 0.0);
			boolean grounded = !sl.noCollision(h, below) || h.getY() <= getY() + 0.05;
			if (grounded) {
				releaseHeld();
				return;
			}
			h.moveTo(lowerX, h.getY() - step, lowerZ, h.getYRot(), h.getXRot());
			return;
		}
		Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
		double hx = getX() + fwd.x * (getBbWidth() * 0.5 + 1.7);
		double hz = getZ() + fwd.z * (getBbWidth() * 0.5 + 1.7);
		double hy = getY() + getBbHeight() * 0.5 - h.getBbHeight() * 0.5;
		h.moveTo(hx, hy, hz, getYRot() + 180.0f, 0.0f);
	}

	private void tickLeap(ServerLevel sl, long now) {
		if (leaping && now - leapStartedAt > 5 && onGround()) {
			leaping = false;
			ServerPlayer owner = owner();
			if (owner != null) {
				TitanCombat.leapLanding(this, owner);
			}
		}
	}

	private void tickFootsteps(ServerLevel sl) {
		if (!TitanShifterConfig.effects().footsteps || !onGround() || isInWaterOrBubble()) {
			return;
		}
		double dx = getX() - xo;
		double dz = getZ() - zo;
		double moved = Math.sqrt(dx * dx + dz * dz);
		if (moved < 0.02) {
			return;
		}
		stride += moved;
		if (stride >= TitanShifterConfig.effects().strideBlocks) {
			stride = 0.0;
			footstep(sl, 1.0f, true);
		}
	}

	/** One heavy footfall: sound, dust, a small tremor for nearby players. */
	public void footstep(ServerLevel sl, float power, boolean alternate) {
		if (alternate) {
			rightFoot = !rightFoot;
		}
		Vec3 right = Vec3.directionFromRotation(0, getYRot() + 90.0f);
		double side = alternate ? (rightFoot ? 0.9 : -0.9) : 0.0;
		double fx = getX() + right.x * side;
		double fz = getZ() + right.z * side;
		BlockPos below = BlockPos.containing(fx, getY() - 0.2, fz);
		BlockState ground = sl.getBlockState(below);
		sl.playSound(null, fx, getY(), fz, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 2.0f * power, 0.55f);
		sl.playSound(null, fx, getY(), fz, SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 1.5f * power, 0.4f);
		if (!ground.isAir()) {
			sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), fx, getY() + 0.1, fz,
					(int) (10 * power), 0.9, 0.1, 0.9, 0.05);
		}
		sl.sendParticles(ParticleTypes.POOF, fx, getY() + 0.2, fz, (int) (3 * power), 0.7, 0.1, 0.7, 0.02);
		TitanCombat.shake(sl, position(), 0.12f * power, 6);
	}

	private void tickTrample(ServerLevel sl, long now) {
		if (!TitanShifterConfig.world().trampleWeakBlocks || now - lastTrampleAt < 4) {
			return;
		}
		Vec3 motion = getDeltaMovement();
		if (motion.horizontalDistanceSqr() < 0.0004) {
			return;
		}
		lastTrampleAt = now;
		Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
		Vec3 c = position().add(fwd.scale(getBbWidth() * 0.5 + 0.8));
		TitanCombat.breakWeakBlocks(sl, new net.minecraft.world.phys.AABB(
				c.x - getBbWidth() * 0.5, getY() + 0.05, c.z - getBbWidth() * 0.5,
				c.x + getBbWidth() * 0.5, getY() + getBbHeight(), c.z + getBbWidth() * 0.5), 24);
	}

	private void tickSteam(ServerLevel sl, long now) {
		boolean low = getHealth() < getMaxHealth() * TitanShifterConfig.effects().steamHealthFraction;
		boolean recentlyHurt = now - lastHurtAt < 100;
		boolean steaming = low || recentlyHurt || formState() != FORM_NORMAL || isHardened();
		if (steaming != isSteaming()) {
			this.entityData.set(DATA_STEAMING, steaming);
		}
		if (steaming && tickCount % 8 == 0) {
			steamBurst(sl, low ? 3 : 2);
		}
	}

	/** Vents of steam off the shoulders and back. */
	public void steamBurst(ServerLevel sl, int count) {
		Vec3 right = Vec3.directionFromRotation(0, getYRot() + 90.0f);
		double h = getBbHeight() * 0.72;
		for (int side = -1; side <= 1; side += 2) {
			double x = getX() + right.x * 1.7 * side;
			double z = getZ() + right.z * 1.7 * side;
			sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, getY() + h, z, count, 0.25, 0.1, 0.25, 0.02);
			sl.sendParticles(ParticleTypes.CLOUD, x, getY() + h, z, 1, 0.3, 0.1, 0.3, 0.01);
		}
	}

	// ---------------- damage ----------------

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (attacker != null && (attacker == getControllingPassenger() || isPassengerOfSameVehicle(attacker))) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (formState() != FORM_NORMAL || defeatStarted) {
			return false; // transforming / reverting / defeated: untouchable
		}
		boolean fromMob = attacker instanceof Mob;
		float dealt = amount;
		var res = TitanShifterConfig.resistances();
		if (source.is(DamageTypeTags.IS_FIRE)) {
			dealt *= (float) res.fireDamageFactor;
		} else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			dealt *= (float) res.explosionDamageFactor;
		}
		if (fromMob) {
			// v0.12.34: ordinary mobs still hurt -- no armour, no minor-hit cut
			dealt *= (float) res.mobDamageFactor;
		} else if (dealt < res.minorHitThreshold) {
			dealt *= (float) res.minorHitFactor;
		}
		if (isHardened()) {
			dealt *= 1.0f - (float) TitanShifterConfig.abilities().hardenDamageReduction;
		}
		// v0.16.7: the Armoured Titan's crystal plates are always on; its Brace takes three quarters more off
		dealt *= 1.0f - (float) titanType().plateReduction();
		if (hasMoveFlag(FLAG_BRACING)) {
			dealt *= 0.25f;
		}
		// v0.16.7 (user): the nape -- a hit from behind at or above the shoulders deals 3x (unless a hand guards it)
		Vec3 at = hitPoint(source);
		int zone = at == null ? 0 : zoneOf(at);
		boolean nape = zone == ZONE_NAPE;
		if (nape && !isNapeGuarded()) {
			dealt *= (float) TitanShifterConfig.nape().multiplier;
		}
		if (dealt <= 0.0f) {
			return false;
		}
		float hpBefore = getHealth();
		mobHit = fromMob;
		boolean hit;
		try {
			hit = super.hurt(source, dealt);
		} finally {
			mobHit = false;
		}
		if (hit && !defeatStarted && isAlive() && formState() == FORM_NORMAL) {
			float lost = hpBefore - getHealth();
			float max = getMaxHealth();
			ServerPlayer owner = owner();
			var nc = TitanShifterConfig.nape();
			var lc = TitanShifterConfig.limbs();
			// a heavy nape hit, or any nape hit under 20% HP, cuts the shifter out -- unless the nape is guarded or crystal
			boolean napeSafe = isNapeGuarded() || isCrystal() || isHardened();
			if (nape && !napeSafe && owner != null && owner.getVehicle() == this
					&& (lost >= max * nc.heavyHitFraction || getHealth() < max * nc.cutOutHealthFraction)) {
				lastHurtAt = level().getGameTime();
				TitanShifter.cutOut(owner, this);
				return true;
			}
			// v0.16.7 (user): a big hit on an arm / leg (or a big blast near one) severs it; hardened skin holds
			if (!isHardened() && zone != 0 && zone != ZONE_NAPE) {
				boolean blast = source.is(DamageTypeTags.IS_EXPLOSION);
				if (lost >= max * lc.severHitFraction || (blast && lost >= max * lc.severExplosionFraction)) {
					sever(zone);
				}
			}
		}
		if (hit) {
			long now = level().getGameTime();
			lastHurtAt = now;
			if (dealt >= 8.0f && now - lastHurtAnimAt > 14) {
				lastHurtAnimAt = now;
				play("hurt");
				if (level() instanceof ServerLevel sl) {
					steamBurst(sl, 4);
				}
			}
		}
		return hit;
	}

	@Override
	protected float getDamageAfterArmorAbsorb(DamageSource source, float amount) {
		return mobHit ? amount : super.getDamageAfterArmorAbsorb(source, amount);
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return super.isInvulnerableTo(source)
				|| source.is(DamageTypes.IN_WALL)
				|| source.is(DamageTypes.DROWN)
				|| source.is(DamageTypes.CRAMMING)
				|| source.is(DamageTypes.CACTUS)
				|| source.is(DamageTypes.SWEET_BERRY_BUSH)
				|| source.is(DamageTypes.FREEZE)
				|| source.is(DamageTypes.FLY_INTO_WALL);
	}

	@Override
	public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
		if (fallDistance > 3.0f && level() instanceof ServerLevel sl && !leaping && formState() == FORM_NORMAL) {
			float power = Math.min(2.0f, 0.6f + fallDistance * 0.08f);
			footstep(sl, power, false);
			sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 1.0, 0.1, 1.0, 0.0);
			TitanCombat.shake(sl, position(), 0.35f * power, 10);
			play("landing");
		}
		return false; // v0.12.36: the Titan takes no fall damage at all
	}

	/** A Titan never dies -- reaching 0 HP is the shifter's defeat, handled by {@link TitanShifter}. */
	@Override
	public void die(DamageSource source) {
		if (level().isClientSide || defeatStarted) {
			return;
		}
		releaseHeld();
		ServerPlayer owner = owner();
		if (owner == null || owner.getVehicle() != this) {
			defeatStarted = true;
			discard();
			return;
		}
		defeatStarted = true;
		setHealth(1.0f);
		TitanShifter.onTitanDefeated(owner, this);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_DAMAGE;
	}

	@Override
	protected void playHurtSound(DamageSource source) {
		playSound(SoundEvents.IRON_GOLEM_DAMAGE, 1.8f, 0.45f);
	}

	@Override
	protected SoundEvent getDeathSound() {
		return null;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// heavy footsteps are event-driven (see tickFootsteps), not per-vanilla-stride
	}

	@Override
	protected float getSoundVolume() {
		return 2.0f;
	}

	// ---------------- boilerplate ----------------

	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return List.of();
	}

	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	@Override
	public boolean showVehicleHealth() {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		// never saved
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		// never saved
	}

	@Override
	public void remove(RemovalReason reason) {
		releaseHeld();
		scheduled.clear();
		super.remove(reason);
	}

	// ---------------- GeckoLib ----------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<TitanFormEntity> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
		for (String name : new String[] { "punch", "kick", "heavy_punch", "smash", "stomp", "leap", "landing", "roar", "hurt",
				"regeneration", "hardening", "grab", "bite" }) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay(ANIM + name));
		}
		// v0.16.7 (user, Titan revamp): the new moves' clips (each type's own animation file; see TitanMove#ANIMS)
		for (String name : com.projecthero.mod.titanshifter.TitanMove.NEW_ANIMS) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay(ANIM + name));
		}
		// v0.16.7: the 4.5 s transformation clip is stretched to the shift's length (60 ticks, 12 for a Bite Shift)
		controllers.add(new AnimationController<>(this, "main", 4, this::mainPredicate).setAnimationSpeedHandler(
				f -> f.formState() == FORM_TRANSFORMING ? 90.0 / Math.max(1, f.formStateTicks())
						: f.formState() == FORM_REVERTING ? 60.0 / Math.max(1, f.formStateTicks()) : 1.0));
		// the raised right arm while a mob is in the hand (only that bone is keyed, so it layers over walk / run)
		controllers.add(new AnimationController<>(this, "carry", 6, state -> isHolding() && formState() == FORM_NORMAL
				? state.setAndContinue(CARRY) : PlayState.STOP));
		// v0.16.7 (user, Titan revamp): held poses for the timed move states (upper body only, so they layer over walk / run)
		controllers.add(new AnimationController<>(this, "flags", 5, this::flagPredicate));
		controllers.add(action);
	}

	private static final RawAnimation CHARGE_LOOP = RawAnimation.begin().thenLoop(ANIM + "shoulder_charge_loop");
	private static final RawAnimation BRACE_HOLD = RawAnimation.begin().thenLoop(ANIM + "brace_hold");
	private static final RawAnimation NAPE_HOLD = RawAnimation.begin().thenLoop(ANIM + "nape_guard_hold");
	private static final RawAnimation BOULDER_HOLD = RawAnimation.begin().thenLoop(ANIM + "boulder_hold");
	private static final RawAnimation KNEEL = RawAnimation.begin().thenLoop(ANIM + "kneel");
	private static final RawAnimation KNEEL_CRAWL = RawAnimation.begin().thenLoop(ANIM + "kneel_crawl");

	private PlayState flagPredicate(AnimationState<TitanFormEntity> state) {
		if (formState() != FORM_NORMAL) {
			return PlayState.STOP;
		}
		if (hasMoveFlag(FLAG_CHARGING)) {
			return state.setAndContinue(CHARGE_LOOP);
		}
		if (hasMoveFlag(FLAG_BRACING)) {
			return state.setAndContinue(BRACE_HOLD);
		}
		if (hasMoveFlag(FLAG_NAPE_GUARD)) {
			return state.setAndContinue(NAPE_HOLD);
		}
		if (hasMoveFlag(FLAG_HOLDING_BOULDER)) {
			return state.setAndContinue(BOULDER_HOLD);
		}
		return PlayState.STOP;
	}

	private PlayState mainPredicate(AnimationState<TitanFormEntity> state) {
		switch (formState()) {
			case FORM_TRANSFORMING:
				return state.setAndContinue(TRANSFORM);
			case FORM_REVERTING:
				return state.setAndContinue(REVERT);
			case FORM_DEFEATED:
				return state.setAndContinue(DEATH);
			default:
				break;
		}
		if (isKneeling()) {
			// v0.16.7: a leg is gone -- down on one knee, dragging itself when it moves
			return state.setAndContinue(state.getLimbSwingAmount() > 0.04f ? KNEEL_CRAWL : KNEEL);
		}
		float swing = state.getLimbSwingAmount();
		if (isRunning() && swing > 0.04f) {
			return state.setAndContinue(RUN);
		}
		if (swing > 0.04f) {
			return state.setAndContinue(WALK);
		}
		return state.setAndContinue(IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
