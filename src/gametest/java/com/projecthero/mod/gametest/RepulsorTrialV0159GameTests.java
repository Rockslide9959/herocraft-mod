package com.projecthero.mod.gametest;

import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternTrial;
import com.projecthero.mod.ironman.RepulsorBoots;
import com.projecthero.mod.ironman.RepulsorFlightLook;
import com.projecthero.mod.ironman.item.IronManItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.15.9: Repulsor Boots flight (no sprint flying + the new pose's numbers) and the Green Lantern Will Trial's
 * 20-level entry cost, the Green Lantern ring light, and the Iron Man integrity / stuck-arrow rules. Kept to four
 * {@code @GameTest}s (related checks folded together): with the class near the top of the entrypoint list, a different
 * count shifts every later test's place in the grid, and ColantotteBraceletsV0154GameTests#braceletCallStillTravelsFromThePlatform
 * is sensitive to where its racks land relative to the ticking chunks.
 */
public class RepulsorTrialV0159GameTests implements FabricGameTest {
	private static final float EPS = 1.0e-4f;

	// ---------------------------------------------------------------- Repulsor Boots: no sprint flying

	@GameTest(template = EMPTY_STRUCTURE)
	public void repulsorBootsFlightRefusesSprint(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(IronManItems.REPULSOR));
		double walkSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);

		p.setSprinting(true);
		h.assertTrue(p.isSprinting(), "on the ground the boots wearer can still sprint");
		RepulsorBoots.setFlying(p, true);
		h.assertTrue(RepulsorBoots.isFlying(p) && RepulsorBoots.blocksSprint(p), "boots flight engages");
		h.assertFalse(p.isSprinting(), "taking off drops the sprint");
		h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - walkSpeed) < 1.0e-6,
				"no sprint speed modifier left on while boots-flying");

		p.setSprinting(true);
		h.assertFalse(p.isSprinting(), "no sprint flying on the boots: a sprint request is refused");
		h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - walkSpeed) < 1.0e-6,
				"a refused sprint adds no speed modifier");

		RepulsorBoots.setFlying(p, false);
		h.assertFalse(RepulsorBoots.blocksSprint(p), "landing lifts the rule");
		p.setSprinting(true);
		h.assertTrue(p.isSprinting(), "sprinting works again once the boots flight ends");
		p.setSprinting(false);
		poseChecks(h);
		h.succeed();
	}

	// ---------------------------------------------------------------- Repulsor Boots: the pose numbers

	// (folded into a neighbouring test to keep the gametest grid layout unchanged -- see the class javadoc)
	private static void poseChecks(GameTestHelper h) {
		h.assertTrue(RepulsorFlightLook.targetLean(0f) == 0f && RepulsorFlightLook.targetRoll(0f) == 0f,
				"hovering is upright and level");
		h.assertTrue(Math.abs(RepulsorFlightLook.targetLean(1f) - RepulsorFlightLook.MAX_LEAN_DEGREES) < EPS,
				"full cruise leans the full amount");
		h.assertTrue(RepulsorFlightLook.MAX_LEAN_DEGREES >= 30f && RepulsorFlightLook.MAX_LEAN_DEGREES <= 40f,
				"forward lean caps at 30-40 degrees, never the 90-degree superman pose");
		float prev = 0f;
		for (int i = 1; i <= 10; i++) {
			float lean = RepulsorFlightLook.targetLean(i / 10f);
			h.assertTrue(lean > prev, "the lean grows steadily with speed (" + i + ")");
			prev = lean;
		}
		h.assertTrue(Math.abs(RepulsorFlightLook.targetLean(5f) - RepulsorFlightLook.MAX_LEAN_DEGREES) < EPS,
				"never past the cap");
		h.assertTrue(Math.abs(RepulsorFlightLook.targetLean(-1f) + RepulsorFlightLook.MAX_BACK_LEAN_DEGREES) < EPS,
				"flying backward leans back, a little");
		h.assertTrue(RepulsorFlightLook.targetRoll(1f) > 0f && RepulsorFlightLook.targetRoll(-1f) < 0f
				&& Math.abs(RepulsorFlightLook.targetRoll(1f) + RepulsorFlightLook.targetRoll(-1f)) < EPS,
				"strafing banks into the slide, symmetrically");

		// body frame: yaw 0 faces +Z (right = -X); yaw 90 faces -X (right = -Z)
		h.assertTrue(Math.abs(RepulsorFlightLook.forward(0, 0.5, 0f) - 0.5) < 1.0e-6
				&& Math.abs(RepulsorFlightLook.right(-0.5, 0, 0f) - 0.5) < 1.0e-6, "body frame at yaw 0");
		h.assertTrue(Math.abs(RepulsorFlightLook.forward(-0.5, 0, 90f) - 0.5) < 1.0e-5
				&& Math.abs(RepulsorFlightLook.right(0, -0.5, 90f) - 0.5) < 1.0e-5, "body frame at yaw 90");

		// easing: monotonic, no overshoot, settles
		float v = 0f;
		for (int i = 0; i < 60; i++) {
			float next = RepulsorFlightLook.ease(v, 35f, RepulsorFlightLook.BODY_EASE);
			h.assertTrue(next >= v && next <= 35f, "the lean eases in without overshoot");
			v = next;
		}
		h.assertTrue(Math.abs(v - 35f) < 0.01f, "and settles on the target");
		h.assertTrue(RepulsorFlightLook.hoverWeight(0f, 0f) == 1f && RepulsorFlightLook.hoverWeight(1f, 0f) == 0f,
				"the hover pose is for standing still in the air only");

	}

	// ---------------------------------------------------------------- Iron Man: integrity + stuck arrows

	private static ServerPlayer suited(GameTestHelper h, com.projecthero.mod.ironman.suit.IronManSuit suit) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		com.projecthero.mod.ironman.TonyStark.grant(p);
		for (net.minecraft.world.item.ArmorItem.Type t : net.minecraft.world.item.ArmorItem.Type.values()) {
			var item = IronManItems.armor(suit.id(), t);
			if (item != null) {
				p.setItemSlot(com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotFor(t), new ItemStack(item));
			}
		}
		com.projecthero.mod.ironman.TonyStark.setActiveSuit(p, suit.id());
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(p, suit.id(), com.projecthero.mod.ironman.IronManEnergy.capacity(suit.id()));
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noIronManSuitRepairsItselfWhileWorn(GameTestHelper h) {
		for (com.projecthero.mod.ironman.suit.IronManSuit suit : com.projecthero.mod.ironman.suit.IronManSuits.all()) {
			h.assertTrue(suit.wornIntegrityRegenPerSecond() == 0f, suit.id() + ": no worn integrity regen");
			ServerPlayer p = suited(h, suit);
			com.projecthero.mod.ironman.IronManEnergy.setIntegrity(p, suit.id(), 100f);
			for (int i = 0; i < 40; i++) {
				com.projecthero.mod.ironman.IronManEnergy.tickRecharge(p, suit);
			}
			h.assertTrue(com.projecthero.mod.ironman.IronManEnergy.integrity(p, suit.id()) == 100f,
					suit.id() + ": two seconds worn repair nothing, at " + com.projecthero.mod.ironman.IronManEnergy.integrity(p, suit.id()));
			h.getLevel().getServer().getPlayerList().remove(p);
		}
		wholeHitChecks(h);
		h.succeed();
	}

	// (folded into a neighbouring test to keep the gametest grid layout unchanged -- see the class javadoc)
	private static void wholeHitChecks(GameTestHelper h) {
		var events = net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.invoker();
		for (com.projecthero.mod.ironman.suit.IronManSuit suit : com.projecthero.mod.ironman.suit.IronManSuits.all()) {
			ServerPlayer p = suited(h, suit);
			String id = suit.id();
			float max = com.projecthero.mod.ironman.IronManEnergy.maxIntegrity(id);
			com.projecthero.mod.ironman.IronManEnergy.setIntegrity(p, id, max);
			// 10 damage arrives; armour / Resistance trim the health loss to 4 -- the suit still loses the full 10
			events.afterDamage(p, p.damageSources().generic(), 10f, 4f, false);
			h.assertTrue(Math.abs(max - com.projecthero.mod.ironman.IronManEnergy.integrity(p, id) - 10f) < 1e-3f,
					id + ": 10 damage = 10 integrity, lost " + (max - com.projecthero.mod.ironman.IronManEnergy.integrity(p, id)));
			// a shield-blocked hit is no hit
			events.afterDamage(p, p.damageSources().generic(), 6f, 0f, true);
			h.assertTrue(Math.abs(max - com.projecthero.mod.ironman.IronManEnergy.integrity(p, id) - 10f) < 1e-3f,
					id + ": a blocked hit costs nothing");
			// the hit-does-nothing exemptions are kept
			if (suit.arrowFireImmune()) {
				h.assertFalse(com.projecthero.mod.ironman.IronManDamage.onAllowDamage(p, p.damageSources().inFire(), 10f),
						id + ": still ignores fire outright");
			}
			// at zero integrity nothing more comes off and the hit still lands
			com.projecthero.mod.ironman.IronManEnergy.setIntegrity(p, id, 3f);
			events.afterDamage(p, p.damageSources().generic(), 10f, 10f, false);
			h.assertTrue(com.projecthero.mod.ironman.IronManEnergy.integrity(p, id) == 0f, id + ": integrity bottoms out at 0");
			h.assertTrue(com.projecthero.mod.ironman.IronManDamage.onAllowDamage(p, p.damageSources().generic(), 10f),
					id + ": a failed suit still lets hits land");
			h.getLevel().getServer().getPlayerList().remove(p);
		}

	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitingUpOrDownKnocksStuckArrowsOff(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		com.projecthero.mod.ironman.IronManSuitArrows.forget(p.getUUID());
		p.setArrowCount(5);
		p.setStingerCount(2);
		com.projecthero.mod.ironman.IronManSuitArrows.tick(p);
		com.projecthero.mod.ironman.IronManSuitArrows.tick(p);
		h.assertTrue(p.getArrowCount() == 5 && p.getStingerCount() == 2, "nothing changes, nothing is knocked off");

		// a piece goes on (the start of a suit-up)
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(IronManItems.armor("mark_4", net.minecraft.world.item.ArmorItem.Type.CHESTPLATE)));
		com.projecthero.mod.ironman.IronManSuitArrows.tick(p);
		h.assertTrue(p.getArrowCount() == 0 && p.getStingerCount() == 0, "suiting up knocks the arrows and stingers off");

		p.setArrowCount(3);
		com.projecthero.mod.ironman.IronManSuitArrows.tick(p);
		h.assertTrue(p.getArrowCount() == 3, "arrows that land on the suit afterwards stay until the next change");
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(IronManItems.armor("mark_4", net.minecraft.world.item.ArmorItem.Type.BOOTS)));
		com.projecthero.mod.ironman.IronManSuitArrows.tick(p);
		h.assertTrue(p.getArrowCount() == 0, "every piece going on (the end of a suit-up) clears them too");

		p.setArrowCount(4);
		com.projecthero.mod.ironman.IronManSuitArrows.tick(p);
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		com.projecthero.mod.ironman.IronManSuitArrows.tick(p);
		h.assertTrue(p.getArrowCount() == 0, "and suiting down clears them");
		com.projecthero.mod.ironman.IronManSuitArrows.forget(p.getUUID());
		h.succeed();
	}

	// ---------------------------------------------------------------- Green Lantern: Lantern Light from the ring

	@GameTest(template = EMPTY_STRUCTURE)
	public void lanternLightShinesFromTheRing(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		com.projecthero.mod.greenlantern.GreenLantern.bond(p);
		net.minecraft.world.phys.Vec3 at = h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z); // in the open air of the test structure (a mock player joins at the world spawn)
		try {
			com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.deploy(p,
					com.projecthero.mod.greenlantern.construct.ConstructType.LANTERN_LIGHT);
			MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
			h.assertTrue(nv != null && nv.isAmbient() && !nv.isVisible(), "the ring light gives ambient, particle-free Night Vision");
			BlockPos light = com.projecthero.mod.greenlantern.construct.GreenLanternRingLight.lightPos(p.getUUID());
			h.assertTrue(light != null && p.serverLevel().getBlockState(light).is(Blocks.LIGHT),
					"a light block shines from the caster");
			h.assertTrue(light.distSqr(p.blockPosition()) <= 2, "right at the caster (head or feet), not out where they aim");

			// it follows: move the caster and tick the constructs
			BlockPos old = light;
			p.moveTo(p.getX() + 2, p.getY(), p.getZ());
			com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.tick(p.server);
			BlockPos moved = com.projecthero.mod.greenlantern.construct.GreenLanternRingLight.lightPos(p.getUUID());
			h.assertTrue(moved != null && !moved.equals(old) && p.serverLevel().getBlockState(moved).is(Blocks.LIGHT),
					"the light moves with the caster");
			h.assertTrue(p.serverLevel().getBlockState(old).isAir(), "and the old light is taken away");

			// a second press puts it out
			com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.deploy(p,
					com.projecthero.mod.greenlantern.construct.ConstructType.LANTERN_LIGHT);
			h.assertTrue(com.projecthero.mod.greenlantern.construct.GreenLanternRingLight.lightPos(p.getUUID()) == null
					&& p.serverLevel().getBlockState(moved).isAir(), "toggling off removes the light block");
			h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) == null, "and the Night Vision");
		} finally {
			com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.clearFor(p.getUUID());
		}
		trialChecks(h);
		h.succeed();
	}

	// ---------------------------------------------------------------- Green Lantern Will Trial: 20 levels consumed

	// (folded into a neighbouring test to keep the gametest grid layout unchanged -- see the class javadoc)
	private static void trialChecks(GameTestHelper h) {
		int cost = GreenLanternConfig.TRIAL_LEVEL_REQUIREMENT;
		h.assertTrue(cost == 20, "the trial asks for 20 experience levels");
		BlockPos pedestal = h.absolutePos(new BlockPos(1, 1, 1));
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ServerPlayer rival = h.makeMockServerPlayerInLevel();
		rival.setGameMode(GameType.SURVIVAL);
		try {
			p.giveExperienceLevels(cost - 1);
			h.assertFalse(GreenLanternTrial.admit(p, pedestal, false), "level 19 is refused");
			h.assertTrue(p.experienceLevel == cost - 1, "a refused start costs nothing");

			p.giveExperienceLevels(6); // 25
			h.assertFalse(GreenLanternTrial.admit(p, pedestal, true), "a claimed site is refused");
			h.assertTrue(p.experienceLevel == 25, "a refused (claimed) start costs nothing");

			h.assertTrue(GreenLanternTrial.admit(p, pedestal, false), "level 25 starts the trial");
			h.assertTrue(p.experienceLevel == 25 - cost, "starting drains the 20 levels, left " + p.experienceLevel);

			rival.giveExperienceLevels(30);
			h.assertFalse(GreenLanternTrial.admit(rival, pedestal, false), "a second player can't race the same site");
			h.assertTrue(rival.experienceLevel == 30, "the refused rival keeps every level");
		} finally {
			GreenLanternTrial.release(pedestal);
		}

		ServerPlayer exact = h.makeMockServerPlayerInLevel();
		exact.setGameMode(GameType.SURVIVAL);
		BlockPos other = h.absolutePos(new BlockPos(2, 1, 2));
		try {
			exact.giveExperienceLevels(cost);
			h.assertTrue(GreenLanternTrial.admit(exact, other, false), "exactly level 20 is enough");
			h.assertTrue(exact.experienceLevel == 0, "and it is spent down to 0");
		} finally {
			GreenLanternTrial.release(other);
		}

	}
}
