package com.herocraft.mod.client.render;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/**
 * One renderer for every zombie-shaped raid mob. It reuses vanilla's zombie model and armour layers
 * verbatim -- the raid mobs are zombies, so re-authoring the model would buy nothing -- and takes the
 * texture and render scale as constructor arguments so a single class covers the Cursed Zombie, the
 * three {@code RaidZombie} variants, the Acid Zombie, the Juggernaut and the Empowered Zombie.
 *
 * <p>The texture is a {@link Function} rather than a constant so a mob whose look depends on its own
 * state -- the {@code RaidZombie} variants share one entity type -- can vary it per entity without
 * another renderer.
 */
public class RaidZombieRenderer<T extends Zombie> extends AbstractZombieRenderer<T, ZombieModel<T>> {
	private final Function<T, ResourceLocation> texture;
	private final float scale;

	public RaidZombieRenderer(EntityRendererProvider.Context context, Function<T, ResourceLocation> texture, float scale) {
		super(context,
				new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
				new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
				new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
		this.texture = texture;
		this.scale = scale;
		this.shadowRadius *= scale;
	}

	@Override
	public ResourceLocation getTextureLocation(Zombie entity) {
		@SuppressWarnings("unchecked")
		T typed = (T) entity;
		return texture.apply(typed);
	}

	@Override
	protected void scale(T entity, PoseStack poseStack, float partialTick) {
		super.scale(entity, poseStack, partialTick);
		if (scale != 1.0f) {
			poseStack.scale(scale, scale, scale);
		}
	}

	/** Convenience for the common "one fixed texture" case. */
	public static <T extends Zombie> Function<T, ResourceLocation> fixed(ResourceLocation location) {
		return entity -> location;
	}

}
