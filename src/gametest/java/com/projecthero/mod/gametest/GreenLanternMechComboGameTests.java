package com.projecthero.mod.gametest;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.mech.GreenLanternMech;
import com.projecthero.mod.greenlantern.mech.GreenLanternMechCombos;
import com.projecthero.mod.greenlantern.mech.GreenLanternMechConfig;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;
import com.projecthero.mod.greenlantern.mech.MechComboKeys;
import com.projecthero.mod.greenlantern.mech.MechComboKeys.Combo;
import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

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
 * v0.17.1 Hard-Light Mech construct combos ({@link GreenLanternMechCombos}, keys from {@link MechComboKeys}): the trigger
 * rule (Shift + the construct's own key, the fallbacks, never H, no shared keys); the Giga Drill's hold / release, its
 * multi-hits, the tunnel it bores and the blocks it must leave (containers, barriers, bedrock); the Hard-Light Katana's
 * draw / sheathe, its R chain, Shift+R iaido crescent and G spin, its upkeep and being sheathed on climb-out.
 *
 * <p>The mech stands at (4, 2, 2) facing +Z. The pilot's client moves a real mech; here nothing does, so the drill tests
 * move it down the line by hand, the way the pilot's client would, and keep the charge short so the bore stays near
 * the test area.
 */
public class GreenLanternMechComboGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	private static ServerPlayer lantern(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 1, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(4.0, 2.0, 2.0));
		player.moveTo(at.x, at.y, at.z, 0f, 0f);
		player.setYHeadRot(0f);
		GreenLantern.bond(player);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE * 0.5f; // no Willpower Surge either way
		GreenLantern.save(player, s);
		return player;
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		h.moveTo(at.x, at.y, at.z, 180f, 0f);
		h.setNoAi(true);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
		h.getAttribute(Attributes.ARMOR).setBaseValue(0);
		h.setHealth(400);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	private static HardLightMechEntity pilotReady(GameTestHelper helper, ServerPlayer p) {
		GreenLanternMech.deploy(p);
		HardLightMechEntity m = GreenLanternMech.mechOf(p);
		helper.assertTrue(m != null, "the mech formed and the pilot sits in it");
		m.finishAssembly();
		return m;
	}

	private static void bind(ServerPlayer p, String key, ConstructType t) {
		GreenLanternState s = GreenLantern.state(p).copy();
		s.bindings.put(key, GreenLanternBinding.forConstruct(t).id());
		GreenLantern.save(p, s);
	}

	private static void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		GreenLanternAbilityManager.handle(p, slot, true);
		p.setShiftKeyDown(false);
	}

	private static void release(ServerPlayer p, AbilitySlot slot) {
		GreenLanternAbilityManager.handle(p, slot, false);
	}

	private static float charge(ServerPlayer p) {
		return GreenLantern.state(p).ringCharge;
	}

	// ================================================================ the trigger rule

	@GameTest(template = EMPTY_STRUCTURE)
	public void comboKeysFollowTheBindingsWithFallbacks(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		// nothing bound (the default layout): the fixed free keys
		char[] k = MechComboKeys.keys(GreenLantern.state(p));
		helper.assertTrue(k[Combo.GIGA_DRILL.ordinal()] == 'G' && k[Combo.KATANA.ordinal()] == 'X', "fallbacks Shift+G / Shift+X, got " + new String(k));
		// the Mining Drill on Shift+Z: the Giga Drill is Shift+Z in the mech; the Energy Blade on H alone: its fallback
		bind(p, "SZ", ConstructType.MINING_DRILL);
		bind(p, "H", ConstructType.ENERGY_BLADE);
		k = MechComboKeys.keys(GreenLantern.state(p));
		helper.assertTrue(k[Combo.GIGA_DRILL.ordinal()] == 'Z', "drill follows its binding, got " + k[0]);
		helper.assertTrue(k[Combo.KATANA.ordinal()] == 'X', "H never counts: the katana falls back to X, got " + k[1]);
		helper.assertTrue(MechComboKeys.fromBinding(GreenLantern.state(p), Combo.GIGA_DRILL), "drill key came from the binding");
		// the Energy Blade on C: Shift+C
		bind(p, "C", ConstructType.ENERGY_BLADE);
		helper.assertTrue(MechComboKeys.keyOf(GreenLantern.state(p), Combo.KATANA) == 'C', "katana on Shift+C");
		// both on G (G and Shift+G): the drill keeps it, the katana falls back -- never a shared key
		bind(p, "G", ConstructType.MINING_DRILL);
		bind(p, "SZ", ConstructType.MINING_DRILL);
		bind(p, "SG", ConstructType.ENERGY_BLADE);
		bind(p, "C", ConstructType.HARD_LIGHT_WALL);
		k = MechComboKeys.keys(GreenLantern.state(p));
		helper.assertTrue(k[0] == 'G' && k[1] == 'X', "drill G, katana X, got " + new String(k));
		// the drill on X: the katana's own fallback is taken, so it gets the first free one (G)
		GreenLanternState s = GreenLantern.state(p).copy();
		s.bindings.clear();
		GreenLantern.save(p, s);
		bind(p, "X", ConstructType.MINING_DRILL);
		k = MechComboKeys.keys(GreenLantern.state(p));
		helper.assertTrue(k[0] == 'X' && k[1] == 'G', "drill X, katana G, got " + new String(k));
		helper.assertTrue(MechComboKeys.comboOn(GreenLantern.state(p), 'X') == Combo.GIGA_DRILL, "Shift+X is the drill");
		helper.assertTrue(MechComboKeys.comboOn(GreenLantern.state(p), 'Z') == null, "Shift+Z is free");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void shiftPlusTheBoundKeyStartsTheCombo(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		bind(p, "SV", ConstructType.MINING_DRILL); // drill on Shift+V outside -> Shift+V in the mech
		HardLightMechEntity m = pilotReady(helper, p);
		press(p, AbilitySlot.SLOT_2, true); // Shift+G: no longer the drill (it went to V), the katana's fallback is X -> a plain slam
		helper.assertTrue(m.drillState() == HardLightMechEntity.DRILL_NONE && !m.katanaOut(), "Shift+G is nothing special now");
		release(p, AbilitySlot.SLOT_2);
		press(p, AbilitySlot.SLOT_5, true); // Shift+V
		helper.assertTrue(m.drillState() == HardLightMechEntity.DRILL_SPIN, "Shift+V spins the drill up");
		helper.assertFalse(m.hasFlag(HardLightMechEntity.FLAG_SHIELD), "and does not raise the shield");
		press(p, AbilitySlot.SLOT_5, false); // plain V while it spins: waits
		helper.assertFalse(m.hasFlag(HardLightMechEntity.FLAG_SHIELD), "the drill arm is busy");
		release(p, AbilitySlot.SLOT_5); // let go before the minimum spin: it winds down
		helper.assertTrue(m.drillState() == HardLightMechEntity.DRILL_RECOVER, "a fizzle winds it down");
		helper.assertTrue(!m.ready(HardLightMechEntity.CD_DRILL), "short cooldown");
		press(p, AbilitySlot.SLOT_3, true); // Shift+X: the katana
		helper.assertTrue(m.katanaState() == HardLightMechEntity.KATANA_DRAWING, "Shift+X draws the katana");
		helper.succeed();
	}

	// ================================================================ Giga Drill

	/**
	 * Every tick, move the mech down the charge line the way the pilot's client would. (Stages are all scheduled from
	 * the test body: scheduling from inside a scheduled callback corrupts the gametest's tick map.)
	 */
	private static void follow(GameTestHelper helper, HardLightMechEntity m) {
		helper.onEachTick(() -> {
			if (m.isRemoved() || m.drillState() != HardLightMechEntity.DRILL_CHARGE) {
				return;
			}
			double along = m.position().add(0, GreenLanternMechConfig.DRILL_AXIS_Y, 0).subtract(m.drillStart).dot(m.drillAim);
			double step = Math.min(GreenLanternMechConfig.DRILL_SPEED, Math.max(0.0, m.drillProgress - along));
			Vec3 to = m.position().add(m.drillAim.scale(step));
			m.setPos(to.x, to.y, to.z);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void gigaDrillSpinsUpChargesBoresAndBreaks(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = pilotReady(helper, p);
		// a wall of stone ahead, a chest and a barrier inside the bore's edge (never bored), a husk in the way
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(2, 3, 5)), helper.absolutePos(new BlockPos(5, 6, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		helper.setBlock(new BlockPos(1, 4, 6), Blocks.CHEST);
		helper.setBlock(new BlockPos(6, 4, 6), Blocks.BARRIER);
		Husk h = husk(helper, 4.0, 4.2);
		float before = charge(p);
		press(p, AbilitySlot.SLOT_2, true); // Shift+G (the drill's fallback)
		helper.assertTrue(m.drillState() == HardLightMechEntity.DRILL_SPIN, "spinning up");
		helper.assertTrue(before - charge(p) >= GreenLanternMechConfig.DRILL_START_COST - 0.5f, "120 to start");
		follow(helper, m);
		int t1 = GreenLanternMechConfig.DRILL_SPIN_TICKS + 2;
		helper.runAfterDelay(t1, () -> {
			helper.assertTrue(m.drillPower() >= 1f, "full spin after 2 s, " + m.drillPower());
			helper.assertTrue(charge(p) < before - GreenLanternMechConfig.DRILL_START_COST - 40f, "the spin drains while held");
			release(p, AbilitySlot.SLOT_2);
			helper.assertTrue(m.drillState() == HardLightMechEntity.DRILL_CHARGE, "let go: it charges");
			helper.assertTrue(Math.abs(m.drillEnd - GreenLanternMechConfig.DRILL_RANGE_MAX) < 0.01, "a full-spin charge, " + m.drillEnd);
			m.drillEnd = 3.0; // keep the bore near the test area
		});
		helper.runAfterDelay(t1 + 34, () -> {
			helper.assertTrue(m.drillState() == HardLightMechEntity.DRILL_RECOVER || m.drillState() == HardLightMechEntity.DRILL_NONE,
					"the charge ended, state " + m.drillState());
			helper.assertTrue(m.breakAt() > 0, "a full spin ends in GIGA DRILL BREAK");
			helper.assertTrue(!m.ready(HardLightMechEntity.CD_DRILL), "the drill is cooling down");
			for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 3, 5), new BlockPos(5, 6, 6))) {
				helper.assertBlockPresent(Blocks.AIR, pos);
			}
			helper.assertBlockPresent(Blocks.CHEST, new BlockPos(1, 4, 6));
			helper.assertBlockPresent(Blocks.BARRIER, new BlockPos(6, 4, 6));
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 1, 4)); // the floor under the bore stays
			float dealt = 400f - h.getHealth();
			helper.assertTrue(dealt >= GreenLanternMechConfig.DRILL_HIT_DAMAGE - 0.5f, "drilled the husk, dealt " + dealt);
			helper.assertTrue(m.drillBroken >= 16, "bored the wall, " + m.drillBroken + " blocks");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void gigaDrillStopsAtBedrock(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = pilotReady(helper, p);
		// bedrock right on the line 4 blocks ahead, stone round it
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(2, 3, 6)), helper.absolutePos(new BlockPos(5, 6, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		helper.setBlock(new BlockPos(3, 5, 6), Blocks.BEDROCK);
		helper.setBlock(new BlockPos(4, 5, 6), Blocks.BEDROCK);
		press(p, AbilitySlot.SLOT_2, true);
		follow(helper, m);
		helper.runAfterDelay(14, () -> {
			release(p, AbilitySlot.SLOT_2);
			helper.assertTrue(m.drillState() == HardLightMechEntity.DRILL_CHARGE, "a part spin still charges");
			helper.assertTrue(m.drillPower() < 1f, "not a full spin");
		});
		helper.runAfterDelay(14 + 44, () -> {
			helper.assertTrue(m.drillState() != HardLightMechEntity.DRILL_CHARGE, "the charge ended");
			helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(3, 5, 6));
			helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(4, 5, 6));
			helper.assertTrue(m.drillEnd <= 2.7, "it stopped short of the bedrock, end " + m.drillEnd);
			helper.assertTrue(m.breakAt() < 0, "no finisher on a part spin");
			Vec3 rel = m.position().subtract(helper.absoluteVec(new Vec3(4.0, 2.0, 2.0)));
			helper.assertTrue(rel.z < 3.0, "the mech is not through the bedrock, " + rel.z);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boreRulesKeepProtectedBlocks(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		var level = helper.getLevel();
		BlockPos stone = helper.absolutePos(new BlockPos(2, 3, 3));
		helper.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
		helper.assertTrue(GreenLanternMechCombos.canBore(p, level, stone, level.getBlockState(stone)), "stone may be bored");
		for (var b : new net.minecraft.world.level.block.Block[] { Blocks.BEDROCK, Blocks.BARRIER, Blocks.OBSIDIAN, Blocks.CHEST,
				Blocks.END_PORTAL_FRAME }) {
			helper.setBlock(new BlockPos(2, 3, 3), b);
			helper.assertFalse(GreenLanternMechCombos.canBore(p, level, stone, level.getBlockState(stone)), b + " is never bored");
		}
		p.setGameMode(GameType.ADVENTURE);
		helper.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
		helper.assertFalse(GreenLanternMechCombos.canBore(p, level, stone, level.getBlockState(stone)), "not in adventure mode");
		p.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(GreenLanternMechCombos.cuttable(Blocks.SHORT_GRASS.defaultBlockState()), "the crescent cuts grass");
		helper.assertTrue(GreenLanternMechCombos.cuttable(Blocks.OAK_LEAVES.defaultBlockState()), "and leaves");
		helper.assertFalse(GreenLanternMechCombos.cuttable(Blocks.STONE.defaultBlockState()), "not stone");
		helper.assertFalse(GreenLanternMechCombos.cuttable(Blocks.WATER.defaultBlockState()), "not water");
		helper.succeed();
	}


	// ================================================================ Hard-Light Katana

	private static HardLightMechEntity katanaReady(GameTestHelper helper, ServerPlayer p) {
		HardLightMechEntity m = pilotReady(helper, p);
		press(p, AbilitySlot.SLOT_3, true); // Shift+X
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void katanaDrawsAndSheathes(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		float before = charge(p);
		HardLightMechEntity m = katanaReady(helper, p);
		helper.assertTrue(m.katanaState() == HardLightMechEntity.KATANA_DRAWING, "drawing");
		helper.assertTrue(before - GreenLanternMechConfig.FORM_COST - charge(p) >= GreenLanternMechConfig.KATANA_DRAW_COST - 0.5f, "60 to draw");
		int drawn = GreenLanternMechConfig.KATANA_DRAW_TICKS + 2;
		int toggle = drawn + GreenLanternMechConfig.KATANA_TOGGLE_COOLDOWN_TICKS + 1;
		helper.runAfterDelay(drawn, () -> helper.assertTrue(m.katanaDrawn(), "drawn after the clip"));
		helper.runAfterDelay(toggle, () -> {
			press(p, AbilitySlot.SLOT_3, true);
			helper.assertTrue(m.katanaState() == HardLightMechEntity.KATANA_SHEATHING, "the same key sheathes it");
		});
		helper.runAfterDelay(toggle + GreenLanternMechConfig.KATANA_SHEATH_TICKS + 2, () -> {
			helper.assertTrue(!m.katanaOut(), "sheathed");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void katanaChangesRAndShiftRAndG(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = katanaReady(helper, p);
		Husk h = husk(helper, 4.0, 6.0);
		float[] hp = new float[1];
		int t0 = GreenLanternMechConfig.KATANA_DRAW_TICKS + 2;
		helper.runAfterDelay(t0, () -> {
			// R: the first cut of the chain (not a punch)
			press(p, AbilitySlot.SLOT_1, false);
			release(p, AbilitySlot.SLOT_1);
			helper.assertTrue(m.slashStep == 0 && !m.ready(HardLightMechEntity.CD_SLASH), "R cuts");
			helper.assertTrue(m.ready(HardLightMechEntity.CD_PUNCH), "and does not punch");
		});
		int t1 = t0 + GreenLanternMechConfig.SLASH_IMPACT_TICKS[0] + 1;
		helper.runAfterDelay(t1, () -> {
			float dealt = 400f - h.getHealth();
			helper.assertTrue(Math.abs(dealt - GreenLanternMechConfig.SLASH_DAMAGE[0]) < 0.6f, "a 24 cut, dealt " + dealt);
			helper.assertTrue(m.hitAt() > 0, "the hit-stop mark");
		});
		int t2 = t0 + GreenLanternMechConfig.SLASH_COOLDOWN_TICKS[0] + 1;
		helper.runAfterDelay(t2, () -> {
			press(p, AbilitySlot.SLOT_1, false);
			release(p, AbilitySlot.SLOT_1);
			helper.assertTrue(m.slashStep == 1, "the chain goes on to the second cut");
		});
		// Shift+R: the iaido draw-cut and its crescent
		int t3 = t2 + GreenLanternMechConfig.SLASH_COOLDOWN_TICKS[1] + 1;
		helper.runAfterDelay(t3, () -> {
			hp[0] = h.getHealth();
			press(p, AbilitySlot.SLOT_1, true);
			release(p, AbilitySlot.SLOT_1);
			helper.assertTrue(!m.ready(HardLightMechEntity.CD_IAIDO), "Shift+R is the iaido");
			helper.assertTrue(m.ready(HardLightMechEntity.CD_ROCKET) && m.rocketFist == null, "not the Rocket Fist");
		});
		// G: the spin sweep, not the slam
		helper.runAfterDelay(t3 + GreenLanternMechConfig.IAIDO_LAUNCH_TICKS + 5, () -> {
			helper.assertTrue(m.waveAt() > 0 && m.waveHit.contains(h.getId()), "the crescent flew through the husk");
			helper.assertTrue(hp[0] - h.getHealth() >= GreenLanternMechConfig.IAIDO_DAMAGE - 0.6f, "a 34 hit, " + (hp[0] - h.getHealth()));
			press(p, AbilitySlot.SLOT_2, false);
			release(p, AbilitySlot.SLOT_2);
			helper.assertTrue(!m.ready(HardLightMechEntity.CD_SPIN) && m.ready(HardLightMechEntity.CD_SLAM), "G spins, it does not slam");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void katanaCostsUpkeepWhileDrawn(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		pilotReady(helper, p);
		float[] mark = new float[3];
		mark[0] = charge(p);
		int drawAt = 40, drawnAt = drawAt + GreenLanternMechConfig.KATANA_DRAW_TICKS + 2;
		helper.runAfterDelay(drawAt, () -> {
			mark[1] = charge(p); // 40 ticks of the mech alone
			press(p, AbilitySlot.SLOT_3, true);
		});
		helper.runAfterDelay(drawnAt, () -> mark[2] = charge(p));
		helper.runAfterDelay(drawnAt + 40, () -> {
			float withKatana = mark[2] - charge(p);
			float alone = mark[0] - mark[1];
			helper.assertTrue(withKatana >= alone + GreenLanternMechConfig.KATANA_UPKEEP_PER_SEC * 2 * 0.8f,
					"the katana adds its upkeep: " + withKatana + " vs " + alone);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void climbingOutSheathesTheKatana(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = katanaReady(helper, p);
		helper.runAfterDelay(GreenLanternMechConfig.KATANA_DRAW_TICKS + 2, () -> {
			helper.assertTrue(m.katanaDrawn(), "drawn");
			GreenLanternAbilityManager.clearConstructs(p); // N
			helper.assertTrue(!m.katanaOut(), "sheathed as the mech dissolves");
			press(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(m.slashStep < 0, "no cuts while it dissolves");
			helper.succeed();
		});
	}
}
