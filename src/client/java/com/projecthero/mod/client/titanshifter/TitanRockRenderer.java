package com.projecthero.mod.client.titanshifter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.geo.GeoModels;
import com.projecthero.mod.titanshifter.entity.TitanRockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.7 (user): the Beast Titan's rocks ({@link TitanRockEntity}), built from real block models of the ground they were
 * torn out of. A boulder is a chunky cluster of stone blocks ({@link #boulder}) rolling end over end; a chunk of debris is
 * a jagged lump of a few cubes ({@link GeoModels#rock}) tumbling fast -- the smaller, the faster it spins. The Titan's
 * hand draws the same models while it holds them ({@code TitanFormRenderer}).
 */
public class TitanRockRenderer extends EntityRenderer<TitanRockEntity> {
	private static final BlockState[] CRUST = { Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
			Blocks.ANDESITE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState() };

	public TitanRockRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.3f;
	}

	@Override
	public void render(TitanRockEntity rock, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = rock.tickCount + partial;
		float s = rock.scale();
		int seed = rock.shapeSeed();
		pose.pushPose();
		// a fixed random tumble axis per rock
		pose.mulPose(Axis.YP.rotationDegrees(GeoModels.rnd(seed, 90) * 360.0f));
		pose.mulPose(Axis.ZP.rotationDegrees((GeoModels.rnd(seed, 91) - 0.5f) * 60.0f));
		if (rock.isBoulder()) {
			pose.mulPose(Axis.XP.rotationDegrees(t * 11.0f / Math.max(1.0f, s * 0.5f)));
			boulder(pose, buffers, light, rock.block(), seed, s);
		} else {
			float spin = (14.0f + GeoModels.rnd(seed, 92) * 16.0f) / Math.max(0.35f, s);
			pose.mulPose(Axis.XP.rotationDegrees(t * spin));
			pose.mulPose(Axis.YP.rotationDegrees(t * spin * 0.37f));
			GeoModels.rock(pose, buffers, light, rock.block(), seed, s);
		}
		pose.popPose();
		super.render(rock, yaw, partial, pose, buffers, light);
	}

	/**
	 * A boulder about {@code size} across, centred on the origin: a big core block of {@code state} with a crust of eight
	 * smaller stone blocks knocked into it at odd angles (some cobble / stone / andesite), so it reads as rubble clumped
	 * together rather than one cube.
	 */
	public static void boulder(PoseStack pose, MultiBufferSource buffers, int light, BlockState state, int seed, float size) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(GeoModels.rnd(seed, 0) * 360));
		GeoModels.centred(pose, buffers, light, state, size * 0.66f, size * 0.6f, size * 0.64f);
		for (int i = 0; i < 8; i++) {
			pose.pushPose();
			// spread over a sphere (a golden-angle spiral, jittered)
			float yy = 1.0f - (i + 0.5f) / 8.0f * 2.0f;
			float rr = (float) Math.sqrt(1.0f - yy * yy);
			float a = i * 2.39996f + GeoModels.rnd(seed, 10 + i) * 0.6f;
			float d = size * (0.27f + GeoModels.rnd(seed, 20 + i) * 0.06f);
			pose.translate(Math.cos(a) * rr * d, yy * d * 0.85f, Math.sin(a) * rr * d);
			pose.mulPose(Axis.YP.rotationDegrees(GeoModels.rnd(seed, 30 + i) * 90));
			pose.mulPose(Axis.XP.rotationDegrees((GeoModels.rnd(seed, 40 + i) - 0.5f) * 70));
			pose.mulPose(Axis.ZP.rotationDegrees((GeoModels.rnd(seed, 50 + i) - 0.5f) * 70));
			float s = size * (0.36f + GeoModels.rnd(seed, 60 + i) * 0.16f);
			BlockState st = GeoModels.rnd(seed, 70 + i) < 0.4f ? CRUST[(int) (GeoModels.rnd(seed, 80 + i) * CRUST.length) % CRUST.length] : state;
			GeoModels.centred(pose, buffers, light, st, s, s * (0.8f + GeoModels.rnd(seed, 85 + i) * 0.2f), s);
			pose.popPose();
		}
		pose.popPose();
	}

	/** A handful of stones about {@code size} across (held for the Volley / the Fastball). */
	public static void handful(PoseStack pose, MultiBufferSource buffers, int light, BlockState state, int seed, float size) {
		for (int i = 0; i < 4; i++) {
			pose.pushPose();
			float a = i * 1.7f + GeoModels.rnd(seed, i) * 0.5f;
			pose.translate(Math.cos(a) * size * 0.22f, (GeoModels.rnd(seed, 5 + i) - 0.5f) * size * 0.3f, Math.sin(a) * size * 0.22f);
			GeoModels.rock(pose, buffers, light, i == 3 ? CRUST[0] : state, seed * 13 + i, size * (0.42f + GeoModels.rnd(seed, 9 + i) * 0.14f));
			pose.popPose();
		}
	}

	@Override
	public ResourceLocation getTextureLocation(TitanRockEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
