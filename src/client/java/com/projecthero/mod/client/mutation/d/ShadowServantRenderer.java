package com.projecthero.mod.client.mutation.d;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.revamp.d.ShadowServantEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.13.22 (Shadow, N): a Shadow Servant is a humanoid silhouette of living darkness -- the player model in a
 * near-black skin with faint violet veins, and two glowing purple eyes drawn full-bright on top.
 */
public class ShadowServantRenderer extends HumanoidMobRenderer<ShadowServantEntity, PlayerModel<ShadowServantEntity>> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/mutation/p19_shadow_servant.png");
	private static final ResourceLocation EYES = ProjectHeroMod.id("textures/entity/mutation/p19_shadow_servant_eyes.png");
	private static final RenderType EYES_TYPE = RenderType.eyes(EYES);

	public ShadowServantRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.4f);
		// only the base body: the outer skin layers would show the texture's empty regions as nothing anyway
		this.model.hat.visible = false;
		this.model.jacket.visible = false;
		this.model.leftSleeve.visible = false;
		this.model.rightSleeve.visible = false;
		this.model.leftPants.visible = false;
		this.model.rightPants.visible = false;
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EYES_TYPE;
			}
		});
	}

	@Override
	public ResourceLocation getTextureLocation(ShadowServantEntity entity) {
		return TEXTURE;
	}
}
