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

	// ------------------------------------------------------------------ Rockets

	// ------------------------------------------------------------------ Micro-Missiles

	// ------------------------------------------------------------------ Miniguns

	private static final int MINIGUN_CHECK = 110;

	// ------------------------------------------------------------------ Unibeam (held)

	// ------------------------------------------------------------------ Energy Shield

	// ------------------------------------------------------------------ JARVIS scan

	@GameTest(template = EMPTY_STRUCTURE)
	public void jarvisScanCountsHostilesAndCostsTwenty(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		mob(helper, EntityType.ZOMBIE, p, 2, 2);
		mob(helper, EntityType.SKELETON, p, -2, 2);
		mob(helper, EntityType.SPIDER, p, 2, -2);
		mob(helper, EntityType.COW, p, -2, -2);
		// v0.16.1: X is the comic kit now -- the shared scan is driven directly; no energy cost any more
		helper.assertTrue(IronManJarvisScan.run(p, IronManSuits.MARK_III) != null, "the scan runs");
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

}
