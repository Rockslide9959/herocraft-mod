package com.projecthero.mod.gametest;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightAbilities;
import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.OneForAllKit;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.item.QuirkItems;
import com.projecthero.mod.quirk.item.QuirkSerumItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user: "for one for all remove strain and output it should just be the 20 minute timer like how it was before"): the
 * One For All timer is the whole system -- 20:00 that drains in the Power Form and refills in the Base Form, Plus Ultra and the
 * United States of Smash spending a slice, forced back to the Base Form at 0:00 -- every Smash at its full damage (x1.3 in Plus
 * Ultra), old saves with the removed Output / limit / limb keys loading cleanly, and One For All as the eighth Quirk. Mock
 * players are not ticked by the server, so each test drives the ticks itself. Design: docs/OFA_TIMER.md.
 */
public class OneForAllTimerGameTests implements FabricGameTest {
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 5, 8))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		AllMight.grant(p);
		return p;
	}

	private static void powerForm(ServerPlayer p) {
		AllMightState s = AllMight.state(p).copy();
		s.fullPower = true;
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.reconcile(p);
		p.setHealth(p.getMaxHealth());
	}

	/** Presses and releases a key; the previous move's wind-up lock is cleared first so presses in a row each start. */
	private static void press(ServerPlayer p, int slot, boolean shift) {
		AllMightState s = AllMight.state(p).copy();
		s.busyUntil = 0L;
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	// ---------------------------------------------------------------- the timer

	@GameTest(template = EMPTY_STRUCTURE)
	public void theTimerIsTwentyMinutes(GameTestHelper helper) {
		helper.assertTrue(AllMightConfig.timer().powerFormSeconds == 1200f && AllMight.timerMax() == 1200f, "20:00 of Power Form");
		helper.assertTrue(AllMightConfig.timer().drainPerSecond == 1f && AllMightConfig.timer().rechargePerSecond == 0.5f,
				"drains 1 s a second, refills 0.5 s a second (a full refill takes 40 min)");
		ServerPlayer p = hero(helper);
		helper.assertTrue(AllMight.timeLeft(p) == 1200f, "a new holder has a full timer");
		// the save: a new key, missing = full (old saves); a value round-trips
		AllMightState s = AllMight.state(p).copy();
		s.ofaTimer = 321.5f;
		var json = AllMightState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, s).getOrThrow();
		helper.assertTrue(AllMightState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow().ofaTimer == 321.5f, "saved");
		com.google.gson.JsonObject old = new com.google.gson.JsonObject();
		old.addProperty("has_power", true);
		old.addProperty("ofa", 0.0f); // the pre-0.16.6 key stays ignored
		AllMightState loaded = AllMightState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(AllMight.timeLeft(loaded) == 1200f, "an existing save loads with a full timer");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theTimerDrainsInThePowerFormAndRefillsInTheBaseForm(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(50, () -> {
			float used = 1200f - AllMight.timeLeft(p);
			helper.assertTrue(used >= 2f && used <= 3f, "1 s a second in the Power Form, used " + used);
			AllMightState s = AllMight.state(p).copy();
			s.fullPower = false;
			p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
			AllMight.reconcile(p);
			AllMight.setTimer(p, 600f);
		});
		helper.runAfterDelay(100, () -> {
			float gained = AllMight.timeLeft(p) - 600f;
			helper.assertTrue(gained >= 1f && gained <= 1.5f, "0.5 s a second in the Base Form, gained " + gained);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void atZeroHeIsForcedBackToTheBaseForm(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		AllMight.setTimer(p, 0f);
		AllMight.tick(p);
		helper.assertFalse(AllMight.isFullPower(p), "0:00 -- back to the Base Form");
		// H is refused until 4 s have come back
		AllMightState s = AllMight.state(p).copy();
		s.formChangedAt = p.level().getGameTime() - 100L;
		s.transformUntil = 0L;
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.setTimer(p, 3f);
		AllMight.toggleForm(p);
		helper.assertFalse(AllMight.isFullPower(p), "3 s is not enough to transform");
		AllMight.setTimer(p, AllMightConfig.timer().minimumToTransformSeconds);
		AllMight.toggleForm(p);
		helper.assertTrue(AllMight.isFullPower(p), "4 s is");
		// a respawn refills it
		AllMight.setTimer(p, 10f);
		AllMight.onPlayerRespawn(p);
		helper.assertTrue(AllMight.timeLeft(p) == 1200f, "respawn refills the timer");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void plusUltraAndTheUnitedStatesOfSmashSpendTheTimer(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		AllMight.setTimer(p, 150f);
		press(p, 6, false); // C: Plus Ultra needs 200 s (+1)
		helper.assertFalse(AllMight.plusUltraActive(p), "refused without 3:20 left");
		helper.assertTrue(AllMight.timeLeft(p) == 150f, "nothing spent");
		AllMight.setTimer(p, 1200f);
		press(p, 6, false);
		helper.assertTrue(AllMight.plusUltraActive(p) && AllMight.timeLeft(p) == 1000f, "Plus Ultra spends 200 s, left " + AllMight.timeLeft(p));
		helper.assertTrue(AllMight.state(p).plusUltraUntil - p.level().getGameTime() == AllMightConfig.PLUS_ULTRA_DURATION_TICKS,
				"a fixed 22 s window");
		// the United States of Smash: refused up front with less than 400 s
		AllMightState s = AllMight.state(p).copy();
		s.busyUntil = 0L;
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.setTimer(p, 300f);
		AbilityRouter.handleInput(p, 4, true);
		helper.assertTrue(AllMight.state(p).chargeStart == 0L, "no hold with less than 6:40 left");
		AbilityRouter.handleInput(p, 4, false);
		AllMight.setTimer(p, 1200f);
		AbilityRouter.handleInput(p, 4, true);
		helper.assertTrue(AllMight.state(p).chargeStart > 0L && AllMight.timeLeft(p) == 1200f, "the hold starts; nothing is paid until the cast");
		AbilityRouter.handleInput(p, 4, false);
		helper.succeed();
	}

	// ---------------------------------------------------------------- no Output, no strain

	@GameTest(template = EMPTY_STRUCTURE)
	public void everySmashLandsItsFullDamage(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		helper.assertTrue(AllMight.smashMultiplier(p) == 1.0f, "the Power Form: x1, the designed damage");
		float hp = p.getHealth();
		// Smashes in a row cost nothing from the body: no HP, nothing refused
		press(p, 1, false); // R: Detroit
		press(p, 2, false); // G: Texas
		press(p, 3, true); // Shift+X: Manchester
		press(p, 5, false); // V: Carolina
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) > 0 && AllMight.cooldownRemaining(p, AllMightAbilities.TEXAS) > 0
				&& AllMight.cooldownRemaining(p, AllMightAbilities.MANCHESTER) > 0 && AllMight.cooldownRemaining(p, AllMightAbilities.CAROLINA) > 0,
				"every Smash goes off");
		helper.assertTrue(p.getHealth() == hp, "and none of them hurts him, " + hp + " -> " + p.getHealth());
		helper.assertTrue(AllMight.timeLeft(p) == 1200f, "the Smashes take nothing from the timer");
		press(p, 6, false); // C: Plus Ultra
		helper.assertTrue(AllMight.smashMultiplier(p) == AllMightConfig.PLUS_ULTRA_MULTIPLIER && AllMightConfig.PLUS_ULTRA_MULTIPLIER == 1.30f,
				"Plus Ultra: x1.3");
		AllMight.toggleForm(p); // back to the Base Form ends Plus Ultra
		helper.assertFalse(AllMight.plusUltraActive(p), "Plus Ultra ends with the Power Form");
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.PLUS_ULTRA) == AllMightConfig.PLUS_ULTRA_COOLDOWN_TICKS,
				"and its 20 s cooldown starts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anOldSaveWithStrainAndOutputLoadsCleanly(GameTestHelper helper) {
		com.google.gson.JsonObject old = new com.google.gson.JsonObject();
		old.addProperty("has_power", true);
		old.addProperty("full_power", true);
		old.addProperty("ofa_timer", 512.0f);
		old.addProperty("ofa_timer_max", 1200.0f);
		// every key of the removed systems (v0.16.6 Output levels / Strain bar, v0.16.7 Output / limit / limbs)
		old.addProperty("output_pct", 87.0f);
		old.addProperty("mastery", 42.5f);
		com.google.gson.JsonArray limbs = new com.google.gson.JsonArray();
		limbs.add(100.0f);
		limbs.add(55.0f);
		limbs.add(12.0f);
		old.add("limb_strain", limbs);
		old.addProperty("broken_limbs", 1);
		old.addProperty("last_hit_at", 12345L);
		old.addProperty("strain", 77.0f);
		old.addProperty("recoil_until", 999L);
		old.addProperty("output", 2);
		AllMightState s = AllMightState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(s.hasPower && s.fullPower && s.ofaTimer == 512f && s.ofaTimerMax == 1200f, "the timer and the form load as saved");
		var again = AllMightState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, s).getOrThrow().getAsJsonObject();
		for (String k : new String[] { "output_pct", "mastery", "limb_strain", "broken_limbs", "last_hit_at", "strain", "recoil_until", "output" }) {
			helper.assertFalse(again.has(k), "the old key " + k + " is dropped on the next save");
		}
		// and such a holder simply plays on
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.onPlayerJoin(p);
		AllMight.tick(p);
		helper.assertTrue(AllMight.hasPower(p) && AllMight.timeLeft(p) <= 512f && AllMight.timeLeft(p) > 500f, "the timer carries on from 8:32");
		helper.succeed();
	}

	// ---------------------------------------------------------------- One For All in the Quirk framework

	@GameTest(template = EMPTY_STRUCTURE)
	public void theQuirkRegistryHoldsOneForAll(GameTestHelper helper) {
		helper.assertTrue(Quirk.byId("one_for_all") == Quirk.ONE_FOR_ALL && Quirk.byHeroKey("all_might") == Quirk.ONE_FOR_ALL,
				"One For All is a Quirk, by id and by its old hero key");
		helper.assertTrue(Quirk.ONE_FOR_ALL.heroKey().equals(AllMight.KEY) && Quirk.ONE_FOR_ALL.standalone, "it keeps the all_might key");
		helper.assertTrue(Quirk.values().length == 8 && Quirk.kitQuirks().length == 7, "eight Quirks, seven on QuirkState");
		helper.assertTrue(QuirkKits.of(Quirk.ONE_FOR_ALL) instanceof OneForAllKit, "its kit is registered");
		helper.assertTrue(QuirkKits.of(Quirk.ONE_FOR_ALL).idsOf(AbilitySlot.SLOT_1)[0].equals(AllMightAbilities.DETROIT)
				&& QuirkKits.of(Quirk.ONE_FOR_ALL).idsOf(AbilitySlot.SLOT_6)[1].equals(AllMightAbilities.ST_LOUIS), "R = Detroit ... Shift+C = St. Louis");
		helper.assertTrue(QuirkItems.serumOf(Quirk.ONE_FOR_ALL) != null, "it has a Quirk Serum");

		// granted the Quirk way
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(Quirks.grant(p, Quirk.ONE_FOR_ALL), "Quirks.grant gives One For All");
		helper.assertTrue(AllMight.hasPower(p) && Quirks.holds(p, Quirk.ONE_FOR_ALL) && Quirks.hasPower(p) && HeroTiers.heroCount(p) == 1,
				"held as All Might and as a Quirk, one hero");
		helper.assertFalse(Quirks.grant(p, Quirk.ONE_FOR_ALL), "twice refused");
		helper.assertTrue(Quirks.grant(p, Quirk.HELLFLAME) && !AllMight.hasPower(p) && Quirks.holds(p, Quirk.HELLFLAME), "another Quirk replaces it");
		helper.assertTrue(QuirkSerumItem.drink(p, Quirk.ONE_FOR_ALL, p.getRandom()) == Quirk.ONE_FOR_ALL && AllMight.hasPower(p)
				&& !Quirks.holds(p, Quirk.HELLFLAME), "its serum gives it back and replaces Hellflame");
		Quirks.revoke(p);
		helper.assertFalse(AllMight.hasPower(p) || Quirks.hasPower(p), "Quirks.revoke takes it");

		// an existing holder (a save from before One For All was a Quirk: only the All Might state) is a Quirk holder as is
		ServerPlayer old = helper.makeMockServerPlayerInLevel();
		AllMightState s = new AllMightState();
		s.hasPower = true;
		old.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		helper.assertTrue(Quirks.quirk(old) == Quirk.ONE_FOR_ALL, "no migration needed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theKeysGoThroughTheQuirkCore(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		// the Base Form: the Quirk core takes the keys and nothing happens
		press(p, 1, false);
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) == 0, "no Smashes in the Base Form");
		helper.assertTrue(new OneForAllKit().keyLocked(p, AbilitySlot.SLOT_1, false), "the HUD dims the keys in the Base Form");
		powerForm(p);
		helper.assertFalse(new OneForAllKit().keyLocked(p, AbilitySlot.SLOT_1, false), "and lights them in the Power Form");
		AbilityRouter.handleInput(p, 1, true);
		AbilityRouter.handleInput(p, 1, true); // the OS key repeat: debounced by the Quirk core
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) > 0, "Detroit in the Power Form");
		helper.succeed();
	}
}
