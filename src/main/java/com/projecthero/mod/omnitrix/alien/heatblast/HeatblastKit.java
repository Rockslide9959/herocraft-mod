package com.projecthero.mod.omnitrix.alien.heatblast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.p08.PyroFxEntity;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Heatblast, the Pyronite -- a magma-rock body with a flame for a head.
 *
 * <p>v0.17.2 (user-approved revamp, the show-accurate kit):
 *
 * <pre>
 *   Passive  Pyronite Body     immune to fire and lava; standing in fire or lava heals 1 heart a second; punches set enemies
 *                              alight (+2); snowballs hurt him (3)
 *   R        Fireball Volley   three quick fireballs (4 each, 3 s alight)
 *   Shift+R  Flamethrower      HOLD, up to 3 s: a stream from both hands (2 every 4 ticks, lights the ground)
 *   G        Fire Lasso        a whip of flame grabs the creature you look at (16 blocks) and yanks it to you (6, 5 s alight)
 *   Shift+G  Magma Bomb        lobbed molten rock: 6 direct, a 3.5-block blast (12), then a burning patch for 4 s (2 a half second)
 *   X        Flame Jump        a jet of fire launches him up and forward
 *   Hold X   Rock Ride         2 s charge: punch out a chunk of the ground and surf it through the air where you look, up to
 *                              1 minute; X again or sneak drops off (no fall damage)
 *   Shift+X  Blaze Dash        a ground burst leaving a fire trail, scorching what it passes (8)
 *   Z        Fire Tornado      a spinning column of flame where you look: pulls creatures in, burns them for 4 s (up to 18)
 *   Shift+Z  Supernova         ULTIMATE: a 1.25 s charge, then a huge blast (30, 18 at the edge) that leaves a ring of fire
 *   V        Heat Aura         toggle: creatures near catch fire, snow and ice melt, dropped items smelt
 *   Shift+V  Heat Shield       5 s dome of heat: 60% less damage, melts arrows and fireballs, burns attackers
 *   C        Volcanic Eruption fist into the ground: fire geysers burst up in a line ahead, launching enemies (12)
 *   Shift+C  Meteor Crash      wrapped in fire he leaps up and crashes down: a 5-block fiery impact (16)
 *   Use      empty hand: a small fire bolt (5); on a block: melts it; Shift + an item: smelts up to 8 of it
 * </pre>
 *
 * <p>Doused (user: "when he comes into contact with water hes put out, after getting out of water it takes him 10 seconds to
 * reignite, during that time he cant use his powers"): water -- in it, wading, swimming, a splash water potion; never rain --
 * puts his fire out. No moves, no burning fist or fire bolt, the aura and a running Rock Ride end; out of the water a 10 s
 * countdown relights him (getting wet again restarts it), and stepping into fire or lava relights him at once. Fire and lava
 * still cannot hurt him. The client swaps in the charred skin and puts the head flame out ({@code HeatblastClient}).
 */
public final class HeatblastKit extends AlienKit {
	public static final String ID = "heatblast";
	public static final int COLOUR = 0xFF7A1A;

	public static final int ANIM_VOLLEY = 1;
	public static final int ANIM_THROWER = 2;
	public static final int ANIM_LASSO = 3;
	public static final int ANIM_MAGMA = 4;
	public static final int ANIM_JUMP = 5;
	/** Hold X: crouched, the fist raised and burning (the Rock Ride's 2 s charge). */
	public static final int ANIM_RIDE_CHARGE = 6;
	/** ...the fist into the ground as the rock rips out. */
	public static final int ANIM_RIDE_PUNCH = 7;
	public static final int ANIM_DASH = 8;
	public static final int ANIM_TORNADO = 9;
	public static final int ANIM_CHARGE = 10;
	public static final int ANIM_SUPERNOVA = 11;
	public static final int ANIM_PUNCH = 12;
	public static final int ANIM_AURA = 13;
	public static final int ANIM_BOLT = 14;
	public static final int ANIM_SMELT = 15;
	public static final int ANIM_SHIELD = 16;
	public static final int ANIM_ERUPT = 17;
	public static final int ANIM_METEOR_RISE = 18;
	public static final int ANIM_METEOR_DIVE = 19;
	/** Put out: a flinch, shaking the water off. */
	public static final int ANIM_DOUSED = 20;
	/** The flames burst back: arms flung wide. */
	public static final int ANIM_REIGNITE = 21;
	public static final int ANIM_YANK = 22;
	/** The volley's second fireball, from the left hand. */
	public static final int ANIM_VOLLEY_L = 23;

	public static final int F_RIDING = 1;
	public static final int F_SHIELD = 2;
	public static final int F_STREAM = 4;
	public static final int F_CHARGING = 8;
	public static final int F_AURA = 16;
	public static final int F_DOUSED = 32;
	/** Doused and still in the water (the reignite countdown waits). */
	public static final int F_WET = 64;
	public static final int F_RIDE_CHARGE = 128;
	public static final int F_TORNADO = 256;
	public static final int F_METEOR = 512;
	public static final int F_LASSO = 1024;
	public static final int F_DASH = 2048;

	public static final String VOLLEY = "fireball_volley";
	public static final String THROWER = "flamethrower";
	public static final String LASSO = "fire_lasso";
	public static final String MAGMA = "magma_bomb";
	public static final String JUMP = "flame_jump";
	public static final String RIDE = "rock_ride";
	public static final String DASH = "blaze_dash";
	public static final String TORNADO = "fire_tornado";
	public static final String SUPERNOVA = "supernova";
	public static final String HEAT_AURA = "heat_aura";
	public static final String HEAT_SHIELD = "heat_shield";
	public static final String ERUPTION = "volcanic_eruption";
	public static final String METEOR = "meteor_crash";
	public static final String BODY = "pyronite_body";
	public static final String FIRE_BOLT = "fire_bolt";
	public static final String SMELT = "smelt";

	// v0.17.2 (user: "adjust the other combat aliens damage aswell please to try and match this"): on Four Arms / Diamondhead's scale -- quick hits 5-8, moves 12-17, big ones 17-20, ultimates 21-36
	public static final float VOLLEY_DAMAGE = 5f;
	public static final int VOLLEY_SHOTS = 3, VOLLEY_GAP = 3;
	public static final float THROWER_DAMAGE = 2f;
	public static final int THROWER_TICKS = 60, THROWER_PULSE = 4;
	public static final double THROWER_RANGE = 7.0;
	public static final float LASSO_DAMAGE = 9f;
	public static final double LASSO_RANGE = 16.0;
	public static final int LASSO_TICKS = 10;
	public static final float MAGMA_DAMAGE = 15f;
	public static final int PATCH_TICKS = 80;
	public static final float PATCH_RADIUS = 2.5f, PATCH_DAMAGE = 2f;
	/** The Rock Ride: X held this long from the press (2 s), how long the ride lasts (1 minute), and its speeds (blocks a tick). */
	public static final int RIDE_CHARGE_TICKS = 40, RIDE_TICKS = 1200;
	// v0.17.2 (user: "speed up heatblasts rock ride ability to 25 blocks per second"): cruising 1.25 (was 0.8), forward 1.6 (was 1.3)
	public static final double RIDE_SPEED = 1.25, RIDE_BOOST = 1.6, RIDE_BRAKE = 0.35;
	public static final float DASH_DAMAGE = 10f;
	public static final int DASH_TICKS = 10;
	public static final double DASH_SPEED = 1.15;
	public static final float TORNADO_DAMAGE = 2f;
	public static final int TORNADO_TICKS = 80;
	public static final double TORNADO_RANGE = 24.0, TORNADO_PULL = 6.0;
	public static final float SUPERNOVA_RADIUS = 9f, SUPERNOVA_DAMAGE = 34f, SUPERNOVA_EDGE_DAMAGE = 20f;
	public static final int SUPERNOVA_CHARGE = 25;
	public static final float FIRE_RING_RADIUS = 7.5f, FIRE_RING_DAMAGE = 3f;
	public static final int FIRE_RING_TICKS = 80;
	public static final int SHIELD_TICKS = 100;
	public static final float SHIELD_FACTOR = 0.4f;
	public static final float ERUPT_DAMAGE = 16f;
	public static final int ERUPT_GEYSERS = 8;
	public static final double ERUPT_SPACING = 1.6;
	public static final float METEOR_DAMAGE = 20f, METEOR_RADIUS = 5f;
	public static final double METEOR_RISE = 1.35;
	public static final float BOLT_DAMAGE = 5f;
	public static final float PUNCH_FIRE = 2f;
	public static final float SNOWBALL_DAMAGE = 3f;
	/** Out of the water, this long (10 s) until his fire is back. */
	public static final int REIGNITE_TICKS = 200;
	/** Standing in fire or lava: this much back (a heart) every second. */
	public static final float FIRE_HEAL = 2f;
	public static final double AURA_RADIUS = 3.5;
	public static final float AURA_DAMAGE = 1f;

	/** An item the aura already smelted (so cobblestone stops at stone). */
	private static final String AURA_TAG = "projecthero_heat_aura";

	/** The Flamethrower's flame, the volley's / bomb's throw (the shots are Pyrokinesis' fireballs, HeatblastClient). */
	public static final OmnitrixSfx.Cue SND_LASSO = OmnitrixSfx.cue("heatblast_lasso")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.4f, 0.6f)
			.layer(SoundEvents.BLAZE_SHOOT, 0.3f, 1.3f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.3f, 1.1f, 2);
	/** The Rock Ride charging: a low rumble under him. */
	public static final OmnitrixSfx.Cue SND_RUMBLE = OmnitrixSfx.cue("heatblast_rumble")
			.layer(SoundEvents.BASALT_BREAK, 0.35f, 0.5f)
			.layer(SoundEvents.FIRE_AMBIENT, 0.4f, 0.6f)
			.gap(4);
	/** The rock ripping out of the ground. */
	public static final OmnitrixSfx.Cue SND_RIP = OmnitrixSfx.cue("heatblast_rip")
			.layer(SoundEvents.DEEPSLATE_BREAK, 0.6f, 0.6f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.3f, 1.4f)
			.layer(SoundEvents.BLAZE_SHOOT, 0.4f, 0.6f, 1)
			.layer(SoundEvents.GRAVEL_BREAK, 0.45f, 0.6f, 2);
	/** The rock in flight: a roaring flame under it (once every 16 ticks). */
	public static final OmnitrixSfx.Cue SND_RIDE_LOOP = OmnitrixSfx.cue("heatblast_ride_loop")
			.layer(SoundEvents.BLAZE_BURN, 0.28f, 0.7f)
			.layer(SoundEvents.FIRE_AMBIENT, 0.35f, 0.6f, 4)
			.jitter(0.08f).gap(12);
	/** The rock crumbling when he drops off. */
	public static final OmnitrixSfx.Cue SND_CRUMBLE = OmnitrixSfx.cue("heatblast_crumble")
			.layer(SoundEvents.GRAVEL_BREAK, 0.6f, 0.7f)
			.layer(SoundEvents.STONE_BREAK, 0.45f, 0.6f, 1)
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.2f, 0.9f, 2);
	/** A geyser of fire. */
	public static final OmnitrixSfx.Cue SND_GEYSER = OmnitrixSfx.cue("heatblast_geyser")
			.layer(SoundEvents.BLAZE_SHOOT, 0.4f, 0.55f)
			.layer(SoundEvents.LAVA_POP, 0.6f, 0.8f)
			.layer(SoundEvents.BASALT_BREAK, 0.3f, 0.6f)
			.gap(2);
	/** The Fire Tornado's roar (once a second while it spins). */
	public static final OmnitrixSfx.Cue SND_TORNADO = OmnitrixSfx.cue("heatblast_tornado")
			.layer(SoundEvents.BLAZE_BURN, 0.5f, 0.5f)
			.layer(SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.2f, 0.5f)
			.layer(SoundEvents.FIRE_AMBIENT, 0.6f, 0.7f, 6)
			.gap(10);
	/** Water on fire: a hiss and a puff of steam. */
	public static final OmnitrixSfx.Cue SND_HISS = OmnitrixSfx.cue("heatblast_hiss")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.8f)
			.layer(SoundEvents.LAVA_EXTINGUISH, 0.45f, 1.1f, 1)
			.layer(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.4f, 0.7f, 3)
			.gap(10);
	/** The flames bursting back: a whoosh and a crackle. */
	public static final OmnitrixSfx.Cue SND_REIGNITE = OmnitrixSfx.cue("heatblast_reignite")
			.layer(SoundEvents.FIRECHARGE_USE, 0.55f, 0.6f)
			.layer(SoundEvents.BLAZE_SHOOT, 0.45f, 0.8f, 1)
			.layer(SoundEvents.FIRE_AMBIENT, 0.6f, 1.0f, 3);
	/** The Meteor Crash's impact. */
	public static final OmnitrixSfx.Cue SND_CRASH = OmnitrixSfx.cue("heatblast_crash")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.55f, 0.7f)
			.layer(SoundEvents.BLAZE_SHOOT, 0.4f, 0.5f)
			.layer(SoundEvents.BASALT_BREAK, 0.5f, 0.5f, 1)
			.layer(SoundEvents.FIRE_AMBIENT, 0.6f, 0.8f, 4);

	/**
	 * v0.17.2 (user: "change heatblasts landing sound effect, it sounds weird, make it slightly softer too please"): the
	 * touchdown -- a soft, muffled flame whoosh over a light thud of his rock body, the flames crackling out after.
	 */
	public static final OmnitrixSfx.Cue LAND = OmnitrixSfx.cue("heatblast_land")
			.layer(SoundEvents.BLAZE_SHOOT, 0.22f, 0.55f)
			.layer(SoundEvents.BASALT_FALL, 0.3f, 0.75f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.12f, 0.6f)
			.layer(SoundEvents.FURNACE_FIRE_CRACKLE, 0.18f, 1.1f, 3)
			.jitter(0.05f).gap(6);

	/** A glowing orange ember and a paler, smaller spark (dust: they hang and drift instead of rising like flames). */
	static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0f, 0.48f, 0.08f), 1.0f);
	static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.42f), 0.6f);

	/** player -> the Magma Bomb patches burning where they landed. */
	private static final Map<UUID, List<Patch>> PATCHES = new ConcurrentHashMap<>();
	/** player -> creatures the current Blaze Dash already scorched. */
	private static final Map<UUID, Set<Integer>> DASH_HITS = new ConcurrentHashMap<>();
	/** player -> creatures the current Volcanic Eruption already launched, and where it runs. */
	private static final Map<UUID, Eruption> ERUPTIONS = new ConcurrentHashMap<>();
	/** player -> snowballs and water potions near him last tick (and where they were heading), to catch the ones that hit. */
	private static final Map<UUID, Map<Projectile, Vec3[]>> WATCHED = new ConcurrentHashMap<>();
	/** player -> the game tick his kit last ran (a second call in the same tick is skipped). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private record Patch(ResourceKey<Level> dim, Vec3 at, long end) {
	}

	private record Eruption(Vec3 from, Vec3 dir, long start, ResourceKey<Level> dim, Set<Integer> hits) {
	}

	private HeatblastKit() {
	}

	public static void register() {
		Aliens.register(new HeatblastKit());
		AlienProjectileEntity.onHit("heatblast_bomb", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				// it bursts like Pyrokinesis' Meteor and leaves a pool of burning magma
				PyrokinesisHandlers.meteorBlastFx(l, proj.getOwner() instanceof ServerPlayer sp ? sp : null, at, 3.5);
				OmnitrixSounds.play(l, at, OmnitrixSounds.HEATBLAST_NOVA, 1.2f, 1.2f);
				QuirkCamera.shakeAround(l, at, 12, 0.4f, 8);
				if (proj.getOwner() instanceof ServerPlayer sp) {
					magmaPatch(sp, l, at);
				}
			}
		});
		AlienProjectileEntity.onHit("heatblast_blast", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				PyrokinesisHandlers.fireballImpactFx(l, hit.getLocation(), 0.8f);
			}
		});
		AlienProjectileEntity.onHit("heatblast_bolt", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				PyrokinesisHandlers.fireballImpactFx(l, hit.getLocation(), 0.5f);
			}
		});
		AlienProjectileEntity.trail("heatblast_bomb", ParticleTypes.LAVA);
	}

	/** The superhero landing ({@code HeroLandingFx}, which draws the shared shockwave): his sound and embers. */
	public static void heroLanding(ServerPlayer p, ServerLevel l, float s) {
		OmnitrixSounds.play(l, p.position(), LAND, 0.7f + 0.3f * s, 1.0f);
		embers(l, p.position().add(0, 0.2, 0), 6 + (int) (6 * s), 0.6, 0.08);
	}

	/** A pop of embers: orange dust, sparks, small flames and (big enough) a lava spit. */
	static void embers(ServerLevel l, Vec3 at, int n, double spread, double speed) {
		l.sendParticles(EMBER, at.x, at.y, at.z, n, spread, spread * 0.6, spread, speed);
		l.sendParticles(SPARK, at.x, at.y, at.z, Math.max(1, n / 2), spread, spread * 0.6, spread, speed);
		l.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, Math.max(1, n / 2), spread, spread * 0.6, spread, speed * 0.5);
		if (n >= 6) {
			l.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, Math.max(1, n / 6), spread * 0.5, 0.1, spread * 0.5, 0);
		}
	}

	public static void clearSessionState() {
		PATCHES.clear();
		DASH_HITS.clear();
		ERUPTIONS.clear();
		WATCHED.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 (user: "give heatblast iron level armour aswell as xlr8"): 15 armour, an iron set
		return AlienStats.builder().health(26f).speed(1.05f).armor(15f, 0f).attack(2f).knockbackResist(0.1f).safeFall(4f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(VOLLEY, AbilitySlot.SLOT_1, 40), AlienMove.shift(THROWER, AbilitySlot.SLOT_1, 140),
				AlienMove.tap(LASSO, AbilitySlot.SLOT_2, 100), AlienMove.shift(MAGMA, AbilitySlot.SLOT_2, 160),
				AlienMove.tap(JUMP, AbilitySlot.SLOT_3, 30), AlienMove.hold(RIDE, AbilitySlot.SLOT_3, 200),
				AlienMove.shift(DASH, AbilitySlot.SLOT_3, 80),
				AlienMove.tap(TORNADO, AbilitySlot.SLOT_4, 260), AlienMove.shift(SUPERNOVA, AbilitySlot.SLOT_4, 600),
				AlienMove.tap(HEAT_AURA, AbilitySlot.SLOT_5, 60), AlienMove.shift(HEAT_SHIELD, AbilitySlot.SLOT_5, 300),
				AlienMove.tap(ERUPTION, AbilitySlot.SLOT_6, 200), AlienMove.shift(METEOR, AbilitySlot.SLOT_6, 300),
				// the vanilla keys' moves (no box on the HUD)
				AlienMove.vanilla(BODY, "Passive / Attack", 10), AlienMove.vanilla(FIRE_BOLT, "Use", 10),
				AlienMove.vanilla(SMELT, "Use on a block / Shift+Use with an item", 20));
	}

	// ---------------------------------------------------------------- client-safe state

	private static boolean is(Player p) {
		return Omnitrix.hasPower(p) && ID.equals(Omnitrix.state(p).active) && Omnitrix.state(p).transformed();
	}

	/** Heatblast surfing his rock right now (any side: the synced flags). */
	public static boolean riding(Player p) {
		return is(p) && (Omnitrix.state(p).kitFlags & F_RIDING) != 0;
	}

	/** Heatblast put out by water right now (any side). */
	public static boolean doused(Player p) {
		return is(p) && (Omnitrix.state(p).kitFlags & F_DOUSED) != 0;
	}

	/** Ticks until his flames come back (0 while still in the water, or not doused). Client-safe. */
	public int reigniteLeft(Player p) {
		long at = timer(p, "reignite_at");
		return at <= 0 || flag(p, F_WET) ? 0 : (int) Math.max(0L, at - p.level().getGameTime());
	}

	/** The Rock Ride's charge 0..1 while X is held (client-safe), 0 when none. */
	public float rideCharge(Player p) {
		long at = timer(p, "ride_charge");
		if (at <= 0 || !flag(p, F_RIDE_CHARGE)) {
			return 0f;
		}
		return Math.min(1f, (p.level().getGameTime() - at) / (float) RIDE_CHARGE_TICKS);
	}

	/** Ticks left on the Rock Ride (client-safe). */
	public int rideLeft(Player p) {
		long end = timer(p, "ride_end");
		return end <= 0 ? 0 : (int) Math.max(0L, end - p.level().getGameTime());
	}

	/** The block the Rock Ride's rock is made of (client-safe), stone if unknown. */
	public BlockState rideBlock(Player p) {
		long id = timer(p, "ride_block");
		BlockState s = id > 0 ? Block.stateById((int) (id - 1)) : Blocks.AIR.defaultBlockState();
		return s.isAir() ? Blocks.STONE.defaultBlockState() : s;
	}

	/** Where the Fire Tornado spins (client-safe), or null. */
	public Vec3 tornadoAt(Player p) {
		if (!flag(p, F_TORNADO) || timer(p, "tor_x") == 0) {
			return null;
		}
		return new Vec3(unbits(timer(p, "tor_x")), unbits(timer(p, "tor_y")), unbits(timer(p, "tor_z")));
	}

	/** The creature the Fire Lasso has hold of (client-safe: its entity id), or -1. */
	public int lassoTarget(Player p) {
		long id = timer(p, "lasso_id");
		return flag(p, F_LASSO) && id > 0 ? (int) (id - 1) : -1;
	}

	/** A coordinate as a kit timer, which must stay positive (setTimer drops anything <= 0): thousandths, offset. */
	private static final long OFFSET = 1L << 40;

	private static long bits(double v) {
		return OFFSET + Math.round(v * 1000.0);
	}

	private static double unbits(long b) {
		return (b - OFFSET) / 1000.0;
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_IGNITE, 1.0f, 1.0f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFF0B0, COLOUR).size(3.5f).thickness(0.6f).life(16).ground().spawn();
		p.clearFire();
	}

	@Override
	public void onRevert(ServerPlayer p) {
		stopEverything(p);
		for (int f : new int[] { F_DOUSED, F_WET, F_TORNADO }) {
			setFlag(p, f, false);
		}
		for (String t : new String[] { "reignite_at", "tor_x", "tor_y", "tor_z", "tor_end", "nofall_until", "nofall_from", "volley_start", "ring_end", "ring_x", "ring_y",
				"ring_z", "hiss_at" }) {
			setTimer(p, t, 0);
		}
		PATCHES.remove(p.getUUID());
		ERUPTIONS.remove(p.getUUID());
		WATCHED.remove(p.getUUID());
		LAST_TICK.remove(p.getUUID());
	}

	/** Ends every running move (the doused state and a revert): aura, stream, shield, charges, lasso, dash, meteor, the ride. */
	private void stopEverything(ServerPlayer p) {
		for (int f : new int[] { F_AURA, F_STREAM, F_SHIELD, F_CHARGING, F_RIDE_CHARGE, F_LASSO, F_DASH, F_METEOR }) {
			setFlag(p, f, false);
		}
		for (String t : new String[] { "stream_end", "shield_end", "supernova_at", "ride_charge", "lasso_id", "lasso_at", "dash_end", "meteor_at",
				"meteor_dive" }) {
			setTimer(p, t, 0);
		}
		DASH_HITS.remove(p.getUUID());
		endRide(p, true);
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_STREAM);
			case SLOT_3 -> flag(p, F_RIDING) || flag(p, F_RIDE_CHARGE);
			case SLOT_4 -> flag(p, F_TORNADO) || flag(p, F_CHARGING);
			case SLOT_5 -> flag(p, F_AURA) || flag(p, F_SHIELD);
			default -> false;
		};
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		p.clearFire();
		if (p.isInLava()) {
			p.resetFallDistance();
		}
		watchProjectiles(p, l);
		tickDouse(p, l, now);
		boolean out = flag(p, F_DOUSED);
		// the flame head and the embers (others see the client-side flames; this is the server's share) -- smoke when doused
		if (p.tickCount % 6 == 0) {
			l.sendParticles(out ? ParticleTypes.SMOKE : ParticleTypes.FLAME, p.getX(), p.getY() + p.getBbHeight() + 0.1, p.getZ(), 1, 0.12, 0.05, 0.12,
					0.01);
		}
		if (now % 20 == 0 && inFireOrLava(p) && p.getHealth() < p.getMaxHealth()) {
			// Pyronite Body: fire and lava feed him
			p.heal(FIRE_HEAL);
			l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.6, 0.3, 0.02);
		}
		tickAura(p, l, now);
		tickVolley(p, l, now);
		tickThrower(p, l, now);
		tickLasso(p, l, now);
		tickPatches(p, l, now);
		tickRideCharge(p, l, now);
		tickRide(p, l, now);
		tickDash(p, l, now);
		tickTornado(p, l, now);
		tickSupernova(p, l, now);
		tickFireRing(p, l, now);
		tickShield(p, l, now);
		tickEruption(p, l, now);
		tickMeteor(p, l, now);
		tickNoFall(p, now);
	}

	// ---------------------------------------------------------------- the passive: water, snowballs, fire

	/** Water touching any part of him (in it, wading, swimming); never rain. Block-based, so it works on any player. */
	public static boolean touchingWater(Player p) {
		if (p.isInWaterOrBubble()) {
			return true;
		}
		AABB box = p.getBoundingBox().deflate(0.001);
		Level l = p.level();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			var fluid = l.getFluidState(pos);
			if (fluid.is(FluidTags.WATER) && pos.getY() + fluid.getHeight(l, pos) > box.minY) {
				return true;
			}
		}
		return false;
	}

	/** Fire or lava where he stands. */
	public static boolean inFireOrLava(Player p) {
		if (p.isInLava()) {
			return true;
		}
		AABB box = p.getBoundingBox().deflate(0.001);
		Level l = p.level();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.minY + 0.9, box.maxZ))) {
			BlockState st = l.getBlockState(pos);
			if (st.is(BlockTags.FIRE) || st.getFluidState().is(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	private void tickDouse(ServerPlayer p, ServerLevel l, long now) {
		boolean wet = touchingWater(p);
		if (wet) {
			if (!flag(p, F_DOUSED)) {
				douse(p, true);
			}
			if (!flag(p, F_WET)) {
				setFlag(p, F_WET, true);
				setTimer(p, "reignite_at", 0);
			}
			if (now % 8 == 0) {
				l.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.3, 0.5, 0.3, 0.05);
			}
			return;
		}
		if (flag(p, F_WET)) {
			// out of the water: the countdown starts
			setFlag(p, F_WET, false);
			setTimer(p, "reignite_at", now + REIGNITE_TICKS);
		}
		if (!flag(p, F_DOUSED)) {
			return;
		}
		long at = timer(p, "reignite_at");
		if (inFireOrLava(p)) {
			reignite(p); // the approved bonus: fire or lava relights him at once
		} else if (at > 0 && now >= at) {
			reignite(p);
		} else if (at > 0) {
			// embers flicker over him, more and more as it counts down
			float k = 1f - (at - now) / (float) REIGNITE_TICKS;
			if (l.random.nextFloat() < 0.15f + 0.6f * k) {
				l.sendParticles(EMBER, p.getX(), p.getY() + p.getBbHeight() * l.random.nextDouble(), p.getZ(), 1 + (int) (k * 3), 0.3, 0.2, 0.3, 0.01);
			}
		}
	}

	/**
	 * Water puts him out: everything running ends, no moves until he relights. {@code inWater}: still in it (the countdown waits
	 * for him to come out); else (a splash) the 10 s start now. Getting wet again while doused restarts the countdown.
	 */
	public void douse(ServerPlayer p, boolean inWater) {
		long now = p.level().getGameTime();
		boolean was = flag(p, F_DOUSED);
		setTimer(p, "reignite_at", inWater ? 0 : now + REIGNITE_TICKS);
		if (was) {
			return;
		}
		stopEverything(p);
		setFlag(p, F_DOUSED, true);
		anim(p, ANIM_DOUSED);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_HISS, 1.0f, 1.0f);
		l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.4, p.getZ(), 24, 0.35, 0.5, 0.35, 0.05);
		l.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + p.getBbHeight(), p.getZ(), 10, 0.2, 0.15, 0.2, 0.03);
		QuirkFx.ring(l, p.position().add(0, 1.2, 0)).style(QuirkFx.RING_STEAM).colours(0xFFFFFF, 0xC8D0D8).size(2.4f).life(14).spawn();
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.heatblast.doused").withStyle(ChatFormatting.AQUA), true);
	}

	/** The flames burst back. */
	public void reignite(ServerPlayer p) {
		if (!flag(p, F_DOUSED)) {
			return;
		}
		setFlag(p, F_DOUSED, false);
		setFlag(p, F_WET, false);
		setTimer(p, "reignite_at", 0);
		anim(p, ANIM_REIGNITE);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_REIGNITE, 1.0f, 1.0f);
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.4, 0.9, 0.4, 0.12);
		l.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1.4, p.getZ(), 8, 0.3, 0.4, 0.3, 0);
		embers(l, p.position().add(0, p.getBbHeight(), 0), 10, 0.3, 0.1);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFF0B0, COLOUR).size(3.2f).thickness(0.6f).life(14).ground().spawn();
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.heatblast.reignited").withStyle(ChatFormatting.GOLD), true);
	}

	/** Doused: true (with a hiss and the countdown on the action bar) -- the move is refused. */
	private boolean out(ServerPlayer p) {
		if (!flag(p, F_DOUSED)) {
			return false;
		}
		long now = p.level().getGameTime();
		if (now - timer(p, "hiss_at") > 10) {
			setTimer(p, "hiss_at", now);
			ServerLevel l = level(p);
			l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
			l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
		}
		int left = reigniteLeft(p);
		p.displayClientMessage((flag(p, F_WET) || left <= 0 ? Component.translatable("message.projecthero.omnitrix.heatblast.wet")
				: Component.translatable("message.projecthero.omnitrix.heatblast.reigniting", (left + 19) / 20)).withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	/** Snowballs and water potions round him: a snowball that hit him hurts, a water potion that burst near him douses him. */
	private void watchProjectiles(ServerPlayer p, ServerLevel l) {
		Map<Projectile, Vec3[]> seen = WATCHED.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		for (Map.Entry<Projectile, Vec3[]> e : seen.entrySet()) {
			Projectile pr = e.getKey();
			if (!pr.isRemoved() || pr.getRemovalReason() != Entity.RemovalReason.DISCARDED) {
				continue;
			}
			Vec3 from = e.getValue()[0], to = e.getValue()[1];
			if (pr instanceof Snowball) {
				AABB box = p.getBoundingBox().inflate(0.35);
				if (box.contains(from) || box.contains(to) || box.clip(from, to).isPresent()) {
					snowballHit(p, l, pr);
				}
			} else if (p.getBoundingBox().inflate(4.0, 2.0, 4.0).contains(to) || p.getBoundingBox().inflate(4.0, 2.0, 4.0).contains(from)) {
				douse(p, touchingWater(p)); // the splash
			}
		}
		seen.clear();
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(10), HeatblastKit::watched)) {
			seen.put(pr, new Vec3[] { pr.position(), pr.position().add(pr.getDeltaMovement().scale(1.5)) });
		}
	}

	private static boolean watched(Projectile pr) {
		return pr.isAlive() && (pr instanceof Snowball
				|| pr instanceof ThrownPotion tp && tp.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER));
	}

	private void snowballHit(ServerPlayer p, ServerLevel l, Projectile ball) {
		p.invulnerableTime = 0;
		p.hurt(l.damageSources().thrown(ball, ball.getOwner()), SNOWBALL_DAMAGE);
		l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.5f);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3;
	}

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (out(p)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					flamethrower(p);
				} else {
					volley(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					magmaBomb(p);
				} else {
					lasso(p, lookTarget(p, LASSO_RANGE));
				}
			}
			case SLOT_3 -> {
				if (flag(p, F_RIDING)) {
					endRide(p, true); // X again (or Shift+X) drops off
				} else if (shift) {
					blazeDash(p);
				} else {
					flameJump(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					supernova(p);
				} else {
					fireTornado(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					heatShield(p);
				} else {
					heatAura(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					meteorCrash(p);
				} else {
					eruption(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		if (slot == AbilitySlot.SLOT_1 && flag(p, F_STREAM)) {
			stopThrower(p); // the Flamethrower runs while Shift+R is held
		}
	}

	// ---------------------------------------------------------------- R: Fireball Volley

	/** R: three quick fireballs down the look. */
	public boolean volley(ServerPlayer p) {
		if (out(p) || !use(p, VOLLEY)) {
			return false;
		}
		setTimer(p, "volley_start", p.level().getGameTime());
		fireball(p, 0);
		return true;
	}

	private void tickVolley(ServerPlayer p, ServerLevel l, long now) {
		long start = timer(p, "volley_start");
		if (start <= 0) {
			return;
		}
		long d = now - start;
		if (d > 0 && d % VOLLEY_GAP == 0 && d / VOLLEY_GAP < VOLLEY_SHOTS) {
			fireball(p, (int) (d / VOLLEY_GAP));
		}
		if (d >= (long) VOLLEY_GAP * (VOLLEY_SHOTS - 1)) {
			setTimer(p, "volley_start", 0);
		}
	}

	/** One fireball of the volley: alternating hands, a little spread. */
	private void fireball(ServerPlayer p, int n) {
		anim(p, n == 1 ? ANIM_VOLLEY_L : ANIM_VOLLEY);
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		double side = n == 1 ? -0.035 : n == 2 ? 0.035 : 0;
		AlienProjectileEntity.of(p, "heatblast_blast").colour(COLOUR).size(0.4f).damage(VOLLEY_DAMAGE).fire(3).speed(1.9).life(45)
				.direction(look.add(right.scale(side))).spawn();
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_BLAST, 0.8f, 0.95f + n * 0.1f);
	}

	// ---------------------------------------------------------------- Shift+R: Flamethrower (held)

	/** Shift+R: a stream of fire from both hands while the keys are held, 3 s at most. */
	public boolean flamethrower(ServerPlayer p) {
		if (out(p) || flag(p, F_STREAM) || !use(p, THROWER)) {
			return false;
		}
		anim(p, ANIM_THROWER);
		setTimer(p, "stream_end", p.level().getGameTime() + THROWER_TICKS);
		setFlag(p, F_STREAM, true);
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_STREAM, 1.0f, 1.0f);
		return true;
	}

	public void stopThrower(ServerPlayer p) {
		setTimer(p, "stream_end", 0);
		setFlag(p, F_STREAM, false);
		if (Omnitrix.state(p).animId == ANIM_THROWER) {
			anim(p, 0);
		}
	}

	private void tickThrower(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "stream_end");
		if (end <= 0) {
			if (flag(p, F_STREAM)) {
				setFlag(p, F_STREAM, false);
			}
			return;
		}
		if (now >= end) {
			stopThrower(p);
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 low = eye.add(0, -0.3, 0);
		// the cone is drawn on every client (HeatblastClient: Pyrokinesis' Flamethrower); this is its spray
		for (int i = 0; i < 2; i++) {
			double d = 1.0 + l.random.nextDouble() * (THROWER_RANGE - 1.0);
			Vec3 pt = low.add(look.scale(d));
			l.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 1, 0.08 * d, 0.08 * d, 0.08 * d, 0.02);
		}
		if (now % 12 == 0) {
			OmnitrixSounds.play(l, eye, OmnitrixSounds.HEATBLAST_STREAM, 0.6f, 1.1f);
		}
		if ((end - now) % THROWER_PULSE == 0) {
			for (LivingEntity e : around(p, eye.add(look.scale(THROWER_RANGE * 0.5)), THROWER_RANGE * 0.7)) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
				if (to.length() <= THROWER_RANGE + 0.5 && to.normalize().dot(look) > Math.cos(Math.toRadians(28))) {
					hurt(p, e, THROWER_DAMAGE, true);
					e.igniteForSeconds(3);
				}
			}
		}
		// the stream lights the ground it plays over (temporary fire, only where he may build and terrain damage is on)
		if (now % 6 == 0) {
			BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(look.scale(THROWER_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (hit.getType() == HitResult.Type.BLOCK) {
				PyrokinesisHandlers.placeFire(p, l, hit.getBlockPos().relative(hit.getDirection()), 60);
			}
		}
	}

	// ---------------------------------------------------------------- G: Fire Lasso

	/** G: a whip of flame lashes out, catches the creature you look at, and hauls it in -- burning. */
	public boolean lasso(ServerPlayer p, LivingEntity target) {
		if (out(p)) {
			return false;
		}
		if (target == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.heatblast.nothing_to_lasso").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, LASSO)) {
			return false;
		}
		anim(p, ANIM_LASSO);
		setFlag(p, F_LASSO, true);
		setTimer(p, "lasso_id", target.getId() + 1L);
		setTimer(p, "lasso_at", p.level().getGameTime());
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_LASSO, 1.0f, 1.0f);
		target.invulnerableTime = 0;
		hurt(p, target, LASSO_DAMAGE, true);
		target.igniteForSeconds(5);
		l.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 16, 0.3, 0.4, 0.3, 0.04);
		return true;
	}

	private void tickLasso(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_LASSO)) {
			return;
		}
		long t = now - timer(p, "lasso_at");
		Entity e = l.getEntity(lassoTarget(p));
		if (!(e instanceof LivingEntity le) || !le.isAlive() || t > LASSO_TICKS + 4) {
			setFlag(p, F_LASSO, false);
			setTimer(p, "lasso_id", 0);
			setTimer(p, "lasso_at", 0);
			return;
		}
		if (t == 3) {
			anim(p, ANIM_YANK);
		}
		boolean heavy = le instanceof EnderDragon || le instanceof WitherBoss || le.getBbHeight() > 3.2f;
		if (t >= 3 && t <= LASSO_TICKS && !heavy) {
			// hauled in, stopping an arm's length in front of him
			Vec3 to = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6)).subtract(le.position());
			if (to.length() > 0.8) {
				Vec3 v = to.normalize().scale(Math.min(1.3, to.length() * 0.45));
				le.setDeltaMovement(v.x, Math.max(v.y, 0.12), v.z);
				le.hurtMarked = true;
				le.resetFallDistance();
			}
		}
		if (t % 2 == 0) {
			l.sendParticles(ParticleTypes.FLAME, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 3, 0.25, 0.3, 0.25, 0.01);
		}
	}

	// ---------------------------------------------------------------- Shift+G: Magma Bomb

	/** Shift+G: a lobbed ball of molten rock -- a fiery burst, then a pool of burning magma for 4 s. */
	public boolean magmaBomb(ServerPlayer p) {
		if (out(p) || !use(p, MAGMA)) {
			return false;
		}
		anim(p, ANIM_MAGMA);
		AlienProjectileEntity.of(p, "heatblast_bomb").colour(0xFF5A10).size(0.75f).damage(MAGMA_DAMAGE * 0.5f).blast(3.5f, MAGMA_DAMAGE).fire(5)
				.gravity(0.045).speed(1.15).life(80).spawn();
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_BLAST, 1.0f, 0.7f);
		return true;
	}

	/** A burning patch where a Magma Bomb landed: a magma decal and fire (terrain damage / build rights permitting). */
	public static void magmaPatch(ServerPlayer p, ServerLevel l, Vec3 at) {
		BlockPos ground = groundUnder(l, BlockPos.containing(at.x, at.y + 0.5, at.z));
		Vec3 c = ground == null ? at : Vec3.atBottomCenterOf(ground.above());
		PATCHES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Patch(l.dimension(), c, l.getGameTime() + PATCH_TICKS));
		QuirkFx.decal(l, c.add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(0xE04A10).size(PATCH_RADIUS * 2f).life(PATCH_TICKS + 20).spawn();
		QuirkFx.decal(l, c.add(0, 0.02, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x1A1210).size(PATCH_RADIUS * 2.6f).life(PATCH_TICKS + 120).spawn();
		int r = (int) Math.ceil(PATCH_RADIUS);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz <= PATCH_RADIUS * PATCH_RADIUS && l.random.nextFloat() < 0.45f) {
					BlockPos g = groundUnder(l, BlockPos.containing(c.x + dx, c.y - 0.5, c.z + dz));
					if (g != null) {
						PyrokinesisHandlers.placeFire(p, l, g.above(), PATCH_TICKS);
					}
				}
			}
		}
	}

	private void tickPatches(ServerPlayer p, ServerLevel l, long now) {
		List<Patch> list = PATCHES.get(p.getUUID());
		if (list == null || list.isEmpty()) {
			return;
		}
		list.removeIf(pt -> now >= pt.end() || pt.dim() != l.dimension());
		for (Patch pt : list) {
			if (now % 3 == 0) {
				l.sendParticles(ParticleTypes.LAVA, pt.at().x, pt.at().y + 0.1, pt.at().z, 1, PATCH_RADIUS * 0.4, 0.02, PATCH_RADIUS * 0.4, 0);
				l.sendParticles(ParticleTypes.FLAME, pt.at().x, pt.at().y + 0.15, pt.at().z, 3, PATCH_RADIUS * 0.4, 0.05, PATCH_RADIUS * 0.4, 0.01);
				l.sendParticles(ParticleTypes.SMOKE, pt.at().x, pt.at().y + 0.4, pt.at().z, 1, PATCH_RADIUS * 0.3, 0.1, PATCH_RADIUS * 0.3, 0.01);
			}
			if (now % 10 == 0) {
				for (LivingEntity e : around(p, pt.at(), PATCH_RADIUS + 0.5)) {
					if (Math.abs(e.getY() - pt.at().y) < 1.5) {
						hurt(p, e, PATCH_DAMAGE, true);
						e.igniteForSeconds(3);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- X: Flame Jump

	/** X (tap): a jet of fire off the ground launches him up and forward. */
	public boolean flameJump(ServerPlayer p) {
		if (out(p) || !use(p, JUMP)) {
			return false;
		}
		anim(p, ANIM_JUMP);
		Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		p.setDeltaMovement(flat.x * 0.95, 1.05, flat.z * 0.95);
		p.hurtMarked = true;
		p.resetFallDistance();
		noFall(p, 100);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET, 1.0f, 1.0f);
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.1, p.getZ(), 40, 0.4, 0.1, 0.4, 0.1);
		l.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.4, 0.05, 0.4, 0.03);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFE0A0, COLOUR).size(2.2f).thickness(0.5f).life(10).ground().spawn();
		return true;
	}

	// ---------------------------------------------------------------- Hold X: Rock Ride

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (slot != AbilitySlot.SLOT_3 || out(p)) {
			return;
		}
		if (flag(p, F_RIDING)) {
			endRide(p, true); // holding X on the rock drops off too
			return;
		}
		int left = cooldownLeft(p, RIDE);
		if (left > 0) {
			use(p, RIDE); // (refused: the "not ready" line)
			return;
		}
		// the hold began KIT_HOLD_TICKS after the press: count the charge from the press
		setTimer(p, "ride_charge", Math.max(1L, p.level().getGameTime() - OmnitrixConfig.KIT_HOLD_TICKS));
		setFlag(p, F_RIDE_CHARGE, true);
		anim(p, ANIM_RIDE_CHARGE);
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_IGNITE, 0.7f, 0.7f);
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		if (slot != AbilitySlot.SLOT_3 || !flag(p, F_RIDE_CHARGE)) {
			return;
		}
		// let go before the 2 s: no rock, a Flame Jump instead
		setFlag(p, F_RIDE_CHARGE, false);
		setTimer(p, "ride_charge", 0);
		anim(p, 0);
		flameJump(p);
	}

	private void tickRideCharge(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_RIDE_CHARGE)) {
			return;
		}
		long at = timer(p, "ride_charge");
		long held = now - at;
		if (at <= 0) {
			setFlag(p, F_RIDE_CHARGE, false);
			return;
		}
		if (held >= RIDE_CHARGE_TICKS) {
			setFlag(p, F_RIDE_CHARGE, false);
			setTimer(p, "ride_charge", 0);
			startRide(p);
			return;
		}
		float k = held / (float) RIDE_CHARGE_TICKS;
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false, false));
		// the charge: the ground under him rumbles and cracks, flames gather round his raised fist
		BlockState under = l.getBlockState(p.blockPosition().below());
		if (now % 3 == 0) {
			if (!under.isAir()) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.05, p.getZ(), 2 + (int) (k * 8), 0.5 + k * 0.4, 0.02,
						0.5 + k * 0.4, 0.05);
			}
			Vec3 fist = p.position().add(0, p.getBbHeight() + 0.2, 0);
			l.sendParticles(ParticleTypes.FLAME, fist.x, fist.y, fist.z, 1 + (int) (k * 4), 0.15, 0.15, 0.15, 0.02);
		}
		if (now % 8 == 0) {
			OmnitrixSounds.play(l, p.position(), SND_RUMBLE, 0.6f + 0.6f * k, 0.8f + 0.3f * k);
			QuirkCamera.shake(p, 0.06f + 0.18f * k, 6);
		}
	}

	/**
	 * The Rock Ride: his fist into the ground, a chunk rips out (made of whatever he stands on -- shown under his feet; the
	 * ground itself is left alone) and he surfs it through the air where he looks. His own client flies it (HeatblastClient);
	 * the server times it (1 minute), trails the fire and ends it.
	 */
	public boolean startRide(ServerPlayer p) {
		if (out(p) || !use(p, RIDE)) {
			return false;
		}
		ServerLevel l = level(p);
		BlockPos ground = null;
		for (int dy = 1; dy <= 4 && ground == null; dy++) {
			BlockPos b = p.blockPosition().below(dy - 1).below();
			BlockState st = l.getBlockState(b);
			if (!st.isAir() && st.getFluidState().isEmpty() && !st.getCollisionShape(l, b).isEmpty() && !st.hasBlockEntity()) {
				ground = b;
			}
		}
		BlockState rock = ground == null ? Blocks.BASALT.defaultBlockState() : l.getBlockState(ground);
		long now = l.getGameTime();
		setFlag(p, F_RIDING, true);
		setTimer(p, "ride_end", now + RIDE_TICKS);
		setTimer(p, "ride_at", now);
		setTimer(p, "ride_block", Block.getId(rock) + 1L);
		if (!p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true; // (so a server without allow-flight never kicks him for floating)
			p.onUpdateAbilities();
		}
		anim(p, ANIM_RIDE_PUNCH);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.x * 0.5, 0.75, look.z * 0.5);
		p.hurtMarked = true;
		p.resetFallDistance();
		Vec3 at = ground == null ? p.position() : Vec3.atCenterOf(ground);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock), at.x, at.y + 0.6, at.z, 40, 0.6, 0.3, 0.6, 0.2);
		l.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.6, at.z, 30, 0.5, 0.2, 0.5, 0.12);
		l.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.6, at.z, 6, 0.4, 0.1, 0.4, 0);
		QuirkFx.shards(l, at.add(0, 0.7, 0)).block(rock).size(1.4f).count(14).spawn();
		QuirkFx.decal(l, at.add(0, 0.52, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size(2.4f).yaw(p.getYRot()).life(160).spawn();
		QuirkFx.ring(l, at.add(0, 0.55, 0)).colours(0xFFE0A0, COLOUR).size(3.0f).thickness(0.6f).life(12).ground().spawn();
		OmnitrixSounds.play(l, at, SND_RIP, 1.2f, 1.0f);
		QuirkCamera.shake(p, 0.35f, 8);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.heatblast.ride_on").withStyle(ChatFormatting.GOLD), true);
		return true;
	}

	/** Off the rock: it crumbles ({@code crumble}: with its dust and sound); no fall damage until he lands. */
	public void endRide(ServerPlayer p, boolean crumble) {
		if (!flag(p, F_RIDING)) {
			return;
		}
		BlockState rock = rideBlock(p);
		setFlag(p, F_RIDING, false);
		for (String t : new String[] { "ride_end", "ride_at", "ride_block" }) {
			setTimer(p, t, 0);
		}
		if (!p.isCreative() && !p.isSpectator()) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		p.resetFallDistance();
		noFall(p, 400);
		cooldown(p, RIDE, -1); // the cooldown runs from when he steps off
		if (crumble) {
			ServerLevel l = level(p);
			Vec3 at = p.position().add(0, -0.3, 0);
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock), at.x, at.y, at.z, 36, 0.5, 0.25, 0.5, 0.15);
			l.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 6, 0.4, 0.2, 0.4, 0.02);
			QuirkFx.shards(l, at).block(rock).size(1.2f).count(12).spawn();
			OmnitrixSounds.play(l, at, SND_CRUMBLE, 1.0f, 1.0f);
		}
	}

	private void tickRide(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_RIDING)) {
			return;
		}
		long end = timer(p, "ride_end");
		if (end <= 0 || now >= end) {
			endRide(p, true);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.heatblast.ride_off").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if ((p.isShiftKeyDown() && now - timer(p, "ride_at") > 5) || p.isPassenger() || !p.isAlive()) {
			endRide(p, true); // sneak drops off
			return;
		}
		if (p.getAbilities().flying) {
			p.getAbilities().flying = false; // (mayfly is only for the anti-float check: no creative flight on the rock)
			p.onUpdateAbilities();
		}
		p.resetFallDistance();
		// the fire jetting out under the rock (the rock itself and its jets are drawn on every client)
		Vec3 under = p.position().add(0, -0.6, 0);
		Vec3 v = p.getDeltaMovement();
		l.sendParticles(ParticleTypes.FLAME, under.x - v.x, under.y, under.z - v.z, 3, 0.25, 0.1, 0.25, 0.02);
		if (now % 4 == 0) {
			l.sendParticles(ParticleTypes.LARGE_SMOKE, under.x - v.x * 2, under.y - 0.2, under.z - v.z * 2, 1, 0.2, 0.1, 0.2, 0.01);
		}
		if (now % 5 == 0) {
			l.sendParticles(ParticleTypes.LAVA, under.x, under.y, under.z, 1, 0.2, 0.05, 0.2, 0);
		}
		if ((now - timer(p, "ride_at")) % 16 == 0) {
			OmnitrixSounds.play(l, p.position(), SND_RIDE_LOOP, 1.0f, 1.0f);
		}
	}

	/** No fall damage for up to {@code ticks}, or until he next lands (a jump off, a drop off the rock, the crash). */
	private void noFall(ServerPlayer p, int ticks) {
		long now = p.level().getGameTime();
		setTimer(p, "nofall_until", now + ticks);
		setTimer(p, "nofall_from", now);
	}

	private void tickNoFall(ServerPlayer p, long now) {
		long until = timer(p, "nofall_until");
		if (until <= 0) {
			return;
		}
		if (now >= until || (p.onGround() && now - timer(p, "nofall_from") > 5 && !flag(p, F_RIDING) && !flag(p, F_METEOR))) {
			setTimer(p, "nofall_until", 0);
			setTimer(p, "nofall_from", 0);
		} else {
			p.resetFallDistance();
		}
	}

	// ---------------------------------------------------------------- Shift+X: Blaze Dash

	/** Shift+X: a burst along the ground, leaving a trail of fire and scorching everything it passes. */
	public boolean blazeDash(ServerPlayer p) {
		if (out(p) || !use(p, DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		setFlag(p, F_DASH, true);
		setTimer(p, "dash_end", p.level().getGameTime() + DASH_TICKS);
		DASH_HITS.put(p.getUUID(), new HashSet<>());
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET, 1.0f, 1.3f);
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.3, p.getZ(), 30, 0.3, 0.2, 0.3, 0.12);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFE0A0, COLOUR).size(2.0f).thickness(0.5f).life(10).ground().spawn();
		return true;
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
		Vec3 dir = Vec3.directionFromRotation(0, p.getYRot());
		p.setDeltaMovement(dir.x * DASH_SPEED, Math.min(0, p.getDeltaMovement().y), dir.z * DASH_SPEED);
		p.hurtMarked = true;
		p.resetFallDistance();
		Set<Integer> hits = DASH_HITS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (LivingEntity e : around(p, p.position().add(dir.scale(0.8)).add(0, 0.9, 0), 1.8)) {
			if (hits.add(e.getId())) {
				hurt(p, e, DASH_DAMAGE, true);
				e.igniteForSeconds(4);
				l.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
			}
		}
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.3, 0.15, 0.3, 0.02);
		l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
		// the trail: fire left burning where he ran (temporary, terrain damage / build rights permitting) and a scorch line
		BlockPos feet = p.blockPosition();
		PyrokinesisHandlers.placeFire(p, l, feet, 60);
		if (now % 2 == 0) {
			QuirkFx.decal(l, p.position().add(0, 0.02, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x1A1210).size(1.4f).yaw(p.getYRot()).life(120).spawn();
		}
	}

	// ---------------------------------------------------------------- Z: Fire Tornado

	/** Z: a spinning column of flame where you look (24 blocks): it pulls creatures in and burns them for 4 s. */
	public boolean fireTornado(ServerPlayer p) {
		if (out(p) || !use(p, TORNADO)) {
			return false;
		}
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(TORNADO_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		LivingEntity aim = lookTarget(p, TORNADO_RANGE);
		Vec3 at = aim != null ? aim.position() : hit.getLocation();
		BlockPos g = groundUnder(l, BlockPos.containing(at.x, at.y + 0.5, at.z));
		if (g == null) {
			g = groundUnder(l, BlockPos.containing(at.x, at.y - 3, at.z));
		}
		Vec3 base = g == null ? at : Vec3.atBottomCenterOf(g.above()).add(at.x - Math.floor(at.x) - 0.5, 0, at.z - Math.floor(at.z) - 0.5);
		anim(p, ANIM_TORNADO);
		setFlag(p, F_TORNADO, true);
		setTimer(p, "tor_x", bits(base.x));
		setTimer(p, "tor_y", bits(base.y));
		setTimer(p, "tor_z", bits(base.z));
		setTimer(p, "tor_end", l.getGameTime() + TORNADO_TICKS);
		OmnitrixSounds.play(l, base, OmnitrixSounds.HEATBLAST_NOVA, 1.0f, 0.8f);
		QuirkFx.ring(l, base.add(0, 0.05, 0)).colours(0xFFE0A0, COLOUR).size(4.5f).thickness(0.7f).life(14).ground().spawn();
		QuirkFx.decal(l, base.add(0, 0.02, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x1A1210).size(5f).life(TORNADO_TICKS + 100).spawn();
		return true;
	}

	private void tickTornado(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_TORNADO)) {
			return;
		}
		Vec3 c = tornadoAt(p);
		long end = timer(p, "tor_end");
		if (c == null || end <= 0 || now >= end) {
			setFlag(p, F_TORNADO, false);
			for (String t : new String[] { "tor_x", "tor_y", "tor_z", "tor_end" }) {
				setTimer(p, t, 0);
			}
			if (c != null) {
				l.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 2, c.z, 20, 0.8, 1.5, 0.8, 0.03);
			}
			return;
		}
		long age = TORNADO_TICKS - (end - now);
		// the spiralling flames (the column itself is drawn on every client)
		for (int i = 0; i < 4; i++) {
			double h = l.random.nextDouble() * 6.0;
			double a = now * 0.6 + i * Math.PI / 2 + h;
			double r = 0.5 + h * 0.3;
			l.sendParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * r, c.y + h, c.z + Math.sin(a) * r, 0, -Math.sin(a) * 0.3, 0.15, Math.cos(a) * 0.3, 1.0);
		}
		if (now % 4 == 0) {
			l.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 6.5, c.z, 2, 0.8, 0.3, 0.8, 0.02);
		}
		if (age % 20 == 0) {
			OmnitrixSounds.play(l, c, SND_TORNADO, 1.2f, 1.0f);
		}
		for (LivingEntity e : around(p, c.add(0, 2, 0), TORNADO_PULL)) {
			Vec3 to = c.subtract(e.position()).multiply(1, 0, 1);
			double d = to.length();
			if (d > 0.3) {
				// sucked in and spun round, lifted a little
				Vec3 in = to.normalize();
				Vec3 swirl = new Vec3(-in.z, 0, in.x);
				Vec3 v = in.scale(Math.min(0.35, 0.08 + d * 0.05)).add(swirl.scale(0.18));
				e.setDeltaMovement(e.getDeltaMovement().multiply(0.5, 1, 0.5).add(v.x, d < 1.6 ? 0.06 : 0, v.z));
				e.hurtMarked = true;
			}
			if (age % 10 == 0 && d < 2.2) {
				e.invulnerableTime = 0;
				hurt(p, e, TORNADO_DAMAGE, true);
				e.igniteForSeconds(4);
			}
		}
	}

	// ---------------------------------------------------------------- Shift+Z: Supernova (ultimate)

	public boolean supernova(ServerPlayer p) {
		if (out(p) || !use(p, SUPERNOVA)) {
			return false;
		}
		anim(p, ANIM_CHARGE);
		setFlag(p, F_CHARGING, true);
		setTimer(p, "supernova_at", p.level().getGameTime() + SUPERNOVA_CHARGE);
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_SHIELD, 1.0f, 0.6f);
		return true;
	}

	private void tickSupernova(ServerPlayer p, ServerLevel l, long now) {
		long at = timer(p, "supernova_at");
		if (at <= 0) {
			return;
		}
		if (now >= at) {
			setTimer(p, "supernova_at", 0);
			setFlag(p, F_CHARGING, false);
			supernovaBlast(p);
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 3, false, false, false));
		if (now % 3 == 0) {
			float f = 1f - (at - now) / (float) SUPERNOVA_CHARGE;
			double yaw = Math.toRadians(p.yBodyRot);
			Vec3 core = p.position().add(-Math.sin(yaw) * 0.6, 1.05, Math.cos(yaw) * 0.6);
			l.sendParticles(ParticleTypes.FLAME, core.x, core.y, core.z, 2 + (int) (f * 6), 0.2 + f * 0.5, 0.2 + f * 0.5, 0.2 + f * 0.5, 0.01);
		}
	}

	private void supernovaBlast(ServerPlayer p) {
		anim(p, ANIM_SUPERNOVA);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		OmnitrixSounds.play(l, c, OmnitrixSounds.HEATBLAST_NOVA, 1.4f, 0.75f);
		QuirkCamera.shakeAround(l, c, SUPERNOVA_RADIUS * 2.5, 0.6f, 12);
		// Pyrokinesis' Phoenix Dive landing (the nova ring under a swelling fireball, held to a Meteor's size)
		PyrokinesisHandlers.phoenixImpactFx(l, p, p.position(), SUPERNOVA_RADIUS, PyrokinesisHandlers.METEOR_RADIUS);
		for (LivingEntity e : around(p, c, SUPERNOVA_RADIUS)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			hurt(p, e, (float) (SUPERNOVA_DAMAGE - (SUPERNOVA_DAMAGE - SUPERNOVA_EDGE_DAMAGE) * Math.min(1.0, d / SUPERNOVA_RADIUS)), true);
			e.igniteForSeconds(8);
			shove(e, c, 1.8, 0.45);
		}
		// ...and it leaves a ring of fire burning round him
		PyroFxEntity.spawn(l, p, PyroFxEntity.RING, p.position(), p.getYRot(), FIRE_RING_RADIUS, FIRE_RING_TICKS).visualOnly();
		PyrokinesisHandlers.ringCastFx(l, p.position(), FIRE_RING_RADIUS);
		setTimer(p, "ring_end", l.getGameTime() + FIRE_RING_TICKS);
		setTimer(p, "ring_x", bits(p.getX()));
		setTimer(p, "ring_y", bits(p.getY()));
		setTimer(p, "ring_z", bits(p.getZ()));
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI * 2 / 40;
			BlockPos g = groundUnder(l, BlockPos.containing(p.getX() + Math.cos(a) * FIRE_RING_RADIUS, p.getY() - 0.5, p.getZ() + Math.sin(a) * FIRE_RING_RADIUS));
			if (g != null) {
				PyrokinesisHandlers.placeFire(p, l, g.above(), FIRE_RING_TICKS + 20);
			}
		}
	}

	private void tickFireRing(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "ring_end");
		if (end <= 0) {
			return;
		}
		if (now >= end) {
			for (String t : new String[] { "ring_end", "ring_x", "ring_y", "ring_z" }) {
				setTimer(p, t, 0);
			}
			return;
		}
		if (now % 10 != 0) {
			return;
		}
		Vec3 c = new Vec3(unbits(timer(p, "ring_x")), unbits(timer(p, "ring_y")), unbits(timer(p, "ring_z")));
		for (LivingEntity e : around(p, c, FIRE_RING_RADIUS + 1.0)) {
			double d = Math.sqrt(e.distanceToSqr(c.x, e.getY(), c.z));
			if (Math.abs(d - FIRE_RING_RADIUS) < 1.4 && Math.abs(e.getY() - c.y) < 2.5) {
				hurt(p, e, FIRE_RING_DAMAGE, true);
				e.igniteForSeconds(3);
			}
		}
	}

	// ---------------------------------------------------------------- V: Heat Aura / Shift+V: Heat Shield

	/**
	 * V: Heat Aura on / off. On, he burns white-hot: every creature within 3.5 blocks catches fire (and takes 1 a second), snow
	 * and ice round him melt, and items dropped near him come out smelted, like a walking furnace. Water puts it out.
	 */
	public boolean heatAura(ServerPlayer p) {
		boolean on = !flag(p, F_AURA);
		if (on && out(p)) {
			return false;
		}
		if (!use(p, HEAT_AURA)) {
			return false;
		}
		setFlag(p, F_AURA, on);
		anim(p, ANIM_AURA);
		ServerLevel l = level(p);
		if (on) {
			OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_IGNITE, 1.0f, 0.8f);
			l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.4, 0.9, 0.4, 0.05);
		} else {
			l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.4f);
			l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
		}
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.heatblast.aura_on" : "message.projecthero.omnitrix.heatblast.aura_off")
				.withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
		return true;
	}

	private void tickAura(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_AURA)) {
			return;
		}
		if (now % 4 == 0) {
			l.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.3, 0.6, 0.3, 0.01);
		}
		if (now % 3 == 0) {
			l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.3, p.getZ(), 1, AURA_RADIUS * 0.4, 0.2, AURA_RADIUS * 0.4, 0.02);
		}
		if (now % 10 == 0) {
			for (LivingEntity e : around(p, p.position().add(0, 1, 0), AURA_RADIUS)) {
				e.igniteForSeconds(3);
				if (now % 20 == 0) {
					hurt(p, e, AURA_DAMAGE, true);
				}
			}
			for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(AURA_RADIUS), it -> it.isAlive() && !it.getTags().contains(AURA_TAG))) {
				ItemStack held = it.getItem();
				Optional<RecipeHolder<SmeltingRecipe>> recipe = l.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(held), l);
				if (recipe.isEmpty()) {
					continue;
				}
				ItemStack out = recipe.get().value().getResultItem(l.registryAccess()).copy();
				if (out.isEmpty()) {
					continue;
				}
				out.setCount(Math.min(out.getMaxStackSize(), out.getCount() * held.getCount()));
				it.setItem(out);
				it.addTag(AURA_TAG);
				l.sendParticles(ParticleTypes.FLAME, it.getX(), it.getY() + 0.2, it.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
				l.sendParticles(ParticleTypes.SMOKE, it.getX(), it.getY() + 0.3, it.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
			}
		}
		if (now % 20 == 5 && p.mayBuild()) {
			BlockPos c = p.blockPosition();
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dy = -1; dy <= 2; dy++) {
					for (int dz = -3; dz <= 3; dz++) {
						m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
						BlockState st = l.getBlockState(m);
						if (st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW)) {
							l.setBlockAndUpdate(m, Blocks.AIR.defaultBlockState());
						} else if (st.is(Blocks.ICE) || st.is(Blocks.FROSTED_ICE)) {
							l.setBlockAndUpdate(m, l.dimensionType().ultraWarm() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState());
						} else {
							continue;
						}
						l.sendParticles(ParticleTypes.CLOUD, m.getX() + 0.5, m.getY() + 0.6, m.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
					}
				}
			}
		}
		if (now % 40 == 0) {
			l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.8f);
		}
	}

	/** Shift+V: a 5 s dome of heat -- 60% less damage, arrows and fireballs burn up in it, attackers are burned. */
	public boolean heatShield(ServerPlayer p) {
		if (out(p) || !use(p, HEAT_SHIELD)) {
			return false;
		}
		anim(p, ANIM_SHIELD);
		setTimer(p, "shield_end", p.level().getGameTime() + SHIELD_TICKS);
		setFlag(p, F_SHIELD, true);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_SHIELD, 1.0f, 1.0f);
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.9, 0.9, 0.9, 0.05);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFE0A0, COLOUR).size(3.2f).thickness(0.5f).life(12).ground().spawn();
		return true;
	}

	private void tickShield(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "shield_end");
		if (end <= 0) {
			return;
		}
		if (now >= end) {
			setTimer(p, "shield_end", 0);
			setFlag(p, F_SHIELD, false);
			return;
		}
		Map<Projectile, Vec3[]> seen = WATCHED.get(p.getUUID());
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(2.5),
				pr -> pr.getOwner() != p && !(pr instanceof AlienProjectileEntity))) {
			l.sendParticles(ParticleTypes.LARGE_SMOKE, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
			l.sendParticles(ParticleTypes.FLAME, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.04);
			l.playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.25f, 1.8f);
			if (seen != null) {
				seen.remove(pr); // burned up in the dome: a water potion never reaches him, a snowball never lands
			}
			pr.discard();
		}
		if (now % 3 == 0) {
			l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.9, 0.8, 0.9, 0.02);
		}
	}

	// ---------------------------------------------------------------- C: Volcanic Eruption

	/** C: his fist into the ground -- geysers of fire burst up one after another in a line ahead, launching what they catch. */
	public boolean eruption(ServerPlayer p) {
		if (out(p) || !use(p, ERUPTION)) {
			return false;
		}
		anim(p, ANIM_ERUPT);
		ServerLevel l = level(p);
		Vec3 dir = Vec3.directionFromRotation(0, p.getYRot());
		ERUPTIONS.put(p.getUUID(), new Eruption(p.position(), dir, l.getGameTime() + 4, l.dimension(), new HashSet<>()));
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_IGNITE, 0.8f, 0.6f);
		return true;
	}

	private void tickEruption(ServerPlayer p, ServerLevel l, long now) {
		Eruption er = ERUPTIONS.get(p.getUUID());
		if (er == null || now < er.start()) {
			return;
		}
		long d = now - er.start();
		if (d == 0) {
			// the fist lands
			Vec3 c = er.from().add(er.dir().scale(0.9));
			QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFE0A0, COLOUR).size(2.4f).thickness(0.6f).life(12).ground().spawn();
			QuirkCamera.shakeAround(l, c, 14, 0.45f, 10);
			OmnitrixSounds.play(l, c, SND_GEYSER, 1.0f, 0.7f);
		}
		if (d % 2 != 0) {
			return;
		}
		int step = (int) (d / 2);
		if (step >= ERUPT_GEYSERS || er.dim() != l.dimension()) {
			ERUPTIONS.remove(p.getUUID());
			return;
		}
		Vec3 at = er.from().add(er.dir().scale(1.8 + step * ERUPT_SPACING));
		BlockPos ground = groundUnder(l, BlockPos.containing(at.x, er.from().y - 0.5, at.z));
		if (ground == null) {
			ERUPTIONS.remove(p.getUUID()); // the line runs off a ledge
			return;
		}
		Vec3 top = Vec3.atBottomCenterOf(ground.above());
		BlockState under = l.getBlockState(ground);
		// a geyser: a column of fire and lava shooting up out of a cracked vent
		QuirkFx.cone(l, top, new Vec3(0, 1, 0)).style(QuirkFx.CONE_FLAME).colours(0xFFE0A0, 0xFF4A10).size(1.4f).length(4.5f).life(14).spawn();
		QuirkFx.decal(l, top.add(0, 0.02, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A1A14).size(1.6f).yaw(p.getYRot()).life(160).spawn();
		QuirkFx.decal(l, top.add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(0xE04A10).size(1.1f).life(60).spawn();
		l.sendParticles(ParticleTypes.FLAME, top.x, top.y + 1.5, top.z, 30, 0.25, 1.4, 0.25, 0.06);
		l.sendParticles(ParticleTypes.LAVA, top.x, top.y + 0.5, top.z, 6, 0.3, 0.4, 0.3, 0);
		l.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y + 3.5, top.z, 4, 0.3, 0.5, 0.3, 0.02);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), top.x, top.y + 0.2, top.z, 14, 0.4, 0.2, 0.4, 0.15);
		OmnitrixSounds.play(l, top, SND_GEYSER, 0.9f, 0.9f + step * 0.04f);
		for (LivingEntity e : around(p, top.add(0, 1.0, 0), 1.9)) {
			if (er.hits().add(e.getId())) {
				e.invulnerableTime = 0;
				hurt(p, e, ERUPT_DAMAGE, true);
				e.igniteForSeconds(4);
				e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 1.1 * (1.0 - Math.min(0.7, e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE))),
						e.getDeltaMovement().z * 0.3);
				e.hurtMarked = true;
			}
		}
	}

	// ---------------------------------------------------------------- Shift+C: Meteor Crash

	/** Shift+C: wrapped in fire, up he goes -- then down like a meteor, a 5-block fiery impact where he lands. */
	public boolean meteorCrash(ServerPlayer p) {
		if (out(p) || !use(p, METEOR)) {
			return false;
		}
		ServerLevel l = level(p);
		anim(p, ANIM_METEOR_RISE);
		setFlag(p, F_METEOR, true);
		setTimer(p, "meteor_at", l.getGameTime());
		noFall(p, 400);
		Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		p.setDeltaMovement(flat.x * 0.5, METEOR_RISE, flat.z * 0.5);
		p.hurtMarked = true;
		p.resetFallDistance();
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET, 1.2f, 0.7f);
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 50, 0.5, 0.2, 0.5, 0.12);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFE0A0, COLOUR).size(2.6f).thickness(0.6f).life(12).ground().spawn();
		return true;
	}

	private void tickMeteor(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_METEOR)) {
			return;
		}
		long t = now - timer(p, "meteor_at");
		p.resetFallDistance();
		l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.9, p.getZ(), 6, 0.35, 0.6, 0.35, 0.03);
		if (timer(p, "meteor_dive") == 0) {
			if (t > 30 || (t > 5 && p.getDeltaMovement().y < 0.1)) {
				// the top of the leap: down he comes, along the look
				setTimer(p, "meteor_dive", now);
				anim(p, ANIM_METEOR_DIVE);
				Vec3 look = p.getLookAngle();
				Vec3 flat = look.multiply(1, 0, 1);
				flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
				p.setDeltaMovement(flat.x * 0.7, -2.0, flat.z * 0.7);
				p.hurtMarked = true;
				OmnitrixSounds.play(l, p.position(), OmnitrixSounds.HEATBLAST_JET, 1.0f, 0.6f);
			}
			return;
		}
		long dive = now - timer(p, "meteor_dive");
		if ((dive > 1 && (p.onGround() || p.isInWater() || p.isInLava())) || dive > 100) {
			setFlag(p, F_METEOR, false);
			setTimer(p, "meteor_at", 0);
			setTimer(p, "meteor_dive", 0);
			crashLand(p, l);
		} else if (p.getDeltaMovement().y > -1.6) {
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x, -2.0, v.z); // a meteor does not float
			p.hurtMarked = true;
		}
	}

	private void crashLand(ServerPlayer p, ServerLevel l) {
		Vec3 c = p.position();
		anim(p, ANIM_SUPERNOVA);
		p.resetFallDistance();
		OmnitrixSounds.play(l, c, SND_CRASH, 1.3f, 1.0f);
		QuirkCamera.shakeAround(l, c, 18, 0.6f, 12);
		PyrokinesisHandlers.infernoFx(l, p, c, METEOR_RADIUS);
		PyrokinesisHandlers.meteorBlastFx(l, p, c, 2.5);
		QuirkFx.decal(l, c.add(0, 0.02, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x1A1210).size(METEOR_RADIUS * 2f).life(300).spawn();
		QuirkFx.decal(l, c.add(0, 0.03, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A1A14).size(METEOR_RADIUS * 1.4f).yaw(p.getYRot()).life(300).spawn();
		BlockState under = l.getBlockState(p.blockPosition().below());
		if (!under.isAir()) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.2, c.z, 50, 1.5, 0.3, 1.5, 0.2);
		}
		for (LivingEntity e : around(p, c.add(0, 0.5, 0), METEOR_RADIUS)) {
			e.invulnerableTime = 0;
			hurt(p, e, METEOR_DAMAGE, true);
			e.igniteForSeconds(5);
			shove(e, c, 1.4, 0.55);
		}
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le && !flag(p, F_DOUSED)) {
			anim(p, ANIM_PUNCH);
			if (ready(p, BODY)) {
				cooldown(p, BODY, 10);
				le.igniteForSeconds(4);
				le.invulnerableTime = 0;
				hurt(p, le, PUNCH_FIRE, true);
				level(p).sendParticles(ParticleTypes.FLAME, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
				le.invulnerableTime = 0; // the fist itself still lands this tick
			}
		}
		return false;
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		ItemStack held = p.getItemInHand(hand);
		if (held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
			if (out(p) || !use(p, FIRE_BOLT)) {
				return InteractionResult.FAIL;
			}
			anim(p, ANIM_BOLT);
			AlienProjectileEntity.of(p, "heatblast_bolt").colour(0xFFA040).size(0.28f).damage(BOLT_DAMAGE).fire(2).speed(1.6).life(30).spawn();
			OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.HEATBLAST_BLAST, 0.5f, 1.4f);
			p.swing(hand, true);
			return InteractionResult.SUCCESS;
		}
		if (p.isShiftKeyDown() && !held.isEmpty()) {
			return smelt(p, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return InteractionResult.PASS;
	}

	/** Shift + use with an item: smelts up to 8 of the held stack, like a furnace would. */
	public boolean smelt(ServerPlayer p, InteractionHand hand) {
		ItemStack held = p.getItemInHand(hand);
		ServerLevel l = level(p);
		Optional<RecipeHolder<SmeltingRecipe>> recipe = l.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(held), l);
		if (recipe.isEmpty()) {
			return false;
		}
		if (out(p) || !use(p, SMELT)) {
			return true;
		}
		anim(p, ANIM_SMELT);
		int n = Math.min(8, held.getCount());
		ItemStack out = recipe.get().value().getResultItem(l.registryAccess()).copy();
		out.setCount(out.getCount() * n);
		held.shrink(n);
		if (!p.getInventory().add(out)) {
			p.drop(out, false);
		}
		p.giveExperiencePoints(Math.max(1, Math.round(recipe.get().value().getExperience() * n)));
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 1.0f, 1.0f);
		Vec3 cup = p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.45, 0);
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4;
			l.sendParticles(ParticleTypes.FLAME, cup.x + Math.cos(a) * 0.25, cup.y, cup.z + Math.sin(a) * 0.25, 0, -Math.sin(a) * 0.5, 0.6, Math.cos(a) * 0.5, 0.06);
		}
		l.sendParticles(ParticleTypes.SMOKE, cup.x, cup.y + 0.3, cup.z, 4, 0.1, 0.1, 0.1, 0.02);
		p.swing(hand, true);
		return true;
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		return melt(p, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** What a meltable block becomes (null: not meltable). Ores drop their smelted ingot. */
	public static BlockState meltsInto(BlockState s) {
		if (s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) {
			return Blocks.GLASS.defaultBlockState();
		}
		if (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.FROSTED_ICE)) {
			return Blocks.WATER.defaultBlockState();
		}
		if (s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (s.is(Blocks.COBBLESTONE)) {
			return Blocks.STONE.defaultBlockState();
		}
		if (s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.COBBLED_DEEPSLATE)) {
			return Blocks.MAGMA_BLOCK.defaultBlockState();
		}
		if (s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.OBSIDIAN)) {
			return Blocks.LAVA.defaultBlockState();
		}
		if (s.is(Blocks.CLAY)) {
			return Blocks.TERRACOTTA.defaultBlockState();
		}
		if (s.is(Blocks.WET_SPONGE)) {
			return Blocks.SPONGE.defaultBlockState();
		}
		return null;
	}

	private static ItemStack oreDrop(BlockState s) {
		if (s.is(Blocks.IRON_ORE) || s.is(Blocks.DEEPSLATE_IRON_ORE)) {
			return new ItemStack(Items.IRON_INGOT);
		}
		if (s.is(Blocks.GOLD_ORE) || s.is(Blocks.DEEPSLATE_GOLD_ORE) || s.is(Blocks.NETHER_GOLD_ORE)) {
			return new ItemStack(Items.GOLD_INGOT);
		}
		if (s.is(Blocks.COPPER_ORE) || s.is(Blocks.DEEPSLATE_COPPER_ORE)) {
			return new ItemStack(Items.COPPER_INGOT, 2);
		}
		if (s.is(Blocks.ANCIENT_DEBRIS)) {
			return new ItemStack(Items.NETHERITE_SCRAP);
		}
		return ItemStack.EMPTY;
	}

	/** Use on a block (empty hand): melts it. True if anything happened. */
	public boolean melt(ServerPlayer p, BlockPos pos) {
		ServerLevel l = level(p);
		BlockState s = l.getBlockState(pos);
		BlockState into = meltsInto(s);
		ItemStack drop = into == null ? oreDrop(s) : ItemStack.EMPTY;
		if (into == null && drop.isEmpty()) {
			return false;
		}
		if (out(p) || !use(p, SMELT)) {
			return true;
		}
		if (!p.mayInteract(l, pos) || !p.mayBuild()) {
			return true;
		}
		anim(p, ANIM_SMELT);
		if (into != null) {
			l.setBlock(pos, into, Block.UPDATE_ALL);
		} else {
			l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			l.addFreshEntity(new ItemEntity(l, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));
		}
		l.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.2f);
		l.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0);
		l.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.02);
		Vec3 mid = Vec3.atCenterOf(pos);
		l.sendParticles(ParticleTypes.DRIPPING_LAVA, mid.x, mid.y - 0.3, mid.z, 4, 0.3, 0.1, 0.3, 0);
		l.sendParticles(ParticleTypes.LARGE_SMOKE, mid.x, mid.y + 0.6, mid.z, 3, 0.25, 0.2, 0.25, 0.02);
		p.swing(InteractionHand.MAIN_HAND, true);
		return true;
	}

	// ---------------------------------------------------------------- damage

	/** Fire, lava, magma floors, burning: nothing (doused or not); no fall damage off his rock, his jumps and his crash. */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.LAVA)) {
			p.clearFire();
			return false;
		}
		if (source.is(DamageTypeTags.IS_FALL)
				&& (flag(p, F_RIDING) || flag(p, F_METEOR) || timer(p, "nofall_until") > p.level().getGameTime())) {
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (flag(p, F_SHIELD)) {
			if (source.getDirectEntity() instanceof LivingEntity attacker && attacker != p && source.getDirectEntity() == source.getEntity()) {
				attacker.igniteForSeconds(3);
				hurt(p, attacker, 2f, true);
				level(p).sendParticles(ParticleTypes.FLAME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 6, 0.2, 0.3, 0.2,
						0.02);
			}
			return amount * SHIELD_FACTOR;
		}
		return amount;
	}

	// ---------------------------------------------------------------- helpers

	/** The top solid block at {@code pos}'s column (a little above to a little below), or null. */
	static BlockPos groundUnder(ServerLevel l, BlockPos pos) {
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos b = pos.offset(0, dy, 0);
			if (!l.getBlockState(b).getCollisionShape(l, b).isEmpty() && l.getBlockState(b.above()).getCollisionShape(l, b.above()).isEmpty()) {
				return b;
			}
		}
		return null;
	}
}
