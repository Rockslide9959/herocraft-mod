package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.ability.IronManJarvisScan;
import com.projecthero.mod.ironman.ability.IronManMark3;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.14.27 (agent D): the Mark III's own kit -- the weapon wheel on V (Rockets / Miniguns / Micro-Missiles fired by
 * G), the held Unibeam on Z, the Sneak+V Energy Shield and the Sneak+X JARVIS scan.
 */
public class IronManV01427Mark3GameTests implements FabricGameTest {
	private static final String M3 = IronManMark3.SUIT_ID;
	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = IronManV01427Mark3GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				throw new IllegalStateException("cannot read en_us.json", e);
			}
		}
		if (!lang.has(key)) {
			throw new IllegalStateException("missing lang key " + key);
		}
		return lang.get(key).getAsString();
	}

	private static ServerPlayer suited(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		for (ArmorItem.Type type : ArmorItem.Type.values()) {
			var item = IronManItems.armor(M3, type);
			if (item != null) {
				p.setItemSlot(IronManSuitUpManager.slotFor(type), new ItemStack(item));
			}
		}
		IronManEnergy.setEnergy(p, M3, 1000f);
		IronManEnergy.setIntegrity(p, M3, 1000f);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f); // facing +Z
		p.setXRot(0f);
		p.setYHeadRot(0f);
		return p;
	}

	private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz) {
		T m = type.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0f, 0f);
		m.setNoAi(true);
		h.getLevel().addFreshEntity(m);
		return m;
	}

	private static float energy(ServerPlayer p) {
		return IronManEnergy.energy(p, M3);
	}

	// ------------------------------------------------------------------ layout + weapon selection

	@GameTest(template = EMPTY_STRUCTURE)
	public void markThreeLayoutAndWeaponSelectionPersists(GameTestHelper helper) {
		IronManSuit suit = IronManSuits.MARK_III;
		helper.assertTrue(IronManAbilities.REPULSOR_BLAST.equals(suit.abilityInSlot(1)), "R stays the repulsor");
		helper.assertTrue(IronManMark3.ARSENAL.equals(suit.abilityInSlot(2)), "G fires the wheel weapon");
		helper.assertTrue(IronManMark3.FLARES.equals(suit.abilityInSlot(3)), "X is Flares / JARVIS");
		helper.assertTrue(IronManMark3.UNIBEAM.equals(suit.abilityInSlot(4)), "Z is the held Unibeam");
		helper.assertTrue(IronManMark3.WHEEL.equals(suit.abilityInSlot(5)), "V is the weapon wheel / shield");
		helper.assertTrue(IronManAbilities.SUIT_TOGGLE.equals(suit.abilityInSlot(6)), "C stores the suit");
		for (int s = 1; s <= 6; s++) {
			helper.assertFalse(IronManAbilities.REPULSOR_BARRIER.equals(suit.abilityInSlot(s))
					|| IronManAbilities.MICRO_MISSILES.equals(suit.abilityInSlot(s)), "old Mark III bindings are gone");
		}

		ServerPlayer p = suited(helper);
		helper.assertTrue(IronManMark3.ROCKETS.equals(IronManMark3.selectedWeapon(p)), "Rockets by default");
		IronManMark3.selectWeapon(p, IronManMark3.MINIGUN);
		helper.assertTrue(IronManMark3.MINIGUN.equals(IronManMark3.selectedWeapon(p)), "the wheel picks the minigun");
		IronManMark3.selectWeapon(p, "not_a_weapon");
		helper.assertTrue(IronManMark3.MINIGUN.equals(IronManMark3.selectedWeapon(p)), "junk ids are ignored");
		// survives a save / load round trip
		Tag saved = TonyStarkState.CODEC.encodeStart(NbtOps.INSTANCE, TonyStark.state(p)).getOrThrow();
		TonyStarkState loaded = TonyStarkState.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(IronManMark3.MINIGUN.equals(IronManMark3.selectedWeapon(loaded)), "the pick is saved");

		// every name / wheel text exists and fits the wheel's centre disc
		for (String id : new String[] { IronManMark3.ARSENAL, IronManMark3.FLARES, IronManMark3.UNIBEAM, IronManMark3.WHEEL }) {
			t("hud.projecthero.ironman.ability." + id);
		}
		for (int[] s : new int[][] { { 320, 240 }, { 426, 240 }, { 960, 540 } }) {
			int tw = IronManUiLayout.wheelTextWidth(IronManUiLayout.wheelRadii(s[0], s[1])[0]);
			for (String w : IronManMark3.WEAPONS) {
				helper.assertTrue(IronManUiLayout.approxWidth(t("hud.projecthero.ironman.ability." + w)) <= tw, w + " name fits");
				helper.assertTrue(IronManUiLayout.wrapLines(t("screen.projecthero.weapon_wheel.desc." + w), tw,
						IronManUiLayout::approxWidth) <= 2, w + " description wraps to two lines at most");
			}
		}
		t("screen.projecthero.weapon_wheel.mk3_title");
		t("screen.projecthero.weapon_wheel.mk3_hint");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Rockets

	@GameTest(template = EMPTY_STRUCTURE)
	public void rocketHitsForThirtyFiveAndCostsOneHundred(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		float before = energy(p);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		var rockets = helper.getLevel().getEntitiesOfClass(IronManMissileEntity.class, p.getBoundingBox().inflate(8),
				e -> e.getOwner() == p);
		helper.assertTrue(rockets.size() == 1, "one rocket, got " + rockets.size());
		helper.assertTrue(rockets.get(0).directDamage() == IronManMark3.ROCKET_DAMAGE && IronManMark3.ROCKET_DAMAGE == 35f,
				"the rocket hits for 35");
		helper.assertTrue(Math.abs(before - energy(p) - 100f) < 0.01f, "a rocket costs 100 energy");
		int cd = TonyStark.abilityCooldownRemaining(p, M3, IronManMark3.ROCKETS);
		helper.assertTrue(cd > 190 && cd <= 200, "10 s cooldown, got " + cd);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(IronManMissileEntity.class, p.getBoundingBox().inflate(8),
				e -> e.getOwner() == p).size() == 1, "no second rocket while cooling down");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Micro-Missiles

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void microMissilesLaunchFourForOneHundredFifty(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		IronManMark3.selectWeapon(p, IronManMark3.MICRO_MISSILES);
		float before = energy(p);
		Set<Integer> seen = new HashSet<>();
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(Math.abs(before - energy(p) - 150f) < 0.01f, "the volley costs 150 up front");
		int cd = TonyStark.abilityCooldownRemaining(p, M3, IronManMark3.MICRO_MISSILES);
		helper.assertTrue(cd > 230 && cd <= 240, "12 s cooldown, got " + cd);
		helper.onEachTick(() -> {
			IronManMark3.tickForTest(p, IronManSuits.MARK_III);
			for (IronManMissileEntity m : helper.getLevel().getEntitiesOfClass(IronManMissileEntity.class,
					p.getBoundingBox().inflate(16), e -> e.getOwner() == p)) {
				if (seen.add(m.getId())) {
					helper.assertTrue(m.directDamage() == 25f, "each micro missile hits for 25");
					helper.assertTrue(m.isHoming(), "micro missiles home");
				}
			}
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(seen.size() == IronManMark3.MICRO_COUNT && IronManMark3.MICRO_COUNT == 4,
					"exactly four micro missiles, got " + seen.size());
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ Miniguns

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void minigunTenDamageEveryHalfSecondForFiveSeconds(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		p.setXRot(15f); // look down a little onto the cow
		Cow cow = mob(helper, EntityType.COW, p, 0, 4);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		cow.setHealth(1000f);
		IronManMark3.selectWeapon(p, IronManMark3.MINIGUN);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(IronManMark3.minigunFiring(p), "holding G spins the miniguns up");
		long start = helper.getLevel().getGameTime();
		// record the game time of every 10-damage hit -- they must be exactly 10 ticks (0.5 s) apart
		java.util.List<Long> hits = new java.util.ArrayList<>();
		if (cow.getHealth() < 1000f) {
			hits.add(start); // the first burst leaves on the press
		}
		float[] last = { cow.getHealth() };
		helper.onEachTick(() -> {
			IronManMark3.tickForTest(p, IronManSuits.MARK_III);
			if (cow.getHealth() < last[0] - 0.01f) {
				helper.assertTrue(Math.abs(last[0] - cow.getHealth() - 10f) < 0.01f, "each hit deals 10");
				hits.add(helper.getLevel().getGameTime());
				last[0] = cow.getHealth();
			}
		});
		helper.runAfterDelay(MINIGUN_CHECK, () -> {
			helper.assertFalse(IronManMark3.minigunFiring(p), "the miniguns stop on their own after 5 s");
			helper.assertTrue(hits.size() >= 9 && hits.size() <= 10, "ten shots in 5 s (the first may still be spinning up), got " + hits);
			helper.assertTrue(hits.get(hits.size() - 1) - start < IronManMark3.MINIGUN_MAX_TICKS, "nothing after 5 s: " + hits);
			for (int i = 1; i < hits.size(); i++) {
				helper.assertTrue(hits.get(i) - hits.get(i - 1) == IronManMark3.MINIGUN_SHOT_INTERVAL, "0.5 s apart: " + hits);
			}
			int cd = TonyStark.abilityCooldownRemaining(p, M3, IronManMark3.MINIGUN);
			long since = helper.getLevel().getGameTime() - start - IronManMark3.MINIGUN_MAX_TICKS;
			helper.assertTrue(cd > 0 && cd <= IronManMark3.MINIGUN_COOLDOWN && cd >= IronManMark3.MINIGUN_COOLDOWN - since - 2,
					"15 s cooldown after they stop, got " + cd);
			IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
			helper.succeed();
		});
	}

	private static final int MINIGUN_CHECK = 110;

	@GameTest(template = EMPTY_STRUCTURE)
	public void minigunReleaseStopsAndStartsCooldown(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		IronManMark3.selectWeapon(p, IronManMark3.MINIGUN);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(IronManMark3.minigunFiring(p), "firing");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		helper.assertFalse(IronManMark3.minigunFiring(p), "releasing G stops the guns");
		helper.assertTrue(TonyStark.abilityCooldownRemaining(p, M3, IronManMark3.MINIGUN) == IronManMark3.MINIGUN_COOLDOWN,
				"the 15 s cooldown starts on release");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Unibeam (held)

	@GameTest(template = EMPTY_STRUCTURE)
	public void unibeamDrainsWhileHeldAndCoolsDownAfterRelease(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(IronManMark3.unibeamFiring(p), "holding Z fires the Unibeam");
		float before = energy(p);
		for (int i = 0; i < 20; i++) {
			IronManMark3.tickForTest(p, IronManSuits.MARK_III);
		}
		float spent = before - energy(p);
		helper.assertTrue(Math.abs(spent - 80f) < 0.05f, "80 energy per second of beam, spent " + spent);
		helper.assertTrue(IronManMark3.unibeamFiring(p), "still firing while held");
		helper.assertTrue(TonyStark.abilityReady(p, M3, IronManMark3.UNIBEAM), "no cooldown while it fires");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
		helper.assertFalse(IronManMark3.unibeamFiring(p), "release stops it");
		helper.assertTrue(TonyStark.abilityCooldownRemaining(p, M3, IronManMark3.UNIBEAM) == 400,
				"20 s cooldown from release");

		// running dry also ends it and starts the cooldown
		TonyStarkState s = TonyStark.state(p).copy();
		s.abilityReadyAt.remove(M3 + "/" + IronManMark3.UNIBEAM);
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, s);
		IronManEnergy.setEnergy(p, M3, 6f);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(IronManMark3.unibeamFiring(p), "fires with a little energy");
		IronManMark3.tickForTest(p, IronManSuits.MARK_III); // 6 -> 2
		IronManMark3.tickForTest(p, IronManSuits.MARK_III); // cannot pay 4 -> stops
		helper.assertFalse(IronManMark3.unibeamFiring(p), "out of energy ends the beam");
		helper.assertTrue(TonyStark.abilityCooldownRemaining(p, M3, IronManMark3.UNIBEAM) == 400, "and starts the cooldown");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Energy Shield

	@GameTest(template = EMPTY_STRUCTURE)
	public void shieldBlocksAllDamageForATenPercentEnergyTax(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(IronManMark3.shieldOn(p), "Sneak+V raises the shield");

		float before = energy(p);
		var src = helper.getLevel().damageSources().generic();
		boolean allowed = ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, src, 30f);
		helper.assertFalse(allowed, "the shield blocks the hit outright");
		helper.assertTrue(Math.abs(before - energy(p) - 3f) < 0.01f, "10% of a 30-damage hit is paid in energy, spent "
				+ (before - energy(p)));

		before = energy(p);
		for (int i = 0; i < 20; i++) {
			IronManMark3.tickForTest(p, IronManSuits.MARK_III);
		}
		helper.assertTrue(Math.abs(before - energy(p) - 10f) < 0.05f, "10 energy per second while up");

		// an empty suit drops it
		IronManEnergy.setEnergy(p, M3, 0.2f);
		IronManMark3.tickForTest(p, IronManSuits.MARK_III);
		helper.assertFalse(IronManMark3.shieldOn(p), "no power, no shield");

		// toggles off with a second Sneak+V
		IronManEnergy.setEnergy(p, M3, 500f);
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(IronManMark3.shieldOn(p), "back up");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		p.setShiftKeyDown(false);
		helper.assertFalse(IronManMark3.shieldOn(p), "a second Sneak+V drops it");
		helper.succeed();
	}

	// ------------------------------------------------------------------ JARVIS scan

	@GameTest(template = EMPTY_STRUCTURE)
	public void jarvisScanCountsHostilesAndCostsTwenty(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		mob(helper, EntityType.ZOMBIE, p, 2, 2);
		mob(helper, EntityType.SKELETON, p, -2, 2);
		mob(helper, EntityType.SPIDER, p, 2, -2);
		mob(helper, EntityType.COW, p, -2, -2);
		float before = energy(p);
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(Math.abs(before - energy(p) - 20f) < 0.01f, "a scan costs 20 energy");
		int cd = TonyStark.abilityCooldownRemaining(p, M3, IronManMark3.JARVIS_SCAN);
		helper.assertTrue(cd > 50 && cd <= 60, "3 s cooldown, got " + cd);
		helper.assertTrue(IronManJarvisScan.run(p, IronManSuits.MARK_III) == null, "no second scan inside the cooldown");

		IronManJarvisScan.Report r = IronManJarvisScan.scan(p);
		helper.assertTrue(r.hostiles() >= 3, "three hostiles counted, got " + r.hostiles());
		helper.assertTrue(r.passive() >= 1, "the cow is passive");
		helper.assertTrue(r.topThreat() != null, "a main threat is named");
		for (String k : new String[] { "header", "hostiles", "threat", "creatures", "boss", "no_players", "player",
				"structure", "ores", "no_ores", "env", "suit", "summary", "weather.clear", "weather.rain", "weather.thunder" }) {
			t("message.projecthero.ironman.jarvis." + k);
		}
		helper.succeed();
	}

	// ================================================================== v0.14.29 (agent A): the Mark 4 runs this kit

	private static final String M4 = IronManMark3.MARK_4_ID;

	private static ServerPlayer suitedMk4(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		for (ArmorItem.Type type : ArmorItem.Type.values()) {
			var item = IronManItems.armor(M4, type);
			if (item != null) {
				p.setItemSlot(IronManSuitUpManager.slotFor(type), new ItemStack(item));
			}
		}
		IronManEnergy.setEnergy(p, M4, 1000f);
		IronManEnergy.setIntegrity(p, M4, 1000f);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f); // facing +Z
		p.setXRot(0f);
		p.setYHeadRot(0f);
		return p;
	}

	private static Cow tankCow(GameTestHelper h, ServerPlayer p, double dz) {
		Cow cow = mob(h, EntityType.COW, p, 0, dz);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		cow.setHealth(1000f);
		return cow;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markFourStatsMatchTheV01429Spec(GameTestHelper helper) {
		IronManSuit m4 = IronManSuits.MARK_4;
		IronManSuit m3 = IronManSuits.MARK_III;
		helper.assertTrue(m4.energyCapacity() == 3000f, "3000 energy");
		helper.assertTrue(m4.energyRegenPerSecond() == 5f, "5 energy/s regen");
		helper.assertTrue(m4.maxIntegrity() == 1750f && IronManEnergy.maxIntegrity(M4) == 1750f, "1750 integrity");
		helper.assertTrue(m4.arrowFireImmune(), "arrow + fire immune");
		helper.assertTrue(m4.waterBreathing() && m4.airTankSeconds() == 0, "breathes underwater, no air tank");
		helper.assertTrue(m4.autoFeed(), "auto-feeds");
		helper.assertTrue(com.projecthero.mod.ironman.IronManTargeting.hasTargeting(m4), "lock-on / auto-aim");
		helper.assertTrue(m4.strengthBonus() == 7f, "+7 melee");
		helper.assertFalse(m4.hasWristLaser(), "no wrist laser");
		var chest = (net.minecraft.world.item.ArmorItem) IronManItems.armor(M4, ArmorItem.Type.CHESTPLATE);
		var helm = (net.minecraft.world.item.ArmorItem) IronManItems.armor(M4, ArmorItem.Type.HELMET);
		var legs = (net.minecraft.world.item.ArmorItem) IronManItems.armor(M4, ArmorItem.Type.LEGGINGS);
		var boots = (net.minecraft.world.item.ArmorItem) IronManItems.armor(M4, ArmorItem.Type.BOOTS);
		helper.assertTrue(helm.getDefense() == 3 && chest.getDefense() == 8 && legs.getDefense() == 6 && boots.getDefense() == 3,
				"diamond armour points");
		helper.assertTrue(chest.getToughness() == 2.0f, "diamond toughness");
		for (int s = 1; s <= 6; s++) {
			helper.assertTrue(java.util.Objects.equals(m4.abilityInSlot(s), m3.abilityInSlot(s)), "slot " + s + " = Mark III kit");
		}
		// R: the Mark III repulsor +2 damage, -2 s cooldowns, same energy
		helper.assertTrue(m4.repulsorDamage() == m3.repulsorDamage() + 2f, "tap +2");
		helper.assertTrue(m4.chargedRepulsorDamage() == m3.chargedRepulsorDamage() + 2f, "charged +2");
		helper.assertTrue(m4.dashDamage() == m3.dashDamage() + 2f, "dash +2");
		helper.assertTrue(m4.repulsorTapCooldownTicks() == 20, "v0.15.6: tap cd 1 s (no repulsor spam)");
		helper.assertTrue(m4.chargedRepulsorCooldownTicks() == m3.chargedRepulsorCooldownTicks() - 40, "charged cd -2 s");
		helper.assertTrue(m4.dashCooldownTicks() == m3.dashCooldownTicks() - 40, "dash cd -2 s");
		helper.assertTrue(m4.repulsorTapEnergy() == m3.repulsorTapEnergy() && m4.chargedRepulsorEnergy() == m3.chargedRepulsorEnergy()
				&& m4.dashEnergy() == m3.dashEnergy(), "same repulsor energy costs");
		helper.assertTrue(m4.chargeHoldTicks() == m3.chargeHoldTicks(), "same charge hold");
		// the Mark III is untouched
		helper.assertTrue(IronManMark3.cooldownFor(M3, 200) == 200 && IronManMark3.damageFor(M3, 35f) == 35f, "Mark III tuning is +0 / -0");
		helper.assertTrue(IronManMark3.cooldownFor(M4, 20) == 0, "a cooldown never goes below 0");
		helper.assertTrue(IronManMark3.isKitSuit(M4) && IronManMark3.isKitSuit(M3) && !IronManMark3.isKitSuit("mark_6"), "kit suits");
		// wheel-open payloads
		helper.assertTrue(IronManMark3.OPEN_WHEEL.equals(IronManMark3.openWheelPayload(M3)), "Mark III payload unchanged");
		helper.assertTrue(M4.equals(IronManMark3.wheelSuit(IronManMark3.openWheelPayload(M4))), "Mark 4 payload round-trips");
		helper.assertTrue(M3.equals(IronManMark3.wheelSuit("mk3")) && IronManMark3.wheelSuit("") == null
				&& IronManMark3.wheelSuit("mk3:mark_6") == null, "Mark VII / junk payloads are not kit wheels");
		t("screen.projecthero.weapon_wheel.mk4_title");
		int tw = IronManUiLayout.wheelTextWidth(IronManUiLayout.wheelRadii(320, 240)[0]);
		helper.assertTrue(IronManUiLayout.wrapLines(t("screen.projecthero.weapon_wheel.desc.mk4_rockets").replace("%s", "37"), tw,
				IronManUiLayout::approxWidth) <= 2, "the Mark 4 rocket line wraps to two lines at most");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markFourRocketHitsForThirtySevenWithAnEightSecondCooldown(GameTestHelper helper) {
		ServerPlayer p = suitedMk4(helper);
		float before = IronManEnergy.energy(p, M4);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		var rockets = helper.getLevel().getEntitiesOfClass(IronManMissileEntity.class, p.getBoundingBox().inflate(8),
				e -> e.getOwner() == p);
		helper.assertTrue(rockets.size() == 1, "one rocket, got " + rockets.size());
		helper.assertTrue(rockets.get(0).directDamage() == 37f, "the Mark 4 rocket hits for 35 + 2");
		helper.assertTrue(Math.abs(before - IronManEnergy.energy(p, M4) - 100f) < 0.01f, "same 100 energy");
		int cd = TonyStark.abilityCooldownRemaining(p, M4, IronManMark3.ROCKETS);
		helper.assertTrue(cd > 150 && cd <= 160, "8 s cooldown, got " + cd);
		helper.assertTrue(TonyStark.abilityReady(p, M3, IronManMark3.ROCKETS), "the Mark III's own cooldown is untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void markFourMicroMissilesHitForTwentySeven(GameTestHelper helper) {
		ServerPlayer p = suitedMk4(helper);
		IronManMark3.selectWeapon(p, IronManMark3.MICRO_MISSILES);
		helper.assertTrue(IronManMark3.MICRO_MISSILES.equals(IronManMark3.selectedWeapon(TonyStark.state(p), M4)), "picked on the Mark 4");
		helper.assertTrue(IronManMark3.ROCKETS.equals(IronManMark3.selectedWeapon(TonyStark.state(p), M3)), "the Mark III keeps its own pick");
		float before = IronManEnergy.energy(p, M4);
		Set<Integer> seen = new HashSet<>();
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(Math.abs(before - IronManEnergy.energy(p, M4) - 150f) < 0.01f, "same 150 energy");
		int cd = TonyStark.abilityCooldownRemaining(p, M4, IronManMark3.MICRO_MISSILES);
		helper.assertTrue(cd > 190 && cd <= 200, "10 s cooldown, got " + cd);
		helper.onEachTick(() -> {
			IronManMark3.tickForTest(p, IronManSuits.MARK_4);
			for (IronManMissileEntity m : helper.getLevel().getEntitiesOfClass(IronManMissileEntity.class,
					p.getBoundingBox().inflate(16), e -> e.getOwner() == p)) {
				if (seen.add(m.getId())) {
					helper.assertTrue(m.directDamage() == 27f, "each Mark 4 micro missile hits for 27");
				}
			}
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(seen.size() == 4, "four micro missiles, got " + seen.size());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void markFourMinigunHitsForTwelveAndCoolsDownThirteenSeconds(GameTestHelper helper) {
		ServerPlayer p = suitedMk4(helper);
		p.setXRot(15f);
		Cow cow = tankCow(helper, p, 4);
		IronManMark3.selectWeapon(p, IronManMark3.MINIGUN);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(IronManMark3.minigunFiring(p), "holding G spins the Mark 4 miniguns up");
		float[] last = { 1000f };
		int[] hits = { 0 };
		if (cow.getHealth() < 1000f) {
			helper.assertTrue(Math.abs(1000f - cow.getHealth() - 12f) < 0.01f, "first hit deals 12");
			last[0] = cow.getHealth();
			hits[0]++;
		}
		helper.onEachTick(() -> {
			if (IronManMark3.minigunFiring(p)) {
				IronManMark3.tickForTest(p, IronManSuits.MARK_4);
			}
			if (cow.getHealth() < last[0] - 0.01f) {
				helper.assertTrue(Math.abs(last[0] - cow.getHealth() - 12f) < 0.01f, "each Mark 4 minigun hit deals 12");
				last[0] = cow.getHealth();
				hits[0]++;
			}
		});
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(hits[0] >= 2, "the guns hit, got " + hits[0]);
			IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
			helper.assertFalse(IronManMark3.minigunFiring(p), "release stops them");
			helper.assertTrue(TonyStark.abilityCooldownRemaining(p, M4, IronManMark3.MINIGUN) == 260, "15 s - 2 s = 13 s cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markFourUnibeamHitsForTwentyTwoAndCoolsDownEighteenSeconds(GameTestHelper helper) {
		ServerPlayer p = suitedMk4(helper);
		Cow cow = tankCow(helper, p, 4);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(IronManMark3.unibeamFiring(p), "holding Z fires the Mark 4 Unibeam");
		float before = IronManEnergy.energy(p, M4);
		IronManMark3.tickForTest(p, IronManSuits.MARK_4);
		helper.assertTrue(Math.abs(1000f - cow.getHealth() - 22f) < 0.01f, "the beam hits for 20 + 2, cow at " + cow.getHealth());
		for (int i = 1; i < 20; i++) {
			IronManMark3.tickForTest(p, IronManSuits.MARK_4);
		}
		helper.assertTrue(Math.abs(before - IronManEnergy.energy(p, M4) - 80f) < 0.05f, "same 80 energy/s");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(TonyStark.abilityCooldownRemaining(p, M4, IronManMark3.UNIBEAM) == 360, "20 s - 2 s = 18 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markFourSneakMovesAndFlaresAreTwoSecondsFaster(GameTestHelper helper) {
		ServerPlayer p = suitedMk4(helper);
		Cow cow = tankCow(helper, p, 3);
		float before = IronManEnergy.energy(p, M4);
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true); // Sneak+G sonic clap
		p.setShiftKeyDown(false);
		helper.assertTrue(Math.abs(1000f - cow.getHealth() - 22f) < 0.01f, "the clap hits for 20 + 2, cow at " + cow.getHealth());
		helper.assertTrue(Math.abs(before - IronManEnergy.energy(p, M4) - 50f) < 0.01f, "same 50 energy");
		helper.assertTrue(TonyStark.abilityCooldownRemaining(p, M4, com.projecthero.mod.ironman.ability.IronManSonicClap.ABILITY_ID) == 120,
				"8 s - 2 s = 6 s clap cooldown");

		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_3, true); // Sneak+X JARVIS
		p.setShiftKeyDown(false);
		helper.assertTrue(TonyStark.abilityCooldownRemaining(p, M4, IronManMark3.JARVIS_SCAN) == 20, "3 s - 2 s = 1 s scan cooldown");

		IronManAbilityManager.handle(p, AbilitySlot.SLOT_3, true); // X flares
		helper.assertTrue(TonyStark.abilityCooldownRemaining(p, M4, IronManAbilities.FLARE) == 160, "10 s - 2 s = 8 s flare cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markFourShieldSplitAndArrowFireImmunity(GameTestHelper helper) {
		ServerPlayer p = suitedMk4(helper);
		var sources = helper.getLevel().damageSources();
		// fire does nothing -- not even to integrity
		boolean allowed = ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, sources.inFire(), 6f);
		helper.assertFalse(allowed, "fire is ignored");
		helper.assertTrue(IronManEnergy.integrity(p, M4) == 1000f, "fire never drains integrity");
		// an ordinary hit (v0.15.3): it lands in full, and what lands costs 75% of it in integrity
		helper.assertTrue(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, sources.generic(), 20f), "the hit lands");
		helper.assertTrue(IronManEnergy.integrity(p, M4) == 1000f, "nothing is soaked up front");
		ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(p, sources.generic(), 20f, 20f, false);
		helper.assertTrue(Math.abs(IronManEnergy.integrity(p, M4) - 985f) < 0.01f, "integrity takes 75%, at " + IronManEnergy.integrity(p, M4));
		// Sneak+V Energy Shield works on the Mark 4 too
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(IronManMark3.shieldOn(p) && IronManMark3.shieldOn(TonyStark.state(p), M4), "Mark 4 shield up");
		helper.assertFalse(IronManMark3.shieldOn(TonyStark.state(p), M3), "on the Mark 4's own key");
		float before = IronManEnergy.energy(p, M4);
		helper.assertFalse(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, sources.generic(), 30f), "blocked");
		helper.assertTrue(Math.abs(before - IronManEnergy.energy(p, M4) - 3f) < 0.01f, "same 10% tax");
		IronManMark3.shutDown(p);
		helper.assertFalse(IronManMark3.shieldOn(TonyStark.state(p), M4), "shutdown drops the Mark 4 shield");
		helper.succeed();
	}
}
