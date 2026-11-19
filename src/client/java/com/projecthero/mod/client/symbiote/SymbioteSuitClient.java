package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteSuitColours;
import com.projecthero.mod.symbiote.item.SymbioteHostItems;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * v0.17.5: client wiring for the coloured Symbiote suit -- the suit pieces' icons tinted to your colours (layer 0 body, layer 1
 * the untinted gloss, layer 2 the emblem), and the texture caches ({@link SymbioteSuitTint}, {@link SymbioteSuitFx}) dropped on a
 * resource reload and on leaving a world.
 */
public final class SymbioteSuitClient {
	private SymbioteSuitClient() {
	}

	public static void initialize() {
		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
			if (tintIndex == 1) {
				return -1;
			}
			var p = Minecraft.getInstance().player;
			SymbioteColours c = p == null ? SymbioteColours.CLASSIC : SymbioteSuitColours.get(p);
			return 0xFF000000 | (tintIndex == 2 ? c.secondary() : c.primary());
		}, SymbioteHostItems.HELMET, SymbioteHostItems.CHESTPLATE, SymbioteHostItems.LEGGINGS, SymbioteHostItems.BOOTS);
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> {
			SymbioteSuitTint.clear();
			SymbioteMouth.clear();
		}));
		SymbioteMouth.initialize(); // v0.17.5: the Lethal Protector maw's swing / hurt / hit tracking and drool
		// v0.17.5 round 2: saliva / goo particles, and the suit-up's droplets, sounds and canvas housekeeping
		SymbioteParticleClient.register();
		SymbioteVfxClient.initialize(); // v0.17.5 tendril-vfx round: the moves' tendril effects (replacing their particles)
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(SymbioteEmerge::tick);
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ProjectHeroMod.id("symbiote_suit_tint");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				SymbioteSuitTint.clear();
			}
		});
	}
}
