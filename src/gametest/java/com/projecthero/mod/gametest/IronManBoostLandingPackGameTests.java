package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.HulkbusterLanding;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManLandingSlam;
import com.projecthero.mod.ironman.RepulsorBoots;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.IronManComicRouter;
import com.projecthero.mod.ironman.ability.IronManSpaceBoost;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.suit.ThrusterPackFlight;
import com.projecthero.mod.item.ModDataComponents;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.16.7 (user): the Mk VIII Space Flight Boost ("doubled flight speed while player is sprint flying, it automatically
 * disables itself after players stop sprint flying, it makes flight cost 2.5x as much energy"), the Hulkbuster's landing
 * animation trigger, and the Mk VIII Thruster Pack's carried slow flight.
 */
public class IronManBoostLandingPackGameTests implements FabricGameTest {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };

	private static ServerPlayer tony(GameTestHelper h, String suit) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		BlockPos at = h.absolutePos(new BlockPos(3, 2, 3));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setOnGround(true);
		if (suit != null) {
			for (int i = 0; i < 4; i++) {
				p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(suit, TYPES[i])));
			}
			TonyStark.setActiveSuit(p, suit);
			IronManEnergy.setIntegrity(p, suit, IronManEnergy.maxIntegrity(suit));
			IronManEnergy.setEnergy(p, suit, IronManEnergy.capacity(suit));
		}
		return p;
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.05f;
	}

	// ------------------------------------------------------------------ Flight Boost

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightBoostReplacesBoosterRocketsOnX(GameTestHelper h) {
		h.assertTrue(IronManComicKit.byId("mk7_booster_rockets") == null, "Booster Rockets are gone");
		h.assertTrue(IronManSpaceBoost.MOVE.equals(IronManComicKit.move("mark_vii", 3, false).id()), "X tap = Flight Boost");
		h.assertTrue("mk7_meteor_dive".equals(IronManComicKit.move("mark_vii", 3, true).id()), "Shift+X still Meteor Dive");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightBoostDoublesSpeedOnlyWhileSprintFlying(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_vii");
		IronManSuit suit = IronManSuits.MARK_VII;
		// armed on the ground: waits, no speed change
		h.assertTrue(IronManComicRouter.fireForTests(p, suit, 3, false), "X arms the boost");
		h.assertTrue(IronManSpaceBoost.armed(TonyStark.state(p), "mark_vii"), "armed");
		IronManSpaceBoost.tick(p);
		h.assertTrue(IronManSpaceBoost.armed(TonyStark.state(p), "mark_vii"), "still armed while not sprint-flying");
		h.assertFalse(IronManSpaceBoost.engagedServer(p), "not engaged on the ground");
		// flying, not sprinting: x1
		p.setOnGround(false);
		IronManFlight.setFlying(p, true);
		p.setSprinting(false);
		IronManSpaceBoost.tick(p);
		h.assertTrue(IronManSpaceBoost.speedMultiplier(TonyStark.state(p), "mark_vii", true, false) == 1.0, "x1 flying slowly");
		h.assertTrue(IronManSpaceBoost.armed(TonyStark.state(p), "mark_vii"), "armed, waiting for sprint flight");
		// sprint-flying: engaged, x2 -- and only on the Mk VIII
		p.setSprinting(true);
		IronManSpaceBoost.tick(p);
		h.assertTrue(IronManSpaceBoost.engagedServer(p), "engaged sprint-flying");
		h.assertTrue(IronManSpaceBoost.speedMultiplier(TonyStark.state(p), "mark_vii", true, true) == 2.0, "x2 sprint-flying");
		h.assertTrue(IronManSpaceBoost.boosting(p), "boosting (either-side check)");
		h.assertTrue(IronManSpaceBoost.speedMultiplier(TonyStark.state(p), "mark_6", true, true) == 1.0, "never another suit");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightBoostSwitchesItselfOffWhenSprintFlightStops(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_vii");
		IronManSuit suit = IronManSuits.MARK_VII;
		p.setOnGround(false);
		IronManFlight.setFlying(p, true);
		p.setSprinting(true);
		h.assertTrue(IronManComicRouter.fireForTests(p, suit, 3, false), "X while sprint-flying");
		IronManSpaceBoost.tick(p);
		h.assertTrue(IronManSpaceBoost.engagedServer(p), "engaged at once");
		p.setSprinting(false);
		IronManSpaceBoost.tick(p);
		h.assertFalse(IronManSpaceBoost.armed(TonyStark.state(p), "mark_vii"), "off as soon as sprint flight stops");
		p.setSprinting(true);
		IronManSpaceBoost.tick(p);
		h.assertFalse(IronManSpaceBoost.engagedServer(p), "and it stays off");
		// X again while armed switches it off by hand (free)
		h.runAfterDelay(25, () -> h.assertTrue(IronManComicRouter.fireForTests(p, suit, 3, false), "re-armed"));
		h.runAfterDelay(50, () -> {
			IronManComicRouter.fireForTests(p, suit, 3, false);
			h.assertFalse(IronManSpaceBoost.armed(TonyStark.state(p), "mark_vii"), "X again switches it off");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightBoostDrainsTwoAndAHalfTimes(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_vii");
		IronManSuit suit = IronManSuits.MARK_VII;
		p.setOnGround(false);
		IronManFlight.setFlying(p, true);
		p.setSprinting(true);
		float start = IronManEnergy.energy(p, "mark_vii");
		for (int i = 0; i < 20; i++) {
			IronManEnergy.flightDrainTick(p, suit);
		}
		float plain = start - IronManEnergy.energy(p, "mark_vii");
		h.assertTrue(near(plain, IronManEnergy.ERA_ONE_SPRINT_FLIGHT_PER_SECOND), "plain sprint flight: 4/s (" + plain + ")");
		IronManComicRouter.fireForTests(p, suit, 3, false);
		IronManSpaceBoost.tick(p);
		float mid = IronManEnergy.energy(p, "mark_vii");
		for (int i = 0; i < 20; i++) {
			IronManEnergy.flightDrainTick(p, suit);
		}
		float boosted = mid - IronManEnergy.energy(p, "mark_vii");
		h.assertTrue(near(boosted, plain * 2.5f), "boosted: 2.5x = 10/s (" + boosted + ")");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightBoostCutsOutOnBrownout(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_vii");
		IronManSuit suit = IronManSuits.MARK_VII;
		p.setOnGround(false);
		IronManFlight.setFlying(p, true);
		p.setSprinting(true);
		IronManComicRouter.fireForTests(p, suit, 3, false);
		IronManSpaceBoost.tick(p);
		h.assertTrue(IronManSpaceBoost.engagedServer(p), "engaged");
		IronManEnergy.setEnergy(p, "mark_vii", 0f);
		IronManSpaceBoost.tick(p);
		h.assertTrue(IronManEnergy.brownedOut(p, "mark_vii"), "browned out");
		h.assertFalse(IronManSpaceBoost.armed(TonyStark.state(p), "mark_vii"), "the boost is off at 0 energy");
		h.assertFalse(IronManComicRouter.fireForTests(p, suit, 3, false), "and cannot be armed browned out");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ Hulkbuster landing

	@GameTest(template = EMPTY_STRUCTURE)
	public void hulkbusterHardTouchdownPlaysTheHeavyLanding(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_8");
		h.assertTrue(HulkbusterLanding.suited(p), "full Hulkbuster");
		p.setOnGround(false);
		double x = p.getX(), y = p.getY(), z = p.getZ();
		p.teleportTo(x, y + 6, z);
		IronManFlight.setFlying(p, true);
		h.runAfterDelay(12, () -> { // past the take-off grace: touching down lands the flight
			h.assertTrue(HulkbusterLanding.last(p) == null, "nothing before the touchdown");
			// a steep dive in the last ticks: about a block a tick down (the server's own motion history)
			for (int i = 5; i >= 0; i--) {
				p.teleportTo(x, y + i, z + (5 - i) * 0.6);
				IronManLandingSlam.recordFlightTick(p);
			}
			p.setOnGround(true);
			IronManFlight.tick(p);
			h.assertFalse(IronManFlight.isFlying(p), "the flight ended on the ground");
			HulkbusterLanding.Landing l = HulkbusterLanding.last(p);
			h.assertTrue(l != null, "the landing fired on touchdown");
			h.assertTrue(l.heavy() && l.intensity() > 0.5f, "a hard dive -> the heavy superhero landing (" + l.intensity() + ")");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hulkbusterGentleTouchdownPlaysTheLightLanding(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_8");
		p.setOnGround(false);
		double x = p.getX(), y = p.getY(), z = p.getZ();
		p.teleportTo(x, y + 0.5, z);
		IronManFlight.setFlying(p, true);
		h.runAfterDelay(12, () -> {
			for (int i = 4; i >= 0; i--) {
				p.teleportTo(x, y + i * 0.1, z);
				IronManLandingSlam.recordFlightTick(p);
			}
			p.setOnGround(true);
			IronManFlight.tick(p);
			HulkbusterLanding.Landing l = HulkbusterLanding.last(p);
			h.assertTrue(l != null && !l.heavy(), "a hover touchdown -> the light landing");
			h.assertTrue(l.intensity() < 0.2f, "low intensity (" + l.intensity() + ")");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hulkbusterBigFallLandsAndOtherSuitsDoNot(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_8");
		double y = p.getY();
		p.setOnGround(false);
		p.teleportTo(p.getX(), y + 9, p.getZ());
		HulkbusterLanding.tick(p);
		p.teleportTo(p.getX(), y, p.getZ());
		p.setOnGround(true);
		HulkbusterLanding.tick(p);
		HulkbusterLanding.Landing l = HulkbusterLanding.last(p);
		h.assertTrue(l != null && l.heavy(), "a 9-block fall -> the heavy landing");
		ServerPlayer q = tony(h, "mark_vii");
		q.setOnGround(false);
		q.teleportTo(q.getX(), y + 9, q.getZ());
		HulkbusterLanding.tick(q);
		q.teleportTo(q.getX(), y, q.getZ());
		q.setOnGround(true);
		HulkbusterLanding.tick(q);
		h.assertTrue(HulkbusterLanding.last(q) == null, "only the Hulkbuster");
		leave(h, p);
		leave(h, q);
		h.succeed();
	}

	// ------------------------------------------------------------------ Thruster Pack flight

	private static ItemStack pack(float charge) {
		ItemStack st = new ItemStack(IronManItems.SPACE_THRUSTER_PACK);
		st.set(ModDataComponents.SUIT_ENERGY, charge);
		return st;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thrusterPackFliesOnlyWhileCarried(GameTestHelper h) {
		ServerPlayer p = tony(h, null);
		p.setOnGround(false);
		h.assertFalse(RepulsorBoots.toggle(p), "no pack, no flight");
		p.getInventory().add(pack(100f));
		h.assertTrue(ThrusterPackFlight.available(p), "a carried charged pack");
		h.assertTrue(RepulsorBoots.toggle(p), "double-tap: takes off");
		h.assertTrue(RepulsorBoots.isFlying(p) && ThrusterPackFlight.packFlying(p), "pack flight on");
		h.assertTrue(p.getAbilities().flying, "creative-style flight (no fall damage)");
		// the pack leaves the inventory: it ends
		p.getInventory().clearContent();
		RepulsorBoots.tick(p);
		h.assertFalse(RepulsorBoots.isFlying(p), "pack gone -> flight over");
		// suited up: no pack flight
		p.getInventory().add(pack(100f));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(IronManItems.armor("mark_6", ArmorItem.Type.CHESTPLATE)));
		h.assertFalse(ThrusterPackFlight.available(p), "not with a suit on");
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		// no Tony Stark power: no flight (the pack's own rule)
		TonyStark.revoke(p);
		h.assertFalse(RepulsorBoots.toggle(p), "the pack answers only to Tony Stark");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void thrusterPackDrainsAndStopsAtZero(GameTestHelper h) {
		ServerPlayer p = tony(h, null);
		p.setOnGround(false);
		p.getInventory().add(pack(2f));
		h.assertTrue(RepulsorBoots.toggle(p), "takes off on 2 charge");
		for (int t = 1; t <= 70; t++) {
			h.runAfterDelay(t, () -> RepulsorBoots.tick(p));
		}
		h.runAfterDelay(25, () -> h.assertTrue(ThrusterPackFlight.charge(p) <= 1.01f, "1 charge a second"));
		h.runAfterDelay(75, () -> {
			h.assertTrue(ThrusterPackFlight.charge(p) <= 0f, "drained to 0");
			h.assertFalse(RepulsorBoots.isFlying(p), "and dropped out of the air");
			h.assertFalse(ThrusterPackFlight.available(p), "no take-off on an empty pack");
			h.assertTrue(p.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false) == false, "flag cleared");
			leave(h, p);
			h.succeed();
		});
	}
}
