package com.projecthero.mod.client.alarm;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.alarm.block.LaserTripwireBlock;
import com.projecthero.mod.alarm.block.LaserTripwireBlockEntity;
import com.projecthero.mod.alarm.block.SensorBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** v0.17.7: a live Laser Tripwire's red beam, exactly where the server checks it ({@link LaserTripwireBlockEntity}). */
public class LaserTripwireRenderer implements BlockEntityRenderer<LaserTripwireBlockEntity> {
	public LaserTripwireRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(LaserTripwireBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		BlockState s = be.getBlockState();
		if (be.getLevel() == null || !s.hasProperty(SensorBlockEntity.LIVE) || !s.getValue(SensorBlockEntity.LIVE)) {
			return;
		}
		Direction d = s.getValue(LaserTripwireBlock.FACING);
		BlockPos pos = be.getBlockPos();
		int len = LaserTripwireBlockEntity.length(be.getLevel(), pos, d);
		if (len <= 0) {
			return;
		}
		Vec3 base = Vec3.atLowerCornerOf(pos);
		Vec3 a = LaserTripwireBlockEntity.start(pos, d).subtract(base);
		Vec3 b = LaserTripwireBlockEntity.end(pos, d, len).subtract(base);
		float flicker = 0.75f + 0.25f * (float) Math.sin((be.getLevel().getGameTime() + partial) * 0.9 + pos.asLong() % 7);
		AlarmWorldRender.beam(pose.last().pose(), buffers.getBuffer(RenderType.lightning()), a, b, 0.018f, 1f, 0.06f, 0.04f, 0.85f * flicker);
		AlarmWorldRender.beam(pose.last().pose(), buffers.getBuffer(RenderType.lightning()), a, b, 0.05f, 1f, 0.1f, 0.05f, 0.18f * flicker);
	}

	@Override
	public boolean shouldRenderOffScreen(LaserTripwireBlockEntity be) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 64;
	}
}
