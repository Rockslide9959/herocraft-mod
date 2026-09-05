package com.herocraft.mod.gametest;

import com.herocraft.mod.maxsteel.MaxSteel;
import com.herocraft.mod.maxsteel.MaxSteelBlast;
import com.herocraft.mod.maxsteel.MaxSteelBonding;
import com.herocraft.mod.maxsteel.MaxSteelConfig;
import com.herocraft.mod.maxsteel.MaxSteelEnergy;
import com.herocraft.mod.maxsteel.MaxSteelMode;
import com.herocraft.mod.maxsteel.MaxSteelModes;
import com.herocraft.mod.maxsteel.MaxSteelSuitArmor;
import com.herocraft.mod.maxsteel.MaxSteelTransform;
import com.herocraft.mod.maxsteel.data.MaxSteelState;
import com.herocraft.mod.maxsteel.entity.MaxSteelEntityTypes;
import com.herocraft.mod.maxsteel.entity.SteelEntity;
import com.herocraft.mod.maxsteel.item.MaxSteelArmorItem;
import com.herocraft.mod.maxsteel.item.MaxSteelItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Server-side coverage for Max Steel: bonding rules, the T.U.R.B.O. Energy pool, the transform state
 * machine, the specialised-mode framework, ability gates, and lifecycle cleanup. Movement itself
 * (flight, the Cannon flight arc) is client/physics and belongs to the manual test plan; what is
 * checked here is every rule the server enforces around it.
 */
public class MaxSteelGameTests implements FabricGameTest {

	private static ServerPlayer bonded(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		MaxSteel.bond(player);
		return player;
	}

	private static ServerPlayer suited(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		MaxSteelTransform.beginSuitUp(player, false);
		settleSuit(player);
		setEnergy(player, 100f);
		return player;
	}

	private static ServerPlayer plain(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static SteelEntity spawnSteelNear(ServerPlayer player) {
		SteelEntity steel = MaxSteelEntityTypes.STEEL.create(player.serverLevel());
		steel.moveTo(player.getX(), player.getY(), player.getZ(), 0f, 0f);
		player.serverLevel().addFreshEntity(steel);
		return steel;
	}

	// ---------------- bonding ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void bondingRefusesBelowLevel30WithNoStabilizer(GameTestHelper helper) {
		ServerPlayer player = plain(helper);
		player.experienceLevel = 12;
		MaxSteelBonding.attempt(player, spawnSteelNear(player));
		helper.assertFalse(MaxSteel.hasPower(player), "a level-12 player with no Stabilizer must not bond");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void level30BondingConsumesFiveLevels(GameTestHelper helper) {
		ServerPlayer player = plain(helper);
		player.experienceLevel = 34;
		MaxSteelBonding.attempt(player, spawnSteelNear(player));
		helper.assertTrue(MaxSteel.hasPower(player), "a level-34 player should bond");
		helper.assertTrue(player.experienceLevel == 29,
				"bonding should consume exactly 5 levels, left at " + player.experienceLevel);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stabilizerBondsBelowLevel30AndIsConsumed(GameTestHelper helper) {
		ServerPlayer player = plain(helper);
		player.experienceLevel = 3;
		player.getInventory().add(new ItemStack(MaxSteelItems.TURBO_STABILIZER, 2));
		SteelEntity steel = spawnSteelNear(player);
		MaxSteelBonding.attempt(player, steel);

		helper.assertTrue(MaxSteel.hasPower(player), "the Stabilizer route should bond below level 30");
		helper.assertTrue(player.experienceLevel == 3, "the Stabilizer route must not touch XP");
		helper.assertTrue(player.getInventory().countItem(MaxSteelItems.TURBO_STABILIZER) == 1,
				"exactly one Stabilizer should be consumed");
		helper.assertTrue(steel.isRemoved(), "Steel should be gone after a successful bond");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void twoPlayersCannotClaimTheSameSteel(GameTestHelper helper) {
		ServerPlayer a = plain(helper);
		ServerPlayer b = plain(helper);
		a.experienceLevel = 40;
		b.experienceLevel = 40;
		SteelEntity steel = spawnSteelNear(a);
		helper.assertTrue(steel.claimBond(a, 100), "player A claims the bond lock");
		helper.assertFalse(steel.claimBond(b, 100), "player B is refused while A holds it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void clearTransientNeverRemovesThePower(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		MaxSteel.clearTransient(player);
		helper.assertTrue(MaxSteel.hasPower(player), "clearTransient must never remove the power");
		MaxSteel.revoke(player);
		helper.assertFalse(MaxSteel.hasPower(player), "revoke (command only) removes it");
		helper.succeed();
	}

	// ---------------- energy ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void energyNeverGoesNegative(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		setEnergy(player, 3f);
		helper.assertFalse(MaxSteelEnergy.spend(player, 10f), "cannot spend more than you have");
		helper.assertTrue(MaxSteel.state(player).turboEnergy == 3f, "a failed spend takes nothing");
		helper.assertTrue(MaxSteelEnergy.spend(player, 3f), "an exact spend works");
		helper.assertTrue(MaxSteel.state(player).turboEnergy == 0f, "energy floors at 0");
		MaxSteelEnergy.drain(player, 5f);
		helper.assertTrue(MaxSteel.state(player).turboEnergy == 0f, "drain never goes negative");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void energyRegeneratesWhileSuitedAndIdle(GameTestHelper helper) {
		ServerPlayer player = suited(helper);
		MaxSteelState s = MaxSteel.state(player).copy();
		s.turboEnergy = 20f;
		s.lastHighCostTick = -1000L;
		s.combatUntil = 0L;
		MaxSteel.save(player, s);

		for (int i = 0; i < 20; i++) {
			MaxSteelEnergy.tickRegen(player, false, false);
		}
		helper.assertTrue(MaxSteel.state(player).turboEnergy > 20f,
				"energy should regenerate, got " + MaxSteel.state(player).turboEnergy);
		helper.succeed();
	}

	// ---------------- transform ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void transformEquipsAndStripsTheSyntheticSuit(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		helper.assertFalse(MaxSteelSuitArmor.wearing(player), "not suited yet");

		MaxSteelTransform.beginSuitUp(player, false);
		helper.assertTrue(MaxSteel.isTransformed(player), "transformed flag set immediately");
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof MaxSteelArmorItem,
				"the chest piece should be a synthetic Max Steel piece");

		settleSuit(player);
		helper.assertTrue(MaxSteel.state(player).transformDir == MaxSteelState.DIR_IDLE, "suit-up settled");

		MaxSteelTransform.beginSuitDown(player);
		settleSuit(player);
		helper.assertFalse(MaxSteel.isTransformed(player), "suit down");
		helper.assertFalse(MaxSteelSuitArmor.wearing(player), "synthetic pieces removed on suit-down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitUpPushesRealArmourToInventory(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		MaxSteelTransform.beginSuitUp(player, false);
		helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_HELMET) == 1,
				"the displaced diamond helmet should be in the inventory");
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof MaxSteelArmorItem,
				"the head slot now holds a Max Steel piece");
		helper.succeed();
	}

