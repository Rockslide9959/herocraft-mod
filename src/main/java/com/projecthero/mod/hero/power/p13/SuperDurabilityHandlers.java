package com.projecthero.mod.hero.power.p13;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.power.AbilityHelpers;
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
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Power 13 — Super Durability.
 *
 * <p>All of this power's mitigation is applied as explicit multiplicative factors in
 * {@link com.projecthero.mod.hero.power.HeroDamageRules} rather than vanilla Resistance, so the
 * pieces stack predictably:
 * <ul>
 *   <li>passive: 30% less damage from every source</li>
 *   <li>{@code tank_mode}: an additional 30% less</li>
 *   <li>{@code block}: an additional 60% less from the frontal 180° arc</li>
 *   <li>{@code unbreakable}: 100% of all damage for 15s</li>
 *   <li>{@code projectile_deflection}: full projectile immunity while held</li>
 * </ul>
 *
 * <p>{@code block} and {@code projectile_deflection} are both HOLD abilities that drain the same
 * shared {@code guard} stamina bar (0..100); it refills while neither is held.
 */
public final class SuperDurabilityHandlers {
	private static final String KEY = "power_13_super_durability";
	private static final net.minecraft.resources.ResourceLocation TANK_KB = com.projecthero.mod.ProjectHeroMod.id("tank_kb");
	private static final net.minecraft.resources.ResourceLocation TANK_SPD = com.projecthero.mod.ProjectHeroMod.id("tank_slow");
	private static final net.minecraft.resources.ResourceLocation BLOCK_KB = com.projecthero.mod.ProjectHeroMod.id("block_kb");
	private static final net.minecraft.resources.ResourceLocation UNBREAK_KB = com.projecthero.mod.ProjectHeroMod.id("unbreakable_kb");
	private static final net.minecraft.resources.ResourceLocation PASSIVE_KB = com.projecthero.mod.ProjectHeroMod.id("durability_passive_kb");

	public static final String GUARD = "guard";
	public static final float GUARD_MAX = 500.0f;
	private static final float GUARD_DRAIN = 1.0f;
	private static final float GUARD_REGEN = 2.0f;

	private SuperDurabilityHandlers() {
	}

