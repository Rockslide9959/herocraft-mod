package com.projecthero.mod.hero.power.p01;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Power 01 — Super Strength (v0.10.3 overhaul).
 *
 * <h2>Passives (see {@link com.projecthero.mod.hero.power.HeroDamageRules} for the mitigation half)</h2>
 * 12 unarmed damage, +150% attack knockback, ~2-block jump, 15% flat damage reduction, 70% knockback
 * resistance, 20% explosion reduction, 65% less fall damage, +25% mining, +15% sprint speed, +30%
 * swim speed, and stone-tool hands ({@link StrengthBareHands}).
 *
 * <h2>Charged Punch</h2>
 * Hold the attack key for 2 s (client-tracked, {@code StrengthActionPayload}) to arm a single 20-damage
 * blow with massive knockback that disables shields. A 2.5 s cooldown starts <em>after</em> the punch
 * lands.
 *
 * <h2>Slots</h2>
 * R Ground Slam (air = dive), G Air Punch, X Power Leap (charge), Z Bull Rush / sneak+Z Impact Smash,
 * V Grab &amp; Carry, C Maximum Effort.
 */
public final class SuperStrengthHandlers {
	public static final String KEY = "power_01_super_strength";

	private static final ResourceLocation PASSIVE_ATK = id("strength_passive_attack");
	private static final ResourceLocation PASSIVE_ATK_KB = id("strength_passive_attack_kb");
	private static final ResourceLocation PASSIVE_JUMP = id("strength_passive_jump");
	private static final ResourceLocation PASSIVE_KB_RES = id("strength_passive_kb_resist");
	private static final ResourceLocation PASSIVE_SWIM = id("strength_passive_swim");
	private static final ResourceLocation SPRINT_SPD = id("strength_sprint_speed");
	private static final ResourceLocation RUSH_KB = id("strength_rush_kb");
	private static final ResourceLocation RUSH_STEP = id("strength_rush_step");

	/** 2 s hold, 2.5 s post-hit cooldown. */
	private static final int CHARGED_DAMAGE = 20;
	private static final int CHARGED_CD_TICKS = 50;
	/**
	 * Z is a 5 s hold-to-charge for both moves. Bull Rush then plows forward for 8 s. Bull Rush and
	 * Impact Smash share one cooldown timer ({@code z_cd}): 40 s after a rush, 90 s after a smash.
	 */
	private static final int SMASH_CHARGE = 100;
	private static final int RUSH_RUN = 160;
	private static final int RUSH_CD = 800;
	private static final int SMASH_CD = 1800;
	/** Maximum Effort: 22 s active, 60 s cooldown. */
	private static final int EFFORT_TICKS = 440;

	private static final ResourceLocation id(String p) {
		return com.projecthero.mod.ProjectHeroMod.id(p);
	}

	private SuperStrengthHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	/** Client-safe: does this player own Super Strength (persistent-stacking model). */
	public static boolean owns(Player player) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	public static boolean maxEffortActive(ServerPlayer player) {
		return res(player, "effort_left") > 0.5f;
	}

	private static void res(ServerPlayer player, String name, float value) {
		com.projecthero.mod.hero.ExperimentalPowers.setResource(player, power(), name, value, 1e12f);
	}

	private static float res(ServerPlayer player, String name) {
		Power p = power();
		return p == null ? 0f : com.projecthero.mod.hero.ExperimentalPowers.getResource(player, p, name);
	}

	/** Cooldown ticks, optionally halved while Maximum Effort is running. */
	private static void triggerCd(AbilityContext ctx, int baseTicks) {
		ctx.triggerCooldown(maxEffortActive(ctx.player()) ? baseTicks / 2 : baseTicks);
	}

	// ============================================================================================

