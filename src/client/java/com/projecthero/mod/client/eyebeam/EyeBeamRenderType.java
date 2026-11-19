package com.projecthero.mod.client.eyebeam;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * v0.17.5 eye beams ({@link EyeBeamFx}): untextured position+colour quads, unlit, double-sided and depth-TESTED but never
 * depth-WRITTEN, so the soft-edged layers (their outer edges fade to alpha 0) paint over each other without cutting
 * see-through boxes out of water, particles or each other. {@link #GLOW} blends normally (a red glow stays red against a blue
 * sky); {@link #ADD} is additive like vanilla lightning (the white-hot core burns bright); {@link #OVER} is additive with no
 * depth test, for the first stretch of your own beams seen from behind your head in third person. The subclass only exists to
 * reach the protected render-state shards.
 */
public final class EyeBeamRenderType extends RenderType {
	public static final RenderType GLOW = new EyeBeamRenderType("projecthero_eye_beam_glow", false, false);
	public static final RenderType ADD = new EyeBeamRenderType("projecthero_eye_beam_add", true, false);
	public static final RenderType OVER = new EyeBeamRenderType("projecthero_eye_beam_over", true, true);

	private EyeBeamRenderType(String name, boolean additive, boolean noDepth) {
		super(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, false, () -> {
			RENDERTYPE_LIGHTNING_SHADER.setupRenderState();
			(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY).setupRenderState();
			COLOR_WRITE.setupRenderState();
			NO_CULL.setupRenderState();
			(noDepth ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST).setupRenderState();
		}, () -> {
			(noDepth ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST).clearRenderState();
			NO_CULL.clearRenderState();
			COLOR_WRITE.clearRenderState();
			(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY).clearRenderState();
			RENDERTYPE_LIGHTNING_SHADER.clearRenderState();
		});
	}
}
