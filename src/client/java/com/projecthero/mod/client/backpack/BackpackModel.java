package com.projecthero.mod.client.backpack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * v0.17.5 backpacks: the worn backpack, in the player's body space (px; y down from the neck, +z out of the back). Two groups:
 * {@link #pack} (the bag, lid, front and side pockets and the carry handle) and {@link #straps} (the two shoulder straps, over
 * the shoulders and down the chest). The texture layout is painted by {@code scratchpad/gen_backpack_textures.js} -- keep the
 * {@code texOffs} in step with it.
 */
public final class BackpackModel {
	/** How far the bag stands out of the back (px), for anything slung over it (the Captain America shield). */
	public static final float DEPTH = 6.0f;

	public final ModelPart pack;
	public final ModelPart straps;

	public BackpackModel() {
		ModelPart root = create().bakeRoot();
		pack = root.getChild("pack");
		straps = root.getChild("straps");
	}

	private static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("pack", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4f, 1f, 2f, 8, 9, 4) // the bag
				.texOffs(0, 13).addBox(-4f, 0f, 2f, 8, 2, 4, new CubeDeformation(0.3f)) // the lid, a little proud of the bag
				.texOffs(0, 19).addBox(-3f, 5f, 6f, 6, 4, 2) // the front pocket
				.texOffs(16, 19).addBox(-1.5f, -1.4f, 3.5f, 3, 1, 1) // the carry handle
				.texOffs(24, 0).addBox(-5f, 5f, 2.5f, 1, 4, 3) // side pockets
				.texOffs(24, 7).addBox(4f, 5f, 2.5f, 1, 4, 3),
				PartPose.ZERO);
		root.addOrReplaceChild("straps", CubeListBuilder.create()
				.texOffs(32, 0).addBox(-3f, 0f, -2.6f, 1, 8, 1) // down the chest
				.texOffs(36, 0).addBox(2f, 0f, -2.6f, 1, 8, 1)
				.texOffs(40, 0).addBox(-3f, -0.6f, -2.6f, 1, 1, 5) // over the shoulders
				.texOffs(40, 6).addBox(2f, -0.6f, -2.6f, 1, 1, 5),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}
}
