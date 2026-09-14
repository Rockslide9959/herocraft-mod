package com.projecthero.mod.hero.power.p15;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/** Power 15 — Invisibility / Light Manipulation. Stronger and longer-ranged in direct sunlight. */
public final class InvisibilityLightHandlers {
	private static final String KEY = "power_15_invisibility_light_manipulation";
	private static final Vector3f YELLOW = new Vector3f(1.0f, 0.86f, 0.2f);

	private InvisibilityLightHandlers() {
	}

	private static boolean inSunlight(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level)) {
			return false;
		}
		return level.isDay() && !level.isRaining() && level.canSeeSky(p.blockPosition());
	}

	/** Damage bonus multiplier: +10% in direct sunlight. */
	private static float dmg(ServerPlayer p, float base) {
		return inSunlight(p) ? base * 1.1f : base;
	}

	/** Range bonus: +20% in direct sunlight. */
	private static double range(ServerPlayer p, double base) {
		return inSunlight(p) ? base * 1.2 : base;
	}

	/** A yellow-tinted origin point at the player's hand rather than their eyes, so R never blinds them. */
	private static Vec3 handOrigin(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0e-6) {
			double yaw = Math.toRadians(p.getYRot());
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		} else {
			right = right.normalize();
		}
		return p.getEyePosition().add(look.scale(0.8)).add(right.scale(0.4)).add(0, -0.35, 0);
	}

	private static void yellowBurst(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(new DustParticleOptions(YELLOW, 2.0f), at.x, at.y, at.z, count, spread, spread, spread, 0.02);
	}

	public static void register() {
		// R -- Light Blast, fired from the hand. Tap for a quick shot; hold to charge (more damage, more
		// cooldown per second held). Shift+R fires a 5-blast volley instead.
		AbilityHandlers.register(KEY, "light_blast", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (!ctx.cooldownReady()) {
						onCooldownMessage(ctx);
						return;
					}
					ctx.setResource("burst_left", 5, 5);
					ctx.setResource("burst_timer", 0, 1);
					fireBlast(ctx, 0.0f);
					ctx.setResource("burst_left", 4, 5);
					ctx.triggerCooldown(11 * 20);
					return;
				}
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				ctx.setResource("charging_light", 1, 1);
				ctx.setResource("light_charge_start", p.level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("charging_light") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("light_charge_start");
				double seconds = Math.min(3.0, held / 20.0);
				ctx.setResource("charging_light", 0, 1);
				ctx.setResource("light_charge", 0, 100);
				fireBlast(ctx, (float) (seconds * 6.0));
				ctx.triggerCooldown((int) Math.round(20 + seconds * 20));
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				int left = (int) ctx.resource("burst_left");
				if (left > 0) {
					float timer = ctx.resource("burst_timer") + 1;
					if (timer >= 4) {
						fireBlast(ctx, 0.0f);
						ctx.setResource("burst_left", left - 1, 5);
						timer = 0;
					}
					ctx.setResource("burst_timer", timer, 1);
					return;
				}
				if (ctx.resource("charging_light") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("light_charge_start");
				ctx.setResource("light_charge", (float) Math.min(100.0, held / (3.0 * 20) * 100.0), 100);
				if (held % 3 == 0) {
					Vec3 at = handOrigin(p);
					ctx.level().sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.01);
					yellowBurst(ctx.level(), at, 2, 0.08);
				}
			}
		});

		// G -- Solar Lance: a piercing beam that runs several enemies through. Shift+G is Solar Eruption,
		// a short-range ground-slam nova.
		AbilityHandlers.register(KEY, "flash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			if (p.isShiftKeyDown()) {
				Vec3 at = p.position().add(p.getLookAngle().scale(3));
				double r = range(p, 4.0);
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, r)) {
					AbilityHelpers.hurt(p, e, dmg(p, 16.0f));
					AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 60, 0);
					AbilityHelpers.knockbackFrom(e, at, 1.0);
				}
				level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
				yellowBurst(level, at, 40, r * 0.5);
				AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.1f, 1.6f);
				ctx.triggerCooldown(10 * 20);
				return;
			}
			Vec3 from = handOrigin(p);
			double r = range(p, 12.0);
			Vec3 end = from.add(p.getLookAngle().scale(r));
			AbilityHelpers.line(level, from, end, ParticleTypes.END_ROD, 3.0);
			yellowBurst(level, from, 6, 0.1);
			int hits = 0;
			for (LivingEntity e : AbilityHelpers.living(level, from.add(p.getLookAngle().scale(r * 0.5)),
					r * 0.5 + 0.6, le -> le != p && !(le instanceof ArmorStand))) {
				Vec3 dir = e.position().subtract(from);
				if (dir.lengthSqr() < 1.0e-4 || dir.normalize().dot(p.getLookAngle()) < 0.85) {
					continue;
				}
				AbilityHelpers.hurt(p, e, dmg(p, 12.0f));
				AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, 60, 0);
				hits++;
				if (hits >= 5) {
					break;
				}
			}
			AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.5f);
			ctx.triggerCooldown(6 * 20);
		}));

		// X -- Sparkling Flight (unchanged).
		AbilityHandlers.register(KEY, "mirage_dash", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!ctx.cooldownReady()) {
					return;
				}
				if (!ExperimentalPowers.state(p).resources.containsKey(KEY + "/sparkle")) {
					ctx.setResource("sparkle", MAX_SPARKLE, MAX_SPARKLE);
				}
				if (ctx.resource("sparkle") < 10.0f) {
					ctx.actionBar("message.projecthero.light.sparkle_low");
					return;
				}
				if (p.getAbilities().instabuild || com.projecthero.mod.power.ThorPowers.isFlying(p)) {
					return;
				}
				ctx.setResource("sparkling", 1, 1);
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.7f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endSparkle(ctx, true);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("sparkling") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (p.getAbilities().instabuild || com.projecthero.mod.power.ThorPowers.isFlying(p)) {
					endSparkle(ctx, false);
					return;
				}
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = true;
				AbilityHelpers.addImpulse(p, p.getLookAngle().scale(0.42));
				p.resetFallDistance();
				p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 6, 0, false, false, false));
				ServerLevel level = ctx.level();
				double h = Math.max(1.0, p.getBbHeight());
				for (int s = 0; s < 4; s++) {
					double yy = p.getY() + h * (s / 3.0);
					level.sendParticles(ParticleTypes.END_ROD, p.getX(), yy, p.getZ(), 2, 0.22, 0.12, 0.22, 0.004);
					level.sendParticles(ParticleTypes.GLOW, p.getX(), yy, p.getZ(), 1, 0.2, 0.12, 0.2, 0.0);
				}
				ctx.addResource("sparkle", -SPARKLE_DRAIN, MAX_SPARKLE);
				if (ctx.resource("sparkle") <= 0.0f) {
					endSparkle(ctx, true);
					ctx.actionBar("message.projecthero.light.sparkle_out");
				}
			}
		});

		// Z -- hold for 5 seconds to charge Holy Light, then it fires automatically.
		AbilityHandlers.register(KEY, "perfect_cloak", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("holy_ticks") > 0.0f || ctx.resource("charging_holy") > 0.5f || !ctx.cooldownReady()) {
					return;
				}
				ctx.setResource("charging_holy", 1, 1);
				ctx.setResource("holy_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("charging_holy") > 0.5f && ctx.resource("holy_ticks") <= 0.0f) {
					ctx.setResource("charging_holy", 0, 1);
					ctx.setResource("holy_charge", 0, 100);
					AbilityHelpers.sound(ctx.player(), SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.8f);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				if (ctx.resource("charging_holy") > 0.5f) {
					long held = p.level().getGameTime() - (long) ctx.resource("holy_charge_start");
					ctx.setResource("holy_charge", (float) Math.min(100.0, held / (5.0 * 20) * 100.0), 100);
					if (p.tickCount % 2 == 0) {
						yellowBurst(level, p.getEyePosition().add(p.getLookAngle().scale(0.6)), 3, 0.1);
					}
					if (held >= 5 * 20) {
						ctx.setResource("charging_holy", 0, 1);
						ctx.setResource("holy_charge", 0, 100);
						ctx.setResource("holy_ticks", 60, 60);
						AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.5f);
						ctx.triggerCooldown(75 * 20);
					}
					return;
				}
				int t = (int) ctx.resource("holy_ticks");
				if (t <= 0) {
					return;
				}
				ctx.setResource("holy_ticks", t - 1, 60);
				Vec3 start = p.getEyePosition();
				LivingEntity target = AbilityHelpers.raycastEntity(p, 40.0);
				Vec3 end;
				if (target != null) {
					end = target.position().add(0, target.getBbHeight() * 0.5, 0);
					AbilityHelpers.hurt(p, target, dmg(p, 12.0f));
				} else {
					var bhr = AbilityHelpers.raycastBlock(p, 40.0);
					end = bhr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
							? bhr.getLocation() : start.add(p.getLookAngle().scale(40.0));
				}
				Vec3 from = start.add(p.getLookAngle().scale(0.3));
				AbilityHelpers.line(level, from, end, ParticleTypes.END_ROD, 4.0);
				yellowBurst(level, from.lerp(end, 0.5), 6, 1.0);
				if (t % 10 == 0) {
					Vec3 impact = AbilityHelpers.aimPoint(p, 40.0);
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, impact, 5.0)) {
						AbilityHelpers.hurt(p, e, dmg(p, 24.0f));
						AbilityHelpers.applyControl(e, MobEffects.GLOWING, 60, 0);
						AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 40, 0);
					}
					level.sendParticles(ParticleTypes.FLASH, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
					level.sendParticles(ParticleTypes.END_ROD, impact.x, impact.y, impact.z, 30, 1.0, 1.0, 1.0, 0.05);
					yellowBurst(level, impact, 20, 1.2);
				}
			}
		});

		// V -- Flash: blind + slow a wide burst around you.
		AbilityHandlers.register(KEY, "decoy", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			double r = range(p, 8.0);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				AbilityHelpers.hurt(p, e, dmg(p, 10.0f));
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 200, 0); // 10s
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 200, 1); // Slowness II, 10s
				if (e instanceof Mob mob) {
					mob.setTarget(null);
				}
			}
			level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 60, 4, 1, 4, 0.2);
			yellowBurst(level, p.position().add(0, 1, 0), 50, r * 0.5);
			AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.5f);
			ctx.triggerCooldown(10 * 20);
		}));

		AbilityHandlers.register(KEY, "cloaking_toggle", Handlers.toggle(
				ctx -> ctx.player().addEffect(inf()),
				ctx -> ctx.player().removeEffect(MobEffects.INVISIBILITY),
				ctx -> {
					ServerPlayer p = ctx.player();
					if (ctx.resource("revealed_until") > p.level().getGameTime()) {
						p.removeEffect(MobEffects.INVISIBILITY);
					} else {
						MobEffectInstance cur = p.getEffect(MobEffects.INVISIBILITY);
						if (cur == null || !cur.isInfiniteDuration()) {
							p.addEffect(inf());
						}
					}
				}));

		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp) {
				var power = Powers.byKey(KEY);
				if (power != null && ExperimentalPowers.owns(sp, power)
						&& ExperimentalPowers.isToggled(sp, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6))) {
					ExperimentalPowers.setResource(sp, power, "revealed_until",
							sp.level().getGameTime() + 40, 1e12f);
				}
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			var power = Powers.byKey(KEY);
			if (power != null) {
				ExperimentalPowers.setResource(player, power, "sparkling", 0, 1);
			}
			if (!player.getAbilities().instabuild && !com.projecthero.mod.power.ThorPowers.isFlying(player)
					&& !com.projecthero.mod.hero.power.HeroFlight.isFlying(player)) {
				player.getAbilities().mayfly = false;
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
			}
			if (!active) {
				player.removeEffect(MobEffects.NIGHT_VISION);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false, false));
			var blind = player.getEffect(MobEffects.BLINDNESS);
			if (blind != null && blind.getDuration() > 40) {
				player.removeEffect(MobEffects.BLINDNESS);
			}
			var power = Powers.byKey(KEY);
			if (power == null) {
				return;
			}
			boolean sparkling = ExperimentalPowers.getResource(player, power, "sparkling") > 0.5f;
			if (!sparkling && ExperimentalPowers.state(player).resources.containsKey(KEY + "/sparkle")
					&& ExperimentalPowers.getResource(player, power, "sparkle") < MAX_SPARKLE) {
				ExperimentalPowers.addResource(player, power, "sparkle", SPARKLE_REGEN, MAX_SPARKLE);
			}
		});
	}

	private static void onCooldownMessage(AbilityContext ctx) {
		ctx.actionBar("message.projecthero.ability.on_cooldown",
				net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
	}

	private static void fireBlast(AbilityContext ctx, float bonusDamage) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		double r = range(p, 22.0);
		LivingEntity t = AbilityHelpers.raycastEntity(p, r);
		Vec3 from = handOrigin(p);
		Vec3 to = AbilityHelpers.aimPoint(p, r);
		AbilityHelpers.line(level, from, to, ParticleTypes.END_ROD, 3.0);
		yellowBurst(level, from, 8, 0.12);
		if (t != null) {
			AbilityHelpers.hurt(p, t, dmg(p, 8.0f) + bonusDamage);
			AbilityHelpers.applyControl(t, MobEffects.BLINDNESS, 80, 0); // 4s
		}
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
	}

	/** End Sparkling Flight: drop flight, restore the body, cushion the landing, start the cooldown. */
	private static void endSparkle(AbilityContext ctx, boolean startCooldown) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("sparkling") < 0.5f) {
			return;
		}
		ctx.setResource("sparkling", 0, 1);
		if (!p.getAbilities().instabuild && !com.projecthero.mod.power.ThorPowers.isFlying(p)
				&& !com.projecthero.mod.hero.power.HeroFlight.isFlying(p)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		if (!cloaked(p)) {
			p.removeEffect(MobEffects.INVISIBILITY);
		}
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, false));
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
		if (startCooldown) {
			ctx.triggerCooldown();
		}
	}

	/** True while the cloaking toggle is on -- read from the synced attachment (server + client). */
	public static boolean cloaked(net.minecraft.world.entity.player.Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.activeToggles.contains(KEY + "/cloaking_toggle");
	}

	/** True while Sparkling Flight is channelling -- read from the synced attachment (server + client). */
	public static boolean sparkleFlying(net.minecraft.world.entity.player.Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY)
				&& st.resources.getOrDefault(KEY + "/sparkling", 0.0f) > 0.5f;
	}

	/** The player's whole appearance (armour included) is hidden while cloaked or sparkle-flying. */
	public static boolean hideArmor(net.minecraft.world.entity.player.Player p) {
		return cloaked(p) || sparkleFlying(p);
	}

	private static final float MAX_SPARKLE = 100.0f;
	private static final float SPARKLE_DRAIN = 100.0f / (25 * 20); // ~25 s of flight from full
	private static final float SPARKLE_REGEN = 100.0f / (18 * 20); // refills in ~18 s

	private static MobEffectInstance inf() {
		return new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
	}
}
