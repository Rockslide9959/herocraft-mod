package com.herocraft.mod.hero.power.p12;

import com.herocraft.mod.hero.Ability;
import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.AbilitySlot;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Items;

/**
 * Power 12 — Super Regeneration (formerly "Healing Factor"; the internal power key
 * {@code power_12_healing_factor} and this class name are kept for save/asset compatibility).
 *
 * <p>Regeneration on this power is fast and runs on a quarter-second cadence: a base 0.5 HP every
 * 0.25 s (2 HP/s), with {@link #regeneration_mode} and Cellular Surge each stacking another 0.5 HP
 * every 0.25 s -- all three at once is 6 HP/s, enough to outheal most sustained damage. The Ultimate
 * slot is {@link #resurrection}: arm it, and the next lethal hit triggers the exact totem-of-undying
 * death protection, after which it goes on a one-minute cooldown.
 */
public final class HealingFactorHandlers {
	private static final String KEY = "power_12_healing_factor";
	/** Half a heart every quarter second (v0.9.6). */
	private static final float TICK_HEAL = 0.5f;
	private static final int HEAL_INTERVAL = 5;

	private HealingFactorHandlers() {
	}

	public static void register() {
		AbilityHandlers.register(KEY, "rapid_heal", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			p.heal(6.0f);
			particles(ctx);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 2.0f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "purge", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			for (var effect : new java.util.ArrayList<>(p.getActiveEffects())) {
				if (!effect.getEffect().value().isBeneficial()) {
					p.removeEffect(effect.getEffect());
				}
			}
			particles(ctx);
			AbilityHelpers.sound(p, SoundEvents.BREWING_STAND_BREW, 0.8f, 1.5f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "recovery_burst", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, true, true));
			particles(ctx);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.4f);
			ctx.triggerCooldown();
		}));

		// Resurrection is automatic: whenever the player dies while it is off cooldown, they are
		// brought back with the vanilla totem-of-undying rescue and a one-minute cooldown begins.
		// Pressing the key just explains that.
		AbilityHandlers.register(KEY, "resurrection", Handlers.instant(ctx -> {
			boolean ready = ctx.cooldownReady();
			ctx.actionBar(ready ? "message.herocraft.heal.resurrection_ready"
					: "message.herocraft.heal.resurrection_cooldown",
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
		}));

		// Cellular Surge: an extra 0.5 HP / 0.25 s for 30 s on top of everything else, plus mobility.
		AbilityHandlers.register(KEY, "cellular_surge", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			ctx.setResource("surge_until", p.level().getGameTime() + 600, 1e12f);
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0, false, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 600, 0, false, true, true));
			particles(ctx);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.7f);
			ctx.triggerCooldown();
		}, ctx -> {
			ServerPlayer p = ctx.player();
			if (ctx.resource("surge_until") <= p.level().getGameTime() || p.tickCount % HEAL_INTERVAL != 0) {
				return;
			}
			if (p.getHealth() < p.getMaxHealth()) {
				p.heal(TICK_HEAL);
				ctx.level().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1, p.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
			}
		}));

		// Regeneration Mode: an extra 0.5 HP / 0.25 s while toggled, at the cost of saturation. The only
		// Healing Factor ability that touches hunger. Emits a heart aura to show it is active.
		AbilityHandlers.register(KEY, "regeneration_mode", Handlers.toggle(Handlers.noop(), Handlers.noop(), ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.modeAura(p, ParticleTypes.HEART, 1);
			if (p.getAbilities().instabuild) {
				return;
			}
			if (p.tickCount % HEAL_INTERVAL == 0 && p.getHealth() < p.getMaxHealth()) {
				p.heal(TICK_HEAL);
			}
			if (p.tickCount % 100 == 0) {
				FoodData food = p.getFoodData();
				float sat = food.getSaturationLevel();
				if (sat > 0.0f) {
					food.setSaturation(Math.max(0.0f, sat - 2.0f));
				} else {
					food.setFoodLevel(Math.max(0, food.getFoodLevel() - 1));
				}
			}
		}));

		// Base passive regeneration: 0.5 HP every 0.25 s, no strings attached. Stacks with the modes above.
		com.herocraft.mod.hero.PowerPassives.registerTick(KEY, player -> {
			if (player.getAbilities().instabuild || player.tickCount % HEAL_INTERVAL != 0) {
				return;
			}
			if (player.getHealth() < player.getMaxHealth()) {
				player.heal(TICK_HEAL);
			}
		});

		// Automatic Resurrection: cancel death whenever the Ultimate slot is off cooldown.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p)) {
				return true;
			}
			Power power = Powers.byKey(KEY);
			if (power == null || !ExperimentalPowers.owns(p, power)) {
				return true;
			}
			if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
					|| source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
				return true; // /kill and the void still kill you, like a totem
			}
			Ability z = power.ability(AbilitySlot.SLOT_4);
			if (!ExperimentalPowers.cooldownReady(p, power, z)) {
				return true;
			}
			resurrect(p);
			ExperimentalPowers.triggerCooldown(p, power, z,
					com.herocraft.mod.hero.HeroConfig.get().scaledCooldown(z.cooldownTicks()));
			return false; // death cancelled
		});
	}

	/** Exactly what a Totem of Undying does on a lethal hit. */
	private static void resurrect(ServerPlayer p) {
		p.setHealth(1.0f);
		p.removeAllEffects();
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
		p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
		p.level().broadcastEntityEvent(p, (byte) 35); // totem particle + sound on every client
		p.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
	}

	private static void particles(AbilityContext ctx) {
		ctx.level().sendParticles(ParticleTypes.HEART, ctx.player().getX(), ctx.player().getY() + 1.0,
				ctx.player().getZ(), 8, 0.3, 0.5, 0.3, 0.0);
	}
}
