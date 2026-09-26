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
	}

	/** Server: bind this Titan to its owner and stats. Call before adding to the world. */
	public void bind(ServerPlayer owner, TitanType type) {
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

	private void applyTypeStats(TitanType type) {
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(type.health());
		getAttribute(Attributes.ARMOR).setBaseValue(type.armorValue());
		getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(type.toughnessValue());
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(type.speedValue());
		getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(TitanShifterConfig.stats().knockbackResistance);
		getAttribute(Attributes.STEP_HEIGHT).setBaseValue(TitanShifterConfig.stats().stepHeight);
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
		if (DATA_TYPE.equals(key)) {
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
		var t = TitanShifterConfig.transformation();
		int elapsed = tickCount - stateChangedTick;
		switch (formState()) {
			case FORM_TRANSFORMING: {
				float p = Math.min(1.0f, elapsed / (float) Math.max(1, t.transformTicks));
				// mirrors the keyframes of animation.titan.transformation (0.25 -> 0.5 -> 1.05 -> 1.0)
				if (p < 1.0f / 3.0f) {
					return 0.25f + 0.25f * (p / (1.0f / 3.0f));
				}
				if (p < 2.2f / 3.0f) {
					return 0.5f + 0.55f * ((p - 1.0f / 3.0f) / (2.2f / 3.0f - 1.0f / 3.0f));
				}
				return 1.05f - 0.05f * ((p - 2.2f / 3.0f) / (1.0f - 2.2f / 3.0f));
			}
			case FORM_REVERTING: {
				float p = Math.min(1.0f, elapsed / (float) Math.max(1, t.revertTicks));
				return p < 0.5f ? 1.0f - 0.4f * p : 0.8f - 1.1f * (p - 0.5f);
			}
			case FORM_DEFEATED: {
				float p = Math.min(1.0f, elapsed / (float) Math.max(1, t.defeatTicks));
				return 1.0f - 0.6f * p;
			}
			default:
				return 1.0f;
		}
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		TitanType type = this.entityData == null ? TitanType.GENERIC_TITAN : titanType();
		return EntityDimensions.scalable(type.dimensionWidth(), type.dimensionHeight())
				.withEyeHeight(type.dimensionHeight() * 0.9f); // a player's eyes sit at 0.9 of their height
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
		return (float) speed;
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		float yaw = player.getYRot();
		this.setRot(yaw, Math.max(-30.0f, Math.min(30.0f, player.getXRot() * 0.4f)));
		this.yRotO = this.yBodyRot = this.yHeadRot = yaw;
		if (!level().isClientSide && !isLocked() && onGround()
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
		boolean forward = owner.zza > 0.1f && !isLocked();
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
		if (dealt <= 0.0f) {
			return false;
		}
		mobHit = fromMob;
		boolean hit;
		try {
			hit = super.hurt(source, dealt);
		} finally {
			mobHit = false;
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
		return super.causeFallDamage(fallDistance, multiplier * (float) TitanShifterConfig.resistances().fallDamageFactor, source);
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
		controllers.add(new AnimationController<>(this, "main", 4, this::mainPredicate));
		// the raised right arm while a mob is in the hand (only that bone is keyed, so it layers over walk / run)
		controllers.add(new AnimationController<>(this, "carry", 6, state -> isHolding() && formState() == FORM_NORMAL
				? state.setAndContinue(CARRY) : PlayState.STOP));
		controllers.add(action);
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
