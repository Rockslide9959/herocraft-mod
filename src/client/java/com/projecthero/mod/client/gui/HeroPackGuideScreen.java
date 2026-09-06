package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.hero.guide.HeroPackGuide.IndexEntry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The HeroPack Guide: a dependency-free chapter book. Left = a document-style outline (section
 * headings, category sub-headings and indented, clickable chapter links); right = the selected
 * chapter, word-wrapped and scrollable. All content comes from {@link HeroPackGuide} (which reads the
 * power registry) so it can't drift from what's implemented. Hand-rolled hit-testing rather than a
 * list widget, to stay off churny client API.
 */
public final class HeroPackGuideScreen extends Screen {
	private static final int INDEX_X = 14;
	private static final int INDEX_W = 150;
	private static final int LINE_H = 11;
	private static final int INDENT = 9;

	private int chapter = 0;
	private int indexScroll = 0;
	private int contentScroll = 0;
	private final List<FormattedCharSequence> wrapped = new ArrayList<>();
	private int contentX;
	private int top;
	private int bottom;

	public HeroPackGuideScreen() {
		super(Component.translatable("screen.projecthero.guide"));
	}

	@Override
	protected void init() {
		this.contentX = INDEX_X + INDEX_W + 16;
		this.top = 36;
		this.bottom = this.height - 30;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(INDEX_X, this.height - 24, INDEX_W, 18).build());
		rewrap();
	}

	private List<IndexEntry> index() {
		return HeroPackGuide.index();
	}

	private void rewrap() {
		wrapped.clear();
		contentScroll = 0;
		int w = Math.max(120, this.width - contentX - 16);
		for (Component line : HeroPackGuide.chapters().get(chapter).lines()) {
			var split = this.font.split(line, w);
			if (split.isEmpty()) {
				wrapped.add(FormattedCharSequence.EMPTY);
			} else {
				wrapped.addAll(split);
			}
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (mx >= INDEX_X && mx <= INDEX_X + INDEX_W && my >= top && my <= bottom) {
			int row = (int) ((my - top + indexScroll) / LINE_H);
			List<IndexEntry> index = index();
			if (row >= 0 && row < index.size()) {
				IndexEntry entry = index.get(row);
				if (!entry.isHeading()) {
					chapter = entry.chapterIndex();
					rewrap();
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		if (mx < INDEX_X + INDEX_W) {
			int max = Math.max(0, index().size() * LINE_H - (bottom - top));
			indexScroll = clamp(indexScroll - (int) (dy * 20), 0, max);
		} else {
			int max = Math.max(0, wrapped.size() * LINE_H + 20 - (bottom - top));
			contentScroll = clamp(contentScroll - (int) (dy * 20), 0, max);
		}
		return true;
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
		g.fill(INDEX_X - 4, top - 4, INDEX_X + INDEX_W + 4, bottom + 4, 0x60000000);
		g.fill(contentX - 8, top - 4, this.width - 8, bottom + 4, 0x40000000);

		// outline
		g.enableScissor(INDEX_X - 3, top, INDEX_X + INDEX_W + 3, bottom);
		List<IndexEntry> index = index();
		int hoverRow = -1;
		if (mouseX >= INDEX_X && mouseX <= INDEX_X + INDEX_W && mouseY >= top && mouseY <= bottom) {
			hoverRow = (int) ((mouseY - top + indexScroll) / LINE_H);
		}
		int iy = top - indexScroll;
		for (int i = 0; i < index.size(); i++) {
			IndexEntry entry = index.get(i);
			if (iy > top - LINE_H && iy < bottom) {
				int x = INDEX_X + entry.depth() * INDENT;
				if (entry.isHeading()) {
					if (!entry.label().getString().isEmpty()) {
						g.drawString(this.font, entry.label(), x, iy, 0xFFFFFFFF, false);
					}
				} else {
					boolean selected = entry.chapterIndex() == chapter;
					int color = selected ? 0xFFFFE08A : (i == hoverRow ? 0xFFFFFFFF : 0xFFB6B6C8);
					Component label = selected
							? Component.literal("› ").append(entry.label())
							: entry.label();
					g.drawString(this.font, label, x, iy, color, false);
				}
			}
			iy += LINE_H;
		}
		g.disableScissor();

		// content
		g.enableScissor(contentX - 6, top, this.width - 6, bottom);
		int cy = top - contentScroll;
		g.drawString(this.font, HeroPackGuide.chapters().get(chapter).title(), contentX, cy, 0xFFFFE08A);
		cy += 14;
		for (FormattedCharSequence line : wrapped) {
			if (cy > top - LINE_H && cy < bottom) {
				g.drawString(this.font, line, contentX, cy, 0xFFD8D8E4, false);
			}
			cy += LINE_H;
		}
		g.disableScissor();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
