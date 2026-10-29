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
	/** v0.17.1 mech construct combos: the Giga Drill, the katana toggle and the katana's three moves. */
	public static final int CD_DRILL = 5, CD_KATANA = 6, CD_SLASH = 7, CD_IAIDO = 8, CD_SPIN = 9;
	/** v0.17.1 round 2 mech combos: the Meteor Mace, the Wrecking Whirl and the Lantern Cannon. */
	public static final int CD_MACE = 10, CD_WHIRL = 11, CD_CANNON = 12;
	public static final int[] CD_LENGTHS = { GreenLanternMechConfig.PUNCH_COOLDOWN_TICKS, GreenLanternMechConfig.ROCKET_COOLDOWN_TICKS,
			GreenLanternMechConfig.SLAM_COOLDOWN_TICKS, GreenLanternMechConfig.DASH_COOLDOWN_TICKS, GreenLanternMechConfig.MISSILE_COOLDOWN_TICKS,
			GreenLanternMechConfig.DRILL_COOLDOWN_TICKS, GreenLanternMechConfig.KATANA_TOGGLE_COOLDOWN_TICKS,
			GreenLanternMechConfig.SLASH_COOLDOWN_TICKS[2], GreenLanternMechConfig.IAIDO_COOLDOWN_TICKS, GreenLanternMechConfig.SPIN_COOLDOWN_TICKS,
			GreenLanternMechConfig.MACE_COOLDOWN_TICKS, GreenLanternMechConfig.WHIRL_COOLDOWN_TICKS, GreenLanternMechConfig.CANNON_COOLDOWN_TICKS };

	/**
	 * v0.17.1 round 2: the one-shot combo move playing right now ({@link #move()}), synced: none, the Meteor Mace smash,
	 * the Wrecking Whirl or the Lantern Cannon shot.
	 */
	public static final int MOVE_NONE = 0, MOVE_MACE = 1, MOVE_WHIRL = 2, MOVE_CANNON = 3;

	/** v0.17.1 Giga Drill (Mining Drill combo) states, synced. */
	public static final int DRILL_NONE = 0, DRILL_SPIN = 1, DRILL_CHARGE = 2, DRILL_RECOVER = 3;
	/** v0.17.1 Hard-Light Katana (Energy Blade combo) states, synced. */
	public static final int KATANA_NONE = 0, KATANA_DRAWING = 1, KATANA_DRAWN = 2, KATANA_SHEATHING = 3;
	/** {@link #slashKind()}: the three chain cuts, then the spin sweep and the iaido draw-cut. */
	public static final int SLASH_1 = 0, SLASH_2 = 1, SLASH_3 = 2, SLASH_SPIN = 3, SLASH_IAIDO = 4;

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
	private static final EntityDataAccessor<Long>[] READY = new EntityDataAccessor[CD_LENGTHS.length];
	static {
		for (int i = 0; i < READY.length; i++) {
			READY[i] = SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
		}
	}
	// ---- v0.17.1 construct combos (synced so every client draws the drill / katana / wave / finisher)
	private static final EntityDataAccessor<Integer> DRILL =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DRILL_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Float> DRILL_POWER =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Vector3f> DRILL_DIR =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Long> BREAK_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Vector3f> BREAK_POS =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Integer> KATANA =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> KATANA_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Long> SLASH_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> SLASH_KIND =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> HIT_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Long> WAVE_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Vector3f> WAVE_FROM =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Vector3f> WAVE_DIR =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Float> WAVE_LEN =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.FLOAT);
	// v0.17.1 round 2: the one-shot combo moves (which, since when, the aim, where the cannon shot landed / when)
	private static final EntityDataAccessor<Integer> MOVE =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> MOVE_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Vector3f> MOVE_DIR =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Vector3f> SHOT_END =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Long> SHOT_AT =
			SynchedEntityData.defineId(HardLightMechEntity.class, EntityDataSerializers.LONG);

	public static final String ANIM = "animation.green_lantern_mech.";
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
	private static final RawAnimation BEAM = RawAnimation.begin().thenLoop(ANIM + "beam");
	private static final RawAnimation SHIELD = RawAnimation.begin().thenLoop(ANIM + "shield");
	private static final RawAnimation ASSEMBLE = RawAnimation.begin().thenPlayAndHold(ANIM + "assemble");
	private static final RawAnimation DISSOLVE = RawAnimation.begin().thenPlayAndHold(ANIM + "dissolve");
	public static final String[] ACTIONS = { "punch_r", "punch_l", "rocket_fist", "ground_slam", "dash", "missiles", "jump",
			// v0.17.1 construct combos
			"drill_break", "katana_draw", "katana_sheath", "katana_slash_1", "katana_slash_2", "katana_slash_3", "katana_iaido",
			"katana_spin",
			// v0.17.1 round 2
			"mace_smash", "wrecking_whirl", "lantern_cannon" };
	private static final RawAnimation DRILL_SPIN_LOOP = RawAnimation.begin().thenLoop(ANIM + "drill_spin");
	private static final RawAnimation DRILL_CHARGE_LOOP = RawAnimation.begin().thenLoop(ANIM + "drill_charge");
	private static final RawAnimation KATANA_IDLE = RawAnimation.begin().thenLoop(ANIM + "katana_idle");
	private static final RawAnimation KATANA_WALK = RawAnimation.begin().thenLoop(ANIM + "katana_walk");

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

	// ---- v0.17.1 construct combos, server state (see GreenLanternMechCombos)
	/** The mech key that started the Giga Drill's spin (its release lets the drill go). */
	public com.projecthero.mod.hero.AbilitySlot drillSlot;
	public int drillHeld;
	public Vec3 drillStart = Vec3.ZERO;
	public Vec3 drillAim = new Vec3(0, 0, 1);
	public double drillProgress;
	public double drillBored;
	public double drillEnd;
	public int drillCharge;
	public int drillStall;
	public double drillLastAlong;
	/** Blocks the drill has bored (tests read it). */
	public int drillBroken;
	public boolean drillHitAny;
	public final java.util.Map<Integer, Integer> drillHits = new java.util.HashMap<>();
	public final java.util.Set<Long> drillDone = new java.util.HashSet<>();
	/** R is held with the katana out: keep cutting whenever the next cut is ready. */
	public boolean slashHeld;
	public int slashStep = -1;
	public long lastSlashAt = -1000L;
	public boolean katanaHitAny;
	/** The iaido crescent in flight (the server's simulation of what WAVE_* shows). */
	public boolean waveActive;
	public double waveTravel;
	public final java.util.Set<Integer> waveHit = new java.util.HashSet<>();

	// ---- client state
	/** Client: smoothed ground speed in blocks / tick (the walk clip's speed and the footsteps come from it). */
	public float clientSpeed;
	public double clientStepDist;
	public int clientFoot;
	public boolean clientWasOnGround = true;
	public double clientFallVel;
	/** v0.17.1 client: the katana's recent edge positions (world space: base xyz, tip xyz, time) for the slash trail. */
	public final java.util.ArrayDeque<double[]> clientTrail = new java.util.ArrayDeque<>();
	public long clientLastBreakSeen = Long.MIN_VALUE;
	public long clientLastHitSeen = Long.MIN_VALUE;

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
		builder.define(DRILL, DRILL_NONE);
		builder.define(DRILL_AT, 0L);
		builder.define(DRILL_POWER, 0f);
		builder.define(DRILL_DIR, new Vector3f(0, 0, 1));
		builder.define(BREAK_AT, -1000L);
		builder.define(BREAK_POS, new Vector3f());
		builder.define(KATANA, KATANA_NONE);
		builder.define(KATANA_AT, 0L);
		builder.define(SLASH_AT, -1000L);
		builder.define(SLASH_KIND, 0);
		builder.define(HIT_AT, -1000L);
		builder.define(WAVE_AT, -1000L);
		builder.define(WAVE_FROM, new Vector3f());
		builder.define(WAVE_DIR, new Vector3f(0, 0, 1));
		builder.define(WAVE_LEN, 0f);
		builder.define(MOVE, MOVE_NONE);
		builder.define(MOVE_AT, 0L);
		builder.define(MOVE_DIR, new Vector3f(0, 0, 1));
		builder.define(SHOT_END, new Vector3f());
		builder.define(SHOT_AT, -1000L);
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

	// ---------------------------------------------------------------- v0.17.1 construct combos (synced state)

	private static Vec3 vec(Vector3f v) {
		return new Vec3(v.x(), v.y(), v.z());
	}

	private static Vector3f vec(Vec3 v) {
		return new Vector3f((float) v.x, (float) v.y, (float) v.z);
	}

	public int drillState() {
		return this.entityData.get(DRILL);
	}

	public void setDrillState(int s) {
		this.entityData.set(DRILL, s);
		this.entityData.set(DRILL_AT, level().getGameTime());
	}

	/** Ticks since the drill state last changed (both sides read the shared game clock). */
	public long drillStateTicks() {
		return level().getGameTime() - this.entityData.get(DRILL_AT);
	}

	/** 0..1: how far the drill was spun up when it was let go (the charge and its finisher read this). */
	public float drillPower() {
		return this.entityData.get(DRILL_POWER);
	}

	public void setDrillPower(float k) {
		this.entityData.set(DRILL_POWER, k);
	}

	public Vec3 drillDir() {
		return vec(this.entityData.get(DRILL_DIR));
	}

	public void setDrillDir(Vec3 d) {
		this.entityData.set(DRILL_DIR, vec(d));
	}

	public long breakAt() {
		return this.entityData.get(BREAK_AT);
	}

	public Vec3 breakPos() {
		return vec(this.entityData.get(BREAK_POS));
	}

	public void markBreak(Vec3 at) {
		this.entityData.set(BREAK_POS, vec(at));
		this.entityData.set(BREAK_AT, level().getGameTime());
	}

	public int katanaState() {
		return this.entityData.get(KATANA);
	}

	public void setKatanaState(int s) {
		this.entityData.set(KATANA, s);
		this.entityData.set(KATANA_AT, level().getGameTime());
	}

	public long katanaStateTicks() {
		return level().getGameTime() - this.entityData.get(KATANA_AT);
	}

	/** The katana is in the mech's hand (being drawn, drawn or being sheathed). */
	public boolean katanaOut() {
		return katanaState() != KATANA_NONE;
	}

	public boolean katanaDrawn() {
		return katanaState() == KATANA_DRAWN;
	}

	public long slashAt() {
		return this.entityData.get(SLASH_AT);
	}

	public int slashKind() {
		return this.entityData.get(SLASH_KIND);
	}

	public void markSlash(int kind) {
		this.entityData.set(SLASH_KIND, kind);
		this.entityData.set(SLASH_AT, level().getGameTime());
	}

	public long hitAt() {
		return this.entityData.get(HIT_AT);
	}

	public void markHit() {
		this.entityData.set(HIT_AT, level().getGameTime());
	}

	public long waveAt() {
		return this.entityData.get(WAVE_AT);
	}

	public Vec3 waveFrom() {
		return vec(this.entityData.get(WAVE_FROM));
	}

	public Vec3 waveDir() {
		return vec(this.entityData.get(WAVE_DIR));
	}

	public float waveLen() {
		return this.entityData.get(WAVE_LEN);
	}

	public void launchWave(Vec3 from, Vec3 dir, float len) {
		this.entityData.set(WAVE_FROM, vec(from));
		this.entityData.set(WAVE_DIR, vec(dir));
		this.entityData.set(WAVE_LEN, len);
		this.entityData.set(WAVE_AT, level().getGameTime());
	}

	public void setWaveLen(float len) {
		this.entityData.set(WAVE_LEN, len);
	}

	// ---- v0.17.1 round 2: the one-shot combo moves

	public int move() {
		return this.entityData.get(MOVE);
	}

	public void setMove(int move) {
		this.entityData.set(MOVE, move);
		this.entityData.set(MOVE_AT, level().getGameTime());
	}

	public long moveTicks() {
		return level().getGameTime() - this.entityData.get(MOVE_AT);
	}

	public Vec3 moveDir() {
		return vec(this.entityData.get(MOVE_DIR));
	}

	public void setMoveDir(Vec3 d) {
		this.entityData.set(MOVE_DIR, vec(d));
	}

	public Vec3 shotEnd() {
		return vec(this.entityData.get(SHOT_END));
	}

	public long shotAt() {
		return this.entityData.get(SHOT_AT);
	}

	public void markShot(Vec3 end) {
		this.entityData.set(SHOT_END, vec(end));
		this.entityData.set(SHOT_AT, level().getGameTime());
	}

	/** v0.17.1: when C went down in the mech (a short tap fires the pods on release; a hold opens the Construct Menu). */
	public long cPressAt = -1L;
	/** v0.17.1 round 2: the current combo move has hit something (Willpower XP once per move). */
	public boolean moveHitAny;

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
		if (!isActive() || drillState() == DRILL_CHARGE) {
			return Vec3.ZERO; // v0.17.1: the Giga Drill's charge steers itself
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
		if (drillState() == DRILL_SPIN) {
			speed *= GreenLanternMechConfig.DRILL_SPIN_SPEED;
		}
		if (move() != MOVE_NONE) {
			speed *= move() == MOVE_WHIRL ? GreenLanternMechConfig.WHIRL_SPEED : GreenLanternMechConfig.MOVE_SPEED; // v0.17.1 round 2
		}
		return speed;
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		if (drillState() == DRILL_CHARGE) {
			// v0.17.1: the Giga Drill charges along the aim it was let go with -- the body stays on that line
			Vec3 d = drillDir();
			setRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)), 0f);
		} else {
			setRot(player.getYRot(), 0f);
		}
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
		AnimationController<HardLightMechEntity> action = new AnimationController<HardLightMechEntity>(this, "action", 2, state -> PlayState.STOP)
				// v0.17.1: hit-stop -- a katana cut that connects freezes the swing for a beat
				.setAnimationSpeedHandler(m -> m.inHitStop() ? 0.08 : 1.0);
		for (String a : ACTIONS) {
			action.triggerableAnim(a, RawAnimation.begin().thenPlay(ANIM + a));
		}
		controllers.add(action);
	}

	public boolean isWalking() {
		return isActive() && clientSpeed > 0.04f && !hasFlag(FLAG_BEAM) && !hasFlag(FLAG_SHIELD) && drillState() != DRILL_CHARGE;
	}

	/** Client: a katana cut connected a moment ago (the swing holds still for {@link GreenLanternMechConfig#HIT_STOP_TICKS}). */
	public boolean inHitStop() {
		long since = level().getGameTime() - hitAt();
		// two ticks late: the clip reaches the client (and blends in) about that much after the server swung it
		return since >= 2 && since < 2 + GreenLanternMechConfig.HIT_STOP_TICKS;
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
		// v0.17.1 construct combos
		int drill = drillState();
		if (drill == DRILL_CHARGE) {
			return state.setAndContinue(DRILL_CHARGE_LOOP);
		}
		if (drill == DRILL_SPIN) {
			return state.setAndContinue(DRILL_SPIN_LOOP);
		}
		if (hasFlag(FLAG_BEAM)) {
			return state.setAndContinue(BEAM);
		}
		if (hasFlag(FLAG_SHIELD)) {
			return state.setAndContinue(SHIELD);
		}
		if (katanaOut()) {
			return state.setAndContinue(isWalking() ? KATANA_WALK : KATANA_IDLE);
		}
		return state.setAndContinue(isWalking() ? WALK : IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
