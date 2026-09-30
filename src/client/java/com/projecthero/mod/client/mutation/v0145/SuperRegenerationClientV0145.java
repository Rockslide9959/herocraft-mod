package com.projecthero.mod.client.mutation.v0145;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.gui.AbilityHudExtras;
import com.projecthero.mod.client.mutation.MutationOverlays;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.hero.power.p12.SuperRegenerationHandlers;

import net.minecraft.resources.ResourceLocation;

/** v0.14.5 Super Regeneration rework: client registration (HUD theme and bars, poses, overlays, renderers). */
public final class SuperRegenerationClientV0145 {
	/** Revive-charge dot colours: ready = green, spent / recharging = empty grey (Wolverine Death Surge style). */
	private static final int DOT_READY = 0xFF3CE05A;
	private static final int DOT_EMPTY = 0xFF3A3A3A;
	private static final int DOT = 3;
	private static final int DOT_GAP = 1;

	/** 8 frames of red pixel veins; the bright pulse travels down each vein from frame to frame. */
	private static final int VEIN_FRAMES = 8;
	private static final ResourceLocation[] VEINS = new ResourceLocation[VEIN_FRAMES];

	static {
		for (int i = 0; i < VEIN_FRAMES; i++) {
			VEINS[i] = ProjectHeroMod.id("textures/entity/mutation/p12_blood_veins_" + i + ".png");
		}
	}

	private SuperRegenerationClientV0145() {
	}

	public static void init() {
		AbilityHudExtras.mono(SuperRegenerationHandlers.KEY);

		// "Super Regeneration ▪▪▪" -- one square pixel per revive charge, green when ready, grey while recharging.
		AbilityHudExtras.registerDecor(SuperRegenerationHandlers.KEY, (g, client, state, x, y) -> {
			int top = y + 2; // centred on the 9 px text line
			for (int i = 0; i < SuperRegenerationHandlers.REVIVE_CHARGES; i++) {
				float cd = state.resources.getOrDefault(SuperRegenerationHandlers.KEY + "/revive_cd_" + i, 0f);
				int dx = x + i * (DOT + DOT_GAP);
				g.fill(dx, top, dx + DOT, top + DOT, cd <= 0f ? DOT_READY : DOT_EMPTY);
			}
		});

		// Red veins that trickle down the whole body (frame-stepped) and throb in pixel-stepped brightness.
		MutationOverlays.register(SuperRegenerationHandlers.VEINS_FLAG, ctx -> {
			float t = ctx.ageInTicks() + ctx.partialTick();
			int frame = ((int) (t / 2f)) % VEIN_FRAMES;
			// heartbeat: a quick double throb every 16 ticks, quantised to 4 brightness steps ("pixel" pulse)
			float phase = (t % 16f) / 16f;
			float beat = phase < 0.12f ? 1f : phase < 0.25f ? 0.55f : phase < 0.37f ? 0.85f : 0.3f;
			float k = Math.round((0.45f + 0.55f * beat) * 4f) / 4f;
			MutationRender.shell(ctx, VEINS[frame], MutationRender.Shell.THIN,
					MutationRender.argb(255, Math.round(255 * k), Math.round(255 * k), Math.round(255 * k)), true);
		});
	}
}
