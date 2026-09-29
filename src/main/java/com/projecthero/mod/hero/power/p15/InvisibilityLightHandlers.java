package com.projecthero.mod.hero.power.p15;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.d.BatchDContent;
import com.projecthero.mod.hero.revamp.d.BatchDFx;
import com.projecthero.mod.hero.revamp.d.HardLightBladeItem;
import com.projecthero.mod.hero.revamp.d.MirrorImageEntity;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Power 15 — Invisibility / Light Manipulation. Stronger and longer-ranged in direct sunlight.
 *
 * <p>v0.13.22 revamp (batch D): <b>refraction</b>. R Light Blast, G Flash (sneak: Radiant Lance), X Sparkling
 * Flight, Z Holy Light, V Mirror Images (refracted decoys that draw aggro and burst into blinding light), C Cloak
 * (now also hides held items, for every viewer), H Hard-Light Blade (a conjured emissive sword, 30 s, never a real
 * item), N Prism Shield (hold: reflects projectiles and beams coming at your front).
 */
public final class InvisibilityLightHandlers {
	private static final String KEY = "power_15_invisibility_light_manipulation";
	private static final Vector3f YELLOW = new Vector3f(1.0f, 0.86f, 0.2f);

	// --- R ---
	private static final float BLAST_DAMAGE = 10.0f;
	private static final float BLAST_CHARGE_PER_SECOND = 7.0f;
	private static final int VOLLEY_COOLDOWN = 187;
	// --- G ---
	private static final float FLASH_DAMAGE = 12.0f;
	private static final float LANCE_DAMAGE = 24.0f;
	private static final int LANCE_COOLDOWN = 204;
	// --- Z ---
	private static final float HOLY_BEAM_DAMAGE = 14.0f;
	private static final float HOLY_BURST_DAMAGE = 29.0f;
	private static final int HOLY_COOLDOWN = 64 * 20;
	// --- V ---
	private static final int MIRROR_IMAGES = 2;
	// --- H ---
	private static final int BLADE_COOLDOWN = 20 * 20;
	// --- N ---
	public static final float MAX_PRISM = 115.0f;
	private static final float PRISM_DRAIN = 0.7f;
	private static final float PRISM_PER_REFLECT = 8.0f;
	private static final float PRISM_REGEN = MAX_PRISM / (12 * 20);
	private static final net.minecraft.resources.ResourceLocation PRISM_SLOW =
			com.projecthero.mod.ProjectHeroMod.id("prism_shield_slow");

	private InvisibilityLightHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
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

