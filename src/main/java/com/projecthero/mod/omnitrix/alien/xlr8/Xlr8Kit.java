package com.projecthero.mod.omnitrix.alien.xlr8;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * from {@code SuperSpeedMoves} / {@code SuperSpeedHandlers} (Rapid Assault, Mach Punch, Blitz, Speed Sweep, Afterimage Decoys,
 * the after-image streaks, Phase's settle) on this kit's own cooldowns, XLR8's own Tornado Run / Blur Dash / Whirlwind / Kick
 * Barrage / Time Burst / Speed Mine, and V = Speed Mode: the visor slams down, he runs at speedster pace with an after-image
 * trail, runs up walls and across water and steps over blocks. The visor is down (the closed-mask skin) while Speed Mode is on
 * and during every speed move; it opens at rest.
 *
 * <pre>
 *   R        Rapid Assault   4 punches of 5 on everything in front           Shift+R  Mach Punch     one huge punch (20) and a shockwave (6)
 *   G        Blitz           zip to the foe you aim at (24 blocks): 20,     Shift+G  Tornado Run    3 s of running rings: a twister lifts what
 *                            and 10 to everything round it                                           is near, then throws it out
 *   X        Blur Dash       a 10-block dash leaving afterimages (3)         Shift+X  Speed Sweep    blink through every foe within 50 (15 each),
 *   hold X   Phase           vibrate through walls while held (5 s max);                             then home
 *                            nothing physical touches you
 *   Z        Whirlwind       spin: everything within 6 is blown away (4)     Shift+Z  Kick Barrage   8 kicks in 0.8 s (1.5 each), the last launches
 *   V        Speed Mode      toggle: visor down, flat-out speed, after-      Shift+V  Afterimage     three afterimages draw every foe off you (6 s)
 *                            images, wall and water running, step assist               Decoys
 *   C        Time Burst      everything within 12 slows to a crawl for 5 s   Shift+C  Speed Mine     strip-mine a 1x2 tunnel 16 blocks ahead
 *   Attack   a double jab (+1.5)     Use (empty hand)  sweep up every dropped item within 14     Use on a mob  snatch what it holds
 * </pre>
 */
public final class Xlr8Kit extends AlienKit {
	public static final String ID = "xlr8";
	public static final int COLOUR = 0x3A7BD5;
	public static final int GLOW = 0x9FD8FF;

	public static final int ANIM_ASSAULT = 1;
	public static final int ANIM_TORNADO = 2;
	public static final int ANIM_BLITZ = 3;
	public static final int ANIM_SNATCH = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_MODE = 6;
	public static final int ANIM_WHIRL = 7;
	public static final int ANIM_KICKS = 8;
	public static final int ANIM_BURST = 9;
	public static final int ANIM_MINE = 10;
	public static final int ANIM_JAB = 11;
	public static final int ANIM_SWEEP = 12;
	public static final int ANIM_MACH = 13;
	public static final int ANIM_SPEED_SWEEP = 14;
	public static final int ANIM_PHASE = 15;
	public static final int ANIM_DECOYS = 16;

	/** v0.17.1 revamp: V's Speed Mode (was Shift+X's Wall Run, which it now includes). */
	public static final int F_SPEED = 1;
	public static final int F_TORNADO = 2;
	public static final int F_DASH = 4;
	/** v0.17.1 revamp: hold X's Phase. */
	public static final int F_PHASE = 8;
	/** v0.17.1 (user: "when you hold V as xlr8 you enter overdrive mode like with regular speedsters"): hold V's Overdrive. */
	public static final int F_OVERDRIVE = 16;
	public static final String OVERDRIVE = "overdrive";
	/** Overdrive: {@link #OVERDRIVE_TICKS} of Speed Mode with {@link #OVERDRIVE_BONUS} more speed on top, blows twice as hard. */
	public static final int OVERDRIVE_TICKS = 20 * 20, OVERDRIVE_COOLDOWN = 45 * 20;
	public static final double OVERDRIVE_BONUS = 2.0, OVERDRIVE_DAMAGE = 1.0, OVERDRIVE_STEP = 1.0;

	public static final String ASSAULT = "rapid_assault";
	public static final String MACH = "mach_punch";
	public static final String BLITZ = "blitz";
	public static final String TORNADO = "tornado_run";
	public static final String DASH = "blur_dash";
	public static final String SPEED_SWEEP = "speed_sweep";
	public static final String PHASE = "phase";
	public static final String WHIRL = "whirlwind";
	public static final String KICKS = "kick_barrage";
	public static final String MODE = "speed_mode";
	public static final String DECOYS = "afterimage_decoys";
	public static final String BURST = "time_burst";
	public static final String MINE = "speed_mine";
	public static final String JAB = "double_jab";
	public static final String SWEEP = "item_sweep";
	public static final String SNATCH = "snatch";

	public static final float TORNADO_HIT = 1.5f;
	public static final float TORNADO_END = 4f;
	public static final int TORNADO_TICKS = 60;
	public static final double TORNADO_RADIUS = 6;
	public static final float DASH_DAMAGE = 3f;
	public static final float WHIRL_DAMAGE = 4f;
	public static final double WHIRL_RADIUS = 6;
	public static final float KICK_HIT = 1.5f;
	public static final int KICK_TICKS = 16;
	public static final double BURST_RADIUS = 12;
	public static final int BURST_TICKS = 100;
	public static final int MINE_LENGTH = 16;
	public static final float JAB_EXTRA = 1.5f;
	public static final double SPRINT_BONUS = 0.5;
	/** v0.17.1 revamp: Speed Mode on top of his body's 1.9x -- about 16 blocks a second walking, 30+ sprinting. */
	public static final double SPEED_MODE_BONUS = 1.0;
	/**
	 * v0.17.1 (user: "make xlr8s step assist better and dont make him lose momentum on going up blocks"): he always steps up
	 * {@link #BASE_STEP} more than a player (1.1 blocks) and Speed Mode adds a full block more (1.6) -- at his speed a rise he
	 * cannot step was a wall that stopped him dead; Overdrive adds another block on top.
	 */
	public static final double BASE_STEP = 0.5;
	public static final double SPEED_MODE_STEP = 1.0;
	public static final int PHASE_MAX_TICKS = 100;
	public static final int PHASE_COOLDOWN = 100;
	/** The visor stays down this long after a speed move. */
	public static final int VISOR_TICKS = 30;

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
	private static final ResourceLocation OD_STEP_ID = PowerToggles.id("omnitrix_xlr8_overdrive_step");

	/** player -> the Tornado Run's eye. */
	private static final Map<UUID, Twister> TWISTERS = new ConcurrentHashMap<>();
	/** player -> creatures the current Blur Dash already hit. */
	private static final Map<UUID, Set<Integer>> DASH_HITS = new ConcurrentHashMap<>();

	private record Twister(ResourceKey<Level> dim, Vec3 at) {
	}

	private Xlr8Kit() {
	}

	public static void register() {
		Aliens.register(new Xlr8Kit());
	}

	public static void clearSessionState() {
		TWISTERS.clear();
		DASH_HITS.clear();
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
		return AlienStats.builder().health(22f).speed(1.9f).scale(0.95f).armor(2f, 0f).attack(1f).step(0.65f).jump(0.06f).safeFall(6f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(ASSAULT, AbilitySlot.SLOT_1, 40), AlienMove.shift(MACH, AbilitySlot.SLOT_1, 200),
				AlienMove.tap(BLITZ, AbilitySlot.SLOT_2, 100), AlienMove.shift(TORNADO, AbilitySlot.SLOT_2, 300),
				AlienMove.tap(DASH, AbilitySlot.SLOT_3, 40), AlienMove.shift(SPEED_SWEEP, AbilitySlot.SLOT_3, 300),
				AlienMove.hold(PHASE, AbilitySlot.SLOT_3, PHASE_COOLDOWN),
				AlienMove.tap(WHIRL, AbilitySlot.SLOT_4, 200), AlienMove.shift(KICKS, AbilitySlot.SLOT_4, 140),
				AlienMove.tap(MODE, AbilitySlot.SLOT_5, 20), AlienMove.hold(OVERDRIVE, AbilitySlot.SLOT_5, OVERDRIVE_COOLDOWN),
				AlienMove.shift(DECOYS, AbilitySlot.SLOT_5, 400),
				AlienMove.tap(BURST, AbilitySlot.SLOT_6, 600), AlienMove.shift(MINE, AbilitySlot.SLOT_6, 300),
				AlienMove.vanilla(JAB, "Attack", 0), AlienMove.vanilla(SWEEP, "Use (empty hand)", 60),
				AlienMove.vanilla(SNATCH, "Use on a creature (empty hand)", 160));
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
		if (flag(p, F_PHASE)) {
			SuperSpeedHandlers.settleOutsideBlocks(p);
		}
		endOverdrive(p, false);
		setFlag(p, F_SPEED | F_TORNADO | F_DASH | F_PHASE, false);
		setTimer(p, "phase_end", 0);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, MODE_ID);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, MODE_STEP_ID);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, BASE_STEP_ID);
		SuperSpeedMoves.endSweep(p); // a running sweep still brings him home
		TWISTERS.remove(p.getUUID());
		DASH_HITS.remove(p.getUUID());
	}

	/** Speed Mode: pressed into a wall (and not sneaking) he runs straight up it, like a ladder. */
	@Override
	public boolean climbs(Player p) {
		return flag(p, F_SPEED) && p.horizontalCollision && !p.isShiftKeyDown();
	}

	/** Speed Mode: water holds him up (Shift to sink in). */
	@Override
	public boolean standsOnFluid(Player p, FluidState fluid) {
		return flag(p, F_SPEED) && fluid.is(FluidTags.WATER) && !p.isShiftKeyDown() && !p.isUnderWater();
	}

	/** Hold X: through walls on his own level. */
	@Override
	public boolean phasing(Player p) {
		return flag(p, F_PHASE);
	}

	/** Phasing: nothing physical touches him (the void, /kill and the like still do). */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !flag(p, F_PHASE) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	/**
	 * v0.17.1 revamp: the visor is down -- Speed Mode, a dash, the tornado, Phase, a sweep, or a speed move in the last
	 * {@link #VISOR_TICKS} (client-safe: the closed-mask skin is drawn from it).
	 */
	public static boolean visorDown(Player p) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & (F_SPEED | F_TORNADO | F_DASH | F_PHASE)) != 0) {
			return true;
		}
		boolean speedMove = s.animId != ANIM_JAB && s.animId != ANIM_SWEEP && s.animId != ANIM_SNATCH && s.animId > 0
				&& s.animId < Omnitrix.ANIM_TRANSFORM;
		return speedMove && p.level().getGameTime() - s.animStart < VISOR_TICKS;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		// sprinting: faster still
		boolean sprint = p.isSprinting();
		var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		boolean has = speed != null && speed.getModifier(SPRINT_ID) != null;
		if (sprint && !has) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID, SPRINT_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else if (!sprint && has) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID);
		}
		if (now % 20 == 0) {
			PowerToggles.modifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, 2.0, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(p, Attributes.STEP_HEIGHT, BASE_STEP_ID, BASE_STEP, AttributeModifier.Operation.ADD_VALUE);
		}
		boolean mode = flag(p, F_SPEED);
		if (mode) {
			p.resetFallDistance();
			if (p.isInWater() && !p.isUnderWater()) {
				p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0, 1).add(0, 0.12, 0)); // pops back up onto the surface
			}
			if (now % 40 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, false));
			}
			boolean moving = p.getDeltaMovement().horizontalDistanceSqr() > 0.04;
			if (moving && now % 2 == 0) {
				l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.15, 0.02, 0.15, 0.0);
				l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.6, p.getZ(), 1, 0.2, 0.3, 0.2, 0.01);
			}
			if (p.horizontalCollision && now % 3 == 0) {
				l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
			}
		} else if (sprint && now % 2 == 0) {
			l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.15, 0.02, 0.15, 0.0);
		}
		if (flag(p, F_OVERDRIVE)) {
			if (now >= timer(p, "od_end")) {
				endOverdrive(p, true);
			} else if (p.getDeltaMovement().horizontalDistanceSqr() > 0.09 && now % 2 == 0) {
				l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.8, p.getZ(), 3, 0.3, 0.5, 0.3, 0.08);
			}
		}
		tickPhase(p, l, now);
		tickTornado(p, l, now);
		tickDash(p, l, now);
		tickKicks(p, l, now);
		SuperSpeedMoves.sweepTick(p); // the borrowed Speed Sweep (nothing else runs it for an alien)
	}

	private void tickPhase(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_PHASE)) {
			return;
		}
		if (now >= timer(p, "phase_end") || !p.isAlive()) {
			endPhase(p);
			return;
		}
		p.resetFallDistance();
		if (now % 2 == 0) {
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.6, 0.3, 0.05);
			SuperSpeedMoves.streak(p, SpeedStreakPayload.KIND_STREAK, p.position().add((p.getRandom().nextDouble() - 0.5) * 0.3, 0,
					(p.getRandom().nextDouble() - 0.5) * 0.3), p.position(), GLOW, 4);
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
				hurt(p, e, TORNADO_END, false);
				shove(e, t.at(), 1.1, 0.5);
			}
			QuirkFx.ring(l, t.at().add(0, 0.1, 0)).colours(0xFFFFFF, GLOW).size((float) TORNADO_RADIUS + 2).thickness(0.6f).life(14).ground().spawn();
			return;
		}
		int k = (int) (end - now);
		// XLR8 runs rings round the eye (if there is room), a streak of afterimages behind him
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
		for (LivingEntity e : around(p, t.at(), TORNADO_RADIUS)) {
			Vec3 to = t.at().subtract(e.position());
			Vec3 in = new Vec3(to.x, 0, to.z);
			if (in.lengthSqr() < 1.0e-4) {
				in = new Vec3(1, 0, 0);
			}
			in = in.normalize();
			Vec3 tangent = new Vec3(-in.z, 0, in.x);
			double res = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			double up = e.getY() > t.at().y + 5 ? -0.02 : 0.16;
			e.setDeltaMovement(tangent.scale(0.45 * res).add(in.scale(0.12 * res)).add(0, up * Math.max(0.3, res), 0));
			e.hurtMarked = true;
			e.resetFallDistance();
			if (k % 10 == 0) {
				e.invulnerableTime = 0;
				hurt(p, e, TORNADO_HIT, false);
			}
		}
		// the twister: a spiral of cloud
		for (int i = 0; i < 6; i++) {
			double h = i * 0.9;
			double aa = a * 1.4 + i * 1.1;
			double r = 0.6 + h * 0.35;
			l.sendParticles(ParticleTypes.CLOUD, t.at().x + Math.cos(aa) * r, t.at().y + h, t.at().z + Math.sin(aa) * r, 1, 0, 0.02, 0, 0.0);
		}
		if (k % 20 == 0) {
			OmnitrixSounds.play(l, t.at(), SND_TORNADO, 0.8f, 1.1f);
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
		Set<Integer> hits = DASH_HITS.computeIfAbsent(p.getUUID(), x -> new HashSet<>());
		for (LivingEntity e : around(p, p.position().add(0, 1, 0), 1.8)) {
			if (hits.add(e.getId())) {
				hurt(p, e, DASH_DAMAGE, false);
				shove(e, p.position(), 0.5, 0.2);
			}
		}
		l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
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
		if (left % 2 == 0) {
			boolean last = left <= 2;
			boolean any = false;
			for (LivingEntity e : cone(p, 3.6, 55)) {
				e.invulnerableTime = 0;
				hurt(p, e, KICK_HIT, false);
				shove(e, p.position(), last ? 1.4 : 0.05, last ? 0.5 : 0.02);
				l.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() * 0.4, e.getZ(), 1, 0, 0, 0, 0);
				any = true;
			}
			OmnitrixSounds.play(l, p.position(), SND_HIT, any ? 0.6f : 0.3f, 0.9f + (left % 4) * 0.1f);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3 || slot == AbilitySlot.SLOT_5; // hold X: Phase; hold V: Overdrive (a tap of V fires on release)
	}

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_PHASE)) {
			return; // while phasing every other key is swallowed (letting go of X ends it)
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					machPunch(p);
				} else {
					rapidAssault(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					tornadoRun(p);
				} else {
					blitz(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					speedSweep(p);
				} else {
					blurDash(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					kickBarrage(p);
				} else {
					whirlwind(p);
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
		if (slot == AbilitySlot.SLOT_3) {
			startPhase(p);
		} else if (slot == AbilitySlot.SLOT_5 && !flag(p, F_OVERDRIVE)) {
			overdrive(p);
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		if (slot == AbilitySlot.SLOT_3) {
			endPhase(p);
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

	/** R: the plain Speedster's Rapid Assault -- four punches of 5 on everything in front, each landing in full. */
	public int rapidAssault(ServerPlayer p) {
		if (!use(p, ASSAULT)) {
			return 0;
		}
		anim(p, ANIM_ASSAULT);
		int struck = SuperSpeedHandlers.rapidAssaultCore(p);
		ServerLevel l = level(p);
		QuirkFx.cone(l, p.getEyePosition().add(0, -0.4, 0), p.getLookAngle()).colours(0xFFFFFF, GLOW).size(1.4f).length(3.6f).life(10)
				.style(QuirkFx.CONE_DEBRIS).spawn();
		OmnitrixSounds.play(l, p.position(), SND_HIT, 0.8f, 1.3f);
		return struck;
	}

	/** Shift+R: the plain Speedster's Mach Punch -- a flat 20 and a shockwave, the knockback growing with his speed. */
	public boolean machPunch(ServerPlayer p) {
		if (!use(p, MACH)) {
			return false;
		}
		anim(p, ANIM_MACH);
		double bpt = Math.max(p.getDeltaMovement().horizontalDistance(), flag(p, F_SPEED) ? 1.6 : 0.0);
		SuperSpeedMoves.machPunchCore(p, (float) Mth.clamp(bpt / SuperSpeedMoves.MACH_FULL_SPEED, 0.0, 1.0));
		QuirkCamera.shakeAround(level(p), p.position(), 10, 0.3f, 6);
		return true;
	}

	/** G: the plain Speedster's Blitz -- a zip to the foe under the crosshair and a hit for 20 (10 to everything round it). */
	public boolean blitz(ServerPlayer p) {
		if (cooldownLeft(p, BLITZ) > 0) {
			use(p, BLITZ); // (the "not ready" line)
			return false;
		}
		if (!SuperSpeedMoves.blitzCore(p)) {
			return false; // nothing to hit: no cooldown
		}
		cooldown(p, BLITZ, -1);
		anim(p, ANIM_BLITZ);
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 1.0f, 1.0f);
		return true;
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
		hurt(p, target, 2f, false);
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, p.position().add(0, 1, 0), target.position().add(0, target.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, GLOW).size(0.3f).life(8)
				.style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 0.7f, 1.6f);
		return true;
	}

	public boolean blurDash(ServerPlayer p) {
		if (!use(p, DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		Vec3 f = flatLook(p);
		Vec3 from = p.position();
		p.setDeltaMovement(f.scale(2.4).add(0, Math.max(0.05, p.getLookAngle().y * 0.6), 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, "dash_end", p.level().getGameTime() + 8);
		setFlag(p, F_DASH, true);
		DASH_HITS.put(p.getUUID(), new HashSet<>());
		ServerLevel l = level(p);
		SuperSpeedMoves.streak(p, SpeedStreakPayload.KIND_STREAK, from, from.add(f.scale(5.0)), GLOW, 14);
		QuirkFx.trail(l, p).colours(0xFFFFFF, COLOUR).size(0.45f).size2(10f).life(18).vec(new Vec3(0, 0.9, 0)).style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 1.0f, 1.0f);
		return true;
	}

	/** Shift+X: the plain Speedster's Speed Sweep -- blink through every foe within 50 blocks (15 each), then home. Again: call it off. */
	public boolean speedSweep(ServerPlayer p) {
		if (SuperSpeedMoves.sweeping(p)) {
			SuperSpeedMoves.endSweep(p);
			return false;
		}
		if (cooldownLeft(p, SPEED_SWEEP) > 0) {
			use(p, SPEED_SWEEP);
			return false;
		}
		if (!SuperSpeedMoves.beginSweep(p)) {
			return false;
		}
		cooldown(p, SPEED_SWEEP, -1);
		anim(p, ANIM_SPEED_SWEEP);
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 1.0f, 1.3f);
		return true;
	}

	/** Hold X: vibrate his molecules -- through walls on his own level, untouchable, up to 5 s (letting go ends it). */
	public boolean startPhase(ServerPlayer p) {
		if (flag(p, F_PHASE) || cooldownLeft(p, PHASE) > 0) {
			if (!flag(p, F_PHASE)) {
				use(p, PHASE);
			}
			return false;
		}
		anim(p, ANIM_PHASE);
		setFlag(p, F_PHASE, true);
		setTimer(p, "phase_end", p.level().getGameTime() + PHASE_MAX_TICKS);
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 2.0f);
		OmnitrixSounds.play(l, p.position(), SND_VISOR, 0.6f, 1.4f);
		return true;
	}

	public void endPhase(ServerPlayer p) {
		if (!flag(p, F_PHASE)) {
			return;
		}
		setFlag(p, F_PHASE, false);
		setTimer(p, "phase_end", 0);
		SuperSpeedHandlers.settleOutsideBlocks(p);
		cooldown(p, PHASE, -1);
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 2.0f);
	}

	public boolean whirlwind(ServerPlayer p) {
		if (!use(p, WHIRL)) {
			return false;
		}
		anim(p, ANIM_WHIRL);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1, 0);
		p.clearFire();
		for (LivingEntity e : around(p, c, WHIRL_RADIUS)) {
			e.invulnerableTime = 0;
			hurt(p, e, WHIRL_DAMAGE, false);
			shove(e, c, 1.6, 0.7);
			e.clearFire();
		}
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(5), pr -> pr.getOwner() != p)) {
			Vec3 away = pr.position().subtract(c).normalize();
			pr.setDeltaMovement(away.scale(Math.max(0.6, pr.getDeltaMovement().length())));
			pr.hurtMarked = true;
		}
		for (int i = 0; i < 2; i++) {
			QuirkFx.ring(l, p.position().add(0, 0.2 + i * 1.2, 0)).colours(0xFFFFFF, GLOW).size((float) WHIRL_RADIUS).thickness(0.5f).life(12)
					.style(QuirkFx.RING_SOFT).spawn();
		}
		for (int k = 0; k < 24; k++) {
			double a = k * Math.PI / 12;
			l.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * 1.2, c.y - 0.6 + (k % 4) * 0.5, c.z + Math.sin(a) * 1.2, 0, Math.cos(a + 1.4), 0.1,
					Math.sin(a + 1.4), 0.6);
		}
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2;
			SuperSpeedMoves.streak(p, SpeedStreakPayload.KIND_STREAK, p.position().add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5),
					p.position().add(Math.cos(a + 1.4) * 1.5, 0, Math.sin(a + 1.4) * 1.5), GLOW, 8);
		}
		OmnitrixSounds.play(l, c, SND_TORNADO, 1.0f, 1.4f);
		QuirkCamera.shakeAround(l, c, 10, 0.2f, 6);
		return true;
	}

	public boolean kickBarrage(ServerPlayer p) {
		if (!use(p, KICKS)) {
			return false;
		}
		anim(p, ANIM_KICKS);
		setTimer(p, "kicks_end", p.level().getGameTime() + KICK_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 0.6f, 1.6f);
		return true;
	}

	/**
	 * V (v0.17.1 revamp, user: V = utility / mode): Speed Mode on / off. On: the visor slams down, his speed doubles again (on
	 * top of the body's 1.9x and the sprint bonus), an after-image trail, he runs up walls and across water, steps over a block
	 * and a half, never takes fall damage and heals slowly. Off: the visor opens and he is the plain (still fast) XLR8.
	 */
	public boolean toggleSpeedMode(ServerPlayer p) {
		if (!use(p, MODE)) {
			return false;
		}
		boolean on = !flag(p, F_SPEED);
		setFlag(p, F_SPEED, on);
		anim(p, ANIM_MODE);
		ServerLevel l = level(p);
		if (on) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, MODE_ID, SPEED_MODE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(p, Attributes.STEP_HEIGHT, MODE_STEP_ID, SPEED_MODE_STEP, AttributeModifier.Operation.ADD_VALUE);
			QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, GLOW).size(3.5f).thickness(0.4f).life(12).ground().spawn();
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.3, 0.6, 0.3, 0.15);
			l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, MODE_ID);
			PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, MODE_STEP_ID);
		}
		OmnitrixSounds.play(l, p.position(), SND_VISOR, 0.9f, on ? 1.0f : 0.8f);
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.xlr8.speed_on" : "message.projecthero.omnitrix.xlr8.speed_off")
				.withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
		return true;
	}

	/**
	 * Hold V: Overdrive (as the plain Speedster's V) -- {@link #OVERDRIVE_TICKS} of Speed Mode (switched on if it was off) with
	 * {@link #OVERDRIVE_BONUS} more speed on top, his blows twice as hard, Haste III, a block more step, a shock ring and a sonic
	 * boom off the start line and a crackle of sparks as he runs. A tap of V ends it early; Speed Mode goes back to how it was.
	 */
	public boolean overdrive(ServerPlayer p) {
		if (!use(p, OVERDRIVE)) {
			return false;
		}
		setTimer(p, "od_mode_was", flag(p, F_SPEED) ? 1 : 0);
		if (!flag(p, F_SPEED)) {
			setFlag(p, F_SPEED, true);
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, MODE_ID, SPEED_MODE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(p, Attributes.STEP_HEIGHT, MODE_STEP_ID, SPEED_MODE_STEP, AttributeModifier.Operation.ADD_VALUE);
		}
		setFlag(p, F_OVERDRIVE, true);
		setTimer(p, "od_end", p.level().getGameTime() + OVERDRIVE_TICKS);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, OD_SPEED_ID, OVERDRIVE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, OD_DAMAGE_ID, OVERDRIVE_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, OD_STEP_ID, OVERDRIVE_STEP, AttributeModifier.Operation.ADD_VALUE);
		p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, OVERDRIVE_TICKS, 2, false, false, true));
		anim(p, ANIM_MODE);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, GLOW).size(6f).thickness(0.6f).life(14).ground().spawn();
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.6, 0.8, 0.6, 0.3);
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
			setFlag(p, F_SPEED, false);
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, MODE_ID);
			PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, MODE_STEP_ID);
		}
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

	/** C: everything near slows to a crawl (bosses less, and their threat table hears of it). */
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
			l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
		}
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(BURST_RADIUS), pr -> pr.getOwner() != p)) {
			pr.setDeltaMovement(pr.getDeltaMovement().scale(0.08));
			pr.hurtMarked = true;
		}
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(0xFFFFFF, GLOW).size((float) BURST_RADIUS).thickness(0.3f).life(20).ground().spawn();
		QuirkFx.blast(l, c).colours(0xFFFFFF, GLOW).size(2.0f).life(12).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		QuirkCamera.vignette(p, GLOW, 0.5f, 40);
		OmnitrixSounds.play(l, c, SND_BURST, 1.2f, 1.0f);
		return true;
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
		QuirkFx.trail(l, p).colours(0xFFFFFF, COLOUR).size(0.4f).size2(10f).life(16).vec(new Vec3(0, 0.9, 0)).style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 1.0f, 0.8f);
		OmnitrixSounds.play(l, p.position(), SND_HIT, 0.8f, 0.6f);
		return mined;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le && ready(p, JAB)) {
			anim(p, ANIM_JAB);
			le.invulnerableTime = 0;
			hurt(p, le, JAB_EXTRA, false);
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
		return itemSweep(p) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty() || !(target instanceof Mob mob) || mob.getMainHandItem().isEmpty()) {
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
			l.sendParticles(ParticleTypes.CLOUD, it.getX(), it.getY() + 0.2, it.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
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
