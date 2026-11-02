package com.projecthero.mod.omnitrix.alien.wildmutt;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Wildmutt, the Vulpimancer -- the first "real" alien (user: "we're gonna try to move out of demo and build real
 * aliens now"). A big orange, blind, ape / dog-like quadruped: no eyes, he hunts by smell and sound (the gills on his neck),
 * fast and brutally strong, quills on his back he can fire (lore: adult Vulpimancers launch them; Ben's are short but sharp).
 * His body is a custom GeckoLib quadruped ({@code WildmuttRenderer}) with a low, wide hitbox ({@link #body}).
 *
 * <pre>
 *   R        Pounce        lunge onto the creature ahead (12 r): 12, pins it   Shift+R  Maul          rear up: 3 rakes (3.8) + a double slam (6.6) = 18
 *   G        Quill Volley  7 quills from the back in a cone (3 each)            Shift+G  Savage Roar   cone 9: 6, slowed, weakened, staggered; a sound
 *                                                                                                      ping (r 24) outlines everything for him + squad 6 s
 *   X        Leap          a huge bound (no fall damage, landing shock)         Hold X   Coiled Leap   up to 1.5 s of coil: up to 1.8x as far, and the
 *                                                                                                      landing SLAMS (r 3-6, 10-22 by coil + fall)
 *                                                                               Shift+X  Wall Climb    toggle: press into a wall to scale it; Jump while
 *                                                                                                      climbing = a wall-kick (a ~6-block bound)
 *   Z        Ricochet Hunt pounce from prey to prey: up to 4 hits of 8        Shift+Z  Quill Roll    ULTIMATE: 1.5 s spiked roll (12 a hit, -40% taken),
 *                          (one lone target is hit twice)                                              then a quill burst (14, r 4)
 *   C        Feral Frenzy  8 s: +25% speed, +4 claws, +1 attack speed, heals    Shift+C  Scent Track   marks a creature (48 r) for 30 s: a trail to it
 *   V        Dig           a 3x3 tunnel along the look                          Shift+V  Deep Sniff    6 s: senses 64, ores / chests, 20 s of footprints
 *   Attack   claw swipe (cleaves the creatures beside the target); every 3rd hit is a Bite (+3, short slow)
 *   Use      eats and drinks as usual (v0.17.2: Sniff moved to Shift+V)
 *   Passive  Scent Sense: creatures within 24 are outlined for him (invisible ones too) -- bright while they move, faint while
 *            they stand still or sneak, and those only within 8; gallops (+20% while sprinting); no eyes: immune to Blindness and
 *            Darkness; Predator's Appetite: a kill gives back 2 hunger and a heart (5 s apart).
 *   Weakness underwater his senses shrink to 6 and his air runs out twice as fast; an explosion close by deafens his senses for
 *            3 s; Choked Nose: smoke and stench (campfire smoke, Stinkfly's gas, lingering potion clouds, powder snow) shrink his
 *            senses to 4 for 5 s, the screen edge going dark, and he sneezes it out.
 * </pre>
 * <p>v0.17.2 revamp (docs/OMNITRIX_REVAMP_WILDMUTT_GHOSTFREAK.md, approved): the blind hunter -- what he senses depends on
 * movement and smell, he chains attacks between targets, and Ground Slam left Z to become the landing of the Coiled Leap.
 */
public final class WildmuttKit extends AlienKit {
	public static final String ID = "wildmutt";
	public static final int COLOUR = 0xF08A24;

	// ---------------------------------------------------------------- sounds (OmnitrixSfx recipes from vanilla samples;
	// docs/OMNITRIX_SOUNDS_0171.md -- pitched-down beasts, quiet and natural, nothing synthesised)
	/** His snarl: a low wolf growl under a pitched-up ravager rumble. */
	public static final OmnitrixSfx.Cue GROWL = OmnitrixSfx.cue("wildmutt_growl")
			.layer(SoundEvents.WOLF_GROWL, 0.55f, 0.6f)
			.layer(SoundEvents.RAVAGER_AMBIENT, 0.25f, 1.3f)
			.gap(30);
	/** The bite: a ravager chomp and a fox snap. */
	public static final OmnitrixSfx.Cue BITE = OmnitrixSfx.cue("wildmutt_bite")
			.layer(SoundEvents.RAVAGER_ATTACK, 0.5f, 1.35f)
			.layer(SoundEvents.FOX_BITE, 0.5f, 0.7f);
	/** A claw rake. */
	public static final OmnitrixSfx.Cue CLAW = OmnitrixSfx.cue("wildmutt_claw")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 1.3f)
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.25f, 0.7f)
			.jitter(0.12f);
	/** The roar: a ravager roar with a polar bear's warning under it. */
	public static final OmnitrixSfx.Cue ROAR = OmnitrixSfx.cue("wildmutt_roar")
			.layer(SoundEvents.RAVAGER_ROAR, 0.65f, 1.1f)
			.layer(SoundEvents.POLAR_BEAR_WARNING, 0.5f, 0.65f)
			.layer(SoundEvents.WOLF_GROWL, 0.4f, 0.5f, 4);
	/** The quills bristling (a shake) and flying (a hiss of darts). */
	public static final OmnitrixSfx.Cue BRISTLE = OmnitrixSfx.cue("wildmutt_bristle")
			.layer(SoundEvents.WOLF_SHAKE, 0.55f, 0.75f)
			.layer(SoundEvents.WOLF_GROWL, 0.3f, 0.8f);
	public static final OmnitrixSfx.Cue QUILLS = OmnitrixSfx.cue("wildmutt_quills")
			.layer(SoundEvents.ARROW_SHOOT, 0.4f, 1.5f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.25f, 1.8f);
	/** Both fists into the ground. */
	public static final OmnitrixSfx.Cue SLAM = OmnitrixSfx.cue("wildmutt_slam")
			.layer(SoundEvents.RAVAGER_STEP, 0.72f, 0.5f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.18f, 1.6f)
			.layer(SoundEvents.POLAR_BEAR_WARNING, 0.3f, 0.9f);
	/** A bound: the push-off and a pant. */
	public static final OmnitrixSfx.Cue LEAP = OmnitrixSfx.cue("wildmutt_leap")
			.layer(SoundEvents.RAVAGER_STEP, 0.45f, 1.2f)
			.layer(SoundEvents.WOLF_PANT, 0.35f, 0.75f);
	public static final OmnitrixSfx.Cue LAND = OmnitrixSfx.cue("wildmutt_land")
			.layer(SoundEvents.RAVAGER_STEP, 0.7f, 0.8f)
			.layer(SoundEvents.POLAR_BEAR_STEP, 0.5f, 0.7f);
	/** Sniffing through the gills. */
	public static final OmnitrixSfx.Cue SNIFF = OmnitrixSfx.cue("wildmutt_sniff")
			.layer(SoundEvents.SNIFFER_SNIFFING, 0.45f, 1.3f)
			.layer(SoundEvents.FOX_SNIFF, 0.4f, 0.75f);
	/** The frenzy: a snarling growl into a short roar. */
	public static final OmnitrixSfx.Cue FRENZY_CUE = OmnitrixSfx.cue("wildmutt_frenzy")
			.layer(SoundEvents.WOLF_GROWL, 0.6f, 0.9f)
			.layer(SoundEvents.RAVAGER_ROAR, 0.35f, 1.5f, 6);
	/** Rolling: an armadillo's roll, pitched down. */
	public static final OmnitrixSfx.Cue ROLL_CUE = OmnitrixSfx.cue("wildmutt_roll")
			.layer(SoundEvents.ARMADILLO_ROLL, 0.5f, 0.7f)
			.layer(SoundEvents.RAVAGER_STEP, 0.3f, 1.1f);
	/** v0.17.2: Choked Nose clearing -- a big beast's sneeze (a panda's, pitched down) and a snort. */
	public static final OmnitrixSfx.Cue SNEEZE = OmnitrixSfx.cue("wildmutt_sneeze")
			.layer(SoundEvents.PANDA_SNEEZE, 0.7f, 0.6f)
			.layer(SoundEvents.SNIFFER_SNIFFING, 0.35f, 0.8f, 3);
	/** v0.17.2: the Savage Roar's ping coming back -- a low rolling echo under the roar. */
	public static final OmnitrixSfx.Cue PING = OmnitrixSfx.cue("wildmutt_ping")
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.25f, 1.6f, 4)
			.layer(SoundEvents.RAVAGER_ROAR, 0.2f, 0.5f, 10);

	// ---------------------------------------------------------------- anims (the client kit maps them to GeckoLib clips)
	public static final int ANIM_POUNCE = 1;
	public static final int ANIM_MAUL = 2;
	public static final int ANIM_QUILLS = 3;
	public static final int ANIM_ROAR = 4;
	public static final int ANIM_LEAP = 5;
	public static final int ANIM_COIL = 6;
	public static final int ANIM_SLAM = 7;
	public static final int ANIM_ROLL = 8;
	public static final int ANIM_FRENZY = 9;
	public static final int ANIM_TRACK = 10;
	public static final int ANIM_SNIFF = 11;
	/** v0.17.2: one hop of the Ricochet Hunt (the pounce with a twist in the air). */
	public static final int ANIM_HUNT = 12;
	/** v0.17.2: the sneeze that clears a Choked Nose. */
	public static final int ANIM_SNEEZE = 13;
	/** v0.17.2: the push-off of a wall-kick. */
	public static final int ANIM_WALL_KICK = 14;

	// ---------------------------------------------------------------- synced bits
	public static final int F_CLIMB = 1;
	public static final int F_LEAPING = 2;
	public static final int F_POUNCE = 4;
	public static final int F_ROLL = 8;
	public static final int F_FRENZY = 16;
	public static final int F_SNIFF = 32;
	public static final int F_COIL = 64;
	/** v0.17.2: a Ricochet Hunt is running. */
	public static final int F_HUNT = 128;

	// ---------------------------------------------------------------- moves
	public static final String POUNCE = "pounce";
	public static final String MAUL = "maul";
	public static final String QUILL_VOLLEY = "quill_volley";
	public static final String ROAR_MOVE = "savage_roar";
	public static final String LEAP_MOVE = "leap";
	public static final String COILED_LEAP = "coiled_leap";
	public static final String CLIMB = "wall_climb";
	/** v0.17.2 revamp: Z (was Ground Slam, now the Coiled Leap's landing). */
	public static final String HUNT = "ricochet_hunt";
	public static final String ROLL = "quill_roll";
	public static final String FRENZY = "feral_frenzy";
	public static final String TRACK = "scent_track";
	public static final String SWIPE = "claw_swipe";
	public static final String SNIFF_MOVE = "sniff";
	public static final String DIG = "dig";

	// ---------------------------------------------------------------- numbers
	public static final float HEALTH = 34f;
	public static final float SPEED = 1.35f;
	public static final float ATTACK = 4f;
	/** v0.17.1 rework 2: the slim, leggy body -- 1.48 blocks at the shoulders, the head's top level with them. */
	public static final float BODY_WIDTH = 0.9f, BODY_HEIGHT = 1.55f, EYE_HEIGHT = 1.25f;
	public static final float CROUCH_HEIGHT = 1.3f, CROUCH_EYE = 1.05f;

	public static final double POUNCE_RANGE = 12.0;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 9
	// v0.17.2 (user: "adjust the other combat aliens damage aswell please to try and match this"): on Four Arms / Diamondhead's scale -- quick hits 5-8, moves 12-17, big ones 17-20, ultimates 21-36
	public static final float POUNCE_DAMAGE = 14f;
	public static final int POUNCE_TICKS = 16;
	public static final int PIN_TICKS = 25;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3.5 / 6 (16.5 in
	// all) -- scaled to 3 x 3.8 + 6.6 = 18
	public static final float MAUL_RAKE = 3.8f, MAUL_SLAM = 6.6f;
	public static final int[] MAUL_HITS = { 4, 7, 10, 16 };
	public static final double MAUL_RANGE = 3.4;
	public static final int QUILL_COUNT = 7;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3.5 (7 quills = 21)
	public static final float QUILL_DAMAGE = 3f;
	public static final int QUILL_DELAY = 9;
	public static final double ROAR_RANGE = 9.0;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3
	public static final float ROAR_DAMAGE = 8f;
	public static final int ROAR_DELAY = 6;
	public static final int ROAR_EFFECT_TICKS = 70;
	public static final int COIL_MAX = 30;
	/**
	 * v0.17.2 revamp (user: Ground Slam "becomes the landing hit of Coiled Leap"): a coiled leap lands in a slam -- radius
	 * {@code 3 + 1.5 x coil + 0.2 x fall} (up to 6), {@code 10 + 6 x coil + 0.75 x fall} at the centre (up to 22), 60% of that at the
	 * edge, a shove and Slowness II for 2 s. A full coil on the flat is the old Ground Slam (r 4.5, 16 / ~10).
	 */
	public static final float SLAM_RADIUS = 3f, SLAM_RADIUS_COIL = 1.5f, SLAM_RADIUS_FALL = 1.5f;
	public static final float SLAM_NEAR = 10f, SLAM_NEAR_COIL = 6f, SLAM_NEAR_FALL = 6f, SLAM_EDGE = 0.6f;
	public static final int SLAM_SLOW_TICKS = 40;
	/** v0.17.2 revamp, Z: Ricochet Hunt -- first prey within 10, each next within 8 of the last, up to 4 hits of 8, a hop every 6 ticks. */
	public static final double HUNT_RANGE = 10.0, HUNT_HOP = 8.0;
	public static final int HUNT_HITS_MAX = 4, HUNT_HOP_TICKS = 6;
	public static final float HUNT_DAMAGE = 9f;
	/** v0.17.2 revamp, Shift+G: the roar's sound ping -- everything within 24 is outlined for him and his squad for 6 s. */
	public static final double PING_RANGE = 24.0;
	public static final int PING_TICKS = 120;
	/** v0.17.2 revamp: a wall-kick off a climbed wall -- a ~6-block bound the way he looks (no cooldown). */
	public static final double WALL_KICK_SPEED = 0.8, WALL_KICK_UP = 0.5;
	/** v0.17.2 revamp: every 3rd claw hit is a Bite: +3 and Slowness I for 1.5 s. */
	public static final int BITE_EVERY = 3, BITE_SLOW_TICKS = 30;
	public static final float BITE_BONUS = 3f;
	/** v0.17.2 revamp: Predator's Appetite -- a kill gives back 2 hunger and a heart, at most once every 5 s. */
	public static final int APPETITE_FOOD = 2, APPETITE_COOLDOWN = 100;
	public static final float APPETITE_HEAL = 2f;
	/** v0.17.2 revamp: Choked Nose -- 5 s with his senses down to 4 blocks. */
	public static final int CHOKE_TICKS = 100;
	public static final double CHOKE_RANGE = 4.0;
	/** v0.17.2 revamp: Scent Sense -- a creature standing still or sneaking is only sensed (faintly) within this. */
	public static final double STILL_RANGE = 8.0;
	/** v0.17.2 revamp: Deep Sniff shows the footprints left this long ago or less. */
	public static final int FOOTPRINT_TICKS = 400;
	public static final int ROLL_TICKS = 30;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): Quill Roll is his
	// ultimate -- was 5 a creature, no burst
	public static final float ROLL_DAMAGE = 12f, ROLL_FACTOR = 0.6f;
	/** v0.17.2: the quill burst when the roll ends -- this much to everything within {@link #ROLL_BURST_RADIUS}. */
	// v0.17.2 (user: "increase the damage of every alien ultimate that deals damage to be between 40-70 damage"): the closing burst 45
	public static final float ROLL_BURST_DAMAGE = 45f, ROLL_BURST_RADIUS = 4f;
	public static final int FRENZY_TICKS = 160;
	public static final float FRENZY_SPEED = 0.25f, FRENZY_CLAWS = 4f, FRENZY_HEAL = 1.5f;
	public static final double TRACK_RANGE = 48.0;
	public static final int TRACK_TICKS = 600;
	public static final int SNIFF_TICKS = 120;
	public static final float GALLOP_BONUS = 0.2f;
	/** The passive sense: creatures within this many blocks are outlined for him; Sniff widens it; water shrinks it. */
	public static final double SENSE_RANGE = 24.0, SNIFF_RANGE = 64.0, WATER_RANGE = 6.0;
	public static final int DEAF_TICKS = 60;

	private static final ResourceLocation GALLOP_ID = PowerToggles.id("omnitrix_wildmutt_gallop");
	private static final ResourceLocation FRENZY_SPEED_ID = PowerToggles.id("omnitrix_wildmutt_frenzy_speed");
	private static final ResourceLocation FRENZY_CLAWS_ID = PowerToggles.id("omnitrix_wildmutt_frenzy_claws");
	private static final ResourceLocation FRENZY_ATTACK_SPEED_ID = PowerToggles.id("omnitrix_wildmutt_frenzy_attack_speed");
	private static final DustParticleOptions FUR = new DustParticleOptions(new Vector3f(0.94f, 0.54f, 0.14f), 1.1f);
	private static final DustParticleOptions SCENT = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.45f), 0.8f);

	/** Per player: the creatures the running Quill Roll already hit (each once). */
	private static final Map<UUID, Set<Integer>> ROLL_HITS = new ConcurrentHashMap<>();
	/** v0.17.2: per player, the creatures the running Ricochet Hunt already struck (each once, a lone one twice). */
	private static final Map<UUID, Set<Integer>> HUNT_HITS = new ConcurrentHashMap<>();

	private WildmuttKit() {
	}

	public static void register() {
		Aliens.register(new WildmuttKit());
		// v0.17.2 revamp: Predator's Appetite -- his kills feed him
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer p && p != entity && AlienKit.activeKit(p) instanceof WildmuttKit k) {
				k.appetite(p);
			}
		});
		AlienProjectileEntity.trail("wildmutt_quill", ParticleTypes.CRIT);
		AlienProjectileEntity.onHit("wildmutt_quill", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				l.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.2);
				l.sendParticles(FUR, at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0);
			}
		});
	}

	public static void clearSessionState() {
		ROLL_HITS.clear();
		HUNT_HITS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 (user: "give armour ratings to the aliens you think deserve armour ratings please but dont make it terrible"): a tough hide, 10
		return AlienStats.builder().health(HEALTH).speed(SPEED).armor(10f, 1f).knockbackResist(0.3f).attack(ATTACK).step(0.4f).jump(0.12f)
				.safeFall(4f).build();
	}

	/** v0.17.1 rework 2 (user: "make it so that the player cant use tools"): paws -- he holds and uses nothing. */
	@Override
	public boolean pawsOnly(Player p) {
		return true;
	}

	/** v0.17.1 framework: a four-legged body -- wider and lower than a player (0.9 x 1.55, eyes at 1.25). */
	@Override
	@Nullable
	public EntityDimensions body(Player p, Pose pose) {
		return switch (pose) {
			case STANDING -> EntityDimensions.scalable(BODY_WIDTH, BODY_HEIGHT).withEyeHeight(EYE_HEIGHT);
			case CROUCHING -> EntityDimensions.scalable(BODY_WIDTH, CROUCH_HEIGHT).withEyeHeight(CROUCH_EYE);
			default -> null;
		};
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(POUNCE, AbilitySlot.SLOT_1, 60), AlienMove.shift(MAUL, AbilitySlot.SLOT_1, 140),
				AlienMove.tap(QUILL_VOLLEY, AbilitySlot.SLOT_2, 100), AlienMove.shift(ROAR_MOVE, AbilitySlot.SLOT_2, 300),
				AlienMove.tap(LEAP_MOVE, AbilitySlot.SLOT_3, 30), AlienMove.hold(COILED_LEAP, AbilitySlot.SLOT_3, 60),
				AlienMove.shift(CLIMB, AbilitySlot.SLOT_3, 10),
				// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): Quill Roll is his
				// ultimate now -- was a 200-tick cooldown
				// v0.17.2 revamp: Ricochet Hunt took Z (Ground Slam is the Coiled Leap's landing now)
				AlienMove.tap(HUNT, AbilitySlot.SLOT_4, 200), AlienMove.shift(ROLL, AbilitySlot.SLOT_4, 500),
				AlienMove.tap(FRENZY, AbilitySlot.SLOT_6, 600), AlienMove.shift(TRACK, AbilitySlot.SLOT_6, 300),
				// v0.17.2 (user: "take sniff off wildmutts right click ... he cant eat food right now"): Sniff on Shift+V
				AlienMove.tap(DIG, AbilitySlot.SLOT_5, 16), AlienMove.shift(SNIFF_MOVE, AbilitySlot.SLOT_5, SNIFF_TICKS),
				AlienMove.vanilla(SWIPE, "Attack", 0));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), GROWL, 1.0f, 0.9f);
		l.sendParticles(FUR, p.getX(), p.getY() + 1.2, p.getZ(), 10, 0.5, 0.4, 0.5, 0);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		ROLL_HITS.remove(p.getUUID());
		HUNT_HITS.remove(p.getUUID());
		com.projecthero.mod.omnitrix.OmnitrixRiding.endRides(p); // v0.17.2: whoever rode his back gets off
		setFlag(p, F_CLIMB | F_LEAPING | F_POUNCE | F_ROLL | F_FRENZY | F_SNIFF | F_COIL | F_HUNT, false);
		for (String t : new String[] { "pounce_end", "maul_start", "quills_at", "roar_at", "roll_end", "frenzy_end", "track_id",
				"track_end", "sniff_end", "deaf_until", "coil_start", "leap_start", "coil_power", "leap_peak", "hunt_target", "hunt_strike",
				"hunt_count", "swipes", "appetite_at", "choked_until" }) {
			if (timer(p, t) > 0) {
				setTimer(p, t, 0);
			}
		}
		clearFrenzy(p);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, GALLOP_ID);
		p.resetFallDistance();
	}

	// ---------------------------------------------------------------- keys

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3;
	}

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					maul(p);
				} else {
					pounce(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					roar(p);
				} else {
					quills(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					toggleClimb(p);
				} else {
					leap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					roll(p);
				} else {
					hunt(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					track(p);
				} else {
					frenzy(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					sniff(p);
				} else {
					dig(p); // v0.17.1 (user: "add abilities to the V key for every alien")
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (slot != AbilitySlot.SLOT_3) {
			return;
		}
		if (cooldownLeft(p, COILED_LEAP) > 0) {
			use(p, COILED_LEAP); // the "not ready" line
			return;
		}
		setFlag(p, F_COIL, true);
		setTimer(p, "coil_start", p.level().getGameTime());
		anim(p, ANIM_COIL);
		OmnitrixSounds.play(level(p), p.position(), GROWL, 0.7f, 1.2f);
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		if (slot != AbilitySlot.SLOT_3 || !flag(p, F_COIL)) {
			return;
		}
		long start = timer(p, "coil_start");
		int coiled = (int) Mth.clamp(p.level().getGameTime() - start, 0, COIL_MAX);
		setFlag(p, F_COIL, false);
		setTimer(p, "coil_start", 0);
		cooldown(p, COILED_LEAP, -1);
		launch(p, 1f + 0.8f * coiled / (float) COIL_MAX);
		// v0.17.2 revamp: this leap lands in a slam -- remember the coil (1..31) and where the arc started
		setTimer(p, "coil_power", coiled + 1L);
		setTimer(p, "leap_peak", Math.round(p.getY() * 100.0) + PEAK_BIAS);
	}

	/** Timers are "unset" at 0, so the leap's peak height (centi-blocks) is stored with this bias (copes with negative Y). */
	private static final long PEAK_BIAS = 1_000_000L;

	// ---------------------------------------------------------------- R: Pounce / Maul

	public boolean pounce(ServerPlayer p) {
		if (!use(p, POUNCE)) {
			return false;
		}
		anim(p, ANIM_POUNCE);
		LivingEntity t = lookTarget(p, POUNCE_RANGE);
		Vec3 dir;
		if (t != null) {
			Vec3 to = t.position().subtract(p.position());
			dir = new Vec3(to.x, 0, to.z).normalize();
			double d = Math.sqrt(to.x * to.x + to.z * to.z);
			double f = Mth.clamp(d / 7.0, 0.55, 1.6);
			p.setDeltaMovement(dir.x * f, 0.38 + Math.max(0, to.y) * 0.1, dir.z * f);
		} else {
			dir = horizontal(p.getLookAngle());
			p.setDeltaMovement(dir.x * 1.1, 0.45, dir.z * 1.1);
		}
		p.hurtMarked = true;
		setFlag(p, F_POUNCE | F_LEAPING, true);
		setTimer(p, "pounce_end", p.level().getGameTime() + POUNCE_TICKS);
		OmnitrixSounds.play(level(p), p.position(), GROWL, 0.9f, 1.25f);
		return true;
	}

	/** The pounce landing on a creature: the bite. */
	private void pounceHit(ServerPlayer p, LivingEntity e) {
		ServerLevel l = level(p);
		e.invulnerableTime = 0;
		hurt(p, e, POUNCE_DAMAGE, false);
		AlienStatus.root(p, e, PIN_TICKS, 20);
		shove(e, p.position(), 0.3, 0.05);
		p.heal(1f);
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.15, 0.3, 0.15));
		p.hurtMarked = true;
		Vec3 at = e.position().add(0, e.getBbHeight() * 0.6, 0);
		QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, 0xF0B070).size(1.4f).thickness(0.35f).life(7).spawn();
		l.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.3);
		OmnitrixSounds.play(l, at, BITE, 1.0f, 1.0f);
		QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		setFlag(p, F_POUNCE, false);
		setTimer(p, "pounce_end", 0);
	}

	/** How far Dig reaches ahead (blocks) and its tunnel's half width. */
	static final int DIG_DEPTH = 3, DIG_HALF = 1;

	/**
	 * V: Dig -- the claws tear a 3 x 3 tunnel three blocks deep through ground where he looks (anything a shovel or -- v0.17.2 -- a pickaxe takes, leaves,
	 * snow), the blocks dropping as items. Nothing with a block entity, nothing harder than an iron block (hardness 5), nothing the
	 * player may not build at. Returns the blocks dug.
	 */
	public int dig(ServerPlayer p) {
		if (!use(p, DIG)) {
			return 0;
		}
		anim(p, ANIM_MAUL);
		ServerLevel l = level(p);
		// v0.17.2 (user: "make wildmutts dig ability directional instead of just ahead in front of the player"): the 3 x 3 tunnel runs
		// along his look -- up, down or on a slant -- its cross-section square to the look, from the middle of a player-high body
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		Vec3 centre = p.position().add(0, 1.0, 0);
		java.util.Set<BlockPos> cut = new java.util.LinkedHashSet<>();
		for (double d = 0.5; d <= DIG_DEPTH + 0.5; d += 0.5) {
			for (int a = -DIG_HALF; a <= DIG_HALF; a++) {
				for (int b = -1; b <= 1; b++) {
					cut.add(BlockPos.containing(centre.add(look.scale(d)).add(right.scale(a)).add(up.scale(b))));
				}
			}
		}
		int dug = 0;
		for (BlockPos q : cut) {
			if (diggable(p, l, q)) {
				// v0.17.2: the claws mine like an iron pickaxe, so stone drops cobble and the ores drop their ore
				var st = l.getBlockState(q);
				net.minecraft.world.level.block.Block.dropResources(st, l, q, null, p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
				if (l.destroyBlock(q, false, p)) {
					dug++;
				}
			}
		}
		OmnitrixSounds.play(l, p.position(), GROWL, 0.7f, 1.2f);
		if (dug > 0) {
			l.playSound(null, p.blockPosition(), SoundEvents.GRAVEL_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
		}
		return dug;
	}

	private static boolean diggable(ServerPlayer p, ServerLevel l, BlockPos q) {
		var st = l.getBlockState(q);
		if (st.isAir() || st.hasBlockEntity() || !p.mayBuild() || !l.mayInteract(p, q)) {
			return false;
		}
		float hard = st.getDestroySpeed(l, q);
		if (hard < 0f || hard > DIG_MAX_HARDNESS) {
			return false;
		}
		return st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL) || st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
				|| st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.SNOW);
	}

	/**
	 * v0.17.2 (user: "make it so that wildmutt can break hard blocks like stone"): pickaxe blocks too, up to iron-block hardness (5) --
	 * stone, deepslate, ores, bricks; never obsidian, ancient debris or bedrock.
	 */
	static final float DIG_MAX_HARDNESS = 5.0f;

	public boolean maul(ServerPlayer p) {
		if (!use(p, MAUL)) {
			return false;
		}
		anim(p, ANIM_MAUL);
		setTimer(p, "maul_start", p.level().getGameTime());
		OmnitrixSounds.play(level(p), p.position(), GROWL, 0.9f, 1.0f);
		return true;
	}

	/** One rake of the maul: everything in the arc in front. */
	private void maulHit(ServerPlayer p, int i) {
		ServerLevel l = level(p);
		boolean last = i == MAUL_HITS.length - 1;
		Vec3 look = horizontal(p.getLookAngle());
		Vec3 front = p.position().add(look.scale(1.4)).add(0, 0.8, 0);
		for (LivingEntity e : around(p, front, MAUL_RANGE)) {
			if (!inCone(p, e, look, MAUL_RANGE + 0.6, 70)) {
				continue;
			}
			e.invulnerableTime = 0;
			hurt(p, e, last ? MAUL_SLAM : MAUL_RAKE, false);
			if (last) {
				shove(e, p.position(), 0.9, 0.3);
			}
			frenzyHeal(p);
		}
		Vec3 side = new Vec3(-look.z, 0, look.x).scale(i % 2 == 0 ? 1 : -1);
		Vec3 a = front.add(side.scale(1.1)).add(0, 0.9, 0), b = front.subtract(side.scale(1.1)).add(0, -0.6, 0);
		QuirkFx.ribbon(l, a, b).colours(0xFFF4E0, 0xF0A050).size(0.35f).life(6).style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, front, last ? SLAM : CLAW, last ? 0.8f : 0.9f, 1.0f + i * 0.05f);
		if (last) {
			QuirkFx.ring(l, front.add(0, -0.75, 0)).colours(0xFFFFFF, 0xB09070).size(2.4f).thickness(0.5f).life(10).ground().spawn();
			QuirkCamera.shakeAround(l, front, 8, 0.3f, 6);
		}
	}

	// ---------------------------------------------------------------- G: Quill Volley / Savage Roar

	public boolean quills(ServerPlayer p) {
		if (!use(p, QUILL_VOLLEY)) {
			return false;
		}
		anim(p, ANIM_QUILLS);
		setTimer(p, "quills_at", p.level().getGameTime() + QUILL_DELAY);
		OmnitrixSounds.play(level(p), p.position(), BRISTLE, 1.0f, 1.0f);
		return true;
	}

	private void fireQuills(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 back = p.position().add(0, p.getBbHeight() * 0.95, 0);
		float yaw = p.getYRot(), pitch = Mth.clamp(p.getXRot(), -50f, 40f);
		for (int i = 0; i < QUILL_COUNT; i++) {
			float dy = (i - (QUILL_COUNT - 1) / 2f) * 7.5f;
			float dp = (i % 2 == 0 ? -3f : 2f) - 4f;
			Vec3 dir = Vec3.directionFromRotation(pitch + dp, yaw + dy);
			AlienProjectileEntity q = AlienProjectileEntity.of(p, "wildmutt_quill").colour(0xFFE6C0).size(0.16f).damage(QUILL_DAMAGE).speed(1.9)
					.gravity(0.02).life(30).direction(dir);
			q.moveTo(back.x + dir.x * 0.4, back.y, back.z + dir.z * 0.4, yaw + dy, pitch + dp);
			q.spawn();
		}
		l.sendParticles(FUR, back.x, back.y, back.z, 12, 0.4, 0.15, 0.4, 0);
		OmnitrixSounds.play(l, back, QUILLS, 1.0f, 1.0f);
	}

	public boolean roar(ServerPlayer p) {
		if (!use(p, ROAR_MOVE)) {
			return false;
		}
		anim(p, ANIM_ROAR);
		setTimer(p, "roar_at", p.level().getGameTime() + ROAR_DELAY);
		return true;
	}

	private void roarBlast(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 mouth = p.getEyePosition().add(horizontal(look).scale(0.9));
		OmnitrixSounds.play(l, mouth, ROAR, 1.2f, 1.0f);
		QuirkFx.ring(l, mouth).flags(QuirkFx.F_VERTICAL).vec(look).colours(0xFFF6E8, 0xF0B070).size(2.6f).thickness(0.5f).life(12).spawn();
		QuirkFx.ring(l, mouth.add(look.scale(2.5))).flags(QuirkFx.F_VERTICAL).vec(look).colours(0xFFF6E8, 0xF0B070).size(4.2f).thickness(0.4f).life(14)
				.spawn();
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xC0A080).size(3.5f).thickness(0.5f).life(12).ground().spawn();
		QuirkCamera.shakeAround(l, mouth, 14, 0.45f, 12);
		for (LivingEntity e : around(p, mouth.add(look.scale(ROAR_RANGE * 0.5)), ROAR_RANGE * 0.75)) {
			if (!inCone(p, e, look, ROAR_RANGE + 0.5, 50)) {
				continue;
			}
			hurt(p, e, ROAR_DAMAGE, false);
			shove(e, p.position(), 0.8, 0.25);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROAR_EFFECT_TICKS, 1));
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ROAR_EFFECT_TICKS, 0));
			AlienStatus.stun(p, e, 16);
		}
		ping(p);
	}

	/**
	 * v0.17.2 revamp, Savage Roar (his roar is his only voice; the ping uses his hearing): the roar rolls out as a sound ping --
	 * one flat translucent ring spreading to {@link #PING_RANGE} -- and every creature it reaches is outlined in amber for
	 * {@link #PING_TICKS} for him AND his squad (a {@code WildmuttPingPayload} to each of them; nothing is set on the creatures,
	 * so nobody else sees it). Returns the creatures pinged.
	 */
	public List<LivingEntity> ping(ServerPlayer p) {
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(0xFFD8A0, 0xF08A24).size((float) PING_RANGE).thickness(0.35f).style(QuirkFx.RING_SOFT)
				.ease(QuirkFx.Ease.OUT_QUAD).life(26).ground().spawn();
		OmnitrixSounds.play(l, p.position(), PING, 1.0f, 1.0f);
		List<LivingEntity> found = new java.util.ArrayList<>();
		for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(PING_RANGE),
				e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
						&& e.distanceToSqr(p) <= PING_RANGE * PING_RANGE)) {
			found.add(e);
		}
		// sent even when it found nothing: his own client draws the ring over his blind pass from it
		int[] ids = found.stream().limit(256).mapToInt(Entity::getId).toArray();
		var payload = new com.projecthero.mod.network.WildmuttPingPayload(ids, PING_TICKS);
		for (ServerPlayer to : pingListeners(p)) {
			if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(to, com.projecthero.mod.network.WildmuttPingPayload.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(to, payload);
			}
		}
		return found;
	}

	/** Who sees the ping's outlines: him and every squad mate in the same level. */
	public static List<ServerPlayer> pingListeners(ServerPlayer p) {
		List<ServerPlayer> out = new java.util.ArrayList<>();
		out.add(p);
		for (ServerPlayer o : level(p).players()) {
			if (o != p && com.projecthero.mod.squad.Squads.areAllies(p, o)) {
				out.add(o);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- X: Leap / Coiled Leap / Wall Climb

	public boolean leap(ServerPlayer p) {
		if (!use(p, LEAP_MOVE)) {
			return false;
		}
		setTimer(p, "coil_power", 0); // a plain leap lands with the plain shock
		launch(p, 1f);
		return true;
	}

	/** A bound along the look, {@code power} 1 = a plain leap (~8 blocks), 1.8 = a full coil. */
	private void launch(ServerPlayer p, float power) {
		Vec3 look = p.getLookAngle();
		Vec3 h = horizontal(look);
		double up = Mth.clamp(0.62 + look.y * 0.5, 0.45, 1.05) * (0.85 + 0.25 * power);
		p.setDeltaMovement(h.x * 1.15 * power, up, h.z * 1.15 * power);
		p.hurtMarked = true;
		setFlag(p, F_LEAPING, true);
		setTimer(p, "leap_start", p.level().getGameTime());
		anim(p, ANIM_LEAP);
		ServerLevel l = level(p);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundUnder(p)), p.getX(), p.getY() + 0.1, p.getZ(), 14, 0.5, 0.05, 0.5, 0.1);
		OmnitrixSounds.play(l, p.position(), LEAP, 1.0f, 1.0f / Math.max(0.9f, power * 0.85f));
	}

	public boolean toggleClimb(ServerPlayer p) {
		if (!use(p, CLIMB)) {
			return false;
		}
		boolean on = !flag(p, F_CLIMB);
		setFlag(p, F_CLIMB, on);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildmutt.climb_" + (on ? "on" : "off"))
				.withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
		return true;
	}

	/** Wall Climb on: pressing into a wall scales it like a ladder (Sneak lets go). Asked on both sides. */
	@Override
	public boolean climbs(Player p) {
		return flag(p, F_CLIMB) && p.horizontalCollision && !p.isShiftKeyDown() && !flag(p, F_ROLL);
	}

	/**
	 * v0.17.2 revamp (Wall Climb, "bouncing off walls and trees in his fights"): Jump while clinging to a wall kicks him off it --
	 * a ~6-block bound the way he looks (turned away from the wall if he looks into it), no cooldown, no fall damage. His client
	 * asks for it ({@code OmnitrixActionPayload.Action.WALL_KICK}) on the Jump press; refused unless Wall Climb is on and a
	 * wall is right beside him.
	 */
	public boolean wallKick(ServerPlayer p) {
		if (!flag(p, F_CLIMB) || flag(p, F_ROLL) || p.onGround() && !p.horizontalCollision) {
			return false;
		}
		Vec3 wall = wallNormal(p);
		if (wall == null) {
			return false;
		}
		Vec3 h = horizontal(p.getLookAngle());
		if (h.dot(wall) < 0.2) {
			// looking into (or along) the wall: kick out off it, bent toward the look
			h = wall.add(h.scale(0.6)).normalize();
			if (h.dot(wall) < 0.2) {
				h = wall;
			}
		}
		p.setDeltaMovement(h.x * WALL_KICK_SPEED, WALL_KICK_UP, h.z * WALL_KICK_SPEED);
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, "coil_power", 0);
		setFlag(p, F_LEAPING, true);
		setTimer(p, "leap_start", p.level().getGameTime());
		anim(p, ANIM_WALL_KICK);
		ServerLevel l = level(p);
		Vec3 at = p.position().add(0, 0.7, 0).subtract(wall.scale(p.getBbWidth() * 0.5));
		QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(wall).colours(0xFFFFFF, 0xB09070).size(1.3f).thickness(0.35f).life(8).spawn();
		OmnitrixSounds.play(l, p.position(), LEAP, 0.9f, 1.15f);
		return true;
	}

	/** The outward normal of the wall right beside him (the nearest of the four sides with a solid block at body height), or null. */
	@Nullable
	private static Vec3 wallNormal(ServerPlayer p) {
		double reach = p.getBbWidth() * 0.5 + 0.35;
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		Vec3 look = horizontal(p.getLookAngle());
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			for (double y : new double[] { 0.3, 1.0 }) {
				BlockPos q = BlockPos.containing(p.getX() + d.getStepX() * reach, p.getY() + y, p.getZ() + d.getStepZ() * reach);
				if (!p.level().getBlockState(q).getCollisionShape(p.level(), q).isEmpty()) {
					double score = -look.dot(new Vec3(d.getStepX(), 0, d.getStepZ())); // prefer the wall he faces
					if (score < bestD) {
						bestD = score;
						best = new Vec3(-d.getStepX(), 0, -d.getStepZ());
					}
					break;
				}
			}
		}
		return best;
	}

	/**
	 * v0.17.2 revamp (user: Ground Slam "becomes the landing hit of Coiled Leap"): a coiled leap comes down in a slam --
	 * {@code charge} 0..1 (how long the coil was held), {@code fall} the blocks dropped from the arc's top. Everything within the
	 * radius takes the centre damage falling to {@link #SLAM_EDGE} of it at the edge, is shoved out and slowed. Returns the hits.
	 */
	public int leapSlam(ServerPlayer p, float charge, double fall) {
		ServerLevel l = level(p);
		float c = Mth.clamp(charge, 0f, 1f);
		float f = (float) Math.max(0, fall);
		float radius = SLAM_RADIUS + SLAM_RADIUS_COIL * c + Math.min(SLAM_RADIUS_FALL, f * 0.2f);
		float near = SLAM_NEAR + SLAM_NEAR_COIL * c + Math.min(SLAM_NEAR_FALL, f * 0.75f);
		Vec3 at = p.position();
		int hits = 0;
		for (LivingEntity e : around(p, at.add(0, 0.5, 0), radius)) {
			double d = Math.sqrt(e.distanceToSqr(at));
			float dmg = (float) Mth.lerp(Mth.clamp(d / radius, 0, 1), near, near * SLAM_EDGE);
			e.invulnerableTime = 0;
			if (hurt(p, e, dmg, false)) {
				hits++;
			}
			shove(e, at, 0.8, 0.45);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLAM_SLOW_TICKS, 1));
			frenzyHeal(p);
		}
		// the shockwave: a fast wide ring, a slower inner one, a crack web -- and only a little dust
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB09070).size(radius).thickness(0.9f).life(14).ease(QuirkFx.Ease.OUT_EXPO).ground().spawn();
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xFFF0D8, 0xF08A24).size(radius * 0.55f).thickness(0.6f).life(18).ground().spawn();
		QuirkFx.decal(l, at.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2018).size(2.2f + radius * 0.4f).life(160).spawn();
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundUnder(p)), at.x, at.y + 0.2, at.z, 18, radius * 0.35, 0.15, radius * 0.35, 0.2);
		OmnitrixSounds.play(l, at, SLAM, 1.2f, 1.05f - 0.1f * c);
		QuirkCamera.shakeAround(l, at, 14, 0.4f + 0.3f * c, 10);
		anim(p, ANIM_SLAM);
		return hits;
	}

	// ---------------------------------------------------------------- Z: Ricochet Hunt / Quill Roll

	/**
	 * v0.17.2 revamp, Z (his ricochet fighting against groups -- Vilgax's drones, Khyber's hunt): he pounces on the nearest prey
	 * within {@link #HUNT_RANGE}, then bounces on to the nearest one within {@link #HUNT_HOP} of it, up to {@link #HUNT_HITS_MAX}
	 * hits of {@link #HUNT_DAMAGE} -- each creature once, a lone one twice. A hop lands every {@link #HUNT_HOP_TICKS} ticks
	 * ({@link #tick} runs it). Nothing in reach: no cooldown.
	 */
	public boolean hunt(ServerPlayer p) {
		LivingEntity first = lookTarget(p, HUNT_RANGE);
		if (first == null) {
			first = nearestPrey(p, p.position(), HUNT_RANGE, Set.of());
		}
		if (first == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildmutt.no_prey").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, HUNT)) {
			return false;
		}
		HUNT_HITS.put(p.getUUID(), new HashSet<>());
		setFlag(p, F_HUNT, true);
		setTimer(p, "hunt_count", 0);
		OmnitrixSounds.play(level(p), p.position(), GROWL, 1.0f, 1.3f);
		huntHop(p, first);
		return true;
	}

	/** The nearest creature within {@code range} of {@code from} that is not in {@code skip} (monsters before the rest). */
	@Nullable
	private static LivingEntity nearestPrey(ServerPlayer p, Vec3 from, double range, Set<Integer> skip) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : around(p, from, range)) {
			if (skip.contains(e.getId()) || e.distanceToSqr(from) > range * range) {
				continue;
			}
			double d = e.distanceToSqr(from) * (e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof Player ? 1.0 : 2.0);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** One hop: spring at {@code t}; the strike lands {@link #HUNT_HOP_TICKS} later. */
	private void huntHop(ServerPlayer p, LivingEntity t) {
		Vec3 to = t.position().subtract(p.position());
		Vec3 h = horizontal(to);
		double d = Math.sqrt(to.x * to.x + to.z * to.z);
		double f = Mth.clamp(d / 6.0, 0.35, 1.5);
		p.setDeltaMovement(h.x * f, 0.32 + Math.max(0, to.y) * 0.1, h.z * f);
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, "hunt_target", t.getId() + 1L);
		setTimer(p, "hunt_strike", p.level().getGameTime() + HUNT_HOP_TICKS);
		anim(p, ANIM_HUNT);
	}

	/** The hop lands: the claws rake {@code t}, he rebounds off it and springs on to the next prey (or the hunt ends). */
	private void huntStrike(ServerPlayer p) {
		ServerLevel l = level(p);
		Entity e = l.getEntity((int) (timer(p, "hunt_target") - 1));
		Set<Integer> hit = HUNT_HITS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		int count = (int) timer(p, "hunt_count");
		if (e instanceof LivingEntity t && t.isAlive() && t.distanceToSqr(p) < (HUNT_RANGE + 4) * (HUNT_RANGE + 4)) {
			// he lands on it: snapped beside it, on the side he came from
			Vec3 from = horizontal(p.position().subtract(t.position()));
			double off = t.getBbWidth() * 0.5 + p.getBbWidth() * 0.5 + 0.1;
			if (p.distanceToSqr(t) > off * off * 2.5) {
				p.teleportTo(t.getX() + from.x * off, t.getY(), t.getZ() + from.z * off);
			}
			t.invulnerableTime = 0;
			hurt(p, t, HUNT_DAMAGE, false);
			shove(t, p.position(), 0.35, 0.15);
			frenzyHeal(p);
			hit.add(t.getId());
			count++;
			setTimer(p, "hunt_count", count);
			Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0);
			Vec3 side = new Vec3(-from.z, 0, from.x).scale(count % 2 == 0 ? 1 : -1);
			QuirkFx.ribbon(l, at.add(side.scale(0.8)).add(0, 0.5, 0), at.subtract(side.scale(0.8)).add(0, -0.4, 0)).colours(0xFFF4E0, 0xF0A050)
					.size(0.32f).life(6).style(QuirkFx.RIBBON_LIGHT).spawn();
			QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(from.scale(-1)).colours(0xFFFFFF, 0xF0B070).size(1.2f).thickness(0.3f).life(6).spawn();
			OmnitrixSounds.play(l, at, count % 2 == 0 ? CLAW : BITE, 0.9f, 1.0f + count * 0.06f);
			QuirkCamera.fovPunch(p, 0.06f, 5);
			if (count < HUNT_HITS_MAX) {
				LivingEntity next = nearestPrey(p, t.position(), HUNT_HOP, hit);
				if (next == null && hit.size() == 1 && count == 1) {
					next = t; // a lone creature takes the second hit
				}
				if (next != null) {
					huntHop(p, next);
					return;
				}
			}
		}
		endHunt(p, true);
	}

	private void endHunt(ServerPlayer p, boolean bounce) {
		setFlag(p, F_HUNT, false);
		setTimer(p, "hunt_target", 0);
		setTimer(p, "hunt_strike", 0);
		setTimer(p, "hunt_count", 0);
		HUNT_HITS.remove(p.getUUID());
		if (bounce) {
			// the last rebound: a short hop back off the prey
			Vec3 back = horizontal(p.getLookAngle()).scale(-0.35);
			p.setDeltaMovement(back.x, 0.35, back.z);
			p.hurtMarked = true;
		}
	}

	public boolean roll(ServerPlayer p) {
		if (!use(p, ROLL)) {
			return false;
		}
		anim(p, ANIM_ROLL);
		setFlag(p, F_ROLL, true);
		setTimer(p, "roll_end", p.level().getGameTime() + ROLL_TICKS);
		ROLL_HITS.put(p.getUUID(), new HashSet<>());
		OmnitrixSounds.play(level(p), p.position(), ROLL_CUE, 1.0f, 1.0f);
		return true;
	}

	/**
	 * v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): as the roll ends his
	 * quills burst outward -- {@link #ROLL_BURST_DAMAGE} to everything within {@link #ROLL_BURST_RADIUS}.
	 */
	public void rollBurst(ServerPlayer p, ServerLevel l) {
		Vec3 c = p.position().add(0, 0.6, 0);
		for (LivingEntity e : around(p, c, ROLL_BURST_RADIUS)) {
			e.invulnerableTime = 0;
			hurt(p, e, ROLL_BURST_DAMAGE, false);
			shove(e, p.position(), 0.8, 0.3);
		}
		l.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 40, ROLL_BURST_RADIUS * 0.4, 0.4, ROLL_BURST_RADIUS * 0.4, 0.5);
		l.sendParticles(FUR, c.x, c.y, c.z, 16, ROLL_BURST_RADIUS * 0.35, 0.3, ROLL_BURST_RADIUS * 0.35, 0);
		OmnitrixSounds.play(l, c, QUILLS, 1.0f, 0.8f);
	}

	private void endRoll(ServerPlayer p) {
		setFlag(p, F_ROLL, false);
		setTimer(p, "roll_end", 0);
		ROLL_HITS.remove(p.getUUID());
		anim(p, 0);
	}

	// ---------------------------------------------------------------- C: Feral Frenzy / Scent Track

	public boolean frenzy(ServerPlayer p) {
		if (!use(p, FRENZY)) {
			return false;
		}
		anim(p, ANIM_FRENZY);
		setFlag(p, F_FRENZY, true);
		setTimer(p, "frenzy_end", p.level().getGameTime() + FRENZY_TICKS);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, FRENZY_SPEED_ID, FRENZY_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, FRENZY_CLAWS_ID, FRENZY_CLAWS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, FRENZY_ATTACK_SPEED_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), FRENZY_CUE, 1.1f, 1.0f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFE0B0, 0xE05020).size(3f).thickness(0.6f).life(14).ground().spawn();
		l.sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.4, p.getZ(), 4, 0.5, 0.2, 0.5, 0);
		return true;
	}

	private void clearFrenzy(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, FRENZY_SPEED_ID);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, FRENZY_CLAWS_ID);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, FRENZY_ATTACK_SPEED_ID);
	}

	private void frenzyHeal(ServerPlayer p) {
		if (flag(p, F_FRENZY) && p.getHealth() < p.getMaxHealth()) {
			p.heal(FRENZY_HEAL);
		}
	}

	public boolean track(ServerPlayer p) {
		LivingEntity t = lookTarget(p, 32);
		if (t == null) {
			t = nearestQuarry(p);
		}
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildmutt.no_scent").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, TRACK)) {
			return false;
		}
		anim(p, ANIM_TRACK);
		long now = p.level().getGameTime();
		setTimer(p, "track_id", t.getId() + 1L);
		setTimer(p, "track_end", now + TRACK_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SNIFF, 1.0f, 0.9f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildmutt.tracking", t.getDisplayName()).withStyle(ChatFormatting.GOLD),
				true);
		return true;
	}

	/** The nearest creature (players first) within {@link #TRACK_RANGE}, never the wearer or a squad ally. */
	@Nullable
	public static LivingEntity nearestQuarry(ServerPlayer p) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : around(p, p.position(), TRACK_RANGE)) {
			double d = e.distanceToSqr(p) * (e instanceof Player ? 0.25 : 1.0);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** The creature Scent Track follows now (client-safe: the synced timers), or null. */
	@Nullable
	public Entity tracked(Player p) {
		long id = timer(p, "track_id");
		if (id <= 0 || timer(p, "track_end") <= p.level().getGameTime()) {
			return null;
		}
		Entity e = p.level().getEntity((int) (id - 1));
		return e != null && e.isAlive() ? e : null;
	}

	// ---------------------------------------------------------------- the vanilla keys

	/**
	 * The claw swipe: vanilla's hit (his fist is +4) and a cleave through whatever stands beside the target. v0.17.2 revamp (he
	 * bites constantly in the show): every {@link #BITE_EVERY}rd hit on a creature is a Bite instead -- the kit makes that hit
	 * itself, {@link #BITE_BONUS} on top of the claw and a short slow.
	 */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		ServerLevel l = level(p);
		if (target instanceof LivingEntity bitten) {
			long n = timer(p, "swipes") + 1;
			setTimer(p, "swipes", n >= BITE_EVERY ? 0 : n);
			if (n >= BITE_EVERY) {
				bite(p, bitten);
				return true;
			}
		}
		if (target instanceof LivingEntity main) {
			float cleave = (p.getMainHandItem().isEmpty() ? (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1f + ATTACK) * 0.5f;
			Vec3 look = horizontal(p.getLookAngle());
			for (LivingEntity e : around(p, main.position(), 1.8)) {
				if (e != main && inCone(p, e, look, 4.0, 75)) {
					hurt(p, e, cleave, false);
				}
			}
			frenzyHeal(p);
			Vec3 at = main.position().add(0, main.getBbHeight() * 0.6, 0);
			Vec3 side = new Vec3(-look.z, 0, look.x);
			QuirkFx.ribbon(l, at.add(side.scale(0.7)).add(0, 0.5, 0), at.subtract(side.scale(0.7)).add(0, -0.4, 0)).colours(0xFFF4E0, 0xF0A050)
					.size(0.28f).life(5).style(QuirkFx.RIBBON_LIGHT).spawn();
		}
		OmnitrixSounds.play(l, p.position(), CLAW, 0.8f, 1.0f);
		// v0.17.1 rework 2: paws hold nothing -- with something in the hotbar slot the hit is his own (a bare claw: his
		// attack stat, scaled by the swing's charge), so a held weapon adds no damage, enchantment or sweep and takes no wear
		if (!p.getMainHandItem().isEmpty() && target instanceof LivingEntity main) {
			float charge = p.getAttackStrengthScale(0.5f);
			float dmg = (1f + ATTACK) * (0.2f + charge * charge * 0.8f);
			if (hurt(p, main, dmg, false)) {
				shove(main, p.position(), 0.4 * charge, 0.1);
			}
			p.resetAttackStrengthTicker();
			return true;
		}
		return false;
	}

	/** The Bite (every 3rd claw hit): his claw damage scaled by the swing's charge, + {@link #BITE_BONUS}, slowed 1.5 s. */
	public float bite(ServerPlayer p, LivingEntity t) {
		ServerLevel l = level(p);
		float claws = p.getMainHandItem().isEmpty() ? (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1f + ATTACK;
		float charge = p.getAttackStrengthScale(0.5f);
		float dmg = claws * (0.2f + charge * charge * 0.8f) + BITE_BONUS;
		t.invulnerableTime = 0;
		if (hurt(p, t, dmg, false)) {
			shove(t, p.position(), 0.3 * charge, 0.05);
			t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BITE_SLOW_TICKS, 0));
			frenzyHeal(p);
		}
		p.resetAttackStrengthTicker();
		Vec3 at = t.position().add(0, t.getBbHeight() * 0.65, 0);
		Vec3 look = horizontal(p.getLookAngle());
		// the jaws: two short white ribbons closing on each other
		QuirkFx.ribbon(l, at.add(look.scale(-0.4)).add(0, 0.35, 0), at.add(look.scale(0.3)).add(0, 0.05, 0)).colours(0xFFFFFF, 0xF0C090).size(0.22f)
				.life(5).style(QuirkFx.RIBBON_LIGHT).spawn();
		QuirkFx.ribbon(l, at.add(look.scale(-0.4)).add(0, -0.35, 0), at.add(look.scale(0.3)).add(0, -0.05, 0)).colours(0xFFFFFF, 0xF0C090).size(0.22f)
				.life(5).style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, at, BITE, 0.9f, 1.05f);
		return dmg;
	}

	/**
	 * v0.17.2 revamp, Predator's Appetite: a kill gives back {@link #APPETITE_FOOD} hunger and a heart, at most once every
	 * {@link #APPETITE_COOLDOWN} ticks (he can't hold food well). Returns whether it fed him.
	 */
	public boolean appetite(ServerPlayer p) {
		long now = p.level().getGameTime();
		if (timer(p, "appetite_at") > now) {
			return false;
		}
		setTimer(p, "appetite_at", now + APPETITE_COOLDOWN);
		p.getFoodData().eat(APPETITE_FOOD, 0.3f);
		p.heal(APPETITE_HEAL);
		OmnitrixSounds.play(level(p), p.position(), GROWL, 0.6f, 1.3f);
		return true;
	}

	/** Shift+V: Sniff -- 6 s of wider senses that also pick out dropped items (v0.17.2: was right-click, which kept him from eating). */
	public boolean sniff(ServerPlayer p) {
		if (!use(p, SNIFF_MOVE)) {
			return false;
		}
		anim(p, ANIM_SNIFF);
		setFlag(p, F_SNIFF, true);
		setTimer(p, "sniff_end", p.level().getGameTime() + SNIFF_TICKS);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SNIFF, 1.0f, 1.0f);
		// the scent of dropped items rises off them (shown to him only)
		for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(24))) {
			l.sendParticles(p, SCENT, true, it.getX(), it.getY() + 0.4, it.getZ(), 8, 0.15, 0.4, 0.15, 0);
		}
		return true;
	}

	// ---------------------------------------------------------------- passives, senses, damage

	/**
	 * How far his senses reach now (client-safe): 24, 64 while sniffing, 6 underwater, 4 with a Choked Nose (v0.17.2), 0 while an
	 * explosion deafens him.
	 */
	public double senseRange(Player p) {
		long now = p.level().getGameTime();
		if (timer(p, "deaf_until") > now) {
			return 0;
		}
		if (choked(p)) {
			return CHOKE_RANGE;
		}
		if (p.isUnderWater()) {
			return WATER_RANGE;
		}
		return flag(p, F_SNIFF) ? SNIFF_RANGE : SENSE_RANGE;
	}

	/** v0.17.2 revamp: Choked Nose is on (client-safe: the synced timer). */
	public boolean choked(Player p) {
		return timer(p, "choked_until") > p.level().getGameTime();
	}

	/**
	 * v0.17.2 revamp, Scent Sense by movement (W4: sneaking hides beyond 8): how strongly he senses a creature {@code dist}
	 * blocks away -- 2 bright (it moves), 1 faint (it stands still or sneaks, within {@link #STILL_RANGE}), 0 not at all.
	 * {@code range} is his {@link #senseRange} now.
	 */
	public static int senseStrength(double dist, double range, boolean moving, boolean sneaking) {
		if (range <= 0 || dist > range) {
			return 0;
		}
		if (moving && !sneaking) {
			return 2;
		}
		return dist <= STILL_RANGE ? 1 : 0;
	}

	/**
	 * v0.17.2 revamp, Choked Nose (from <i>Lucky Girl</i>): smoke or stench in his nose -- campfire smoke rising round him, a
	 * Stinkfly gas cloud, a lingering potion cloud, or freezing in powder snow.
	 */
	public static boolean smellsSmoke(ServerPlayer p) {
		ServerLevel l = level(p);
		if (p.isInPowderSnow || p.getTicksFrozen() > 0) {
			return true;
		}
		if (!l.getEntitiesOfClass(net.minecraft.world.entity.AreaEffectCloud.class, p.getBoundingBox().inflate(0.5)).isEmpty()) {
			return true;
		}
		if (com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit.inGas(l, p.position().add(0, 0.8, 0))) {
			return true;
		}
		// v0.17.2 (Swampfire revamp): his methane cloud and his Swamp Stench count too
		if (com.projecthero.mod.omnitrix.alien.swampfire.SwampfireKit.inGas(l, p.position().add(0, 0.8, 0))) {
			return true;
		}
		// a lit campfire's smoke column: up to 6 blocks under his nose, within a block sideways
		BlockPos head = BlockPos.containing(p.getEyePosition());
		for (int dy = 0; dy <= 6; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockState st = l.getBlockState(head.offset(dx, -dy, dz));
					if (st.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock && net.minecraft.world.level.block.CampfireBlock.isLitCampfire(st)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Chokes his nose for {@link #CHOKE_TICKS} (his client closes a dark vignette in on the screen while it lasts). */
	public void choke(ServerPlayer p) {
		long now = p.level().getGameTime();
		if (!choked(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildmutt.choked").withStyle(ChatFormatting.GRAY), true);
		}
		setTimer(p, "choked_until", now + CHOKE_TICKS);
	}

	/** The nose clears: a sneeze (a head-jerk clip and a snort). */
	private void sneeze(ServerPlayer p) {
		setTimer(p, "choked_until", 0);
		anim(p, ANIM_SNEEZE);
		OmnitrixSounds.play(level(p), p.getEyePosition(), SNEEZE, 1.0f, 1.0f);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !(source.is(DamageTypeTags.IS_FALL) && (flag(p, F_LEAPING) || flag(p, F_CLIMB) || flag(p, F_HUNT)));
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			setTimer(p, "deaf_until", p.level().getGameTime() + DEAF_TICKS);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildmutt.deafened").withStyle(ChatFormatting.GRAY), true);
		}
		return flag(p, F_ROLL) ? amount * ROLL_FACTOR : amount;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		// no eyes: nothing to blind
		if (p.hasEffect(MobEffects.BLINDNESS)) {
			p.removeEffect(MobEffects.BLINDNESS);
		}
		if (p.hasEffect(MobEffects.DARKNESS)) {
			p.removeEffect(MobEffects.DARKNESS);
		}
		// underwater his air runs out twice as fast
		if (p.isUnderWater() && now % 2 == 0 && p.getAirSupply() > 0) {
			p.setAirSupply(p.getAirSupply() - 1);
		}
		// the gallop
		boolean gallop = p.isSprinting() && !p.isInWater();
		var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		boolean has = speed != null && speed.getModifier(GALLOP_ID) != null;
		if (gallop && !has) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, GALLOP_ID, GALLOP_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else if (!gallop && has) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, GALLOP_ID);
		}
		if (flag(p, F_CLIMB) && climbs(p)) {
			p.resetFallDistance();
			if (now % 6 == 0) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wallAhead(p)), p.getX(), p.getY() + 0.5, p.getZ(), 2, 0.3, 0.3, 0.3, 0);
			}
		}
		// v0.17.2 revamp: a coiled leap remembers the top of its arc (for the landing slam's fall bonus)
		long coil = timer(p, "coil_power");
		if (coil > 0 && flag(p, F_LEAPING)) {
			long y = Math.round(p.getY() * 100.0) + PEAK_BIAS;
			if (y > timer(p, "leap_peak")) {
				setTimer(p, "leap_peak", y);
			}
		}
		// a leap ends on landing (the shock -- a coiled leap's is the slam)
		if (flag(p, F_LEAPING) && now - timer(p, "leap_start") > 3 && !flag(p, F_POUNCE) && (p.onGround() || p.isInWater())) {
			setFlag(p, F_LEAPING, false);
			setTimer(p, "leap_start", 0);
			p.resetFallDistance();
			if (coil > 0 && !p.isInWater()) {
				double fall = (timer(p, "leap_peak") - PEAK_BIAS) / 100.0 - p.getY();
				setTimer(p, "coil_power", 0);
				setTimer(p, "leap_peak", 0);
				leapSlam(p, (coil - 1) / (float) COIL_MAX, fall);
			} else {
				setTimer(p, "coil_power", 0);
				setTimer(p, "leap_peak", 0);
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundUnder(p)), p.getX(), p.getY() + 0.1, p.getZ(), 16, 0.6, 0.05, 0.6, 0.12);
				QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB09070).size(2.0f).thickness(0.45f).life(10).ground().spawn();
				OmnitrixSounds.play(l, p.position(), LAND, 0.9f, 1.0f);
			}
		}
		// v0.17.2 revamp: the Ricochet Hunt's hops
		long hs = timer(p, "hunt_strike");
		if (hs > 0 && now >= hs) {
			huntStrike(p);
		}
		// v0.17.2 revamp: Choked Nose -- smoke keeps it shut; once clear of it the nose runs out and he sneezes
		if (now % 5 == 0 && smellsSmoke(p)) {
			choke(p);
		} else if (timer(p, "choked_until") > 0 && !choked(p)) {
			sneeze(p);
		}
		// the pounce in flight
		if (flag(p, F_POUNCE)) {
			long end = timer(p, "pounce_end");
			LivingEntity hit = null;
			for (LivingEntity e : around(p, p.position().add(0, 0.7, 0), 2.2)) {
				if (e.getBoundingBox().intersects(p.getBoundingBox().inflate(0.65))) {
					hit = e;
					break;
				}
			}
			if (hit != null) {
				pounceHit(p, hit);
			} else if (now >= end || (p.onGround() && end - now < POUNCE_TICKS - 3)) {
				setFlag(p, F_POUNCE, false);
				setTimer(p, "pounce_end", 0);
			}
		}
		// the maul's rakes
		long ms = timer(p, "maul_start");
		if (ms > 0) {
			long t = now - ms;
			for (int i = 0; i < MAUL_HITS.length; i++) {
				if (t == MAUL_HITS[i]) {
					maulHit(p, i);
				}
			}
			if (t >= MAUL_HITS[MAUL_HITS.length - 1]) {
				setTimer(p, "maul_start", 0);
			}
		}
		fireAt(p, now, "quills_at", () -> fireQuills(p));
		fireAt(p, now, "roar_at", () -> roarBlast(p));
		// the roll
		if (flag(p, F_ROLL)) {
			if (now >= timer(p, "roll_end") || p.isInWater()) {
				rollBurst(p, l);
				endRoll(p);
			} else {
				Vec3 f = horizontal(p.getLookAngle());
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(f.x * 0.8, Math.min(v.y, 0.3), f.z * 0.8);
				p.hurtMarked = true;
				Set<Integer> hits = ROLL_HITS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
				for (LivingEntity e : around(p, p.position().add(0, 0.6, 0), 1.7)) {
					if (hits.add(e.getId())) {
						e.invulnerableTime = 0;
						hurt(p, e, ROLL_DAMAGE, false);
						shove(e, p.position(), 1.1, 0.3);
						l.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
					}
				}
				if (now % 3 == 0) {
					l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundUnder(p)), p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.4, 0.05, 0.4, 0.1);
				}
				if (now % 8 == 0) {
					OmnitrixSounds.play(l, p.position(), ROLL_CUE, 0.6f, 1.1f);
				}
			}
		}
		// the frenzy
		long fe = timer(p, "frenzy_end");
		if (fe > 0) {
			if (now >= fe) {
				setTimer(p, "frenzy_end", 0);
				setFlag(p, F_FRENZY, false);
				clearFrenzy(p);
			} else if (now % 5 == 0) {
				l.sendParticles(new DustParticleOptions(new Vector3f(0.9f, 0.25f, 0.1f), 1.0f), p.getX(), p.getY() + 1.2, p.getZ(), 2, 0.5, 0.3, 0.5, 0);
			}
		}
		// sniffing
		long se = timer(p, "sniff_end");
		if (se > 0 && now >= se) {
			setTimer(p, "sniff_end", 0);
			setFlag(p, F_SNIFF, false);
		}
		// the trail to the tracked creature is drawn by his client; the server only ends it
		long te = timer(p, "track_end");
		if (te > 0 && (now >= te || tracked(p) == null)) {
			setTimer(p, "track_end", 0);
			setTimer(p, "track_id", 0);
		}
		if (now % 160 == 0 && l.random.nextInt(3) == 0) {
			OmnitrixSounds.play(l, p.position(), GROWL, 0.5f, 0.9f + l.random.nextFloat() * 0.2f);
		}
	}

	private void fireAt(ServerPlayer p, long now, String timer, Runnable fn) {
		long at = timer(p, timer);
		if (at > 0 && now >= at) {
			setTimer(p, timer, 0);
			fn.run();
		}
	}

	// ---------------------------------------------------------------- helpers

	private static boolean inCone(ServerPlayer p, LivingEntity e, Vec3 dir, double range, double halfDeg) {
		Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(p.position().add(0, p.getBbHeight() * 0.5, 0));
		double len = to.length();
		if (len > range) {
			return false;
		}
		if (len < 1.2) {
			return true;
		}
		Vec3 h = new Vec3(to.x, to.y * 0.5, to.z).normalize();
		return h.dot(dir.normalize()) > Math.cos(Math.toRadians(halfDeg));
	}

	private static Vec3 horizontal(Vec3 v) {
		Vec3 h = new Vec3(v.x, 0, v.z);
		return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	private static BlockState groundUnder(ServerPlayer p) {
		BlockState s = p.level().getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ()));
		return s.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : s;
	}

	private static BlockState wallAhead(ServerPlayer p) {
		Vec3 f = horizontal(p.getLookAngle());
		BlockState s = p.level().getBlockState(BlockPos.containing(p.getX() + f.x * 0.8, p.getY() + 0.5, p.getZ() + f.z * 0.8));
		return s.isAir() ? groundUnder(p) : s;
	}
}
