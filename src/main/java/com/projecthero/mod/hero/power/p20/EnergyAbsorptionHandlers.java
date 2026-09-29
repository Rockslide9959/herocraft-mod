package com.projecthero.mod.hero.power.p20;

import java.util.Set;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.BatchCFx;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 20 -- Energy Absorption (v0.13.22 revamp).
 *
 * <h2>Signature: you release what you absorbed</h2>
 * Fire, lightning, explosion and magic damage are soaked (60%, 80% in Absorption Mode) into the <b>Energy</b> meter
 * ({@value #MAX}) <em>and set its element</em>; ordinary physical hits only soak 25% (40% in the mode) and leave the
 * element as it was. Every blast then comes out as that element: fire ignites, lightning arcs to a second target and
 * stuns, explosion blows the target away, magic withers and weakens. The HUD bar and the Absorption Mode shell are
 * colour-coded (fire orange, lightning blue, explosion yellow, magic purple, raw cyan).
 */
public final class EnergyAbsorptionHandlers {
	public static final String KEY = "power_20_energy_absorption";
	public static final String METER = "energy";
	/** +15% over the pre-revamp 500. */
	public static final float MAX = 575.0f;

	public static final int RAW = 0;
	public static final int FIRE = 1;
	public static final int LIGHTNING = 2;
	public static final int EXPLOSION = 3;
	public static final int MAGIC = 4;
	/** RGB per element: raw cyan, fire orange, lightning blue, explosion yellow, magic purple. */
	public static final int[] ELEMENT_RGB = { 0x8CD9FF, 0xFF8A2A, 0x4FA8FF, 0xFFE040, 0xB060FF };

	public static final float BLAST_COST = 10.0f;
	public static final float BLAST_DAMAGE = 16.0f;
	private static final float BEAM_DAMAGE = 11.0f;
	private static final float BURST_DAMAGE = 18.0f;
	private static final float DASH_DAMAGE = 7.0f;
	public static final float OVERLOAD_MIN = 100.0f;
	private static final int OVERLOAD_CHARGE = 70;
	public static final float REDIRECT_COST = 25.0f;
	private static final int REDIRECT_TICKS = 60;
	public static final float EMPOWER_COST = 100.0f;
	private static final int EMPOWER_TICKS = 240;
	private static final float MODE_DRAIN_PER_SECOND = 7.0f;
	private static final float MODE_ABILITY_BONUS = 10.0f;

	private static final net.minecraft.resources.ResourceLocation SUPER_ATK =
			com.projecthero.mod.ProjectHeroMod.id("energy_supercharged_atk");

	private EnergyAbsorptionHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	// ---- element + state -------------------------------------------------------------------

	public static int element(ServerPlayer p) {
		int el = Math.round(BatchCFx.resourceQuick(p, KEY, "element"));
		return el < RAW || el > MAGIC ? RAW : el;
	}

	public static void setElement(ServerPlayer p, int el) {
		Power power = power();
		if (power != null && element(p) != el) {
			ExperimentalPowers.setResource(p, power, "element", el, MAGIC);
		}
	}

	/** Which element a damage source carries, or -1 for plain physical damage. */
	public static int elementOf(DamageSource source) {
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			return LIGHTNING;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return FIRE;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return EXPLOSION;
		}
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)
				|| source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.SONIC_BOOM)) {
			return MAGIC;
		}
		return -1;
	}

	public static boolean modeActive(ServerPlayer p) {
		return BatchCFx.toggledQuick(p, KEY, "absorption_mode");
	}

	/** +10 ability damage while Absorption Mode is on. */
	public static float superchargedBonus(ServerPlayer p) {
		return modeActive(p) ? MODE_ABILITY_BONUS : 0.0f;
	}

	/** False while the meter is locked out (kept for older saves: nothing in the new kit locks it). */
	public static boolean canAbsorb(ServerPlayer p) {
		var power = power();
		return power == null || ExperimentalPowers.getResource(p, power, "absorb_lock_until") <= p.level().getGameTime();
	}

	public static void markAbsorbed(ServerPlayer p) {
		var power = power();
		if (power != null) {
			ExperimentalPowers.setResource(p, power, "last_absorb", p.level().getGameTime(), 1.0e12f);
		}
	}

	/**
	 * The passive soak, called from {@code HeroDamageRules} for every hit this player takes. Stores the soaked share as
	 * energy (and, for an elemental hit, switches the meter to that element) and returns the damage factor that still
	 * lands.
	 */
	public static float absorb(ServerPlayer p, DamageSource source, float amount) {
		Power power = power();
		if (power == null || !canAbsorb(p)) {
			return 1.0f;
		}
		int el = elementOf(source);
		boolean mode = modeActive(p);
		float soak = el >= 0 ? (mode ? 0.8f : 0.6f) : (mode ? 0.4f : 0.25f);
		ExperimentalPowers.addResource(p, power, METER, amount * soak, MAX);
		if (el > RAW) {
			setElement(p, el);
		}
		markAbsorbed(p);
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(dust(elementColour(el > RAW ? el : element(p)), 1.2f), p.getX(), p.getY() + 1.0, p.getZ(),
					8, 0.35, 0.6, 0.35, 0.02);
		}
		return 1.0f - soak;
	}

	private static int elementColour(int el) {
		return ELEMENT_RGB[Math.max(0, Math.min(MAGIC, el))];
	}

	private static ParticleOptions dust(int rgb, float size) {
		return BatchCFx.dust(rgb, size);
	}

	private static ParticleOptions elDust(ServerPlayer p, float size) {
		return dust(elementColour(element(p)), size);
	}

	/** A hand-fired origin point so beams never spawn right in front of the caster's eyes. */
	private static Vec3 handOrigin(ServerPlayer p) {
		return AbilityHelpers.handPosition(p);
	}

	// ---- element effects -------------------------------------------------------------------

	private static DamageSource sourceFor(ServerPlayer p, int el) {
		return switch (el) {
			case FIRE -> AbilityHelpers.fire(p);
			case MAGIC -> p.damageSources().indirectMagic(p, p);
			default -> AbilityHelpers.kinetic(p);
		};
	}

	/** One elemental hit: damage of the meter's element plus its rider effect. Returns true if the damage landed. */
	public static boolean elementalHit(ServerPlayer p, LivingEntity t, float dmg, boolean burst) {
		int el = element(p);
		DamageSource src = sourceFor(p, el);
		boolean hit = burst ? AbilityHelpers.hurtBurst(p, t, src, dmg) : AbilityHelpers.hurt(p, t, src, dmg);
		applyElement(p, t, dmg, el);
		return hit;
	}

	private static void applyElement(ServerPlayer p, LivingEntity t, float dmg, int el) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
		switch (el) {
			case FIRE -> {
				t.igniteForSeconds(4.0f);
				level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.03);
			}
			case LIGHTNING -> {
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 20, 6);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 14, 0.3, 0.4, 0.3, 0.2);
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, t.position(), 5.0)) {
					if (e != t) {
						AbilityHelpers.hurt(p, e, AbilityHelpers.kinetic(p), dmg * 0.5f);
						BatchCFx.arc(level, c, e.position().add(0, e.getBbHeight() * 0.5, 0), ParticleTypes.ELECTRIC_SPARK, 0.5);
						break;
					}
				}
			}
			case EXPLOSION -> {
				AbilityHelpers.knockbackFrom(t, p.position(), 1.6);
				AbilityHelpers.push(t, new Vec3(0, 0.35, 0));
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			}
			case MAGIC -> {
				AbilityHelpers.applyControl(t, MobEffects.WITHER, 60, 0);
				AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, 80, 0);
				level.sendParticles(ParticleTypes.WITCH, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.02);
			}
			default -> {
			}
		}
	}

	// ---- registration -----------------------------------------------------------------------

	public static void register() {
		// R -- Energy Blast. Hold to charge (max 3 s): +6 damage / +1.7 s cooldown per second held.
		AbilityHandlers.register(KEY, "energy_blast", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				if (!ctx.spendResource(METER, BLAST_COST)) {
					ctx.actionBar("message.projecthero.energy.empty");
					return;
				}
				ctx.setResource("blast_charging", 1, 1);
				ctx.setResource("blast_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("blast_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("blast_charge_start");
				double seconds = Math.min(3.0, held / 20.0);
				ctx.setResource("blast_charging", 0, 1);
				ctx.setResource("blast_charge", 0, 100);
				ServerLevel level = ctx.level();
				LivingEntity t = AbilityHelpers.raycastEntity(p, 26.0);
				Vec3 end = AbilityHelpers.aimPoint(p, 26.0);
				AbilityHelpers.line(level, handOrigin(p), end, elDust(p, 1.4f), 3.0);
				AbilityHelpers.line(level, handOrigin(p), end, ParticleTypes.END_ROD, 1.0);
				if (t != null) {
					elementalHit(p, t, BLAST_DAMAGE + (float) (seconds * 6.0) + superchargedBonus(p), false);
					AbilityHelpers.knockbackFrom(t, p.position(), 0.6);
				}
				MutationVisuals.play(p, "cast_right");
				AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.4f);
				ctx.triggerCooldown((int) Math.round(34 + seconds * 34));
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("blast_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("blast_charge_start");
				MutationVisuals.ensure(p, "channel_right");
				if (held % 3 == 0) {
					ctx.setResource("blast_charge", (float) Math.min(100.0, held / (3.0 * 20) * 100.0), 100);
					Vec3 at = handOrigin(p);
					ctx.level().sendParticles(elDust(p, 1.6f), at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.01);
				}
			}
		});

		// G -- Energy Beam (hold). Shift: a 9-block burst around you instead.
		AbilityHandlers.register(KEY, "energy_beam", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (!ctx.cooldownReady()) {
						onCooldownMessage(ctx);
						return;
					}
					if (!ctx.spendResource(METER, 50.0f)) {
						ctx.actionBar("message.projecthero.energy.empty");
						return;
					}
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 9.0)) {
						elementalHit(p, e, BURST_DAMAGE + superchargedBonus(p), false);
						AbilityHelpers.knockbackFrom(e, p.position(), 1.0);
					}
					for (int i = 1; i <= 3; i++) {
						BatchCFx.flatRing(ctx.level(), p.position().add(0, 1.0, 0), i * 0.8, 20 + i * 8, elDust(p, 1.8f), 0.4 + 0.2 * i);
					}
					MutationVisuals.play(p, "power_up");
					AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.8f);
					ctx.triggerCooldown(17 * 20);
					return;
				}
				if (ctx.resource(METER) < 1.0f) {
					ctx.actionBar("message.projecthero.energy.empty");
					return;
				}
				ctx.setResource("beaming", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("beaming") > 0.5f) {
					ctx.setResource("beaming", 0, 1);
					MutationVisuals.stopIf(ctx.player(), "channel_two_hand");
					ctx.triggerCooldown(85);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("beaming") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (!ctx.spendResource(METER, 1.0f)) { // 20/s
					ctx.setResource("beaming", 0, 1);
					MutationVisuals.stopIf(p, "channel_two_hand");
					ctx.triggerCooldown(85);
					return;
				}
				MutationVisuals.ensure(p, "channel_two_hand");
				LivingEntity t = AbilityHelpers.raycastEntity(p, 22.0);
				Vec3 end = AbilityHelpers.aimPoint(p, 22.0);
				AbilityHelpers.line(ctx.level(), handOrigin(p), end, elDust(p, 1.2f), 2.5);
				if (p.tickCount % 2 == 0) {
					AbilityHelpers.line(ctx.level(), handOrigin(p), end, ParticleTypes.END_ROD, 0.6);
				}
				if (t != null) {
					if (p.tickCount % 10 == 0) {
						elementalHit(p, t, BEAM_DAMAGE + superchargedBonus(p), false);
					} else {
						AbilityHelpers.hurt(p, t, BEAM_DAMAGE + superchargedBonus(p));
					}
				}
			}
		});

		// X -- Energy Dash (id kept from the old slot so saves keep their cooldown).
		AbilityHandlers.register(KEY, "absorption_shield", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (!ctx.spendResource(METER, 5.0f)) {
				ctx.actionBar("message.projecthero.energy.empty");
				return;
			}
			Vec3 start = p.position();
			Vec3 look = p.getLookAngle();
			AbilityHelpers.launchSelf(p, look.scale(1.8).add(0, 0.1, 0));
			ServerLevel level = ctx.level();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, start.add(look.scale(3.0)), 3.5)) {
				elementalHit(p, e, DASH_DAMAGE, false);
			}
			for (int i = 0; i < 12; i++) {
				Vec3 pt = start.add(0, 1, 0).add(look.scale(i * 0.5));
				level.sendParticles(elDust(p, 1.5f), pt.x, pt.y, pt.z, 2, 0.15, 0.15, 0.15, 0.0);
			}
			MutationVisuals.play(p, "dash_forward");
			AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.2f);
			ctx.triggerCooldown();
		}));

		// Z -- Overload: hold ~3.5 s holding a ball of your element overhead, then release the WHOLE meter at once.
		AbilityHandlers.register(KEY, "overload", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("pulse_charging") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				if (ctx.resource(METER) < OVERLOAD_MIN) {
					ctx.actionBar("message.projecthero.energy.overload_low");
					return;
				}
				ctx.setResource("pulse_charging", 1, 1);
				ctx.setResource("pulse_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("pulse_charging") > 0.5f) {
					ctx.setResource("pulse_charging", 0, 1);
					ctx.setResource("pulse_charge", 0, 100);
					MutationVisuals.stopIf(ctx.player(), "carry_overhead");
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("pulse_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				long held = p.level().getGameTime() - (long) ctx.resource("pulse_charge_start");
				MutationVisuals.ensure(p, "carry_overhead");
				if (held % 3 == 0) {
					ctx.setResource("pulse_charge", (float) Math.min(100.0, held / (double) OVERLOAD_CHARGE * 100.0), 100);
				}
				double frac = Math.min(1.0, held / (double) OVERLOAD_CHARGE);
				Vec3 ball = p.position().add(0, p.getBbHeight() + 0.9, 0);
				if (p.tickCount % 2 == 0) {
					level.sendParticles(elDust(p, (float) (1.5 + frac * 2.0)), ball.x, ball.y, ball.z,
							4 + (int) (frac * 8), 0.2 + frac * 0.4, 0.2 + frac * 0.4, 0.2 + frac * 0.4, 0.01);
					BatchCFx.inwardSpiral(level, ball, 2.5 - frac, 8, elDust(p, 0.8f), 0.2, p.tickCount);
				}
				if (held >= OVERLOAD_CHARGE) {
					ctx.setResource("pulse_charging", 0, 1);
					ctx.setResource("pulse_charge", 0, 100);
					fireOverload(ctx);
				}
			}
		});

		// V -- Energy Drain (hold): siphon a creature (or a redstone source) for energy and a little health.
		// Shift + V: Energy Conversion -- 10 energy becomes 2 health every half second.
		AbilityHandlers.register(KEY, "energy_drain", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				ctx.setResource(ctx.player().isShiftKeyDown() ? "converting" : "draining", 1, 1);
				ctx.setResource("drain_ticks", 0, 1000);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endDrain(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				boolean converting = ctx.resource("converting") > 0.5f;
				boolean draining = ctx.resource("draining") > 0.5f;
				if (!converting && !draining) {
					return;
				}
				ServerPlayer p = ctx.player();
				long t = p.tickCount;
				MutationVisuals.ensure(p, converting ? "power_up" : "channel_right");
				if (t % 10 != 0) {
					return;
				}
				float ticks = ctx.resource("drain_ticks") + 10;
				ctx.setResource("drain_ticks", ticks, 1000);
				if (ticks > 120) {
					endDrain(ctx);
					return;
				}
				if (converting) {
					if (ctx.spendResource(METER, 10.0f)) {
						p.heal(2.0f);
						ctx.level().sendParticles(elDust(p, 1.6f), p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
						AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.4f, 1.8f);
					} else {
						endDrain(ctx);
					}
					return;
				}
				drainPulse(ctx);
			}
		});

		// C -- Absorption Mode: soak more of everything, +10 ability / +8 melee, speed and haste, a glowing
		// elemental shell. 7 energy/s.
		AbilityHandlers.register(KEY, "absorption_mode", Handlers.toggle(
				ctx -> {
					if (!ctx.cooldownReady()) {
						ctx.setToggled(false);
						onCooldownMessage(ctx);
						return;
					}
					if (ctx.resource(METER) < 10.0f) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.energy.empty");
						return;
					}
					PowerToggles.modifier(ctx.player(), Attributes.ATTACK_DAMAGE, SUPER_ATK, 8.0, AttributeModifier.Operation.ADD_VALUE);
					MutationVisuals.play(ctx.player(), "p20.open");
					AbilityHelpers.sound(ctx.player(), SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
				},
				ctx -> {
					PowerToggles.clearModifier(ctx.player(), Attributes.ATTACK_DAMAGE, SUPER_ATK);
					ctx.triggerCooldown(17 * 20);
				},
				ctx -> {
					ServerPlayer p = ctx.player();
					AbilityHelpers.modeAura(p, elDust(p, 1.5f), 3);
					if (p.tickCount % 10 == 0) {
						p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, false, false, false));
						p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 30, 1, false, false, false));
						PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, SUPER_ATK, 8.0, AttributeModifier.Operation.ADD_VALUE);
					}
					if (p.tickCount % 5 == 0 && !ctx.spendResource(METER, MODE_DRAIN_PER_SECOND / 4.0f)) {
						PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, SUPER_ATK);
						ctx.setToggled(false);
						ctx.triggerCooldown(17 * 20);
						ctx.actionBar("message.projecthero.energy.empty");
					}
				}));

		// H -- Redirect: for 3 s the next projectile or melee hit aimed at you is caught and fired back.
		AbilityHandlers.register(KEY, "redirect", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.spendResource(METER, REDIRECT_COST)) {
					ctx.actionBar("message.projecthero.energy.empty");
					return;
				}
				ServerPlayer p = ctx.player();
				ctx.setResource("redirect_until", p.level().getGameTime() + REDIRECT_TICKS, 1.0e12f);
				BatchCFx.ring(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(0.8)), p.getLookAngle(), 0.9, 20,
						elDust(p, 1.2f), 0);
				AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.7f);
				ctx.triggerCooldown();
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				float until = ctx.resource("redirect_until");
				if (until <= 0.5f) {
					return;
				}
				long now = p.level().getGameTime();
				if (now >= (long) until) {
					ctx.setResource("redirect_until", 0, 1.0e12f);
					MutationVisuals.stopIf(p, "guard");
					return;
				}
				MutationVisuals.ensure(p, "guard");
				if (p.tickCount % 3 == 0) {
					BatchCFx.ring(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.3, 0),
							p.getLookAngle(), 0.7 + 0.1 * (p.tickCount % 6), 14, elDust(p, 0.8f), 0);
				}
				catchProjectile(ctx);
			}
		});

		// N -- Empower: spend 100 energy on a 12 s buff that depends on the stored element.
		AbilityHandlers.register(KEY, "empower", Handlers.instant(ctx -> {
			if (!ctx.spendResource(METER, EMPOWER_COST)) {
				ctx.actionBar("message.projecthero.energy.empower_low");
				return;
			}
			ServerPlayer p = ctx.player();
			int el = element(p);
			empower(p, el);
			ctx.setResource("empower_until", p.level().getGameTime() + EMPOWER_TICKS, 1.0e12f);
			ctx.setResource("empower_el", el, MAGIC);
			for (int i = 0; i < 3; i++) {
				BatchCFx.flatRing(ctx.level(), p.position().add(0, 0.3 + i * 0.8, 0), 1.0, 20, elDust(p, 1.4f), 0.15);
			}
			MutationVisuals.play(p, "flex");
			AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.2f);
			ctx.triggerCooldown();
		}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, SUPER_ATK);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, EnergyAbsorptionHandlers::passiveTick);

		// Melee while Absorption Mode is on or while Empowered: the blow carries the stored element.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof LivingEntity le)
					|| !BatchCFx.ownsQuick(sp, KEY)) {
				return InteractionResult.PASS;
			}
			boolean empowered = BatchCFx.resourceQuick(sp, KEY, "empower_until") > sp.level().getGameTime();
			if (empowered) {
				int el = Math.round(BatchCFx.resourceQuick(sp, KEY, "empower_el"));
				applyElement(sp, le, 4.0f, el);
			} else if (modeActive(sp) && world instanceof ServerLevel sl) {
				sl.sendParticles(elDust(sp, 1.4f), le.getX(), le.getY() + le.getBbHeight() / 2, le.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
			}
			return InteractionResult.PASS;
		});

		registerRedstoneInteractions();
	}

	// ---- routines ------------------------------------------------------------------------------

	private static void fireOverload(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float spent = ctx.resource(METER);
		if (spent < OVERLOAD_MIN) {
			ctx.actionBar("message.projecthero.energy.overload_low");
			MutationVisuals.stopIf(p, "carry_overhead");
			return;
		}
		ctx.setResource(METER, 0, MAX);
		float dmg = 25.0f + spent * 0.1f + superchargedBonus(p);
		double radius = 6.0 + spent / 115.0;
		Vec3 above = p.position().add(0, p.getBbHeight() + 1.0, 0);
		level.sendParticles(elDust(p, 3.5f), above.x, above.y, above.z, 80, 1.2, 1.2, 1.2, 0.08);
		level.sendParticles(ParticleTypes.FLASH, above.x, above.y, above.z, 1, 0, 0, 0, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, above, radius)) {
			elementalHit(p, e, dmg, true);
			AbilityHelpers.knockbackFrom(e, above, 2.0);
		}
		for (int i = 1; i <= 4; i++) {
			BatchCFx.flatRing(level, p.position().add(0, 0.5, 0), i * 0.7, 24 + i * 6, elDust(p, 2.0f), 0.4 + i * 0.3);
		}
		if (AbilityHelpers.canGrief()) {
			BlockPos c = BlockPos.containing(above);
			int r = spent > 400 ? 4 : 3;
			for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
				if (bp.distToCenterSqr(above.x, above.y, above.z) <= (double) (r * r)) {
					var bs = level.getBlockState(bp);
					if (!bs.isAir() && bs.getDestroySpeed(level, bp) >= 0 && bs.getDestroySpeed(level, bp) < 50.0f) {
						level.destroyBlock(bp, false);
					}
				}
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, above.x, above.y, above.z, 1, 0, 0, 0, 0);
		MutationVisuals.play(p, "slam_two_hand");
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.5f);
		ctx.triggerCooldown();
	}

	private static void drainPulse(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = AbilityHelpers.raycastEntity(p, 12.0);
		if (t != null) {
			AbilityHelpers.hurt(p, t, p.damageSources().indirectMagic(p, p), 5.0f);
			gain(p, 14.0f);
			p.heal(1.0f);
			Vec3 from = t.position().add(0, t.getBbHeight() * 0.5, 0);
			BatchCFx.inwardSpiral(level, from, 0.8, 8, elDust(p, 1.0f), 0.1, p.tickCount);
			AbilityHelpers.line(level, from, handOrigin(p), elDust(p, 1.1f), 2.0);
			AbilityHelpers.sound(p, SoundEvents.BEACON_AMBIENT, 0.6f, 1.8f);
			return;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, 8.0);
		if (hit.getType() == HitResult.Type.BLOCK) {
			var st = level.getBlockState(hit.getBlockPos());
			if (st.is(Blocks.REDSTONE_BLOCK) || st.is(Blocks.REDSTONE_ORE) || st.is(Blocks.DEEPSLATE_REDSTONE_ORE)
					|| st.is(Blocks.REDSTONE_LAMP) || st.is(Blocks.LIGHTNING_ROD)) {
				gain(p, 10.0f);
				setElement(p, LIGHTNING);
				AbilityHelpers.line(level, hit.getLocation(), handOrigin(p), dust(ELEMENT_RGB[LIGHTNING], 1.0f), 2.0);
			}
		}
	}

	private static void endDrain(AbilityContext ctx) {
		boolean was = ctx.resource("converting") > 0.5f || ctx.resource("draining") > 0.5f;
		if (!was) {
			return;
		}
		ctx.setResource("converting", 0, 1);
		ctx.setResource("draining", 0, 1);
		MutationVisuals.stopIf(ctx.player(), "channel_right");
		MutationVisuals.stopIf(ctx.player(), "power_up");
		ctx.triggerCooldown(8 * 20);
	}

	/** Reflects the first hostile projectile heading at a player whose Redirect window is open. */
	private static void catchProjectile(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.5))) {
			if (proj.getOwner() == p) {
				continue;
			}
			Vec3 vel = proj.getDeltaMovement();
			Vec3 toMe = p.position().add(0, 1.0, 0).subtract(proj.position());
			if (vel.lengthSqr() < 0.01 || vel.dot(toMe) <= 0) {
				continue;
			}
			double speed = Math.max(1.2, vel.length() * 1.3);
			Vec3 dir = proj.getOwner() instanceof LivingEntity shooter
					? shooter.getEyePosition().subtract(proj.position()).normalize()
					: vel.normalize().reverse();
			proj.setDeltaMovement(dir.scale(speed));
			proj.setOwner(p);
			proj.hasImpulse = true;
			proj.hurtMarked = true;
			redirected(ctx, proj.position());
			return;
		}
	}

	/**
	 * Called from {@code HeroDamageRules}: while Redirect is open, a projectile or melee hit is negated and fired back
	 * at whoever threw it (x1.5 +6, with the element's effect). Returns true when the hit was caught.
	 */
	public static boolean tryRedirect(ServerPlayer p, DamageSource source, float amount) {
		Power power = power();
		if (power == null || ExperimentalPowers.getResource(p, power, "redirect_until") <= p.level().getGameTime()) {
			return false;
		}
		boolean projectile = source.getDirectEntity() instanceof Projectile;
		boolean melee = source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity();
		if (!projectile && !melee) {
			return false;
		}
		if (source.getEntity() instanceof LivingEntity attacker && attacker != p) {
			float back = amount * 1.5f + 6.0f;
			elementalHit(p, attacker, back, true);
			AbilityHelpers.knockbackFrom(attacker, p.position(), 1.2);
			AbilityHelpers.line((ServerLevel) p.level(), handOrigin(p), attacker.position().add(0, attacker.getBbHeight() * 0.5, 0),
					elDust(p, 1.4f), 3.0);
		}
		ExperimentalPowers.addResource(p, power, METER, amount * 0.5f, MAX);
		AbilityContext ctx = new AbilityContext(p, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_7), false);
		redirected(ctx, p.position().add(0, 1.0, 0));
		return true;
	}

	private static void redirected(AbilityContext ctx, Vec3 at) {
		ServerPlayer p = ctx.player();
		ctx.setResource("redirect_until", 0, 1.0e12f);
		MutationVisuals.play(p, "cast_right");
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(elDust(p, 1.6f), at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.1);
		AbilityHelpers.sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 1.4f);
		AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.8f);
	}

	/** Empower's element-dependent buff. */
	private static void empower(ServerPlayer p, int el) {
		int t = EMPOWER_TICKS;
		switch (el) {
			case FIRE -> {
				p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, t, 0, false, true, true));
				p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, t, 0, false, true, true));
			}
			case LIGHTNING -> {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, t, 1, false, true, true));
				p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, t, 1, false, true, true));
			}
			case EXPLOSION -> {
				p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, t, 1, false, true, true));
			}
			case MAGIC -> {
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, t, 1, false, true, true));
				p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, t, 1, false, true, true));
			}
			default -> {
				p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, t, 0, false, true, true));
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, t, 0, false, true, true));
			}
		}
	}

	private static final Set<String> STALE = Set.of("field_bar", "field_active", "overload_running", "convert_timer");

	private static void passiveTick(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		var power = power();
		if (power == null) {
			return;
		}
		if (player.tickCount % 100 == 0) {
			BatchCFx.purge(player, KEY, STALE, Set.of());
		}
		if (player.tickCount % 5 != 0) {
			return;
		}
		// Standing on a redstone source charges the meter (as lightning).
		var belowState = level.getBlockState(player.blockPosition().below());
		if (belowState.is(Blocks.REDSTONE_BLOCK)) {
			gain(player, 2.0f); // 8/s
			setElement(player, LIGHTNING);
		} else if (belowState.is(Blocks.REDSTONE_ORE) || belowState.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
			gain(player, 1.25f); // 5/s
			setElement(player, LIGHTNING);
		}

		// Passive trickle regen: only while nothing has been absorbed for 10 s, capped at 25%. 1.5/s.
		long now = level.getGameTime();
		float lastAbsorb = ExperimentalPowers.getResource(player, power, "last_absorb");
		float energy = ExperimentalPowers.getResource(player, power, METER);
		if (now - (long) lastAbsorb >= 200 && energy < MAX * 0.25f) {
			ExperimentalPowers.addResource(player, power, METER, 1.5f / 4f, MAX);
		}
		// An empty meter forgets its element.
		if (energy < 0.5f && element(player) != RAW) {
			setElement(player, RAW);
		}

		// Overcharge: 80% strength/speed + glow; 100% self-damage.
		if (energy >= MAX * 0.8f) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false, false));
			level.sendParticles(elDust(player, 1.2f), player.getX(), player.getY() + 1, player.getZ(), 3, 0.35, 0.6, 0.35, 0.01);
		}
		if (energy >= MAX - 0.5f && player.tickCount % 40 == 0) {
			player.hurt(player.damageSources().magic(), 1.0f);
		}
	}

	private static void gain(ServerPlayer player, float amount) {
		var power = power();
		if (power != null && canAbsorb(player)) {
			ExperimentalPowers.addResource(player, power, METER, amount, MAX);
			markAbsorbed(player);
		}
	}

	private static void registerRedstoneInteractions() {
		// Right-click redstone dust: harvest it for a flat 5 energy.
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)
					|| !ExperimentalPowers.owns(sp, power()) || !canAbsorb(sp)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			var state = world.getBlockState(pos);
			if (state.is(Blocks.REDSTONE_WIRE)) {
				world.removeBlock(pos, false);
				gain(sp, 5.0f);
				setElement(sp, LIGHTNING);
				AbilityHelpers.sound(sp, SoundEvents.ITEM_PICKUP, 0.6f, 1.6f);
				return InteractionResult.SUCCESS;
			}
			if (ExperimentalPowers.getResource(sp, power(), METER) >= 1.0f && triggerRedstoneMachine(world, pos, state)) {
				ExperimentalPowers.addResource(sp, power(), METER, -1.0f, MAX);
				AbilityHelpers.sound(sp, SoundEvents.STONE_BUTTON_CLICK_ON, 1.0f, 1.2f);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// Sneak + use a Redstone Block (40) or Redstone Dust (5) in hand: burn it for energy.
		UseItemCallback.EVENT.register((player, world, hand) -> {
			var stack = player.getItemInHand(hand);
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
					|| !(stack.is(Items.REDSTONE_BLOCK) || stack.is(Items.REDSTONE)) || !(player instanceof ServerPlayer sp)
					|| !ExperimentalPowers.owns(sp, power()) || !canAbsorb(sp)) {
				return InteractionResultHolder.pass(stack);
			}
			boolean block = stack.is(Items.REDSTONE_BLOCK);
			stack.shrink(1);
			gain(sp, block ? 40.0f : 5.0f);
			setElement(sp, LIGHTNING);
			AbilityHelpers.sound(sp, block ? SoundEvents.BEACON_ACTIVATE : SoundEvents.ITEM_PICKUP, 0.8f, 1.5f);
			((ServerLevel) sp.level()).sendParticles(dust(ELEMENT_RGB[LIGHTNING], 2.0f), sp.getX(), sp.getY() + 1, sp.getZ(),
					block ? 20 : 6, 0.4, 0.6, 0.4, 0.05);
			return InteractionResultHolder.success(stack);
		});
	}

	/** A restrained, non-destructive "button press" on a handful of common redstone machines. */
	private static boolean triggerRedstoneMachine(net.minecraft.world.level.Level world, BlockPos pos,
			net.minecraft.world.level.block.state.BlockState state) {
		if (state.is(Blocks.NOTE_BLOCK)) {
			world.blockEvent(pos, state.getBlock(), 0, 0);
			return true;
		}
		if (state.is(Blocks.REDSTONE_LAMP) && !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
			world.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true), 3);
			world.scheduleTick(pos, state.getBlock(), 20);
			return true;
		}
		return false;
	}

	private static void onCooldownMessage(AbilityContext ctx) {
		ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
	}
}
