package com.projecthero.mod.omnitrix.alien.chromastone;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Chromastone, the Crystalsapien (Recalibrated Omnitrix). v0.17.2 revamp (docs/OMNITRIX_REVAMP_CHROMASTONE.md; user: "can
 * you work on revamps for the rest of the alien force aliens please, look at their powers and abilities in the show and do
 * research and make them good please"): a living violet crystal that DRINKS energy and fires it back as prism light -- his
 * engine is the Charge meter (0-100). Light and energy only: the crystal-shard / spike moves were Diamondhead's and are gone.
 * No Ultimate forms (user: "dont add ultimate aliens yet"). The body is only the player model in the user's skin.
 *
 * v0.17.2 damage pass (user: "make the damage of all of ben 10s alien stronger"; docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md): the
 * damage numbers in the table below are the pre-pass ones (~x1.6 now) -- the constants are the source of truth.
 *
 * <pre>
 *   R          Prism Bolt       a bolt of UV light (6), 0.6 s, free
 *   Shift+R    Prism Beam       20 charge: a rainbow beam (28) piercing everything in line, 15 each
 *   G          Prism Punch      v0.17.2 round 4, free: a lunge and a straight right; the fist hits the first thing in reach 4.5 for
 *                               16 and its light bursts on through in a 12-block line, 10 to all of that
 *   Shift+G    Spectrum Sweep   v0.17.2 round 4, 15 charge: his beam swept across 120 degrees from left to right (18 blocks), 19 once each
 *   X          Light Flight     take off / land (hovering on his own light)
 *   Shift+X    Light Dash       10 charge: a streak of light along your look, 6 to everything it passes
 *   Z          Radiant Burst    beams out of his whole body in 8 directions (14): 13 to the first in each; with 25+ charge
 *                               it spends 25 and they hit 17
 *   Shift+Z    Full Spectrum    ULTIMATE (50+ charge, spends it all): 0.75 s gathering the light, then a rainbow beam (32): 40
 *                               + 0.6 per charge over 50 (70 at full)
 *   C          Charge Release   20 charge, v0.17.2 round 4: 0.4 s drawing it in, then a shockwave of the spectrum (r 6): 20
 *   Hold C     Refraction       30 charge: the light splits onto up to 5 creatures within 16, 20 each (moved here from Shift+G)
 *   Shift+C    UV Flare         15 charge: a flash (r 8) -- blinded 3 s and outlined; the undead burn in the ultraviolet (6, 4 s)
 *   V          Redirect         1.5 s: energy, fire, magic and projectiles that hit you are absorbed whole (no damage) and fired
 *                               straight back at whoever sent them -- 1.5x the hit (12 to 28), 3 returns at most (moved here from G)
 *   Hold V     Absorb Stance    5 s: EVERY hit absorbed at double charge, slowed (V again ends it; it held V before the move)
 *   Shift+V    Prism Shield     20 charge, 6 s: a wall of hard light in front -- hits from the front -70%, projectiles from the
 *                               front absorbed into charge
 *   Attack     Charged Fists    with 25+ charge a blow flares: +3, spends 2
 *   Passives   Absorption (energy / fire / magic / projectiles / lightning: a fifth gets through, 4 charge a point); lightning
 *              fills him; light-fed (+1 every 2 s under the daytime sky); crystal does not burn; 34 health, armour 10
 *   Weak       brittle (blows, explosions, falling things x1.25, never absorbed); OVERLOAD -- full and still absorbing, he must
 *              spend charge within 10 s or it bursts out of him (10 round him, 4 to him, charge down to 40)
 * </pre>
 */
public final class ChromastoneKit extends AlienKit {
	public static final String ID = Aliens.CHROMASTONE.id();
	public static final int MAX_CHARGE = 100;
	/** Charge per point of damage absorbed. */
	public static final float CHARGE_PER_DAMAGE = 4f;
	/** The part of an absorbable hit that still gets through. */
	public static final float ABSORB_LEAK = 0.2f;
	public static final int VIOLET = 0xC860FF, WHITE = 0xFFF4FF, UV = 0x8A40FF;
	public static final int[] SPECTRUM = { 0xFF3030, 0xFF9020, 0xFFE830, 0x40FF50, 0x30C8FF, 0x4060FF, 0xC040FF };

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue BOLT = OmnitrixSfx.cue("chromastone_bolt")
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, 1.4f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.15f, 2.0f);
	public static final OmnitrixSfx.Cue PRISM = OmnitrixSfx.cue("chromastone_prism")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.35f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.45f, 1.2f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.25f, 1.5f, 2);
	public static final OmnitrixSfx.Cue ABSORB = OmnitrixSfx.cue("chromastone_absorb")
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.35f, 0.8f)
			.layer(SoundEvents.BEACON_AMBIENT, 0.3f, 1.4f)
			.gap(6);
	public static final OmnitrixSfx.Cue CRACK = OmnitrixSfx.cue("chromastone_crack")
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.5f, 0.7f)
			.layer(SoundEvents.GLASS_HIT, 0.3f, 0.6f)
			.gap(4);
	public static final OmnitrixSfx.Cue SLAM = OmnitrixSfx.cue("chromastone_slam")
			.layer(SoundEvents.AMETHYST_BLOCK_BREAK, 0.5f, 0.6f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.15f, 1.4f)
			.layer(SoundEvents.AMETHYST_CLUSTER_PLACE, 0.4f, 0.8f, 2);
	public static final OmnitrixSfx.Cue LEAP = OmnitrixSfx.cue("chromastone_leap")
			.layer(SoundEvents.AMETHYST_BLOCK_STEP, 0.5f, 0.8f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.2f, 0.7f);
	public static final OmnitrixSfx.Cue STANCE = OmnitrixSfx.cue("chromastone_stance")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.35f, 0.7f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.45f, 0.6f);
	public static final OmnitrixSfx.Cue FLASH = OmnitrixSfx.cue("chromastone_flash")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.3f, 2.0f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, 2.0f);
	public static final OmnitrixSfx.Cue SPECTRUM_SND = OmnitrixSfx.cue("chromastone_spectrum")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.45f, 1.2f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.2f, 1.5f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 1.8f, 3);
	// v0.17.2 revamp
	public static final OmnitrixSfx.Cue RETURN = OmnitrixSfx.cue("chromastone_return")
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 1.5f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.3f, 1.8f)
			.layer(SoundEvents.GUARDIAN_ATTACK, 0.12f, 1.9f)
			.gap(2);
	public static final OmnitrixSfx.Cue OVERLOAD = OmnitrixSfx.cue("chromastone_overload")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.4f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.5f, 0.6f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.3f, 1.3f, 2);
	// v0.17.2 round 4
	public static final OmnitrixSfx.Cue GATHER = OmnitrixSfx.cue("chromastone_gather")
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 0.7f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.3f, 1.2f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, 0.9f, 3);
	public static final OmnitrixSfx.Cue DISCHARGE = OmnitrixSfx.cue("chromastone_discharge")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.45f, 1.5f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6f, 0.7f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.3f, 1.5f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.6f, 2);
	public static final OmnitrixSfx.Cue PUNCH = OmnitrixSfx.cue("chromastone_punch")
			.layer(SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 0.8f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.5f, 0.7f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.3f, 1.9f, 1);
	public static final OmnitrixSfx.Cue SWEEP = OmnitrixSfx.cue("chromastone_sweep")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.4f, 1.8f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.45f, 0.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.45f, 1.5f, 2);

	// ---------------------------------------------------------------- poses (client: ChromastoneClient)
	public static final int ANIM_BOLT = 1, ANIM_PRISM = 2, ANIM_REDIRECT = 3, ANIM_REFRACT = 4, ANIM_TAKEOFF = 5, ANIM_DASH = 6, ANIM_RADIANT = 7,
			ANIM_SPECTRUM = 8, ANIM_STANCE = 9, ANIM_SHIELD = 10, ANIM_RELEASE = 11, ANIM_FLARE = 12, ANIM_RETURN = 13, ANIM_OVERLOAD = 14,
			ANIM_LAND = 15, ANIM_PUNCH = 16, ANIM_SWEEP = 17;

	public static final int F_STANCE = 1;
	public static final int F_SPECTRUM = 2;
	/** The Refraction's rays: the targets' entity ids ride in the {@code ray.N} timers for the client (id + 1). */
	public static final int F_REFRACT = 4;
	/** v0.17.2 revamp: the Redirect window is open. */
	public static final int F_REDIRECT = 8;
	/** v0.17.2 revamp: the Prism Shield is up. */
	public static final int F_SHIELD = 16;
	/** v0.17.2 revamp: full and still absorbing -- the overload is coming. */
	public static final int F_OVER = 32;
	/** v0.17.2 revamp: flying. */
	public static final int F_FLYING = 64;
	/** v0.17.2 round 4: the Prism Punch is coming round. */
	public static final int F_PUNCH = 128;
	/** v0.17.2 round 4: the Spectrum Sweep is running. */
	public static final int F_SWEEP = 256;
	/** v0.17.2 round 4: the Charge Release is being drawn in. */
	public static final int F_RELEASE = 512;

	public static final String BOLT_MOVE = "prism_bolt", PRISM_BEAM = "prism_beam", REDIRECT = "redirect", REFRACTION = "refraction",
			FLIGHT = "light_flight", LIGHT_DASH = "light_dash", RADIANT_BURST = "radiant_burst", FULL_SPECTRUM = "full_spectrum",
			CHARGE_RELEASE = "charge_release", UV_FLARE = "uv_flare", ABSORB_STANCE = "absorb_stance", PRISM_SHIELD = "prism_shield",
			CHARGED_FISTS = "charged_fists", PRISM_PUNCH = "prism_punch", SPECTRUM_SWEEP = "spectrum_sweep";
	/** The kit timer holding the charge (0-100; unset = 0). */
	public static final String CHARGE = "charge";
	public static final String REDIRECT_END = "redirect_end", SHIELD_END = "shield_end", OVER_AT = "over_at", PUNCH_AT = "punch_at", SWEEP_AT = "sweep_at",
			RELEASE_AT = "release_at";

	// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger, i want each alien to feel like its own entire ability and i want them to be strong aswell"): ~x1.6 damage pass (docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md)
	public static final float BOLT_DAMAGE = 10f;
	public static final float PRISM_DAMAGE = 26f;
	public static final int PRISM_COST = 20;
	public static final int REDIRECT_TICKS = 30, REDIRECT_MAX = 3;
	public static final float REDIRECT_MIN = 12f, REDIRECT_CAP = 28f, REDIRECT_FACTOR = 1.5f;
	public static final float REFRACT_DAMAGE = 20f;
	public static final int REFRACT_COST = 30;
	public static final float FLY_SPEED = 0.05f;
	public static final int DASH_COST = 10;
	public static final float DASH_DAMAGE = 10f;
	public static final double DASH_LENGTH = 8.0;
	public static final float RADIANT_DAMAGE = 22f, RADIANT_CHARGED = 30f;
	public static final int RADIANT_COST = 25, RADIANT_BEAMS = 8;
	public static final double RADIANT_RANGE = 14.0;
	// v0.17.2 round 4 (user: "upgrade the vfx and animation on charge release, only make it deal 20 damage and release 20 energy please")
	public static final float RELEASE_DAMAGE = 20f;
	public static final int RELEASE_COST = 20, RELEASE_WINDUP = 8;
	public static final double RELEASE_RADIUS = 6.0;
	// v0.17.2 round 4 (user: "give him more attack moves on G and shift+G please")
	public static final float PUNCH_DAMAGE = 16f, PUNCH_BLAST = 10f;
	public static final double PUNCH_REACH = 4.5, PUNCH_RANGE = 12.0;
	public static final int PUNCH_WINDUP = 5, PUNCH_COOLDOWN = 80;
	public static final float SWEEP_DAMAGE = 19f;
	public static final int SWEEP_COST = 15, SWEEP_WINDUP = 6, SWEEP_TICKS = 12, SWEEP_COOLDOWN = 160;
	public static final double SWEEP_RANGE = 18.0;
	public static final float SWEEP_ARC = 120f;
	public static final int FLARE_COST = 15;
	public static final float FLARE_UNDEAD = 10f;
	public static final double FLARE_RADIUS = 8.0;
	public static final int STANCE_TICKS = 100;
	public static final int SHIELD_COST = 20, SHIELD_TICKS = 120;
	public static final float SHIELD_FACTOR = 0.3f;
	public static final int FIST_MIN = 25, FIST_COST = 2;
	public static final float FIST_BONUS = 5f;
	public static final int SPECTRUM_MIN = 50;
	public static final float SPECTRUM_BASE = 70f, SPECTRUM_PER_CHARGE = 0.8f;
	public static final int SPECTRUM_WINDUP = 15;
	public static final double SPECTRUM_RANGE = 32.0;
	public static final int OVERLOAD_TICKS = 200, OVERLOAD_LEFT = 40;
	public static final float OVERLOAD_DAMAGE = 16f, OVERLOAD_SELF = 4f;
	public static final double OVERLOAD_RADIUS = 4.0;

	/** v0.17.2 revamp: player -> how many hits the current Redirect has fired back. */
	private static final Map<UUID, Integer> RETURNS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: once per game tick (the sunlight / overload clocks must not double up: RipjawsKit.tick). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** v0.17.2 round 4: player -> the creatures the running Spectrum Sweep has already hit. */
	private static final Map<UUID, Set<Integer>> SWEPT = new ConcurrentHashMap<>();

	public static void register() {
		Aliens.register(new ChromastoneKit());
	}

	public static void clearSessionState() {
		RETURNS.clear();
		LAST_TICK.clear();
		SWEPT.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(34).speed(0.95f).armor(15, 2).attack(7).knockbackResist(0.4f).safeFall(3).build(); // v0.17.2 (user set the unarmed melee bonuses: "Swampfire +9, Echo Echo +4, Humungousaur +15, Jetray +5, Big Chill +6, Chromastone +7, Brainstorm +4, Spidermonkey +5, Goop +5")
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BOLT_MOVE, AbilitySlot.SLOT_1, 12), AlienMove.shift(PRISM_BEAM, AbilitySlot.SLOT_1, 60),
				AlienMove.tap(PRISM_PUNCH, AbilitySlot.SLOT_2, PUNCH_COOLDOWN), AlienMove.shift(SPECTRUM_SWEEP, AbilitySlot.SLOT_2, SWEEP_COOLDOWN),
				AlienMove.tap(FLIGHT, AbilitySlot.SLOT_3, 10), AlienMove.shift(LIGHT_DASH, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(RADIANT_BURST, AbilitySlot.SLOT_4, 160), AlienMove.shift(FULL_SPECTRUM, AbilitySlot.SLOT_4, 900),
				// v0.17.2 round 4: G's old moves kept -- tap V Redirect (hold V the Absorb Stance it shared the key with), hold C Refraction
				AlienMove.tap(REDIRECT, AbilitySlot.SLOT_5, 120), AlienMove.hold(ABSORB_STANCE, AbilitySlot.SLOT_5, 300), AlienMove.shift(PRISM_SHIELD, AbilitySlot.SLOT_5, 300),
				AlienMove.tap(CHARGE_RELEASE, AbilitySlot.SLOT_6, 200), AlienMove.hold(REFRACTION, AbilitySlot.SLOT_6, 120), AlienMove.shift(UV_FLARE, AbilitySlot.SLOT_6, 200),
				AlienMove.vanilla(CHARGED_FISTS, "Attack", 0));
	}

	// ---------------------------------------------------------------- the charge

	/** The charge, 0-100 (client-safe: the synced kit timer). */
	public static int charge(Player p) {
		return (int) Math.max(0L, Math.min(MAX_CHARGE, Omnitrix.state(p).readyAt(ID + "." + CHARGE)));
	}

	public void setCharge(ServerPlayer p, int value) {
		int v = Math.max(0, Math.min(MAX_CHARGE, value));
		if (v != charge(p)) {
			setTimer(p, CHARGE, v);
		}
		if (v < MAX_CHARGE && flag(p, F_OVER)) {
			setFlag(p, F_OVER, false); // spent below full: the overload is off
			setTimer(p, OVER_AT, 0);
		}
	}

	/** Adds absorbed charge; already full, the excess starts the Overload clock (10 s to spend some). */
	public void addCharge(ServerPlayer p, float amount) {
		int add = Math.max(1, Math.round(amount));
		int c = charge(p);
		if (c >= MAX_CHARGE && !flag(p, F_OVER)) {
			setFlag(p, F_OVER, true);
			setTimer(p, OVER_AT, p.level().getGameTime() + OVERLOAD_TICKS);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.chromastone.overcharged").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return;
		}
		setCharge(p, c + add);
	}

	/** Spends {@code cost} charge; false (and a line on the action bar) if there is not enough. */
	private boolean spend(ServerPlayer p, int cost, String moveId) {
		if (charge(p) < cost) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.chromastone.no_charge",
					Component.translatable("projecthero.omnitrix." + ID + "." + moveId), cost).withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return false;
		}
		setCharge(p, charge(p) - cost);
		return true;
	}

	/** Energy, fire, magic or a projectile: what his crystal drinks in. */
	public static boolean absorbable(DamageSource s) {
		if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		return s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypeTags.IS_LIGHTNING) || s.is(DamageTypeTags.IS_PROJECTILE) || s.is(DamageTypeTags.WITCH_RESISTANT_TO)
				|| s.is(DamageTypes.MAGIC) || s.is(DamageTypes.INDIRECT_MAGIC) || s.is(DamageTypes.SONIC_BOOM) || s.is(DamageTypes.WITHER)
				|| s.is(DamageTypes.DRAGON_BREATH) || s.is(com.projecthero.mod.omnitrix.OmnitrixDamage.ALIEN_FIRE);
	}

	/** Blunt force: blows, explosions, falling things -- they crack crystal. */
	public static boolean blunt(DamageSource s) {
		return s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypes.MOB_ATTACK) || s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.FALLING_BLOCK)
				|| s.is(DamageTypes.FALLING_ANVIL) || s.is(DamageTypes.FALLING_STALACTITE) || s.is(com.projecthero.mod.omnitrix.OmnitrixDamage.ALIEN_STRIKE);
	}

	/** Where his light leaves his body (chest height, a little forward). */
	private static Vec3 chest(ServerPlayer p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0).add(p.getLookAngle().scale(0.3));
	}

	private static void ray(ServerLevel l, Vec3 from, Vec3 to, int colour, float width, int life) {
		QuirkFx.ribbon(l, from, to).style(QuirkFx.RIBBON_LIGHT).colours(WHITE, colour).size(width).life(life).spawn();
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		setCharge(p, 0);
		OmnitrixSfx.playOn(p, ABSORB, 1.0f, 1.2f, SoundSource.PLAYERS);
	}

	private static final String[] STATE_TIMERS = { CHARGE, "stance_end", "spectrum_at", REDIRECT_END, SHIELD_END, OVER_AT, PUNCH_AT, SWEEP_AT, RELEASE_AT, "ray.0",
			"ray.1", "ray.2", "ray.3", "ray.4" };

	@Override
	public void onRevert(ServerPlayer p) {
		RETURNS.remove(p.getUUID());
		LAST_TICK.remove(p.getUUID());
		SWEPT.remove(p.getUUID());
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : STATE_TIMERS) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
		MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		if (slow != null && slow.getDuration() <= 12 && slow.getAmplifier() == 2) {
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
		RecalKitMath.groundWings(p);
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> flag(p, F_SWEEP);
			case SLOT_3 -> flag(p, F_FLYING);
			case SLOT_4 -> flag(p, F_SPECTRUM);
			case SLOT_5 -> flag(p, F_REDIRECT) || flag(p, F_STANCE) || flag(p, F_SHIELD);
			case SLOT_6 -> flag(p, F_RELEASE);
			default -> false;
		};
	}

	/** v0.17.2 round 4: V and C each carry a hold move, so their tap fires on the key-up. */
	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_5 || slot == AbilitySlot.SLOT_6;
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (busy(p)) {
			return;
		}
		if (slot == AbilitySlot.SLOT_5) {
			stance(p);
		} else if (slot == AbilitySlot.SLOT_6) {
			refraction(p);
		}
	}

	/** A timed move has the body: the other keys wait. */
	private boolean busy(ServerPlayer p) {
		return flag(p, F_SPECTRUM) || flag(p, F_PUNCH) || flag(p, F_SWEEP) || flag(p, F_RELEASE);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2 revamp: once per game tick
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		boolean flying = p.getAbilities().flying;
		if (flying != flag(p, F_FLYING)) {
			setFlag(p, F_FLYING, flying);
		}
		if (flying) {
			p.resetFallDistance();
		}
		// sunlight trickles in (Crystalsapiens feed on light): +1 every 2 s under the open daytime sky
		if (now % 40 == 0 && l.isDay() && !l.isRaining() && l.canSeeSky(BlockPos.containing(p.getEyePosition())) && charge(p) < MAX_CHARGE) {
			setCharge(p, charge(p) + 1);
		}
		if (p.isOnFire()) {
			p.clearFire(); // crystal does not burn
		}
		if (flag(p, F_STANCE)) {
			if (now >= timer(p, "stance_end")) {
				setFlag(p, F_STANCE, false);
				setTimer(p, "stance_end", 0);
			} else if (now % 5 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 2, false, false, false));
			}
		}
		if (flag(p, F_REDIRECT) && now >= timer(p, REDIRECT_END)) {
			setFlag(p, F_REDIRECT, false);
			setTimer(p, REDIRECT_END, 0);
			RETURNS.remove(p.getUUID());
		}
		if (flag(p, F_SHIELD) && now >= timer(p, SHIELD_END)) {
			setFlag(p, F_SHIELD, false);
			setTimer(p, SHIELD_END, 0);
		}
		if (flag(p, F_REFRACT) && now - s.animStart > 10) {
			setFlag(p, F_REFRACT, false);
		}
		if (flag(p, F_OVER) && now >= timer(p, OVER_AT)) {
			overload(p, l);
		}
		if (flag(p, F_SPECTRUM)) {
			p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
			p.hurtMarked = true;
			if (now >= timer(p, "spectrum_at")) {
				fireSpectrum(p, l);
			}
		}
		if (flag(p, F_RELEASE)) {
			p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0); // he plants himself as it comes in
			p.hurtMarked = true;
			if (now >= timer(p, RELEASE_AT)) {
				fireRelease(p, l);
			}
		}
		if (flag(p, F_PUNCH) && now >= timer(p, PUNCH_AT)) {
			landPunch(p, l);
		}
		if (flag(p, F_SWEEP)) {
			tickSweep(p, l, now);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (busy(p)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					prismBeam(p);
				} else {
					bolt(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					spectrumSweep(p);
				} else {
					prismPunch(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					lightDash(p);
				} else {
					flight(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					fullSpectrum(p);
				} else {
					radiantBurst(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					prismShield(p);
				} else if (flag(p, F_STANCE)) {
					stance(p); // a tap while it runs ends it
				} else {
					redirect(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					uvFlare(p);
				} else {
					release(p);
				}
			}
			default -> {
			}
		}
	}

	/** Charged Fists: with 25+ charge a blow flares with light (+3, 2 charge). */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le && charge(p) >= FIST_MIN) {
			// its own hit; vanilla's punch still lands after it
			hurt(p, le, FIST_BONUS, false);
			le.invulnerableTime = 0;
			setCharge(p, charge(p) - FIST_COST);
			QuirkFx.ring(level(p), le.getBoundingBox().getCenter()).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SOFT).colours(WHITE, VIOLET)
					.size(1.1f).thickness(0.25f).life(6).spawn();
			OmnitrixSfx.play(level(p), le.position(), BOLT, 0.7f, 0.8f);
		}
		return false;
	}

	// ---------------------------------------------------------------- R / Shift+R

	public boolean bolt(ServerPlayer p) {
		if (!use(p, BOLT_MOVE)) {
			return false;
		}
		anim(p, ANIM_BOLT);
		AlienProjectileEntity.of(p, "chromastone_bolt").colour(VIOLET).size(0.3f).damage(BOLT_DAMAGE).speed(2.6).life(24).spawn();
		OmnitrixSfx.play(level(p), p.getEyePosition(), BOLT, 1.0f, 1.0f);
		return true;
	}

	/** Shift+R: 20 charge for a beam that pierces everything in line (15 each). True if it fired. */
	public boolean prismBeam(ServerPlayer p) {
		if (cooldownLeft(p, PRISM_BEAM) > 0) {
			return use(p, PRISM_BEAM); // the "not ready" line
		}
		if (!spend(p, PRISM_COST, PRISM_BEAM) || !use(p, PRISM_BEAM)) {
			return false;
		}
		anim(p, ANIM_PRISM);
		ServerLevel l = level(p);
		for (LivingEntity e : RecalKitMath.line(p, p.getLookAngle(), 28.0, 0.6)) {
			hurt(p, e, PRISM_DAMAGE, false);
		}
		Vec3 end = RecalKitMath.beamEnd(p, p.getLookAngle(), 28.0);
		QuirkFx.ring(l, end).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SOFT).colours(WHITE, VIOLET).size(1.2f).thickness(0.3f).life(8)
				.spawn();
		QuirkCamera.shake(p, 0.1f, 4);
		OmnitrixSfx.play(l, p.getEyePosition(), PRISM, 1.2f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- G: Prism Punch / Shift+G: Spectrum Sweep

	/**
	 * G: v0.17.2 round 4 (user: "give him more attack moves on G and shift+G please"; the show: a crystal bruiser who hits hard and whose
	 * light goes through what it touches). A free move, since so much of his kit needs charge: he lunges, and the blow lands
	 * {@link #PUNCH_WINDUP} ticks later ({@link #landPunch}). True if it started.
	 */
	public boolean prismPunch(ServerPlayer p) {
		if (!use(p, PRISM_PUNCH)) {
			return false;
		}
		anim(p, ANIM_PUNCH);
		setFlag(p, F_PUNCH, true);
		setTimer(p, PUNCH_AT, p.level().getGameTime() + PUNCH_WINDUP);
		p.setDeltaMovement(p.getDeltaMovement().add(p.getLookAngle().scale(0.8)));
		p.hurtMarked = true;
		OmnitrixSfx.playOn(p, LEAP, 0.8f, 1.1f, SoundSource.PLAYERS);
		return true;
	}

	/** The Prism Punch lands: 16 to the first thing in reach 4.5, and its light bursts on through a 12-block line for 10. Public for tests. */
	public void landPunch(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_PUNCH, false);
		setTimer(p, PUNCH_AT, 0);
		Vec3 look = p.getLookAngle();
		List<LivingEntity> line = RecalKitMath.line(p, look, PUNCH_RANGE, 1.1);
		LivingEntity struck = !line.isEmpty() && line.get(0).distanceTo(p) <= PUNCH_REACH ? line.get(0) : null;
		for (LivingEntity e : line) {
			hurt(p, e, e == struck ? PUNCH_DAMAGE : PUNCH_BLAST, false);
			shove(e, p.position(), e == struck ? 1.0 : 0.4, 0.25);
		}
		Vec3 from = struck != null ? struck.getBoundingBox().getCenter() : p.getEyePosition().add(look.scale(1.6)).add(0, -0.3, 0);
		Vec3 end = RecalKitMath.beamEnd(p, look, PUNCH_RANGE);
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		for (int i = 0; i < SPECTRUM.length; i++) {
			// the light fans as it leaves the fist: seven colours out of one point
			QuirkFx.ribbon(l, from, end.add(side.scale((i - 3) * 0.55)).add(0, (i % 3 - 1) * 0.4, 0)).style(QuirkFx.RIBBON_LIGHT).colours(SPECTRUM[i], SPECTRUM[i]).size(0.22f).life(11)
					.spawn();
		}
		QuirkFx.ring(l, from).flags(QuirkFx.F_VERTICAL).vec(look).style(QuirkFx.RING_SHOCK).colours(WHITE, VIOLET).size(2.0f).thickness(0.35f).life(8)
				.ease(QuirkFx.Ease.OUT_EXPO).spawn();
		QuirkFx.shards(l, from).block(Blocks.AMETHYST_BLOCK.defaultBlockState()).size(0.5f).count(6).life(14).spawn();
		QuirkCamera.shake(p, 0.14f, 4);
		OmnitrixSfx.play(l, from, PUNCH, 1.1f, 1.0f);
	}

	/** How far round the Spectrum Sweep is, {@code t} ticks after it started: degrees off the aim, left (-60) to right (+60). Client-safe. */
	public static float sweepYaw(float t) {
		float k = Math.max(0f, Math.min(1f, (t - SWEEP_WINDUP) / SWEEP_TICKS));
		return (k * k * (3f - 2f * k) - 0.5f) * SWEEP_ARC;
	}

	/**
	 * Shift+G: v0.17.2 round 4 (user: "give him more attack moves on G and shift+G please"; the show: he plays his beam across a line of
	 * foes). 15 charge: a beam from his hand swept across 120 degrees from left to right, 19 to each creature it crosses. True if it started.
	 */
	public boolean spectrumSweep(ServerPlayer p) {
		if (cooldownLeft(p, SPECTRUM_SWEEP) > 0) {
			return use(p, SPECTRUM_SWEEP);
		}
		if (!spend(p, SWEEP_COST, SPECTRUM_SWEEP) || !use(p, SPECTRUM_SWEEP)) {
			return false;
		}
		anim(p, ANIM_SWEEP);
		setFlag(p, F_SWEEP, true);
		setTimer(p, SWEEP_AT, p.level().getGameTime());
		SWEPT.put(p.getUUID(), ConcurrentHashMap.newKeySet());
		OmnitrixSfx.playOn(p, STANCE, 0.8f, 1.5f, SoundSource.PLAYERS);
		return true;
	}

	private void tickSweep(ServerPlayer p, ServerLevel l, long now) {
		long t = now - timer(p, SWEEP_AT);
		if (t > SWEEP_WINDUP + SWEEP_TICKS) {
			setFlag(p, F_SWEEP, false);
			setTimer(p, SWEEP_AT, 0);
			SWEPT.remove(p.getUUID());
			return;
		}
		if (t < SWEEP_WINDUP) {
			return;
		}
		if (t == SWEEP_WINDUP) {
			OmnitrixSfx.play(l, p.getEyePosition(), SWEEP, 1.2f, 1.0f);
			QuirkCamera.shake(p, 0.1f, SWEEP_TICKS);
		}
		Set<Integer> done = SWEPT.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet());
		Vec3 look = p.getLookAngle();
		// three rays a tick, so the fast middle of the sweep leaves no gaps at full range
		for (int i = 0; i < 3; i++) {
			Vec3 dir = RecalKitMath.turned(look, sweepYaw(t - i / 3f));
			for (LivingEntity e : RecalKitMath.line(p, dir, SWEEP_RANGE, 0.9)) {
				if (done.add(e.getId())) {
					hurt(p, e, SWEEP_DAMAGE, false);
					QuirkFx.ring(l, e.getBoundingBox().getCenter()).flags(QuirkFx.F_VERTICAL).vec(dir).style(QuirkFx.RING_SOFT).colours(WHITE, SPECTRUM[done.size() % SPECTRUM.length])
							.size(1.3f).thickness(0.28f).life(7).spawn();
				}
			}
		}
		if (t % 2 == 0) {
			Vec3 end = RecalKitMath.beamEnd(p, RecalKitMath.turned(look, sweepYaw(t)), SWEEP_RANGE);
			if (end.distanceTo(p.getEyePosition()) < SWEEP_RANGE - 0.2) {
				QuirkFx.decal(l, end.add(0, 0.03, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x3A1850).size(0.9f).life(70).spawn(); // the beam scores what it crosses
			}
		}
	}

	// ---------------------------------------------------------------- V: Redirect / Hold C: Refraction

	/** V: for 1.5 s, energy hits are absorbed whole and fired back at their sender. True if the window opened. */
	public boolean redirect(ServerPlayer p) {
		if (flag(p, F_REDIRECT) || !use(p, REDIRECT)) {
			return false;
		}
		anim(p, ANIM_REDIRECT);
		setFlag(p, F_REDIRECT, true);
		setTimer(p, REDIRECT_END, p.level().getGameTime() + REDIRECT_TICKS);
		RETURNS.put(p.getUUID(), 0);
		OmnitrixSfx.playOn(p, STANCE, 0.9f, 1.4f, SoundSource.PLAYERS);
		return true;
	}

	/** Fires an absorbed hit of {@code amount} back at {@code at} (1.5x, 8 to 17). Public for tests. */
	public void fireBack(ServerPlayer p, LivingEntity at, float amount) {
		float dmg = Math.max(REDIRECT_MIN, Math.min(REDIRECT_CAP, amount * REDIRECT_FACTOR));
		hurt(p, at, dmg, false);
		ServerLevel l = level(p);
		ray(l, chest(p), at.getBoundingBox().getCenter(), VIOLET, 0.35f, 10);
		QuirkFx.ring(l, at.getBoundingBox().getCenter()).flags(QuirkFx.F_VERTICAL).vec(at.position().subtract(p.position())).style(QuirkFx.RING_SOFT)
				.colours(WHITE, VIOLET).size(1.4f).thickness(0.3f).life(8).spawn();
		anim(p, ANIM_RETURN);
		OmnitrixSfx.play(l, p.getEyePosition(), RETURN, 1.1f, 1.0f);
	}

	public boolean refraction(ServerPlayer p) {
		if (cooldownLeft(p, REFRACTION) > 0) {
			return use(p, REFRACTION);
		}
		List<LivingEntity> targets = around(p, p.position(), 16).stream().filter(p::hasLineOfSight)
				.sorted(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(5).toList();
		if (targets.isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.chromastone.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!spend(p, REFRACT_COST, REFRACTION) || !use(p, REFRACTION)) {
			return false;
		}
		anim(p, ANIM_REFRACT);
		setFlag(p, F_REFRACT, true);
		for (int i = 0; i < 5; i++) {
			setTimer(p, "ray." + i, i < targets.size() ? targets.get(i).getId() + 1L : 0L);
		}
		ServerLevel l = level(p);
		Vec3 from = chest(p);
		int i = 0;
		for (LivingEntity e : targets) {
			hurt(p, e, REFRACT_DAMAGE, false);
			ray(l, from, e.getBoundingBox().getCenter(), SPECTRUM[(i++ * 2) % SPECTRUM.length], 0.3f, 10);
		}
		OmnitrixSfx.play(l, p.getEyePosition(), PRISM, 1.2f, 1.3f);
		return true;
	}

	// ---------------------------------------------------------------- X: Light Flight / Shift+X: Light Dash

	public boolean flight(ServerPlayer p) {
		if (!use(p, FLIGHT)) {
			return false;
		}
		boolean fly = !p.getAbilities().flying;
		RecalKitMath.wings(p, fly, FLY_SPEED);
		if (!fly && !RecalKitMath.creative(p)) {
			p.getAbilities().mayfly = false;
			p.onUpdateAbilities();
		}
		setFlag(p, F_FLYING, fly);
		if (fly) {
			anim(p, ANIM_TAKEOFF);
			p.setDeltaMovement(p.getDeltaMovement().add(0, 0.45, 0));
			p.hurtMarked = true;
			QuirkFx.ring(level(p), p.position().add(0, 0.05, 0)).style(QuirkFx.RING_SOFT).colours(WHITE, VIOLET).size(2.0f).thickness(0.35f).life(10).ground()
					.spawn();
			OmnitrixSfx.playOn(p, LEAP, 1.0f, 1.2f, SoundSource.PLAYERS);
		} else {
			anim(p, ANIM_LAND);
		}
		return true;
	}

	/** Shift+X: 10 charge, a streak of light along the look; 6 to everything along the 8 blocks ahead. True if it went. */
	public boolean lightDash(ServerPlayer p) {
		if (cooldownLeft(p, LIGHT_DASH) > 0) {
			return use(p, LIGHT_DASH);
		}
		if (!spend(p, DASH_COST, LIGHT_DASH) || !use(p, LIGHT_DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		for (LivingEntity e : RecalKitMath.line(p, p.getLookAngle(), DASH_LENGTH, 0.8)) {
			hurt(p, e, DASH_DAMAGE, false);
		}
		QuirkFx.trail(level(p), p).style(QuirkFx.RIBBON_LIGHT).colours(WHITE, VIOLET).size(0.5f).life(14).spawn();
		p.setDeltaMovement(p.getLookAngle().scale(2.6));
		p.hurtMarked = true;
		p.resetFallDistance();
		OmnitrixSfx.playOn(p, FLASH, 0.8f, 1.2f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- Z: Radiant Burst / Shift+Z: Full Spectrum

	/** Z: beams out of his whole body in 8 directions (13 each; with 25+ charge it spends 25 and they hit 17). True if it fired. */
	public boolean radiantBurst(ServerPlayer p) {
		if (!use(p, RADIANT_BURST)) {
			return false;
		}
		anim(p, ANIM_RADIANT);
		boolean charged = charge(p) >= RADIANT_COST;
		if (charged) {
			setCharge(p, charge(p) - RADIANT_COST);
		}
		float dmg = charged ? RADIANT_CHARGED : RADIANT_DAMAGE;
		ServerLevel l = level(p);
		Vec3 flat = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 from = p.position().add(0, p.getBbHeight() * 0.62, 0);
		Set<Integer> hit = new HashSet<>();
		for (int i = 0; i < RADIANT_BEAMS; i++) {
			Vec3 dir = RecalKitMath.turned(flat, i * 360.0 / RADIANT_BEAMS);
			LivingEntity t = RecalKitMath.first(p, dir, RADIANT_RANGE, 0.6);
			if (t != null && hit.add(t.getId())) {
				hurt(p, t, dmg, false);
			}
			Vec3 end = t != null ? t.getBoundingBox().getCenter() : RecalKitMath.beamEnd(p, dir, RADIANT_RANGE);
			ray(l, from, new Vec3(end.x, t != null ? end.y : from.y, end.z), SPECTRUM[i % SPECTRUM.length], charged ? 0.4f : 0.3f, 12);
		}
		QuirkFx.ring(l, from).style(QuirkFx.RING_SOFT).colours(WHITE, VIOLET).size(2.4f).thickness(0.4f).life(10).spawn();
		QuirkCamera.shake(p, 0.15f, 5);
		OmnitrixSfx.play(l, from, PRISM, 1.3f, 0.9f);
		return true;
	}

	public boolean fullSpectrum(ServerPlayer p) {
		if (cooldownLeft(p, FULL_SPECTRUM) > 0) {
			return use(p, FULL_SPECTRUM);
		}
		if (charge(p) < SPECTRUM_MIN) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.chromastone.no_charge",
					Component.translatable("projecthero.omnitrix." + ID + "." + FULL_SPECTRUM), SPECTRUM_MIN).withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return false;
		}
		if (!use(p, FULL_SPECTRUM)) {
			return false;
		}
		anim(p, ANIM_SPECTRUM);
		setFlag(p, F_SPECTRUM, true);
		setTimer(p, "spectrum_at", p.level().getGameTime() + SPECTRUM_WINDUP);
		QuirkCamera.vignette(p, VIOLET, 0.4f, SPECTRUM_WINDUP + 8);
		OmnitrixSfx.playOn(p, STANCE, 1.3f, 1.4f, SoundSource.PLAYERS);
		return true;
	}

	/** The Full Spectrum beam: 40 + 0.6 per charge over 50 (70 at full) to everything in a 32-block line; the charge is spent. */
	public void fireSpectrum(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_SPECTRUM, false);
		setTimer(p, "spectrum_at", 0);
		int c = charge(p);
		float dmg = SPECTRUM_BASE + Math.max(0, c - SPECTRUM_MIN) * SPECTRUM_PER_CHARGE;
		setCharge(p, 0);
		for (LivingEntity e : RecalKitMath.line(p, p.getLookAngle(), SPECTRUM_RANGE, 1.3)) {
			hurt(p, e, dmg, false);
			shove(e, p.position(), 1.0, 0.3);
		}
		Vec3 end = RecalKitMath.beamEnd(p, p.getLookAngle(), SPECTRUM_RANGE);
		QuirkFx.ring(l, chest(p)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SHOCK).colours(WHITE, VIOLET).size(2.6f).thickness(0.5f)
				.life(10).spawn();
		QuirkFx.blast(l, end).colours(WHITE, VIOLET).size(1.8f).life(14).flags(QuirkFx.F_NO_SMOKE).spawn();
		QuirkFx.decal(l, end).style(QuirkFx.DECAL_SCORCH).colour(0x3A1850).size(2.2f).life(200).ground().spawn();
		QuirkCamera.shake(p, 0.35f, 10);
		QuirkCamera.shakeAround(l, end, 10, 0.3f, 8);
		OmnitrixSfx.play(l, p.getEyePosition(), SPECTRUM_SND, 1.6f, 1.0f);
	}

	// ---------------------------------------------------------------- Hold V: Absorb Stance / Shift+V: Prism Shield

	public boolean stance(ServerPlayer p) {
		if (flag(p, F_STANCE)) {
			setFlag(p, F_STANCE, false);
			setTimer(p, "stance_end", 0);
			return true;
		}
		if (!use(p, ABSORB_STANCE)) {
			return false;
		}
		anim(p, ANIM_STANCE);
		setFlag(p, F_STANCE, true);
		setTimer(p, "stance_end", p.level().getGameTime() + STANCE_TICKS);
		OmnitrixSfx.playOn(p, STANCE, 1.0f, 1.0f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+V: 20 charge, 6 s of a hard-light wall in front. True if it went up. */
	public boolean prismShield(ServerPlayer p) {
		if (cooldownLeft(p, PRISM_SHIELD) > 0) {
			return use(p, PRISM_SHIELD);
		}
		if (!spend(p, SHIELD_COST, PRISM_SHIELD) || !use(p, PRISM_SHIELD)) {
			return false;
		}
		anim(p, ANIM_SHIELD);
		setFlag(p, F_SHIELD, true);
		setTimer(p, SHIELD_END, p.level().getGameTime() + SHIELD_TICKS);
		OmnitrixSfx.playOn(p, STANCE, 1.0f, 1.6f, SoundSource.PLAYERS);
		return true;
	}

	/** Whether a hit from {@code source} comes from in front of him (within 75 degrees of where he looks). */
	public static boolean fromFront(Player p, DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null && source.getDirectEntity() != null) {
			from = source.getDirectEntity().position();
		}
		if (from == null) {
			return false;
		}
		Vec3 d = from.subtract(p.position());
		Vec3 h = new Vec3(d.x, 0, d.z);
		Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		if (h.lengthSqr() < 1.0e-4 || look.lengthSqr() < 1.0e-4) {
			return true;
		}
		return h.normalize().dot(look.normalize()) >= Math.cos(Math.toRadians(75));
	}

	// ---------------------------------------------------------------- C: Charge Release / Shift+C: UV Flare

	/**
	 * C: v0.17.2 round 4 (user: "upgrade the vfx and animation on charge release, only make it deal 20 damage and release 20 energy
	 * please"). 20 charge, not all of it. He plants himself and draws the light into his chest for {@link #RELEASE_WINDUP} ticks, then
	 * it bursts out of him ({@link #fireRelease}). True if it started.
	 */
	public boolean release(ServerPlayer p) {
		if (cooldownLeft(p, CHARGE_RELEASE) > 0) {
			return use(p, CHARGE_RELEASE);
		}
		if (!spend(p, RELEASE_COST, CHARGE_RELEASE) || !use(p, CHARGE_RELEASE)) {
			return false;
		}
		anim(p, ANIM_RELEASE);
		setFlag(p, F_RELEASE, true);
		setTimer(p, RELEASE_AT, p.level().getGameTime() + RELEASE_WINDUP);
		OmnitrixSfx.playOn(p, GATHER, 1.1f, 1.0f, SoundSource.PLAYERS);
		return true;
	}

	/** The Charge Release goes off: 20 to everything within 6. Its rings of the spectrum are the client's, from the synced clip. Public for tests. */
	public void fireRelease(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_RELEASE, false);
		setTimer(p, RELEASE_AT, 0);
		for (LivingEntity e : around(p, p.position(), RELEASE_RADIUS)) {
			hurt(p, e, RELEASE_DAMAGE, false);
			shove(e, p.position(), 0.9, 0.35);
		}
		Vec3 c = p.position();
		// flung from a ring round him, never through his own view
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			QuirkFx.shards(l, c.add(Math.cos(a) * 2.6, 0.3, Math.sin(a) * 2.6)).block(Blocks.AMETHYST_BLOCK.defaultBlockState()).size(0.6f).count(3).life(20).spawn();
		}
		QuirkFx.decal(l, c.add(0, 0.03, 0)).style(QuirkFx.DECAL_CRACK).colour(0xB070FF).size(3.0f).yaw(p.getYRot()).life(120).spawn();
		QuirkCamera.shakeAround(l, c, 10, 0.28f, 7);
		OmnitrixSfx.play(l, c, DISCHARGE, 1.4f, 1.0f);
	}

	/** Shift+C: 15 charge, a flash of ultraviolet -- blinded 3 s and outlined; the undead burn (6, 4 s). True if it went off. */
	public boolean uvFlare(ServerPlayer p) {
		if (cooldownLeft(p, UV_FLARE) > 0) {
			return use(p, UV_FLARE);
		}
		if (!spend(p, FLARE_COST, UV_FLARE) || !use(p, UV_FLARE)) {
			return false;
		}
		anim(p, ANIM_FLARE);
		for (LivingEntity e : around(p, p.position(), FLARE_RADIUS)) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), p);
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false), p);
			com.projecthero.mod.quirk.QuirkThreat.add(e, p, 3f);
			if (e.getType().is(EntityTypeTags.UNDEAD)) {
				hurt(p, e, FLARE_UNDEAD, false);
				e.igniteForSeconds(4); // the ultraviolet burns them like the sun
			}
		}
		ServerLevel l = level(p);
		QuirkFx.blast(l, p.position().add(0, 1.2, 0)).colours(WHITE, UV).size((float) FLARE_RADIUS * 0.6f).life(10).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING)
				.spawn();
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).style(QuirkFx.RING_SOFT).colours(WHITE, UV).size((float) FLARE_RADIUS).thickness(0.5f).life(12).ground()
				.spawn();
		OmnitrixSfx.play(l, p.position(), FLASH, 1.2f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- Overload

	/** The Overload: the light bursts out of him -- 10 round him (r 4), 4 to him, the charge down to 40. Public for tests. */
	public void overload(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_OVER, false);
		setTimer(p, OVER_AT, 0);
		setTimer(p, CHARGE, OVERLOAD_LEFT);
		anim(p, ANIM_OVERLOAD);
		for (LivingEntity e : around(p, p.position(), OVERLOAD_RADIUS)) {
			hurt(p, e, OVERLOAD_DAMAGE, false);
			shove(e, p.position(), 0.6, 0.3);
		}
		p.invulnerableTime = 0;
		p.hurt(l.damageSources().generic(), OVERLOAD_SELF);
		QuirkFx.blast(l, p.position().add(0, 1.0, 0)).colours(WHITE, VIOLET).size((float) OVERLOAD_RADIUS).life(14).flags(QuirkFx.F_NO_SMOKE).spawn();
		QuirkCamera.shake(p, 0.4f, 8);
		OmnitrixSfx.play(l, p.position(), OVERLOAD, 1.3f, 1.0f);
	}

	// ---------------------------------------------------------------- damage: absorption

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !(source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.CAMPFIRE)); // crystal does not burn
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		boolean stance = flag(p, F_STANCE) && !bypass;
		boolean absorb = absorbable(source);
		// only when the hit would land (not inside the hurt cooldown), so nothing is farmed from i-frames
		boolean lands = p.invulnerableTime <= 10;
		// Redirect: an energy hit is drunk whole and fired straight back
		if (absorb && flag(p, F_REDIRECT) && lands) {
			int n = RETURNS.getOrDefault(p.getUUID(), 0);
			LivingEntity sender = source.getEntity() instanceof LivingEntity le && le != p ? le : null;
			if (source.getDirectEntity() instanceof Projectile proj) {
				proj.discard();
			}
			addCharge(p, amount * CHARGE_PER_DAMAGE);
			if (sender != null && n < REDIRECT_MAX) {
				RETURNS.put(p.getUUID(), n + 1);
				fireBack(p, sender, amount);
			}
			return 0f;
		}
		// Prism Shield: the front is a wall of hard light
		if (flag(p, F_SHIELD) && !bypass && fromFront(p, source)) {
			if (absorb && lands) {
				addCharge(p, amount * CHARGE_PER_DAMAGE);
				if (source.getDirectEntity() instanceof Projectile proj) {
					proj.discard();
				}
				OmnitrixSfx.play(level(p), p.position(), ABSORB, 0.8f, 1.2f);
				return 0f;
			}
			amount *= SHIELD_FACTOR;
		}
		if (stance || absorb) {
			if (lands) {
				if (source.is(DamageTypeTags.IS_LIGHTNING)) {
					setCharge(p, MAX_CHARGE); // lightning fills him
				} else {
					addCharge(p, amount * CHARGE_PER_DAMAGE * (stance ? 2f : 1f));
				}
				OmnitrixSfx.play(level(p), p.position(), ABSORB, 0.8f, 1.0f);
				QuirkFx.ring(level(p), p.position().add(0, p.getBbHeight() * 0.55, 0)).style(QuirkFx.RING_SOFT).colours(WHITE, VIOLET).size(1.2f).thickness(0.25f)
						.life(6).spawn();
			}
			return stance ? 0f : amount * ABSORB_LEAK;
		}
		if (blunt(source)) {
			OmnitrixSfx.play(level(p), p.position(), CRACK, 0.8f, 1.0f);
			return amount * 1.25f;
		}
		return amount;
	}
}
