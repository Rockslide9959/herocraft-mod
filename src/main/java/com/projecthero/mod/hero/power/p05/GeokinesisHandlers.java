package com.projecthero.mod.hero.power.p05;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.ConjuredStructures;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.StanceMode;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.power.TimedSelfFlight;
import com.projecthero.mod.hero.revamp.BatchBScheduler;
import com.projecthero.mod.hero.revamp.BatchBUtil;
import com.projecthero.mod.hero.visual.MutationVisuals;

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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Power 05 -- Geokinesis (v0.13.22 revamp). Signature: <b>the ground under you is your ammo</b> -- see
 * {@link GroundType}: every rock, spike, quake and pillar is made of what you stand on, and the material changes
 * damage, cooldowns and what a hit does.
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>R Rock Shot -- throw a chunk of the ground (a real projectile that looks like it). Sneak: ground-shake cone.</li>
 *   <li>G Earth Spike -- a line of spikes racing along the ground (16 blocks). Sneak: auto-tracking cone.</li>
 *   <li>X Rock Surf -- ride a slab of the ground across it (toggle). Sneak: Earth Swim.</li>
 *   <li>Z Earthquake -- hold to charge; ravines. Sneak at the press: Colossal Rock (the old Boulder Lift, folded in).</li>
 *   <li>V Stone Wall -- wall / dome (look up) / bridge (look down) of the local stone. Sneak: Seismic Sense.</li>
 *   <li>C Earth Armor -- stone-skin shell, strain bar.</li>
 *   <li>H Sinkhole -- the ground under your target drops away into a temporary pit.</li>
 *   <li>N Tectonic Pillar -- a column erupts under your target (or under you) and launches it skyward.</li>
 * </ul>
 * Passives: iron-tool hands, faster earth mining, bare-hand vein mining ({@link GeoBareHands}), Haste on earth,
 * Sneak + right-click the ground empty-handed for Seismic Sense.
 */
public final class GeokinesisHandlers {
	public static final String KEY = "power_05_geokinesis";
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockParticleOption DIRT_DUST =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
	private static final net.minecraft.resources.ResourceLocation ARMOR_KB =
			com.projecthero.mod.ProjectHeroMod.id("earth_armor_kb");
	private static final net.minecraft.resources.ResourceLocation ARMOR_SPD =
			com.projecthero.mod.ProjectHeroMod.id("earth_armor_slow");
	private static final net.minecraft.resources.ResourceLocation ARMOR_ATK =
			com.projecthero.mod.ProjectHeroMod.id("earth_armor_atk");

	/** Earth Armor strain: +15% over the v0.12 kit (500). */
	public static final float MAX_STRAIN = 575.0f;
	private static final float STRAIN_DRAIN = MAX_STRAIN / (29 * 20);
	private static final float STRAIN_REGEN = MAX_STRAIN / (36 * 20);

	public static final float ROCK_SHOT_DAMAGE = 11.0f;
	private static final int ROCK_SHOT_CD = 34;
	private static final float SHAKE_DAMAGE = 11.0f;
	private static final int SHAKE_CD = 170;
	public static final float SPIKE_DAMAGE = 15.0f;
	private static final int SPIKE_CD = 85;
	private static final double SPIKE_LENGTH = 16.0;
	private static final float CONE_DAMAGE = 18.0f;
	private static final int CONE_CD = 19 * 20;
	/** Z is a 4.25 s hold-to-charge for both the Earthquake and the Colossal Rock. */
	public static final int QUAKE_CHARGE = 85;
	public static final float QUAKE_DAMAGE = 54.0f;
	private static final int QUAKE_CD = 55 * 20;
	private static final float COLOSSAL_DAMAGE = 60.0f;
	private static final int COLOSSAL_CD = 75 * 20;
	private static final int SURF_MAX_TICKS = 15 * 20;
	private static final int SURF_CD = 85;
	private static final float SURF_BUMP_DAMAGE = 7.0f;
	private static final int EARTHSWIM_TICKS = 12 * 20;
	private static final int EARTHSWIM_CD = 25 * 20;
	private static final int SEISMIC_TICKS = 12 * 20;
	private static final int SEISMIC_CD = 10 * 20;
	private static final double SEISMIC_RANGE = 30.0;
	public static final float SINKHOLE_DAMAGE = 10.0f;
	public static final int SINKHOLE_TICKS = 6 * 20;
	private static final int SINKHOLE_ROOT = 3 * 20;
	private static final int SINKHOLE_CD = 12 * 20;
	public static final float PILLAR_DAMAGE = 12.0f;
	private static final int PILLAR_CD = 153;

	private static final ThreadLocal<Boolean> VEIN = ThreadLocal.withInitial(() -> false);

