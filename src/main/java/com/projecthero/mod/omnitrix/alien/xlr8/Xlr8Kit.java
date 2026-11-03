package com.projecthero.mod.omnitrix.alien.xlr8;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedMoves;
import com.projecthero.mod.network.SpeedStreakPayload;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: XLR8, the Kineceleran -- a sleek blue-and-black velociraptor of a speedster with a visor helmet, a tail and ball
 * feet. Very fast (faster still sprinting), fast hands, light armour.
 *
 * <p>v0.17.1 revamp (user: "Give him a new moveset thats similar to the regular super speed powers moveset ... i want him to be
 * really cool and i want the power set to be on par with the other abilities in the mod"): the plain Speedster's moves, borrowed
 * from {@code SuperSpeedMoves} / {@code SuperSpeedHandlers}, and V = Speed Mode (the visor slams down, speedster pace, wall and
 * water running, step assist). v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power
 * abilities"): his own numbers for the borrowed moves.
 *
 * <p>v0.17.2 revamp (user: "i like his speedster based moveset and i want him to basically be able to do the same things but have
 * it be his own thing, also add xlr8s speed trail to his speed mode please, the blue and black lines"; the approved
 * {@code docs/OMNITRIX_REVAMP_UPGRADE_GREYMATTER_XLR8.md}): every speedster move becomes his -- visor-down tells, the tail and the
 * ball feet, a show moment behind each one -- with his own meter (<b>Kinetic Charge</b>), the <b>blue-and-black streak lines</b>
 * from his feet and tail tip in Speed Mode (and along every zip; {@code Xlr8StreakTrail}), and the old particle spam gone.
 *
 * <pre>
 *   R        Blur Punches    6 x 4 in a   3.5-block cone (2 s)               Shift+R  Kick Barrage   8 kicks of 2.5, then a tail whip (8, r 3)
 *   G        Raptor Strike   zip to the foe you aim at (24): 24, tripped     Shift+G  Tornado Run    3 s of running rings; Shift+G again lets the
 *                            1 s; 12 to everything round it                                           twister loose -- 12 blocks along your aim
 *   X        Blur Dash       a 10-block dash where you aim (10)              Shift+X  Friction Zero  4 s of frictionless low skating: through
 *   hold X   Tail Spin       the tail out flat: everything within 6 (22,                              1-block gaps, bowling over what he hits (10)
 *                            knockback 2)
 *   Z        Sonic Punch     28 + shock 12 r 4; spends Kinetic Charge: at     Shift+Z  Raptor Rush    ULT: blink through every foe within 24 (80
 *                            full charge 35 + shock 18 r 6                                           each), then home; untouchable while it runs
 *   V        Speed Mode      toggle: visor down, 20 b/s, 60 sprinting, wall and water    Shift+V  Afterimage     three after-images draw foes off you (6 s)
 *                            running, the blue/black streak lines                      Decoys
 *   hold V   Overdrive       30 s: 25 b/s, 170 sprinting, all damage +50%, cd 45 s from the start, charge fills x2, the lines longer with a white-hot core
 *   C        Time Burst      everything within 12 slows for 5 s; a blue      Shift+C  Speed Mine     strip-mine a 1x2 tunnel 16 blocks ahead
 *                            visor-glass tint on your screen
 *   Attack   fist +4, a double jab (+1.5)     Use (empty hand)  sweep up every dropped item within 14     Use on a mob  snatch what it holds
 *   Use on a player  Rescue Carry: they ride on your back (no fall damage) until Use again, Sneak, or any attack
 * </pre>
 *
 * Passives: Kinetic Charge (fills while he runs flat out in Speed Mode, 6 s / 3 s in Overdrive, drains standing still), Tail
 * Balance (no knockback while running in Speed Mode), Reflexes (30% of projectiles miss him in Speed Mode). Weaknesses:
 * Friction (ice, slime and honey: he skids and cannot wall-run), Tail Pin (a root, a cobweb or Slowness III+ drops him out of
 * Speed Mode, empties the charge and locks it 3 s), glass armour.
 */
public final class Xlr8Kit extends AlienKit {
	public static final String ID = "xlr8";
	public static final int COLOUR = 0x3A7BD5;
	public static final int GLOW = 0x9FD8FF;
	/** v0.17.2 revamp: the black of his streak lines. */
	public static final int EDGE = 0x0A0E14;

	public static final int ANIM_PUNCHES = 1;
	public static final int ANIM_TORNADO = 2;
	public static final int ANIM_STRIKE = 3;
	public static final int ANIM_SNATCH = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_MODE = 6;
	public static final int ANIM_TAIL_SPIN = 7;
	public static final int ANIM_KICKS = 8;
	public static final int ANIM_BURST = 9;
	public static final int ANIM_MINE = 10;
	public static final int ANIM_JAB = 11;
	public static final int ANIM_SWEEP = 12;
	public static final int ANIM_SONIC = 13;
	public static final int ANIM_RUSH = 14;
	/** v0.17.2 revamp: Friction Zero's drop into the skate (was Phase's id). */
	public static final int ANIM_SKATE = 15;
	public static final int ANIM_DECOYS = 16;
	/** v0.17.2 revamp: Kick Barrage's tail-whip finisher. */
	public static final int ANIM_TAIL_WHIP = 17;
	/** v0.17.2 revamp: Shift+G again -- the twister let loose. */
	public static final int ANIM_LOOSE = 18;
	/** v0.17.2 revamp: Rescue Carry's scoop. */
	public static final int ANIM_CARRY = 19;

	/** v0.17.1 revamp: V's Speed Mode (was Shift+X's Wall Run, which it now includes). */
	public static final int F_SPEED = 1;
	public static final int F_TORNADO = 2;
	public static final int F_DASH = 4;
	/** v0.17.2 revamp: Friction Zero (Shift+X; the bit was Phase's). */
	public static final int F_SKATE = 8;
	/** v0.17.1 (user: "when you hold V as xlr8 you enter overdrive mode like with regular speedsters"): hold V's Overdrive. */
	public static final int F_OVERDRIVE = 16;
	/** v0.17.2 revamp (Friction): Speed Mode on ice, slime or honey -- he skids and cannot wall-run (both sides). */
	public static final int F_SLICK = 32;
	/** v0.17.2 revamp: someone rides on his back (Rescue Carry). */
	public static final int F_CARRY = 64;
	/** v0.17.2 revamp: the twister is loose (Shift+G again). */
	public static final int F_LOOSE = 128;

	public static final String OVERDRIVE = "overdrive";
	/**
	 * Overdrive: {@link #OVERDRIVE_TICKS} of Speed Mode at {@link #OD_WALK_BPS} / {@link #OD_RUN_BPS}, everything he deals
	 * {@link #OVERDRIVE_DAMAGE} harder. v0.17.2 (user: "Overdrive: Lasts 30 seconds now, 45 second cooldown upon activation, 50%
	 * increased damage, 25 blocks per second while walking, 170 blocks per second while running"): was 20 s, x2 blows, x3 speed.
	 */
	public static final int OVERDRIVE_TICKS = 30 * 20, OVERDRIVE_COOLDOWN = 45 * 20;
	public static final double OVERDRIVE_DAMAGE = 0.5, OVERDRIVE_STEP = 1.0;

	/**
	 * v0.17.2 (user: "Speed mode: 20 blocks per second walking, 60 blocks per second running"; Overdrive 25 / 170): the speeds,
	 * blocks a second on ordinary ground. A player's flat-ground pace is {@link #BPS_PER_SPEED} blocks a second per point of
	 * MOVEMENT_SPEED (20 ticks x vanilla's 0.98 input scale / (1 - 0.6 slipperiness x 0.91 drag)), so the modifiers below are
	 * worked out from these four numbers and his body's {@link #BODY_SPEED}.
	 */
	public static final double MODE_WALK_BPS = 20, MODE_RUN_BPS = 60, OD_WALK_BPS = 25, OD_RUN_BPS = 170;
	public static final double BPS_PER_SPEED = 20.0 * 0.98 / (1.0 - 0.6 * 0.91);
	/** His body's walk speed (x a player's 0.1), and vanilla's own sprint boost (+30%, multiplied on). */
	public static final float BODY_SPEED = 1.9f;
	public static final double VANILLA_SPRINT = 1.3;
	/** Overdrive's speed on top of Speed Mode (walking 20 -> 25). */
	public static final double OVERDRIVE_BONUS = OD_WALK_BPS / MODE_WALK_BPS - 1.0;
	/** The extra sprint boost in Speed Mode / Overdrive, on top of vanilla's, so running is exactly 60 / 170. */
	public static final double MODE_SPRINT_BONUS = MODE_RUN_BPS / (MODE_WALK_BPS * VANILLA_SPRINT) - 1.0;
	public static final double OD_SPRINT_BONUS = OD_RUN_BPS / (OD_WALK_BPS * VANILLA_SPRINT) - 1.0;

	// v0.17.2 revamp: renamed moves -- Rapid Assault -> Blur Punches, Blitz -> Raptor Strike, Whirlwind -> Tail Spin, Mach Punch ->
	// Sonic Punch, Speed Sweep -> Raptor Rush, Phase -> Friction Zero; Rescue Carry is new
	public static final String PUNCHES = "blur_punches";
	public static final String SONIC = "sonic_punch";
	public static final String STRIKE = "raptor_strike";
	public static final String TORNADO = "tornado_run";
	public static final String DASH = "blur_dash";
	public static final String RUSH = "raptor_rush";
	public static final String SKATE = "friction_zero";
	public static final String TAIL_SPIN = "tail_spin";
	public static final String KICKS = "kick_barrage";
	public static final String MODE = "speed_mode";
	public static final String DECOYS = "afterimage_decoys";
	public static final String BURST = "time_burst";
	public static final String MINE = "speed_mine";
	public static final String JAB = "double_jab";
	public static final String SWEEP = "item_sweep";
	public static final String SNATCH = "snatch";
	public static final String CARRY = "rescue_carry";

	// v0.17.2 (user: "make the damage of all of ben 10s alien stronger, i want each alien to feel like its own entire ability and i
	// want them to be strong"): the new scale -- quick 8-12, normal 20-28, big 28-35, damage over time 7-10 a tick, ultimates 70-110
	public static final float TORNADO_HIT = 7f; // was 1.5 every half second
	public static final float TORNADO_END = 12f; // was 4
	public static final int TORNADO_TICKS = 60;
	public static final double TORNADO_RADIUS = 6;
	/** v0.17.2 revamp: the loose twister -- {@link #LOOSE_BLOCKS} along the aim over {@link #LOOSE_TICKS}, 7 every 0.5 s within 3. */
	public static final int LOOSE_TICKS = 80;
	public static final double LOOSE_BLOCKS = 12, LOOSE_RADIUS = 3;
	public static final float LOOSE_HIT = 7f; // was 2
	public static final float DASH_DAMAGE = 10f; // was 6
	/** Blur Dash: how long it drives him along his aim, and how fast (blocks a tick, easing to a third). */
	public static final int DASH_TICKS = 6;
	public static final double DASH_SPEED = 2.2;
	/** v0.17.2 revamp: Tail Spin (was Whirlwind's 8 in 6), knockback 2. */
	public static final float TAIL_SPIN_DAMAGE = 22f; // v0.17.2 (user: "make the damage of all of ben 10s alien stronger"): was 8
	public static final double TAIL_SPIN_RADIUS = 6, TAIL_SPIN_KNOCKBACK = 2.0;
	public static final float KICK_HIT = 2.5f; // v0.17.2 (user: "make the damage of all of ben 10s alien stronger"): was 2 (8 kicks: 20)
	/** v0.17.2 revamp: Kick Barrage's finisher -- the tail whips round 360 degrees, r 3, 8 (was 5), knockback 1.2. */
	public static final float TAIL_WHIP = 8f;
	public static final double TAIL_WHIP_RADIUS = 3, TAIL_WHIP_KNOCKBACK = 1.2;
	/**
	 * Blur Punches (read by {@code SuperSpeedHandlers.rapidAssaultCore} when he is the one using it; the Super Speed mutation
	 * keeps its own): v0.17.2, {@link #ASSAULT_PUNCHES} x 4 = 24 (was 6 x 2.5; user: "make the damage of all of ben 10s alien stronger").
	 */
	public static final float ASSAULT_PUNCH = 4f;
	public static final int ASSAULT_PUNCHES = 6;
	/** v0.17.2: Raptor Strike (Blitz), 24 + 12 round it (was 14 + 8). The target is tripped for {@link #TRIP_TICKS}. */
	public static final float BLITZ_HIT = 24f, BLITZ_AOE = 12f;
	public static final int TRIP_TICKS = 20;
	/** Sonic Punch's shockwave at no charge (the borrowed Mach Punch's shockwave reads it): v0.17.2, 12 (was 8). */
	public static final float MACH_SHOCK = 12f;
	/** v0.17.2: Sonic Punch -- 28 + shock 12 r 4 with no charge, 35 + shock 18 r 6 at a full bar (was 20 / 35, shock 8 / 12). */
	public static final float SONIC_HIT = 28f, SONIC_HIT_FULL = 35f, SONIC_SHOCK_FULL = 18f;
	public static final double SONIC_RADIUS = 4, SONIC_RADIUS_FULL = 6;
	/** v0.17.2: Raptor Rush (Speed Sweep), 80 a foe (was 45; user: "make the damage of all of ben 10s alien stronger" -- ultimates 70-110). */
	public static final float SWEEP_HIT = 80f;
	/** v0.17.2 revamp: Raptor Rush reaches 24 blocks (the old 50 emptied the whole area). */
	public static final double RUSH_RANGE = 24.0;
	/** v0.17.2: the ultimate's cooldown (Raptor Rush, Shift+Z), 30 s. */
	public static final int ULT_COOLDOWN = 600;
	public static final int KICK_TICKS = 16;
	public static final double BURST_RADIUS = 12;
	public static final int BURST_TICKS = 100;
	public static final int MINE_LENGTH = 16;
	public static final float JAB_EXTRA = 1.5f;
	/** v0.17.2 (the user set the bare-fist bonus): +4 on every punch (stats().attack, was +2). */
	public static final float FIST_BONUS = 4f;
	/** Sprinting outside Speed Mode: +50% on top of vanilla's sprint. */
	public static final double SPRINT_BONUS = 0.5;
	/** Speed Mode on top of his body's 1.9x: exactly {@link #MODE_WALK_BPS} walking (v0.17.2, was x2 -- about 16). */
	public static final double SPEED_MODE_BONUS = MODE_WALK_BPS / (BPS_PER_SPEED * 0.1 * BODY_SPEED) - 1.0;
	/**
	 * v0.17.1 (user: "make xlr8s step assist better and dont make him lose momentum on going up blocks"): he always steps up
	 * {@link #BASE_STEP} more than a player (1.1 blocks) and Speed Mode adds a full block more (1.6) -- at his speed a rise he
	 * cannot step was a wall that stopped him dead; Overdrive adds another block on top.
	 */
	public static final double BASE_STEP = 0.5;
	public static final double SPEED_MODE_STEP = 1.0;
	/** v0.17.2 revamp: Friction Zero -- {@link #SKATE_TICKS} of skating at {@link #SKATE_SPEED} blocks a tick, 6 to what he hits. */
	public static final int SKATE_TICKS = 80, SKATE_COOLDOWN = 160;
	public static final double SKATE_SPEED = 1.1;
	public static final float SKATE_HIT = 10f; // v0.17.2 (user: "make the damage of all of ben 10s alien stronger"): was 6
	/** The visor stays down this long after a speed move. */
	public static final int VISOR_TICKS = 30;

	/** v0.17.2 revamp: Kinetic Charge, 0..{@link #CHARGE_MAX}: full in 6 s flat out (3 s in Overdrive), drains 10 a second still. */
	public static final float CHARGE_MAX = 100f, CHARGE_FILL = CHARGE_MAX / 120f, CHARGE_DRAIN = 0.5f;
	/** Blocks a tick that count as "flat out" for the charge (a walk in Speed Mode is ~0.6), and as standing still. */
	public static final double CHARGE_FAST = 0.3, CHARGE_STILL = 0.03;
	/** v0.17.2 revamp (Reflexes): the share of projectiles that miss him in Speed Mode. */
	public static final float DODGE_CHANCE = 0.3f;
	/** v0.17.2 revamp (Tail Pin): how long Speed Mode is locked after he is pinned. */
	public static final int PIN_TICKS = 60;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** A dash: a zip and a wind rush. */
	public static final OmnitrixSfx.Cue SND_DASH = OmnitrixSfx.cue("xlr8_dash")
			.layer(SoundEvents.WIND_CHARGE_THROW, 0.35f, 1.4f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.3f, 1.6f)
			.layer(SoundEvents.BREEZE_JUMP, 0.25f, 1.6f);
	/** One blow of a flurry (the flurry calls it every other tick; the gap keeps it to ~7 a second). */
	public static final OmnitrixSfx.Cue SND_HIT = OmnitrixSfx.cue("xlr8_hit")
			.layer(SoundEvents.PLAYER_ATTACK_WEAK, 0.35f, 1.3f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.12f, 1.9f)
			.jitter(0.1f).gap(3);
	/** A tornado: a swirling wind. */
	public static final OmnitrixSfx.Cue SND_TORNADO = OmnitrixSfx.cue("xlr8_tornado")
			.layer(SoundEvents.BREEZE_WHIRL, 0.5f, 0.8f)
			.layer(SoundEvents.BREEZE_INHALE, 0.3f, 0.7f)
			.gap(10);
	/** The sonic burst: a wind-charge boom and a rush. */
	public static final OmnitrixSfx.Cue SND_BURST = OmnitrixSfx.cue("xlr8_burst")
			.layer(SoundEvents.WIND_CHARGE_BURST, 0.55f, 0.8f)
			.layer(SoundEvents.BREEZE_SHOOT, 0.35f, 0.6f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.15f, 1.7f);
	/** The visor snapping down / up. */
	public static final OmnitrixSfx.Cue SND_VISOR = OmnitrixSfx.cue("xlr8_visor")
			.layer(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.3f, 1.8f)
			.layer(SoundEvents.COPPER_BULB_TURN_ON, 0.25f, 1.2f);

	private static final ResourceLocation SPRINT_ID = PowerToggles.id("omnitrix_xlr8_sprint");
	private static final ResourceLocation ATTACK_SPEED_ID = PowerToggles.id("omnitrix_xlr8_attack_speed");
	private static final ResourceLocation MODE_ID = PowerToggles.id("omnitrix_xlr8_speed_mode");
	private static final ResourceLocation MODE_STEP_ID = PowerToggles.id("omnitrix_xlr8_speed_mode_step");
	private static final ResourceLocation BASE_STEP_ID = PowerToggles.id("omnitrix_xlr8_step");
	private static final ResourceLocation OD_SPEED_ID = PowerToggles.id("omnitrix_xlr8_overdrive_speed");
	private static final ResourceLocation OD_DAMAGE_ID = PowerToggles.id("omnitrix_xlr8_overdrive_damage");
	/** v0.17.2 (user): Speed Mode mines 50% faster. */
	private static final ResourceLocation MODE_MINING_ID = PowerToggles.id("omnitrix_xlr8_speed_mode_mining");
	public static final double SPEED_MODE_MINING = 0.5;
	private static final ResourceLocation OD_STEP_ID = PowerToggles.id("omnitrix_xlr8_overdrive_step");
	/** v0.17.2 revamp (Tail Balance): no knockback while he runs in Speed Mode. */
	private static final ResourceLocation BALANCE_ID = PowerToggles.id("omnitrix_xlr8_tail_balance");

	/** player -> the Tornado Run's eye. */
	private static final Map<UUID, Twister> TWISTERS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> the twister he let loose. */
	private static final Map<UUID, Loose> LOOSE = new ConcurrentHashMap<>();
	/** player -> creatures the current Blur Dash / Friction Zero already hit. */
	private static final Map<UUID, Set<Integer>> DASH_HITS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> Kinetic Charge (the server's exact value; the client reads the synced timer). */
	private static final Map<UUID, Float> CHARGE = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> where he stood last tick (his speed, for the charge -- his own client moves him). */
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> tripped creature id -> until (game time). */
	private static final Map<UUID, Map<Integer, Long>> TRIPPED = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: carrier -> the player on his back. */
	private static final Map<UUID, UUID> CARRYING = new ConcurrentHashMap<>();
	/** v0.17.2: player id -> the game tick his kit last ran (the once-a-tick guard in {@link #tick}). */
	private static final Map<Integer, Long> LAST_TICK = new ConcurrentHashMap<>();

	private record Twister(ResourceKey<Level> dim, Vec3 at) {
	}

	private record Loose(ResourceKey<Level> dim, Vec3 start, Vec3 dir, long end) {
		Vec3 at(long now) {
			float k = Mth.clamp(1f - (end - now) / (float) LOOSE_TICKS, 0f, 1f);
			return start.add(dir.scale(LOOSE_BLOCKS * k));
		}
	}

	private Xlr8Kit() {
	}

	public static void register() {
		Aliens.register(new Xlr8Kit());
	}

	public static void clearSessionState() {
		TWISTERS.clear();
		LOOSE.clear();
		DASH_HITS.clear();
		CHARGE.clear();
		LAST_POS.clear();
		TRIPPED.clear();
		CARRYING.clear();
		LAST_TICK.clear();
	}

	/** Whether {@code p} is XLR8 right now (client-safe; the borrowed speed moves streak in his blue). */
	public static boolean is(Player p) {
		return AlienKit.activeKit(p) instanceof Xlr8Kit;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 (user: every alien should have a melee bonus): was +1 fist
		// v0.17.2 (user: "give heatblast iron level armour aswell as xlr8"): 15 armour, an iron set
		// v0.17.2 (the user set the bare-fist bonus): +4 (was +2)
		return AlienStats.builder().health(22f).speed(BODY_SPEED).scale(0.95f).armor(15f, 0f).attack(FIST_BONUS).step(0.65f).jump(0.06f).safeFall(6f).build();
	}

	/**
	 * v0.17.2 (user: "Overdrive: ... 50% increased damage"): what every XLR8 hit is multiplied by right now -- 1.5 in Overdrive, else
	 * 1 (his kit's hits and the borrowed speedster moves read it; the fist gets it through ATTACK_DAMAGE).
	 */
	public static float damageScale(Player p) {
		return is(p) && (Omnitrix.state(p).kitFlags & F_OVERDRIVE) != 0 ? 1f + (float) OVERDRIVE_DAMAGE : 1f;
	}

	/** One of his hits, Overdrive's boost on it. */
	private static boolean hit(ServerPlayer p, LivingEntity e, float amount) {
		return hurt(p, e, amount * damageScale(p), false);
	}

	/**
	 * v0.17.2: the sprint boost for where he is -- plain +50%, Speed Mode / Overdrive exactly what makes running 60 / 170 blocks a
	 * second -- kept in step every tick and the moment the mode changes.
	 */
	private void applySprint(ServerPlayer p) {
		if (!p.isSprinting()) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID);
			return;
		}
		double bonus = flag(p, F_OVERDRIVE) ? OD_SPRINT_BONUS : flag(p, F_SPEED) ? MODE_SPRINT_BONUS : SPRINT_BONUS;
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				// v0.17.2 revamp: R Blur Punches (cd 2 s) / Shift+R Kick Barrage (7 s); G Raptor Strike / Shift+G Tornado Run; X Blur
				// Dash / Shift+X Friction Zero (was Phase) / hold X Tail Spin; Z Sonic Punch / Shift+Z Raptor Rush (ult)
				AlienMove.tap(PUNCHES, AbilitySlot.SLOT_1, 40), AlienMove.shift(KICKS, AbilitySlot.SLOT_1, 140),
				AlienMove.tap(STRIKE, AbilitySlot.SLOT_2, 100), AlienMove.shift(TORNADO, AbilitySlot.SLOT_2, 300),
				AlienMove.tap(DASH, AbilitySlot.SLOT_3, 40), AlienMove.shift(SKATE, AbilitySlot.SLOT_3, SKATE_COOLDOWN),
				AlienMove.hold(TAIL_SPIN, AbilitySlot.SLOT_3, 200),
				AlienMove.tap(SONIC, AbilitySlot.SLOT_4, 200), AlienMove.shift(RUSH, AbilitySlot.SLOT_4, ULT_COOLDOWN),
				AlienMove.tap(MODE, AbilitySlot.SLOT_5, 20), AlienMove.hold(OVERDRIVE, AbilitySlot.SLOT_5, OVERDRIVE_COOLDOWN),
				AlienMove.shift(DECOYS, AbilitySlot.SLOT_5, 400),
				AlienMove.tap(BURST, AbilitySlot.SLOT_6, 600), AlienMove.shift(MINE, AbilitySlot.SLOT_6, 300),
				AlienMove.vanilla(JAB, "Attack", 0), AlienMove.vanilla(SWEEP, "Use (empty hand)", 60),
				AlienMove.vanilla(SNATCH, "Use on a creature (empty hand)", 160), AlienMove.vanilla(CARRY, "Use on a player (empty hand)", 20));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_VISOR, 1.0f, 1.0f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.4f).life(12).ground().spawn();
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, 2.0, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, BASE_STEP_ID, BASE_STEP, AttributeModifier.Operation.ADD_VALUE);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		setDown(p);
		endOverdrive(p, false);
		setFlag(p, F_SPEED | F_TORNADO | F_DASH | F_SKATE | F_SLICK | F_CARRY | F_LOOSE, false);
		setTimer(p, "skate_end", 0);
		setTimer(p, "charge", 0);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, MODE_ID);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, MODE_STEP_ID);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, BASE_STEP_ID);
		PowerToggles.clearModifier(p, Attributes.BLOCK_BREAK_SPEED, MODE_MINING_ID);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, BALANCE_ID);
		SuperSpeedMoves.endSweep(p); // a running rush still brings him home
		TWISTERS.remove(p.getUUID());
		LOOSE.remove(p.getUUID());
		DASH_HITS.remove(p.getUUID());
		CHARGE.remove(p.getUUID());
		LAST_POS.remove(p.getUUID());
		TRIPPED.remove(p.getUUID());
		CARRYING.remove(p.getUUID());
		LAST_TICK.remove(p.getId());
	}

	/**
	 * Speed Mode: pressed into a wall (and not sneaking) he runs straight up it, like a ladder -- v0.17.2 (user: "only run up
	 * walls when he looks up against them like how it works for other speedsters"): only while looking steeply up, the Super
	 * Speed wall run's pitch. v0.17.2 revamp (Friction): never off slick ground.
	 */
	@Override
	public boolean climbs(Player p) {
		return flag(p, F_SPEED) && !flag(p, F_SLICK) && p.horizontalCollision && !p.isShiftKeyDown() && p.getXRot() <= SuperSpeedHandlers.WALL_RUN_PITCH;
	}

	/** Speed Mode: water holds him up (Shift to sink in). */
	@Override
	public boolean standsOnFluid(Player p, FluidState fluid) {
		return flag(p, F_SPEED) && fluid.is(FluidTags.WATER) && !p.isShiftKeyDown() && !p.isUnderWater();
	}

	/** v0.17.2 revamp: Friction Zero -- low on his ball feet, a 0.7-block body that fits through 1-block gaps (both sides). */
	@Override
	@Nullable
	public EntityDimensions body(Player p, Pose pose) {
		return flag(p, F_SKATE) ? EntityDimensions.scalable(0.6f, 0.7f).withEyeHeight(0.55f) : null;
	}

	/**
	 * v0.17.2 revamp: untouchable while Raptor Rush runs (the void, /kill and the like still do); Reflexes -- in Speed Mode
	 * {@link #DODGE_CHANCE} of projectiles miss him.
	 */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (SuperSpeedMoves.sweeping(p)) {
			return false;
		}
		return !dodges(p, source, p.getRandom().nextFloat());
	}

	/** Reflexes: whether {@code source} misses him at this {@code roll} (0..1) -- a projectile, in Speed Mode, under the chance. */
	public boolean dodges(ServerPlayer p, DamageSource source, float roll) {
		if (!flag(p, F_SPEED) || !(source.getDirectEntity() instanceof Projectile) || source.is(DamageTypeTags.IS_EXPLOSION)
				|| roll >= DODGE_CHANCE) {
			return false;
		}
		// the side-step: a 1-frame blur copy where he was
		Vec3 side = new Vec3(-flatLook(p).z, 0, flatLook(p).x).scale(p.getRandom().nextBoolean() ? 0.6 : -0.6);
		SuperSpeedMoves.streak(p, SpeedStreakPayload.KIND_STREAK, p.position(), p.position().add(side), GLOW, 4);
		return true;
	}

	/**
	 * v0.17.1 revamp: the visor is down -- Speed Mode, a dash, the tornado, the skate, a rush, or a speed move in the last
	 * {@link #VISOR_TICKS} (client-safe: the closed-mask skin is drawn from it).
	 */
	public static boolean visorDown(Player p) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & (F_SPEED | F_TORNADO | F_DASH | F_SKATE)) != 0) {
			return true;
		}
		// v0.17.1 (user: "his visor doesnt immediately go up, it only goes up like 2 seconds after the speed mode is turned off"):
		// the Speed Mode toggle itself is not a speed move -- switching it off lifts the visor at once
		boolean speedMove = s.animId != ANIM_JAB && s.animId != ANIM_SWEEP && s.animId != ANIM_SNATCH && s.animId != ANIM_MODE
				&& s.animId != ANIM_CARRY && s.animId > 0 && s.animId < Omnitrix.ANIM_TRANSFORM;
		return speedMove && p.level().getGameTime() - s.animStart < VISOR_TICKS;
	}

	/** v0.17.2 revamp: Kinetic Charge 0..1 (client-safe, from the synced timer). */
	public static float charge(Player p) {
		long v = Omnitrix.state(p).readyAt(ID + ".charge");
		return v <= 0 ? 0f : Mth.clamp((v - 1) / CHARGE_MAX, 0f, 1f);
	}

	/** v0.17.2 revamp: the server's exact Kinetic Charge, 0..{@link #CHARGE_MAX}. */
	public static float kineticCharge(Player p) {
		return CHARGE.getOrDefault(p.getUUID(), 0f);
	}

	/** Sets Kinetic Charge (tests, the pin) and syncs it. */
	public void setCharge(ServerPlayer p, float v) {
		float c = Mth.clamp(v, 0f, CHARGE_MAX);
		CHARGE.put(p.getUUID(), c);
		syncCharge(p, c);
	}

	private void syncCharge(ServerPlayer p, float c) {
		long want = c <= 0.001f ? 0 : Math.round(c) + 1L;
		if (timer(p, "charge") != want) {
			setTimer(p, "charge", want);
		}
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2: once per game tick -- the timed pulses (the kicks, the twister, the skate) must not double up if he is ticked
		// twice in a tick (a gametest's mock player is ticked by the server and by the test's own pump)
		Long last = LAST_TICK.put(p.getId(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		// sprinting: faster still (v0.17.2: the boost follows Speed Mode / Overdrive -- 60 / 170 blocks a second running)
		applySprint(p);
		if (now % 20 == 0) {
			PowerToggles.modifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, 2.0, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(p, Attributes.STEP_HEIGHT, BASE_STEP_ID, BASE_STEP, AttributeModifier.Operation.ADD_VALUE);
		}
		// how fast he really goes (his own client moves him: from where he stood last tick)
		Vec3 was = LAST_POS.put(p.getUUID(), p.position());
		double bpt = was == null || was.distanceToSqr(p.position()) > 64 ? 0 : Math.sqrt(p.position().subtract(was).horizontalDistanceSqr());
		boolean mode = flag(p, F_SPEED);
		// v0.17.2 (user: "when players are in xlr8s speed mode that they break blocks 50% faster"): follows the mode every tick
		var mining = p.getAttribute(Attributes.BLOCK_BREAK_SPEED);
		boolean mines = mining != null && mining.getModifier(MODE_MINING_ID) != null;
		if (mode && !mines) {
			PowerToggles.modifier(p, Attributes.BLOCK_BREAK_SPEED, MODE_MINING_ID, SPEED_MODE_MINING, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else if (!mode && mines) {
			PowerToggles.clearModifier(p, Attributes.BLOCK_BREAK_SPEED, MODE_MINING_ID);
		}
		if (mode && pinned(p)) {
			tailPin(p);
			mode = false;
		}
		if (mode) {
			p.resetFallDistance();
			if (p.isInWater() && !p.isUnderWater()) {
				p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0, 1).add(0, 0.12, 0)); // pops back up onto the surface
			}
			if (now % 40 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, false));
			}
			// v0.17.2 revamp: the CLOUD / ELECTRIC_SPARK particles that used to stream off him are gone -- the blue-and-black streak
			// lines (Xlr8StreakTrail, client) replace them
		}
		// v0.17.2 revamp (Tail Balance): no knockback while he runs in Speed Mode
		boolean balance = mode && bpt > 0.1;
		var kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		boolean balanced = kb != null && kb.getModifier(BALANCE_ID) != null;
		if (balance && !balanced) {
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, BALANCE_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
		} else if (!balance && balanced) {
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, BALANCE_ID);
		}
		// v0.17.2 revamp (Friction): on ice, slime or honey Speed Mode loses its grip
		boolean slick = mode && slickUnder(p);
		if (slick != flag(p, F_SLICK)) {
			setFlag(p, F_SLICK, slick);
		}
		tickCharge(p, mode, bpt, now);
		if (flag(p, F_OVERDRIVE) && now >= timer(p, "od_end")) {
			endOverdrive(p, true);
		}
		tickSkate(p, l, now);
		tickTornado(p, l, now);
		tickLoose(p, l, now);
		tickDash(p, l, now);
		tickKicks(p, l, now);
		tickTripped(p, now);
		tickCarry(p);
		SuperSpeedMoves.sweepTick(p); // the borrowed sweep behind Raptor Rush (nothing else runs it for an alien)
	}

	/** v0.17.2 revamp: Kinetic Charge fills flat out in Speed Mode (twice as fast in Overdrive) and drains standing still. */
	private void tickCharge(ServerPlayer p, boolean mode, double bpt, long now) {
		float c = CHARGE.getOrDefault(p.getUUID(), 0f);
		float was = c;
		if (mode && bpt >= CHARGE_FAST) {
			c += CHARGE_FILL * (flag(p, F_OVERDRIVE) ? 2f : 1f);
		} else if (bpt < CHARGE_STILL) {
			c -= CHARGE_DRAIN;
		}
		c = Mth.clamp(c, 0f, CHARGE_MAX);
		if (c != was) {
			CHARGE.put(p.getUUID(), c);
		}
		if (now % 4 == 0 || (c >= CHARGE_MAX) != (was >= CHARGE_MAX) || c <= 0f && was > 0f) {
			syncCharge(p, c);
		}
	}

	/** Friction: the block he runs on is ice (any), slime or honey. */
	private static boolean slickUnder(ServerPlayer p) {
		BlockState under = p.level().getBlockState(p.getOnPos());
		return under.is(BlockTags.ICE) || under.is(Blocks.SLIME_BLOCK) || under.is(Blocks.HONEY_BLOCK);
	}

	/** Tail Pin: anything that roots him -- a cobweb, a root / stun (Slowness X), Slowness III or worse. */
	public static boolean pinned(Player p) {
		MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		if (slow != null && slow.getAmplifier() >= 2) {
			return true;
		}
		BlockPos at = p.blockPosition();
		return p.level().getBlockState(at).is(Blocks.COBWEB) || p.level().getBlockState(at.above()).is(Blocks.COBWEB);
	}

	/** Tail Pin: out of Speed Mode (and Overdrive), the charge emptied, no Speed Mode for {@link #PIN_TICKS}. */
	public void tailPin(ServerPlayer p) {
		endOverdrive(p, false);
		setSpeedMode(p, false);
		setCharge(p, 0f);
		setTimer(p, "pin_until", p.level().getGameTime() + PIN_TICKS);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.xlr8.pinned").withStyle(ChatFormatting.RED), true);
		OmnitrixSounds.play(level(p), p.position(), SND_VISOR, 0.8f, 0.6f);
	}

	private boolean pinLocked(ServerPlayer p) {
		if (p.level().getGameTime() < timer(p, "pin_until")) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.xlr8.pinned").withStyle(ChatFormatting.RED), true);
			return true;
		}
		return false;
	}

	private void tickSkate(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_SKATE)) {
			return;
		}
		if (now >= timer(p, "skate_end") || !p.isAlive()) {
			endSkate(p);
			return;
		}
		p.resetFallDistance();
		// frictionless: the speed holds, the heading eases round to his aim
		Vec3 v = p.getDeltaMovement();
		Vec3 want = flatLook(p).scale(SKATE_SPEED);
		p.setDeltaMovement(Mth.lerp(0.25, v.x, want.x), v.y, Mth.lerp(0.25, v.z, want.z));
		p.hurtMarked = true;
		Set<Integer> hits = DASH_HITS.computeIfAbsent(p.getUUID(), x -> new HashSet<>());
		for (LivingEntity e : around(p, p.position().add(0, 0.4, 0), 1.4)) {
			if (hits.add(e.getId())) {
				e.invulnerableTime = 0;
				hit(p, e, SKATE_HIT);
				shove(e, p.position(), 0.6, 0.55); // bowled off its feet
				OmnitrixSounds.play(l, e.position(), SND_HIT, 0.7f, 0.8f);
			}
		}
	}

	private void tickTornado(ServerPlayer p, ServerLevel l, long now) {
		Twister t = TWISTERS.get(p.getUUID());
		if (t == null) {
			return;
		}
		long end = timer(p, "tornado_end");
		if (now >= end || t.dim() != l.dimension()) {
			TWISTERS.remove(p.getUUID());
			setTimer(p, "tornado_end", 0);
			setFlag(p, F_TORNADO, false);
			for (LivingEntity e : around(p, t.at(), TORNADO_RADIUS + 1)) {
				e.invulnerableTime = 0;
				hit(p, e, TORNADO_END);
				shove(e, t.at(), 1.1, 0.5);
			}
			QuirkFx.ring(l, t.at().add(0, 0.1, 0)).colours(0xFFFFFF, GLOW).size((float) TORNADO_RADIUS + 2).thickness(0.6f).life(14).ground().spawn();
			return;
		}
		int k = (int) (end - now);
		// XLR8 runs rings round the eye (if there is room); his streak lines draw the rings (client)
		double a = k * 0.9;
		Vec3 ring = t.at().add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
		if (l.noCollision(p, p.getBoundingBox().move(ring.subtract(p.position())))) {
			Vec3 from = p.position();
			p.teleportTo(ring.x, ring.y, ring.z);
			if (k % 2 == 0) {
				SuperSpeedMoves.streak(p, SpeedStreakPayload.KIND_STREAK, from, ring, GLOW, 8);
			}
		}
		p.resetFallDistance();
		lift(p, t.at(), TORNADO_RADIUS, k % 10 == 0 ? TORNADO_HIT : 0f, 0.45);
		// v0.17.2 revamp: the cloud spiral is gone -- the rendered rings spawned at the start are the twister
		if (k % 20 == 0) {
			OmnitrixSounds.play(l, t.at(), SND_TORNADO, 0.8f, 1.1f);
		}
	}

	/** Pulls everything within {@code r} of {@code eye} round and up the twister; {@code hit} > 0 lands that too. */
	private static void lift(ServerPlayer p, Vec3 eye, double r, float hit, double spin) {
		for (LivingEntity e : around(p, eye, r)) {
			if (e == p || e.getVehicle() == p) {
				continue;
			}
			Vec3 to = eye.subtract(e.position());
			Vec3 in = new Vec3(to.x, 0, to.z);
			if (in.lengthSqr() < 1.0e-4) {
				in = new Vec3(1, 0, 0);
			}
			in = in.normalize();
			Vec3 tangent = new Vec3(-in.z, 0, in.x);
			double res = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			double up = e.getY() > eye.y + 5 ? -0.02 : 0.16;
			e.setDeltaMovement(tangent.scale(spin * res).add(in.scale(0.12 * res)).add(0, up * Math.max(0.3, res), 0));
			e.hurtMarked = true;
			e.resetFallDistance();
			if (hit > 0f) {
				e.invulnerableTime = 0;
				hit(p, e, hit);
			}
		}
	}

	/** v0.17.2 revamp: the loose twister travels its 12 blocks, lifting and battering what it passes, then throws them out. */
	private void tickLoose(ServerPlayer p, ServerLevel l, long now) {
		Loose t = LOOSE.get(p.getUUID());
		if (t == null) {
			return;
		}
		Vec3 at = t.at(now);
		if (now >= t.end() || t.dim() != l.dimension()) {
			LOOSE.remove(p.getUUID());
			setFlag(p, F_LOOSE, false);
			if (t.dim() == l.dimension()) {
				for (LivingEntity e : around(p, at, LOOSE_RADIUS + 1)) {
					if (e.getVehicle() != p) {
						shove(e, at, 1.0, 0.45);
					}
				}
				QuirkFx.ring(l, at.add(0, 0.1, 0)).colours(0xFFFFFF, GLOW).size((float) LOOSE_RADIUS + 2).thickness(0.5f).life(12).ground().spawn();
			}
			return;
		}
		int k = (int) (t.end() - now);
		// carried along with it, round and up
		lift(p, at, LOOSE_RADIUS, k % 10 == 0 ? LOOSE_HIT : 0f, 0.35);
		for (LivingEntity e : around(p, at, LOOSE_RADIUS)) {
			if (e.getVehicle() != p) {
				e.setDeltaMovement(e.getDeltaMovement().add(t.dir().scale(LOOSE_BLOCKS / LOOSE_TICKS)));
			}
		}
		if (k % 5 == 0) {
			// the funnel: three short-lived rendered rings, widening upward (no particle spam)
			for (int i = 0; i < 3; i++) {
				QuirkFx.ring(l, at.add(0, 0.3 + i * 1.2, 0)).colours(0xFFFFFF, GLOW).size(1.6f + i * 0.8f).thickness(0.3f).life(7)
						.style(QuirkFx.RING_SOFT).spawn();
			}
		}
		if (k % 20 == 0) {
			OmnitrixSounds.play(l, at, SND_TORNADO, 0.9f, 1.0f);
		}
	}

	private void tickDash(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "dash_end");
		if (end <= 0) {
			return;
		}
		if (now >= end) {
			setTimer(p, "dash_end", 0);
			setFlag(p, F_DASH, false);
			DASH_HITS.remove(p.getUUID());
			return;
		}
		// v0.17.2 (user: "make xlr8s dash directional ... in the direction that the player aims"): the dash follows his aim -- up,
		// down, any way -- easing off over its length (about the old 10 blocks)
		float left = (end - now) / (float) DASH_TICKS;
		p.setDeltaMovement(p.getLookAngle().scale(DASH_SPEED * (0.35f + 0.65f * left)));
		p.hurtMarked = true;
		p.resetFallDistance();
		Set<Integer> hits = DASH_HITS.computeIfAbsent(p.getUUID(), x -> new HashSet<>());
		for (LivingEntity e : around(p, p.position().add(0, 1, 0), 1.8)) {
			if (hits.add(e.getId())) {
				hit(p, e, DASH_DAMAGE);
				shove(e, p.position(), 0.5, 0.2);
			}
		}
		// v0.17.2 revamp: no cloud puffs -- the streak line along the dash is drawn by his clients
	}

	private void tickKicks(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "kicks_end");
		if (end <= 0) {
			return;
		}
		if (now >= end) {
			setTimer(p, "kicks_end", 0);
			return;
		}
		long left = end - now;
		if (left <= 2) {
			if (left == 2) {
				tailWhip(p, l);
			}
			return;
		}
		if (left % 2 == 0) {
			boolean any = false;
			for (LivingEntity e : cone(p, 3.6, 55)) {
				e.invulnerableTime = 0;
				hit(p, e, KICK_HIT);
				shove(e, p.position(), 0.05, 0.02);
				any = true;
			}
			OmnitrixSounds.play(l, p.position(), SND_HIT, any ? 0.6f : 0.3f, 0.9f + (left % 4) * 0.1f);
		}
	}

	/** v0.17.2 revamp: Kick Barrage's finisher -- the tail whips round flat, 360 degrees, r 3. */
	private void tailWhip(ServerPlayer p, ServerLevel l) {
		anim(p, ANIM_TAIL_WHIP);
		Vec3 c = p.position().add(0, 0.8, 0);
		for (LivingEntity e : around(p, c, TAIL_WHIP_RADIUS)) {
			if (e.getVehicle() == p) {
				continue;
			}
			e.invulnerableTime = 0;
			hit(p, e, TAIL_WHIP);
			shove(e, c, TAIL_WHIP_KNOCKBACK, 0.4);
		}
		QuirkFx.ring(l, p.position().add(0, 0.7, 0)).colours(GLOW, COLOUR).size((float) TAIL_WHIP_RADIUS).thickness(0.35f).life(8).style(QuirkFx.RING_SOFT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 0.8f, 0.8f);
	}

	/** Tripped creatures (Raptor Strike): pinned where they fell -- no walking, no jumping -- until their time is up. */
	private static void tickTripped(ServerPlayer p, long now) {
		Map<Integer, Long> m = TRIPPED.get(p.getUUID());
		if (m == null) {
			return;
		}
		m.entrySet().removeIf(en -> {
			Entity e = p.level().getEntity(en.getKey());
			if (!(e instanceof LivingEntity le) || !le.isAlive() || now >= en.getValue()) {
				return true;
			}
			Vec3 d = le.getDeltaMovement();
			le.setDeltaMovement(d.x * 0.2, Math.min(0, d.y), d.z * 0.2);
			if (le instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.setJumping(false);
			}
			return false;
		});
		if (m.isEmpty()) {
			TRIPPED.remove(p.getUUID());
		}
	}

	/** Whether {@code e} is tripped by {@code p}'s Raptor Strike right now. */
	public static boolean tripped(ServerPlayer p, LivingEntity e) {
		Map<Integer, Long> m = TRIPPED.get(p.getUUID());
		Long until = m == null ? null : m.get(e.getId());
		return until != null && p.level().getGameTime() < until;
	}

	// ---------------------------------------------------------------- keys

	@Override
	public boolean holdable(AbilitySlot slot) {
		// v0.17.2: hold X is Tail Spin; hold V: Overdrive (a tap of either fires on release)
		return slot == AbilitySlot.SLOT_3 || slot == AbilitySlot.SLOT_5;
	}

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_SKATE)) {
			// Friction Zero: tapping Shift+X again ends it; every other key waits
			if (slot == AbilitySlot.SLOT_3 && shift) {
				endSkate(p);
			}
			return;
		}
		if (slot != AbilitySlot.SLOT_5 && !(slot == AbilitySlot.SLOT_3 && !shift)) {
			setDown(p); // Rescue Carry: any attack sets them down (V and the dash keep them on)
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					kickBarrage(p);
				} else {
					blurPunches(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					if (TWISTERS.containsKey(p.getUUID())) {
						letLoose(p); // v0.17.2 revamp: Shift+G again while it spins
					} else {
						tornadoRun(p);
					}
				} else {
					raptorStrike(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					frictionZero(p);
				} else {
					blurDash(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					raptorRush(p);
				} else {
					sonicPunch(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					decoys(p);
				} else if (flag(p, F_OVERDRIVE)) {
					endOverdrive(p, true); // a tap during Overdrive ends it early
				} else {
					toggleSpeedMode(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					speedMine(p);
				} else {
					timeBurst(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (flag(p, F_SKATE)) {
			return;
		}
		if (slot == AbilitySlot.SLOT_3) {
			setDown(p);
			tailSpin(p);
		} else if (slot == AbilitySlot.SLOT_5 && !flag(p, F_OVERDRIVE)) {
			overdrive(p);
		}
	}

	/** Living creatures in a cone of {@code deg} degrees, {@code range} blocks in front. */
	public static List<LivingEntity> cone(ServerPlayer p, double range, double deg) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		List<LivingEntity> out = new ArrayList<>();
		double cos = Math.cos(Math.toRadians(deg / 2));
		for (LivingEntity e : around(p, eye.add(look.scale(range / 2)), range)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d <= range + e.getBbWidth() * 0.5 && (d < 1.0 || to.normalize().dot(look) >= cos)) {
				out.add(e);
			}
		}
		return out;
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 h = new Vec3(l.x, 0, l.z);
		return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	/**
	 * R: Blur Punches (v0.17.2 revamp, was Rapid Assault) -- the visor snaps down and a blur of six punches of 2.5 lands on
	 * everything in a 3.5-block cone (his arms render as three overlapping ghost copies).
	 */
	public int blurPunches(ServerPlayer p) {
		if (!use(p, PUNCHES)) {
			return 0;
		}
		anim(p, ANIM_PUNCHES);
		int struck = SuperSpeedHandlers.rapidAssaultCore(p);
		ServerLevel l = level(p);
		QuirkFx.cone(l, p.getEyePosition().add(0, -0.4, 0), p.getLookAngle()).colours(0xFFFFFF, GLOW).size(1.4f).length(3.6f).life(10)
				.style(QuirkFx.CONE_DEBRIS).spawn();
		OmnitrixSounds.play(l, p.position(), SND_HIT, 0.8f, 1.3f);
		return struck;
	}

	/**
	 * Z: Sonic Punch (v0.17.2 revamp: Mach Punch and the Speed Force's Infinite Mass Punch folded into one) -- one huge punch
	 * with a sound-barrier ring. It spends all his Kinetic Charge: none = 20 and a shockwave of 8 within 4, a full bar = 35 and
	 * 12 within 6. Returns the charge spent (0..1), or -1 if it did not fire.
	 */
	public float sonicPunch(ServerPlayer p) {
		if (!use(p, SONIC)) {
			return -1f;
		}
		anim(p, ANIM_SONIC);
		float c = kineticCharge(p) / CHARGE_MAX;
		setCharge(p, 0f);
		float hit = Mth.lerp(c, SONIC_HIT, SONIC_HIT_FULL);
		float shock = Mth.lerp(c, MACH_SHOCK, SONIC_SHOCK_FULL);
		double radius = Mth.lerp(c, SONIC_RADIUS, SONIC_RADIUS_FULL);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		LivingEntity target = SuperSpeedMoves.aimTarget(p, 5.0);
		if (target != null && target.distanceToSqr(p) > 36.0) {
			target = null;
		}
		Vec3 impact = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : p.getEyePosition().add(look.scale(2.5));
		if (target != null) {
			SuperSpeedMoves.punchThrough(p, target, hit * damageScale(p));
			Vec3 shove = look.scale(2.2 + 2.0 * c);
			target.push(shove.x, Math.max(0.45, shove.y + 0.45), shove.z);
			target.hurtMarked = true;
		}
		for (LivingEntity e : around(p, impact, radius)) {
			if (e == target || e.getVehicle() == p) {
				continue;
			}
			SuperSpeedMoves.punchThrough(p, e, shock * damageScale(p));
			shove(e, impact, 1.2 + c, 0.3);
		}
		// the sound barrier: a ring standing across the punch (and a ground ring as wide as the shockwave)
		QuirkFx.ring(l, impact).colours(0xFFFFFF, GLOW).size(1.5f + 2f * c).thickness(0.35f).life(10).flags(QuirkFx.F_VERTICAL)
				.vec(look).style(QuirkFx.RING_SHOCK).spawn();
		QuirkFx.ring(l, new Vec3(impact.x, p.getY() + 0.1, impact.z)).colours(0xFFFFFF, GLOW).size((float) radius).thickness(0.4f).life(12).ground().spawn();
		l.sendParticles(ParticleTypes.SONIC_BOOM, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f + 0.3f * c, 1.7f);
		OmnitrixSounds.play(l, impact, SND_BURST, 0.8f + 0.4f * c, 1.2f);
		QuirkCamera.shakeAround(l, p.position(), 10, 0.3f + 0.3f * c, 6);
		return c;
	}

	/**
	 * G: Raptor Strike (v0.17.2 revamp, was Blitz) -- a zip to the foe you aim at (24 blocks) and a slide-tackle off its feet on
	 * his ball feet: 14, tripped (no walking or jumping) for {@link #TRIP_TICKS}; everything round it takes 8. No target, no cooldown.
	 */
	public boolean raptorStrike(ServerPlayer p) {
		if (cooldownLeft(p, STRIKE) > 0) {
			use(p, STRIKE); // (the "not ready" line)
			return false;
		}
		LivingEntity target = SuperSpeedMoves.aimTarget(p, SuperSpeedMoves.BLITZ_RANGE);
		if (!SuperSpeedMoves.blitzCore(p)) {
			return false; // nothing to hit: no cooldown
		}
		cooldown(p, STRIKE, -1);
		anim(p, ANIM_STRIKE);
		if (target != null && target.isAlive()) {
			trip(p, target);
		}
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 1.0f, 1.0f);
		return true;
	}

	/** Trips {@code e} for {@link #TRIP_TICKS} (a boss for a quarter of it). */
	public void trip(ServerPlayer p, LivingEntity e) {
		int t = AlienStatus.isBoss(e) ? TRIP_TICKS / 4 : TRIP_TICKS;
		AlienStatus.root(p, e, t, 0);
		TRIPPED.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).put(e.getId(), p.level().getGameTime() + t);
	}

	/** Shift+G: XLR8 runs rings round where he stood -- a twister that lifts and batters what is near, then throws it out. */
	public boolean tornadoRun(ServerPlayer p) {
		if (!use(p, TORNADO)) {
			return false;
		}
		anim(p, ANIM_TORNADO);
		long now = p.level().getGameTime();
		TWISTERS.put(p.getUUID(), new Twister(p.level().dimension(), p.position()));
		setTimer(p, "tornado_end", now + TORNADO_TICKS);
		setFlag(p, F_TORNADO, true);
		ServerLevel l = level(p);
		for (int i = 0; i < 3; i++) {
			QuirkFx.ring(l, p.position().add(0, 0.4 + i * 1.6, 0)).colours(0xFFFFFF, GLOW).size(2.5f + i).thickness(0.35f).life(TORNADO_TICKS)
					.style(QuirkFx.RING_SOFT).spawn();
		}
		OmnitrixSounds.play(l, p.position(), SND_TORNADO, 1.2f, 0.9f);
		return true;
	}

	/**
	 * Shift+G again while the Tornado Run spins (v0.17.2 revamp): he lets the twister go -- it travels {@link #LOOSE_BLOCKS}
	 * the way he looks over 4 s, picking things up (2 every 0.5 s within 3), and he is free to move.
	 */
	public boolean letLoose(ServerPlayer p) {
		Twister t = TWISTERS.remove(p.getUUID());
		if (t == null) {
			return false;
		}
		setTimer(p, "tornado_end", 0);
		setFlag(p, F_TORNADO, false);
		long now = p.level().getGameTime();
		LOOSE.put(p.getUUID(), new Loose(t.dim(), t.at(), flatLook(p), now + LOOSE_TICKS));
		setFlag(p, F_LOOSE, true);
		anim(p, ANIM_LOOSE);
		OmnitrixSounds.play(level(p), t.at(), SND_TORNADO, 1.2f, 1.2f);
		return true;
	}

	/** Use on a creature (empty hand): the item in a mob's hand ends up in yours. */
	public boolean snatch(ServerPlayer p, LivingEntity target) {
		if (!(target instanceof Mob mob) || mob.getMainHandItem().isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.xlr8.nothing_to_snatch").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, SNATCH)) {
			return false;
		}
		anim(p, ANIM_SNATCH);
		ItemStack item = mob.getMainHandItem().copy();
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
		if (!p.getInventory().add(item)) {
			p.drop(item, false);
		}
		target.invulnerableTime = 0;
		hit(p, target, 2f);
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, p.position().add(0, 1, 0), target.position().add(0, target.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, GLOW).size(0.3f).life(8)
				.style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 0.7f, 1.6f);
		return true;
	}

	/** X: Blur Dash -- a 10-block dash where you aim, through bodies (6 to each); his clients draw the streak line along it. */
	public boolean blurDash(ServerPlayer p) {
		if (!use(p, DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		Vec3 f = p.getLookAngle();
		Vec3 from = p.position();
		p.setDeltaMovement(f.scale(DASH_SPEED));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, "dash_end", p.level().getGameTime() + DASH_TICKS);
		setFlag(p, F_DASH, true);
		DASH_HITS.put(p.getUUID(), new HashSet<>());
		SuperSpeedMoves.streak(p, SpeedStreakPayload.KIND_STREAK, from, from.add(f.scale(5.0)), GLOW, 14);
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 1.0f, 1.0f);
		return true;
	}

	/**
	 * Shift+Z (his ultimate): Raptor Rush (v0.17.2 revamp, was Speed Sweep) -- blink through every foe within
	 * {@link #RUSH_RANGE} (26 each), then home; untouchable while it runs; every hop leaves a blue-and-black streak line. Again:
	 * call it off.
	 */
	public boolean raptorRush(ServerPlayer p) {
		if (SuperSpeedMoves.sweeping(p)) {
			SuperSpeedMoves.endSweep(p);
			return false;
		}
		if (cooldownLeft(p, RUSH) > 0) {
			use(p, RUSH);
			return false;
		}
		if (!SuperSpeedMoves.beginSweep(p)) {
			return false;
		}
		cooldown(p, RUSH, -1);
		anim(p, ANIM_RUSH);
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 1.0f, 1.3f);
		return true;
	}

	/**
	 * Shift+X: Friction Zero (v0.17.2 revamp, replaces Phase -- the Flash's trick, not his) -- {@link #SKATE_TICKS} of
	 * frictionless skating on his ball feet, low to the ground: through 1-block gaps, bowling over what he hits (6). Tap Shift+X
	 * again to stand up early.
	 */
	public boolean frictionZero(ServerPlayer p) {
		if (flag(p, F_SKATE) || !use(p, SKATE)) {
			return false;
		}
		setDown(p);
		anim(p, ANIM_SKATE);
		setFlag(p, F_SKATE, true);
		setTimer(p, "skate_end", p.level().getGameTime() + SKATE_TICKS);
		DASH_HITS.put(p.getUUID(), new HashSet<>());
		Vec3 v = flatLook(p).scale(SKATE_SPEED);
		p.setDeltaMovement(v.x, p.getDeltaMovement().y, v.z);
		p.hurtMarked = true;
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, GLOW).size(2.5f).thickness(0.3f).life(10).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 0.9f, 0.7f);
		return true;
	}

	public void endSkate(ServerPlayer p) {
		if (!flag(p, F_SKATE)) {
			return;
		}
		setFlag(p, F_SKATE, false); // his box stands back up -- in a gap, vanilla keeps him crawling until there is room
		setTimer(p, "skate_end", 0);
		DASH_HITS.remove(p.getUUID());
		OmnitrixSounds.play(level(p), p.position(), SND_VISOR, 0.5f, 1.4f);
	}

	/** Hold X: Tail Spin (v0.17.2 revamp, was Whirlwind) -- he spins on one ball foot, tail out flat: everything within 6 is blown away (8). */
	public boolean tailSpin(ServerPlayer p) {
		if (!use(p, TAIL_SPIN)) {
			return false;
		}
		anim(p, ANIM_TAIL_SPIN);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1, 0);
		p.clearFire();
		for (LivingEntity e : around(p, c, TAIL_SPIN_RADIUS)) {
			e.invulnerableTime = 0;
			hit(p, e, TAIL_SPIN_DAMAGE);
			shove(e, c, TAIL_SPIN_KNOCKBACK, 0.7);
			e.clearFire();
		}
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(5), pr -> pr.getOwner() != p)) {
			Vec3 away = pr.position().subtract(c).normalize();
			pr.setDeltaMovement(away.scale(Math.max(0.6, pr.getDeltaMovement().length())));
			pr.hurtMarked = true;
		}
		// the tail's sweep: a flat blue arc at tail height, and the wind ring
		QuirkFx.ring(l, p.position().add(0, 0.7, 0)).colours(GLOW, COLOUR).size(2.2f).thickness(0.5f).life(10).style(QuirkFx.RING_SOFT).spawn();
		QuirkFx.ring(l, p.position().add(0, 0.2, 0)).colours(0xFFFFFF, GLOW).size((float) TAIL_SPIN_RADIUS).thickness(0.5f).life(12)
				.style(QuirkFx.RING_SOFT).spawn();
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2;
			SuperSpeedMoves.streak(p, SpeedStreakPayload.KIND_STREAK, p.position().add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5),
					p.position().add(Math.cos(a + 1.4) * 1.5, 0, Math.sin(a + 1.4) * 1.5), GLOW, 8);
		}
		OmnitrixSounds.play(l, c, SND_TORNADO, 1.0f, 1.4f);
		QuirkCamera.shakeAround(l, c, 10, 0.2f, 6);
		return true;
	}

	/** Shift+R: Kick Barrage -- 8 skating kicks in 0.8 s (2 each), then the tail-whip finisher (5, r 3, all round). */
	public boolean kickBarrage(ServerPlayer p) {
		if (!use(p, KICKS)) {
			return false;
		}
		anim(p, ANIM_KICKS);
		setTimer(p, "kicks_end", p.level().getGameTime() + KICK_TICKS + 2);
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 0.6f, 1.6f);
		return true;
	}

	/**
	 * V (v0.17.1 revamp, user: V = utility / mode): Speed Mode on / off. On: the visor slams down, he runs 20 blocks a second (60
	 * sprinting; v0.17.2 user), the blue-and-black streak lines, he runs up walls and across water, steps
	 * over a block and a half, never takes fall damage and heals slowly. Off: the visor opens and he is the plain (still fast) XLR8.
	 */
	public boolean toggleSpeedMode(ServerPlayer p) {
		boolean on = !flag(p, F_SPEED);
		if (on && pinLocked(p)) {
			return false;
		}
		if (!use(p, MODE)) {
			return false;
		}
		setSpeedMode(p, on);
		anim(p, ANIM_MODE);
		ServerLevel l = level(p);
		if (on) {
			QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, GLOW).size(3.5f).thickness(0.4f).life(12).ground().spawn();
			l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
		}
		OmnitrixSounds.play(l, p.position(), SND_VISOR, 0.9f, on ? 1.0f : 0.8f);
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.xlr8.speed_on" : "message.projecthero.omnitrix.xlr8.speed_off")
				.withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
		return true;
	}

	private void setSpeedMode(ServerPlayer p, boolean on) {
		setFlag(p, F_SPEED, on);
		if (on) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, MODE_ID, SPEED_MODE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(p, Attributes.STEP_HEIGHT, MODE_STEP_ID, SPEED_MODE_STEP, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, MODE_ID);
			PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, MODE_STEP_ID);
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, BALANCE_ID);
			setFlag(p, F_SLICK, false);
		}
		applySprint(p);
	}

	/**
	 * Hold V: Overdrive (as the plain Speedster's V) -- {@link #OVERDRIVE_TICKS} of Speed Mode (switched on if it was off) with
	 * {@link #OD_WALK_BPS} blocks a second walking and {@link #OD_RUN_BPS} running (v0.17.2), everything he deals +50%, Haste III, a block more step, Kinetic Charge filling
	 * twice as fast, and the streak lines longer with a white-hot core. A tap of V ends it early; Speed Mode goes back to how it was.
	 */
	public boolean overdrive(ServerPlayer p) {
		if (pinLocked(p) || !use(p, OVERDRIVE)) {
			return false;
		}
		setTimer(p, "od_mode_was", flag(p, F_SPEED) ? 1 : 0);
		if (!flag(p, F_SPEED)) {
			setSpeedMode(p, true);
		}
		setFlag(p, F_OVERDRIVE, true);
		setTimer(p, "od_end", p.level().getGameTime() + OVERDRIVE_TICKS);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, OD_SPEED_ID, OVERDRIVE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, OD_DAMAGE_ID, OVERDRIVE_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, OD_STEP_ID, OVERDRIVE_STEP, AttributeModifier.Operation.ADD_VALUE);
		applySprint(p);
		p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, OVERDRIVE_TICKS, 2, false, false, true));
		anim(p, ANIM_MODE);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, GLOW).size(6f).thickness(0.6f).life(14).ground().spawn();
		l.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.PLAYERS, 0.9f, 1.6f);
		OmnitrixSounds.play(l, p.position(), SND_VISOR, 1.0f, 1.2f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.xlr8.overdrive_on").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	/** Overdrive off: its speed, damage and step go; Speed Mode goes back to how it was before it. */
	public void endOverdrive(ServerPlayer p, boolean announce) {
		if (!flag(p, F_OVERDRIVE)) {
			return;
		}
		setFlag(p, F_OVERDRIVE, false);
		setTimer(p, "od_end", 0);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, OD_SPEED_ID);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, OD_DAMAGE_ID);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, OD_STEP_ID);
		if (timer(p, "od_mode_was") == 0) {
			setSpeedMode(p, false);
		}
		applySprint(p);
		if (announce) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.xlr8.overdrive_off").withStyle(ChatFormatting.GRAY), true);
			OmnitrixSounds.play(level(p), p.position(), SND_VISOR, 0.8f, 0.8f);
		}
	}

	/** Shift+V: the plain Speedster's Afterimage Decoys -- three afterimages stay behind and every foe after him goes for them. */
	public boolean decoys(ServerPlayer p) {
		if (cooldownLeft(p, DECOYS) > 0) {
			use(p, DECOYS);
			return false;
		}
		if (!SuperSpeedMoves.decoysCore(p)) {
			return false;
		}
		cooldown(p, DECOYS, -1);
		anim(p, ANIM_DECOYS);
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 0.8f, 1.5f);
		return true;
	}

	/**
	 * C: Time Burst -- everything within 12 slows to a crawl for 5 s (bosses less, and their threat table hears of it), the way
	 * the world looks to him; v0.17.2 revamp: his own screen gets a thin blue visor-glass tint with stretched edges while it lasts
	 * (the {@code burst_until} timer, drawn by his client).
	 */
	public boolean timeBurst(ServerPlayer p) {
		if (!use(p, BURST)) {
			return false;
		}
		anim(p, ANIM_BURST);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1, 0);
		for (LivingEntity e : around(p, c, BURST_RADIUS)) {
			boolean boss = QuirkThreat.add(e, p, 8f) || e.getMaxHealth() >= 150f;
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BURST_TICKS, boss ? 2 : 5));
			e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, BURST_TICKS, boss ? 1 : 3));
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BURST_TICKS, boss ? 0 : 1));
			if (!boss) {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.1));
				e.hurtMarked = true;
			}
		}
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(BURST_RADIUS), pr -> pr.getOwner() != p)) {
			pr.setDeltaMovement(pr.getDeltaMovement().scale(0.08));
			pr.hurtMarked = true;
		}
		setTimer(p, "burst_until", p.level().getGameTime() + BURST_TICKS);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(0xFFFFFF, GLOW).size((float) BURST_RADIUS).thickness(0.3f).life(20).ground().spawn();
		QuirkFx.blast(l, c).colours(0xFFFFFF, GLOW).size(2.0f).life(12).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		OmnitrixSounds.play(l, c, SND_BURST, 1.2f, 1.0f);
		return true;
	}

	/** v0.17.2 revamp: 0..1 of Time Burst's slow-motion view left (client-safe; his screen tint). */
	public static float burstView(Player p, float partial) {
		long until = Omnitrix.state(p).readyAt(ID + ".burst_until");
		if (until <= 0) {
			return 0f;
		}
		float left = until - (p.level().getGameTime() + partial);
		return Mth.clamp(left / BURST_TICKS, 0f, 1f);
	}

	/** Shift+C: a 1x2 tunnel 16 blocks ahead, mined in a blink; the drops land in your pockets and you shoot down it. */
	public int speedMine(ServerPlayer p) {
		ServerLevel l = level(p);
		if (!p.mayBuild()) {
			return 0;
		}
		if (!use(p, MINE)) {
			return 0;
		}
		anim(p, ANIM_MINE);
		Direction dir = Direction.fromYRot(p.getYRot());
		BlockPos start = p.blockPosition();
		ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
		int mined = 0;
		int reach = 0;
		for (int i = 1; i <= MINE_LENGTH; i++) {
			boolean stop = false;
			for (int up = 0; up <= 1; up++) {
				BlockPos pos = start.relative(dir, i).above(up);
				BlockState s = l.getBlockState(pos);
				if (s.isAir() || !s.getFluidState().isEmpty() && s.getCollisionShape(l, pos).isEmpty()) {
					continue;
				}
				float hard = s.getDestroySpeed(l, pos);
				if (hard < 0f || hard >= 50f || l.getBlockEntity(pos) != null || s.is(Blocks.BEDROCK) || !l.mayInteract(p, pos)) {
					stop = true;
					break;
				}
				for (ItemStack drop : Block.getDrops(s, l, pos, null, p, tool)) {
					if (!p.getInventory().add(drop)) {
						p.drop(drop, false);
					}
				}
				l.destroyBlock(pos, false, p);
				mined++;
			}
			if (stop) {
				break;
			}
			reach = i;
		}
		Vec3 f = Vec3.atLowerCornerOf(dir.getNormal());
		p.setDeltaMovement(f.scale(Math.min(2.2, 0.4 + reach * 0.12)));
		p.hurtMarked = true;
		SuperSpeedMoves.streak(p, SpeedStreakPayload.KIND_STREAK, p.position(), p.position().add(f.scale(Math.max(2, reach))), GLOW, 12);
		OmnitrixSounds.play(l, p.position(), SND_DASH, 1.0f, 0.8f);
		OmnitrixSounds.play(l, p.position(), SND_HIT, 0.8f, 0.6f);
		return mined;
	}

	// ---------------------------------------------------------------- Rescue Carry (v0.17.2 revamp)

	/**
	 * Use on a player (empty hand): Rescue Carry -- he scoops them onto his back and they come along at his speed, taking no
	 * fall damage. Use again, Sneak (either of you) or any of his attacks sets them down.
	 */
	public boolean rescueCarry(ServerPlayer p, ServerPlayer ally) {
		if (ally == p || ally.isSpectator() || !ally.isAlive() || flag(p, F_SKATE) || p.isPassenger()) {
			return false;
		}
		if (ally.getVehicle() == p) {
			setDown(p);
			return true;
		}
		if (!p.getPassengers().isEmpty() || !use(p, CARRY)) {
			return false;
		}
		if (ally.isPassenger()) {
			ally.stopRiding();
		}
		if (!ally.startRiding(p, true)) {
			return false;
		}
		CARRYING.put(p.getUUID(), ally.getUUID());
		setFlag(p, F_CARRY, true);
		SuperSpeedHandlers.syncPassengersToSelf(p);
		ally.resetFallDistance();
		anim(p, ANIM_CARRY);
		ally.displayClientMessage(Component.translatable("message.projecthero.omnitrix.xlr8.carried", p.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.xlr8.carrying", ally.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 0.7f, 1.4f);
		return true;
	}

	/** Who rides on his back (Rescue Carry), or null. */
	@Nullable
	public static ServerPlayer carried(ServerPlayer p) {
		UUID id = CARRYING.get(p.getUUID());
		if (id == null || p.getServer() == null) {
			return null;
		}
		ServerPlayer r = p.getServer().getPlayerList().getPlayer(id);
		return r != null && r.getVehicle() == p ? r : null;
	}

	/** Sets the carried player down beside him (no fall damage on the way). */
	public void setDown(ServerPlayer p) {
		UUID id = CARRYING.remove(p.getUUID());
		if (id == null) {
			return;
		}
		setFlag(p, F_CARRY, false);
		ServerPlayer r = p.getServer() == null ? null : p.getServer().getPlayerList().getPlayer(id);
		if (r != null && r.getVehicle() == p) {
			r.stopRiding();
			Vec3 side = new Vec3(-flatLook(p).z, 0, flatLook(p).x);
			Vec3 at = p.position().add(side.scale(0.9));
			if (p.level().noCollision(r, r.getBoundingBox().move(at.subtract(r.position())))) {
				r.teleportTo(at.x, at.y, at.z);
			}
			r.setDeltaMovement(Vec3.ZERO);
			r.hurtMarked = true;
		}
		if (r != null) {
			r.resetFallDistance();
		}
		SuperSpeedHandlers.syncPassengersToSelf(p);
	}

	private void tickCarry(ServerPlayer p) {
		if (!CARRYING.containsKey(p.getUUID())) {
			return;
		}
		ServerPlayer r = carried(p);
		if (r == null || !p.isAlive() || p.isShiftKeyDown()) {
			setDown(p);
			return;
		}
		r.resetFallDistance();
		if (p.tickCount % 20 == 0) {
			SuperSpeedHandlers.syncPassengersToSelf(p); // belt and braces (a relog, a chunk load)
		}
	}

	/** Where the carried player sits: on his back, high and a little behind (both sides, {@code OmnitrixRiding.seat}). */
	@Nullable
	public static Vec3 carrySeat(Player host, Entity passenger, EntityDimensions dims) {
		if (!(passenger instanceof Player) || !is(host)) {
			return null;
		}
		float yaw = host.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(0.0, dims.height() * 0.42, -0.32).yRot(-yaw);
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		setDown(p);
		if (target instanceof LivingEntity le && ready(p, JAB)) {
			anim(p, ANIM_JAB);
			le.invulnerableTime = 0;
			hit(p, le, JAB_EXTRA);
			le.invulnerableTime = 0; // the fist itself still lands this tick
			level(p).sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 5, 0.2, 0.3, 0.2, 0.3);
		}
		return false;
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (CARRYING.containsKey(p.getUUID())) {
			setDown(p); // Use again: set them down
			return InteractionResult.SUCCESS;
		}
		return itemSweep(p) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (target instanceof ServerPlayer ally) {
			return rescueCarry(p, ally) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (!(target instanceof Mob mob) || mob.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		return snatch(p, mob) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/** Use (empty hand): every dropped item and XP orb within 14 blocks is at your feet in a blink. */
	public int itemSweep(ServerPlayer p) {
		ServerLevel l = level(p);
		List<ItemEntity> items = l.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(14), ItemEntity::isAlive);
		List<ExperienceOrb> orbs = l.getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(14), ExperienceOrb::isAlive);
		if (items.isEmpty() && orbs.isEmpty()) {
			return 0;
		}
		if (!use(p, SWEEP)) {
			return 0;
		}
		anim(p, ANIM_SWEEP);
		for (ItemEntity it : items) {
			it.teleportTo(p.getX(), p.getY() + 0.2, p.getZ());
			it.setNoPickUpDelay();
		}
		for (ExperienceOrb o : orbs) {
			o.teleportTo(p.getX(), p.getY() + 0.2, p.getZ());
		}
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, GLOW).size(3f).thickness(0.3f).life(10).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 0.7f, 1.8f);
		return items.size() + orbs.size();
	}
}
