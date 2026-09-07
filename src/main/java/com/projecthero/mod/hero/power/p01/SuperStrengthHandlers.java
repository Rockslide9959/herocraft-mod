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

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
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
	/** Bull Rush: 5 s wind-up, 5 s run, 40 s cooldown. Impact Smash: 5 s charge, 90 s cooldown. */
	private static final int RUSH_WINDUP = 100;
	private static final int RUSH_RUN = 100;
	private static final int RUSH_CD = 800;
	private static final int SMASH_CHARGE = 100;
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
		AbilityHandlers.register(KEY, "air_punch",
				Handlers.instantTicking(SuperStrengthHandlers::airPunchActivate, SuperStrengthHandlers::airPunchTick));
		AbilityHandlers.register(KEY, "power_leap", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
					return;
				}
				res(ctx.player(), "leap_press", ctx.player().level().getGameTime());
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				powerLeapFire(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				powerLeapTick(ctx);
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
		registerChargedPunch();
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

	/** Client asked to arm the charged punch (2 s attack-hold). Validated here. */
	public static void armChargedPunch(ServerPlayer player) {
		if (!owns(player) || res(player, "charged_cd") > 0.5f || res(player, "charged_armed") > 0.5f) {
			return;
		}
		res(player, "charged_armed", 1);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 0.5f);
		((ServerLevel) player.level()).sendParticles(ParticleTypes.CRIT,
				player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.5, 0.4, 0.2);
	}

	private static void registerChargedPunch() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof LivingEntity target)) {
				return InteractionResult.PASS;
			}
			if (res(sp, "charged_armed") <= 0.5f) {
				return InteractionResult.PASS;
			}
			res(sp, "charged_armed", 0);
			res(sp, "charged_cd", CHARGED_CD_TICKS);

			AbilityHelpers.hurt(sp, target, AbilityHelpers.kinetic(sp), CHARGED_DAMAGE);
			AbilityHelpers.knockbackFrom(target, sp.position(), 3.2);
			AbilityHelpers.push(target, new Vec3(0, 0.55, 0));
			ServerLevel level = (ServerLevel) world;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					4, 0.3, 0.3, 0.3, 0.0);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 20, 0.4, 0.5, 0.4, 0.4);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()),
					target.getX(), target.getY(), target.getZ(), 16, 0.4, 0.2, 0.4, 0.1);
			level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);

			if (target instanceof Player victim) {
				victim.stopUsingItem();
				victim.getCooldowns().addCooldown(Items.SHIELD, 100);
			}
			return InteractionResult.SUCCESS; // consume the hit -- exactly 20, no vanilla damage on top
		});
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

	/** Radial shockwave: damage, 2 s slow, upward launch, and weak-block breaking. */
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
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.1f);
		if (AbilityHelpers.canGrief()) {
			int ir = (int) Math.ceil(radius);
			BlockPos base = p.blockPosition();
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (dx * dx + dz * dz > radius * radius) {
						continue;
					}
					for (int dy = -1; dy <= 0; dy++) {
						BlockPos bp = base.offset(dx, dy, dz);
						var st = level.getBlockState(bp);
						float hard = st.getDestroySpeed(level, bp);
						if (!st.isAir() && hard >= 0 && hard < 1.6f) {
							level.destroyBlock(bp, true, p);
						}
					}
				}
			}
		}
	}

	// ---- G: Air Punch --------------------------------------------------------------------------

	private static void airPunchActivate(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		res(p, "airpunch_at", p.level().getGameTime() + 20);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
		triggerCd(ctx, 12 * 20);
	}

	private static void airPunchTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float at = res(p, "airpunch_at");
		if (at <= 0) {
			return;
		}
		long now = p.level().getGameTime();
		ServerLevel level = ctx.level();
		if (now < at) {
			Vec3 fist = p.getEyePosition().add(p.getLookAngle().scale(0.8));
			level.sendParticles(ParticleTypes.CLOUD, fist.x, fist.y, fist.z, 3, 0.1, 0.1, 0.1, 0.01);
			return;
		}
		res(p, "airpunch_at", 0);
		float dmg = maxEffortActive(p) ? 22f : 16f;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = eye.add(look.scale(14));
		AbilityHelpers.line(level, eye.add(look.scale(1.2)), end, ParticleTypes.SWEEP_ATTACK, 1.2);
		AbilityHelpers.line(level, eye.add(look.scale(1.2)), end, ParticleTypes.CLOUD, 2.0);
		boolean hitAny = false;
		for (LivingEntity e : AbilityHelpers.living(level, eye.add(look.scale(7)), 8.0,
				e -> e != p && new Vec3(e.getX() - eye.x, e.getEyeY() - eye.y, e.getZ() - eye.z).normalize().dot(look) > 0.9)) {
			AbilityHelpers.hurt(p, e, dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 1.6);
			hitAny = true;
		}
		AbilityHelpers.sound(p, hitAny ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 0.6f);
	}

	// ---- X: Power Leap (charge) --------------------------------------------------------------

	private static void powerLeapTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float press = res(p, "leap_press");
		if (press <= 0) {
			return;
		}
		long held = p.level().getGameTime() - (long) press;
		ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(),
				2, 0.3, 0.05, 0.3, 0.02);
		if (held >= 50) {
			powerLeapFire(ctx); // auto-release at max charge
		}
	}

	private static void powerLeapFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float press = res(p, "leap_press");
		if (press <= 0) {
			return;
		}
		res(p, "leap_press", 0);
		long held = p.level().getGameTime() - (long) press;
		int tier = held < 10 ? 0 : held < 20 ? 1 : held < 30 ? 2 : held < 40 ? 3 : held < 50 ? 4 : 5;
		double[] blocks = {11, 15, 20, 26, 32, 38};
		double b = blocks[tier];
		double horiz = 0.40 * Math.sqrt(b);
		double up = 0.42 + tier * 0.07;
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0e-4) {
			flat = new Vec3(0, 0, 1);
		}
		flat = flat.normalize();
		AbilityHelpers.launchSelf(p, flat.scale(horiz).add(0, up, 0));
		res(p, "no_fall_until", p.level().getGameTime() + 600);
		AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 24, 0.35);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
		triggerCd(ctx, 3 * 20);
	}

	// ---- Z: Bull Rush / Impact Smash ---------------------------------------------------------

	private static void bullRushPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		long now = p.level().getGameTime();
		if (p.isShiftKeyDown()) {
			if (res(p, "smash_cd") > 0.5f || res(p, "rush_phase") > 0.5f) {
				return;
			}
			res(p, "smash_press", now);
			AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.7f, 0.6f);
			return;
		}
		if (res(p, "rush_cd") > 0.5f || res(p, "rush_phase") > 0.5f || res(p, "smash_press") > 0.5f) {
			return;
		}
		res(p, "rush_phase", 1);
		res(p, "rush_end", now + RUSH_WINDUP);
		applyRushGuards(p, true);
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.9f, 0.9f);
	}

	private static void bullRushRelease(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "smash_press") > 0.5f) {
			long held = p.level().getGameTime() - (long) res(p, "smash_press");
			res(p, "smash_press", 0);
			if (held >= SMASH_CHARGE) {
				impactSmash(ctx);
			} else {
				AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.0f);
			}
		}
	}

	private static void bullRushTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		long now = p.level().getGameTime();

		// timers used by the HUD / gating
		for (String t : new String[] {"rush_cd", "smash_cd"}) {
			float v = res(p, t);
			if (v > 0) {
				res(p, t, v - 1);
			}
		}

		float smashPress = res(p, "smash_press");
		if (smashPress > 0.5f) {
			long held = now - (long) smashPress;
			ctx.level().sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 4, 0.4, 0.6, 0.4, 0.1);
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 1, 0.2));
			if (held >= SMASH_CHARGE) {
				res(p, "smash_press", 0);
				impactSmash(ctx);
			}
		}

		float phase = res(p, "rush_phase");
		if (phase < 0.5f) {
			return;
		}
		if (phase < 1.5f) { // wind-up
			ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.5, 0.05, 0.5, 0.02);
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 1, 0.3));
			if (now >= (long) res(p, "rush_end")) {
				res(p, "rush_phase", 2);
				res(p, "rush_end", now + RUSH_RUN);
				PowerToggles.modifier(p, Attributes.STEP_HEIGHT, RUSH_STEP, 4.0, AttributeModifier.Operation.ADD_VALUE);
				AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 1.2f, 1.2f);
			}
			return;
		}
		// rushing
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 v = flat.scale(0.52);
		p.setDeltaMovement(v.x, Math.max(p.getDeltaMovement().y, -0.2), v.z);
		p.hurtMarked = true;
		p.hasImpulse = true;
		ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.3, 0.05, 0.3, 0.03);
		boolean hit = false;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(flat.scale(1.2)), 1.8)) {
			AbilityHelpers.hurt(p, e, maxEffortActive(p) ? 26f : 20f);
			AbilityHelpers.knockbackFrom(e, p.position(), 3.6);
			AbilityHelpers.push(e, new Vec3(0, 0.4, 0));
			hit = true;
		}
		if (hit || now >= (long) res(p, "rush_end") || p.horizontalCollision) {
			endBullRush(p);
		}
	}

	private static void applyRushGuards(ServerPlayer p, boolean on) {
		if (on) {
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, RUSH_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RUSH_WINDUP + RUSH_RUN + 20, 0, false, false, true));
		} else {
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, RUSH_KB);
			p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
		}
	}

	private static void endBullRush(ServerPlayer p) {
		res(p, "rush_phase", 0);
		res(p, "rush_end", 0);
		res(p, "rush_cd", RUSH_CD);
		applyRushGuards(p, false);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, RUSH_STEP);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
	}

	private static void impactSmash(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		res(p, "smash_cd", SMASH_CD);
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
