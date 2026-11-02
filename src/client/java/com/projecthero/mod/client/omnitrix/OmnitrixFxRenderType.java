package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * v0.17.1: the Omnitrix transformation burst's flat layers ({@link OmnitrixTransformFx#drawBurst}) -- untextured
 * position+colour quads, translucent, unlit (the entity shaders shade a camera-facing quad by its normal, greying the pale
 * core), double-sided, depth-TESTED so walls still hide it but no depth WRITE, so the layers just paint over each other in
 * the order they are drawn (no sorting). Never instantiated as anything but {@link #FLAT}; the subclass only exists to reach
 * the protected render-state shards.
 */
public final class OmnitrixFxRenderType extends RenderType {
	public static final RenderType FLAT = new OmnitrixFxRenderType("projecthero_omnitrix_burst", DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 32768, false, false, () -> {
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

	/**
	 * v0.17.2: the long transformation's cartoon stage and full-screen flashes ({@code OmnitrixSequenceClient}) -- the same flat
	 * quads with NO depth test, so they paint over the whole view in the order drawn.
	 */
	public static final RenderType STAGE = new OmnitrixFxRenderType("projecthero_omnitrix_stage", DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 65536, false, false, () -> {
				RENDERTYPE_LIGHTNING_SHADER.setupRenderState();
				TRANSLUCENT_TRANSPARENCY.setupRenderState();
				COLOR_WRITE.setupRenderState();
				NO_CULL.setupRenderState();
				NO_DEPTH_TEST.setupRenderState();
			}, () -> {
				NO_DEPTH_TEST.clearRenderState();
				NO_CULL.clearRenderState();
				COLOR_WRITE.clearRenderState();
				TRANSLUCENT_TRANSPARENCY.clearRenderState();
				RENDERTYPE_LIGHTNING_SHADER.clearRenderState();
			});

	private OmnitrixFxRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
			boolean sortOnUpload, Runnable setup, Runnable clear) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
	}
}
