package com.projecthero.mod.client.shazam;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.shazam.ShazamConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3 Shazam's <b>Wisdom of Solomon</b> (V, 10 s), drawn on his own client: above every creature within 24 blocks its health,
 * armour and weakness ({@link #weakness}); and under the crosshair the best tool for the block he looks at, or the best weapon against
 * the creature. Everything it shows is already on the client (health and armour are synced), so no packet is needed.
 */
public final class ShazamWisdom {
	private ShazamWisdom() {
	}

	/** The weakness line's lang key for {@code e} (what hurts it most). */
	public static String weakness(LivingEntity e) {
		if (e instanceof Player p && Shazam.transformed(p)) {
			return "hud.projecthero.shazam.weak.magic";
		}
		if (e.getType().is(EntityTypeTags.UNDEAD)) {
			return "hud.projecthero.shazam.weak.undead";
		}
		if (e.getType().is(EntityTypeTags.ARTHROPOD)) {
			return "hud.projecthero.shazam.weak.arthropod";
		}
		if (e instanceof Blaze || e instanceof EnderMan || e instanceof Strider) {
			return "hud.projecthero.shazam.weak.water";
		}
		if (e.getType().is(EntityTypeTags.SENSITIVE_TO_IMPALING)) {
			return "hud.projecthero.shazam.weak.impaling";
		}
		if (e instanceof Creeper) {
			return "hud.projecthero.shazam.weak.creeper";
		}
		if (e.fireImmune()) {
			return "hud.projecthero.shazam.weak.fireproof";
		}
		return "hud.projecthero.shazam.weak.fire";
	}

	/** The best tool for {@code state}'s lang key. */
	public static String tool(BlockState state) {
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return "hud.projecthero.shazam.tool.pickaxe";
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return "hud.projecthero.shazam.tool.axe";
		}
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return "hud.projecthero.shazam.tool.shovel";
		}
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL)) {
			return "hud.projecthero.shazam.tool.shears";
		}
		if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
			return "hud.projecthero.shazam.tool.hoe";
		}
		return "hud.projecthero.shazam.tool.hand";
	}

	/** The world labels (WorldRenderEvents.AFTER_ENTITIES). */
	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !Shazam.wise(mc.player)) {
			return;
		}
		PoseStack ps = ctx.matrixStack();
		if (ps == null) {
			return;
		}
		Font font = mc.font;
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		double r = ShazamConfig.WISDOM_RANGE;
		for (Entity ent : mc.level.entitiesForRendering()) {
			if (!(ent instanceof LivingEntity e) || e == mc.player || !e.isAlive() || e.isInvisible() || e.distanceToSqr(mc.player) > r * r) {
				continue;
			}
			Vec3 at = e.getPosition(pt).add(0, e.getBbHeight() + 0.55, 0);
			ps.pushPose();
			ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			ps.mulPose(ctx.camera().rotation());
			ps.scale(0.025f, -0.025f, 0.025f);
			Matrix4f m = ps.last().pose();
			int armour = (int) Math.round(e.getAttributeValue(Attributes.ARMOR));
			String top = "❤ " + Mth.ceil(e.getHealth()) + "/" + Mth.ceil(e.getMaxHealth()) + "   ⛨ " + armour;
			Component weak = Component.translatable(weakness(e));
			drawCentred(font, Component.literal(top), 0, 0xFFFF7070, m, buffers);
			drawCentred(font, weak, 10, 0xFF9AD0FF, m, buffers);
			ps.popPose();
		}
		buffers.endBatch();
	}

	private static void drawCentred(Font font, Component text, int y, int colour, Matrix4f m, MultiBufferSource buffers) {
		float x = -font.width(text) / 2f;
		font.drawInBatch(text, x, y, colour, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0x60000000, LightTexture.FULL_BRIGHT);
	}

	/** Under the crosshair: the best tool for the block he looks at, or the best weapon against the creature. Wrapped. */
	static void crosshairHint(GuiGraphics g, Minecraft mc) {
		HitResult hit = mc.hitResult;
		Component text = null;
		if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
			text = Component.translatable("hud.projecthero.shazam.hint_weapon", Component.translatable(weakness(e)));
		} else if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
			BlockState state = mc.level.getBlockState(bh.getBlockPos());
			text = Component.translatable("hud.projecthero.shazam.hint_tool", state.getBlock().getName(), Component.translatable(tool(state)));
		}
		if (text == null) {
			return;
		}
		List<FormattedCharSequence> lines = mc.font.split(text, Math.min(220, g.guiWidth() - 20));
		int y = g.guiHeight() / 2 + 14;
		for (FormattedCharSequence line : lines) {
			g.drawString(mc.font, line, g.guiWidth() / 2 - mc.font.width(line) / 2, y, 0xFF9AD0FF, true);
			y += 10;
		}
	}
}
