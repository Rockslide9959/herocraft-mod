package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;
import com.projecthero.mod.quirk.hellflame.HellflameMeter;
import com.projecthero.mod.quirk.hellflame.HellflameMoves;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Hellflame revamp (Endeavor, {@code docs/QUIRK_REVAMP_V0167.md}): Body Heat is the heart of the kit, so most tests are
 * about its limits -- the sustained moves (Fire Jet Flight, Prominence Burn) heat every tick and end at Overheat, Overheat locks
 * the fire moves and burns him until it drains (only V Cool Down works), Plus Ultra builds no Heat and doubles damage, then the
 * burnout pins the Heat at 100 and locks even Cool Down. Plus one test per attack, the fire-placement rules, squad safety and the
 * boss cap. Mock players are not ticked by the server, so tests that run over time drive the Quirk tick themselves ({@link #pump}).
 */
public class QuirkHellflameGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** Endeavor facing +Z, 0 Heat, every move ready. */
	private static ServerPlayer endeavor(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(UniquePowers.forced(() -> Quirks.grant(p, Quirk.HELLFLAME)), "Hellflame is granted");
		Quirks.setMeter(p, 0f);
		Quirks.edit(p, n -> {
			n.animId = QuirkState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setYHeadRot(180f);
		m.yBodyRot = 180f;
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z, double health) {
		return mob(helper, EntityType.ZOMBIE, x, 2.0, z, health);
	}

	private static float taken(Mob m, double health) {
		return (float) health - m.getHealth();
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) <= EPS, what + ": expected " + expected + ", got " + actual);
	}

	// ---------------------------------------------------------------- the kit

	@GameTest(template = EMPTY_STRUCTURE)
	public void hellflameKitFollowsTheSlotRules(GameTestHelper helper) {
		QuirkKit kit = QuirkKits.of(Quirk.HELLFLAME);
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(kit.idsOf(s).length == 2, "a tap and a Shift move on " + s);
			for (String id : kit.idsOf(s)) {
				helper.assertTrue(id.startsWith("quirk_hellflame_") && kit.maxCooldown(id) > 0, "id and cooldown of " + id);
			}
		}
		// X is the movement key, V the utility key
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_3)[0].equals(HellflameConfig.FLIGHT) && kit.idsOf(AbilitySlot.SLOT_3)[1].equals(HellflameConfig.JET),
				"X = Fire Jet Flight / Jet Burn");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_5)[0].equals(HellflameConfig.COOL) && kit.idsOf(AbilitySlot.SLOT_5)[1].equals(HellflameConfig.PLUS),
				"V = Cool Down / Plus Ultra");
		helper.assertTrue(kit.usableWhileOverloaded(AbilitySlot.SLOT_5, false), "Cool Down works while overheated");
		helper.assertFalse(kit.usableWhileOverloaded(AbilitySlot.SLOT_5, true), "Plus Ultra does not");
		helper.assertFalse(kit.usableWhileOverloaded(AbilitySlot.SLOT_1, false), "Hell Spider does not");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Body Heat limits

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void heatBuildsWithFireAndVentsWhenHeStops(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		HellflameMoves.hellSpider(p);
		near(helper, Quirks.meter(p), HellflameConfig.SPIDER_HEAT * HellflameMeter.BUILD, "Hell Spider's Heat (v0.17.1: paced)");
		HellflameMoves.coolForTest(p);
		Quirks.setMeter(p, 50f); // v0.17.1: a paced Hell Spider adds ~1 Heat, too little to time 2 s of venting on
		float before = Quirks.meter(p);
		pump(helper, p);
		helper.runAfterDelay(41, () -> {
			float vented = before - Quirks.meter(p);
			near(helper, vented, HellflameConfig.heat().ventPerSecond * 2f, "2 s of venting");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void overheatLocksFireMovesButCoolDownEndsIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		Quirks.setMeter(p, 99.5f); // v0.17.1: heat is paced (x HellflameMeter.BUILD)
		HellflameMoves.heat(p, 10f);
		helper.assertTrue(Quirks.overloaded(p) && Quirks.meter(p) >= 99.9f, "overheated at 100");
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(Quirks.ready(p, HellflameConfig.SPIDER), "R was refused (no Hell Spider, no cooldown)");
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		helper.assertFalse(Quirks.overloaded(p), "V Cool Down ended the Overheat");
		helper.assertTrue(Quirks.meter(p) <= 0.01f, "and dumped the Heat");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void overheatBurnsHimAndDrainsTheHeat(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		HellflameMoves.overheat(p);
		float hp = p.getHealth();
		pump(helper, p);
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(p.getHealth() < hp - 1.5f, "he burns from the inside, " + hp + " -> " + p.getHealth());
			HellflameConfig.Heat h = HellflameConfig.heat();
			float expected = 100f - (100f - h.overheatRecoverTo) / h.overheatTicks * 45f;
			helper.assertTrue(Math.abs(Quirks.meter(p) - expected) < 3f, "the Heat drains toward 40, got " + Quirks.meter(p));
			helper.assertTrue(Quirks.overloaded(p), "still overheated");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ordinaryFireDoesNotHurtHimUnlessOverheated(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		float hp = p.getHealth();
		p.hurt(p.damageSources().onFire(), 4f);
		helper.assertTrue(p.getHealth() == hp, "fire-proof while cool");
		HellflameMoves.overheat(p);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().onFire(), 4f);
		helper.assertTrue(p.getHealth() < hp, "overheated, the fire hurts");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X: Fire Jet Flight (hold) / Shift+X: Jet Burn

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void fireJetFlightHeatsWhileHeldAndEndsOnRelease(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(HellflameMoves.flying(p) && Hellflame.flying(p), "X lit the jets");
		helper.assertTrue(p.getAbilities().mayfly, "kept from the fly kick");
		pump(helper, p);
		helper.runAfterDelay(40, () -> {
			float h = Quirks.meter(p);
			float min = HellflameConfig.heat().flightHoverPerSecond * 1.6f * HellflameMeter.BUILD;
			helper.assertTrue(h >= min, "flight heats him every tick, got " + h);
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, false);
			helper.assertFalse(HellflameMoves.flying(p), "releasing X ends the flight");
			helper.assertFalse(p.getAbilities().mayfly, "mayfly handed back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void flightOverheatsAndDropsHimSoftly(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 4.0, 1.5);
		Quirks.setMeter(p, 99.85f); // v0.17.1: flight heat is paced, so start right at the edge
		HellflameMoves.toggleFlight(p);
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(Quirks.overloaded(p), "the flight overheated him");
			helper.assertFalse(HellflameMoves.flying(p), "and the jets cut out");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetBurnDashesIntoABurningPunch(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 2.5);
		Zombie z = zombie(helper, 4.5, 3.5, 100);
		HellflameMoves.jetBurn(p);
		near(helper, taken(z, 100), HellflameConfig.JET_DAMAGE, "Jet Burn");
		helper.assertTrue(z.getRemainingFireTicks() > 0, "set alight");
		near(helper, Quirks.meter(p), HellflameConfig.JET_HEAT * HellflameMeter.BUILD, "+12 Heat, paced");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R: Hell Spider / Shift+R: Mesh

	@GameTest(template = EMPTY_STRUCTURE)
	public void hellSpiderStringsSweepAcrossTheAim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie ahead = zombie(helper, 4.5, 6.5, 100);
		// 17 degrees to his right: only the sweep reaches it
		Vec3 side = Vec3.directionFromRotation(0f, 17f).scale(5.5);
		Zombie off = zombie(helper, 4.5 + side.x, 1.0 + side.z, 100);
		int n = HellflameMoves.hellSpider(p);
		helper.assertTrue(n == 2, "both cut, got " + n);
		near(helper, taken(ahead, 100), HellflameConfig.SPIDER_DAMAGE, "the string ahead (once, however many cross it)");
		near(helper, taken(off, 100), HellflameConfig.SPIDER_DAMAGE, "the swept string");
		helper.assertTrue(Hellflame.val(p, Hellflame.V_SPIDER) != 0L && Hellflame.vec(p, Hellflame.VEC_SPIDER) != null, "the strings are synced");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void meshTrapsAndBurns(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 6.5, 100);
		int n = HellflameMoves.mesh(p);
		helper.assertTrue(n == 1 && HellflameMoves.meshed(p).contains(z.getId()), "the zombie is trapped");
		pump(helper, p);
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(taken(z, 100) >= HellflameConfig.MESH_BURN_DAMAGE * 2f - EPS, "it keeps burning, took " + taken(z, 100));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C: Fire Wall / Shift+C: Flame Cloak

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fireWallStopsArrowsAndBurnsWhatCrosses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 4.0, 100);
		HellflameMoves.fireWall(p);
		helper.assertTrue(HellflameMoves.wallUp(p), "the wall is up");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 3.2, 7.0));
		arrow.moveTo(at.x, at.y, at.z);
		arrow.setDeltaMovement(0, 0, -0.8);
		helper.getLevel().addFreshEntity(arrow);
		pump(helper, p);
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(arrow.isRemoved(), "the arrow burned up in the wall");
			helper.assertTrue(taken(z, 100) >= HellflameConfig.WALL_DAMAGE - EPS && z.getRemainingFireTicks() > 0, "the zombie in it burns");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fireWallLightsNoFireWithMobGriefingOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		GameRules.BooleanValue rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
		boolean was = rule.get();
		try {
			rule.set(false, helper.getLevel().getServer());
			helper.assertTrue(HellflameMoves.fireWall(p) == 0, "no fire blocks with mobGriefing off");
			for (int x = 0; x <= 7; x++) {
				for (int z = 0; z <= 7; z++) {
					helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(x, 2, z));
				}
			}
			rule.set(true, helper.getLevel().getServer());
			Quirks.edit(p, n -> n.abilityReadyAt.clear());
			int fires = HellflameMoves.fireWall(p);
			if (AbilityHelpers.canGrief()) {
				helper.assertTrue(fires > 0, "with terrain damage on, the wall lights real (temporary) fire");
				int found = 0;
				for (int x = 0; x <= 7; x++) {
					for (int z = 0; z <= 7; z++) {
						BlockPos bp = helper.absolutePos(new BlockPos(x, 2, z));
						BlockState st = helper.getLevel().getBlockState(bp);
						if (st.is(Blocks.FIRE)) {
							helper.assertTrue(GeoTerrain.isTemp(helper.getLevel(), bp), "every fire is on the restore ledger");
							found++;
						}
					}
				}
				helper.assertTrue(found >= 1 && found <= fires, "the fires are where they were counted, " + found + " of " + fires);
			} else {
				helper.assertTrue(fires == 0, "terrain damage off: no fire");
			}
		} finally {
			rule.set(was, helper.getLevel().getServer());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void fireWallFiresGoOutOnTime(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, helper.getLevel().getServer());
		int fires = HellflameMoves.fireWall(p);
		if (fires == 0) {
			helper.succeed(); // terrain damage off in this config: nothing to put out
			return;
		}
		helper.assertTrue(HellflameMoves.firesLit(p) == fires, "every fire is on his clock");
		pump(helper, p);
		helper.runAfterDelay(HellflameConfig.WALL_TICKS + 15, () -> {
			for (int x = 0; x <= 7; x++) {
				for (int z = 0; z <= 7; z++) {
					helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(x, 2, z));
				}
			}
			helper.assertTrue(HellflameMoves.firesLit(p) == 0, "and off it");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flameCloakBurnsWhatIsClose(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 2.5);
		Zombie z = zombie(helper, 4.5, 4.0, 100);
		helper.assertTrue(HellflameMoves.toggleCloak(p), "the Cloak is on");
		int n = HellflameMoves.cloakPulseForTest(p);
		helper.assertTrue(n == 1, "the aura burned the zombie");
		near(helper, taken(z, 100), HellflameConfig.CLOAK_AURA_DAMAGE, "aura damage");
		helper.assertTrue(Quirks.meter(p) > 0f, "the Cloak heats him");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z: Barrage / Shift+Z: Pillar

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void barrageThrowsEightJabs(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 3.0, 200);
		HellflameMoves.barrage(p);
		pump(helper, p);
		helper.runAfterDelay(HellflameConfig.BARRAGE_JABS * HellflameConfig.BARRAGE_INTERVAL + 4, () -> {
			helper.assertTrue(HellflameMoves.barrageLeft(p) == 0, "every jab thrown");
			helper.assertTrue(taken(z, 200) >= HellflameConfig.BARRAGE_DAMAGE * 6f - EPS, "most jabs landed, took " + taken(z, 200));
			near(helper, Quirks.meter(p), HellflameConfig.BARRAGE_HEAT_PER_JAB * HellflameConfig.BARRAGE_JABS * HellflameMeter.BUILD, "+2 Heat a jab, paced");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void firePillarEruptsUnderTheTarget(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 6.0, 100);
		Vec3 at = HellflameMoves.firePillar(p);
		helper.assertTrue(Math.hypot(at.x - z.getX(), at.z - z.getZ()) < 0.6, "under the target");
		helper.assertTrue(taken(z, 100) == 0f, "nothing yet: the ground only glows");
		helper.assertTrue(HellflameMoves.eruptForTest(p) == 1, "the pillar hit it");
		near(helper, taken(z, 100), HellflameConfig.PILLAR_DAMAGE, "Fire Pillar");
		helper.assertTrue(z.getDeltaMovement().y > 0.5, "and launched it");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G: Prominence Burn (hold) / Shift+G: Vanishing Fist

	@GameTest(template = EMPTY_STRUCTURE)
	public void prominenceBeamBurnsAlongItsLength(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 0.5);
		Zombie z = zombie(helper, 4.5, 7.0, 100);
		helper.assertTrue(HellflameMoves.startProminence(p), "the charge starts");
		helper.assertTrue(HellflameMoves.charging(p) && !HellflameMoves.beaming(p), "charging, not firing yet");
		HellflameMoves.fireBeamForTest(p);
		helper.assertTrue(HellflameMoves.beaming(p), "the beam fires");
		int n = HellflameMoves.beamTickForTest(p);
		helper.assertTrue(n == 1, "it burned the zombie");
		near(helper, taken(z, 100), HellflameConfig.PROMINENCE_DAMAGE, "a beam beat");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void prominenceBurnRampsHeatUntilItOverheats(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 0.5);
		HellflameMoves.startProminence(p);
		pump(helper, p);
		helper.runAfterDelay(HellflameConfig.PROMINENCE_CHARGE_TICKS + 20, () -> {
			helper.assertTrue(HellflameMoves.beaming(p), "still firing while held");
			float h = Quirks.meter(p);
			// v0.17.1 (user: heat must build gently): the beam still heats him, paced -- then take him to the edge
			helper.assertTrue(h > 0.5f && h < 20f, "the beam heats him, gently, got " + h);
			Quirks.setMeter(p, 99.5f);
		});
		helper.runAfterDelay(HellflameConfig.PROMINENCE_CHARGE_TICKS + 75, () -> {
			helper.assertTrue(Quirks.overloaded(p), "held long enough, it overheats him");
			helper.assertFalse(HellflameMoves.beaming(p), "and the beam gives out");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vanishingFistLeavesADecoyAndStrikesFromBehind(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 5.0, 100);
		Vec3 start = p.position();
		helper.assertTrue(HellflameMoves.vanishingFist(p) == z, "aimed at the zombie");
		helper.assertTrue(Hellflame.decoyUp(p), "the flame decoy burns where he stood");
		helper.assertTrue(p.getZ() > z.getZ() + 0.5, "he is behind it now, z " + p.getZ() + " vs " + z.getZ());
		helper.assertTrue(p.position().distanceTo(start) > 3.0, "he moved");
		HellflameMoves.vanishStrikeForTest(p);
		near(helper, taken(z, 100), HellflameConfig.VANISH_DAMAGE, "the strike");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V: Cool Down / Shift+V: Plus Ultra

	@GameTest(template = EMPTY_STRUCTURE)
	public void plusUltraDoublesDamageBuildsNoHeatThenBurnsOut(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 6.0, 100);
		helper.assertTrue(HellflameMoves.plusUltraStart(p) && Hellflame.plusUltra(p), "full power");
		HellflameMoves.hellSpider(p);
		near(helper, taken(z, 100), HellflameConfig.SPIDER_DAMAGE * HellflameConfig.PLUS_DAMAGE_MULT, "doubled");
		helper.assertTrue(Quirks.meter(p) == 0f, "no Heat builds");
		HellflameMoves.endPlusForTest(p);
		helper.assertTrue(Quirks.overloaded(p) && HellflameMoves.burntOut(p) && Hellflame.burntOut(p), "burnt out");
		helper.assertTrue(Quirks.meter(p) >= 99.9f, "Heat pinned at max");
		helper.assertTrue(HellflameMoves.coolDown(p) == -1 && Quirks.overloaded(p), "Cool Down is refused while burnt out");
		helper.assertTrue(QuirkKits.of(Quirk.HELLFLAME).keyLocked(p, AbilitySlot.SLOT_5, false), "the HUD shows the keys locked");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void burnoutKeepsTheHeatPinned(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		HellflameMoves.plusUltraStart(p);
		HellflameMoves.endPlusForTest(p);
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(Quirks.meter(p) >= 99.9f, "still pinned at 100 after 1.5 s, got " + Quirks.meter(p));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- safety

	@GameTest(template = EMPTY_STRUCTURE)
	public void squadmatesAreNeverBurned(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 3.5);
		ServerPlayer mate = mock(helper, 4.5, 2.0, 5.5);
		Zombie z = zombie(helper, 2.5, 3.5, 100);
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("hf" + p.getUUID().toString().substring(0, 8), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			float before = mate.getHealth();
			Quirks.setMeter(p, 60f);
			HellflameMoves.coolDown(p);
			HellflameMoves.hellSpider(p);
			HellflameMoves.toggleCloak(p);
			HellflameMoves.cloakPulseForTest(p);
			helper.assertTrue(mate.getHealth() == before && mate.getRemainingFireTicks() <= 0, "the squadmate is untouched");
			helper.assertTrue(taken(z, 100) > 0f, "the zombie was hit");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesAreCappedAndNotThrown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie boss = zombie(helper, 4.5, 6.0, 300);
		HellflameMoves.firePillar(p);
		HellflameMoves.eruptForTest(p);
		float t = taken(boss, 300);
		helper.assertTrue(t > 0f && t <= HellflameConfig.PILLAR_DAMAGE + EPS, "a normal hit under the cap, " + t);
		helper.assertTrue(boss.getDeltaMovement().y < 0.5, "a boss is not launched, " + boss.getDeltaMovement());
		boss.invulnerableTime = 0;
		HellflameMoves.plusUltraStart(p);
		Quirks.setMeter(p, 100f);
		HellflameMoves.coolDown(p);
		float t2 = taken(boss, 300) - t;
		near(helper, t2, 300 * HellflameConfig.BOSS_MAX_FRACTION_PER_HIT, "a Plus Ultra Cool Down (44) is capped at 10% max health");
		helper.succeed();
	}
}