	private GeokinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	public static boolean armorActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	public static boolean surfing(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_3));
	}

	/** Earth Armor adds a flat bonus to every Geokinesis attack (see {@link StanceMode}). */
	private static float armorBonus(ServerPlayer p) {
		return armorActive(p) ? StanceMode.ABILITY_BONUS : 0.0f;
	}

	private static float strengthBonus(ServerPlayer p) {
		return com.projecthero.mod.hero.power.PowerCombos.geoStrengthBonus(p);
	}

	/** Base damage -> this ground's damage, plus Earth Armor and the Geo + Strength combo. */
	private static float dmg(ServerPlayer p, GroundType.Ground g, float base) {
		return g.damage(base + strengthBonus(p)) + armorBonus(p);
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
			if (ctx.player().isShiftKeyDown()) {
				groundShakeCone(ctx);
			} else {
				rockShot(ctx);
			}
		}));

		AbilityHandlers.register(KEY, "earth_spike", Handlers.instant(ctx -> {
			if (ctx.player().isShiftKeyDown()) {
				earthSpikeCone(ctx);
			} else {
				earthSpikeLine(ctx);
			}
		}));

		AbilityHandlers.register(KEY, "rock_surf", Handlers.toggle(GeokinesisHandlers::surfOn,
				GeokinesisHandlers::surfOff, GeokinesisHandlers::surfTick));

		// Z: hold to charge. Earthquake normally; Colossal Rock if sneaking at the press.
		AbilityHandlers.register(KEY, "earthquake", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (BatchBUtil.chargeStart(ctx, "quake_start")) {
					ctx.setResource("quake_colossal", ctx.player().isShiftKeyDown() ? 1 : 0, 1);
					AbilityHelpers.sound(ctx.player(), SoundEvents.RAVAGER_ROAR, 0.7f, 0.5f);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				long held = BatchBUtil.chargeHeld(ctx, "quake_start");
				if (held < 0) {
					return;
				}
				if (held >= QUAKE_CHARGE) {
					quakeFire(ctx);
				} else {
					quakeCancel(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				quakeTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "stone_wall", Handlers.instant(GeokinesisHandlers::stoneWall));

		AbilityHandlers.register(KEY, "earth_armor", Handlers.toggle(
				ctx -> {
					if (StanceMode.blockedByCooldown(ctx)) {
						return;
					}
					ModeMeter.ensureSeeded(ctx, "earth_armor", MAX_STRAIN);
					if (!ModeMeter.hasCharge(ctx, "earth_armor", 40.0f)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.geo.strain_low");
						return;
					}
					earthArmorOn(ctx);
					MutationVisuals.play(ctx.player(), "flex");
					AbilityHelpers.sound(ctx.player(), SoundEvents.DEEPSLATE_PLACE, 1.2f, 0.6f);
					ctx.level().sendParticles(GroundType.under(ctx.player()).dust(), ctx.player().getX(),
							ctx.player().getY() + 1.0, ctx.player().getZ(), 50, 0.5, 0.9, 0.5, 0.1);
				},
				ctx -> {
					earthArmorOff(ctx);
					StanceMode.startDeactivateCooldown(ctx);
				},
				ctx -> {
					earthArmorOn(ctx);
					AbilityHelpers.modeAura(ctx.player(), DIRT_DUST, 3);
					if (!ModeMeter.drain(ctx, "earth_armor", MAX_STRAIN, STRAIN_DRAIN)) {
						ctx.setToggled(false);
						earthArmorOff(ctx);
						StanceMode.startDeactivateCooldown(ctx);
						ctx.actionBar("message.projecthero.geo.strain_out");
					}
				}));

		AbilityHandlers.register(KEY, "sinkhole", Handlers.instant(GeokinesisHandlers::sinkhole));
		AbilityHandlers.register(KEY, "tectonic_pillar", Handlers.instant(GeokinesisHandlers::tectonicPillar));

		// Bare-handed vein mining: an empty-handed hit on an ore breaks the connected vein.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (VEIN.get() || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) {
				return;
			}
			if (!GeoBareHands.applies(sp) || !sp.getMainHandItem().isEmpty() || !GroundType.isOre(state)) {
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
			if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty() || !GeoBareHands.applies(sp)
					|| !(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.PASS;
			}
			if (!GeoBareHands.isEarth(serverLevel.getBlockState(hitResult.getBlockPos()))) {
				return InteractionResult.PASS;
			}
			return trySeismicSense(sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});

		PowerPassives.registerTick(KEY, GeokinesisHandlers::passiveTick);
		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				// only this power's own modifiers: the shared Resistance effect is cleared by the toggle's own off
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, ARMOR_SPD);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
			}
		});
	}

	// ---- passive per-tick upkeep ------------------------------------------------------------------

	private static void passiveTick(ServerPlayer player) {
		Power power = power();
		if (power == null || !(player.level() instanceof ServerLevel sl)) {
			return;
		}
		ModeMeter.regen(player, power, "earth_armor", MAX_STRAIN, STRAIN_REGEN, armorActive(player));

		// v0.13.22: the old rock flight / boulder are gone -- tear down any left running by an older save.
		if (TimedSelfFlight.isActive(player, power, "rock")) {
			TimedSelfFlight.stop(player, power, power.ability(AbilitySlot.SLOT_3), "rock", false);
		}
		if (BatchBUtil.get(player, power, "boulder") > 0.5f) {
			for (int i = 0; i < 7; i++) {
				int id = (int) BatchBUtil.get(player, power, "boulder_id" + i);
				if (id != 0 && sl.getEntity(id) instanceof FallingBlockEntity chunk) {
					chunk.discard();
				}
				ExperimentalPowers.setResource(player, power, "boulder_id" + i, 0, 1.0e9f);
			}
			ExperimentalPowers.setResource(player, power, "boulder", 0, 1);
		}

		// the HUD's "Ground:" line -- only written when the ground actually changes
		if (player.tickCount % 5 == 0) {
			GroundType now = GroundType.under(player).type();
			int last = (int) BatchBUtil.get(player, power, "ground_last") - 1;
			if (last != now.ordinal()) {
				if (last >= 0 && last < GroundType.values().length) {
					ExperimentalPowers.setResource(player, power, GroundType.values()[last].resource(), 0, 1);
				}
				ExperimentalPowers.setResource(player, power, now.resource(), 1, 1);
				ExperimentalPowers.setResource(player, power, "ground_last", now.ordinal() + 1, 16);
			}
		}

		// gentle mining haste while standing on earth (kept from the original kit)
		if (player.onGround() && GeoBareHands.isEarth(sl.getBlockState(player.blockPosition().below()))) {
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false, false));
		}

		// Earth Swim upkeep / teardown.
		long now = sl.getGameTime();
		float swimUntil = BatchBUtil.get(player, power, "earthswim_until");
		boolean swimOn = BatchBUtil.get(player, power, "earthswim_on") > 0.5f;
		if (swimUntil > now) {
			player.noPhysics = true;
			earthSwimSpeed(player);
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
			BlockPos at = player.blockPosition();
			sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, earthAround(sl, at)),
					player.getX(), player.getY() + 0.9, player.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
		} else if (swimOn) {
			endEarthSwim(player);
		}
	}

	// ---- R: Rock Shot / ground-shake cone ------------------------------------------------------

	private static void rockShot(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		GroundType.Ground g = GroundType.under(p);
		Vec3 dir = p.getLookAngle();
		GeoRockEntity rock = new GeoRockEntity(level, p, dmg(p, g, ROCK_SHOT_DAMAGE), g);
		Vec3 hand = AbilityHelpers.handPosition(p);
		rock.setPos(hand.x, hand.y, hand.z);
		rock.shoot(dir.x, dir.y, dir.z, 2.4f, 0.4f);
		level.addFreshEntity(rock);
		// the chunk visibly tears up out of the ground at your feet
		level.sendParticles(g.dust(), p.getX(), p.getY() + 0.1, p.getZ(), 14, 0.35, 0.1, 0.35, 0.08);
		MutationVisuals.play(p, "throw_right");
		AbilityHelpers.sound(p, g.state().getSoundType().getBreakSound(), 1.0f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.SNOWBALL_THROW, 0.8f, 0.5f);
		ctx.triggerCooldown(g.cooldown(ROCK_SHOT_CD));
	}

	/** Sneak + R: a ground shockwave in a ~10-block cone. */
	private static void groundShakeCone(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		GroundType.Ground g = GroundType.under(p);
		Vec3 flat = BatchBUtil.flatLook(p);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(flat.scale(5.0)), 8.0)) {
			Vec3 to = e.position().subtract(p.position());
			Vec3 toFlat = new Vec3(to.x, 0, to.z);
			if (toFlat.horizontalDistanceSqr() > 10.0 * 10.0 || toFlat.horizontalDistanceSqr() < 1.0e-4
					|| toFlat.normalize().dot(flat) < 0.55) {
				continue;
			}
			if (AbilityHelpers.hurtLands(p, e, dmg(p, g, SHAKE_DAMAGE))) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
				g.type().onHit(p, e, p.position());
			}
		}
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
		MutationVisuals.play(p, "stomp");
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.5f);
		level.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
		ctx.triggerCooldown(g.cooldown(SHAKE_CD));
	}

	// ---- G: Earth Spike line / cone -------------------------------------------------------------

	/** The surface cell (first air above solid ground) near {@code (x, z)} around height {@code y}. */
	private static BlockPos surfaceCell(ServerLevel level, double x, double y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int top = Mth.floor(y) + 2;
		for (int yy = top; yy >= top - 5; yy--) {
			BlockPos bp = new BlockPos(bx, yy, bz);
			BlockState below = level.getBlockState(bp.below());
			if ((level.getBlockState(bp).isAir() || level.getBlockState(bp).canBeReplaced())
					&& !below.isAir() && below.isFaceSturdy(level, bp.below(), Direction.UP)) {
				return bp;
			}
		}
		return null;
	}

	/**
	 * G: a line of spikes races out along the ground in the direction you face -- two blocks a tick, 16 blocks long.
	 * Every creature a spike erupts under is hit once (15 x ground), thrown up and slowed; the spikes themselves are
	 * the local stone with a dripstone point and crumble after 2.5 s.
	 */
	private static void earthSpikeLine(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		GroundType.Ground g = GroundType.under(p);
		Vec3 flat = BatchBUtil.flatLook(p);
		Vec3 origin = p.position();
		float damage = dmg(p, g, SPIKE_DAMAGE);
		BlockState column = g.type().buildBlock(g.state());
		Set<Integer> hit = new HashSet<>();
		BatchBScheduler.schedule(level, age -> {
			for (int k = 0; k < 2; k++) {
				double d = 1.8 + age * 2 + k;
				if (d > SPIKE_LENGTH) {
					return false;
				}
				Vec3 pt = origin.add(flat.scale(d));
				BlockPos cell = surfaceCell(level, pt.x, origin.y, pt.z);
				if (cell == null) {
					continue;
				}
				Vec3 c = Vec3.atBottomCenterOf(cell);
				if (AbilityHelpers.canGrief() && c.distanceToSqr(p.position()) > 2.5) {
					boolean tall = (age + k) % 2 == 0;
					TempBlocks.place(level, cell, column, 50);
					if (tall && level.getBlockState(cell).is(column.getBlock())) {
						TempBlocks.place(level, cell.above(), Blocks.POINTED_DRIPSTONE.defaultBlockState(), 50);
					}
				}
				level.sendParticles(g.dust(), c.x, c.y + 0.5, c.z, 12, 0.3, 0.5, 0.3, 0.1);
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 0.8, 0), 1.6)) {
					if (!hit.add(e.getId())) {
						continue;
					}
					if (AbilityHelpers.hurtLands(p, e, damage)) {
						AbilityHelpers.push(e, new Vec3(0, 0.85, 0));
						AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
						g.type().onHit(p, e, origin);
					}
				}
				if ((age + k) % 3 == 0) {
					level.playSound(null, cell, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1.0f,
							0.6f + level.random.nextFloat() * 0.3f);
				}
			}
			return true;
		});
		MutationVisuals.play(p, "ground_pound");
		AbilityHelpers.sound(p, SoundEvents.POINTED_DRIPSTONE_LAND, 1.2f, 0.6f);
		ctx.triggerCooldown(g.cooldown(SPIKE_CD));
	}

	/** Sneak + G: auto-tracks every enemy in a 10-block cone, a spike under each. */
	private static void earthSpikeCone(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		GroundType.Ground g = GroundType.under(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(5.0)), 8.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() > 10.5 || to.normalize().dot(look) < 0.6) {
				continue;
			}
			if (AbilityHelpers.hurtLands(p, e, dmg(p, g, CONE_DAMAGE))) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 120, 1);
				g.type().onHit(p, e, p.position());
			}
			BlockPos feet = e.blockPosition();
			if (AbilityHelpers.canGrief() && !(e instanceof Player)) {
				TempBlocks.place(level, feet, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 120);
				TempBlocks.place(level, feet.above(), Blocks.POINTED_DRIPSTONE.defaultBlockState(), 120);
			}
			level.sendParticles(g.dust(), e.getX(), e.getY(), e.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
		}
		AbilityHelpers.line(level, eye, eye.add(look.scale(10.0)), g.dust(), 2.0);
		MutationVisuals.play(p, "slam_two_hand");
		AbilityHelpers.sound(p, SoundEvents.POINTED_DRIPSTONE_LAND, 1.2f, 0.6f);
		ctx.triggerCooldown(g.cooldown(CONE_CD));
	}

	// ---- X: Rock Surf / Earth Swim ---------------------------------------------------------------

	private static void surfOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (p.isShiftKeyDown()) {
			ctx.setToggled(false); // Sneak + X is Earth Swim, not a surf
			startEarthSwim(ctx);
			return;
		}
		if (BatchBUtil.onCooldown(ctx)) {
			ctx.setToggled(false);
			return;
		}
		GroundType.Ground g = GroundType.under(p);
		if (g.type() == GroundType.NONE) {
			ctx.setToggled(false);
			ctx.actionBar("message.projecthero.geo.no_earth");
			return;
		}
		ctx.setResource("surf_start", ctx.level().getGameTime(), 1e12f);
		ctx.setResource("surf_air_since", 0, 1e12f);
		ctx.level().sendParticles(g.dust(), p.getX(), p.getY() + 0.1, p.getZ(), 30, 0.6, 0.1, 0.6, 0.12);
		AbilityHelpers.sound(p, g.state().getSoundType().getBreakSound(), 1.2f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.STONE_PLACE, 1.0f, 0.5f);
		MutationVisuals.play(p, "p05.surf");
	}

	private static void surfOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		MutationVisuals.stopIf(p, "p05.surf");
		if (BatchBUtil.get(p, ctx.power(), "surf_start") > 0.5f) {
			ctx.setResource("surf_start", 0, 1e12f);
			ctx.setResource("surf_air_since", 0, 1e12f);
			ctx.triggerCooldown(SURF_CD);
			ctx.setResource("no_fall_until", p.level().getGameTime() + 60, 1.0e12f);
			if (p.level() instanceof ServerLevel sl) {
				sl.sendParticles(GroundType.under(p).dust(), p.getX(), p.getY() + 0.1, p.getZ(), 20, 0.5, 0.1, 0.5, 0.1);
			}
			AbilityHelpers.sound(p, SoundEvents.STONE_BREAK, 0.9f, 0.8f);
		}
	}

	/**
	 * X: you ride a slab of the ground itself, carried along the direction you face at a sprinting horse's pace
	 * (faster on ice, slower on deepslate), hopping up single-block steps. Anything you plough into takes 7 x ground
	 * and is bowled aside. Ends after 15 s, in water, if you leave the ground for 1.5 s, or on a second press.
	 */
	private static void surfTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		long now = level.getGameTime();
		float start = ctx.resource("surf_start");
		if (start <= 0.5f) {
			ctx.setResource("surf_start", now, 1e12f); // toggled back on after a relog
			start = now;
		}
		// airborne time, written only on the take-off / landing edges (every write re-syncs the whole state)
		float airSince = ctx.resource("surf_air_since");
		if (p.onGround() && airSince > 0.5f) {
			ctx.setResource("surf_air_since", 0, 1e12f);
			airSince = 0;
		} else if (!p.onGround() && airSince <= 0.5f) {
			ctx.setResource("surf_air_since", now, 1e12f);
			airSince = now;
		}
		long air = airSince > 0.5f ? now - (long) airSince : 0;
		if (now - (long) start > SURF_MAX_TICKS || air > 30 || p.isInWater() || p.getAbilities().flying
				|| p.isPassenger() || !p.isAlive()) {
			ctx.setToggled(false);
			surfOff(ctx);
			return;
		}
		GroundType.Ground g = GroundType.under(p);
		double speed = switch (g.type()) {
			case FROST -> 0.95;
			case SAND -> 0.78;
			case DEEPSLATE -> 0.62;
			case NONE -> 0.5;
			default -> 0.72;
		};
		Vec3 flat = BatchBUtil.flatLook(p);
		Vec3 v = p.getDeltaMovement();
		double vy = v.y;
		BlockPos ahead = BlockPos.containing(p.getX() + flat.x * 0.9, p.getY() + 0.1, p.getZ() + flat.z * 0.9);
		if (p.onGround() && !level.getBlockState(ahead).getCollisionShape(level, ahead).isEmpty()
				&& level.getBlockState(ahead.above()).getCollisionShape(level, ahead.above()).isEmpty()
				&& level.getBlockState(p.blockPosition().above(2)).getCollisionShape(level, p.blockPosition().above(2)).isEmpty()) {
			vy = 0.46; // hop the step
		}
		AbilityHelpers.launchSelf(p, new Vec3(flat.x * speed, vy, flat.z * speed));
		MutationVisuals.ensure(p, "p05.surf");
		if (p.tickCount % 2 == 0) {
			level.sendParticles(g.dust(), p.getX() - flat.x * 0.6, p.getY() + 0.05, p.getZ() - flat.z * 0.6,
					4, 0.3, 0.05, 0.3, 0.06);
		}
		if (p.tickCount % 8 == 0) {
			level.playSound(null, p.blockPosition(), g.state().getSoundType().getStepSound(), SoundSource.PLAYERS, 0.7f, 0.6f);
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(flat.scale(1.0)).add(0, 0.8, 0), 1.3)) {
			if (AbilityHelpers.hurtLands(p, e, dmg(p, g, SURF_BUMP_DAMAGE))) {
				AbilityHelpers.push(e, flat.scale(1.1).add(0, 0.45, 0));
				g.type().onHit(p, e, p.position());
			}
		}
	}

	private static void startEarthSwim(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		long now = level.getGameTime();
		float readyAt = ctx.resource("earthswim_ready_at");
		if (readyAt > now) {
			ctx.actionBar("message.projecthero.ability.on_cooldown",
					net.minecraft.network.chat.Component.translatable("projecthero.power.power_05_geokinesis.earth_swim"),
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil((readyAt - now) / 20.0f)));
			return;
		}
		if (!GeoBareHands.isEarth(level.getBlockState(p.blockPosition().below()))
				&& !GeoBareHands.isEarth(level.getBlockState(p.blockPosition()))) {
			ctx.actionBar("message.projecthero.geo.no_earth");
			return;
		}
		ctx.setResource("earthswim_until", now + EARTHSWIM_TICKS, 1e12f);
		ctx.setResource("earthswim_on", 1, 1);
		ctx.setResource("earthswim_ready_at", now + EARTHSWIM_TICKS
				+ com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(EARTHSWIM_CD), 1e12f);
		p.noPhysics = true;
		if (!p.getAbilities().instabuild) {
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		AbilityHelpers.launchSelf(p, new Vec3(0, -0.35, 0));
		AbilityHelpers.burst(level, p.position(), DIRT_DUST, 50, 0.7);
		MutationVisuals.play(p, "dash_forward");
		level.playSound(null, p.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
	}

	/** Fast while inside the ground, slow the moment you are not (v0.10.10). */
	private static void earthSwimSpeed(ServerPlayer p) {
		BlockPos eye = BlockPos.containing(p.getEyePosition());
		boolean submerged = GeoBareHands.isEarth(p.level().getBlockState(eye))
				|| GeoBareHands.isEarth(p.level().getBlockState(p.blockPosition()))
				|| p.level().getBlockState(eye).isSuffocating(p.level(), eye);
		if (!p.getAbilities().instabuild) {
			p.getAbilities().setFlyingSpeed(submerged ? 0.14f : 0.03f);
			p.onUpdateAbilities();
		}
		if (!submerged) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		if (v.lengthSqr() > 1.0e-4) {
			p.setDeltaMovement(v.add(v.normalize().scale(0.06)));
			p.hurtMarked = true;
		}
	}

	private static void endEarthSwim(ServerPlayer p) {
		Power power = power();
		ExperimentalPowers.setResource(p, power, "earthswim_on", 0, 1);
		ExperimentalPowers.setResource(p, power, "earthswim_until", 0, 1e12f);
		p.noPhysics = false;
		if (!p.getAbilities().instabuild) {
			p.getAbilities().setFlyingSpeed(0.05f);
		}
		if (!p.getAbilities().instabuild && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p)) {
			p.getAbilities().flying = false;
			p.getAbilities().mayfly = false;
			p.onUpdateAbilities();
		}
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

	private static void quakeTick(AbilityContext ctx) {
		long held = BatchBUtil.chargeHeld(ctx, "quake_start");
		if (held < 0) {
			return;
		}
		ServerPlayer p = ctx.player();
		if (held > QUAKE_CHARGE + 100) {
			quakeCancel(ctx);
			return;
		}
		BatchBUtil.chargeMeter(ctx, held, QUAKE_CHARGE);
		ServerLevel level = ctx.level();
		boolean colossal = ctx.resource("quake_colossal") > 0.5f;
		MutationVisuals.ensure(p, colossal ? "carry_overhead" : "crouch_charge");
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 1.0, 0.2));
		p.hurtMarked = true;
		double frac = Math.min(1.0, held / (double) QUAKE_CHARGE);
		GroundType.Ground g = GroundType.under(p);
		if (colossal) {
			// the boulder gathers overhead as you charge
			Vec3 over = p.position().add(0, p.getBbHeight() + 0.8 + frac * 0.8, 0);
			level.sendParticles(g.dust(), over.x, over.y, over.z, 4 + (int) (frac * 10), 0.3 + frac * 0.6,
					0.3 + frac * 0.6, 0.3 + frac * 0.6, 0.02);
			level.sendParticles(g.dust(), p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.8, 0.05, 0.8, 0.1);
		} else {
			level.sendParticles(g.dust(), p.getX(), p.getY() + 0.1, p.getZ(),
					6 + (int) (frac * 14), 0.6 * frac + 0.3, 0.05, 0.6 * frac + 0.3, 0.03);
		}
		if (held % 20 == 0) {
			AbilityHelpers.sound(p, SoundEvents.STONE_HIT, 0.7f, 0.4f + (float) frac * 0.6f);
		}
		if (held >= QUAKE_CHARGE) {
			quakeFire(ctx);
		}
	}

	private static void quakeCancel(AbilityContext ctx) {
		BatchBUtil.chargeClear(ctx, "quake_start");
		ctx.setResource("quake_colossal", 0, 1);
		MutationVisuals.stopIf(ctx.player(), "crouch_charge");
		MutationVisuals.stopIf(ctx.player(), "carry_overhead");
		AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.2f);
	}

	private static void quakeFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		boolean colossal = ctx.resource("quake_colossal") > 0.5f;
		BatchBUtil.chargeClear(ctx, "quake_start");
		ctx.setResource("quake_colossal", 0, 1);
		GroundType.Ground g = GroundType.under(p);

		if (colossal) {
			MutationVisuals.play(p, "p05.heave");
			Vec3 dir = p.getLookAngle().normalize();
			ColossalRockEntity rock = new ColossalRockEntity(level, p, dmg(p, g, COLOSSAL_DAMAGE));
			Vec3 spawn = p.getEyePosition().add(dir.scale(3.0));
			rock.setPos(spawn.x, spawn.y, spawn.z);
			rock.shoot(dir.x, dir.y, dir.z, 2.6f, 0.0f);
			level.addFreshEntity(rock);
			AbilityHelpers.burst(level, spawn, g.dust(), 90, 1.5);
			level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 0.35f);
			level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 0.5f);
			ctx.triggerCooldown(COLOSSAL_CD);
			return;
		}

		MutationVisuals.play(p, "slam_two_hand");
		double r = 25.0;
		float base = dmg(p, g, QUAKE_DAMAGE);
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), r)) {
			double d = e.position().distanceTo(p.position());
			float amount = (float) (base * (1.0 - Math.min(0.55, d / r)));
			if (AbilityHelpers.hurtBurst(p, e, amount)) {
				g.type().onHit(p, e, p.position());
			}
			AbilityHelpers.knockbackFrom(e, p.position(), 1.4);
			AbilityHelpers.push(e, new Vec3(0, 0.6, 0));
			AbilityHelpers.slow7s(e);
			AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 120, 0);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 2, 3, 0.5, 3, 0);
		level.sendParticles(g.dust(), p.getX(), p.getY() + 0.3, p.getZ(), 200, 8, 0.3, 8, 0.2);
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.35f);
		level.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
		carveRavines(p, level);
		ctx.triggerCooldown(QUAKE_CD);
	}

	/** Several deep, winding ravines radiating from the epicentre (terrain-gated; a 3x3 pad under you survives). */
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
			int length = 16 + level.random.nextInt(10);
			double cx = ox;
			double cz = oz;
			for (int step = 1; step <= length; step++) {
				heading += curve;
				cx += Math.cos(heading);
				cz += Math.sin(heading);
				double taper = 1.0 - step / (double) length;
				int half = 1 + (int) Math.round(1.6 * taper);
				int depth = 3 + (int) Math.round(4.0 * taper);
				double perpX = -Math.sin(heading);
				double perpZ = Math.cos(heading);
				int surf = surfaceY(level, Mth.floor(cx), Mth.floor(cz), oy);
				for (int w = -half; w <= half; w++) {
					int bx = Mth.floor(cx + perpX * w);
					int bz = Mth.floor(cz + perpZ * w);
					if (Math.abs(bx + 0.5 - ox) < 2.0 && Math.abs(bz + 0.5 - oz) < 2.0) {
						continue;
					}
					for (int dy = 1; dy >= -depth; dy--) {
						BlockPos bp = new BlockPos(bx, surf + dy, bz);
						BlockState st = level.getBlockState(bp);
						if (!st.isAir() && GeoBareHands.isEarth(st) && st.getDestroySpeed(level, bp) >= 0
								&& level.getFluidState(bp).isEmpty()) {
							level.destroyBlock(bp, false);
						}
					}
				}
			}
		}
		level.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 2.0f, 0.3f);
		level.playSound(null, p.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.8f, 0.4f);
	}

	private static int surfaceY(ServerLevel level, int x, int z, int guessY) {
		int y = guessY + 4;
		int floor = guessY - 10;
		while (y > floor && level.getBlockState(new BlockPos(x, y, z)).isAir()) {
			y--;
		}
		return y;
	}

	// ---- V: Stone Wall / Seismic Sense -----------------------------------------------------------

	private static void stoneWall(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (p.isShiftKeyDown()) {
			if (trySeismicSense(p)) {
				MutationVisuals.play(p, "ground_pound");
			}
			return;
		}
		GroundType.Ground g = GroundType.under(p);
		BlockState material = g.type().buildBlock(g.state());
		float pitch = p.getXRot();
		boolean built;
		if (pitch < -75.0f) {
			built = ConjuredStructures.dome(p, level, material);
			MutationVisuals.play(p, "cast_raise_both");
		} else if (pitch > 75.0f) {
			built = ConjuredStructures.bridge(p, level, material);
			MutationVisuals.play(p, "ground_pound");
		} else {
			built = ConjuredStructures.wall(p, level, material);
			MutationVisuals.play(p, "summon_ground");
		}
		level.sendParticles(g.dust(), p.getX(), p.getY() + 1.0, p.getZ(), 40, 1.5, 1.5, 1.5, 0.1);
		AbilityHelpers.sound(p, SoundEvents.STONE_PLACE, 1.0f, 0.5f);
		if (built || !AbilityHelpers.canGrief()) {
			ctx.triggerCooldown();
		}
	}

	private static boolean trySeismicSense(ServerPlayer sp) {
		Power power = power();
		if (power == null || !(sp.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		if (ExperimentalPowers.getResource(sp, power, "seismic_until") > now) {
			return false;
		}
		seismicSense(level, sp);
		ExperimentalPowers.setResource(sp, power, "seismic_until", now + SEISMIC_CD, 1e12f);
		return true;
	}

	// ---- H: Sinkhole ---------------------------------------------------------------------------

	/**
	 * H: the ground beneath whatever you are looking at (20 blocks) drops away -- a pit three deep, sized to the
	 * target, that closes back up after 6 s (lifting anything still in it back to the surface). The target takes
	 * 10 x ground, is dragged down and rooted for 3 s. Players and boss-sized creatures are rooted without the dig.
	 */
	private static void sinkhole(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = AbilityHelpers.raycastEntity(p, 20.0);
		if (t == null) {
			ctx.actionBar("message.projecthero.geo.no_target");
			return;
		}
		GroundType.Ground g = GroundType.under(level, t.position());
		BlockPos base = t.blockPosition();
		boolean dig = AbilityHelpers.canGrief() && !(t instanceof Player) && t.getMaxHealth() <= 200.0f;
		int dug = 0;
		if (dig) {
			int w = Math.max(1, Mth.ceil(t.getBbWidth() / 2.0));
			for (int dy = 3; dy >= 1; dy--) { // bottom-up, so the refill lifts anything inside back out in order
				for (int dx = -w; dx <= w; dx++) {
					for (int dz = -w; dz <= w; dz++) {
						BlockPos bp = base.offset(dx, -dy, dz);
						// never open the ground under the caster
						if (Math.abs(bp.getX() + 0.5 - p.getX()) < 1.3 && Math.abs(bp.getZ() + 0.5 - p.getZ()) < 1.3) {
							continue;
						}
						if (BatchBScheduler.removeTemporarily(level, bp, SINKHOLE_TICKS)) {
							dug++;
						}
					}
				}
			}
		}
		if (AbilityHelpers.hurtLands(p, t, dmg(p, g, SINKHOLE_DAMAGE))) {
			g.type().onHit(p, t, p.position());
		}
		t.setDeltaMovement(0, -0.8, 0);
		t.hurtMarked = true;
		AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, SINKHOLE_ROOT, 9);
		AbilityHelpers.applyControl(t, MobEffects.JUMP, SINKHOLE_ROOT, -10);
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0 * Math.PI * 2;
			level.sendParticles(g.dust(), t.getX() + Math.cos(a) * 1.6, t.getY() + 0.1, t.getZ() + Math.sin(a) * 1.6,
					3, 0.1, 0.1, 0.1, 0.05);
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, t.getX(), t.getY(), t.getZ(), 4, 0.6, 0.2, 0.6, 0.01);
		level.playSound(null, t.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, t.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.4f, 0.4f);
		ctx.setResource("sinkhole_dug", dug, 1000);
		MutationVisuals.play(p, "p05.sinkhole");
		ctx.triggerCooldown(GroundType.under(p).cooldown(SINKHOLE_CD));
	}

	// ---- N: Tectonic Pillar --------------------------------------------------------------------

	/**
	 * N: a column of the local stone erupts. Looking at a creature (20 blocks): it bursts up under them -- 12 x ground
	 * and a launch straight into the air. Otherwise it erupts under you and throws you ~9 blocks up (8 s of no fall
	 * damage). The column stands for 4 s.
	 */
	private static void tectonicPillar(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = AbilityHelpers.raycastEntity(p, 20.0);
		Entity rider = t != null ? t : p;
		GroundType.Ground g = GroundType.under(level, rider.position());
		BlockState block = g.type().buildBlock(g.state());
		BlockPos base = rider.blockPosition();
		if (t != null) {
			if (AbilityHelpers.hurtLands(p, t, dmg(p, g, PILLAR_DAMAGE))) {
				g.type().onHit(p, t, p.position());
			}
			AbilityHelpers.push(t, new Vec3(0, 1.45, 0));
			MutationVisuals.play(p, "summon_ground");
		} else {
			Vec3 look = BatchBUtil.flatLook(p);
			AbilityHelpers.launchSelf(p, new Vec3(look.x * 0.25, 1.5, look.z * 0.25));
			ctx.setResource("no_fall_until", level.getGameTime() + 160, 1.0e12f);
			MutationVisuals.play(p, "leap");
		}
		BatchBScheduler.schedule(level, age -> {
			if (age > 4) {
				return false;
			}
			BlockPos cell = base.above(age);
			if (AbilityHelpers.canGrief()) {
				// never inside a player (the launch has already carried the rider clear)
				boolean occupied = !level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(cell)).isEmpty();
				if (!occupied) {
					TempBlocks.place(level, cell, block, 80);
				}
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block), cell.getX() + 0.5, cell.getY() + 0.5,
					cell.getZ() + 0.5, 14, 0.5, 0.3, 0.5, 0.1);
			if (age == 0) {
				level.playSound(null, cell, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
			}
			return true;
		});
		level.sendParticles(g.dust(), base.getX() + 0.5, base.getY() + 0.1, base.getZ() + 0.5, 40, 1.0, 0.1, 1.0, 0.15);
		level.playSound(null, base, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
		ctx.triggerCooldown(GroundType.under(p).cooldown(PILLAR_CD));
	}

	// ---- C: Earth Armor ----------------------------------------------------------------------

	private static void earthArmorOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.effect(p, MobEffects.DAMAGE_RESISTANCE, 1, true);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB, 0.6, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, ARMOR_SPD, -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK, StanceMode.MELEE_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
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
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SEISMIC_RANGE),
				e -> e != p && e.isAlive() && !(e instanceof Player))) {
			if (e.distanceToSqr(p) > SEISMIC_RANGE * SEISMIC_RANGE) {
				continue;
			}
			// never mark players -- a shared GLOWING effect makes them trivial to spot for everyone
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, SEISMIC_TICKS, 0, false, false, false));
			marked++;
		}
		for (int i = 0; i < 60; i++) {
			double a = i / 60.0 * Math.PI * 2;
			for (double d = 2; d <= SEISMIC_RANGE; d += 6) {
				level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.getX() + Math.cos(a) * d, p.getY() + 0.2,
						p.getZ() + Math.sin(a) * d, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.playSound(null, p.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.4f, 1.6f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.geo.seismic", marked), true);
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

	/** A guaranteed-air point ~2.5 blocks along the player's aim. */
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
