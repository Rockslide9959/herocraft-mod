package com.projecthero.mod.client.miraculous.renarouge;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.miraculous.renarouge.MirageIllusion;
import com.projecthero.mod.miraculous.renarouge.network.RenaMirageChoicePayload;
import com.projecthero.mod.miraculous.renarouge.network.RenaMirageMenuPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.16.7 (user), Shift+Z: the Mirage wheel -- Rena Rouge's eight illusions as the wedges of an orange ring, in the
 * style of the power / construct wheels. Point at one (mouse direction) and click it, or release Z over it; the centre
 * shows the hovered illusion's icon and name, the line under the ring what it does (wrapped). Escape, or closing with
 * nothing picked, spends nothing (the server only starts the cooldown on a pick). Opened by the server's
 * {@link RenaMirageMenuPayload}; the pick goes back as a {@link RenaMirageChoicePayload}.
 */
public final class RenaMirageWheelScreen extends Screen {
	private static final int ORANGE = 0xFFFF9A3C;
	private static final int ORANGE_DIM = 0xFFB35A1E;
	private static final int CREAM = 0xFFFFF0DC;

	private final MirageIllusion[] entries = MirageIllusion.values();
	private int hovered = -1;
	private long openedAt;
	private boolean sent;

	public RenaMirageWheelScreen() {
		super(Component.translatable("screen.projecthero.rena_mirage.title"));
	}

	static void receive(RenaMirageMenuPayload payload) {
		Minecraft.getInstance().setScreen(new RenaMirageWheelScreen());
	}

	/** The wheel icon of each illusion (shared with the guide text). */
	public static ItemStack icon(MirageIllusion m) {
		return new ItemStack(switch (m) {
			case FOXES -> Items.FOX_SPAWN_EGG;
			case MONSTER -> Items.DRAGON_HEAD;
			case VANISH -> Items.PHANTOM_MEMBRANE;
			case ZONE -> Items.FIRE_CHARGE;
			case LURE -> Items.GOLD_INGOT;
			case PITFALL -> Items.IRON_SHOVEL;
			case ECLIPSE -> Items.ENDER_EYE;
			case MIRRORS -> Items.GLASS;
		});
	}

