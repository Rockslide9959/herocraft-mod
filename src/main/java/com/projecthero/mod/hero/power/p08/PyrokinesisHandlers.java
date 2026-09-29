package com.projecthero.mod.hero.power.p08;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.StanceMode;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.power.TimedSelfFlight;
import com.projecthero.mod.hero.revamp.BatchBScheduler;
import com.projecthero.mod.hero.revamp.BatchBUtil;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 08 -- Pyrokinesis (v0.13.22 revamp). Signature: <b>one Heat bar.</b> Every attack stokes it; the hotter you
 * run the harder you hit (up to +40% at full heat), above {@value #BLUE_AT}% your flames burn blue (R becomes a
 * flame laser, Inferno a blue inferno, Flame Body scorches everything alight around you), and at 100% you
 * <b>overheat</b>: 1 damage a second (not fire -- a pyrokinetic is never set alight by their own power) and no
 * heat-building move works until you cool below 60% -- or vent it all at once with Heat Wave (H). Heat bleeds away on
 * its own while you are not channelling (three times faster in water or rain).
 *
 * <h2>Keys</h2>
 * R Fireball (blue: Flame Laser), G Flamethrower (hold), X Jet Flight (hold: thrust along your aim), Z Inferno
 * (hold to charge), V Flame Spark (light / Sneak: absorb fire, cook, smelt), C Flame Body (emissive flame shell that
 * turns blue with heat), H Heat Wave (vent all heat as a damaging ring), N Fire Whip.
 */
public final class PyrokinesisHandlers {
	public static final String KEY = "power_08_pyrokinesis";
	public static final float MAX_HEAT = 100.0f;
	public static final float BLUE_AT = 75.0f;
	private static final float OVERHEAT_CLEAR = 60.0f;
	private static final float COOL_PER_TICK = 0.2f;
	private static final ResourceLocation FLAME_BODY_ATK = com.projecthero.mod.ProjectHeroMod.id("flame_body_atk");

	private static final float HEAT_FIREBALL = 9.0f;
	private static final float HEAT_LASER = 12.0f;
	private static final float HEAT_FLAME_TICK = 0.35f;
	private static final float HEAT_JET_TICK = 0.45f;
	private static final float HEAT_INFERNO = 30.0f;
	private static final float HEAT_WHIP = 7.0f;

	/** Base direct-hit damage for the R fireball (before heat / Nether / Flame Body bonuses). +20% over v0.12. */
	public static final float FIREBALL_DIRECT_DAMAGE = 14.0f;
	private static final float FLAME_LASER_BONUS = 8.0f;
	public static final float FLAMETHROWER_DAMAGE = 7.0f;
	public static final int INFERNO_CHARGE = 85;
	private static final int INFERNO_CD = 47 * 20;
	private static final float INFERNO_AFTERBURN = 14.0f;
	public static final float HEAT_WAVE_BASE = 8.0f;
	public static final float HEAT_WAVE_SCALE = 24.0f;
	private static final float HEAT_WAVE_MIN = 10.0f;
	public static final float WHIP_DAMAGE = 11.0f;
	private static final double WHIP_RANGE = 9.0;

	private PyrokinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	// ---------------- heat ----------------

	public static float heat(ServerPlayer p) {
		Power power = power();
		return power == null ? 0.0f : ExperimentalPowers.getResource(p, power, "heat");
	}

	public static boolean overheated(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.getResource(p, power, "overheated") > 0.5f;
	}

	/** Blue flames: running at or above {@value #BLUE_AT}% heat. */
	public static boolean blue(ServerPlayer p) {
		return ownsPyrokinesis(p) && heat(p) >= BLUE_AT;
	}

	/** Damage multiplier from heat: x1.0 cold, x1.4 at full heat. */
	public static float heatMult(ServerPlayer p) {
		return 1.0f + 0.4f * Math.min(1.0f, heat(p) / MAX_HEAT);
	}

