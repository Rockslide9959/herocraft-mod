package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ironman.HulkbusterShield;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.gantry.StarkGantry;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.StarkGantryMenuPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Hulkbuster fixes: the suit takes every hit instead of its pilot (Titan Shifter style) and is wrecked at
 * 0 -- the pilot thrown out of the back, the suit left as a wrecked Sentry on a reboot cooldown; the Stark Gantry refuses
 * the Hulkbuster; a Suit Platform hands the racked Hulkbuster over as its Uplink and racks an Uplink again. (The Sentry
 * climb-out / climb-in is in {@link IronManSentryV01515GameTests}.)
 */
public class IronManHulkbusterFixesGameTests implements FabricGameTest {
	private static final String SUIT = "mark_8";
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };

	private static void floor(GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	private static ServerPlayer tony(GameTestHelper h, BlockPos rel) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(rel));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static void wear(ServerPlayer p, float integrity) {
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(SUIT, TYPES[i])));
		}
		TonyStark.setActiveSuit(p, SUIT);
		IronManEnergy.setIntegrity(p, SUIT, integrity);
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		for (IronManSentryEntity s : h.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(30),
				s -> p.getUUID().equals(s.ownerId()))) {
			s.discard();
		}
		HulkbusterShield.forget(p.getUUID());
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h, ServerPlayer owner, BlockPos rel, String suit, float integrity) {
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
		be.bindTo(owner.getUUID());
		if (suit != null) {
			for (ArmorItem.Type t : TYPES) {
				ItemStack piece = new ItemStack(IronManItems.armor(suit, t));
				IronManEnergy.stampStack(piece, IronManEnergy.FULL, integrity);
				h.assertTrue(be.store(piece), "racked " + suit + " " + t.getName());
			}
		}
		return be;
	}

	// ------------------------------------------------------------------ the suit takes the hits

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHulkbusterTakesEveryHitNotItsPilot(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(2, 2, 2));
		wear(p, 3000f);
		float hp = p.getHealth();
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().generic(), 20f), "the hit never reaches the pilot");
		h.assertTrue(Math.abs(IronManEnergy.integrity(p, SUIT) - 2980f) < 1e-3f,
				"the suit takes all 20, 1:1 (" + IronManEnergy.integrity(p, SUIT) + ")");
		h.assertTrue(p.getHealth() == hp, "the pilot is untouched");
		// a second, smaller hit inside the i-frames is shrugged off; a bigger one only lands what it exceeds the first by
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().generic(), 8f), "a weaker hit right after");
		h.assertTrue(Math.abs(IronManEnergy.integrity(p, SUIT) - 2980f) < 1e-3f, "costs nothing (vanilla i-frames)");
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().generic(), 30f), "a stronger one");
		h.assertTrue(Math.abs(IronManEnergy.integrity(p, SUIT) - 2970f) < 1e-3f, "lands only the extra 10");
		// sealed in: drowning / suffocation never reach the pilot and don't wear the suit
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().drown(), 4f), "no drowning in the Hulkbuster");
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().inWall(), 4f), "no suffocating either");
		h.assertTrue(Math.abs(IronManEnergy.integrity(p, SUIT) - 2970f) < 1e-3f, "and the suit loses nothing to them");
		h.assertTrue(IronManSuits.MARK_8.absorbShare() == 1f, "the spec says 100%");
		// a Hulkbuster at 0 (stepped back into a rebooted wreck) protects nothing: the hit lands as usual
		HulkbusterShield.forget(p.getUUID());
		IronManEnergy.setIntegrity(p, SUIT, 0f);
		h.assertFalse(HulkbusterShield.shielding(p, IronManSuits.MARK_8), "not shielding at 0");
		h.assertTrue(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().generic(), 5f), "at 0 the hit goes through");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void zeroIntegrityWrecksItAndThrowsThePilotOutOfTheBack(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(3, 2, 3));
		wear(p, 15f);
		float hp = p.getHealth();
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().generic(), 40f), "the killing blow is the suit's too");
		h.assertTrue(p.getHealth() == hp, "the pilot takes none of it");
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "ejected: nothing on");
		List<IronManSentryEntity> found = h.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(6),
				s -> p.getUUID().equals(s.ownerId()));
		h.assertTrue(found.size() == 1, "the suit is left behind as a Sentry (" + found.size() + ")");
		IronManSentryEntity s = found.get(0);
		h.assertTrue(s.wrecked() && s.integrity() == 0f && s.pieceCount() == 4, "wrecked, 0 integrity, all four pieces kept");
		h.assertTrue(s.isOpen(), "its back blown open");
		h.assertTrue(s.wreckTicksLeft() > IronManSentryEntity.WRECK_COOLDOWN_TICKS - 5, "on its reboot cooldown (" + s.wreckTicksLeft() + ")");
		Vec3 fwd = Vec3.directionFromRotation(0f, s.getYRot());
		double along = p.position().subtract(s.position()).dot(fwd);
		h.assertTrue(along < -0.5, "thrown out of the BACK (" + along + ")");
		h.assertTrue(p.getDeltaMovement().dot(fwd) < -0.2 && p.getDeltaMovement().y > 0.1, "flung back and up (" + p.getDeltaMovement() + ")");
		h.assertFalse(s.use(p, true), "no stepping back into a wreck before it reboots");
		h.assertTrue(s.phase() == IronManSentryEntity.PHASE_IDLE, "nothing started");
		// the cooldown survives a save / load
		CompoundTag tag = new CompoundTag();
		s.saveWithoutId(tag);
		IronManSentryEntity copy = new IronManSentryEntity(IronManEntityTypes.SENTRY, h.getLevel());
		copy.load(tag);
		h.assertTrue(copy.wrecked() && copy.wreckTicksLeft() > 0, "still wrecked after a reload");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ no Gantry

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void theGantryRefusesTheHulkbuster(GameTestHelper h) {
		BlockPos centre = new BlockPos(2, 1, 2);
		IronManGantryV0154GameTests.floor(h, centre);
		ServerPlayer p = tony(h, new BlockPos(2, 2, 2));
		IronManGantryV0154GameTests.standOn(h, p, centre);
		IronManSuitPlatformBlockEntity hb = platform(h, p, new BlockPos(6, 2, 6), SUIT, 4500f);
		platform(h, p, new BlockPos(6, 2, 2), "mark_vii", 2600f);
		h.assertFalse(StarkGantry.fits(SUIT), "the Hulkbuster doesn't fit the gantry");
		h.assertTrue(StarkGantry.fits("mark_vii"), "the other suits do");
		List<StarkGantryMenuPayload.Entry> list = StarkGantry.suitsFor(h.getLevel(), h.absolutePos(centre), p, null);
		h.assertTrue(list.stream().anyMatch(e -> e.suitId().equals("mark_vii")), "the Mk VII is listed");
		h.assertTrue(list.stream().noneMatch(e -> e.suitId().equals(SUIT)), "the racked Hulkbuster is not (" + list + ")");
		h.assertFalse(StarkGantry.beginEquip(p, hb.getBlockPos()), "picking it anyway is refused");
		h.assertTrue(hb.isFull() && !IronManArmor.wearingAnyIronMan(p), "and nothing moved");
		// worn, it can't be taken off (or swapped) on the gantry either
		wear(p, 4000f);
		h.assertFalse(StarkGantry.beginUnequip(p), "no gantry removal of the Hulkbuster");
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "still in it");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ the platform's Uplink

	@GameTest(template = EMPTY_STRUCTURE)
	public void thePlatformHandsTheHulkbusterOverAsItsUplinkAndRacksItAgain(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(1, 2, 1));
		IronManSuitPlatformBlockEntity be = platform(h, p, new BlockPos(3, 2, 3), SUIT, 3100f);
		h.assertTrue(be.holdsHulkbuster(), "the whole Hulkbuster is racked");
		h.assertTrue(be.packUplink(p), "the Uplink tab");
		h.assertTrue(be.isEmptyPlatform(), "the rack is empty");
		ItemStack uplink = ItemStack.EMPTY;
		for (ItemStack st : p.getInventory().items) {
			if (st.is(IronManItems.HULKBUSTER_UPLINK)) {
				uplink = st;
			}
		}
		h.assertTrue(!uplink.isEmpty() && SuitcaseContents.nonEmpty(uplink).size() == 4, "an Uplink holding all four pieces");
		h.assertTrue(Math.abs(IronManEnergy.stackIntegrity(uplink, SUIT) - 3100f) < 1f, "with its integrity");
		h.assertFalse(be.packUplink(p), "nothing left to pack");
		// and back onto the rack: the Uplink stays, emptied (it can't call the crate while the suit is racked)
		h.assertTrue(be.storeUplink(p, uplink), "an Uplink handed to the platform racks the Hulkbuster");
		h.assertTrue(be.isFull() && SUIT.equals(be.storedSuitId()), "all four pieces on the rack");
		h.assertTrue(Math.abs(be.suitIntegrity() - 3100f) < 1f, "integrity kept (" + be.suitIntegrity() + ")");
		h.assertTrue(SuitcaseContents.isEmpty(uplink) && !SuitcaseContents.isLegacyEmpty(uplink), "the Uplink is emptied, not fresh");
		h.assertFalse(be.storeUplink(p, uplink), "an emptied Uplink racks nothing");
		// a never-used Uplink racks a fresh suit
		IronManSuitPlatformBlockEntity other = platform(h, p, new BlockPos(5, 2, 5), null, 0f);
		ItemStack fresh = new ItemStack(IronManItems.HULKBUSTER_UPLINK);
		h.assertTrue(other.storeUplink(p, fresh) && other.isFull()
				&& Math.abs(other.suitIntegrity() - IronManEnergy.maxIntegrity(SUIT)) < 1f, "a fresh Uplink racks a fresh Hulkbuster");
		// it won't go onto a rack holding another suit
		IronManSuitPlatformBlockEntity mk7 = platform(h, p, new BlockPos(6, 2, 1), "mark_vii", 2600f);
		ItemStack loaded = new ItemStack(IronManItems.HULKBUSTER_UPLINK);
		NonNullList<ItemStack> slots = NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int bit = 0; bit < 4; bit++) {
			slots.set(bit, new ItemStack(IronManItems.armor(SUIT, IronManSuitUpManager.typeOf(SLOTS[bit]))));
		}
		SuitcaseContents.write(loaded, slots);
		h.assertFalse(mk7.storeUplink(p, loaded), "not onto a Mk VII's rack");
		h.assertTrue(SuitcaseContents.nonEmpty(loaded).size() == 4, "and the Uplink keeps the suit");
		leave(h, p);
		h.succeed();
	}
}
