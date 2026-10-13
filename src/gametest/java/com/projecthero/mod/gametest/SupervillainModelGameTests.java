package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.entity.SupervillainAnims;
import com.projecthero.mod.event.entity.SupervillainVariant;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * v0.16.1: the Supervillain bosses' GeckoLib models (Chimera / Arsenal / Omega Mage) against their contract
 * ({@link SupervillainAnims}): every clip exists in every model, play-once clips finish before the server drops them,
 * every strike keyframe sits on {@code (hit - TRANSITION)}, the code-driven bones are present and never keyed, the cast
 * clips release on {@code BossPowerController#beginCast}'s wind-up, and the synced clip obeys its priorities -- while an
 * ordinary Zombie Raid boss (no variant) never syncs anything.
 */
public class SupervillainModelGameTests implements FabricGameTest {
	private static JsonObject read(String path) {
		try (InputStream in = SupervillainAnims.class.getResourceAsStream("/assets/projecthero/" + path)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everySupervillainClipExistsAndStrikesLandOnTheServerTick(GameTestHelper helper) {
		for (SupervillainVariant v : SupervillainVariant.values()) {
			JsonObject file = read("animations/supervillain/" + SupervillainAnims.model(v) + ".animation.json");
			helper.assertTrue(file != null, v + ": animation json must be on the classpath");
			JsonObject clips = file.getAsJsonObject("animations");
			for (SupervillainAnims.Clip c : SupervillainAnims.ALL) {
				helper.assertTrue(clips.has(c.id(v)), "missing clip " + c.id(v));
				JsonObject clip = clips.getAsJsonObject(c.id(v));
				double len = clip.get("animation_length").getAsDouble();
				if (!c.loops()) {
					helper.assertTrue(len * 20.0 + SupervillainAnims.TRANSITION <= c.length() + 0.5,
							c.id(v) + ": the clip must finish before the server drops it (" + len + " s vs " + c.length() + " ticks)");
				} else {
					helper.assertTrue(Math.abs(len * 20.0 - c.length()) < 0.5, c.id(v) + ": a loop is exactly its cycle long");
				}
				double strike = c.strikeSeconds();
				if (strike < 0) {
					continue;
				}
				boolean found = false;
				JsonObject bones = clip.getAsJsonObject("bones");
				for (String bone : bones.keySet()) {
					JsonObject channels = bones.getAsJsonObject(bone);
					for (String ch : channels.keySet()) {
						if (!channels.get(ch).isJsonObject()) {
							continue;
						}
						for (String k : channels.getAsJsonObject(ch).keySet()) {
							found |= Math.abs(Double.parseDouble(k) - strike) < 1.0e-3;
						}
					}
				}
				helper.assertTrue(found, c.id(v) + ": no keyframe on the strike time " + strike + " s (server tick " + c.hit() + ")");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void supervillainModelsHaveTheCodeDrivenBonesAndNeverKeyThem(GameTestHelper helper) {
		String[] need = { "root", "hips", "waist", "flinch", "chest", "neck", "head", "arm_l", "arm_r", "forearm_l", "forearm_r", "hand_l", "hand_r" };
		for (SupervillainVariant v : SupervillainVariant.values()) {
			String model = SupervillainAnims.model(v);
			JsonObject geo = read("geo/supervillain/" + model + ".geo.json");
			helper.assertTrue(geo != null, model + ".geo.json must be on the classpath");
			Set<String> names = new HashSet<>();
			geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")
					.forEach(b -> names.add(b.getAsJsonObject().get("name").getAsString()));
			for (String b : need) {
				if (b.equals("hand_r") && v == SupervillainVariant.ARSENAL) {
					continue; // the Arsenal's right forearm is a rotary gun, not a hand
				}
				helper.assertTrue(names.contains(b), model + ": missing bone " + b);
			}
			JsonObject clips = read("animations/supervillain/" + model + ".animation.json").getAsJsonObject("animations");
			for (String clip : clips.keySet()) {
				JsonObject keyed = clips.getAsJsonObject(clip).getAsJsonObject("bones");
				for (String never : SupervillainAnims.CODE_DRIVEN_BONES) {
					helper.assertFalse(keyed != null && keyed.has(never), clip + " must not key the code-driven bone " + never);
				}
			}
			for (String tex : new String[] { model + ".png", model + "_glowmask.png" }) {
				try (InputStream in = SupervillainAnims.class.getResourceAsStream("/assets/projecthero/textures/entity/supervillain/" + tex)) {
					helper.assertTrue(in != null, "missing texture " + tex);
				} catch (java.io.IOException e) {
					helper.fail("unreadable texture " + tex);
				}
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void supervillainCastClipsReleaseOnTheCastWindup(GameTestHelper helper) {
		for (int cycles = 1; cycles <= 4; cycles++) {
			SupervillainAnims.Clip c = SupervillainAnims.cast(cycles);
			// BossPowerController#beginCast fires `cycles` ability cycles (10 ticks each) after it starts
			helper.assertTrue(c.hit() == cycles * 10, c.name() + " releases on tick " + cycles * 10 + ", got " + c.hit());
			helper.assertTrue(c.length() > c.hit() + 8, c.name() + " has a follow-through after the release");
		}
		helper.assertTrue(SupervillainAnims.byName("melee") == SupervillainAnims.MELEE, "byName finds clips");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void supervillainAnimationSyncsWithPrioritiesAndNeverOnTheZombieRaidBoss(GameTestHelper helper) {
		EmpoweredZombie ordinary = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		helper.assertFalse(ordinary.playSupervillainAnim(SupervillainAnims.MELEE), "an ordinary Powered Zombie Boss never plays a Supervillain clip");
		helper.assertTrue(ordinary.currentSupervillainAnim() == null, "nothing synced on the ordinary boss");

		EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		boss.configureAsSupervillain(SupervillainVariant.CHIMERA, "power_05_geokinesis", 1);
		helper.assertTrue(boss.playSupervillainAnim(SupervillainAnims.MELEE), "melee plays");
		helper.assertTrue(boss.currentSupervillainAnim() == SupervillainAnims.MELEE, "melee synced");
		helper.assertFalse(boss.playSupervillainAnim(SupervillainAnims.BURST), "a lower-priority move does not cut the swing");
		helper.assertTrue(boss.playSupervillainAnim(SupervillainAnims.CAST_4), "an ultimate's wind-up takes over");
		helper.assertTrue(boss.playSupervillainAnim(SupervillainAnims.DEATH), "death always plays");
		helper.assertTrue(boss.currentSupervillainAnim() == SupervillainAnims.DEATH, "death synced");
		ordinary.discard();
		boss.discard();
		helper.succeed();
	}
}
