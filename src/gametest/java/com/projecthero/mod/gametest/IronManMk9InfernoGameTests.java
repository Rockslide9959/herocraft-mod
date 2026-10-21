package com.projecthero.mod.gametest;

import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlightLook;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.16.7 (user) Era II stage 1: the Mk IX Inferno -- the Nether suit exists, is wearable, buildable (blueprint after the
 * Mk VIII Space, Fabricator recipes) and flies, and the full suit shrugs off fire and lava (one piece short, it does not).
 */
public class IronManMk9InfernoGameTests implements FabricGameTest {
	private static final String MK9 = "mark_9";

	private static ServerPlayer suited(GameTestHelper h, String suitId) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		TonyStark.grant(p);
		for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
				ArmorItem.Type.BOOTS }) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(suitId, t)));
		}
		IronManEnergy.setIntegrity(p, suitId, IronManEnergy.maxIntegrity(suitId));
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theInfernoIsAWearableBuildableFlyingSuit(GameTestHelper h) {
		IronManSuit s = IronManSuits.byId(MK9);
		h.assertTrue(s != null && s == IronManSuits.MARK_9, "mark_9 is registered");
		h.assertTrue(s.markNumber() == 9 && s.techLevel() == IronManSuits.MARK_VII.techLevel() + 1, "Mark 9, one tech tier past the Mk VIII");
		h.assertTrue(s.maxIntegrity() > IronManSuits.MARK_VII.maxIntegrity() && s.maxIntegrity() < IronManSuits.MARK_8.maxIntegrity()
				&& s.absorbShare() > IronManSuits.MARK_VII.absorbShare() && s.absorbShare() < IronManSuits.MARK_8.absorbShare(),
				"integrity / absorb between the Mk VIII Space and the Hulkbuster");
		h.assertTrue(s.manualFlight() && s.flightNormalBps() == IronManSuits.MARK_VII.flightNormalBps()
				&& s.flightSprintBps() == IronManSuits.MARK_VII.flightSprintBps(), "flies like the Mk VIII Space");
		h.assertTrue(s.energyCapacity() == IronManEnergy.FULL, "keeps the no-energy shim");
		h.assertTrue(s.fireImmune() && !s.hazardImmune(), "fire / lava proof (full suit), not the Space suit's freeze immunity");
		// progression: the blueprint comes after the Mk VIII, the Hulkbuster after it
		h.assertTrue("mark_vii".equals(IronManItems.prerequisiteSuit(MK9)) && MK9.equals(IronManItems.prerequisiteSuit("mark_8")),
				"Mk VIII -> Mk IX -> Hulkbuster");
		h.assertTrue(IronManItems.blueprintFor(MK9) == IronManItems.MARK_9_BLUEPRINT && s.requiredBlueprint() == IronManItems.MARK_9_BLUEPRINT,
				"its own blueprint");
		for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
				ArmorItem.Type.BOOTS }) {
			var piece = IronManItems.armor(MK9, t);
			h.assertTrue(piece != null && piece.armorSetId().equals(MK9), "the " + t.getName() + " exists");
			h.assertTrue(new ItemStack(piece).has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT), t.getName() + " floats in lava");
			h.assertTrue(FabricatorRecipes.byId("iron_man_" + MK9 + "_" + t.getName()) != null, "a Fabricator recipe for the " + t.getName());
		}
		h.assertTrue(FabricatorRecipes.ARMOR_BUILD_ORDER.indexOf(MK9) == FabricatorRecipes.ARMOR_BUILD_ORDER.indexOf("mark_vii") + 1,
				"built after the Mk VIII Space");
		h.assertTrue(SuperheroArmorVisuals.has(MK9), "it has its own visuals (model / skin)");
		h.assertTrue(IronManFlightLook.thrustColour(MK9) != IronManFlightLook.thrustColour("mark_vii"), "its own (orange) thrust");

		ServerPlayer p = suited(h, MK9);
		h.assertTrue(IronManArmor.wearingFullSuit(p, MK9) && MK9.equals(IronManArmor.wornSuitId(p)), "the full suit is worn");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFullInfernoIgnoresFireAndLava(GameTestHelper h) {
		ServerPlayer p = suited(h, MK9);
		float integ = IronManEnergy.integrity(p, MK9);
		p.igniteForSeconds(5f);
		h.assertFalse(IronManDamage.onAllowDamage(p, p.damageSources().lava(), 4f), "lava is cancelled on the full Inferno");
		h.assertFalse(IronManDamage.onAllowDamage(p, p.damageSources().inFire(), 4f), "fire is cancelled");
		h.assertFalse(IronManDamage.onAllowDamage(p, p.damageSources().onFire(), 1f), "burning is cancelled");
		h.assertFalse(IronManDamage.onAllowDamage(p, p.damageSources().hotFloor(), 1f), "magma blocks are cancelled");
		h.assertTrue(IronManEnergy.integrity(p, MK9) == integ, "and the suit loses nothing to them");
		h.assertFalse(p.isOnFire(), "the flames go out");
		h.assertTrue(IronManDamage.fireProof(p, IronManSuits.MARK_9, p.damageSources().lava()), "fire-proof check");
		h.assertFalse(IronManDamage.fireProof(p, IronManSuits.MARK_9, p.damageSources().generic()), "an ordinary hit is not stopped");

		// one piece short: the immunity is gone
		p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		h.assertFalse(IronManDamage.fireProof(p, IronManSuits.MARK_9, p.damageSources().lava()), "no boots, no lava immunity");
		// and no other suit has it
		ServerPlayer space = suited(h, "mark_6");
		h.assertFalse(IronManDamage.fireProof(space, IronManSuits.MARK_6, space.damageSources().lava()), "the Centurion is not fire-proof");
		h.succeed();
	}
}
