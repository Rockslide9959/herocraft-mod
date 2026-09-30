package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * v0.14.7: vanilla never wraps item tooltip lines, so one long description ran right across the screen (the Heroic
 * Serum's rule line did). Every item tooltip line wider than {@link #MAX_WIDTH} is word-wrapped here, keeping its
 * colours and formatting -- one central guard, so no item can forget it. The first line (the item name) is left
 * alone.
 */
public final class TooltipWrap {
	/** Widest a tooltip line may be, in GUI pixels (about 45 characters of the default font). */
	public static final int MAX_WIDTH = 220;

	private TooltipWrap() {
	}

	public static void initialize() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			Font font = Minecraft.getInstance().font;
			if (font == null || lines.size() < 2) {
				return;
			}
			List<Component> out = null;
			for (int i = 0; i < lines.size(); i++) {
				Component line = lines.get(i);
				if (i == 0 || font.width(line) <= MAX_WIDTH) {
					if (out != null) {
						out.add(line);
					}
					continue;
				}
				if (out == null) {
					out = new ArrayList<>(lines.subList(0, i));
				}
				out.addAll(wrap(font, line));
			}
			if (out != null) {
				lines.clear();
				lines.addAll(out);
			}
		});
	}

	/** Splits {@code line} into pieces no wider than {@link #MAX_WIDTH}, each keeping the styles of its text. */
	public static List<Component> wrap(Font font, Component line) {
		List<Component> pieces = new ArrayList<>();
		for (FormattedText part : font.getSplitter().splitLines(line, MAX_WIDTH, Style.EMPTY)) {
			MutableComponent piece = Component.empty();
			part.visit((style, text) -> {
				piece.append(Component.literal(text).withStyle(style));
				return Optional.empty();
			}, Style.EMPTY);
			pieces.add(piece);
		}
		return pieces.isEmpty() ? List.of(line) : pieces;
	}
}
