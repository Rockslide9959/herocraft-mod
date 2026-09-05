package com.herocraft.mod.client.render;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.event.entity.EmpoweredZombie;
import com.herocraft.mod.event.entity.SupervillainVariant;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The Supervillain Village Raid's wave-6 boss. Shares the {@link EmpoweredZombie} entity and its
 * whole AI, and reuses the vanilla zombie model like every other raid mob -- it only swaps the
 * texture and render scale on the rolled {@link SupervillainVariant}.
 *
 * <p>An ordinary (non-Supervillain) Empowered Zombie -- the Zombie Raid's Powered Zombie Boss -- falls
 * through to its own {@code empowered_zombie} texture and {@link EmpoweredZombie#SCALE}, unchanged.
 *
 * <p><b>Art note:</b> these three currently use flat tinted placeholder textures on the zombie mesh.
 * The bespoke voxel models from the reference designs (Chimera / Arsenal / Omega Mage) are a
 * follow-up: the gameplay, AI, boss bar, rewards and model-selection are all wired to
 * {@link SupervillainVariant} already, so dropping in real models is texture + (optional) GeckoLib
 * work with no logic change.
 */
public class SupervillainBossRenderer extends RaidZombieRenderer<EmpoweredZombie> {
	private static final ResourceLocation EMPOWERED = HeroCraftMod.id("textures/entity/empowered_zombie.png");
	private static final ResourceLocation CHIMERA = HeroCraftMod.id("textures/entity/supervillain_chimera.png");
	private static final ResourceLocation ARSENAL = HeroCraftMod.id("textures/entity/supervillain_arsenal.png");
	private static final ResourceLocation OMEGA = HeroCraftMod.id("textures/entity/supervillain_omega_mage.png");

	public SupervillainBossRenderer(EntityRendererProvider.Context context) {
		super(context, SupervillainBossRenderer::textureFor, EmpoweredZombie.SCALE);
	}

	private static ResourceLocation textureFor(EmpoweredZombie boss) {
		SupervillainVariant v = boss.variant();
		if (v == null) {
			return EMPOWERED;
		}
		return switch (v) {
			case CHIMERA -> CHIMERA;
			case ARSENAL -> ARSENAL;
			case OMEGA_MAGE -> OMEGA;
		};
	}

	@Override
	protected void scale(EmpoweredZombie entity, PoseStack poseStack, float partialTick) {
		SupervillainVariant v = entity.variant();
		float s = v == null ? EmpoweredZombie.SCALE : v.scale();
		poseStack.scale(s, s, s);
	}
}
