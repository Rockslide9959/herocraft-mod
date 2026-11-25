package com.projecthero.mod.client.thoracquire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.thoracquire.forge.StarForge;
import com.projecthero.mod.thoracquire.forge.StarForgeBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the Star Forge's contents and fire. The head floats over the core, turning slowly (glinting and crackling once
 * forged). While a Thor channels: a white-hot core of light swelling out of the forge top with the progress, and a thick bolt
 * from his hammer to the head. While finishing: a green haft-glow, then Stormbreaker itself rising out of the forge in a pillar
 * of light, spinning to a stop.
 */
public class StarForgeRenderer implements BlockEntityRenderer<StarForgeBlockEntity> {
	private final ItemRenderer items;

	public StarForgeRenderer(BlockEntityRendererProvider.Context ctx) {
		this.items = ctx.getItemRenderer();
	}

	@Override
	public boolean shouldRenderOffScreen(StarForgeBlockEntity be) {
		return true; // the rising axe and the bolt reach well outside the block
	}

	@Override
	public void render(StarForgeBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		Minecraft mc = Minecraft.getInstance();
		if (be.getLevel() == null) {
			return;
		}
		float time = be.getLevel().getGameTime() + partial;
		ItemStack head = be.head();
		int finish = be.finish();
		VertexConsumer glow = TurboDraw.buffer(buffers);

		if (finish > 0) {
			renderFinish(be, pose, buffers, glow, time, finish + partial);
			return;
		}
		if (head.isEmpty()) {
			return;
		}
		boolean forged = StarForge.isForged(head);
		float heat = be.channelling() ? be.channel() / (float) StarForge.CHANNEL_TICKS : forged ? 0.35f : 0.0f;

		// the core of light on the forge top
		if (be.channelling() || forged) {
			pose.pushPose();
			pose.translate(0.5, 1.15, 0.5);
			float s = 0.12f + heat * 0.18f + 0.02f * (float) Math.sin(time * 0.8);
			TurboDraw.box(glow, pose.last(), s * 0.45f, s * 0.45f, s * 0.45f, 0xFFFFFF, 0.95f);
			TurboDraw.box(glow, pose.last(), s, s, s, be.channelling() ? 0xFFC060 : ThorDraw.GLOW, 0.35f + heat * 0.25f);
			TurboDraw.box(glow, pose.last(), s * 1.5f, s * 1.5f, s * 1.5f, be.channelling() ? 0xFF7020 : ThorDraw.HALO, 0.08f + heat * 0.06f);
			pose.popPose();
		}

		// the head, floating and turning
		pose.pushPose();
		pose.translate(0.5, 1.25 + 0.05 * Math.sin(time * 0.12), 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(time * (be.channelling() ? 6f + heat * 20f : 1.5f)));
		pose.scale(0.75f, 0.75f, 0.75f);
		items.renderStatic(head, ItemDisplayContext.FIXED, be.channelling() || forged ? LightTexture.FULL_BRIGHT : light,
				OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
		pose.popPose();

		// the channel: a bolt from the channeller's hammer into the head
		if (be.channelling() && be.channeler() != null) {
			Player p = be.getLevel().getPlayerByUUID(be.channeler());
			if (p != null) {
				Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
				Vec3 base = Vec3.atLowerCornerOf(be.getBlockPos()).subtract(cam);
				pose.pushPose();
				pose.translate(-base.x, -base.y, -base.z);
				Vec3 headAt = Vec3.atLowerCornerOf(be.getBlockPos()).add(0.5, 1.25, 0.5).subtract(cam);
				Vec3 eye = p.getEyePosition(partial);
				Vec3 look = p.getViewVector(partial);
				Vec3 right = look.cross(new Vec3(0, 1, 0));
				right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
				boolean own = p == mc.player && mc.options.getCameraType().isFirstPerson();
				// from the hammer head: in your own first-person view it starts out past the held hammer, low right, so it never
				// fills the screen
				Vec3 hand = (own ? eye.add(look.scale(1.4)).add(right.scale(0.45)).add(0, -0.45, 0)
						: eye.add(look.scale(0.8)).add(right.scale(0.35)).add(0, -0.45, 0)).subtract(cam);
				double seed = Math.floor(time / 2.0) * 4.1;
				ThorDraw.bolt(glow, pose.last(), ThorDraw.jagged(hand, headAt, 9, 0.25, seed), (own ? 0.45f : 0.9f) + heat * 0.5f, 1.0f);
				ThorDraw.bolt(glow, pose.last(), ThorDraw.jagged(hand, headAt, 7, 0.4, seed + 3), own ? 0.25f : 0.45f, 0.7f);
				pose.popPose();
			}
		}
	}

	private void renderFinish(StarForgeBlockEntity be, PoseStack pose, MultiBufferSource buffers, VertexConsumer glow, float time, float t) {
		int strike = StarForge.FINISH_STRIKE;
		int rise = StarForge.FINISH_TICKS - 8;
		if (t < strike) {
			// the branch knitting into a haft: a green column of light swelling under the head
			float k = t / strike;
			pose.pushPose();
			pose.translate(0.5, 1.0 + k * 0.4, 0.5);
			TurboDraw.box(glow, pose.last(), 0.05f + k * 0.05f, 0.1f + k * 0.4f, 0.05f + k * 0.05f, 0x70FF80, 0.85f);
			TurboDraw.box(glow, pose.last(), 0.12f + k * 0.1f, 0.15f + k * 0.5f, 0.12f + k * 0.1f, 0x30C050, 0.3f);
			pose.popPose();
			pose.pushPose();
			pose.translate(0.5, 1.3, 0.5);
			pose.mulPose(Axis.YP.rotationDegrees(time * 8f));
			pose.scale(0.75f, 0.75f, 0.75f);
			items.renderStatic(StarForge.forgedHead(), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
					buffers, be.getLevel(), 0);
			pose.popPose();
			return;
		}
		float k = Math.min(1f, (t - strike) / (rise - strike));
		float ease = k * k * (3 - 2 * k);
		// a pillar of light the axe rises through
		float fade = t < rise ? 1f : Math.max(0f, 1f - (t - rise) / 8f);
		pose.pushPose();
		pose.translate(0.5, 1.0 + 1.5, 0.5);
		TurboDraw.box(glow, pose.last(), 0.18f, 1.6f, 0.18f, 0xFFFFFF, 0.55f * fade);
		TurboDraw.box(glow, pose.last(), 0.42f, 1.7f, 0.42f, ThorDraw.GLOW, 0.22f * fade);
		TurboDraw.box(glow, pose.last(), 0.8f, 1.8f, 0.8f, ThorDraw.HALO, 0.08f * fade);
		pose.popPose();
		if (t < rise) {
			pose.pushPose();
			pose.translate(0.5, 0.9 + ease * 1.5, 0.5);
			float spin = (1f - ease) * 900f;
			pose.mulPose(Axis.YP.rotationDegrees(spin));
			pose.scale(1.5f, 1.5f, 1.5f);
			items.renderStatic(new ItemStack(ModItems.STORMBREAKER), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
			pose.popPose();
		}
	}
}
