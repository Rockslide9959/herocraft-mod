package com.projecthero.mod.client.render.raid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.SupervillainBossRenderer;
import com.projecthero.mod.event.entity.EmpoweredZombie;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.1: the {@code empowered_zombie} entity type is shared by the Zombie Raid's Powered Zombie Boss and the
 * Supervillain Raid's boss variants. This picks per entity: an ordinary boss ({@link EmpoweredZombie#variant()} null) gets
 * the new {@link PoweredZombieRenderer}; a Supervillain variant goes to {@link SupervillainBossRenderer}, untouched.
 */
public class EmpoweredZombieDispatchRenderer extends EntityRenderer<EmpoweredZombie> {
	private final PoweredZombieRenderer powered;
	private final EntityRenderer<EmpoweredZombie> supervillain;

	public EmpoweredZombieDispatchRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.powered = new PoweredZombieRenderer(context);
		this.supervillain = new SupervillainBossRenderer(context);
		this.shadowRadius = 1.0f;
	}

	private EntityRenderer<EmpoweredZombie> pick(EmpoweredZombie e) {
		return e.variant() == null ? powered : supervillain;
	}

	@Override
	public void render(EmpoweredZombie entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		pick(entity).render(entity, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public boolean shouldRender(EmpoweredZombie entity, Frustum frustum, double x, double y, double z) {
		return pick(entity).shouldRender(entity, frustum, x, y, z);
	}

	@Override
	public ResourceLocation getTextureLocation(EmpoweredZombie entity) {
		return pick(entity).getTextureLocation(entity);
	}
}
