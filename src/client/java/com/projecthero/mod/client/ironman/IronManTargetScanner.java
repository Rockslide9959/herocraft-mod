package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.gui.IronManGui;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.network.IronManLockPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.26: the Iron Man helmet's target scanner. Every Mark from the Mark II up (visor closed) reads out whatever living
 * thing is under the crosshair -- out to {@link #range} (100 blocks; the Mark II's older optics reach 25): its name, health
 * (numbers and a bar), armour, distance and threat class. The Mark III's targeting system shows its <b>lock</b> instead
 * (the target the suit auto-aims at), with a rotating red-and-gold lock reticle drawn on the target in the world.
 */
public final class IronManTargetScanner {
	private static int lockId = -1;

	private IronManTargetScanner() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(IronManLockPayload.TYPE, (payload, context) -> lockId = payload.entityId());
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> lockId = -1);
		WorldRenderEvents.AFTER_ENTITIES.register(IronManTargetScanner::renderReticle);
	}

	/** How far this suit's scanner reads: 25 for the Mark II, 100 for everything newer. */
	public static double range(IronManSuit suit) {
		return "mark_2".equals(suit.id()) ? 25.0 : 100.0;
	}

	public static LivingEntity lockTarget() {
		Minecraft mc = Minecraft.getInstance();
		if (lockId < 0 || mc.level == null) {
			return null;
		}
		Entity e = mc.level.getEntity(lockId);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** The living thing under the crosshair within {@code range}, not hidden behind blocks. */
	public static LivingEntity pick(Player player, double range, float partial) {
		Vec3 eye = player.getEyePosition(partial);
		Vec3 look = player.getViewVector(partial);
		Vec3 end = eye.add(look.scale(range));
		var block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
		Vec3 stop = eye.add(look.scale(reach));
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : player.level().getEntities(player, new AABB(eye, stop).inflate(1.5),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !e.isInvisible())) {
			AABB box = e.getBoundingBox().inflate(Math.max(0.3, e.distanceTo(player) * 0.004));
			var hit = box.clip(eye, stop);
			if (hit.isPresent()) {
				double d = hit.get().distanceToSqr(eye);
				if (d < bestD) {
					bestD = d;
					best = (LivingEntity) e;
				}
			}
		}
		return best;
	}

	/** The target panel, to the right of the crosshair. */
	public static void renderPanel(GuiGraphics g, Font font, Player player, IronManSuit suit, float partial) {
		LivingEntity locked = com.projecthero.mod.ironman.IronManTargeting.hasTargeting(suit) ? lockTarget() : null;
		LivingEntity t = locked != null ? locked : pick(player, range(suit), partial);
		if (t == null) {
			return;
		}
		int x = g.guiWidth() / 2 + 16;
		int y = g.guiHeight() / 2 - 8;
		int w = 118;
		float hp = t.getHealth();
		float max = Math.max(1f, t.getMaxHealth());
		double armour = t.getAttributes().hasAttribute(Attributes.ARMOR) ? t.getAttributeValue(Attributes.ARMOR) : 0.0;
		boolean hostile = t instanceof Enemy;
		boolean boss = max >= 150f;
		int threatColour = boss ? 0xFFE04040 : hostile ? 0xFFF0A030 : 0xFF60D070;
		String threat = Component.translatable(boss ? "hud.projecthero.ironman.scan.boss"
				: hostile ? "hud.projecthero.ironman.scan.hostile" : t instanceof Player ? "hud.projecthero.ironman.scan.player"
						: "hud.projecthero.ironman.scan.neutral").getString();
		int h = 44;
		g.fill(x - 3, y - 3, x + w + 3, y + h, 0x88000810);
		g.fill(x - 3, y - 3, x - 2, y + h, IronManGui.alpha(locked != null ? 0xE04040 : IronManGui.CYAN, 0.9f));
		String name = IronManGui.fit(font, t.getName().getString(), w - (locked != null ? 34 : 0));
		g.drawString(font, name, x, y, IronManGui.alpha(IronManGui.GOLD, 0.95f), true);
		if (locked != null) {
			String lk = Component.translatable("hud.projecthero.ironman.scan.locked").getString();
			g.drawString(font, lk, x + w - font.width(lk), y, 0xFFFF5050, true);
		}
		String hpText = String.format(java.util.Locale.ROOT, "%.0f / %.0f", hp, max);
		g.drawString(font, Component.translatable("hud.projecthero.ironman.scan.hp").getString(), x, y + 11, IronManGui.alpha(IronManGui.TEXT_DIM, 0.9f), false);
		g.drawString(font, hpText, x + w - font.width(hpText), y + 11, 0xFFFFFFFF, false);
		float frac = Math.max(0f, Math.min(1f, hp / max));
		g.fill(x, y + 20, x + w, y + 23, 0x66000000);
		int barColour = frac > 0.6f ? 0xFF50D060 : frac > 0.3f ? 0xFFE0B030 : 0xFFE04040;
		g.fill(x, y + 20, x + Math.round(w * frac), y + 23, barColour);
		String info = Component.translatable("hud.projecthero.ironman.scan.info", (int) Math.round(armour),
				String.format(java.util.Locale.ROOT, "%.1f", player.distanceTo(t))).getString();
		g.drawString(font, IronManGui.fit(font, info, w), x, y + 26, IronManGui.alpha(IronManGui.TEXT, 0.9f), false);
		g.drawString(font, threat, x, y + 35, threatColour, false);
	}

	/** The Mark III lock reticle on the target. */
	private static void renderReticle(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		LivingEntity target = lockTarget();
		if (target == null || mc.player == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float pt = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float time = mc.level.getGameTime() + pt;
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		Vec3 centre = target.getPosition(pt).add(0, target.getBbHeight() * 0.5, 0);
		float size = Math.max(target.getBbWidth(), target.getBbHeight()) * 0.55f + 0.3f;
		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(centre.x - cam.x, centre.y - cam.y, centre.z - cam.z);
		pose.mulPose(camera.rotation());
		float dist = (float) cam.distanceTo(centre);
		float t = 0.03f + dist * 0.0015f;
		VertexConsumer vc = TurboDraw.buffer(context.consumers());
		// outer: four gold brackets turning slowly
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(time * 3f));
		float arm = size * 0.4f;
		for (int k = 0; k < 4; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 90f));
			pose.pushPose();
			pose.translate(size - arm * 0.5f, size, 0f);
			TurboDraw.box(vc, pose.last(), arm * 0.5f, t, t * 0.5f, 0xE8B83A, 0.9f);
			pose.popPose();
			pose.pushPose();
			pose.translate(size, size - arm * 0.5f, 0f);
			TurboDraw.box(vc, pose.last(), t, arm * 0.5f, t * 0.5f, 0xE8B83A, 0.9f);
			pose.popPose();
			pose.popPose();
		}
		pose.popPose();
		// inner: a red diamond pulsing the other way, and a centre dot
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(45f - time * 6f));
		float in = size * (0.45f + 0.06f * (float) Math.sin(time * 0.4f));
		for (int k = 0; k < 4; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 90f));
			pose.translate(0f, in, 0f);
			TurboDraw.box(vc, pose.last(), in * 0.35f, t * 0.8f, t * 0.4f, 0xE03030, 0.85f);
			pose.popPose();
		}
		pose.popPose();
		TurboDraw.box(vc, pose.last(), t * 1.4f, t * 1.4f, t * 0.5f, 0xFF5050, 0.9f);
		pose.popPose();
	}
}
