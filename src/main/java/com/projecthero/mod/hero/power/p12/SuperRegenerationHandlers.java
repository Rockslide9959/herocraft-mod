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
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.batcha.BatchA;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Power 12 — Super Regeneration (v0.13.22 revamp: <b>Adrenaline</b>). Key {@code power_12_super_regeneration}
 * -- Wolverine ascends from this power via the Adamantium Serum, so the key and {@link #tickBaseRegen} are
 * load-bearing.
 *
 * <p>Taking damage fills a 0..115 Adrenaline gauge (4 per point of damage taken); it drains slowly once you are
 * out of combat. Adrenaline supercharges Rapid Heal, Adrenal Rush and Mend, and is the whole fuel of Blood Rage.
 *
 * <p>Passive: 4 HP/s regeneration that never slows down. R Rapid Heal, G Purge (never touches the unstable
 * mutation), X Adrenal Rush, Z Resurrection (automatic), V Cellular Surge, C Regeneration Mode, H Blood Rage,
 * N Mend (heals squad-mates, your pets and villagers around you).
 */
public final class SuperRegenerationHandlers {
	public static final String KEY = "power_12_super_regeneration";
	public static final String ADRENALINE = "adrenaline";
	public static final float MAX_ADRENALINE = 115f;
	private static final int HEAL_INTERVAL = 5;
	/** Base passive heal per interval (4 HP/s). */
	private static final float BASE_HEAL_CALM = 1.0f;
	/** Regeneration Mode adds this per interval on top of the base. */
	private static final float MODE_HEAL = 1.0f;
	/** Cellular Surge adds twice that on top (v0.12.16): +8 HP/s. */
	private static final float SURGE_HEAL = 2.0f;
	private static final int SURGE_TICKS = 600;
	private static final int RAGE_TICKS = 200;
	/** How long after taking or dealing damage the player counts as "in combat" (3 s). */
	private static final int COMBAT_TICKS = 60;
	private static final ResourceLocation RAGE_ATK = com.projecthero.mod.ProjectHeroMod.id("regen_blood_rage");

	/** Mark the Super Regeneration owner as in combat (called from the damage listeners). */
	public static void markCombat(ServerPlayer player) {
		Power power = Powers.byKey(KEY);
		if (power != null && ExperimentalPowers.owns(player, power)) {
			BatchA.set(player, KEY, "combat_left", COMBAT_TICKS);
		}
	}

	private static boolean inCombat(ServerPlayer player) {
		return BatchA.res(player, KEY, "combat_left") > 0.5f;
	}

	private SuperRegenerationHandlers() {
	}

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v, 1e9f);
	}

	public static float adrenaline(ServerPlayer p) {
		return res(p, ADRENALINE);
	}

	/** Spends {@code amount} Adrenaline if there is that much; returns whether it did. */
	private static boolean spendAdrenaline(ServerPlayer p, float amount) {
		float a = adrenaline(p);
		if (a < amount) {
			return false;
		}
		set(p, ADRENALINE, a - amount);
		return true;
	}

	/** Adrenaline gained from a hit taken (also used by the gametests). */
	public static void gainAdrenaline(ServerPlayer p, float damageTaken) {
		if (!ExperimentalPowers.owns(p, KEY) || damageTaken <= 0f) {
			return;
		}
		BatchA.set(p, KEY, ADRENALINE, Math.min(MAX_ADRENALINE, adrenaline(p) + damageTaken * 4f), MAX_ADRENALINE);
	}

	public static boolean bloodRaging(ServerPlayer p) {
		return res(p, "rage_left") > 0.5f;
	}

	public static void register() {
		// R -- Rapid Heal: a burst of health; 20 Adrenaline adds another 6.
		AbilityHandlers.register(KEY, "rapid_heal", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float heal = 7.2f;
			if (spendAdrenaline(p, 20f)) {
				heal += 6f;
				ctx.level().sendParticles(ParticleTypes.CRIMSON_SPORE, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.5, 0.4, 0.02);
			}
			p.heal(heal);
			particles(ctx);
			BatchA.play(p, KEY, "p12.heal", 16);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 2.0f);
			ctx.triggerCooldown();
		}));

		// G -- Purge: burn every harmful effect out of your blood (the unstable mutation is not a poison -- it stays).
		AbilityHandlers.register(KEY, "purge", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			purgeHarmful(p);
			particles(ctx);
			ctx.level().sendParticles(ParticleTypes.EFFECT, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
			BatchA.play(p, KEY, "p12.purge", 16);
			AbilityHelpers.sound(p, SoundEvents.BREWING_STAND_BREW, 0.8f, 1.5f);
			ctx.triggerCooldown();
		}));

		// X -- Adrenal Rush: a lunge forward and 6 s of speed and jump; 25 Adrenaline makes it Speed III + Resistance.
		AbilityHandlers.register(KEY, "adrenal_rush", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			boolean pumped = spendAdrenaline(p, 25f);
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, pumped ? 2 : 1, false, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.JUMP, 120, 1, false, true, true));
			if (pumped) {
				p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0, false, true, true));
			}
			Vec3 f = BatchA.flatLook(p);
			AbilityHelpers.addImpulse(p, new Vec3(f.x * 1.1, 0.45, f.z * 1.1));
			ServerLevel level = ctx.level();
			level.sendParticles(pumped ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(),
					24, 0.4, 0.2, 0.4, 0.05);
			BatchA.play(p, KEY, "dash_forward", 12);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_BREATH, 1.0f, 0.8f);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 1.4f);
			ctx.triggerCooldown();
		}));

		// Z -- Resurrection is automatic: whenever the player dies while it is off cooldown, they are brought back
		// with the vanilla totem-of-undying rescue. Pressing the key just explains that.
		AbilityHandlers.register(KEY, "resurrection", Handlers.instant(ctx -> {
			boolean ready = ctx.cooldownReady();
			ctx.actionBar(ready ? "message.projecthero.heal.resurrection_ready"
					: "message.projecthero.heal.resurrection_cooldown",
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
		}));

		// V -- Cellular Surge: an extra 2 HP / 0.25 s for 30 s on top of everything else, plus mobility.
		AbilityHandlers.register(KEY, "cellular_surge", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			set(p, "surge_left", SURGE_TICKS);
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SURGE_TICKS, 0, false, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, SURGE_TICKS, 0, false, true, true));
			particles(ctx);
			BatchA.play(p, KEY, "power_up", 20);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.7f);
			ctx.triggerCooldown();
		}, ctx -> {
			ServerPlayer p = ctx.player();
			float left = BatchA.countDown(p, KEY, "surge_left");
			if (left <= 0f || p.tickCount % HEAL_INTERVAL != 0) {
				return;
			}
			if (p.getHealth() < p.getMaxHealth()) {
				p.heal(SURGE_HEAL);
				ctx.level().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1, p.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
			}
		}));

		// C -- Regeneration Mode: an extra 1 HP / 0.25 s while toggled, at the cost of saturation.
		AbilityHandlers.register(KEY, "regeneration_mode", Handlers.toggle(
				ctx -> BatchA.play(ctx.player(), KEY, "flex", 16), Handlers.noop(), ctx -> {
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

		// H -- Blood Rage: pour all your Adrenaline (40 minimum) into 10 s of fury -- melee +25%, rising to +100% the
		// closer you are to death.
		AbilityHandlers.register(KEY, "blood_rage", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (adrenaline(p) < 40f) {
				ctx.actionBar("message.projecthero.regen.no_adrenaline", 40);
				return;
			}
			set(p, ADRENALINE, 0);
			set(p, "rage_left", RAGE_TICKS);
			applyRage(p);
			ServerLevel level = ctx.level();
			level.sendParticles(ParticleTypes.CRIMSON_SPORE, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.7, 0.5, 0.05);
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 2.1, p.getZ(), 3, 0.3, 0.1, 0.3, 0);
			BatchA.play(p, KEY, "p12.roar", 22);
			AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 1.0f, 1.2f);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.2f, 1.0f);
			ctx.triggerCooldown();
		}));

		// N -- Mend: close the wounds of everyone you protect within 8 blocks.
		AbilityHandlers.register(KEY, "mend", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			java.util.List<LivingEntity> allies = AbilityHelpers.living(level, p.position(), 8.0, e -> BatchA.isAlly(p, e));
			if (allies.isEmpty()) {
				ctx.actionBar("message.projecthero.regen.no_allies");
				return;
			}
			boolean pumped = spendAdrenaline(p, 20f);
			for (LivingEntity e : allies) {
				e.heal(pumped ? 12f : 8f);
				if (pumped) {
					e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, true, true));
				}
				AbilityHelpers.line(level, p.position().add(0, 1.2, 0), e.position().add(0, e.getBbHeight() * 0.6, 0),
						ParticleTypes.HAPPY_VILLAGER, 2.0);
				level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 5, 0.3, 0.3, 0.3, 0);
			}
			BatchA.play(p, KEY, "cast_two_hand", 16);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.8f);
			ctx.triggerCooldown();
		}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, RAGE_ATK);
				set(player, "rage_left", 0);
				set(player, "surge_left", 0);
			}
		});

		// Base passive regeneration (4 HP/s) and the Adrenaline / Blood Rage upkeep.
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			tickBaseRegen(player, 1.0f, false);
			BatchA.countDown(player, KEY, "combat_left");
			float a = adrenaline(player);
			if (a > 0f && !inCombat(player)) {
				set(player, ADRENALINE, Math.max(0f, a - 0.15f));
			}
			float rage = res(player, "rage_left");
			if (rage > 0.5f) {
				set(player, "rage_left", rage - 1);
				if (rage - 1 <= 0.5f) {
					PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, RAGE_ATK);
				} else if (player.tickCount % 10 == 0) {
					applyRage(player);
				}
				if (player.tickCount % 4 == 0 && player.level() instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1, player.getZ(), 2, 0.3, 0.5, 0.3, 0.0);
				}
			}
		});

		// "In combat" = the owner took or dealt damage in the last 3 s. Damage taken also fills Adrenaline.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (entity instanceof ServerPlayer hurt) {
				markCombat(hurt);
				gainAdrenaline(hurt, dealtAmount);
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

	/** Melee bonus: +25%, plus the fraction of health you are missing, capped at +100%. */
	private static void applyRage(ServerPlayer p) {
		float missing = 1f - p.getHealth() / Math.max(1f, p.getMaxHealth());
		double bonus = Math.min(1.0, 0.25 + missing);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, RAGE_ATK, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Removes every harmful effect except the unstable mutation (acquisition owns that one). */
	public static void purgeHarmful(ServerPlayer p) {
		for (var effect : new java.util.ArrayList<>(p.getActiveEffects())) {
			if (effect.getEffect().value().isBeneficial()) {
				continue;
			}
			if (effect.getEffect().value() == com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION.value()) {
				continue;
			}
			p.removeEffect(effect.getEffect());
		}
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
			// v0.12.25: the heal never slows down -- combat or not, it is always the full rate
			player.heal(BASE_HEAL_CALM * multiplier);
		}
	}

	/** Exactly what a Totem of Undying does on a lethal hit -- except the unstable mutation survives it. */
	private static void resurrect(ServerPlayer p) {
		MobEffectInstance mutation = p.getEffect(com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION);
		p.setHealth(1.0f);
		p.removeAllEffects();
		if (mutation != null) {
			p.addEffect(new MobEffectInstance(mutation));
		}
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
		p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
		p.level().broadcastEntityEvent(p, (byte) 35); // totem particle + sound on every client
		p.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
		BatchA.set(p, KEY, ADRENALINE, MAX_ADRENALINE, MAX_ADRENALINE); // coming back from the dead is quite a rush
		BatchA.play(p, KEY, "p12.roar", 22);
	}

	private static void particles(AbilityContext ctx) {
		ctx.level().sendParticles(ParticleTypes.HEART, ctx.player().getX(), ctx.player().getY() + 1.0,
				ctx.player().getZ(), 8, 0.3, 0.5, 0.3, 0.0);
	}
}