	public static void register() {
		AbilityHandlers.register(KEY, "heavy_strike", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 4.5);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 8.0f);
				AbilityHelpers.knockbackFrom(t, p.position(), 0.8);
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 3);
			}
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "shoulder_charge", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.launchSelf(p, p.getLookAngle().scale(1.6).add(0, 0.1, 0));
			ctx.setResource("charging", 12, 12);
			AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.7f, 1.2f);
			ctx.triggerCooldown();
		}, ctx -> {
			int t = (int) ctx.resource("charging");
			if (t <= 0) {
				return;
			}
			ctx.setResource("charging", t - 1, 12);
			ServerPlayer p = ctx.player();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 1.6)) {
				AbilityHelpers.hurt(p, e, 8.0f);
				AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
			}
		}));

		AbilityHandlers.register(KEY, "block", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource(GUARD) <= 0.0f) {
					ctx.actionBar("message.projecthero.durability.guard_spent");
					return;
				}
				PowerToggles.modifier(ctx.player(), Attributes.KNOCKBACK_RESISTANCE, BLOCK_KB, 1.0,
						AttributeModifier.Operation.ADD_VALUE);
				ctx.setResource("blocking", 1, 1);
				AbilityHelpers.sound(ctx.player(), SoundEvents.SHIELD_BLOCK, 1.0f, 0.8f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				stopBlocking(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("blocking") > 0.5f) {
					ctx.addResource(GUARD, -GUARD_DRAIN, GUARD_MAX);
					// Blocking now also throws back projectiles, with the same visible/audible feedback
					// Projectile Deflection has.
					deflectAndSparkle(ctx);
					if (ctx.resource(GUARD) <= 0.0f) {
						stopBlocking(ctx);
						ctx.actionBar("message.projecthero.durability.guard_spent");
					}
				}
			}
		});

		AbilityHandlers.register(KEY, "unbreakable", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			// 15 seconds of total damage negation (see HeroDamageRules).
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 4, false, true, true));
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, UNBREAK_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
			ctx.setResource("unbreakable_until", p.level().getGameTime() + 300, 1e12f);
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.ENCHANTED_HIT, 30, 0.5);
			AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.8f, 1.4f);
			ctx.triggerCooldown();
		}, ctx -> {
			float until = ctx.resource("unbreakable_until");
			if (until > 0 && until <= ctx.player().level().getGameTime()) {
				PowerToggles.clearModifier(ctx.player(), Attributes.KNOCKBACK_RESISTANCE, UNBREAK_KB);
				ctx.setResource("unbreakable_until", 0, 1e12f);
			}
		}));

		AbilityHandlers.register(KEY, "projectile_deflection", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource(GUARD) <= 0.0f) {
					ctx.actionBar("message.projecthero.durability.guard_spent");
					return;
				}
				ctx.setResource("deflecting", 1, 1);
				AbilityHelpers.sound(ctx.player(), SoundEvents.SHIELD_BLOCK, 1.0f, 0.8f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("deflecting", 0, 1);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("deflecting") <= 0.5f) {
					return;
				}
				ctx.addResource(GUARD, -GUARD_DRAIN, GUARD_MAX);
				if (ctx.resource(GUARD) <= 0.0f) {
					ctx.setResource("deflecting", 0, 1);
					ctx.actionBar("message.projecthero.durability.guard_spent");
					return;
				}
				deflectAndSparkle(ctx);
			}
		});

		AbilityHandlers.register(KEY, "tank_mode", Handlers.toggle(
				SuperDurabilityHandlers::tankOn, SuperDurabilityHandlers::tankOff, ctx -> {
					tankOn(ctx);
					AbilityHelpers.modeAura(ctx.player(), new net.minecraft.core.particles.BlockParticleOption(
							ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState()), 4);
				}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB, 0.3,
						AttributeModifier.Operation.ADD_VALUE);
				var s = com.projecthero.mod.hero.ExperimentalPowers.state(player);
				if (!s.resources.containsKey(KEY + "/" + GUARD)) {
					com.projecthero.mod.hero.ExperimentalPowers.setResource(player,
							com.projecthero.mod.hero.Powers.byKey(KEY), GUARD, GUARD_MAX, GUARD_MAX);
				}
			} else {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, BLOCK_KB);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, UNBREAK_KB);
				var power = com.projecthero.mod.hero.Powers.byKey(KEY);
				com.projecthero.mod.hero.ExperimentalPowers.setResource(player, power, "blocking", 0, 1);
				com.projecthero.mod.hero.ExperimentalPowers.setResource(player, power, "deflecting", 0, 1);
			}
		});

		// Refill the shared guard bar while neither block nor deflection is being held.
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			var power = com.projecthero.mod.hero.Powers.byKey(KEY);
			float guard = com.projecthero.mod.hero.ExperimentalPowers.getResource(player, power, GUARD);
			boolean guarding = com.projecthero.mod.hero.ExperimentalPowers.getResource(player, power, "blocking") > 0.5f
					|| com.projecthero.mod.hero.ExperimentalPowers.getResource(player, power, "deflecting") > 0.5f;
			if (!guarding && guard < GUARD_MAX) {
				com.projecthero.mod.hero.ExperimentalPowers.addResource(player, power, GUARD, GUARD_REGEN, GUARD_MAX);
			}
		});
	}

	/** Throw back nearby projectiles and show the block/deflect feedback (sparks + shield tick). */
	private static void deflectAndSparkle(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		for (Projectile proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.0))) {
			Vec3 away = proj.position().subtract(p.position()).normalize().scale(1.2);
			proj.setDeltaMovement(away);
			if (proj instanceof AbstractArrow arrow) {
				arrow.setOwner(p);
			}
		}
		if (p.tickCount % 6 == 0) {
			AbilityHelpers.burst(ctx.level(), p.position().add(0, 1, 0), ParticleTypes.CRIT, 6, 0.5);
		}
	}

	private static void stopBlocking(AbilityContext ctx) {
		PowerToggles.clearModifier(ctx.player(), Attributes.KNOCKBACK_RESISTANCE, BLOCK_KB);
		ctx.setResource("blocking", 0, 1);
	}

	private static void tankOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, TANK_KB, 0.8, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, TANK_SPD, -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void tankOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, TANK_KB);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, TANK_SPD);
	}
}
