package com.projecthero.mod.hero.power.p02;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 02 — Laser Vision (v0.13.22 revamp: <b>the heat gauge -- hotter means a sharper beam</b>).
 *
 * <p>Every laser adds heat to a 0..575 gauge, and every laser hits harder the hotter your eyes are running
 * (up to +60% at a full gauge). Fill it completely and you overheat: 3 s locked out while it vents. Heat vents
 * on its own a second after you stop firing, or Cauterize (N) dumps all of it at once to heal you.
 *
 * <p>R Heat Vision (hold, damage ramps the longer you hold; Shift+R mines), G Piercing Lance (charge, pierces up
 * to four targets and cuts glass / leaves / panes), X Recoil Blast (fire at your feet to launch yourself),
 * Z Maximum Output, V Ricochet Shot (bounces off blocks up to 3 times), C Thermal Vision, H Sweeping Arc,
 * N Cauterize. The eyes glow red all the time (brighter while firing -- see {@code RevampClientA}).
 */
public final class LaserVisionHandlers {
	public static final String KEY = "power_02_laser_vision";
	public static final float MAX_HEAT = 575.0f;
	private static final int OVERHEAT_TICKS = 60;
	private static final int VENT_DELAY = 20;
	private static final float VENT_RATE = 4.5f;

	/** Maximum Output: hold Z this long to build the heat before the beam fires. */
	private static final int MAX_OUTPUT_CHARGE_TICKS = 4 * 20;
	private static final int LANCE_FULL = 40;

