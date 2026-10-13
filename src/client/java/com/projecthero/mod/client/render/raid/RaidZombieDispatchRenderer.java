package com.projecthero.mod.client.render.raid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.RaidZombieRenderer;
import com.projecthero.mod.event.entity.RaidZombie;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the {@code raid_zombie} type covers three variants. The basic and baby Risen look exactly like a vanilla zombie /
 * baby zombie (the vanilla zombie model and texture, through the shared {@link RaidZombieRenderer}, as they always did);
 * the Armoured Risen is drawn on the player rig with its own rusted-kit skin ({@link RaidPlayerRenderers.ZombieRenderer}).
 */
public class RaidZombieDispatchRenderer extends EntityRenderer<RaidZombie> {
	private static final ResourceLocation VANILLA_ZOMBIE = ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");

	private final RaidZombieRenderer<RaidZombie> vanilla;
	private final RaidPlayerRenderers.ZombieRenderer<RaidZombie> armoured;

	public RaidZombieDispatchRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.vanilla = new RaidZombieRenderer<>(context, RaidZombieRenderer.fixed(VANILLA_ZOMBIE), 1.0f);
		this.armoured = new RaidPlayerRenderers.ZombieRenderer<>(context, z -> "raid_zombie_armoured", 1.0f);
		this.shadowRadius = 0.5f;
	}

	private EntityRenderer<RaidZombie> pick(RaidZombie z) {
		return z.variant() == RaidZombie.Variant.ARMOURED ? armoured : vanilla;
	}

	@Override
	public void render(RaidZombie entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		pick(entity).render(entity, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public boolean shouldRender(RaidZombie entity, Frustum frustum, double x, double y, double z) {
		return pick(entity).shouldRender(entity, frustum, x, y, z);
	}

	@Override
	public Vec3 getRenderOffset(RaidZombie entity, float partialTick) {
		return pick(entity).getRenderOffset(entity, partialTick);
	}

	@Override
	public ResourceLocation getTextureLocation(RaidZombie entity) {
		return pick(entity).getTextureLocation(entity);
	}
}
