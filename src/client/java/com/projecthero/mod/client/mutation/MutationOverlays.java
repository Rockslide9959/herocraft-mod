package com.projecthero.mod.client.mutation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.hero.visual.MutationVisualState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * v0.13.22: client registry of mutation overlay renderers, keyed by the same flag the power registers
 * server-side with {@code MutationVisuals.registerFlag}. {@link MutationOverlayLayer} draws every overlay
 * whose flag is on for the player being rendered -- so everyone around sees your stone skin, frost armour
 * or glowing eyes, not just you. Helpers for the common cases live in {@link MutationRender}.
 */
public final class MutationOverlays {
	/** Everything an overlay needs for one player, one frame. */
	public record Context(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			PlayerModel<AbstractClientPlayer> model, float partialTick, float ageInTicks, MutationVisualState state) {
	}

	@FunctionalInterface
	public interface Overlay {
		void render(Context ctx);
	}

	private static final Map<String, List<Overlay>> OVERLAYS = new LinkedHashMap<>();

	private MutationOverlays() {
	}

	/** Adds an overlay drawn while {@code flag} is on (several overlays may share one flag). */
	public static void register(String flag, Overlay overlay) {
		OVERLAYS.computeIfAbsent(flag, k -> new ArrayList<>()).add(overlay);
	}

	public static List<Overlay> get(String flag) {
		return OVERLAYS.getOrDefault(flag, List.of());
	}

	public static java.util.Set<String> flags() {
		return java.util.Collections.unmodifiableSet(OVERLAYS.keySet());
	}
}
