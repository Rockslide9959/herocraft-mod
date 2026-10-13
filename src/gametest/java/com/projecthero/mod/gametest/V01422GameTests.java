package com.projecthero.mod.gametest;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.14.22: Mark 1 retune, durability-free Iron Man armour, C auto-equipping a partial set, and the Rescue Tether's
 * bubble / unlimited carry. (The client/server version handshake is covered implicitly: it must leave every mock
 * player in this suite connected.)
 */
public class V01422GameTests implements FabricGameTest {
	private static final String[] MARKS = { "mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii" };

	private static ServerPlayer at(GameTestHelper h, ServerPlayer p) {
		p.setGameMode(GameType.SURVIVAL);
		BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markOneRetune(GameTestHelper helper) {
		// v0.14.27 retune: 500 energy, a 10-energy punch, a 50-energy flight burst with no per-second drain
		helper.assertTrue(IronManSuits.MARK_1.energyCapacity() == 500f, "Mark 1 max energy is 500");
		helper.assertTrue(IronManAbilities.PUNCH_ENERGY_COST == 10f, "Strong Punch costs 10");
		helper.assertTrue(IronManAbilities.MARK_1_FLAMETHROWER_DAMAGE == 5f, "Mark 1 flamethrower does 5 a tick");
		helper.assertTrue(IronManAbilities.MARK_1_ROCKET_DAMAGE == 30f, "Mark 1 rocket does 30");
		helper.assertTrue(IronManAbilities.MARK_1_ROCKET_COOLDOWN_TICKS == 200, "Mark 1 rocket cools down in 10 s");
		helper.assertTrue(IronManAbilities.TIMED_FLIGHT_ACTIVATION_COST == 50f
				&& IronManAbilities.TIMED_FLIGHT_DRAIN_PER_SECOND == 3f, "Mark 1 flight burst costs 50 up front, then 3/s (v0.15.3)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManArmourHasNoDurability(GameTestHelper helper) {
		for (String mark : MARKS) {
			for (ArmorItem.Type type : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
					ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS }) {
				ItemStack stack = new ItemStack(IronManItems.armor(mark, type));
				helper.assertFalse(stack.isDamageableItem(), mark + " " + type.getName() + " takes no durability damage");
				helper.assertTrue(stack.getMaxStackSize() == 1, mark + " " + type.getName() + " still stacks to 1");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cEquipsAPartialSetFromTheInventory(GameTestHelper helper) {
		ServerPlayer p = at(helper, helper.makeMockServerPlayerInLevel());
		TonyStark.grant(p);
		p.getInventory().add(new ItemStack(IronManItems.armor("mark_1", ArmorItem.Type.HELMET)));
		p.getInventory().add(new ItemStack(IronManItems.armor("mark_1", ArmorItem.Type.CHESTPLATE)));
		helper.assertTrue(IronManSuitCall.autoEquipInventorySuit(p), "C puts on the two Mark I pieces (tech 0, so no research gate)");
		helper.assertTrue(IronManSuitUpManager.inTransition(p), "the suit-up has started");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cWithNoArmourFallsBackToThePicker(GameTestHelper helper) {
		ServerPlayer p = at(helper, helper.makeMockServerPlayerInLevel());
		TonyStark.grant(p);
		helper.assertFalse(IronManSuitCall.autoEquipInventorySuit(p), "nothing to equip -> the caller opens the picker");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void rescueTetherCarriesInABubbleWithNoTimeLimit(GameTestHelper helper) {
		ServerPlayer p = at(helper, helper.makeMockServerPlayerInLevel());
		GreenLantern.bond(p);
		Cow cow = EntityType.COW.create(helper.getLevel());
		cow.moveTo(p.getX(), p.getY(), p.getZ() + 4, 0f, 0f);
		cow.setNoAi(true);
		helper.getLevel().addFreshEntity(cow);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, cow.getBbHeight() * 0.5, 0));

		GreenLanternConstructs.deploy(p, ConstructType.RESCUE_TETHER);
		helper.assertTrue(GreenLanternConstructs.rescueHeldId(p) == cow.getId(), "the tether grabbed the cow");
		helper.assertTrue(helper.getLevel().getEntity(GreenLanternConstructs.rescueBubbleId(p))
				instanceof HardLightConstructEntity b && b.shape() == HardLightConstructEntity.Shape.BUBBLE
				&& b.targetId() == cow.getId() && b.scale() > cow.getBbHeight() * 0.5f, "a bubble wraps the cow");

		helper.assertTrue(GreenLanternConfig.TETHER_UPKEEP_PER_SEC == 0f, "the hold has no upkeep");
		GreenLantern.state(p).ringCharge = 0f; // an empty ring used to drop the target within a second
		for (int i = 0; i < 1200; i++) { // a minute of holding
			p.tickCount++;
			GreenLanternConstructs.tickRescueHeld(p);
		}
		helper.assertTrue(GreenLanternConstructs.rescueHeldId(p) == cow.getId(), "still carried a minute later");
		var bubble = helper.getLevel().getEntity(GreenLanternConstructs.rescueBubbleId(p));
		helper.assertTrue(bubble != null && bubble.position().distanceTo(cow.position().add(0, cow.getBbHeight() * 0.5, 0)) < 0.01,
				"the bubble follows the cow");

		GreenLanternConstructs.releaseRescueHeldSafely(p);
		helper.assertTrue(GreenLanternConstructs.rescueHeldId(p) == -1 && GreenLanternConstructs.rescueBubbleId(p) == -1,
				"setting it down pops the bubble");
		helper.succeed();
	}
}
