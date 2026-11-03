package com.projecthero.mod.omnitrix.alien.ripjaws;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixDamage;
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
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Ripjaws, the Piscciss Volann -- a grey-blue fish-man with a huge toothy jaw, gills and fins, whose legs become a fish
 * tail in the water. In water he is a predator: fast (Dolphin's Grace), never drowns, sees and digs freely (Conduit Power), and
 * hits a quarter again as hard. On land he slowly DRIES OUT (the hydration meter over his keys): two minutes of dry land are free,
 * then he chokes for 1 health every 2 s (slowed, weakened) until he gets wet again. Water refills the meter in 15 s; rain stops
 * the drying and refills it slowly; Drench (Shift+C) splashes half of it back.
 *
 * <p>v0.17.2 revamp (docs/OMNITRIX_REVAMP_RIPJAWS.md; user: "Drop tidal roar", "Do lure light i suppose", "Draw jaws as an
 * effect", "keep drag under the same"): the JAWS are the centre of the kit -- Steel Jaws bites through armour, Drag Under clamps
 * and drowns, Gnash chews on land and Megamouth is one colossal chomp. The jaws are a rendered effect (toothed arcs that snap shut,
 * {@code RipjawsClient}), not a model part.
 *
 * <pre>
 *   R        Bite           a huge chomp (12; heals 1; Steel Jaws:          Shift+R  Jaw Lunge       lunge jaws-first: the first creature in the way (16)
 *                           half its armour ignored, 8 off its armour)
 *   G        Drag Under     clamp on for 3 s: in water towed and drowned    Shift+G  Tail Sweep      a 270-degree tail sweep (12 shove wave in water /
 *                           (3 a second), on land thrashed (3 x 4) and                               10, knockback and slow on land)
 *                           flung; a boss 1 s, not towed
 *   X        Water Dash     a burst through the water / on land a hop       Shift+X  Breach          leap out of the water / on land a back-flip; looking
 *                                                                                                     down in the air, X again = Dive Strike (14)
 *   Z        Whirlpool      in water: a 5 s vortex that drags and drowns    Shift+Z  Megamouth       ULTIMATE: a 0.75 s gape, then a 4-block chomp (30,
 *            Gnash          on land: 5 bites in 1 s in a cone (5 x 4)                                no armour; heals a quarter, max 8; kills refill 30 s)
 *   V        Water Bubble   30 s: dry land counts as rain                   Shift+V  Lure Light      toggle: night vision, fish come, hostiles target you
 *   C        Feeding Frenzy 10 s: +30% damage, faster, bites heal 3,        Shift+C  Drench          splash yourself: half the hydration back
 *                           the wounded outlined red (Blood Scent)
 *   Attack   jaw punch (+3 in water)        Use on a block   dig through sand, gravel, dirt...; bite through iron bars, chains, iron doors
 * </pre>
 * Every move does x1.25 in water (v0.17.2: was x1.5). Hot biomes dry him 1.5x as fast, the Nether 3x, the Lure Light another
 * quarter; burning adds 10 s of dryness a second.
 */
public final class RipjawsKit extends AlienKit {
	public static final String ID = "ripjaws";
	public static final int COLOUR = 0x7A98A4;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** The chomp: a jaw snap and a meaty hit. */
	public static final OmnitrixSfx.Cue BITE_SOUND = OmnitrixSfx.cue("ripjaws_bite")
			.layer(SoundEvents.EVOKER_FANGS_ATTACK, 0.45f, 1.1f)
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.3f, 0.8f);
	/** A wet, low growl. */
	public static final OmnitrixSfx.Cue ROAR = OmnitrixSfx.cue("ripjaws_roar")
			.layer(SoundEvents.POLAR_BEAR_WARNING, 0.4f, 0.8f)
			.layer(SoundEvents.DROWNED_AMBIENT, 0.35f, 0.6f)
			.gap(10);
	public static final OmnitrixSfx.Cue SPLASH = OmnitrixSfx.cue("ripjaws_splash")
			.layer(SoundEvents.GENERIC_SPLASH, 0.45f, 1.0f)
			.layer(SoundEvents.PLAYER_SPLASH, 0.22f, 0.9f)
			.gap(4);
	/** A body slam into water / ground: a heavy splash and a thud. */
	public static final OmnitrixSfx.Cue SLAM = OmnitrixSfx.cue("ripjaws_slam")
			.layer(SoundEvents.GENERIC_SPLASH, 0.55f, 0.7f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.35f, 0.6f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.2f, 1.4f);
	/** Out of water too long: a fishy flop and a choked gasp. */
	public static final OmnitrixSfx.Cue GASP = OmnitrixSfx.cue("ripjaws_gasp")
			.layer(SoundEvents.GUARDIAN_FLOP, 0.45f, 1.0f)
			.layer(SoundEvents.DROWNED_HURT, 0.25f, 0.8f)
			.gap(10);
	/** A whirlpool churning. */
	public static final OmnitrixSfx.Cue WHIRL = OmnitrixSfx.cue("ripjaws_whirl")
			.layer(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.45f, 0.9f)
			.layer(SoundEvents.GENERIC_SWIM, 0.35f, 0.8f)
			.layer(SoundEvents.TRIDENT_RIPTIDE_2, 0.22f, 0.8f)
			.gap(10);

	public static final int ANIM_BITE = 1;
	public static final int ANIM_LUNGE = 2;
	/** v0.17.2: G in water -- clamped on, towing it (was Tail Slam's id). */
	public static final int ANIM_DRAG = 3;
	/** v0.17.2: Shift+G Tail Sweep (was Fin Slash's id). */
	public static final int ANIM_SWEEP = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_BREACH = 6;
	public static final int ANIM_WHIRL = 7;
	/** v0.17.2: Z on land -- Gnash (was Mud Splash's id). */
	public static final int ANIM_GNASH = 8;
	/** v0.17.2: Shift+Z Megamouth -- the gape then the chomp (was Tidal Roar's id). */
	public static final int ANIM_MEGA = 9;
	public static final int ANIM_FRENZY = 10;
	public static final int ANIM_DRENCH = 11;
	public static final int ANIM_PUNCH = 12;
	public static final int ANIM_DIG = 13;
	public static final int ANIM_GASP = 14;
	/** v0.17.2: G on land -- clamped on, thrashing it side to side. */
	public static final int ANIM_THRASH = 16;
	/** v0.17.2: the end of a land Drag Under -- the throw. */
	public static final int ANIM_FLING = 17;
	/** v0.17.2: the Dive Strike's plunge, jaws open. */
	public static final int ANIM_DIVE = 18;
	/** v0.17.2: the Dive Strike's impact, jaws snapping shut. */
	public static final int ANIM_DIVE_HIT = 19;
	/** v0.17.2: Shift+V -- the lure sprouts. */
	public static final int ANIM_LURE = 20;

	public static final int F_WET = 1;
	public static final int F_RAIN = 2;
	public static final int F_FRENZY = 4;
	public static final int F_WARNED = 8;
	public static final int F_CHOKING = 16;
	// (F_BUBBLE = 32 below, with the V move)
	/** v0.17.2: a hot biome (desert, badlands, savanna): he dries 1.5x as fast. */
	public static final int F_HOT = 64;
	/** v0.17.2: the Nether: he dries 3x as fast. */
	public static final int F_NETHER = 128;
	/** v0.17.2: Shift+V Lure Light is on. */
	public static final int F_LURE = 256;
	/** v0.17.2: G Drag Under has a creature clamped in his jaws. */
	public static final int F_DRAG = 512;
	/** v0.17.2: airborne after a Breach -- X (looking down) is the Dive Strike. */
	public static final int F_BREACH = 1024;
	/** v0.17.2: the Dive Strike is plunging. */
	public static final int F_DIVING = 2048;

	public static final String BITE = "bite";
	public static final String LUNGE = "jaw_lunge";
	public static final String DRAG = "drag_under";
	public static final String SWEEP = "tail_sweep";
	public static final String DASH = "water_dash";
	public static final String BREACH = "breach";
	public static final String WHIRLPOOL = "whirlpool";
	public static final String MEGAMOUTH = "megamouth";
	public static final String FRENZY = "feeding_frenzy";
	public static final String DRENCH = "drench";
	public static final String PUNCH = "jaw_punch";
	public static final String DIG = "dig";

	/** Ticks of dry land before he starts choking (2 minutes). */
	public static final int MAX_DRY = 2400;
	public static final int REFILL_WATER = 8;
	public static final int REFILL_RAIN = 2;
	public static final int CHOKE_EVERY = 40;
	public static final float CHOKE_DAMAGE = 1f;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 1.5
	public static final float WATER_BONUS = 1.25f;
	public static final float FRENZY_BONUS = 1.3f;
	public static final int FRENZY_TICKS = 200;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 7 / 10
	// v0.17.2 (user: "adjust the other combat aliens damage aswell please to try and match this"): on Four Arms / Diamondhead's scale -- quick hits 5-8, moves 12-17, big ones 17-20, ultimates 21-36 (the bite is quick: 12 -> 8)
	// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger ... make each alien stronger accordingly please"): every damage number in the kit about x1.6, the bites hardest (old -> new in docs/OMNITRIX_DAMAGE_PASS_PROTOTYPE.md)
	public static final float BITE_DAMAGE = 12f;
	public static final float BITE_REACH = 3.5f;
	public static final float LUNGE_DAMAGE = 26f;
	/** v0.17.2 Steel Jaws: the share of a target's armour every bite ignores (Megamouth ignores all of it). */
	public static final float STEEL_JAWS = 0.5f;
	/** v0.17.2 Steel Jaws: what R's bite takes off each armour piece the target wears. */
	public static final int STEEL_JAWS_WEAR = 8;
	public static final float WHIRL_RADIUS = 6f;
	public static final int WHIRL_TICKS = 100;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 1.5 (10 pulses = 20)
	public static final float WHIRL_DAMAGE = 7f;
	public static final float WET_FIST = 5f;

	// v0.17.2 revamp (user: "keep drag under the same"): G Drag Under -- reach 4, a 3 s hold (players too), a boss 1 s
	public static final float DRAG_REACH = 4f;
	public static final int DRAG_TICKS = 60;
	public static final int DRAG_BOSS_TICKS = 20;
	/** In water: a bite every second while towed (3 x 3 = 9), its air drained as it goes. */
	public static final float DRAG_WATER_DAMAGE = 7f;
	public static final int DRAG_WATER_EVERY = 20;
	public static final int DRAG_AIR_DRAIN = 5;
	/** On land: thrashed side to side three times (3 x 4 = 12), then flung about 6 blocks. */
	public static final float DRAG_THRASH_DAMAGE = 8f;
	public static final int DRAG_THRASH_EVERY = 15;
	public static final double DRAG_FLING = 1.6;
	// v0.17.2 revamp: Shift+G Tail Sweep (Tail Slam and Fin Slash merged) -- 270 degrees round him, the front quarter spared
	public static final float SWEEP_RADIUS = 3.5f;
	public static final float SWEEP_WATER_DAMAGE = 24f;
	public static final float SWEEP_LAND_DAMAGE = 20f;
	public static final int SWEEP_SLOW_TICKS = 40;
	// v0.17.2 revamp: Shift+X's follow-up, the Dive Strike
	public static final float DIVE_DAMAGE = 24f;
	public static final float DIVE_RADIUS = 2.5f;
	/** How far down he must look (degrees of pitch) for X to dive. */
	public static final float DIVE_PITCH = 35f;
	public static final int BREACH_WINDOW = 80;
	// v0.17.2 revamp: Z on land -- Gnash (replaces Mud Splash): 5 bites in 1 s in a short cone
	public static final float GNASH_DAMAGE = 7f;
	public static final int GNASH_BITES = 5;
	public static final int GNASH_EVERY = 4;
	public static final float GNASH_RANGE = 3.5f;
	// v0.17.2 revamp (user: "Drop tidal roar"): Shift+Z Megamouth, his ultimate
	public static final int MEGA_WINDUP = 15;
	// v0.17.2 (user: "increase the damage of every alien ultimate that deals damage to be between 40-70 damage"): 55 (x1.25 in water, ~69)
	public static final float MEGA_DAMAGE = 88f; // v0.17.2 damage pass: 55 -> 88 (x1.25 in water, 110)
	public static final float MEGA_RANGE = 4f;
	public static final float MEGA_PULL = 6f;
	public static final float MEGA_HEAL_SHARE = 0.25f;
	public static final float MEGA_HEAL_MAX = 8f;
	/** Each Megamouth kill: 30 s of hydration back. */
	public static final int MEGA_KILL_REFILL = 600;
	// v0.17.2 revamp: Blood Scent / Blood in the Water (client outlines)
	public static final float SCENT_RANGE = 24f;
	// v0.17.2 revamp: heat dries him faster
	public static final float HOT_RATE = 1.5f;
	public static final float NETHER_RATE = 3f;
	public static final float LURE_RATE = 1.25f;
	/** Burning: 10 s of dryness a second. */
	public static final int FIRE_DRY = 200;

	private static final ResourceLocation SWIM = PowerToggles.id("omnitrix_ripjaws_swim");
	private static final ResourceLocation DIG_WET = PowerToggles.id("omnitrix_ripjaws_dig");
	private static final ResourceLocation FIST_WET = PowerToggles.id("omnitrix_ripjaws_wet_fist");
	private static final ResourceLocation FRENZY_FIST = PowerToggles.id("omnitrix_ripjaws_frenzy_fist");
	private static final ResourceLocation FRENZY_SPEED = PowerToggles.id("omnitrix_ripjaws_frenzy_speed");

	/** player -> the whirlpool they spun up. */
	private static final Map<UUID, Whirl> WHIRLS = new ConcurrentHashMap<>();
	/** v0.17.2: player -> the creature clamped in his jaws (G Drag Under). */
	private static final Map<UUID, Drag> DRAGS = new ConcurrentHashMap<>();
	/** v0.17.2: player -> the game tick his kit last ran (the once-a-tick guard in {@link #tick}). */
	private static final Map<Integer, Long> LAST_TICK = new ConcurrentHashMap<>();

	private record Whirl(ResourceKey<Level> dim, Vec3 at, long until) {
	}

	/** v0.17.2: a Drag Under -- who, since when, towed in water or thrashed on land, a boss (1 s, never moved). */
	private record Drag(ResourceKey<Level> dim, int target, long start, boolean water, boolean boss) {
	}

	private RipjawsKit() {
	}

	public static void register() {
		Aliens.register(new RipjawsKit());
	}

	public static void clearSessionState() {
		WHIRLS.clear();
		DRAGS.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 (user: "give armour ratings to the aliens you think deserve armour ratings please but dont make it terrible"): scales, 10
		// v0.17.2 (user set the unarmed melee bonuses: Four Arms +10, Diamondhead +8 (+4 more with the blades), Upgrade +5, Ghostfreak +5, Stinkfly +4, Wildmutt +6, Ripjaws +5, Grey Matter unchanged)
		return AlienStats.builder().health(30f).speed(0.9f).scale(1.05f).armor(10f, 1f).attack(5f).knockbackResist(0.2f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(BITE, AbilitySlot.SLOT_1, 14), AlienMove.shift(LUNGE, AbilitySlot.SLOT_1, 70),
				// v0.17.2 revamp: G Drag Under (was Tail Slam), Shift+G Tail Sweep (Tail Slam + Fin Slash)
				AlienMove.tap(DRAG, AbilitySlot.SLOT_2, 200), AlienMove.shift(SWEEP, AbilitySlot.SLOT_2, 120),
				AlienMove.tap(DASH, AbilitySlot.SLOT_3, 30), AlienMove.shift(BREACH, AbilitySlot.SLOT_3, 100),
				// v0.17.2 (user: "Drop tidal roar"): Megamouth is the ultimate now
				AlienMove.tap(WHIRLPOOL, AbilitySlot.SLOT_4, 320), AlienMove.shift(MEGAMOUTH, AbilitySlot.SLOT_4, 600),
				AlienMove.tap(BUBBLE, AbilitySlot.SLOT_5, 1200), AlienMove.shift(LURE, AbilitySlot.SLOT_5, LURE_COOLDOWN),
				AlienMove.tap(FRENZY, AbilitySlot.SLOT_6, 600), AlienMove.shift(DRENCH, AbilitySlot.SLOT_6, 900),
				AlienMove.vanilla(PUNCH, "Attack", 0), AlienMove.vanilla(DIG, "Use on sand / gravel / dirt / iron bars", 3));
	}

	// ---------------------------------------------------------------- wet and dry

	/** In water (feet or head), or a bubble column. Reads the blocks, so it also holds for a player the server does not tick. */
	public static boolean inWater(Player p) {
		if (p.isInWaterOrBubble()) {
			return true;
		}
		Level l = p.level();
		return l.getFluidState(p.blockPosition()).is(FluidTags.WATER) || l.getFluidState(BlockPos.containing(p.getEyePosition())).is(FluidTags.WATER);
	}

	public static boolean inRain(Player p) {
		Level l = p.level();
		return l.isRainingAt(p.blockPosition()) || l.isRainingAt(BlockPos.containing(p.getEyePosition()).above());
	}

	/** v0.17.2: a hot biome (desert, badlands, savanna...: base temperature 1.5+), outside the Nether. */
	public static boolean inHeat(Player p) {
		return p.level().getBiome(p.blockPosition()).value().getBaseTemperature() >= 1.5f;
	}

	/**
	 * v0.17.2: how fast the meter moves per tick for these synced flags -- water refills it (-8), rain slowly (-2); dry land
	 * drains it at 1, x1.5 in a hot biome or x3 in the Nether, and another x1.25 under the Lure Light.
	 */
	public static float dryRate(int flags) {
		if ((flags & F_WET) != 0) {
			return -REFILL_WATER;
		}
		if ((flags & F_RAIN) != 0) {
			return -REFILL_RAIN;
		}
		float r = 1f;
		if ((flags & F_NETHER) != 0) {
			r *= NETHER_RATE;
		} else if ((flags & F_HOT) != 0) {
			r *= HOT_RATE;
		}
		if ((flags & F_LURE) != 0) {
			r *= LURE_RATE;
		}
		return r;
	}

	/**
	 * How long he has been dry, in ticks (0 = soaked, {@link #MAX_DRY} = choking). Lives in the synced state as the value at the
	 * last rate change plus the rate since, so the HUD reads it without a packet every tick.
	 */
	public static int dryness(Player p, long now) {
		OmnitrixState s = Omnitrix.state(p);
		long base = s.readyAt(ID + ".dry_base");
		long since = s.readyAt(ID + ".dry_since");
		if (base <= 0 || since <= 0) {
			return 0;
		}
		double v = (base - 1) + Math.floor(dryRate(s.kitFlags) * Math.max(0, now - since));
		return (int) Math.max(0, Math.min(MAX_DRY, v));
	}

	/** 1 = fully wet, 0 = choking. */
	public static float hydration(Player p, long now) {
		return 1f - dryness(p, now) / (float) MAX_DRY;
	}

	/** Sets the dryness (tests, Drench) and the wet / rain flags as they are now (the heat / lure flags are kept). */
	public void setDryness(ServerPlayer p, int ticks, boolean wet, boolean rain) {
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key("dry_base"), Math.max(0, Math.min(MAX_DRY, ticks)) + 1L);
		n.timers.put(key("dry_since"), Math.max(1L, now));
		n.kitFlags = (n.kitFlags & ~(F_WET | F_RAIN)) | (wet ? F_WET : 0) | (rain && !wet ? F_RAIN : 0);
		if (ticks < MAX_DRY * 3 / 4) {
			n.kitFlags &= ~F_WARNED;
		}
		if (ticks < MAX_DRY) {
			n.kitFlags &= ~F_CHOKING;
		}
		Omnitrix.save(p, n);
	}

	/** v0.17.2: flips a flag that changes the drying rate (heat, the lure), folding the time so far into the stored value first. */
	private void setRateFlag(ServerPlayer p, int bit, boolean on) {
		if (flag(p, bit) == on) {
			return;
		}
		long now = p.level().getGameTime();
		int d = dryness(p, now);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key("dry_base"), d + 1L);
		n.timers.put(key("dry_since"), Math.max(1L, now));
		n.kitFlags = on ? n.kitFlags | bit : n.kitFlags & ~bit;
		Omnitrix.save(p, n);
	}

	private void setWetFist(ServerPlayer p, boolean wet) {
		if (wet) {
			PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, FIST_WET, WET_FIST, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, FIST_WET);
		}
	}

	/** A move's damage now: x1.25 in water, x1.3 in a feeding frenzy. */
	public float damage(ServerPlayer p, float base) {
		float d = base;
		if (inWater(p)) {
			d *= WATER_BONUS;
		}
		if (flag(p, F_FRENZY)) {
			d *= FRENZY_BONUS;
		}
		return d;
	}

	/**
	 * v0.17.2 Steel Jaws: {@code amount} raised so that, after {@code t}'s armour, it lands as if {@code share} of that armour
	 * (and toughness) were not there -- 0.5 for every bite, 1 for Megamouth. Unarmoured targets take {@code amount} as is.
	 */
	public static float pierce(ServerPlayer p, LivingEntity t, float amount, float share) {
		float armour = t.getArmorValue();
		if (armour <= 0f || share <= 0f) {
			return amount;
		}
		float tough = (float) t.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		DamageSource src = OmnitrixDamage.alienStrike(p);
		float full = CombatRules.getDamageAfterAbsorb(t, amount, src, armour, tough);
		float part = share >= 1f ? amount : CombatRules.getDamageAfterAbsorb(t, amount, src, armour * (1f - share), tough * (1f - share));
		return full <= 0.01f ? amount : amount * part / full;
	}

	/** v0.17.2: a bite -- the move's damage now (water, frenzy) through Steel Jaws. */
	private boolean chomp(ServerPlayer p, LivingEntity t, float base, float share) {
		return hurt(p, t, pierce(p, t, damage(p, base), share), false);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		boolean wet = inWater(p);
		setDryness(p, 0, wet, inRain(p));
		PowerToggles.modifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM, 0.65, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.SUBMERGED_MINING_SPEED, DIG_WET, 4.0, AttributeModifier.Operation.ADD_VALUE);
		setWetFist(p, wet);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), ROAR, 0.8f, 1.3f);
		l.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		WHIRLS.remove(p.getUUID());
		LAST_TICK.remove(p.getId());
		releaseDrag(p); // v0.17.2: Drag Under lets go cleanly (revert, death, logout)
		endFrenzy(p);
		PowerToggles.clearModifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM);
		PowerToggles.clearModifier(p, Attributes.SUBMERGED_MINING_SPEED, DIG_WET);
		setWetFist(p, false);
		for (var eff : new net.minecraft.core.Holder[] { MobEffects.DOLPHINS_GRACE, MobEffects.CONDUIT_POWER }) {
			@SuppressWarnings("unchecked")
			var h = (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) eff;
			MobEffectInstance cur = p.getEffect(h);
			if (cur != null && cur.getDuration() <= 60) {
				p.removeEffect(h); // only his own short water buffs, never a real potion
			}
		}
		clearChoke(p);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { "dry_base", "dry_since", "lunge_end", "frenzy_end", "gnash_start", "mega_at", "breach_start", "dive_start" }) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2: once per game tick -- the timed pulses (Drag Under, Gnash) must not double up if he is ticked twice in a tick
		// (a gametest's mock player is ticked by the server and by the test's own pump)
		Long last = LAST_TICK.put(p.getId(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		tickV(p, l, now);
		boolean wet = inWater(p);
		boolean rain = !wet && (inRain(p) || flag(p, F_BUBBLE)); // v0.17.1: the Water Bubble counts as rain
		boolean wasWet = flag(p, F_WET);
		if (wet != wasWet || rain != flag(p, F_RAIN)) {
			// the rate changed: fold the time so far into the stored value
			int d = dryness(p, now);
			setDryness(p, d, wet, rain);
			if (wet != wasWet) {
				setWetFist(p, wet);
				if (wet) {
					OmnitrixSounds.play(l, p.position(), SPLASH, 0.7f, 1.2f);
					clearChoke(p);
					refresh(p, MobEffects.DOLPHINS_GRACE, 0);
					refresh(p, MobEffects.CONDUIT_POWER, 0);
				}
			}
		}
		// v0.17.2 revamp: heat dries him faster -- a hot biome x1.5, the Nether x3; burning adds 10 s of dryness a second
		boolean nether = l.dimensionType().ultraWarm();
		setRateFlag(p, F_NETHER, nether);
		setRateFlag(p, F_HOT, !nether && inHeat(p));
		if (!wet && p.isOnFire() && now % 20 == 0) {
			setDryness(p, dryness(p, now) + FIRE_DRY, false, rain);
		}
		int dry = dryness(p, now);
		if (wet) {
			p.setAirSupply(p.getMaxAirSupply());
			if (p.tickCount % 20 == 0) {
				refresh(p, MobEffects.DOLPHINS_GRACE, 0);
				refresh(p, MobEffects.CONDUIT_POWER, 0);
			}
			if (p.tickCount % 10 == 0) {
				l.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + p.getBbHeight() * 0.8, p.getZ(), 1, 0.2, 0.1, 0.2, 0.02);
			}
		}
		// the warnings and the choking
		if (dry >= MAX_DRY * 3 / 4 && !flag(p, F_WARNED)) {
			setFlag(p, F_WARNED, true);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ripjaws.drying").withStyle(ChatFormatting.GOLD), true);
			OmnitrixSounds.play(l, p.position(), GASP, 0.8f, 1.1f);
		}
		if (dry >= MAX_DRY) {
			if (!flag(p, F_CHOKING)) {
				setFlag(p, F_CHOKING, true);
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ripjaws.choking").withStyle(ChatFormatting.RED), true);
			}
			if (now % CHOKE_EVERY == 0) {
				p.invulnerableTime = 0;
				p.hurt(l.damageSources().dryOut(), CHOKE_DAMAGE);
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHOKE_EVERY + 4, 0, false, false, true));
				p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CHOKE_EVERY + 4, 0, false, false, true));
				anim(p, ANIM_GASP);
				OmnitrixSounds.play(l, p.position(), GASP, 0.9f, 0.9f + p.getRandom().nextFloat() * 0.2f);
			}
		} else if (flag(p, F_CHOKING)) {
			setFlag(p, F_CHOKING, false);
		}
		// the jaw lunge: the first creature in the way
		long lunge = timer(p, "lunge_end");
		if (lunge > 0) {
			if (now >= lunge) {
				setTimer(p, "lunge_end", 0);
			} else {
				for (LivingEntity e : around(p, p.position().add(0, p.getBbHeight() * 0.5, 0).add(p.getLookAngle().scale(0.8)), 1.8)) {
					chomp(p, e, LUNGE_DAMAGE, STEEL_JAWS);
					shove(e, p.position(), 0.7, 0.3);
					OmnitrixSounds.play(l, e.position(), BITE_SOUND, 1.2f, 0.8f);
					anim(p, ANIM_BITE); // the jaws snap shut on it
					setTimer(p, "lunge_end", 0);
					p.setDeltaMovement(p.getDeltaMovement().scale(0.2));
					p.hurtMarked = true;
					break;
				}
			}
		}
		// the feeding frenzy
		long frenzy = timer(p, "frenzy_end");
		if (frenzy > 0 && now >= frenzy) {
			endFrenzy(p);
		}
		tickWhirl(p, l, now);
		tickDrag(p, l, now);
		tickGnash(p, l, now);
		tickMegamouth(p, l, now);
		tickBreachAndDive(p, l, now, wet);
	}

	private void tickWhirl(ServerPlayer p, ServerLevel l, long now) {
		Whirl w = WHIRLS.get(p.getUUID());
		if (w == null) {
			return;
		}
		if (now >= w.until() || w.dim() != l.dimension()) {
			WHIRLS.remove(p.getUUID());
			return;
		}
		if (now % 4 == 0) {
			for (int k = 0; k < 3; k++) {
				double a = now * 0.35 + k * Math.PI * 2 / 3;
				double r = 1.0 + ((now + k * 7) % 20) / 20.0 * (WHIRL_RADIUS - 1);
				l.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, w.at().x + Math.cos(a) * r, w.at().y + (k % 3) * 0.8, w.at().z + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
		for (LivingEntity e : around(p, w.at(), WHIRL_RADIUS)) {
			Vec3 to = w.at().subtract(e.position());
			Vec3 h = new Vec3(to.x, 0, to.z);
			Vec3 swirl = new Vec3(-h.z, 0, h.x).normalize().scale(0.12);
			Vec3 pull = h.lengthSqr() > 0.25 ? h.normalize().scale(0.1) : Vec3.ZERO;
			double res = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			e.setDeltaMovement(e.getDeltaMovement().scale(0.8).add(pull.add(swirl).scale(res)).add(0, -0.04 * res, 0));
			e.hurtMarked = true;
			if (now % 10 == 0) {
				hurt(p, e, damage(p, WHIRL_DAMAGE), false);
				e.setAirSupply(Math.max(-19, e.getAirSupply() - 40));
			}
		}
		if (now % 20 == 0) {
			OmnitrixSounds.play(l, w.at(), WHIRL, 0.8f, 1.0f);
		}
	}

	/** The choking's own Slowness / Weakness (short, hidden particles), gone the moment he is wet again or turns back. */
	private static void clearChoke(ServerPlayer p) {
		for (var eff : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS)) {
			MobEffectInstance cur = p.getEffect(eff);
			if (cur != null && cur.getDuration() <= CHOKE_EVERY + 5 && cur.getAmplifier() == 0 && !cur.isVisible()) {
				p.removeEffect(eff);
			}
		}
	}

	private static void refresh(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> eff, int amp) {
		MobEffectInstance cur = p.getEffect(eff);
		if (cur == null || cur.getDuration() < 30 && cur.getAmplifier() <= amp) {
			p.addEffect(new MobEffectInstance(eff, 50, amp, true, false, true));
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					jawLunge(p);
				} else {
					bite(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					tailSweep(p);
				} else {
					dragUnder(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					breach(p);
				} else if (canDive(p)) {
					diveStrike(p); // v0.17.2: X again in the air after a Breach, looking down
				} else {
					waterDash(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					megamouth(p);
				} else {
					whirlpool(p);
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> {
				if (shift) {
					drench(p);
				} else {
					feedingFrenzy(p);
				}
			}
			default -> {
			}
		}
	}

	/**
	 * R: Bite -- a huge chomp on the creature ahead (reach 3.5): 12, heals 1 (3 in a frenzy). v0.17.2 Steel Jaws: half its armour
	 * ignored, and 8 durability off every armour piece it wears.
	 */
	public boolean bite(ServerPlayer p) {
		if (!use(p, BITE)) {
			return false;
		}
		anim(p, ANIM_BITE);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), BITE_SOUND, 1.0f, 0.95f + p.getRandom().nextFloat() * 0.15f);
		LivingEntity t = lookTarget(p, BITE_REACH);
		if (t != null) {
			chomp(p, t, BITE_DAMAGE, STEEL_JAWS);
			wearArmour(t, STEEL_JAWS_WEAR);
			p.heal(flag(p, F_FRENZY) ? 3f : 1f);
			l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.7, t.getZ(), 6, 0.25, 0.25, 0.25, 0.25);
		}
		return true;
	}

	/** v0.17.2 Steel Jaws: {@code amount} durability off every armour piece {@code t} wears. */
	public static void wearArmour(LivingEntity t, int amount) {
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY }) {
			ItemStack s = t.getItemBySlot(slot);
			if (!s.isEmpty() && s.isDamageableItem()) {
				s.hurtAndBreak(amount, t, slot);
			}
		}
	}

	/** Shift+R: Jaw Lunge -- jaws-first along the look (about 6 blocks, twice that in water); the first creature in the way: 16. */
	public boolean jawLunge(ServerPlayer p) {
		if (!use(p, LUNGE)) {
			return false;
		}
		anim(p, ANIM_LUNGE);
		ServerLevel l = level(p);
		boolean wet = inWater(p);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.scale(wet ? 2.4 : 1.3).add(0, wet ? 0 : 0.2, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, "lunge_end", l.getGameTime() + (wet ? 14 : 10));
		QuirkFx.trail(l, p).colours(0xE0F4FF, COLOUR).size(0.4f).size2(8f).life(14).vec(new Vec3(0, 1.0, 0)).spawn();
		OmnitrixSounds.play(l, p.position(), ROAR, 0.7f, 1.4f);
		return true;
	}

	// ---------------------------------------------------------------- v0.17.2 G: Drag Under

	/**
	 * G: Drag Under -- v0.17.2 revamp (user: "keep drag under the same"). He clamps onto the creature he looks at (reach 4) and
	 * holds it 3 s, players too. In water it is towed in front of his face as he swims, bitten every second (3 x 3) with its air
	 * draining; on land he thrashes it side to side (3 x 4) and then flings it about 6 blocks. A boss is held 1 s and never moved.
	 * False (no cooldown) with nothing to bite.
	 */
	public boolean dragUnder(ServerPlayer p) {
		LivingEntity t = lookTarget(p, DRAG_REACH);
		if (t == null || DRAGS.containsKey(p.getUUID()) || !use(p, DRAG)) {
			return false;
		}
		ServerLevel l = level(p);
		boolean water = inWater(p);
		boolean boss = AlienStatus.isBoss(t);
		DRAGS.put(p.getUUID(), new Drag(l.dimension(), t.getId(), l.getGameTime(), water, boss));
		setFlag(p, F_DRAG, true);
		anim(p, water ? ANIM_DRAG : ANIM_THRASH);
		OmnitrixSounds.play(l, t.position(), BITE_SOUND, 1.2f, 0.7f);
		QuirkThreat.add(t, p, 3f);
		return true;
	}

	/** The id of the creature in {@code p}'s jaws (Drag Under), or -1. Server side. */
	public static int dragTarget(Player p) {
		Drag d = DRAGS.get(p.getUUID());
		return d == null ? -1 : d.target();
	}

	/** Lets go of whatever is in his jaws (revert, death, logout, the target gone). */
	private void releaseDrag(ServerPlayer p) {
		Drag d = DRAGS.remove(p.getUUID());
		if (d != null && p.level().getEntity(d.target()) instanceof LivingEntity t) {
			t.resetFallDistance();
		}
		setFlag(p, F_DRAG, false);
	}

	private void tickDrag(ServerPlayer p, ServerLevel l, long now) {
		Drag d = DRAGS.get(p.getUUID());
		if (d == null) {
			if (flag(p, F_DRAG)) {
				setFlag(p, F_DRAG, false);
			}
			return;
		}
		Entity e = l.getEntity(d.target());
		if (d.dim() != l.dimension() || !(e instanceof LivingEntity t) || !t.isAlive() || t.isRemoved() || t.distanceToSqr(p) > 12 * 12) {
			releaseDrag(p);
			return;
		}
		long age = now - d.start();
		int hold = d.boss() ? DRAG_BOSS_TICKS : DRAG_TICKS;
		Vec3 look = p.getLookAngle();
		if (d.boss()) {
			// a boss is only held a moment, never towed
			t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false), p);
		} else {
			// pinned in front of his face: towed in water, shaken side to side on land
			Vec3 at = p.getEyePosition().add(look.scale(0.9 + t.getBbWidth() * 0.5)).subtract(0, t.getBbHeight() * 0.5, 0);
			if (!d.water()) {
				Vec3 side = look.cross(new Vec3(0, 1, 0));
				side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
				at = at.add(side.scale(Math.sin(age * 0.45) * 0.7));
			}
			t.teleportTo(at.x, at.y, at.z);
			t.setDeltaMovement(Vec3.ZERO);
			t.resetFallDistance();
			t.hurtMarked = true;
		}
		if (d.water()) {
			if (!t.canBreatheUnderwater()) {
				t.setAirSupply(Math.max(-20, t.getAirSupply() - DRAG_AIR_DRAIN));
			}
			if (age > 0 && age % DRAG_WATER_EVERY == 0) {
				chomp(p, t, DRAG_WATER_DAMAGE, STEEL_JAWS);
				if (now % 2 == 0) {
					l.sendParticles(ParticleTypes.BUBBLE, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
				}
			}
		} else if (age > 0 && age % DRAG_THRASH_EVERY == 0 && age < DRAG_TICKS) {
			chomp(p, t, DRAG_THRASH_DAMAGE, STEEL_JAWS);
			OmnitrixSounds.play(l, t.position(), BITE_SOUND, 0.8f, 1.2f);
		}
		if (!t.isAlive()) {
			releaseDrag(p);
			return;
		}
		if (age >= hold) {
			releaseDrag(p);
			if (!d.water() && !d.boss()) {
				// the throw
				Vec3 f = new Vec3(look.x, 0, look.z);
				f = f.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : f.normalize();
				double res = 1.0 - Math.min(1.0, t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
				t.setDeltaMovement(f.x * DRAG_FLING * res, 0.55 * Math.max(0.3, res), f.z * DRAG_FLING * res);
				t.hurtMarked = true;
				anim(p, ANIM_FLING);
				OmnitrixSounds.play(l, t.position(), SLAM, 0.9f, 1.2f);
			}
		}
	}

	// ---------------------------------------------------------------- v0.17.2 Shift+G: Tail Sweep

	/** Whether {@code e} is in the sweep's 270 degrees: everything round him but the quarter straight ahead. */
	private static boolean inSweep(ServerPlayer p, LivingEntity e) {
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z);
		Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
		if (f.lengthSqr() < 1.0e-6 || to.lengthSqr() < 0.36) {
			return true;
		}
		return f.normalize().dot(to.normalize()) < Math.cos(Math.toRadians(45));
	}

	/**
	 * Shift+G: Tail Sweep -- v0.17.2 revamp (Tail Slam and Fin Slash merged; the tail is the one fin his model still has): his tail
	 * (his legs on land) sweeps 270 degrees round him, r 3.5, sparing the quarter in front. In water: 12 and a wave that shoves 5
	 * blocks; on land: 10, knockback and Slowness II for 2 s.
	 */
	public boolean tailSweep(ServerPlayer p) {
		if (!use(p, SWEEP)) {
			return false;
		}
		anim(p, ANIM_SWEEP);
		ServerLevel l = level(p);
		boolean wet = inWater(p);
		Vec3 c = p.position().add(0, 0.6, 0);
		QuirkFx.ring(l, p.position().add(0, 0.15, 0)).colours(0xF0FAFF, wet ? 0x3A9AE0 : 0x8AB0C0).style(QuirkFx.RING_SOFT).size(SWEEP_RADIUS + 0.4f)
				.thickness(0.4f).life(10).ground().spawn();
		OmnitrixSounds.play(l, c, SLAM, 1.0f, wet ? 0.9f : 1.2f);
		for (LivingEntity e : around(p, c, SWEEP_RADIUS)) {
			if (!inSweep(p, e)) {
				continue;
			}
			if (wet) {
				hurt(p, e, damage(p, SWEEP_WATER_DAMAGE), false);
				shove(e, c, 1.4, 0.2);
			} else {
				hurt(p, e, damage(p, SWEEP_LAND_DAMAGE), false);
				shove(e, c, 0.9, 0.35);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SWEEP_SLOW_TICKS, 1), p);
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- X / Shift+X

	public boolean waterDash(ServerPlayer p) {
		if (!use(p, DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		if (inWater(p)) {
			p.setDeltaMovement(look.scale(2.2));
			QuirkFx.trail(l, p).colours(0xE0F4FF, 0x3A8AE0).size(0.45f).size2(10f).life(18).vec(new Vec3(0, 0.6, 0)).spawn();
			OmnitrixSounds.play(l, p.position(), SPLASH, 0.8f, 1.4f);
		} else {
			Vec3 f = new Vec3(look.x, 0, look.z).normalize();
			p.setDeltaMovement(f.x * 0.75, 0.55, f.z * 0.75);
			OmnitrixSounds.play(l, p.position(), SPLASH, 0.5f, 1.6f);
		}
		p.hurtMarked = true;
		p.resetFallDistance();
		return true;
	}

	/** Shift+X: Breach -- out of the water in a leap (on land a back-flip); while airborne, looking down, X dives (v0.17.2). */
	public boolean breach(ServerPlayer p) {
		if (!use(p, BREACH)) {
			return false;
		}
		anim(p, ANIM_BREACH);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z).normalize();
		if (inWater(p)) {
			p.setDeltaMovement(f.x * 0.8, 1.6, f.z * 0.8);
			l.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1, p.getZ(), 16, 0.6, 0.4, 0.6, 0.3);
			QuirkFx.ring(l, p.position().add(0, 0.5, 0)).colours(0xFFFFFF, 0x3A9AE0).size(2.5f).thickness(0.5f).life(12).ground().spawn();
			OmnitrixSounds.play(l, p.position(), SPLASH, 1.2f, 0.8f);
		} else {
			p.setDeltaMovement(-f.x * 0.9, 0.7, -f.z * 0.9);
			OmnitrixSounds.play(l, p.position(), SPLASH, 0.5f, 1.8f);
		}
		p.hurtMarked = true;
		p.resetFallDistance();
		setFlag(p, F_BREACH, true);
		setTimer(p, "breach_start", l.getGameTime());
		return true;
	}

	/** v0.17.2: X is the Dive Strike right now -- airborne after a Breach, looking down. */
	public boolean canDive(ServerPlayer p) {
		return flag(p, F_BREACH) && !flag(p, F_DIVING) && !p.onGround() && !inWater(p) && p.getXRot() >= DIVE_PITCH;
	}

	/**
	 * X after a Breach, airborne and looking down: the Dive Strike -- v0.17.2 revamp (a shark's attack from above). He plunges
	 * jaws-first onto the spot below; where he lands (ground or water) everything within 2.5 is bitten for 14. Once per Breach.
	 */
	public boolean diveStrike(ServerPlayer p) {
		if (!canDive(p)) {
			return false;
		}
		ServerLevel l = level(p);
		setFlag(p, F_BREACH, false);
		setFlag(p, F_DIVING, true);
		setTimer(p, "dive_start", l.getGameTime());
		anim(p, ANIM_DIVE);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.x * 0.4, -2.2, look.z * 0.4);
		p.hurtMarked = true;
		p.resetFallDistance();
		OmnitrixSounds.play(l, p.position(), ROAR, 0.8f, 1.5f);
		return true;
	}

	private void tickBreachAndDive(ServerPlayer p, ServerLevel l, long now, boolean wet) {
		if (flag(p, F_BREACH)) {
			long since = now - timer(p, "breach_start");
			if (since > BREACH_WINDOW || since > 5 && (p.onGround() || wet)) {
				setFlag(p, F_BREACH, false);
				setTimer(p, "breach_start", 0);
			}
		}
		if (!flag(p, F_DIVING)) {
			return;
		}
		p.resetFallDistance();
		long since = now - timer(p, "dive_start");
		if (!(p.onGround() || wet || since > BREACH_WINDOW)) {
			if (p.getDeltaMovement().y > -1.2) {
				p.setDeltaMovement(p.getDeltaMovement().x, -1.6, p.getDeltaMovement().z);
				p.hurtMarked = true;
			}
			return;
		}
		// the impact: jaws first into the ground / the water
		setFlag(p, F_DIVING, false);
		setTimer(p, "dive_start", 0);
		anim(p, ANIM_DIVE_HIT);
		Vec3 c = p.position();
		QuirkFx.ring(l, c.add(0, 0.1, 0)).colours(0xFFFFFF, wet ? 0x3A9AE0 : 0x8AB0C0).size(DIVE_RADIUS + 0.6f).thickness(0.5f).life(12).ground().spawn();
		l.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.3, c.z, 12, 0.8, 0.1, 0.8, 0.2);
		OmnitrixSounds.play(l, c, SLAM, 1.2f, 0.9f);
		OmnitrixSounds.play(l, c, BITE_SOUND, 1.0f, 0.8f);
		QuirkCamera.shakeAround(l, c, 8, 0.35f, 6);
		for (LivingEntity e : around(p, c.add(0, 0.5, 0), DIVE_RADIUS)) {
			chomp(p, e, DIVE_DAMAGE, STEEL_JAWS);
			shove(e, c, 0.5, 0.3);
		}
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	/** Z: in water a whirlpool; on land (v0.17.2, replacing Mud Splash) Gnash -- 5 bites in 1 s in a short cone (5 x 4). */
	public boolean whirlpool(ServerPlayer p) {
		if (!use(p, WHIRLPOOL)) {
			return false;
		}
		ServerLevel l = level(p);
		if (inWater(p)) {
			anim(p, ANIM_WHIRL);
			Vec3 at = p.position().add(p.getLookAngle().scale(4));
			WHIRLS.put(p.getUUID(), new Whirl(l.dimension(), at, l.getGameTime() + WHIRL_TICKS));
			QuirkFx.ring(l, at).colours(0xE0F4FF, 0x2A7AD0).style(QuirkFx.RING_SOFT).size(WHIRL_RADIUS).thickness(0.8f).life(WHIRL_TICKS / 3).spawn();
			OmnitrixSounds.play(l, at, WHIRL, 1.2f, 0.8f);
			return true;
		}
		anim(p, ANIM_GNASH);
		setTimer(p, "gnash_start", l.getGameTime());
		gnashBite(p, l);
		return true;
	}

	/** One of Gnash's bites: everything in the cone ahead (3.5 blocks, 45 degrees either side). */
	private void gnashBite(ServerPlayer p, ServerLevel l) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		OmnitrixSounds.play(l, p.position(), BITE_SOUND, 0.7f, 1.1f + p.getRandom().nextFloat() * 0.2f);
		for (LivingEntity e : around(p, eye.add(look.scale(GNASH_RANGE * 0.5)), GNASH_RANGE)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= GNASH_RANGE + e.getBbWidth() * 0.5 && (to.lengthSqr() < 0.5 || to.normalize().dot(look) > Math.cos(Math.toRadians(45)))) {
				chomp(p, e, GNASH_DAMAGE, STEEL_JAWS);
			}
		}
	}

	private void tickGnash(ServerPlayer p, ServerLevel l, long now) {
		long start = timer(p, "gnash_start");
		if (start <= 0) {
			return;
		}
		long age = now - start;
		if (age > 0 && age % GNASH_EVERY == 0 && age <= (long) GNASH_EVERY * (GNASH_BITES - 1)) {
			gnashBite(p, l);
		}
		if (age >= (long) GNASH_EVERY * (GNASH_BITES - 1)) {
			setTimer(p, "gnash_start", 0);
		}
	}

	/**
	 * Shift+Z: Megamouth, his ultimate -- v0.17.2 revamp (user: "Drop tidal roar"). His jaw unhinges over 0.75 s (in water the bite
	 * swallows the current: everything within 6 is pulled toward his mouth), then one colossal chomp in a 4-block cone ahead: 30
	 * (x1.25 in water), all armour ignored; he heals a quarter of the damage dealt (up to 8) and each kill refills 30 s of
	 * hydration.
	 */
	public boolean megamouth(ServerPlayer p) {
		if (timer(p, "mega_at") > 0 || !use(p, MEGAMOUTH)) {
			return false;
		}
		anim(p, ANIM_MEGA);
		ServerLevel l = level(p);
		setTimer(p, "mega_at", l.getGameTime() + MEGA_WINDUP);
		OmnitrixSounds.play(l, p.position(), ROAR, 1.3f, 0.6f);
		return true;
	}

	private void tickMegamouth(ServerPlayer p, ServerLevel l, long now) {
		long at = timer(p, "mega_at");
		if (at <= 0) {
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 mouth = eye.add(look.scale(1.0));
		if (now < at) {
			if (inWater(p)) {
				// the current pours into his mouth
				for (LivingEntity e : around(p, mouth, MEGA_PULL)) {
					Vec3 to = mouth.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
					if (to.lengthSqr() > 1.0) {
						double res = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
						e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(0.35 * res)));
						e.hurtMarked = true;
					}
				}
			}
			return;
		}
		setTimer(p, "mega_at", 0);
		OmnitrixSounds.play(l, p.position(), BITE_SOUND, 1.6f, 0.5f);
		OmnitrixSounds.play(l, p.position(), SLAM, 1.0f, 0.7f);
		QuirkCamera.shakeAround(l, mouth, 12, 0.5f, 8);
		float dealt = 0f;
		int kills = 0;
		for (LivingEntity e : around(p, eye.add(look.scale(MEGA_RANGE * 0.5)), MEGA_RANGE)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() > MEGA_RANGE + e.getBbWidth() * 0.5 || to.lengthSqr() > 0.5 && to.normalize().dot(look) < Math.cos(Math.toRadians(55))) {
				continue;
			}
			float before = e.getHealth();
			chomp(p, e, MEGA_DAMAGE, 1f);
			dealt += Math.max(0f, before - Math.max(0f, e.getHealth()));
			if (!e.isAlive() || e.getHealth() <= 0f) {
				kills++;
			}
			QuirkThreat.add(e, p, 3f);
		}
		if (dealt > 0f) {
			p.heal(Math.min(MEGA_HEAL_MAX, dealt * MEGA_HEAL_SHARE));
		}
		if (kills > 0) {
			setDryness(p, dryness(p, now) - kills * MEGA_KILL_REFILL, inWater(p), inRain(p) || flag(p, F_BUBBLE));
		}
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** C: Feeding Frenzy -- 10 s: x1.3 damage, faster, bites heal 3; v0.17.2 Blood Scent: the wounded (under half) outlined red. */
	public boolean feedingFrenzy(ServerPlayer p) {
		if (!use(p, FRENZY)) {
			return false;
		}
		anim(p, ANIM_FRENZY);
		ServerLevel l = level(p);
		setTimer(p, "frenzy_end", l.getGameTime() + FRENZY_TICKS);
		setFlag(p, F_FRENZY, true);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, FRENZY_FIST, 3.0, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, FRENZY_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		OmnitrixSounds.play(l, p.position(), ROAR, 1.2f, 0.7f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFB0A0, 0xC02020).size(3f).thickness(0.5f).life(12).ground().spawn();
		return true;
	}

	private void endFrenzy(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, FRENZY_FIST);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, FRENZY_SPEED);
		if (timer(p, "frenzy_end") > 0) {
			setTimer(p, "frenzy_end", 0);
		}
		setFlag(p, F_FRENZY, false);
	}

	public boolean drench(ServerPlayer p) {
		if (!use(p, DRENCH)) {
			return false;
		}
		anim(p, ANIM_DRENCH);
		ServerLevel l = level(p);
		long now = l.getGameTime();
		setDryness(p, dryness(p, now) - MAX_DRY / 2, inWater(p), inRain(p));
		p.clearFire();
		l.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + p.getBbHeight(), p.getZ(), 24, 0.5, 0.3, 0.5, 0.3);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0x3A9AE0).style(QuirkFx.RING_STEAM).size(2.5f).thickness(0.5f).life(12).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SPLASH, 1.2f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity) {
			anim(p, ANIM_PUNCH);
		}
		return false;
	}

	/** What his claws dig straight through. */
	public static boolean diggable(BlockState s) {
		return s.is(BlockTags.SAND) || s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.SOUL_SAND)
				|| s.is(Blocks.SOUL_SOIL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH)
				|| s.is(Blocks.MUDDY_MANGROVE_ROOTS) || s.is(BlockTags.CONCRETE_POWDER);
	}

	/** v0.17.2 Steel Jaws: the metal his jaws bite straight through -- iron bars, chains, iron doors and trapdoors. */
	public static boolean biteable(BlockState s) {
		return s.is(Blocks.IRON_BARS) || s.is(Blocks.CHAIN) || s.is(Blocks.IRON_DOOR) || s.is(Blocks.IRON_TRAPDOOR);
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		return dig(p, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** Use on sand, gravel, dirt... or iron bars, chains, iron doors (v0.17.2): it is gone (and drops), one block per use. */
	public boolean dig(ServerPlayer p, BlockPos pos) {
		ServerLevel l = level(p);
		BlockState s = l.getBlockState(pos);
		boolean metal = biteable(s);
		if (!diggable(s) && !metal || !p.mayInteract(l, pos) || !p.mayBuild() || cooldownLeft(p, DIG) > 0) {
			return false;
		}
		if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			pos = pos.below(); // the lower half drops the door
			s = l.getBlockState(pos);
		}
		cooldown(p, DIG, -1);
		anim(p, metal ? ANIM_BITE : ANIM_DIG);
		l.destroyBlock(pos, !p.isCreative(), p);
		if (metal) {
			OmnitrixSounds.play(l, Vec3.atCenterOf(pos), BITE_SOUND, 0.9f, 0.7f);
		}
		p.swing(InteractionHand.MAIN_HAND, true);
		return true;
	}

	// ---------------------------------------------------------------- damage

	/** He breathes water: never drowns. */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_DROWNING);
	}

	// ---------------------------------------------------------------- v0.17.1 (user: "add abilities to the V key for every alien"): V Water Bubble

	public static final String BUBBLE = "water_bubble";
	public static final int ANIM_BUBBLE = 15;
	public static final int F_BUBBLE = 32;
	public static final int BUBBLE_TICKS = 600;
	// v0.17.2 revamp (user: "Do lure light i suppose"): Shift+V Lure Light
	public static final String LURE = "lure_light";
	public static final float LURE_RANGE = 12f;
	/** The 3 s cooldown once it is switched off. */
	public static final int LURE_COOLDOWN = 60;
	private static final int LURE_VISION_TICKS = 260;

	private void vKey(ServerPlayer p, boolean shift) {
		if (shift) {
			lureLight(p);
		} else {
			waterBubble(p);
		}
	}

	/**
	 * V: Water Bubble -- he gulps a bubble of water round his head: for 30 s dry land is like rain to him (the hydration meter
	 * refills slowly instead of draining), so he can hunt far from the sea.
	 */
	public boolean waterBubble(ServerPlayer p) {
		if (flag(p, F_BUBBLE) || !use(p, BUBBLE)) {
			return false;
		}
		setFlag(p, F_BUBBLE, true);
		setTimer(p, "bubble_end", p.level().getGameTime() + BUBBLE_TICKS);
		anim(p, ANIM_BUBBLE);
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getEyeY(), p.getZ(), 16, 0.3, 0.3, 0.3, 0.1);
		OmnitrixSounds.play(l, p.position(), SPLASH, 1.0f, 1.4f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ripjaws.bubble_on").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	/**
	 * Shift+V: Lure Light -- v0.17.2 revamp (user: "Do lure light i suppose"; the Piscciss Volann's luminescent lure). A toggle:
	 * a glowing lure sprouts from his head. He sees in the dark; fish, squid and axolotls within 12 swim to him; hostile mobs that
	 * see the light come for him (a taunt). While it is on he dries out a quarter faster on land. 3 s cooldown after switching off.
	 */
	public boolean lureLight(ServerPlayer p) {
		ServerLevel l = level(p);
		if (flag(p, F_LURE)) {
			lureOff(p);
			cooldown(p, LURE, -1);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ripjaws.lure_off").withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		int left = cooldownLeft(p, LURE);
		if (left > 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cooldown", Component.translatable("projecthero.omnitrix." + ID + "." + LURE),
					String.format(java.util.Locale.ROOT, "%.1f", left / 20f)).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		setRateFlag(p, F_LURE, true);
		anim(p, ANIM_LURE);
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LURE_VISION_TICKS, 0, true, false, true));
		OmnitrixSounds.play(l, p.position(), SPLASH, 0.6f, 1.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ripjaws.lure_on").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	private void lureOff(ServerPlayer p) {
		setRateFlag(p, F_LURE, false);
		MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
		if (nv != null && nv.isAmbient() && !nv.isVisible() && nv.getDuration() <= LURE_VISION_TICKS) {
			p.removeEffect(MobEffects.NIGHT_VISION); // only the lure's own, never a real potion
		}
	}

	private void revertV(ServerPlayer p) {
		setFlag(p, F_BUBBLE, false);
		setTimer(p, "bubble_end", 0);
		if (flag(p, F_LURE)) {
			lureOff(p);
		}
	}

	private void tickV(ServerPlayer p, ServerLevel l, long now) {
		if (flag(p, F_LURE)) {
			tickLure(p, l, now);
		}
		if (!flag(p, F_BUBBLE)) {
			return;
		}
		if (now >= timer(p, "bubble_end")) {
			setFlag(p, F_BUBBLE, false);
			setTimer(p, "bubble_end", 0);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ripjaws.bubble_off").withStyle(ChatFormatting.GRAY), true);
		}
	}

	private void tickLure(ServerPlayer p, ServerLevel l, long now) {
		MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
		if (nv == null || nv.isAmbient() && nv.getDuration() < LURE_VISION_TICKS - 20) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LURE_VISION_TICKS, 0, true, false, true));
		}
		if (now % 10 != 0) {
			return;
		}
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(LURE_RANGE))) {
			if (!m.isAlive() || m.distanceToSqr(p) > LURE_RANGE * LURE_RANGE) {
				continue;
			}
			if (m instanceof WaterAnimal || m instanceof Axolotl) {
				// drawn to the light
				if (m.distanceToSqr(p) > 4.0) {
					m.getNavigation().moveTo(p, 1.2);
				}
			} else if (m instanceof Enemy && m.hasLineOfSight(p) && !com.projecthero.mod.squad.Squads.areAllies(p, m)) {
				// the taunt: whatever sees the light comes for him (a boss through its threat table)
				if (AlienStatus.isBoss(m)) {
					QuirkThreat.add(m, p, 2f);
				} else if (m.getTarget() != p && !p.getAbilities().invulnerable) {
					m.setTarget(p); // (never a creative player: nothing can hurt him anyway)
				}
			}
		}
	}

	/** v0.17.2: the HUD's V box lights while the Lure Light is on. */
	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_5 && flag(p, F_LURE);
	}
}
