package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;
import com.projecthero.mod.quirk.hellflame.HellflameMoves;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 the Hellflame Quirk (Endeavor): one test per move -- the server effect and its number -- plus Heat venting,
 * Overheat (the keys refused, Shift+C Vent still works and ends it), the Cloak's fire-proofing / retaliation, the boss
 * cap and squad safety. Mock players are not ticked by the server, so tests that run over time drive the Quirk tick
 * themselves ({@link #pump}).
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
	public void hellflameKitRegistered(GameTestHelper helper) {
		QuirkKit kit = QuirkKits.of(Quirk.HELLFLAME);
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_1).length == 2 && kit.idsOf(AbilitySlot.SLOT_6)[1].equals(HellflameConfig.VENT), "12 moves");
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			for (String id : kit.idsOf(s)) {
				helper.assertTrue(id.startsWith("quirk_hellflame_") && kit.maxCooldown(id) > 0, "id and cooldown of " + id);
			}
		}
		helper.assertTrue(kit.usableWhileOverloaded(AbilitySlot.SLOT_6, true), "Shift+C works while overheated");
		helper.assertFalse(kit.usableWhileOverloaded(AbilitySlot.SLOT_6, false), "the Cloak does not");
		helper.assertFalse(kit.usableWhileOverloaded(AbilitySlot.SLOT_1, false), "Hell Spider does not");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void hellSpiderSlicesALineForNine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 0.5);
		Zombie z = zombie(helper, 4.5, 6.5, 100);
		int n = HellflameMoves.hellSpider(p);
		helper.assertTrue(n == 1, "one target sliced, " + n);
		near(helper, taken(z, 100), HellflameConfig.SPIDER_DAMAGE, "Hell Spider damage (once, however many threads)");
		helper.assertTrue(z.getRemainingFireTicks() > 0, "set alight");
		near(helper, Quirks.meter(p), HellflameConfig.SPIDER_HEAT, "Heat");
		helper.assertFalse(Quirks.ready(p, HellflameConfig.SPIDER), "cooldown set");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void spiderNetRootsAndBurns(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 0.5);
		Zombie z = zombie(helper, 4.5, 6.5, 100);
		int n = HellflameMoves.spiderNet(p);
		helper.assertTrue(n == 1 && HellflameMoves.netted(p).contains(z.getId()), "the zombie is caught in the web, " + n);
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "rooted");
		near(helper, Quirks.meter(p), HellflameConfig.NET_HEAT, "Heat");
		pump(helper, p);
		helper.runAfterDelay(35, () -> {
			helper.assertTrue(taken(z, 100) >= HellflameConfig.NET_BURN_DAMAGE * 3 - EPS, "it burns while rooted, took " + taken(z, 100));
			helper.assertTrue(Math.abs(z.getDeltaMovement().x) < 1.0e-3 && Math.abs(z.getDeltaMovement().z) < 1.0e-3, "it can't move");
		});
		helper.runAfterDelay(HellflameConfig.NET_ROOT_TICKS + 5, () -> {
			helper.assertTrue(HellflameMoves.netted(p).isEmpty(), "the web is gone after 3 s");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetBurnHitsTheConeOnly(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 3.5);
		Zombie front = zombie(helper, 4.5, 6.5, 100);
		Zombie back = zombie(helper, 4.5, 0.5, 100);
		int n = HellflameMoves.jetBurn(p);
		helper.assertTrue(n == 1, "one in the cone, " + n);
		near(helper, taken(front, 100), HellflameConfig.JET_DAMAGE, "Jet Burn damage");
		helper.assertTrue(front.getRemainingFireTicks() > 0, "set alight");
		near(helper, taken(back, 100), 0f, "the one behind is untouched");
		near(helper, Quirks.meter(p), HellflameConfig.JET_HEAT, "Heat");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hellflameBeamBurnsAndHeatsFast(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 0.5);
		Zombie z = zombie(helper, 4.5, 6.5, 100);
		helper.assertTrue(HellflameMoves.startBeam(p) && Hellflame.beaming(p), "the beam is on (synced)");
		int n = HellflameMoves.beamTickForTest(p);
		helper.assertTrue(n == 1, "the beam hit, " + n);
		near(helper, taken(z, 100), HellflameConfig.BEAM_DAMAGE, "one beam hit");
		near(helper, Quirks.meter(p), HellflameConfig.BEAM_HEAT_PER_TICK, "Heat per beam tick");
		HellflameMoves.endBeam(p);
		helper.assertFalse(Hellflame.beaming(p), "released: off");
		helper.assertFalse(Quirks.ready(p, HellflameConfig.BEAM), "cooldown starts on release");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void flameBoostFliesAndHeats(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 4.5);
		helper.assertTrue(HellflameMoves.startBoost(p) && Hellflame.boosting(p), "the jets are lit (synced)");
		helper.assertTrue(p.getAbilities().mayfly && !p.getAbilities().flying, "may float, not creative-flying");
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			float h = Quirks.meter(p);
			helper.assertTrue(Math.abs(h - 20 * HellflameConfig.BOOST_HEAT_PER_TICK) <= 1.0f, "5 Heat a second, got " + h);
			HellflameMoves.endBoost(p);
			helper.assertFalse(Hellflame.boosting(p), "released: off");
			helper.assertFalse(p.getAbilities().mayfly, "float tolerance handed back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void burstDodgeThrowsBackAndScorches(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 4.5);
		Zombie z = zombie(helper, 5.5, 4.5, 100);
		Vec3 v = HellflameMoves.burstDodge(p);
		helper.assertTrue(v.z < -0.3 && v.y > 0.0, "standing still: thrown backwards, " + v);
		near(helper, taken(z, 100), HellflameConfig.DODGE_DAMAGE, "the blast scorches what is next to him");
		near(helper, Quirks.meter(p), HellflameConfig.DODGE_HEAT, "Heat");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE)
	public void flashfireFistPunchesForEighteen(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 2.5);
		Zombie z = zombie(helper, 4.5, 4.0, 100);
		HellflameMoves.flashfire(p, false);
		near(helper, taken(z, 100), HellflameConfig.FIST_DAMAGE, "Flashfire Fist damage");
		helper.assertTrue(z.getRemainingFireTicks() > 0, "set alight");
		helper.assertFalse(HellflameMoves.dashing(p), "the punch ends the dash");
		near(helper, Quirks.meter(p), HellflameConfig.FIST_HEAT, "Heat");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flashfireUppercutLaunches(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 2.5);
		Zombie z = zombie(helper, 4.5, 4.0, 100);
		HellflameMoves.flashfire(p, true);
		near(helper, taken(z, 100), HellflameConfig.UPPERCUT_DAMAGE, "Uppercut damage");
		helper.assertTrue(z.getDeltaMovement().y > 1.0, "launched up, " + z.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void flashfireDashCarriesHimForward(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 0.5);
		HellflameMoves.flashfire(p, false);
		helper.assertTrue(HellflameMoves.dashing(p) && Hellflame.dashing(p), "nothing in reach: the dash runs");
		helper.assertTrue(p.getDeltaMovement().z > HellflameConfig.FIST_DASH_SPEED - 0.1, "rocketing forward, " + p.getDeltaMovement());
		pump(helper, p);
		helper.runAfterDelay(HellflameConfig.FIST_DASH_TICKS + 2, () -> {
			helper.assertFalse(HellflameMoves.dashing(p), "the dash ends");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE)
	public void prominenceBurnScalesWithCharge(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 4.5);
		Zombie close = zombie(helper, 4.5, 6.5, 100);
		Zombie far = zombie(helper, 4.5, 0.0, 100);
		int n = HellflameMoves.prominence(p, 0f);
		helper.assertTrue(n == 1, "no charge: 3 blocks, only the close one, " + n);
		near(helper, taken(close, 100), HellflameConfig.PROMINENCE_DAMAGE, "no-charge damage");
		near(helper, taken(far, 100), 0f, "the far one is outside");
		near(helper, Quirks.meter(p), HellflameConfig.PROMINENCE_HEAT, "no-charge Heat");
		Quirks.setMeter(p, 0f);
		n = HellflameMoves.prominence(p, 1f);
		helper.assertTrue(n == 2, "full charge: 7 blocks, both, " + n);
		near(helper, taken(far, 100), HellflameConfig.PROMINENCE_DAMAGE + HellflameConfig.PROMINENCE_DAMAGE_EXTRA, "full-charge damage");
		near(helper, Quirks.meter(p), HellflameConfig.PROMINENCE_HEAT + HellflameConfig.PROMINENCE_HEAT_EXTRA, "full-charge Heat");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void prominenceChargesWhileHeld(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 4.5);
		helper.assertTrue(HellflameMoves.startCharge(p) && Hellflame.charging(p), "charging (synced)");
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			float c = HellflameMoves.chargeNow(p);
			helper.assertTrue(c > 0.4f && c < 0.6f, "half charged after 1.5 s, " + c);
			helper.assertTrue(HellflameMoves.releaseProminence(p) >= 0, "released");
			helper.assertFalse(Hellflame.charging(p), "the charge is spent");
			helper.assertFalse(Quirks.ready(p, HellflameConfig.PROMINENCE), "cooldown set");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void hellflameSunRainsFireballs(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 6.5, 100);
		HellflameMoves.hellflameSun(p);
		helper.assertTrue(Hellflame.sunUp(p), "the sun is up (synced)");
		near(helper, Quirks.meter(p), HellflameConfig.SUN_HEAT, "Heat");
		pump(helper, p);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(taken(z, 100) >= 2 * HellflameConfig.SUN_DAMAGE - EPS, "fireballs fell on it, took " + taken(z, 100));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void flameCloakFireproofsAndBurnsAttackers(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 3.5);
		Zombie z = zombie(helper, 4.5, 5.0, 100);
		QuirkKit kit = QuirkKits.of(Quirk.HELLFLAME);
		helper.assertTrue(kit.modifyIncomingDamage(p, p.damageSources().inFire(), 5f) == 5f, "no Cloak: fire hurts");
		helper.assertTrue(HellflameMoves.toggleCloak(p) && Hellflame.cloaked(p), "Cloak on (synced)");
		helper.assertTrue(kit.modifyIncomingDamage(p, p.damageSources().inFire(), 5f) == 0f, "fire-proof");
		helper.assertTrue(kit.modifyIncomingDamage(p, p.damageSources().lava(), 5f) == 0f, "lava-proof");
		float melee = kit.modifyIncomingDamage(p, p.damageSources().mobAttack(z), 4f);
		helper.assertTrue(melee == 4f && z.getRemainingFireTicks() > 0, "the attacker burns (the hit is not reduced)");
		z.clearFire();
		HellflameMoves.cloakMelee(p, z);
		helper.assertTrue(z.getRemainingFireTicks() > 0, "a punch sets it alight");
		helper.assertFalse(HellflameMoves.toggleCloak(p) || Hellflame.cloaked(p), "Cloak off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ventDumpsHeatInABurst(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 3.5);
		Zombie z = zombie(helper, 4.5, 6.5, 100);
		Quirks.setMeter(p, 80f);
		int n = HellflameMoves.vent(p);
		helper.assertTrue(n == 1, "it hit, " + n);
		near(helper, taken(z, 100), HellflameConfig.VENT_BASE_DAMAGE + HellflameConfig.VENT_DAMAGE_PER_HEAT * 80f, "damage scales with Heat");
		near(helper, Quirks.meter(p), 0f, "all Heat dumped");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Heat / Overheat

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void heatVentsFourASecond(GameTestHelper helper) {
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 4.5);
		Quirks.setMeter(p, 50f);
		HellflameMoves.coolForTest(p);
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			float h = Quirks.meter(p);
			helper.assertTrue(Math.abs(h - (50f - 20 * HellflameConfig.HEAT_VENT_PER_TICK)) <= 0.8f, "4 Heat a second, got " + h);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void overheatLocksFireButVentStillWorks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 3.5);
		HellflameMoves.toggleCloak(p);
		Quirks.setMeter(p, 95f);
		HellflameMoves.jetBurn(p);
		helper.assertTrue(Quirks.overloaded(p) && Hellflame.overheated(p), "100 Heat: Overheat");
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1, "Slowness II");
		helper.assertFalse(Hellflame.cloaked(p), "the Cloak goes out");
		// the keys: a fire move is refused, Shift+C Vent goes through
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(Quirks.ready(p, HellflameConfig.SPIDER), "Hell Spider refused while overheated");
		p.setShiftKeyDown(true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
		p.setShiftKeyDown(false);
		helper.assertFalse(Quirks.ready(p, HellflameConfig.VENT), "Vent went off");
		helper.assertFalse(Quirks.overloaded(p), "and ended the Overheat");
		near(helper, Quirks.meter(p), 0f, "Heat 0");
		helper.succeed();
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
			Quirks.setMeter(p, 100f);
			HellflameMoves.vent(p);
			HellflameMoves.prominence(p, 1f);
			HellflameMoves.jetBurn(p);
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
		ServerPlayer p = endeavor(helper, 4.5, 2.0, 2.5);
		Zombie boss = zombie(helper, 4.5, 4.0, 300);
		HellflameMoves.flashfire(p, true);
		float t = taken(boss, 300);
		helper.assertTrue(t > 0f && t <= HellflameConfig.UPPERCUT_DAMAGE + EPS, "a normal hit under the cap, " + t);
		helper.assertTrue(boss.getDeltaMovement().y < 0.5, "a boss is not launched, " + boss.getDeltaMovement());
		boss.invulnerableTime = 0;
		HellflameMoves.prominence(p, 1f);
		float t2 = taken(boss, 300) - t;
		near(helper, t2, 300 * HellflameConfig.BOSS_MAX_FRACTION_PER_HIT, "capped at 10% max health");
		helper.succeed();
	}
}
