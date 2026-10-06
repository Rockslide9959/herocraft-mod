package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ironman.IronManSounds;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.14.31: the Iron Man move sounds. Every new {@link IronManSounds#MOVE_SOUNDS} event is registered and defined in
 * sounds.json with a translated subtitle, and every projecthero sounds.json entry parses (valid ids, a non-empty sound
 * list, sane volume / pitch). The vanilla FILE paths themselves can't be checked here (the server has no client
 * assets) -- scratchpad/gen_v01431_sounds.js checks those against the 1.21.1 asset index when it writes them.
 */
public class IronManSoundsV01431GameTests implements FabricGameTest {

	private static JsonObject read(String path) {
		try (InputStream in = IronManSoundsV01431GameTests.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("cannot read " + path, e);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyMoveSoundIsRegisteredAndDefined(GameTestHelper h) {
		JsonObject sounds = read("/assets/projecthero/sounds.json");
		JsonObject lang = read("/assets/projecthero/lang/en_us.json");
		h.assertTrue(IronManSounds.MOVE_SOUNDS.length >= 50, "the move sound set is all there");
		java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
		for (SoundEvent ev : IronManSounds.MOVE_SOUNDS) {
			h.assertTrue(ev != null, "a move sound is null (class-init order?)");
			ResourceLocation id = ev.getLocation();
			h.assertTrue(seen.add(id), "duplicate move sound " + id);
			h.assertTrue("projecthero".equals(id.getNamespace()) && id.getPath().startsWith("ironman_"), "namespaced Iron Man id " + id);
			h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(id), "registered: " + id);
			h.assertTrue(BuiltInRegistries.SOUND_EVENT.get(id) == ev, "the registry holds this exact event: " + id);
			h.assertTrue(sounds.has(id.getPath()), "sounds.json defines " + id);
			JsonObject def = sounds.getAsJsonObject(id.getPath());
			h.assertTrue(def.has("subtitle"), "subtitle on " + id);
			String sub = def.get("subtitle").getAsString();
			h.assertTrue(lang.has(sub), "lang has the subtitle " + sub);
			for (JsonElement e : def.getAsJsonArray("sounds")) {
				JsonObject s = e.getAsJsonObject();
				float vol = s.has("volume") ? s.get("volume").getAsFloat() : 1f;
				float pitch = s.has("pitch") ? s.get("pitch").getAsFloat() : 1f;
				h.assertTrue(vol > 0f && vol <= 1.2f, "volume within 1.2 on " + id);
				h.assertTrue(pitch >= 0.5f && pitch <= 2.0f, "pitch within the engine's 0.5-2.0 clamp on " + id);
				h.assertTrue(s.get("name").getAsString().startsWith("minecraft:"), "built only from vanilla sounds: " + id);
			}
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everySoundsJsonEntryParses(GameTestHelper h) {
		JsonObject sounds = read("/assets/projecthero/sounds.json");
		for (Map.Entry<String, JsonElement> entry : sounds.entrySet()) {
			String event = entry.getKey();
			h.assertTrue(ResourceLocation.tryBuild("projecthero", event) != null, "valid event id " + event);
			h.assertTrue(entry.getValue().isJsonObject(), "event " + event + " is an object");
			JsonObject def = entry.getValue().getAsJsonObject();
			h.assertTrue(def.has("sounds") && def.get("sounds").isJsonArray(), "event " + event + " has a sounds list");
			JsonArray list = def.getAsJsonArray("sounds");
			h.assertTrue(list.size() > 0, "event " + event + " has at least one sound");
			for (JsonElement e : list) {
				String name = e.isJsonPrimitive() ? e.getAsString() : e.getAsJsonObject().get("name").getAsString();
				h.assertTrue(ResourceLocation.tryParse(name) != null, "valid sound name " + name + " in " + event);
				if (e.isJsonObject()) {
					JsonObject s = e.getAsJsonObject();
					if (s.has("volume")) {
						h.assertTrue(s.get("volume").getAsFloat() > 0f, "positive volume in " + event);
					}
					if (s.has("pitch")) {
						h.assertTrue(s.get("pitch").getAsFloat() > 0f, "positive pitch in " + event);
					}
					// an "event"-type entry must point at a real projecthero / vanilla event
					if (s.has("type") && "event".equals(s.get("type").getAsString())) {
						ResourceLocation ref = ResourceLocation.parse(name);
						h.assertTrue(!"projecthero".equals(ref.getNamespace()) || sounds.has(ref.getPath()),
								"event reference " + name + " in " + event + " resolves");
					}
				}
			}
		}
		h.succeed();
	}
}
