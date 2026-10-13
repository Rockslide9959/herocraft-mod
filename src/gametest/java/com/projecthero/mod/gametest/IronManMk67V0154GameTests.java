package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.ability.IronManFlares;
import com.projecthero.mod.ironman.ability.IronManHeldBeam;
import com.projecthero.mod.ironman.ability.IronManMark3;
import com.projecthero.mod.ironman.ability.IronManMark6;
import com.projecthero.mod.ironman.ability.IronManMark7;
import com.projecthero.mod.ironman.ability.IronManSonicClap;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.network.IronManWeaponWheelPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.15.4 (explicit user spec): the Mark 6 / Mark 7 rework -- pools and regen (4000 / 4500 energy at 5/s, 2500 / 2750
 * integrity repairing 1/s while worn), Resistance II with the chestplate, Regeneration I only while hurt and draining 3
 * energy/s, the modern kit (G fires the wheel weapon, Shift+G Sonic Clap, X flares or a supersonic boost in the air,
 * Shift+X JARVIS, V wheel / Shift+V shield) and the Mark 7's Z red laser vs Shift+Z Unibeam. Mock players never tick
 * on their own, so every timing check drives the ticker by hand.
 */
public class IronManMk67V0154GameTests implements FabricGameTest {
	private static final String M6 = IronManMark6.SUIT_ID;
	private static final String M7 = IronManMark7.SUIT_ID;
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = IronManMk67V0154GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
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

