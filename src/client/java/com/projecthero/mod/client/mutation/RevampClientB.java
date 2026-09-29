package com.projecthero.mod.client.mutation;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.batchb.CrystalNodeRenderer;
import com.projecthero.mod.client.mutation.batchb.CrystalShardRenderer;
import com.projecthero.mod.hero.revamp.BatchBEntities;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.13.22 mutation revamp, batch B -- client-side registration (poses, overlays, entity renderers) for:
 * <ul>
 * <li>05 Geokinesis -- stone-skin THICK shell tinted by the ground you draw on; the Rock Surf slab under your feet</li>
 * <li>06 Crystalkinesis -- amethyst THICK shell with crystal spikes; the glide disc; node / shard renderers</li>
 * <li>08 Pyrokinesis -- emissive flame shell that turns blue with heat; jet flames at the feet; blue eyes when hot</li>
 * <li>09 Cryokinesis -- frost THICK shell with icicles; an ice-block shell on a player frozen solid</li>
 * <li>25 Water Manipulation -- a shimmering THIN water film</li>
 * </ul>
 */
public final class RevampClientB {
	private static final ResourceLocation STONE = ProjectHeroMod.id("textures/entity/mutation/p05_stone_skin.png");
	private static final ResourceLocation CRYSTAL = ProjectHeroMod.id("textures/entity/mutation/p06_crystal_armor.png");
	private static final ResourceLocation FLAME = ProjectHeroMod.id("textures/entity/mutation/p08_flame_body.png");
	private static final ResourceLocation FROST = ProjectHeroMod.id("textures/entity/mutation/p09_frozen_armor.png");
	private static final ResourceLocation ICE = ProjectHeroMod.id("textures/entity/mutation/p09_ice_shell.png");
	private static final ResourceLocation WATER = ProjectHeroMod.id("textures/entity/mutation/p25_aquatic.png");

	/** Stone-skin tint per ground (index = GroundType ordinal + 1; 0 = unknown). */
	private static final int[] GROUND_TINT = {
			0xFFFFFFFF, // unknown
			0xFFB8B8B8, // none
			0xFFFFFFFF, // stone
			0xFF6E6E7C, // deepslate
			0xFFF0DCA0, // sand
			0xFFC8574A, // nether
			0xFFB9F0E6, // ore
			0xFFDDF4FF, // frost
	};

	private static ModelPart spike;
	private static ModelPart jetFlame;
	private static ModelPart jetCore;

	private RevampClientB() {
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
		registerPoses();
		registerOverlays();
		EntityRendererRegistry.register(BatchBEntities.GEO_ROCK, ctx -> new ThrownItemRenderer<>(ctx, 1.6f, false));
		EntityRendererRegistry.register(BatchBEntities.CRYSTAL_SHARD, CrystalShardRenderer::new);
		EntityRendererRegistry.register(BatchBEntities.CRYSTAL_NODE, CrystalNodeRenderer::new);
	}

	// ---------------- poses ----------------

