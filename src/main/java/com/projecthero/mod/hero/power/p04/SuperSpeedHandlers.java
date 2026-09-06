package com.projecthero.mod.hero.power.p04;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Power 04 — Super Speed. */
public final class SuperSpeedHandlers {
	private static final String KEY = "power_04_super_speed";

	/** Absolute game time until which Overdrive is running (0 = off). Persisted; read client-side too. */
	public static final String OVERDRIVE_UNTIL = "overdrive_until";
	private static final int OVERDRIVE_TICKS = 25 * 20;

	private static final ResourceLocation PASSIVE_STEP = com.projecthero.mod.ProjectHeroMod.id("speed_passive_step");

	private static final ResourceLocation SM_SPEED = com.projecthero.mod.ProjectHeroMod.id("speed_mode_speed");
	private static final ResourceLocation SM_ATTACK = com.projecthero.mod.ProjectHeroMod.id("speed_mode_attack_speed");
	private static final ResourceLocation SM_STEP = com.projecthero.mod.ProjectHeroMod.id("speed_mode_step");
	private static final ResourceLocation SM_WATER = com.projecthero.mod.ProjectHeroMod.id("speed_mode_water");
	private static final ResourceLocation SM_FALL = com.projecthero.mod.ProjectHeroMod.id("speed_mode_fall");

	private static final ResourceLocation OD_SPEED = com.projecthero.mod.ProjectHeroMod.id("overdrive_speed");
	private static final ResourceLocation OD_ATTACK = com.projecthero.mod.ProjectHeroMod.id("overdrive_attack_speed");
	private static final ResourceLocation OD_FALL = com.projecthero.mod.ProjectHeroMod.id("overdrive_fall");

	private SuperSpeedHandlers() {
	}

