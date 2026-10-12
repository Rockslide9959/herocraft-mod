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
import com.projecthero.mod.event.entity.RaidZombie;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * v0.16.1: the raid undead GeckoLib models (Risen / grave-child / Acid / Juggernaut / Sword Skeleton / Cursed / Pillager
 * Spy / Powered Zombie Boss) against their contract {@link RaidAnims}: every clip exists, play-once clips finish before
 * the server drops them, every strike keyframe sits on {@code (hit - TRANSITION)}, the code-driven {@code neck} is never
 * keyed, the model bones the renderer drives exist, the predictive strikes fit inside the AI's own attack intervals, the
 * synced clip plays and expires, every boss power has a look and every boss ability an exertion clip -- and vanilla
 * zombies are not raid-animated.
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
			if (model.equals(RaidAnims.ZOMBIE)) {
				for (String b : new String[] { "armour_head", "armour_chest", "armour_pauldron_r", "armour_fore_r", "armour_fore_l", "armour_shin_r", "armour_shin_l", "armour_mail", "item_r" }) {
					helper.assertTrue(names.contains(b), "the Risen needs bone " + b);
				}
			}
			if (model.equals(RaidAnims.BOSS)) {
				for (String a : PoweredBossAnims.ACCENTS) {
					helper.assertTrue(names.contains("acc_" + a), "the boss needs accent set acc_" + a);
				}
			}
			if (model.equals(RaidAnims.SPY)) {
				helper.assertTrue(names.contains("crossbow") && names.contains("spyglass_hand") && names.contains("spyglass_belt"), "the spy's crossbow / spyglass bones");
			}
			if (model.equals(RaidAnims.SKELETON)) {
				helper.assertTrue(names.contains("weapon_r") && names.contains("shield"), "the skeleton's sword / shield bones");
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
			if (c.name().equals("attack") || c.name().startsWith("slash_a") || c.name().startsWith("slash_b")) {
				helper.assertTrue(c.hit() > RaidAnims.TRANSITION && c.hit() < RaidAnimator.MELEE_INTERVAL, c.id() + " wind-up must fit the melee interval");
			}
			if (c.name().endsWith("_snap") || c.name().equals("crossbow_fire") || c.name().startsWith("ability_") || c.name().equals("shove")
					|| c.name().equals("cast_release") || c.name().equals("charge_impact")) {
				helper.assertTrue(c.hit() == RaidAnims.TRANSITION, c.id() + ": a reaction clip strikes as its blend ends");
			}
		}
		// the Acid Zombie's spit starts inside its 50-tick shot interval; the Juggernaut's tell is its 25-tick wind-up
		helper.assertTrue(RaidAnims.ACID_SPIT.hit() < 50, "spit wind-up fits the shot interval");
		helper.assertTrue(RaidAnims.JUGG_CHARGE_WINDUP.length() == 25, "the charge tell matches JuggernautZombie.WINDUP_TICKS");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void raidClipSyncsAndExpires(GameTestHelper helper) {
		RaidZombie z = RaidEntityTypes.RAID_ZOMBIE.create(helper.getLevel());
		helper.assertTrue(z.raidModel().equals(RaidAnims.ZOMBIE), "an adult Risen uses the Risen model");
		z.setBaby(true);
		helper.assertTrue(z.raidModel().equals(RaidAnims.BABY), "a baby Risen uses the grave-child model, never the vanilla one");
		z.setBaby(false);
		z.raidAnimator().set(RaidAnims.ZOMBIE_ATTACK);
		helper.assertTrue(z.raidAnimator().current() == RaidAnims.ZOMBIE_ATTACK, "attack synced");
		for (int i = 0; i < RaidAnims.ZOMBIE_ATTACK.length(); i++) {
			z.raidAnimator().tick();
		}
		helper.assertTrue(z.raidAnimator().current() == null, "a play-once clip is dropped after its length");
		z.raidAnimator().set(RaidAnims.ZOMBIE_READY);
		for (int i = 0; i < 40; i++) {
			z.raidAnimator().tick();
		}
		helper.assertTrue(z.raidAnimator().current() == RaidAnims.ZOMBIE_READY, "a held clip stays until replaced");
		z.discard();
		Entity vanilla = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertFalse(vanilla instanceof RaidAnimated, "the vanilla zombie is untouched (no raid model)");
		vanilla.discard();
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
