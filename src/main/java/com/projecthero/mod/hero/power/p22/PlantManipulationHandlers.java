package com.projecthero.mod.hero.power.p22;

import com.projecthero.mod.hero.AbilityHandlers;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Power 22 — Plant Manipulation / Chlorokinesis. */
public final class PlantManipulationHandlers {
	private static final String KEY = "power_22_plant_manipulation_chlorokinesis";

	private PlantManipulationHandlers() {
	}

	public static void register() {
		AbilityHandlers.register(KEY, "thorn_shot", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 22.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 22.0), ParticleTypes.COMPOSTER, 3.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 8.0f + natureBonus(p));
				AbilityHelpers.applyControl(t, MobEffects.POISON, 160, 1); // Poison II, 8 s
			}
			AbilityHelpers.sound(p, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 0.8f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "vine_grab", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
			if (t != null) {
				// vines root the target: near-total immobilisation for 8 s
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 160, 7);
				AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, 160, 1);
				t.setDeltaMovement(Vec3.ZERO);
				t.hurtMarked = true;
				AbilityHelpers.hurt(p, t, 12.0f + natureBonus(p));
				ctx.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + 1, t.getZ(), 15, 0.4, 0.6, 0.4, 0.0);
			}
			AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_BREAK, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "vine_swing", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			var hit = AbilityHelpers.raycastBlock(p, 100.0);
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
				return;
			}
			Vec3 anchor = Vec3.atCenterOf(hit.getBlockPos());
			Vec3 pull = anchor.subtract(p.position());
			AbilityHelpers.launchSelf(p, pull.normalize().scale(1.8).add(0, 0.4, 0));
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), anchor, ParticleTypes.COMPOSTER, 2.0);
			AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_HIT, 1.0f, 1.2f);
			// swinging on a vine and letting go: you land on your feet, no fall damage for the next 10 s
			ctx.setResource("no_fall_until", p.level().getGameTime() + 200, 1.0e12f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "overgrowth", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			double r = 10.0;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				AbilityHelpers.hurt(p, e, 27.0f + natureBonus(p));
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 120, 4);
				AbilityHelpers.applyControl(e, MobEffects.POISON, 200, 4); // Poison V, 10 s
				e.setDeltaMovement(e.getDeltaMovement().multiply(0.1, 1, 0.1));
			}
			if (AbilityHelpers.canGrief()) {
				// a dense burst of short-lived growth that recedes after ~6 s
				for (int i = 0; i < 44; i++) {
					BlockPos bp = BlockPos.containing(p.getX() + level.random.nextGaussian() * 4, p.getY(), p.getZ() + level.random.nextGaussian() * 4);
					if (level.getBlockState(bp).canBeReplaced() && level.getBlockState(bp.below()).isSolidRender(level, bp.below())) {
						TempBlocks.place(level, bp, level.random.nextBoolean() ? Blocks.OAK_LEAVES.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState(), 120);
						if (level.random.nextInt(3) == 0 && level.getBlockState(bp.above()).canBeReplaced()) {
							TempBlocks.place(level, bp.above(), Blocks.OAK_LEAVES.defaultBlockState(), 120);
						}
					}
				}
			}
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 120, r / 2, 1, r / 2, 0.1);
			AbilityHelpers.sound(p, SoundEvents.GRASS_BREAK, 1.4f, 0.5f);
			ctx.triggerCooldown();
		}));

		// Living Wall: conjure a wall / dome / bridge of leaves like the other wall powers (look up for a
		// dome, look down for a bridge, else a wall). Sneak instead to charge your touch with bone meal
		// for 30 s -- right-click any plant to instantly grow it.
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
			net.minecraft.world.level.block.state.BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
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

		// Bone-meal touch: while the Living Wall sneak-charge is active, right-clicking a block bone-meals it.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer sp)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			var power = com.projecthero.mod.hero.Powers.byKey(KEY);
			if (power == null || !com.projecthero.mod.hero.ExperimentalPowers.owns(sp, power)
					|| com.projecthero.mod.hero.ExperimentalPowers.getResource(sp, power, "bonemeal_until") <= sp.level().getGameTime()) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			net.minecraft.world.item.ItemStack meal = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL);
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

		// Nature's Blessing: while standing on grass/moss or within 5 blocks of any plant or leaf, gain
		// Regeneration II and +8 to every ability's and melee hit's damage. The buff drops the moment
		// you leave living ground.
		AbilityHandlers.register(KEY, "natures_blessing", Handlers.toggle(
				Handlers.noop(),
				ctx -> com.projecthero.mod.hero.power.PowerToggles.clearModifier(ctx.player(),
						net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK),
				ctx -> {
					ServerPlayer p = ctx.player();
					AbilityHelpers.modeAura(p, ParticleTypes.HAPPY_VILLAGER, 3);
					if (p.tickCount % 20 != 0) {
						return;
					}
					if (isBlessed((ServerLevel) p.level(), p.blockPosition())) {
						p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false, true));
						com.projecthero.mod.hero.power.PowerToggles.modifier(p,
								net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK, 8.0,
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
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			if (player.tickCount % 100 == 0 && player.level() instanceof ServerLevel sl
					&& player.getHealth() < player.getMaxHealth() && nearVegetation(sl, player.blockPosition())) {
				player.heal(1.0f);
			}
		});
	}

	private static final net.minecraft.resources.ResourceLocation BLESS_ATK =
			com.projecthero.mod.ProjectHeroMod.id("natures_blessing_atk");

	/** +8 to Plant abilities while Nature's Blessing is on and the player is on living ground. */
	static float natureBonus(ServerPlayer p) {
		var power = com.projecthero.mod.hero.Powers.byKey(KEY);
		return power != null && com.projecthero.mod.hero.ExperimentalPowers.owns(p, power)
				&& com.projecthero.mod.hero.ExperimentalPowers.isToggled(p, power,
						power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6))
				&& isBlessed((ServerLevel) p.level(), p.blockPosition())
				? 8.0f : 0.0f;
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
		// or the block AT the feet is a plant you can sink into
		var at = level.getBlockState(feet);
		return at.getBlock() instanceof net.minecraft.world.level.block.BushBlock
				|| at.is(Blocks.SHORT_GRASS) || at.is(Blocks.TALL_GRASS) || at.is(Blocks.FERN)
				|| at.is(Blocks.LARGE_FERN) || at.is(Blocks.VINE) || at.is(Blocks.MOSS_CARPET);
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
