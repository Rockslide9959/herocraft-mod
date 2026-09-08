package com.projecthero.mod.hero.power.p06;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.StanceMode;
import com.projecthero.mod.hero.power.TempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** Power 06 — Crystalkinesis (v0.10.7 tuning). */
public final class CrystalkinesisHandlers {
	private static final String KEY = "power_06_crystalkinesis";
	private static final BlockState CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockState BARRIER = Blocks.TINTED_GLASS.defaultBlockState();
	private static final BlockParticleOption CRYSTAL_DUST =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
	private static final net.minecraft.resources.ResourceLocation ARMOR_KB = com.projecthero.mod.ProjectHeroMod.id("crystal_armor_kb");
	private static final net.minecraft.resources.ResourceLocation ARMOR_ATK = com.projecthero.mod.ProjectHeroMod.id("crystal_armor_atk");

	private static final float MAX_STRAIN = 500.0f;
	private static final float STRAIN_DRAIN = MAX_STRAIN / (25 * 20);
	private static final float STRAIN_REGEN = MAX_STRAIN / (40 * 20);

	/** Z is a 5 s (100-tick) hold-to-charge for the eruption and the shift+Z Colossal Crystal. */
	private static final int ERUPT_CHARGE = 100;
	private static final int ERUPT_CD = 70 * 20;
	private static final int COLOSSAL_CD = 100 * 20;

