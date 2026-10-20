package com.projecthero.mod.client.render.workbench;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * v0.16.7 (user): the Stark Workbench hologram's render type -- untextured position+colour quads, ADDITIVE (src alpha,
 * one: the vanilla lightning blend), full-bright (no lightmap in the shader), double-sided, depth-TESTED so walls in front
 * of the desk still hide it, but no depth WRITE, so the hologram's own faces never occlude each other or z-fight -- they
 * just add up into light. Never instantiated as anything but {@link #HOLO}; the subclass only exists to reach the
 * protected render-state shards.
 */
public final class WorkbenchHoloRenderType extends RenderType {
	public static final RenderType HOLO = new WorkbenchHoloRenderType("projecthero_workbench_holo",
			DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, false,
			() -> {
				RENDERTYPE_LIGHTNING_SHADER.setupRenderState();
				LIGHTNING_TRANSPARENCY.setupRenderState();
				COLOR_WRITE.setupRenderState();
				NO_CULL.setupRenderState();
				LEQUAL_DEPTH_TEST.setupRenderState();
			},
			() -> {
				LEQUAL_DEPTH_TEST.clearRenderState();
				NO_CULL.clearRenderState();
				COLOR_WRITE.clearRenderState();
				LIGHTNING_TRANSPARENCY.clearRenderState();
				RENDERTYPE_LIGHTNING_SHADER.clearRenderState();
			});

	private WorkbenchHoloRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
			boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
	}
}
