package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.flight.FlightLanding;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManManualSuitUp;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitRemoval;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.19 (Iron Man / flight round): Marks 2-7 unbuilt off the body by hand (C), a worn suit dropping like ordinary
 * armour on death (keepInventory respected), and the shared "touching the floor ends the flight" rule
 * ({@link FlightLanding}) with its take-off grace and not-while-rising check.
 */
public class IronManV01519GameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private static ServerPlayer tony(GameTestHelper h, BlockPos rel) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos at = h.absolutePos(rel);
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	private static void wear(ServerPlayer p, String suit) {
		for (int bit = 0; bit < 4; bit++) {
			p.setItemSlot(SLOTS[bit], new ItemStack(IronManItems.armor(suit, TYPES[bit])));
		}
		TonyStark.setActiveSuit(p, suit);
		IronManEnergy.setEnergy(p, suit, IronManSuits.byId(suit).energyCapacity() * 0.5f);
		IronManEnergy.setIntegrity(p, suit, IronManEnergy.maxIntegrity(suit));
	}

	private static List<ItemEntity> droppedPieces(GameTestHelper h, ServerPlayer p, String suit) {
		AABB box = new AABB(p.blockPosition()).inflate(6.0);
		return h.getLevel().getEntitiesOfClass(ItemEntity.class, box,
				e -> e.isAlive() && e.getItem().getItem() instanceof IronManArmorItem a && a.suitId().equals(suit));
	}

	// ------------------------------------------------------------------ 1. Marks 2-7: unbuilt by hand

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aPartialMarkSevenComesOffByHandPieceByPiece(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(1, 1, 1));
		wear(p, "mark_vii");
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		h.assertTrue(IronManSuitUpManager.beginSuitDown(p, "mark_vii"), "C starts the removal");
		h.assertTrue(IronManSuitRemoval.running(p) && !IronManManualSuitUp.running(p), "a by-hand removal");
		IronManSuitFx fx = IronManSuitFx.of(p);
		h.assertTrue(fx.poseKind() == IronManSuitFx.POSE_WORKSHOP_OFF && IronManManualSuitUp.planOf(fx.poseVariant()) == 0b1001
				&& "mark_vii".equals(IronManManualSuitUp.suitOf(fx.poseVariant())),
				"the synced pose carries the suit and the two worn pieces");
		var sch = IronManSuitRemoval.schedule(IronManSuitRemoval.KIND_WORKSHOP, 0b1001);
		h.assertTrue(sch.offAt(1) < 0 && sch.offAt(2) < 0 && sch.offAt(0) > 0 && sch.offAt(3) > sch.offAt(0),
				"only the helmet and the boots are planned, helmet first");
		int ticks = 0;
		while (IronManSuitUpManager.inTransition(p) && ticks++ < 1000) {
			IronManSuitUpManager.tick(p);
		}
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "both pieces are off");
		int stored = 0;
		for (ItemStack s : p.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && a.suitId().equals("mark_vii")) {
				stored += s.getCount();
			}
		}
		h.assertTrue(stored == 2, "both pieces are in the pack (got " + stored + ")");
		h.succeed();
	}

	// ------------------------------------------------------------------ 2. death: the suit drops like armour

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void aWornSuitDropsLikeArmourOnDeath(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(3, 1, 3));
		wear(p, "mark_iii");
		var rule = h.getLevel().getServer().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
		boolean was = rule.get();
		rule.set(false, h.getLevel().getServer());
		try {
			p.die(p.damageSources().genericKill());
		} finally {
			rule.set(was, h.getLevel().getServer());
		}
		for (EquipmentSlot s : SLOTS) {
			h.assertTrue(p.getItemBySlot(s).isEmpty(), s + " is empty after death");
		}
		List<ItemEntity> drops = droppedPieces(h, p, "mark_iii");
		h.assertTrue(drops.size() == 4, "the four real pieces drop at the death spot, got " + drops.size());
		for (ItemEntity e : drops) {
			h.assertTrue(e.distanceTo(p) < 4.0, "dropped where the player died");
		}
		// they are ordinary item entities: still there a second later (nothing whisks them home or deletes them)
		h.runAfterDelay(20, () -> {
			h.assertTrue(droppedPieces(h, p, "mark_iii").size() == 4, "the dropped pieces stay on the ground");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void keepInventoryKeepsTheSuit(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(5, 1, 5));
		wear(p, "mark_4");
		var rule = h.getLevel().getServer().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
		boolean was = rule.get();
		rule.set(true, h.getLevel().getServer());
		try {
			p.die(p.damageSources().genericKill());
		} finally {
			rule.set(was, h.getLevel().getServer());
		}
		h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_4"), "keepInventory: the suit stays with the player");
		h.assertTrue(droppedPieces(h, p, "mark_4").isEmpty(), "and nothing drops");
		h.succeed();
	}

	// ------------------------------------------------------------------ 3. touching the floor ends the flight

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void ironManFlightLandsOnTouchdownPastTheGraceAndNotWhileRising(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(1, 3, 5));
		wear(p, "mark_iii");
		p.setNoGravity(true);
		p.setOnGround(true);
		IronManFlight.setFlying(p, true);
		h.assertTrue(IronManFlight.isFlying(p) && p.getAbilities().flying, "flight engages from the ground");
		IronManFlight.tick(p);
		h.assertTrue(IronManFlight.isFlying(p), "the take-off grace: still on the ground right after take-off, still flying");
		p.setOnGround(false);
		h.runAfterDelay(FlightLanding.LIFTOFF_GRACE_TICKS + 2, () -> {
			h.assertTrue(IronManFlight.isFlying(p), "airborne the whole time: still flying");
			p.setOnGround(true);
			p.setKnownMovement(new Vec3(0.0, 0.4, 0.0));
			IronManFlight.tick(p);
			h.assertTrue(IronManFlight.isFlying(p), "on the ground but rising (taking off again): still flying");
			p.setKnownMovement(new Vec3(0.1, -0.2, 0.0));
			IronManFlight.tick(p);
			h.assertFalse(IronManFlight.isFlying(p), "touched down: the flight ends");
			h.assertFalse(p.getAbilities().flying || p.getAbilities().mayfly, "exactly as toggling it off (abilities cleared)");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void landingRuleGraceAndUnknownStart(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(6, 1, 1));
		p.setOnGround(true);
		p.setKnownMovement(Vec3.ZERO);
		long now = h.getLevel().getGameTime();
		h.assertFalse(FlightLanding.landed(p, now), "the take-off tick itself is inside the grace");
		h.assertFalse(FlightLanding.landed(p, now - FlightLanding.LIFTOFF_GRACE_TICKS + 1), "still inside the grace");
		h.assertTrue(FlightLanding.landed(p, now - FlightLanding.LIFTOFF_GRACE_TICKS), "past the grace: landed");
		h.assertTrue(FlightLanding.landed(p, -1L), "no recorded take-off (a relog mid-flight) counts as past the grace");
		h.assertTrue(FlightLanding.landed(p, "nothing_recorded"), "the same through a system key");
		p.setOnGround(false);
		h.assertFalse(FlightLanding.landed(p, -1L), "airborne: never landed");
		h.succeed();
	}
}
