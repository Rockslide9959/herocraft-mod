package com.projecthero.mod.client.render;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.supervillain.SupervillainGeoRenderer;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.SupervillainVariant;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The renderer registered for {@link EmpoweredZombie} (the Zombie Raid's Powered Zombie Boss and the Supervillain Village
 * Raid's wave-6 boss share that entity).
 *
 * <ul>
 *   <li>An ordinary Powered Zombie Boss (no {@link SupervillainVariant}) is drawn by
 *       {@link com.projecthero.mod.client.render.raid.PoweredZombieRenderer} (v0.16.1: its own GeckoLib grave-champion model, power-tinted accents).</li>
 *   <li>v0.16.1: a Supervillain is drawn by its own GeckoLib model -- the Chimera, the Arsenal or the Omega Mage
 *       ({@link SupervillainGeoRenderer}, one per variant), with its own animation set synced from the server
 *       ({@code SupervillainAnims}). The vanilla zombie / baby zombie and every other mob are untouched: this class is only
 *       ever bound to {@code projecthero:empowered_zombie}.</li>
 * </ul>
 */
public class SupervillainBossRenderer extends EntityRenderer<EmpoweredZombie> {

	private final com.projecthero.mod.client.render.raid.PoweredZombieRenderer zombie;
	private final Map<SupervillainVariant, SupervillainGeoRenderer> villains = new EnumMap<>(SupervillainVariant.class);

	public SupervillainBossRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.zombie = new com.projecthero.mod.client.render.raid.PoweredZombieRenderer(context);
		for (SupervillainVariant v : SupervillainVariant.values()) {
			villains.put(v, new SupervillainGeoRenderer(context, v));
		}
		this.shadowRadius = 0.5f * EmpoweredZombie.SCALE;
	}

	private EntityRenderer<? super EmpoweredZombie> delegate(EmpoweredZombie boss) {
		SupervillainVariant v = boss.variant();
		return v == null ? zombie : villains.get(v);
	}

	@Override
	public void render(EmpoweredZombie entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		SupervillainVariant v = entity.variant();
		if (v == null) {
			this.shadowRadius = 0.5f * EmpoweredZombie.SCALE;
			zombie.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			return;
		}
		this.shadowRadius = 0.55f * v.scale();
		villains.get(v).render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	public boolean shouldRender(EmpoweredZombie entity, Frustum frustum, double x, double y, double z) {
		if (entity.variant() == null) {
			return zombie.shouldRender(entity, frustum, x, y, z);
		}
		// the model is taller and wider than the hit-box: cull against the drawn size
		if (!entity.shouldRender(x, y, z)) {
			return false;
		}
		return frustum.isVisible(entity.getBoundingBox().inflate(1.5, 1.6, 1.5).expandTowards(0, 1.0, 0));
	}

	@Override
	public Vec3 getRenderOffset(EmpoweredZombie entity, float partialTick) {
		return delegate(entity).getRenderOffset(entity, partialTick);
	}

	@Override
	public ResourceLocation getTextureLocation(EmpoweredZombie entity) {
		SupervillainVariant v = entity.variant();
		return v == null ? zombie.getTextureLocation(entity) : villains.get(v).getTextureLocation(entity);
	}
}
