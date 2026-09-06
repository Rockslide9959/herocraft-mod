package com.projecthero.mod.hero.power.p17;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.GrabHelper;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Power 17 — Elasticity. */
public final class ElasticityHandlers {
	private static final String KEY = "power_17_elasticity";
	private static final ResourceLocation FORM_REACH = com.projecthero.mod.ProjectHeroMod.id("elastic_form_reach");
	private static final ResourceLocation FORM_SPEED = com.projecthero.mod.ProjectHeroMod.id("elastic_form_speed");

	private ElasticityHandlers() {
	}

	public static void register() {
		AbilityHandlers.register(KEY, "stretch_punch", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 15.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 15.0), ParticleTypes.ITEM_SLIME, 3.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 8.0f);
				AbilityHelpers.knockbackFrom(t, p.position(), 0.9);
			}
			AbilityHelpers.sound(p, SoundEvents.SLIME_ATTACK, 1.0f, 1.2f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "double_fist_slam", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(7));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 4.0)) {
				AbilityHelpers.hurt(p, e, 12.0f);
				AbilityHelpers.knockbackFrom(e, p.getEyePosition(), 2.0);
			}
			AbilityHelpers.burst(ctx.level(), front, ParticleTypes.ITEM_SLIME, 30, 0.6);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.2f, 0.7f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "slingshot", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			var hit = AbilityHelpers.raycastBlock(p, 45.0);
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
				return;
			}
			Vec3 pull = Vec3.atCenterOf(hit.getBlockPos()).subtract(p.position()).normalize().scale(2.8);
			AbilityHelpers.launchSelf(p, pull.add(0, 0.3, 0));
			AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "giant_hammer_fist", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 at = AbilityHelpers.aimPoint(p, 8.0);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 4.5)) {
				AbilityHelpers.hurt(p, e, 27.0f);
				AbilityHelpers.knockbackFrom(e, at, 1.5);
				AbilityHelpers.push(e, new Vec3(0, -0.4, 0));
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 50, 2);
			}
			ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 80, 2.5, 0.5, 2.5, 0.2);
			ctx.level().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.4f, 0.4f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "elastic_grab", Handlers.instantTicking(ctx -> {
			if (GrabHelper.isHolding(ctx)) {
				GrabHelper.throwHeld(ctx, 2.0, 3.0f);
				ctx.triggerCooldown();
			} else if (GrabHelper.tryGrab(ctx, 15.0, 120)) {
				ctx.actionBar("message.projecthero.ability.grabbed");
			}
		}, ctx -> GrabHelper.tick(ctx, 2.5)));

		AbilityHandlers.register(KEY, "elastic_form", Handlers.toggle(
				ElasticityHandlers::formOn, ElasticityHandlers::formOff, ctx -> {
					formOn(ctx);
					ServerPlayer p = ctx.player();
					// bounce like a slime block on every landing -- the harder the fall, the bigger the rebound.
					// track the descent speed while airborne (resources can't be negative, so store its magnitude).
					float fall = ctx.resource("elastic_fall");
					if (!p.onGround()) {
						ctx.setResource("elastic_fall", (float) Math.max(fall, -p.getDeltaMovement().y), 1000.0f);
					} else if (fall > 0.12f) {
						double bounce = Math.min(1.35, 0.4 + fall * 0.9);
						p.setDeltaMovement(p.getDeltaMovement().x * 1.05, bounce, p.getDeltaMovement().z * 1.05);
						p.hurtMarked = true;
						p.hasImpulse = true;
						p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
						p.resetFallDistance();
						ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY(), p.getZ(), 12, 0.3, 0.05, 0.3, 0.02);
						AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 0.6f, 1.4f);
						ctx.setResource("elastic_fall", 0, 1000.0f);
					} else {
						ctx.setResource("elastic_fall", 0, 1000.0f);
					}
					AbilityHelpers.modeAura(p, ParticleTypes.ITEM_SLIME, 3);
				}));

		// While Elastic Form is on, anything that hits you in melee bounces ~2 blocks straight back.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && formActive(sp)
					&& source.getEntity() instanceof LivingEntity attacker && sp.distanceToSqr(attacker) < 25.0) {
				Vec3 away = attacker.position().subtract(sp.position()).normalize().scale(0.9).add(0, 0.35, 0);
				attacker.setDeltaMovement(away);
				attacker.hurtMarked = true;
			}
		});
	}

	private static void formOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, FORM_REACH, 5.0,
				AttributeModifier.Operation.ADD_VALUE); // base 3 -> 8 blocks
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, FORM_SPEED, 0.3,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		// Jump Boost IV ~= a 3-block leap.
		var jump = p.getEffect(MobEffects.JUMP);
		if (jump == null || !jump.isInfiniteDuration() || jump.getAmplifier() != 3) {
			p.addEffect(new MobEffectInstance(MobEffects.JUMP, MobEffectInstance.INFINITE_DURATION, 3, false, false, false));
		}
	}

	private static void formOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, FORM_REACH);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, FORM_SPEED);
		p.removeEffect(MobEffects.JUMP);
		p.removeEffect(MobEffects.SLOW_FALLING);
	}

	private static boolean formActive(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6));
	}
}
