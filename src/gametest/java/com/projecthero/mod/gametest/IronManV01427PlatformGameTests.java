package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManLandingSlam;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.27 agent A: flat 10/s Suit Platform regen with no reserve, fall immunity in any Iron Man armour, the flight
 * landing slam, and the Sneak+C send-back of carried pieces.
 */
public class IronManV01427PlatformGameTests implements FabricGameTest {
	private static final ArmorItem.Type[] ALL = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer tony(GameTestHelper helper, BlockPos rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(rel));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static IronManSuitPlatformBlockEntity platform(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		return (IronManSuitPlatformBlockEntity) helper.getBlockEntity(rel);
	}

	/** v0.15.20: the welding arms' synced flag is on exactly while a racked suit is below max integrity. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void platformRepairFlagFollowsIntegrity(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 2, 2);
		IronManSuitPlatformBlockEntity be = platform(helper, rel);
		BlockPos abs = helper.absolutePos(rel);
		var level = helper.getLevel();
		IronManSuitPlatformBlockEntity.serverTick(level, abs, be.getBlockState(), be);
		helper.assertFalse(be.repairing(), "an empty rack welds nothing");
		float maxI = IronManEnergy.maxIntegrity("mark_iii");
		for (ArmorItem.Type t : ALL) {
			ItemStack piece = new ItemStack(IronManItems.armor("mark_iii", t));
			IronManEnergy.stampStack(piece, 0f, maxI - 2f);
			be.store(piece);
		}
		IronManSuitPlatformBlockEntity.serverTick(level, abs, be.getBlockState(), be);
		helper.assertTrue(be.repairing(), "a damaged suit on the rack is being welded");
		for (int i = 0; i < 20; i++) {
			IronManSuitPlatformBlockEntity.serverTick(level, abs, be.getBlockState(), be);
		}
		helper.assertTrue(be.suitIntegrity() >= maxI - 0.01f, "2 integrity is repaired well within a second at 5/s");
		helper.assertFalse(be.repairing(), "the arms go down once the suit is whole");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void platformRegenIsFlatTenPerSecondForEveryMark(GameTestHelper helper) {
		for (String suitId : new String[] { "mark_1", "mark_iii" }) {
			BlockPos rel = new BlockPos(suitId.equals("mark_1") ? 1 : 3, 2, 1);
			IronManSuitPlatformBlockEntity be = platform(helper, rel);
			for (ArmorItem.Type t : ALL) {
				ItemStack piece = new ItemStack(IronManItems.armor(suitId, t));
				IronManEnergy.stampStack(piece, 0f, 0f);
				helper.assertTrue(be.store(piece), "platform must accept " + suitId + " " + t);
			}
			var suit = IronManSuits.byId(suitId);
			for (int i = 0; i < 20; i++) {
				be.regenTick(suit);
			}
			// v0.15.20: the suit's own passive energy rate + 5 integrity/s
			float rate = suit.energyRegenPerSecond();
			helper.assertTrue(Math.abs(be.suitEnergy() - rate) < 0.05f, suitId + ": 1 s on the rack = +" + rate + " energy, got " + be.suitEnergy());
			helper.assertTrue(Math.abs(be.suitIntegrity() - 5f) < 0.05f, suitId + ": 1 s on the rack = +5 integrity, got " + be.suitIntegrity());
			helper.assertTrue(IronManEnergy.platformEnergyPerSecond(suit) == rate && IronManEnergy.platformIntegrityPerSecond(suit) == 5f,
					suitId + ": per-mark platformRegen overrides no longer apply");
		}
		helper.assertTrue(platform(helper, new BlockPos(5, 2, 1)).data.getCount() == 3,
				"the platform menu no longer syncs a reserve value");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManArmourIsImmuneToFallDamage(GameTestHelper helper) {
		// Mark 1 (used to take 20% of a fall) -- boots + chest worn, a big fall does nothing and costs the suit nothing
		ServerPlayer p = tony(helper, new BlockPos(3, 2, 3));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(IronManItems.armor("mark_1", ArmorItem.Type.BOOTS)));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(IronManItems.armor("mark_1", ArmorItem.Type.CHESTPLATE)));
		IronManEnergy.setEnergy(p, "mark_1", 500f);
		IronManEnergy.setIntegrity(p, "mark_1", 100f);
		float e0 = IronManEnergy.energy(p, "mark_1");
		float i0 = IronManEnergy.integrity(p, "mark_1");
		float h0 = p.getHealth();
		helper.assertTrue(IronManDamage.fallImmune(p), "wearing Iron Man armour = fall immune");
		p.hurt(p.damageSources().fall(), 15f);
		helper.assertTrue(p.getHealth() == h0, "Mark 1 wearer takes no fall damage (health " + p.getHealth() + " vs " + h0 + ")");
		helper.assertTrue(IronManEnergy.energy(p, "mark_1") == e0, "a fall never drains suit energy");
		helper.assertTrue(IronManEnergy.integrity(p, "mark_1") == i0, "a fall never drains suit integrity");

		// a helmet alone counts too
		ServerPlayer h = tony(helper, new BlockPos(5, 2, 5));
		h.setItemSlot(EquipmentSlot.HEAD, new ItemStack(IronManItems.armor("mark_iii", ArmorItem.Type.HELMET)));
		float hh = h.getHealth();
		h.hurt(h.damageSources().fall(), 10f);
		helper.assertTrue(h.getHealth() == hh, "any Iron Man piece blocks fall damage");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void landingSlamHitsNearbyAndHasACooldown(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(1, 2, 1));
		IronGolem near = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 2, 1));
		IronGolem far = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(7, 2, 7));
		float nearHp = near.getHealth();
		float farHp = far.getHealth();
		Vec3 nearPos = near.position();
		helper.assertTrue(IronManLandingSlam.trySlam(p), "the first landing slams");
		helper.assertTrue(nearHp - near.getHealth() >= IronManLandingSlam.DAMAGE - 0.01f,
				"a golem 2 blocks away takes the 20 slam damage (took " + (nearHp - near.getHealth()) + ")");
		helper.assertTrue(far.getHealth() == farHp, "a golem well outside the radius is untouched");
		helper.assertTrue(p.getHealth() == p.getMaxHealth(), "the slam never hurts the wearer");
		helper.assertTrue(near.getDeltaMovement().x > 0.0 || near.position().x > nearPos.x,
				"the slam knocks targets outward (away from the landing point)");
		helper.assertFalse(IronManLandingSlam.trySlam(p), "a second landing right away is on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void sneakCSendsCarriedArmourHome(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 2, 1);
		IronManSuitPlatformBlockEntity be = platform(helper, rel);
		ServerPlayer p = tony(helper, new BlockPos(4, 2, 4));
		be.bindTo(p.getUUID());
		p.getInventory().add(new ItemStack(IronManItems.armor("mark_iii", ArmorItem.Type.HELMET)));
		p.getInventory().add(new ItemStack(IronManItems.armor("mark_iii", ArmorItem.Type.BOOTS)));

		boolean listed = IronManSuitCall.sendBackOptions(p).stream()
				.anyMatch(o -> o.suitId().equals("mark_iii") && o.source() == IronManSuitListPayload.SOURCE_SEND_BACK);
		helper.assertTrue(listed, "the Sneak+C picker lists carried Mark III pieces as send-back");

		IronManSuitCall.execute(p, "mark_iii", IronManSuitListPayload.SOURCE_SEND_BACK);
		boolean stillCarried = false;
		for (ItemStack s : p.getInventory().items) {
			if (s.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				stillCarried = true;
			}
		}
		helper.assertFalse(stillCarried, "the sent pieces leave the inventory");
		helper.assertTrue(IronManSuitCall.sendBackOptions(p).isEmpty(), "nothing left to send back");
		// v0.14.28: they build themselves into a standing suit 1 block in front of the player first...
		var home = helper.getLevel().getEntitiesOfClass(com.projecthero.mod.ironman.entity.IronManSuitPartEntity.class,
				p.getBoundingBox().inflate(4), com.projecthero.mod.ironman.entity.IronManSuitPartEntity::homeMode);
		helper.assertTrue(home.size() == 2, "two send-home pieces stand in front of the player, got " + home.size());
		for (var e : home) {
			double flat = Math.hypot(e.getX() - p.getX(), e.getZ() - p.getZ());
			helper.assertTrue(Math.abs(flat - IronManSuitCall.SEND_HOME_DISTANCE) < 0.05,
					"the suit stands 1 block in front of the player (" + flat + ")");
		}
		helper.assertFalse(be.holds("mark_iii", ArmorItem.Type.HELMET), "not docked yet -- it builds and flies first");
		// ...then fly home and dock on the platform
		helper.succeedWhen(() -> {
			helper.assertTrue(be.holds("mark_iii", ArmorItem.Type.HELMET) && be.holds("mark_iii", ArmorItem.Type.BOOTS),
					"the carried pieces end up docked on the platform");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(com.projecthero.mod.ironman.entity.IronManSuitPartEntity.class,
					p.getBoundingBox().inflate(30)).isEmpty(), "and the flying suit is gone");
			int armour = 0;
			for (ItemStack s : p.getInventory().items) {
				if (s.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
					armour++;
				}
			}
			helper.assertTrue(armour == 0, "nothing came back to the pack (no duplicate)");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void sendHomeToAnUnloadedPlatformGoesOnTheQueue(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(4, 2, 4));
		ItemStack boots = new ItemStack(IronManItems.armor("mark_iii", ArmorItem.Type.BOOTS));
		// a dock far away in a chunk nobody has loaded
		BlockPos far = helper.absolutePos(new BlockPos(4, 2, 4)).offset(3000, 0, 3000);
		var e = com.projecthero.mod.ironman.entity.IronManSuitPartEntity.spawnHome(helper.getLevel(),
				p.position().add(1, 0, 0), 0f, p, boots, 0, 1, far);
		var queue = com.projecthero.mod.ironman.data.StarkSuitReturnQueue.get(helper.getLevel());
		helper.succeedWhen(() -> {
			helper.assertTrue(e.isRemoved(), "the suit climbs out of sight and hands over");
			helper.assertTrue(queue.hasPendingFor(helper.getLevel(), far), "the piece waits on the return queue for its platform");
		});
	}
}
