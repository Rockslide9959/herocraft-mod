package com.projecthero.mod.client.saiyan.kit;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * v0.17.3: the Spirit Bomb's render types, untextured position+colour quads, double-sided, depth-tested, never writing depth
 * (so the nested shells add up instead of hiding each other): {@link #GLOW} additive (the light), {@link #TINT} alpha-blended
 * (a pale body that still reads against a bright sky). The subclass only exists to reach the protected shards.
 */
final class SpiritBombRenderType extends RenderType {
	static final RenderType GLOW = new SpiritBombRenderType("projecthero_spirit_bomb_glow", DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 262144, false, false, () -> {
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

	static final RenderType TINT = new SpiritBombRenderType("projecthero_spirit_bomb_tint", DefaultVertexFormat.POSITION_COLOR,
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

	private SpiritBombRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
			boolean sortOnUpload, Runnable setup, Runnable clear) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
	}
}
