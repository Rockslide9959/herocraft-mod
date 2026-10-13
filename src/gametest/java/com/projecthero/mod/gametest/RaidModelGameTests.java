package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.PoweredBossAnims;
import com.projecthero.mod.event.entity.RaidAnimated;
import com.projecthero.mod.event.entity.RaidAnimator;
import com.projecthero.mod.event.entity.RaidAnims;
import com.projecthero.mod.event.entity.RaidEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * v0.16.1: the Powered Zombie Boss's GeckoLib model against its contract {@link RaidAnims} (every clip exists, play-once
 * clips finish before the server drops them, every strike keyframe sits on {@code (hit - TRANSITION)}, the code-driven
 * {@code neck} is never keyed, the accent bones exist, the predictive strikes fit the melee interval, the synced clip plays
 * and expires, every boss power has a look and every boss ability an exertion clip); the raid's other mobs are on the
 * player rig with their skins on the classpath; and vanilla zombies are untouched.
 */
public class RaidModelGameTests implements FabricGameTest {
	private static JsonObject read(String path) {
		try (InputStream in = RaidAnims.class.getResourceAsStream("/assets/projecthero/" + path)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyRaidClipExistsAndStrikesLandOnTheServerTick(GameTestHelper helper) {
		for (String model : RaidAnims.MODELS) {
			JsonObject file = read("animations/raid/" + model + ".animation.json");
			helper.assertTrue(file != null, model + ": animation json must be on the classpath");
			JsonObject clips = file.getAsJsonObject("animations");
			for (RaidAnims.Clip c : RaidAnims.of(model)) {
				helper.assertTrue(clips.has(c.id()), "missing clip " + c.id());
				JsonObject clip = clips.getAsJsonObject(c.id());
				double len = clip.get("animation_length").getAsDouble();
				if (c.loops()) {
					helper.assertTrue(Math.abs(len * 20.0 - c.length()) < 0.5, c.id() + ": a loop is exactly its cycle long");
				} else {
					helper.assertTrue(len * 20.0 + RaidAnims.TRANSITION <= c.length() + 0.5, c.id() + ": the clip must finish before the server drops it");
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
						if (channels.get(ch).isJsonObject()) {
							for (String k : channels.getAsJsonObject(ch).keySet()) {
								found |= Math.abs(Double.parseDouble(k) - strike) < 1.0e-3;
							}
						}
					}
				}
				helper.assertTrue(found, c.id() + ": no keyframe on the strike time " + strike + " s (server tick " + c.hit() + ")");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void raidModelsHaveTheirBonesAndTexturesAndNeverKeyTheNeck(GameTestHelper helper) {
		String[] need = { "root", "hips", "waist", "chest", "neck", "head", "jaw", "arm_l", "arm_r", "forearm_l", "forearm_r", "thigh_l", "thigh_r" };
		for (String model : RaidAnims.MODELS) {
			JsonObject geo = read("geo/raid/" + model + ".geo.json");
			helper.assertTrue(geo != null, model + ".geo.json must be on the classpath");
			Set<String> names = new HashSet<>();
			geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")
					.forEach(b -> names.add(b.getAsJsonObject().get("name").getAsString()));
			for (String b : need) {
				helper.assertTrue(names.contains(b), model + ": missing bone " + b);
			}
			if (model.equals(RaidAnims.BOSS)) {
				for (String a : PoweredBossAnims.ACCENTS) {
					helper.assertTrue(names.contains("acc_" + a), "the boss needs accent set acc_" + a);
				}
			}
			JsonObject clips = read("animations/raid/" + model + ".animation.json").getAsJsonObject("animations");
			for (String clip : clips.keySet()) {
				JsonObject keyed = clips.getAsJsonObject(clip).getAsJsonObject("bones");
				helper.assertFalse(keyed != null && keyed.has("neck"), clip + " must not key the code-driven neck");
			}
			for (String tex : new String[] { model + ".png", model + "_glowmask.png" }) {
				try (InputStream in = RaidAnims.class.getResourceAsStream("/assets/projecthero/textures/entity/raid/" + tex)) {
					helper.assertTrue(in != null, "missing texture " + tex);
				} catch (java.io.IOException e) {
					helper.fail("unreadable texture " + tex);
				}
			}
		}
		helper.assertTrue(read("geo/raid/acid_glob.geo.json") != null, "the acid glob model");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void predictiveStrikesFitTheAiIntervals(GameTestHelper helper) {
		// the melee wind-up starts `hit` ticks before the goal's next hit, inside its 20-tick interval
		for (RaidAnims.Clip c : RaidAnims.ALL) {
			if (c.name().equals("attack")) {
				helper.assertTrue(c.hit() > RaidAnims.TRANSITION && c.hit() < RaidAnimator.MELEE_INTERVAL, c.id() + " wind-up must fit the melee interval");
			}
			if (c.name().endsWith("_snap") || c.name().startsWith("ability_") || c.name().equals("shove") || c.name().equals("cast_release")) {
				helper.assertTrue(c.hit() == RaidAnims.TRANSITION, c.id() + ": a reaction clip strikes as its blend ends");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossClipSyncsAndExpires(GameTestHelper helper) {
		EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		helper.assertTrue(boss.raidModel().equals(RaidAnims.BOSS), "the Powered Zombie Boss uses its own model");
		boss.raidAnimator().set(RaidAnims.BOSS_ATTACK);
		helper.assertTrue(boss.raidAnimator().current() == RaidAnims.BOSS_ATTACK, "attack synced");
		for (int i = 0; i < RaidAnims.BOSS_ATTACK.length(); i++) {
			boss.raidAnimator().tick();
		}
		helper.assertTrue(boss.raidAnimator().current() == null, "a play-once clip is dropped after its length");
		boss.raidAnimator().set(RaidAnims.BOSS_READY);
		for (int i = 0; i < 40; i++) {
			boss.raidAnimator().tick();
		}
		helper.assertTrue(boss.raidAnimator().current() == RaidAnims.BOSS_READY, "a held clip stays until replaced");
		boss.discard();
		helper.succeed();
	}

	/**
	 * The raid's other mobs are on the player rig (skins, no GeckoLib model), every skin and glow map is on the classpath,
	 * and the vanilla zombie is untouched.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void raidMobsWearPlayerSkinsAndVanillaZombiesAreUntouched(GameTestHelper helper) {
		for (EntityType<?> type : new EntityType<?>[] { RaidEntityTypes.RAID_ZOMBIE, RaidEntityTypes.ACID_ZOMBIE, RaidEntityTypes.JUGGERNAUT_ZOMBIE,
				RaidEntityTypes.CURSED_ZOMBIE, RaidEntityTypes.SWORD_SKELETON, RaidEntityTypes.PILLAGER_SPY, EntityType.ZOMBIE }) {
			Entity e = type.create(helper.getLevel());
			helper.assertFalse(e instanceof RaidAnimated, EntityType.getKey(type) + " is drawn on the player rig / vanilla, not a GeckoLib raid model");
			e.discard();
		}
		for (String skin : new String[] { "raid_zombie_armoured", "acid_zombie", "juggernaut_zombie", "cursed_zombie", "sword_skeleton", "pillager_spy" }) {
			for (String tex : new String[] { skin + ".png", skin + "_glow.png" }) {
				try (InputStream in = RaidAnims.class.getResourceAsStream("/assets/projecthero/textures/entity/raid_skin/" + tex)) {
					helper.assertTrue(in != null, "missing skin " + tex);
				} catch (java.io.IOException e) {
					helper.fail("unreadable skin " + tex);
				}
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyBossPowerHasALookAndEveryAbilityAClip(GameTestHelper helper) {
		Set<String> accents = Set.of(PoweredBossAnims.ACCENTS);
		for (String key : BossPowers.eligibleKeys()) {
			PoweredBossAnims.Look look = PoweredBossAnims.look(key);
			helper.assertTrue(accents.contains(look.accent()), key + ": unknown accent set " + look.accent());
			EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
			boss.configure(key, null, false, 1);
			BossPowerController ctl = boss.primaryController();
			for (String id : ctl.abilityIds()) {
				RaidAnims.Clip c = PoweredBossAnims.forAbility(id);
				helper.assertTrue(c == null || c.model().equals(RaidAnims.BOSS), id + " maps to a boss clip");
			}
			boss.discard();
		}
		helper.assertTrue(PoweredBossAnims.forAbility("ground_slam") == RaidAnims.BOSS_SLAM, "slams slam");
		helper.assertTrue(PoweredBossAnims.forAbility("haymaker") == RaidAnims.BOSS_PUNCH, "punches punch");
		helper.assertTrue(PoweredBossAnims.forAbility("blink") == RaidAnims.BOSS_DASH, "blinks dash");
		helper.assertTrue(PoweredBossAnims.forAbility("regen") == null, "passive regen does not animate");
		helper.assertTrue(PoweredBossAnims.look("power_03_flight").accent().equals("wings"), "Flight gets wings (not the light orbit)");
		helper.assertTrue(PoweredBossAnims.look("power_15_invisibility_light_manipulation").accent().equals("orbit"), "Light gets the orbit");
		helper.succeed();
	}
}
