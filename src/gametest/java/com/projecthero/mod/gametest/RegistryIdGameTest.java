package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.3: no two things in any registry may share an id. Vanilla does not stop it (it only pauses in an IDE), the
 * integrated server hides it (the host's client shares the server's tables), but a player joining over the network gets
 * the registry sync by name, loses one of the pair, and is disconnected the moment the other is sent to them -- the
 * "projecthero:web_shot" crash (horde web shot + Spider-Man's Web Shot both used it).
 */
public class RegistryIdGameTest implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	@SuppressWarnings("unchecked")
	public void everyRegistryIdIsUnique(GameTestHelper helper) {
		List<String> dupes = new ArrayList<>();
		for (Registry<?> reg : BuiltInRegistries.REGISTRY) {
			Registry<Object> r = (Registry<Object>) reg;
			Map<ResourceLocation, Integer> seen = new HashMap<>();
			for (Object o : r) {
				ResourceLocation key = r.getKey(o);
				if (key != null) {
					seen.merge(key, 1, Integer::sum);
				}
				if (key == null || r.get(key) != o) {
					dupes.add(reg.key().location() + ": shadowed entry " + o + " (" + key + ")");
				}
			}
			seen.forEach((k, n) -> {
				if (n > 1) {
					dupes.add(reg.key().location() + ": " + k + " registered " + n + " times");
				}
			});
		}
		helper.assertTrue(dupes.isEmpty(), "duplicate registry ids: " + dupes);
		helper.succeed();
	}
}
