package com.projecthero.mod.omnitrix.alien.fourarms;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.spider.entity.ThrownBlockEntity;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Four Arms, the Tetramand -- big (1.4x), red, four arms, four eyes; 40 health, heavy armour, hard to knock back,
 * slower on his feet.
 *
 * <p>v0.17.2 (user: "i like this new fourarms revamp, please build it"): the show-accurate kit.
 *
 * <pre>
 *   Passive  Tetramand Might  punches cycle all four fists; every 4th is a double-fist hammer (knocked down and back);
 *                             small mobs cannot knock him back; his Charge smashes glass, leaves and light wood
 *   R        Four-Fist Flurry four alternating punches (4 x 2) and a rising uppercut (4, launches)
 *   Shift+R  Hammer Smash     both pairs overhead: a crack runs 9 blocks ahead, launching what stands on it (14)
 *   G        Grab / Throw     grab what you look at; G again hurls it (12)
 *   Shift+G  Boulder Rip      tear up the ground in front of you and hurl it (12)  -- holding: Giant Swing (5 a body, flung 14)
 *   X        Super Leap       tap 14 blocks / hold up to 200; landing shockwave (8, long 14)
 *   Shift+X  Tetramand Charge a bull rush carrying enemies along (6), smashing weak blocks, ending in a shove (6)
 *   Z        Thunder Clap     a 10-block sonic cone: 17, blasted back, stunned and dizzy; shatters glass, puts fire out
 *   Shift+Z  Meteor Slam      ULTIMATE: up into the sky and down -- a radius-10 quake (18 + 0.4 a block fallen, 30 at most)
 *   V        Wall Climb       toggle                                   Shift+V  Four-Arm Guard  3 s: frontal hits blocked, projectiles swatted back
 *   C        Rampage          8 s: every punch quakes (4), +4 punch, faster, no knockback; winded after
 *   Shift+C  Bear Hug         crush the creature in front for 3 s (6 x 2) and slam it down (8)
 * </pre>
 */
public final class FourArmsKit extends AlienKit {
	public static final String ID = "four_arms";
	public static final int COLOUR = 0xD8323A;

	// the four fists of the passive cycle: upper right, upper left, lower right, lower left; the 4th punch is the hammer
	public static final int ANIM_PUNCH_R = 1;
	public static final int ANIM_PUNCH_L = 2;
	public static final int ANIM_PUNCH_LOW_R = 3;
	public static final int ANIM_PUNCH_LOW_L = 4;
	public static final int ANIM_HAMMER_PUNCH = 5;
	public static final int ANIM_FLURRY = 6;
	public static final int ANIM_HAMMER_SMASH = 7;
	public static final int ANIM_GRAB = 8;
	public static final int ANIM_THROW = 9;
	public static final int ANIM_BOULDER = 10;
	public static final int ANIM_SWING = 11;
	public static final int ANIM_LEAP = 12;
	public static final int ANIM_CHARGE = 13;
	public static final int ANIM_CLAP = 14;
	public static final int ANIM_METEOR_RISE = 15;
	public static final int ANIM_DIVE = 16;
	public static final int ANIM_CLIMB = 17;
	public static final int ANIM_GUARD = 18;
	public static final int ANIM_RAMPAGE = 19;
	public static final int ANIM_HUG = 20;
	public static final int ANIM_HUG_SLAM = 21;
	/** The long leap's crouch (held X). */
	public static final int ANIM_CROUCH = 22;

	public static final int F_HOLDING = 1;
	public static final int F_GUARD = 2;
	public static final int F_LEAPING = 4;
	public static final int F_DIVING = 8;
	public static final int F_CHARGING = 16;
	public static final int F_CLIMB = 32;
	/** v0.17.1: X held -- crouched, charging the long leap. */
	public static final int F_LEAP_CHARGE = 64;
	public static final int F_RAMPAGE = 128;
	public static final int F_HUGGING = 256;
	public static final int F_SWINGING = 512;

	/**
	 * v0.17.1 (user: "make his leap ability chargeable up to 200 blocks"): hold X to charge (the leap bar fills over
	 * {@link #LEAP_CHARGE_TICKS}), let go to jump {@link #LEAP_TAP_BLOCKS} up to {@link #LEAP_FULL_BLOCKS} along the look --
	 * a drag-free arc like the Hulk's Super Leap ({@code HulkLeap.dragFreeArc}; his client holds the launch speed, see
	 * FourArmsClient#tick). A tap is the old short leap.
	 */
	public static final int LEAP_CHARGE_TICKS = 50;
	public static final double LEAP_TAP_BLOCKS = 14.0, LEAP_FULL_BLOCKS = 200.0;

	public static final String FLURRY = "four_fist_flurry";
	public static final String HAMMER = "hammer_smash";
	public static final String GRAB = "grab";
	public static final String THROW = "throw";
	public static final String BOULDER = "boulder_rip";
	public static final String SWING = "giant_swing";
	public static final String LEAP = "super_leap";
	public static final String LEAP_FAR = "charged_leap";
	public static final String CHARGE = "tetramand_charge";
	public static final String CLAP = "thunder_clap";
	public static final String METEOR = "meteor_slam";
	public static final String CLIMB = "wall_climb";
	public static final String GUARD = "four_arm_guard";
	public static final String RAMPAGE = "rampage";
	public static final String HUG = "bear_hug";
	public static final String MIGHT = "tetramand_might";

	// v0.17.2 numbers, on par with the other aliens (R ~12, strong ~17, ultimate up to 30)
	public static final float FLURRY_PUNCH_DAMAGE = 2f, UPPERCUT_DAMAGE = 4f;
	public static final float HAMMER_DAMAGE = 14f;
	public static final int HAMMER_LENGTH = 9;
	public static final float THROW_DAMAGE = 12f;
	public static final float BOULDER_DAMAGE = 12f;
	public static final float SWING_HIT_DAMAGE = 5f, SWING_FLING_DAMAGE = 14f;
	public static final int SWING_TICKS = 30;
	public static final float LEAP_LAND_DAMAGE = 8f;
	public static final float LEAP_FAR_LAND_DAMAGE = 14f;
	public static final float CHARGE_DAMAGE = 6f, CHARGE_END_DAMAGE = 6f;
	public static final int CHARGE_TICKS = 18;
	public static final float CLAP_DAMAGE = 17f;
	public static final float CLAP_RANGE = 10f;
	public static final float METEOR_DAMAGE = 18f, METEOR_PER_BLOCK = 0.4f, METEOR_MAX_DAMAGE = 30f, METEOR_RADIUS = 10f;
	/** The Meteor Slam's take-off speed (about 25 blocks up). */
	public static final double METEOR_RISE = 2.4;
	public static final int GUARD_TICKS = 60;
	public static final int RAMPAGE_TICKS = 160, WINDED_TICKS = 100;
	public static final float RAMPAGE_BONUS = 4f, RAMPAGE_QUAKE_DAMAGE = 4f;
	public static final int HUG_TICKS = 60;
	public static final float HUG_SQUEEZE_DAMAGE = 2f, HUG_SLAM_DAMAGE = 8f;
	public static final int HOLD_TICKS = 200;
	public static final double REACH = 4.5;

	// (the id stays the old Iron Stance one so a modifier left from an older build is cleared too)
	private static final ResourceLocation KB = PowerToggles.id("omnitrix_four_arms_stance");
	private static final ResourceLocation RAGE_DAMAGE = PowerToggles.id("omnitrix_four_arms_rage_damage");
	private static final ResourceLocation RAGE_SPEED = PowerToggles.id("omnitrix_four_arms_rage_speed");
	private static final ResourceLocation RAGE_MOVE = PowerToggles.id("omnitrix_four_arms_rage_move");