	public static void register() {
		AbilityHandlers.register(KEY, "speed_blitz", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float m = overdriveMult(p);
			LivingEntity target = AbilityHelpers.raycastEntity(p, 14.0);
			if (target != null) {
				Vec3 to = target.position().subtract(p.getLookAngle().scale(1.5));
				if (safe(ctx.level(), to)) {
					p.teleportTo(to.x, to.y, to.z);
				}
				AbilityHelpers.hurt(p, target, 10.0f * m);
				AbilityHelpers.knockbackFrom(target, p.position(), 0.4 * m);
			} else {
				AbilityHelpers.addImpulse(p, p.getLookAngle().scale(1.6 * selfMult(m)));
			}
			trail(ctx.level(), p);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.6f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "rapid_assault", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float m = overdriveMult(p);
			int hits = 0;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(1.5)), 3.5)) {
				for (int i = 0; i < 4; i++) {
					AbilityHelpers.hurt(p, e, 2.0f * m);
				}
				AbilityHelpers.knockbackFrom(e, p.position(), 0.3 * m);
				hits++;
			}
			trail(ctx.level(), p);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.8f);
			if (hits == 0) {
				return;
			}
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "momentum_dash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float m = selfMult(overdriveMult(p));
			Vec3 v = p.getDeltaMovement();
			Vec3 dir = (v.horizontalDistanceSqr() > 0.01) ? new Vec3(v.x, 0, v.z).normalize() : p.getLookAngle();
			AbilityHelpers.launchSelf(p, new Vec3(dir.x * 1.7 * m, 0.25, dir.z * 1.7 * m));
			trail(ctx.level(), p);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "overdrive", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ctx.setResource(OVERDRIVE_UNTIL, p.level().getGameTime() + OVERDRIVE_TICKS, 1e12f);
			applyOverdrive(p);
			p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, OVERDRIVE_TICKS, 2, false, true, true));
			overdriveBurst(ctx.level(), p);
			// A real detonation, not a trickle.
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.5f);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, 1.3f);
			ctx.triggerCooldown();
		}));

		// Whirlwind: hold to spin, up to 8 s; releasing (or the timer running out) starts an 8 s cooldown.
		AbilityHandlers.register(KEY, "whirlwind", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("whirl_ticks") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
					return;
				}
				ctx.setResource("whirl_ticks", 160, 160);
				AbilityHelpers.sound(ctx.player(), SoundEvents.WIND_CHARGE_THROW, 1.0f, 1.2f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endWhirl(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float t = ctx.resource("whirl_ticks");
				if (t <= 0.5f) {
					return;
				}
				whirlTick(ctx);
				t -= 1.0f;
				ctx.setResource("whirl_ticks", t, 160);
				if (t <= 0.5f) {
					endWhirl(ctx);
				}
			}
		});

		AbilityHandlers.register(KEY, "speed_mode", Handlers.toggle(
				ctx -> speedModeApply(ctx.player()),
				ctx -> speedModeClear(ctx.player()),
				ctx -> {
					speedModeApply(ctx.player());
					AbilityHelpers.modeAura(ctx.player(), ParticleTypes.CRIT, 3);
				}));

		PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				PowerToggles.modifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP, 0.6, AttributeModifier.Operation.ADD_VALUE);
			} else {
				PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP);
				speedModeClear(player);
				clearOverdrive(player);
				ExperimentalPowers.setResource(player, Powers.byKey(KEY), OVERDRIVE_UNTIL, 0, 1e12f);
			}
		});
		PowerPassives.registerTick(KEY, SuperSpeedHandlers::serverTick);
	}

	private static void endWhirl(AbilityContext ctx) {
		if (ctx.resource("whirl_ticks") > 0.5f) {
			ctx.setResource("whirl_ticks", 0, 160);
			ctx.triggerCooldown();
		}
	}

	private static void whirlTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float m = overdriveMult(p);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 4.0)) {
			AbilityHelpers.knockbackFrom(e, p.position(), 0.9 * m);
		}
		for (var proj : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
				p.getBoundingBox().inflate(4.0))) {
			proj.setDeltaMovement(proj.getDeltaMovement().reverse().scale(0.6));
		}
		p.clearFire();
		if (p.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 4, 1.6, 0.5, 1.6, 0.1);
			AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_THROW, 0.5f, 1.6f);
		}
	}

	private static void serverTick(ServerPlayer player) {
		com.projecthero.mod.hero.power.PowerCombos.speedGeneratesCharge(player);

		Power power = Powers.byKey(KEY);
		float until = ExperimentalPowers.getResource(player, power, OVERDRIVE_UNTIL);
		boolean overdrive = until > player.level().getGameTime();
		if (overdrive) {
			applyOverdrive(player);
		} else if (until > 0.0f) {
			clearOverdrive(player);
			ExperimentalPowers.setResource(player, power, OVERDRIVE_UNTIL, 0, 1e12f);
		}

		boolean speedMode = ExperimentalPowers.isToggled(player, power,
				power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6));

		if (!(player.level() instanceof ServerLevel sl)) {
			return;
		}

		Vec3 v = player.getDeltaMovement();
		boolean moving = v.horizontalDistanceSqr() > 0.02;

		// Speed Mode burns hunger 50% faster than normal as a balancing cost.
		if (speedMode && !player.getAbilities().instabuild && moving) {
			player.getFoodData().addExhaustion(0.05f);
		}

		// Bursting-with-power visuals while Overdrive is up.
		if (overdrive && player.tickCount % 2 == 0) {
			overdriveBurst(sl, player);
		}

		// Run THROUGH living things: anything within ~2 blocks while you're moving fast takes a hit.
		if ((speedMode || overdrive) && moving) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.position(), 2.0)) {
				if (e.invulnerableTime <= 0) {
					AbilityHelpers.hurt(player, e, 5.0f);
					AbilityHelpers.knockbackFrom(e, player.position(), 0.5);
				}
			}
		}

		// Running on water: splash and footfall feedback so it reads as running, not gliding.
		if ((speedMode || overdrive) && moving && !player.isShiftKeyDown()) {
			BlockPos feet = player.blockPosition();
			var fluid = sl.getFluidState(feet);
			if (fluid.is(FluidTags.WATER) && sl.getFluidState(feet.above()).isEmpty()) {
				double surfaceY = feet.getY() + fluid.getHeight(sl, feet);
				if (player.getY() >= surfaceY - 1.0 && player.getY() <= surfaceY + 0.6) {
					sl.sendParticles(ParticleTypes.SPLASH, player.getX(), surfaceY, player.getZ(),
							8, 0.3, 0.05, 0.3, 0.05);
					if (player.tickCount % 6 == 0) {
						sl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH,
								net.minecraft.sounds.SoundSource.PLAYERS, 0.35f, 1.3f);
					}
				}
			}
		}

		// Sprint trail only while Speed Mode is toggled on -- not on every ordinary sprint.
		if (speedMode && player.isSprinting() && player.tickCount % 3 == 0) {
			bodyTrail(sl, player, 1);
		}
	}

	private static void overdriveBurst(ServerLevel level, ServerPlayer p) {
		double h = p.getBbHeight();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + h * 0.5, p.getZ(),
				14, 0.5, h * 0.5, 0.5, 0.25);
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + h * 0.6, p.getZ(),
				2, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + h * 0.5, p.getZ(),
				10, 0.5, h * 0.5, 0.5, 0.2);
	}

	/**
	 * The movement/mining/eating multiplier of the current Super Speed state, read from the synced
	 * attachment so it works on both sides. 1.0 = no boost.
	 */
	public static float speedFactor(Player player) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(KEY)) {
			return 1.0f;
		}
		Float until = st.resources.get(KEY + "/" + OVERDRIVE_UNTIL);
		boolean overdrive = until != null && until > player.level().getGameTime();
		boolean speedMode = st.activeToggles.contains(KEY + "/speed_mode");
		if (overdrive && speedMode) {
			return 8.0f;
		}
		if (overdrive) {
			return 8.0f;
		}
		return speedMode ? 4.0f : 1.0f;
	}

	private static float overdriveMult(ServerPlayer p) {
		return ExperimentalPowers.getResource(p, Powers.byKey(KEY), OVERDRIVE_UNTIL) > p.level().getGameTime() ? 2.0f : 1.0f;
	}

	private static float selfMult(float overdriveMult) {
		return 1.0f + (overdriveMult - 1.0f) * 0.5f;
	}

	private static void speedModeApply(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SM_SPEED, 3.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, SM_ATTACK, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, SM_STEP, 0.8, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SM_WATER, 1.0, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, SM_FALL, -0.8, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void speedModeClear(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SM_SPEED);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, SM_ATTACK);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SM_STEP);
		PowerToggles.clearModifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SM_WATER);
		PowerToggles.clearModifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, SM_FALL);
	}

	private static void applyOverdrive(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, OD_SPEED, 7.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, OD_ATTACK, 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, OD_FALL, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void clearOverdrive(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, OD_SPEED);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, OD_ATTACK);
		PowerToggles.clearModifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, OD_FALL);
	}

	private static boolean safe(ServerLevel level, Vec3 pos) {
		var bp = BlockPos.containing(pos);
		return level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()
				&& level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty();
	}

	private static void trail(ServerLevel level, ServerPlayer p) {
		bodyTrail(level, p, 3);
	}

	/**
	 * The speed trail: a low scuff of particles right at the player's feet, trailing just behind the
	 * direction of travel. Deliberately foot-level only -- no full-body column.
	 */
	private static void bodyTrail(ServerLevel level, ServerPlayer p, int density) {
		Vec3 v = p.getDeltaMovement();
		Vec3 dir = v.horizontalDistanceSqr() > 1.0e-4 ? new Vec3(v.x, 0, v.z).normalize() : p.getLookAngle();
		double bx = p.getX() - dir.x * 0.5;
		double bz = p.getZ() - dir.z * 0.5;
		double y = p.getY() + 0.05;
		level.sendParticles(ParticleTypes.CLOUD, bx, y, bz, density, 0.15, 0.02, 0.15, 0.004);
		level.sendParticles(ParticleTypes.CRIT, bx, y + 0.1, bz, density, 0.15, 0.06, 0.15, 0.02);
	}
}