	/** A point at the player's hand rather than their eyes, so R never blinds them. */
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
		// R -- Light Blast, fired from the hand. Tap for a quick shot; hold to charge. Shift+R: a 5-blast volley.
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
					ctx.triggerCooldown(VOLLEY_COOLDOWN);
					return;
				}
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				ctx.setResource("charging_light", 1, 1);
				ctx.setResource("light_charge_start", p.level().getGameTime(), 1.0e12f);
				MutationVisuals.play(p, "channel_right");
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
				MutationVisuals.stopIf(p, "channel_right");
				fireBlast(ctx, (float) (seconds * BLAST_CHARGE_PER_SECOND));
				ctx.triggerCooldown((int) Math.round(17 + seconds * 17));
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				int left = (int) ctx.resource("burst_left");
				if (left > 0) {
					float timer = ctx.resource("burst_timer") + 1;
					if (timer >= 10) {
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
				MutationVisuals.ensure(p, "channel_right");
				if (held % 3 == 0) {
					Vec3 at = handOrigin(p);
					ctx.level().sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.01);
					yellowBurst(ctx.level(), at, 2, 0.08);
				}
			}
		});

		// G -- Flash: a searing burst of light around you. Shift+G: Radiant Lance, a piercing line of light.
		AbilityHandlers.register(KEY, "flash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			if (p.isShiftKeyDown()) {
				double r = range(p, 30.0);
				Vec3 end = AbilityHelpers.aimPoint(p, r);
				for (LivingEntity e : lanceTargets(p, r, 1.6)) {
					AbilityHelpers.hurt(p, e, dmg(p, LANCE_DAMAGE));
					AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 80, 0);
					AbilityHelpers.applyControl(e, MobEffects.GLOWING, 100, 0);
					AbilityHelpers.knockbackFrom(e, p.position(), 0.8);
				}
				AbilityHelpers.line(level, handOrigin(p), end, ParticleTypes.END_ROD, 2.0);
				AbilityHelpers.line(level, handOrigin(p), end, BatchDFx.PRISM, 1.0);
				level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
				yellowBurst(level, end, 40, 1.4);
				MutationVisuals.play(p, "point_right");
				AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.1f, 1.6f);
				ctx.triggerCooldown(LANCE_COOLDOWN);
				return;
			}
			double r = range(p, 8.0);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				if (Squads.areAllies(p, e) || e instanceof MirrorImageEntity) {
					continue;
				}
				AbilityHelpers.hurt(p, e, dmg(p, FLASH_DAMAGE));
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 200, 0);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 200, 1);
				if (e instanceof Mob mob) {
					mob.setTarget(null);
				}
			}
			level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 60, 4, 1, 4, 0.2);
			yellowBurst(level, p.position().add(0, 1, 0), 50, r * 0.5);
			MutationVisuals.play(p, "clap");
			AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.5f);
			ctx.triggerCooldown();
		}));

		// X -- Sparkling Flight.
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
				MutationVisuals.play(p, "dash_forward");
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
				double chestY = p.getY() + p.getBbHeight() * 0.55;
				level.sendParticles(new DustParticleOptions(YELLOW, 2.4f), p.getX(), chestY, p.getZ(),
						4, 0.1, 0.1, 0.1, 0.0);
				level.sendParticles(ParticleTypes.END_ROD, p.getX(), chestY, p.getZ(), 2, 0.15, 0.1, 0.15, 0.003);
				level.sendParticles(BatchDFx.PRISM, p.getX(), chestY, p.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
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
				MutationVisuals.play(ctx.player(), "p15.holy_charge");
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("charging_holy") > 0.5f && ctx.resource("holy_ticks") <= 0.0f) {
					ctx.setResource("charging_holy", 0, 1);
					ctx.setResource("holy_charge", 0, 100);
					MutationVisuals.stopIf(ctx.player(), "p15.holy_charge");
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
					MutationVisuals.ensure(p, "p15.holy_charge");
					if (p.tickCount % 2 == 0) {
						yellowBurst(level, p.getEyePosition().add(0, 0.8, 0), 3, 0.3);
						BatchDFx.ring(level, p.position().add(0, 0.2, 0), 1.0 + held / 100.0, BatchDFx.LIGHT, 8, held * 0.2);
					}
					if (held >= 5 * 20) {
						ctx.setResource("charging_holy", 0, 1);
						ctx.setResource("holy_charge", 0, 100);
						ctx.setResource("holy_ticks", 60, 60);
						MutationVisuals.play(p, "channel_two_hand");
						AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.5f);
						ctx.triggerCooldown(HOLY_COOLDOWN);
					}
					return;
				}
				int t = (int) ctx.resource("holy_ticks");
				if (t <= 0) {
					return;
				}
				ctx.setResource("holy_ticks", t - 1, 60);
				if (t - 1 <= 0) {
					MutationVisuals.stopIf(p, "channel_two_hand");
				} else {
					MutationVisuals.ensure(p, "channel_two_hand");
				}
				Vec3 start = p.getEyePosition();
				LivingEntity target = AbilityHelpers.raycastEntity(p, 40.0);
				Vec3 end;
				if (target != null && !Squads.areAllies(p, target)) {
					end = target.position().add(0, target.getBbHeight() * 0.5, 0);
					AbilityHelpers.hurt(p, target, dmg(p, HOLY_BEAM_DAMAGE));
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
						if (Squads.areAllies(p, e)) {
							continue;
						}
						AbilityHelpers.hurt(p, e, dmg(p, HOLY_BURST_DAMAGE));
						AbilityHelpers.applyControl(e, MobEffects.GLOWING, 60, 0);
						AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 40, 0);
					}
					level.sendParticles(ParticleTypes.FLASH, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
					level.sendParticles(ParticleTypes.END_ROD, impact.x, impact.y, impact.z, 30, 1.0, 1.0, 1.0, 0.05);
					yellowBurst(level, impact, 20, 1.2);
				}
			}
		});

		// V -- Mirror Images: refracted copies of you that scatter and pull aggro, bursting into light when struck.
		AbilityHandlers.register(KEY, "decoy", Handlers.instant(InvisibilityLightHandlers::mirrorImages));

		// C -- Cloak.
		AbilityHandlers.register(KEY, "cloaking_toggle", Handlers.toggle(
				ctx -> {
					ctx.player().addEffect(inf());
					MutationVisuals.play(ctx.player(), "p15.cloak");
					shimmer(ctx.level(), ctx.player());
					AbilityHelpers.sound(ctx.player(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8f, 1.4f);
				},
				ctx -> {
					ctx.player().removeEffect(MobEffects.INVISIBILITY);
					shimmer(ctx.level(), ctx.player());
					AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
				},
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

		// H -- Hard-Light Blade.
		AbilityHandlers.register(KEY, "hard_light_blade", Handlers.instantTicking(
				InvisibilityLightHandlers::bladePress, InvisibilityLightHandlers::bladeTick));

		// N -- Prism Shield (hold).
		AbilityHandlers.register(KEY, "prism_shield", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				prismStart(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				prismEnd(ctx.player());
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				prismTick(ctx);
			}
		});

		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp) {
				var power = power();
				if (power != null && ExperimentalPowers.owns(sp, power)
						&& ExperimentalPowers.isToggled(sp, power, power.ability(AbilitySlot.SLOT_6))) {
					ExperimentalPowers.setResource(sp, power, "revealed_until",
							sp.level().getGameTime() + 40, 1e12f);
				}
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			var power = power();
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
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, PRISM_SLOW);
				if (power != null && ExperimentalPowers.owns(player, power)) {
					prismEnd(player);
					ExperimentalPowers.setResource(player, power, "blade_until", 0, 1.0e12f);
					ExperimentalPowers.setResource(player, power, "blade_left", 0, HardLightBladeItem.LIFETIME_TICKS);
				}
				HardLightBladeItem.purge(player);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false, false));
			var blind = player.getEffect(MobEffects.BLINDNESS);
			if (blind != null && blind.getDuration() > 40) {
				player.removeEffect(MobEffects.BLINDNESS);
			}
			var power = power();
			if (power == null) {
				return;
			}
			boolean sparkling = ExperimentalPowers.getResource(player, power, "sparkling") > 0.5f;
			if (!sparkling && ExperimentalPowers.state(player).resources.containsKey(KEY + "/sparkle")
					&& ExperimentalPowers.getResource(player, power, "sparkle") < MAX_SPARKLE) {
				ExperimentalPowers.addResource(player, power, "sparkle", SPARKLE_REGEN, MAX_SPARKLE);
			}
			if (!ExperimentalPowers.state(player).resources.containsKey(KEY + "/prism")) {
				ExperimentalPowers.setResource(player, power, "prism", MAX_PRISM, MAX_PRISM);
			} else if (ExperimentalPowers.getResource(player, power, "prism_on") < 0.5f
					&& ExperimentalPowers.getResource(player, power, "prism") < MAX_PRISM) {
				ExperimentalPowers.addResource(player, power, "prism", PRISM_REGEN, MAX_PRISM);
			}
		});
	}

	private static void onCooldownMessage(AbilityContext ctx) {
		ctx.actionBar("message.projecthero.ability.on_cooldown",
				net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
	}

	private static void shimmer(ServerLevel level, ServerPlayer p) {
		level.sendParticles(BatchDFx.PRISM, p.getX(), p.getY() + 1.0, p.getZ(), 24, 0.35, 0.8, 0.35, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.7, 0.3, 0.01);
	}

	/** Every enemy within {@code radius} of the look-ray out to {@code range}, for a piercing lance. */
	private static java.util.Set<LivingEntity> lanceTargets(ServerPlayer p, double range, double radius) {
		java.util.LinkedHashSet<LivingEntity> hit = new java.util.LinkedHashSet<>();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		int steps = (int) Math.ceil(range);
		for (int i = 1; i <= steps; i++) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(i)), radius)) {
				if (!Squads.areAllies(p, e) && !(e instanceof MirrorImageEntity)) {
					hit.add(e);
				}
			}
		}
		return hit;
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
		if (t != null && !Squads.areAllies(p, t)) {
			AbilityHelpers.hurt(p, t, dmg(p, BLAST_DAMAGE) + bonusDamage);
			AbilityHelpers.applyControl(t, MobEffects.BLINDNESS, 80, 0);
		}
		MutationVisuals.play(p, "cast_right");
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

	// ================================================================================ V: Mirror Images

	private static void mirrorImages(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		// only one set at a time: an older set bursts first
		for (MirrorImageEntity old : level.getEntitiesOfClass(MirrorImageEntity.class, p.getBoundingBox().inflate(64.0),
				m -> m.ownerId().map(p.getUUID()::equals).orElse(false))) {
			old.shatter(null);
		}
		int count = MIRROR_IMAGES + (inSunlight(p) ? 1 : 0);
		Vec3 fwd = p.getLookAngle().multiply(1, 0, 1);
		fwd = fwd.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : fwd.normalize();
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			double side = count == 1 ? 0 : (i - (count - 1) / 2.0) * 2.0;
			if (Math.abs(side) < 0.1) {
				side = 1.2;
			}
			Vec3 at = p.position().add(right.scale(side)).add(fwd.scale(0.5));
			MirrorImageEntity img = BatchDContent.MIRROR_IMAGE.create(level);
			if (img == null || !level.noCollision(img, img.getType().getDimensions().makeBoundingBox(at))) {
				at = p.position();
				if (img == null) {
					continue;
				}
			}
			img.moveTo(at.x, at.y, at.z, p.getYRot(), 0.0f);
			img.setYHeadRot(p.getYHeadRot());
			img.copyFrom(p);
			Vec3 push = right.scale(Math.signum(side) * 0.35).add(fwd.scale(0.25));
			img.setDeltaMovement(push.x, 0.2, push.z);
			level.addFreshEntity(img);
			level.sendParticles(BatchDFx.PRISM, at.x, at.y + 1.0, at.z, 16, 0.3, 0.6, 0.3, 0.0);
			spawned++;
		}
		if (spawned == 0) {
			return;
		}
		// the refraction switch: you blink out for a second while the images take your place in every mob's eyes
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 24, 0, false, false, false));
		java.util.List<MirrorImageEntity> images = level.getEntitiesOfClass(MirrorImageEntity.class,
				p.getBoundingBox().inflate(8.0), m -> m.ownerId().map(p.getUUID()::equals).orElse(false));
		if (!images.isEmpty()) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0),
					m -> m.getTarget() == p)) {
				mob.setTarget(images.get(level.random.nextInt(images.size())));
			}
		}
		shimmer(level, p);
		MutationVisuals.play(p, "p15.refract");
		AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
		ctx.triggerCooldown();
	}

	// ================================================================================ H: Hard-Light Blade

	private static void bladePress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("blade_until") > 0.5f) {
			endBlade(ctx, true);
			return;
		}
		Inventory inv = p.getInventory();
		if (!inv.getSelected().isEmpty()) {
			int free = -1;
			for (int i = 0; i < 9; i++) {
				if (inv.items.get(i).isEmpty()) {
					free = i;
					break;
				}
			}
			if (free >= 0) {
				inv.selected = free;
				p.connection.send(new ClientboundSetCarriedItemPacket(free));
			} else {
				int stash = -1;
				for (int i = 9; i < inv.items.size(); i++) {
					if (inv.items.get(i).isEmpty()) {
						stash = i;
						break;
					}
				}
				if (stash < 0) {
					ctx.actionBar("message.projecthero.light.blade_hands_full");
					return;
				}
				inv.items.set(stash, inv.getSelected().copy());
				inv.items.set(inv.selected, ItemStack.EMPTY);
			}
		}
		long until = p.level().getGameTime() + HardLightBladeItem.LIFETIME_TICKS;
		inv.items.set(inv.selected, BatchDContent.HARD_LIGHT_BLADE.create(p, until));
		ctx.setResource("blade_until", until, 1.0e12f);
		ctx.setResource("blade_left", HardLightBladeItem.LIFETIME_TICKS, HardLightBladeItem.LIFETIME_TICKS);
		Vec3 hand = AbilityHelpers.handPosition(p);
		ctx.level().sendParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 20, 0.15, 0.4, 0.15, 0.05);
		yellowBurst(ctx.level(), hand, 16, 0.3);
		MutationVisuals.play(p, "p15.blade_summon");
		AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.8f);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.4f);
	}

	private static void bladeTick(AbilityContext ctx) {
		float until = ctx.resource("blade_until");
		if (until <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		long now = p.level().getGameTime();
		boolean inHand = HardLightBladeItem.isBlade(p.getMainHandItem());
		// leaving the hand, running out, or opening any container (chest, anvil, ...) puts the light out
		if (now >= until || !inHand || p.containerMenu != p.inventoryMenu || !p.isAlive()) {
			endBlade(ctx, true);
			return;
		}
		ctx.setResource("blade_left", until - now, HardLightBladeItem.LIFETIME_TICKS);
	}

	private static void endBlade(AbilityContext ctx, boolean fx) {
		ServerPlayer p = ctx.player();
		HardLightBladeItem.purge(p);
		ctx.setResource("blade_until", 0, 1.0e12f);
		ctx.setResource("blade_left", 0, HardLightBladeItem.LIFETIME_TICKS);
		if (fx) {
			Vec3 hand = AbilityHelpers.handPosition(p);
			ctx.level().sendParticles(BatchDFx.LIGHT, hand.x, hand.y, hand.z, 14, 0.2, 0.3, 0.2, 0.02);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8f, 1.6f);
		}
		ctx.triggerCooldown(BLADE_COOLDOWN);
	}

	// ================================================================================ N: Prism Shield

	private static void prismStart(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!ExperimentalPowers.state(p).resources.containsKey(KEY + "/prism")) {
			ctx.setResource("prism", MAX_PRISM, MAX_PRISM);
		}
		if (ctx.resource("prism") < 10.0f) {
			ctx.actionBar("message.projecthero.light.prism_low");
			return;
		}
		ctx.setResource("prism_on", 1, 1);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, PRISM_SLOW, -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		MutationVisuals.play(p, "shield_brace");
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
	}

	private static void prismEnd(ServerPlayer p) {
		Power power = power();
		if (power == null) {
			return;
		}
		if (ExperimentalPowers.getResource(p, power, "prism_on") > 0.5f) {
			MutationVisuals.stopIf(p, "shield_brace");
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 1.6f);
		}
		ExperimentalPowers.setResource(p, power, "prism_on", 0, 1);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, PRISM_SLOW);
	}

	private static void prismTick(AbilityContext ctx) {
		if (ctx.resource("prism_on") < 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		ctx.addResource("prism", -PRISM_DRAIN, MAX_PRISM);
		if (ctx.resource("prism") <= 0.0f || !p.isAlive()) {
			prismEnd(p);
			ctx.actionBar("message.projecthero.light.prism_low");
			return;
		}
		MutationVisuals.ensure(p, "shield_brace");
		Vec3 look = p.getLookAngle();
		Vec3 eye = p.getEyePosition();
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(5.0),
				pr -> pr.isAlive() && pr.getOwner() != p)) {
			Vec3 to = proj.position().subtract(eye);
			Vec3 vel = proj.getDeltaMovement();
			if (to.lengthSqr() < 1.0e-4 || to.normalize().dot(look) < 0.25 || vel.dot(to) > 0.0) {
				continue; // behind the shield, or not coming at us
			}
			if (proj.deflect(ProjectileDeflection.REVERSE, p, p, true)) {
				proj.setDeltaMovement(proj.getDeltaMovement().scale(1.2));
				proj.hurtMarked = true;
				level.sendParticles(BatchDFx.PRISM, proj.getX(), proj.getY(), proj.getZ(), 10, 0.2, 0.2, 0.2, 0.0);
				level.sendParticles(ParticleTypes.END_ROD, proj.getX(), proj.getY(), proj.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
				AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 1.6f);
				ctx.addResource("prism", -PRISM_PER_REFLECT, MAX_PRISM);
			}
		}
		if (p.tickCount % 2 == 0) {
			Vec3 c = eye.add(look.scale(0.9)).add(0, -0.4, 0);
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
			for (int i = 0; i < 3; i++) {
				double s = (level.random.nextDouble() - 0.5) * 1.6;
				double u = (level.random.nextDouble() - 0.5) * 1.6;
				Vec3 at = c.add(right.scale(s)).add(0, u, 0);
				level.sendParticles(BatchDFx.PRISM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
	}

	/** True while the Prism Shield is held (server). */
	public static boolean prismActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.getResource(p, power, "prism_on") > 0.5f;
	}

	/**
	 * The Prism Shield's damage rule (from {@code HeroDamageRules}): a projectile or beam coming at your front is
	 * refracted away -- no damage -- and bounced back into whoever sent it at 60% strength. Melee still lands.
	 */
	public static boolean prismReflects(ServerPlayer player, DamageSource source, float amount) {
		if (!prismActive(player)) {
			return false;
		}
		net.minecraft.world.entity.Entity direct = source.getDirectEntity();
		boolean ranged = source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypes.MAGIC)
				|| source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.SONIC_BOOM)
				|| source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.LIGHTNING_BOLT)
				|| (direct != null && direct.distanceTo(player) > 4.0);
		Vec3 from = source.getSourcePosition();
		if (!ranged || from == null) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		Vec3 to = from.subtract(player.getEyePosition());
		if (look.x * to.x + look.z * to.z <= 0.0) {
			return false; // it came from behind
		}
		Power power = power();
		ExperimentalPowers.addResource(player, power, "prism", -Math.min(30.0f, amount * 1.5f), MAX_PRISM);
		if (player.level() instanceof ServerLevel sl) {
			Vec3 c = player.getEyePosition().add(look.scale(0.9));
			sl.sendParticles(BatchDFx.PRISM, c.x, c.y, c.z, 16, 0.4, 0.4, 0.4, 0.0);
			if (source.getEntity() instanceof LivingEntity sender && sender != player && sender.isAlive()
					&& !Squads.areAllies(player, sender)) {
				AbilityHelpers.line(sl, c, BatchDFx.centre(sender), ParticleTypes.END_ROD, 2.0);
				AbilityHelpers.hurt(player, sender, amount * 0.6f);
			}
		}
		AbilityHelpers.sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.8f);
		return true;
	}

	// ================================================================================ visibility helpers

	/** True while the cloaking toggle is on -- read from the owner-synced attachment (server + own client). */
	public static boolean cloaked(net.minecraft.world.entity.player.Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.activeToggles.contains(KEY + "/cloaking_toggle");
	}

	/** True while Sparkling Flight is channelling -- read from the owner-synced attachment. */
	public static boolean sparkleFlying(net.minecraft.world.entity.player.Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY)
				&& st.resources.getOrDefault(KEY + "/sparkling", 0.0f) > 0.5f;
	}

	/**
	 * The player's whole appearance -- armour and (v0.13.22) held items -- is hidden while cloaked, sparkle-flying or
	 * Shadow Walking. v0.13.22: also reads the all-viewer {@code MutationVisuals} flags, because the experimental
	 * state only ever syncs to its owner -- before this, other players still saw a cloaked player's armour.
	 */
	public static boolean hideArmor(net.minecraft.world.entity.player.Player p) {
		return cloaked(p) || sparkleFlying(p) || MutationVisuals.hasFlag(p, "p15.cloak")
				|| MutationVisuals.hasFlag(p, "p19.shadow_walk");
	}

	private static final float MAX_SPARKLE = 115.0f;
	private static final float SPARKLE_DRAIN = 100.0f / (25 * 20);
	private static final float SPARKLE_REGEN = MAX_SPARKLE / (18 * 20);

	private static MobEffectInstance inf() {
		return new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
	}
}
