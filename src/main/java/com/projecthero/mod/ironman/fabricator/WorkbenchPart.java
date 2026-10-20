package com.projecthero.mod.ironman.fabricator;

import net.minecraft.util.StringRepresentable;

/**
 * v0.16.7 (user): which half of the two-block Stark Workbench a {@code stark_fabricator} block is.
 * <ul>
 *   <li>{@link #SINGLE} -- the default state, so every one-block Stark Fabricator from an older world loads as this. It is a
 *       complete, working one-block desk (its own compact model) and quietly claims the block to its right as soon as that
 *       block is plain air, turning into a {@link #MAIN} + {@link #SIDE} pair. It never replaces anything that is there.</li>
 *   <li>{@link #MAIN} -- the half the player clicked; it owns the block entity (inventory, energy, the job).</li>
 *   <li>{@link #SIDE} -- the half to the placer's right; no block entity, forwards every interaction to the main half.</li>
 * </ul>
 */
public enum WorkbenchPart implements StringRepresentable {
	SINGLE("single"),
	MAIN("main"),
	SIDE("side");

	private final String name;

	WorkbenchPart(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	@Override
	public String toString() {
		return name;
	}
}
