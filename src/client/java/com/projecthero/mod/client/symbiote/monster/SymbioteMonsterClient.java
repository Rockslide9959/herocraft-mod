package com.projecthero.mod.client.symbiote.monster;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/** v0.17.5 Symbiote Monster form: the client half's start-up (the cocoon / melt renderer and the colour-bake housekeeping). */
public final class SymbioteMonsterClient {
	private SymbioteMonsterClient() {
	}

	public static void initialize() {
		SymbioteMonsterCocoon.initialize();
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(SymbioteMonsterTint::clear));
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ProjectHeroMod.id("symbiote_monster_tint");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				SymbioteMonsterTint.clear();
			}
		});
	}
}
