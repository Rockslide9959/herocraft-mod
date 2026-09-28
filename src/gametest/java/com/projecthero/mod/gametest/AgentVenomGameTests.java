package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities;
import com.projecthero.mod.symbiote.SymbioteHostType;
import com.projecthero.mod.symbiote.SymbioteSuit;
import com.projecthero.mod.symbiote.item.AgentVenomArmorItem;
import com.projecthero.mod.symbiote.item.SymbioteHostArmorItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.11: Agent Venom -- the Symbiote on a bonded Punisher. The bond keeps the Punisher, H puts on the Agent
 * Venom suit, the suit's stats go on and off, Tendril Snatch disarms a mob, and losing the Punisher demotes the
 * host to a Normal Symbiote host with the plain armour.
 */
public class AgentVenomGameTests implements FabricGameTest {
	private static ServerPlayer punisher(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(Punisher.grant(p), "the Punisher is granted");
		return p;
	}

	/** Push the suit-up clock past its end so the transform settles this tick. */
	private static void suitUp(ServerPlayer p) {
		Symbiote.state(p).toggleReadyAt = 0L;
		Symbiote.toggle(p);
		Symbiote.state(p).transformStartTick -= 10_000L;
		Symbiote.tick(p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aBondedPunisherBecomesAgentVenom(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		com.projecthero.mod.symbiote.entity.SymbioteEntity blob =
				com.projecthero.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		blob.moveTo(helper.absoluteVec(Vec3.ZERO));
		helper.getLevel().addFreshEntity(blob);
		com.projecthero.mod.symbiote.SymbioteBonding.bondNow(p, blob);
		helper.assertTrue(Symbiote.hasSymbiote(p), "the bond takes");
		helper.assertTrue(Punisher.hasPower(p), "and the Punisher is NOT purged");
		helper.assertTrue(SymbioteHostType.of(p) == SymbioteHostType.AGENT_VENOM, "the host is Agent Venom");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hPutsOnTheAgentVenomSuitAndItsStats(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		Symbiote.grant(p);
		double baseAttack = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		suitUp(p);
		helper.assertTrue(Symbiote.isActive(p), "the suit is on");
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			helper.assertTrue(p.getItemBySlot(slot).getItem() instanceof AgentVenomArmorItem, slot + " holds an Agent Venom piece");
		}
		helper.assertTrue(SymbioteAgentVenomAbilities.agentVenom(p), "suited as Agent Venom");
		SymbioteAgentVenomAbilities.reconcile(p);
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) > baseAttack + 0.2, "the suit's melee bonus is on");

		// H again retracts it and the stats come off
		suitUp(p);
		helper.assertFalse(Symbiote.isActive(p), "the suit is off");
		helper.assertFalse(SymbioteSuit.wearing(p), "no piece left behind");
		SymbioteAgentVenomAbilities.reconcile(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttack) < 0.001, "melee back to normal");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void tendrilSnatchDisarmsAMob(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		Symbiote.grant(p);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 3, 6))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f); // yaw 0 = looking +Z
		suitUp(p);
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		zombie.moveTo(at.x, at.y, at.z + 4.0, 180.0f, 0.0f);
		zombie.setNoAi(true);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.getLevel().addFreshEntity(zombie);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
		p.setShiftKeyDown(true);
		helper.assertTrue(SymbioteAgentVenomAbilities.handle(p, AbilitySlot.SLOT_4, true), "Sneak + Z is Agent Venom's");
		helper.assertTrue(zombie.getMainHandItem().isEmpty(), "the tendril rips the sword out of its hand");
		helper.assertTrue(Punisher.cooldownRemaining(p, SymbioteAgentVenomAbilities.TENDRIL_SNATCH) > 0, "and goes on cooldown");
		p.setShiftKeyDown(false);
		helper.assertFalse(SymbioteAgentVenomAbilities.handle(p, AbilitySlot.SLOT_4, true), "without Sneak, Z is Suppressive Fire again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void losingThePunisherDemotesToANormalHost(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		Symbiote.grant(p);
		suitUp(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof AgentVenomArmorItem, "precondition: Agent Venom suit");
		Punisher.revoke(p);
		helper.assertTrue(Symbiote.hasSymbiote(p), "the bond itself is kept");
		helper.assertTrue(SymbioteHostType.of(p) == SymbioteHostType.NORMAL, "now a Normal host");
		Symbiote.tick(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SymbioteHostArmorItem,
				"the suit swaps to the plain Symbiote armour");
		helper.succeed();
	}
}
