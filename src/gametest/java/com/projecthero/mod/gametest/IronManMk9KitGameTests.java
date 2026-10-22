package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.IronManComicRouter;
import com.projecthero.mod.ironman.ability.IronManComicState;
import com.projecthero.mod.ironman.ability.IronManMark9;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.16.7 (user) Era II stage 3: the Mk IX Inferno's kit ({@code docs/IRONMAN_ERA2_KITS.md}) -- its shape (same form as
 * the Era II siblings, no energy), the heat sink (fills, vents, Hot Plating, saturation lock), the moves (Fire Blast,
 * Inferno Stream hold + cooldown, Magma Bomb + pool, Heat Dump nova, Thermal Siphon), the cleanup and the UI text widths.
 */
public class IronManMk9KitGameTests implements FabricGameTest {
	private static final String MK9 = "mark_9";
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };
	private static final float HP = 300f;
	private static JsonObject lang;

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer tony(GameTestHelper h) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		BlockPos at = h.absolutePos(new BlockPos(3, 2, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f); // facing +Z
		p.setXRot(0f);
		p.setYHeadRot(0f);
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(MK9, TYPES[i])));
		}
		TonyStark.setActiveSuit(p, MK9);
		IronManEnergy.setIntegrity(p, MK9, IronManEnergy.maxIntegrity(MK9));
		IronManMark9.setHeat(p, 0f);
		return p;
	}

	private static LivingEntity husk(GameTestHelper h, ServerPlayer p, double dx, double dz) {
		Mob m = EntityType.HUSK.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180f, 0f);
		m.setNoAi(true);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HP);
		m.setHealth(HP);
		h.getLevel().addFreshEntity(m);
		return m;
	}

	private static float lost(LivingEntity e) {
		return HP - e.getHealth();
	}

	private static IronManSuit mk9() {
		return IronManSuits.byId(MK9);
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = IronManMk9KitGameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
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

	// ------------------------------------------------------------------ the kit

	@GameTest(template = EMPTY_STRUCTURE)
	public void theInfernoHasAFiveKeyKitLikeItsEraTwoSiblings(GameTestHelper h) {
		IronManComicKit.Kit kit = IronManComicKit.kit(MK9);
		h.assertTrue(kit != null && kit.keys().size() == 5, "a five-key (R G X Z V) kit, as the Mk VI / VII / VIII / Hulkbuster");
		String[][] ids = { { "mk9_fire_blast", "mk9_inferno_stream" }, { "mk9_magma_fist", "mk9_eruption" },
				{ "mk9_afterburner", "mk9_lava_geyser" }, { "mk9_magma_bomb", "mk9_firestorm" }, { "mk9_heat_dump", "mk9_thermal_siphon" } };
		for (int slot = 1; slot <= 5; slot++) {
			for (int s = 0; s < 2; s++) {
				IronManComicKit.Move m = IronManComicKit.move(MK9, slot, s == 1);
				h.assertTrue(m != null && m.id().equals(ids[slot - 1][s]), "slot " + slot + (s == 1 ? " Shift" : " tap") + " = " + ids[slot - 1][s]);
				h.assertTrue(m.cooldownTicks() > 0, m.id() + " has a cooldown");
				h.assertTrue(IronManComicKit.energyCost(m.id()) == 0f && IronManComicKit.energyPerSecond(m.id()) == 0f,
						m.id() + " costs no energy (Era II keeps the no-energy shim)");
				h.assertTrue(IronManComicKit.isKitSlot(mk9().abilityInSlot(slot)), "slot " + slot + " routes to the comic kit");
				t(m.nameKey()); // the HUD name exists
			}
		}
		h.assertTrue(IronManComicKit.byId("mk9_inferno_stream").input() == IronManComicKit.Input.HOLD, "the stream is held");
		h.assertTrue(IronManAbilities.SUIT_TOGGLE.equals(mk9().abilityInSlot(6)), "C stays the suit toggle (fold hint; Shift+C folds)");
		h.assertTrue(IronManEnergy.capacity(MK9) == IronManEnergy.FULL, "no reactor");
		// on the Era II scale: between the Mk VIII Space and the Hulkbuster
		h.assertTrue(IronManComicKit.byId("mk9_fire_blast").damage() >= IronManComicKit.byId("mk7_repulsor").damage()
				&& IronManComicKit.byId("mk9_fire_blast").damage() < IronManComicKit.byId("mk8_punch_combo").damage(), "R on the Era II scale");
		h.succeed();
	}

	// ------------------------------------------------------------------ the heat sink

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHeatSinkFillsOnMagmaVentsInTheOpenAndHeatsThePlating(GameTestHelper h) {
		ServerPlayer p = tony(h);
		// venting: -3/s in the open
		IronManMark9.setHeat(p, 40f);
		h.assertTrue(IronManMark9.hotMultiplier(p) == 1f, "under 50: no Hot Plating");
		IronManMark9.tickForTests(p);
		h.assertTrue(Math.abs(IronManMark9.heat(p) - (40f - IronManMark9.VENT_PER_SECOND / 20f)) < 1.0e-3f, "vents 3/s: " + IronManMark9.heat(p));
		// heating: standing on magma, +2/s
		h.getLevel().setBlockAndUpdate(p.getOnPos(), Blocks.MAGMA_BLOCK.defaultBlockState());
		float before = IronManMark9.heat(p);
		IronManMark9.tickForTests(p);
		h.assertTrue(Math.abs(IronManMark9.heat(p) - (before + IronManMark9.FIRE_HEAT_PER_SECOND / 20f)) < 1.0e-3f,
				"magma heats it 2/s: " + IronManMark9.heat(p));
		// Hot Plating
		IronManMark9.setHeat(p, 60f);
		h.assertTrue(IronManMark9.hotMultiplier(p) == IronManMark9.HOT_MULT, "50+: the fire moves hit 25% harder");
		// the HUD copy is synced
		h.assertTrue(Math.abs(IronManMark9.syncedHeat(p) - 60f) < 0.6f, "the HUD attachment follows");
		// fire an attacker throws heats it; the environment does not count twice
		var blaze = EntityType.BLAZE.create(h.getLevel());
		blaze.moveTo(p.getX(), p.getY(), p.getZ() + 4);
		h.getLevel().addFreshEntity(blaze);
		IronManMark9.onFireShrugged(p, p.damageSources().fireball(null, blaze), 4f);
		h.assertTrue(Math.abs(IronManMark9.heat(p) - (60f + 4f * IronManMark9.HIT_HEAT_PER_DAMAGE)) < 1.0e-3f, "a blaze fireball heats the sink");
		IronManMark9.onFireShrugged(p, p.damageSources().lava(), 4f);
		h.assertTrue(Math.abs(IronManMark9.heat(p) - 66f) < 1.0e-3f, "lava damage is counted by the tick, not here");
		blaze.discard();
		// taking the suit off empties it
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		IronManMark9.tickForTests(p);
		h.assertTrue(IronManMark9.heat(p) == 0f, "suit off: the sink is empty");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aFullSinkLocksTheFireMovesUntilTheHeatDumpVentsIt(GameTestHelper h) {
		ServerPlayer p = tony(h);
		LivingEntity husk = husk(h, p, 0, 3);
		IronManMark9.setHeat(p, IronManMark9.HEAT_MAX);
		h.assertTrue(IronManMark9.saturated(p), "100 = saturated");
		h.assertFalse(IronManComicRouter.fireForTests(p, mk9(), 1, false), "Fire Blast refuses on a full sink");
		h.assertFalse(IronManComicRouter.fireForTests(p, mk9(), 4, false), "Magma Bomb refuses on a full sink");
		h.assertTrue(TonyStark.abilityReady(p, MK9, "mk9_fire_blast"), "a refused move starts no cooldown");
		float radius = (float) IronManMark9.dumpRadius(IronManMark9.HEAT_MAX);
		float dmg = IronManMark9.dumpDamage(IronManMark9.HEAT_MAX);
		h.assertTrue(Math.abs(radius - 12f) < 1.0e-3f && Math.abs(dmg - 46f) < 1.0e-3f, "a full dump: 46 in 12 blocks");
		h.assertTrue(IronManComicRouter.fireForTests(p, mk9(), 5, false), "Heat Dump always works");
		h.assertTrue(IronManMark9.heat(p) == 0f && !IronManMark9.saturated(p), "the sink is vented and unlocked");
		// the nova lands 4 ticks later (the crouch)
		h.runAfterDelay(6, () -> {
			IronManComicState.tickServerForTests(h.getLevel().getServer());
			h.assertTrue(lost(husk) >= dmg - 1f, "the nova hit for ~" + dmg + ", got " + lost(husk));
			h.assertTrue(husk.isOnFire(), "and set it burning");
			h.assertFalse(TonyStark.abilityReady(p, MK9, "mk9_heat_dump"), "Heat Dump on cooldown");
			// the lock is gone: Fire Blast works again
			h.assertTrue(IronManComicRouter.fireForTests(p, mk9(), 1, false), "Fire Blast works after the dump");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHeatDumpNeedsTwentyHeat(GameTestHelper h) {
		ServerPlayer p = tony(h);
		IronManMark9.setHeat(p, 10f);
		h.assertFalse(IronManComicRouter.fireForTests(p, mk9(), 5, false), "10 heat is not enough");
		h.assertTrue(IronManMark9.heat(p) == 10f && TonyStark.abilityReady(p, MK9, "mk9_heat_dump"), "nothing vented, no cooldown");
		h.assertTrue(IronManMark9.dumpRadius(IronManMark9.DUMP_MIN) > 5.5 && IronManMark9.dumpDamage(IronManMark9.DUMP_MIN) > 17f,
				"a minimum dump still reaches ~5.6 blocks for ~17");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ moves

	@GameTest(template = EMPTY_STRUCTURE)
	public void fireBlastBurnsAndHeats(GameTestHelper h) {
		ServerPlayer p = tony(h);
		LivingEntity husk = husk(h, p, 0, 5);
		LivingEntity side = husk(h, p, 1.2, 5.6);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk9(), 1, false), "Fire Blast fires");
		float d = IronManComicKit.byId("mk9_fire_blast").damage();
		h.assertTrue(lost(husk) >= d - 1f, "the bolt hits for ~" + d + ", got " + lost(husk));
		h.assertTrue(husk.isOnFire(), "and sets it burning");
		h.assertTrue(lost(side) >= IronManMark9.BLAST_SPLASH - 1f, "the splash catches the one beside it: " + lost(side));
		h.assertTrue(Math.abs(IronManMark9.heat(p) - IronManMark9.BLAST_HEAT) < 1.0e-3f, "+3 heat");
		h.assertFalse(TonyStark.abilityReady(p, MK9, "mk9_fire_blast"), "on cooldown");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theInfernoStreamBurnsWhileHeldAndCoolsDownOnRelease(GameTestHelper h) {
		ServerPlayer p = tony(h);
		LivingEntity husk = husk(h, p, 0, 3);
		p.setShiftKeyDown(true);
		IronManComicRouter.trigger(p, mk9(), IronManComicKit.SLOT_R, true);
		h.assertTrue(IronManMark9.streaming(p), "Shift+R held: the stream is lit");
		h.assertTrue(TonyStark.abilityReady(p, MK9, "mk9_inferno_stream"), "no cooldown while it is held");
		IronManMark9.tickForTests(p);
		h.assertTrue(lost(husk) > 0f && husk.isOnFire(), "the cone burns what is in front: " + lost(husk));
		h.assertTrue(IronManMark9.heat(p) > 0f, "the stream heats the sink");
		IronManComicRouter.trigger(p, mk9(), IronManComicKit.SLOT_R, false);
		p.setShiftKeyDown(false);
		h.assertFalse(IronManMark9.streaming(p), "released: out");
		h.assertFalse(TonyStark.abilityReady(p, MK9, "mk9_inferno_stream"), "the cooldown starts on the release");
		// a full sink chokes a held stream off by itself
		TonyStark.triggerCooldown(p, MK9, "mk9_inferno_stream", 0);
		IronManMark9.setHeat(p, 99.9f);
		p.setShiftKeyDown(true);
		IronManComicRouter.trigger(p, mk9(), IronManComicKit.SLOT_R, true);
		IronManMark9.tickForTests(p);
		h.assertFalse(IronManMark9.streaming(p), "at 100 the stream chokes off");
		p.setShiftKeyDown(false);
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMagmaBombLandsAndLeavesABurningPool(GameTestHelper h) {
		ServerPlayer p = tony(h);
		p.setXRot(60f); // lob it at the floor in front
		LivingEntity husk = husk(h, p, 0, 2);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk9(), 4, false), "Magma Bomb thrown");
		h.assertTrue(IronManMark9.activeBombs() == 1, "one bomb in flight");
		for (int i = 0; i < 40 && IronManMark9.activeBombs() > 0; i++) {
			IronManMark9.tickForTests(p);
		}
		h.assertTrue(IronManMark9.activeBombs() == 0, "it landed");
		h.assertTrue(IronManMark9.activePools() == 1, "a magma pool burns where it landed");
		h.assertTrue(lost(husk) > 0f && husk.isOnFire(), "the burst caught the husk: " + lost(husk));
		IronManMark9.clearSessionState();
		h.assertTrue(IronManMark9.activePools() == 0 && IronManMark9.heat(p) == 0f, "ServerStateReset clears it all");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theThermalSiphonDrawsHeatPutsFiresOutAndRepairs(GameTestHelper h) {
		ServerPlayer p = tony(h);
		// two fires and a lava pool nearby
		BlockPos base = p.blockPosition();
		h.getLevel().setBlockAndUpdate(base.offset(3, 0, 2), Blocks.FIRE.defaultBlockState());
		h.getLevel().setBlockAndUpdate(base.offset(-2, 0, 3), Blocks.FIRE.defaultBlockState());
		h.getLevel().setBlockAndUpdate(base.offset(0, -1, 4), Blocks.LAVA.defaultBlockState());
		IronManEnergy.setIntegrity(p, MK9, 1000f);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk9(), 5, true), "Thermal Siphon fires");
		float heat = IronManMark9.heat(p);
		h.assertTrue(heat >= 3 * IronManMark9.SIPHON_HEAT_PER_SOURCE - 1e-3f, "at least three sources drawn: " + heat);
		h.assertTrue(!h.getLevel().getBlockState(base.offset(3, 0, 2)).is(Blocks.FIRE)
				&& !h.getLevel().getBlockState(base.offset(-2, 0, 3)).is(Blocks.FIRE), "the fires went out");
		h.assertTrue(h.getLevel().getBlockState(base.offset(0, -1, 4)).is(Blocks.LAVA), "the lava stays");
		h.assertTrue(IronManEnergy.integrity(p, MK9) >= 1000f + 3 * IronManMark9.SIPHON_REPAIR_PER_SOURCE - 1e-3f,
				"the suit repaired: " + IronManEnergy.integrity(p, MK9));
		// nothing hot: refused, no cooldown
		TonyStark.triggerCooldown(p, MK9, "mk9_thermal_siphon", 0);
		h.getLevel().setBlockAndUpdate(base.offset(0, -1, 4), Blocks.STONE.defaultBlockState());
		h.assertFalse(IronManComicRouter.fireForTests(p, mk9(), 5, true), "nothing hot nearby: refused");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ UI text

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMk9TextFitsTheHud(GameTestHelper h) {
		int widest = 0;
		for (String id : new String[] { "mk6_unibeam_overcharge", "mk8_earthquake_stomp", "mk7_vacuum_missiles" }) {
			widest = Math.max(widest, IronManUiLayout.approxWidth(t(IronManComicKit.byId(id).nameKey())));
		}
		for (IronManComicKit.Key key : IronManComicKit.kit(MK9).keys()) {
			for (IronManComicKit.Move m : new IronManComicKit.Move[] { key.tap(), key.shift() }) {
				h.assertTrue(IronManUiLayout.approxWidth(t(m.nameKey())) <= widest, m.id() + " is no wider than the existing Era II names");
			}
		}
		// the HEAT SINK meter row: label + "100% FULL" inside the meter column
		int row = IronManUiLayout.approxWidth(t("hud.projecthero.ironman.heat_sink")) + 6
				+ IronManUiLayout.approxWidth("100% " + t("hud.projecthero.ironman.heat_sink.full"));
		h.assertTrue(row <= IronManUiLayout.HUD_W, "the heat sink meter fits its column: " + row + " / " + IronManUiLayout.HUD_W);
		// the action-bar messages stay one short line
		for (String k : new String[] { "message.projecthero.ironman.mk9.saturated", "message.projecthero.ironman.mk9.dump_low",
				"message.projecthero.ironman.mk9.siphon_none" }) {
			h.assertTrue(IronManUiLayout.approxWidth(t(k)) <= IronManUiLayout.MIN_W - 40, k + " fits the action bar at 320");
		}
		h.succeed();
	}
}
