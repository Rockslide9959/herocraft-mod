package com.projecthero.mod.client.doctorfate;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** v0.17.3 Doctor Fate: the Tomb Guardian -- the zombie (player-shaped) body in mummy wrappings with a gold collar and glowing eyes. */
public class TombGuardianRenderer extends ZombieRenderer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/tomb_guardian.png");

	public TombGuardianRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(Zombie zombie) {
		return TEXTURE;
	}
}
