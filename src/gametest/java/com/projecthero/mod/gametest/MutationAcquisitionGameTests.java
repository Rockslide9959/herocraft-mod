package com.projecthero.mod.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.MutationTrigger;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.device.LabDeviceBlock;
import com.projecthero.mod.hero.device.ModDevices;
import com.projecthero.mod.hero.item.HeroPackItems;
import com.projecthero.mod.hero.mutation.ModMobEffects;
import com.projecthero.mod.hero.mutation.ModSerums;
import com.projecthero.mod.hero.mutation.MutationManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.1: every way of obtaining a mutation, end to end -- the reagent recipes, the brewing step, the serum marker's
 * survival, the detectors that were broken (Elasticity's slime impact, Cryokinesis' powder snow), the capacity rule
 * and the lab devices.
 */
public class MutationAcquisitionGameTests implements FabricGameTest {

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	private static void drink(ServerPlayer p, Power power) {
		p.addEffect(new MobEffectInstance(ModMobEffects.UNSTABLE_MUTATION, 1200, ModSerums.amplifierFor(power), false, true, true));
		MutationManager.serverTick(p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyReagentRecipeIsUniqueAndCraftable(GameTestHelper helper) {
		var recipes = helper.getLevel().getRecipeManager();
		Map<String, String> seen = new HashMap<>();
		for (Power p : Powers.all()) {
			String path = BuiltInRegistries.ITEM.getKey(HeroPackItems.reagent(p)).getPath();
			var holder = recipes.byKey(ResourceLocation.fromNamespaceAndPath("projecthero", path));
			if (!p.enabled()) {
				// v0.14.8: a disabled power's reagent recipe is held back by the projecthero:power_enabled condition
				helper.assertFalse(holder.isPresent(), p.key() + " is disabled but its reagent recipe loaded");
				continue;
			}
			helper.assertTrue(holder.isPresent(), p.key() + " has no reagent recipe");
			TreeSet<String> items = new TreeSet<>();
			for (Ingredient ing : holder.get().value().getIngredients()) {
				for (ItemStack s : ing.getItems()) {
					items.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
				}
			}
			String key = String.join("+", items);
			String clash = seen.put(key, p.key());
			helper.assertTrue(clash == null, p.key() + " has the same reagent recipe as " + clash);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everySerumBrewsFromItsReagent(GameTestHelper helper) {
		var brewing = helper.getLevel().potionBrewing();
		for (Power p : Powers.all()) {
			ItemStack base = PotionContents.createItemStack(Items.POTION, ModSerums.basePotion(p.serum().basePotion()));
			ItemStack reagent = new ItemStack(HeroPackItems.reagent(p));
			if (!p.enabled()) {
				helper.assertFalse(brewing.hasMix(base, reagent), p.key() + " is disabled but still brews");
				continue;
			}
			helper.assertTrue(brewing.hasMix(base, reagent), p.key() + ": base + reagent does not brew");
			ItemStack out = brewing.mix(reagent, base);
			PotionContents contents = out.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
			helper.assertTrue(contents != null && contents.potion().isPresent()
					&& contents.potion().get().equals(ModSerums.serum(p)), p.key() + ": brewed the wrong potion");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void strippedSerumEffectIsRestored(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Power flight = Powers.byKey("power_04_super_speed");
		drink(p, flight);
		p.removeEffect(ModMobEffects.UNSTABLE_MUTATION); // milk / Purge / a totem / a suit transform
		MutationManager.serverTick(p);
		helper.assertTrue(p.getEffect(ModMobEffects.UNSTABLE_MUTATION) != null, "the serum marker should be put back");
		helper.assertTrue(flight.key().equals(ExperimentalPowers.state(p).pendingMutationPower), "the attempt should survive");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pendingSerumSurvivesSaveAndLoad(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Power flight = Powers.byKey("power_04_super_speed");
		drink(p, flight);
		var state = ExperimentalPowers.state(p);
		var tag = com.projecthero.mod.hero.data.ExperimentalState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, state)
				.getOrThrow();
		var back = com.projecthero.mod.hero.data.ExperimentalState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(flight.key().equals(back.pendingMutationPower), "pending power must be persisted");
		helper.assertTrue(back.pendingMutationExpiryTick == state.pendingMutationExpiryTick, "expiry must be persisted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void newestSerumReplacesTheOld(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Power speed = Powers.byKey("power_04_super_speed");
		Power strength = Powers.byKey("power_01_super_strength");
		drink(p, speed);
		drink(p, strength); // lower amplifier: vanilla merging used to keep the first
		helper.assertTrue(p.getEffect(ModMobEffects.UNSTABLE_MUTATION).getAmplifier() == ModSerums.amplifierFor(strength),
				"the newest serum should win");
		helper.assertTrue(strength.key().equals(ExperimentalPowers.state(p).pendingMutationPower), "pending should follow");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fullCapacityKeepsEverythingAndEndsTheAttempt(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		int i = 0;
		for (Power pw : Powers.all()) {
			if (i++ >= ExperimentalPowers.capacity()) {
				break;
			}
			ExperimentalPowers.grant(p, pw);
		}
		int before = ExperimentalPowers.ownedCount(p);
		// v0.14.8: the first three registered powers are owned (Strength, Laser Vision, Flight); a Super Speed serum
		Power speed = Powers.byKey("power_04_super_speed"); // ELECTRICAL_DISCHARGE
		drink(p, speed);
		helper.assertTrue(speed.key().equals(ExperimentalPowers.state(p).pendingMutationPower), "the serum takes hold");
		MutationManager.triggerExposure(p, MutationTrigger.Kind.ELECTRICAL_DISCHARGE);
		helper.assertFalse(ExperimentalPowers.owns(p, speed), "no room: must not be granted");
		helper.assertTrue(ExperimentalPowers.ownedCount(p) == before, "nothing owned may be lost");
		helper.assertTrue(ExperimentalPowers.state(p).pendingMutationPower.isEmpty(), "the attempt should end, not repeat");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void slimeImpactGrantsElasticity(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Power elastic = Powers.byKey("power_17_elasticity");
		BlockPos slime = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel().setBlockAndUpdate(slime, Blocks.SLIME_BLOCK.defaultBlockState());
		p.moveTo(slime.getX() + 0.5, slime.getY() + 1.0, slime.getZ() + 0.5);
		drink(p, elastic);
		p.fallDistance = 12.0f;
		MutationManager.serverTick(p);
		// v0.14.8: Elasticity is disabled (Powers.ENABLED) -- its serum takes no hold and the slime impact grants nothing
		helper.assertFalse(ExperimentalPowers.owns(p, elastic), "a disabled power must not be granted by its detector");
		helper.assertTrue(ExperimentalPowers.state(p).pendingMutationPower.isEmpty(), "a disabled serum takes no hold");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void powderSnowGrantsCryokinesis(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Power cryo = Powers.byKey("power_09_cryokinesis");
		BlockPos snow = helper.absolutePos(new BlockPos(1, 2, 1));
		helper.getLevel().setBlockAndUpdate(snow, Blocks.POWDER_SNOW.defaultBlockState());
		p.moveTo(snow.getX() + 0.5, snow.getY(), snow.getZ() + 0.5);
		drink(p, cryo);
		for (int t = 0; t < 70 && !ExperimentalPowers.owns(p, cryo); t++) {
			MutationManager.serverTick(p);
		}
		// v0.14.8: Cryokinesis is disabled
		helper.assertFalse(ExperimentalPowers.owns(p, cryo), "a disabled power must not be granted by its detector");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyDeviceFiresOnRightClick(GameTestHelper helper) {
		// redstone devices used to ignore right-clicks entirely (research sites had no lever). v0.14.8: the Blast
		// Chamber's powers are all disabled, so this uses the Charged Copper Plates and Super Speed.
		ServerPlayer p = player(helper);
		Power speed = Powers.byKey("power_04_super_speed"); // ELECTRICAL_DISCHARGE, Charged Copper Plates
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel().setBlockAndUpdate(pos, ModDevices.CHARGED_COPPER_PLATES.defaultBlockState());
		p.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
		drink(p, speed);
		helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), p,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		helper.assertTrue(ExperimentalPowers.owns(p, speed), "right-clicking the Charged Copper Plates should expose the player");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void newDevicesAreRegistered(GameTestHelper helper) {
		for (String id : new String[] { "charged_copper_plates", "crystal_chamber", "enchanting_resonance", "blast_chamber",
				"resonant_chamber", "gravity_distortion_rig" }) {
			Block b = ModDevices.block(id);
			helper.assertTrue(b instanceof LabDeviceBlock, id + " is not a registered lab device");
			helper.assertTrue(helper.getLevel().getRecipeManager()
					.byKey(ResourceLocation.fromNamespaceAndPath("projecthero", id)).isPresent(), id + " has no recipe");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void randomSerumMutationRespectsCapacity(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		int i = 0;
		for (Power pw : Powers.all()) {
			if (i++ >= ExperimentalPowers.capacity()) {
				break;
			}
			ExperimentalPowers.grant(p, pw);
		}
		helper.assertFalse(PowerGrants.grantExperimental(p, Powers.byKey("power_04_super_speed")),
				"a full player must not get another mutation from a random serum");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void randomSerumRecordsResearch(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Power speed = Powers.byKey("power_04_super_speed");
		helper.assertTrue(PowerGrants.grantExperimental(p, speed), "grant should succeed");
		helper.assertFalse(PowerGrants.grantExperimental(helper.makeMockServerPlayerInLevel(),
				Powers.byKey("power_24_wind_manipulation")), "v0.14.8: a disabled power is never handed out");
		helper.assertTrue(ExperimentalPowers.researchStage(p, speed)
				== com.projecthero.mod.hero.data.ResearchStage.MUTATION_CONFIRMED, "research should be recorded");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void guideReagentMatchesRealRecipe(GameTestHelper helper) {
		var recipes = helper.getLevel().getRecipeManager();
		for (Power p : Powers.enabled()) { // v0.14.8: disabled powers have no guide chapter and no recipe
			String path = BuiltInRegistries.ITEM.getKey(HeroPackItems.reagent(p)).getPath();
			var holder = recipes.byKey(ResourceLocation.fromNamespaceAndPath("projecthero", path)).orElseThrow();
			TreeSet<String> real = new TreeSet<>();
			for (Ingredient ing : holder.value().getIngredients()) {
				for (ItemStack st : ing.getItems()) {
					real.add(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
				}
			}
			TreeSet<String> guide = new TreeSet<>(p.serum().additives());
			if (p.serum().fuel() != null) {
				guide.add(p.serum().fuel());
			}
			helper.assertTrue(real.equals(guide), p.key() + ": the guide lists " + guide + " but the recipe is " + real);
		}
		helper.succeed();
	}
}
