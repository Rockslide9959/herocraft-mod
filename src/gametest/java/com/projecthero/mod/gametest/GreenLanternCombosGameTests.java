package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternCombat;
import com.projecthero.mod.greenlantern.GreenLanternCombos;
import com.projecthero.mod.greenlantern.GreenLanternCombos.Combo;
import com.projecthero.mod.greenlantern.GreenLanternCombos.Move;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternRanks;
import com.projecthero.mod.greenlantern.GreenLanternRingVoice;
import com.projecthero.mod.greenlantern.GreenLanternRingVoice.Line;
import com.projecthero.mod.greenlantern.construct.Construct;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructHealth;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Green Lantern revamp, round 2: Construct Combos (the table, the 3 s window, Overcharge's three bolts, Crush's
 * shockwave, Shatter's broken armour, Pin against a real Hard-Light Wall), construct durability (cracks / tracking / the
 * shatter at 0) and the ring's voice (greeting, rank, charge edges, hostiles, cooldowns and the gap).
 *
 * <p>Like the other GL tests, every Lantern runs at half charge -- no Willpower Surge either way, so damage numbers are
 * exact (power = 1). The cage is 8 blocks wide.
 */
public class GreenLanternCombosGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	private static ServerPlayer lantern(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 1, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.moveTo(at.x, at.y, at.z, 0f, 0f);
		player.setYHeadRot(0f);
		GreenLantern.bond(player);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE * 0.5f;
		GreenLantern.save(player, s);
		return player;
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		h.moveTo(at.x, at.y, at.z, 180f, 0f);
		h.setNoAi(true);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		h.getAttribute(Attributes.ARMOR).setBaseValue(0);
		h.setHealth(200);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	private static void setCharge(ServerPlayer p, float fraction) {
		GreenLanternState s = GreenLantern.state(p).copy();
		s.ringCharge = GreenLanternRanks.maxCharge(p) * fraction;
		GreenLantern.save(p, s);
	}

	// ---------------------------------------------------------------- combos

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyComboHasAnOpenerAndADistinctPair(GameTestHelper helper) {
		java.util.Set<String> pairs = new java.util.HashSet<>();
		for (Combo c : Combo.values()) {
			helper.assertTrue(c.opener != null, c + " has an opener");
			helper.assertTrue(pairs.add(c.opener + ">" + c.finisher), c + " is the only combo for its pair");
		}
		helper.assertTrue(Combo.values().length >= 6 && Combo.values().length <= 8, "6-8 combos");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crushDoublesTheAnvilAndSendsAShockwave(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk target = husk(helper, 4.5, 4.5);
		Husk bystander = husk(helper, 6.5, 4.5);
		GreenLanternCombos.mark(p, target, Move.CHAINS);
		float mult = GreenLanternCombos.strike(p, target, Move.ANVIL);
		helper.assertTrue(mult == GreenLanternConfig.CRUSH_DAMAGE_MULT, "snared + anvil = x" + GreenLanternConfig.CRUSH_DAMAGE_MULT + ", got " + mult);
		helper.assertTrue(GreenLanternCombos.lastCombo(p) == Combo.CRUSH, "the combo is Crush");
		float shock = 200f - bystander.getHealth();
		helper.assertTrue(Math.abs(shock - GreenLanternConfig.CRUSH_SHOCKWAVE_DAMAGE) < 0.6f, "the shockwave hit the bystander for 8, dealt " + shock);
		// one opener, one combo: a second anvil straight away is ordinary
		helper.assertTrue(GreenLanternCombos.strike(p, target, Move.ANVIL) == 1f, "the opener was consumed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aFinisherWithNoOpenerIsOrdinary(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk target = husk(helper, 4.5, 4.5);
		helper.assertTrue(GreenLanternCombos.strike(p, target, Move.ANVIL) == 1f, "anvil alone: no bonus");
		helper.assertTrue(GreenLanternCombos.strike(p, target, Move.HAMMER) == 1f, "hammer after anvil: no combo pairs them");
		helper.assertTrue(GreenLanternCombos.comboCount(p) == 0, "nothing landed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void theOpenerExpiresAfterTheWindow(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk target = husk(helper, 4.5, 4.5);
		GreenLanternCombos.mark(p, target, Move.BEAR_TRAP);
		helper.runAfterDelay(GreenLanternConfig.COMBO_WINDOW_TICKS + 5, () -> {
			helper.assertTrue(GreenLanternCombos.strike(p, target, Move.SNIPER) == 1f, "3 s later the trap no longer counts");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void overchargeNeedsThreeRealRingBolts(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk target = husk(helper, 1.5, 5.5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		int gap = GreenLanternConfig.BOLT_COOLDOWN_TICKS + 1;
		helper.startSequence()
				.thenExecute(() -> GreenLanternCombat.ringBolt(p))
				.thenIdle(gap)
				.thenExecute(() -> GreenLanternCombat.ringBolt(p))
				.thenIdle(gap)
				.thenExecute(() -> {
					helper.assertTrue(target.getHealth() < 200f, "the bolts hit the husk");
					// a copy of the state after two bolts: a beam now would be ordinary
					helper.assertTrue(GreenLanternCombos.lastCombo(p) == null, "no combo yet");
					GreenLanternCombat.ringBolt(p);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					float mult = GreenLanternCombos.strike(p, target, Move.BEAM);
					helper.assertTrue(mult == GreenLanternConfig.OVERCHARGE_BEAM_MULT, "three bolts + beam = x5, got " + mult);
					helper.assertTrue(GreenLanternCombos.lastCombo(p) == Combo.OVERCHARGE, "Overcharge");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void twoBoltsAreNotEnough(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk target = husk(helper, 4.5, 4.5);
		GreenLanternCombos.mark(p, target, Move.RING_BOLT); // same tick: still one bolt
		GreenLanternCombos.mark(p, target, Move.RING_BOLT);
		helper.assertTrue(GreenLanternCombos.strike(p, target, Move.BEAM) == 1f, "one bolt + beam is no Overcharge");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void shatterBreaksArmourForEightSeconds(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk target = husk(helper, 4.5, 4.5);
		target.getAttribute(Attributes.ARMOR).setBaseValue(10);
		GreenLanternCombos.mark(p, target, Move.BUZZSAW);
		float mult = GreenLanternCombos.strike(p, target, Move.HAMMER);
		helper.assertTrue(mult == GreenLanternConfig.SHATTER_DAMAGE_MULT, "buzzsaw + hammer = Shatter");
		helper.assertTrue(target.getArmorValue() == 0, "armour broken, reads " + target.getArmorValue());
		helper.assertTrue(GreenLanternCombos.isShattered(target), "tracked as shattered");
		helper.runAfterDelay(GreenLanternConfig.SHATTER_TICKS + 10, () -> {
			helper.assertTrue(target.getArmorValue() == 10, "armour back after 8 s, reads " + target.getArmorValue());
			helper.assertTrue(!GreenLanternCombos.isShattered(target), "no longer shattered");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void pinWhenARammedTargetHitsYourWall(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		// raise a real Hard-Light Wall on the floor ahead (looking down at the stone 3 blocks out)
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(1.5, 1.5, 4.5)));
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_WALL);
		List<Construct> walls = GreenLanternConstructs.of(p.getUUID()).stream().filter(c -> c.type == ConstructType.HARD_LIGHT_WALL).toList();
		helper.assertTrue(walls.size() == 1, "the wall went up");
		Construct wall = walls.get(0);
		helper.runAfterDelay(8, () -> {
			BlockPos cell = wall.cells.get(0).pos();
			helper.assertTrue(GreenLanternConstructs.wallOwnerAt(helper.getLevel(), cell) != null, "the wall's block is indexed");
			// a husk right up against the wall's near face
			Husk target = EntityType.HUSK.create(helper.getLevel());
			Vec3 at = Vec3.atBottomCenterOf(cell).add(0, 0, -0.9); // just clear of the block (no suffocation), within reach
			target.moveTo(at.x, at.y, at.z, 0f, 0f);
			target.setNoAi(true);
			target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
			target.setHealth(200);
			helper.getLevel().addFreshEntity(target);
			GreenLanternCombos.rammed(p, target);
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(GreenLanternCombos.lastCombo(p) == Combo.PIN, "rammed into your own wall = Pin, got " + GreenLanternCombos.lastCombo(p));
				float dealt = 200f - target.getHealth();
				helper.assertTrue(Math.abs(dealt - GreenLanternConfig.PIN_DAMAGE) < 0.6f, "Pin hits for 10, dealt " + dealt);
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- durability

	@GameTest(template = EMPTY_STRUCTURE)
	public void crackStagesFollowTheHealth(GameTestHelper helper) {
		helper.assertTrue(GreenLanternConstructHealth.crackStage(1f) == -1 && GreenLanternConstructHealth.crackStage(0.5f) == -1,
				"no cracks at or above 50%");
		helper.assertTrue(GreenLanternConstructHealth.crackStage(0.49f) == 0, "first crack just under half");
		helper.assertTrue(GreenLanternConstructHealth.crackStage(0.25f) == 5, "deeper at a quarter");
		helper.assertTrue(GreenLanternConstructHealth.crackStage(0.0f) == 9, "deepest at empty");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aDamagedWallIsTrackedWarnsAndShatters(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(1.5, 1.5, 4.5)));
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_WALL);
		Construct wall = GreenLanternConstructs.of(p.getUUID()).stream().filter(c -> c.type == ConstructType.HARD_LIGHT_WALL)
				.findFirst().orElse(null);
		helper.assertTrue(wall != null, "the wall went up");
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(!GreenLanternConstructHealth.isTracked(wall), "an undamaged wall shows nothing extra");
			GreenLanternRingVoice.resetCooldowns(p);
			GreenLanternConstructs.damageConstruct(wall, GreenLanternConfig.WALL_HP * 0.6f, null);
			helper.assertTrue(GreenLanternConstructHealth.isTracked(wall), "damaged: tracked (tint + cracks)");
			helper.assertTrue(Math.abs(GreenLanternConstructHealth.fraction(wall) - 0.4f) < 0.01f, "40% left");
			GreenLanternConstructs.damageConstruct(wall, GreenLanternConfig.WALL_HP * 0.25f, null);
			helper.assertTrue(GreenLanternRingVoice.lastLine(p) == Line.INTEGRITY, "under 20%: \"Construct integrity failing.\"");
			GreenLanternConstructs.damageConstruct(wall, GreenLanternConfig.WALL_HP, null);
			helper.assertTrue(!GreenLanternConstructs.of(p.getUUID()).contains(wall), "at 0 it shatters");
			helper.assertTrue(!GreenLanternConstructHealth.isTracked(wall), "and its cracks / tint go with it");
			helper.assertTrue(GreenLanternConstructs.constructAt(helper.getLevel(), wall.cells.get(0).pos()) == null, "its blocks are gone");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the ring's voice

	@GameTest(template = EMPTY_STRUCTURE)
	public void theRingGreetsItsBearerAndAnnouncesARank(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		helper.assertTrue(GreenLanternRingVoice.lastLine(p) == Line.GREETING, "putting the ring on: a greeting");
		GreenLanternRanks.award(p, GreenLanternConfig.RANK_XP[1]);
		helper.assertTrue(GreenLanternRingVoice.lastLine(p) == Line.RANK, "rank up: \"Rank achieved\"");
		helper.assertTrue(GreenLanternRanks.nameKey(2).equals(GreenLanternRingVoice.lastArg(p)), "with the rank's name");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chargeEdgesSpeakOnceEach(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternRingVoice.onCleanup(p.getUUID()); // fresh baseline
		GreenLanternRingVoice.resetCooldowns(p);
		GreenLanternRingVoice.evaluate(p, false); // baseline at 50%: says nothing
		int before = GreenLanternRingVoice.spokenCount(p);
		GreenLanternRingVoice.evaluate(p, false);
		helper.assertTrue(GreenLanternRingVoice.spokenCount(p) == before, "a steady ring is quiet");
		setCharge(p, 0.2f);
		GreenLanternRingVoice.evaluate(p, false);
		helper.assertTrue(GreenLanternRingVoice.lastLine(p) == Line.CHARGE_LOW, "falling under 25%: \"Ring charge at 25%.\"");
		int afterLow = GreenLanternRingVoice.spokenCount(p);
		GreenLanternRingVoice.evaluate(p, false);
		helper.assertTrue(GreenLanternRingVoice.spokenCount(p) == afterLow, "said once, not every look");
		setCharge(p, 0.05f);
		GreenLanternRingVoice.evaluate(p, false);
		helper.assertTrue(GreenLanternRingVoice.lastLine(p) == Line.CHARGE_CRITICAL, "under 10%: critical (urgent, skips the gap)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void surgeAndHostilesAreCalledOut(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternRingVoice.onCleanup(p.getUUID());
		GreenLanternRingVoice.resetCooldowns(p);
		GreenLanternRingVoice.evaluate(p, true); // baseline: half charge, no hostiles
		for (int i = 0; i < GreenLanternConfig.RING_VOICE_HOSTILE_COUNT; i++) {
			husk(helper, 3.5 + i, 5.5);
		}
		GreenLanternRingVoice.evaluate(p, true);
		helper.assertTrue(GreenLanternRingVoice.lastLine(p) == Line.HOSTILES, "three hostiles: \"Hostile life forms detected.\"");
		GreenLanternRingVoice.resetCooldowns(p);
		setCharge(p, 0.9f);
		GreenLanternRingVoice.evaluate(p, true);
		helper.assertTrue(GreenLanternRingVoice.lastLine(p) == Line.SURGE, "over 80%: \"Willpower surge.\"");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void linesHaveCooldownsAndAGap(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternRingVoice.resetCooldowns(p);
		helper.assertTrue(GreenLanternRingVoice.say(p, Line.RECHARGE), "\"Recharge recommended.\" the first time");
		helper.assertTrue(!GreenLanternRingVoice.say(p, Line.RECHARGE), "not again straight away (its own cooldown)");
		helper.assertTrue(!GreenLanternRingVoice.say(p, Line.SURGE), "another ordinary line waits out the gap");
		helper.assertTrue(GreenLanternRingVoice.say(p, Line.OATH), "an urgent line (the Oath) cuts in");
		helper.succeed();
	}
}