	// ---------------- modes ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void modesAreMutuallyExclusiveAndCostEnergy(GameTestHelper helper) {
		ServerPlayer player = suited(helper);

		helper.assertTrue(MaxSteelModes.toggle(player, MaxSteelMode.STRENGTH, MaxSteelConfig.STRENGTH_ACTIVATION_COST),
				"can enter Strength with full energy");
		helper.assertTrue(MaxSteel.mode(player) == MaxSteelMode.STRENGTH, "mode is Strength");
		helper.assertTrue(MaxSteelEnergy.get(player) <= 100f - MaxSteelConfig.STRENGTH_ACTIVATION_COST + 0.01f,
				"entering Strength should cost energy");

		MaxSteelModes.toggle(player, MaxSteelMode.SPEED, MaxSteelConfig.SPEED_ACTIVATION_COST);
		helper.assertTrue(MaxSteel.mode(player) == MaxSteelMode.SPEED, "switched to Speed, not stacked");

		MaxSteelModes.toggle(player, MaxSteelMode.SPEED, MaxSteelConfig.SPEED_ACTIVATION_COST);
		helper.assertTrue(MaxSteel.mode(player) == MaxSteelMode.BASE, "re-pressing the active mode drops to Base");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroEnergyForcesBaseModeAndLocksOut(GameTestHelper helper) {
		ServerPlayer player = suited(helper);
		MaxSteelModes.toggle(player, MaxSteelMode.STRENGTH, MaxSteelConfig.STRENGTH_ACTIVATION_COST);
		MaxSteelModes.overload(player);

		helper.assertTrue(MaxSteel.mode(player) == MaxSteelMode.BASE, "overload drops to Base");
		helper.assertTrue(MaxSteelEnergy.isLockedOut(player), "overload starts the lock-out");
		helper.assertFalse(MaxSteelModes.toggle(player, MaxSteelMode.SPEED, MaxSteelConfig.SPEED_ACTIVATION_COST),
				"cannot enter a mode during the lock-out");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void modeSwitchDoesNotStackAttributeModifiers(GameTestHelper helper) {
		ServerPlayer player = suited(helper);
		double base = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);

		for (int i = 0; i < 5; i++) {
			MaxSteelModes.toggle(player, MaxSteelMode.STRENGTH, 0f);
			MaxSteelModes.toggle(player, MaxSteelMode.STRENGTH, 0f);
			setEnergy(player, 100f);
		}
		MaxSteelModes.toggle(player, MaxSteelMode.STRENGTH, 0f);
		double withStrength = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(
				withStrength <= base + MaxSteelConfig.BASE_MELEE_BONUS + MaxSteelConfig.STRENGTH_MELEE_BONUS + 0.01,
				"attack damage must not accumulate across toggles, got " + withStrength);
		helper.succeed();
	}

