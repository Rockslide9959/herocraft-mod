package com.projecthero.mod.client.powerarmour;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.ironman.IronManBoxes;
import com.projecthero.mod.powerarmour.entity.ArmourMissileEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** v0.17.5 Power Armour: a Missile Pod missile -- a little pixel rocket (light body, red nose, dark fins, a hot exhaust). */
public class ArmourMissileRenderer extends EntityRenderer<ArmourMissileEntity> {
	public ArmourMissileRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(ArmourMissileEntity m, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, m.yRotO, m.getYRot()) - 90f));
		pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partial, m.xRotO, m.getXRot())));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(SuitRenderer.MODULE_TEX));
		int o = OverlayTexture.NO_OVERLAY;
		// along +x: body, nose, fins
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-3f, -0.8f, -0.8f, 2.5f, 0.8f, 0.8f, 9f, 1f, 2f, 2f), 32, 32, light, o, 0xFFFFFFFF);
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(2.5f, -0.6f, -0.6f, 4f, 0.6f, 0.6f, 17f, 1f, 2f, 2f), 32, 32, light, o, 0xFFFFFFFF);
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-3f, -1.8f, -0.2f, -1.6f, 1.8f, 0.2f, 1f, 1f, 2f, 2f), 32, 32, light, o, 0xFFFFFFFF);
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-3f, -0.2f, -1.8f, -1.6f, 0.2f, 1.8f, 1f, 1f, 2f, 2f), 32, 32, light, o, 0xFFFFFFFF);
		IronManBoxes.draw(pose, buffers.getBuffer(RenderType.eyes(SuitRenderer.MODULE_TEX)),
				IronManBoxes.Box.swatch(-4.2f, -0.5f, -0.5f, -3f, 0.5f, 0.5f, 1f, 5f, 2f, 2f), 32, 32, LightTexture.FULL_BRIGHT, o, 0xFFFFB050);
		pose.popPose();
		super.render(m, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(ArmourMissileEntity m) {
		return SuitRenderer.MODULE_TEX;
	}
}