	private LaserVisionHandlers() {
	}

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v, 1e9f);
	}

	/** True while any Laser Vision beam is actively firing or charging -- drives the bright eye glow. */
	public static boolean firing(ServerPlayer p) {
		return res(p, "beaming") > 0.5f || res(p, "lance_on") > 0.5f || res(p, "mo_on") > 0.5f
				|| res(p, "max_ticks") > 0.5f || res(p, "sweep_ticks") > 0.5f;
	}

	/** Beam damage multiplier from the heat gauge: x1.0 cold, x1.6 at a full gauge. */
	public static float heatMult(ServerPlayer p) {
		return 1.0f + 0.6f * Math.min(1f, res(p, "heat") / MAX_HEAT);
	}

	private static boolean overheated(ServerPlayer p) {
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

	/** Adds heat; a full gauge trips the 3 s overheat lockout and cuts every channel. */
	private static void addHeat(ServerPlayer p, float amount) {
		float h = Math.min(MAX_HEAT, res(p, "heat") + amount);
		set(p, "heat", h);
		set(p, "vent_delay", VENT_DELAY);
		if (h >= MAX_HEAT - 0.01f && !overheated(p)) {
			set(p, "overheat", OVERHEAT_TICKS);
			stopChannels(p);
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.laser.overheated"), true);
			p.level().playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.PLAYERS,
					1.0f, 0.6f);
		}
	}

	private static void stopChannels(ServerPlayer p) {
		set(p, "beaming", 0);
		set(p, "beam_util", 0);
		set(p, "lance_on", 0);
		set(p, "lance_charge", 0);
		set(p, "mo_on", 0);
		set(p, "ult_charge", 0);
		MutationVisuals.stopIf(p, "beam_eyes");
	}

	private static void cooldownMessage(AbilityContext ctx) {
		ctx.actionBar("message.projecthero.ability.on_cooldown",
				net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
	}

	public static void register() {
		// R -- Heat Vision. Hold to beam; the damage ramps up the longer you hold it (to x2.5 after 3 s).
		// Shift + R switches to utility mode, which mines blocks as fast as a diamond tool but runs hotter.
		AbilityHandlers.register(KEY, "heat_vision", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!canFire(ctx)) {
					return;
				}
				set(ctx.player(), "beaming", 1);
				set(ctx.player(), "beam_held", 0);
				set(ctx.player(), "beam_util", ctx.player().isShiftKeyDown() ? 1 : 0);
				MutationVisuals.play(ctx.player(), "beam_eyes");
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				set(ctx.player(), "beaming", 0);
				set(ctx.player(), "beam_util", 0);
				set(ctx.player(), "beam_held", 0);
				MutationVisuals.stopIf(ctx.player(), "beam_eyes");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "beaming") <= 0.5f) {
					return;
				}
				float held = res(p, "beam_held") + 1;
				set(p, "beam_held", Math.min(held, 2000));
				MutationVisuals.ensure(p, "beam_eyes");
				if (res(p, "beam_util") > 0.5f) {
					utilityBeamTick(ctx);
					addHeat(p, 3.0f);
				} else {
					drawBeam(ctx, 21.0, 1);
					if (p.tickCount % 10 == 0) {
						float ramp = 1.0f + Math.min(1.5f, held / 40.0f);
						beamDamage(ctx, 21.0, 4.8f * ramp * heatMult(p), 0.0);
					}
					addHeat(p, 2.0f);
				}
			}
		});

		// G -- Piercing Lance. Hold to charge (2 s for full), release: a needle of light that runs through up to
		// four creatures and cuts through glass, leaves and panes on its way.
		AbilityHandlers.register(KEY, "piercing_lance", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "lance_on") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					cooldownMessage(ctx);
					return;
				}
				if (!canFire(ctx)) {
					return;
				}
				set(p, "lance_on", 1);
				set(p, "lance_held", 0);
				MutationVisuals.play(p, "beam_eyes");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "lance_on") <= 0.5f) {
					return;
				}
				float held = res(p, "lance_held") + 1;
				set(p, "lance_held", held);
				set(p, "lance_charge", Math.min(100f, held / LANCE_FULL * 100f));
				MutationVisuals.ensure(p, "beam_eyes");
				eyeSpark(ctx, p, 1 + (int) Math.min(6, held / 6));
				if (held == LANCE_FULL) {
					AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
				}
				if (held > LANCE_FULL * 3) {
					fireLance(ctx); // held far too long: it goes off by itself
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (res(ctx.player(), "lance_on") > 0.5f) {
					fireLance(ctx);
				}
			}
		});

		// X -- Recoil Blast: a point-blank eye blast into the ground (or behind you) that throws you the other way.
		AbilityHandlers.register(KEY, "recoil_blast", Handlers.instant(ctx -> {
			if (!canFire(ctx)) {
				return;
			}
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			Vec3 look = p.getLookAngle();
			BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 6.0);
			Vec3 blast = bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : p.getEyePosition().add(look.scale(3.0));
			Vec3 launch = look.scale(-1.9).add(0, 0.25, 0);
			AbilityHelpers.launchSelf(p, launch);
			set(p, "no_fall_until", p.level().getGameTime() + 80);
			float dmg = 9.6f * heatMult(p);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, blast, 2.5)) {
				AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), dmg);
				AbilityHelpers.knockbackFrom(e, blast, 1.2);
				e.setRemainingFireTicks(60);
			}
			drawBeamTo(level, p, blast, 2);
			level.sendParticles(ParticleTypes.EXPLOSION, blast.x, blast.y, blast.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.FLAME, blast.x, blast.y, blast.z, 30, 0.6, 0.3, 0.6, 0.12);
			level.sendParticles(ParticleTypes.LAVA, blast.x, blast.y, blast.z, 6, 0.4, 0.2, 0.4, 0.0);
			AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.5f);
			BatchA.play(p, KEY, "p02.recoil", 14);
			addHeat(p, 50f);
			ctx.triggerCooldown();
		}));

		// Z -- Maximum Output. Hold Z for 4 s while heat gathers at the eyes, then a thick beam tears out for ~3 s.
		AbilityHandlers.register(KEY, "maximum_output", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "mo_on") > 0.5f || res(p, "max_ticks") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					cooldownMessage(ctx);
					return;
				}
				if (!canFire(ctx)) {
					return;
				}
				set(p, "mo_on", 1);
				set(p, "mo_held", 0);
				MutationVisuals.play(p, "beam_eyes");
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "mo_on") <= 0.5f) {
					return;
				}
				float held = res(p, "mo_held");
				set(p, "mo_on", 0);
				set(p, "ult_charge", 0);
				if (held >= MAX_OUTPUT_CHARGE_TICKS) {
					fireMaxOutput(ctx);
				} else {
					MutationVisuals.stopIf(p, "beam_eyes");
					AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.8f);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "mo_on") > 0.5f) {
					float held = res(p, "mo_held") + 1;
					set(p, "mo_held", held);
					if (held > MAX_OUTPUT_CHARGE_TICKS + 40) {
						set(p, "mo_on", 0);
						fireMaxOutput(ctx);
						return;
					}
					MutationVisuals.ensure(p, "beam_eyes");
					double frac = Math.min(1.0, held / (double) MAX_OUTPUT_CHARGE_TICKS);
					set(p, "ult_charge", (float) (frac * 100.0));
					eyeSpark(ctx, p, 3 + (int) (frac * 12));
					if ((int) held % 12 == 0) {
						AbilityHelpers.sound(p, SoundEvents.BLAZE_AMBIENT, 0.6f, 0.6f + (float) frac);
					}
					if (held >= MAX_OUTPUT_CHARGE_TICKS && (int) held % 20 == 0) {
						p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
								"message.projecthero.laser.max_ready"), true);
					}
					return;
				}
				int t = (int) res(p, "max_ticks");
				if (t <= 0) {
					return;
				}
				set(p, "max_ticks", t - 1);
				MutationVisuals.ensure(p, "beam_eyes");
				drawBeam(ctx, 48.0, 3);
				beamDamage(ctx, 48.0, 9.6f * heatMult(p), 0.6);
				if (t % 10 == 0) {
					Vec3 impact = AbilityHelpers.aimPoint(p, 48.0);
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, impact, 5.0)) {
						AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), 41.0f);
						e.setRemainingFireTicks(120);
					}
					ctx.level().sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
				}
				if (t - 1 <= 0) {
					MutationVisuals.stopIf(p, "beam_eyes");
				}
			}
		});

		// V -- Ricochet Shot: a bolt that caroms off up to three surfaces, hitting harder after every bounce.
		AbilityHandlers.register(KEY, "ricochet_shot", Handlers.instant(ctx -> {
			if (!canFire(ctx)) {
				return;
			}
			ServerPlayer p = ctx.player();
			BatchA.play(p, KEY, "p02.glare", 10);
			ricochet(ctx);
			AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 0.9f, 1.5f);
			addHeat(p, 40f);
			ctx.triggerCooldown();
		}));

		// C -- Thermal Vision (the outline itself is drawn client-side for the viewer only, EntityGlowMixin).
		AbilityHandlers.register(KEY, "thermal_vision", Handlers.toggle(ctx -> BatchA.play(ctx.player(), KEY, "p02.glare", 10),
				Handlers.noop(), ctx -> {
					ServerPlayer p = ctx.player();
					// running the thermal overlay keeps the eyes warm: a slow trickle of heat
					float h = Math.min(MAX_HEAT, res(p, "heat") + 0.4f);
					set(p, "heat", h);
					if (h >= MAX_HEAT - 0.01f) {
						ctx.setToggled(false);
						addHeat(p, 1f);
					}
				}));

		// H -- Sweeping Arc: the beam swings through a 150-degree arc in half a second.
		AbilityHandlers.register(KEY, "sweeping_arc", Handlers.instantTicking(ctx -> {
			if (!canFire(ctx)) {
				return;
			}
			ServerPlayer p = ctx.player();
			set(p, "sweep_ticks", 10);
			set(p, "sweep_yaw", p.getYRot() + 7200f);
			set(p, "sweep_pitch", p.getXRot() + 90f);
			BatchA.play(p, KEY, "p02.sweep", 14);
			AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.8f);
			addHeat(p, 80f);
			ctx.triggerCooldown();
		}, LaserVisionHandlers::sweepTick));

		// N -- Cauterize: vent all of your heat into your own wounds.
		AbilityHandlers.register(KEY, "cauterize", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float heat = res(p, "heat");
			if (heat < 60f) {
				ctx.actionBar("message.projecthero.laser.not_hot_enough");
				return;
			}
			float heal = 3f + 13f * Math.min(1f, heat / MAX_HEAT);
			p.heal(heal);
			p.clearFire();
			p.removeEffect(MobEffects.WITHER);
			p.removeEffect(MobEffects.POISON);
			set(p, "heat", 0);
			set(p, "overheat", 0);
			ServerLevel level = ctx.level();
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.35, 0.6, 0.35, 0.03);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			BatchA.ring(level, p.position().add(0, 0.1, 0), 0.4, ParticleTypes.SMALL_FLAME, 18, 0.2);
			AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.8f);
			AbilityHelpers.sound(p, SoundEvents.BLAZE_HURT, 0.4f, 1.5f);
			BatchA.play(p, KEY, "p02.cauterize", 18);
			ctx.triggerCooldown();
		}));

		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				stopChannels(player);
				set(player, "max_ticks", 0);
				set(player, "sweep_ticks", 0);
			}
		});

		PowerPassives.registerTick(KEY, player -> {
			// Immune to blindness / darkness, and permanently able to see in the dark.
			if (player.hasEffect(MobEffects.BLINDNESS)) {
				player.removeEffect(MobEffects.BLINDNESS);
			}
			if (player.hasEffect(MobEffects.DARKNESS)) {
				player.removeEffect(MobEffects.DARKNESS);
			}
			var nv = player.getEffect(MobEffects.NIGHT_VISION);
			if (nv == null || nv.getDuration() < 30) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
						MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
			}
			// the heat gauge: overheat lockout, then venting a second after the last shot
			float over = res(player, "overheat");
			if (over > 0.5f) {
				set(player, "overheat", over - 1);
			}
			float delay = res(player, "vent_delay");
			if (delay > 0.5f) {
				set(player, "vent_delay", delay - 1);
			} else if (!firing(player) && res(player, "heat") > 0f
					&& !com.projecthero.mod.hero.ExperimentalPowers.state(player).activeToggles.contains(KEY + "/thermal_vision")) {
				float rate = over > 0.5f ? VENT_RATE * 2.5f : VENT_RATE;
				set(player, "heat", Math.max(0f, res(player, "heat") - rate));
			}
			// white-hot sparks off the eyes while a beam runs hot
			if (firing(player) && res(player, "heat") > MAX_HEAT * 0.6f && player.tickCount % 3 == 0
					&& player.level() instanceof ServerLevel sl) {
				Vec3 e = player.getEyePosition().add(player.getLookAngle().scale(0.35));
				sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.x, e.y, e.z, 2, 0.08, 0.05, 0.08, 0.02);
			}
		});
	}

	// ---- shared beam helpers -----------------------------------------------------------------

	private static void eyeSpark(AbilityContext ctx, ServerPlayer p, int count) {
		Vec3 look = p.getLookAngle();
		Vec3 eye = p.getEyePosition().add(look.scale(0.3));
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		for (int s = -1; s <= 1; s += 2) {
			Vec3 e = eye.add(right.scale(0.13 * s));
			ctx.level().sendParticles(ParticleTypes.FLAME, e.x, e.y, e.z, count, 0.06, 0.06, 0.06, 0.02);
			ctx.level().sendParticles(ParticleTypes.SMALL_FLAME, e.x, e.y, e.z, count, 0.05, 0.05, 0.05, 0.01);
		}
	}

	/** Damage whatever the beam is pointed at (entity hit, plus a small splash if {@code splash > 0}). */
	private static void beamDamage(AbilityContext ctx, double range, float damage, double splash) {
		ServerPlayer p = ctx.player();
		LivingEntity target = AbilityHelpers.raycastEntity(p, range);
		Vec3 impact = target != null
				? target.position().add(0, target.getBbHeight() * 0.5, 0)
				: AbilityHelpers.aimPoint(p, range);
		if (target != null) {
			AbilityHelpers.hurt(p, target, AbilityHelpers.fire(p), damage);
			target.setRemainingFireTicks(60);
		}
		if (splash > 0.0) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, impact, splash)) {
				if (e == target) {
					continue;
				}
				AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), damage * 0.6f);
				e.setRemainingFireTicks(40);
			}
		}
	}

	/** Utility mode: bore through the block the beam lands on as fast as a diamond tool would. */
	private static void utilityBeamTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		drawBeam(ctx, 20.0, 1);
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 20.0);
		if (bhr.getType() != HitResult.Type.BLOCK || !AbilityHelpers.canGrief()) {
			return;
		}
		BlockPos bp = bhr.getBlockPos();
		BlockState st = level.getBlockState(bp);
		float hardness = st.getDestroySpeed(level, bp);
		if (hardness < 0.0f || st.isAir()) {
			return; // bedrock / unbreakable / nothing there
		}
		float budget = res(p, "mine_budget") + 8.0f / 20.0f;
		float need = Math.max(0.15f, hardness * 1.5f);
		if (budget >= need) {
			level.destroyBlock(bp, true, p);
			set(p, "mine_budget", 0);
		} else {
			set(p, "mine_budget", budget);
		}
	}

	/** Glass, leaves and panes -- what a Piercing Lance cuts straight through. */
	private static boolean cuttable(BlockState st) {
		// IMPERMEABLE = every glass block; glass panes (plain and stained) are IronBarsBlocks that are not iron bars
		return st.is(BlockTags.LEAVES) || st.is(BlockTags.IMPERMEABLE) || st.getBlock() instanceof StainedGlassPaneBlock
				|| (st.getBlock() instanceof IronBarsBlock && !st.is(Blocks.IRON_BARS));
	}

	private static void fireLance(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float held = res(p, "lance_held");
		set(p, "lance_on", 0);
		set(p, "lance_charge", 0);
		set(p, "lance_held", 0);
		MutationVisuals.stopIf(p, "beam_eyes");
		float frac = Math.min(1f, held / LANCE_FULL);
		float dmg = (18f + 11f * frac) * heatMult(p);
		int pierce = 2 + Math.round(2 * frac);
		double range = 40.0;
		Vec3 start = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		Vec3 end = start.add(dir.scale(range));
		Set<Integer> hit = new HashSet<>();
		int cut = 0;
		boolean grief = AbilityHelpers.canGrief();
		for (double d = 0.5; d <= range; d += 0.5) {
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
					AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), dmg);
					e.setRemainingFireTicks(80);
					level.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 4, 0.2, 0.2, 0.2, 0);
				}
			}
		}
		drawBeamTo(level, p, end, 2);
		if (heatMult(p) > 1.35f) {
			AbilityHelpers.line(level, start.add(0, -0.1, 0).add(dir.scale(0.4)), end, ParticleTypes.END_ROD, 1.5);
		}
		level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		BatchA.play(p, KEY, "p02.glare", 10);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.8f);
		addHeat(p, 90f);
		ctx.triggerCooldown();
	}

	private static void fireMaxOutput(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		set(p, "mo_on", 0);
		set(p, "ult_charge", 0);
		set(p, "max_ticks", 60);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.35f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.7f);
		addHeat(p, 250f);
		ctx.triggerCooldown();
	}

	/** Ricochet: trace the bolt, bouncing off up to three block faces over 48 blocks. */
	private static void ricochet(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 pos = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		double remaining = 48.0;
		Set<Integer> hit = new HashSet<>();
		float base = 11f * heatMult(p);
		for (int bounce = 0; bounce <= 3 && remaining > 0.5; bounce++) {
			Vec3 target = pos.add(dir.scale(remaining));
			BlockHitResult bhr = level.clip(new ClipContext(pos, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			Vec3 end = bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : target;
			float dmg = base * (1f + 0.25f * bounce);
			double len = pos.distanceTo(end);
			for (double d = 0; d <= len; d += 0.5) {
				Vec3 at = pos.add(dir.scale(d));
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 0.7)) {
					if (hit.add(e.getId())) {
						AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), dmg);
						e.setRemainingFireTicks(60);
					}
				}
			}
			AbilityHelpers.line(level, pos, end, ParticleTypes.FLAME, 2.0);
			AbilityHelpers.line(level, pos, end, ParticleTypes.SMALL_FLAME, 1.0);
			remaining -= len;
			if (bhr.getType() != HitResult.Type.BLOCK) {
				break;
			}
			BlockPos bp = bhr.getBlockPos();
			if (level.getBlockState(bp).is(Blocks.TNT)) {
				level.removeBlock(bp, false);
				level.addFreshEntity(new net.minecraft.world.entity.item.PrimedTnt(level, bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, p));
				break;
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 10, 0.1, 0.1, 0.1, 0.2);
			level.playSound(null, bp, SoundEvents.AMETHYST_BLOCK_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.6f + bounce * 0.1f);
			Direction face = bhr.getDirection();
			Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
			dir = dir.subtract(n.scale(2 * dir.dot(n))).normalize();
			pos = end.add(n.scale(0.05));
		}
	}

	private static void sweepTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int t = (int) res(p, "sweep_ticks");
		if (t <= 0) {
			return;
		}
		set(p, "sweep_ticks", t - 1);
		ServerLevel level = ctx.level();
		float progress = (10 - t) / 9.0f;
		float yaw = res(p, "sweep_yaw") - 7200f - 75f + 150f * progress;
		float pitch = res(p, "sweep_pitch") - 90f;
		Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
		Vec3 start = p.getEyePosition();
		double range = 14.0;
		BlockHitResult bhr = level.clip(new ClipContext(start, start.add(dir.scale(range)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : start.add(dir.scale(range));
		float dmg = 14.4f * heatMult(p);
		double len = start.distanceTo(end);
		for (double d = 0.5; d <= len; d += 0.6) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, start.add(dir.scale(d)), 0.8)) {
				if (AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), dmg)) {
					e.setRemainingFireTicks(60);
				}
			}
		}
		drawBeamTo(level, p, end, 1, dir);
		level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 4, 0.15, 0.15, 0.15, 0.03);
		level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 2, 0.1, 0.1, 0.1, 0.01);
	}

	/** Draws the twin eye beam along the look direction and cuts soft blocks for a heavy beam. */
	private static void drawBeam(AbilityContext ctx, double range, int width) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 start = p.getEyePosition();
		LivingEntity target = AbilityHelpers.raycastEntity(p, range);
		Vec3 end;
		if (target != null) {
			end = target.position().add(0, target.getBbHeight() * 0.5, 0);
		} else {
			BlockHitResult bhr = AbilityHelpers.raycastBlock(p, range);
			end = bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : start.add(p.getLookAngle().scale(range));
			if (width >= 3 && bhr.getType() == HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
				BlockPos bp = bhr.getBlockPos();
				float hard = level.getBlockState(bp).getDestroySpeed(level, bp);
				if (hard < 3.0f && hard >= 0) {
					level.destroyBlock(bp, false, p);
				}
			}
		}
		drawBeamTo(level, p, end, width);
	}

	private static void drawBeamTo(ServerLevel level, ServerPlayer p, Vec3 end, int width) {
		drawBeamTo(level, p, end, width, p.getLookAngle());
	}

	/**
	 * The twin eye beam from the eyes to {@code end}. {@code width} 1 is the ordinary beam; 2-3 fan extra parallel
	 * streaks out for a thicker one. Past 60% heat a white-hot core runs down the middle (the "sharper" beam).
	 */
	private static void drawBeamTo(ServerLevel level, ServerPlayer p, Vec3 end, int width, Vec3 look) {
		Vec3 start = p.getEyePosition();
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		Vec3 eyeLine = start.add(0.0, -0.1, 0.0).add(look.scale(0.35));
		double spread = 0.13 * width;
		for (int i = -width; i <= width; i++) {
			Vec3 off = right.scale(i / (double) width * spread);
			Vec3 vOff = up.scale((Math.abs(i) == width ? 0.0 : 0.06) * (width - 1));
			AbilityHelpers.line(level, eyeLine.add(off).add(vOff), end.add(off).add(vOff), ParticleTypes.FLAME, 2.0 * width);
			AbilityHelpers.line(level, eyeLine.add(off).add(vOff), end.add(off).add(vOff), ParticleTypes.SMALL_FLAME, 1.2 * width);
		}
		if (BatchA.res(p, KEY, "heat") > MAX_HEAT * 0.6f) {
			AbilityHelpers.line(level, eyeLine, end, ParticleTypes.END_ROD, 1.0);
		}
	}
}