	@Override
	protected void init() {
		openedAt = System.currentTimeMillis();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fillGradient(0, 0, this.width, this.height, 0x50000000, 0x90000000);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int n = entries.length;
		float cx = this.width / 2f;
		float cy = this.height / 2f - 8f;
		float outer = Math.max(60f, Math.min(120f, Math.min(this.width * 0.34f, (this.height - 96) / 2f)));
		float inner = outer * 0.52f;
		float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 140f);
		float ease = 1f - (1f - open) * (1f - open);
		double seg = Math.PI * 2 / n;
		double start = -Math.PI / 2 - seg / 2;
		double dist = Math.hypot(mouseX - cx, mouseY - cy);
		hovered = -1;
		if (dist > inner * 0.5f) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx) - start;
			ang = ((ang % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
			hovered = Math.min(n - 1, (int) (ang / seg));
		}

		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().scale(0.85f + 0.15f * ease, 0.85f + 0.15f * ease, 1f);
		g.pose().translate(-cx, -cy, 0);

		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		double pad = 0.02;
		for (int i = 0; i < n; i++) {
			double a0 = start + i * seg + pad;
			double a1 = start + (i + 1) * seg - pad;
			boolean hov = i == hovered;
			float lift = hov ? 6f : 0f;
			sector(vc, m, cx, cy, inner + lift, outer + lift, a0, a1, hov ? 0xE8803A10 : 0xC0301408);
			sector(vc, m, cx, cy, inner + lift, inner + lift + 3f, a0, a1, hov ? ORANGE : 0xB0B35A1E);
			if (hov) {
				sector(vc, m, cx, cy, outer + lift - 2f, outer + lift, a0, a1, 0xFFFFD08A);
			}
		}
		sector(vc, m, cx, cy, 0f, inner - 8f, 0, Math.PI * 2, 0xE6140804);
		g.flush();

		for (int i = 0; i < n; i++) {
			double mid = start + (i + 0.5) * seg;
			boolean hov = i == hovered;
			float r = (inner + outer) / 2f + (hov ? 6f : 0f);
			float s = hov ? 1.6f : 1.25f;
			float ix = cx + (float) Math.cos(mid) * r;
			float iy = cy + (float) Math.sin(mid) * r;
			g.pose().pushPose();
			g.pose().translate(ix, iy, 0);
			g.pose().scale(s, s, 1f);
			g.renderItem(icon(entries[i]), -8, -8);
			g.pose().popPose();
		}

		int textW = Math.round((inner - 14f) * 2f);
		List<FormattedCharSequence> hint = List.of();
		if (hovered >= 0) {
			MirageIllusion e = entries[hovered];
			List<FormattedCharSequence> name = this.font.split(Component.translatable("screen.projecthero.rena_mirage." + e.id).withStyle(ChatFormatting.BOLD),
					textW);
			int block = 22 + name.size() * 9;
			int y = Math.round(cy) - block / 2;
			g.pose().pushPose();
			g.pose().translate(cx, y + 9, 0);
			g.pose().scale(1.2f, 1.2f, 1f);
			g.renderItem(icon(e), -8, -8);
			g.pose().popPose();
			y += 22;
			for (FormattedCharSequence l : name) {
				g.drawString(this.font, l, Math.round(cx) - this.font.width(l) / 2, y, ORANGE, true);
				y += 9;
			}
			hint = this.font.split(Component.translatable("screen.projecthero.rena_mirage." + e.id + ".hint"), Math.min(this.width - 24, 300));
		} else {
			Component c = Component.translatable("screen.projecthero.rena_mirage.pick").withStyle(ChatFormatting.GRAY);
			List<FormattedCharSequence> lines = this.font.split(c, textW);
			int y = Math.round(cy) - lines.size() * 5;
			for (FormattedCharSequence l : lines) {
				g.drawString(this.font, l, Math.round(cx) - this.font.width(l) / 2, y, CREAM, true);
				y += 10;
			}
		}
		g.pose().popPose();

		Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
		g.drawString(this.font, title, Math.round(cx) - this.font.width(title) / 2, Math.max(4, Math.round(cy - outer - 28)), ORANGE, true);
		int dy = Math.round(cy + outer + 20);
		if (hint.isEmpty()) {
			hint = this.font.split(Component.translatable("screen.projecthero.rena_mirage.hint"), Math.min(this.width - 24, 300));
		}
		for (int k = 0; k < Math.min(3, hint.size()); k++) {
			FormattedCharSequence l = hint.get(k);
			g.drawString(this.font, l, Math.round(cx) - this.font.width(l) / 2, dy, k == 0 && hovered >= 0 ? CREAM : 0xFFD8B898, true);
			dy += 10;
		}
		if (hovered < 0) {
			return;
		}
		// a thin orange underline under the hint
		g.fill(Math.round(cx) - 30, dy + 2, Math.round(cx) + 30, dy + 3, ORANGE_DIM);
	}

	private static void sector(VertexConsumer vc, Matrix4f m, float cx, float cy, float r0, float r1, double a0, double a1, int argb) {
		int steps = Math.max(2, (int) Math.ceil((a1 - a0) / (Math.PI / 40)));
		for (int k = 0; k < steps; k++) {
			double t0 = a0 + (a1 - a0) * k / steps;
			double t1 = a0 + (a1 - a0) * (k + 1) / steps;
			float c0 = (float) Math.cos(t0);
			float s0 = (float) Math.sin(t0);
			float c1 = (float) Math.cos(t1);
			float s1 = (float) Math.sin(t1);
			vc.addVertex(m, cx + c0 * r1, cy + s0 * r1, 0).setColor(argb);
			vc.addVertex(m, cx + c0 * r0, cy + s0 * r0, 0).setColor(argb);
			vc.addVertex(m, cx + c1 * r0, cy + s1 * r0, 0).setColor(argb);
			vc.addVertex(m, cx + c1 * r1, cy + s1 * r1, 0).setColor(argb);
		}
	}

	private void pick(int index) {
		if (!sent) {
			sent = true;
			ClientPlayNetworking.send(new RenaMirageChoicePayload(index));
		}
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		// releasing Z over a wedge picks it; over the middle the wheel stays open (a quick tap opens it for the mouse)
		if (com.projecthero.mod.client.ModKeyBindings.ABILITY_4.matches(keyCode, scanCode) && hovered >= 0
				&& System.currentTimeMillis() - openedAt > 150) {
			pick(hovered);
			onClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0) {
			pick(hovered);
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void onClose() {
		pick(-1); // nothing picked: nothing spent
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