	public static void register() {
		AbilityHandlers.register(KEY, "ground_slam",
				Handlers.instantTicking(SuperStrengthHandlers::groundSlamActivate, SuperStrengthHandlers::groundSlamTick));
		AbilityHandlers.register(KEY, "air_punch", Handlers.instant(SuperStrengthHandlers::airPunch));
		// Power Leap's timing is owned entirely by the client (StrengthActionPayload.PERFORM_POWER_LEAP)
		// so the launch is deterministic. The slot handler only draws the wind-up dust: onActivate marks
		// the start, onRelease/onServerTick self-clean it.
		AbilityHandlers.register(KEY, "power_leap", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.cooldownReady()) {
					res(ctx.player(), "leap_press", ctx.player().level().getGameTime());
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				res(ctx.player(), "leap_press", 0);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				float press = res(p, "leap_press");
				if (press <= 0) {
					return;
				}
				long held = p.level().getGameTime() - (long) press;
				if (held < 0 || held > LEAP_MAX_CHARGE + 30) {
					res(p, "leap_press", 0); // lost the release edge -- do not leak dust forever
					return;
				}
				ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(),
						3, 0.3, 0.05, 0.3, 0.02);
				if (held > 0 && held <= LEAP_MAX_CHARGE && held % 10 == 0) {
					AbilityHelpers.sound(p, SoundEvents.STONE_HIT, 0.5f, 0.8f + held / 60.0f);
				}
			}
		});
		AbilityHandlers.register(KEY, "bull_rush", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				bullRushPress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				bullRushRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				bullRushTick(ctx);
			}
		});
		AbilityHandlers.register(KEY, "grab_carry",
				Handlers.instantTicking(SuperStrengthHandlers::grabCarry, SuperStrengthHandlers::grabTick));
		AbilityHandlers.register(KEY, "maximum_effort", Handlers.instant(SuperStrengthHandlers::maximumEffort));

		registerPassives();
	}

	// ---- passives ---------------------------------------------------------------------------------

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				// Vanilla unarmed ATTACK_DAMAGE base is 1.0, so +11 makes an unarmed hit land at 12.
				PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, PASSIVE_ATK, 11.0, AttributeModifier.Operation.ADD_VALUE);
				PowerToggles.modifier(player, Attributes.ATTACK_KNOCKBACK, PASSIVE_ATK_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
				PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, PASSIVE_JUMP, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB_RES, 0.70, AttributeModifier.Operation.ADD_VALUE);
				PowerToggles.modifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, PASSIVE_SWIM, 0.30, AttributeModifier.Operation.ADD_VALUE);
			} else {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, PASSIVE_ATK);
				PowerToggles.clearModifier(player, Attributes.ATTACK_KNOCKBACK, PASSIVE_ATK_KB);
				PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, PASSIVE_JUMP);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB_RES);
				PowerToggles.clearModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, PASSIVE_SWIM);
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPRINT_SPD);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			// Countdown timers used by the HUD.
			float cd = res(player, "charged_cd");
			if (cd > 0) {
				res(player, "charged_cd", cd - 1);
			}
			float eff = res(player, "effort_left");
			if (eff > 0) {
				res(player, "effort_left", eff - 1);
			}
			// +15% speed while sprinting only.
			if (player.isSprinting()) {
				PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPRINT_SPD, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			} else {
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPRINT_SPD);
			}
		});
	}

	// ---- charged punch --------------------------------------------------------------------------

	/**
	 * The client held the attack key for ~2 s (while not aimed at a minable block) and then released
	 * it. Throw the charged punch now: 20 to whatever is in front, massive knockback, shields broken,
	 * then a 2.5 s cooldown.
	 */
	public static void performChargedPunch(ServerPlayer p) {
		if (!owns(p) || res(p, "charged_cd") > 0.5f || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		res(p, "charged_cd", maxEffortActive(p) ? CHARGED_CD_TICKS / 2 : CHARGED_CD_TICKS);
		float dmg = maxEffortActive(p) ? CHARGED_DAMAGE + 6 : CHARGED_DAMAGE;

		Vec3 look = p.getLookAngle();
		Vec3 fist = p.getEyePosition().add(look.scale(1.6));
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, fist.x, fist.y, fist.z, 6, 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, fist.x, fist.y, fist.z, 24, 0.5, 0.5, 0.5, 0.5);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()),
				fist.x, fist.y - 0.4, fist.z, 24, 0.5, 0.3, 0.5, 0.15);
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.4f, 0.45f);

		LivingEntity aimed = AbilityHelpers.raycastEntity(p, 4.5);
		java.util.List<LivingEntity> victims;
		if (aimed != null) {
			victims = java.util.List.of(aimed);
		} else {
			Vec3 centre = p.getEyePosition().add(look.scale(2.6));
			victims = AbilityHelpers.living(level, centre, 3.5, e -> e != p
					&& new Vec3(e.getX() - p.getX(), e.getEyeY() - p.getEyeY(), e.getZ() - p.getZ())
							.normalize().dot(look) > 0.3);
		}
		for (LivingEntity e : victims) {
			AbilityHelpers.hurt(p, e, AbilityHelpers.kinetic(p), dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 3.6);
			AbilityHelpers.push(e, new Vec3(0, 0.5, 0));
			if (e instanceof Player victim) {
				victim.stopUsingItem();
				victim.getCooldowns().addCooldown(Items.SHIELD, 100);
			}
		}
	}

	// ---- R: Ground Slam / air dive -------------------------------------------------------------

	private static void groundSlamActivate(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "diving") > 0.5f) {
			return; // already mid-dive
		}
		if (p.onGround()) {
			groundSlam(ctx, 14.0f, 5.0, 0.85, 1.0);
			triggerCd(ctx, 8 * 20);
		} else {
			res(p, "diving", 1);
			res(p, "dive_from_y", (float) p.getY());
			Vec3 look = p.getLookAngle();
			AbilityHelpers.launchSelf(p, new Vec3(look.x * 0.35, -2.4, look.z * 0.35));
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
		}
	}

	private static void groundSlamTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "diving") <= 0.5f) {
			return;
		}
		if (p.onGround() || p.verticalCollisionBelow) {
			double dist = Math.max(0, res(p, "dive_from_y") - p.getY());
			float dmg;
			if (dist < 6) {
				dmg = 14f;
			} else if (dist < 10) {
				dmg = 16f;
			} else if (dist < 20) {
				dmg = 20f;
			} else if (dist < 30) {
				dmg = 24f;
			} else {
				dmg = 28f;
			}
			res(p, "diving", 0);
			res(p, "dive_from_y", 0);
			p.resetFallDistance(); // committed dive: the landing shrugs off fall damage
			groundSlam(ctx, dmg, 5.0, 0.6, 1.4);
			((ServerLevel) p.level()).sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
			triggerCd(ctx, 8 * 20);
		}
	}

	/** Radial shockwave: damage, 2 s slow, upward launch. Leaves terrain intact. */
	private static void groundSlam(AbilityContext ctx, float damage, double radius, double launch, double kb) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float bonus = maxEffortActive(p) ? 4.0f : 0.0f;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), radius)) {
			AbilityHelpers.hurt(p, e, damage + bonus);
			AbilityHelpers.knockbackFrom(e, p.position(), kb);
			AbilityHelpers.push(e, new Vec3(0, launch, 0));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 50, radius / 2, 0.1, radius / 2, 0.06);
		// A ring of block-crack particles for the impact, sampling the ground -- no blocks are broken.
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0 * Math.PI * 2;
			double bx = p.getX() + Math.cos(a) * radius * 0.7;
			double bz = p.getZ() + Math.sin(a) * radius * 0.7;
			BlockPos gp = BlockPos.containing(bx, p.getY() - 0.5, bz);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(gp)),
					bx, p.getY(), bz, 6, 0.2, 0.1, 0.2, 0.02);
		}
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.1f);
	}

	// ---- G: Air Punch --------------------------------------------------------------------------

	/** Fires the instant G is pressed: a fist-shaped slug of compressed air punched down your sightline. */
	private static void airPunch(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float dmg = maxEffortActive(p) ? 22f : 16f;
		double range = 16.0;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();

		// The compressed air travelling out: a tight core of GUST + a leading burst.
		Vec3 start = eye.add(look.scale(1.2));
		Vec3 end = eye.add(look.scale(range));
		AbilityHelpers.line(level, start, end, ParticleTypes.GUST, 1.4);
		AbilityHelpers.line(level, start, end, ParticleTypes.CLOUD, 2.2);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, start.x, start.y, start.z, 2, 0.1, 0.1, 0.1, 0.0);

		LivingEntity aimed = AbilityHelpers.raycastEntity(p, range);
		Vec3 impact = aimed != null ? aimed.position().add(0, aimed.getBbHeight() * 0.5, 0) : end;
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.POOF, impact.x, impact.y, impact.z, 20, 0.5, 0.5, 0.5, 0.1);

		boolean hitAny = false;
		for (LivingEntity e : AbilityHelpers.living(level, eye.add(look.scale(range * 0.5)), range * 0.5 + 1.5,
				e -> e != p && new Vec3(e.getX() - eye.x, e.getEyeY() - eye.y, e.getZ() - eye.z).normalize().dot(look) > 0.88)) {
			AbilityHelpers.hurt(p, e, dmg);
			// A punch of pressurised air hits like a wall of wind -- heavy shove, small upward pop.
			AbilityHelpers.knockbackFrom(e, p.position(), 3.0);
			AbilityHelpers.push(e, look.scale(1.2).add(0, 0.35, 0));
			hitAny = true;
		}
		p.level().playSound(null, p.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.3f, 0.7f);
		AbilityHelpers.sound(p, hitAny ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 0.5f);
		triggerCd(ctx, 10 * 20);
	}

	// ---- X: Power Leap ----------------------------------------------------------------------

	/** 0.5 s per tier of charge, 2.5 s (50 ticks) = maximum. Mirrors the client-side charge bar. */
	public static final int LEAP_MAX_CHARGE = 50;

	private static int leapTier(int heldTicks) {
		return heldTicks < 10 ? 0 : heldTicks < 20 ? 1 : heldTicks < 30 ? 2 : heldTicks < 40 ? 3 : heldTicks < 50 ? 4 : 5;
	}

	/**
	 * The client released X after holding it {@code heldTicks} ticks. Launch along the full line of
	 * sight, scaled by the charge tier. Cooldown-gated here (the only authority).
	 */
	public static void performPowerLeap(ServerPlayer p, int heldTicks) {
		Power power = power();
		if (power == null || !owns(p)) {
			return;
		}
		com.projecthero.mod.hero.Ability ab = power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_3);
		if (!com.projecthero.mod.hero.ExperimentalPowers.cooldownReady(p, power, ab)) {
			return;
		}
		res(p, "leap_press", 0);

		int tier = leapTier(heldTicks);
		double[] speed = {2.4, 3.3, 4.3, 5.4, 6.6, 7.8};
		double s = speed[tier] * (maxEffortActive(p) ? 1.2 : 1.0);
		Vec3 dir = p.getLookAngle().normalize();
		if (dir.y < 0.15) {
			dir = dir.add(0, 0.30, 0).normalize();
		}
		AbilityHelpers.launchSelf(p, dir.scale(s));
		res(p, "no_fall_until", p.level().getGameTime() + 600);
		if (p.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 40, 0.4, 0.2, 0.4, 0.1);
		}
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1f, 0.5f);
		com.projecthero.mod.hero.ExperimentalPowers.triggerCooldown(p, power, ab,
				com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(maxEffortActive(p) ? 30 : 60));
	}

	// ---- Z: Bull Rush / Impact Smash (both hold-to-charge for 5 s) --------------------------

	/**
	 * Press Z (hold): begin charging. Sneak while pressing charges Impact Smash instead of Bull Rush.
	 * Idempotent -- a repeated press while already charging or rushing is ignored, which is what stops
	 * the packet re-fire that used to reset the charge every tick.
	 */
	private static void bullRushPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "z_charge") > 0.5f || res(p, "z_run_end") > 0.5f) {
			return;
		}
		boolean smash = p.isShiftKeyDown();
		// Bull Rush and Impact Smash share one cooldown -- either being on cooldown blocks both.
		if (res(p, "z_cd") > 0.5f) {
			ctx.actionBar("message.projecthero.ability.on_cooldown",
					net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(res(p, "z_cd") / 20.0f)));
			return;
		}
		res(p, "z_charge", p.level().getGameTime());
		res(p, "z_smash", smash ? 1 : 0);
		rushGuards(p, true);
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.8f, 0.55f);
	}

	/** Release Z: fire if the 5 s charge finished, otherwise cancel it. */
	private static void bullRushRelease(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "z_charge") <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) res(p, "z_charge");
		if (held >= SMASH_CHARGE) {
			fireZ(ctx);
		} else {
			cancelZ(p);
		}
	}

	private static void bullRushTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		long now = p.level().getGameTime();
		ServerLevel level = ctx.level();

		float zcd = res(p, "z_cd");
		if (zcd > 0) {
			res(p, "z_cd", zcd - 1);
		}

		// ---- charging ----
		float charge = res(p, "z_charge");
		if (charge > 0.5f) {
			long held = now - (long) charge;
			boolean smash = res(p, "z_smash") > 0.5f;
			// Root the player and build a ring of dust / crackle -- no repeating loud sound.
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.15, 1.0, 0.15));
			p.hurtMarked = true;
			double frac = Math.min(1.0, held / (double) SMASH_CHARGE);
			level.sendParticles(smash ? ParticleTypes.CRIT : ParticleTypes.CLOUD,
					p.getX(), p.getY() + 0.1, p.getZ(), 4 + (int) (frac * 8), 0.5 * frac + 0.2, 0.05, 0.5 * frac + 0.2, 0.02);
			if (held == 20 || held == 60) {
				AbilityHelpers.sound(p, SoundEvents.PISTON_CONTRACT, 0.5f, 0.6f + (float) frac * 0.5f);
			}
			if (held >= SMASH_CHARGE) {
				fireZ(ctx);
			}
			return;
		}

		// ---- rushing (Bull Rush only) ----
		float runEnd = res(p, "z_run_end");
		if (runEnd <= 0.5f) {
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		double sprint = maxEffortActive(p) ? 0.62 : 0.55;
		p.setDeltaMovement(flat.x * sprint, Math.max(p.getDeltaMovement().y, -0.25), flat.z * sprint);
		p.hurtMarked = true;
		p.hasImpulse = true;
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 5, 0.3, 0.05, 0.3, 0.04);

		// Plough straight through -- hitting an enemy does NOT stop the rush. Vanilla i-frames keep
		// the same mob from being hit more than ~twice a second.
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(flat.scale(1.3)), 2.0)) {
			AbilityHelpers.hurt(p, e, maxEffortActive(p) ? 26f : 20f);
			AbilityHelpers.knockbackFrom(e, p.position(), 4.2); // ~5 blocks
			AbilityHelpers.push(e, new Vec3(0, 0.45, 0));
		}
		long ticksRun = RUSH_RUN - ((long) runEnd - now);
		boolean stuck = ticksRun > 6 && p.horizontalCollision
				&& p.getDeltaMovement().horizontalDistanceSqr() < 0.06;
		if (now >= (long) runEnd || stuck) {
			endRush(p);
		}
	}

	/** The 5 s charge finished: launch the rush, or detonate the smash. */
	private static void fireZ(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		boolean smash = res(p, "z_smash") > 0.5f;
		res(p, "z_charge", 0);
		res(p, "z_smash", 0);
		if (smash) {
			rushGuards(p, false);
			impactSmash(ctx);
			res(p, "z_cd", SMASH_CD);
		} else {
			res(p, "z_run_end", p.level().getGameTime() + RUSH_RUN);
			PowerToggles.modifier(p, Attributes.STEP_HEIGHT, RUSH_STEP, 4.0, AttributeModifier.Operation.ADD_VALUE);
			AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 1.3f, 1.1f);
		}
	}

	private static void cancelZ(ServerPlayer p) {
		res(p, "z_charge", 0);
		res(p, "z_smash", 0);
		rushGuards(p, false);
		AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.2f);
	}

	private static void endRush(ServerPlayer p) {
		res(p, "z_run_end", 0);
		res(p, "z_cd", RUSH_CD);
		rushGuards(p, false);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, RUSH_STEP);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
	}

	/** 25%-ish damage resistance + full knockback resistance, on while charging and rushing. */
	private static void rushGuards(ServerPlayer p, boolean on) {
		if (on) {
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, RUSH_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SMASH_CHARGE + RUSH_RUN + 40, 0, false, false, true));
		} else {
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, RUSH_KB);
			p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
		}
	}

	private static void impactSmash(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float dmg = maxEffortActive(p) ? 72f : 60f;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 20.0)) {
			double d = e.position().distanceTo(p.position());
			AbilityHelpers.hurt(p, e, (float) (dmg * (1.0 - Math.min(0.6, d / 20.0))));
			AbilityHelpers.knockbackFrom(e, p.position(), 2.4);
			AbilityHelpers.push(e, new Vec3(0, 0.9, 0));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 3, 3, 1, 3, 0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 120, 6, 0.5, 6, 0.1);
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.4f);
		level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 0.6f);
		if (AbilityHelpers.canGrief()) {
			BlockPos base = p.blockPosition();
			int r = 6;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int dy = -2; dy <= 1; dy++) {
						if (dx * dx + dy * dy + dz * dz > r * r) {
							continue;
						}
						BlockPos bp = base.offset(dx, dy, dz);
						var st = level.getBlockState(bp);
						float hard = st.getDestroySpeed(level, bp);
						if (!st.isAir() && hard >= 0 && hard < 3.0f) {
							level.destroyBlock(bp, true, p);
						}
					}
				}
			}
		}
	}

	// ---- V: Grab & Carry (no damage) -------------------------------------------------------

	private static void grabCarry(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int held = (int) res(p, "grabbed");
		if (held != 0) {
			Entity e = ctx.level().getEntity(held);
			if (e instanceof LivingEntity le && le.isAlive()) {
				le.setNoGravity(false);
				if (p.isShiftKeyDown()) {
					// sneak: set the passenger down gently, do not hurl them
					le.setDeltaMovement(0, le.getDeltaMovement().y, 0);
					AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.7f, 1.0f);
				} else {
					le.setDeltaMovement(p.getLookAngle().scale(2.6).add(0, 0.3, 0));
					le.hurtMarked = true;
					le.hasImpulse = true;
					AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
				}
			}
			res(p, "grabbed", 0);
			res(p, "grab_ticks", 0);
			triggerCd(ctx, 5 * 20);
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, 5.0);
		if (target != null && AbilityHelpers.isValidGrabTarget(target, p)) {
			res(p, "grabbed", target.getId());
			res(p, "grab_ticks", 300);
			target.setNoGravity(true);
			ctx.actionBar("message.projecthero.ability.grabbed");
		}
	}

	private static void grabTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int held = (int) res(p, "grabbed");
		if (held == 0) {
			return;
		}
		Entity e = ctx.level().getEntity(held);
		int ticks = (int) res(p, "grab_ticks") - 1;
		if (!(e instanceof LivingEntity le) || !le.isAlive() || ticks <= 0 || p.distanceToSqr(e) > 100) {
			if (e instanceof LivingEntity le2) {
				le2.setNoGravity(false);
			}
			res(p, "grabbed", 0);
			res(p, "grab_ticks", 0);
			return;
		}
		res(p, "grab_ticks", ticks);
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(2.2));
		le.setPos(hold.x, hold.y - le.getBbHeight() / 2, hold.z);
		le.setDeltaMovement(Vec3.ZERO);
		le.fallDistance = 0;
		le.hurtMarked = true;
	}

	// ---- C: Maximum Effort -----------------------------------------------------------------

	private static void maximumEffort(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		res(p, "effort_left", EFFORT_TICKS);
		int d = EFFORT_TICKS;
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, d, 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, d, 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, d, 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, d, 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.JUMP, d, 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, d, 2, false, false, true));
		AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CRIT, 40, 0.6);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.9f, 0.6f);
		p.level().playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 1.4f);
		ctx.actionBar("message.projecthero.strength.maximum_effort");
		ctx.triggerCooldown(60 * 20);
	}
}
