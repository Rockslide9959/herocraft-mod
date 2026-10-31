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
 *   G        Quill Volley  7 quills from the back in a cone (3 each)            Shift+G  Savage Roar   cone 9: 6, slowed, weakened, staggered
 *   X        Leap          a huge bound (no fall damage, landing shock)         Hold X   Coiled Leap   up to 1.5 s of coil: up to 1.8x as far
 *                                                                               Shift+X  Wall Climb    toggle: press into a wall to scale it
 *   Z        Ground Slam   both fists down: r 4.5, 16 near / 10 at the edge     Shift+Z  Quill Roll    ULTIMATE: 1.5 s spiked roll (12 a hit, -40% taken),
 *                                                                                                      then a quill burst (14, r 4)
 *   C        Feral Frenzy  8 s: +25% speed, +4 claws, +1 attack speed, heals    Shift+C  Scent Track   marks a creature (48 r) for 30 s: a trail to it
 *   Attack   claw swipe (cleaves the creatures beside the target)              Use (empty hand, air)  Sniff: 6 s, senses widen to 64, pick out items and buried ores / chests
 *   Passive  Scent Sense: creatures within 24 are outlined for him (invisible ones too); gallops (+20% while sprinting); no eyes:
 *            immune to Blindness and Darkness. Weakness: underwater his senses shrink to 6 and his air runs out twice as fast;
 *            an explosion close by deafens his senses for 3 s.
 * </pre>
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

	// ---------------------------------------------------------------- synced bits
	public static final int F_CLIMB = 1;
	public static final int F_LEAPING = 2;
	public static final int F_POUNCE = 4;
	public static final int F_ROLL = 8;
	public static final int F_FRENZY = 16;
	public static final int F_SNIFF = 32;
	public static final int F_COIL = 64;

	// ---------------------------------------------------------------- moves
	public static final String POUNCE = "pounce";
	public static final String MAUL = "maul";
	public static final String QUILL_VOLLEY = "quill_volley";
	public static final String ROAR_MOVE = "savage_roar";
	public static final String LEAP_MOVE = "leap";
	public static final String COILED_LEAP = "coiled_leap";
	public static final String CLIMB = "wall_climb";
	public static final String SLAM_MOVE = "ground_slam";
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
	public static final float POUNCE_DAMAGE = 12f;
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
	public static final float ROAR_DAMAGE = 6f;
	public static final int ROAR_DELAY = 6;
	public static final int ROAR_EFFECT_TICKS = 70;
	public static final int COIL_MAX = 30;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 8 near / 5 far
	public static final float SLAM_RADIUS = 4.5f, SLAM_NEAR = 16f, SLAM_FAR = 10f;
	public static final int SLAM_DELAY = 9;
	public static final int ROLL_TICKS = 30;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): Quill Roll is his
	// ultimate -- was 5 a creature, no burst
	public static final float ROLL_DAMAGE = 12f, ROLL_FACTOR = 0.6f;
	/** v0.17.2: the quill burst when the roll ends -- this much to everything within {@link #ROLL_BURST_RADIUS}. */
	public static final float ROLL_BURST_DAMAGE = 14f, ROLL_BURST_RADIUS = 4f;
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

	private WildmuttKit() {
	}

	public static void register() {
		Aliens.register(new WildmuttKit());
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
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(HEALTH).speed(SPEED).armor(4f, 1f).knockbackResist(0.3f).attack(ATTACK).step(0.4f).jump(0.12f)
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
				AlienMove.tap(SLAM_MOVE, AbilitySlot.SLOT_4, 160), AlienMove.shift(ROLL, AbilitySlot.SLOT_4, 500),
				AlienMove.tap(FRENZY, AbilitySlot.SLOT_6, 600), AlienMove.shift(TRACK, AbilitySlot.SLOT_6, 300),
				AlienMove.tap(DIG, AbilitySlot.SLOT_5, 16),
				AlienMove.vanilla(SWIPE, "Attack", 0), AlienMove.vanilla(SNIFF_MOVE, "Use (empty hand)", SNIFF_TICKS));
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
		setFlag(p, F_CLIMB | F_LEAPING | F_POUNCE | F_ROLL | F_FRENZY | F_SNIFF | F_COIL, false);
		for (String t : new String[] { "pounce_end", "maul_start", "quills_at", "roar_at", "slam_at", "roll_end", "frenzy_end", "track_id",
				"track_end", "sniff_end", "deaf_until", "coil_start", "leap_start" }) {
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
					slam(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					track(p);
				} else {
					frenzy(p);
				}
			}
			case SLOT_5 -> dig(p); // v0.17.1 (user: "add abilities to the V key for every alien")
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
	}

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
	 * V: Dig -- the claws tear a 3 x 3 tunnel three blocks deep through soft ground in front (anything a shovel takes, leaves,
	 * snow), the blocks dropping as items. Nothing with a block entity, nothing harder than dirt-ish (hardness 1.5), nothing the
	 * player may not build at. Returns the blocks dug.
	 */
	public int dig(ServerPlayer p) {
		if (!use(p, DIG)) {
			return 0;
		}
		anim(p, ANIM_MAUL);
		ServerLevel l = level(p);
		net.minecraft.core.Direction face = p.getXRot() > 55f ? net.minecraft.core.Direction.DOWN : p.getDirection();
		BlockPos base = p.blockPosition();
		int dug = 0;
		for (int d = 1; d <= DIG_DEPTH; d++) {
			for (int a = -DIG_HALF; a <= DIG_HALF; a++) {
				for (int b = 0; b <= 2; b++) {
					BlockPos q;
					if (face == net.minecraft.core.Direction.DOWN) {
						q = base.offset(a, -d, b - 1);
					} else {
						BlockPos ahead = base.relative(face, d);
						q = face.getAxis() == net.minecraft.core.Direction.Axis.X ? ahead.offset(0, b, a) : ahead.offset(a, b, 0);
					}
					if (diggable(p, l, q) && l.destroyBlock(q, true, p)) {
						dug++;
					}
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
		if (hard < 0f || hard > 1.5f) {
			return false;
		}
		return st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL) || st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.SNOW);
	}

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
	}

	// ---------------------------------------------------------------- X: Leap / Coiled Leap / Wall Climb

	public boolean leap(ServerPlayer p) {
		if (!use(p, LEAP_MOVE)) {
			return false;
		}
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

	// ---------------------------------------------------------------- Z: Ground Slam / Quill Roll

	public boolean slam(ServerPlayer p) {
		if (!use(p, SLAM_MOVE)) {
			return false;
		}
		anim(p, ANIM_SLAM);
		setTimer(p, "slam_at", p.level().getGameTime() + SLAM_DELAY);
		OmnitrixSounds.play(level(p), p.position(), GROWL, 0.9f, 0.85f);
		return true;
	}

	private void slamHit(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 c = p.position().add(horizontal(p.getLookAngle()).scale(1.3));
		for (LivingEntity e : around(p, c.add(0, 0.5, 0), SLAM_RADIUS)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			float dmg = (float) Mth.lerp(Mth.clamp(d / SLAM_RADIUS, 0, 1), SLAM_NEAR, SLAM_FAR);
			e.invulnerableTime = 0;
			hurt(p, e, dmg, false);
			shove(e, c, 0.8, 0.55);
			frenzyHeal(p);
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB09070).size(SLAM_RADIUS).thickness(0.9f).life(16).ground().spawn();
		QuirkFx.decal(l, c.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2018).size(3.2f).life(160).spawn();
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundUnder(p)), c.x, c.y + 0.2, c.z, 40, 1.6, 0.2, 1.6, 0.25);
		l.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 10, 1.2, 0.1, 1.2, 0.04);
		OmnitrixSounds.play(l, c, SLAM, 1.2f, 1.0f);
		QuirkCamera.shakeAround(l, c, 14, 0.6f, 10);
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

	/** The claw swipe: vanilla's hit (his fist is +4) and a cleave through whatever stands beside the target. */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		ServerLevel l = level(p);
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

	/**
	 * Use on air: Sniff -- 6 s of wider senses that also pick out dropped items. v0.17.1 rework 2: paws hold nothing, so it
	 * sniffs whatever the hotbar slot has in it (the main hand's use; the off hand's is refused).
	 */
	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		return sniff(p) ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

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

	/** How far his senses reach now (client-safe): 24, 64 while sniffing, 6 underwater, 0 while an explosion deafens him. */
	public double senseRange(Player p) {
		long now = p.level().getGameTime();
		if (timer(p, "deaf_until") > now) {
			return 0;
		}
		if (p.isUnderWater()) {
			return WATER_RANGE;
		}
		return flag(p, F_SNIFF) ? SNIFF_RANGE : SENSE_RANGE;
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !(source.is(DamageTypeTags.IS_FALL) && (flag(p, F_LEAPING) || flag(p, F_CLIMB)));
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
		// a leap ends on landing (the shock)
		if (flag(p, F_LEAPING) && now - timer(p, "leap_start") > 3 && !flag(p, F_POUNCE) && (p.onGround() || p.isInWater())) {
			setFlag(p, F_LEAPING, false);
			setTimer(p, "leap_start", 0);
			p.resetFallDistance();
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundUnder(p)), p.getX(), p.getY() + 0.1, p.getZ(), 16, 0.6, 0.05, 0.6, 0.12);
			QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB09070).size(2.0f).thickness(0.45f).life(10).ground().spawn();
			OmnitrixSounds.play(l, p.position(), LAND, 0.9f, 1.0f);
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
		fireAt(p, now, "slam_at", () -> slamHit(p));
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
