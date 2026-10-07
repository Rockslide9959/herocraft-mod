package com.projecthero.mod.hero.power.p02;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.network.LaserBeamPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 02 -- Laser Vision (v0.14.5 rework: <b>six keys, a 0-100 heat gauge, real beam models</b>).
 *
 * <p>Every laser adds heat to a 0..100 gauge (v0.14.8: heat no longer scales any damage -- every move always hits for
 * its baseline). Fill it and you overheat: 3 s locked out. v0.14.8: heat only vents once no Laser Vision move has been
 * used for 5 s, and then at 3 heat a second ({@link #VENT_DELAY}, {@link #VENT_RATE}).
 *
 * <p>R Heat Vision (hold: +1 heat a second; aimed down in mid-air it slows your fall) / Shift+R Piercing Blast
 * (+10), G Sweeping Arc (+10), X Recoil Blast (+5), Z Maximum Output (1.5 s charge, then a 10 s powered-up beam that
 * pins heat at 100; refused above 50 heat), V Ignite (flint and steel at range, +2), C Thermal Vision (50 blocks).
 * Every move reaches 100 blocks.
 *
 * <p>The beams are drawn client-side as geometry ({@code client/mutation/v0145/LaserBeamRenderer}) from the
 * {@code p02.*} animation each move plays -- the animation is synced to every viewer tracking the shooter; viewers out of
 * tracking range but within sight of the beam get it as a {@link LaserBeamPayload} ({@link LaserBeams}).
 * No eye glow, no particle streams.
 */
public final class LaserVisionHandlers {
	public static final String KEY = "power_02_laser_vision";
	public static final float MAX_HEAT = 100.0f;
	/** Every Laser Vision move reaches this far. */
	public static final double RANGE = 100.0;
	/** Thermal Vision outlines living things within this many blocks (read by the client glow mixin). */
	public static final double THERMAL_RANGE = 50.0;

	public static final float HEAT_BEAM_PER_TICK = 1.0f / 20.0f;
	public static final float HEAT_PIERCING_BLAST = 10.0f;
	public static final float HEAT_SWEEPING_ARC = 10.0f;
	public static final float HEAT_RECOIL_BLAST = 5.0f;
	public static final float HEAT_IGNITE = 2.0f;
	/** Maximum Output cannot start above this much heat. */
	public static final float MAX_OUTPUT_HEAT_LIMIT = 50.0f;

	public static final int OVERHEAT_TICKS = 60;
	/** v0.14.8: heat starts venting 5 s after the last use of any Laser Vision move (every use re-arms it). */
	public static final int VENT_DELAY = 100;
	/** v0.14.8: 3 heat (3%) a second, overheated or not -- a full gauge takes ~33 s to vent. */
	public static final float VENT_RATE = 3.0f / 20.0f;
	/** Thermal Vision keeps the eyes warm: ~1.4 heat a second while it is on. */
	private static final float THERMAL_TRICKLE = 0.07f;

	public static final int PIERCING_BLAST_COOLDOWN = 150;
	public static final int MAX_OUTPUT_CHARGE_TICKS = 30;
	public static final int MAX_OUTPUT_TICKS = 200;
	public static final int SWEEP_TICKS = 10;

	/** The one-shot beam animations, with how long each one's beam is on screen (a running beam never cuts them off). */
	public static final String ANIM_BEAM = "p02.beam";
	public static final String ANIM_MAX = "p02.max";
	public static final String ANIM_MAX_CHARGE = "p02.max_charge";
	public static final String ANIM_PIERCE = "p02.pierce";
	public static final String ANIM_SWEEP = "p02.sweep";
	public static final String ANIM_RECOIL = "p02.recoil";
	public static final String ANIM_IGNITE = "p02.ignite";

	/** Pre-v0.14.5 resources that must not linger in a save (ult_charge is still a legacy HUD meter name). */
	private static final String[] STALE = { "ult_charge", "lance_charge", "lance_on", "lance_held", "mo_on", "mo_held",
			"beam_util", "mine_budget" };

	private LaserVisionHandlers() {
	}

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v, 1e9f);
	}

	/** True while any Laser Vision beam is running or charging. */
	public static boolean firing(ServerPlayer p) {
		return res(p, "beaming") > 0.5f || res(p, "mo_charging") > 0.5f || res(p, "max_ticks") > 0.5f
				|| res(p, "sweep_ticks") > 0.5f;
	}

	public static boolean maxOutputRunning(ServerPlayer p) {
		return res(p, "max_ticks") > 0.5f;
	}

	private static boolean maxOutputBusy(ServerPlayer p) {
		return maxOutputRunning(p) || res(p, "mo_charging") > 0.5f;
	}


	public static boolean overheated(ServerPlayer p) {
		return res(p, "overheat") > 0.5f;
	}

	/** Refuses (with the overheat message) while locked out. */
	private static boolean canFire(AbilityContext ctx) {
		if (overheated(ctx.player())) {
			ctx.actionBar("message.projecthero.laser.overheated");
			return false;
		}
		return true;
	}

	/**
	 * Adds heat; a full gauge trips the 3 s overheat lockout and cuts every channel. While Maximum Output runs the
	 * gauge is pinned at 100 and nothing overheats until it ends.
	 */
	public static void addHeat(ServerPlayer p, float amount) {
		set(p, "vent_delay", VENT_DELAY);
		if (maxOutputRunning(p)) {
			set(p, "heat", MAX_HEAT);
			return;
		}
		float h = Math.min(MAX_HEAT, res(p, "heat") + amount);
		set(p, "heat", h);
		if (h >= MAX_HEAT - 0.01f && !overheated(p)) {
			overheat(p);
		}
	}

	private static void overheat(ServerPlayer p) {
		set(p, "heat", MAX_HEAT);
		set(p, "overheat", OVERHEAT_TICKS);
		stopChannels(p);
		p.displayClientMessage(Component.translatable("message.projecthero.laser.overheated"), true);
		p.level().playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	private static void stopChannels(ServerPlayer p) {
		set(p, "beaming", 0);
		set(p, "beam_held", 0);
		set(p, "mo_charging", 0);
		set(p, "mo_charge", 0);
		set(p, "max_ticks", 0);
		MutationVisuals.stopIf(p, ANIM_BEAM);
		MutationVisuals.stopIf(p, ANIM_MAX);
		MutationVisuals.stopIf(p, ANIM_MAX_CHARGE);
	}

	private static void cooldownMessage(AbilityContext ctx) {
		ctx.actionBar("message.projecthero.ability.on_cooldown",
				Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
	}

	/** How long a one-shot beam animation keeps its beam on screen. */
	public static int oneShotTicks(String anim) {
		return switch (anim) {
			case ANIM_PIERCE -> 10;
			case ANIM_SWEEP -> SWEEP_TICKS + 2;
			case ANIM_RECOIL -> 7;
			case ANIM_IGNITE -> 5;
			default -> 0;
		};
	}

	/**
	 * Re-asserts a running beam's loop animation, but never over a one-shot laser move that is still on screen
	 * (a Sweeping Arc fired while holding R keeps its sweep until it is done).
	 */
	private static void ensureLoop(ServerPlayer p, String anim) {
		MutationVisualState s = MutationVisuals.state(p);
		if (anim.equals(s.anim())) {
			return;
		}
		int shot = oneShotTicks(s.anim());
		if (shot > 0 && p.level().getGameTime() - s.animStart() < shot) {
			return;
		}
		MutationVisuals.play(p, anim);
	}

	public static void register() {
		LaserCooking.initialize(); // v0.15.11: laser kills drop cooked food
		// R -- Heat Vision. Hold to beam (+1 heat a second); the damage ramps up the longer you hold it (to x2.5 after
		// 3 s). Aimed down while airborne, the beam holds you up like slow falling. Shift + R: Piercing Blast.
		AbilityHandlers.register(KEY, "heat_vision", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (maxOutputBusy(p)) {
					return; // Maximum Output already owns the eyes
				}
				if (p.isShiftKeyDown()) {
					if (!ctx.cooldownReady()) {
						cooldownMessage(ctx);
						return;
					}
					if (!canFire(ctx)) {
						return;
					}
					firePiercingBlast(ctx);
					return;
				}
				if (!canFire(ctx)) {
					return;
				}
				set(p, "beaming", 1);
				set(p, "beam_held", 0);
				MutationVisuals.play(p, ANIM_BEAM);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				set(ctx.player(), "beaming", 0);
				set(ctx.player(), "beam_held", 0);
				MutationVisuals.stopIf(ctx.player(), ANIM_BEAM);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "beaming") <= 0.5f) {
					return;
				}
				if (maxOutputBusy(p)) {
					set(p, "beaming", 0);
					return;
				}
				heatVisionTick(ctx);
			}
		});

		// G -- Sweeping Arc: the beam swings through a 150-degree arc across your view in half a second.
		AbilityHandlers.register(KEY, "sweeping_arc", Handlers.instantTicking(ctx -> {
			if (!canFire(ctx)) {
				return;
			}
			ServerPlayer p = ctx.player();
			set(p, "sweep_ticks", SWEEP_TICKS);
			BatchA.play(p, KEY, ANIM_SWEEP, 14);
			AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.8f);
			addHeat(p, HEAT_SWEEPING_ARC);
			ctx.triggerCooldown();
		}, LaserVisionHandlers::sweepTick));

		// X -- Recoil Blast: an eye blast wherever you look (the ground, a wall, behind you) that throws you the other way.
		AbilityHandlers.register(KEY, "recoil_blast", Handlers.instant(ctx -> {
			if (!canFire(ctx)) {
				return;
			}
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			Vec3 look = p.getLookAngle();
			Vec3 blast = AbilityHelpers.aimPoint(p, RANGE);
			AbilityHelpers.launchSelf(p, look.scale(-1.9).add(0, 0.25, 0));
			set(p, "no_fall_until", p.level().getGameTime() + 80);
			float dmg = 9.6f;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, blast, 2.5)) {
				LaserCooking.hurt(p, e, AbilityHelpers.fire(p), dmg);
				AbilityHelpers.knockbackFrom(e, blast, 1.2);
				e.setRemainingFireTicks(60);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, blast.x, blast.y, blast.z, 1, 0, 0, 0, 0);
			impact(level, blast, 10);
			AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.5f);
			BatchA.play(p, KEY, ANIM_RECOIL, 14);
			LaserBeams.send(level, p, p.getEyePosition(), blast, LaserBeamPayload.KIND_RECOIL, oneShotTicks(ANIM_RECOIL));
			addHeat(p, HEAT_RECOIL_BLAST);
			ctx.triggerCooldown();
		}));

		// Z -- Maximum Output: press once, 1.5 s charge-up, then a 10 s powered-up beam that follows your aim.
		// Refused above 50 heat; pins the gauge at 100 while it runs, and you overheat when it ends.
		AbilityHandlers.register(KEY, "maximum_output", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (maxOutputBusy(p) || !canFire(ctx)) {
				return;
			}
			if (res(p, "heat") > MAX_OUTPUT_HEAT_LIMIT) {
				ctx.actionBar("message.projecthero.laser.too_hot_for_max");
				return;
			}
			set(p, "beaming", 0);
			set(p, "mo_charging", 1);
			set(p, "mo_charge", 0);
			MutationVisuals.play(p, ANIM_MAX_CHARGE);
			AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.4f);
			ctx.triggerCooldown();
		}, LaserVisionHandlers::maxOutputTick));

		// V -- Ignite: a pin-point flick of heat that works like flint and steel, 100 blocks out.
		AbilityHandlers.register(KEY, "ignite", Handlers.instant(ctx -> {
			if (!canFire(ctx)) {
				return;
			}
			ignite(ctx);
			addHeat(ctx.player(), HEAT_IGNITE);
			ctx.triggerCooldown();
		}));

		// C -- Thermal Vision (the outline itself is drawn client-side for the viewer only, EntityGlowMixin).
		AbilityHandlers.register(KEY, "thermal_vision", Handlers.toggle(ctx -> BatchA.play(ctx.player(), KEY, "p02.glare", 10),
				Handlers.noop(), ctx -> {
					ServerPlayer p = ctx.player();
					if (maxOutputRunning(p)) {
						return;
					}
					// running the thermal overlay keeps the eyes warm: a slow trickle of heat (and it counts as use)
					set(p, "vent_delay", VENT_DELAY);
					float h = Math.min(MAX_HEAT, res(p, "heat") + THERMAL_TRICKLE);
					set(p, "heat", h);
					if (h >= MAX_HEAT - 0.01f) {
						ctx.setToggled(false);
						addHeat(p, 1f);
					}
				}));

		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				stopChannels(player);
				set(player, "sweep_ticks", 0);
			}
		});

		PowerPassives.registerTick(KEY, LaserVisionHandlers::passiveTick);
	}

	// ---- the heat gauge ------------------------------------------------------------------------

	private static void passiveTick(ServerPlayer player) {
		if (player.tickCount % 100 == 0) {
			for (String s : STALE) {
				set(player, s, 0);
			}
			dropLegacyNightVision(player);
		}
		if (res(player, "heat") > MAX_HEAT) {
			set(player, "heat", MAX_HEAT); // a pre-v0.14.5 save on the old 0-575 scale
		}
		float over = res(player, "overheat");
		if (over > 0.5f) {
			set(player, "overheat", over - 1);
		}
		if (maxOutputRunning(player)) {
			return;
		}
		float delay = res(player, "vent_delay");
		if (delay > 0.5f) {
			set(player, "vent_delay", delay - 1);
		} else if (!firing(player) && res(player, "heat") > 0f
				&& !ExperimentalPowers.state(player).activeToggles.contains(KEY + "/thermal_vision")) {
			set(player, "heat", Math.max(0f, res(player, "heat") - VENT_RATE));
		}
	}

	/**
	 * Before v0.14.5 Laser Vision gave a permanent, hidden Night Vision. Nothing in the mod hands out an infinite
	 * hidden Night Vision any more, so one that is still there is that leftover: take it off.
	 */
	public static void dropLegacyNightVision(ServerPlayer player) {
		MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
		if (nv != null && nv.isInfiniteDuration() && !nv.isVisible() && !nv.showIcon()) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	// ---- R: Heat Vision / Piercing Blast ---------------------------------------------------------

	private static void heatVisionTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float held = res(p, "beam_held") + 1;
		set(p, "beam_held", Math.min(held, 2000));
		ensureLoop(p, ANIM_BEAM);
		beamToFarViewers(p, ctx.level(), LaserBeamPayload.KIND_BEAM);
		if (p.tickCount % 10 == 0) {
			float ramp = 1.0f + Math.min(1.5f, held / 40.0f);
			Vec3 end = beamDamage(ctx, 4.8f * ramp, 0.0);
			impact(ctx.level(), end, 3);
		}
		if (!p.onGround() && p.getXRot() > 40.0f) {
			slowFall(p);
		}
		addHeat(p, HEAT_BEAM_PER_TICK);
	}

	/** Beaming straight down in mid-air holds you up like slow falling (and cancels the fall damage). */
	public static void slowFall(ServerPlayer p) {
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 6, 0, false, false, false));
		p.resetFallDistance();
		set(p, "no_fall_until", p.level().getGameTime() + 20);
		Vec3 v = p.getDeltaMovement();
		if (v.y < -0.2) {
			p.setDeltaMovement(v.x, -0.2, v.z);
			p.hurtMarked = true;
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
		}
	}

	/** Glass, leaves and panes -- what a Piercing Blast cuts straight through. */
	private static boolean cuttable(BlockState st) {
		// IMPERMEABLE = every glass block; glass panes (plain and stained) are IronBarsBlocks that are not iron bars
		return st.is(BlockTags.LEAVES) || st.is(BlockTags.IMPERMEABLE) || st.getBlock() instanceof StainedGlassPaneBlock
				|| (st.getBlock() instanceof IronBarsBlock && !st.is(Blocks.IRON_BARS));
	}

	/** Shift + R: a needle of light, 100 blocks, through up to four creatures and any glass / leaves / panes. */
	private static void firePiercingBlast(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float dmg = 29f;
		int pierce = 4;
		Vec3 start = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		Vec3 end = start.add(dir.scale(RANGE));
		Set<Integer> hit = new HashSet<>();
		int cut = 0;
		boolean grief = AbilityHelpers.canGrief();
		for (double d = 0.5; d <= RANGE; d += 0.5) {
			Vec3 at = start.add(dir.scale(d));
			BlockPos bp = BlockPos.containing(at);
			BlockState st = level.getBlockState(bp);
			if (!st.getCollisionShape(level, bp).isEmpty() || st.is(BlockTags.LEAVES)) {
				if (grief && cut < 12 && cuttable(st)) {
					level.destroyBlock(bp, false, p);
					cut++;
				} else if (!st.getCollisionShape(level, bp).isEmpty()) {
					end = at;
					break;
				}
			}
			if (hit.size() < pierce) {
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 0.6)) {
					if (hit.size() >= pierce || !hit.add(e.getId())) {
						continue;
					}
					LaserCooking.hurtBurst(p, e, AbilityHelpers.fire(p), dmg);
					e.setRemainingFireTicks(80);
				}
			}
		}
		level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		impact(level, end, 6);
		BatchA.play(p, KEY, ANIM_PIERCE, 10);
		LaserBeams.send(level, p, start, end, LaserBeamPayload.KIND_PIERCE, oneShotTicks(ANIM_PIERCE));
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.8f);
		addHeat(p, HEAT_PIERCING_BLAST);
		ctx.triggerCooldown(PIERCING_BLAST_COOLDOWN); // on R's own box
	}

	// ---- Z: Maximum Output -----------------------------------------------------------------------

	private static void maxOutputTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float charging = res(p, "mo_charging");
		if (charging > 0.5f) {
			float t = charging + 1;
			if (t > MAX_OUTPUT_CHARGE_TICKS) {
				startMaxOutput(p);
				return;
			}
			set(p, "mo_charging", t);
			set(p, "mo_charge", Math.min(100f, (t - 1) / MAX_OUTPUT_CHARGE_TICKS * 100f));
			ensureLoop(p, ANIM_MAX_CHARGE);
			if ((int) t % 6 == 0) {
				AbilityHelpers.sound(p, SoundEvents.BLAZE_AMBIENT, 0.6f, 0.6f + t / MAX_OUTPUT_CHARGE_TICKS);
			}
			return;
		}
		int left = (int) res(p, "max_ticks");
		if (left <= 0) {
			return;
		}
		set(p, "max_ticks", left - 1);
		set(p, "heat", MAX_HEAT);
		set(p, "vent_delay", VENT_DELAY);
		ensureLoop(p, ANIM_MAX);
		ServerLevel level = ctx.level();
		Vec3 end = beamDamage(ctx, 14.0f, 0.9);
		LaserBeams.send(level, p, p.getEyePosition(), end, LaserBeamPayload.KIND_MAX, LaserBeamPayload.REFRESH_TICKS);
		if (left % 2 == 0) {
			burnThrough(p, level);
		}
		if (left % 10 == 0) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, end, 3.5)) {
				LaserCooking.hurt(p, e, AbilityHelpers.fire(p), 12.0f);
				e.setRemainingFireTicks(120);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		}
		if (left % 3 == 0) {
			impact(level, end, 4);
		}
		if (left % 20 == 0) {
			AbilityHelpers.sound(p, SoundEvents.BEACON_AMBIENT, 1.2f, 1.6f);
		}
		if (left - 1 <= 0) {
			MutationVisuals.stopIf(p, ANIM_MAX);
			overheat(p); // the eyes are spent: the ordinary overheat lockout and vent follow
		}
	}

	private static void startMaxOutput(ServerPlayer p) {
		set(p, "mo_charging", 0);
		set(p, "mo_charge", 0);
		set(p, "max_ticks", MAX_OUTPUT_TICKS);
		set(p, "heat", MAX_HEAT);
		set(p, "vent_delay", VENT_DELAY);
		MutationVisuals.play(p, ANIM_MAX);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.35f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.7f);
	}

	/** Maximum Output burns through soft blocks where it lands (with terrain damage on). */
	private static void burnThrough(ServerPlayer p, ServerLevel level) {
		if (!AbilityHelpers.canGrief() || AbilityHelpers.raycastEntity(p, RANGE) != null) {
			return;
		}
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, RANGE);
		if (bhr.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos bp = bhr.getBlockPos();
		float hard = level.getBlockState(bp).getDestroySpeed(level, bp);
		if (hard < 3.0f && hard >= 0) {
			level.destroyBlock(bp, false, p);
		}
	}

	// ---- V: Ignite ---------------------------------------------------------------------------

	private static void ignite(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		BatchA.play(p, KEY, ANIM_IGNITE, 8);
		LaserBeams.send(level, p, p.getEyePosition(), AbilityHelpers.aimPoint(p, RANGE), LaserBeamPayload.KIND_IGNITE,
				oneShotTicks(ANIM_IGNITE));
		LivingEntity target = AbilityHelpers.raycastEntity(p, RANGE);
		if (target != null) {
			LaserCooking.hurt(p, target, AbilityHelpers.fire(p), 2.0f);
			target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 100));
			level.playSound(null, target.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
			return;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, RANGE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			AbilityHelpers.sound(p, SoundEvents.FLINTANDSTEEL_USE, 0.6f, 1.2f);
			return;
		}
		igniteBlock(level, p, hit);
		level.playSound(null, hit.getBlockPos(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
		AbilityHelpers.sound(p, SoundEvents.FLINTANDSTEEL_USE, 0.5f, 1.2f);
	}

	/**
	 * Flint and steel on a block face: lights campfires / candles / candle cakes, primes TNT, otherwise sets fire on
	 * the face. Placing fire and priming TNT need terrain damage on ({@code abilityTerrainDamage}).
	 *
	 * @return true when something was lit
	 */
	public static boolean igniteBlock(ServerLevel level, ServerPlayer p, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState st = level.getBlockState(pos);
		if (CampfireBlock.canLight(st) || CandleBlock.canLight(st) || CandleCakeBlock.canLight(st)) {
			level.setBlockAndUpdate(pos, st.setValue(BlockStateProperties.LIT, true));
			return true;
		}
		if (!AbilityHelpers.canGrief()) {
			return false;
		}
		if (st.is(Blocks.TNT)) {
			level.removeBlock(pos, false);
			level.addFreshEntity(new net.minecraft.world.entity.item.PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p));
			return true;
		}
		BlockPos face = pos.relative(hit.getDirection());
		if (BaseFireBlock.canBePlacedAt(level, face, p.getDirection())) {
			level.setBlockAndUpdate(face, BaseFireBlock.getState(level, face));
			return true;
		}
		return false;
	}

	// ---- G: Sweeping Arc ---------------------------------------------------------------------

	/** The arc's direction {@code progress} (0..1) of the way through, relative to where you look now. */
	public static Vec3 sweepDirection(float yaw, float pitch, float progress) {
		return Vec3.directionFromRotation(pitch, yaw - 75f + 150f * Math.max(0f, Math.min(1f, progress)));
	}

	private static void sweepTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int t = (int) res(p, "sweep_ticks");
		if (t <= 0) {
			return;
		}
		set(p, "sweep_ticks", t - 1);
		ServerLevel level = ctx.level();
		float progress = (SWEEP_TICKS - t) / (float) (SWEEP_TICKS - 1);
		Vec3 dir = sweepDirection(p.getYRot(), p.getXRot(), progress);
		Vec3 start = p.getEyePosition();
		BlockHitResult bhr = level.clip(new ClipContext(start, start.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : start.add(dir.scale(RANGE));
		LaserBeams.send(level, p, start, end, LaserBeamPayload.KIND_SWEEP, 2);
		float dmg = 14.4f;
		double len = start.distanceTo(end);
		for (double d = 0.5; d <= len; d += 1.0) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, start.add(dir.scale(d)), 1.0)) {
				if (LaserCooking.hurt(p, e, AbilityHelpers.fire(p), dmg)) {
					e.setRemainingFireTicks(60);
				}
			}
		}
		impact(level, end, 3);
	}

	// ---- shared ------------------------------------------------------------------------------

	/** A small scorch where a beam lands -- the beam itself is a model, drawn client-side. */
	private static void impact(ServerLevel level, Vec3 at, int count) {
		level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, count, 0.12, 0.12, 0.12, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, Math.max(1, count / 2), 0.1, 0.1, 0.1, 0.01);
	}

	/**
	 * A held beam, refreshed every tick for the viewers that do not track {@code p} ({@link LaserBeams}). The end point
	 * is only ray-cast when somebody actually needs it.
	 */
	private static void beamToFarViewers(ServerPlayer p, ServerLevel level, int kind) {
		Vec3 eye = p.getEyePosition();
		if (LaserBeams.anyRecipient(level, p, eye, p.getLookAngle(), RANGE)) {
			LaserBeams.send(level, p, eye, AbilityHelpers.aimPoint(p, RANGE), kind, LaserBeamPayload.REFRESH_TICKS);
		}
	}

	/** Damages whatever the beam is pointed at (plus a splash if {@code splash > 0}); returns where it lands. */
	private static Vec3 beamDamage(AbilityContext ctx, float damage, double splash) {
		ServerPlayer p = ctx.player();
		LivingEntity target = AbilityHelpers.raycastEntity(p, RANGE);
		Vec3 impact = target != null
				? target.position().add(0, target.getBbHeight() * 0.5, 0)
				: AbilityHelpers.aimPoint(p, RANGE);
		if (target != null && LaserCooking.hurt(p, target, AbilityHelpers.fire(p), damage)) {
			target.setRemainingFireTicks(60);
		}
		if (splash > 0.0) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, impact, splash)) {
				if (e != target && LaserCooking.hurt(p, e, AbilityHelpers.fire(p), damage * 0.6f)) {
					e.setRemainingFireTicks(40);
				}
			}
		}
		return impact;
	}
}
