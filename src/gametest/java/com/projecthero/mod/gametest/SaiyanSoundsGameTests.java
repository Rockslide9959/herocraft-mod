package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.saiyan.SaiyanSounds;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.4: the Saiyan's two sound sets (see {@link SaiyanSounds}). The public set must stand on its own -- CI and CurseForge
 * builds have no Dragon Ball clips -- so every Saiyan event has a public sounds.json entry with a subtitle in en_us.json, and
 * none of them points into the git-ignored {@code saiyan/dbz/} folder; every mod file it names is present. The test-build pack
 * only replaces events that exist publicly, and only with {@code saiyan/dbz/} files.
 */
public class SaiyanSoundsGameTests implements FabricGameTest {
	private static JsonObject read(String path) {
		try (InputStream in = SaiyanSoundsGameTests.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("cannot read " + path, e);
		}
	}

	private static boolean resourceExists(String path) {
		try (InputStream in = SaiyanSoundsGameTests.class.getResourceAsStream(path)) {
			return in != null;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	private static String name(JsonElement e) {
		return e.isJsonObject() ? e.getAsJsonObject().get("name").getAsString() : e.getAsString();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void publicSaiyanSoundsStandAlone(GameTestHelper h) {
		SaiyanSounds.initialize();
		JsonObject sounds = read("/assets/projecthero/sounds.json");
		JsonObject lang = read("/assets/projecthero/lang/en_us.json");
		int n = 0;
		for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
			if (!id.getNamespace().equals("projecthero") || !id.getPath().startsWith("saiyan_")) {
				continue;
			}
			n++;
			h.assertTrue(sounds.has(id.getPath()), "sounds.json defines " + id);
			JsonObject def = sounds.getAsJsonObject(id.getPath());
			h.assertTrue(def.has("subtitle") && lang.has(def.get("subtitle").getAsString()), "subtitle + lang on " + id);
			for (JsonElement e : def.getAsJsonArray("sounds")) {
				ResourceLocation file = ResourceLocation.parse(name(e));
				h.assertFalse(file.getPath().startsWith("saiyan/dbz/"), "public " + id + " never names a show clip: " + file);
				if (file.getNamespace().equals("projecthero")) {
					h.assertTrue(resourceExists("/assets/projecthero/sounds/" + file.getPath() + ".ogg"), "the file for " + id + " is present: " + file);
				}
			}
		}
		h.assertTrue(n >= 30, "all the Saiyan events are registered (" + n + ")");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void testBuildPackOnlyReplacesWithClips(GameTestHelper h) {
		JsonObject pub = read("/assets/projecthero/sounds.json");
		JsonObject pack = read("/resourcepacks/saiyan_dbz/assets/projecthero/sounds.json");
		h.assertTrue(resourceExists("/resourcepacks/saiyan_dbz/pack.mcmeta"), "the pack has its pack.mcmeta");
		for (Map.Entry<String, JsonElement> entry : pack.entrySet()) {
			h.assertTrue(pub.has(entry.getKey()), "the pack only replaces public events: " + entry.getKey());
			JsonObject def = entry.getValue().getAsJsonObject();
			h.assertTrue(def.has("replace") && def.get("replace").getAsBoolean(), "the pack replaces (not adds to) " + entry.getKey());
			for (JsonElement e : def.getAsJsonArray("sounds")) {
				h.assertTrue(name(e).startsWith("projecthero:saiyan/dbz/"), "the pack names only clips: " + name(e));
			}
		}
		h.succeed();
	}
}
