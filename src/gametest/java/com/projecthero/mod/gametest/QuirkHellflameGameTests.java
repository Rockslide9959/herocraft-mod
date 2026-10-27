package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;
import com.projecthero.mod.quirk.hellflame.HellflameMeter;
import com.projecthero.mod.quirk.hellflame.HellflameMoves;
import com.projecthero.mod.quirk.meter.QuirkBand;
import com.projecthero.mod.quirk.meter.QuirkMeters;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Hellflame revamp, v0.17.1 Quirks revamp ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.1): Body Heat's bands and limits
 * (the damage per band, the Inferno burn, the push past 100 and the burnout, Heat Vent working through it), one test per
 * move (damage, cost, cooldown), the tap / hold keys, the combo route, the Prominence Burn ultimate and its burnout, the
 * survival uses (the Forge Hand, melting, water), the fire-placement rules, squad safety and the boss cap. Mock players are
 * not ticked by the server, so tests that run over time drive the Quirk tick themselves ({@link #pump}). Damage tests run in
 * the Blaze band (50 Heat, x1.0) unless they say otherwise.
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

	/** Endeavor facing +Z, every move ready, 50 Heat (Blaze: x1.0). */
	private static ServerPlayer endeavor(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(UniquePowers.forced(() -> Quirks.grant(p, Quirk.HELLFLAME)), "Hellflame is granted");
		Quirks.setMeter(p, 50f);
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

	/** {@link #pump}, and the player is moved by his velocity each tick (a mock is not moved by the server; the client would). */
	private static void pumpMoving(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
			p.move(net.minecraft.world.entity.MoverType.SELF, p.getDeltaMovement());
		});
	}

	/** A stone wall across the far end of the area (a dash or the comet stops there, clear of the neighbouring tests). */
	private static void farWall(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int y = 2; y <= 7; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 7)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
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
			boolean hold = s == AbilitySlot.SLOT_2 || s == AbilitySlot.SLOT_3;
			helper.assertTrue(kit.idsOf(s).length == (hold ? 3 : 2), "the moves on " + s);
			for (String id : kit.idsOf(s)) {
				helper.assertTrue(id.startsWith("quirk_hellflame_") && kit.maxCooldown(id) > 0, "id and cooldown of " + id);
			}
		}
		String[] g = kit.idsOf(AbilitySlot.SLOT_2), x = kit.idsOf(AbilitySlot.SLOT_3), c = kit.idsOf(AbilitySlot.SLOT_6), v = kit.idsOf(AbilitySlot.SLOT_5);
		helper.assertTrue(g[0].equals(HellflameConfig.FIST) && g[1].equals(HellflameConfig.VANISH) && g[2].equals(HellflameConfig.HELLBLAST),
				"G = Flashfire Fist / Vanishing Fist / hold Hellblast");
		helper.assertTrue(x[0].equals(HellflameConfig.BURST) && x[1].equals(HellflameConfig.JET) && x[2].equals(HellflameConfig.FLIGHT),
				"X (movement) = Jet Burst / Jet Burn / hold Fire Jet Flight");
		helper.assertTrue(c[0].equals(HellflameConfig.WAVE) && c[1].equals(HellflameConfig.CLOAK), "C = Flame Wave / Flame Cloak");
		helper.assertTrue(v[0].equals(HellflameConfig.COOL) && v[1].equals(HellflameConfig.WALL), "V (utility) = Heat Vent / Fire Wall");
		helper.assertTrue(kit.usableWhileOverloaded(AbilitySlot.SLOT_5, false), "Heat Vent works while overheated");
		helper.assertFalse(kit.usableWhileOverloaded(AbilitySlot.SLOT_5, true), "the Fire Wall does not");
		helper.assertFalse(kit.usableWhileOverloaded(AbilitySlot.SLOT_1, false), "Hell Spider does not");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theNewMovesAreInThePacingScript(GameTestHelper helper) {
		var h = QuirkMeters.spec(Quirk.HELLFLAME).heavyUse();
		helper.assertTrue(h.moves.size() == 12, "twelve costed moves in the rotation, got " + h.moves.size());
		helper.assertTrue(h.sustainedPerSecond == HellflameConfig.heat().flightSprintPerSecond, "sprint flight the whole time");
		var spec = QuirkMeters.spec(Quirk.HELLFLAME);
		helper.assertTrue(spec.pushPast && spec.pushTicks == HellflameConfig.OVERHEAT_PUSH_TICKS, "the Overheat push past 100 is on");
		helper.assertTrue(spec.suitBuildMult == HellflameConfig.SUIT_BUILD, "the Endeavor suit: +15% headroom");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Body Heat: bands and limits

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void heatBuildsWithFireAndVentsWhenHeStops(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		Quirks.setMeter(p, 0f);
		HellflameMoves.hellSpider(p);
		near(helper, Quirks.meter(p), HellflameConfig.SPIDER_HEAT * HellflameMeter.BUILD, "Hell Spider's Heat (paced)");
		HellflameMoves.coolForTest(p);
		Quirks.setMeter(p, 50f);
		float before = Quirks.meter(p);
		pump(helper, p);
		helper.runAfterDelay(41, () -> {
			float vented = before - Quirks.meter(p);
			near(helper, vented, HellflameConfig.heat().ventPerSecond * 2f, "2 s of venting");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBandsScaleEveryFlame(GameTestHelper helper) {
		floor(helper);
		float[] heat = { 10f, 50f, 90f };
		float[] mult = { 0.85f, 1.0f, 1.3f };
		for (int i = 0; i < 3; i++) {
			ServerPlayer p = endeavor(helper, 1.5 + i * 3, 2.0, 1.0);
			Zombie z = zombie(helper, 1.5 + i * 3, 4.0, 100);
			Quirks.setMeter(p, heat[i]);
			HellflameMoves.flashfireFist(p);
			near(helper, taken(z, 100), HellflameConfig.FIST_DAMAGE[0] * mult[i], "the jab at " + heat[i] + " Heat");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void infernoBurnsHimAndMeltsTheSnow(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 3.5);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(5, 2, 3)), Blocks.SNOW_BLOCK.defaultBlockState(), 3);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 2, 3)), Blocks.ICE.defaultBlockState(), 3);
		Quirks.setMeter(p, 95f);
		float hp = p.getHealth();
		pump(helper, p);
		helper.runAfterDelay(22, () -> {
			helper.assertTrue(p.getHealth() <= hp - HellflameConfig.INFERNO_HP_PER_SECOND + 0.01f, "Inferno burns him half a heart a second");
			helper.assertBlockNotPresent(Blocks.SNOW_BLOCK, new BlockPos(5, 2, 3));
			helper.assertBlockNotPresent(Blocks.ICE, new BlockPos(3, 2, 3));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void atOneHundredHePushesPastThenBurnsOut(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		Quirks.setMeter(p, 99.5f);
		HellflameMoves.heat(p, 10f);
		helper.assertTrue(QuirkMeters.beyond(p) && HellflameMoves.overheating(p), "pushed past 100: the Overheat");
		helper.assertTrue(!Quirks.overloaded(p), "the moves still work while pushed");
		helper.assertTrue(QuirkMeters.band(p) == QuirkBand.BEYOND && QuirkMeters.damageMult(p) == 1.5f, "x1.5 past 100");
		QuirkMeters.endPushForTest(p);
		pump(helper, p);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Quirks.overloaded(p) && Hellflame.overheated(p), "then the burnout");
			helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "with Slowness");
			helper.assertTrue(Quirks.overloadLeft(p) >= HellflameConfig.heat().overheatTicks - 5, "for 8 s");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void burnoutLocksFireMovesButHeatVentEndsIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		HellflameMoves.burnout(p);
		helper.assertTrue(Quirks.overloaded(p) && Quirks.meter(p) >= 99.9f, "burnt out at 100");
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(Quirks.ready(p, HellflameConfig.SPIDER), "R was refused (no Hell Spider, no cooldown)");
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		helper.assertFalse(Quirks.overloaded(p), "V Heat Vent ended the burnout");
		near(helper, Quirks.meter(p), 100f - HellflameConfig.COOL_MAX_VENT, "it vents at most 60");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heatVentDamagesPutsOutAndBoilsOffTheWater(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 3.5);
		Zombie z = zombie(helper, 4.5, 5.5, 100);
		for (int dx = -1; dx <= 1; dx++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(4 + dx, 2, 4)), Blocks.WATER.defaultBlockState(), 3);
		}
		p.igniteForSeconds(5);
		Quirks.setMeter(p, 100f);
		int n = HellflameMoves.coolDown(p);
		helper.assertTrue(n == 1, "it hit the zombie");
		// 4 + 0.18 x 60 = 14.8, in Inferno (x1.3)
		near(helper, taken(z, 100), (HellflameConfig.COOL_BASE_DAMAGE + HellflameConfig.COOL_DAMAGE_PER_HEAT * 60f) * 1.3f, "the vent burst");
		helper.assertTrue(p.getRemainingFireTicks() <= 0, "it put him out");
		for (int dx = -1; dx <= 1; dx++) {
			helper.assertBlockNotPresent(Blocks.WATER, new BlockPos(4 + dx, 2, 4));
		}
		helper.assertTrue(!Quirks.ready(p, HellflameConfig.COOL), "cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ordinaryFireAndTheColdDoNotHurtHim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		float hp = p.getHealth();
		p.hurt(p.damageSources().onFire(), 4f);
		p.hurt(p.damageSources().freeze(), 4f);
		helper.assertTrue(p.getHealth() == hp, "fire-proof and freeze-proof while cool");
		HellflameMoves.burnout(p);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().onFire(), 4f);
		helper.assertTrue(p.getHealth() < hp, "burnt out, the fire hurts");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X: Jet Burst / Shift+X: Jet Burn / hold X: Fire Jet Flight

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void holdingXFliesAndHeatsATapIsAJetBurst(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		ServerPlayer q = endeavor(helper, 1.5, 2.0, 1.5);
		Quirks.setMeter(p, 0f);
		pump(helper, p);
		pump(helper, q);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		QuirkAbilityManager.handle(q, AbilitySlot.SLOT_3, true);
		helper.runAfterDelay(1, () -> QuirkAbilityManager.handle(q, AbilitySlot.SLOT_3, false));
		helper.runAfterDelay(HellflameConfig.HOLD_TICKS + 2, () -> {
			helper.assertTrue(HellflameMoves.flying(p) && Hellflame.flying(p), "held: the jets lit");
			helper.assertTrue(p.getAbilities().mayfly, "kept from the fly kick");
			helper.assertTrue(!HellflameMoves.flying(q) && !Quirks.ready(q, HellflameConfig.BURST), "tapped: a Jet Burst, no flight");
		});
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Quirks.meter(p) > 0.1f, "the flight heats him, gently, got " + Quirks.meter(p));
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, false);
			helper.assertFalse(HellflameMoves.flying(p), "releasing X ends the flight");
			helper.assertFalse(p.getAbilities().mayfly, "mayfly handed back");
			helper.assertTrue(Quirks.ready(p, HellflameConfig.BURST), "the release after a hold is no Jet Burst");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetBurstDashesWithoutHurtingAnything(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 2.5, 100);
		Quirks.setMeter(p, 0f);
		HellflameMoves.jetBurst(p);
		helper.assertTrue(HellflameMoves.bursting(p) && p.getDeltaMovement().z > 1.0, "thrust along the look: " + p.getDeltaMovement());
		helper.assertTrue(taken(z, 100) == 0f, "no damage");
		near(helper, Quirks.meter(p), HellflameConfig.BURST_HEAT * HellflameMeter.BUILD, "+6 Heat, paced");
		helper.assertTrue(!Quirks.ready(p, HellflameConfig.BURST), "cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 30)
	public void jetBurnPunchesThroughEverythingInTheLine(GameTestHelper helper) {
		floor(helper);
		farWall(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 0.5);
		Zombie a = zombie(helper, 4.5, 2.0, 100);
		Zombie b = zombie(helper, 4.5, 4.5, 100);
		HellflameMoves.jetBurn(p);
		helper.assertTrue(HellflameMoves.dashing(p), "the dash runs");
		pumpMoving(helper, p);
		helper.runAfterDelay(HellflameConfig.JET_DASH_TICKS + 2, () -> {
			helper.assertTrue(HellflameMoves.dashHit(p).contains(a.getId()) && HellflameMoves.dashHit(p).contains(b.getId()), "through both");
			helper.assertTrue(taken(a, 100) >= HellflameConfig.JET_DAMAGE - EPS && taken(b, 100) >= HellflameConfig.JET_DAMAGE * 0.85f - EPS,
					"16 each: " + taken(a, 100) + ", " + taken(b, 100));
			helper.assertTrue(a.getRemainingFireTicks() > 0, "set alight");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- R: Hell Spider / Shift+R: Mesh

	@GameTest(template = EMPTY_STRUCTURE)
	public void hellSpiderStringsSweepAcrossTheAim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie ahead = zombie(helper, 4.5, 6.5, 100);
		Vec3 side = Vec3.directionFromRotation(0f, 17f).scale(5.5);
		Zombie off = zombie(helper, 4.5 + side.x, 1.0 + side.z, 100);
		int n = HellflameMoves.hellSpider(p);
		helper.assertTrue(n == 2, "both cut, got " + n);
		near(helper, taken(ahead, 100), HellflameConfig.SPIDER_DAMAGE, "the string ahead (once, however many cross it)");
		near(helper, taken(off, 100), HellflameConfig.SPIDER_DAMAGE, "the swept string");
		helper.assertTrue(Hellflame.val(p, Hellflame.V_SPIDER) != 0L && Hellflame.vec(p, Hellflame.VEC_SPIDER) != null, "the strings are synced");
		helper.assertTrue(HellflameConfig.SPIDER_RANGE == 14.0, "14 blocks");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void meshTrapsForFourSecondsAndABossIsOnlySlowed(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 6.5, 100);
		Zombie boss = zombie(helper, 5.5, 6.5, 400);
		int n = HellflameMoves.mesh(p);
		helper.assertTrue(n == 2 && HellflameMoves.meshed(p).contains(z.getId()), "both are caught");
		helper.assertTrue(boss.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 0, "the boss is only slowed");
		helper.assertTrue(z.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() >= 5, "the zombie is rooted");
		pump(helper, p);
		helper.runAfterDelay(25, () -> helper.assertTrue(taken(z, 100) >= HellflameConfig.MESH_BURN_DAMAGE * 2f - EPS, "it burns 2.5 a half-second"));
		helper.runAfterDelay(HellflameConfig.MESH_TICKS + 2, () -> {
			helper.assertTrue(HellflameMoves.meshed(p).isEmpty(), "gone after 4 s");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G: Flashfire Fist / Vanishing Fist / hold Hellblast

	@GameTest(template = EMPTY_STRUCTURE)
	public void flashfireFistIsAJabACrossAndAJetStraight(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 3.5, 200);
		helper.assertTrue(HellflameMoves.flashfireFist(p) == 1, "the jab");
		near(helper, taken(z, 200), HellflameConfig.FIST_DAMAGE[0], "6");
		helper.assertTrue(Quirks.cooldownRemaining(p, HellflameConfig.FIST) <= HellflameConfig.FIST_STEP_COOLDOWN, "a short step to the next punch");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(HellflameMoves.flashfireFist(p) == 2, "the cross");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		Vec3 before = p.getDeltaMovement();
		helper.assertTrue(HellflameMoves.flashfireFist(p) == 3, "the straight");
		near(helper, taken(z, 200), HellflameConfig.FIST_DAMAGE[0] + HellflameConfig.FIST_DAMAGE[1] + HellflameConfig.FIST_DAMAGE[2], "6 + 6 + 10");
		helper.assertTrue(p.getDeltaMovement().z > before.z + 0.5, "the jet-assisted lunge");
		helper.assertTrue(Quirks.cooldownRemaining(p, HellflameConfig.FIST) > HellflameConfig.FIST_STEP_COOLDOWN, "the full cooldown after the third");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(HellflameMoves.flashfireFist(p) == 1, "and it starts over");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void hellblastChargesThenBeams(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 0.5);
		Zombie z = zombie(helper, 4.5, 7.0, 100);
		pump(helper, p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.runAfterDelay(HellflameConfig.HOLD_TICKS + 1, () -> {
			helper.assertTrue(HellflameMoves.charging(p) && !HellflameMoves.beaming(p), "held G: the fist draws back, charging");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_2, false); // too soon: it fires once it has its half second
			helper.assertTrue(Quirks.ready(p, HellflameConfig.FIST), "the release after a hold is no Flashfire Fist");
		});
		helper.runAfterDelay(HellflameConfig.HOLD_TICKS + HellflameConfig.HELLBLAST_MIN_CHARGE + 2, () -> {
			helper.assertTrue(HellflameMoves.beaming(p), "the beam fires");
			long left = HellflameMoves.beamUntil(p) - helper.getLevel().getGameTime();
			helper.assertTrue(left > 0 && left <= HellflameConfig.HELLBLAST_BASE_TICKS + HellflameConfig.HELLBLAST_MIN_CHARGE, "a short charge, a short beam");
			z.invulnerableTime = 0;
			float before = taken(z, 100);
			HellflameMoves.beamTickForTest(p);
			helper.assertTrue(taken(z, 100) - before >= HellflameConfig.HELLBLAST_DAMAGE - EPS, "7 a beat");
		});
		helper.runAfterDelay(HellflameConfig.HOLD_TICKS + HellflameConfig.HELLBLAST_MIN_CHARGE + HellflameConfig.HELLBLAST_BASE_TICKS + 16, () -> {
			helper.assertTrue(!HellflameMoves.beaming(p) && !Quirks.ready(p, HellflameConfig.HELLBLAST), "it ends on its own, then the cooldown");
			helper.assertTrue(Quirks.meter(p) > 50f, "the beam heats him");
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

	// ---------------------------------------------------------------- Z: Barrage / Shift+Z: Pillar

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void barrageThrowsEightJabsAndTheLastLaunches(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 3.0, 200);
		Quirks.setMeter(p, 50f);
		HellflameMoves.barrage(p);
		pump(helper, p);
		helper.runAfterDelay(HellflameConfig.BARRAGE_JABS * HellflameConfig.BARRAGE_INTERVAL + 2, () -> {
			helper.assertTrue(HellflameMoves.barrageLeft(p) == 0, "every jab thrown");
			helper.assertTrue(taken(z, 200) >= HellflameConfig.BARRAGE_DAMAGE * 6f - EPS, "most jabs landed, took " + taken(z, 200));
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
		helper.assertTrue(taken(z, 100) == 0f, "nothing yet: the ground only cracks and glows");
		helper.assertTrue(HellflameMoves.eruptForTest(p) == 1, "the pillar hit it");
		near(helper, taken(z, 100), HellflameConfig.PILLAR_DAMAGE, "Hellflame Pillar");
		helper.assertTrue(z.getDeltaMovement().y > 0.5, "and launched it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void meshThenPillarIsTheCagedInferno(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 6.0, 200);
		HellflameMoves.mesh(p);
		float mesh = taken(z, 200);
		HellflameMoves.firePillar(p);
		HellflameMoves.eruptForTest(p);
		helper.assertTrue(QuirkCombos.last(p) == HellflameMeter.MESH_PILLAR, "the route landed");
		near(helper, taken(z, 200) - mesh, HellflameConfig.PILLAR_DAMAGE * 1.5f, "the pillar hits 1.5x");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C: Flame Wave / Shift+C: Flame Cloak

	@GameTest(template = EMPTY_STRUCTURE)
	public void flameWaveBurnsAWideConeInFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.0, 2.0, 2.0);
		Zombie ahead = zombie(helper, 4.0, 7.0, 100);
		Vec3 d = Vec3.directionFromRotation(0f, 35f).scale(5.0);
		Zombie wide = zombie(helper, 4.0 + d.x, 2.0 + d.z, 100);
		Zombie behind = zombie(helper, 4.0, 0.0, 100);
		int n = HellflameMoves.flameWave(p);
		helper.assertTrue(n == 2, "the two in front, got " + n);
		near(helper, taken(ahead, 100), HellflameConfig.WAVE_DAMAGE, "11");
		helper.assertTrue(taken(wide, 100) > 0f && wide.getRemainingFireTicks() > 0, "35 degrees off: caught and burning");
		helper.assertTrue(taken(behind, 100) == 0f, "behind him: safe");
		helper.assertTrue(!Quirks.ready(p, HellflameConfig.WAVE), "cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flameCloakBurnsWhatIsCloseAndArmoursHim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 2.5);
		Zombie z = zombie(helper, 4.5, 4.0, 100);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		helper.assertTrue(HellflameMoves.toggleCloak(p) && Hellflame.cloaked(p), "the Cloak is on");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - armour - HellflameConfig.CLOAK_ARMOR) < 0.01, "+2 armour");
		helper.assertTrue(HellflameMoves.cloakPulseForTest(p) == 1, "the aura burned the zombie");
		near(helper, taken(z, 100), HellflameConfig.CLOAK_AURA_DAMAGE, "aura damage");
		helper.assertTrue(Hellflame.val(p, Hellflame.V_CLOAK) - helper.getLevel().getGameTime() <= HellflameConfig.CLOAK_TICKS, "for 12 s");
		HellflameMoves.toggleCloak(p);
		helper.assertTrue(!Hellflame.cloaked(p) && Math.abs(p.getAttributeValue(Attributes.ARMOR) - armour) < 0.01, "put out early: the armour goes");
		helper.assertTrue(!Quirks.ready(p, HellflameConfig.CLOAK), "then the cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hotHandsSetPunchedTargetsAlightFromBlaze(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 2.5);
		Zombie a = zombie(helper, 4.5, 4.0, 100);
		Zombie b = zombie(helper, 2.5, 4.0, 100);
		Quirks.setMeter(p, 20f);
		HellflameMoves.hotHands(p, a);
		helper.assertTrue(a.getRemainingFireTicks() <= 0, "Kindle: no hot hands");
		Quirks.setMeter(p, 45f);
		HellflameMoves.hotHands(p, b);
		helper.assertTrue(b.getRemainingFireTicks() > 0, "Blaze: the punch ignites");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V: Heat Vent / Shift+V: Fire Wall

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fireWallStopsArrowsAndBurnsWhatCrossesButLightsNoBlocks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 4.0, 100);
		GameRules.BooleanValue rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
		boolean was = rule.get();
		rule.set(true, helper.getLevel().getServer());
		HellflameMoves.fireWall(p);
		helper.assertTrue(HellflameMoves.wallUp(p) && Hellflame.wallUp(p), "the wall is up (synced for the flame sheets)");
		helper.assertTrue(HellflameMoves.firesLit(p) == 0, "it never lights a block");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 3.2, 7.0));
		arrow.moveTo(at.x, at.y, at.z);
		arrow.setDeltaMovement(0, 0, -0.8);
		helper.getLevel().addFreshEntity(arrow);
		pump(helper, p);
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(arrow.isRemoved(), "the arrow burned up in the wall");
			helper.assertTrue(taken(z, 100) >= HellflameConfig.WALL_DAMAGE - EPS && z.getRemainingFireTicks() > 0, "the zombie in it burns");
			for (int x = 0; x <= 7; x++) {
				for (int zz = 0; zz <= 7; zz++) {
					helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(x, 2, zz));
				}
			}
			rule.set(was, helper.getLevel().getServer());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- survival: the Forge Hand

	@GameTest(template = EMPTY_STRUCTURE)
	public void theForgeHandCooksWhatIsInTheOffHand(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 2.5);
		Quirks.setMeter(p, 0f);
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BEEF, 2));
		p.setShiftKeyDown(true);
		helper.assertTrue(HellflameMoves.forgeWants(p, InteractionHand.OFF_HAND), "sneak + use, empty main hand, raw beef: the Forge Hand takes it");
		helper.assertTrue(HellflameMoves.forge(p), "it cooked one");
		helper.assertTrue(p.getOffhandItem().getCount() == 1, "one beef used");
		helper.assertTrue(p.getInventory().countItem(Items.COOKED_BEEF) == 1, "a steak");
		near(helper, Quirks.meter(p), HellflameConfig.FORGE_HEAT * HellflameMeter.BUILD, "4 Heat (paced)");
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
		helper.assertTrue(!HellflameMoves.forgeWants(p, InteractionHand.OFF_HAND), "a stick does not smelt: the use goes through");
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.RAW_IRON));
		p.setShiftKeyDown(false);
		helper.assertTrue(!HellflameMoves.forgeWants(p, InteractionHand.OFF_HAND), "only while sneaking");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Plus Ultra: Prominence Burn

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void prominenceBurnIgnitesThenTheCometBurnsThrough(GameTestHelper helper) {
		floor(helper);
		farWall(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 0.5);
		Zombie z = zombie(helper, 4.5, 5.0, 200);
		PlusUltra.set(p, 100f);
		PlusUltra.fire(p);
		helper.assertTrue(PlusUltra.firedCount(p) == 1, "the ultimate fired");
		helper.assertTrue(HellflameMoves.inProminence(p) && Hellflame.prominence(p), "he ignites");
		helper.assertTrue(Quirks.meter(p) >= 99.9f, "Heat pinned at 100");
		pumpMoving(helper, p);
		helper.runAfterDelay(HellflameConfig.PROM_IGNITE_TICKS - 4, () -> {
			helper.assertTrue(HellflameMoves.inProminence(p) && taken(z, 200) == 0f, "still the sun: nothing hit yet");
		});
		helper.runAfterDelay(HellflameConfig.PROM_IGNITE_TICKS + HellflameConfig.PROM_MAX_TICKS + 4, () -> {
			helper.assertTrue(HellflameMoves.prominenceHit(p).contains(z.getId()) || taken(z, 200) > 40f, "the comet burned through it");
			helper.assertTrue(taken(z, 200) >= HellflameConfig.PROM_PASS_DAMAGE, "20 on the way through (and the blast), took " + taken(z, 200));
			helper.assertTrue(!HellflameMoves.inProminence(p), "it detonated");
			helper.assertTrue(HellflameMoves.burntOut(p) && Quirks.overloaded(p), "the burnout");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void theBurnoutAfterProminenceLocksEverything(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.0);
		Zombie z = zombie(helper, 4.5, 3.5, 300);
		HellflameMoves.prominenceBurn(p);
		int n = HellflameMoves.prominenceForTest(p, true);
		helper.assertTrue(n == 1, "the detonation hit");
		near(helper, taken(z, 300), 300 * HellflameConfig.BOSS_MAX_FRACTION_PER_HIT, "a boss takes at most 10% (45 x 1.3 is capped)");
		helper.assertTrue(HellflameMoves.burntOut(p) && Hellflame.burntOut(p), "burnt out");
		helper.assertTrue(HellflameMoves.coolDown(p) == -1 && Quirks.overloaded(p), "Heat Vent is refused while burnt out");
		helper.assertTrue(QuirkKits.of(Quirk.HELLFLAME).keyLocked(p, AbilitySlot.SLOT_5, false), "the HUD shows the keys locked");
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
			mate.igniteForSeconds(4);
			Quirks.setMeter(p, 60f);
			HellflameMoves.coolDown(p);
			helper.assertTrue(mate.getRemainingFireTicks() <= 0, "Heat Vent puts out a burning squadmate");
			HellflameMoves.hellSpider(p);
			HellflameMoves.flameWave(p);
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
		helper.succeed();
	}
}
