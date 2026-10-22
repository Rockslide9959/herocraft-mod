package com.projecthero.mod.client.quirk.erasure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.erasure.ErasureConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.7: Eraser Head on every player who holds Erasure (every viewer: the state is synced) -- while he stares
 * ({@link ErasureConfig#V_ERASE}) his eyes glow red (v0.16.7 user: no risen hair); his yellow
 * goggles sit pushed up on his forehead (with the costume) or down over his eyes (the Goggles move); and the posed right
 * hand's world position is recorded for {@link ErasureWorld}'s scarf.
 */
public class ErasureLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation EYES = ProjectHeroMod.id("textures/entity/quirk/erasure_eyes.png");
	public static final ResourceLocation PARTS = ProjectHeroMod.id("textures/entity/quirk/erasure_parts.png");

	/** [x, z, length] of each risen hair lock on the top of the head (model px, head space). */
	private static final float[][] LOCKS = {
			{ -3f, -3f, 6f }, { -1f, -3.4f, 5f }, { 1f, -3.4f, 5f }, { 3f, -3f, 6f },
			{ -3.4f, -1f, 7f }, { -1f, -1f, 7.5f }, { 1f, -1f, 7.5f }, { 3.4f, -1f, 7f },
			{ -3.4f, 1f, 7.5f }, { -1f, 1f, 8f }, { 1f, 1f, 8f }, { 3.4f, 1f, 7.5f },
			{ -3f, 3.2f, 7f }, { -1f, 3.4f, 7.5f }, { 1f, 3.4f, 7.5f }, { 3f, 3.2f, 7f } };

	private static ModelPart hair;
	private static ModelPart gogglesDown;
	private static ModelPart gogglesUp;

	public ErasureLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	private static void build() {
		if (hair != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition h = root.addOrReplaceChild("hair", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < LOCKS.length; i++) {
			float x = LOCKS[i][0], z = LOCKS[i][1], len = LOCKS[i][2];
			int tex = (i % 4) * 8;
			h.addOrReplaceChild("lock" + i, CubeListBuilder.create().texOffs(tex, 0).addBox(-1.0f, -len, -1.0f, 2, (int) Math.ceil(len), 2),
					PartPose.offsetAndRotation(x, -7.6f, z, -z * 0.13f, 0f, x * 0.13f));
		}
		// the goggles: a yellow two-lens plate on a black strap round the head
		PartDefinition gd = root.addOrReplaceChild("goggles_down", CubeListBuilder.create()
				.texOffs(0, 16).addBox(-4.5f, -5.2f, -4.75f, 9, 3, 1)
				.texOffs(0, 21).addBox(-4.75f, -4.6f, -4.4f, 1, 1, 9)
				.texOffs(0, 21).addBox(3.75f, -4.6f, -4.4f, 1, 1, 9)
				.texOffs(20, 16).addBox(-4.5f, -4.6f, 4.0f, 9, 1, 1), PartPose.ZERO);
		root.addOrReplaceChild("goggles_up", CubeListBuilder.create()
				.texOffs(0, 16).addBox(-4.5f, -1.6f, -0.6f, 9, 3, 1)
				.texOffs(0, 21).addBox(-4.75f, -0.8f, -0.2f, 1, 1, 9)
				.texOffs(0, 21).addBox(3.75f, -0.8f, -0.2f, 1, 1, 9)
				.texOffs(20, 16).addBox(-4.5f, -0.8f, 8.2f, 9, 1, 1), PartPose.offsetAndRotation(0f, -6.9f, -4.45f, -0.3f, 0f, 0f));
		ModelPart baked = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		hair = baked.getChild("hair");
		gogglesDown = baked.getChild("goggles_down");
		gogglesUp = baked.getChild("goggles_up");
	}

	/** 0..1: how far the hair has risen (0 when not staring). */
	static float rise(AbstractClientPlayer p, QuirkState s, float partial) {
		if (s.val(ErasureConfig.V_ERASE) == 0L) {
			return 0f;
		}
		float age = p.level().getGameTime() - s.val(ErasureConfig.V_ERASE_AT) + partial;
		return Mth.clamp(age / 4f, 0f, 1f);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !Quirks.holds(player, Quirk.ERASURE)) {
			return;
		}
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return;
		}
		build();
		PlayerModel<AbstractClientPlayer> parent = getParentModel();
		int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
		float rise = rise(player, s, partialTick);
		boolean blinking = com.projecthero.mod.quirk.erasure.ErasureMoves.eyesShut(player);
		boolean goggles = s.val(ErasureConfig.V_GOGGLES) != 0L;

		pose.pushPose();
		parent.head.translateAndRotate(pose);
		// v0.16.7 (user): no risen hair on the stare ("it looks weird") -- only the red eyes show it
		// v0.16.7 (user skin): the costume's own skin has the goggles pushed up on the forehead, so only the
		// Goggles move draws the 3D pair (down over the eyes)
		if (goggles) {
			gogglesDown.render(pose, buffers.getBuffer(RenderType.entityTranslucent(PARTS)), light, overlay);
		}
		pose.popPose();

		// the red eyes (full bright, drawn on the face of the head) -- v0.16.7 revamp: hidden behind the goggles (they hide
		// where he is looking)
		if (rise > 0f && !blinking && !goggles) {
			int a = (int) (255 * Mth.clamp(rise * 1.5f, 0f, 1f));
			int argb = (a << 24) | 0xFFFFFF;
			parent.head.render(pose, buffers.getBuffer(RenderType.eyes(EYES)), LightTexture.FULL_BRIGHT,
					net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, argb);
		}

		// where the posed right hand is this frame (camera-relative in the entity pass), for the scarf
		pose.pushPose();
		parent.rightArm.translateAndRotate(pose);
		Vector3f tip = pose.last().pose().transformPosition(new Vector3f(-1f / 16f, 11f / 16f, 0f));
		pose.popPose();
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Vec3 world = cam.add(tip.x, tip.y, tip.z);
		if (world.distanceToSqr(player.getPosition(partialTick)) < 9.0) {
			ErasureWorld.recordHand(player, world);
		}
	}
}
