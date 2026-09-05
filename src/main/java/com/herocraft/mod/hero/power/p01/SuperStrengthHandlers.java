package com.herocraft.mod.hero.power.p01;

import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.PowerPassives;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.PowerToggles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Power 01 — Super Strength. */
public final class SuperStrengthHandlers {
	private static final String KEY = "power_01_super_strength";
	private static final net.minecraft.resources.ResourceLocation BRACE_KB =
			com.herocraft.mod.HeroCraftMod.id("brace_knockback_resist");
	private static final net.minecraft.resources.ResourceLocation BRACE_SPD =
			com.herocraft.mod.HeroCraftMod.id("brace_slow");
	private static final net.minecraft.resources.ResourceLocation PASSIVE_ATK =
			com.herocraft.mod.HeroCraftMod.id("strength_passive_attack");
	private static final net.minecraft.resources.ResourceLocation PASSIVE_JUMP =
			com.herocraft.mod.HeroCraftMod.id("strength_passive_jump");

	private SuperStrengthHandlers() {
	}

	public static void register() {
		AbilityHandlers.register(KEY, "power_punch", Handlers.instant(SuperStrengthHandlers::powerPunch));
		AbilityHandlers.register(KEY, "ground_slam", Handlers.instant(SuperStrengthHandlers::groundSlam));
		AbilityHandlers.register(KEY, "super_leap", Handlers.instant(SuperStrengthHandlers::superLeap));
		AbilityHandlers.register(KEY, "thunderous_impact", Handlers.instant(SuperStrengthHandlers::thunderousImpact));
		AbilityHandlers.register(KEY, "grab_throw", Handlers.instantTicking(SuperStrengthHandlers::grabThrow, SuperStrengthHandlers::grabTick));
		AbilityHandlers.register(KEY, "brace", Handlers.toggle(SuperStrengthHandlers::braceOn, SuperStrengthHandlers::braceOff, ctx -> {
			braceOn(ctx);
			com.herocraft.mod.hero.power.AbilityHelpers.modeAura(ctx.player(), net.minecraft.core.particles.ParticleTypes.CRIT, 3);
		}));

		PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				// Vanilla unarmed ATTACK_DAMAGE base is 1.0, so +6 makes an unarmed hit land at 7.
				// A held tool's own ADD_VALUE modifiers stack on top of this additively.
				PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, PASSIVE_ATK, 6.0, AttributeModifier.Operation.ADD_VALUE);
				PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, PASSIVE_JUMP, 0.12, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			} else {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, PASSIVE_ATK);
				PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, PASSIVE_JUMP);
			}
		});
	}

	private static void powerPunch(com.herocraft.mod.hero.AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity target = AbilityHelpers.raycastEntity(p, 5.0);
		Vec3 tip = p.getEyePosition().add(p.getLookAngle().scale(3.0));
		AbilityHelpers.burst(level, tip, ParticleTypes.SWEEP_ATTACK, 3, 0.2);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
		if (target != null) {
			AbilityHelpers.hurt(p, target, 9.0f);
			AbilityHelpers.knockbackFrom(target, p.position(), 1.4);
			AbilityHelpers.burst(level, target.position().add(0, 1, 0), ParticleTypes.CRIT, 12, 0.3);
			if (AbilityHelpers.canGrief()) {
				breakWeakBlockBehind(level, p, target);
			}
		}
		ctx.triggerCooldown();
	}

	private static void breakWeakBlockBehind(ServerLevel level, ServerPlayer p, LivingEntity target) {
		Vec3 behind = target.position().add(p.getLookAngle().scale(1.0));
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(behind.x, target.getEyeY(), behind.z);
		var state = level.getBlockState(pos);
		if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0 && state.getDestroySpeed(level, pos) < 1.6f) {
			level.destroyBlock(pos, true, p);
		}
	}

	private static void groundSlam(com.herocraft.mod.hero.AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		double r = 4.0;
		float meteor = com.herocraft.mod.hero.power.PowerCombos.meteorSlamBonus(p, KEY);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
			AbilityHelpers.hurt(p, e, 6.0f + meteor);
			AbilityHelpers.knockbackFrom(e, p.position(), 0.9);
			AbilityHelpers.push(e, new Vec3(0, 0.55, 0));
		}
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 40, r / 2, 0.1, r / 2, 0.05);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.2f);
		ctx.triggerCooldown();
	}

	private static void superLeap(com.herocraft.mod.hero.AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		Vec3 dir = p.getLookAngle();
		AbilityHelpers.launchSelf(p, new Vec3(dir.x * 1.7, Math.max(0.55, dir.y * 1.4 + 0.5), dir.z * 1.7));
		AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 20, 0.3);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
		ctx.triggerCooldown();
	}

	private static void thunderousImpact(com.herocraft.mod.hero.AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		double r = 7.0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
			double dist = e.position().distanceTo(p.position());
			float dmg = (float) (30.0 * (1.0 - Math.min(0.8, dist / r)));
			AbilityHelpers.hurt(p, e, dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 2.0);
			AbilityHelpers.push(e, new Vec3(0, 0.8, 0));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.2, p.getZ(), 60, r / 2, 0.2, r / 2, 0.02);
		// cosmetic cracks: block-break particles in a ring, no actual block changes
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0 * Math.PI * 2;
			double bx = p.getX() + Math.cos(a) * r * 0.7;
			double bz = p.getZ() + Math.sin(a) * r * 0.7;
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
							level.getBlockState(net.minecraft.core.BlockPos.containing(bx, p.getY() - 1, bz))),
					bx, p.getY(), bz, 6, 0.2, 0.1, 0.2, 0.02);
		}
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.6f);
		level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.8f);
		ctx.triggerCooldown();
	}

	// ---- grab & throw ----

	private static void grabThrow(com.herocraft.mod.hero.AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int held = (int) ctx.resource("grabbed");
		if (held != 0) {
			Entity e = ctx.level().getEntity(held);
			if (e instanceof LivingEntity le && le.isAlive()) {
				le.setDeltaMovement(p.getLookAngle().scale(2.6).add(0, 0.3, 0));
				le.hurtMarked = true;
				le.hasImpulse = true;
				AbilityHelpers.hurt(p, le, 4.0f);
			}
			ctx.setResource("grabbed", 0, 1_000_000);
			ctx.setResource("grab_ticks", 0, 1_000_000);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
			ctx.triggerCooldown();
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, 5.0);
		if (target != null && AbilityHelpers.isValidGrabTarget(target, p)) {
			ctx.setResource("grabbed", target.getId(), 1_000_000);
			ctx.setResource("grab_ticks", 160, 1_000_000);
			ctx.actionBar("message.herocraft.ability.grabbed");
		}
	}

	private static void grabTick(com.herocraft.mod.hero.AbilityContext ctx) {
		int held = (int) ctx.resource("grabbed");
		if (held == 0) {
			return;
		}
		ServerPlayer p = ctx.player();
		Entity e = ctx.level().getEntity(held);
		int ticks = (int) ctx.resource("grab_ticks") - 1;
		if (!(e instanceof LivingEntity le) || !le.isAlive() || ticks <= 0 || p.distanceToSqr(e) > 64) {
			ctx.setResource("grabbed", 0, 1_000_000);
			ctx.setResource("grab_ticks", 0, 1_000_000);
			return;
		}
		ctx.setResource("grab_ticks", ticks, 1_000_000);
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(2.2));
		le.setPos(hold.x, hold.y - le.getBbHeight() / 2, hold.z);
		le.setDeltaMovement(Vec3.ZERO);
		le.fallDistance = 0;
		le.hurtMarked = true;
	}

	// ---- brace ----

	private static void braceOn(com.herocraft.mod.hero.AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, BRACE_KB, 0.8, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, BRACE_SPD, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.effect(p, MobEffects.DAMAGE_RESISTANCE, 1, true);
	}

	private static void braceOff(com.herocraft.mod.hero.AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, BRACE_KB);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, BRACE_SPD);
		PowerToggles.clearEffect(p, MobEffects.DAMAGE_RESISTANCE);
	}
}
