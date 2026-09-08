package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.StanceMode;
import com.projecthero.mod.hero.power.TempBlocks;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Power 25 — Water Manipulation (v0.10.9 pass). */
public final class WaterHandlers {
	private static final String KEY = "power_25_water_manipulation";

	private static final float MAX_WATER = 500.0f;
	private static final float WATER_PER_SPRAY_TICK = 3.0f; // ~8 s of continuous spray
	private static final float WATER_REGEN = MAX_WATER / (18 * 20);

	private static final ResourceLocation AQUATIC_ATK = com.projecthero.mod.ProjectHeroMod.id("aquatic_atk");

	/** Z Tidal Wave: hold ~5 s to charge, then a 35-damage wall of water. */
	private static final int TIDAL_CHARGE = 100;
	private static final int TIDAL_CD = 45 * 20;
	private static final int PRISON_TICKS = 8 * 20;
	private static final int CREATE_TICKS = 80;

	private WaterHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	private static boolean aquaticActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	/** +5 to every Water ability's damage in water/rain/snow. */
	private static float waterBonus(ServerPlayer p) {
		boolean snow = p.isInPowderSnow
				|| p.level().getBlockState(p.blockPosition()).is(Blocks.SNOW)
				|| p.level().getBlockState(p.blockPosition().below()).is(Blocks.SNOW);
		return (p.isInWaterOrRain() || snow) ? 5.0f : 0.0f;
	}

	/** Aquatic Form adds a flat bonus on top (see {@link StanceMode}). */
	private static float aquaticBonus(ServerPlayer p) {
		return aquaticActive(p) ? StanceMode.ABILITY_BONUS : 0.0f;
	}

	private static float bonus(ServerPlayer p) {
		return waterBonus(p) + aquaticBonus(p);
	}

