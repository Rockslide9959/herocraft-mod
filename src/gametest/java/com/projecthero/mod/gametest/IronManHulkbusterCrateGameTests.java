package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.crate.HulkbusterCrate;
import com.projecthero.mod.ironman.crate.HulkbusterCrateEntity;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Iron Man Era II stage 2: the Hulkbuster I drop crate -- C calls it down (and it refuses under a roof),
 * walking into the suit's open back equips the Hulkbuster, and the Shift+C fold puts it back in orbit (the Uplink) with
 * its integrity.
 */
public class IronManHulkbusterCrateGameTests implements FabricGameTest {
	private static final String SUIT = HulkbusterCrate.SUIT_ID;
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	/** Ticks from the call to the crate standing open. */
	private static final int OPEN_AT = HulkbusterCrateEntity.DROP_TICKS + HulkbusterCrateEntity.IMPACT_TICKS
			+ HulkbusterCrateEntity.OPEN_TICKS + 3;

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
		p.setOnGround(true);
		return p;
	}

	private static ItemStack uplink(float integrity) {
		ItemStack u = new ItemStack(IronManItems.HULKBUSTER_UPLINK);
		NonNullList<ItemStack> slots = NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int bit = 0; bit < 4; bit++) {
			slots.set(bit, new ItemStack(IronManItems.armor(SUIT, TYPES[bit])));
		}
		SuitcaseContents.write(u, slots);
		IronManEnergy.stampStack(u, 0f, integrity);
		return u;
	}

	private static Vec3 centre(GameTestHelper h) {
		return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 2, 4)));
	}

	private static HulkbusterCrateEntity crate(GameTestHelper h, ServerPlayer p) {
		List<HulkbusterCrateEntity> found = h.getLevel().getEntities(IronManEntityTypes.HULKBUSTER_CRATE,
				new AABB(p.position(), p.position()).inflate(40), c -> p.getUUID().equals(c.ownerId()));
		return found.isEmpty() ? null : found.get(0);
	}

	private static void cleanup(GameTestHelper h, ServerPlayer p) {
		for (HulkbusterCrateEntity c : h.getLevel().getEntities(IronManEntityTypes.HULKBUSTER_CRATE, new AABB(p.position(), p.position()).inflate(40),
				c -> p.getUUID().equals(c.ownerId()))) { // only this test's: the batch runs side by side
			c.discard();
		}
		for (IronManSentryEntity s : h.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(40),
				s -> p.getUUID().equals(s.ownerId()))) {
			s.discard();
		}
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void registeredAsABlockForm(GameTestHelper h) {
		PortableForm f = PortableForms.byId(SUIT);
		h.assertTrue(f == PortableForms.HULKBUSTER_CRATE && PortableForms.byItem(IronManItems.HULKBUSTER_UPLINK) == f,
				"the Hulkbuster is registered by suit and by its Uplink");
		h.assertTrue(f.rest() == PortableForm.Place.BLOCK, "a block-like (crate) form");
		// the pure pose: closed in the drop, flat once open, the lid back; the drop ends on the ground
		h.assertTrue(HulkbusterCrateEntity.wallOpen(0, HulkbusterCrateEntity.P_DROP, 5f) == 0f
				&& HulkbusterCrateEntity.wallOpen(0, HulkbusterCrateEntity.P_OPEN, 0f) == 1f, "walls shut, then flat");
		h.assertTrue(HulkbusterCrateEntity.height(HulkbusterCrateEntity.P_DROP, HulkbusterCrateEntity.DROP_TICKS, 64f) == 0f
				&& HulkbusterCrateEntity.height(HulkbusterCrateEntity.P_DROP, 0f, 64f) == 64f, "it falls 64 blocks onto the spot");
		float prev = 0f;
		for (int t = 0; t <= HulkbusterCrateEntity.CLOSE_TICKS; t++) {
			float w = HulkbusterCrateEntity.wallOpen(0, HulkbusterCrateEntity.P_CLOSING, t);
			h.assertTrue(t == 0 || w <= prev + 1e-4f, "the front wall only ever rises while closing (" + t + ")");
			prev = w;
		}
		h.assertTrue(prev == 0f, "shut at the end of the close");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 200)
	public void callLandsTheCrateAndTheSuitStandsOpenInIt(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(0, 2, 0));
		p.getInventory().items.set(0, new ItemStack(IronManItems.HULKBUSTER_UPLINK)); // fresh: a whole new suit
		h.assertTrue(HulkbusterCrate.callTo(p, centre(h)), "the call is accepted");
		ItemStack up = p.getInventory().items.get(0);
		h.assertTrue(up.is(IronManItems.HULKBUSTER_UPLINK) && !SuitcaseContents.isLegacyEmpty(up) && SuitcaseContents.isEmpty(up),
				"the Uplink stays, emptied: the suit is in the crate");
		HulkbusterCrateEntity c = crate(h, p);
		h.assertTrue(c != null && c.phase() == HulkbusterCrateEntity.P_DROP && c.carrying(), "the crate is dropping with the suit");
		h.assertFalse(HulkbusterCrate.callTo(p, centre(h).add(1, 0, 0)), "only one crate at a time");
		h.runAfterDelay(OPEN_AT, () -> {
			HulkbusterCrateEntity cc = crate(h, p);
			h.assertTrue(cc != null && cc.waitingOpen(), "landed and open (" + (cc == null ? "gone" : cc.phase() + " @" + cc.phaseAge(0f)) + ")");
			IronManSentryEntity s = cc.sentry();
			h.assertTrue(s != null && s.isOpen() && s.crated() && s.pieceCount() == 4 && SUIT.equals(s.suitId()),
					"the Hulkbuster stands in it, back open");
			h.assertTrue(s.position().distanceTo(cc.position()) < 0.3, "at the crate's centre");
			// C again: it shuts and goes back up, and the suit is back in the Uplink
			h.assertTrue(HulkbusterCrate.recall(p), "C recalls it");
			h.succeedWhen(() -> {
				h.assertTrue(crate(h, p) == null, "the crate has lifted off");
				ItemStack back = p.getInventory().items.get(0);
				h.assertTrue(SuitcaseContents.nonEmpty(back).size() == 4, "the suit is back in the same Uplink");
				cleanup(h, p);
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void callRefusedUnderARoof(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(0, 2, 0));
		p.getInventory().items.set(0, new ItemStack(IronManItems.HULKBUSTER_UPLINK));
		h.setBlock(new BlockPos(4, 6, 4), Blocks.STONE); // a roof over the spot
		h.assertFalse(HulkbusterCrate.callTo(p, centre(h)), "no crate under a roof");
		h.assertTrue(crate(h, p) == null, "nothing spawned");
		h.assertTrue(SuitcaseContents.isLegacyEmpty(p.getInventory().items.get(0)), "the Uplink is untouched");
		h.setBlock(new BlockPos(4, 6, 4), Blocks.AIR);
		h.setBlock(new BlockPos(4, 3, 4), Blocks.STONE); // no room
		h.assertFalse(HulkbusterCrate.callTo(p, centre(h)), "no crate where it cannot stand");
		h.setBlock(new BlockPos(4, 3, 4), Blocks.AIR);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 400)
	public void walkingInEquipsTheHulkbusterWithItsIntegrity(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(0, 2, 0));
		p.getInventory().items.set(0, uplink(2222f));
		h.assertTrue(HulkbusterCrate.callTo(p, centre(h)), "called");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(crate(h, p) != null && crate(h, p).waitingOpen() && crate(h, p).sentry() != null
						&& crate(h, p).sentry().isOpen(), "open"))
				.thenExecute(() -> {
					HulkbusterCrateEntity c = crate(h, p);
					Vec3 behind = c.world(0, -1.3);
					p.teleportTo(h.getLevel(), behind.x, behind.y, behind.z, c.getYRot(), 0f);
				})
				.thenWaitUntil(() -> h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "walking into its back equips the Hulkbuster"))
				.thenExecute(() -> {
					h.assertTrue(Math.abs(IronManEnergy.integrity(p, SUIT) - 2222f) < 1f, "with the integrity it had in orbit");
					h.assertTrue(crate(h, p) != null, "the empty crate waits while you stand in it");
					Vec3 away = p.position().add(9, 0, 9);
					p.teleportTo(h.getLevel(), away.x, away.y, away.z, 0f, 0f);
				})
				.thenWaitUntil(() -> h.assertTrue(crate(h, p) == null, "once you are clear the empty crate lifts off"))
				.thenExecute(() -> {
					h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "and you keep the suit");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 500)
	public void foldStepsOutAndLiftsTheSuitBackToOrbit(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(4, 2, 4));
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(SUIT, t)));
		}
		TonyStark.setActiveSuit(p, SUIT);
		IronManEnergy.setIntegrity(p, SUIT, 1234f);
		ItemStack emptied = new ItemStack(IronManItems.HULKBUSTER_UPLINK);
		SuitcaseContents.write(emptied, NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY));
		p.getInventory().items.set(5, emptied);
		h.assertTrue(PortableDeploy.fold(p, SUIT), "Shift+C (the portable fold) brings the crate down round you");
		h.assertTrue(PortableDeploy.running(p), "held still, abilities locked, while it comes down");
		HulkbusterCrateEntity c = crate(h, p);
		h.assertTrue(c != null && c.phase() == HulkbusterCrateEntity.P_FOLD_DROP && !c.carrying(), "an open crate is coming down");
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "still wearing it while it lands");
		h.startSequence()
				.thenWaitUntil(() -> h.assertFalse(IronManArmor.wearingAnyIronMan(p), "stepped out of it"))
				.thenExecute(() -> h.assertFalse(PortableDeploy.running(p), "free again once out"))
				.thenWaitUntil(() -> h.assertTrue(crate(h, p) == null, "it shut and lifted off"))
				.thenExecute(() -> {
					ItemStack u = p.getInventory().items.get(5);
					h.assertTrue(u.is(IronManItems.HULKBUSTER_UPLINK) && SuitcaseContents.nonEmpty(u).size() == 4,
							"the suit is back in orbit: the emptied Uplink holds all four pieces (" + p.getInventory().items + ")");
					h.assertTrue(Math.abs(IronManEnergy.stackIntegrity(u, SUIT) - 1234f) < 1f, "with its integrity");
					int uplinks = 0;
					for (ItemStack s : p.getInventory().items) {
						uplinks += s.is(IronManItems.HULKBUSTER_UPLINK) ? 1 : 0;
					}
					h.assertTrue(uplinks == 1, "refilled, not duplicated");
					h.assertTrue(h.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(20),
							s -> p.getUUID().equals(s.ownerId())).isEmpty(), "no suit left standing");
					cleanup(h, p);
				})
				.thenSucceed();
	}
}
