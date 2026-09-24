package com.projecthero.mod.hero.power.p12;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;

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
 * Power 12 — Super Regeneration (formerly "Healing Factor"; fully renamed to Super Regeneration
 * in v0.9.22, key {@code power_12_super_regeneration}).
 *
 * <p>Regeneration on this power is fast and runs on a quarter-second cadence: a base 0.5 HP every
 * 0.25 s (2 HP/s), with {@link #regeneration_mode} and Cellular Surge each stacking another 0.5 HP
 * every 0.25 s -- all three at once is 6 HP/s, enough to outheal most sustained damage. The Ultimate
 * slot is {@link #resurrection}: arm it, and the next lethal hit triggers the exact totem-of-undying
 * death protection, after which it goes on a one-minute cooldown.
 */
public final class SuperRegenerationHandlers {
	private static final String KEY = "power_12_super_regeneration";
	private static final int HEAL_INTERVAL = 5;
	/** Base passive heal per {@link #HEAL_INTERVAL} while recently hurt / in combat. */
	private static final float BASE_HEAL_COMBAT = 0.5f;
	/** Base passive heal per interval out of combat, or any time health is at/below {@link #DESPERATE_HP}. */
	private static final float BASE_HEAL_CALM = 1.0f;
	/** Each of Regeneration Mode and Cellular Surge adds this per interval on top of the base. */
	private static final float MODE_HEAL = 1.0f;
	/** 4 hearts. At or below this, the base heal jumps to the calm rate regardless of combat. */
	private static final float DESPERATE_HP = 8.0f;
	/** How long after taking or dealing damage the player counts as "in combat" (3 s). */
	private static final int COMBAT_TICKS = 60;

	/** Mark the Super Regeneration owner as in combat (called from the damage listeners). */
	public static void markCombat(ServerPlayer player) {
		Power power = Powers.byKey(KEY);
		if (power != null && ExperimentalPowers.owns(player, power)) {
			ExperimentalPowers.setResource(player, power, "combat_until",
					player.level().getGameTime() + COMBAT_TICKS, 1e12f);
		}
	}

	private static boolean inCombat(ServerPlayer player) {
		Power power = Powers.byKey(KEY);
		return power != null
				&& ExperimentalPowers.getResource(player, power, "combat_until") > player.level().getGameTime();
	}

	private SuperRegenerationHandlers() {
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
			ctx.actionBar(ready ? "message.projecthero.heal.resurrection_ready"
					: "message.projecthero.heal.resurrection_cooldown",
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
				p.heal(MODE_HEAL);
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
				p.heal(MODE_HEAL);
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

		// Base passive regeneration, every 0.25 s. Rate depends on the situation:
		//   - recently hurt / in combat: 0.5 HP per interval
		//   - 3 s out of combat, or at/below 4 hearts: 1 HP per interval
		// Stacks with Regeneration Mode and Cellular Surge above.
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> tickBaseRegen(player, 1.0f, false));

		// "In combat" = the Super Regeneration owner took or dealt damage in the last 3 s.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (entity instanceof ServerPlayer hurt) {
				markCombat(hurt);
			}
			if (source.getEntity() instanceof ServerPlayer attacker) {
				markCombat(attacker);
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
					com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(z.cooldownTicks()));
			return false; // death cancelled
		});
	}

	/**
	 * The base passive heal, factored out so an ascension can reuse and scale it instead of duplicating it.
	 * Wolverine (v0.12.1) calls this with a 1x / 2x / 3x multiplier by health tier and
	 * {@code ignoreCombat = true} (his healing factor never slows down mid-fight).
	 */
	public static void tickBaseRegen(ServerPlayer player, float multiplier, boolean ignoreCombat) {
		if (player.getAbilities().instabuild || player.tickCount % HEAL_INTERVAL != 0) {
			return;
		}
		if (player.getHealth() < player.getMaxHealth()) {
			boolean desperate = player.getHealth() <= DESPERATE_HP;
			float heal = (!ignoreCombat && inCombat(player) && !desperate) ? BASE_HEAL_COMBAT : BASE_HEAL_CALM;
			player.heal(heal * multiplier);
		}
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
