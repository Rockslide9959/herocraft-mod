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

/**
 * Power 17 — Elasticity.
 *
 * <p>v0.10.10: every attack hits harder (the kit was tuned well under comparable powers -- an 8-damage
 * primary against Geokinesis' 9-damage Rock Shot and a 27-damage ultimate against Boulder Lift's 20 for
 * a much shorter cooldown), Elastic Form shrugs off arrows and other projectiles outright rather than
 * only bouncing melee attackers away, its slime-block bounce now damps out instead of rebounding
 * forever, and Slingshot fired at a <em>creature</em> reels you into them and lands a hit.
 */
public final class ElasticityHandlers {
	private static final String KEY = "power_17_elasticity";
	private static final ResourceLocation FORM_REACH = com.projecthero.mod.ProjectHeroMod.id("elastic_form_reach");
	private static final ResourceLocation FORM_SPEED = com.projecthero.mod.ProjectHeroMod.id("elastic_form_speed");

	/** Slingshot: how far it looks for an anchor, and how long the anchored dash may run. */
	private static final double SLING_RANGE = 45.0;
	private static final float SLING_TICKS = 40.0f;
	private static final double SLING_IMPACT_RANGE = 2.6;
	private static final float SLING_DAMAGE = 14.0f;

	/** Elastic Form's slime-block landing: fraction of the impact returned, and the floor below which it stops. */
	private static final float BOUNCE_MIN_FALL = 0.35f;
	private static final double BOUNCE_RESTITUTION = 0.5;
	private static final double BOUNCE_MAX = 1.25;

	private ElasticityHandlers() {
	}

	public static void register() {
		AbilityHandlers.register(KEY, "stretch_punch", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 15.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 15.0), ParticleTypes.ITEM_SLIME, 3.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 12.0f);
				AbilityHelpers.knockbackFrom(t, p.position(), 0.9);
			}
			AbilityHelpers.sound(p, SoundEvents.SLIME_ATTACK, 1.0f, 1.2f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "double_fist_slam", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(7));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 4.0)) {
				AbilityHelpers.hurt(p, e, 17.0f);
				AbilityHelpers.knockbackFrom(e, p.getEyePosition(), 2.0);
			}
			AbilityHelpers.burst(ctx.level(), front, ParticleTypes.ITEM_SLIME, 30, 0.6);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.2f, 0.7f);
			ctx.triggerCooldown();
		}));

		// v0.10.10: aim Slingshot at a creature and you anchor onto THEM -- you are reeled in at speed and
		// slam into them on arrival, instead of the shot simply doing nothing because there was a mob
		// where the block you needed should have been. Aimed at terrain it is the old reel-in, unchanged.
		AbilityHandlers.register(KEY, "slingshot", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity target = AbilityHelpers.raycastEntity(p, SLING_RANGE);
			if (target != null) {
				Vec3 pull = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
				AbilityHelpers.launchSelf(p, pull.normalize().scale(2.6).add(0, 0.25, 0));
				ctx.setResource("sling_id", target.getId(), 1.0e9f);
				ctx.setResource("sling_ticks", SLING_TICKS, SLING_TICKS);
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), target.position(), ParticleTypes.ITEM_SLIME, 3.0);
				AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 0.8f);
				ctx.triggerCooldown();
				return;
			}
			var hit = AbilityHelpers.raycastBlock(p, SLING_RANGE);
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
				return;
			}
			Vec3 pull = Vec3.atCenterOf(hit.getBlockPos()).subtract(p.position()).normalize().scale(2.8);
			AbilityHelpers.launchSelf(p, pull.add(0, 0.3, 0));
			AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}, ElasticityHandlers::slingTick));

		AbilityHandlers.register(KEY, "giant_hammer_fist", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 at = AbilityHelpers.aimPoint(p, 8.0);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 4.5)) {
				AbilityHelpers.hurt(p, e, 34.0f);
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
				GrabHelper.throwHeld(ctx, 2.4, 7.0f);
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
					} else if (fall > BOUNCE_MIN_FALL) {
						// v0.10.10: the rebound is strictly a FRACTION of the impact that caused it, and
						// small impacts do not rebound at all. The old curve (0.4 + fall * 0.9) had a fixed
						// 0.4 floor, so a landing always threw you back up hard enough to produce another
						// qualifying landing -- once you touched the ground in Elastic Form you bounced for
						// ever with no way to stop. Halving the energy each time means a big fall still
						// gives a big, satisfying rebound but the series always converges and you settle.
						double bounce = Math.min(BOUNCE_MAX, fall * BOUNCE_RESTITUTION);
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

	/**
	 * v0.10.10: Elastic Form is projectile-proof. A rubber body absorbing an arrow is the single most
	 * recognisable thing this power does, and it is what makes the form worth holding at range -- the
	 * melee-only knockback it had before did nothing at all against the archers and skeletons it is
	 * most needed for. Read by {@link com.projecthero.mod.hero.power.HeroDamageRules}.
	 */
	public static boolean deflectsProjectiles(ServerPlayer p) {
		return formActive(p);
	}

	/**
	 * Slingshot's anchored dash: haul the player toward the creature they latched onto, and slam into it
	 * on arrival. Runs for at most {@link #SLING_TICKS} so a target that dies, teleports or outruns the
	 * pull can never leave the player being reeled at nothing.
	 */
	private static void slingTick(AbilityContext ctx) {
		float left = ctx.resource("sling_ticks");
		if (left <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("sling_ticks", left - 1.0f, SLING_TICKS);
		int id = (int) ctx.resource("sling_id");
		if (id == 0 || !(p.level().getEntity(id) instanceof LivingEntity target) || !target.isAlive()) {
			endSling(ctx);
			return;
		}
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
		double dist = to.length();
		if (dist <= SLING_IMPACT_RANGE) {
			AbilityHelpers.hurt(p, target, SLING_DAMAGE);
			AbilityHelpers.knockbackFrom(target, p.position(), 1.2);
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			// stop dead on the hit rather than sailing straight past them
			p.setDeltaMovement(p.getDeltaMovement().scale(-0.15));
			p.hurtMarked = true;
			ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + 1.0, target.getZ(),
					40, 0.5, 0.5, 0.5, 0.15);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.3f, 0.8f);
			endSling(ctx);
			return;
		}
		// keep the pull topped up so terrain drag and gravity do not stall the dash halfway
		AbilityHelpers.launchSelf(p, to.normalize().scale(Math.min(2.6, 0.9 + dist * 0.12)).add(0, 0.08, 0));
		p.resetFallDistance();
		AbilityHelpers.line(ctx.level(), p.getEyePosition(), target.position(), ParticleTypes.ITEM_SLIME, 2.0);
	}

	private static void endSling(AbilityContext ctx) {
		ctx.setResource("sling_ticks", 0, SLING_TICKS);
		ctx.setResource("sling_id", 0, 1.0e9f);
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
