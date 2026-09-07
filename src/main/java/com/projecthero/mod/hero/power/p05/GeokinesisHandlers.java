package com.projecthero.mod.hero.power.p05;

import com.projecthero.mod.hero.Ability;
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
import com.projecthero.mod.hero.power.ConjuredStructures;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.power.TimedSelfFlight;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 05 — Geokinesis (v0.10.7 overhaul).
 *
 * <p>Passives: iron-tool hands whatever is held, earth-family blocks break 50% faster, and a
 * bare-handed hit on an ore vein-mines the whole vein ({@link GeoBareHands}). Sneak + right-click the
 * ground with an empty hand for Seismic Sense — every creature within 30 blocks is outlined for 12 s.
 *
 * <h2>Slots</h2>
 * R Rock Shot (sneak = 10-block ground-shake cone), G Earth Spike (sneak = auto-tracking cone),
 * X Stone Wall (sneak + look down = rock flight, sneak otherwise = Earth Swim), Z hold-5s Earthquake
 * with ravines — or Colossal Rock while a boulder is lifted, V Boulder Lift (held indefinitely),
 * C Earth Armor.
 */
public final class GeokinesisHandlers {
	static final String KEY = "power_05_geokinesis";
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockParticleOption STONE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, STONE);
	private static final BlockParticleOption DIRT_DUST =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
	private static final net.minecraft.resources.ResourceLocation ARMOR_KB =
			com.projecthero.mod.ProjectHeroMod.id("earth_armor_kb");
	private static final net.minecraft.resources.ResourceLocation ARMOR_SPD =
			com.projecthero.mod.ProjectHeroMod.id("earth_armor_slow");
	private static final net.minecraft.resources.ResourceLocation ARMOR_ATK =
			com.projecthero.mod.ProjectHeroMod.id("earth_armor_atk");

	private static final float MAX_STRAIN = 500.0f;
	private static final float STRAIN_DRAIN = MAX_STRAIN / (25 * 20); // earth armor holds ~25 s
	private static final float STRAIN_REGEN = MAX_STRAIN / (40 * 20); // recharges over ~40 s while off

	/** Z is a 5 s (100-tick) hold-to-charge for both the Earthquake and the Colossal Rock combo. */
	private static final int QUAKE_CHARGE = 100;
	private static final int QUAKE_CD = 65 * 20;
	private static final int COLOSSAL_Z_CD = 90 * 20;
	private static final int COLOSSAL_V_CD = 25 * 20;
	private static final int EARTHSWIM_TICKS = 12 * 20;
	private static final int EARTHSWIM_CD = 30 * 20;
	private static final int SEISMIC_TICKS = 12 * 20;
	private static final int SEISMIC_CD = 12 * 20;
	private static final double SEISMIC_RANGE = 30.0;

	private static final ThreadLocal<Boolean> VEIN = ThreadLocal.withInitial(() -> false);

	private GeokinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	private static boolean armorActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	/** Earth Armor adds a flat +10 to every Geokinesis attack. */
	private static float armorBonus(ServerPlayer p) {
		return armorActive(p) ? 10.0f : 0.0f;
	}

	/** Client-safe: is this player currently phased into the earth (Earth Swim). */
	public static boolean earthSwimming(Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(KEY)) {
			return false;
		}
		Float until = st.resources.get(KEY + "/earthswim_until");
		return until != null && until > p.level().getGameTime();
	}

	public static void register() {
		AbilityHandlers.register(KEY, "rock_shot", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				groundShakeCone(ctx);
			} else {
				rockShotSingle(ctx, 9.0f);
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "earth_spike", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				earthSpikeCone(ctx);
				return;
			}
			Vec3 at = AbilityHelpers.aimPoint(p, 18.0);
			LivingEntity direct = AbilityHelpers.raycastEntity(p, 18.0);
			if (direct != null) {
				AbilityHelpers.hurt(p, direct, 17.0f + armorBonus(p));
				AbilityHelpers.push(direct, new Vec3(0, 0.9, 0));
				AbilityHelpers.slow7s(direct);
			}
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 2.0)) {
				if (e == direct) {
					continue;
				}
				AbilityHelpers.hurt(p, e, 10.0f + armorBonus(p));
				AbilityHelpers.push(e, new Vec3(0, 0.7, 0));
				AbilityHelpers.slow7s(e);
			}
			ServerLevel level = ctx.level();
			BlockPos base = BlockPos.containing(at).below();
			if (AbilityHelpers.canGrief() && GeoBareHands.isEarth(level.getBlockState(base))) {
				TempBlocks.place(level, base.above(1), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 160);
				TempBlocks.place(level, base.above(2), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 160);
				TempBlocks.place(level, base.above(3), Blocks.POINTED_DRIPSTONE.defaultBlockState(), 160);
			}
			level.sendParticles(STONE_DUST, at.x, at.y, at.z, 30, 0.4, 0.6, 0.4, 0.1);
			AbilityHelpers.sound(p, SoundEvents.POINTED_DRIPSTONE_LAND, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "stone_wall", Handlers.instant(GeokinesisHandlers::stoneWall));

		// Z: hold for 5 s. Earthquake normally; Colossal Rock if a boulder is currently lifted.
		AbilityHandlers.register(KEY, "earthquake", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				quakePress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				quakeRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				quakeTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "boulder_lift", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (ctx.resource("boulder") > 0.5f) {
				Vec3 at = AbilityHelpers.aimPoint(p, 20.0);
				float geoBonus = com.projecthero.mod.hero.power.PowerCombos.geoStrengthBonus(p);
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 3.0 + geoBonus * 0.3)) {
					AbilityHelpers.hurt(p, e, 20.0f + geoBonus + armorBonus(p));
					AbilityHelpers.knockbackFrom(e, p.getEyePosition(), 1.6);
					AbilityHelpers.slow7s(e);
				}
				ctx.level().sendParticles(STONE_DUST, at.x, at.y, at.z, 50, 1.0, 1.0, 1.0, 0.2);
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), at, STONE_DUST, 2.0);
				AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.7f);
				dropBoulder(p);
				ctx.triggerCooldown();
			} else {
				ctx.setResource("boulder", 1, 1);
				ctx.actionBar("message.projecthero.ability.boulder_ready");
				AbilityHelpers.sound(p, SoundEvents.STONE_PLACE, 1.0f, 0.6f);
			}
		}, GeokinesisHandlers::boulderTick));

		AbilityHandlers.register(KEY, "earth_armor", Handlers.toggle(
				ctx -> {
					com.projecthero.mod.hero.power.ModeMeter.ensureSeeded(ctx, "earth_armor", MAX_STRAIN);
					if (!com.projecthero.mod.hero.power.ModeMeter.hasCharge(ctx, "earth_armor", 40.0f)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.geo.strain_low");
						return;
					}
					earthArmorOn(ctx);
				},
				GeokinesisHandlers::earthArmorOff,
				ctx -> {
					earthArmorOn(ctx);
					AbilityHelpers.modeAura(ctx.player(), DIRT_DUST, 4);
					if (!com.projecthero.mod.hero.power.ModeMeter.drain(ctx, "earth_armor", MAX_STRAIN, STRAIN_DRAIN)) {
						ctx.setToggled(false);
						earthArmorOff(ctx);
						ctx.actionBar("message.projecthero.geo.strain_out");
					}
				}));

		// Bare-handed vein mining: an empty-handed hit on an ore breaks the connected vein.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (VEIN.get() || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) {
				return;
			}
			if (!GeoBareHands.applies(sp) || !sp.getMainHandItem().isEmpty() || !isOre(state)) {
				return;
			}
			VEIN.set(true);
			try {
				veinMine(sl, sp, pos.immutable(), state.getBlock());
			} finally {
				VEIN.set(false);
			}
		});

		// Seismic Sense: sneak + right-click the ground, empty-handed.
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty() || !GeoBareHands.applies(sp)) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.PASS;
			}
			BlockState hitState = serverLevel.getBlockState(hitResult.getBlockPos());
			if (!GeoBareHands.isEarth(hitState)) {
				return InteractionResult.PASS;
			}
			Power power = power();
			if (power == null) {
				return InteractionResult.PASS;
			}
			long now = serverLevel.getGameTime();
			if (ExperimentalPowers.getResource(sp, power, "seismic_until") > now) {
				return InteractionResult.PASS;
			}
			seismicSense(serverLevel, sp);
			ExperimentalPowers.setResource(sp, power, "seismic_until", now + SEISMIC_CD, 1e12f);
			return InteractionResult.SUCCESS;
		});

		PowerPassives.registerTick(KEY, GeokinesisHandlers::passiveTick);
	}

	// ---- passive per-tick upkeep ------------------------------------------------------------------

	private static void passiveTick(ServerPlayer player) {
		Power power = power();
		if (power == null) {
			return;
		}
		com.projecthero.mod.hero.power.ModeMeter.regen(player, power, "earth_armor", MAX_STRAIN, STRAIN_REGEN,
				armorActive(player));

		// Rock flight (Stone Wall, sneak + look straight down) upkeep.
		com.projecthero.mod.hero.power.TimedSelfFlight.tick(player, power, power.ability(AbilitySlot.SLOT_3),
				"rock", STONE_DUST);

		// gentle mining haste while standing on earth (kept from the original kit)
		if (player.onGround() && player.level() instanceof ServerLevel sl
				&& GeoBareHands.isEarth(sl.getBlockState(player.blockPosition().below()))) {
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false, false));
		}

		// Earth Swim upkeep / teardown.
		long now = player.level().getGameTime();
		float swimUntil = ExperimentalPowers.getResource(player, power, "earthswim_until");
		boolean swimOn = ExperimentalPowers.getResource(player, power, "earthswim_on") > 0.5f;
		if (swimUntil > now) {
			player.noPhysics = true;
			if (!player.getAbilities().instabuild) {
				player.getAbilities().mayfly = true;
				if (!player.getAbilities().flying) {
					player.getAbilities().flying = true;
					player.onUpdateAbilities();
				}
			}
			player.resetFallDistance();
			player.setAirSupply(player.getMaxAirSupply());
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 40, 0, false, false, false));
			if (player.level() instanceof ServerLevel sl) {
				BlockPos at = player.blockPosition();
				sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, earthAround(sl, at)),
						player.getX(), player.getY() + 0.9, player.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
			}
		} else if (swimOn) {
			endEarthSwim(player);
		}
	}

	// ---- R: Rock Shot / ground-shake cone ------------------------------------------------------

	private static void rockShotSingle(AbilityContext ctx, float dmg) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
		Vec3 end = AbilityHelpers.aimPoint(p, 24.0);
		AbilityHelpers.line(ctx.level(), p.getEyePosition(), end, STONE_DUST, 3.0);
		if (t != null) {
			AbilityHelpers.hurt(p, t, dmg + armorBonus(p));
			AbilityHelpers.knockbackFrom(t, p.position(), 1.4);
			AbilityHelpers.slow7s(t);
		}
		AbilityHelpers.sound(p, SoundEvents.STONE_BREAK, 1.0f, 0.7f);
	}

	/**
	 * Sneak + R: a ground shockwave in a ~10-block cone. Everything caught in it takes 9 damage and is
	 * slowed for 2 s. 10 s cooldown.
	 */
	private static void groundShakeCone(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();

		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(flat.scale(5.0)), 8.0)) {
			Vec3 to = e.position().subtract(p.position());
			Vec3 toFlat = new Vec3(to.x, 0, to.z);
			if (toFlat.horizontalDistanceSqr() > 10.0 * 10.0 || toFlat.horizontalDistanceSqr() < 1.0e-4) {
				continue;
			}
			if (toFlat.normalize().dot(flat) < 0.55) {
				continue; // outside the cone
			}
			AbilityHelpers.hurt(p, e, 9.0f + armorBonus(p));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
		}

		// a fan of ground-crack dust running out along the cone
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		for (int i = 0; i < 40; i++) {
			double d = 1.5 + level.random.nextDouble() * 8.5;
			double lateral = (level.random.nextDouble() - 0.5) * d * 0.9;
			double bx = p.getX() + flat.x * d + side.x * lateral;
			double bz = p.getZ() + flat.z * d + side.z * lateral;
			BlockPos gp = BlockPos.containing(bx, p.getY() - 0.5, bz);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(gp)),
					bx, p.getY() + 0.1, bz, 5, 0.2, 0.1, 0.2, 0.03);
		}
		level.sendParticles(STONE_DUST, p.getX(), p.getY() + 0.1, p.getZ(), 30, 1.0, 0.1, 1.0, 0.05);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.5f);
		level.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
		ctx.triggerCooldown(10 * 20);
	}

	// ---- G: Earth Spike cone -------------------------------------------------------------------

	private static void earthSpikeCone(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		boolean hitAny = false;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(5.0)), 8.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() > 10.5 || to.normalize().dot(look) < 0.6) {
				continue;
			}
			hitAny = true;
			AbilityHelpers.hurt(p, e, 15.0f + armorBonus(p));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 120, 1);
			BlockPos feet = e.blockPosition();
			if (AbilityHelpers.canGrief() && !(e instanceof Player)) {
				TempBlocks.place(level, feet, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 120);
				TempBlocks.place(level, feet.above(), Blocks.POINTED_DRIPSTONE.defaultBlockState(), 120);
			}
			level.sendParticles(STONE_DUST, e.getX(), e.getY(), e.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
		}
		AbilityHelpers.line(level, eye, eye.add(look.scale(10.0)), STONE_DUST, 2.0);
		AbilityHelpers.sound(p, SoundEvents.POINTED_DRIPSTONE_LAND, 1.2f, 0.6f);
		ctx.triggerCooldown(22 * 20);
	}

	// ---- X: Stone Wall / Earth Swim ------------------------------------------------------------

	private static void stoneWall(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();

		if (p.isShiftKeyDown()) {
			// Sneak + look straight down → 20 s of rock flight (restored). Sneak otherwise → Earth Swim.
			if (p.getXRot() > 75.0f) {
				Power power = ctx.power();
				if (TimedSelfFlight.isActive(p, power, "rock")) {
					return;
				}
				if (TimedSelfFlight.start(p, power, ctx.ability(), "rock")) {
					AbilityHelpers.sound(p, SoundEvents.STONE_PLACE, 1.0f, 0.5f);
					AbilityHelpers.burst(level, p.position(), STONE_DUST, 40, 0.6);
				}
				return;
			}
			startEarthSwim(ctx);
			return;
		}

		float pitch = p.getXRot();
		boolean built;
		if (pitch < -75.0f) {
			built = ConjuredStructures.dome(p, level, STONE);
		} else if (pitch > 75.0f) {
			built = ConjuredStructures.bridge(p, level, STONE);
		} else {
			built = ConjuredStructures.wall(p, level, STONE);
		}
		level.sendParticles(STONE_DUST, p.getX(), p.getY() + 1.0, p.getZ(), 40, 1.5, 1.5, 1.5, 0.1);
		AbilityHelpers.sound(p, SoundEvents.STONE_PLACE, 1.0f, 0.5f);
		if (built || !AbilityHelpers.canGrief()) {
			ctx.triggerCooldown();
		}
	}

	private static void startEarthSwim(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (!ctx.cooldownReady()) {
			ctx.actionBar("message.projecthero.ability.on_cooldown",
					net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
			return;
		}
		if (!GeoBareHands.isEarth(level.getBlockState(p.blockPosition().below()))
				&& !GeoBareHands.isEarth(level.getBlockState(p.blockPosition()))) {
			ctx.actionBar("message.projecthero.geo.no_earth");
			return;
		}
		long now = level.getGameTime();
		ExperimentalPowers.setResource(p, ctx.power(), "earthswim_until", now + EARTHSWIM_TICKS, 1e12f);
		ExperimentalPowers.setResource(p, ctx.power(), "earthswim_on", 1, 1);
		p.noPhysics = true;
		if (!p.getAbilities().instabuild) {
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		p.setDeltaMovement(0, -0.35, 0);
		AbilityHelpers.launchSelf(p, new Vec3(0, -0.35, 0));
		AbilityHelpers.burst(level, p.position(), DIRT_DUST, 50, 0.7);
		level.playSound(null, p.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
		ctx.triggerCooldown(EARTHSWIM_CD);
	}

	private static void endEarthSwim(ServerPlayer p) {
		Power power = power();
		ExperimentalPowers.setResource(p, power, "earthswim_on", 0, 1);
		ExperimentalPowers.setResource(p, power, "earthswim_until", 0, 1e12f);
		p.noPhysics = false;
		if (!p.getAbilities().instabuild && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p)) {
			p.getAbilities().flying = false;
			p.getAbilities().mayfly = false;
			p.onUpdateAbilities();
		}
		// surface out of any solid block
		int guard = 0;
		while (guard++ < 16 && !p.level().getBlockState(p.blockPosition()).getCollisionShape(p.level(), p.blockPosition()).isEmpty()) {
			p.setPos(p.getX(), p.getY() + 1.0, p.getZ());
		}
		p.setDeltaMovement(0, 0, 0);
		p.resetFallDistance();
		if (p.level() instanceof ServerLevel sl) {
			AbilityHelpers.burst(sl, p.position(), DIRT_DUST, 40, 0.6);
		}
		AbilityHelpers.sound(p, SoundEvents.GRAVEL_PLACE, 1.0f, 0.7f);
	}

	// ---- Z: Earthquake hold-charge / Colossal Rock -------------------------------------------

	private static void quakePress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("quake_start") > 0.5f) {
			return; // already charging -- idempotent against packet re-fire
		}
		if (!ExperimentalPowers.cooldownReady(p, ctx.power(), ctx.ability())) {
			ctx.actionBar("message.projecthero.ability.on_cooldown",
					net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f",
							Math.ceil(ExperimentalPowers.cooldownRemainingTicks(p, ctx.power(), ctx.ability()) / 20.0f)));
			return;
		}
		boolean colossal = ctx.resource("boulder") > 0.5f;
		ctx.setResource("quake_start", p.level().getGameTime(), 1e12f);
		ctx.setResource("quake_colossal", colossal ? 1 : 0, 1);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.7f, 0.5f);
	}

	private static void quakeRelease(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("quake_start") <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) ctx.resource("quake_start");
		if (held >= QUAKE_CHARGE) {
			quakeFire(ctx);
		} else {
			quakeCancel(ctx);
		}
	}

	private static void quakeTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float start = ctx.resource("quake_start");
		if (start <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > QUAKE_CHARGE + 100) {
			quakeCancel(ctx);
			return;
		}
		ctx.setResource("ult_charge", Math.min(100f, held * 100f / QUAKE_CHARGE), 100);
		ServerLevel level = ctx.level();
		// root the player and build the charge visuals
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 1.0, 0.2));
		p.hurtMarked = true;
		double frac = Math.min(1.0, held / (double) QUAKE_CHARGE);
		boolean colossal = ctx.resource("quake_colossal") > 0.5f;
		level.sendParticles(colossal ? STONE_DUST : DIRT_DUST, p.getX(), p.getY() + 0.1, p.getZ(),
				6 + (int) (frac * 14), 0.6 * frac + 0.3, 0.05, 0.6 * frac + 0.3, 0.03);
		if (held % 20 == 0) {
			AbilityHelpers.sound(p, SoundEvents.STONE_HIT, 0.7f, 0.4f + (float) frac * 0.6f);
		}
		if (held >= QUAKE_CHARGE) {
			quakeFire(ctx);
		}
	}

	private static void quakeCancel(AbilityContext ctx) {
		ctx.setResource("quake_start", 0, 1e12f);
		ctx.setResource("quake_colossal", 0, 1);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.2f);
	}

	private static void quakeFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		boolean colossal = ctx.resource("quake_colossal") > 0.5f;
		ctx.setResource("quake_start", 0, 1e12f);
		ctx.setResource("quake_colossal", 0, 1);
		ctx.setResource("ult_charge", 0, 100);

		if (colossal && ctx.resource("boulder") > 0.5f) {
			launchColossalRock(p, level);
			dropBoulder(p);
			ctx.triggerCooldown(COLOSSAL_Z_CD);
			ExperimentalPowers.triggerCooldown(p, ctx.power(), ctx.power().ability(AbilitySlot.SLOT_5),
					HeroConfig.get().scaledCooldown(COLOSSAL_V_CD));
			return;
		}

		// The Earthquake: 45 damage in a 25-block radius, plus deep ravines torn open around the epicentre.
		double r = 25.0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
			double d = e.position().distanceTo(p.position());
			float dmg = (float) ((45.0f + armorBonus(p)) * (1.0 - Math.min(0.55, d / r)));
			AbilityHelpers.hurt(p, e, dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 1.4);
			AbilityHelpers.push(e, new Vec3(0, 0.6, 0));
			AbilityHelpers.slow7s(e);
			AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 120, 0);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 2, 3, 0.5, 3, 0);
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.35f);
		level.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
		carveRavines(p, level);
		ctx.triggerCooldown(QUAKE_CD);
	}

	/**
	 * Rips several deep, winding ravines out of the earth radiating from the epicentre. Done in one
	 * pass (not stored between ticks, so it works at any world coordinate) and gated on
	 * {@link AbilityHelpers#canGrief()} like every other terrain effect.
	 */
	private static void carveRavines(ServerPlayer p, ServerLevel level) {
		if (!AbilityHelpers.canGrief()) {
			return;
		}
		int rifts = 5;
		double base = level.random.nextDouble() * Math.PI * 2;
		double ox = p.getX();
		double oz = p.getZ();
		int oy = Mth.floor(p.getY());
		for (int rf = 0; rf < rifts; rf++) {
			double heading = base + rf * (Math.PI * 2 / rifts) + (level.random.nextDouble() - 0.5) * 0.5;
			double curve = (level.random.nextDouble() - 0.5) * 0.12;
			int length = 16 + level.random.nextInt(10); // 16..25 blocks
			double cx = ox;
			double cz = oz;
			for (int step = 1; step <= length; step++) {
				heading += curve;
				cx += Math.cos(heading);
				cz += Math.sin(heading);
				double taper = 1.0 - step / (double) length;
				int half = 1 + (int) Math.round(1.6 * taper); // ~3 wide near the middle, 1 at the tips
				int depth = 3 + (int) Math.round(4.0 * taper); // ~7 deep near the middle, 3 at the tips
				double perpX = -Math.sin(heading);
				double perpZ = Math.cos(heading);
				int surf = surfaceY(level, Mth.floor(cx), Mth.floor(cz), oy);
				for (int w = -half; w <= half; w++) {
					int bx = Mth.floor(cx + perpX * w);
					int bz = Mth.floor(cz + perpZ * w);
					for (int dy = 1; dy >= -depth; dy--) {
						BlockPos bp = new BlockPos(bx, surf + dy, bz);
						BlockState st = level.getBlockState(bp);
						if (!st.isAir() && GeoBareHands.isEarth(st) && st.getDestroySpeed(level, bp) >= 0
								&& level.getFluidState(bp).isEmpty()) {
							level.destroyBlock(bp, false);
						}
					}
				}
				if (step % 3 == 0) {
					level.sendParticles(STONE_DUST, cx, surf + 0.5, cz, 18, 0.6, 0.6, 0.6, 0.06);
				}
			}
		}
		level.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 2.0f, 0.3f);
		level.playSound(null, p.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.8f, 0.4f);
	}

	/** Walks down from a little above {@code guessY} to the first non-air block. */
	private static int surfaceY(ServerLevel level, int x, int z, int guessY) {
		int y = guessY + 4;
		int floor = guessY - 10;
		while (y > floor && level.getBlockState(new BlockPos(x, y, z)).isAir()) {
			y--;
		}
		return y;
	}

	// ---- Colossal Rock ------------------------------------------------------------------------

	private static void launchColossalRock(ServerPlayer p, ServerLevel level) {
		Vec3 dir = p.getLookAngle().normalize();
		ColossalRockEntity rock = new ColossalRockEntity(level, p, 50.0f + armorBonus(p));
		Vec3 spawn = p.getEyePosition().add(dir.scale(3.0));
		rock.setPos(spawn.x, spawn.y, spawn.z);
		rock.shoot(dir.x, dir.y, dir.z, 2.6f, 0.0f); // launched at full speed immediately, ghast-fireball fast
		level.addFreshEntity(rock);
		AbilityHelpers.burst(level, spawn, STONE_DUST, 90, 1.5);
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 0.35f);
		level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 0.5f);
	}

	// ---- V: Boulder Lift -----------------------------------------------------------------------

	private static void boulderTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("boulder") < 0.5f) {
			return;
		}
		// held for as long as the player likes -- but it weighs on them (Slowness I).
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 0, false, false, false));
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(2.5));
		ctx.level().sendParticles(STONE_DUST, hold.x, hold.y, hold.z, 4, 0.4, 0.4, 0.4, 0.0);
	}

	private static void dropBoulder(ServerPlayer p) {
		ExperimentalPowers.setResource(p, power(), "boulder", 0, 1);
	}

	// ---- C: Earth Armor ----------------------------------------------------------------------

	private static void earthArmorOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.effect(p, MobEffects.DAMAGE_RESISTANCE, 1, true);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB, 0.6, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, ARMOR_SPD, -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK, 10.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void earthArmorOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearEffect(p, MobEffects.DAMAGE_RESISTANCE);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, ARMOR_SPD);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
	}

	// ---- Seismic Sense / vein mining ---------------------------------------------------------

	private static void seismicSense(ServerLevel level, ServerPlayer p) {
		int marked = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
				p.getBoundingBox().inflate(SEISMIC_RANGE), e -> e != p && e.isAlive())) {
			if (e.distanceToSqr(p) > SEISMIC_RANGE * SEISMIC_RANGE) {
				continue;
			}
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, SEISMIC_TICKS, 0, false, false, false));
			marked++;
		}
		for (int i = 0; i < 60; i++) {
			double a = i / 60.0 * Math.PI * 2;
			for (double d = 2; d <= SEISMIC_RANGE; d += 6) {
				double bx = p.getX() + Math.cos(a) * d;
				double bz = p.getZ() + Math.sin(a) * d;
				level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, bx, p.getY() + 0.2, bz, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.playSound(null, p.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.4f, 1.6f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
				"message.projecthero.geo.seismic", marked), true);
	}

	private static void veinMine(ServerLevel level, ServerPlayer p, BlockPos start, Block block) {
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		java.util.HashSet<Long> seen = new java.util.HashSet<>();
		queue.add(start);
		seen.add(start.asLong());
		int broken = 0;
		final int cap = 64;
		while (!queue.isEmpty() && broken < cap) {
			BlockPos cur = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos n = cur.offset(dx, dy, dz);
						if (!seen.add(n.asLong()) || n.distSqr(start) > 22 * 22) {
							continue;
						}
						if (level.getBlockState(n).is(block)) {
							queue.add(n.immutable());
							level.destroyBlock(n, true, p);
							broken++;
							if (broken >= cap) {
								return;
							}
						}
					}
				}
			}
		}
	}

	private static boolean isOre(BlockState state) {
		return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES)
				|| state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)
				|| state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.REDSTONE_ORES)
				|| state.is(Blocks.NETHER_GOLD_ORE) || state.is(Blocks.NETHER_QUARTZ_ORE)
				|| state.is(Blocks.ANCIENT_DEBRIS);
	}

	/** A guaranteed-air point ~2.5 blocks along the player's aim (so FallingBlockEntity.fall never eats a block). */
	public static Vec3 airSpawn(ServerLevel level, ServerPlayer p, Vec3 dir) {
		Vec3 eye = p.getEyePosition();
		for (double d = 2.5; d >= 0.6; d -= 0.5) {
			Vec3 c = eye.add(dir.scale(d));
			if (level.getBlockState(BlockPos.containing(c)).isAir()) {
				return c;
			}
		}
		return eye;
	}

	private static BlockState earthAround(ServerLevel level, BlockPos at) {
		for (Direction d : Direction.values()) {
			BlockState st = level.getBlockState(at.relative(d));
			if (GeoBareHands.isEarth(st)) {
				return st;
			}
		}
		return STONE;
	}
}
