package com.projecthero.mod.hero.power.p23;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.GrabHelper;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Power 23 — Gravity Manipulation. C cycles Low / Normal / High personal gravity. */
public final class GravityHandlers {
	private static final String KEY = "power_23_gravity_manipulation";
	private static final net.minecraft.resources.ResourceLocation GRAV = com.projecthero.mod.ProjectHeroMod.id("gravity_field_grav");
	private static final net.minecraft.resources.ResourceLocation ZERO_G = com.projecthero.mod.ProjectHeroMod.id("zero_g_grav");
	// cycle starts at Normal (0): Normal -> High -> Low -> Normal ...
	private static final String[] MODES = {"normal", "high", "low"};

	private GravityHandlers() {
	}

	public static void register() {
		AbilityHandlers.register(KEY, "gravity_push", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(4));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 4.0)) {
				AbilityHelpers.push(e, e.position().subtract(p.getEyePosition()).normalize().scale(2.6).add(0, 0.5, 0));
				AbilityHelpers.hurt(p, e, 9.0f);
			}
			AbilityHelpers.burst(ctx.level(), front, ParticleTypes.PORTAL, 20, 0.6);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.4f);
			ctx.triggerCooldown();
		}));

		// Gravity Crush: pin a target under crushing gravity for 10 s -- 4 damage a second, held down,
		// and slowed for the whole duration.
		AbilityHandlers.register(KEY, "gravity_crush", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 18.0);
			if (t != null) {
				ctx.setResource("crush_id", t.getId(), 1_000_000);
				ctx.setResource("crush_ticks", 200, 200);
			}
			ctx.triggerCooldown();
		}, ctx -> {
			int id = (int) ctx.resource("crush_id");
			int t = (int) ctx.resource("crush_ticks") - 1;
			if (id == 0) {
				return;
			}
			if (t <= 0 || !(ctx.level().getEntity(id) instanceof LivingEntity le) || !le.isAlive()) {
				ctx.setResource("crush_id", 0, 1_000_000);
				return;
			}
			ctx.setResource("crush_ticks", t, 200);
			le.setDeltaMovement(le.getDeltaMovement().x * 0.3, -0.5, le.getDeltaMovement().z * 0.3);
			le.hurtMarked = true;
			// slowed for the entire duration (refreshed each tick)
			AbilityHelpers.applyControl(le, MobEffects.MOVEMENT_SLOWDOWN, 20, 3);
			if (t % 20 == 0) {
				AbilityHelpers.hurt(ctx.player(), le, 4.0f);
				ctx.level().sendParticles(ParticleTypes.SCULK_CHARGE_POP, le.getX(), le.getY() + 1, le.getZ(), 10, 0.3, 0.3, 0.3, 0.0);
			}
		}));

		AbilityHandlers.register(KEY, "zero_g", Handlers.toggle(
				ctx -> PowerToggles.modifier(ctx.player(), Attributes.GRAVITY, ZERO_G, -0.85, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
				ctx -> PowerToggles.clearModifier(ctx.player(), Attributes.GRAVITY, ZERO_G),
				ctx -> {
					PowerToggles.modifier(ctx.player(), Attributes.GRAVITY, ZERO_G, -0.85, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
					ctx.player().resetFallDistance();
				}));

		// Gravity Well: become a black hole for 12 s. Invulnerable, dragging every creature within
		// 10 blocks inward and crushing anything that gets close for 15 damage a second. Unlike
		// Density's Singularity it does not lift you off the ground -- you hold your position.
		AbilityHandlers.register(KEY, "gravity_well", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			ctx.setResource("well_ticks", 240, 240);
			p.setInvulnerable(true);
			ctx.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 80, 1.5, 1.5, 1.5, 0.2);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 0.4f);
			ctx.triggerCooldown();
		}, ctx -> {
			int t = (int) ctx.resource("well_ticks");
			if (t <= 0) {
				return;
			}
			ServerPlayer p = ctx.player();
			t--;
			ctx.setResource("well_ticks", t, 240);
			if (t <= 0) {
				p.setInvulnerable(false);
				return;
			}
			p.setInvulnerable(true); // re-assert (survives a relog mid-ability)
			// hold position: bleed off horizontal drift, let vanilla gravity keep you grounded
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x * 0.6, v.y, v.z * 0.6);
			p.resetFallDistance();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 10.0)) {
				Vec3 pull = p.position().subtract(e.position());
				double dist = pull.length();
				if (dist > 0.5) {
					e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.2)));
					e.hurtMarked = true;
				}
				if (dist <= 4.5 && t % 20 == 0) {
					AbilityHelpers.hurt(p, e, 15.0f);
				}
			}
			if (p.tickCount % 3 == 0) {
				ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 2.0, 1.5, 2.0, 0.4);
				ctx.level().sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.getX(), p.getY() + 1, p.getZ(), 6, 1.0, 1.0, 1.0, 0.1);
			}
			if (t % 20 == 0) {
				AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.5f);
			}
		}));

		// Levitate: a telekinetic-style grab -- lift the target in front of you and hold it on your
		// aim; press again to hurl it. Mirrors Telekinesis' Telekinetic Grab.
		AbilityHandlers.register(KEY, "levitate", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (GrabHelper.isHolding(ctx)) {
				GrabHelper.throwHeld(ctx, 2.4, 4.0f);
				AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 0.6f);
				ctx.triggerCooldown();
			} else if (GrabHelper.tryGrab(ctx, 16.0, 160)) {
				ctx.actionBar("message.projecthero.ability.grabbed");
				ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 15, 0.4, 0.6, 0.4, 0.2);
			}
		}, ctx -> GrabHelper.tick(ctx, 3.0)));

		AbilityHandlers.register(KEY, "gravity_field", Handlers.cycle(ctx -> {
			ctx.advanceCycle(3);
			applyField(ctx);
			ctx.actionBar("message.projecthero.gravity.field_" + MODES[ctx.cycleMode() % 3]);
			AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 0.5f + (ctx.cycleMode() % 3) * 0.4f);
		}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.GRAVITY, GRAV);
				player.setInvulnerable(false); // drop any lingering Gravity Well invulnerability
			} else {
				var power = com.projecthero.mod.hero.Powers.byKey(KEY);
				applyField(new AbilityContext(player, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6), true));
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			var power = com.projecthero.mod.hero.Powers.byKey(KEY);
			if (power != null && player.tickCount % 20 == 0) {
				applyField(new AbilityContext(player, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6), true));
			}
		});
	}

	private static void applyField(AbilityContext ctx) {
		int m = ctx.cycleMode() % 3; // 0 normal, 1 high, 2 low
		double grav = m == 1 ? 0.6 : (m == 2 ? -0.5 : 0.0);
		PowerToggles.modifier(ctx.player(), Attributes.GRAVITY, GRAV, grav, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (m == 2) {
			ctx.player().resetFallDistance();
		}
	}
}
