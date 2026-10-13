package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.drone.IronManDroneEntity;
import com.projecthero.mod.ironman.drone.IronManDrones;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 agent E: Remote Pilot. Deploying moves the four real stacks out of the pack into the drone (no copy left
 * behind), closing the link flies them back into the pack, zero integrity drops them where the drone is with their
 * state, leaving the 96-block range drops the link, and the owner logging out puts the suit back on its platform.
 */
public class IronManDroneV01429GameTests implements FabricGameTest {
	private static final String SUIT = "mark_2";
	private static final ArmorItem.Type[] ALL = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer tony(GameTestHelper helper, BlockPos rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		StarkGlassesV0151GameTests.wearGlasses(p); // v0.15.1: calling a suit needs the Stark Glasses
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(rel));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static ItemStack piece(ArmorItem.Type t, float energy, float integrity) {
		ItemStack s = new ItemStack(IronManItems.armor(SUIT, t));
		IronManEnergy.stampStack(s, energy, integrity);
		return s;
	}

	private static void giveSuit(ServerPlayer p, float energy, float integrity) {
		for (ArmorItem.Type t : ALL) {
			p.getInventory().add(piece(t, energy, integrity));
		}
	}

	private static int suitPiecesInPack(ServerPlayer p) {
		int n = 0;
		for (ItemStack s : p.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && a.suitId().equals(SUIT)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static List<ItemEntity> droppedPieces(GameTestHelper helper, Vec3 around) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(around, around).inflate(6),
				e -> e.getItem().getItem() instanceof IronManArmorItem a && a.suitId().equals(SUIT));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void deployMovesTheStacksIntoTheDrone(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(2, 2, 2));
		giveSuit(p, 500f, 700f);
		IronManDroneEntity d = IronManDrones.deploy(p, SUIT, IronManSuitListPayload.SOURCE_INVENTORY);
		helper.assertTrue(d != null, "a full charged suit in the pack deploys as a drone");
		helper.assertTrue(suitPiecesInPack(p) == 0, "every piece left the pack (" + suitPiecesInPack(p) + " still there)");
		helper.assertTrue(d.pieceCount() == 4, "the drone carries all four pieces");
		helper.assertTrue(Math.abs(d.energy() - 500f) < 0.01f && Math.abs(d.integrity() - 700f) < 0.01f,
				"the drone carries the suit's energy / integrity (" + d.energy() + " / " + d.integrity() + ")");
		helper.assertTrue(IronManDrones.linkedDrone(p) == d, "the link is open");
		helper.assertTrue(IronManDrones.deploy(p, SUIT, IronManSuitListPayload.SOURCE_INVENTORY) == null,
				"a second deploy is refused");
		// nothing to pilot: an empty or uncharged suit is refused and stays in the pack
		ServerPlayer q = tony(helper, new BlockPos(5, 2, 5));
		giveSuit(q, 0f, 700f);
		helper.assertTrue(IronManDrones.deploy(q, SUIT, IronManSuitListPayload.SOURCE_INVENTORY) == null,
				"a suit with no charge cannot be piloted");
		helper.assertTrue(suitPiecesInPack(q) == 4, "a refused deploy takes nothing");
		d.dock(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void endingTheLinkFliesTheSuitBackIntoThePack(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(2, 2, 2));
		giveSuit(p, 500f, 700f);
		IronManDroneEntity d = IronManDrones.deploy(p, SUIT, IronManSuitListPayload.SOURCE_INVENTORY);
		helper.assertTrue(d != null, "deployed");
		d.setPos(d.getX(), d.getY() + 3, d.getZ()); // a few blocks out, so it has to fly home
		IronManDrones.endLink(p, null);
		helper.assertTrue(IronManDrones.linkedDrone(p) == null, "the link is closed");
		helper.assertTrue(d.state() == IronManDroneEntity.RETURNING, "the drone heads home");
		helper.succeedWhen(() -> {
			helper.assertTrue(d.isRemoved(), "the drone docks");
			helper.assertTrue(suitPiecesInPack(p) == 4, "all four pieces are back in the pack (" + suitPiecesInPack(p) + ")");
			for (ItemStack s : p.getInventory().items) {
				if (s.getItem() instanceof IronManArmorItem) {
					float e = IronManEnergy.stackEnergy(s, SUIT);
					helper.assertTrue(e > 400f && e <= 500f, "the piece keeps its (slightly drained) charge, got " + e);
					helper.assertTrue(IronManEnergy.stackIntegrity(s, SUIT) == 700f, "the piece keeps its integrity");
				}
			}
			helper.assertTrue(IronManDrones.droneOf(p) == null, "no drone left on record");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroIntegrityDropsThePiecesWithTheirState(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(2, 2, 2));
		giveSuit(p, 500f, 700f);
		IronManDroneEntity d = IronManDrones.deploy(p, SUIT, IronManSuitListPayload.SOURCE_INVENTORY);
		helper.assertTrue(d != null, "deployed");
		Vec3 at = d.position();
		d.hurt(helper.getLevel().damageSources().generic(), 300f);
		helper.assertTrue(!d.isRemoved() && Math.abs(d.integrity() - 100f) < 0.01f,
				"300 damage = 600 integrity off (2 per point), got " + d.integrity());
		d.hurt(helper.getLevel().damageSources().generic(), 100f);
		helper.assertTrue(d.isRemoved(), "at zero integrity the drone is gone");
		List<ItemEntity> drops = droppedPieces(helper, at);
		int n = 0;
		for (ItemEntity e : drops) {
			n += e.getItem().getCount();
			helper.assertTrue(IronManEnergy.stackIntegrity(e.getItem(), SUIT) == 0f, "dropped piece shows 0 integrity");
			helper.assertTrue(Math.abs(IronManEnergy.stackEnergy(e.getItem(), SUIT) - 500f) < 1f, "dropped piece keeps its charge");
		}
		helper.assertTrue(n == 4, "all four pieces dropped (" + n + ")");
		helper.assertTrue(suitPiecesInPack(p) == 0, "no copy appeared in the pack");
		helper.assertTrue(IronManDrones.droneOf(p) == null && IronManDrones.linkedDrone(p) == null, "the link is gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void leavingTheRangeEndsTheLink(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(2, 2, 2));
		giveSuit(p, 500f, 700f);
		IronManDroneEntity d = IronManDrones.deploy(p, SUIT, IronManSuitListPayload.SOURCE_INVENTORY);
		helper.assertTrue(d != null, "deployed");
		// straight up past the 96-block range (same chunk column, so it keeps ticking)
		d.setPos(d.getX(), p.getY() + IronManDrones.MAX_RANGE + 2, d.getZ());
		helper.succeedWhen(() -> {
			helper.assertTrue(IronManDrones.linkedDrone(p) == null, "out of range: the link drops");
			helper.assertTrue(d.isRemoved() || d.state() == IronManDroneEntity.RETURNING, "and the suit heads home");
			if (!d.isRemoved()) {
				d.dock(p); // don't make the test wait for the long flight down
			}
			helper.assertTrue(suitPiecesInPack(p) == 4, "the pieces come back");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ownerHurtEndsTheLink(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(2, 2, 2));
		giveSuit(p, 500f, 700f);
		IronManDroneEntity d = IronManDrones.deploy(p, SUIT, IronManSuitListPayload.SOURCE_INVENTORY);
		helper.assertTrue(d != null, "deployed");
		p.hurt(p.damageSources().fellOutOfWorld(), 1f); // bypasses the mock player's creative invulnerability
		helper.assertTrue(IronManDrones.linkedDrone(p) == null, "a hit on the pilot's body drops the link");
		helper.assertTrue(d.state() == IronManDroneEntity.RETURNING, "the suit flies home");
		d.dock(p);
		helper.assertTrue(suitPiecesInPack(p) == 4, "docked back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ownerLogoutReturnsThePiecesToTheirPlatform(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(2, 2, 2));
		BlockPos rel = new BlockPos(5, 2, 2);
		helper.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) helper.getBlockEntity(rel);
		be.bindTo(p.getUUID());
		for (ArmorItem.Type t : ALL) {
			helper.assertTrue(be.store(piece(t, 400f, 600f)), "platform takes " + t);
		}
		IronManDroneEntity d = IronManDrones.deploy(p, SUIT, IronManSuitListPayload.SOURCE_PLATFORM);
		helper.assertTrue(d != null, "a full charged suit on the player's platform deploys");
		helper.assertTrue(be.isEmptyPlatform(), "the pieces left the platform");
		helper.assertTrue(d.pieceCount() == 4 && d.home() != null, "the drone carries them and remembers its platform");
		IronManDrones.onDisconnect(p);
		helper.assertTrue(d.isRemoved(), "the drone is gone once its owner logs out");
		helper.assertTrue(be.isFull() && SUIT.equals(be.storedSuitId()), "all four pieces are back on the platform");
		helper.assertTrue(Math.abs(be.suitEnergy() - 400f) < 1f && Math.abs(be.suitIntegrity() - 600f) < 1f,
				"with their state (" + be.suitEnergy() + " / " + be.suitIntegrity() + ")");

		// from the pack with no platform anywhere: the pieces drop where the drone was, none lost
		ServerPlayer q = tony(helper, new BlockPos(1, 2, 6));
		giveSuit(q, 500f, 700f);
		IronManDroneEntity d2 = IronManDrones.deploy(q, SUIT, IronManSuitListPayload.SOURCE_INVENTORY);
		helper.assertTrue(d2 != null, "deployed from the pack");
		Vec3 at = d2.position();
		IronManDrones.onDisconnect(q);
		int n = 0;
		for (ItemEntity e : droppedPieces(helper, at)) {
			n += e.getItem().getCount();
		}
		helper.assertTrue(d2.isRemoved() && n == 4, "with no platform the four pieces drop (" + n + ")");
		helper.succeed();
	}
}
