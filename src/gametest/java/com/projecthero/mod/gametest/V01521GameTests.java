package com.projecthero.mod.gametest;

import java.util.List;
import java.util.Optional;

import com.projecthero.mod.armor.ArmorNightVision;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkDamage;
import com.projecthero.mod.hulk.HulkRiding;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.nova.Nova;
import com.projecthero.mod.nova.item.NovaItems;
import com.projecthero.mod.wolverine.item.WolverineArmorItem;
import com.projecthero.mod.wolverine.item.WolverineItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.21: Metal Plating no longer shadows vanilla's two-ingot recipes, automatic armour Night Vision (dark only, visor
 * down), Shift + hold N takes the Nova helmet off, the Wolverine Suit mends itself, and riding the Hulk (the seat turns
 * with his back, the rider faces his way, never takes fall damage, and his HUD can find the rider).
 */
public class V01521GameTests implements FabricGameTest {

	private static ServerPlayer mock(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = h.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static ItemStack craft(GameTestHelper h, Item... grid) {
		List<ItemStack> stacks = new java.util.ArrayList<>();
		for (Item i : grid) {
			stacks.add(i == null ? ItemStack.EMPTY : new ItemStack(i));
		}
		CraftingInput input = CraftingInput.of(3, 3, stacks);
		Optional<RecipeHolder<CraftingRecipe>> r = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel());
		return r.map(x -> x.value().assemble(input, h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------- Metal Plating

	@GameTest(template = EMPTY_STRUCTURE)
	public void metalPlatingLeavesShearsAndTrapdoorsAlone(GameTestHelper h) {
		Item i = Items.IRON_INGOT;
		h.assertTrue(craft(h, i, null, null, null, i, null, null, null, null).is(Items.SHEARS), "two iron diagonally: shears");
		h.assertTrue(craft(h, null, i, null, i, null, null, null, null, null).is(Items.SHEARS), "the other diagonal too");
		h.assertTrue(craft(h, i, i, null, null, null, null, null, null, null).is(Items.HEAVY_WEIGHTED_PRESSURE_PLATE),
				"two iron side by side: the heavy pressure plate");
		h.assertTrue(craft(h, i, i, null, i, i, null, null, null, null).is(Items.IRON_TRAPDOOR), "four iron in a square: the trapdoor");
		ItemStack plating = craft(h, null, null, null, i, i, i, null, null, null);
		h.assertTrue(plating.is(IronManItems.METAL_PLATING) && plating.getCount() == 6, "three iron in a row: 6 Metal Plating");
		h.succeed();
	}

	// ---------------------------------------------------------------- automatic night vision

	@GameTest(template = EMPTY_STRUCTURE)
	public void helmetNightVisionOnlyInTheDarkWithTheVisorDown(GameTestHelper h) {
		ServerPlayer p = mock(h);
		TonyStark.grant(p);
		for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
				ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS }) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor("mark_iii", t)));
		}
		TonyStark.setActiveSuit(p, "mark_iii");
		IronManEnergy.setEnergy(p, "mark_iii", IronManEnergy.capacity("mark_iii"));
		IronManEnergy.setIntegrity(p, "mark_iii", IronManEnergy.maxIntegrity("mark_iii"));
		try {
			ArmorNightVision.testOverride = true;
			IronManSuitTicker.tick(p);
			h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) != null, "dark, visor down: night vision");
			p.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
			IronManSuitTicker.tick(p);
			h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) == null, "lifting the faceplate drops it");
			p.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
			IronManSuitTicker.tick(p);
			h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) != null, "visor down again: back on");
			ArmorNightVision.testOverride = false;
			IronManSuitTicker.tick(p);
			h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) == null, "lit: it switches itself off");
		} finally {
			ArmorNightVision.testOverride = null;
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void nightVisionThresholdsHaveAGap(GameTestHelper h) {
		h.assertTrue(ArmorNightVision.ON_AT_OR_BELOW == 7, "on at light 7 or less");
		h.assertTrue(ArmorNightVision.OFF_AT_OR_ABOVE > ArmorNightVision.ON_AT_OR_BELOW + 1, "and a gap before it goes off (no flicker)");
		h.succeed();
	}

	// ---------------------------------------------------------------- Nova: Shift + hold N

	@GameTest(template = EMPTY_STRUCTURE)
	public void novaTakesTheHelmetOffAndGetsItBack(GameTestHelper h) {
		ServerPlayer p = mock(h);
		h.assertTrue(Nova.grant(p), "Nova");
		Nova.helmetRemoveStart(p);
		h.assertFalse(Nova.removingHelmet(p), "N without Sneak does not start it");
		p.setShiftKeyDown(true);
		Nova.helmetRemoveStart(p);
		h.assertTrue(Nova.removingHelmet(p), "Shift + N starts taking it off");
		Nova.helmetRemoveStop(p);
		h.assertFalse(Nova.removingHelmet(p), "letting go cancels");
		h.assertTrue(Nova.hasPower(p), "and the power stays");
		Nova.removeHelmet(p);
		h.assertFalse(Nova.hasPower(p), "after the 5 s hold the Nova Force leaves");
		h.assertTrue(p.getInventory().contains(new ItemStack(NovaItems.NOVA_CORPS_HELMET)), "and the helmet is back in the inventory");
		h.assertTrue(com.projecthero.mod.nova.NovaConfig.HELMET_REMOVE_HOLD_TICKS == 100, "the hold is 5 seconds");
		h.succeed();
	}

	// ---------------------------------------------------------------- Wolverine Suit

	@GameTest(template = EMPTY_STRUCTURE)
	public void wolverineSuitMendsItself(GameTestHelper h) {
		ItemStack suit = new ItemStack(WolverineItems.SUIT_CHESTPLATE);
		suit.setDamageValue(10);
		WolverineArmorItem.repairTick(suit);
		h.assertTrue(suit.getDamageValue() == 9, "one point per repair tick");
		suit.setDamageValue(0);
		WolverineArmorItem.repairTick(suit);
		h.assertTrue(suit.getDamageValue() == 0, "an undamaged suit stays as it is");
		h.assertTrue(WolverineArmorItem.REPAIR_INTERVAL_TICKS == 100, "every 5 seconds");
		h.succeed();
	}

	// ---------------------------------------------------------------- riding the Hulk

	@GameTest(template = EMPTY_STRUCTURE)
	public void theRiderSitsOnHisBackAndTurnsWithHim(GameTestHelper h) {
		ServerPlayer hulk = mock(h);
		BlockPos base = BlockPos.containing(hulk.position());
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 5, 2))) {
			h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		h.assertTrue(Hulk.grant(hulk), "Gamma");
		Hulk.setRage(hulk, 100.0f);
		Hulk.tryTransform(hulk);
		h.assertTrue(Hulk.isHulk(hulk), "precondition: Hulk");
		ServerPlayer mate = h.makeMockServerPlayerInLevel();
		mate.setGameMode(GameType.SURVIVAL);
		mate.moveTo(hulk.getX(), hulk.getY(), hulk.getZ() + 1.5, 180.0f, 0.0f);
		var squads = com.projecthero.mod.squad.SquadManager.get(h.getLevel().getServer());
		var squad = squads.create("RideTestSquad" + h.getLevel().getGameTime(), hulk.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			h.assertTrue(HulkRiding.tryMount(mate, hulk), "a squad-mate climbs on");
			h.assertTrue(HulkRiding.rider(hulk) == mate, "the Hulk's HUD can see who is on his back");
			h.assertTrue(HulkRiding.ridingHulk(mate), "and the rider knows (the piggyback pose)");

			hulk.yBodyRot = 0.0f; // facing +Z: his back is -Z
			Vec3 seat = hulk.getPassengerRidingPosition(mate).subtract(hulk.position());
			h.assertTrue(seat.z < -0.2 && Math.abs(seat.x) < 0.05, "facing south the seat is behind him (-Z), got " + seat);
			hulk.yBodyRot = 90.0f; // facing -X: his back is +X
			seat = hulk.getPassengerRidingPosition(mate).subtract(hulk.position());
			h.assertTrue(seat.x > 0.2 && Math.abs(seat.z) < 0.05, "turned west the seat turns with him (+X), got " + seat);

			hulk.positionRider(mate);
			h.assertTrue(Math.abs(mate.yBodyRot - hulk.yBodyRot) < 0.01f, "the rider faces the way he does");

			HulkRiding.tick(hulk);
			h.assertFalse(HulkDamage.allowDamage(mate, mate.damageSources().fall(), 8.0f), "the rider takes no fall damage");
			mate.stopRiding();
			h.assertFalse(HulkDamage.allowDamage(mate, mate.damageSources().fall(), 8.0f), "nor just after hopping off mid-leap");
		} finally {
			mate.stopRiding();
			squads.disband(squad);
		}
		h.succeed();
	}
}
