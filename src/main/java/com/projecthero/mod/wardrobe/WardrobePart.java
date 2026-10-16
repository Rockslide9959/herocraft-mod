package com.projecthero.mod.wardrobe;

import net.minecraft.util.StringRepresentable;

/** v0.16.5: which of the Wardrobe's three columns a block is, as seen from the front (the viewer's left / right). */
public enum WardrobePart implements StringRepresentable {
	LEFT("left"),
	CENTER("center"),
	RIGHT("right");

	private final String name;

	WardrobePart(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
