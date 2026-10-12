package com.projecthero.mod.client.render.raid;

import java.util.function.ToIntFunction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.event.entity.RaidAnimated;
import com.projecthero.mod.event.entity.RaidAnimator;
import com.projecthero.mod.event.entity.RaidZombie;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * v0.16.1: one GeckoLib renderer for every raid mob with its own model ({@link RaidAnimated}: the Risen and its Armoured
 * kit, the grave-child, the Acid Zombie, the Juggernaut, the Sword Skeleton, the Cursed Zombie, the Pillager Spy and --
 * through {@link PoweredZombieRenderer} -- the Powered Zombie Boss). Only these mod entities use it: vanilla zombies,
 * baby zombies and every other vanilla mob keep their vanilla renderers.
 * <ul>
 *   <li>The model's rest pose is scaled to the adult hit-box height ({@link RaidModelFit}); GeckoLib halves a baby.</li>
 *   <li>Glow: GeckoLib's emissive layer (eyes, acid, runes ...) plus, where given, an additive bloom ({@link RaidHaloLayer}).</li>
 *   <li>The head follows the look target through the never-keyed {@code neck} bone (absolute each frame).</li>
 *   <li>The Armoured Risen shows its {@code armour_*} bones; other Risen hide them. Worn vanilla armour is not drawn (the
 *       kit is modelled); a held item is drawn at the {@code item_r} bone for the zombie models that have one (the
 *       skeleton's sword / shield and the spy's crossbow are modelled and the vanilla items are hidden).</li>
 *   <li>No vanilla tip-over on death -- the death clip is the collapse.</li>
 * </ul>
 */
public class RaidUndeadRenderer<T extends Mob & RaidAnimated> extends GeoEntityRenderer<T> {
	public RaidUndeadRenderer(EntityRendererProvider.Context context, float shadow, ToIntFunction<T> halo, boolean heldItem) {
		super(context, new Model<>());
		this.shadowRadius = shadow;
		addRenderLayer(glowLayer());
		if (halo != null) {
			addRenderLayer(new RaidHaloLayer<>(this, halo));
		}
		if (heldItem) {
			addRenderLayer(new BlockAndItemGeoLayer<>(this, (bone, mob) -> bone.getName().equals("item_r") && !mob.getMainHandItem().isEmpty() ? mob.getMainHandItem() : null,
					(bone, mob) -> null) {
				@Override
				protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, T animatable) {
					return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
				}

				@Override
				protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, T animatable, MultiBufferSource bufferSource,
						float partialTick, int packedLight, int packedOverlay) {
					// the hand hangs along -Y with the palm inward: turn the vanilla held-item frame onto it
					poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
					poseStack.translate(0.0, 0.06, -0.02);
					super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
				}
			});
		}
	}

	/** The emissive layer (the boss renderer tints it). */
	protected AutoGlowingGeoLayer<T> glowLayer() {
		return new AutoGlowingGeoLayer<>(this);
	}

	@Override
	public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
			boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (!isReRender) {
			float adult = animatable.getBbHeight() / Math.max(0.05f, animatable.getScale());
			float s = RaidModelFit.fit(model, adult);
			poseStack.scale(s, s, s);
		}
	}

	@Override
	protected float getDeathMaxRotation(T animatable) {
		return 0.0f;
	}

	@Override
	public int getPackedOverlay(T animatable, float u, float partialTick) {
		boolean flash = animatable.hurtTime > 0 && !animatable.isDeadOrDying();
		return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(flash));
	}

	/** Per-frame bone tweaks after the clips (accent sets, orbit spin ...); default does nothing. */
	protected void customBones(T mob, GeoModel<T> model, AnimationState<T> state) {
	}

	public static class Model<T extends Mob & RaidAnimated> extends GeoModel<T> {
		private RaidUndeadRenderer<T> owner;

		@Override
		public ResourceLocation getModelResource(T animatable) {
			return RaidModelFit.geo(animatable.raidModel());
		}

		@Override
		public ResourceLocation getTextureResource(T animatable) {
			return RaidModelFit.texture(animatable.raidModel());
		}

		@Override
		public ResourceLocation getAnimationResource(T animatable) {
			return RaidModelFit.animation(animatable.raidModel());
		}

		@Override
		public void setCustomAnimations(T mob, long instanceId, AnimationState<T> state) {
			RaidAnimator anim = mob.raidAnimator();
			GeoBone neck = getAnimationProcessor().getBone("neck");
			if (neck != null) {
				float target = mob.isDeadOrDying() || anim.clientClip() != null ? 0.35f : 1.0f;
				anim.headWeight += (target - anim.headWeight) * 0.12f;
				EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
				float yaw = data == null ? 0.0f : Mth.clamp(data.netHeadYaw(), -60.0f, 60.0f);
				float pitch = data == null ? 0.0f : Mth.clamp(data.headPitch(), -35.0f, 35.0f);
				float w = mob.isDeadOrDying() ? 0f : anim.headWeight;
				neck.setRotY(yaw * w * Mth.DEG_TO_RAD);
				neck.setRotX(pitch * w * Mth.DEG_TO_RAD);
			}
			if (mob instanceof RaidZombie z) {
				boolean armoured = z.variant() == RaidZombie.Variant.ARMOURED;
				for (String b : ARMOUR_BONES) {
					GeoBone bone = getAnimationProcessor().getBone(b);
					if (bone != null) {
						bone.setHidden(!armoured);
					}
				}
			}
			if (owner != null) {
				owner.customBones(mob, this, state);
			}
		}
	}

	private static final String[] ARMOUR_BONES = { "armour_head", "armour_chest", "armour_pauldron_r", "armour_fore_r", "armour_fore_l", "armour_shin_r", "armour_shin_l", "armour_mail" };

	{
		@SuppressWarnings("unchecked")
		Model<T> m = (Model<T>) getGeoModel();
		m.owner = this;
	}
}
