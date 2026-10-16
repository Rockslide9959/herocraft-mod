package com.projecthero.mod.client.mutation.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p05.GeoBoulderEntity;
import com.projecthero.mod.hero.power.p05.GeoRockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.5 Geokinesis: the Rock Shot stone and the boulder's shards (a jagged chunk of the block it was torn from,
 * tumbling in flight, settling as it rises out of the ground), and the Boulder Toss boulder (a big cluster of the same
 * block, slowly turning while it is held overhead, rolling end over end once thrown).
 */
public class GeoRockRenderer<T extends Entity> extends EntityRenderer<T> {
	public GeoRockRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.2f;
	}

	@Override
	public void render(T entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = entity.tickCount + partial;
		pose.pushPose();
		if (entity instanceof GeoRockEntity rock) {
			float s = rock.scale();
			pose.translate(0, s * 0.5, 0);
			if (rock.rising() > 0) {
				// still coming up out of the ground: a slow wobble, no tumble yet
				pose.mulPose(Axis.YP.rotationDegrees(t * 9));
				pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * 0.8) * 10));
			} else {
				pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
				pose.mulPose(Axis.XP.rotationDegrees(t * (rock.isShard() ? 31 : 23)));
				pose.mulPose(Axis.ZP.rotationDegrees(t * 11));
			}
			GeoModels.rock(pose, buffers, light, rock.block(), entity.getId(), s);
		} else if (entity instanceof GeoBoulderEntity b) {
			float s = 0.95f + 0.25f * b.liftFraction();
			pose.translate(0, 0.75, 0);
			if (b.held()) {
				pose.mulPose(Axis.YP.rotationDegrees(t * 2.0f));
				pose.mulPose(Axis.XP.rotationDegrees((float) Math.sin(t * 0.15) * 6));
			} else {
				pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
				pose.mulPose(Axis.XP.rotationDegrees(t * 14));
			}
			// a boulder: a big core and a crust of lumps
			GeoModels.rock(pose, buffers, light, b.block(), entity.getId(), s);
			for (int i = 0; i < 4; i++) {
				pose.pushPose();
				float a = (i / 4.0f) * (float) Math.PI * 2 + 0.6f;
				pose.translate(Math.cos(a) * s * 0.42, (GeoModels.rnd(entity.getId(), 70 + i) - 0.5) * s * 0.6, Math.sin(a) * s * 0.42);
				GeoModels.rock(pose, buffers, light, b.block(), entity.getId() * 7 + i, s * 0.55f);
				pose.popPose();
			}
		}
		pose.popPose();
		super.render(entity, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
