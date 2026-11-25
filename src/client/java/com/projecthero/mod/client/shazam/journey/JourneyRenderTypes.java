package com.projecthero.mod.client.shazam.journey;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.7 Shazam acquisition: the illusion's render types.
 * <ul>
 * <li>{@link #HOLE} -- the "portal" trick that makes the stairwell a hole in the ground that isn't there: the opening's
 * rectangle is drawn with no colour, depth test ALWAYS and the depth range pinned to the far plane, so inside the opening the
 * depth buffer is pushed back to "infinitely far". The stairwell drawn next (ordinary depth-tested translucent quads) then
 * shows only through that opening, and its steps hide each other properly.</li>
 * <li>{@link #ghost} -- textured, translucent, unlit (it glows faintly), double-sided, depth-tested: the railings, arch and
 * sign standing on the ground.</li>
 * </ul>
 * The subclass only exists to reach the protected render-state shards.
 */
public final class JourneyRenderTypes extends RenderType {
	private static final Map<String, RenderType> CACHE = new HashMap<>();

	public static final RenderType HOLE = new JourneyRenderTypes("projecthero_illusion_hole", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
			256, false, false, () -> {
				POSITION_COLOR_SHADER.setupRenderState();
				NO_CULL.setupRenderState();
				RenderSystem.enableDepthTest();
				RenderSystem.depthFunc(GL11.GL_ALWAYS);
				RenderSystem.depthMask(true);
				RenderSystem.colorMask(false, false, false, false);
				GL11.glDepthRange(1.0, 1.0);
			}, () -> {
				GL11.glDepthRange(0.0, 1.0);
				RenderSystem.colorMask(true, true, true, true);
				RenderSystem.depthFunc(GL11.GL_LEQUAL);
				NO_CULL.clearRenderState();
				POSITION_COLOR_SHADER.clearRenderState();
			});

	private JourneyRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
			boolean sortOnUpload, Runnable setup, Runnable clear) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
	}

	public static RenderType ghost(ResourceLocation texture) {
		return CACHE.computeIfAbsent(texture.toString(), k -> {
			TextureStateShard tex = new TextureStateShard(texture, false, false);
			return new JourneyRenderTypes("projecthero_illusion_ghost", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 8192, false, false, () -> {
				RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER.setupRenderState();
				tex.setupRenderState();
				TRANSLUCENT_TRANSPARENCY.setupRenderState();
				NO_CULL.setupRenderState();
				OVERLAY.setupRenderState();
				COLOR_WRITE.setupRenderState();
				LEQUAL_DEPTH_TEST.setupRenderState();
			}, () -> {
				LEQUAL_DEPTH_TEST.clearRenderState();
				COLOR_WRITE.clearRenderState();
				OVERLAY.clearRenderState();
				NO_CULL.clearRenderState();
				TRANSLUCENT_TRANSPARENCY.clearRenderState();
				tex.clearRenderState();
				RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER.clearRenderState();
			});
		});
	}
}
