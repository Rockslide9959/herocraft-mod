package com.projecthero.mod.client.loot.finds;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.finds.SupplyDropEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.5: a falling Supply Drop -- the Hero Vault crate (its real block model, so the tier trim shows) hanging from
 * four cords under a domed red-and-white canopy, swaying a little. Nothing is drawn once it has landed (the vault block
 * is in the world then).
 */
public class SupplyDropRenderer extends EntityRenderer<SupplyDropEntity> {
	private static final float CANOPY_Y = 3.4f;
	private static final float CANOPY_R = 1.9f;

	public SupplyDropRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.6f;
	}

	@Override
	public void render(SupplyDropEntity crate, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		if (crate.landed()) {
			return;
		}
		BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
		float age = crate.tickCount + partial;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partial, crate.yRotO, crate.getYRot())));
		// the swing: the whole rig pivots about the canopy
		pose.translate(0, CANOPY_Y, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(5f * Mth.sin(age * 0.07f)));
		pose.mulPose(Axis.XP.rotationDegrees(3f * Mth.sin(age * 0.05f + 1f)));
		pose.translate(0, -CANOPY_Y, 0);

		// the crate
		BlockState vault = HeroVault.state(crate.tier(), crate.facing());
		pose.pushPose();
		pose.translate(-0.5, 0, -0.5);
		blocks.renderSingleBlock(vault, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();

		// the canopy: a 4x4 grid of thin wool panels, the outer ring lower, red and white quarters
		int n = 4;
		float cell = CANOPY_R * 2 / n;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				float x0 = -CANOPY_R + i * cell;
				float z0 = -CANOPY_R + j * cell;
				boolean outer = i == 0 || j == 0 || i == n - 1 || j == n - 1;
				BlockState wool = ((i + j) % 2 == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL).defaultBlockState();
				pose.pushPose();
				pose.translate(x0, CANOPY_Y + (outer ? -0.35f : 0f), z0);
				pose.scale(cell, 0.18f, cell);
				blocks.renderSingleBlock(wool, pose, buffers, light, OverlayTexture.NO_OVERLAY);
				pose.popPose();
			}
		}

		// four cords from the crate's top corners to the canopy's edge
		BlockState cord = Blocks.WHITE_WOOL.defaultBlockState();
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				Vector3f from = new Vector3f(sx * 0.45f, 0.95f, sz * 0.45f);
				Vector3f to = new Vector3f(sx * (CANOPY_R - 0.25f), CANOPY_Y - 0.35f, sz * (CANOPY_R - 0.25f));
				Vector3f d = new Vector3f(to).sub(from);
				float len = d.length();
				pose.pushPose();
				pose.translate(from.x, from.y, from.z);
				pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d.normalize()));
				pose.scale(0.04f, len, 0.04f);
				pose.translate(-0.5, 0, -0.5);
				blocks.renderSingleBlock(cord, pose, buffers, light, OverlayTexture.NO_OVERLAY);
				pose.popPose();
			}
		}
		pose.popPose();
		super.render(crate, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SupplyDropEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
