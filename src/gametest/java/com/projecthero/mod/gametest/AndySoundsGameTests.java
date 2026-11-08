package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.andy.AndySoundEvents;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.3 Andy sounds: every {@code projecthero:andy.*} event is registered, defined in sounds.json with a translated
 * subtitle and at least three takes, each take is a mod file that ships in the jar or a vanilla file at a sane volume /
 * pitch; and sounds.json defines no {@code andy.*} event the code doesn't register.
 */
public class AndySoundsGameTests implements FabricGameTest {

	private static JsonObject read(String path) {
		try (InputStream in = AndySoundsGameTests.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("cannot read " + path, e);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyAndySoundIsRegisteredDefinedAndSubtitled(GameTestHelper h) {
		JsonObject sounds = read("/assets/projecthero/sounds.json");
		JsonObject lang = read("/assets/projecthero/lang/en_us.json");
		h.assertTrue(AndySoundEvents.ALL.length >= 22, "the whole Andy sound set is listed");
		Set<String> registered = new HashSet<>();
		for (SoundEvent ev : AndySoundEvents.ALL) {
			h.assertTrue(ev != null, "an Andy sound is null (class-init order?)");
			ResourceLocation id = ev.getLocation();
			h.assertTrue(registered.add(id.getPath()), "duplicate Andy sound " + id);
			h.assertTrue("projecthero".equals(id.getNamespace()) && id.getPath().startsWith("andy."), "namespaced Andy id " + id);
			h.assertTrue(BuiltInRegistries.SOUND_EVENT.get(id) == ev, "registered: " + id);
			h.assertTrue(sounds.has(id.getPath()), "sounds.json defines " + id);
			JsonObject def = sounds.getAsJsonObject(id.getPath());
			h.assertTrue(def.has("subtitle"), "subtitle on " + id);
			String sub = def.get("subtitle").getAsString();
			h.assertTrue(lang.has(sub) && !lang.get(sub).getAsString().isBlank(), "lang has the subtitle " + sub);
			h.assertTrue(def.getAsJsonArray("sounds").size() >= 3, "at least three takes for " + id);
			for (JsonElement e : def.getAsJsonArray("sounds")) {
				JsonObject s = e.getAsJsonObject();
				String name = s.get("name").getAsString();
				ResourceLocation file = ResourceLocation.tryParse(name);
				h.assertTrue(file != null, "valid sound file name " + name);
				float vol = s.has("volume") ? s.get("volume").getAsFloat() : 1f;
				float pitch = s.has("pitch") ? s.get("pitch").getAsFloat() : 1f;
				h.assertTrue(vol > 0f && vol <= 1f, "volume within 1 on " + name + " in " + id);
				h.assertTrue(pitch >= 0.5f && pitch <= 2.0f, "pitch within the engine's clamp on " + name + " in " + id);
				h.assertTrue(s.has("attenuation_distance") && s.get("attenuation_distance").getAsInt() >= 8
						&& s.get("attenuation_distance").getAsInt() <= 32, "sensible attenuation on " + name + " in " + id);
				if ("projecthero".equals(file.getNamespace())) {
					String res = "/assets/projecthero/sounds/" + file.getPath() + ".ogg";
					try (InputStream in = AndySoundsGameTests.class.getResourceAsStream(res)) {
						h.assertTrue(in != null && in.read() == 'O', "the take ships as an OGG: " + res);
					} catch (java.io.IOException ex) {
						throw new IllegalStateException(ex);
					}
				} else {
					h.assertTrue("minecraft".equals(file.getNamespace()), "a vanilla or mod take: " + name);
				}
			}
		}
		for (String key : sounds.keySet()) {
			if (key.startsWith("andy.")) {
				h.assertTrue(registered.contains(key), "sounds.json event " + key + " is registered in AndySoundEvents");
			}
		}
		h.succeed();
	}
}
