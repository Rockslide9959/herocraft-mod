package com.projecthero.mod.hideout.worldgen;

import com.projecthero.mod.hideout.HideoutFaction;

import net.minecraft.core.BlockPos;

/**
 * v0.17.5: one villain hideout's layout. Draws the whole place on a {@link HideoutCanvas} in local coordinates
 * (entrance to local north); the canvas rotates and clips. {@link #minX}..{@link #maxZ} bound everything it draws
 * (the piece's bounding box comes from them).
 */
public abstract class HideoutBuilder {
	public final int minX;
	public final int minY;
	public final int minZ;
	public final int maxX;
	public final int maxY;
	public final int maxZ;

	protected HideoutBuilder(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxY = maxY;
		this.maxZ = maxZ;
	}

	/** Where local y = 0 sits for a hideout whose generator surface height is {@code surfaceY}. */
	public int floorY(int surfaceY) {
		return surfaceY;
	}

	/** Draws everything. */
	public abstract void build(HideoutCanvas c);

	/** Local position of the core block (the gametests and the harness read it). */
	public abstract BlockPos coreLocal();

	/** Local position of the sealed vault. */
	public abstract BlockPos vaultLocal();

	public static HideoutBuilder of(HideoutFaction faction) {
		return switch (faction) {
			case KINGPIN -> KingpinFrontBuilder.INSTANCE;
			case ULTRON -> UltronForgeBuilder.INSTANCE;
			case APOKOLIPS -> ApokolipsOutpostBuilder.INSTANCE;
			case SYMBIOTE -> SymbioteHiveBuilder.INSTANCE;
			case FROST -> FrostHallBuilder.INSTANCE;
			case GRAVE -> GraveboundCryptBuilder.INSTANCE;
			case CARNAGE -> CarnageNestBuilder.INSTANCE;
			case OATH -> OathbreakerKeepBuilder.INSTANCE;
		};
	}
}