	private static void addHeat(ServerPlayer p, float amount) {
		Power power = power();
		float h = Math.min(MAX_HEAT, heat(p) + amount);
		BatchBUtil.set(p, power, "heat", h, MAX_HEAT, 0.25f);
		if (h >= MAX_HEAT && !overheated(p)) {
			ExperimentalPowers.setResource(p, power, "overheated", 1, 1);
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.pyro.overheat"), true);
			AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.5f);
			AbilityHelpers.sound(p, SoundEvents.BLAZE_HURT, 0.8f, 0.6f);
		}
	}

	/** False (with feedback) while overheated -- heat-building moves are locked out until you cool. */
	private static boolean canHeat(AbilityContext ctx) {
		if (overheated(ctx.player())) {
			ctx.actionBar("message.projecthero.pyro.overheated");
			return false;
		}
		return true;
	}

	private static SimpleParticleType flame(ServerPlayer p) {
		return blue(p) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
	}

	private static boolean fireOk() {
		return HeroConfig.get().abilityFireSpread && AbilityHelpers.canGrief();
	}

	/**
	 * "changes 22": the flamethrower's own stream is exempt from {@link HeroConfig#abilityFireSpread} -- directly
	 * spraying a surface is the ability; only {@code abilityTerrainDamage} still gates it.
	 */
	private static void placeStreamFire(ServerLevel level, BlockPos pos, int ttl) {
		if (AbilityHelpers.canGrief() && level.getBlockState(pos).isAir()) {
			TempBlocks.place(level, pos, BaseFireBlock.getState(level, pos), ttl);
		}
	}

	private static void placeFire(ServerLevel level, BlockPos pos, int ttl) {
		if (fireOk() && level.getBlockState(pos).isAir()) {
			TempBlocks.place(level, pos, BaseFireBlock.getState(level, pos), ttl);
		}
	}

	/** +5 to every Pyrokinesis ability's damage while in the Nether. */
	private static float netherBonus(ServerPlayer p) {
		return p.level().dimension() == Level.NETHER ? 5.0f : 0.0f;
	}

	private static float flameBodyBonus(ServerPlayer p) {
		return flameBodyActive(p) ? StanceMode.ABILITY_BONUS : 0.0f;
	}

	/** base x heat + Nether + Flame Body. */
	private static float dmg(ServerPlayer p, float base) {
		return base * heatMult(p) + netherBonus(p) + flameBodyBonus(p);
	}

	/**
	 * Direct-hit damage a Pyrokinesis R fireball deals -- read by {@code LargeFireballMixin}, which only calls this for
	 * a low-power fireball owned by a pyrokinetic (never the Inferno's own big fireball).
	 */
	public static float fireballImpactDamage(ServerPlayer p) {
		return dmg(p, FIREBALL_DIRECT_DAMAGE);
	}

	/** True when {@code p} owns Pyrokinesis -- the gate the fireball mixin uses. */
	public static boolean ownsPyrokinesis(ServerPlayer p) {
		return ExperimentalPowers.owns(p, KEY);
	}

	public static boolean flameBodyActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	public static boolean jetting(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power) && ExperimentalPowers.getResource(p, power, "jetting") > 0.5f;
	}

	// ---------------- registration ----------------

	public static void register() {
		AbilityHandlers.register(KEY, "fireball", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (!canHeat(ctx)) {
				return;
			}
			if (blue(p)) {
				flameLaser(ctx);
				addHeat(p, HEAT_LASER);
				MutationVisuals.play(p, "point_right");
				ctx.triggerCooldown();
				return;
			}
			Vec3 dir = p.getLookAngle();
			// a real ghast fireball: explosion power 3 (4 in the Nether); LargeFireballMixin sets the direct hit
			LargeFireball fb = new LargeFireball(p.level(), p, dir, netherBonus(p) > 0 ? 4 : 3);
			fb.setPos(p.getX() + dir.x * 1.5, p.getEyeY() + dir.y * 1.5 - 0.1, p.getZ() + dir.z * 1.5);
			p.level().addFreshEntity(fb);
			addHeat(p, HEAT_FIREBALL);
			MutationVisuals.play(p, "throw_right");
			AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.9f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "flamethrower", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!canHeat(ctx)) {
					return;
				}
				ctx.setResource("flaming", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("flaming") > 0.5f) {
					ctx.setResource("flaming", 0, 1);
				}
				MutationVisuals.stopIf(ctx.player(), "channel_two_hand");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("flaming") < 0.5f) {
					return;
				}
				flamethrowerTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "jet_flight", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!canHeat(ctx)) {
					return;
				}
				if (p.isInWater()) {
					ctx.actionBar("message.projecthero.pyro.doused");
					return;
				}
				ctx.setResource("jetting", 1, 1);
				AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
				AbilityHelpers.burst(ctx.level(), p.position(), flame(p), 30, 0.4);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				stopJet(ctx.player());
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("jetting") < 0.5f) {
					return;
				}
				jetTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "inferno", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!canHeat(ctx)) {
					return;
				}
				if (BatchBUtil.chargeStart(ctx, "pyro_start")) {
					AbilityHelpers.sound(ctx.player(), SoundEvents.BLAZE_AMBIENT, 0.8f, 0.6f);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				long held = BatchBUtil.chargeHeld(ctx, "pyro_start");
				if (held < 0) {
					return;
				}
				if (held >= INFERNO_CHARGE) {
					infernoFire(ctx);
				} else {
					infernoCancel(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				infernoChargeTick(ctx);
			}
		});

		// V: Flame Spark -- a flint-and-steel in your hand. Sneak: pull in nearby fire, smelt held wood, cook held food.
		AbilityHandlers.register(KEY, "flame_wall", Handlers.instant(PyrokinesisHandlers::flameSpark));

		AbilityHandlers.register(KEY, "flame_body", Handlers.toggle(
				ctx -> {
					if (StanceMode.blockedByCooldown(ctx)) {
						return;
					}
					MutationVisuals.play(ctx.player(), "power_up");
					AbilityHelpers.sound(ctx.player(), SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
					ctx.player().level().playSound(null, ctx.player().blockPosition(), SoundEvents.BLAZE_AMBIENT,
							SoundSource.PLAYERS, 0.8f, 1.0f);
					ctx.level().sendParticles(flame(ctx.player()), ctx.player().getX(), ctx.player().getY() + 1.0,
							ctx.player().getZ(), 60, 0.4, 0.9, 0.4, 0.05);
				},
				ctx -> {
					PowerToggles.clearModifier(ctx.player(), Attributes.ATTACK_DAMAGE, FLAME_BODY_ATK);
					StanceMode.startDeactivateCooldown(ctx);
				},
				PyrokinesisHandlers::flameBodyTick));

		AbilityHandlers.register(KEY, "heat_wave", Handlers.instant(PyrokinesisHandlers::heatWave));
		AbilityHandlers.register(KEY, "fire_whip", Handlers.instant(PyrokinesisHandlers::fireWhip));

		// Melee hits from a flame-bodied player set the target alight.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && flameBodyActive(sp) && entity instanceof LivingEntity) {
				entity.setRemainingFireTicks(100);
			}
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && flameBodyActive(sp)
					&& source.getEntity() instanceof LivingEntity attacker && sp.distanceToSqr(attacker) < 9.0) {
				attacker.setRemainingFireTicks(80);
			}
		});

		// Passive: complete fire immunity while Pyrokinesis is owned (see also HeroDamageRules).
		PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				PowerToggles.effect(player, MobEffects.FIRE_RESISTANCE, 0, false);
			} else {
				PowerToggles.clearEffect(player, MobEffects.FIRE_RESISTANCE);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, FLAME_BODY_ATK);
			}
		});
		PowerPassives.registerTick(KEY, PyrokinesisHandlers::passiveTick);
	}

	// ---------------- passive tick: cooling, overheat, legacy teardown ----------------

	private static void passiveTick(ServerPlayer player) {
		Power power = power();
		if (power == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		PowerToggles.effect(player, MobEffects.FIRE_RESISTANCE, 0, false);
		// A pyrokinetic simply cannot burn -- snuff any ignition the same tick it happens.
		if (player.getRemainingFireTicks() > 0) {
			player.clearFire();
		}
		// v0.13.22: the old flame flight / two-gauge kit are gone -- clean up after an older save
		if (TimedSelfFlight.isActive(player, power, "flame")) {
			TimedSelfFlight.stop(player, power, power.ability(AbilitySlot.SLOT_3), "flame", false);
		}
		BatchBUtil.retire(player, power, "flamethrower", "flame_body", "flame_body_regen_until", "pyro_lightning");
		BatchBUtil.seed(player, power, "heat", 0.0f);

		float heat = heat(player);
		boolean channeling = BatchBUtil.get(player, power, "flaming") > 0.5f || jetting(player)
				|| BatchBUtil.get(player, power, "pyro_start") > 0.5f;
		if (!channeling) {
			float cool = COOL_PER_TICK * (player.isInWaterOrRain() ? 3.0f : 1.0f);
			float next = heat - cool;
			if (flameBodyActive(player) && heat < 50.0f) {
				next = Math.min(50.0f, heat + 0.1f); // the flame body keeps you simmering at half heat
			}
			if (next != heat) {
				BatchBUtil.set(player, power, "heat", next, MAX_HEAT, 0.5f);
			}
		}
		if (overheated(player)) {
			if (heat(player) < OVERHEAT_CLEAR) {
				ExperimentalPowers.setResource(player, power, "overheated", 0, 1);
			} else if (player.tickCount % 20 == 0) {
				// not a fire source -- the power's fire immunity would swallow it, and it must never ignite its user
				player.hurt(player.damageSources().generic(), 1.0f);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.2, player.getZ(),
						6, 0.3, 0.4, 0.3, 0.02);
			}
		}
		if (blue(player) && player.tickCount % 5 == 0) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.9, player.getZ(),
					1, 0.12, 0.05, 0.12, 0.01);
		}
		// Blue Flame Body: anything the pyrokinetic has set alight burns far hotter.
		if (flameBodyActive(player) && blue(player) && player.tickCount % 8 == 0) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.position(), 24.0)) {
				if (e.getRemainingFireTicks() > 0) {
					AbilityHelpers.hurt(player, e, AbilityHelpers.fire(player), 3.0f);
				}
			}
		}
	}

	// ---------------- R: flame laser ----------------

	/** Blocks-only explosion for the flame laser's crater (entity damage is hand-applied). */
	private static final net.minecraft.world.level.ExplosionDamageCalculator BLOCKS_ONLY_BLAST =
			new net.minecraft.world.level.ExplosionDamageCalculator() {
				@Override
				public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion, Entity entity) {
					return false;
				}

				@Override
				public float getKnockbackMultiplier(Entity entity) {
					return 0.0f;
				}
			};

	private static void flameLaser(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		double range = 40.0;
		Vec3 eye = p.getEyePosition();
		LivingEntity target = AbilityHelpers.raycastEntity(p, range);
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, range);
		Vec3 end = target != null
				? target.position().add(0, target.getBbHeight() * 0.5, 0)
				: (bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : AbilityHelpers.aimPoint(p, range));
		Vec3 hand = AbilityHelpers.handPosition(p);
		AbilityHelpers.line(level, hand, end, ParticleTypes.SOUL_FIRE_FLAME, 4.0);
		AbilityHelpers.line(level, hand, end, ParticleTypes.FLAME, 1.5);

		float dmg = dmg(p, FIREBALL_DIRECT_DAMAGE + FLAME_LASER_BONUS);
		Set<LivingEntity> hit = new HashSet<>(AbilityHelpers.enemiesAround(p, end, 3.0));
		if (target != null) {
			hit.add(target);
		}
		for (LivingEntity e : hit) {
			AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), dmg);
			e.setRemainingFireTicks(160);
		}
		if (AbilityHelpers.canGrief()) {
			level.explode(p, null, BLOCKS_ONLY_BLAST, end.x, end.y, end.z, 3.0f, true, Level.ExplosionInteraction.MOB,
					ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
		}
		if (fireOk()) {
			BlockPos centre = BlockPos.containing(end);
			for (int i = 0; i < 10; i++) {
				placeFire(level, centre.offset(level.random.nextInt(5) - 2, level.random.nextInt(3) - 1,
						level.random.nextInt(5) - 2), 120);
			}
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, end.x, end.y, end.z, 60, 2.0, 1.0, 2.0, 0.05);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.4f, 0.5f);
	}

	// ---------------- G: flamethrower ----------------

	private static void flamethrowerTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (overheated(p)) {
			ctx.setResource("flaming", 0, 1);
			MutationVisuals.stopIf(p, "channel_two_hand");
			return;
		}
		MutationVisuals.ensure(p, "channel_two_hand");
		Vec3 look = p.getLookAngle();
		Vec3 origin = p.getEyePosition().add(0, -0.25, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, origin.add(look.scale(3.0)), 3.5)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin).normalize();
			if (to.dot(look) > 0.6) {
				AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), dmg(p, FLAMETHROWER_DAMAGE) - flameBodyBonus(p) * 0.75f);
				e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 80));
			}
		}
		double reach = 7.0;
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, reach);
		double streamLen = bhr.getType() == HitResult.Type.BLOCK ? origin.distanceTo(bhr.getLocation()) : reach;
		SimpleParticleType fp = flame(p);
		for (double d = 0.6; d <= streamLen + 0.01; d += 0.5) {
			Vec3 pt = origin.add(look.scale(d));
			level.sendParticles(fp, pt.x, pt.y, pt.z, 3, 0.1 * d, 0.1 * d, 0.1 * d, 0.02);
			if (p.tickCount % 2 == 0) {
				BlockPos bp = BlockPos.containing(pt);
				boolean nearSurface = !level.getBlockState(bp.below()).isAir()
						|| !level.getBlockState(bp.above()).isAir()
						|| !level.getBlockState(bp.north()).isAir() || !level.getBlockState(bp.south()).isAir()
						|| !level.getBlockState(bp.east()).isAir() || !level.getBlockState(bp.west()).isAir();
				if (nearSurface && d > 2.0) {
					placeStreamFire(level, bp, 100);
				}
			}
		}
		if (bhr.getType() == HitResult.Type.BLOCK) {
			placeStreamFire(level, bhr.getBlockPos().relative(bhr.getDirection()), 120);
		}
		if (p.tickCount % 5 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.9f, 1.3f);
		}
		addHeat(p, HEAT_FLAME_TICK);
	}

	// ---------------- X: jet flight ----------------

	/**
	 * X (hold): twin jets of flame drive you along your aim at about 18 blocks a second -- steer by looking, let go to
	 * coast (no fall damage for 5 s after). Builds heat fast; overheating or diving into water cuts the jets.
	 */
	private static void jetTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (!p.isAlive() || overheated(p) || p.isInWater() || p.isPassenger()) {
			if (p.isInWater()) {
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
				AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 1.0f);
			}
			stopJet(p);
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 v = p.getDeltaMovement().scale(0.6).add(look.scale(0.36)).add(0, 0.035, 0);
		AbilityHelpers.launchSelf(p, v);
		MutationVisuals.ensure(p, "p08.jet");
		SimpleParticleType fp = flame(p);
		Vec3 back = p.position().add(0, 0.2, 0).subtract(look.scale(0.4));
		level.sendParticles(fp, back.x, back.y, back.z, 6, 0.12, 0.12, 0.12, 0.03);
		level.sendParticles(ParticleTypes.SMOKE, back.x, back.y, back.z, 2, 0.1, 0.1, 0.1, 0.01);
		if (p.tickCount % 6 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.7f, 0.7f);
		}
		if (com.projecthero.mod.hero.power.PowerCombos.flameFlightTrail(p) && p.tickCount % 2 == 0) {
			placeFire(level, p.blockPosition().below(2), 40);
		}
		addHeat(p, HEAT_JET_TICK);
	}

	private static void stopJet(ServerPlayer p) {
		Power power = power();
		if (power == null) {
			return;
		}
		if (ExperimentalPowers.getResource(p, power, "jetting") > 0.5f) {
			ExperimentalPowers.setResource(p, power, "jetting", 0, 1);
			ExperimentalPowers.setResource(p, power, "no_fall_until", p.level().getGameTime() + 100, 1.0e12f);
			p.resetFallDistance();
		}
		MutationVisuals.stopIf(p, "p08.jet");
	}

	// ---------------- Z: Inferno ----------------

	private static void infernoChargeTick(AbilityContext ctx) {
		long held = BatchBUtil.chargeHeld(ctx, "pyro_start");
		if (held < 0) {
			return;
		}
		ServerPlayer p = ctx.player();
		if (held > INFERNO_CHARGE + 100) {
			infernoCancel(ctx);
			return;
		}
		BatchBUtil.chargeMeter(ctx, held, INFERNO_CHARGE);
		MutationVisuals.ensure(p, "carry_overhead");
		ServerLevel level = ctx.level();
		double frac = Math.min(1.0, held / (double) INFERNO_CHARGE);
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 1.0, 0.3));
		p.hurtMarked = true;
		// the fireball swells overhead as it charges
		Vec3 over = p.position().add(0, p.getBbHeight() + 0.7 + frac * 0.6, 0);
		level.sendParticles(flame(p), over.x, over.y, over.z, 4 + (int) (frac * 14), 0.2 + frac * 0.7,
				0.2 + frac * 0.7, 0.2 + frac * 0.7, 0.01);
		if (held % 16 == 0) {
			AbilityHelpers.sound(p, SoundEvents.FIRE_AMBIENT, 0.9f, 0.5f + (float) frac);
		}
		if (held >= INFERNO_CHARGE) {
			infernoFire(ctx);
		}
	}

	private static void infernoCancel(AbilityContext ctx) {
		BatchBUtil.chargeClear(ctx, "pyro_start");
		MutationVisuals.stopIf(ctx.player(), "carry_overhead");
		AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.0f);
	}

	private static void infernoFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		BatchBUtil.chargeClear(ctx, "pyro_start");
		boolean blueNow = blue(p);
		Vec3 dir = p.getLookAngle();
		int power = 5 + (netherBonus(p) > 0 || blueNow ? 1 : 0);
		LargeFireball fb = new LargeFireball(p.level(), p, dir, power);
		fb.accelerationPower = 0.05;
		fb.setPos(p.getX() + dir.x * 2.0, p.getEyeY() + dir.y * 2.0 - 0.1, p.getZ() + dir.z * 2.0);
		p.level().addFreshEntity(fb);
		trackInferno(p, level, fb, blueNow);
		AbilityHelpers.burst(level, fb.position(), blueNow ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, 90, 1.4);
		level.sendParticles(ParticleTypes.LAVA, fb.getX(), fb.getY(), fb.getZ(), 20, 0.8, 0.8, 0.8, 0.0);
		MutationVisuals.play(p, "throw_right");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.5f, 0.4f);
		addHeat(p, HEAT_INFERNO);
		ctx.triggerCooldown(INFERNO_CD);
	}

	/**
	 * Wraps the in-flight Inferno in a rolling ball of flame (blue if it was loosed blue) and, when it bursts, an
	 * afterburn scorches everything within 5.5 blocks of the impact for 14 x heat and 7 s of burning.
	 */
	private static void trackInferno(ServerPlayer p, ServerLevel level, LargeFireball fb, boolean blueFb) {
		SimpleParticleType fp = blueFb ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
		Vec3[] last = { fb.position() };
		BatchBScheduler.schedule(level, age -> {
			if (fb.isAlive()) {
				if (age > 200) {
					return false;
				}
				last[0] = fb.position();
				double x = fb.getX();
				double y = fb.getY();
				double z = fb.getZ();
				level.sendParticles(fp, x, y, z, 40, 1.2, 1.2, 1.2, 0.03);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 8, 0.9, 0.9, 0.9, 0.01);
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 4, 0.7, 0.7, 0.7, 0.0);
				placeFire(level, BlockPos.containing(x, y, z), 60);
				return true;
			}
			Vec3 at = last[0];
			if (p.isAlive()) {
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 5.5)) {
					AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), dmg(p, INFERNO_AFTERBURN));
					e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 140));
				}
			}
			level.sendParticles(fp, at.x, at.y, at.z, 80, 3.0, 1.5, 3.0, 0.05);
			return false;
		});
	}

	// ---------------- V: Flame Spark ----------------

	private static void flameSpark(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (p.isShiftKeyDown()) {
			int absorbed = absorbNearbyFlames(level, p);
			boolean smelted = smeltHeldWood(p);
			boolean cooked = !smelted && cookHeldFood(p, level);
			if (cooked || smelted || absorbed > 0) {
				AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 0.8f, 1.4f);
				level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.2, p.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
			}
			if (absorbed > 0) {
				addHeat(p, Math.min(30.0f, absorbed * 3.0f)); // drinking in fire stokes you
			}
			MutationVisuals.play(p, "grab_pull");
			ctx.triggerCooldown();
			return;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, 6.0);
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = hit.getBlockPos();
			BlockState st = level.getBlockState(pos);
			BlockPos face = pos.relative(hit.getDirection());
			if (st.is(Blocks.TNT)) {
				level.removeBlock(pos, false);
				level.addFreshEntity(new net.minecraft.world.entity.item.PrimedTnt(
						level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p));
			} else if (st.is(Blocks.CAMPFIRE) || st.is(Blocks.SOUL_CAMPFIRE)) {
				if (!st.getValue(CampfireBlock.LIT)) {
					level.setBlockAndUpdate(pos, st.setValue(BlockStateProperties.LIT, true));
				}
			} else if (level.getBlockState(face).isAir() || level.getBlockState(face).canBeReplaced()) {
				// a real fire block: also ignites nether portals and spreads to adjacent TNT
				level.setBlockAndUpdate(face, BaseFireBlock.getState(level, face));
			}
			Vec3 at = hit.getLocation();
			AbilityHelpers.line(level, AbilityHelpers.handPosition(p), at, ParticleTypes.SMALL_FLAME, 3.0);
		}
		MutationVisuals.play(p, "point_right");
		AbilityHelpers.sound(p, SoundEvents.FLINTANDSTEEL_USE, 1.0f, 1.0f);
		ctx.triggerCooldown();
	}

	// ---------------- C: Flame Body ----------------

	private static void flameBodyTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, FLAME_BODY_ATK, StanceMode.MELEE_BONUS + netherBonus(p),
				AttributeModifier.Operation.ADD_VALUE);
		AbilityHelpers.modeAura(p, flame(p), 3);
		if (p.tickCount % 2 == 0 && p.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
			placeFire(level, p.blockPosition(), 60);
		}
		if (p.tickCount % 10 == 0) {
			double r = blue(p) ? 4.0 : 3.0;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 80));
			}
		}
		if (p.tickCount % 30 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.PLAYERS, 0.5f, 1.0f);
		}
	}

	// ---------------- H: Heat Wave ----------------

	/**
	 * H: vent every degree of heat at once as an expanding ring of fire -- radius 4 + 6 x heat, damage 8 + 24 x heat
	 * (x Flame Body / Nether bonuses), knockback and 5 s of burning -- leaving you stone cold and clearing an overheat.
	 * Needs at least 10% heat.
	 */
	private static void heatWave(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float heat = heat(p);
		if (heat < HEAT_WAVE_MIN) {
			ctx.actionBar("message.projecthero.pyro.too_cold");
			return;
		}
		float frac = Math.min(1.0f, heat / MAX_HEAT);
		double radius = 4.0 + 6.0 * frac;
		float damage = HEAT_WAVE_BASE + HEAT_WAVE_SCALE * frac + netherBonus(p) + flameBodyBonus(p);
		boolean wasBlue = heat >= BLUE_AT;
		Vec3 c = p.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 1, 0), radius)) {
			AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), damage);
			e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 100));
			AbilityHelpers.knockbackFrom(e, c, 1.0 + frac);
			AbilityHelpers.push(e, new Vec3(0, 0.35, 0));
		}
		SimpleParticleType fp = wasBlue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
		// the ring rolls outward over a few ticks
		BatchBScheduler.schedule(level, age -> {
			double r = Math.min(radius, 1.0 + age * 1.6);
			int n = (int) (r * 10);
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n;
				level.sendParticles(fp, c.x + Math.cos(a) * r, c.y + 0.4, c.z + Math.sin(a) * r, 1, 0.05, 0.2, 0.05, 0.01);
			}
			return r < radius;
		});
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 1, c.z, 20, 1.0, 0.5, 1.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1, c.z, 20, 0.5, 0.8, 0.5, 0.05);
		level.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.6f, 0.4f);
		level.playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, 0.6f);
		Power power = ctx.power();
		ExperimentalPowers.setResource(p, power, "heat", 0, MAX_HEAT);
		if (overheated(p)) {
			ExperimentalPowers.setResource(p, power, "overheated", 0, 1);
		}
		MutationVisuals.play(p, "p08.heat_wave");
		ctx.triggerCooldown();
	}

	// ---------------- N: Fire Whip ----------------

	/**
	 * N: a lash of living flame, 9 blocks, swept in an S-curve along your aim. Everything it touches takes 11 (x heat)
	 * fire damage, burns for 4 s and is yanked toward you.
	 */
	private static void fireWhip(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!canHeat(ctx)) {
			return;
		}
		ServerLevel level = ctx.level();
		Vec3 hand = AbilityHelpers.handPosition(p);
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		float damage = dmg(p, WHIP_DAMAGE);
		SimpleParticleType fp = flame(p);
		Set<LivingEntity> hit = new HashSet<>();
		Vec3 sway = right;
		Vec3 lift = up;
		BatchBScheduler.schedule(level, age -> {
			// the lash uncurls over 4 ticks
			double reach = Math.min(WHIP_RANGE, (age + 1) * (WHIP_RANGE / 4.0));
			int segs = (int) (reach * 3);
			Vec3 prev = hand;
			for (int i = 1; i <= segs; i++) {
				double s = i / (double) segs;
				double d = reach * s;
				double wave = Math.sin(s * Math.PI * 2 + age * 0.9) * 0.55 * s * (1 - 0.5 * age / 4.0);
				Vec3 pt = hand.add(look.scale(d)).add(sway.scale(wave)).add(lift.scale(Math.sin(s * Math.PI) * 0.35));
				level.sendParticles(fp, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0);
				if (i % 3 == 0) {
					level.sendParticles(ParticleTypes.SMALL_FLAME, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, pt, 0.9)) {
					if (hit.add(e)) {
						AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), damage);
						e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 80));
						Vec3 pull = p.position().subtract(e.position()).normalize();
						AbilityHelpers.push(e, new Vec3(pull.x * 0.9, 0.3, pull.z * 0.9));
					}
				}
				prev = pt;
			}
			if (age == 3) {
				level.playSound(null, prev.x, prev.y, prev.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9f, 1.4f);
			}
			return age < 4;
		});
		MutationVisuals.play(p, "whip_right");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 0.8f, 1.5f);
		addHeat(p, HEAT_WHIP);
		ctx.triggerCooldown();
	}

	// ---------------- helpers kept from the original kit ----------------

	/** Sneak-smelt held wood: logs become coal blocks, planks become charcoal. */
	private static boolean smeltHeldWood(ServerPlayer p) {
		ItemStack held = p.getMainHandItem();
		if (held.isEmpty()) {
			return false;
		}
		int count = held.getCount();
		ItemStack out;
		if (held.is(ItemTags.LOGS)) {
			out = new ItemStack(Blocks.COAL_BLOCK, count);
		} else if (held.is(ItemTags.PLANKS)) {
			out = new ItemStack(Items.CHARCOAL, count);
		} else {
			return false;
		}
		held.setCount(0);
		while (!out.isEmpty()) {
			ItemStack chunk = out.split(Math.min(out.getMaxStackSize(), out.getCount()));
			if (!p.getInventory().add(chunk)) {
				p.drop(chunk, false);
			}
		}
		return true;
	}

	/** Cook the entire held stack of a raw food in one go. */
	private static boolean cookHeldFood(ServerPlayer p, ServerLevel level) {
		ItemStack held = p.getMainHandItem();
		if (held.isEmpty()) {
			return false;
		}
		Optional<net.minecraft.world.item.crafting.RecipeHolder<SmeltingRecipe>> recipe =
				level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(held), level);
		if (recipe.isEmpty()) {
			return false;
		}
		ItemStack single = recipe.get().value().getResultItem(level.registryAccess());
		if (single.isEmpty()) {
			return false;
		}
		int count = held.getCount();
		held.setCount(0);
		ItemStack result = single.copyWithCount(count);
		while (!result.isEmpty()) {
			ItemStack chunk = result.split(Math.min(result.getMaxStackSize(), result.getCount()));
			if (!p.getInventory().add(chunk)) {
				p.drop(chunk, false);
			}
		}
		return true;
	}

	/** Snuff out every fire block within 5 blocks. */
	private static int absorbNearbyFlames(ServerLevel level, ServerPlayer p) {
		int count = 0;
		BlockPos c = p.blockPosition();
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-5, -3, -5), c.offset(5, 3, 5))) {
			if (level.getBlockState(bp).is(net.minecraft.tags.BlockTags.FIRE)) {
				level.removeBlock(bp, false);
				level.sendParticles(ParticleTypes.SMOKE, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
				count++;
			}
		}
		p.clearFire();
		return count;
	}
}