	public static void register() {
		// Water Shot: tap to fire. Sneak + hold R: spray a jet of water — puts out fire, deals 5,
		// drains the water bar.
		AbilityHandlers.register(KEY, "water_shot", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (ctx.resource("water") >= MAX_WATER - 20.0f) {
						ctx.actionBar("message.projecthero.water.dry");
						return;
					}
					ctx.setResource("spraying", 1, 1);
					return;
				}
				if (!ctx.cooldownReady()) {
					return;
				}
				LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 24.0),
						ParticleTypes.FALLING_WATER, 4.0);
				if (t != null) {
					AbilityHelpers.hurt(p, t, 8.0f + bonus(p));
					AbilityHelpers.knockbackFrom(t, p.position(), 1.0);
					t.setRemainingFireTicks(0);
					markWet(ctx, t);
				}
				AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH, 1.0f, 0.9f);
				ctx.triggerCooldown();
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("spraying", 0, 1);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("spraying") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (!p.isShiftKeyDown()) {
					ctx.setResource("spraying", 0, 1);
					return;
				}
				ServerLevel level = ctx.level();
				Vec3 origin = p.getEyePosition();
				Vec3 look = p.getLookAngle();
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, origin.add(look.scale(2.5)), 3.0)) {
					if (e.position().subtract(origin).normalize().dot(look) > 0.6) {
						AbilityHelpers.hurt(p, e, 5.0f + bonus(p) * 0.4f);
						e.setRemainingFireTicks(0);
						markWet(ctx, e);
					}
				}
				BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 6.0);
				double streamLen = bhr.getType() == HitResult.Type.BLOCK ? origin.distanceTo(bhr.getLocation()) : 6.0;
				for (double d = 0.5; d <= streamLen + 0.01; d += 0.5) {
					Vec3 pt = origin.add(look.scale(d));
					level.sendParticles(ParticleTypes.SPLASH, pt.x, pt.y, pt.z, 3, 0.1 * d, 0.1 * d, 0.1 * d, 0.01);
					BlockPos bp = BlockPos.containing(pt);
					if (level.getBlockState(bp).is(BlockTags.FIRE)) {
						level.removeBlock(bp, false);
					}
				}
				ctx.addResource("water", WATER_PER_SPRAY_TICK, MAX_WATER);
				if (ctx.resource("water") >= MAX_WATER) {
					ctx.setResource("spraying", 0, 1);
					ctx.actionBar("message.projecthero.water.dry");
				}
			}
		});

		AbilityHandlers.register(KEY, "water_whip", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(4));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 4.0)) {
				AbilityHelpers.hurt(p, e, 16.0f + bonus(p));
				AbilityHelpers.push(e, p.position().subtract(e.position()).normalize().scale(1.2));
				markWet(ctx, e);
			}
			AbilityHelpers.burst(ctx.level(), front, ParticleTypes.SPLASH, 20, 0.6);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "riptide", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			Vec3 look = p.getLookAngle();
			boolean wet = p.isInWaterOrRain() || p.isInWater();
			double strength = wet ? 3.4 : 2.5;
			Vec3 impulse = look.scale(strength).add(0.0, look.y < -0.1 ? 0.05 : 0.4, 0.0);
			AbilityHelpers.addImpulse(p, impulse);
			p.resetFallDistance();
			p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 80, 1, false, false, false));
			AbilityHelpers.line(level, p.getEyePosition(), p.getEyePosition().add(look.scale(6.0)),
					ParticleTypes.SPLASH, 3.0);
			AbilityHelpers.burst(level, p.position(), ParticleTypes.FALLING_WATER, 24, 0.5);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(look.scale(2.0)), 2.5)) {
				AbilityHelpers.hurt(p, e, 6.0f + bonus(p));
				AbilityHelpers.push(e, look.scale(1.2).add(0, 0.2, 0));
				markWet(ctx, e);
			}
			AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.1f);
			ctx.triggerCooldown();
		}));

		// Tidal Wave: hold Z ~5 s to charge, then a 35-damage surge that sweeps everything caught in it
		// off along the direction it was aimed.
		AbilityHandlers.register(KEY, "tidal_wave", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				tidalPress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				tidalRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				tidalChargeTick(ctx);
			}
		});

		// Water Prison: seal a target in water for 8 s — pinned, but not drowned. Press again to free
		// them early. Sneak + hold V: shape a water source where you look (right-click a source while it
		// charges to siphon that source away instead).
		AbilityHandlers.register(KEY, "water_prison", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					ctx.setResource("create_ticks", CREATE_TICKS, CREATE_TICKS);
					AbilityHelpers.sound(p, SoundEvents.BUCKET_FILL, 0.7f, 0.9f);
					return;
				}
				if (ctx.resource("prison_ticks") > 0.5f) {
					releasePrison(ctx);
					return;
				}
				LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
				if (t == null) {
					return;
				}
				ctx.setResource("prison_id", t.getId(), 1e9f);
				ctx.setResource("prison_ticks", PRISON_TICKS, PRISON_TICKS);
				ctx.setResource("prison_x", (float) t.getX(), 1e9f);
				ctx.setResource("prison_y", (float) t.getY(), 1e9f);
				ctx.setResource("prison_z", (float) t.getZ(), 1e9f);
				AbilityHelpers.sound(p, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0f, 0.8f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("create_ticks") > 0.5f) {
					ctx.setResource("create_ticks", 0, CREATE_TICKS);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				prisonTick(ctx);
				createTick(ctx);
			}
		});

		// Aquatic Form: doubles the passive water buffs, +10 ability / +8 melee, 20 s cooldown once off.
		AbilityHandlers.register(KEY, "aquatic_form", Handlers.toggle(
				ctx -> {
					if (StanceMode.blockedByCooldown(ctx)) {
						return;
					}
				},
				ctx -> {
					PowerToggles.clearModifier(ctx.player(), Attributes.ATTACK_DAMAGE, AQUATIC_ATK);
					StanceMode.startDeactivateCooldown(ctx);
				},
				ctx -> {
					ServerPlayer p = ctx.player();
					PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, AQUATIC_ATK, StanceMode.MELEE_BONUS,
							AttributeModifier.Operation.ADD_VALUE);
					p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20, 2, false, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 20, 0, false, false, false));
					if (p.isInWater()) {
						p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 2, false, false, false));
					}
					AbilityHelpers.modeAura(p, ParticleTypes.FALLING_WATER, 4);
					if (p.tickCount % 10 == 0) {
						AbilityHelpers.modeAura(p, ParticleTypes.BUBBLE, 3);
					}
				}));

		// Right-click a water source while sneak-holding V (creation charging) to siphon it away.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			Power power = power();
			if (power == null || ExperimentalPowers.getResource(sp, power, "create_ticks") <= 0.5f) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (level.getBlockState(pos).getFluidState().is(Fluids.WATER) && level.getBlockState(pos).getFluidState().isSource()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				((ServerLevel) level).sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						12, 0.3, 0.3, 0.3, 0.0);
				level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8f, 1.1f);
				ExperimentalPowers.setResource(sp, power, "create_ticks", 0, CREATE_TICKS);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, AQUATIC_ATK);
			}
		});

		// Passive: the old Aquatic Mode buffs, plus the water-bar regen.
		PowerPassives.registerTick(KEY, player -> {
			ModeMeter.cool(player, power(), "water", MAX_WATER, WATER_REGEN,
					ExperimentalPowers.getResource(player, power(), "spraying") > 0.5f);
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 25, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 25, 0, false, false, false));
			if (player.isInWater()) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 25, 1, false, false, false));
				player.setAirSupply(player.getMaxAirSupply());
			}
			if (player.isInWater() && player.getAirSupply() < player.getMaxAirSupply()) {
				player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 4));
			}
		});
	}

	// ---- Z: Tidal Wave charge ----------------------------------------------------------------

	private static void tidalPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("tidal_start") > 0.5f) {
			return;
		}
		if (!ExperimentalPowers.cooldownReady(p, ctx.power(), ctx.ability())) {
			ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f",
							Math.ceil(ExperimentalPowers.cooldownRemainingTicks(p, ctx.power(), ctx.ability()) / 20.0f)));
			return;
		}
		ctx.setResource("tidal_start", p.level().getGameTime(), 1e12f);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(p, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.0f, 0.5f);
	}

	private static void tidalRelease(AbilityContext ctx) {
		if (ctx.resource("tidal_start") <= 0.5f) {
			return;
		}
		long held = ctx.player().level().getGameTime() - (long) ctx.resource("tidal_start");
		if (held >= TIDAL_CHARGE) {
			tidalFire(ctx);
		} else {
			ctx.setResource("tidal_start", 0, 1e12f);
			ctx.setResource("ult_charge", 0, 100);
			AbilityHelpers.sound(ctx.player(), SoundEvents.GENERIC_SPLASH, 0.4f, 1.2f);
		}
	}

	private static void tidalChargeTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float start = ctx.resource("tidal_start");
		if (start <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > TIDAL_CHARGE + 100) {
			ctx.setResource("tidal_start", 0, 1e12f);
			ctx.setResource("ult_charge", 0, 100);
			return;
		}
		ctx.setResource("ult_charge", Math.min(100f, held * 100f / TIDAL_CHARGE), 100);
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 1.0, 0.3));
		double frac = Math.min(1.0, held / (double) TIDAL_CHARGE);
		ctx.level().sendParticles(ParticleTypes.FALLING_WATER, p.getX(), p.getY() + 1.0, p.getZ(),
				6 + (int) (frac * 16), 0.6 * frac + 0.4, 0.6, 0.6 * frac + 0.4, 0.02);
		if (held % 16 == 0) {
			AbilityHelpers.sound(p, SoundEvents.AMBIENT_UNDERWATER_LOOP, 0.7f, 0.4f + (float) frac);
		}
		if (held >= TIDAL_CHARGE) {
			tidalFire(ctx);
		}
	}

	private static void tidalFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		ctx.setResource("tidal_start", 0, 1e12f);
		ctx.setResource("ult_charge", 0, 100);
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		float dmg = 35.0f + bonus(p);
		for (int d = 2; d <= 16; d += 2) {
			Vec3 at = p.position().add(dir.scale(d));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 4.0)) {
				AbilityHelpers.hurt(p, e, dmg);
				AbilityHelpers.push(e, dir.scale(2.4).add(0, 0.4, 0));
				e.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, false, false, false));
				markWet(ctx, e);
			}
			level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1, at.z, 60, 2.4, 2.0, 2.4, 0.15);
			level.sendParticles(ParticleTypes.FALLING_WATER, at.x, at.y + 1.5, at.z, 30, 2.2, 1.5, 2.2, 0.1);
		}
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.6f, 0.4f);
		ctx.triggerCooldown(TIDAL_CD);
	}

	// ---- V: Water Prison + water creation --------------------------------------------------

	private static void prisonTick(AbilityContext ctx) {
		float ticks = ctx.resource("prison_ticks");
		if (ticks <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		Entity ent = ctx.level().getEntity((int) ctx.resource("prison_id"));
		if (!(ent instanceof LivingEntity t) || !t.isAlive()) {
			releasePrison(ctx);
			return;
		}
		ticks -= 1.0f;
		ctx.setResource("prison_ticks", ticks, PRISON_TICKS);

		double px = ctx.resource("prison_x");
		double py = ctx.resource("prison_y");
		double pz = ctx.resource("prison_z");
		boolean isPlayer = t instanceof net.minecraft.world.entity.player.Player;
		t.setDeltaMovement(0, 0, 0);
		t.hurtMarked = true;
		t.fallDistance = 0;
		t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, isPlayer ? 4 : 9, false, false, false));
		t.addEffect(new MobEffectInstance(MobEffects.JUMP, 10, -10, false, false, false));
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 2, false, false, false));
		// keep them breathing — the prison traps, it does not drown
		t.setAirSupply(t.getMaxAirSupply());
		if (!isPlayer && t.distanceToSqr(px, py, pz) > 0.04) {
			t.teleportTo(px, py, pz);
		}
		if (HeroConfig.get().abilityTerrainDamage) {
			BlockPos base = BlockPos.containing(px, py, pz);
			for (Direction d : Direction.values()) {
				TempBlocks.placeStatic(ctx.level(), base.relative(d), Blocks.WATER.defaultBlockState(), 15);
				TempBlocks.placeStatic(ctx.level(), base.above().relative(d), Blocks.WATER.defaultBlockState(), 15);
			}
		}
		if (p.tickCount % 4 == 0) {
			ctx.level().sendParticles(ParticleTypes.BUBBLE, t.getX(), t.getY() + 1, t.getZ(), 20, 0.5, 1.0, 0.5, 0.0);
		}
		if (ticks <= 0.5f) {
			releasePrison(ctx);
		}
	}

	private static void releasePrison(AbilityContext ctx) {
		ctx.setResource("prison_ticks", 0, PRISON_TICKS);
		ctx.setResource("prison_id", 0, 1e9f);
		ctx.triggerCooldown();
	}

	private static void createTick(AbilityContext ctx) {
		float ct = ctx.resource("create_ticks");
		if (ct <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		if (!p.isShiftKeyDown()) {
			ctx.setResource("create_ticks", 0, CREATE_TICKS);
			return;
		}
		ct -= 1.0f;
		ctx.setResource("create_ticks", ct, CREATE_TICKS);
		if (p.tickCount % 4 == 0) {
			Vec3 aim = AbilityHelpers.aimPoint(p, 12.0);
			ctx.level().sendParticles(ParticleTypes.SPLASH, aim.x, aim.y, aim.z, 4, 0.2, 0.2, 0.2, 0.0);
		}
		if (ct <= 0.5f && HeroConfig.get().abilityTerrainDamage) {
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, 12.0);
			BlockPos target = hit.getType() == HitResult.Type.BLOCK
					? hit.getBlockPos().relative(hit.getDirection())
					: BlockPos.containing(AbilityHelpers.aimPoint(p, 12.0));
			if (ctx.level().getBlockState(target).canBeReplaced()) {
				ctx.level().setBlockAndUpdate(target, Blocks.WATER.defaultBlockState());
				AbilityHelpers.sound(p, SoundEvents.BUCKET_EMPTY, 1.0f, 1.0f);
			}
		}
	}

	/** Combo: Water + Electrokinesis — mark a target "wet" for bonus electrical damage. */
	private static void markWet(AbilityContext ctx, LivingEntity e) {
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, false));
		if (e.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.FALLING_WATER, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 8, 0.3, 0.1, 0.3, 0.0);
		}
	}
}