	/** Every 4th punch: both upper fists together. */
	public static final OmnitrixSfx.Cue SND_HAMMER = OmnitrixSfx.cue("four_arms_hammer")
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.5f, 0.55f)
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.45f, 0.7f)
			.layer(SoundEvents.ANVIL_LAND, 0.12f, 0.5f, 1);
	/** The uppercut: a whoosh and a crack. */
	public static final OmnitrixSfx.Cue SND_UPPERCUT = OmnitrixSfx.cue("four_arms_uppercut")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 0.7f)
			.layer(SoundEvents.PLAYER_ATTACK_CRIT, 0.45f, 0.7f)
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.4f, 0.8f, 1);
	/** The ground splitting (Hammer Smash, the crack line). */
	public static final OmnitrixSfx.Cue SND_CRACK = OmnitrixSfx.cue("four_arms_crack")
			.layer(SoundEvents.DEEPSLATE_BREAK, 0.45f, 0.6f)
			.layer(SoundEvents.GRAVEL_BREAK, 0.3f, 0.7f)
			.gap(2);
	/** Tearing a boulder out of the ground. */
	public static final OmnitrixSfx.Cue SND_RIP = OmnitrixSfx.cue("four_arms_rip")
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.5f, 0.6f)
			.layer(SoundEvents.STONE_BREAK, 0.45f, 0.6f)
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.3f, 0.7f, 2);
	/** The Giant Swing's whoosh, round and round. */
	public static final OmnitrixSfx.Cue SND_SWING = OmnitrixSfx.cue("four_arms_swing")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 0.6f)
			.layer(SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.15f, 0.6f)
			.gap(5);
	/** The Thunder Clap: a huge sonic bang (bigger than the old clap). */
	public static final OmnitrixSfx.Cue SND_THUNDER = OmnitrixSfx.cue("four_arms_thunder")
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.6f, 0.5f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.4f, 1.3f)
			.layer(SoundEvents.WIND_CHARGE_BURST, 0.5f, 0.6f, 1)
			.layer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.12f, 1.6f, 2);
	/** Swatting a projectile / a blocked hit. */
	public static final OmnitrixSfx.Cue SND_BLOCK = OmnitrixSfx.cue("four_arms_block")
			.layer(SoundEvents.SHIELD_BLOCK, 0.45f, 0.7f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.3f, 0.8f)
			.gap(2);
	/** The Bear Hug's squeeze: creaking and crunching. */
	public static final OmnitrixSfx.Cue SND_SQUEEZE = OmnitrixSfx.cue("four_arms_squeeze")
			.layer(SoundEvents.IRON_GOLEM_DAMAGE, 0.3f, 0.6f)
			.layer(SoundEvents.BONE_BLOCK_BREAK, 0.3f, 0.6f)
			.gap(4);
	/** The Meteor Slam's impact: deep boom, rubble. */
	public static final OmnitrixSfx.Cue SND_METEOR = OmnitrixSfx.cue("four_arms_meteor")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.6f, 0.5f)
			.layer(SoundEvents.DEEPSLATE_BREAK, 0.55f, 0.5f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.15f, 0.5f, 1)
			.layer(SoundEvents.GRAVEL_BREAK, 0.45f, 0.6f, 3);

	/** player -> creatures this Charge carries. */
	private static final Map<UUID, Set<Integer>> CHARGE_CARRY = new ConcurrentHashMap<>();
	/** thrown creature id -> the thrower, when it was thrown and what it hits for on landing. */
	private static final Map<Integer, Thrown> THROWN = new ConcurrentHashMap<>();
	/** player -> the Hammer Smash crack running ahead of him. */
	private static final Map<UUID, Smash> SMASHES = new ConcurrentHashMap<>();
	/** player -> the creature in his Bear Hug. */
	private static final Map<UUID, Hug> HUGS = new ConcurrentHashMap<>();
	/** player -> creatures the Giant Swing already struck. */
	private static final Map<UUID, Set<Integer>> SWING_HITS = new ConcurrentHashMap<>();
	/** player -> the game tick his kit last ran (a second call in the same tick is skipped). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private record Thrown(UUID thrower, long at, float damage) {
	}

	private record Smash(Vec3 from, Vec3 dir, long start, ResourceKey<Level> dim, Set<Integer> hits) {
	}

	private record Hug(int target, long start) {
	}

	private FourArmsKit() {
	}

	public static void register() {
		Aliens.register(new FourArmsKit());
	}

	public static void clearSessionState() {
		CHARGE_CARRY.clear();
		THROWN.clear();
		SMASHES.clear();
		HUGS.clear();
		SWING_HITS.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 (user: "make fourarms armour rating iron level"): a full iron set, 15 armour (was 8)
		return AlienStats.builder().health(40f).speed(0.85f).scale(1.4f).armor(15f, 2f).knockbackResist(0.6f).attack(6f).step(0.6f).jump(0.12f)
				.safeFall(6f).reach(0.5f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(FLURRY, AbilitySlot.SLOT_1, 50), AlienMove.shift(HAMMER, AbilitySlot.SLOT_1, 140),
				AlienMove.tap(GRAB, AbilitySlot.SLOT_2, 20), AlienMove.shift(BOULDER, AbilitySlot.SLOT_2, 120),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 60), AlienMove.hold(LEAP_FAR, AbilitySlot.SLOT_3, 100),
				AlienMove.shift(CHARGE, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(CLAP, AbilitySlot.SLOT_4, 200), AlienMove.shift(METEOR, AbilitySlot.SLOT_4, 500),
				AlienMove.tap(CLIMB, AbilitySlot.SLOT_5, 20), AlienMove.shift(GUARD, AbilitySlot.SLOT_5, 240),
				AlienMove.tap(RAMPAGE, AbilitySlot.SLOT_6, 600), AlienMove.shift(HUG, AbilitySlot.SLOT_6, 300),
				AlienMove.vanilla(MIGHT, "Attack", 0), AlienMove.vanilla(THROW, "Use (holding) / G (holding)", 200),
				AlienMove.vanilla(SWING, "Shift+G (holding)", 160));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.8f, 1.1f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.6f).life(14).ground().spawn();
	}

	@Override
	public void onRevert(ServerPlayer p) {
		setFlag(p, F_CLIMB, false);
		for (int f : new int[] { F_LEAP_CHARGE, F_GUARD, F_RAMPAGE, F_HUGGING, F_SWINGING, F_CHARGING, F_LEAPING, F_DIVING }) {
			setFlag(p, f, false);
		}
		for (String t : new String[] { "leap_charge", "far_vx", "far_vz", "far_on", "far_hard", "flurry_start", "flurry_target", "guard_end", "rampage_end",
				"charge_end", "meteor_rise", "swing_start", "kb_until", "punch_n" }) {
			setTimer(p, t, 0);
		}
		drop(p, false);
		clearRage(p);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, KB);
		CHARGE_CARRY.remove(p.getUUID());
		SMASHES.remove(p.getUUID());
		HUGS.remove(p.getUUID());
		SWING_HITS.remove(p.getUUID());
		LAST_TICK.remove(p.getUUID());
		THROWN.values().removeIf(t -> t.thrower().equals(p.getUUID()));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_5 ? flag(p, F_CLIMB) || flag(p, F_GUARD) : slot == AbilitySlot.SLOT_6 && (flag(p, F_RAMPAGE) || flag(p, F_HUGGING));
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return; // once a game tick (the flurry beats and hug squeezes count ticks)
		}
		ServerLevel l = level(p);
		tickClimb(p, l, now);
		tickHeld(p, l, s, now);
		tickFlurry(p, l, now);
		tickSmash(p, l, now);
		tickLanding(p, l, now);
		tickCharge(p, l, now);
		tickGuard(p, l, now);
		tickRampage(p, l, now);
		tickHug(p, l, now);
		// no knockback while guarding, raging, hugging, or for a moment after a small mob's hit
		boolean braced = flag(p, F_GUARD) || flag(p, F_RAMPAGE) || flag(p, F_HUGGING) || timer(p, "kb_until") > now;
		if (braced) {
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, KB);
			if (timer(p, "kb_until") != 0) {
				setTimer(p, "kb_until", 0);
			}
		}
		tickThrown(l);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					hammerSmash(p);
				} else {
					flurry(p);
				}
			}
			case SLOT_2 -> {
				boolean holding = Omnitrix.state(p).heldEntity != 0;
				if (holding && shift) {
					giantSwing(p);
				} else if (holding) {
					throwHeld(p);
				} else if (shift) {
					boulderRip(p);
				} else {
					grab(p, lookTarget(p, REACH));
				}
			}
			case SLOT_3 -> {
				if (shift) {
					charge(p);
				} else {
					leap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					meteorSlam(p);
				} else {
					clap(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					guard(p);
				} else {
					toggleClimb(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					bearHug(p, lookTarget(p, REACH));
				} else {
					rampage(p);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- R: Four-Fist Flurry

	/** R: four fast alternating punches (one from each fist) then a rising uppercut that launches. */
	public boolean flurry(ServerPlayer p) {
		if (!use(p, FLURRY)) {
			return false;
		}
		anim(p, ANIM_FLURRY);
		long now = p.level().getGameTime();
		setTimer(p, "flurry_start", now);
		LivingEntity t = lookTarget(p, REACH);
		setTimer(p, "flurry_target", t == null ? 0 : t.getId() + 1L);
		flurryHit(p, level(p), 0);
		return true;
	}

	private void tickFlurry(ServerPlayer p, ServerLevel l, long now) {
		long start = timer(p, "flurry_start");
		if (start <= 0) {
			return;
		}
		long d = now - start;
		if (d == 3 || d == 6 || d == 9) {
			flurryHit(p, l, (int) (d / 3));
		} else if (d >= 13) {
			setTimer(p, "flurry_start", 0);
			flurryHit(p, l, 4);
			setTimer(p, "flurry_target", 0);
		}
	}

	/** One beat of the Flurry: 0-3 the four fists, 4 the uppercut. */
	private void flurryHit(ServerPlayer p, ServerLevel l, int beat) {
		LivingEntity t = lookTarget(p, REACH);
		if (t == null) {
			long id = timer(p, "flurry_target");
			Entity e = id > 0 ? l.getEntity((int) (id - 1)) : null;
			t = e instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < (REACH + 1.5) * (REACH + 1.5) ? le : null;
		}
		boolean upper = beat == 4;
		Vec3 fist = p.getEyePosition().add(p.getLookAngle().scale(1.8)).add(0, -0.3, 0);
		if (upper) {
			OmnitrixSounds.play(l, p.position(), SND_UPPERCUT, 1.1f, 1.0f);
			QuirkFx.ring(l, t != null ? t.position().add(0, t.getBbHeight() * 0.4, 0) : fist).flags(QuirkFx.F_VERTICAL).vec(new Vec3(0, 1, 0))
					.colours(0xFFFFFF, 0xFFC0A0).size(2f).thickness(0.4f).life(9).spawn();
		} else {
			OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 0.8f, 1.0f + beat * 0.08f);
		}
		if (t == null) {
			return;
		}
		t.invulnerableTime = 0;
		hurt(p, t, upper ? UPPERCUT_DAMAGE : FLURRY_PUNCH_DAMAGE, false);
		if (upper) {
			shove(t, p.position(), 0.4, 1.05);
			l.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 1, 0, 0, 0, 0);
			QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		} else {
			shove(t, p.position(), 0.15, 0.02);
			l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
			QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
		}
	}

	// ---------------------------------------------------------------- Shift+R: Hammer Smash

	/** Shift+R: all four fists overhead and down -- the ground cracks in a line ahead, launching whatever stands on it. */
	public boolean hammerSmash(ServerPlayer p) {
		if (!use(p, HAMMER)) {
			return false;
		}
		anim(p, ANIM_HAMMER_SMASH);
		Vec3 dir = Vec3.directionFromRotation(0, p.getYRot());
		SMASHES.put(p.getUUID(), new Smash(p.position(), dir, p.level().getGameTime() + 5, p.level().dimension(), new HashSet<>()));
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.5f, 1.2f);
		return true;
	}

	private void tickSmash(ServerPlayer p, ServerLevel l, long now) {
		Smash sm = SMASHES.get(p.getUUID());
		if (sm == null || now < sm.start()) {
			return;
		}
		int step = (int) (now - sm.start());
		if (step >= HAMMER_LENGTH || sm.dim() != l.dimension()) {
			SMASHES.remove(p.getUUID());
			return;
		}
		if (step == 0) {
			// the fists land
			Vec3 c = sm.from().add(sm.dir().scale(1.2));
			QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(2.5f).thickness(0.7f).life(12).ground().spawn();
			OmnitrixSounds.play(l, c, OmnitrixSounds.FOUR_ARMS_SLAM, 1.3f, 0.8f);
			QuirkCamera.shakeAround(l, c, 14, 0.55f, 10);
		}
		Vec3 at = sm.from().add(sm.dir().scale(1.5 + step));
		BlockPos ground = groundUnder(l, BlockPos.containing(at.x, sm.from().y - 0.5, at.z));
		if (ground == null) {
			SMASHES.remove(p.getUUID()); // the crack runs off a ledge
			return;
		}
		BlockState under = l.getBlockState(ground);
		Vec3 top = Vec3.atBottomCenterOf(ground.above());
		QuirkFx.decal(l, top.add(0, 0.02, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size(1.6f).yaw(p.getYRot()).life(200).spawn();
		QuirkFx.shards(l, top.add(0, 0.2, 0)).block(under).size(1.2f).count(8).spawn();
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), top.x, top.y + 0.1, top.z, 14, 0.4, 0.1, 0.4, 0.15);
		if (step % 2 == 0) {
			OmnitrixSounds.play(l, top, SND_CRACK, 0.9f, 0.9f);
		}
		for (LivingEntity e : around(p, top.add(0, 0.5, 0), 1.7)) {
			if (sm.hits().add(e.getId())) {
				e.invulnerableTime = 0;
				hurt(p, e, HAMMER_DAMAGE, false);
				shove(e, sm.from(), 0.3, 0.95);
			}
		}
	}

	/** The top solid block at {@code pos}'s column (a little above to a little below), or null. */
	private static BlockPos groundUnder(ServerLevel l, BlockPos pos) {
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos b = pos.offset(0, dy, 0);
			if (!l.getBlockState(b).getCollisionShape(l, b).isEmpty() && l.getBlockState(b.above()).getCollisionShape(l, b.above()).isEmpty()) {
				return b;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- G: Grab / Throw

	/** Grabs {@code target} (anything up to horse size; not the Ender Dragon / Wither, not a boss with a threat table). */
	public boolean grab(ServerPlayer p, LivingEntity target) {
		if (!liftable(p, target)) {
			return false;
		}
		if (!use(p, GRAB)) {
			return false;
		}
		anim(p, ANIM_GRAB);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.heldEntity = target.getId();
		n.kitFlags |= F_HOLDING;
		Omnitrix.save(p, n);
		setTimer(p, "held_at", p.level().getGameTime());
		target.stopRiding();
		OmnitrixSounds.play(level(p), target.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 0.7f, 0.6f);
		return true;
	}

	/** Small enough to lift / hug (with the action-bar line when not). */
	private static boolean liftable(ServerPlayer p, LivingEntity target) {
		if (target == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.four_arms.nothing_to_grab").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (target.getBbHeight() > 3.2f || target.getBbWidth() > 2.2f || target instanceof EnderDragon || target instanceof WitherBoss
				|| com.projecthero.mod.quirk.QuirkThreat.add(target, p, 6f)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.four_arms.too_big").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		return true;
	}

	public boolean throwHeld(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		Entity e = level(p).getEntity(s.heldEntity);
		if (!(e instanceof LivingEntity le) || flag(p, F_SWINGING)) {
			if (!(e instanceof LivingEntity)) {
				drop(p, false);
			}
			return false;
		}
		drop(p, false);
		anim(p, ANIM_THROW);
		Vec3 look = p.getLookAngle();
		le.setDeltaMovement(look.x * 2.1, look.y * 2.1 + 0.35, look.z * 2.1);
		le.hurtMarked = true;
		THROWN.put(le.getId(), new Thrown(p.getUUID(), p.level().getGameTime(), THROW_DAMAGE));
		cooldown(p, THROW, -1);
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.FOUR_ARMS_THROW, 1.2f, 1.0f);
		return true;
	}

	/** Lets go of whatever he holds ({@code gently}: it is set down in front of him). */
	private void drop(ServerPlayer p, boolean gently) {
		setFlag(p, F_SWINGING, false);
		setTimer(p, "swing_start", 0);
		SWING_HITS.remove(p.getUUID());
		OmnitrixState s = Omnitrix.state(p);
		if (s.heldEntity == 0) {
			return;
		}
		Entity e = level(p).getEntity(s.heldEntity);
		OmnitrixState n = s.copy();
		n.heldEntity = 0;
		n.kitFlags &= ~F_HOLDING;
		Omnitrix.save(p, n);
		if (e != null && gently) {
			Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
			e.teleportTo(at.x, p.getY(), at.z);
		}
	}

	/** The held creature rides above his head -- or, in the Giant Swing, whirls round him at arm's length. */
	private void tickHeld(ServerPlayer p, ServerLevel l, OmnitrixState s, long now) {
		if (s.heldEntity == 0) {
			return;
		}
		Entity e = l.getEntity(s.heldEntity);
		long since = now - timer(p, "held_at");
		if (!(e instanceof LivingEntity le) || !le.isAlive() || (since > HOLD_TICKS && !flag(p, F_SWINGING))) {
			drop(p, true);
			return;
		}
		if (flag(p, F_SWINGING)) {
			long t = now - timer(p, "swing_start");
			if (t >= SWING_TICKS) {
				fling(p, le);
				return;
			}
			// two and a half turns, speeding up
			double ang = Math.toRadians(p.getYRot()) + t * (0.35 + t * 0.012);
			Vec3 at = p.position().add(-Math.sin(ang) * 2.6, 1.0, Math.cos(ang) * 2.6);
			le.teleportTo(at.x, at.y, at.z);
			le.setDeltaMovement(Vec3.ZERO);
			le.resetFallDistance();
			le.hurtMarked = true;
			Set<Integer> hits = SWING_HITS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
			for (LivingEntity o : around(p, at, 1.8)) {
				if (o != le && hits.add(o.getId())) {
					hurt(p, o, SWING_HIT_DAMAGE, false);
					shove(o, p.position(), 1.3, 0.4);
					OmnitrixSounds.play(l, o.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 0.9f, 0.8f);
				}
			}
			if (t % 6 == 0) {
				OmnitrixSounds.play(l, p.position(), SND_SWING, 0.9f, 0.9f + t * 0.01f);
			}
			if (t % 3 == 0) {
				l.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
			}
			return;
		}
		Vec3 at = p.position().add(0, p.getBbHeight() + 0.15, 0).add(p.getLookAngle().multiply(1, 0, 1).scale(0.3));
		le.teleportTo(at.x, at.y, at.z);
		le.setDeltaMovement(Vec3.ZERO);
		le.resetFallDistance();
		le.hurtMarked = true;
	}

	/** A thrown creature hits (or is dropped after 3 s): damage to it and a little to what it lands among. */
	private void tickThrown(ServerLevel l) {
		long now = l.getGameTime();
		for (Map.Entry<Integer, Thrown> e : List.copyOf(THROWN.entrySet())) {
			Entity ent = l.getEntity(e.getKey());
			ServerPlayer thrower = l.getServer().getPlayerList().getPlayer(e.getValue().thrower());
			if (ent == null || thrower == null || now - e.getValue().at() > 60 || !(ent instanceof LivingEntity le)) {
				THROWN.remove(e.getKey());
				continue;
			}
			if (now - e.getValue().at() > 2 && (le.onGround() || le.horizontalCollision || le.isInWater())) {
				THROWN.remove(e.getKey());
				le.invulnerableTime = 0;
				hurt(thrower, le, e.getValue().damage(), false);
				for (LivingEntity o : around(thrower, le.position(), 2.5)) {
					if (o != le) {
						hurt(thrower, o, 4f, false);
						shove(o, le.position(), 0.6, 0.3);
					}
				}
				QuirkFx.ring(l, le.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(2.5f).thickness(0.5f).life(12).ground().spawn();
				OmnitrixSounds.play(l, le.position(), OmnitrixSounds.FOUR_ARMS_SLAM, 1.0f, 1.2f);
			}
		}
	}

	// ---------------------------------------------------------------- Shift+G: Boulder Rip / Giant Swing

	/**
	 * Shift+G, empty-handed: tear the ground in front of his feet up (a real block when terrain damage is on and he may build
	 * there; otherwise a copy of it) and hurl it along his look -- it shatters on whatever it hits.
	 */
	public boolean boulderRip(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 flat = Vec3.directionFromRotation(0, p.getYRot());
		BlockPos front = groundUnder(l, BlockPos.containing(p.position().add(flat.scale(1.2)).add(0, -0.5, 0)));
		if (front == null) {
			front = p.blockPosition().below();
		}
		BlockState st = l.getBlockState(front);
		if (st.isAir() || !st.getFluidState().isEmpty() || st.getCollisionShape(l, front).isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.four_arms.need_ground").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, BOULDER)) {
			return false;
		}
		anim(p, ANIM_BOULDER);
		float hard = st.getDestroySpeed(l, front);
		boolean real = !st.hasBlockEntity() && hard >= 0f && hard < 50f && canSmash(p, l, front);
		BlockState rock = st.hasBlockEntity() || hard < 0f ? Blocks.STONE.defaultBlockState() : st;
		if (real) {
			l.removeBlock(front, false);
		}
		Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(1.6)).add(0, 0.2, 0);
		ThrownBlockEntity b = ThrownBlockEntity.create(l, p, rock, hand.add(0, -0.5, 0), BOULDER_DAMAGE);
		b.dropsItems = real;
		b.setDeltaMovement(p.getLookAngle().scale(1.7).add(0, 0.2, 0));
		l.addFreshEntity(b);
		Vec3 at = Vec3.atCenterOf(front);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y + 0.5, at.z, 30, 0.5, 0.3, 0.5, 0.15);
		QuirkFx.shards(l, at.add(0, 0.6, 0)).block(st).size(1.4f).count(12).spawn();
		OmnitrixSounds.play(l, at, SND_RIP, 1.1f, 1.0f);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_THROW, 1.0f, 0.9f);
		QuirkCamera.shake(p, 0.3f, 6);
		return true;
	}

	/** Shift+G while holding someone: the Giant Swing -- round and round, battering everything near, then flung. */
	public boolean giantSwing(ServerPlayer p) {
		if (Omnitrix.state(p).heldEntity == 0 || flag(p, F_SWINGING) || !use(p, SWING)) {
			return false;
		}
		anim(p, ANIM_SWING);
		setFlag(p, F_SWINGING, true);
		setTimer(p, "swing_start", p.level().getGameTime());
		SWING_HITS.put(p.getUUID(), new HashSet<>());
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.6f, 1.2f);
		return true;
	}

	private void fling(ServerPlayer p, LivingEntity le) {
		drop(p, false);
		anim(p, ANIM_THROW);
		Vec3 look = p.getLookAngle();
		le.teleportTo(p.getX() + look.x * 1.5, p.getY() + 1.2, p.getZ() + look.z * 1.5);
		le.setDeltaMovement(look.x * 2.6, look.y * 2.0 + 0.45, look.z * 2.6);
		le.hurtMarked = true;
		THROWN.put(le.getId(), new Thrown(p.getUUID(), p.level().getGameTime(), SWING_FLING_DAMAGE));
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_THROW, 1.4f, 0.8f);
		QuirkFx.ring(l, p.position().add(0, 1.0, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xE0D0C0).size(3.5f).life(10).spawn();
		QuirkCamera.shake(p, 0.35f, 8);
	}

	// ---------------------------------------------------------------- X: Super Leap

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3;
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (slot != AbilitySlot.SLOT_3 || !p.onGround() || !ready(p, LEAP_FAR)) {
			return;
		}
		// the hold began KIT_HOLD_TICKS after the press: count the charge from the press
		setTimer(p, "leap_charge", p.level().getGameTime() - com.projecthero.mod.omnitrix.OmnitrixConfig.KIT_HOLD_TICKS);
		setFlag(p, F_LEAP_CHARGE, true);
		anim(p, ANIM_CROUCH);
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.4f, 0.8f);
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		if (slot != AbilitySlot.SLOT_3 || !flag(p, F_LEAP_CHARGE)) {
			return;
		}
		setFlag(p, F_LEAP_CHARGE, false);
		setTimer(p, "leap_charge", 0);
		farLeap(p, Math.min(1f, heldTicks / (float) LEAP_CHARGE_TICKS));
	}

	/** The long leap's distance for a charge 0..1. */
	public static double leapBlocks(float charge) {
		return LEAP_TAP_BLOCKS + (LEAP_FULL_BLOCKS - LEAP_TAP_BLOCKS) * Math.max(0f, Math.min(1f, charge));
	}

	/** The charge 0..1 of the long leap being held (client-safe), 0 when none. */
	public float leapCharge(Player p) {
		long at = timer(p, "leap_charge");
		if (at <= 0 || !flag(p, F_LEAP_CHARGE)) {
			return 0f;
		}
		return Math.min(1f, (p.level().getGameTime() - at) / (float) LEAP_CHARGE_TICKS);
	}

	/**
	 * v0.17.2 (user: "four arms leap isnt fully directional still ... sometimes he doesnt leap in the direction the player is
	 * looking"): a held speed as a kit timer, which must stay positive -- setTimer drops anything <= 0, and the raw bits of a
	 * negative speed are negative, so every leap heading -x or -z had that axis held at 0. Micro-blocks a tick, offset.
	 */
	private static final long SPEED_OFFSET = 1L << 40;

	private static long speedBits(double v) {
		return SPEED_OFFSET + Math.round(v * 1.0e6);
	}

	private static double bitsSpeed(long bits) {
		return bits == 0 ? 0 : (bits - SPEED_OFFSET) / 1.0e6;
	}

	/** The long leap's held launch speed (client-safe), or null when none is in flight. */
	public Vec3 farLeapVelocity(Player p) {
		if (!flag(p, F_LEAPING) || timer(p, "far_on") == 0) {
			return null;
		}
		return new Vec3(bitsSpeed(timer(p, "far_vx")), 0, bitsSpeed(timer(p, "far_vz")));
	}

	public boolean farLeap(ServerPlayer p, float charge) {
		if (!p.onGround() || !use(p, LEAP_FAR)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		launch(p, leapBlocks(charge));
		ServerLevel l = level(p);
		float k = 1f + charge * 2f;
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(2.2f * k).thickness(0.5f * k).life(12).ground().spawn();
		l.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 1 + (int) (charge * 3), 0.6, 0.1, 0.6, 0);
		QuirkCamera.shake(p, 0.2f + charge * 0.5f, 8);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_SLAM, 0.8f + charge * 0.4f, 1.2f - charge * 0.4f);
		return true;
	}

	/**
	 * v0.17.1 (user: "for four arms leap make it directional so the player leaps in the direction that they are looking in,
	 * make it similar to hulks leap"): every leap -- the tap and the charged one -- is the Hulk's Super Leap arc
	 * ({@code HulkLeap.launchVelocity}): it heads where he looks, its angle follows his pitch (kept in the Hulk's band, so a
	 * look down still clears the ground and a look up arcs high), and it flies drag-free to come down {@code blocks} away.
	 */
	private void launch(ServerPlayer p, double blocks) {
		Vec3 v = com.projecthero.mod.hulk.HulkLeap.launchVelocity(p.getLookAngle(), blocks, p.getGravity());
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		setFlag(p, F_LEAPING, true);
		setTimer(p, "air_at", p.level().getGameTime());
		setTimer(p, "far_vx", speedBits(v.x));
		setTimer(p, "far_vz", speedBits(v.z));
		setTimer(p, "far_on", 1);
		setTimer(p, "far_hard", blocks > LEAP_TAP_BLOCKS * 1.5 ? 1 : 0);
	}

	public boolean leap(ServerPlayer p) {
		if (!use(p, LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		launch(p, LEAP_TAP_BLOCKS);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(2.2f).thickness(0.5f).life(10).ground().spawn();
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_SLAM, 0.7f, 1.4f);
		return true;
	}

	/** Super Leap / Meteor Slam: the rise turning into the dive, and the landings. */
	private void tickLanding(ServerPlayer p, ServerLevel l, long now) {
		boolean leaping = flag(p, F_LEAPING);
		boolean diving = flag(p, F_DIVING);
		if ((leaping || diving) && now - timer(p, "air_at") > 4 && (p.onGround() || p.isInWater())) {
			boolean far = timer(p, "far_on") != 0; // (a launch straight along an axis has a vx or vz of 0)
			boolean hard = timer(p, "far_hard") != 0;
			setFlag(p, F_LEAPING, false);
			setFlag(p, F_DIVING, false);
			for (String t : new String[] { "far_vx", "far_vz", "far_on", "far_hard", "meteor_rise" }) {
				setTimer(p, t, 0);
			}
			if (diving) {
				meteorLand(p, l);
			} else if (far && hard) {
				land(p, 5.5f, LEAP_FAR_LAND_DAMAGE); // the long leap comes down harder
			} else {
				land(p, 3.5f, LEAP_LAND_DAMAGE);
			}
			p.resetFallDistance();
		}
		if (leaping || diving) {
			p.resetFallDistance();
		}
		long rise = timer(p, "meteor_rise");
		if (rise > 0 && flag(p, F_LEAPING) && (now - rise > 30 || (now - rise > 4 && p.getDeltaMovement().y < 0.15))) {
			setTimer(p, "meteor_rise", 0);
			startDive(p);
		}
	}

	private void land(ServerPlayer p, float radius, float damage) {
		ServerLevel l = level(p);
		Vec3 c = p.position();
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(radius + 1f).thickness(0.8f).life(14).ground().spawn();
		OmnitrixSounds.play(l, c, OmnitrixSounds.FOUR_ARMS_SLAM, 1.0f, 1.0f);
		QuirkCamera.shakeAround(l, c, 14, 0.4f, 10);
		for (LivingEntity e : around(p, c, radius)) {
			hurt(p, e, damage, false);
			shove(e, c, 0.9, 0.5);
		}
	}

	// ---------------------------------------------------------------- Shift+X: Tetramand Charge

	/** Shift+X: a bull rush -- enemies are scooped up and carried along, weak blocks burst, and it ends in a shove. */
	public boolean charge(ServerPlayer p) {
		if (!use(p, CHARGE)) {
			return false;
		}
		anim(p, ANIM_CHARGE);
		setFlag(p, F_CHARGING, true);
		setTimer(p, "charge_end", p.level().getGameTime() + CHARGE_TICKS);
		CHARGE_CARRY.put(p.getUUID(), new HashSet<>());
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.7f, 1.3f);
		return true;
	}

	private void tickCharge(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "charge_end");
		if (end <= 0) {
			return;
		}
		Vec3 dir = Vec3.directionFromRotation(0, p.getYRot());
		smashAhead(p, l, dir);
		if (now >= end || p.horizontalCollision) {
			endCharge(p, l, dir);
			return;
		}
		p.setDeltaMovement(dir.x * 0.95, Math.min(0, p.getDeltaMovement().y), dir.z * 0.95);
		p.hurtMarked = true;
		Set<Integer> carry = CHARGE_CARRY.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (LivingEntity e : around(p, p.position().add(dir.scale(1.3)).add(0, 1, 0), 1.9)) {
			if (carry.add(e.getId())) {
				hurt(p, e, CHARGE_DAMAGE, false);
				OmnitrixSounds.play(l, e.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 1.0f, 0.7f);
			}
		}
		// the carried ride on his shoulders, pinned just ahead
		int i = 0;
		for (int id : carry) {
			if (l.getEntity(id) instanceof LivingEntity e && e.isAlive() && e.getBbHeight() <= 3.2f) {
				Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(((i++ % 3) - 1) * 0.7);
				Vec3 at = p.position().add(dir.scale(1.6)).add(side);
				e.teleportTo(at.x, Math.max(e.getY(), p.getY()), at.z);
				e.setDeltaMovement(p.getDeltaMovement());
				e.hurtMarked = true;
			}
		}
		if (now % 2 == 0) {
			BlockState under = l.getBlockState(p.blockPosition().below());
			if (!under.isAir()) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.4, 0.05, 0.4, 0.1);
			}
		}
	}

	private void endCharge(ServerPlayer p, ServerLevel l, Vec3 dir) {
		setTimer(p, "charge_end", 0);
		setFlag(p, F_CHARGING, false);
		Set<Integer> carry = CHARGE_CARRY.remove(p.getUUID());
		Vec3 c = p.position().add(dir.scale(1.5));
		if (carry != null) {
			for (int id : carry) {
				if (l.getEntity(id) instanceof LivingEntity e && e.isAlive()) {
					e.invulnerableTime = 0;
					hurt(p, e, CHARGE_END_DAMAGE, false);
					shove(e, p.position(), 1.9, 0.5);
				}
			}
		}
		QuirkFx.ring(l, c.add(0, 1.0, 0)).flags(QuirkFx.F_VERTICAL).vec(dir).colours(0xFFFFFF, 0xE0D0C0).size(2.4f).thickness(0.5f).life(9).spawn();
		QuirkCamera.shake(p, 0.4f, 8);
		OmnitrixSounds.play(l, c, OmnitrixSounds.FOUR_ARMS_SLAM, 0.9f, 1.3f);
	}

	/** The Charge bursts weak blocks (glass, leaves, light wood) in the 3-wide, 3-high slab ahead of him. */
	private void smashAhead(ServerPlayer p, ServerLevel l, Vec3 dir) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		for (int ahead = 1; ahead <= 2; ahead++) {
			for (int s = -1; s <= 1; s++) {
				for (int up = 0; up <= 2; up++) {
					BlockPos pos = BlockPos.containing(p.position().add(dir.scale(ahead * 0.9)).add(side.scale(s * 0.8)).add(0, up + 0.2, 0));
					BlockState st = l.getBlockState(pos);
					if (weakBlock(l, pos, st) && canSmash(p, l, pos)) {
						l.destroyBlock(pos, true, p);
					}
				}
			}
		}
	}

	/** Glass, leaves, wooden fences / gates / doors / trapdoors, wool, and anything nearly as soft. */
	public static boolean weakBlock(ServerLevel l, BlockPos pos, BlockState st) {
		if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
			return false;
		}
		if (st.is(BlockTags.LEAVES) || st.is(ConventionalBlockTags.GLASS_BLOCKS) || st.is(ConventionalBlockTags.GLASS_PANES) || st.is(BlockTags.WOODEN_FENCES)
				|| st.is(BlockTags.FENCE_GATES) || st.is(BlockTags.WOODEN_DOORS) || st.is(BlockTags.WOODEN_TRAPDOORS) || st.is(BlockTags.WOOL)) {
			return true;
		}
		float h = st.getDestroySpeed(l, pos);
		return h >= 0f && h <= 0.3f && !st.getCollisionShape(l, pos).isEmpty();
	}

	/** Terrain damage on, and he may change the world here. */
	private static boolean canSmash(ServerPlayer p, ServerLevel l, BlockPos pos) {
		return HeroConfig.get().abilityTerrainDamage && p.mayBuild() && l.mayInteract(p, pos);
	}

	// ---------------------------------------------------------------- Z: Thunder Clap

	/** Z: his strongest clap -- a 10-block sonic cone that blasts back, stuns and dizzies, shatters glass and blows fire out. */
	public boolean clap(ServerPlayer p) {
		if (!use(p, CLAP)) {
			return false;
		}
		anim(p, ANIM_CLAP);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		if (com.projecthero.mod.hulk.HulkAbilities.airborne(p)) {
			// v0.17.1 (user: "make four arms thunderclap able to reposition him in the air aswell like the hulk can"): the Hulk's air
			// clap -- his momentum dies and the recoil throws him the opposite way to his look (clap down and he goes up); mid-leap
			// the held launch speed becomes the recoil, so he sails on it, and the leap's landing still counts
			Vec3 push = look.normalize().scale(-com.projecthero.mod.hulk.HulkConfig.abilities().airClapPush);
			p.setDeltaMovement(push);
			p.hurtMarked = true;
			if (timer(p, "far_on") != 0) {
				setTimer(p, "far_vx", speedBits(push.x));
				setTimer(p, "far_vz", speedBits(push.z));
			}
			p.resetFallDistance();
		}
		Vec3 hands = eye.add(look.scale(1.2)).add(0, -0.4, 0);
		QuirkFx.ring(l, hands).flags(QuirkFx.F_VERTICAL).vec(look).colours(0xFFFFFF, 0xC8D8FF).size(4f).thickness(0.7f).life(12).spawn();
		QuirkFx.ring(l, hands.add(look.scale(3))).flags(QuirkFx.F_VERTICAL).vec(look).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xC8D8FF).size(6f).life(14).spawn();
		QuirkFx.cone(l, hands, look).style(QuirkFx.CONE_DEBRIS).colours(0xD8D0C0, 0x8A8070).size(3f).length(CLAP_RANGE).life(14).spawn();
		OmnitrixSounds.play(l, hands, SND_THUNDER, 1.5f, 1.0f);
		QuirkCamera.shakeAround(l, hands, 16, 0.7f, 12);
		QuirkCamera.fovPunch(p, 0.12f, 8);
		double cos = Math.cos(Math.toRadians(50));
		for (LivingEntity e : around(p, eye.add(look.scale(CLAP_RANGE * 0.5)), CLAP_RANGE * 0.75)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= CLAP_RANGE + 0.5 && to.normalize().dot(look) > cos) {
				hurt(p, e, CLAP_DAMAGE, false);
				shove(e, p.position(), 2.2, 0.45);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3)); // stunned
				e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0)); // dizzy
				e.clearFire();
			}
		}
		p.clearFire();
		// the shockwave: glass in the cone shatters (terrain damage on), fire in it goes out
		BlockPos c = BlockPos.containing(eye);
		int r = (int) CLAP_RANGE;
		boolean grief = HeroConfig.get().abilityTerrainDamage && p.mayBuild();
		for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			BlockState st = l.getBlockState(pos);
			boolean fire = st.is(BlockTags.FIRE);
			boolean glass = st.is(ConventionalBlockTags.GLASS_BLOCKS) || st.is(ConventionalBlockTags.GLASS_PANES);
			if (!fire && !glass) {
				continue;
			}
			Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
			if (to.length() > CLAP_RANGE || to.length() < 0.5 || to.normalize().dot(look) <= cos || !l.mayInteract(p, pos)) {
				continue;
			}
			if (fire) {
				l.removeBlock(pos, false);
				l.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.02);
			} else if (grief) {
				l.destroyBlock(pos.immutable(), false, p);
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- Shift+Z: Meteor Slam (ultimate)

	/** Shift+Z, his ultimate: up into the sky, then down like a meteor -- a radius-10 quake, harder the higher he fell. */
	public boolean meteorSlam(ServerPlayer p) {
		if (!use(p, METEOR)) {
			return false;
		}
		ServerLevel l = level(p);
		if (p.onGround() || com.projecthero.mod.hulk.HulkAbilities.airborne(p) && p.fallDistance < 1f && p.getDeltaMovement().y > -0.2) {
			anim(p, ANIM_METEOR_RISE);
			p.setDeltaMovement(p.getDeltaMovement().x * 0.2, METEOR_RISE, p.getDeltaMovement().z * 0.2);
			p.hurtMarked = true;
			setFlag(p, F_LEAPING, true);
			setTimer(p, "air_at", l.getGameTime());
			setTimer(p, "far_on", 0);
			setTimer(p, "meteor_rise", l.getGameTime());
			QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(4f).thickness(0.8f).life(14).ground().spawn();
			l.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.8, 0.1, 0.8, 0);
			OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_SLAM, 1.2f, 0.9f);
			OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 1.0f, 0.9f);
			QuirkCamera.shake(p, 0.4f, 8);
			return true;
		}
		startDive(p);
		return true;
	}

	private void startDive(ServerPlayer p) {
		anim(p, ANIM_DIVE);
		setFlag(p, F_LEAPING, false);
		setFlag(p, F_DIVING, true);
		setTimer(p, "far_on", 0);
		setTimer(p, "air_at", p.level().getGameTime());
		setTimer(p, "dive_from", (long) (p.getY() * 1000.0) + SPEED_OFFSET);
		p.setDeltaMovement(p.getDeltaMovement().x * 0.3, -3.0, p.getDeltaMovement().z * 0.3);
		p.hurtMarked = true;
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.8f, 0.9f);
	}

	/** The Meteor Slam's damage for a fall of {@code blocks}. */
	public static float meteorDamage(double blocks) {
		return (float) Math.min(METEOR_MAX_DAMAGE, METEOR_DAMAGE + Math.max(0, blocks) * METEOR_PER_BLOCK);
	}

	private void meteorLand(ServerPlayer p, ServerLevel l) {
		long from = timer(p, "dive_from");
		double fell = from == 0 ? 0 : (from - SPEED_OFFSET) / 1000.0 - p.getY();
		setTimer(p, "dive_from", 0);
		float dmg = meteorDamage(fell);
		Vec3 c = p.position();
		BlockState under = l.getBlockState(p.blockPosition().below());
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(METEOR_RADIUS).thickness(1.2f).life(18).ground().spawn();
		QuirkFx.ring(l, c.add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, COLOUR).size(METEOR_RADIUS * 0.6f).life(14).ground().spawn();
		QuirkFx.ring(l, c.add(0, 1.0, 0)).flags(QuirkFx.F_VERTICAL).vec(new Vec3(0, 1, 0)).colours(0xFFFFFF, 0xE0D0C0).size(3f).thickness(0.6f).life(10).spawn();
		QuirkFx.decal(l, c.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size(8f).life(400).spawn();
		if (!under.isAir()) {
			QuirkFx.shards(l, c.add(0, 0.2, 0)).block(under).size(4f).count(36).spawn();
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.2, c.z, 80, METEOR_RADIUS * 0.4, 0.2, METEOR_RADIUS * 0.4, 0.2);
		}
		l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
		OmnitrixSounds.play(l, c, SND_METEOR, 1.6f, 0.9f);
		QuirkCamera.shakeAround(l, c, 28, 1.0f, 18);
		for (LivingEntity e : around(p, c, METEOR_RADIUS)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			e.invulnerableTime = 0;
			hurt(p, e, (float) (dmg * (1.0 - 0.3 * Math.min(1.0, d / METEOR_RADIUS))), false);
			shove(e, c, 1.4, 0.8);
		}
	}

	// ---------------------------------------------------------------- V: Wall Climb, Shift+V: Four-Arm Guard

	/** V: Wall Climb on / off -- four hands dig into any wall: pressed into one (not sneaking) he climbs it like a ladder. */
	public boolean toggleClimb(ServerPlayer p) {
		if (!use(p, CLIMB)) {
			return false;
		}
		boolean on = !flag(p, F_CLIMB);
		setFlag(p, F_CLIMB, on);
		anim(p, ANIM_CLIMB);
		ServerLevel l = level(p);
		l.playSound(null, p.getX(), p.getY(), p.getZ(), on ? SoundEvents.ARMOR_EQUIP_TURTLE.value() : SoundEvents.ARMOR_EQUIP_LEATHER.value(),
				SoundSource.PLAYERS, 0.9f, on ? 0.7f : 1.0f);
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.four_arms.climb_on" : "message.projecthero.omnitrix.four_arms.climb_off")
				.withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY), true);
		return true;
	}

	/** Wall Climb on: pressed into a wall (and not sneaking) he climbs it. Asked on both sides (synced flag). */
	@Override
	public boolean climbs(Player p) {
		return flag(p, F_CLIMB) && p.horizontalCollision && !p.isShiftKeyDown();
	}

	private void tickClimb(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_CLIMB)) {
			return;
		}
		p.resetFallDistance();
		if (p.horizontalCollision && !p.onGround() && now % 4 == 0) {
			// grit from the wall where the fingers bite in
			BlockPos at = BlockPos.containing(p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.9)).add(0, 1.0, 0));
			BlockState st = l.getBlockState(at);
			if (!st.isAir()) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX(), p.getY() + 1.2, p.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
			}
			if (now % 12 == 0) {
				l.playSound(null, p.getX(), p.getY(), p.getZ(), st.getSoundType().getHitSound(), SoundSource.PLAYERS, 0.5f, 0.7f);
			}
		}
	}

	/** Shift+V: 3 s with all four arms crossed in front -- frontal hits are blocked, projectiles swatted back, no knockback. */
	public boolean guard(ServerPlayer p) {
		if (!use(p, GUARD)) {
			return false;
		}
		anim(p, ANIM_GUARD);
		setFlag(p, F_GUARD, true);
		setTimer(p, "guard_end", p.level().getGameTime() + GUARD_TICKS);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(2f).thickness(0.6f).life(10).ground().spawn();
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_STANCE, 1.0f, 1.0f);
		return true;
	}

	/** In front of him: within about 75 degrees either side of where he faces. */
	public boolean inFront(ServerPlayer p, Vec3 from) {
		Vec3 to = from.subtract(p.position());
		Vec3 h = new Vec3(to.x, 0, to.z);
		if (h.lengthSqr() < 1.0e-4) {
			return true;
		}
		return h.normalize().dot(Vec3.directionFromRotation(0, p.getYRot())) > 0.25;
	}

	private void tickGuard(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "guard_end");
		if (end <= 0) {
			return;
		}
		if (now >= end) {
			setTimer(p, "guard_end", 0);
			setFlag(p, F_GUARD, false);
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 1, false, false, false));
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.0), pr -> pr.getOwner() != p)) {
			Vec3 to = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(pr.position());
			if (pr.getDeltaMovement().lengthSqr() < 0.01 || pr.getDeltaMovement().dot(to) <= 0 || !inFront(p, pr.position())) {
				continue;
			}
			swat(p, l, pr);
		}
	}

	/** Swats a projectile back where he faces. */
	public void swat(ServerPlayer p, ServerLevel l, Projectile pr) {
		pr.deflect(ProjectileDeflection.AIM_DEFLECT, p, p, true);
		pr.setOwner(p);
		l.sendParticles(ParticleTypes.CRIT, pr.getX(), pr.getY(), pr.getZ(), 8, 0.15, 0.15, 0.15, 0.1);
		OmnitrixSounds.play(l, pr.position(), SND_BLOCK, 0.9f, 1.1f);
	}

	// ---------------------------------------------------------------- C: Rampage, Shift+C: Bear Hug

	/** C: 8 s of Tetramand fury -- every punch quakes, he hits harder and faster and cannot be knocked back; winded after. */
	public boolean rampage(ServerPlayer p) {
		if (!use(p, RAMPAGE)) {
			return false;
		}
		anim(p, ANIM_RAMPAGE);
		setFlag(p, F_RAMPAGE, true);
		setTimer(p, "rampage_end", p.level().getGameTime() + RAMPAGE_TICKS);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, RAGE_DAMAGE, RAMPAGE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, RAGE_SPEED, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, RAGE_MOVE, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		QuirkFx.ring(l, c.add(0, 0.05, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, COLOUR).size(6f).life(18).ground().spawn();
		QuirkFx.ring(l, c.add(0, 1.6, 0)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xFF8080).size(3f).life(14).spawn();
		OmnitrixSounds.play(l, c, OmnitrixSounds.FOUR_ARMS_ROAR, 1.6f, 0.85f);
		QuirkCamera.shakeAround(l, c, 12, 0.5f, 12);
		QuirkCamera.vignette(p, COLOUR, 0.35f, RAMPAGE_TICKS);
		return true;
	}

	private void tickRampage(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "rampage_end");
		if (end <= 0) {
			return;
		}
		if (now >= end) {
			setTimer(p, "rampage_end", 0);
			clearRage(p);
			// winded
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WINDED_TICKS, 0));
			p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WINDED_TICKS, 0));
			OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.4f, 0.6f);
			return;
		}
		if (now % 5 == 0) {
			l.sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + p.getBbHeight() * 0.9, p.getZ(), 1, 0.5, 0.3, 0.5, 0);
		}
	}

	private void clearRage(ServerPlayer p) {
		setFlag(p, F_RAMPAGE, false);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, RAGE_DAMAGE);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, RAGE_SPEED);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, RAGE_MOVE);
	}

	/** Shift+C: grab the creature in front and crush it for 3 s (it cannot get away), then slam it into the ground. */
	public boolean bearHug(ServerPlayer p, LivingEntity target) {
		if (Omnitrix.state(p).heldEntity != 0 || HUGS.containsKey(p.getUUID()) || !liftable(p, target)) {
			return false;
		}
		if (!use(p, HUG)) {
			return false;
		}
		anim(p, ANIM_HUG);
		setFlag(p, F_HUGGING, true);
		HUGS.put(p.getUUID(), new Hug(target.getId(), p.level().getGameTime()));
		target.stopRiding();
		OmnitrixSounds.play(level(p), target.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 0.8f, 0.6f);
		return true;
	}

	/** The creature in his Bear Hug, or null. */
	public LivingEntity hugged(ServerPlayer p) {
		Hug h = HUGS.get(p.getUUID());
		return h != null && level(p).getEntity(h.target()) instanceof LivingEntity le ? le : null;
	}

	private void tickHug(ServerPlayer p, ServerLevel l, long now) {
		Hug h = HUGS.get(p.getUUID());
		if (h == null) {
			return;
		}
		LivingEntity t = l.getEntity(h.target()) instanceof LivingEntity le && le.isAlive() ? le : null;
		long d = now - h.start();
		if (t == null || t.distanceToSqr(p) > 64) {
			HUGS.remove(p.getUUID());
			setFlag(p, F_HUGGING, false);
			return;
		}
		Vec3 flat = Vec3.directionFromRotation(0, p.getYRot());
		if (d >= HUG_TICKS) {
			// the slam: down in front of him
			HUGS.remove(p.getUUID());
			setFlag(p, F_HUGGING, false);
			anim(p, ANIM_HUG_SLAM);
			Vec3 at = p.position().add(flat.scale(1.6));
			t.teleportTo(at.x, p.getY(), at.z);
			t.setDeltaMovement(0, -0.5, 0);
			t.hurtMarked = true;
			t.invulnerableTime = 0;
			hurt(p, t, HUG_SLAM_DAMAGE, false);
			t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
			BlockState under = l.getBlockState(BlockPos.containing(at).below());
			QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(3f).thickness(0.7f).life(12).ground().spawn();
			QuirkFx.decal(l, at.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size(2.5f).life(200).spawn();
			if (!under.isAir()) {
				QuirkFx.shards(l, at.add(0, 0.2, 0)).block(under).size(1.8f).count(14).spawn();
			}
			OmnitrixSounds.play(l, at, OmnitrixSounds.FOUR_ARMS_SLAM, 1.3f, 0.8f);
			QuirkCamera.shakeAround(l, at, 12, 0.6f, 10);
			return;
		}
		// held fast against his chest
		Vec3 at = p.position().add(flat.scale(1.1)).add(0, 0.6, 0);
		t.teleportTo(at.x, at.y, at.z);
		t.setDeltaMovement(Vec3.ZERO);
		t.resetFallDistance();
		t.hurtMarked = true;
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false, false));
		if (d % 10 == 5) { // six squeezes
			t.invulnerableTime = 0;
			hurt(p, t, HUG_SQUEEZE_DAMAGE, false);
			l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 6, 0.25, 0.25, 0.25, 0.1);
			OmnitrixSounds.play(l, t.position(), SND_SQUEEZE, 0.9f, 1.0f);
			QuirkCamera.shake(p, 0.15f, 4);
		}
	}

	// ---------------------------------------------------------------- vanilla keys: Tetramand Might

	/** The fist the next punch of the passive cycle uses (0-3; 3 = the double-fist hammer). */
	public int punchIndex(Player p) {
		long n = timer(p, "punch_n");
		return (int) (n <= 0 ? 0 : (n - 1) % 4);
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		OmnitrixState s = Omnitrix.state(p);
		if (s.heldEntity != 0 && target.getId() == s.heldEntity || HUGS.containsKey(p.getUUID())) {
			return true; // never punch what you are holding
		}
		int i = punchIndex(p);
		setTimer(p, "punch_n", i + 2L); // stored +1 (setTimer drops 0)
		ServerLevel l = level(p);
		boolean hammer = i == 3;
		anim(p, switch (i) {
			case 0 -> ANIM_PUNCH_R;
			case 1 -> ANIM_PUNCH_L;
			case 2 -> ANIM_PUNCH_LOW_R;
			default -> ANIM_HAMMER_PUNCH;
		});
		if (target instanceof LivingEntity le) {
			if (hammer) {
				// both upper fists together: knocked down and back
				shove(le, p.position(), 1.3, 0.3);
				le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
				OmnitrixSounds.play(l, le.position(), SND_HAMMER, 1.0f, 1.0f);
				QuirkFx.ring(l, le.position().add(0, le.getBbHeight() * 0.6, 0)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, 0xFFC0A0)
						.size(1.6f).thickness(0.4f).life(8).spawn();
				QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
			} else {
				shove(le, p.position(), 0.5, 0.15);
				OmnitrixSounds.play(l, le.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 0.7f, 1.1f);
			}
			if (flag(p, F_RAMPAGE)) {
				rampageQuake(p, l, le);
			}
		}
		return false;
	}

	/** Rampage: a punch sends a small quake through the ground around what it hit. */
	private void rampageQuake(ServerPlayer p, ServerLevel l, LivingEntity hit) {
		Vec3 c = hit.position();
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.5f).life(10).ground().spawn();
		BlockState under = l.getBlockState(hit.blockPosition().below());
		if (!under.isAir()) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.1, c.z, 12, 1.0, 0.05, 1.0, 0.1);
		}
		OmnitrixSounds.play(l, c, SND_CRACK, 0.7f, 1.1f);
		for (LivingEntity o : around(p, c, 3.0)) {
			if (o != hit) {
				hurt(p, o, RAMPAGE_QUAKE_DAMAGE, false);
				shove(o, c, 0.6, 0.35);
			}
		}
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && Omnitrix.state(p).heldEntity != 0) {
			return throwHeld(p) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (Omnitrix.state(p).heldEntity != 0) {
			return throwHeld(p) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return target instanceof LivingEntity le && grab(p, le) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) && (flag(p, F_LEAPING) || flag(p, F_DIVING))) {
			return false; // his own landings
		}
		// whatever he holds or hugs cannot hurt him
		OmnitrixState s = Omnitrix.state(p);
		Entity src = source.getEntity();
		Hug h = HUGS.get(p.getUUID());
		if (src != null && (s.heldEntity != 0 && src.getId() == s.heldEntity || h != null && src.getId() == h.target())) {
			return false;
		}
		// Four-Arm Guard: anything coming from in front is blocked
		if (flag(p, F_GUARD) && source.getSourcePosition() != null && !source.is(DamageTypeTags.BYPASSES_ARMOR) && inFront(p, source.getSourcePosition())) {
			ServerLevel l = level(p);
			Vec3 at = p.getEyePosition().add(Vec3.directionFromRotation(0, p.getYRot()).scale(0.8));
			l.sendParticles(ParticleTypes.CRIT, at.x, at.y - 0.4, at.z, 6, 0.3, 0.3, 0.3, 0.1);
			OmnitrixSounds.play(l, at, SND_BLOCK, 0.9f, 1.0f);
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		// Tetramand Might: a small mob's blow cannot shift him (no knockback for this hit)
		if (smallMob(source.getEntity())) {
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
			setTimer(p, "kb_until", p.level().getGameTime() + 2);
		}
		return amount;
	}

	/** A mob no bigger than a player-sized creature (never a player). */
	public static boolean smallMob(Entity e) {
		return e instanceof Mob m && m.getBbHeight() <= 2.1f && m.getBbWidth() <= 1.0f;
	}
}
