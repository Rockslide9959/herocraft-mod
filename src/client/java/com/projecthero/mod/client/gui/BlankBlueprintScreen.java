package com.projecthero.mod.client.gui;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;
import com.projecthero.mod.network.IronManBlueprintChoicePayload;
import com.projecthero.mod.network.IronManBlueprintPickerPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * "changes 21": the Blank Blueprint picker; v0.14.21 redesign as a <b>progression track</b>. Every mark that has a
 * blueprint sits on one line in progression order (Mark I -> Mark VII), shown by its helmet. The line is lit up to
 * the last unlocked mark. An unlocked mark is clickable (stamps the blank into that mark's blueprint); a locked one
 * is greyed with a padlock and its tooltip names the suit still to build. Arrow keys move along the track, Enter
 * stamps. Same {@link IronManBlueprintChoicePayload} as before; the server re-validates the pick.
 */
public final class BlankBlueprintScreen extends Screen {
	private final List<IronManBlueprintPickerPayload.Entry> entries;
	private int focused;
	private int hovered = -1;

	public BlankBlueprintScreen(List<IronManBlueprintPickerPayload.Entry> entries) {
		super(Component.translatable("screen.projecthero.blank_blueprint.title"));
		this.entries = entries;
	}

	private static Component markName(String suitId) {
		return Component.translatable("projecthero.ironman.suit." + suitId + ".name");
	}

	private int panelW() {
		return Math.min(width, 420) - 16;
	}

	private int panelH() {
		return Math.min(height - 16, 150);
	}

	private int panelX() {
		return width / 2 - panelW() / 2;
	}

	private int panelY() {
		return height / 2 - panelH() / 2;
	}

	private int trackY() {
		return panelY() + 58;
	}

	@Override
	protected void init() {
		focused = 0;
		for (int i = entries.size() - 1; i >= 0; i--) {
			if (entries.get(i).unlocked()) {
				focused = i; // start on the newest mark you can stamp
				break;
			}
		}
		int bw = 90;
		addRenderableWidget(new IronManGui.StarkButton(width / 2 - bw / 2, panelY() + panelH() - 22, bw, 16,
				Component.translatable("gui.cancel"), this::onClose));
	}

	private void pick(int i) {
		if (i < 0 || i >= entries.size() || !entries.get(i).unlocked()) {
			return;
		}
		ClientPlayNetworking.send(new IronManBlueprintChoicePayload(entries.get(i).suitId()));
		onClose();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		IronManGui.panel(g, panelX(), panelY(), panelW(), panelH()); // under the Cancel button
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int py = panelY();
		int pw = panelW();
		g.drawCenteredString(font, this.title.copy().withStyle(s -> s.withBold(true)), width / 2, py + 7, IronManGui.CYAN);
		String sub = IronManGui.fit(font, Component.translatable("screen.projecthero.blank_blueprint.subtitle"), pw - 12);
		g.drawCenteredString(font, sub, width / 2, py + 19, IronManGui.TEXT_DIM);

		int n = entries.size();
		Rect[] nodes = IronManUiLayout.trackNodes(n, width, trackY());
		hovered = -1;
		for (int i = 0; i < n; i++) {
			if (nodes[i].contains(mouseX, mouseY)) {
				hovered = i;
			}
		}
		// the connecting line: lit through every unlocked mark, dim beyond
		for (int i = 0; i + 1 < n; i++) {
			int x0 = nodes[i].x() + IronManUiLayout.NODE;
			int x1 = nodes[i + 1].x();
			boolean lit = entries.get(i + 1).unlocked();
			g.fill(x0, trackY() - 1, x1, trackY() + 1, lit ? IronManGui.CYAN_DIM : 0xFF262C36);
			if (lit) {
				g.fill(x0, trackY() - 1, x1, trackY(), IronManGui.alpha(IronManGui.CYAN, 0.6f));
			}
		}
		int labelMax = IronManUiLayout.trackLabelMaxWidth(n, width);
		for (int i = 0; i < n; i++) {
			IronManBlueprintPickerPayload.Entry e = entries.get(i);
			Rect r = nodes[i];
			boolean hot = i == hovered || (hovered < 0 && i == focused);
			boolean unlocked = e.unlocked();
			int bg = unlocked ? (hot ? 0xF0183044 : 0xF00C1622) : 0xF0080A0E;
			int border = !unlocked ? 0xFF2A3038 : hot ? IronManGui.GOLD : IronManGui.CYAN_DIM;
			g.fill(r.x(), r.y(), r.right(), r.bottom(), bg);
			g.renderOutline(r.x(), r.y(), r.w(), r.h(), border);
			if (hot && unlocked) {
				IronManGui.brackets(g, r.x() - 2, r.y() - 2, r.w() + 4, r.h() + 4, 4, IronManGui.GOLD);
			}
			ItemStack helmet = IronManGui.suitPieces(e.suitId())[0];
			g.renderFakeItem(helmet, r.x() + 4, r.y() + 4);
			if (!unlocked) {
				g.pose().pushPose();
				g.pose().translate(0, 0, 200);
				g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, 0xA0080A0E);
				padlock(g, r.right() - 8, r.bottom() - 9);
				g.pose().popPose();
			}
			String label = IronManGui.fit(font, markName(e.suitId()), labelMax);
			int ly = i % 2 == 0 ? r.bottom() + 4 : r.y() - 12;
			int col = !unlocked ? IronManGui.TEXT_MUTED : hot ? 0xFFFFFFFF : IronManGui.TEXT;
			g.drawString(font, label, r.x() + r.w() / 2 - font.width(label) / 2, ly, col, false);
		}

		// detail line for the hovered (else focused) mark
		int show = hovered >= 0 ? hovered : focused;
		if (show >= 0 && show < n) {
			IronManBlueprintPickerPayload.Entry e = entries.get(show);
			Component detail = e.unlocked()
					? Component.translatable("screen.projecthero.blank_blueprint.make", markName(e.suitId()))
					: Component.translatable("screen.projecthero.blank_blueprint.locked_short",
							e.prerequisiteSuitId() == null ? Component.literal("?") : markName(e.prerequisiteSuitId()));
			String d = IronManGui.fit(font, detail, pw - 16);
			g.drawCenteredString(font, d, width / 2, trackY() + 32, e.unlocked() ? IronManGui.GOLD : IronManGui.TEXT_DIM);
		}
		if (hovered >= 0) {
			IronManBlueprintPickerPayload.Entry e = entries.get(hovered);
			if (!e.unlocked()) {
				setTooltipForNextRenderPass(Component.translatable("screen.projecthero.blank_blueprint.locked",
						markName(e.suitId()), e.prerequisiteSuitId() == null ? Component.literal("?") : markName(e.prerequisiteSuitId())));
			}
		}
	}

	/** A tiny 7 x 8 padlock glyph. */
	private static void padlock(GuiGraphics g, int x, int y) {
		int c = 0xFF8A93A8;
		g.fill(x + 1, y, x + 6, y + 1, c);
		g.fill(x + 1, y, x + 2, y + 4, c);
		g.fill(x + 5, y, x + 6, y + 4, c);
		g.fill(x, y + 3, x + 7, y + 8, c);
		g.fill(x + 3, y + 5, x + 4, y + 7, 0xFF20242C);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && hovered >= 0) {
			pick(hovered);
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_LEFT) {
			if (!entries.isEmpty()) {
				focused = Math.max(0, Math.min(entries.size() - 1, focused + (key == GLFW.GLFW_KEY_RIGHT ? 1 : -1)));
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			pick(focused);
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
