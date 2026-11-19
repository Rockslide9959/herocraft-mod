package com.projecthero.mod.client.symbiote;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.5: the render types of the Symbiote suit's overlay passes ({@link SymbioteSuitFx}). The suit itself is drawn with
 * GeckoLib's {@code armorCutoutNoCull}, which nudges armour toward the camera ({@code VIEW_OFFSET_Z_LAYERING}); a plain
 * translucent / eyes pass over the same geometry would sit a hair behind it and fail the depth test. These carry the same
 * layering and write colour only, so they land exactly on the suit. Extends {@link RenderType} only to reach the protected shards;
 * never instantiated.
 */
final class SymbioteFxTypes extends RenderType {
	private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(tex -> create("projecthero_symbiote_eye_glow",
			DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
			CompositeState.builder()
					.setShaderState(RENDERTYPE_EYES_SHADER)
					.setTextureState(new TextureStateShard(tex, false, false))
					.setTransparencyState(ADDITIVE_TRANSPARENCY)
					.setLayeringState(VIEW_OFFSET_Z_LAYERING)
					.setWriteMaskState(COLOR_WRITE)
					.createCompositeState(false)));

	private SymbioteFxTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
			Runnable setup, Runnable clear) {
		super(name, format, mode, size, crumbling, sort, setup, clear);
	}

	/** Additive, full bright, layered like armour: the eye glow. */
	static RenderType glow(ResourceLocation texture) {
		return GLOW.apply(texture);
	}
}
