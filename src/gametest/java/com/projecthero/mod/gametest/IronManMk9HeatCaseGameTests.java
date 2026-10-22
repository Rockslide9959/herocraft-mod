package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.PortableDeploy;
import com.projecthero.mod.ironman.suit.PortableForm;
import com.projecthero.mod.ironman.suit.PortableForms;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** v0.16.7 (user) Iron Man Era II stage 2: the Mk IX heat case (deploy, fold, lava, refusals, how you get it). */
public class IronManMk9HeatCaseGameTests implements FabricGameTest {
	private static final PortableForm MK9 = PortableForms.MK9_HEAT_CASE;
	private static final String SUIT = MK9.suitId();
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer player(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.getInventory().clearContent();
		return p;
	}

	private static ItemStack packedCase(float integrity) {
		ItemStack c = new ItemStack(IronManItems.MARK_9_HEAT_CASE);
		NonNullList<ItemStack> slots = NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int bit = 0; bit < 4; bit++) {
			slots.set(bit, new ItemStack(IronManItems.armor(SUIT, TYPES[bit])));
		}
		SuitcaseContents.write(c, slots);
		IronManEnergy.stampStack(c, IronManEnergy.capacity(SUIT), integrity); // v0.16.7 (user): a full Era II reactor
		return c;
	}

	private static void wear(ServerPlayer p) {
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(SUIT, t)));
		}
		TonyStark.setActiveSuit(p, SUIT);
	}

	private static int count(ServerPlayer p) {
		int n = 0;
		for (ItemStack s : p.getInventory().items) {
			if (s.is(IronManItems.MARK_9_HEAT_CASE)) {
				n++;
			}
		}
		return n;
	}

	/** Fill the player's cell with lava (feet and head). */
	private static void lava(GameTestHelper h) {
		h.setBlock(new BlockPos(1, 1, 1), Blocks.LAVA);
		h.setBlock(new BlockPos(1, 2, 1), Blocks.LAVA);
	}

	/** Would lava hurt {@code p} right now? Checks the mod's damage gate and the vanilla Fire Resistance rule. */
	private static boolean lavaHurts(ServerPlayer p) {
		var src = p.damageSources().lava();
		return IronManDamage.onAllowDamage(p, src, 4f) && !p.hasEffect(MobEffects.FIRE_RESISTANCE);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void registryAndTimetable(GameTestHelper h) {
		h.assertTrue(PortableForms.byId(SUIT) == MK9 && PortableForms.byItem(IronManItems.MARK_9_HEAT_CASE) == MK9,
				"the Mk IX is registered by suit and by item");
		h.assertTrue(PortableForms.all().indexOf(MK9) > PortableForms.all().indexOf(PortableForms.MK6_BRIEFCASE),
				"appended after the Mk VI (the synced pose kinds never move)");
		h.assertTrue(PortableForms.byPoseKind(PortableForms.poseKind(MK9, true)) == MK9
				&& PortableForms.foldPose(PortableForms.poseKind(MK9, false)), "its pose kinds carry the form");
		h.assertTrue(MK9.rest() == PortableForm.Place.HAND && MK9.during() == PortableForm.Place.FLOOR, "carried, set down");
		h.assertTrue(MK9.pieceOnAt(3) < MK9.pieceOnAt(2) && MK9.pieceOnAt(2) < MK9.pieceOnAt(1)
				&& MK9.pieceOnAt(1) < MK9.pieceOnAt(0), "piece order: boots, legs, chest, helmet");
		h.assertTrue(MK9.pieceOnAt(3) >= MK9.beats().openTo() - 4, "the plate climbs once the leaves are (nearly) open");
		h.assertTrue(MK9.appear(GantryTimeline.FACEPLATE, 1f) <= MK9.beats().sealAt()
				&& MK9.beats().sealAt() < MK9.beats().total(), "the faceplate (eyes) is built last, before it seals");
		for (int st = 0; st < GantryTimeline.STAGES; st++) {
			h.assertTrue(MK9.uses(st), "the Mk IX uses every gantry stage (" + st + ")");
			h.assertTrue(MK9.pieceOnAt(PortableForm.bitOf(st)) <= MK9.appear(st, 0f), "slot before texels, stage " + st);
			if (st != GantryTimeline.FACEPLATE) {
				h.assertTrue(MK9.stageWindows()[st][1] <= MK9.stageWindows()[GantryTimeline.FACEPLATE][1],
						"the faceplate finishes last (" + st + ")");
			}
		}
		for (float f = 0f; f <= MK9.beats().total(); f += 2f) {
			float[] a = MK9.pose(f);
			h.assertTrue(a != null && a.length == 13, "a pose at frame " + f);
		}
		h.assertTrue(MK9.pose(30f)[4] < -0.7f, "the left forearm comes up against the heat as the leaves open");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cDeploysTheHeatCase(GameTestHelper h) {
		ServerPlayer p = player(h);
		p.getInventory().selected = 0;
		p.getInventory().items.set(0, packedCase(2222f));
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_6, true); // plain C, unsuited
		h.assertTrue(PortableDeploy.running(p) && IronManSuitUpManager.assembling(p), "C starts the deploy");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == PortableForms.poseKind(MK9, true), "the synced deploy clock");
		for (int i = 0; i < MK9.pieceOnAt(3); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(IronManArmor.isPieceWorn(p, EquipmentSlot.FEET, SUIT) && p.getItemBySlot(EquipmentSlot.LEGS).isEmpty(),
				"the boots first");
		h.assertTrue(SuitcaseContents.nonEmpty(p.getInventory().items.get(0)).size() == 3, "out of the case, not conjured");
		for (int i = 0; i < 400 && IronManSuitUpManager.inTransition(p); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "the whole Mk IX is on");
		h.assertFalse(p.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false), "the faceplate sealed");
		h.assertTrue(count(p) == 0, "the case is used up");
		h.assertTrue(Math.abs(IronManEnergy.integrity(p, SUIT) - 2222f) < 1f, "the case's integrity carried onto the suit");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void holdShiftCFoldsBackIntoTheHandWithIntegrity(GameTestHelper h) {
		ServerPlayer p = player(h);
		wear(p);
		IronManEnergy.setIntegrity(p, SUIT, 1500f);
		p.getInventory().selected = 3;
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		long t0 = p.level().getGameTime();
		PortableDeploy.holdTick(p, t0 + PortableDeploy.FOLD_HOLD_TICKS);
		h.assertTrue(PortableDeploy.running(p), "the fold runs after the hold");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == PortableForms.poseKind(MK9, false), "the synced fold clock");
		p.setShiftKeyDown(false);
		for (int i = 0; i < 400 && IronManSuitUpManager.inTransition(p); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "folded away");
		ItemStack c = p.getInventory().items.get(3);
		h.assertTrue(c.is(IronManItems.MARK_9_HEAT_CASE), "the heat case lands in the empty main hand");
		h.assertTrue(SuitcaseContents.nonEmpty(c).size() == 4, "holding all four pieces");
		h.assertTrue(Math.abs(IronManEnergy.stackIntegrity(c, SUIT) - 1500f) < 1f, "and the suit's integrity");
		h.assertFalse(p.hasEffect(MobEffects.FIRE_RESISTANCE), "no heat shroud on cool ground");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void foldAndDeployStandingInLava(GameTestHelper h) {
		ServerPlayer p = player(h);
		wear(p);
		lava(h);
		p.setRemainingFireTicks(200);
		h.assertTrue(PortableDeploy.inHeat(p), "standing in lava");
		h.assertFalse(lavaHurts(p), "the full Mk IX is lava-proof");
		h.assertTrue(PortableDeploy.fold(p, SUIT), "the fold starts in lava");
		int ticks = 0;
		boolean safe = true;
		while (IronManSuitUpManager.inTransition(p) && ticks++ < 400) {
			safe &= !lavaHurts(p);
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(safe, "lava never hurts during the fold (the pieces stay on until the last tick)");
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "folded away, in lava");
		h.assertTrue(count(p) == 1, "the heat case is in the inventory");
		h.assertTrue(p.hasEffect(MobEffects.FIRE_RESISTANCE)
				&& p.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() >= PortableDeploy.HEAT_GRACE_TICKS - 1,
				"the heat shroud: Fire Resistance to climb out");
		h.assertFalse(p.isOnFire(), "the flames are out");
		h.assertFalse(lavaHurts(p), "not hurt right after the fold either");
		float hp = p.getHealth();
		p.hurt(p.damageSources().lava(), 4f);
		h.assertTrue(p.getHealth() >= hp, "lava really does nothing to the shrouded player");
		h.assertTrue(p.isAlive(), "alive");
		// and back on, still in the lava (the shroud gone): immune the whole deploy, immune after it
		p.removeEffect(MobEffects.FIRE_RESISTANCE);
		h.assertTrue(PortableDeploy.deploy(p, SUIT), "deploys in lava");
		safe = true;
		ticks = 0;
		while (IronManSuitUpManager.inTransition(p) && ticks++ < 400) {
			safe &= !lavaHurts(p);
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(safe, "lava never hurts during the deploy");
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT) && !lavaHurts(p), "suited and lava-proof again");
		h.assertFalse(p.hasEffect(MobEffects.FIRE_RESISTANCE), "a full deploy needs no shroud");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void refusesWhenItShould(GameTestHelper h) {
		ServerPlayer p = player(h);
		h.assertFalse(PortableDeploy.deployFromC(p), "no case, no deploy");
		ItemStack empty = new ItemStack(IronManItems.MARK_9_HEAT_CASE);
		SuitcaseContents.write(empty, NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY));
		p.getInventory().items.set(0, empty);
		h.assertFalse(PortableDeploy.deploy(p, SUIT), "an emptied case never conjures a suit");
		p.getInventory().items.set(0, packedCase(500f));
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor("mark_6", t)));
		}
		h.assertFalse(PortableDeploy.deploy(p, SUIT), "not while another suit is worn");
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), ItemStack.EMPTY);
		}
		h.assertFalse(PortableDeploy.fold(p, SUIT), "nothing worn, nothing to fold");
		ServerPlayer q = player(h);
		TonyStark.revoke(q);
		q.getInventory().items.set(0, packedCase(500f));
		q.getInventory().selected = 0;
		IronManItems.MARK_9_HEAT_CASE.use(h.getLevel(), q, InteractionHand.MAIN_HAND);
		h.assertFalse(IronManSuitUpManager.inTransition(q), "no Tony Stark power, no deploy");
		h.assertTrue(PortableDeploy.deploy(p, SUIT), "the packed case deploys");
		h.assertFalse(PortableDeploy.deploy(p, SUIT), "not twice at once");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void looseMk9PiecesPackIntoAHeatCase(GameTestHelper h) {
		ServerPlayer p = player(h);
		for (int i = 0; i < 3; i++) {
			p.getInventory().add(new ItemStack(IronManItems.armor(SUIT, TYPES[i])));
		}
		h.assertFalse(IronManArmorItem.packLoose(p, SUIT, IronManItems.MARK_9_HEAT_CASE, "x"), "three pieces must not pack");
		ItemStack boots = new ItemStack(IronManItems.armor(SUIT, ArmorItem.Type.BOOTS));
		IronManEnergy.stampStack(boots, 0f, 900f); // a worn piece
		p.getInventory().add(boots);
		p.getInventory().selected = 0;
		var r = p.getInventory().getSelected().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
		h.assertTrue(r.getResult().consumesAction(), "right-clicking a loose piece packs them");
		h.assertTrue(count(p) == 1, "one heat case made");
		ItemStack c = ItemStack.EMPTY;
		for (ItemStack s : p.getInventory().items) {
			h.assertFalse(s.getItem() instanceof IronManArmorItem, "no loose pieces left");
			if (s.is(IronManItems.MARK_9_HEAT_CASE)) {
				c = s;
			}
		}
		h.assertTrue(SuitcaseContents.nonEmpty(c).size() == 4, "the case holds all four");
		h.assertTrue(Math.abs(IronManEnergy.stackIntegrity(c, SUIT) - 900f) < 1f, "with the most worn piece's integrity");
		h.assertTrue(IronManItems.MARK_9_HEAT_CASE.components().has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT),
				"the case never burns");
		h.succeed();
	}
}