	private CrystalkinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	private static boolean armorActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	/** Crystal Armor adds a flat bonus to every Crystalkinesis attack (see {@link StanceMode}). */
	private static float armorBonus(ServerPlayer p) {
		return armorActive(p) ? StanceMode.ABILITY_BONUS : 0.0f;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "crystal_shard", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				// Sneak + R: a volley of 5 crystal shards fired one after another (10 s cooldown).
				ctx.setResource("shard_ticks", 5, 5);
				ctx.setResource("shard_step", 1, 1);
				ctx.triggerCooldown(10 * 20);
				AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
				return;
			}
			LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
			Vec3 end = AbilityHelpers.aimPoint(p, 24.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), end, CRYSTAL_DUST, 4.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 11.0f + armorBonus(p));
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 0);
			}
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
			ctx.triggerCooldown();
		}, CrystalkinesisHandlers::shardVolleyTick));

		AbilityHandlers.register(KEY, "crystal_spikes", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				crystalCone(ctx);
				return;
			}
			Vec3 at = AbilityHelpers.aimPoint(p, 18.0);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 3.0)) {
				AbilityHelpers.hurt(p, e, 19.0f + armorBonus(p));
				AbilityHelpers.push(e, new Vec3(0, 0.7, 0));
			}
			ServerLevel level = ctx.level();
			if (AbilityHelpers.canGrief()) {
				for (int i = 0; i < 5; i++) {
					BlockPos bp = BlockPos.containing(at.x + level.random.nextGaussian(), at.y, at.z + level.random.nextGaussian());
					TempBlocks.placeStatic(level, bp, Blocks.AMETHYST_CLUSTER.defaultBlockState(), 120);
				}
			}
			level.sendParticles(CRYSTAL_DUST, at.x, at.y, at.z, 30, 0.5, 0.6, 0.5, 0.1);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 0.8f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "crystal_barrier", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			// v0.10.9 — sneak + look straight down: toggle Crystal Skate. Any later X press ends it
			// (8 s cooldown on the slot once it stops).
			if (ctx.resource("skating") > 0.5f) {
				stopSkate(ctx);
				return;
			}
			if (p.isShiftKeyDown() && p.getXRot() > 75.0f) {
				ctx.setResource("skating", 1, 1);
				ctx.actionBar("message.projecthero.crystal.skate_on");
				AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
				return;
			}
			float pitch = p.getXRot();
			boolean built;
			if (pitch < -75.0f) {
				built = com.projecthero.mod.hero.power.ConjuredStructures.dome(p, level, BARRIER);
			} else if (pitch > 75.0f) {
				built = com.projecthero.mod.hero.power.ConjuredStructures.bridge(p, level, BARRIER);
			} else {
				built = com.projecthero.mod.hero.power.ConjuredStructures.wall(p, level, BARRIER);
			}
			level.sendParticles(CRYSTAL_DUST, p.getX(), p.getY() + 1.5, p.getZ(), 30, 1.2, 1.2, 1.2, 0.05);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.9f);
			if (built || !AbilityHelpers.canGrief()) {
				ctx.triggerCooldown();
			}
		}));

		// Z: hold for 5 s. Crystal Eruption normally, Colossal Crystal on sneak.
		AbilityHandlers.register(KEY, "crystal_eruption", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				eruptPress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				eruptRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				eruptTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "crystal_prison", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
			if (t == null) {
				return;
			}
			boolean applied = AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 140, 9);
			AbilityHelpers.applyControl(t, MobEffects.JUMP, 140, -10);
			if (AbilityHelpers.canGrief() && !(t instanceof Player)) {
				BlockPos base = t.blockPosition();
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					TempBlocks.place(ctx.level(), base.relative(d), CRYSTAL, 140);
					TempBlocks.place(ctx.level(), base.relative(d).above(), CRYSTAL, 140);
				}
				TempBlocks.place(ctx.level(), base.above(2), CRYSTAL, 140);
			}
			ctx.level().sendParticles(CRYSTAL_DUST, t.getX(), t.getY() + 1, t.getZ(), 40, 0.5, 1.0, 0.5, 0.05);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 0.5f);
			if (applied || AbilityHelpers.canGrief()) {
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "crystal_armor", Handlers.toggle(
				ctx -> {
					if (StanceMode.blockedByCooldown(ctx)) {
						return;
					}
					ModeMeter.ensureSeeded(ctx, "crystal_armor", MAX_STRAIN);
					if (!ModeMeter.hasCharge(ctx, "crystal_armor", 40.0f)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.crystal.strain_low");
						return;
					}
					armorOn(ctx);
				},
				ctx -> {
					armorOff(ctx);
					StanceMode.startDeactivateCooldown(ctx);
				},
				ctx -> {
					armorOn(ctx);
					AbilityHelpers.modeAura(ctx.player(), CRYSTAL_DUST, 3);
					if (ctx.player().tickCount % 12 == 0) {
						AbilityHelpers.modeAura(ctx.player(), CRYSTAL_DUST, 1);
					}
					if (!ModeMeter.drain(ctx, "crystal_armor", MAX_STRAIN, STRAIN_DRAIN)) {
						ctx.setToggled(false);
						armorOff(ctx);
						StanceMode.startDeactivateCooldown(ctx);
						ctx.actionBar("message.projecthero.crystal.strain_out");
					}
				}));

		PowerPassives.registerTick(KEY, player -> {
			Power power = power();
			ModeMeter.regen(player, power, "crystal_armor", MAX_STRAIN, STRAIN_REGEN, armorActive(player));
			colossalTick(player, power);
			skateTick(player, power);
		});
	}

	// ---- sneak+R: crystal shard volley ----------------------------------------------------

	private static void shardVolleyTick(AbilityContext ctx) {
		float left = ctx.resource("shard_ticks");
		if (left < 0.5f) {
			return;
		}
		int step = (int) ctx.resource("shard_step") - 1;
		if (step > 0) {
			ctx.setResource("shard_step", step, 5);
			return;
		}
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		// slight spread so the five shards fan out
		Vec3 look = p.getLookAngle();
		Vec3 jitter = new Vec3(level.random.nextGaussian() * 0.05, level.random.nextGaussian() * 0.05,
				level.random.nextGaussian() * 0.05);
		Vec3 dir = look.add(jitter).normalize();
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(dir.scale(24.0));
		AbilityHelpers.line(level, eye, end, CRYSTAL_DUST, 4.0);
		LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
		if (t != null) {
			AbilityHelpers.hurt(p, t, 6.0f + armorBonus(p));
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 30, 0);
		}
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 1.5f);
		ctx.setResource("shard_ticks", left - 1, 5);
		ctx.setResource("shard_step", 4, 5);
	}

	// ---- shift+G: crystal cone --------------------------------------------------------------

	private static void crystalCone(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(5.0)), 8.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() > 10.5 || to.normalize().dot(look) < 0.6) {
				continue;
			}
			AbilityHelpers.hurt(p, e, 15.0f + armorBonus(p));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 120, 1);
			BlockPos feet = e.blockPosition();
			if (AbilityHelpers.canGrief() && !(e instanceof Player)) {
				TempBlocks.placeStatic(level, feet, Blocks.AMETHYST_BLOCK.defaultBlockState(), 120);
				TempBlocks.placeStatic(level, feet.above(), Blocks.LARGE_AMETHYST_BUD.defaultBlockState(), 120);
			}
			level.sendParticles(CRYSTAL_DUST, e.getX(), e.getY() + 0.5, e.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
		}
		AbilityHelpers.line(level, eye, eye.add(look.scale(10.0)), CRYSTAL_DUST, 2.0);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.2f, 0.7f);
		ctx.triggerCooldown(22 * 20);
	}

	// ---- Z: Crystal Eruption hold-charge / Colossal Crystal --------------------------------

	private static void eruptPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("crystal_start") > 0.5f) {
			return;
		}
		if (!ExperimentalPowers.cooldownReady(p, ctx.power(), ctx.ability())) {
			ctx.actionBar("message.projecthero.ability.on_cooldown",
					net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f",
							Math.ceil(ExperimentalPowers.cooldownRemainingTicks(p, ctx.power(), ctx.ability()) / 20.0f)));
			return;
		}
		ctx.setResource("crystal_start", p.level().getGameTime(), 1e12f);
		ctx.setResource("crystal_colossal", p.isShiftKeyDown() ? 1 : 0, 1);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);
	}

	private static void eruptRelease(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("crystal_start") <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) ctx.resource("crystal_start");
		if (held >= ERUPT_CHARGE) {
			eruptFire(ctx);
		} else {
			eruptCancel(ctx);
		}
	}

	private static void eruptTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float start = ctx.resource("crystal_start");
		if (start <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > ERUPT_CHARGE + 100) {
			eruptCancel(ctx);
			return;
		}
		ctx.setResource("ult_charge", Math.min(100f, held * 100f / ERUPT_CHARGE), 100);
		ServerLevel level = ctx.level();
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.25, 1.0, 0.25));
		p.hurtMarked = true;
		double frac = Math.min(1.0, held / (double) ERUPT_CHARGE);
		level.sendParticles(CRYSTAL_DUST, p.getX(), p.getY() + 1.0, p.getZ(),
				4 + (int) (frac * 10), 0.6 * frac + 0.3, 0.5, 0.6 * frac + 0.3, 0.02);
		if (held % 20 == 0) {
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.6f + (float) frac);
		}
		if (held >= ERUPT_CHARGE) {
			eruptFire(ctx);
		}
	}

	private static void eruptCancel(AbilityContext ctx) {
		ctx.setResource("crystal_start", 0, 1e12f);
		ctx.setResource("crystal_colossal", 0, 1);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.4f);
	}

	private static void eruptFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		boolean colossal = ctx.resource("crystal_colossal") > 0.5f;
		ctx.setResource("crystal_start", 0, 1e12f);
		ctx.setResource("crystal_colossal", 0, 1);
		ctx.setResource("ult_charge", 0, 100);

		if (colossal) {
			launchColossalCrystal(p, level);
			ctx.triggerCooldown(COLOSSAL_CD);
			return;
		}

		double r = 20.0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
			double d = e.position().distanceTo(p.position());
			float dmg = (float) ((40.0f + armorBonus(p)) * (1.0 - Math.min(0.55, d / r)));
			AbilityHelpers.hurt(p, e, dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 1.3);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 80, 2);
		}
		if (AbilityHelpers.canGrief()) {
			// a dense field of amethyst pillars -- solid blocks, so they never pop and shed shards
			for (int i = 0; i < 60; i++) {
				double ang = level.random.nextDouble() * Math.PI * 2;
				// keep a 4-block clear ring around the caster -- the pillars must never wall them in,
				// suffocate them, or crush them.
				double dist = 4.0 + level.random.nextDouble() * (r - 4.0);
				int bx = Mth.floor(p.getX() + Math.cos(ang) * dist);
				int bz = Mth.floor(p.getZ() + Math.sin(ang) * dist);
				int gy = p.blockPosition().getY() + 3;
				while (gy > level.getMinBuildHeight() + 1 && level.getBlockState(new BlockPos(bx, gy - 1, bz)).isAir()) {
					gy--;
				}
				int h = 1 + level.random.nextInt(4);
				for (int y = 0; y < h; y++) {
					BlockPos bp = new BlockPos(bx, gy + y, bz);
					BlockState st = level.getBlockState(bp);
					if (st.isAir() || st.canBeReplaced()) {
						TempBlocks.placeStatic(level, bp, Blocks.AMETHYST_BLOCK.defaultBlockState(), 160);
					}
				}
			}
		}
		level.sendParticles(CRYSTAL_DUST, p.getX(), p.getY() + 0.5, p.getZ(), 160, r / 2, 0.6, r / 2, 0.2);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.6f, 0.5f);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 0.4f);
		ctx.triggerCooldown(ERUPT_CD);
	}

	private static void launchColossalCrystal(ServerPlayer p, ServerLevel level) {
		Vec3 dir = p.getLookAngle().normalize();
		Vec3 spawn = com.projecthero.mod.hero.power.p05.GeokinesisHandlers.airSpawn(level, p, dir);
		FallingBlockEntity crystal = FallingBlockEntity.fall(level, BlockPos.containing(spawn),
				Blocks.AMETHYST_BLOCK.defaultBlockState());
		crystal.setPos(spawn.x, spawn.y - 0.5, spawn.z);
		crystal.setNoGravity(true);
		crystal.time = 1;
		crystal.setHurtsEntities(0.0f, 0);
		crystal.disableDrop();
		crystal.setDeltaMovement(dir.scale(2.4));
		ExperimentalPowers.setResource(p, power(), "ccrys_id", crystal.getId(), 1e12f);
		ExperimentalPowers.setResource(p, power(), "ccrys_ticks", 70, 70);
		AbilityHelpers.burst(level, spawn, CRYSTAL_DUST, 90, 1.2);
		AbilityHelpers.burst(level, spawn, CRYSTAL_DUST, 40, 1.0);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
	}

	private static void colossalTick(ServerPlayer player, Power power) {
		float ticks = ExperimentalPowers.getResource(player, power, "ccrys_ticks");
		if (ticks <= 0.5f) {
			return;
		}
		ExperimentalPowers.setResource(player, power, "ccrys_ticks", ticks - 1, 70);
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		Entity e = level.getEntity((int) ExperimentalPowers.getResource(player, power, "ccrys_id"));
		if (!(e instanceof FallingBlockEntity crystal) || !crystal.isAlive()) {
			ExperimentalPowers.setResource(player, power, "ccrys_ticks", 0, 70);
			return;
		}
		Vec3 dir = crystal.getDeltaMovement().normalize();
		crystal.setDeltaMovement(dir.scale(2.4));
		crystal.setNoGravity(true);
		Vec3 c = crystal.position();
		level.sendParticles(CRYSTAL_DUST, c.x, c.y, c.z, 12, 0.5, 0.5, 0.5, 0.02);
		level.sendParticles(CRYSTAL_DUST, c.x, c.y, c.z, 14, 0.6, 0.6, 0.6, 0.02);
		boolean impact = crystal.horizontalCollision || crystal.verticalCollision || crystal.onGround();
		java.util.List<LivingEntity> hits = AbilityHelpers.living(level, c, 2.6,
				le -> le != player && le.isAlive() && !(le instanceof net.minecraft.world.entity.decoration.ArmorStand)
						&& (!(le instanceof Player) || (player.getServer() != null && player.getServer().isPvpAllowed()
								&& HeroConfig.get().abilityPvpDamage)));
		if (!hits.isEmpty()) {
			impact = true;
		}
		if (impact || ticks <= 1.5f) {
			for (LivingEntity le : AbilityHelpers.enemiesAround(player, c, 4.0)) {
				AbilityHelpers.hurt(player, le, 55.0f + armorBonus(player));
				AbilityHelpers.knockbackFrom(le, c, 2.4);
				AbilityHelpers.applyControl(le, MobEffects.MOVEMENT_SLOWDOWN, 60, 2);
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(CRYSTAL_DUST, c.x, c.y, c.z, 140, 2.0, 2.0, 2.0, 0.2);
			level.sendParticles(CRYSTAL_DUST, c.x, c.y, c.z, 80, 2.0, 2.0, 2.0, 0.15);
			level.playSound(null, BlockPos.containing(c), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.8f, 0.4f);
			crystal.discard();
			ExperimentalPowers.setResource(player, power, "ccrys_ticks", 0, 70);
		}
	}

	// ---- X: Crystal Skate (sneak + look straight down) -----------------------------------------

	private static final int SKATE_CD = 8 * 20;

	private static void stopSkate(AbilityContext ctx) {
		ctx.setResource("skating", 0, 1);
		ctx.triggerCooldown(SKATE_CD);
		ctx.actionBar("message.projecthero.crystal.skate_off");
		AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.8f, 1.2f);
	}

	private static void skateTick(ServerPlayer p, Power power) {
		if (power == null || ExperimentalPowers.getResource(p, power, "skating") <= 0.5f) {
			return;
		}
		if (!(p.level() instanceof ServerLevel level) || !p.isAlive()
				|| p.getAbilities().flying || p.isFallFlying() || p.isInWater()) {
			ExperimentalPowers.setResource(p, power, "skating", 0, 1);
			ExperimentalPowers.triggerCooldown(p, power, power.ability(AbilitySlot.SLOT_3),
					HeroConfig.get().scaledCooldown(SKATE_CD));
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		double speed = 0.62;
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(flat.x * speed, Math.min(v.y, 0.0) - 0.08, flat.z * speed));
		p.resetFallDistance();
		if (AbilityHelpers.canGrief()) {
			BlockPos base = p.blockPosition().below();
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					BlockPos bp = base.offset(x, 0, z);
					BlockState cur = level.getBlockState(bp);
					if (cur.isAir() || cur.canBeReplaced() || cur.getFluidState().is(Fluids.WATER)) {
						TempBlocks.place(level, bp, CRYSTAL, 40);
					}
				}
			}
		}
		if (p.tickCount % 2 == 0) {
			level.sendParticles(CRYSTAL_DUST, p.getX(), p.getY() + 0.1, p.getZ(), 5, 0.3, 0.05, 0.3, 0.0);
		}
	}

	private static void armorOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.effect(p, MobEffects.DAMAGE_RESISTANCE, 0, true);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB, 0.4, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK, StanceMode.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void armorOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearEffect(p, MobEffects.DAMAGE_RESISTANCE);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
	}
}
