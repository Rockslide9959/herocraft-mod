package com.projecthero.mod.greenlantern.mech;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.joml.Vector3f;

import com.projecthero.mod.mixin.LivingEntityAccessor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * v0.17.1: the <b>Hard-Light Mech</b> -- Green Lantern's ultimate construct, a ~6.4-block armoured mech of hard light the
 * Lantern pilots from a glass cockpit in its chest.
 *
 * <h2>Movement: the pilot's client simulates it</h2>
 * Like a horse (and the Hell Cycle): {@link #getControllingPassenger()} is the pilot, so vanilla makes the pilot's client
 * authoritative for the mech's movement ({@code isControlledByLocalInstance} is true only there) and reports it with the
 * vehicle-move packet, which the server checks; every other client interpolates. Space jumps on the pilot's client
 * ({@link #tickRidden}); the Thruster Dash is a velocity the server sends to the pilot alone
 * ({@link #impulse}) -- the server never sets a ridden vehicle's velocity itself (the tracker would echo zero back).
 *
 * <h2>Attacks: the server</h2>
 * The GL ability keys arrive on the server and {@link GreenLanternMech} runs every move; clips are triggered server side
 * (GeckoLib syncs them). Damage goes through the GL path (HeroTargets rules, {@code GreenLanternRanks.power}).
 *
 * <h2>Integrity</h2>
 * Its own health pool (300 + 10% a rank). The pilot cannot be hurt while inside -- every hit on the pilot is moved onto the
 * mech ({@code GreenLanternMech#onPilotHurt}). At 0 it shatters and sets the pilot down unharmed. Never saved.
 */
public class HardLightMechEntity extends Mob implements GeoEntity {
	public static final int STATE_ASSEMBLING = 0;
	public static final int STATE_ACTIVE = 1;
	public static final int STATE_DISSOLVING = 2;

	public static final int FLAG_BEAM = 1;
	public static final int FLAG_SHIELD = 2;
	public static final int FLAG_GOLDEN = 4;
	public static final int FLAG_ROCKET_OUT = 8;

	/** Move cooldown slots, synced as absolute ready-at game times (the HUD shades the boxes from these). */
	public static final int CD_PUNCH = 0, CD_ROCKET = 1, CD_SLAM = 2, CD_DASH = 3, CD_MISSILES = 4;
	public static final int[] CD_LENGTHS = { GreenLanternMechConfig.PUNCH_COOLDOWN_TICKS, GreenLanternMechConfig.ROCKET_COOLDOWN_TICKS,
			GreenLanternMechConfig.SLAM_COOLDOWN_TICKS, GreenLanternMechConfig.DASH_COOLDOWN_TICKS, GreenLanternMechConfig.MISSILE_COOLDOWN_TICKS };

	private static final EntityDataAccessor<Optional<UUID>> OWNER =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Integer> STATE =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> STATE_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> FLAGS =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3f> BEAM_END =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Long> SLAM_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<Long>[] READY = new EntityDataAccessor[] {
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG),
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG),
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG),
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG),
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG) };

	public static final String ANIM = "animation.green_lantern_mech.";
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
	private static final RawAnimation BEAM = RawAnimation.begin().thenLoop(ANIM + "beam");
	private static final RawAnimation SHIELD = RawAnimation.begin().thenLoop(ANIM + "shield");
	private static final RawAnimation ASSEMBLE = RawAnimation.begin().thenPlayAndHold(ANIM + "assemble");
	private static final RawAnimation DISSOLVE = RawAnimation.begin().thenPlayAndHold(ANIM + "dissolve");
	public static final String[] ACTIONS = { "punch_r", "punch_l", "rocket_fist", "ground_slam", "dash", "missiles", "jump" };

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	// ---- server state
	private record Task(long due, Runnable run) {
	}

	private final List<Task> tasks = new ArrayList<>();
	private int ownerMissingTicks;
	/** Which fist the next punch uses. */
	public boolean leftNext;
	/** R is held: keep punching whenever the fist is ready. */
	public boolean punchHeld;
	public double lastServerY;
	public int dashTicksLeft;
	public Vec3 dashDir = Vec3.ZERO;
	public final java.util.Set<Integer> dashHit = new java.util.HashSet<>();
	/** The Rocket Fist in flight (a hard-light fist the server moves out and back), or null. */
	public com.projecthero.mod.greenlantern.entity.HardLightConstructEntity rocketFist;
	public Vec3 rocketDir = Vec3.ZERO;
	public double rocketTravel;
	public boolean rocketBack;
	public int rocketAge;
	public final java.util.Set<Integer> rocketHit = new java.util.HashSet<>();

	// ---- client state
	/** Client: smoothed ground speed in blocks / tick (the walk clip's speed and the footsteps come from it). */
	public float clientSpeed;
	public double clientStepDist;
	public int clientFoot;
	public boolean clientWasOnGround = true;
	public double clientFallVel;

	public HardLightMechEntity(EntityType<? extends HardLightMechEntity> type, Level level) {
		super(type, level);
		this.noCulling = true;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, GreenLanternMechConfig.BASE_INTEGRITY)
				.add(Attributes.MOVEMENT_SPEED, GreenLanternMechConfig.WALK_SPEED)
				.add(Attributes.ARMOR, GreenLanternMechConfig.ARMOR)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, GreenLanternMechConfig.STEP_HEIGHT)
				.add(Attributes.SAFE_FALL_DISTANCE, 64.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
		builder.define(STATE, STATE_ASSEMBLING);
		builder.define(STATE_AT, 0L);
		builder.define(FLAGS, 0);
		builder.define(BEAM_END, new Vector3f());
		builder.define(SLAM_AT, -1000L);
		for (EntityDataAccessor<Long> r : READY) {
			builder.define(r, 0L);
		}
	}

	// ---------------------------------------------------------------- state

	public void bind(ServerPlayer owner, float maxHp, boolean golden) {
		this.entityData.set(OWNER, Optional.of(owner.getUUID()));
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHp);
		setHealth(maxHp);
		setFlag(FLAG_GOLDEN, golden);
		setState(STATE_ASSEMBLING);
		lastServerY = getY();
	}

	public UUID ownerId() {
		return this.entityData.get(OWNER).orElse(null);
	}

	public boolean isOwner(Entity e) {
		UUID o = ownerId();
		return o != null && e != null && o.equals(e.getUUID());
	}

	public ServerPlayer pilot() {
		return getControllingPassenger() instanceof ServerPlayer sp ? sp : null;
	}

	public int state() {
		return this.entityData.get(STATE);
	}

	public void setState(int s) {
		this.entityData.set(STATE, s);
		this.entityData.set(STATE_AT, level().getGameTime());
	}

	public long stateAt() {
		return this.entityData.get(STATE_AT);
	}

	/** Ticks since the state last changed (on either side -- both read the shared game clock). */
	public long stateTicks() {
		return level().getGameTime() - stateAt();
	}

	public boolean isActive() {
		return state() == STATE_ACTIVE;
	}

	/** Test hook: skip the 2 s build. */
	public void finishAssembly() {
		if (state() == STATE_ASSEMBLING) {
			setState(STATE_ACTIVE);
		}
	}

	public boolean hasFlag(int f) {
		return (this.entityData.get(FLAGS) & f) != 0;
	}

	public void setFlag(int f, boolean on) {
		int v = this.entityData.get(FLAGS);
		int n = on ? v | f : v & ~f;
		if (n != v) {
			this.entityData.set(FLAGS, n);
		}
	}

	public boolean golden() {
		return hasFlag(FLAG_GOLDEN);
	}

	public Vec3 beamEnd() {
		Vector3f v = this.entityData.get(BEAM_END);
		return new Vec3(v.x(), v.y(), v.z());
	}

	public void setBeamEnd(Vec3 v) {
		this.entityData.set(BEAM_END, new Vector3f((float) v.x, (float) v.y, (float) v.z));
	}

	public long slamAt() {
		return this.entityData.get(SLAM_AT);
	}

	public void markSlam() {
		this.entityData.set(SLAM_AT, level().getGameTime());
	}

	public boolean ready(int slot) {
		return level().getGameTime() >= this.entityData.get(READY[slot]);
	}

	public int cooldownLeft(int slot) {
		return (int) Math.max(0L, this.entityData.get(READY[slot]) - level().getGameTime());
	}

	public void startCooldown(int slot, int ticks) {
		this.entityData.set(READY[slot], level().getGameTime() + ticks);
	}

	public void play(String anim) {
		triggerAnim("action", anim);
	}

	/** Run {@code r} on the server after {@code delay} ticks, if the mech is still standing. */
	public void schedule(int delay, Runnable r) {
		tasks.add(new Task(level().getGameTime() + Math.max(0, delay), r));
	}

	// ---------------------------------------------------------------- geometry helpers (server + client)

	/** A point in the mech's own frame (x = its left, y up, z = forward), in blocks, to the world. */
	public Vec3 local(double x, double y, double z) {
		double yaw = Math.toRadians(getYRot());
		double sin = Math.sin(yaw), cos = Math.cos(yaw);
		// vanilla: forward = (-sin, 0, cos); left = (cos, 0, sin)
		return new Vec3(getX() + x * cos - z * sin, getY() + y, getZ() + x * sin + z * cos);
	}

	/** The middle of the chest emblem (the beam comes out of it). */
	public Vec3 chest() {
		return local(0, 4.58, 1.0);
	}

	// ---------------------------------------------------------------- riding

	@Override
	protected EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
		return EntityDimensions.scalable(GreenLanternMechConfig.WIDTH, GreenLanternMechConfig.HEIGHT).withEyeHeight(GreenLanternMechConfig.HEIGHT * 0.88f);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
		return new Vec3(0.0, GreenLanternMechConfig.SEAT_Y, GreenLanternMechConfig.SEAT_Z);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && isOwner(passenger) && getPassengers().isEmpty();
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player p && isOwner(p) ? p : null;
	}

	/** Whether {@code e} sits in its own Hard-Light Mech. */
	public static boolean isPilot(Entity e) {
		return e != null && e.getVehicle() instanceof HardLightMechEntity m && m.isOwner(e);
	}

	public static HardLightMechEntity mechOf(Entity e) {
		return e != null && e.getVehicle() instanceof HardLightMechEntity m && m.isOwner(e) ? m : null;
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
		if (!isActive()) {
			return Vec3.ZERO;
		}
		float forward = player.zza;
		if (forward < 0f) {
			forward *= 0.5f; // backs up slowly
		}
		return new Vec3(player.xxa * 0.6f, 0.0, forward);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		float speed = (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
		if (hasFlag(FLAG_SHIELD)) {
			speed *= GreenLanternMechConfig.SHIELD_SPEED;
		}
		if (hasFlag(FLAG_BEAM)) {
			speed *= GreenLanternMechConfig.BEAM_SPEED;
		}
		return speed;
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		setRot(player.getYRot(), 0f);
		yRotO = yBodyRot = yHeadRot = getYRot();
		// Space: the pilot's client simulates the mech, so the jump happens there (thruster burst; the server sees it rise)
		if (level().isClientSide && isActive() && onGround() && ((LivingEntityAccessor) player).projecthero$isJumping()) {
			Vec3 v = getDeltaMovement();
			setDeltaMovement(v.x, GreenLanternMechConfig.JUMP_VELOCITY, v.z);
			this.hasImpulse = true;
		}
	}

	/** Send a velocity to the pilot's client (which moves the mech); the server bike-pattern from the Hell Cycle. */
	public void impulse(Vec3 velocity) {
		ServerPlayer p = pilot();
		if (p != null) {
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(getId(), velocity));
		}
	}

	@Override
	public boolean canSprint() {
		return false;
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
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160.0 * 160.0;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)
				|| source.is(DamageTypes.FALL) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) {
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
		if (attacker != null && (isOwner(attacker) || hasPassenger(attacker))) {
			return false; // never its own pilot
		}
		if (state() == STATE_DISSOLVING && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		// the shield wall soaks most of what comes from in front
		if (hasFlag(FLAG_SHIELD) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			Vec3 from = source.getSourcePosition();
			if (from != null) {
				Vec3 to = from.subtract(position());
				Vec3 fwd = Vec3.directionFromRotation(0f, getYRot());
				if (to.x * fwd.x + to.z * fwd.z > 0.0) {
					amount *= GreenLanternMechConfig.SHIELD_FRONT_FACTOR;
				}
			}
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel) {
			GreenLanternMech.shatter(this);
		}
	}

	@Override
	protected void tickDeath() {
		discard();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GLASS_BREAK;
	}

	@Override
	protected float getSoundVolume() {
		return 1.6f;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			double dx = getX() - xo, dz = getZ() - zo;
			float sp = (float) Math.sqrt(dx * dx + dz * dz);
			clientSpeed += (sp - clientSpeed) * 0.35f;
			return;
		}
		ServerLevel sl = (ServerLevel) level();
		if (ownerId() == null) {
			discard();
			return;
		}
		ServerPlayer pilot = pilot();
		if (pilot == null || pilot.isRemoved()) {
			if (++ownerMissingTicks > 20) {
				GreenLanternMech.forget(this);
				discard();
			}
			return;
		}
		ownerMissingTicks = 0;
		long now = sl.getGameTime();
		if (!tasks.isEmpty()) {
			for (Task t : new ArrayList<>(tasks)) {
				if (t.due() <= now) {
					tasks.remove(t);
					if (isAlive() && !isRemoved()) {
						t.run().run();
					}
				}
			}
		}
		GreenLanternMech.tickMech(this, pilot);
		lastServerY = getY();
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 4, this::mainPredicate)
				.setAnimationSpeedHandler(m -> m.isWalking() ? Math.max(0.6, Math.min(1.6, m.clientSpeed / 0.5)) : 1.0));
		AnimationController<HardLightMechEntity> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
		for (String a : ACTIONS) {
			action.triggerableAnim(a, RawAnimation.begin().thenPlay(ANIM + a));
		}
		controllers.add(action);
	}

	public boolean isWalking() {
		return isActive() && clientSpeed > 0.04f && !hasFlag(FLAG_BEAM) && !hasFlag(FLAG_SHIELD);
	}

	private PlayState mainPredicate(AnimationState<HardLightMechEntity> state) {
		switch (state()) {
			case STATE_ASSEMBLING:
				return state.setAndContinue(ASSEMBLE);
			case STATE_DISSOLVING:
				return state.setAndContinue(DISSOLVE);
			default:
				break;
		}
		if (hasFlag(FLAG_BEAM)) {
			return state.setAndContinue(BEAM);
		}
		if (hasFlag(FLAG_SHIELD)) {
			return state.setAndContinue(SHIELD);
		}
		return state.setAndContinue(isWalking() ? WALK : IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
