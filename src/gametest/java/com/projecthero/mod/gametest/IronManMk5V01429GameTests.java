package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.IronManMk5Suitcase;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/** v0.14.29: the Mark 5 rework -- stats, kit, instant repulsor, the suitcase fold / build, C never deploys it. */
public class IronManMk5V01429GameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer player(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	private static void wear(ServerPlayer p, String suitId) {
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(suitId, t)));
		}
		TonyStark.setActiveSuit(p, suitId);
	}

	/** Ticks the staged sequence to its end; returns how many ticks it took. */
	private static int runSequence(ServerPlayer p) {
		int n = 0;
		for (; n < 400 && IronManSuitUpManager.inTransition(p); n++) {
			IronManSuitUpManager.tick(p);
		}
		return n;
	}

	private static int caseSlot(ServerPlayer p) {
		var items = p.getInventory().items;
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).is(IronManItems.MARK_V_SUITCASE)) {
				return i;
			}
		}
		return -1;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk5StatsAndKit(GameTestHelper h) {
		IronManSuit mv = IronManSuits.MARK_V;
		h.assertTrue(mv.energyCapacity() == 2500f, "energy 2500");
		h.assertTrue(mv.energyRegenPerSecond() == 5f, "regens 5 energy/s");
		h.assertTrue(mv.maxIntegrity() == 800f && IronManEnergy.maxIntegrity("mark_v") == 800f, "integrity 800");
		h.assertTrue(mv.arrowFireImmune(), "immune to arrows + fire");
		h.assertTrue(mv.waterBreathing(), "breathes underwater");
		h.assertTrue(mv.autoFeed(), "auto-feeds");
		h.assertTrue(mv.targeting() && IronManTargeting.hasTargeting(mv), "lock-on / auto-aim");
		h.assertTrue(mv.strengthBonus() == 6f, "melee +6");
		h.assertTrue(mv.repulsorWindupTicks() == 0, "no repulsor spin-up");
		String[] kit = { IronManAbilities.REPULSOR_BLAST, IronManAbilities.SONIC_CLAP, IronManAbilities.FLARE,
				IronManAbilities.UNIBEAM, IronManAbilities.BLADE, IronManAbilities.SUIT_TOGGLE };
		for (int s = 1; s <= 6; s++) {
			h.assertTrue(kit[s - 1].equals(mv.abilityInSlot(s)), "slot " + s + " must be " + kit[s - 1]);
		}
		IronManSuit m2 = IronManSuits.MARK_2;
		h.assertTrue(mv.hasDash() && mv.dashDamage() == m2.dashDamage() && mv.sonicClapDamage() == m2.sonicClapDamage()
				&& mv.unibeamChannelTicks() == m2.unibeamChannelTicks(), "the Mark 2's numbers for the shared kit");
		int[] defense = { 3, 8, 6, 3 };
		for (int i = 0; i < 4; i++) {
			ArmorItem a = (ArmorItem) IronManItems.armor("mark_v", TYPES[i]);
			h.assertTrue(a.getDefense() == defense[i], "diamond-level " + TYPES[i].getName() + ": " + a.getDefense());
			h.assertTrue(a.getToughness() == 2.0f, "diamond toughness");
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk5RepulsorTapFiresInstantly(GameTestHelper h) {
		ServerPlayer p = player(h);
		wear(p, "mark_v");
		IronManEnergy.setEnergy(p, "mark_v", 2000f);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		h.assertTrue(TonyStark.state(p).repulsorWindupAt == 0L, "no spin-up is queued");
		h.assertTrue(IronManEnergy.energy(p, "mark_v") < 2000f, "the tap fired (and paid) on release");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk5StoreFoldsIntoACaseInTheMainHand(GameTestHelper h) {
		ServerPlayer p = player(h);
		wear(p, "mark_v");
		IronManEnergy.setEnergy(p, "mark_v", 1234f);
		IronManEnergy.setIntegrity(p, "mark_v", 456f);
		p.getInventory().selected = 2;
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_6, true); // plain C
		h.assertTrue(IronManSuitUpManager.inTransition(p), "C starts the fold");
		h.assertTrue(TonyStark.state(p).transitionTotal == IronManMk5Suitcase.DOWN_TICKS && IronManMk5Suitcase.DOWN_TICKS == 80,
				"the fold takes 4 s");
		h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_v"), "the pieces stay on until the fold ends");
		runSequence(p);
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "folded away");
		h.assertTrue(p.getInventory().items.get(2).is(IronManItems.MARK_V_SUITCASE), "the case lands in the empty main hand");
		for (int i = 0; i < p.getInventory().items.size(); i++) {
			h.assertFalse(p.getInventory().items.get(i).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem,
					"no loose pieces in the inventory");
		}
		ItemStack c = p.getInventory().items.get(2);
		h.assertTrue(SuitcaseContents.nonEmpty(c).size() == 4, "the case holds all four pieces");
		h.assertTrue(Math.abs(IronManEnergy.stackEnergy(c, "mark_v") - 1234f) < 1f
				&& Math.abs(IronManEnergy.stackIntegrity(c, "mark_v") - 456f) < 1f, "and the suit's energy / integrity");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk5CaseGoesToHotbarThenInventoryThenFeet(GameTestHelper h) {
		ServerPlayer p = player(h);
		var items = p.getInventory().items;
		p.getInventory().selected = 0;
		items.set(0, new ItemStack(Items.STONE));
		items.set(1, new ItemStack(Items.STONE));
		h.assertTrue(IronManSuitUpManager.placeSuitcase(p, new ItemStack(IronManItems.MARK_V_SUITCASE)) == 2,
				"busy hand -> the first free hotbar slot");
		for (int i = 0; i < 9; i++) {
			items.set(i, new ItemStack(Items.STONE));
		}
		h.assertTrue(IronManSuitUpManager.placeSuitcase(p, new ItemStack(IronManItems.MARK_V_SUITCASE)) == 9,
				"full hotbar -> the first free inventory slot");
		for (int i = 0; i < 36; i++) {
			items.set(i, new ItemStack(Items.STONE));
		}
		h.assertTrue(IronManSuitUpManager.placeSuitcase(p, new ItemStack(IronManItems.MARK_V_SUITCASE)) == -1,
				"full inventory -> dropped");
		h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(3),
				e -> e.getItem().is(IronManItems.MARK_V_SUITCASE)).isEmpty(), "at the player's feet, never lost");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cNeverDeploysTheMk5(GameTestHelper h) {
		ServerPlayer p = player(h);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor("mark_v", t)));
		}
		ItemStack packed = new ItemStack(IronManItems.MARK_V_SUITCASE); // a legacy case = a whole Mark 5
		p.getInventory().add(packed);
		h.assertFalse(IronManSuitCall.autoEquipInventorySuit(p), "plain C ignores the Mark 5 pieces and its case");
		h.assertFalse(IronManSuitUpManager.inTransition(p), "nothing started");
		h.assertTrue(IronManSuitCall.assemblableSuits(p).stream().noneMatch(o -> o.suitId().equals("mark_v")),
				"the Sneak+C picker never lists the Mark 5");
		h.assertFalse(IronManSuitUpManager.beginSuitUp(p, "mark_v"), "no inventory suit-up path for the Mark 5");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rightClickTheCaseSuitsUpInSixSeconds(GameTestHelper h) {
		ServerPlayer p = player(h);
		p.getInventory().selected = 0;
		p.getInventory().items.set(0, new ItemStack(IronManItems.MARK_V_SUITCASE));
		IronManItems.MARK_V_SUITCASE.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
		h.assertTrue(IronManSuitUpManager.inTransition(p) && IronManSuitUpManager.assembling(p), "right-click starts the build");
		h.assertTrue(TonyStark.state(p).transitionTotal == IronManMk5Suitcase.UP_TICKS && IronManMk5Suitcase.UP_TICKS == 120,
				"the build takes 6 s");
		IronManSuitFx fx = IronManSuitFx.of(p);
		h.assertTrue(fx.poseKind() == IronManSuitFx.POSE_MK5_UP && fx.style() == IronManSuitFx.STYLE_MK5, "the Mark 5 pose + style");
		// held out first: nothing is on the body until the hold is over, then the chest arrives first
		for (int i = 0; i < IronManMk5Suitcase.HOLD_TICKS - 1; i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "the case is still held out in front");
		IronManSuitUpManager.tick(p);
		h.assertTrue(IronManArmor.isPieceWorn(p, EquipmentSlot.CHEST, "mark_v") && p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
				&& p.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "the case became the chestplate, first");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		h.assertFalse(com.projecthero.mod.ironman.IronManBlade.active(p), "abilities stay locked while it builds");
		runSequence(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_v"), "the whole Mark 5 is on");
		h.assertTrue(caseSlot(p) < 0, "the case is used up");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk5AssemblyOrderChestArmsLegsHeadFaceplate(GameTestHelper h) {
		int[] prevEnd = null;
		for (int step = IronManMk5Suitcase.STEP_CHEST; step <= IronManMk5Suitcase.STEP_FACEPLATE; step++) {
			int[] t = IronManMk5Suitcase.upStepTicks(step);
			h.assertTrue(t[1] > t[0], "step " + step + " has a window");
			if (prevEnd != null) {
				h.assertTrue(t[0] >= prevEnd[1], "step " + step + " starts after the one before ends");
			}
			prevEnd = t;
		}
		h.assertTrue(IronManMk5Suitcase.upStepTicks(IronManMk5Suitcase.STEP_CHEST)[0] >= IronManMk5Suitcase.HOLD_TICKS,
				"the case is held out before the chest starts");
		h.assertTrue(prevEnd[1] <= IronManMk5Suitcase.UP_TICKS, "the faceplate is done inside the 6 s");
		// bones map onto the steps
		h.assertTrue(IronManMk5Suitcase.step(1, "chest") == IronManMk5Suitcase.STEP_CHEST
				&& IronManMk5Suitcase.step(1, "arc_reactor") == IronManMk5Suitcase.STEP_CHEST
				&& IronManMk5Suitcase.step(1, "right_upper_arm") == IronManMk5Suitcase.STEP_ARMS
				&& IronManMk5Suitcase.step(1, "left_gauntlet") == IronManMk5Suitcase.STEP_ARMS
				&& IronManMk5Suitcase.step(2, "right_thigh") == IronManMk5Suitcase.STEP_LEGS
				&& IronManMk5Suitcase.step(3, "left_boot") == IronManMk5Suitcase.STEP_LEGS
				&& IronManMk5Suitcase.step(0, "helmet") == IronManMk5Suitcase.STEP_HEAD
				&& IronManMk5Suitcase.step(0, "faceplate") == IronManMk5Suitcase.STEP_FACEPLATE, "bone -> step");
		// within a piece: the torso is whole before an arm bone starts; the helmet before the faceplate
		h.assertTrue(IronManMk5Suitcase.localProgress(1, "chest", IronManMk5Suitcase.CHEST_TORSO_END) == 1f
				&& IronManMk5Suitcase.localProgress(1, "right_gauntlet", IronManMk5Suitcase.CHEST_TORSO_END) == 0f,
				"torso before arms");
		h.assertTrue(IronManMk5Suitcase.localProgress(0, "helmet", IronManMk5Suitcase.HEAD_SHELL_END) == 1f
				&& IronManMk5Suitcase.localProgress(0, "faceplate", IronManMk5Suitcase.HEAD_SHELL_END) == 0f,
				"helmet before faceplate");
		h.assertTrue(IronManMk5Suitcase.remap(1, "chest", 0f) > 0f, "nothing shows at progress 0");
		// the synced clock times each piece over its own window
		IronManSuitFx fx = IronManSuitFx.EMPTY.withPose(IronManSuitFx.POSE_MK5_UP, 1000L, 120, IronManSuitFx.STYLE_MK5)
				.withPiece(1, 1000L, true);
		int w = IronManMk5Suitcase.upWindow(1);
		h.assertTrue(fx.lockTicks(1) == w && fx.pieceProgress(EquipmentSlot.CHEST, 1000L + w, 0f) == 1f
				&& Math.abs(fx.pieceProgress(EquipmentSlot.CHEST, 1000L + w / 2, 0f) - 0.5f) < 1e-3f, "per-piece window");
		// the fold is the exact reverse: faceplate/head first, chest last, and a piece stays hidden once apart
		h.assertTrue(IronManMk5Suitcase.downStart(0) < IronManMk5Suitcase.downStart(3)
				&& IronManMk5Suitcase.downStart(3) < IronManMk5Suitcase.downStart(2)
				&& IronManMk5Suitcase.downStart(2) < IronManMk5Suitcase.downStart(1), "fold order");
		h.assertTrue(IronManMk5Suitcase.downStart(1) + IronManMk5Suitcase.downWindow(1)
				<= IronManMk5Suitcase.DOWN_TICKS - IronManMk5Suitcase.CASE_FORM_TICKS, "the case forms after the chest is apart");
		IronManSuitFx down = IronManSuitFx.EMPTY.withPose(IronManSuitFx.POSE_MK5_DOWN, 1000L, 80, IronManSuitFx.STYLE_MK5)
				.withPiece(0, 1001L, false);
		h.assertTrue(down.pieceProgress(EquipmentSlot.HEAD, 1001L + 60, 0f) == 0f, "a folded piece stays hidden until the end");
		// pose: case held out in front (both arms forward) first, then arms out to the side
		float[] hold = IronManMk5Suitcase.pose(true, 10f);
		float[] spread = IronManMk5Suitcase.pose(true, 50f);
		h.assertTrue(hold[1] < -0.5f && hold[4] < -0.5f, "both arms forward holding the case");
		h.assertTrue(spread[3] > 1.2f && spread[6] < -1.2f, "arms out to the side while it builds");
		h.assertTrue(IronManMk5Suitcase.caseScale(true, 10f) == 1f && IronManMk5Suitcase.caseScale(true, 40f) == 0f
				&& IronManMk5Suitcase.caseScale(false, 79f) > 0.9f, "case drawn while held, gone once it is the chestplate");
		h.succeed();
	}
}
