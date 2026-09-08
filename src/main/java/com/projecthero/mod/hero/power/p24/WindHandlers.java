package com.projecthero.mod.hero.power.p24;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.StanceMode;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Power 24 — Wind Manipulation (v0.10.9 pass). */
public final class WindHandlers {
	private static final String KEY = "power_24_wind_manipulation";
	private static final float MAX_WIND = 500.0f;
	private static final float WIND_DRAIN = MAX_WIND / (20 * 20); // tailwind holds ~20 s
	private static final float WIND_REGEN = MAX_WIND / (30 * 20);

	private static final ResourceLocation JUMP_ID = com.projecthero.mod.ProjectHeroMod.id("wind_jump");
	private static final ResourceLocation SPRINT_ID = com.projecthero.mod.ProjectHeroMod.id("wind_sprint");
	private static final ResourceLocation TAILWIND_STEP = com.projecthero.mod.ProjectHeroMod.id("tailwind_step");
	private static final ResourceLocation TAILWIND_ATK = com.projecthero.mod.ProjectHeroMod.id("tailwind_atk");

	/** shift+R wide wind blade: hold ~2 s, then a piercing corridor for 16 damage. 22 s cooldown. */
	private static final int BLADE_CHARGE = 40;
	private static final int BLADE_CD = 22 * 20;
	/** Hurricane: hold Z ~5 s, then 11 s of storm, 8 dmg/s. 60 s cooldown. */
	private static final int HURR_CHARGE = 100;
	private static final int HURR_DURATION = 11 * 20;
	private static final int HURR_CD = 60 * 20;

	private WindHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	private static boolean tailwindActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	/** Tailwind adds a flat bonus to every Wind ability's damage (see {@link StanceMode}). */
	private static float windBonus(ServerPlayer p) {
		return tailwindActive(p) ? StanceMode.ABILITY_BONUS : 0.0f;
	}

