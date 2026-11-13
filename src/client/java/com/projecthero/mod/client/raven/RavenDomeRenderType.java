package com.projecthero.mod.client.raven;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * v0.17.3 tester round ("Raven bubble doesn't show the player model"): the Dark Shield dome's render types. The dome used to be
 * drawn with {@code entityTranslucent}, which writes depth: when the batch holding the dome was drawn before the player's own
 * translucent skin batch (ImmediatelyFast, which the test modpack runs, batches every entity layer to the end of the frame), the
 * dome's near side filled the depth buffer and the player and anything else translucent inside it never drew. These are untextured
 * position+colour quads, depth-TESTED (walls still hide the dome) but with no depth WRITE and no culling, so whatever order the
 * batches are drawn in, everything inside shows through and the inside of the dome shows when the camera is in it.
 */
public final class RavenDomeRenderType extends RenderType {
	/** The dark see-through shell. */
	public static final RenderType VEIL = new RavenDomeRenderType("projecthero_raven_dome", () -> {
		RENDERTYPE_LIGHTNING_SHADER.setupRenderState();
		TRANSLUCENT_TRANSPARENCY.setupRenderState();
		COLOR_WRITE.setupRenderState();
		NO_CULL.setupRenderState();
		LEQUAL_DEPTH_TEST.setupRenderState();
	}, () -> {
		LEQUAL_DEPTH_TEST.clearRenderState();
		NO_CULL.clearRenderState();
		COLOR_WRITE.clearRenderState();
		TRANSLUCENT_TRANSPARENCY.clearRenderState();
		RENDERTYPE_LIGHTNING_SHADER.clearRenderState();
	});

	/** The violet seams crawling up it (additive light). */
	public static final RenderType SEAM = new RavenDomeRenderType("projecthero_raven_dome_seam", () -> {
		RENDERTYPE_LIGHTNING_SHADER.setupRenderState();
		LIGHTNING_TRANSPARENCY.setupRenderState();
		COLOR_WRITE.setupRenderState();
		NO_CULL.setupRenderState();
		LEQUAL_DEPTH_TEST.setupRenderState();
	}, () -> {
		LEQUAL_DEPTH_TEST.clearRenderState();
		NO_CULL.clearRenderState();
		COLOR_WRITE.clearRenderState();
		LIGHTNING_TRANSPARENCY.clearRenderState();
		RENDERTYPE_LIGHTNING_SHADER.clearRenderState();
	});

	private RavenDomeRenderType(String name, Runnable setup, Runnable clear) {
		super(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, false, setup, clear);
	}
}
