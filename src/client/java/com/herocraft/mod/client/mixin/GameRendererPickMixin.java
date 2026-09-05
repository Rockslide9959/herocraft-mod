package com.herocraft.mod.client.mixin;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.spider.data.SpiderManState;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spider-Man hits mobs through webbing (v0.6.22). Cobweb renders as a full-block outline, so the
 * crosshair pick stops on it and a mob standing just behind a strand of web -- his own Web Net, or a
 * mineshaft's cobweb -- cannot be targeted in melee. For a Spider-Man player only, when the pick lands
 * on a cobweb block this re-runs the entity trace, ignoring cobweb but still stopping at the first
 * solid block, so anything on the far side of the web becomes hittable.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickMixin {
	@Inject(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
			at = @At("RETURN"), cancellable = true)
	private void herocraft$spiderHitsThroughWebs(Entity entity, double blockRange, double entityRange,
			float partialTicks, CallbackInfoReturnable<HitResult> cir) {
		if (!(entity instanceof LocalPlayer player)) {
			return;
		}
		SpiderManState state = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (state == null || !state.hasPower) {
			return;
		}
		HitResult result = cir.getReturnValue();
		if (!(result instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK
				|| !player.level().getBlockState(block.getBlockPos()).is(Blocks.COBWEB)) {
			return;
		}

		Vec3 eye = entity.getEyePosition(partialTicks);
		Vec3 view = entity.getViewVector(partialTicks);
		Vec3 farEnd = eye.add(view.x * entityRange, view.y * entityRange, view.z * entityRange);

		// Walk the block ray, skipping cobweb, to find how far a hit could actually reach.
		double reach = entityRange;
		Vec3 probe = eye;
		for (int i = 0; i < 12; i++) {
			BlockHitResult hit = player.level().clip(new ClipContext(probe, farEnd,
					ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
			if (hit.getType() == HitResult.Type.MISS) {
				break;
			}
			if (!player.level().getBlockState(hit.getBlockPos()).is(Blocks.COBWEB)) {
				reach = Math.min(entityRange, eye.distanceTo(hit.getLocation()));
				break;
			}
			probe = hit.getLocation().add(view.scale(0.01));
		}

		Vec3 end = eye.add(view.x * reach, view.y * reach, view.z * reach);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(entity, eye, end,
				entity.getBoundingBox().expandTowards(view.scale(reach)).inflate(1.0),
				e -> !e.isSpectator() && e.isPickable(), reach * reach);
		if (entityHit != null) {
			cir.setReturnValue(entityHit);
		}
	}
}