	static ServerPlayer suited(GameTestHelper h, String suitId) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f); // facing +Z
		p.setXRot(0f);
		p.setYHeadRot(0f);
		for (ArmorItem.Type type : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(type), new ItemStack(IronManItems.armor(suitId, type)));
		}
		TonyStark.setActiveSuit(p, suitId);
		IronManEnergy.setEnergy(p, suitId, 2000f);
		IronManEnergy.setIntegrity(p, suitId, IronManEnergy.maxIntegrity(suitId));
		return p;
	}

	private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz) {
		T m = type.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0f, 0f);
		m.setNoAi(true);
		h.getLevel().addFreshEntity(m);
		return m;
	}

	private static void press(ServerPlayer p, AbilitySlot slot) {
		IronManAbilityManager.handle(p, slot, true);
		IronManAbilityManager.handle(p, slot, false);
	}

	private static void sneakPress(ServerPlayer p, AbilitySlot slot) {
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, slot, true);
		IronManAbilityManager.handle(p, slot, false);
		p.setShiftKeyDown(false);
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static List<IronManMissileEntity> missiles(GameTestHelper h, ServerPlayer p) {
		return h.getLevel().getEntitiesOfClass(IronManMissileEntity.class, p.getBoundingBox().inflate(8), m -> m.getOwner() == p);
	}

	private static void tick(ServerPlayer p, int ticks) {
		for (int i = 0; i < ticks; i++) {
			IronManSuitTicker.tick(p);
		}
	}

	// ------------------------------------------------------------------ 1. numbers + layout

	@GameTest(template = EMPTY_STRUCTURE)
	public void markSixAndSevenStatsAndKeys(GameTestHelper h) {
		IronManSuit m6 = IronManSuits.MARK_6;
		IronManSuit m7 = IronManSuits.MARK_VII;
		h.assertTrue(m6.energyCapacity() == 4000f && m6.energyRegenPerSecond() == 5f, "Mark 6: 4000 energy at 5/s");
		h.assertTrue(m6.maxIntegrity() == 2500f && m6.wornIntegrityRegenPerSecond() == 0f, "Mark 6: 2500 integrity, no worn repair (v0.15.9)");
		h.assertTrue(m7.energyCapacity() == 4500f && m7.energyRegenPerSecond() == 5f, "Mark 7: 4500 energy at 5/s");
		h.assertTrue(m7.maxIntegrity() == 2750f && m7.wornIntegrityRegenPerSecond() == 0f, "Mark 7: 2750 integrity, no worn repair (v0.15.9)");
		for (IronManSuit s : new IronManSuit[] { m6, m7 }) {
			String id = s.id();
			h.assertTrue(s.resistanceAmplifier() == 1, id + ": Resistance II");
			h.assertTrue(s.targeting() && s.autoFeed() && s.waterBreathing(), id + ": targeting, auto-feed, water breathing");
			h.assertTrue(s.strengthBonus() == 7f, id + ": +7 melee");
			h.assertTrue(s.hurtRegeneration() && s.hurtRegenEnergyPerSecond() == 3f, id + ": Regeneration I at 3 energy/s");
			h.assertTrue(IronManAbilities.REPULSOR_BLAST.equals(s.abilityInSlot(1)) && s.hasDash(), id + ": R repulsor / Shift dash");
			h.assertTrue(IronManMark6.WEAPON.equals(s.abilityInSlot(2)), id + ": G wheel weapon / Shift Sonic Clap");
			h.assertTrue(IronManMark6.FLARES.equals(s.abilityInSlot(3)), id + ": X flares / supersonic / Shift JARVIS");
			h.assertTrue(IronManMark6.WHEEL.equals(s.abilityInSlot(5)), id + ": V wheel / Shift energy shield");
			h.assertTrue(IronManAbilities.SUIT_TOGGLE.equals(s.abilityInSlot(6)), id + ": C store");
			h.assertTrue(s.fullBodyShield(), id + ": the 360-degree shield");
		}
		h.assertTrue(IronManMark6.UNIBEAM.equals(m6.abilityInSlot(4)), "Mark 6 Z: Unibeam");
		h.assertTrue(IronManMark7.LASER.equals(m7.abilityInSlot(4)), "Mark 7 Z: red laser (Shift: Unibeam)");
		// no Mark 4 differences in the integrity rule: hits land in full, 75% of it is integrity wear
		h.assertTrue(java.util.Arrays.asList(IronManMark6.WEAPONS).equals(List.of(IronManMark6.BARRAGE, IronManAbilities.MICRO_MISSILES,
				IronManAbilities.WRIST_LASER, IronManAbilities.FLAMETHROWER, IronManAbilities.ROCKET)),
				"the wheel: Shoulder Barrage, Micro-Missiles, Wrist Laser, Flamethrower, Rocket");
		for (int slot = 1; slot <= 6; slot++) {
			h.assertFalse("mk6_surge".equals(m6.abilityInSlot(slot)), "the Arc Reactor Surge is gone");
		}
		// every new player-facing string exists, and the wheel's names / descriptions fit its centre disc
		for (String k : new String[] { "mk6_weapon", "mk6_flares", "mk6_unibeam", "mk6_wheel", "mk6_barrage", "mk7_laser" }) {
			t("hud.projecthero.ironman.ability." + k);
		}
		t("message.projecthero.ironman.mk7.red_laser_firing");
		h.assertTrue(t("hud.projecthero.ironman.chip.mk6_wheel").startsWith("G"), "the HUD chip names the G key");
		t("screen.projecthero.weapon_wheel.mk6_title");
		t("screen.projecthero.weapon_wheel.mk7_title");
		t("screen.projecthero.ironman_info.integrity_regen");
		t("screen.projecthero.ironman_info.hurt_regen_value");
		int tw = IronManUiLayout.wheelTextWidth(IronManUiLayout.wheelRadii(320, 240)[0]);
		for (String w : IronManMark6.WEAPONS) {
			h.assertTrue(IronManUiLayout.approxWidth(t("hud.projecthero.ironman.ability." + w)) <= tw, "wheel name fits: " + w);
			h.assertTrue(IronManUiLayout.wrapLines(t("screen.projecthero.weapon_wheel.desc.mk6." + w), tw,
					IronManUiLayout::approxWidth) <= 2, "wheel description wraps to two lines at most: " + w);
		}
		h.assertTrue(t("projecthero.guide.iron_man.mark_6.body").contains("Resistance II")
				&& !t("projecthero.guide.iron_man.mark_6.body").contains("Surge"), "the guide describes the new Mark 6");
		h.assertTrue(t("projecthero.guide.iron_man.mark_vii.body").contains("red laser"), "the guide describes the Mark 7 Z");
		h.succeed();
	}

	// ------------------------------------------------------------------ 2. pools + regen

	@GameTest(template = EMPTY_STRUCTURE)
	public void poolsCapAndRegenerateWhileWorn(GameTestHelper h) {
		for (String id : new String[] { M6, M7 }) {
			ServerPlayer p = suited(h, id);
			IronManEnergy.setEnergy(p, id, 99_999f);
			IronManEnergy.setIntegrity(p, id, 99_999f);
			h.assertTrue(IronManEnergy.energy(p, id) == IronManEnergy.capacity(id), id + " energy caps at " + IronManEnergy.capacity(id));
			h.assertTrue(IronManEnergy.integrity(p, id) == IronManEnergy.maxIntegrity(id), id + " integrity caps");
			IronManEnergy.setEnergy(p, id, 1000f);
			IronManEnergy.setIntegrity(p, id, 100f);
			tick(p, 20); // one second, at full health, on the ground
			float e = IronManEnergy.energy(p, id);
			float i = IronManEnergy.integrity(p, id);
			h.assertTrue(Math.abs(e - 1005f) < 0.05f, id + " energy regenerates 5/s, got " + e);
			h.assertTrue(i == 100f, id + " v0.15.9: integrity no longer repairs while worn, got " + i);
			leave(h, p);
		}
		// every other mark keeps the v0.15.3 rule: no worn repair
		ServerPlayer p4 = suited(h, "mark_4");
		IronManEnergy.setIntegrity(p4, "mark_4", 100f);
		tick(p4, 20);
		h.assertTrue(IronManEnergy.integrity(p4, "mark_4") == 100f, "a Mark 4 still never repairs itself while worn");
		leave(h, p4);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void integrityRulesMatchTheMarkFour(GameTestHelper h) {
		for (String id : new String[] { "mark_4", M6, M7 }) {
			ServerPlayer p = suited(h, id);
			float before = IronManEnergy.integrity(p, id);
			h.assertTrue(IronManDamage.onAllowDamage(p, p.damageSources().generic(), 10f), id + ": the hit lands in full");
			IronManDamage.onDamageTaken(p, p.damageSources().generic(), 10f);
			h.assertTrue(Math.abs(before - IronManEnergy.integrity(p, id) - 10f) < 1e-3f, id + ": v0.15.9: 10 damage = 10 integrity");
			leave(h, p);
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ 3. Resistance II + Regeneration I

	@GameTest(template = EMPTY_STRUCTURE)
	public void resistanceTwoWithTheChestplate(GameTestHelper h) {
		for (String id : new String[] { M6, M7 }) {
			ServerPlayer p = suited(h, id);
			tick(p, 1);
			MobEffectInstance res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
			h.assertTrue(res != null && res.getAmplifier() == 1, id + ": Resistance II with the chestplate on");
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			tick(p, 1);
			h.assertTrue(p.getEffect(MobEffects.DAMAGE_RESISTANCE) == null, id + ": gone with the chestplate");
			leave(h, p);
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void regenerationOnlyWhileHurtAndItCostsEnergy(GameTestHelper h) {
		ServerPlayer p = suited(h, M6);
		IronManEnergy.setEnergy(p, M6, 1000f);
		tick(p, 20);
		h.assertTrue(p.getEffect(MobEffects.REGENERATION) == null, "no Regeneration at full health");
		h.assertTrue(Math.abs(IronManEnergy.energy(p, M6) - 1005f) < 0.05f, "and no drain: plain +5/s");

		p.setHealth(10f);
		IronManEnergy.setEnergy(p, M6, 1000f);
		tick(p, 20);
		MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
		h.assertTrue(regen != null && regen.getAmplifier() == 0, "Regeneration I while hurt");
		h.assertTrue(IronManMark6.regenerating(p), "it is the suit's");
		float e = IronManEnergy.energy(p, M6);
		h.assertTrue(Math.abs(e - 1002f) < 0.05f, "+5/s regen - 3/s for the Regeneration = +2 over a second, got " + e);

		p.setHealth(p.getMaxHealth());
		tick(p, 1);
		h.assertTrue(p.getEffect(MobEffects.REGENERATION) == null, "back at full health it comes off");
		IronManEnergy.setEnergy(p, M6, 1000f);
		tick(p, 20);
		h.assertTrue(Math.abs(IronManEnergy.energy(p, M6) - 1005f) < 0.05f, "and the drain stops");

		// someone else's Regeneration (a potion) is never charged for or removed
		p.setHealth(10f);
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
		IronManEnergy.setEnergy(p, M6, 1000f);
		tick(p, 20);
		h.assertTrue(Math.abs(IronManEnergy.energy(p, M6) - 1005f) < 0.05f, "a potion's Regeneration costs the suit nothing");
		p.setHealth(p.getMaxHealth());
		tick(p, 1);
		h.assertTrue(p.getEffect(MobEffects.REGENERATION) != null && p.getEffect(MobEffects.REGENERATION).getAmplifier() == 1,
				"and is left alone");
		p.removeEffect(MobEffects.REGENERATION);

		// off with the suit
		p.setHealth(10f);
		tick(p, 2);
		h.assertTrue(IronManMark6.regenerating(p), "regenerating again");
		for (ArmorItem.Type type : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(type), ItemStack.EMPTY);
		}
		tick(p, 1);
		h.assertTrue(p.getEffect(MobEffects.REGENERATION) == null, "taking the suit off ends it");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ 4. G fires the wheel weapon

	@GameTest(template = EMPTY_STRUCTURE)
	public void gFiresTheSelectedWheelWeapon(GameTestHelper h) {
		ServerPlayer p = suited(h, M6);
		var suit = IronManSuits.MARK_6;
		h.assertTrue(IronManMark6.BARRAGE.equals(IronManMark6.selectedWeapon(TonyStark.state(p), M6)), "Shoulder Barrage by default");

		// Shoulder Barrage
		press(p, AbilitySlot.SLOT_2);
		h.assertTrue(IronManMark6.pendingBarrage(p) == IronManMark6.BARRAGE_COUNT - 1, "G launches the first of 6 barrage missiles");
		List<IronManMissileEntity> mine = missiles(h, p);
		h.assertTrue(mine.size() == 1 && mine.get(0).isHoming()
				&& mine.get(0).directDamage() == IronManMark6.MARK_6_TUNING.barrageDamage(), "a homing barrage missile");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManMark6.BARRAGE) == IronManMark6.BARRAGE_COOLDOWN, "14 s cooldown");
		mine.forEach(m -> m.discard());
		IronManMark6.shutDown(p);

		// the wheel release picks the next weapon; G now fires it
		IronManWeaponWheelPayload.handleServer(p, IronManAbilities.MICRO_MISSILES);
		h.assertTrue(IronManAbilities.MICRO_MISSILES.equals(IronManMark6.selectedWeapon(TonyStark.state(p), M6)), "Micro-Missiles picked");
		press(p, AbilitySlot.SLOT_2);
		h.assertTrue(TonyStark.state(p).pendingMissiles == suit.missileCount(), "G queues the micro-missile volley");

		IronManWeaponWheelPayload.handleServer(p, IronManAbilities.WRIST_LASER);
		press(p, AbilitySlot.SLOT_2);
		h.assertTrue(IronManMark6.laserFiring(p), "G fires the wrist laser");
		h.assertFalse(TonyStark.overloaded(p), "no Mark 4 overload");
		IronManMark6.shutDown(p);
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManAbilities.WRIST_LASER) == IronManMark6.LASER_COOLDOWN,
				"wrist laser cooldown once it stops");

		IronManWeaponWheelPayload.handleServer(p, IronManAbilities.FLAMETHROWER);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		h.assertTrue(TonyStark.state(p).flamethrowerHeld, "holding G runs the flamethrower");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		h.assertFalse(TonyStark.state(p).flamethrowerHeld, "letting go stops it");

		IronManWeaponWheelPayload.handleServer(p, IronManAbilities.ROCKET);
		press(p, AbilitySlot.SLOT_2);
		List<IronManMissileEntity> rockets = missiles(h, p).stream()
				.filter(m -> m.directDamage() == IronManMark6.MARK_6_TUNING.rocketDamage()).toList();
		h.assertTrue(rockets.size() == 1, "G fires a " + IronManMark6.MARK_6_TUNING.rocketDamage() + "-damage rocket");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManAbilities.ROCKET) == IronManMark6.ROCKET_COOLDOWN, "10 s cooldown");
		missiles(h, p).forEach(m -> m.discard());

		// Shift+G is the Sonic Clap, whatever the wheel holds
		sneakPress(p, AbilitySlot.SLOT_2);
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManSonicClap.ABILITY_ID) == IronManMark6.SONIC_CLAP_COOLDOWN,
				"Shift+G: Sonic Clap");

		// V opens the wheel (no shield); Shift+V holds the 360-degree energy shield
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		h.assertFalse(IronManAbilities.barrierActive(p), "plain V never raises the shield");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		p.setShiftKeyDown(false);
		h.assertTrue(IronManAbilities.barrierActive(p), "Shift + hold V raises the energy shield");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		h.assertFalse(IronManAbilities.barrierActive(p), "letting go drops it");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManAbilities.REPULSOR_BARRIER) > 0, "shield cooldown");
		h.assertTrue(IronManAbilities.REPULSOR_BARRIER.equals(IronManMark6.hudCooldownId(IronManMark6.WHEEL)), "the V box reads it");
		IronManMark6.shutDown(p);
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void eachSuitKeepsItsOwnWheelPick(GameTestHelper h) {
		ServerPlayer p = suited(h, M7);
		IronManWeaponWheelPayload.handleServer(p, IronManAbilities.ROCKET);
		h.assertTrue(IronManAbilities.ROCKET.equals(IronManMark6.selectedWeapon(TonyStark.state(p), M7)), "the Mark 7 picked the rocket");
		h.assertTrue(IronManMark6.BARRAGE.equals(IronManMark6.selectedWeapon(TonyStark.state(p), M6)), "the Mark 6 keeps its own pick");
		press(p, AbilitySlot.SLOT_2);
		h.assertTrue(missiles(h, p).stream().anyMatch(m -> m.directDamage() == IronManMark6.MARK_7_TUNING.rocketDamage()),
				"the Mark 7 rocket hits harder (" + IronManMark6.MARK_7_TUNING.rocketDamage() + ")");
		missiles(h, p).forEach(m -> m.discard());
		h.assertTrue(IronManMark6.wheelSuit(IronManMark6.openWheelPayload(M7)).equals(M7)
				&& IronManMark6.wheelSuit("mk6:mark_iii") == null && IronManMark3.wheelSuit(IronManMark6.openWheelPayload(M6)) == null,
				"wheel-open payloads route to the right screen");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ 5. X: flares on the ground, supersonic in the air

	@GameTest(template = EMPTY_STRUCTURE)
	public void xIsFlaresOnTheGroundAndSupersonicInTheAir(GameTestHelper h) {
		ServerPlayer p = suited(h, M6);
		long now = p.level().getGameTime();
		press(p, AbilitySlot.SLOT_3);
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManAbilities.FLARE) == IronManMark6.FLARE_COOLDOWN, "X on the ground: flares");
		h.assertFalse(IronManFlares.boosting(TonyStark.state(p), M6, now), "no boost on the ground");

		ServerPlayer q = suited(h, M7);
		IronManFlight.setFlying(q, true);
		h.assertTrue(IronManFlight.isFlying(q), "flying");
		press(q, AbilitySlot.SLOT_3);
		h.assertTrue(IronManFlares.boosting(TonyStark.state(q), M7, q.level().getGameTime()), "X while flying: the supersonic boost");
		h.assertTrue(TonyStark.abilityCooldownRemaining(q, M7, IronManAbilities.FLARE) == 0, "...instead of the flares");

		sneakPress(p, AbilitySlot.SLOT_3);
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManMark3.JARVIS_SCAN) > 0, "Shift+X: JARVIS scan");
		IronManFlight.setFlying(q, false);
		leave(h, p);
		leave(h, q);
		h.succeed();
	}

	// ------------------------------------------------------------------ 6. Z: Mark 7 red laser vs Shift+Z Unibeam

	@GameTest(template = EMPTY_STRUCTURE)
	public void markSevenZIsARedLaserAndShiftZTheUnibeam(GameTestHelper h) {
		h.assertTrue(IronManMark7.RED_LASER_DAMAGE < IronManMark7.UNIBEAM_DAMAGE, "the laser hits a bit below the Unibeam");
		h.assertTrue(IronManMark7.RED_LASER.laser() && IronManMark7.RED_LASER.beamKind() == IronManHeldBeam.RED_LASER_BEAM,
				"drawn as the red laser beam");
		h.assertFalse(IronManMark7.BEAM.laser(), "the Unibeam stays the chest beam");

		ServerPlayer p = suited(h, M7);
		Zombie z = mob(h, EntityType.ZOMBIE, p, 0.0, 4.0);
		float hp = z.getHealth();
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		h.assertTrue(IronManMark7.redLaserFiring(p), "holding Z fires the red laser");
		IronManMark6.tickForTest(p, IronManSuits.MARK_VII);
		IronManMark6.tickForTest(p, IronManSuits.MARK_VII);
		h.assertTrue(IronManMark7.redLaserFiring(p), "still firing while held");
		h.assertTrue(z.getHealth() < hp, "the laser burns the zombie it is pointed at (" + z.getHealth() + " / " + hp + ")");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
		h.assertFalse(IronManHeldBeam.firing(p), "release stops it");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M7, IronManMark7.LASER) == IronManMark7.RED_LASER_COOLDOWN, "laser cooldown");

		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		p.setShiftKeyDown(false);
		IronManHeldBeam.Spec spec = IronManHeldBeam.current(p);
		h.assertTrue(spec != null && !spec.laser() && IronManMark7.UNIBEAM.equals(spec.cooldownId()), "Shift + hold Z fires the Unibeam");
		h.assertFalse(IronManMark7.redLaserFiring(p), "not the laser");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M7, IronManMark7.UNIBEAM) == IronManMark7.UNIBEAM_COOLDOWN, "Unibeam cooldown");

		// the Mark 6's Z is the Unibeam outright
		ServerPlayer p6 = suited(h, M6);
		IronManAbilityManager.handle(p6, AbilitySlot.SLOT_4, true);
		IronManHeldBeam.Spec spec6 = IronManHeldBeam.current(p6);
		h.assertTrue(spec6 != null && !spec6.laser() && IronManMark6.UNIBEAM.equals(spec6.cooldownId()), "Mark 6 Z: the Unibeam");
		IronManAbilityManager.handle(p6, AbilitySlot.SLOT_4, false);
		z.discard();
		leave(h, p);
		leave(h, p6);
		h.succeed();
	}
}
