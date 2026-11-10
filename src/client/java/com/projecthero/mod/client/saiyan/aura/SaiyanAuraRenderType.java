package com.projecthero.mod.client.saiyan.aura;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * v0.17.3: the Saiyan aura's two render types, both untextured position+colour quads, double-sided, depth-tested (walls and
 * the body still hide them) and never writing depth (so the layers add up instead of hiding each other, and nothing
 * z-fights):
 * <ul>
 * <li>{@link #GLOW}: ADDITIVE (the vanilla lightning blend) -- the light itself; strongest at night.</li>
 * <li>{@link #TINT}: plain alpha blend -- a saturated wash of the aura colour under the glow, so the aura keeps its colour
 * against a bright daytime sky where pure additive light would wash out to white.</li>
 * </ul>
 * The subclass only exists to reach the protected render-state shards.
 */
public final class SaiyanAuraRenderType extends RenderType {
	public static final RenderType GLOW = new SaiyanAuraRenderType("projecthero_saiyan_aura_glow", DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 65536, false, false, () -> {
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

	public static final RenderType TINT = new SaiyanAuraRenderType("projecthero_saiyan_aura_tint", DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 65536, false, false, () -> {
				POSITION_COLOR_SHADER.setupRenderState();
				TRANSLUCENT_TRANSPARENCY.setupRenderState();
				COLOR_WRITE.setupRenderState();
				NO_CULL.setupRenderState();
				LEQUAL_DEPTH_TEST.setupRenderState();
			}, () -> {
				LEQUAL_DEPTH_TEST.clearRenderState();
				NO_CULL.clearRenderState();
				COLOR_WRITE.clearRenderState();
				TRANSLUCENT_TRANSPARENCY.clearRenderState();
				POSITION_COLOR_SHADER.clearRenderState();
			});

	private SaiyanAuraRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
			boolean sortOnUpload, Runnable setup, Runnable clear) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
	}
}
