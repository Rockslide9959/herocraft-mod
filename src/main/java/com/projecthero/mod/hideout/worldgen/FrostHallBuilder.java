package com.projecthero.mod.hideout.worldgen;

import net.minecraft.core.BlockPos;

/** v0.17.5: placeholder until this hideout is built. */
public final class FrostHallBuilder extends HideoutBuilder {
	public static final FrostHallBuilder INSTANCE = new FrostHallBuilder();

	private FrostHallBuilder() {
		super(-8, -2, -8, 8, 8, 8);
	}

	@Override
	public BlockPos coreLocal() {
		return new BlockPos(0, 1, 0);
	}

	@Override
	public BlockPos vaultLocal() {
		return new BlockPos(0, 1, 2);
	}

	@Override
	public void build(HideoutCanvas c) {
	}
}
