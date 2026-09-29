package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix4f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.network.GreenLanternConstructSelectPayload;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Green Lantern construct wheel -- v0.14.3 redesign. A real ring of light: every construct is a wedge of a donut
 * with its own icon, grouped into four coloured arcs (Attack / Defence / Mobility / Utility) named round the rim. The
 * hovered wedge lifts out and brightens; the one already selected carries a bright edge; the centre shows the hovered
 * construct's name, group, cost, any cooldown and what it does. Opens with a quick grow-in.
 *
 * <p>Opened by holding C for {@code GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS} (see
 * {@code ProjectHeroModClient#handleGreenLanternConstructWheelHold}). Point at a wedge and click it or release C to
 * select; release over the centre (or Escape) cancels. This only changes which construct is selected -- a tap of C
 * deploys it.
 */
public final class GreenLanternConstructWheelScreen extends Screen {
	private static final int[] CATEGORY_COLOUR = {0xFF8CFF4F, 0xFF35F0C8, 0xFF3FC8FF, 0xFFD8F035};

	private final List<ConstructType> entries = new ArrayList<>();
	private int hovered = -1;
	private int selectedOrdinal = -1;
	private long openedAt;

	public GreenLanternConstructWheelScreen() {
		super(Component.translatable("screen.projecthero.green_lantern.construct_wheel"));
	}

	@Override
	protected void init() {
		entries.clear();
		Minecraft mc = Minecraft.getInstance();
		GreenLanternState state = mc.player == null ? null
				: mc.player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		selectedOrdinal = state != null ? state.selectedConstruct : -1;
		if (state != null) {
			entries.addAll(List.of(ConstructType.values()));
			entries.sort(Comparator.comparingInt((ConstructType t) -> t.category().ordinal()).thenComparingInt(Enum::ordinal));
		}
		openedAt = System.currentTimeMillis();
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (com.projecthero.mod.client.ModKeyBindings.ABILITY_6.matches(keyCode, scanCode)) {
			confirmAndClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// a soft vignette only -- the world stays visible behind the ring
		g.fillGradient(0, 0, this.width, this.height, 0x60000000, 0x90000000);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int n = entries.size();
		if (n == 0) {
			return;
		}
		float cx = this.width / 2f;
		// the ring sits a little above the middle, leaving room for the title above and the description below
		float cy = this.height / 2f - 8f;
		float outer = Math.max(60f, Math.min(128f, Math.min(this.width * 0.36f, (this.height - 92) / 2f)));
		float inner = outer * 0.54f;
		float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 140f);
		float ease = 1f - (1f - open) * (1f - open);

		double seg = Math.PI * 2 / n;
		double start = -Math.PI / 2 - seg / 2;
		double dist = Math.hypot(mouseX - cx, mouseY - cy);
		hovered = -1;
		if (dist > inner * 0.55f) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx) - start;
			ang = ((ang % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
			hovered = Math.min(n - 1, (int) (ang / seg));
		}

		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().scale(0.85f + 0.15f * ease, 0.85f + 0.15f * ease, 1f);
		g.pose().translate(-cx, -cy, 0);

		// ---- wedges
		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		double pad = 0.018;
		for (int i = 0; i < n; i++) {
			ConstructType type = entries.get(i);
			double a0 = start + i * seg + pad;
			double a1 = start + (i + 1) * seg - pad;
			boolean hov = i == hovered;
			boolean sel = type.ordinal() == selectedOrdinal;
			float lift = hov ? 6f : 0f;
			int fill = hov ? 0xE8155F2C : 0xC0081A0E;
			sector(vc, m, cx, cy, inner + lift, outer + lift, a0, a1, fill);
			// the category band on the inner edge
			sector(vc, m, cx, cy, inner + lift, inner + lift + 3f, a0, a1, withAlpha(CATEGORY_COLOUR[type.category().ordinal()], hov ? 0xFF : 0xB0));
			if (sel) {
				sector(vc, m, cx, cy, outer + lift - 2.5f, outer + lift, a0, a1, 0xFF35F075);
			} else if (hov) {
				sector(vc, m, cx, cy, outer + lift - 1.5f, outer + lift, a0, a1, 0xFFA8FFC0);
			}
		}
		// ---- the centre disc and its rim
		sector(vc, m, cx, cy, 0f, inner - 8f, 0, Math.PI * 2, 0xE6050F08);
		sector(vc, m, cx, cy, inner - 10f, inner - 8f, 0, Math.PI * 2, 0xFF1E661E);
		g.flush();

		// ---- icons
		for (int i = 0; i < n; i++) {
			ConstructType type = entries.get(i);
			double mid = start + (i + 0.5) * seg;
			boolean hov = i == hovered;
			float r = (inner + outer) / 2f + (hov ? 6f : 0f);
			int size = hov ? 24 : 18;
			int ix = Math.round(cx + (float) Math.cos(mid) * r) - size / 2;
			int iy = Math.round(cy + (float) Math.sin(mid) * r) - size / 2;
			GreenLanternHud.icon(g, type.ordinal(), ix, iy, size);
			if (GreenLanternConstructs.cooldownRemainingFor(this.minecraft.player, type) > 0) {
				g.fill(ix, iy + size - 3, ix + size, iy + size - 1, 0xC0FF5A5A);
			}
		}

		// ---- category names round the rim, at the middle of each group
		for (ConstructType.Category c : ConstructType.Category.values()) {
			int first = -1;
			int last = -1;
			for (int i = 0; i < n; i++) {
				if (entries.get(i).category() == c) {
					first = first < 0 ? i : first;
					last = i;
				}
			}
			if (first < 0) {
				continue;
			}
			double mid = start + (first + last + 1) / 2.0 * seg;
			Component label = Component.translatable("screen.projecthero.green_lantern.category." + c.name().toLowerCase(java.util.Locale.ROOT))
					.withStyle(ChatFormatting.BOLD);
			float lr = outer + 16f;
			int lx = Math.round(cx + (float) Math.cos(mid) * lr);
			int ly = Math.round(cy + (float) Math.sin(mid) * lr) - 4;
			int w = this.font.width(label);
			int tx = Math.cos(mid) > 0.3 ? lx : Math.cos(mid) < -0.3 ? lx - w : lx - w / 2;
			g.drawString(this.font, label, tx, ly, CATEGORY_COLOUR[c.ordinal()], true);
		}

		// ---- the centre: what the hovered construct is
		int textW = Math.round((inner - 14f) * 2f);
		List<FormattedCharSequence> descLines = List.of();
		if (hovered >= 0) {
			ConstructType type = entries.get(hovered);
			List<FormattedCharSequence> lines = new ArrayList<>(
					this.font.split(Component.translatable(type.translationKey()).withStyle(ChatFormatting.BOLD), textW));
			Component group = Component.translatable("screen.projecthero.green_lantern.category."
					+ type.category().name().toLowerCase(java.util.Locale.ROOT));
			int cd = GreenLanternConstructs.cooldownRemainingFor(this.minecraft.player, type);
			Component info = cd > 0
					? Component.translatable("hud.projecthero.green_lantern.cooldown_short", (cd + 19) / 20).withStyle(ChatFormatting.RED)
					: Component.translatable("hud.projecthero.green_lantern.cost", Math.round(type.initialCost())).withStyle(ChatFormatting.GRAY);
			int block = 26 + lines.size() * 9 + 20;
			int y = Math.round(cy) - block / 2;
			GreenLanternHud.icon(g, type.ordinal(), Math.round(cx) - 12, y, 24);
			y += 26;
			for (FormattedCharSequence l : lines) {
				g.drawString(this.font, l, Math.round(cx) - this.font.width(l) / 2, y, 0xFF35F075, true);
				y += 9;
			}
			g.drawString(this.font, group, Math.round(cx) - this.font.width(group) / 2, y + 1,
					CATEGORY_COLOUR[type.category().ordinal()], true);
			g.drawString(this.font, info, Math.round(cx) - this.font.width(info) / 2, y + 11, 0xFFFFFFFF, true);
			descLines = this.font.split(Component.translatable(type.descriptionKey()), Math.min(this.width - 24, 320));
		} else {
			GreenLanternHud.icon(g, GreenLanternHud.EMBLEM_ICON, Math.round(cx) - 16, Math.round(cy) - 22, 32);
			Component c = Component.translatable("screen.projecthero.green_lantern.construct_wheel.cancel").withStyle(ChatFormatting.GRAY);
			g.drawString(this.font, c, Math.round(cx) - this.font.width(c) / 2, Math.round(cy) + 14, 0xFFFFFFFF, true);
		}
		g.pose().popPose();

		Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
		g.drawString(this.font, title, Math.round(cx) - this.font.width(title) / 2, Math.max(4, Math.round(cy - outer - 30)), 0xFF35F075, true);
		// what the hovered construct does, under the ring (two lines at most); the hint when nothing is hovered
		int dy = Math.round(cy + outer + 22);
		for (int k = 0; k < Math.min(2, descLines.size()); k++) {
			FormattedCharSequence l = descLines.get(k);
			g.drawString(this.font, l, Math.round(cx) - this.font.width(l) / 2, dy, 0xFFE0F8E8, true);
			dy += 10;
		}
		if (descLines.isEmpty()) {
			Component hint = Component.translatable("screen.projecthero.green_lantern.construct_wheel.hint");
			g.drawString(this.font, hint, Math.round(cx) - this.font.width(hint) / 2, dy, 0xFF9AD8B0, true);
		}
	}

	private static int withAlpha(int argb, int alpha) {
		return (alpha << 24) | (argb & 0xFFFFFF);
	}

	/** An annular sector (a donut wedge) from angle {@code a0} to {@code a1}, radii {@code r0..r1}, as GUI quads. */
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

	/** Called when the C key is released: pick the hovered wedge, or cancel. */
	public void confirmAndClose() {
		if (hovered >= 0 && hovered < entries.size()) {
			ClientPlayNetworking.send(new GreenLanternConstructSelectPayload(entries.get(hovered).ordinal()));
		}
		onClose();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < entries.size()) {
			ClientPlayNetworking.send(new GreenLanternConstructSelectPayload(entries.get(hovered).ordinal()));
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