	public static void register() {
		// R: a quick wind slash. Sneak + hold R ~2 s: a wide piercing wind blade.
		AbilityHandlers.register(KEY, "wind_blade", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (ExperimentalPowers.cooldownReady(p, ctx.power(), ctx.ability())) {
						ctx.setResource("blade_charge", BLADE_CHARGE, BLADE_CHARGE);
						AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 1.0f, 1.0f);
					}
					return;
				}
				if (!ctx.cooldownReady()) {
					return;
				}
				LivingEntity t = AbilityHelpers.raycastEntity(p, 26.0);
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 26.0),
						ParticleTypes.SWEEP_ATTACK, 2.0);
				if (t != null) {
					AbilityHelpers.hurt(p, t, 8.0f + windBonus(p));
					AbilityHelpers.knockbackFrom(t, p.position(), 0.7);
				}
				AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.0f, 1.4f);
				ctx.triggerCooldown();
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("blade_charge") > 0.5f) {
					ctx.setResource("blade_charge", 0, BLADE_CHARGE);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				windBladeTick(ctx);
			}
		});

		// Wind Burst: 12 damage in a 6-block cone ahead of the player, with far pushback.
		AbilityHandlers.register(KEY, "tornado", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 eye = p.getEyePosition();
			Vec3 look = p.getLookAngle();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(3.0)), 6.5)) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
				if (to.length() > 6.0 || to.normalize().dot(look) < 0.6) {
					continue;
				}
				AbilityHelpers.hurt(p, e, 12.0f + windBonus(p));
				AbilityHelpers.knockbackFrom(e, p.position(), 3.2);
				AbilityHelpers.push(e, look.scale(1.6).add(0, 0.35, 0));
			}
			for (var proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(6.0))) {
				proj.setDeltaMovement(look.scale(2.5));
			}
			for (int i = 1; i <= 6; i++) {
				Vec3 pt = eye.add(look.scale(i));
				ctx.level().sendParticles(ParticleTypes.CLOUD, pt.x, pt.y, pt.z, 10, 0.3 * i, 0.3 * i, 0.3 * i, 0.05);
			}
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.3f, 0.7f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "wind_flight", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				if (!HeroFlight.startFlying(ctx.player(), ctx.power())) {
					ctx.setToggled(false);
				}
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				HeroFlight.setFlying(ctx.player(), false);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				if (!HeroFlight.isFlying(ctx.player())) {
					ctx.setToggled(false);
				} else if (ctx.player().tickCount % 4 == 0) {
					ctx.level().sendParticles(ParticleTypes.CLOUD, ctx.player().getX(), ctx.player().getY(),
							ctx.player().getZ(), 2, 0.3, 0.1, 0.3, 0.0);
				}
			}
		});

		// Hurricane: hold Z ~5 s to charge, then an 11-second storm — 8 dmg/s, 15-block radius, howling
		// wind and driven cloud the whole time. 60 s cooldown.
		AbilityHandlers.register(KEY, "hurricane", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				hurricanePress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				hurricaneRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				hurricaneChargeTick(ctx);
				hurricaneStormTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "wind_push", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(4)), 4.5)) {
				AbilityHelpers.knockbackFrom(e, p.position(), 4.5);
				AbilityHelpers.push(e, p.getLookAngle().scale(2.2).add(0, 0.3, 0));
				if (windBonus(p) > 0) {
					AbilityHelpers.hurt(p, e, windBonus(p));
				}
			}
			for (Projectile proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(6.0))) {
				proj.setDeltaMovement(p.getLookAngle().scale(2.0));
			}
			ctx.level().sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 6, 1.5, 0.5, 1.5, 0.0);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.2f, 0.9f);
			ctx.triggerCooldown();
		}));

		// Tailwind: high jumps, faster movement, a 2-block step and a shoving gust. +10 ability / +8 melee
		// while worn, 20 s cooldown once dropped. No longer grants Slow Falling.
		AbilityHandlers.register(KEY, "tailwind", Handlers.toggle(
				ctx -> {
					if (StanceMode.blockedByCooldown(ctx)) {
						return;
					}
					ModeMeter.ensureSeeded(ctx, "tailwind", MAX_WIND);
					if (!ModeMeter.hasCharge(ctx, "tailwind", 40.0f)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.wind.wind_low");
					}
				},
				ctx -> {
					tailwindOff(ctx.player());
					StanceMode.startDeactivateCooldown(ctx);
				},
				ctx -> {
					ServerPlayer p = ctx.player();
					p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, false, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 3, false, false, false));
					PowerToggles.modifier(p, Attributes.STEP_HEIGHT, TAILWIND_STEP, 2.0, AttributeModifier.Operation.ADD_VALUE);
					PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, TAILWIND_ATK, StanceMode.MELEE_BONUS,
							AttributeModifier.Operation.ADD_VALUE);
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 2.0)) {
						Vec3 away = e.position().subtract(p.position());
						double d = away.horizontalDistance();
						if (d > 0.05) {
							AbilityHelpers.push(e, new Vec3(away.x / d, 0.15, away.z / d));
						}
					}
					if (p.tickCount % 6 == 0) {
						ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3, 0.0);
					}
					if (!ModeMeter.drain(ctx, "tailwind", MAX_WIND, WIND_DRAIN)) {
						ctx.setToggled(false);
						tailwindOff(p);
						StanceMode.startDeactivateCooldown(ctx);
						ctx.actionBar("message.projecthero.wind.wind_out");
					}
				}));

		// Elytra: right-click (holding any item) while gliding to boost forward — no rocket needed.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			InteractionResultHolder<net.minecraft.world.item.ItemStack> pass =
					InteractionResultHolder.pass(player.getItemInHand(hand));
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !sp.isFallFlying()) {
				return pass;
			}
			Power power = power();
			if (power == null || !ExperimentalPowers.owns(sp, power)
					|| !sp.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
				return pass;
			}
			long now = level.getGameTime();
			if (ExperimentalPowers.getResource(sp, power, "elytra_cd") >= now) {
				return pass;
			}
			ExperimentalPowers.setResource(sp, power, "elytra_cd", now + 15, 1e12f);
			AbilityHelpers.addImpulse(sp, sp.getLookAngle().scale(1.35));
			((ServerLevel) level).sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY(), sp.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
			level.playSound(null, sp.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.3f);
			return pass;
		});

		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPRINT_ID);
				tailwindOff(player);
			}
		});

		PowerPassives.registerTick(KEY, WindHandlers::passiveTick);
	}

	private static void tailwindOff(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, TAILWIND_STEP);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, TAILWIND_ATK);
	}

	// ---- passive traits (run for every owned Wind player) --------------------------------------

	private static void passiveTick(ServerPlayer p) {
		Power power = power();
		ModeMeter.regen(p, power, "tailwind", MAX_WIND, WIND_REGEN, tailwindActive(p));

		// A 3-block standing jump.
		PowerToggles.modifier(p, Attributes.JUMP_STRENGTH, JUMP_ID, 0.55, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

		// 30% quicker while sprinting.
		if (p.isSprinting()) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID, 0.30,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID);
		}

		// Hold sneak while falling: glide down under Slow Falling with far better mid-air control.
		if (p.isShiftKeyDown() && !p.onGround() && !p.isFallFlying() && p.getDeltaMovement().y < 0.0) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false, false));
		}

		// Drowns 50% slower — feed the air bar back up every other tick while it is draining.
		if (p.isUnderWater() && p.getAirSupply() < p.getMaxAirSupply() && p.getAirSupply() > -18
				&& p.tickCount % 2 == 0) {
			p.setAirSupply(Math.min(p.getMaxAirSupply(), p.getAirSupply() + 1));
		}
	}

	// ---- shift+R: wide wind blade -------------------------------------------------------------

	private static void windBladeTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float charge = ctx.resource("blade_charge");
		if (charge > 0.5f) {
			if (!p.isShiftKeyDown()) {
				ctx.setResource("blade_charge", 0, BLADE_CHARGE);
				return;
			}
			charge -= 1.0f;
			ctx.setResource("blade_charge", charge, BLADE_CHARGE);
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.4, 1.0, 0.4));
			if (p.tickCount % 3 == 0) {
				ctx.level().sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.5, 0.4, 0.5, 0.0);
			}
			if (charge <= 0.5f) {
				fireWindBlade(ctx);
			}
			return;
		}
		// travelling visual after the release
		float show = ctx.resource("blade_show");
		if (show > 0.5f) {
			show -= 1.0f;
			ctx.setResource("blade_show", show, 26);
			// nothing else — the corridor damage already happened on fire
		}
	}

	private static void fireWindBlade(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(13.0)), 15.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double along = to.dot(look);
			if (along < 1.0 || along > 26.0) {
				continue;
			}
			double perp = to.subtract(look.scale(along)).length();
			if (perp > 3.0) {
				continue; // the blade is wide but still a corridor
			}
			AbilityHelpers.hurt(p, e, 16.0f + windBonus(p));
			AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
		}
		// a wall of sweep particles fired out along the corridor
		Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
		Vec3 up = new Vec3(0, 1, 0);
		for (double d = 1.0; d <= 26.0; d += 1.0) {
			Vec3 c = eye.add(look.scale(d));
			for (double w = -3.0; w <= 3.0; w += 1.0) {
				for (double h = -2.0; h <= 2.0; h += 1.0) {
					Vec3 pt = c.add(side.scale(w)).add(up.scale(h));
					level.sendParticles(ParticleTypes.CLOUD, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}
		level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.6f, 0.6f);
		ctx.setResource("blade_show", 12, 26);
		ctx.triggerCooldown(BLADE_CD);
	}

	// ---- Z: Hurricane charge + storm --------------------------------------------------------

	private static void hurricanePress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("hurr_start") > 0.5f || ctx.resource("hurr") > 0.5f) {
			return;
		}
		if (!ExperimentalPowers.cooldownReady(p, ctx.power(), ctx.ability())) {
			ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f",
							Math.ceil(ExperimentalPowers.cooldownRemainingTicks(p, ctx.power(), ctx.ability()) / 20.0f)));
			return;
		}
		ctx.setResource("hurr_start", p.level().getGameTime(), 1e12f);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 1.0f, 0.5f);
	}

	private static void hurricaneRelease(AbilityContext ctx) {
		if (ctx.resource("hurr_start") <= 0.5f) {
			return;
		}
		long held = ctx.player().level().getGameTime() - (long) ctx.resource("hurr_start");
		if (held >= HURR_CHARGE) {
			hurricaneFire(ctx);
		} else {
			ctx.setResource("hurr_start", 0, 1e12f);
			ctx.setResource("ult_charge", 0, 100);
			AbilityHelpers.sound(ctx.player(), SoundEvents.BREEZE_DEFLECT, 0.5f, 1.2f);
		}
	}

	private static void hurricaneChargeTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float start = ctx.resource("hurr_start");
		if (start <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > HURR_CHARGE + 100) {
			ctx.setResource("hurr_start", 0, 1e12f);
			ctx.setResource("ult_charge", 0, 100);
			return;
		}
		ctx.setResource("ult_charge", Math.min(100f, held * 100f / HURR_CHARGE), 100);
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 1.0, 0.3));
		double frac = Math.min(1.0, held / (double) HURR_CHARGE);
		for (int i = 0; i < 4 + (int) (frac * 14); i++) {
			double a = p.level().random.nextDouble() * Math.PI * 2;
			double rad = 0.8 + p.level().random.nextDouble() * (1.0 + frac * 3.0);
			ctx.level().sendParticles(ParticleTypes.CLOUD,
					p.getX() + Math.cos(a) * rad, p.getY() + p.level().random.nextDouble() * 2.0,
					p.getZ() + Math.sin(a) * rad, 1, 0, 0, 0, 0);
		}
		if (held % 12 == 0) {
			AbilityHelpers.sound(p, SoundEvents.BREEZE_IDLE_GROUND, 0.6f, 0.4f + (float) frac);
		}
		if (held >= HURR_CHARGE) {
			hurricaneFire(ctx);
		}
	}

	private static void hurricaneFire(AbilityContext ctx) {
		ctx.setResource("hurr_start", 0, 1e12f);
		ctx.setResource("ult_charge", 0, 100);
		ctx.setResource("hurr", HURR_DURATION, HURR_DURATION);
		ctx.triggerCooldown(HURR_CD);
		AbilityHelpers.sound(ctx.player(), SoundEvents.BREEZE_IDLE_GROUND, 1.6f, 0.3f);
	}

	private static void hurricaneStormTick(AbilityContext ctx) {
		int t = (int) ctx.resource("hurr");
		if (t <= 0) {
			return;
		}
		ctx.setResource("hurr", t - 1, HURR_DURATION);
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (t % 20 == 0) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 15.0)) {
				Vec3 tangent = new Vec3(-(e.getZ() - p.getZ()), 0.35, e.getX() - p.getX()).normalize().scale(0.8);
				AbilityHelpers.push(e, tangent);
				AbilityHelpers.hurt(p, e, 8.0f + windBonus(p));
			}
		}
		if (t % 5 == 0) {
			for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(15.0))) {
				proj.setDeltaMovement(proj.getDeltaMovement().reverse());
			}
		}
		double a = t * 0.5;
		for (double rr = 3; rr <= 15; rr += 3) {
			for (double off = 0; off < Math.PI * 2; off += Math.PI / 2) {
				level.sendParticles(ParticleTypes.CLOUD, p.getX() + Math.cos(a + off) * rr, p.getY() + (t % 6) * 0.4,
						p.getZ() + Math.sin(a + off) * rr, 2, 0.2, 0.4, 0.2, 0.02);
			}
		}
		if (t % 14 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 1.4f, 0.35f);
			level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.0f, 0.6f);
		}
	}
}
