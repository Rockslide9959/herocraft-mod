package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.alien.ripjaws.RipjawsKit;
import com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;
import com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: the Omnitrix / alien sounds were "tring sounds and loud and annoying"): every sound the Omnitrix code
 * plays is an {@link OmnitrixSfx} Cue. Each Cue's every layer is a registered sound event at a sane volume / pitch; the
 * mod-owned layers (the six synthesised files) are defined in sounds.json with a subtitle and their .ogg is on the
 * classpath; no sounds.json entry points at a deleted Omnitrix file; the watch's UI Cues stay quiet; and the throttle and
 * delayed-layer queue behave. (Vanilla sound FILES can't be checked server-side -- there are no client assets here -- but
 * a registered vanilla event always ships its files.)
 */
public class OmnitrixSoundsGameTests implements FabricGameTest {
	/** The six synthesised files plus (v0.17.1) the user's own transform and power-down recordings. */
	private static final Set<String> SYNTH_FILES = Set.of("core_up", "transform", "revert", "timeout", "warning", "denied", "transform_user",
			"power_down_user", "core_ready_user", "recharged_user", "dial_click_user");

	private static JsonObject read(String path) {
		try (InputStream in = OmnitrixSoundsGameTests.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("cannot read " + path, e);
		}
	}

	private static boolean resourceExists(String path) {
		try (InputStream in = OmnitrixSoundsGameTests.class.getResourceAsStream(path)) {
			return in != null;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	/** Every Cue constant the Omnitrix code plays, named so a missing / null one fails here (class-init order). */
	private static List<OmnitrixSfx.Cue> named() {
		return List.of(OmnitrixSounds.CORE_UP, OmnitrixSounds.CORE_DOWN, OmnitrixSounds.DIAL, OmnitrixSounds.SELECT, OmnitrixSounds.SLAP,
				OmnitrixSounds.TRANSFORM, OmnitrixSounds.TRANSFORM_USER, OmnitrixSounds.POWER_DOWN_USER, OmnitrixSounds.REVERT, OmnitrixSounds.TIMEOUT, OmnitrixSounds.FLICKER, OmnitrixSounds.GRANT,
				OmnitrixSounds.WARNING, OmnitrixSounds.DENIED, OmnitrixSounds.RECHARGED, OmnitrixSounds.EXTRACT_PULSE,
				OmnitrixSounds.SELF_DESTRUCT, OmnitrixSounds.EXTRACT_DONE, OmnitrixSounds.POD_OPEN, OmnitrixSounds.POD_BEACON,
				OmnitrixSounds.SCANNER, OmnitrixSounds.METEOR,
				OmnitrixSounds.HEATBLAST_BLAST, OmnitrixSounds.HEATBLAST_STREAM, OmnitrixSounds.HEATBLAST_NOVA, OmnitrixSounds.HEATBLAST_JET,
				OmnitrixSounds.HEATBLAST_JET_LOOP, OmnitrixSounds.HEATBLAST_SHIELD, OmnitrixSounds.HEATBLAST_IGNITE,
				OmnitrixSounds.FOUR_ARMS_PUNCH, OmnitrixSounds.FOUR_ARMS_CLAP, OmnitrixSounds.FOUR_ARMS_SLAM, OmnitrixSounds.FOUR_ARMS_THROW,
				OmnitrixSounds.FOUR_ARMS_ROAR, OmnitrixSounds.FOUR_ARMS_STANCE,
				Xlr8Kit.SND_DASH, Xlr8Kit.SND_HIT, Xlr8Kit.SND_TORNADO, Xlr8Kit.SND_BURST, Xlr8Kit.SND_VISOR,
				DiamondheadKit.SND_SHARD, DiamondheadKit.SND_GROW, DiamondheadKit.SND_SHATTER, DiamondheadKit.SND_BLADE,
				UpgradeKit.SND_BEAM, UpgradeKit.SND_CHARGE, UpgradeKit.SND_MERGE, UpgradeKit.SND_EMP, UpgradeKit.SND_SLIDE,
				GhostfreakKit.SND_CLAW, GhostfreakKit.SND_SCREAM, GhostfreakKit.SND_PHASE, GhostfreakKit.SND_LAUGH, GhostfreakKit.SND_TENDRIL,
				StinkflyKit.BUZZ, StinkflyKit.SPIT, StinkflyKit.SPLAT, StinkflyKit.GAS, StinkflyKit.STING, StinkflyKit.DIVE,
				WildmuttKit.GROWL, WildmuttKit.BITE, WildmuttKit.CLAW, WildmuttKit.ROAR, WildmuttKit.BRISTLE, WildmuttKit.QUILLS, WildmuttKit.SLAM,
				WildmuttKit.LEAP, WildmuttKit.LAND, WildmuttKit.SNIFF, WildmuttKit.FRENZY_CUE, WildmuttKit.ROLL_CUE,
				RipjawsKit.BITE_SOUND, RipjawsKit.ROAR, RipjawsKit.SPLASH, RipjawsKit.SLAM, RipjawsKit.GASP, RipjawsKit.WHIRL,
				GreyMatterKit.PINCH, GreyMatterKit.SCAN, GreyMatterKit.GADGET, GreyMatterKit.LEAP, GreyMatterKit.REPAIR, GreyMatterKit.THINK,
				GreyMatterKit.DECOY_SOUND);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyCueIsBuiltFromRegisteredSounds(GameTestHelper h) {
		JsonObject sounds = read("/assets/projecthero/sounds.json");
		JsonObject lang = read("/assets/projecthero/lang/en_us.json");
		List<OmnitrixSfx.Cue> all = OmnitrixSfx.all();
		Set<String> names = new HashSet<>();
		for (OmnitrixSfx.Cue c : all) {
			h.assertTrue(names.add(c.name()), "duplicate Cue name " + c.name());
		}
		for (OmnitrixSfx.Cue c : named()) {
			h.assertTrue(c != null, "a named Omnitrix Cue is null (class-init order?)");
			h.assertTrue(all.contains(c), "the Cue list holds " + c);
		}
		for (OmnitrixSfx.Cue c : all) {
			h.assertTrue(!c.layers().isEmpty(), c + " has a layer");
			h.assertTrue(c.jitter() <= 0.15f, c + " jitter within 15 %");
			for (OmnitrixSfx.Layer l : c.layers()) {
				Holder<SoundEvent> s = l.sound();
				h.assertTrue(s != null && s.isBound(), c + ": a layer's sound is bound");
				ResourceLocation id = s.unwrapKey().map(ResourceKey::location).orElse(null);
				h.assertTrue(id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id), c + ": layer registered (" + id + ")");
				h.assertTrue(l.volume() > 0f && l.volume() <= 0.75f, c + ": layer volume within 0.75 (" + id + ")");
				h.assertTrue(l.pitch() >= 0.5f && l.pitch() <= 2.0f, c + ": layer pitch within the engine's 0.5-2.0 (" + id + ")");
				h.assertTrue(l.delay() <= 40, c + ": layer delay within 2 s (" + id + ")");
				if ("projecthero".equals(id.getNamespace())) {
					h.assertTrue(sounds.has(id.getPath()), "sounds.json defines " + id);
					JsonObject def = sounds.getAsJsonObject(id.getPath());
					h.assertTrue(def.has("subtitle") && lang.has(def.get("subtitle").getAsString()), "subtitle + lang on " + id);
					for (JsonElement e : def.getAsJsonArray("sounds")) {
						String name = e.isJsonObject() ? e.getAsJsonObject().get("name").getAsString() : e.getAsString();
						ResourceLocation file = ResourceLocation.parse(name);
						h.assertTrue(resourceExists("/assets/" + file.getNamespace() + "/sounds/" + file.getPath() + ".ogg"),
								"the file for " + id + " is present: " + name);
					}
				}
			}
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyTheSynthesisedOmnitrixFilesRemain(GameTestHelper h) {
		JsonObject sounds = read("/assets/projecthero/sounds.json");
		Set<String> referenced = new HashSet<>();
		for (Map.Entry<String, JsonElement> entry : sounds.entrySet()) {
			for (JsonElement e : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
				String name = e.isJsonObject() ? e.getAsJsonObject().get("name").getAsString() : e.getAsString();
				if (name.startsWith("projecthero:omnitrix/")) {
					String file = name.substring("projecthero:omnitrix/".length());
					referenced.add(file);
					h.assertTrue(resourceExists("/assets/projecthero/sounds/omnitrix/" + file + ".ogg"), "file present: " + name);
				}
			}
		}
		h.assertTrue(referenced.equals(SYNTH_FILES), "the Omnitrix files are exactly the six synthesised ones and the user's two: " + referenced);
		java.util.List<SoundEvent> owned = new java.util.ArrayList<>(List.of(OmnitrixSounds.SYNTH));
		owned.addAll(List.of(OmnitrixSounds.USER));
		for (SoundEvent e : owned) {
			h.assertTrue(BuiltInRegistries.SOUND_EVENT.get(e.getLocation()) == e, "registered: " + e.getLocation());
			h.assertTrue(sounds.has(e.getLocation().getPath()), "sounds.json defines " + e.getLocation());
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theWatchUiStaysQuiet(GameTestHelper h) {
		for (OmnitrixSfx.Cue c : List.of(OmnitrixSounds.CORE_UP, OmnitrixSounds.CORE_DOWN, OmnitrixSounds.DIAL, OmnitrixSounds.SELECT,
				OmnitrixSounds.WARNING, OmnitrixSounds.DENIED, OmnitrixSounds.RECHARGED)) {
			for (OmnitrixSfx.Layer l : c.layers()) {
				h.assertTrue(l.volume() <= 0.45f, c + ": a UI layer at most 0.45");
			}
		}
		for (OmnitrixSfx.Layer l : OmnitrixSounds.DIAL.layers()) {
			h.assertTrue(l.volume() <= 0.15f, "the dial click (it repeats) stays tiny");
		}
		h.assertTrue(OmnitrixSfx.shapeVolume(1.6f) < 1.25f, "a loud caller volume is compressed");
		h.assertTrue(Math.abs(OmnitrixSfx.shapePitch(1.8f) - 1.4f) < 1e-4f, "a caller pitch is pulled halfway to 1");
		h.succeed();
	}

	/** Its own batch: nothing else queues Omnitrix sounds while it counts. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "omnitrix_sfx_queue")
	public void aCueIsThrottledAndItsDelayedLayersQueue(GameTestHelper h) {
		Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 1.5));
		int before = OmnitrixSfx.pendingCount();
		// TIMEOUT: one immediate layer and one delayed (the core clunking home)
		OmnitrixSfx.play(h.getLevel(), at, OmnitrixSounds.TIMEOUT, 1f, 1f);
		h.assertTrue(OmnitrixSfx.pendingCount() == before + 1, "the delayed layer is queued");
		OmnitrixSfx.play(h.getLevel(), at, OmnitrixSounds.TIMEOUT, 1f, 1f);
		h.assertTrue(OmnitrixSfx.pendingCount() == before + 1, "the same Cue at the same spot in the same tick is throttled");
		h.runAfterDelay(30, () -> {
			h.assertTrue(OmnitrixSfx.pendingCount() <= before, "the delayed layer has played");
			h.succeed();
		});
	}
}
