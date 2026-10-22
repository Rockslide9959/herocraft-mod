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
import com.projecthero.mod.ironman.IronManIntegrity;
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

	// ------------------------------------------------------------------ 1. numbers + layout (v0.16.1 comic rework)

	@GameTest(template = EMPTY_STRUCTURE)
	public void markSixAndSevenStatsAndKeys(GameTestHelper h) {
		IronManSuit m6 = IronManSuits.MARK_6;
		IronManSuit m7 = IronManSuits.MARK_VII;
		h.assertTrue(m6.maxIntegrity() == 2200f && Math.abs(m6.absorbShare() - 0.45f) < 1e-4f, "Mark 6 (Silver Centurion): 2200 integrity, absorbs 45%");
		h.assertTrue(m7.maxIntegrity() == 2600f && Math.abs(m7.absorbShare() - 0.50f) < 1e-4f, "Mark 7 (Space): 2600 integrity, absorbs 50%");
		h.assertTrue(m6.selfRepairPerSecond() > 0f && m7.selfRepairPerSecond() > m6.selfRepairPerSecond(),
				"both self-repair out of combat, the Space armour faster");
		h.assertTrue(m6.resistanceAmplifier() == 0, "Mark 6: Resistance I");
		h.assertTrue(m7.resistanceAmplifier() < 0, "Mark 7: no Resistance");
		h.assertTrue(m7.hazardImmune() && !m6.hazardImmune(), "only the Space armour ignores fire / lava / freezing");
		String[] comic = { com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_R, com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_G,
				com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_X, com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_Z,
				com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_V };
		for (IronManSuit s : new IronManSuit[] { m6, m7 }) {
			String id = s.id();
			h.assertTrue(s.targeting() && s.waterBreathing(), id + ": targeting, water breathing");
			h.assertTrue(s.strengthBonus() == 9f, id + ": +9 melee");
			h.assertFalse(s.hurtRegeneration(), id + ": no Regeneration-while-hurt any more");
			for (int slot = 1; slot <= 5; slot++) {
				h.assertTrue(comic[slot - 1].equals(s.abilityInSlot(slot)), id + " slot " + slot + " is the comic kit");
			}
			h.assertTrue(com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_C.equals(s.abilityInSlot(6)),
					id + ": C is a kit key (v0.16.7 (user): a utility moved off V; Shift+C folds)");
			h.assertTrue(s.fullBodyShield(), id + ": the 360-degree shield");
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ 2. pools + out-of-combat self-repair

	@GameTest(template = EMPTY_STRUCTURE)
	public void poolsCapAndRegenerateWhileWorn(GameTestHelper h) {
		for (String id : new String[] { M6, M7 }) {
			ServerPlayer p = suited(h, id);
			IronManEnergy.setIntegrity(p, id, 99_999f);
			h.assertTrue(IronManEnergy.integrity(p, id) == IronManEnergy.maxIntegrity(id), id + " integrity caps");
			h.assertTrue(IronManEnergy.energy(p, id) == IronManEnergy.capacity(id) && IronManEnergy.capacity(id) < IronManEnergy.FULL * 3f
					&& IronManEnergy.real(id), id + " v0.16.7 (user): a real Era II reactor, full until used");
			IronManEnergy.setIntegrity(p, id, 100f);
			IronManSuit suit = IronManSuits.byId(id);
			// in combat: no repair
			IronManIntegrity.markCombat(p);
			h.assertFalse(IronManIntegrity.repairStep(p, suit), id + " never repairs within 10 s of a fight");
			h.assertTrue(IronManEnergy.integrity(p, id) == 100f, id + " integrity unchanged in combat");
			// out of combat: one second of self-repair
			IronManIntegrity.setLastCombat(p, IronManIntegrity.OUT_OF_COMBAT_TICKS + 1);
			h.assertTrue(IronManIntegrity.repairStep(p, suit), id + " repairs out of combat");
			h.assertTrue(Math.abs(IronManEnergy.integrity(p, id) - (100f + suit.selfRepairPerSecond())) < 1e-3f,
					id + " repairs " + suit.selfRepairPerSecond() + "/s, got " + IronManEnergy.integrity(p, id));
			// the chestplate off: nothing
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			h.assertFalse(IronManIntegrity.repairStep(p, suit), id + " needs the chestplate on");
			leave(h, p);
		}
		// every other mark never repairs itself while worn
		ServerPlayer p4 = suited(h, "mark_4");
		IronManEnergy.setIntegrity(p4, "mark_4", 100f);
		IronManIntegrity.setLastCombat(p4, IronManIntegrity.OUT_OF_COMBAT_TICKS + 1);
		h.assertFalse(IronManIntegrity.repairStep(p4, IronManSuits.MARK_4), "a Mark 4 never repairs itself");
		h.assertTrue(IronManEnergy.integrity(p4, "mark_4") == 100f, "a Mark 4 still never repairs itself while worn");
		leave(h, p4);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void integrityRulesMatchTheMarkFour(GameTestHelper h) {
		// v0.16.1: every mark absorbs its own share of a hit from integrity
		for (String id : new String[] { "mark_4", M6, M7 }) {
			ServerPlayer p = suited(h, id);
			float before = IronManEnergy.integrity(p, id);
			float share = IronManSuits.byId(id).absorbShare();
			float landed = IronManDamage.absorb(p, IronManSuits.byId(id), p.damageSources().generic(), 10f);
			h.assertTrue(Math.abs(landed - 10f * (1f - share)) < 1e-3f, id + ": the wearer takes the unabsorbed rest");
			h.assertTrue(Math.abs(before - IronManEnergy.integrity(p, id) - 10f * share) < 1e-3f, id + ": integrity takes its share");
			leave(h, p);
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ 3. Resistance I (the Centurion only)

	@GameTest(template = EMPTY_STRUCTURE)
	public void resistanceTwoWithTheChestplate(GameTestHelper h) {
		ServerPlayer p = suited(h, M6);
		tick(p, 1);
		MobEffectInstance res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
		h.assertTrue(res != null && res.getAmplifier() == 0, "Mark 6: Resistance I with the chestplate on");
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		tick(p, 1);
		h.assertTrue(p.getEffect(MobEffects.DAMAGE_RESISTANCE) == null, "gone with the chestplate");
		leave(h, p);
		ServerPlayer p7 = suited(h, M7);
		tick(p7, 1);
		h.assertTrue(p7.getEffect(MobEffects.DAMAGE_RESISTANCE) == null, "Mark 7: no Resistance any more");
		leave(h, p7);
		h.succeed();
	}
}