	// ---------------- abilities ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void turboBlastNeedsTheSuitAndSpendsEnergy(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		setEnergy(player, 100f);

		MaxSteelBlast.tap(player); // unsuited -> nothing
		helper.assertTrue(MaxSteelEnergy.get(player) == 100f, "Turbo Blast does nothing while unsuited");

		MaxSteelTransform.beginSuitUp(player, false);
		settleSuit(player);
		setEnergy(player, 100f);
		MaxSteelBlast.tap(player);
		helper.assertTrue(MaxSteelEnergy.get(player) <= 100f - MaxSteelConfig.BLAST_COST + 0.01f,
				"a suited Turbo Blast spends energy");
		helper.assertFalse(MaxSteel.abilityReady(player, MaxSteelBlast.ABILITY), "and starts a cooldown");
		helper.succeed();
	}

	// ---------------- lifecycle ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void deathClearsTheSuitButKeepsThePower(GameTestHelper helper) {
		ServerPlayer player = suited(helper);
		MaxSteelModes.toggle(player, MaxSteelMode.STRENGTH, MaxSteelConfig.STRENGTH_ACTIVATION_COST);

		MaxSteel.onPlayerRespawn(player);
		helper.assertTrue(MaxSteel.hasPower(player), "the power survives death");
		helper.assertFalse(MaxSteel.isTransformed(player), "the suit does not");
		helper.assertTrue(MaxSteel.mode(player) == MaxSteelMode.BASE, "mode reset to Base");
		helper.assertFalse(MaxSteelSuitArmor.wearing(player), "no synthetic pieces left on respawn");
		helper.assertFalse(player.getAbilities().mayfly && !player.isCreative(), "no leftover mayfly grant");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerSuppressorStripsMaxSteel(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		helper.assertTrue(MaxSteel.hasPower(player), "the player starts bonded");
		player.setShiftKeyDown(true);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				new ItemStack(com.herocraft.mod.item.ModItems.POWER_SUPPRESSOR));

		com.herocraft.mod.item.ModItems.POWER_SUPPRESSOR.use(player.level(), player,
				net.minecraft.world.InteractionHand.MAIN_HAND);

		helper.assertFalse(MaxSteel.hasPower(player),
				"the Power Suppressor gives up every power, Max Steel included (v0.6.18)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitPiecesAreCurseOfBindingLocked(GameTestHelper helper) {
		ServerPlayer player = suited(helper);
		ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
		helper.assertTrue(worn.getItem() instanceof MaxSteelArmorItem, "the chestplate slot holds the suit piece");
		helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.has(worn,
				net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE),
				"the piece must carry Curse of Binding's effect -- this is what stops a survival player "
						+ "pulling it out of the slot and duplicating it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void strayMaxSteelPieceIsDeletedFromANonSuitedPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MaxSteelItems.SUIT_CHESTPLATE));
		player.getInventory().add(new ItemStack(MaxSteelItems.SUIT_HELMET));

		MaxSteel.enforce(player);

		helper.assertFalse(MaxSteelSuitArmor.wearing(player), "a non-suited player cannot wear a Max Steel piece");
		boolean anyLoose = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).getItem() instanceof MaxSteelArmorItem) {
				anyLoose = true;
			}
		}
		helper.assertFalse(anyLoose, "and cannot keep a copy in their inventory");
		helper.succeed();
	}

	// ---------------- helpers ----------------

	private static void settleSuit(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player).copy();
		s.transformStartTick = player.level().getGameTime() - s.transformDurationTicks - 5;
		MaxSteel.save(player, s);
		MaxSteelTransform.tick(player);
	}

	private static void setEnergy(ServerPlayer player, float amount) {
		MaxSteelState s = MaxSteel.state(player).copy();
		s.turboEnergy = amount;
		s.lockoutUntil = 0L;
		s.lastHighCostTick = -1000L;
		MaxSteel.save(player, s);
	}
}
