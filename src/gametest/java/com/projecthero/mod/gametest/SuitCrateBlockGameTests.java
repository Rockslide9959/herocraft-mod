package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.era1.EraOneEject;
import com.projecthero.mod.ironman.crate.CrateSuitUp;
import com.projecthero.mod.ironman.crate.SuitCrateBlock;
import com.projecthero.mod.ironman.crate.SuitCrateBlockEntity;
import com.projecthero.mod.ironman.crate.SuitCrateBlocks;
import com.projecthero.mod.ironman.crate.SuitCrateFoldEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitCrateItem;
import com.projecthero.mod.ironman.suit.IronManManualSuitUp;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Iron Man ERA I: the <b>Suit Crate block</b>. The Shift+C eject shoots the wearer out of the back
 * (fall-safe) and leaves the empty suit standing, which folds into a crate block holding the pieces; breaking the block
 * gives the item with the suit inside (survival and creative), placing the item puts the block back; iron ingots repair
 * the crated suit; Shift+right-click suits up from it in 30 s anywhere, and the crate is gone at the end.
 */
public class SuitCrateBlockGameTests implements FabricGameTest {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };

	private static ServerPlayer tony(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		BlockPos at = h.absolutePos(new BlockPos(2, 1, 4));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	private static void wear(ServerPlayer p, String suit) {
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(suit, TYPES[i])));
		}
		TonyStark.setActiveSuit(p, suit);
		IronManEnergy.setIntegrity(p, suit, IronManEnergy.maxIntegrity(suit));
		IronManEnergy.setEnergy(p, suit, IronManEnergy.capacity(suit));
	}

	/** Four pieces of {@code suit} stamped with {@code integrity} / {@code energy}. */
	private static NonNullList<ItemStack> pieces(String suit, float integrity, float energy) {
		NonNullList<ItemStack> out = NonNullList.withSize(4, ItemStack.EMPTY);
		for (int i = 0; i < 4; i++) {
			ItemStack s = new ItemStack(IronManItems.armor(suit, TYPES[i]));
			IronManEnergy.stampStack(s, energy, integrity);
			out.set(i, s);
		}
		return out;
	}

	private static SuitCrateBlockEntity crateAt(GameTestHelper h, BlockPos rel, String suit, float integrity, float energy) {
		h.setBlock(rel, SuitCrateBlocks.SUIT_CRATE);
		SuitCrateBlockEntity be = (SuitCrateBlockEntity) h.getBlockEntity(rel);
		be.setContents(pieces(suit, integrity, energy));
		return be;
	}

	/** Every crate block in the 8x8x8 test cage. */
	private static SuitCrateBlockEntity findCrateBlock(GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				for (int z = 0; z < 8; z++) {
					if (h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x, y, z))) instanceof SuitCrateBlockEntity be) {
						return be;
					}
				}
			}
		}
		return null;
	}

	private static List<ItemEntity> dropsNear(GameTestHelper h, BlockPos rel) {
		BlockPos abs = h.absolutePos(rel);
		return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2.0));
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	// ------------------------------------------------------------------ the eject

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void ejectShootsYouOutAndTheSuitFoldsIntoACrateBlock(GameTestHelper h) {
		ServerPlayer p = tony(h);
		p.setYRot(0f); // facing +Z (south): out of the back is -Z
		wear(p, "mark_iii");
		IronManEnergy.setIntegrity(p, "mark_iii", 600f);
		IronManEnergy.setEnergy(p, "mark_iii", 500f);
		SuitCrateFoldEntity fold = EraOneEject.eject(p);
		h.assertTrue(fold != null && !IronManArmor.wearingAnyIronMan(p), "the suit came off and stands on its own");
		h.assertTrue(fold.stack(1).getItem() instanceof IronManArmorItem a && "mark_iii".equals(a.suitId()), "holding the Mk III");
		// shot out of the back: backwards and a little up
		Vec3 v = p.getDeltaMovement();
		h.assertTrue(v.z < -0.5 && v.y > 0.3 && Math.abs(v.x) < 0.05, "launched out of the back, got " + v);
		// fall-safe until they land
		h.assertTrue(EraOneEject.launched(p), "the launch is fall-safe");
		float hp = p.getHealth();
		h.assertFalse(p.hurt(p.damageSources().fall(), 12f), "no fall damage while launched");
		h.assertTrue(p.getHealth() == hp, "health unchanged");
		p.fallDistance = 30f;
		h.runAfterDelay(2, () -> h.assertTrue(p.fallDistance == 0f, "the fall distance is reset while launched, got " + p.fallDistance));
		h.runAfterDelay(SuitCrateFoldEntity.STAND_TICKS / 2, () -> {
			h.assertTrue(fold.isAlive() && findCrateBlock(h) == null, "the empty suit stands for a second first");
		});
		h.runAfterDelay(SuitCrateFoldEntity.STAND_TICKS + SuitCrateFoldEntity.FOLD_TICKS / 2, () -> {
			h.assertTrue(fold.isAlive() && fold.foldProgress(0f) > 0f && fold.target() != null, "folding, its crate's spot picked");
		});
		h.runAfterDelay(SuitCrateFoldEntity.STAND_TICKS + SuitCrateFoldEntity.FOLD_TICKS + 10, () -> {
			h.assertFalse(fold.isAlive(), "the standing suit is gone");
			SuitCrateBlockEntity be = findCrateBlock(h);
			h.assertTrue(be != null, "a Suit Crate block stands where the suit stood");
			h.assertTrue(be.getBlockPos().distManhattan(h.absolutePos(new BlockPos(2, 1, 4))) <= 2, "next to the eject spot: " + be.getBlockPos());
			h.assertTrue(be.mask() == 15 && "mark_iii".equals(be.suitId()), "all four Mk III pieces inside");
			h.assertTrue(Math.abs(be.integrity() - 600f) < 0.01f, "with the suit's integrity, got " + be.integrity());
			h.assertTrue(be.energy() >= 500f && be.energy() <= 502f, "and its energy, got " + be.energy());
			h.assertTrue(be.getBlockState().getValue(SuitCrateBlock.FACING) == Direction.SOUTH, "facing the way the suit faced");
			h.assertTrue(dropsNear(h, new BlockPos(2, 1, 4)).isEmpty(), "nothing dropped as loose items");
			leave(h, p);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ break / place

	@GameTest(template = EMPTY_STRUCTURE)
	public void breakingTheCrateDropsTheItemWithTheSuit(GameTestHelper h) {
		ServerPlayer p = tony(h);
		BlockPos rel = new BlockPos(4, 1, 2);
		crateAt(h, rel, "mark_4", 900f, 700f);
		h.assertTrue(p.gameMode.destroyBlock(h.absolutePos(rel)), "a survival player breaks it (no special tool)");
		h.assertTrue(h.getLevel().getBlockState(h.absolutePos(rel)).isAir(), "the block is gone");
		List<ItemEntity> drops = dropsNear(h, rel);
		h.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(IronManItems.SUIT_CRATE), "it drops one Suit Crate item, got " + drops + " inv " + p.getInventory().items.stream().filter(s -> !s.isEmpty()).toList());
		ItemStack crate = drops.get(0).getItem();
		h.assertTrue(SuitCrateItem.mask(crate) == 15 && "mark_4".equals(SuitCrateItem.suitId(crate)), "with the whole Mk IV inside");
		h.assertTrue(IronManEnergy.stackIntegrity(SuitCrateItem.pieces(crate)[1], "mark_4") == 900f
				&& IronManEnergy.stackEnergy(SuitCrateItem.pieces(crate)[1], "mark_4") == 700f, "integrity and energy kept");
		drops.forEach(ItemEntity::discard);
		// creative: a full crate still drops its suit; an empty one just vanishes
		p.setGameMode(GameType.CREATIVE);
		BlockPos rel2 = new BlockPos(4, 1, 5);
		crateAt(h, rel2, "mark_v", 400f, 300f);
		h.assertTrue(p.gameMode.destroyBlock(h.absolutePos(rel2)), "a creative player breaks it");
		List<ItemEntity> creative = dropsNear(h, rel2);
		h.assertTrue(creative.size() == 1 && SuitCrateItem.mask(creative.get(0).getItem()) == 15, "the suit is never lost, creative too");
		creative.forEach(ItemEntity::discard);
		h.setBlock(rel2, SuitCrateBlocks.SUIT_CRATE);
		h.assertTrue(p.gameMode.destroyBlock(h.absolutePos(rel2)), "an empty crate breaks");
		h.assertTrue(dropsNear(h, rel2).isEmpty(), "an empty crate broken in creative drops nothing");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void placingTheCrateItemPutsTheBlockBackWithItsContents(GameTestHelper h) {
		ServerPlayer p = tony(h);
		ItemStack crate = SuitCrateItem.of(pieces("mark_1_v2", 500f, 250f));
		p.setItemInHand(InteractionHand.MAIN_HAND, crate);
		BlockPos base = new BlockPos(4, 1, 2);
		h.setBlock(base, Blocks.IRON_BLOCK);
		BlockPos baseAbs = h.absolutePos(base);
		var r = crate.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(baseAbs).add(0, 0.5, 0), Direction.UP, baseAbs, false)));
		h.assertTrue(r.consumesAction(), "placed, got " + r);
		BlockPos placed = baseAbs.above();
		h.assertTrue(h.getLevel().getBlockEntity(placed) instanceof SuitCrateBlockEntity, "a Suit Crate block");
		SuitCrateBlockEntity be = (SuitCrateBlockEntity) h.getLevel().getBlockEntity(placed);
		h.assertTrue(be.mask() == 15 && "mark_1_v2".equals(be.suitId()) && be.integrity() == 500f && be.energy() == 250f,
				"holding the same suit with its integrity / energy");
		h.assertTrue(p.getMainHandItem().isEmpty(), "the item is used up");
		// regression: a block registered too early (from inside the registry bootstrap) has no state ids and cannot be sent
		for (var st : SuitCrateBlocks.SUIT_CRATE.getStateDefinition().getPossibleStates()) {
			h.assertTrue(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(st) >= 0, "state id for " + st);
		}
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ ingots / info

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironIngotsRepairTheCratedSuit(GameTestHelper h) {
		ServerPlayer p = tony(h);
		BlockPos rel = new BlockPos(4, 1, 2);
		SuitCrateBlockEntity be = crateAt(h, rel, "mark_iii", 0f, 400f);
		float max = IronManEnergy.maxIntegrity("mark_iii");
		float step = max * SuitCrateBlock.REPAIR_FRACTION;
		ItemStack ingots = new ItemStack(Items.IRON_INGOT, 3);
		p.setItemInHand(InteractionHand.MAIN_HAND, ingots);
		SuitCrateBlock.interact(h.getLevel(), h.absolutePos(rel), p, p.getMainHandItem());
		h.assertTrue(Math.abs(be.integrity() - step) < 0.01f && p.getMainHandItem().getCount() == 2, "one ingot = 4% of max, got " + be.integrity());
		SuitCrateBlock.interact(h.getLevel(), h.absolutePos(rel), p, p.getMainHandItem());
		h.assertTrue(Math.abs(be.integrity() - 2 * step) < 0.01f && p.getMainHandItem().getCount() == 1, "two ingots = 8%");
		for (ItemStack piece : be.contents()) {
			h.assertTrue(Math.abs(IronManEnergy.stackIntegrity(piece, "mark_iii") - 2 * step) < 0.01f
					&& IronManEnergy.stackEnergy(piece, "mark_iii") == 400f, "every piece re-stamped, energy kept");
		}
		be.setIntegrity(max - 5f);
		SuitCrateBlock.interact(h.getLevel(), h.absolutePos(rel), p, p.getMainHandItem());
		h.assertTrue(be.integrity() == max && p.getMainHandItem().isEmpty(), "capped at full");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 2));
		SuitCrateBlock.interact(h.getLevel(), h.absolutePos(rel), p, p.getMainHandItem());
		h.assertTrue(p.getMainHandItem().getCount() == 2, "a full suit takes no ingot");
		// a plain right-click only reads it out
		SuitCrateBlock.interact(h.getLevel(), h.absolutePos(rel), p, ItemStack.EMPTY);
		h.assertTrue(be.mask() == 15 && !IronManArmor.wearingAnyIronMan(p), "plain use changes nothing");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ the 30 s suit-up

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = IronManManualSuitUp.MK1_TICKS + 80)
	public void shiftUseSuitsUpFromTheCrateIn30Seconds(GameTestHelper h) {
		ServerPlayer p = tony(h);
		BlockPos rel = new BlockPos(3, 1, 4);
		BlockPos abs = h.absolutePos(rel);
		crateAt(h, rel, "mark_v", 300f, 600f);
		p.setShiftKeyDown(true);
		SuitCrateBlock.interact(h.getLevel(), abs, p, new ItemStack(Items.STICK));
		p.setShiftKeyDown(false);
		h.assertTrue(IronManManualSuitUp.running(p) && CrateSuitUp.fromCrate(p), "the 30 s suit-up started from the crate");
		h.assertTrue(h.getLevel().getBlockState(abs).getValue(SuitCrateBlock.OPEN), "the crate is open");
		h.assertTrue(IronManEnergy.integrity(p, "mark_v") == 300f, "the crate's integrity is the suit's");
		h.runAfterDelay(IronManManualSuitUp.MK1_TICKS / 2, () -> {
			h.assertTrue(IronManManualSuitUp.running(p), "still building at 15 s");
			h.assertTrue(h.getLevel().getBlockEntity(abs) instanceof SuitCrateBlockEntity, "the crate is still there midway");
			SuitCrateBlockEntity be = (SuitCrateBlockEntity) h.getLevel().getBlockEntity(abs);
			int worn = 0;
			for (EquipmentSlot s : SLOTS) {
				worn += p.getItemBySlot(s).getItem() instanceof IronManArmorItem ? 1 : 0;
			}
			h.assertTrue(worn >= 1 && worn < 4, "some pieces on midway, got " + worn);
			h.assertTrue(worn + Integer.bitCount(be.mask()) == 4, "every piece in exactly one place");
		});
		h.runAfterDelay(IronManManualSuitUp.MK1_TICKS + 10, () -> {
			h.assertFalse(IronManManualSuitUp.running(p), "done");
			h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_v"), "the Mk V is on");
			h.assertTrue(h.getLevel().getBlockState(abs).isAir(), "the crate block is gone");
			h.assertTrue(IronManEnergy.integrity(p, "mark_v") == 300f && IronManEnergy.energy(p, "mark_v") >= 600f,
					"with the crate's integrity and energy");
			h.assertFalse(CrateSuitUp.fromCrate(p), "the crate link is forgotten");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void aCrateSuitUpInterruptedFinishesOrKeepsThePiecesSafe(GameTestHelper h) {
		ServerPlayer p = tony(h);
		BlockPos rel = new BlockPos(3, 1, 4);
		BlockPos abs = h.absolutePos(rel);
		crateAt(h, rel, "mark_1", 700f, 600f);
		p.setShiftKeyDown(true);
		h.assertTrue(CrateSuitUp.begin(p, (SuitCrateBlockEntity) h.getLevel().getBlockEntity(abs)), "started");
		p.setShiftKeyDown(false);
		// a second player cannot use the same crate
		ServerPlayer other = tony(h);
		h.assertTrue(CrateSuitUp.busy((SuitCrateBlockEntity) h.getLevel().getBlockEntity(abs)), "the crate is busy");
		h.assertFalse(CrateSuitUp.begin(other, (SuitCrateBlockEntity) h.getLevel().getBlockEntity(abs)), "only one at a time");
		h.runAfterDelay(40, () -> {
			// logout mid-build: finish at once from the crate (test hook = the real logout path)
			IronManManualSuitUp.completeNow(p);
			h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_1"), "every piece went on from the crate");
			h.assertTrue(h.getLevel().getBlockState(abs).isAir(), "and the empty crate is gone");
			leave(h, p);
			leave(h, other);
			h.succeed();
		});
	}
}
