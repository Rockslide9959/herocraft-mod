package com.projecthero.mod.client.blackpanther.plane;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.blackpanther.plane.AncestorEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.7 Black Panther Ancestral Plane: the ancestors -- the plain wide player model in five detailed robed skins (deep purple
 * and gold royal robes, sashes and collars, headwraps and crowns; the eldest in white and gold), from
 * {@code scratchpad/v0177/gen_ancestor_skins.js}. Regular figures, so player models, never a custom body.
 */
public class AncestorRenderer extends HumanoidMobRenderer<AncestorEntity, PlayerModel<AncestorEntity>> {
	private static final ResourceLocation[] SKINS = new ResourceLocation[5];
	static {
		for (int i = 0; i < SKINS.length; i++) {
			SKINS[i] = ProjectHeroMod.id("textures/entity/ancestral/ancestor_" + i + ".png");
		}
	}

	public AncestorRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(AncestorEntity entity) {
		return SKINS[Math.floorMod(entity.variant(), SKINS.length)];
	}
}
