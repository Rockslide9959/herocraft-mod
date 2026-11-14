package com.projecthero.mod.client.saiyan;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * v0.17.4: switches the Saiyan's sounds to the user's Dragon Ball clips in local test builds. The clips (and their cuts) sit in
 * the git-ignored {@code assets/projecthero/sounds/saiyan/dbz/}, so only a build made from the user's own checkout has them; the
 * built-in pack {@code resourcepacks/saiyan_dbz/} (always on) replaces every Saiyan sound event with them. A build without the
 * clips (CI, CurseForge) never registers the pack and keeps the public sounds. See {@code SaiyanSounds} and
 * {@code docs/SAIYAN_DBZ_SOUND_MAP.md}.
 */
public final class SaiyanDbzSounds {
	/** One of the cuts: if it is in the jar, they all are (scratchpad/saiyan/dbz_cut.js makes them together). */
	private static final String PROBE = "assets/projecthero/sounds/saiyan/dbz/cut_aura_loop.ogg";

	private static boolean active;

	private SaiyanDbzSounds() {
	}

	/** Whether the test-build clips are in use (for the log and the harness). */
	public static boolean active() {
		return active;
	}

	public static void initialize() {
		FabricLoader.getInstance().getModContainer(ProjectHeroMod.MOD_ID).ifPresent(mod -> {
			if (mod.findPath(PROBE).isEmpty()) {
				return;
			}
			active = ResourceManagerHelper.registerBuiltinResourcePack(ProjectHeroMod.id("saiyan_dbz"), mod,
					Component.literal("Project Hero: Saiyan test sounds"), ResourcePackActivationType.ALWAYS_ENABLED);
			if (active) {
				ProjectHeroMod.LOGGER.info("[Saiyan] test build: Dragon Ball clips on (resourcepacks/saiyan_dbz)");
			}
		});
	}
}
