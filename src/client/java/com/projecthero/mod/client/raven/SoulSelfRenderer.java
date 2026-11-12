package com.projecthero.mod.client.raven;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.raven.entity.SoulSelfEntity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Raven: the soul-self -- one big raven (wingspan about 8 blocks) of black and violet energy with white eyes, wings beating,
 * flying along her look. Drawn full-bright and see-through ({@code entityTranslucentEmissive}), with a second, larger violet pass as
 * its glow. It is a bird, nothing like the Dark Shadow quirk's clawed shadow.
 *
 * <p>The model is built here in code ({@link #mesh}), +Z forward and +Y up (no vanilla flip): body, head with beak and eyes, a fanned
 * tail, and two-part wings that fold as they beat. Texture: {@code textures/entity/raven/soul_self.png}
 * ({@code scratchpad/raven/build_raven_textures.js}).
 */
public class SoulSelfRenderer extends EntityRenderer<SoulSelfEntity> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/raven/soul_self.png");
	private static final float SCALE = 2.0f;

	private final ModelPart root;
	private final ModelPart leftWing, leftTip, rightWing, rightTip, head, tail;

	public SoulSelfRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.root = mesh().bakeRoot();
		this.leftWing = root.getChild("left_wing");
		this.leftTip = leftWing.getChild("tip");
		this.rightWing = root.getChild("right_wing");
		this.rightTip = rightWing.getChild("tip");
		this.head = root.getChild("head");
		this.tail = root.getChild("tail");
	}

	public static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3f, -3f, -7f, 6, 6, 14), PartPose.ZERO);
		PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 20).addBox(-2.5f, -2.5f, 0f, 5, 5, 5),
				PartPose.offset(0f, 1.5f, 6.5f));
		head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(20, 20).addBox(-1f, -1.5f, 4.5f, 2, 2, 4),
				PartPose.rotation(0.25f, 0f, 0f));
		head.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(56, 0).addBox(2.3f, 0.2f, 2.5f, 0.5f, 1.4f, 1.4f)
				.texOffs(56, 0).addBox(-2.8f, 0.2f, 2.5f, 0.5f, 1.4f, 1.4f), PartPose.ZERO);
		r.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 32).addBox(-4f, -0.5f, -8f, 8, 1, 8), PartPose.offset(0f, 0.5f, -6.5f));
		PartDefinition lw = r.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 42).addBox(0f, -0.5f, -5f, 16, 1, 10),
				PartPose.offset(3f, 2f, 1f));
		lw.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 53).addBox(0f, -0.5f, -4f, 12, 1, 8), PartPose.offset(16f, 0f, -1f));
		PartDefinition rw = r.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 42).mirror().addBox(-16f, -0.5f, -5f, 16, 1, 10),
				PartPose.offset(-3f, 2f, 1f));
		rw.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 53).mirror().addBox(-12f, -0.5f, -4f, 12, 1, 8), PartPose.offset(-16f, 0f, -1f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void render(SoulSelfEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float fade = e.fade() > 0 ? Mth.clamp(1f - (e.fade() + partial) / 9f, 0f, 1f) : Mth.clamp((e.tickCount + partial) / 4f, 0f, 1f);
		if (fade <= 0.01f) {
			return;
		}
		float phase = e.wingPhase(partial);
		float beat = Mth.sin(phase);
		leftWing.zRot = beat * 0.65f;
		rightWing.zRot = -beat * 0.65f;
		leftTip.zRot = Mth.sin(phase - 0.7f) * 0.45f;
		rightTip.zRot = -Mth.sin(phase - 0.7f) * 0.45f;
		head.xRot = Mth.sin(phase * 0.5f) * 0.06f;
		tail.xRot = Mth.sin(phase + 1.2f) * 0.08f;
		pose.pushPose();
		pose.translate(0f, 0.75f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yRotO, e.getYRot())));
		pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(Mth.lerp(partial, e.xRotO, e.getXRot()), -60f, 60f)));
		// it swells a little as it lunges out, then shrinks away
		float grow = SCALE * (0.6f + 0.4f * fade) * (1f + 0.08f * (1f - Math.abs(beat)));
		pose.scale(grow, grow, grow);
		int a = Math.round(235 * fade);
		root.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				(a << 24) | 0xFFFFFF);
		// the glow: the same raven, larger and violet
		pose.scale(1.12f, 1.25f, 1.08f);
		int ga = Math.round(70 * fade);
		root.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				(ga << 24) | 0x9A5AF0);
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SoulSelfEntity e) {
		return TEXTURE;
	}
}
