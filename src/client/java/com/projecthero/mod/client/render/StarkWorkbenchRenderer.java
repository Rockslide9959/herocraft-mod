package com.projecthero.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.render.workbench.WorkbenchHolo;
import com.projecthero.mod.client.render.workbench.WorkbenchHoloRenderType;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlock;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlockEntity;
import com.projecthero.mod.ironman.fabricator.StarkWorkbench;
import com.projecthero.mod.ironman.fabricator.WorkbenchPart;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.16.7 (user): the Stark Workbench's holograms (replaces the v0.14.21 GeckoLib Fabricator rig). Over the projector disc
 * in the middle of the two-block desk: a cyan scan-lined hologram of the suit being worked on (the blueprint in the
 * slot), slowly turning -- the pieces the viewer has already built drawn bright, the rest a dim wireframe, the piece being
 * fabricated printed bright from the bottom up to the REAL progress; a ring turning round it at chest height; two
 * floating screens (stat bars left; the selected piece's recipe slots + the progress bar right). Idle (no blueprint): a
 * slowly turning arc-reactor hologram.
 *
 * <p>Cheap by construction: drawn only within {@value #RANGE} blocks (and fading out towards that), one pre-built mesh
 * ({@link WorkbenchHolo}), no per-frame allocation (the scratch arrays below are reused -- render calls are on the render
 * thread only), additive + full-bright + depth-tested without depth writes ({@link WorkbenchHoloRenderType}), so it never
 * z-fights and walls in front still hide it.
 */
public class StarkWorkbenchRenderer implements BlockEntityRenderer<StarkFabricatorBlockEntity> {
	public static final int RANGE = 12;
	private static final float S = 1f / 16f;
	private static final float FIGURE_SCALE = 0.62f;
	private static final float FIGURE_BASE = 1.6f; // px above the lens top

	private final boolean[] built = new boolean[4];
	private static final float[] NO_BARS = new float[4];

	@Override
	public void render(StarkFabricatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		Minecraft mc = Minecraft.getInstance();
		if (be.getLevel() == null || mc.gameRenderer == null) {
			return;
		}
		BlockState state = be.getBlockState();
		if (!state.hasProperty(StarkFabricatorBlock.FACING)) {
			return;
		}
		Vec3 centre = StarkFabricatorBlock.deskCentre(state, be.getBlockPos());
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		double dist = cam.distanceTo(centre);
		if (dist > RANGE) {
			return;
		}
		// subtle at a distance: full strength within 5 blocks, fading to a third at the edge of the range
		float k = (float) Math.max(0.33, Math.min(1.0, 1.0 - (dist - 5.0) / (RANGE - 5.0) * 0.67));
		float time = (be.getLevel().getGameTime() % 24000L) + partialTick;
		Direction facing = state.getValue(StarkFabricatorBlock.FACING);
		boolean single = state.getValue(StarkFabricatorBlock.PART) == WorkbenchPart.SINGLE;

		VertexConsumer vc = buffers.getBuffer(WorkbenchHoloRenderType.HOLO);
		pose.pushPose();
		pose.translate(centre.x - be.getBlockPos().getX(), centre.y - be.getBlockPos().getY(), centre.z - be.getBlockPos().getZ());
		pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot())); // local +Z = the desk's front, +X = the viewer's right
		pose.scale(S, S, S);

		String suitId = be.holoSuit();
		if (suitId == null || IronManSuits.byId(suitId) == null) {
			drawIdle(vc, pose, time, k);
		} else {
			drawJob(vc, pose, be, suitId, time, k, single);
		}
		pose.popPose();
	}

	private void drawIdle(VertexConsumer vc, PoseStack pose, float time, float k) {
		Matrix4f m = pose.last().pose();
		WorkbenchHolo.beam(vc, m, 2.2f, 3.0f, 6f, k * 0.7f);
		WorkbenchHolo.ring(vc, m, time * 0.02f, 7.5f, 9f, k * 0.6f);
		pose.pushPose();
		pose.translate(0, 9.5f, 0);
		pose.mulPose(Axis.YP.rotation(time * 0.025f));
		WorkbenchHolo.reactor(vc, pose.last().pose(), time * 0.04f, k);
		pose.popPose();
	}

	private void drawJob(VertexConsumer vc, PoseStack pose, StarkFabricatorBlockEntity be, String suitId, float time, float k, boolean single) {
		TonyStarkState s = StarkWorkbench.state(Minecraft.getInstance().player);
		for (int p = 0; p < 4; p++) {
			built[p] = StarkWorkbench.pieceBuilt(s, suitId, p + 1);
		}
		// the real job progress, run on between the 2 s syncs while the block says it is working
		float prog = 0f;
		int max = be.maxProgress();
		boolean working = be.getBlockState().getValue(StarkFabricatorBlock.WORKING);
		if (max > 0) {
			float ticks = be.progress() + (working ? Math.max(0, be.getLevel().getGameTime() - be.syncedAt()) : 0);
			prog = Math.min(1f, ticks / max);
		}
		int building = max > 0 && be.selectedPiece() >= 1 ? be.selectedPiece() - 1 : -1;

		Matrix4f m = pose.last().pose();
		WorkbenchHolo.beam(vc, m, 2.2f, 4.0f, FIGURE_BASE + 2f, k);
		float chest = FIGURE_BASE + 19f * FIGURE_SCALE;
		WorkbenchHolo.ring(vc, m, time * 0.02f, 9.5f, chest, k * 0.85f);

		pose.pushPose();
		pose.translate(0, FIGURE_BASE, 0);
		pose.mulPose(Axis.YP.rotation(time * 0.02f));
		pose.scale(FIGURE_SCALE, FIGURE_SCALE, FIGURE_SCALE);
		float sweep = (time * 0.35f) % 44f - 6f;
		WorkbenchHolo.suit(vc, pose.last().pose(), built, building, prog, k, sweep);
		pose.popPose();

		// the floating screens, tilted towards the user (smaller and closer in on a one-block desk)
		float off = single ? 9.5f : 12.5f;
		float w = single ? 9f : 12f;
		float h = single ? 6f : 8f;
		float y = single ? 7f : 7.5f;
		float[] bars = IronManSuits.byId(suitId) != null ? StarkWorkbench.statBars(IronManSuits.byId(suitId)) : NO_BARS;
		pose.pushPose();
		pose.translate(-off, y, 2.5f);
		pose.mulPose(Axis.YP.rotationDegrees(28f));
		pose.translate(-w / 2f, 0, 0);
		WorkbenchHolo.statsScreen(vc, pose.last().pose(), w, h, bars, k * 0.9f);
		pose.popPose();

		FabricationRecipe recipe = StarkWorkbench.recipe(suitId, be.selectedPiece());
		int slots = recipe == null ? 0 : be.slotCount();
		pose.pushPose();
		pose.translate(off, y, 2.5f);
		pose.mulPose(Axis.YP.rotationDegrees(-28f));
		pose.translate(-w / 2f, 0, 0);
		WorkbenchHolo.recipeScreen(vc, pose.last().pose(), w, h, slots, be.slotMask(), prog, k * 0.9f);
		pose.popPose();
	}

	@Override
	public int getViewDistance() {
		return RANGE;
	}

	@Override
	public boolean shouldRenderOffScreen(StarkFabricatorBlockEntity be) {
		return true; // the hologram stands well above its block, so it must not vanish when the desk block is just off screen
	}
}
