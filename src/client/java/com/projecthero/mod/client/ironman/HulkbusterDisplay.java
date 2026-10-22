package com.projecthero.mod.client.ironman;

import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ironman.crate.HulkbusterCrate;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * v0.16.7 (user) Hulkbuster fixes: "the suit still looks like the mark 8 armour when its in the suit platform ... make it
 * so that it looks like the actual hulkbuster model". The Hulkbuster is never drawn as player armour any more; wherever a
 * racked / listed Hulkbuster is shown -- the Suit Platform rack ({@code IronManSuitPlatformRenderer}) and the screens'
 * turning previews ({@code IronManGui#suitPreview}) -- its real 3-block body is drawn, through one client-only stand-in
 * {@link IronManSentryEntity} (never added to the world) and the Sentry's own Hulkbuster renderer.
 */
public final class HulkbusterDisplay {
	/** The racked Hulkbuster's scale on a Suit Platform: 3 blocks -> ~1.6, a head taller than the other racked suits. */
	public static final float RACK_SCALE = 0.54f;
	/** The body's height at scale 1 (blocks). */
	public static final float HEIGHT = 3.0f;

	private static IronManSentryEntity stand;

	private HulkbusterDisplay() {
	}

	/** Are these the Hulkbuster's pieces? */
	public static boolean isHulkbuster(ItemStack[] pieces) {
		for (ItemStack p : pieces) {
			if (p != null && p.getItem() instanceof IronManArmorItem a) {
				return HulkbusterCrate.SUIT_ID.equals(a.suitId());
			}
		}
		return false;
	}

	/** The stand-in for {@code level} (rebuilt when the level changes). */
	public static IronManSentryEntity stand(Level level) {
		if (level == null) {
			return null;
		}
		if (stand == null || stand.level() != level) {
			stand = IronManSentryEntity.displayModel(level, HulkbusterCrate.SUIT_ID);
		}
		return stand;
	}

	/** Draw the Hulkbuster body at the pose's origin (its feet), turned to {@code yawDeg} (an entity yaw). */
	public static void render(Level level, PoseStack pose, MultiBufferSource buffers, int light, float partialTick, float yawDeg) {
		IronManSentryEntity s = stand(level);
		if (s == null) {
			return;
		}
		s.setYRot(yawDeg);
		s.yRotO = yawDeg;
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		try {
			dispatcher.render(s, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, light);
		} catch (RuntimeException ignored) {
			// a display must never take the frame down
		} finally {
			dispatcher.setRenderShadow(true);
		}
	}

	/** A turning GUI preview of the Hulkbuster body fitted into {@code x0..x1, y0..y1} ({@code IronManGui#suitPreview}). */
	public static void preview(GuiGraphics g, int x0, int y0, int x1, int y1, float yawDeg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		float scale = (y1 - y0) * 0.9f / HEIGHT;
		float cx = (x0 + x1) / 2f;
		float cy = (y0 + y1) / 2f;
		g.enableScissor(x0, y0, x1, y1);
		PoseStack pose = g.pose();
		pose.pushPose();
		try {
			// the same frame as InventoryScreen#renderEntityInInventory (which only takes living entities)
			pose.translate(cx, cy, 50f);
			pose.scale(scale, scale, -scale);
			pose.translate(0f, HEIGHT / 2f, 0f);
			pose.mulPose(new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateX((float) Math.toRadians(-8))));
			Lighting.setupForEntityInInventory();
			MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
			render(mc.level, pose, buffers, LightTexture.FULL_BRIGHT, 1.0f, yawDeg);
			buffers.endBatch();
		} finally {
			pose.popPose();
			Lighting.setupFor3DItems();
			g.disableScissor();
		}
	}
}
