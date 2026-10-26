package com.projecthero.mod.client.speedforce;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.speedforce.entity.SpeedForceDoorwayEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1 (user, Speed Force Memories): a memory road doorway draws only its floating label here -- big, over the frame: the
 * place's name, and under it where it is ("Overworld 120, 64, -30"); full-bright, readable from down the road. The swirl
 * of lightning in the opening is drawn with the rest of the Speed Force lightning ({@code SpeedForceClient#render}).
 */
public class SpeedForceDoorwayRenderer extends EntityRenderer<SpeedForceDoorwayEntity> {
	private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
	private static final double LABEL_RANGE = 96.0;

	public SpeedForceDoorwayRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0f;
	}

	@Override
	public void render(SpeedForceDoorwayEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
		Component label = e.getCustomName();
		if (label == null || entityRenderDispatcher.distanceToSqr(e) > LABEL_RANGE * LABEL_RANGE) {
			return;
		}
		Component name = label;
		Component where = null;
		if (label.getContents() instanceof TranslatableContents tc && tc.getArgs().length == 2
				&& tc.getArgs()[0] instanceof Component a && tc.getArgs()[1] instanceof Component b) {
			name = a.copy().withStyle(label.getStyle());
			where = b;
		}
		Font font = getFont();
		int bg = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.4f) * 255.0f) << 24;
		pose.pushPose();
		pose.translate(0.0, 7.6, 0.0);
		pose.mulPose(entityRenderDispatcher.cameraOrientation());
		pose.pushPose();
		pose.scale(0.06f, -0.06f, 0.06f);
		Matrix4f m = pose.last().pose();
		// as a vanilla name tag: the backdrop see-through, then the text over it
		font.drawInBatch(name, -font.width(name) / 2f, 0, 0x20FFFFFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, bg, 0xF000F0);
		font.drawInBatch(name, -font.width(name) / 2f, 0, 0xFFFFFFFF, false, m, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
		pose.popPose();
		if (where != null) {
			pose.translate(0.0, -0.62, 0.0);
			pose.scale(0.035f, -0.035f, 0.035f);
			Matrix4f m2 = pose.last().pose();
			font.drawInBatch(where, -font.width(where) / 2f, 0, 0x20FFFFFF, false, m2, buffers, Font.DisplayMode.SEE_THROUGH, bg, 0xF000F0);
			font.drawInBatch(where, -font.width(where) / 2f, 0, 0xFFE8E0C8, false, m2, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
		}
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(SpeedForceDoorwayEntity e) {
		return NONE;
	}
}
