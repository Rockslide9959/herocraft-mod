package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.titan.entity.TitanEntity;
import com.projecthero.mod.titan.entity.TitanEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.20: the Titan's GeckoLib model. The assets are generated (scratchpad/gen_v01420_titan_model.js), so these
 * pin the contract between them and {@link TitanEntity}: every synced {@link TitanEntity.Anim} has its clip, every
 * telegraphed attack's strike keyframe sits where the server deals the damage, the bones the code drives exist,
 * and the death collapse keeps the body around until its animation has played.
 */
public class TitanModelV01420GameTests implements FabricGameTest {
	private static JsonObject read(String path) {
		try (InputStream in = TitanEntity.class.getResourceAsStream("/assets/projecthero/" + path)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyAnimHasItsClip(GameTestHelper helper) {
		JsonObject file = read("animations/titan.animation.json");
		helper.assertTrue(file != null, "titan.animation.json must be on the classpath");
		JsonObject clips = file.getAsJsonObject("animations");
		for (TitanEntity.Anim anim : TitanEntity.Anim.values()) {
			if (anim == TitanEntity.Anim.NONE) {
				continue;
			}
			String name = "animation.titan." + anim.name().toLowerCase(java.util.Locale.ROOT);
			helper.assertTrue(clips.has(name), "missing clip " + name);
			if (anim != TitanEntity.Anim.SWAT) {
				helper.assertTrue(TitanEntity.clipFor(anim) != null, "no main-controller clip mapped for " + anim);
			}
		}
		for (String extra : new String[] {"idle", "walk", "run", "charge_run"}) {
			helper.assertTrue(clips.has("animation.titan." + extra), "missing clip " + extra);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void strikeKeyframesLineUpWithTheDamageTick(GameTestHelper helper) {
		JsonObject clips = read("animations/titan.animation.json").getAsJsonObject("animations");
		// the server resolves every telegraphed attack 40 ticks after its animation starts; the client clip starts
		// ANIM_TRANSITION ticks after that (the controller's blend-in)
		double strike = (40 - TitanEntity.ANIM_TRANSITION) / 20.0;
		for (String n : new String[] {"punch", "sweep", "stomp", "slam", "shockwave", "grab", "boulder"}) {
			JsonObject clip = clips.getAsJsonObject("animation.titan." + n);
			JsonObject arm = clip.getAsJsonObject("bones").getAsJsonObject("chest");
			helper.assertTrue(arm != null && arm.has("rotation"), n + ": the chest must be keyed");
			boolean found = arm.getAsJsonObject("rotation").keySet().stream()
					.anyMatch(k -> Math.abs(Double.parseDouble(k) - strike) < 1.0e-3);
			helper.assertTrue(found, n + ": no keyframe on the strike time " + strike + " s");
			helper.assertTrue(clip.get("animation_length").getAsDouble() * 20.0 + TitanEntity.ANIM_TRANSITION <= 66.0,
					n + ": the clip must finish before the server's 64-tick animation ends");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void modelHasTheBonesTheCodeDrives(GameTestHelper helper) {
		JsonObject geo = read("geo/titan.geo.json");
		helper.assertTrue(geo != null, "titan.geo.json must be on the classpath");
		var bones = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
		java.util.Set<String> names = new java.util.HashSet<>();
		bones.forEach(b -> names.add(b.getAsJsonObject().get("name").getAsString()));
		for (String need : new String[] {"root", "neck", "head", "jaw", "flinch", "hand_r", "hand_l", "chest"}) {
			helper.assertTrue(names.contains(need), "missing bone " + need);
		}
		// neck and flinch are posed in code every frame; a clip keying them would fight that (and accumulate)
		JsonObject clips = read("animations/titan.animation.json").getAsJsonObject("animations");
		for (String clip : clips.keySet()) {
			JsonObject keyed = clips.getAsJsonObject(clip).getAsJsonObject("bones");
			helper.assertFalse(keyed.has("neck") || keyed.has("flinch"), clip + " must not key the code-driven bones");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = TitanEntity.DEATH_TICKS + 40)
	public void deathCollapseKeepsTheBodyUntilItHasFallen(GameTestHelper helper) {
		TitanEntity titan = TitanEntityTypes.TITAN.create(helper.getLevel());
		titan.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
		helper.getLevel().addFreshEntity(titan);
		titan.setHealth(0.0f);
		titan.die(titan.level().damageSources().generic());
		helper.assertTrue(titan.currentAnim() == TitanEntity.Anim.DEATH, "dying plays the death clip, got " + titan.currentAnim());
		helper.assertTrue(titan.deathTicksLeft() == TitanEntity.DEATH_TICKS, "the collapse timer starts");
		// vanilla would remove a dead mob after 20 ticks -- the collapse must still be on screen well after that
		helper.runAfterDelay(40, () -> helper.assertFalse(titan.isRemoved(), "the body must still be there 2 s in"));
		helper.runAfterDelay(TitanEntity.DEATH_TICKS + 10, () -> {
			helper.assertTrue(titan.isRemoved(), "the body is gone once the collapse has played");
			helper.succeed();
		});
	}
}
