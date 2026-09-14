package com.projecthero.mod.hero.power.p22;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.TempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Power 22 — Plant Manipulation / Chlorokinesis. */
public final class PlantManipulationHandlers {
	private static final String KEY = "power_22_plant_manipulation_chlorokinesis";
	private static final java.util.Map<java.util.UUID, Long> ROOTED_UNTIL = new java.util.HashMap<>();
	private static final java.util.List<PendingTree> PENDING_TREES = new java.util.ArrayList<>();

	private record PendingTree(ServerLevel level, BlockPos pos, long readyAt) {
	}

	private PlantManipulationHandlers() {
	}

	public static void register() {
		// R -- Thorn Shot, fired from the hand. Shift+R is Branch Thrust.
		AbilityHandlers.register(KEY, "thorn_shot", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			if (p.isShiftKeyDown()) {
				boolean onNature = PlantManipulationHandlers.onNatureGround(level, p.blockPosition());
				boolean holdingPlant = p.getMainHandItem().is(Items.BONE_MEAL) || p.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem bi
						&& bi.getBlock() instanceof SaplingBlock;
				if (!onNature && !holdingPlant) {
					ctx.actionBar("message.projecthero.plant.need_nature");
					return;
				}
				LivingEntity t = AbilityHelpers.raycastEntity(p, 15.0);
				if (t == null) {
					return;
				}
				branchThrust(ctx, t);
				ctx.triggerCooldown(10 * 20);
				return;
			}
			LivingEntity t = AbilityHelpers.raycastEntity(p, 22.0);
			Vec3 from = handOrigin(p);
			AbilityHelpers.line(level, from, AbilityHelpers.aimPoint(p, 22.0), ParticleTypes.COMPOSTER, 3.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 8.0f + natureBonus(p));
				AbilityHelpers.applyControl(t, MobEffects.POISON, 160, 2); // Poison III, 8s
			}
			AbilityHelpers.sound(p, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 0.8f);
			ctx.triggerCooldown();
		}));

		// G -- Thorn Snare: a damage-over-time root. Shift+G is an AoE cone version.
		AbilityHandlers.register(KEY, "vine_grab", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				Vec3 look = p.getLookAngle();
				boolean any = false;
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 10.0)) {
					Vec3 dir = e.position().subtract(p.position());
					if (dir.lengthSqr() < 1.0e-4 || dir.normalize().dot(look) < 0.5) {
						continue;
					}
					rootAndSnare(ctx, e);
					any = true;
				}
				if (any) {
					AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_BREAK, 1.0f, 0.6f);
					ctx.triggerCooldown();
				}
				return;
			}
			LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
			if (t != null) {
				rootAndSnare(ctx, t);
				ctx.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + 1, t.getZ(), 15, 0.4, 0.6, 0.4, 0.0);
			}
			AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_BREAK, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}));

		// X -- Vine Swing pulls YOU to a block, or an aimed-at target TO you.
		AbilityHandlers.register(KEY, "vine_swing", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity target = AbilityHelpers.raycastEntity(p, 30.0);
			if (target != null) {
				AbilityHelpers.push(target, p.position().subtract(target.position()).normalize().scale(1.6).add(0, 0.2, 0));
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), target.position(), ParticleTypes.COMPOSTER, 2.0);
				AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_HIT, 1.0f, 0.9f);
				ctx.triggerCooldown();
				return;
			}
			var hit = AbilityHelpers.raycastBlock(p, 100.0);
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
				return;
			}
			Vec3 anchor = Vec3.atCenterOf(hit.getBlockPos());
			Vec3 pull = anchor.subtract(p.position());
			AbilityHelpers.launchSelf(p, pull.normalize().scale(1.8).add(0, 0.4, 0));
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), anchor, ParticleTypes.COMPOSTER, 2.0);
			AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_HIT, 1.0f, 1.2f);
			ctx.setResource("no_fall_until", p.level().getGameTime() + 200, 1.0e12f);
			ctx.triggerCooldown();
		}));

		// Z -- hold for 5 seconds to charge Overgrowth.
		AbilityHandlers.register(KEY, "overgrowth", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("og_charging") > 0.5f || !ctx.cooldownReady()) {
					return;
				}
				ctx.setResource("og_charging", 1, 1);
				ctx.setResource("og_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("og_charging") > 0.5f) {
					ctx.setResource("og_charging", 0, 1);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("og_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("og_charge_start");
				if (p.tickCount % 3 == 0) {
					ctx.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 4, 0.5, 0.6, 0.5, 0.0);
				}
				if (held < 5 * 20) {
					return;
				}
				ctx.setResource("og_charging", 0, 1);
				ServerLevel level = ctx.level();
				double r = 20.0;
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
					AbilityHelpers.hurt(p, e, 45.0f + natureBonus(p));
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 200, 8);
					AbilityHelpers.applyControl(e, MobEffects.POISON, 200, 9); // Poison X, 10s
					ROOTED_UNTIL.put(e.getUUID(), p.level().getGameTime() + 200);
					e.setDeltaMovement(e.getDeltaMovement().multiply(0.1, 1, 0.1));
				}
				if (AbilityHelpers.canGrief()) {
					for (int i = 0; i < 60; i++) {
						BlockPos bp = BlockPos.containing(p.getX() + level.random.nextGaussian() * r * 0.4, p.getY(),
								p.getZ() + level.random.nextGaussian() * r * 0.4);
						if (level.getBlockState(bp).canBeReplaced() && level.getBlockState(bp.below()).isSolidRender(level, bp.below())) {
							TempBlocks.place(level, bp, level.random.nextBoolean() ? Blocks.OAK_LEAVES.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState(), 120);
						}
					}
				}
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 150, r / 2, 1, r / 2, 0.1);
				AbilityHelpers.sound(p, SoundEvents.GRASS_BREAK, 1.6f, 0.4f);
				ctx.triggerCooldown(60 * 20);
			}
		});

		// V -- Living Wall (unchanged mechanics; sneak-charge still layers on top of the new always-on
		// double-strength bonemeal touch registered below).
		AbilityHandlers.register(KEY, "living_wall", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			if (p.isShiftKeyDown()) {
				ctx.setResource("bonemeal_until", p.level().getGameTime() + 600, 1.0e12f);
				ctx.actionBar("message.projecthero.plant.bonemeal_touch");
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.6, 0.4, 0.0);
				AbilityHelpers.sound(p, SoundEvents.BONE_MEAL_USE, 1.0f, 1.0f);
				ctx.triggerCooldown();
				return;
			}
			BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
			float pitch = p.getXRot();
			boolean built;
			if (pitch < -75.0f) {
				built = com.projecthero.mod.hero.power.ConjuredStructures.dome(p, level, leaves);
			} else if (pitch > 75.0f) {
				built = com.projecthero.mod.hero.power.ConjuredStructures.bridge(p, level, leaves);
			} else {
				built = com.projecthero.mod.hero.power.ConjuredStructures.wall(p, level, leaves);
			}
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.5, p.getZ(), 30, 1.5, 1.5, 1.5, 0.0);
			AbilityHelpers.sound(p, SoundEvents.GRASS_PLACE, 1.0f, 0.6f);
			if (built || !AbilityHelpers.canGrief()) {
				ctx.triggerCooldown();
			}
		}));

		// Always-on: right-clicking a growable plant bonemeals it twice.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer sp) || !ExperimentalPowers.owns(sp, Powers.byKey(KEY))) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (state.getBlock() instanceof BonemealableBlock bm && world instanceof ServerLevel sl
					&& bm.isValidBonemealTarget(sl, pos, state) && bm.isBonemealSuccess(sl, sl.random, pos, state)) {
				bm.performBonemeal(sl, sl.random, pos, state);
				// v0.10.20 crash fix: re-resolve the block fresh -- the first bonemeal can turn a sapling
				// into a full tree, and handing the new block's state to the OLD SaplingBlock instance's
				// performBonemeal crashes the server (see growTreeAt for the full explanation).
				BlockState after = sl.getBlockState(pos);
				if (after.getBlock() instanceof BonemealableBlock bm2 && bm2.isValidBonemealTarget(sl, pos, after)) {
					bm2.performBonemeal(sl, sl.random, pos, after);
				}
				sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						16, 0.4, 0.4, 0.4, 0.0);
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		// The Living Wall sneak-charge: right-clicking a block bone-meals it (kept for the utility window).
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer sp)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			var power = Powers.byKey(KEY);
			if (power == null || !ExperimentalPowers.owns(sp, power)
					|| ExperimentalPowers.getResource(sp, power, "bonemeal_until") <= sp.level().getGameTime()) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			net.minecraft.world.item.ItemStack meal = new net.minecraft.world.item.ItemStack(Items.BONE_MEAL);
			boolean grew = net.minecraft.world.item.BoneMealItem.growCrop(meal, world, pos);
			if (!grew && world.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
				grew = net.minecraft.world.item.BoneMealItem.growWaterPlant(meal, world, pos, hitResult.getDirection());
			}
			if (grew) {
				if (world instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.0);
				}
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		// C -- Nature's Blessing.
		AbilityHandlers.register(KEY, "natures_blessing", Handlers.toggle(
				Handlers.noop(),
				ctx -> com.projecthero.mod.hero.power.PowerToggles.clearModifier(ctx.player(),
						net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK),
				ctx -> {
					ServerPlayer p = ctx.player();
					AbilityHelpers.modeAura(p, ParticleTypes.HAPPY_VILLAGER, 3);
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 3.0)) {
						AbilityHelpers.applyControl(e, MobEffects.POISON, 60, 2); // Poison III
					}
					if (p.tickCount % 20 != 0) {
						return;
					}
					if (isBlessed((ServerLevel) p.level(), p.blockPosition())) {
						p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false, true));
						com.projecthero.mod.hero.power.PowerToggles.modifier(p,
								net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK, 10.0,
								net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
					} else {
						com.projecthero.mod.hero.power.PowerToggles.clearModifier(p,
								net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK);
					}
				}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				com.projecthero.mod.hero.power.PowerToggles.clearModifier(player,
						net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, PlantManipulationHandlers::passiveTick);
	}

	private static void passiveTick(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (player.tickCount % 100 == 0 && player.getHealth() < player.getMaxHealth() && nearVegetation(level, player.blockPosition())) {
			player.heal(1.0f);
		}
		// Standing on, or against, living ground grants a small buff.
		if (player.tickCount % 20 == 0 && (onNatureGround(level, player.blockPosition()) || againstNatureWall(level, player.blockPosition()))) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, false, false));
		}
		// Plants within 30 blocks grow roughly 50% faster: an occasional bonemeal-tier assist.
		if (player.tickCount % 100 == 0) {
			int budget = 25;
			BlockPos origin = player.blockPosition();
			for (BlockPos bp : BlockPos.betweenClosed(origin.offset(-30, -6, -30), origin.offset(30, 6, 30))) {
				if (budget <= 0) {
					break;
				}
				if (bp.distSqr(origin) > 30.0 * 30.0 || level.random.nextInt(6) != 0) {
					continue;
				}
				BlockState state = level.getBlockState(bp);
				if (state.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, bp, state)
						&& bm.isBonemealSuccess(level, level.random, bp, state)) {
					bm.performBonemeal(level, level.random, bp, state);
					budget--;
				}
			}
		}
		// Prune stale Thorn Snare / Overgrowth root markers.
		if (!ROOTED_UNTIL.isEmpty() && player.tickCount % 20 == 0) {
			long now = level.getGameTime();
			ROOTED_UNTIL.values().removeIf(t -> t <= now);
		}
		// Branch Thrust: the trail of logs vanishes on its own (TempBlocks); once its time is up, plant
		// and force-grow the tree at the cast point.
		if (!PENDING_TREES.isEmpty()) {
			long now = level.getGameTime();
			PENDING_TREES.removeIf(t -> {
				if (t.readyAt() > now) {
					return false;
				}
				growTreeAt(t.level(), t.pos());
				return true;
			});
		}
	}

	public static void clearSessionState() {
		ROOTED_UNTIL.clear();
		PENDING_TREES.clear();
	}

	private static void rootAndSnare(AbilityContext ctx, LivingEntity t) {
		ServerPlayer p = ctx.player();
		AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 160, 8);
		AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, 160, 1);
		t.setDeltaMovement(t.getDeltaMovement().multiply(0, 1, 0));
		t.hurtMarked = true;
		ROOTED_UNTIL.put(t.getUUID(), p.level().getGameTime() + 160);
		AbilityHelpers.hurt(p, t, 3.0f + natureBonus(p));
	}

	private static void branchThrust(AbilityContext ctx, LivingEntity target) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 from = p.position();
		Vec3 to = target.position();
		Block log = biomeLog(level, p.blockPosition());
		java.util.List<BlockPos> trail = new java.util.ArrayList<>();
		double dist = from.distanceTo(to);
		int steps = Math.max(1, (int) dist);
		for (int i = 0; i <= steps; i++) {
			Vec3 pt = from.lerp(to, (double) i / steps);
			BlockPos bp = BlockPos.containing(pt);
			if (level.getBlockState(bp).canBeReplaced() && AbilityHelpers.canGrief()) {
				TempBlocks.place(level, bp, log.defaultBlockState(), 100);
				trail.add(bp);
			}
		}
		AbilityHelpers.hurt(p, target, 15.0f + natureBonus(p));
		AbilityHelpers.knockbackFrom(target, from, 1.2);
		AbilityHelpers.sound(p, SoundEvents.WOOD_BREAK, 1.0f, 0.7f);
		if (AbilityHelpers.canGrief()) {
			BlockPos plant = BlockPos.containing(from);
			PENDING_TREES.add(new PendingTree(level, plant, level.getGameTime() + 100));
		}
	}

	/**
	 * Best-effort tree grow: plant the matching sapling, then force it up with a few bonemeal ticks.
	 *
	 * <p>v0.10.20 crash fix: once a bonemeal application actually grows the sapling into a tree, the
	 * block at {@code at} is no longer a sapling (it's a log or an air pocket under the canopy) but the
	 * loop kept calling the ORIGINAL {@code SaplingBlock} instance's {@code performBonemeal} against
	 * that new, unrelated block state -- e.g. handing a {@code minecraft:oak_log} state to
	 * {@code SaplingBlock#performBonemeal}, which reads sapling-only block-state properties that the
	 * log state doesn't have and throws, crashing the server a few ticks after the tree finished
	 * growing. Re-resolve the block (not just the state) fresh every iteration and stop the moment it
	 * is no longer a bonemealable target of some kind.
	 */
	private static void growTreeAt(ServerLevel level, BlockPos pos) {
		BlockPos at = pos.above();
		if (!level.getBlockState(at).canBeReplaced()) {
			return;
		}
		Block sapling = biomeSapling(level, pos);
		level.setBlock(at, sapling.defaultBlockState(), 3);
		for (int i = 0; i < 8; i++) {
			BlockState cur = level.getBlockState(at);
			if (!(cur.getBlock() instanceof BonemealableBlock bm) || !bm.isValidBonemealTarget(level, at, cur)) {
				break;
			}
			bm.performBonemeal(level, level.random, at, cur);
		}
	}

	private static Block biomeLog(ServerLevel level, BlockPos pos) {
		var biome = level.getBiome(pos);
		if (biome.is(net.minecraft.world.level.biome.Biomes.BIRCH_FOREST) || biome.is(net.minecraft.world.level.biome.Biomes.OLD_GROWTH_BIRCH_FOREST)) {
			return Blocks.BIRCH_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.TAIGA) || biome.is(net.minecraft.world.level.biome.Biomes.OLD_GROWTH_SPRUCE_TAIGA)
				|| biome.is(net.minecraft.world.level.biome.Biomes.SNOWY_TAIGA)) {
			return Blocks.SPRUCE_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.JUNGLE) || biome.is(net.minecraft.world.level.biome.Biomes.SPARSE_JUNGLE)) {
			return Blocks.JUNGLE_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.SAVANNA) || biome.is(net.minecraft.world.level.biome.Biomes.SAVANNA_PLATEAU)) {
			return Blocks.ACACIA_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.DARK_FOREST)) {
			return Blocks.DARK_OAK_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE)) {
			return Blocks.CHERRY_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)) {
			return Blocks.MANGROVE_LOG;
		}
		return Blocks.OAK_LOG;
	}

	private static Block biomeSapling(ServerLevel level, BlockPos pos) {
		Block log = biomeLog(level, pos);
		if (log == Blocks.BIRCH_LOG) {
			return Blocks.BIRCH_SAPLING;
		}
		if (log == Blocks.SPRUCE_LOG) {
			return Blocks.SPRUCE_SAPLING;
		}
		if (log == Blocks.JUNGLE_LOG) {
			return Blocks.JUNGLE_SAPLING;
		}
		if (log == Blocks.ACACIA_LOG) {
			return Blocks.ACACIA_SAPLING;
		}
		if (log == Blocks.DARK_OAK_LOG) {
			return Blocks.DARK_OAK_SAPLING;
		}
		if (log == Blocks.CHERRY_LOG) {
			return Blocks.CHERRY_SAPLING;
		}
		if (log == Blocks.MANGROVE_LOG) {
			return Blocks.MANGROVE_PROPAGULE;
		}
		return Blocks.OAK_SAPLING;
	}

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

	private static final net.minecraft.resources.ResourceLocation BLESS_ATK =
			com.projecthero.mod.ProjectHeroMod.id("natures_blessing_atk");

	/** +10 to Plant abilities while Nature's Blessing is on and the player is on living ground. */
	static float natureBonus(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power,
						power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6))
				&& isBlessed((ServerLevel) p.level(), p.blockPosition())
				? 10.0f : 0.0f;
	}

	/** Standing on grass/moss, or within 5 blocks of any plant or leaf. */
	private static boolean isBlessed(ServerLevel level, BlockPos center) {
		var below = level.getBlockState(center.below());
		if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.PODZOL)
				|| below.is(Blocks.MYCELIUM)) {
			return true;
		}
		for (BlockPos bp : BlockPos.betweenClosed(center.offset(-5, -2, -5), center.offset(5, 2, 5))) {
			var st = level.getBlockState(bp);
			if (st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.FLOWERS)
					|| st.is(net.minecraft.tags.BlockTags.SAPLINGS) || st.is(net.minecraft.tags.BlockTags.CROPS)
					|| st.getBlock() instanceof net.minecraft.world.level.block.BushBlock
						|| st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.FERN)
					|| st.is(Blocks.LARGE_FERN) || st.is(Blocks.VINE) || st.is(Blocks.MOSS_CARPET)
					|| st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.BIG_DRIPLEAF) || st.is(Blocks.SMALL_DRIPLEAF)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when the player is standing on living / natural ground -- grass, moss, dirt family, leaves,
	 * farmland, or any bush/plant. Used by {@link com.projecthero.mod.hero.power.HeroDamageRules} to
	 * cancel fall damage: a chlorokinetic lands softly on anything that grows.
	 */
	public static boolean onNatureGround(ServerLevel level, BlockPos feet) {
		var below = level.getBlockState(feet.below());
		if (below.is(net.minecraft.tags.BlockTags.DIRT) || below.is(net.minecraft.tags.BlockTags.LEAVES)
				|| below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.MOSS_CARPET) || below.is(Blocks.FARMLAND)
				|| below.is(Blocks.HAY_BLOCK) || below.is(Blocks.PINK_PETALS)
				|| below.getBlock() instanceof net.minecraft.world.level.block.BushBlock) {
			return true;
		}
		var at = level.getBlockState(feet);
		return at.getBlock() instanceof net.minecraft.world.level.block.BushBlock
				|| at.is(Blocks.SHORT_GRASS) || at.is(Blocks.TALL_GRASS) || at.is(Blocks.FERN)
				|| at.is(Blocks.LARGE_FERN) || at.is(Blocks.VINE) || at.is(Blocks.MOSS_CARPET);
	}

	/** True when a living-ground block forms a wall on any horizontal side of the player. */
	private static boolean againstNatureWall(ServerLevel level, BlockPos feet) {
		for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			var st = level.getBlockState(feet.relative(dir));
			if (st.is(net.minecraft.tags.BlockTags.DIRT) || st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.GRASS_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearVegetation(ServerLevel level, BlockPos center) {
		int count = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
			var st = level.getBlockState(p);
			if (st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.FLOWERS)
					|| st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.MOSS_CARPET) || st.is(Blocks.SHORT_GRASS)
					|| st.is(Blocks.VINE) || st.is(Blocks.GRASS_BLOCK)) {
				if (++count >= 4) {
					return true;
				}
			}
		}
		return false;
	}
}
