package com.projecthero.mod.omnitrix.alien.cannonbolt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "build canonbolt alien for the prototype omnitrix please ... do research on his powers and look at what he does in
 * the show aswell when building his kit please, change his abilities if hes in ball mode or not please"). Cannonbolt, an Arburian
 * Pelarota: a big, slow, top-heavy brute with his face on his chest and a shell of gold plates on his back, shoulders and arms. He
 * curls into a near-indestructible ball and rolls like a cannonball -- ramming through enemies and walls, ricocheting round a room
 * like a pinball, bouncing, rolling up walls, shrugging lasers off his shell. Spec: {@code docs/OMNITRIX_REVAMP_CANNONBOLT.md}.
 *
 * <p>V curls him up / uncurls him: in <b>Ball Mode</b> R, G, X and V swap for rolling moves ({@link #liveMoveOn}), his hitbox is a
 * two-block ball ({@link #body}), a sprint builds his Momentum from 10 to 25 blocks a second ({@link CannonboltRoll}), he rams whatever
 * he rolls into, rolls up ledges and through leaves, bounces off any fall, deflects projectiles and takes half damage -- but he cannot
 * punch or use tools. Z, Shift+Z, C and Shift+C are the same either way, and up to two squad mates can ride inside ({@link CannonboltStow}).
 */
public final class CannonboltKit extends AlienKit {
	public static final String ID = "cannonbolt";
	public static final int COLOUR = 0xDABE0B;

	// ---------------------------------------------------------------- synced flags
	/** Ball Mode (V, or anything that curls him for good): the ball hitbox, the swapped keys. */
	public static final int F_BALL = 1;
	/** Drawn as the ball right now (Ball Mode, or a curl mid-move: the Tumble, the Curl Smash's drop, the Cannonball Launch). */
	public static final int F_ROUND = 2;
	public static final int F_BOOST = 4;
	public static final int F_SPIN = 8;
	public static final int F_GUARD = 32;
	/** The Rolling Rampage: rolling from enemy to enemy. */
	public static final int F_RAMPAGE = 64;
	/** In the air from one of his own moves (no fall damage, the landing hits). */
	public static final int F_AIR = 128;
	/** The Spin Charge: spinning on the spot winding up Momentum, WASD dead. */
	public static final int F_CHARGING = 256;
	/** One of his moves is driving him: his keys do nothing, the server's velocity is what moves him. */
	public static final int F_DRIVEN = 512;
	/** The ultimate: the shell's spikes are out, from the dash to the slam. */
	public static final int F_SPIKES = 1024;

	// ---------------------------------------------------------------- poses
	public static final int ANIM_BASH = 1, ANIM_SLAM = 2, ANIM_CURL = 3, ANIM_GUARD = 4, ANIM_POUND = 5, ANIM_UNCURL = 6, ANIM_PUNCH = 7, ANIM_GUARD_END = 8;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.cannonbolt.<id>)
	// standing
	public static final String BASH = "shoulder_bash", SLAM = "shell_slam", CURL_SMASH = "curl_smash", GUARD = "shell_guard", TUMBLE = "tumble_roll",
			POUND = "ground_pound", LAUNCH = "cannonball_launch", CURL = "curl_up", FISTS = "pelarota_fists", BACK = "shell_back",
			SPIN_CHARGE = "spin_charge", REBOUND = "rebound_strike", UNSTOPPABLE = "unstoppable_cannonball";
	// Ball Mode
	public static final String CHARGE = "cannonball_charge", RAMPAGE = "rolling_rampage", BOUNCE = "big_bounce", DROP = "meteor_drop",
			BOOST = "roll_boost", SPIN = "shell_spin", UNCURL = "uncurl", RAM = "ram", SHELL = "armoured_shell",
			WALL_ROLL = "wall_roll", STOW = "shell_shuttle";

	// ---------------------------------------------------------------- numbers (quick 5-8, normal 12-17, big 17-20, ultimate 21-36; the user's own where given)
	public static final float BASH_DAMAGE = 14f, SLAM_DAMAGE = 20f, CURL_SMASH_DAMAGE = 20f, TUMBLE_DAMAGE = 8f, POUND_DAMAGE = 16f, LAUNCH_DAMAGE = 20f;
	public static final float CHARGE_DAMAGE = 20f, RAMPAGE_DAMAGE = 20f, BOUNCE_DAMAGE = 18f, DROP_DAMAGE = 20f, SPIN_DAMAGE = 8f;
	/** The Rebound Strike: two hits and the big last one. */
	public static final float REBOUND_DAMAGE = 12f, REBOUND_LAST_DAMAGE = 17f;
	/** The ultimate (user: "dash towards the target and deals 30 damage ... boost up into the air and slams down on them dealing another 30"). */
	public static final float ULT_HIT_DAMAGE = 30f, CRASH_DAMAGE = 30f;
	public static final int TUMBLE_TICKS = 10, LAUNCH_TICKS = 24, CHARGE_TICKS = 20, BOOST_TICKS = 100, SPIN_TICKS = 40;
	/** How far off he can lock on, how fast the dash goes (blocks a tick) and how long it may try. */
	public static final double ULT_RANGE = 30.0, ULT_DASH_SPEED = 2.0;
	public static final int ULT_DASH_TICKS = 40;
	/** The Spin Charge: ticks of holding C to full Momentum, and the most he can hold it. */
	public static final int SPIN_CHARGE_FILL = 30, SPIN_CHARGE_MAX = 200;
	/**
	 * v0.17.2 round 3 (user: "actually autotrack the 20 nearest enemies on a similar Y level of the player within 50 blocks and then roll
	 * and attack them dealing 20 damage"): the reach, how far above or below him counts as a similar level, and how long he may
	 * fail to get closer to one enemy before giving it up.
	 */
	public static final int RAMPAGE_TARGETS = 20, RAMPAGE_STUCK_TICKS = 20, RAMPAGE_TICKS = 600;
	public static final double RAMPAGE_RANGE = 30.0, RAMPAGE_Y = 5.0;
	public static final int REBOUND_HITS = 3;
	public static final double REBOUND_RANGE = 24.0, REBOUND_SPEED = 1.6;
	/** Horizontal blocks / tick before rolling into something hurts it. */
	public static final double RAM_SPEED = 0.3;
	/** v0.17.2 (user: "when he sprint jumps its more like a forward launch kind of thing"): the launch's speed out (blocks / tick) and lift. */
	public static final double LAUNCH_SPEED = 1.7, LAUNCH_LIFT = 0.5;
	public static final float LAUNCH_LAND_DAMAGE = 12f;
	public static final int LAUNCH_GAP_TICKS = 16;
	/**
	 * v0.17.2 (user: "if he falls from a great distance the ball form should bounce a bit first"): after a fall that would hurt
	 * (8+ blocks) the ball bounces up to this many times, each {@link #BOUNCE_KEEP} of the last, then settles.
	 */
	public static final int FALL_BOUNCES = 3;
	public static final double BOUNCE_KEEP = 0.55, BOUNCE_MIN = 0.22;
	/**
	 * v0.17.2 round 3 (user: "make cannonbolt 2 blocks tall please even without his head"): the body's scale. His headless body is 24 px
	 * of the player model, and vanilla draws players at 0.9375, so 2 blocks of him is 2 / (1.5 x 0.9375).
	 */
	public static final float SCALE = 2f / 1.40625f;
	/** v0.17.2 round 3 (user: "Make his ball form 2 blocks tall aswell please"): the ball's hitbox before the body's scale. */
	public static final float BALL_SIZE = 2f / SCALE;
	/**
	 * v0.17.2 (user: "make the players view come out of their chest please since they dont have a head" / "make the nametag appear
	 * above the body instead of above the chest please"): standing, the box ends at his headless body's top (24 px), the eyes sit in
	 * his chest and the nametag just over his shoulders -- before the body's scale ({@link #SCALE}).
	 */
	public static final float BODY_HEIGHT = 1.40625f, CHEST_EYE = 1.14f, CROUCH_HEIGHT = 1.2f, CROUCH_EYE = 0.92f, TAG_DROP = -0.15f; // v0.17.2 (user: "make the nametag a bit higher since right now its inside his body"): was 0.4

	private static final ResourceLocation ROLL_SPEED = PowerToggles.id("omnitrix_cannonbolt_roll"); // gone since CannonboltRoll: only ever cleared
	private static final ResourceLocation ROLL_STEP = PowerToggles.id("omnitrix_cannonbolt_step");
	private static final ResourceLocation STEADY = PowerToggles.id("omnitrix_cannonbolt_steady");

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SND_CURL = OmnitrixSfx.cue("cannonbolt_curl")
			.layer(SoundEvents.ARMOR_EQUIP_NETHERITE, 0.5f, 0.7f)
			.layer(SoundEvents.ANVIL_PLACE, 0.12f, 1.6f, 2);
	public static final OmnitrixSfx.Cue SND_UNCURL = OmnitrixSfx.cue("cannonbolt_uncurl")
			.layer(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.5f, 0.6f)
			.layer(SoundEvents.SHULKER_OPEN, 0.3f, 0.6f, 1);
	public static final OmnitrixSfx.Cue SND_IMPACT = OmnitrixSfx.cue("cannonbolt_impact")
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.5f, 0.6f)
			.layer(SoundEvents.ANVIL_LAND, 0.15f, 0.55f);
	// v0.17.2 (user: "when cannonbolt jumps and bounces dont make it an anvil sound, just make it a bounce sound of like light crash
	// sound kind of thing please"): a springy thud and a light crunch, no metal
	public static final OmnitrixSfx.Cue SND_RICOCHET = OmnitrixSfx.cue("cannonbolt_ricochet")
			.layer(SoundEvents.SLIME_BLOCK_FALL, 0.7f, 0.75f)
			.layer(SoundEvents.GENERIC_SMALL_FALL, 0.5f, 0.7f)
			.layer(SoundEvents.GRAVEL_BREAK, 0.25f, 0.8f, 1);
	public static final OmnitrixSfx.Cue SND_QUAKE = OmnitrixSfx.cue("cannonbolt_quake")
			.layer(SoundEvents.GENERIC_EXPLODE.value(), 0.35f, 0.6f)
			.layer(SoundEvents.GENERIC_BIG_FALL, 0.6f, 0.6f, 1); // v0.17.2 (user): a heavy landing, no anvil
	public static final OmnitrixSfx.Cue SND_ROLL = OmnitrixSfx.cue("cannonbolt_roll")
			.layer(SoundEvents.MINECART_RIDING, 0.18f, 0.55f)
			.gap(16);
	public static final OmnitrixSfx.Cue SND_DEFLECT = OmnitrixSfx.cue("cannonbolt_deflect")
			.layer(SoundEvents.SHIELD_BLOCK, 0.45f, 1.2f)
			.layer(SoundEvents.ANVIL_LAND, 0.08f, 1.8f);

	// ---------------------------------------------------------------- server-only move state
	private enum DashKind {
		TUMBLE, LAUNCH, CHARGE
	}

	/** The Rolling Rampage: who is left (creature ids, nearest-next order), the one he is rolling for and how close he has got to it. */
	private static final class Rush {
		final List<Integer> left;
		final Set<Integer> hit = new HashSet<>();
		final long end;
		double best = Double.MAX_VALUE;
		int stuck;

		Rush(List<Integer> left, long end) {
			this.left = left;
			this.end = end;
		}
	}

	/** The Rebound Strike: the mark, strikes landed, and whether he is on the way in or hopping back off the last hit. */
	private static final class Rebound {
		final int target;
		Vec3 aim;
		final long start;
		int hits;
		long hopUntil;
		long strikeStart;

		Rebound(int target, Vec3 aim, long start) {
			this.target = target;
			this.aim = aim;
			this.start = start;
			this.strikeStart = start;
		}
	}

	private record Dash(DashKind kind, Vec3 dir, double speed, long end, Set<Integer> hits) {
	}

	private enum AirKind {
		CURL_SMASH, BOUNCE, DROP, LAUNCH, CRASH
	}

	private static final class Air {
		final AirKind kind;
		final long start;
		boolean falling;
		/** The Sprint Launch: the way he was fired (he lands still rolling that way). */
		Vec3 dir = Vec3.ZERO;

		Air(AirKind kind, long start) {
			this.kind = kind;
			this.start = start;
		}
	}

	/** v0.17.2: a high fall's bounces still to come -- the next bounce's lift, how many are left, when it began. */
	private static final class Bounces {
		double lift;
		int left;
		final long start;
		boolean airborne;

		Bounces(double lift, int left, long start) {
			this.lift = lift;
			this.left = left;
			this.start = start;
		}
	}

	private static final Map<UUID, Bounces> BOUNCES = new ConcurrentHashMap<>();
	/** Last tick's real motion (the client moves the player: the server's own velocity is not his). */
	private static final Map<UUID, Vec3> LAST_MOVE = new ConcurrentHashMap<>();
	/** Whether he stood on the ground last tick (the sprint jump), and the tick the last launch fired. */
	private static final Map<UUID, Boolean> WAS_GROUND = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_LAUNCH = new ConcurrentHashMap<>();

	private static final Map<UUID, Dash> DASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Rush> RUSHES = new ConcurrentHashMap<>();
	/** The ultimate, from the press to the slam: who it is for, and whether the dash has landed. */
	private static final class Smash {
		final int target;
		final long start;
		boolean dashed;

		Smash(int target, long start) {
			this.target = target;
			this.start = start;
		}
	}

	private static final Map<UUID, Smash> SMASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Rebound> REBOUNDS = new ConcurrentHashMap<>();
	/** A Spin Charge just let go: the tick, until he is seen rolling away (or isn't) -- the takeoff's burst. */
	private static final Map<UUID, Long> RELEASED = new ConcurrentHashMap<>();
	private static final Map<UUID, Air> AIRS = new ConcurrentHashMap<>();
	/** Last tick's position (the real rolling speed: the client moves the player). */
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	/** Momentum as the server reads it from his real speed, 0..1 (the owner's client simulates the roll: {@link CannonboltRoll}). */
	private static final Map<UUID, Double> MOMENTUM = new ConcurrentHashMap<>();
	/** Creature id -> the tick the ball last rammed it. */
	private static final Map<UUID, Map<Integer, Long>> RAMMED = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private CannonboltKit() {
	}

	public static void register() {
		Aliens.register(new CannonboltKit());
		CannonboltStow.initialize();
	}

	public static void clearSessionState() {
		DASHES.clear();
		RUSHES.clear();
		SMASHES.clear();
		REBOUNDS.clear();
		RELEASED.clear();
		AIRS.clear();
		LAST_POS.clear();
		MOMENTUM.clear();
		RAMMED.clear();
		LAST_TICK.clear();
		BOUNCES.clear();
		LAST_MOVE.clear();
		WAS_GROUND.clear();
		LAST_LAUNCH.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// a heavy, slow brute (the show: he lumbers when uncurled) -- his shell plates are diamond-grade armour
		return AlienStats.builder().health(40f).speed(0.8f).scale(SCALE).armor(16f, 3f).knockbackResist(0.5f).attack(9f).step(0.4f).jump(0.02f)
				.safeFall(4f).reach(0.3f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				// standing
				AlienMove.tap(BASH, AbilitySlot.SLOT_1, 24), AlienMove.shift(SLAM, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(LAUNCH, AbilitySlot.SLOT_2, 240), AlienMove.shift(POUND, AbilitySlot.SLOT_2, 140),
				AlienMove.tap(TUMBLE, AbilitySlot.SLOT_3, 80),
				AlienMove.tap(CURL, AbilitySlot.SLOT_5, 10), AlienMove.shift(GUARD, AbilitySlot.SLOT_5, 80),
				// the same in both forms (they curl him up if he is standing)
				AlienMove.tap(REBOUND, AbilitySlot.SLOT_4, 160), AlienMove.shift(UNSTOPPABLE, AbilitySlot.SLOT_4, 1200),
				AlienMove.tap(SPIN_CHARGE, AbilitySlot.SLOT_6, 60), AlienMove.shift(CURL_SMASH, AbilitySlot.SLOT_6, 200),
				AlienMove.vanilla(FISTS, "Attack", 0), AlienMove.vanilla(BACK, "Passive", 0),
				// Ball Mode (the HUD shows them on the same keys while he is rolled up, see liveMoveOn)
				AlienMove.vanilla(CHARGE, "Ball Mode: R", 100), AlienMove.vanilla(RAMPAGE, "Ball Mode: Shift+R", 400),
				AlienMove.vanilla(BOUNCE, "Ball Mode: G", 80), AlienMove.vanilla(DROP, "Ball Mode: Shift+G", 400),
				AlienMove.vanilla(BOOST, "Ball Mode: X", 240),
				AlienMove.vanilla(UNCURL, "Ball Mode: V", 10), AlienMove.vanilla(SPIN, "Ball Mode: Shift+V", 160),
				AlienMove.vanilla(RAM, "Ball Mode: roll into it", 0), AlienMove.vanilla(SHELL, "Ball Mode: passive", 0),
				AlienMove.vanilla(WALL_ROLL, "Ball Mode: roll up a ledge", 0), AlienMove.vanilla(STOW, "Ball Mode: squad mates right-click him", 0));
	}

	/** Ball Mode swaps R, G, X and V for their rolling moves; Z and C are the same either way. */
	@Override
	public AlienMove liveMoveOn(Player p, AbilitySlot slot, boolean shift, boolean hold) {
		if (!flag(p, F_BALL) || hold) {
			return moveOn(slot, shift, hold);
		}
		return switch (slot) {
			case SLOT_1 -> move(shift ? RAMPAGE : CHARGE);
			case SLOT_2 -> move(shift ? DROP : BOUNCE);
			case SLOT_3 -> shift ? null : move(BOOST);
			case SLOT_5 -> move(shift ? SPIN : UNCURL);
			default -> moveOn(slot, shift, hold);
		};
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_5 -> flag(p, F_BALL) || flag(p, F_GUARD);
			case SLOT_3 -> flag(p, F_BOOST);
			case SLOT_4 -> flag(p, F_SPIKES);
			case SLOT_6 -> flag(p, F_CHARGING);
			case SLOT_1 -> flag(p, F_RAMPAGE);
			default -> false;
		};
	}

	/** v0.17.2: {@code p} is Cannonbolt rolled up right now (the server's "moved too quickly" check waves his top speed through). */
	public static boolean rolling(Player p) {
		return p != null && ID.equals(Omnitrix.active(p)) && ball(p);
	}

	/** Client-safe: whether {@code p} is rolled up (Ball Mode). */
	public static boolean ball(Player p) {
		return (Omnitrix.state(p).kitFlags & F_BALL) != 0;
	}

	// ---------------------------------------------------------------- the body

	@Override
	@Nullable
	public EntityDimensions body(Player p, Pose pose) {
		if (ball(p)) {
			return BALL_BODY;
		}
		return switch (pose) {
			case STANDING -> STANDING_BODY;
			case CROUCHING -> CROUCHING_BODY;
			default -> null;
		};
	}

	// built once: the body mixin refreshes the hitbox whenever the answer stops being equal to the last one
	private static final EntityDimensions BALL_BODY = EntityDimensions.scalable(BALL_SIZE, BALL_SIZE).withEyeHeight(BALL_SIZE * 0.58f)
			.withAttachments(net.minecraft.world.entity.EntityAttachments.builder()
					.attach(net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0f, BALL_SIZE, 0f)
					.attach(net.minecraft.world.entity.EntityAttachment.PASSENGER, 0f, 0f, 0f)); // a mate rides inside, not on top
	private static final EntityDimensions STANDING_BODY = headless(BODY_HEIGHT, CHEST_EYE);
	private static final EntityDimensions CROUCHING_BODY = headless(CROUCH_HEIGHT, CROUCH_EYE);

	/** A headless body {@code height} tall, the eyes in his chest, the nametag a little under the box's top (the renderer adds 0.5). */
	private static EntityDimensions headless(float height, float eye) {
		return EntityDimensions.scalable(0.6f, height).withEyeHeight(eye).withAttachments(net.minecraft.world.entity.EntityAttachments.builder()
				.attach(net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0f, height - TAG_DROP, 0f)
				.attach(net.minecraft.world.entity.EntityAttachment.VEHICLE, Player.DEFAULT_VEHICLE_ATTACHMENT));
	}

	/** v0.17.2 (user: "make it so that cannonbolts ball form cant climb walls and give his ball form a 4 blocks step assist please"). */
	public static final float BALL_STEP = 4f;

	@Override
	public boolean climbs(Player p) {
		return false; // v0.17.2 (user): the ball no longer rolls up walls -- it rolls up ledges instead (stepHeight)
	}

	@Override
	public float stepHeight(Player p) {
		return flag(p, F_BALL) ? BALL_STEP : -1f; // the ball rolls straight up a ledge up to 4 blocks high
	}

	@Override
	public boolean pawsOnly(Player p) {
		return flag(p, F_BALL); // no hands out of the shell
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_UNCURL, 1.0f, 0.9f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.6f).life(14).ground().spawn();
	}

	@Override
	public void onRevert(ServerPlayer p) {
		CannonboltStow.releaseAll(p); // whatever turned him back, anyone inside is out and dizzy
		for (int f : new int[] { F_BALL, F_ROUND, F_BOOST, F_SPIN, F_GUARD, F_RAMPAGE, F_AIR, F_CHARGING, F_DRIVEN, F_SPIKES }) {
			setFlag(p, f, false);
		}
		for (String t : new String[] { "boost_end", "spin_end", "charge_start" }) {
			setTimer(p, t, 0);
		}
		clearRoll(p);
		UUID id = p.getUUID();
		DASHES.remove(id);
		RUSHES.remove(id);
		SMASHES.remove(id);
		REBOUNDS.remove(id);
		RELEASED.remove(id);
		AIRS.remove(id);
		LAST_POS.remove(id);
		MOMENTUM.remove(id);
		RAMMED.remove(id);
		LAST_TICK.remove(id);
		BOUNCES.remove(id);
		LAST_MOVE.remove(id);
		WAS_GROUND.remove(id);
		LAST_LAUNCH.remove(id);
	}

	private static void clearRoll(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, ROLL_SPEED);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, ROLL_STEP);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, STEADY);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (slot == AbilitySlot.SLOT_4) {
			if (shift) {
				unstoppable(p);
			} else {
				reboundStrike(p);
			}
			return;
		}
		if (slot == AbilitySlot.SLOT_6) {
			if (shift) {
				curlSmash(p);
			} else {
				spinChargeStart(p);
			}
			return;
		}
		if (flag(p, F_BALL)) {
			switch (slot) {
				case SLOT_1 -> {
					if (shift) {
						rampage(p);
					} else {
						charge(p);
					}
				}
				case SLOT_2 -> {
					if (shift) {
						meteorDrop(p);
					} else {
						bounce(p);
					}
				}
				case SLOT_3 -> {
					if (!shift) {
						boost(p);
					}
				}
				case SLOT_5 -> {
					if (shift) {
						spin(p);
					} else if (use(p, UNCURL)) {
						setBall(p, false);
					}
				}
				default -> {
				}
			}
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					shellSlam(p);
				} else {
					shoulderBash(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					groundPound(p);
				} else {
					launch(p);
				}
			}
			case SLOT_3 -> {
				if (!shift) {
					tumble(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					shellGuard(p);
				} else if (use(p, CURL)) {
					setBall(p, true);
				}
			}
			default -> {
			}
		}
	}

	/** The keys he holds: C's Spin Charge lets go, Shift+V's Shell Guard drops. */
	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		if (slot == AbilitySlot.SLOT_6 && flag(p, F_CHARGING)) {
			spinChargeRelease(p);
		} else if (slot == AbilitySlot.SLOT_5 && flag(p, F_GUARD)) {
			guardEnd(p);
		}
	}

	/** Left-click: his heavy fists standing (the body's attack stat); none from inside the ball. */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (flag(p, F_BALL)) {
			return true;
		}
		anim(p, ANIM_PUNCH);
		return false;
	}

	// ---------------------------------------------------------------- V: curl up / uncurl

	/**
	 * Into or out of Ball Mode. Uncurling needs room to stand (false, and a line, under a low ceiling). Curling drops the Shell Guard;
	 * uncurling stops every rolling move and lets out anyone inside.
	 */
	public boolean setBall(ServerPlayer p, boolean on) {
		if (flag(p, F_BALL) == on) {
			return true;
		}
		ServerLevel l = level(p);
		if (!on) {
			float scale = p.getScale();
			AABB stand = EntityDimensions.scalable(0.6f, BODY_HEIGHT).scale(scale).makeBoundingBox(p.position());
			if (!l.noCollision(p, stand.deflate(1.0e-3))) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cannonbolt.no_room").withStyle(ChatFormatting.GRAY), true);
				cooldown(p, UNCURL, 0);
				return false;
			}
			for (int f : new int[] { F_BOOST, F_SPIN, F_RAMPAGE, F_CHARGING, F_SPIKES }) {
				setFlag(p, f, false);
			}
			for (String t : new String[] { "boost_end", "spin_end", "charge_start" }) {
				setTimer(p, t, 0);
			}
			RUSHES.remove(p.getUUID());
			SMASHES.remove(p.getUUID());
			REBOUNDS.remove(p.getUUID());
			RELEASED.remove(p.getUUID());
			Dash d = DASHES.get(p.getUUID());
			if (d != null && d.kind() == DashKind.CHARGE) {
				DASHES.remove(p.getUUID());
			}
			MOMENTUM.remove(p.getUUID());
			BOUNCES.remove(p.getUUID());
			clearRoll(p);
			anim(p, ANIM_UNCURL);
			OmnitrixSounds.play(l, p.position(), SND_UNCURL, 1.0f, 1.0f);
		} else {
			setFlag(p, F_GUARD, false);
			clearRoll(p); // old saves: the speed attribute's bonus from before CannonboltRoll
			anim(p, ANIM_CURL);
			OmnitrixSounds.play(l, p.position(), SND_CURL, 1.0f, 1.0f);
		}
		setFlag(p, F_BALL, on);
		p.refreshDimensions();
		l.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.6, p.getZ(), 10, 0.4, 0.4, 0.4, 0.1);
		if (!on) {
			CannonboltStow.releaseAll(p); // only now he is standing: the room to set them down is real
		}
		return true;
	}

	/** Something of his own is moving him right now: another move has to wait for it. */
	private boolean busy(ServerPlayer p) {
		UUID id = p.getUUID();
		return DASHES.containsKey(id) || RUSHES.containsKey(id) || REBOUNDS.containsKey(id) || flag(p, F_CHARGING) || SMASHES.containsKey(id);
	}

	// ---------------------------------------------------------------- standing moves

	/** R: a lunge behind his shoulder plate -- 14 to everything just in front, knocked back. */
	public boolean shoulderBash(ServerPlayer p) {
		if (!use(p, BASH)) {
			return false;
		}
		anim(p, ANIM_BASH);
		ServerLevel l = level(p);
		Vec3 dir = flat(p);
		p.setDeltaMovement(dir.x * 0.75, 0.1, dir.z * 0.75);
		p.hurtMarked = true;
		Vec3 at = p.position().add(0, p.getBbHeight() * 0.5, 0).add(dir.scale(1.6));
		for (LivingEntity e : around(p, at, 1.9)) {
			hurt(p, e, BASH_DAMAGE, false);
			shove(e, p.position(), 1.3, 0.3);
		}
		OmnitrixSounds.play(l, at, SND_IMPACT, 0.8f, 1.2f);
		return true;
	}

	/** Shift+R: he topples forward onto his shell -- 20 in a 2.5-block patch ahead, the ones under it slowed for 2 s. */
	public boolean shellSlam(ServerPlayer p) {
		if (!use(p, SLAM)) {
			return false;
		}
		anim(p, ANIM_SLAM);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(flat(p).scale(1.8));
		for (LivingEntity e : around(p, c.add(0, 0.5, 0), 2.5)) {
			hurt(p, e, SLAM_DAMAGE, false);
			shove(e, c, 0.5, 0.15);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.6f).life(12).ground().spawn();
		dust(l, c, 20);
		QuirkCamera.shakeAround(l, c, 10, 0.3f, 8);
		OmnitrixSounds.play(l, c, SND_QUAKE, 0.9f, 1.1f);
		return true;
	}

	/** Shift+C: jump, curl in the air and drop like a wrecking ball -- 20 in a 3.5-block ring where he lands. */
	public boolean curlSmash(ServerPlayer p) {
		if (busy(p) || AIRS.containsKey(p.getUUID()) || !use(p, CURL_SMASH)) {
			return false;
		}
		anim(p, ANIM_CURL);
		Vec3 dir = flat(p);
		p.setDeltaMovement(dir.x * 0.45, 0.95, dir.z * 0.45);
		p.hurtMarked = true;
		AIRS.put(p.getUUID(), new Air(AirKind.CURL_SMASH, p.level().getGameTime()));
		setFlag(p, F_AIR, true);
		OmnitrixSounds.play(level(p), p.position(), SND_CURL, 0.8f, 1.2f);
		return true;
	}

	/**
	 * Hold Shift+V (user: "Make holding shift+V a block ability when not in ball form"): his shoulder plates turned to the front for
	 * as long as he holds it -- blows from in front lose 75%, projectiles bounce back. The 4 s cooldown starts when he lets go.
	 */
	public boolean shellGuard(ServerPlayer p) {
		if (flag(p, F_GUARD) || !use(p, GUARD)) {
			return false;
		}
		anim(p, ANIM_GUARD);
		setFlag(p, F_GUARD, true);
		OmnitrixSounds.play(level(p), p.position(), SND_CURL, 0.6f, 1.4f);
		return true;
	}

	private void guardEnd(ServerPlayer p) {
		setFlag(p, F_GUARD, false);
		cooldown(p, GUARD, -1);
		anim(p, ANIM_GUARD_END);
		OmnitrixSounds.play(level(p), p.position(), SND_UNCURL, 0.5f, 1.3f);
	}

	/** X: a quick curl-and-roll dodge, 6 blocks the way he faces; 8 to whatever he bowls through, half damage while curled. */
	public boolean tumble(ServerPlayer p) {
		if (!use(p, TUMBLE)) {
			return false;
		}
		anim(p, ANIM_CURL);
		startDash(p, DashKind.TUMBLE, flat(p), 0.65, TUMBLE_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_CURL, 0.7f, 1.3f);
		return true;
	}

	/** Shift+G: both fists into the ground -- 16 in a 4-block ring, everything thrown up. */
	public boolean groundPound(ServerPlayer p) {
		if (!use(p, POUND)) {
			return false;
		}
		anim(p, ANIM_POUND);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, 4.0)) {
			hurt(p, e, POUND_DAMAGE, false);
			shove(e, c, 0.5, 0.7);
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(5f).thickness(0.8f).life(14).ground().spawn();
		dust(l, c, 30);
		QuirkCamera.shakeAround(l, c, 14, 0.45f, 10);
		OmnitrixSounds.play(l, c, SND_QUAKE, 1.1f, 0.9f);
		return true;
	}

	/** G: curl and fire himself where he looks like a cannonball -- 20 to everything in the way, weak blocks burst; he lands rolled up. */
	public boolean launch(ServerPlayer p) {
		if (!use(p, LAUNCH)) {
			return false;
		}
		anim(p, ANIM_CURL);
		startDash(p, DashKind.LAUNCH, p.getLookAngle().normalize(), 1.6, LAUNCH_TICKS);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_QUAKE, 1.0f, 1.3f);
		QuirkFx.ring(l, p.position().add(0, 0.8, 0)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, COLOUR).size(2.2f).thickness(0.5f)
				.life(9).spawn();
		return true;
	}

	// ---------------------------------------------------------------- Ball Mode moves

	/** Ball R: a straight-line burst where he faces -- 20 to everything in the way, weak blocks burst, stops dead against a wall. */
	public boolean charge(ServerPlayer p) {
		if (busy(p) || !use(p, CHARGE)) {
			return false;
		}
		startDash(p, DashKind.CHARGE, flat(p), 1.25, CHARGE_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_ROLL, 1.2f, 1.2f);
		return true;
	}

	/**
	 * Ball Shift+R (user: "actually autotrack the 20 nearest enemies on a similar Y level of the player within 50 blocks and then roll and
	 * attack them dealing 20 damage please, player doesn't return to original position afterwards, player doesn't teleport between
	 * enemies player must actually roll between enemies"): at top speed from one enemy to the next nearest, 20 each. An enemy he cannot
	 * get any closer to is given up. He stops where the last one fell. The cooldown runs from the end.
	 */
	public boolean rampage(ServerPlayer p) {
		if (busy(p)) {
			return false;
		}
		List<LivingEntity> marks = rampageTargets(p);
		if (marks.isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cannonbolt.no_targets").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, RAMPAGE)) {
			return false;
		}
		List<Integer> order = new ArrayList<>();
		Vec3 from = p.position();
		List<LivingEntity> pool = new ArrayList<>(marks);
		while (!pool.isEmpty()) {
			Vec3 at = from;
			LivingEntity next = pool.stream().min(Comparator.comparingDouble(e -> e.position().distanceToSqr(at))).get();
			pool.remove(next);
			order.add(next.getId());
			from = next.position();
		}
		RUSHES.put(p.getUUID(), new Rush(order, p.level().getGameTime() + RAMPAGE_TICKS));
		setFlag(p, F_RAMPAGE, true);
		setFlag(p, F_DRIVEN, true);
		OmnitrixSounds.play(level(p), p.position(), SND_QUAKE, 0.8f, 1.5f);
		return true;
	}

	/** The nearest {@value #RAMPAGE_TARGETS} enemies within {@value #RAMPAGE_RANGE} blocks and about his level, nearest first. */
	public static List<LivingEntity> rampageTargets(ServerPlayer p) {
		List<LivingEntity> out = new ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RAMPAGE_RANGE, RAMPAGE_Y, RAMPAGE_RANGE),
				e -> HeroTargets.isHostile(p, e) && e.distanceToSqr(p) <= RAMPAGE_RANGE * RAMPAGE_RANGE && Math.abs(e.getY() - p.getY()) <= RAMPAGE_Y));
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		return out.size() > RAMPAGE_TARGETS ? new ArrayList<>(out.subList(0, RAMPAGE_TARGETS)) : out;
	}

	/** Ball G: a huge bounce (about 8 blocks up); the landing hits 18 in a 4-block ring. */
	public boolean bounce(ServerPlayer p) {
		if (busy(p) || !use(p, BOUNCE)) {
			return false;
		}
		Vec3 v = motion(p);
		p.setDeltaMovement(v.x, 1.25, v.z);
		p.hurtMarked = true;
		BOUNCES.remove(p.getUUID());
		AIRS.put(p.getUUID(), new Air(AirKind.BOUNCE, p.level().getGameTime()));
		setFlag(p, F_AIR, true);
		OmnitrixSounds.play(level(p), p.position(), SND_RICOCHET, 0.8f, 0.7f);
		return true;
	}

	/** Ball Shift+G: up high, then straight down like a meteor -- 20 in a 5-block ring and a quake. */
	public boolean meteorDrop(ServerPlayer p) {
		if (busy(p) || !use(p, DROP)) {
			return false;
		}
		p.setDeltaMovement(0, 1.7, 0);
		p.hurtMarked = true;
		BOUNCES.remove(p.getUUID());
		AIRS.put(p.getUUID(), new Air(AirKind.DROP, p.level().getGameTime()));
		setFlag(p, F_AIR, true);
		OmnitrixSounds.play(level(p), p.position(), SND_RICOCHET, 1.0f, 0.6f);
		return true;
	}

	/** Ball X: 5 s of top speed (his Momentum is held full, sprinting or not). */
	public boolean boost(ServerPlayer p) {
		if (!use(p, BOOST)) {
			return false;
		}
		setFlag(p, F_BOOST, true);
		setTimer(p, "boost_end", p.level().getGameTime() + BOOST_TICKS);
		MOMENTUM.put(p.getUUID(), 1.0);
		OmnitrixSounds.play(level(p), p.position(), SND_ROLL, 1.3f, 1.4f);
		return true;
	}

	/** Ball Shift+V: he spins on the spot for 2 s -- 8 to everything within 3 blocks every 0.4 s, projectiles flung away. */
	public boolean spin(ServerPlayer p) {
		if (!use(p, SPIN)) {
			return false;
		}
		setFlag(p, F_SPIN, true);
		setTimer(p, "spin_end", p.level().getGameTime() + SPIN_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_RICOCHET, 0.8f, 1.5f);
		return true;
	}

	/**
	 * Hold C, either form (user: "player holds c and goes into ball form and begins spinning on the ground, theyre charging up their
	 * momentum, the player cant move with wasd while in this mode, upon releasing C while holding W they charge forward at max
	 * momentum"): curled and spinning on the spot, winding up to full Momentum in 1.5 s. The roll itself (and whether W is down) is on
	 * his client: {@link CannonboltRoll#travel}.
	 */
	public boolean spinChargeStart(ServerPlayer p) {
		if (busy(p) || AIRS.containsKey(p.getUUID()) || !ready(p, SPIN_CHARGE)) {
			if (!busy(p)) {
				use(p, SPIN_CHARGE); // the "not ready" line
			}
			return false;
		}
		setBall(p, true);
		setFlag(p, F_CHARGING, true);
		setFlag(p, F_DRIVEN, false);
		setTimer(p, "charge_start", Math.max(1L, p.level().getGameTime()));
		OmnitrixSounds.play(level(p), p.position(), SND_ROLL, 1.0f, 0.7f);
		return true;
	}

	private void spinChargeRelease(ServerPlayer p) {
		setFlag(p, F_CHARGING, false);
		setTimer(p, "charge_start", 0);
		cooldown(p, SPIN_CHARGE, -1);
		RELEASED.put(p.getUUID(), p.level().getGameTime());
	}

	private void tickSpinCharge(ServerPlayer p, ServerLevel l, long now) {
		long held = now - timer(p, "charge_start");
		if (held >= SPIN_CHARGE_MAX) {
			spinChargeRelease(p);
			return;
		}
		float wound = Math.min(1f, held / (float) SPIN_CHARGE_FILL);
		Vec3 back = flat(p).scale(-1.0);
		if (now % 2 == 0) {
			BlockState under = l.getBlockState(p.blockPosition().below());
			if (!under.isAir()) {
				Vec3 c = p.position().add(back.scale(0.9));
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.1, c.z, 3 + (int) (wound * 7), 0.25, 0.05, 0.25, 0.2 + wound * 0.3);
			}
		}
		if (held % 7 == 0) {
			OmnitrixSounds.play(l, p.position(), SND_ROLL, 0.6f + wound * 0.6f, 0.7f + wound * 1.0f);
		}
		if (held == SPIN_CHARGE_FILL) {
			QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3.2f).thickness(0.5f).life(10).ground().spawn();
			OmnitrixSounds.play(l, p.position(), SND_DEFLECT, 0.7f, 1.6f);
			QuirkCamera.shake(p, 0.15f, 4);
		}
		p.resetFallDistance();
	}

	/**
	 * Z, either form (the show: he slams into a robot, bounces off it and comes straight back): fires himself at what he is aiming
	 * at -- the creature under the crosshair, or the nearest enemy near it -- and rebounds off it for three hits, 12, 12 and 17. Never
	 * a teleport: he crosses the gap each time. With nothing to aim at he shoots off along his look and rebounds off the first thing
	 * he meets. He comes down rolled up.
	 */
	public boolean reboundStrike(ServerPlayer p) {
		if (busy(p) || !use(p, REBOUND)) {
			return false;
		}
		setBall(p, true);
		LivingEntity mark = reboundMark(p);
		Vec3 aim = mark != null ? mark.position().add(0, mark.getBbHeight() * 0.5, 0) : p.getEyePosition().add(p.getLookAngle().scale(REBOUND_RANGE * 0.7));
		REBOUNDS.put(p.getUUID(), new Rebound(mark == null ? -1 : mark.getId(), aim, p.level().getGameTime()));
		BOUNCES.remove(p.getUUID());
		AIRS.remove(p.getUUID());
		setFlag(p, F_AIR, true);
		setFlag(p, F_DRIVEN, true);
		ServerLevel l = level(p);
		QuirkFx.ring(l, center(p)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, COLOUR).size(2.4f).thickness(0.5f).life(9).spawn();
		OmnitrixSounds.play(l, p.position(), SND_QUAKE, 0.8f, 1.5f);
		return true;
	}

	@Nullable
	private static LivingEntity reboundMark(ServerPlayer p) {
		LivingEntity under = lookTarget(p, REBOUND_RANGE);
		if (under != null) {
			return under;
		}
		Vec3 look = p.getLookAngle();
		LivingEntity best = null;
		double bestAim = 0.94; // about 20 degrees off the crosshair
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(REBOUND_RANGE), e -> HeroTargets.isHostile(p, e))) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
			double aim = to.lengthSqr() < 1.0e-3 ? 0 : to.normalize().dot(look);
			if (aim > bestAim && to.length() <= REBOUND_RANGE && p.hasLineOfSight(e)) {
				bestAim = aim;
				best = e;
			}
		}
		return best;
	}

	private void tickRebound(ServerPlayer p, ServerLevel l, long now) {
		Rebound r = REBOUNDS.get(p.getUUID());
		if (r == null) {
			return;
		}
		p.resetFallDistance();
		if (now - r.start > 80 || !flag(p, F_BALL)) {
			endRebound(p);
			return;
		}
		if (now < r.hopUntil) {
			return; // arcing back off the last hit: gravity has him
		}
		Entity mark = r.target < 0 ? null : l.getEntity(r.target);
		if (r.target >= 0 && (!(mark instanceof LivingEntity e) || !e.isAlive())) {
			endRebound(p); // it's dead: down he comes
			return;
		}
		if (mark != null) {
			r.aim = mark.position().add(0, mark.getBbHeight() * 0.5, 0);
		}
		Vec3 c = center(p);
		Vec3 to = r.aim.subtract(c);
		double reach = p.getBbWidth() * 0.5 + (mark == null ? 0.6 : mark.getBbWidth() * 0.5 + 0.8);
		Vec3 dir = to.lengthSqr() < 1.0e-4 ? flat(p) : to.normalize();
		BlockHitResult ahead = l.clip(new ClipContext(c, c.add(dir.scale(REBOUND_SPEED + p.getBbWidth() * 0.5 + 0.1)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		// the ground under a dive at his mark is not in the way: only a wall is (or, with no mark, whatever he meets first)
		boolean wall = ahead.getType() == HitResult.Type.BLOCK && (mark == null || ahead.getDirection().getAxis() != Direction.Axis.Y);
		boolean there = to.length() <= reach;
		if (there || wall) {
			if (mark instanceof LivingEntity e) {
				if (!there) {
					endRebound(p); // a wall between them: he thuds into it and drops
					OmnitrixSounds.play(l, c, SND_RICOCHET, 0.9f, 0.8f);
					return;
				}
				boolean last = ++r.hits >= REBOUND_HITS;
				hurt(p, e, last ? REBOUND_LAST_DAMAGE : REBOUND_DAMAGE, false);
				shove(e, c, last ? 1.3 : 0.25, last ? 0.5 : 0.15);
				RAMMED.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(e.getId(), now); // rolling clear of it is not a ram too
			} else {
				r.hits = REBOUND_HITS;
			}
			Vec3 at = mark == null ? c.add(dir.scale(reach)) : r.aim;
			l.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.3);
			QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(dir).colours(0xFFFFFF, COLOUR).size(2.2f).thickness(0.5f).life(8).spawn();
			QuirkCamera.shake(p, 0.25f, 5);
			OmnitrixSounds.play(l, at, SND_IMPACT, 1.0f, 0.9f + r.hits * 0.12f);
			OmnitrixSounds.play(l, at, SND_RICOCHET, 0.9f, 0.9f + r.hits * 0.12f);
			Vec3 away = new Vec3(-dir.x, 0, -dir.z);
			away = away.lengthSqr() < 1.0e-3 ? flat(p).scale(-1.0) : away.normalize();
			if (r.hits >= REBOUND_HITS) {
				p.setDeltaMovement(away.x * 0.45, 0.55, away.z * 0.45); // the last one throws him clear
				p.hurtMarked = true;
				endRebound(p);
				return;
			}
			p.setDeltaMovement(away.x * 0.55, 0.75, away.z * 0.55);
			p.hurtMarked = true;
			r.hopUntil = now + 9;
			r.strikeStart = now + 9;
			return;
		}
		if (now - r.strikeStart > (long) (REBOUND_RANGE / REBOUND_SPEED) + 6) {
			endRebound(p); // never reached it (it kept running): don't chase it for ever
			return;
		}
		p.setDeltaMovement(dir.scale(REBOUND_SPEED));
		p.hurtMarked = true;
	}

	private void endRebound(ServerPlayer p) {
		REBOUNDS.remove(p.getUUID());
		setFlag(p, F_AIR, false);
		p.resetFallDistance();
	}

	/**
	 * Shift+Z, either form (the ultimate). v0.17.2 (user: "change his ultimate to an ability where he dash towards the target and deals
	 * 30 damage to them and then boost up into the air and slams down on them dealing another 30 damage"): he locks on to what he aims
	 * at, spikes out, and rolls straight at it; the hit throws him up, and he comes down on wherever it has gone.
	 */
	public boolean unstoppable(ServerPlayer p) {
		if (flag(p, F_SPIKES) || busy(p)) {
			return false;
		}
		LivingEntity mark = lookTarget(p, ULT_RANGE);
		if (mark == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cannonbolt.no_ult_target").withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		if (!use(p, UNSTOPPABLE)) {
			return false;
		}
		setBall(p, true);
		BOUNCES.remove(p.getUUID());
		AIRS.remove(p.getUUID());
		setFlag(p, F_AIR, false);
		setFlag(p, F_SPIKES, true);
		setFlag(p, F_DRIVEN, true);
		SMASHES.put(p.getUUID(), new Smash(mark.getId(), p.level().getGameTime()));
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.6f).life(10).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_QUAKE, 1.2f, 1.1f);
		return true;
	}

	/** The dash: straight at the mark, over the ground or off it, until it is hit. */
	private void tickUltimate(ServerPlayer p, ServerLevel l, long now) {
		Smash s = SMASHES.get(p.getUUID());
		if (s == null || s.dashed) {
			return;
		}
		LivingEntity mark = l.getEntity(s.target) instanceof LivingEntity e && e.isAlive() ? e : null;
		if (mark == null || now - s.start > ULT_DASH_TICKS) {
			if (mark == null || p.distanceTo(mark) > ULT_RANGE) {
				SMASHES.remove(p.getUUID());
				setFlag(p, F_SPIKES, false);
				return;
			}
		}
		Vec3 to = mark.position().add(0, mark.getBbHeight() * 0.5, 0).subtract(center(p));
		double reach = (p.getBbWidth() + mark.getBbWidth()) * 0.5 + 0.8;
		p.resetFallDistance();
		if (to.length() > reach && now - s.start <= ULT_DASH_TICKS) {
			Vec3 v = to.normalize().scale(Math.min(ULT_DASH_SPEED, to.length()));
			p.setDeltaMovement(v.x, Math.max(v.y, -0.2), v.z);
			p.hurtMarked = true;
			Vec3 c = center(p);
			l.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 5, 0.6, 0.6, 0.6, 0.2);
			return;
		}
		s.dashed = true;
		if (to.length() <= reach) {
			hurt(p, mark, ULT_HIT_DAMAGE, false);
			OmnitrixSounds.play(l, mark.position(), SND_IMPACT, 1.2f, 0.8f);
			QuirkCamera.shakeAround(l, mark.position(), 12, 0.4f, 6);
			Vec3 c = mark.position().add(0, mark.getBbHeight() * 0.5, 0);
			QuirkFx.ring(l, c).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.6f).life(10).spawn();
		}
		crash(p);
	}

	/** The boost: straight up, then down like the end of the world, on the mark. */
	private void crash(ServerPlayer p) {
		p.setDeltaMovement(0, 1.7, 0);
		p.hurtMarked = true;
		BOUNCES.remove(p.getUUID());
		AIRS.put(p.getUUID(), new Air(AirKind.CRASH, p.level().getGameTime()));
		setFlag(p, F_AIR, true);
		OmnitrixSounds.play(level(p), p.position(), SND_RICOCHET, 1.2f, 0.6f);
	}

	/** While he is up, the slam keeps over the mark: he lands on it wherever it runs. */
	private void steerCrash(ServerPlayer p, ServerLevel l) {
		Smash s = SMASHES.get(p.getUUID());
		if (s == null || !(l.getEntity(s.target) instanceof LivingEntity mark) || !mark.isAlive()) {
			return;
		}
		Vec3 to = mark.position().subtract(p.position());
		double max = 0.9;
		double x = Mth.clamp(to.x * 0.35, -max, max), z = Mth.clamp(to.z * 0.35, -max, max);
		p.setDeltaMovement(x, p.getDeltaMovement().y, z);
		p.hurtMarked = true;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		Vec3 pos = p.position();
		Vec3 prev = LAST_POS.put(p.getUUID(), pos);
		Vec3 moved = prev == null ? Vec3.ZERO : pos.subtract(prev);
		double speed = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
		if (speed > 4.0) {
			speed = 0; // a teleport, not a roll
			moved = Vec3.ZERO;
		}
		LAST_MOVE.put(p.getUUID(), moved);
		boolean onGround = p.onGround();
		Boolean wasGround = WAS_GROUND.put(p.getUUID(), onGround);
		endTimed(p, "boost_end", F_BOOST, now);
		endTimed(p, "spin_end", F_SPIN, now);
		boolean ball = flag(p, F_BALL);
		tickDash(p, l, now);
		tickRampage(p, l, now);
		tickRebound(p, l, now);
		tickUltimate(p, l, now);
		tickAir(p, l, now);
		if (flag(p, F_CHARGING)) {
			tickSpinCharge(p, l, now);
		}
		if (ball && Boolean.TRUE.equals(wasGround) && !onGround && moved.y > 0.2 && p.isSprinting()) {
			sprintLaunch(p, l, now, moved);
		}
		tickBounces(p, l, now);
		if (ball) {
			tickRoll(p, l, now, moved, speed);
			if (flag(p, F_SPIN)) {
				tickSpin(p, l, now);
			}
			deflectAround(p, l, 2.1, false);
			if (now % 10 == 0) {
				CannonboltStow.hide(p);
			}
		} else if (flag(p, F_GUARD)) {
			if (!p.isShiftKeyDown()) {
				guardEnd(p); // Shift let go, whatever V is doing
			} else {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 1, false, false, false));
				deflectAround(p, l, 3.0, true);
			}
		}
		UUID id = p.getUUID();
		boolean round = ball || DASHES.containsKey(id) || airRound(p);
		setFlag(p, F_ROUND, round);
		Air air = AIRS.get(id);
		setFlag(p, F_DRIVEN, DASHES.containsKey(id) || RUSHES.containsKey(id) || REBOUNDS.containsKey(id) || SMASHES.containsKey(id)
				|| air != null && air.kind != AirKind.BOUNCE);
		boolean steady = flag(p, F_SPIKES) || RUSHES.containsKey(id) || DASHES.containsKey(id) || REBOUNDS.containsKey(id);
		if (steady) {
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, STEADY, 1.0, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, STEADY);
		}
	}

	private void endTimed(ServerPlayer p, String timer, int flag, long now) {
		long end = timer(p, timer);
		if (end > 0 && now >= end) {
			setTimer(p, timer, 0);
			setFlag(p, flag, false);
		}
	}

	private boolean airRound(ServerPlayer p) {
		Air a = AIRS.get(p.getUUID());
		return a != null && (a.kind != AirKind.CURL_SMASH || a.falling);
	}

	/**
	 * Ball Mode's rolling, as the server sees it: his client moves him ({@link CannonboltRoll}), so Momentum is read back off how fast he
	 * really goes (a reading held briefly, so one short packet doesn't drop it), and that sets the ram: 10 at none, 20 at full.
	 */
	private void tickRoll(ServerPlayer p, ServerLevel l, long now, Vec3 moved, double speed) {
		if (SMASHES.containsKey(p.getUUID())) {
			return; // the ultimate deals its two hits, and only those
		}
		double mom = flag(p, F_BOOST) ? 1.0
				: speed < 0.04 ? 0.0 : Math.max(CannonboltRoll.momentumFromSpeed(speed), MOMENTUM.getOrDefault(p.getUUID(), 0.0) * 0.8);
		MOMENTUM.put(p.getUUID(), mom);
		Long released = RELEASED.get(p.getUUID());
		if (released != null) {
			if (speed > 0.45) {
				RELEASED.remove(p.getUUID());
				Vec3 dir = new Vec3(moved.x, 0, moved.z).normalize();
				Vec3 c = center(p);
				QuirkFx.ring(l, c.subtract(dir.scale(0.8))).flags(QuirkFx.F_VERTICAL).vec(dir).colours(0xFFFFFF, COLOUR).size(2.6f).thickness(0.5f).life(9).spawn();
				dust(l, p.position(), 20);
				l.sendParticles(ParticleTypes.CLOUD, c.x, p.getY() + 0.2, c.z, 10, 0.5, 0.1, 0.5, 0.05);
				OmnitrixSounds.play(l, p.position(), SND_QUAKE, 0.8f, 1.5f);
			} else if (now - released > 6) {
				RELEASED.remove(p.getUUID());
			}
		}
		if (speed > 0.15 && p.onGround()) {
			OmnitrixSounds.play(l, p.position(), SND_ROLL, 0.6f + (float) speed, 0.8f + (float) speed * 0.6f);
			if (now % 3 == 0) {
				BlockState under = l.getBlockState(p.blockPosition().below());
				if (!under.isAir()) {
					l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.3, 0.05, 0.3, 0.1);
				}
			}
		}
		if (speed < RAM_SPEED || DASHES.containsKey(p.getUUID()) || RUSHES.containsKey(p.getUUID()) || REBOUNDS.containsKey(p.getUUID())) {
			return;
		}
		Vec3 dir = new Vec3(moved.x, 0, moved.z).normalize();
		float damage = CannonboltRoll.ramDamage(mom);
		Map<Integer, Long> rammed = RAMMED.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		Vec3 c = center(p).add(dir.scale(p.getBbWidth() * 0.6));
		for (LivingEntity e : around(p, c, p.getBbWidth() * 0.5 + 0.7)) {
			Long at = rammed.get(e.getId());
			if (at != null && now - at < 10) {
				continue;
			}
			rammed.put(e.getId(), now);
			if (hurt(p, e, damage, false)) {
				shove(e, p.position().subtract(dir), 1.0 + speed, 0.35);
				OmnitrixSounds.play(l, e.position(), SND_IMPACT, 0.8f, 1.0f);
			}
		}
		if (rammed.size() > 64) {
			rammed.entrySet().removeIf(en -> now - en.getValue() > 40);
		}
	}

	private void tickSpin(ServerPlayer p, ServerLevel l, long now) {
		long start = timer(p, "spin_end") - SPIN_TICKS;
		if ((now - start) % 8 != 0) {
			return;
		}
		Vec3 c = center(p);
		for (LivingEntity e : around(p, c, 3.0)) {
			hurt(p, e, SPIN_DAMAGE, false);
			shove(e, c, 0.8, 0.25);
		}
		deflectAround(p, l, 3.5, false);
		l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.8, 0.1, 0.8, 0);
		OmnitrixSounds.play(l, c, SND_ROLL, 1.0f, 1.6f);
	}

	// ---------------------------------------------------------------- dashes (the Tumble, the Launch, the Charge)

	private void startDash(ServerPlayer p, DashKind kind, Vec3 dir, double speed, int ticks) {
		DASHES.put(p.getUUID(), new Dash(kind, dir, speed, p.level().getGameTime() + ticks, new HashSet<>()));
		setFlag(p, F_ROUND, true);
	}

	private void tickDash(ServerPlayer p, ServerLevel l, long now) {
		Dash d = DASHES.get(p.getUUID());
		if (d == null) {
			return;
		}
		boolean wall = d.kind() != DashKind.TUMBLE && smashAhead(p, l, d.dir(), d.kind() == DashKind.TUMBLE ? 0 : 2) && blocked(p, l, d.dir(), d.speed());
		if (now >= d.end() || wall || (d.kind() == DashKind.TUMBLE && blocked(p, l, d.dir(), d.speed()))) {
			endDash(p, l, d, wall);
			return;
		}
		double vy = d.kind() == DashKind.LAUNCH ? d.dir().y * d.speed() : Math.min(0, p.getDeltaMovement().y);
		p.setDeltaMovement(d.dir().x * d.speed(), vy, d.dir().z * d.speed());
		p.hurtMarked = true;
		p.resetFallDistance();
		float damage = switch (d.kind()) {
			case TUMBLE -> TUMBLE_DAMAGE;
			case LAUNCH -> LAUNCH_DAMAGE;
			case CHARGE -> CHARGE_DAMAGE;
		};
		Vec3 c = center(p).add(d.dir().scale(0.8));
		for (LivingEntity e : around(p, c, p.getBbWidth() * 0.5 + 0.9)) {
			if (d.hits().add(e.getId()) && hurt(p, e, damage, false)) {
				shove(e, p.position(), d.kind() == DashKind.TUMBLE ? 0.6 : 1.6, 0.4);
				OmnitrixSounds.play(l, e.position(), SND_IMPACT, 0.9f, 1.0f);
			}
		}
		if (now % 2 == 0) {
			l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
		}
	}

	private void endDash(ServerPlayer p, ServerLevel l, Dash d, boolean wall) {
		DASHES.remove(p.getUUID());
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(v.x * 0.2, Math.min(0, v.y), v.z * 0.2);
		p.hurtMarked = true;
		if (wall) {
			Vec3 at = center(p).add(d.dir().scale(0.8));
			QuirkCamera.shake(p, 0.4f, 8);
			OmnitrixSounds.play(l, at, SND_IMPACT, 1.0f, 0.8f);
			l.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.2);
		}
		if (d.kind() == DashKind.LAUNCH) {
			setBall(p, true); // he lands rolled up
		}
	}

	// ---------------------------------------------------------------- the Rolling Rampage

	/**
	 * One tick of the Rampage. He is driven by velocity alone, at his top speed, so every block between two enemies is rolled: the step,
	 * the leaves and the walls all work on him as they do on any roll. A wall he can't step over is steered round; an enemy he stops
	 * getting closer to is given up.
	 */
	private void tickRampage(ServerPlayer p, ServerLevel l, long now) {
		Rush r = RUSHES.get(p.getUUID());
		if (r == null) {
			return;
		}
		p.resetFallDistance();
		if (now >= r.end || !flag(p, F_BALL)) {
			endRampage(p);
			return;
		}
		Vec3 c = center(p);
		// everything he bowls through on the way counts, marked or not
		for (LivingEntity e : around(p, c, p.getBbWidth() * 0.5 + 1.1)) {
			if (r.hit.add(e.getId()) && (r.left.contains(e.getId()) || HeroTargets.isHostile(p, e))) {
				strike(p, l, e, c);
			}
		}
		LivingEntity mark = null;
		while (!r.left.isEmpty()) {
			Entity e = l.getEntity(r.left.get(0));
			if (e instanceof LivingEntity le && le.isAlive() && !r.hit.contains(le.getId()) && le.distanceToSqr(p) <= RAMPAGE_RANGE * RAMPAGE_RANGE * 2.25) {
				mark = le;
				break;
			}
			r.left.remove(0);
			r.best = Double.MAX_VALUE;
			r.stuck = 0;
		}
		if (mark == null) {
			endRampage(p);
			return;
		}
		Vec3 to = mark.position().subtract(p.position());
		double dist = Math.hypot(to.x, to.z);
		if (dist < r.best - 0.3) {
			r.best = dist;
			r.stuck = 0;
		} else if (++r.stuck > RAMPAGE_STUCK_TICKS) {
			r.left.remove(0);
			r.best = Double.MAX_VALUE;
			r.stuck = 0;
			return;
		}
		Vec3 dir = dist < 1.0e-3 ? flat(p) : new Vec3(to.x / dist, 0, to.z / dist);
		dir = steer(p, l, dir);
		double speed = CannonboltRoll.SPEED_MAX;
		Vec3 m = LAST_MOVE.getOrDefault(p.getUUID(), Vec3.ZERO);
		p.setDeltaMovement(dir.x * speed, p.onGround() ? -0.08 : Math.min(0, m.y) - 0.08, dir.z * speed);
		p.hurtMarked = true;
		MOMENTUM.put(p.getUUID(), 1.0);
	}

	private void strike(ServerPlayer p, ServerLevel l, LivingEntity e, Vec3 c) {
		if (hurt(p, e, RAMPAGE_DAMAGE, false)) {
			shove(e, c, 1.4, 0.45);
			Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
			l.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.3);
			OmnitrixSounds.play(l, at, SND_IMPACT, 0.9f, 1.1f);
			QuirkCamera.shake(p, 0.2f, 4);
		}
	}

	private void endRampage(ServerPlayer p) {
		RUSHES.remove(p.getUUID());
		SMASHES.remove(p.getUUID());
		setFlag(p, F_RAMPAGE, false);
		cooldown(p, RAMPAGE, -1);
		p.resetFallDistance();
	}

	/** The heading closest to {@code dir} with nothing he can't step over in the way: straight on, or swung out either side. */
	private static Vec3 steer(ServerPlayer p, ServerLevel l, Vec3 dir) {
		for (double a : new double[] { 0, 30, -30, 60, -60, 90, -90, 125, -125 }) {
			Vec3 d = dir.yRot((float) Math.toRadians(a));
			if (!walled(p, l, d)) {
				return d;
			}
		}
		return dir;
	}

	/** A wall too high to step over 2 blocks ahead: the ray at the top of his step is blocked too, not just the one at his middle. */
	private static boolean walled(ServerPlayer p, ServerLevel l, Vec3 dir) {
		double reach = p.getBbWidth() * 0.5 + 2.0;
		Vec3 low = p.position().add(0, 1.0, 0);
		Vec3 high = p.position().add(0, BALL_STEP + 1.0, 0);
		return l.clip(new ClipContext(low, low.add(dir.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.BLOCK
				&& l.clip(new ClipContext(high, high.add(dir.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.BLOCK;
	}

	// ---------------------------------------------------------------- airborne moves

	private void tickAir(ServerPlayer p, ServerLevel l, long now) {
		Air a = AIRS.get(p.getUUID());
		if (a == null) {
			return;
		}
		long t = now - a.start;
		p.resetFallDistance();
		if (a.kind == AirKind.CRASH) {
			steerCrash(p, l);
		}
		if (!a.falling) {
			int apex = a.kind == AirKind.DROP ? 12 : a.kind == AirKind.CRASH ? 11 : a.kind == AirKind.CURL_SMASH ? 7 : 0;
			if (apex > 0 && t >= apex) {
				a.falling = true;
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(a.kind == AirKind.CRASH ? v.x : v.x * 0.3, a.kind == AirKind.CRASH ? -3.0 : a.kind == AirKind.DROP ? -2.5 : -1.4, a.kind == AirKind.CRASH ? v.z : v.z * 0.3);
				p.hurtMarked = true;
				OmnitrixSounds.play(l, p.position(), SND_ROLL, 1.0f, 0.6f);
			} else if (apex == 0 && t > 4) {
				a.falling = true;
			}
		}
		if (t > 3 && (p.onGround() || p.isInWater()) || t > 200) {
			AIRS.remove(p.getUUID());
			setFlag(p, F_AIR, false);
			if (a.kind == AirKind.CRASH) {
				setFlag(p, F_SPIKES, false);
				SMASHES.remove(p.getUUID());
			}
			if (t > 200 || p.isInWater()) {
				return;
			}
			if (a.kind == AirKind.LAUNCH) {
				landLaunch(p, l, a);
				return;
			}
			float damage = switch (a.kind) {
				case DROP -> DROP_DAMAGE;
				case BOUNCE -> BOUNCE_DAMAGE;
				case CRASH -> CRASH_DAMAGE;
				default -> CURL_SMASH_DAMAGE;
			};
			float radius = a.kind == AirKind.CRASH ? 6f : a.kind == AirKind.DROP ? 5f : a.kind == AirKind.BOUNCE ? 4f : 3.5f;
			Vec3 c = p.position();
			for (LivingEntity e : around(p, c, radius)) {
				hurt(p, e, damage, false);
				shove(e, c, 0.9, 0.5);
				RAMMED.computeIfAbsent(p.getUUID(), x -> new HashMap<>()).put(e.getId(), now); // no ram on top of the slam
			}
			QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(radius + 1f).thickness(0.8f).life(14).ground().spawn();
			dust(l, c, (int) (radius * 8));
			QuirkCamera.shakeAround(l, c, radius * 3, a.kind == AirKind.CRASH ? 0.9f : a.kind == AirKind.DROP ? 0.6f : 0.35f, 10);
			OmnitrixSounds.play(l, c, SND_QUAKE, a.kind == AirKind.DROP || a.kind == AirKind.CRASH ? 1.4f : 1.0f, a.kind == AirKind.CRASH ? 0.7f : 1.0f);
			if (a.kind == AirKind.CRASH) {
				QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(COLOUR, 0xB0A090).size(radius * 1.7f).thickness(1.0f).life(20).ground().spawn();
				l.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 7, 4.5, 0.2, 4.5, 0); // round him, not over his view
			}
			if (a.kind == AirKind.CURL_SMASH && !flag(p, F_BALL)) {
				anim(p, ANIM_UNCURL);
			}
		}
	}

	// ---------------------------------------------------------------- v0.17.2: the Sprint Launch and the fall bounces

	/** His real motion last tick (horizontal), for anything that sets his velocity (the server's own is not his). */
	private static Vec3 motion(ServerPlayer p) {
		Vec3 m = LAST_MOVE.getOrDefault(p.getUUID(), Vec3.ZERO);
		return new Vec3(m.x, 0, m.z);
	}

	/**
	 * v0.17.2 (user: "when he sprint jumps its more like a forward launch kind of thing"): a sprint jump in Ball Mode fires him
	 * forward where he looks -- a long, low arc at least as fast as he was rolling -- in a puff of dust and a crack.
	 */
	private void sprintLaunch(ServerPlayer p, ServerLevel l, long now, Vec3 moved) {
		if (AIRS.containsKey(p.getUUID()) || busy(p) || flag(p, F_SPIN)) {
			return;
		}
		Long last = LAST_LAUNCH.get(p.getUUID());
		if (last != null && now - last < LAUNCH_GAP_TICKS) {
			return;
		}
		LAST_LAUNCH.put(p.getUUID(), now);
		BOUNCES.remove(p.getUUID());
		Vec3 dir = flat(p);
		double speed = Math.max(LAUNCH_SPEED, Math.sqrt(moved.x * moved.x + moved.z * moved.z) * 1.2);
		p.setDeltaMovement(dir.x * speed, LAUNCH_LIFT, dir.z * speed);
		p.hurtMarked = true;
		Air a = new Air(AirKind.LAUNCH, now);
		a.dir = dir;
		AIRS.put(p.getUUID(), a);
		setFlag(p, F_AIR, true);
		MOMENTUM.put(p.getUUID(), 1.0);
		Vec3 c = center(p);
		QuirkFx.ring(l, c.subtract(dir.scale(0.6))).flags(QuirkFx.F_VERTICAL).vec(dir).colours(0xFFFFFF, COLOUR).size(2.4f).thickness(0.5f).life(9).spawn();
		dust(l, p.position(), 18);
		l.sendParticles(ParticleTypes.CLOUD, c.x, p.getY() + 0.2, c.z, 10, 0.5, 0.1, 0.5, 0.05);
		OmnitrixSounds.play(l, p.position(), SND_QUAKE, 0.8f, 1.4f);
	}

	/** The launch comes down: a thud and a small ring (12 within 2.5), and he rolls straight on at speed. */
	private void landLaunch(ServerPlayer p, ServerLevel l, Air a) {
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, 2.5)) {
			hurt(p, e, LAUNCH_LAND_DAMAGE, false);
			shove(e, c, 1.0, 0.4);
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3.2f).thickness(0.6f).life(12).ground().spawn();
		dust(l, c, 24);
		QuirkCamera.shake(p, 0.3f, 6);
		OmnitrixSounds.play(l, c, SND_IMPACT, 1.0f, 0.9f);
		p.setDeltaMovement(a.dir.x * CannonboltRoll.SPEED_MAX, 0, a.dir.z * CannonboltRoll.SPEED_MAX);
		p.hurtMarked = true;
		MOMENTUM.put(p.getUUID(), 1.0);
	}

	/** One bounce of a high fall's run: up by the next lift, still rolling the way he was. */
	private void bounceOnce(ServerPlayer p, ServerLevel l, Bounces b) {
		Vec3 v = motion(p);
		p.setDeltaMovement(v.x, b.lift, v.z);
		p.hurtMarked = true;
		b.airborne = false;
		b.left--;
		b.lift *= BOUNCE_KEEP;
		l.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.4, 0.05, 0.4, 0.2);
		dust(l, p.position(), 10);
		OmnitrixSounds.play(l, p.position(), SND_RICOCHET, 0.5f + (float) b.lift, 0.7f + (FALL_BOUNCES - b.left) * 0.12f);
	}

	/** The rest of a fall's bounces: each time he lands again, a smaller one, until they run out (never more than 6 s). */
	private void tickBounces(ServerPlayer p, ServerLevel l, long now) {
		Bounces b = BOUNCES.get(p.getUUID());
		if (b == null) {
			return;
		}
		if (!flag(p, F_BALL) || p.isInWater() || now - b.start > 120 || AIRS.containsKey(p.getUUID())) {
			BOUNCES.remove(p.getUUID());
			return;
		}
		p.resetFallDistance();
		if (!p.onGround()) {
			b.airborne = true;
			return;
		}
		if (!b.airborne) {
			return;
		}
		if (b.left <= 0 || b.lift < BOUNCE_MIN) {
			BOUNCES.remove(p.getUUID()); // settled: he rolls on
			return;
		}
		bounceOnce(p, l, b);
	}

	/** Gametests: the fall bounces still to come (0 = none running). */
	public static int bouncesLeft(ServerPlayer p) {
		Bounces b = BOUNCES.get(p.getUUID());
		return b == null ? 0 : b.left;
	}

	// ---------------------------------------------------------------- helpers

	/** Projectiles flying at him within {@code radius} glance off the shell ({@code frontOnly}: the Shell Guard's front). */
	private void deflectAround(ServerPlayer p, ServerLevel l, double radius, boolean frontOnly) {
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(radius), pr -> pr.getOwner() != p)) {
			Vec3 to = center(p).subtract(pr.position());
			if (pr.getDeltaMovement().lengthSqr() < 0.01 || pr.getDeltaMovement().dot(to) <= 0 || frontOnly && !inFront(p, pr.position())) {
				continue;
			}
			pr.deflect(frontOnly ? ProjectileDeflection.AIM_DEFLECT : ProjectileDeflection.REVERSE, p, p, true);
			pr.setOwner(p);
			l.sendParticles(ParticleTypes.CRIT, pr.getX(), pr.getY(), pr.getZ(), 8, 0.15, 0.15, 0.15, 0.1);
			OmnitrixSounds.play(l, pr.position(), SND_DEFLECT, 0.9f, 1.1f);
		}
	}

	/** Bursts weak blocks (glass, leaves, light wood...) in the slab ahead. True if anything is still in the way after. */
	private boolean smashAhead(ServerPlayer p, ServerLevel l, Vec3 dir, int depth) {
		if (depth <= 0) {
			return true;
		}
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		if (flat.lengthSqr() < 1.0e-4) {
			return true;
		}
		flat = flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		int high = Math.max(1, Mth.ceil(p.getBbHeight()));
		for (int ahead = 1; ahead <= depth; ahead++) {
			for (int s = -1; s <= 1; s++) {
				for (int up = 0; up < high; up++) {
					BlockPos pos = BlockPos.containing(p.position().add(flat.scale(ahead * 0.8)).add(side.scale(s * 0.6)).add(0, up + 0.2, 0));
					BlockState st = l.getBlockState(pos);
					// leaves are never burst: he rolls straight through them now (SpeedLeaves)
					if (FourArmsKit.weakBlock(l, pos, st) && !st.is(BlockTags.LEAVES) && HeroConfig.get().abilityTerrainDamage && p.mayBuild() && l.mayInteract(p, pos)) {
						l.destroyBlock(pos, true, p);
					}
				}
			}
		}
		return true;
	}

	/** Whether a solid block sits right in front of the ball / body along {@code dir}. */
	private static boolean blocked(ServerPlayer p, ServerLevel l, Vec3 dir, double speed) {
		Vec3 c = center(p);
		Vec3 flat = new Vec3(dir.x, dir.y, dir.z);
		BlockHitResult hit = l.clip(new ClipContext(c, c.add(flat.scale(speed + p.getBbWidth() * 0.5 + 0.1)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis() != Direction.Axis.Y;
	}

	private static Vec3 center(Player p) {
		return p.position().add(0, p.getBbHeight() * 0.5, 0);
	}

	private static Vec3 flat(Player p) {
		return Vec3.directionFromRotation(0, p.getYRot());
	}

	private static boolean inFront(Player p, Vec3 at) {
		Vec3 to = at.subtract(p.position());
		return new Vec3(to.x, 0, to.z).normalize().dot(flat(p)) > 0.2;
	}

	private static void dust(ServerLevel l, Vec3 c, int n) {
		BlockState under = l.getBlockState(BlockPos.containing(c).below());
		if (!under.isAir()) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.1, c.z, n, 1.2, 0.1, 1.2, 0.15);
		}
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		boolean ball = flag(p, F_BALL);
		if (source.is(DamageTypeTags.IS_FALL)) {
			if (flag(p, F_AIR) || DASHES.containsKey(p.getUUID())) {
				return false; // his own landings
			}
			if (ball) {
				// v0.17.2 (user: "if he falls from a great distance the ball form should bounce a bit first"): the shell takes no fall
				// damage -- a fall that would have hurt (8+ blocks) starts a run of bounces, each smaller, then he rolls on
				double lift = Mth.clamp(0.3 + amount * 0.04, 0.45, 1.1);
				Bounces b = new Bounces(lift, FALL_BOUNCES, p.level().getGameTime());
				BOUNCES.put(p.getUUID(), b);
				bounceOnce(p, level(p), b);
				return false;
			}
		}
		// the rolled-up shell (and the Shell Guard's front) turns projectiles away
		if (source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.BYPASSES_ARMOR)
				&& (ball || flag(p, F_GUARD) && source.getSourcePosition() != null && inFront(p, source.getSourcePosition()))) {
			OmnitrixSounds.play(level(p), p.position(), SND_DEFLECT, 0.9f, 1.0f);
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return amount;
		}
		if (flag(p, F_SPIKES)) {
			return amount * 0.2f;
		}
		if (flag(p, F_ROUND)) {
			return amount * 0.5f; // the Armoured Shell, all the way round
		}
		Vec3 from = source.getSourcePosition();
		if (from != null) {
			if (flag(p, F_GUARD) && inFront(p, from)) {
				return amount * 0.25f;
			}
			if (!inFront(p, from) && behind(p, from)) {
				return amount * 0.6f; // the plates on his back
			}
		}
		return amount;
	}

	private static boolean behind(Player p, Vec3 at) {
		Vec3 to = at.subtract(p.position());
		return new Vec3(to.x, 0, to.z).normalize().dot(flat(p)) < -0.3;
	}

	/** Gametests: whether a dash / ricochet / airborne move is running for {@code p}. */
	public static boolean moving(ServerPlayer p) {
		return SMASHES.containsKey(p.getUUID()) || DASHES.containsKey(p.getUUID()) || RUSHES.containsKey(p.getUUID()) || REBOUNDS.containsKey(p.getUUID()) || AIRS.containsKey(p.getUUID());
	}

	/** Gametests: Momentum as the server reads it, 0..1. */
	public static double momentum(ServerPlayer p) {
		return MOMENTUM.getOrDefault(p.getUUID(), 0.0);
	}
}