	private static float[] f(int tick, float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY,
			float rL, float lL, float h) {
		return new float[] { tick, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static float[] z(int tick) {
		return new float[] { tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}

	private static void registerPoses() {
		// 05: surfing a slab -- side-on, knees bent, arms out for balance
		MutationPose.registerLoop("p05.surf", 4, new float[][] { z(0),
				f(4, -0.3f, 0, 1.15f, -0.3f, 0, -1.15f, 0.22f, 0.65f, -0.45f, 0.4f, 0.1f),
				f(14, -0.4f, 0, 1.25f, -0.2f, 0, -1.05f, 0.26f, 0.7f, -0.5f, 0.45f, 0.1f),
				f(24, -0.3f, 0, 1.15f, -0.3f, 0, -1.15f, 0.22f, 0.65f, -0.45f, 0.4f, 0.1f) });
		// 05: heave a colossal rock from overhead
		MutationPose.register("p05.heave", new float[][] { z(0),
				f(3, -3.0f, 0, 0.3f, -3.0f, 0, -0.3f, -0.3f, 0, 0.2f, -0.2f, -0.3f),
				f(7, -1.3f, 0, 0.1f, -1.3f, 0, -0.1f, 0.4f, 0, -0.4f, 0.4f, 0.1f),
				f(12, -1.2f, 0, 0.1f, -1.2f, 0, -0.1f, 0.35f, 0, -0.4f, 0.4f, 0.1f), z(18) });
		// 05: reach out, clench and drag the ground down
		MutationPose.register("p05.sinkhole", new float[][] { z(0),
				f(3, AIM, AIM, 0, 0.2f, 0, -0.2f, 0.1f, -0.2f, -0.1f, 0.1f, 0),
				f(8, AIM, AIM, 0, 0.2f, 0, -0.2f, 0.1f, -0.2f, -0.1f, 0.1f, 0),
				f(12, -0.2f, 0.2f, 0.2f, 0.3f, 0, -0.3f, 0.45f, -0.1f, -0.3f, 0.3f, 0.3f),
				f(16, -0.2f, 0.2f, 0.2f, 0.3f, 0, -0.3f, 0.45f, -0.1f, -0.3f, 0.3f, 0.3f), z(21) });
		// 06: fists pulled in, then flung wide as every node bursts
		MutationPose.register("p06.shatter", new float[][] { z(0),
				f(4, -1.2f, -0.9f, 0, -1.2f, 0.9f, 0, 0.15f, 0, 0, 0, 0.1f),
				f(7, -1.5f, 0.6f, 1.2f, -1.5f, -0.6f, -1.2f, -0.15f, 0, 0.1f, -0.1f, -0.2f),
				f(12, -1.4f, 0.6f, 1.2f, -1.4f, -0.6f, -1.2f, -0.12f, 0, 0.1f, -0.1f, -0.2f), z(17) });
		// 06: gliding the crystal rail -- leaning in, arms trailing
		MutationPose.registerLoop("p06.glide", 4, new float[][] { z(0),
				f(4, 0.55f, 0, 0.45f, 0.55f, 0, -0.45f, 0.35f, 0, -0.1f, 0.25f, -0.25f),
				f(14, 0.65f, 0, 0.5f, 0.6f, 0, -0.5f, 0.38f, 0, -0.15f, 0.3f, -0.25f),
				f(24, 0.55f, 0, 0.45f, 0.55f, 0, -0.45f, 0.35f, 0, -0.1f, 0.25f, -0.25f) });
		// 08: jet flight -- arms driving back and down, body stretched along the flight line
		MutationPose.registerLoop("p08.jet", 3, new float[][] { z(0),
				f(3, 0.55f, 0, 0.3f, 0.55f, 0, -0.3f, 0.45f, 0, 0.25f, 0.3f, -0.4f),
				f(6, 0.6f, 0, 0.34f, 0.6f, 0, -0.34f, 0.47f, 0, 0.3f, 0.25f, -0.4f),
				f(9, 0.55f, 0, 0.3f, 0.55f, 0, -0.3f, 0.45f, 0, 0.25f, 0.3f, -0.4f) });
		// 08: heat wave -- curl in, then throw every limb outward
		MutationPose.register("p08.heat_wave", new float[][] { z(0),
				f(5, -0.7f, -0.6f, 0.1f, -0.7f, 0.6f, -0.1f, 0.4f, 0, 0.3f, 0.2f, 0.35f),
				f(8, -0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.25f, 0, -0.2f, 0.2f, -0.45f),
				f(15, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.2f, 0, -0.2f, 0.2f, -0.4f), z(21) });
		// 09: the Iceman slide -- one arm forward, one back, crouched and leaning
		MutationPose.registerLoop("p09.slide", 4, new float[][] { z(0),
				f(4, 0.7f, 0, 0.2f, -1.1f, 0.2f, -0.1f, 0.3f, 0.2f, -0.35f, 0.45f, -0.1f),
				f(14, 0.75f, 0, 0.25f, -1.2f, 0.25f, -0.1f, 0.32f, 0.22f, -0.4f, 0.5f, -0.1f),
				f(24, 0.7f, 0, 0.2f, -1.1f, 0.2f, -0.1f, 0.3f, 0.2f, -0.35f, 0.45f, -0.1f) });
		// 09: draw the Ice Blade -- a rising cross-body sweep
		MutationPose.register("p09.blade_draw", new float[][] { z(0),
				f(4, 0.4f, -0.9f, 0.2f, 0.1f, 0, -0.1f, 0.1f, -0.4f, 0, 0, 0),
				f(8, -2.4f, 0.4f, 0.3f, 0.1f, 0, -0.2f, -0.1f, 0.3f, 0, 0, -0.1f),
				f(13, -2.3f, 0.4f, 0.3f, 0.1f, 0, -0.2f, -0.1f, 0.3f, 0, 0, -0.1f), z(18) });
		// 25: shove the tidal wave out with both palms
		MutationPose.register("p25.wave_push", new float[][] { z(0),
				f(4, -0.9f, 0.3f, 0.2f, -0.9f, -0.3f, -0.2f, -0.1f, 0, 0.2f, -0.2f, 0),
				f(7, -1.6f, -0.1f, 0, -1.6f, 0.1f, 0, 0.3f, 0, -0.45f, 0.45f, 0.1f),
				f(14, -1.55f, -0.1f, 0, -1.55f, 0.1f, 0, 0.28f, 0, -0.45f, 0.45f, 0.1f), z(20) });
	}

	// ---------------- overlays ----------------

	private static void registerOverlays() {
		// 05 Geokinesis: stone skin, tinted by the ground you are drawing on
		MutationOverlays.register("p05.stone_skin", ctx -> {
			int g = Mth.clamp((int) ctx.state().value("p05.ground", 0), 0, GROUND_TINT.length - 1);
			MutationRender.shell(ctx, STONE, MutationRender.Shell.THICK, GROUND_TINT[g], false);
		});
		// 05: the slab of ground you ride on Rock Surf
		MutationOverlays.register("p05.rock_surf", ctx -> {
			int id = (int) ctx.state().value("p05.surf_block", 0);
			BlockState st = id > 0 ? Block.stateById(id) : Blocks.STONE.defaultBlockState();
			if (st.isAir()) {
				st = Blocks.STONE.defaultBlockState();
			}
			underFoot(ctx, st, 1.1f, 0.3f, 1.7f, ctx.light());
		});

		// 06 Crystalkinesis: amethyst plating with spikes at the shoulders and back
		MutationOverlays.register("p06.crystal_armor", ctx -> {
			MutationRender.shell(ctx, CRYSTAL, MutationRender.Shell.THICK, 0xF0FFFFFF, false);
			spikes(ctx, CRYSTAL, 0xFFFFFFFF, true);
		});
		// 06: the glowing crystal disc you glide on
		MutationOverlays.register("p06.crystal_path", ctx ->
				underFoot(ctx, Blocks.AMETHYST_BLOCK.defaultBlockState(), 0.9f, 0.14f, 1.3f, LightTexture.FULL_BRIGHT));

		// 08 Pyrokinesis: an emissive flame body, orange -> white-gold -> blue as the heat climbs
		MutationOverlays.register("p08.flame_body", ctx -> {
			float heat = Mth.clamp(ctx.state().value("p08.heat", 0f), 0f, 1f);
			float flick = MutationRender.pulse(ctx, 7f);
			int a = (int) (180 + 60 * flick);
			int tint = flameTint(heat);
			MutationRender.shell(ctx, FLAME, MutationRender.Shell.THIN, (a << 24) | (tint & 0xFFFFFF), true);
			int a2 = (int) (90 + 70 * (1f - flick));
			MutationRender.shell(ctx, FLAME, MutationRender.Shell.THICK, (a2 << 24) | (tint & 0xFFFFFF), true);
		});
		MutationOverlays.register("p08.blue", ctx -> MutationRender.eyes(ctx, 0xFF7FC4FF));
		// 08: twin jets of flame from the feet
		MutationOverlays.register("p08.jet", ctx -> {
			float heat = Mth.clamp(ctx.state().value("p08.heat", 0f), 0f, 1f);
			jets(ctx, flameTint(heat));
		});

		// 09 Cryokinesis: frost plating with icicles; a full ice shell when frozen solid
		MutationOverlays.register("p09.frozen_armor", ctx -> {
			MutationRender.shell(ctx, FROST, MutationRender.Shell.THICK, 0xE6FFFFFF, false);
			spikes(ctx, FROST, 0xFFE8F8FF, false);
		});
		MutationOverlays.register("p09.frozen_solid", ctx ->
				MutationRender.shell(ctx, ICE, MutationRender.Shell.THICK, 0xD0FFFFFF, false));

		// 25 Water Manipulation: a thin, breathing film of water
		MutationOverlays.register("p25.aquatic", ctx -> {
			float p = MutationRender.pulse(ctx, 30f);
			int a = (int) (150 + 90 * p);
			MutationRender.shell(ctx, WATER, MutationRender.Shell.THIN, (a << 24) | 0xFFFFFF, false);
		});
	}

	/** Orange at rest, white-gold as it heats, blue flame above 75%. */
	private static int flameTint(float heat) {
		if (heat >= 0.75f) {
			return 0xFF5AAEFF;
		}
		float t = heat / 0.75f;
		int r = 255;
		int g = (int) Mth.lerp(t, 120, 215);
		int b = (int) Mth.lerp(t, 30, 120);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Draws {@code state} squashed into a board under the rendered player's feet, turned with the body. */
	private static void underFoot(MutationOverlays.Context ctx, BlockState state, float w, float h, float len, int light) {
		PoseStack pose = ctx.pose();
		pose.pushPose();
		// layer space is the model's (y down, x mirrored, feet at y = +1.5): flip back to world orientation at the feet
		pose.translate(0.0, 1.5, 0.0);
		pose.scale(-1.0f, -1.0f, 1.0f);
		float bob = (float) Math.sin((ctx.ageInTicks() + ctx.partialTick()) * 0.5) * 0.02f;
		pose.translate(-w / 2.0, -h - 0.02 + bob, -len / 2.0);
		pose.scale(w, h, len);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, ctx.buffers(), light,
				OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** Three crystal / ice spikes: one off each shoulder, one down the spine. */
	private static void spikes(MutationOverlays.Context ctx, ResourceLocation tex, int argb, boolean glow) {
		if (spike == null) {
			spike = MutationRender.bake(CubeListBuilder.create().texOffs(8, 8).addBox(-1.0f, -5.0f, -1.0f, 2, 5, 2)
					.texOffs(40, 40).addBox(-0.5f, -7.0f, -0.5f, 1, 2, 1), 64, 64);
		}
		var m = ctx.model();
		RenderType type = glow ? RenderType.entityTranslucentEmissive(tex) : RenderType.entityTranslucent(tex);
		int light = glow ? LightTexture.FULL_BRIGHT : ctx.light();
		PoseStack pose = ctx.pose();
		// shoulders (in the arm frames, so they follow every swing)
		pose.pushPose();
		m.rightArm.translateAndRotate(pose);
		pose.translate(-2.0 / 16.0, -1.5 / 16.0, 0.0);
		pose.mulPose(Axis.ZP.rotation(-0.55f));
		spike.render(pose, ctx.buffers().getBuffer(type), light, OverlayTexture.NO_OVERLAY, argb);
		pose.popPose();
		pose.pushPose();
		m.leftArm.translateAndRotate(pose);
		pose.translate(2.0 / 16.0, -1.5 / 16.0, 0.0);
		pose.mulPose(Axis.ZP.rotation(0.55f));
		spike.render(pose, ctx.buffers().getBuffer(type), light, OverlayTexture.NO_OVERLAY, argb);
		pose.popPose();
		// spine
		pose.pushPose();
		m.body.translateAndRotate(pose);
		pose.translate(0.0, 3.0 / 16.0, 2.4 / 16.0);
		pose.mulPose(Axis.XP.rotation(-1.1f));
		spike.render(pose, ctx.buffers().getBuffer(type), light, OverlayTexture.NO_OVERLAY, argb);
		pose.popPose();
	}

	/** A flickering flame cone under each foot. */
	private static void jets(MutationOverlays.Context ctx, int tint) {
		if (jetFlame == null) {
			jetFlame = MutationRender.bake(CubeListBuilder.create().addBox(-2.0f, 0.0f, -2.0f, 4, 6, 4), 4, 4);
			jetCore = MutationRender.bake(CubeListBuilder.create().addBox(-1.0f, 0.0f, -1.0f, 2, 9, 2), 4, 4);
		}
		var m = ctx.model();
		float t = ctx.ageInTicks() + ctx.partialTick();
		ModelPart[] legs = { m.rightLeg, m.leftLeg };
		for (int i = 0; i < 2; i++) {
			PoseStack pose = ctx.pose();
			pose.pushPose();
			legs[i].translateAndRotate(pose);
			pose.translate(0.0, 12.0 / 16.0, 0.0);
			float flick = 0.75f + 0.35f * (float) Math.sin(t * 2.3 + i * 1.7);
			pose.scale(1.0f, flick, 1.0f);
			jetFlame.render(pose, ctx.buffers().getBuffer(RenderType.eyes(MutationRender.WHITE)), LightTexture.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, (0xB0 << 24) | (tint & 0xFFFFFF));
			jetCore.render(pose, ctx.buffers().getBuffer(RenderType.eyes(MutationRender.WHITE)), LightTexture.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, 0xFFFFF4D8);
			pose.popPose();
		}
	}
}
