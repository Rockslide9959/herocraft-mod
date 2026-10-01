package com.projecthero.mod.client.mutation.v0145;

import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mixin.CompositeStateAccessor;
import com.projecthero.mod.client.mixin.LivingEntityRendererLayersAccessor;
import com.projecthero.mod.client.mixin.RenderTypeCompositeInvoker;
import com.projecthero.mod.client.mixin.TextureShardInvoker;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.14.12: the armour on a Super Speed after-image. The player renderer's own armour layer is run on the copy (so
 * vanilla armour and every GeckoLib suit -- the Flash Suit included -- come out exactly as worn, posed like the copy),
 * through a buffer source that turns whatever it draws into the same ghost as the copy's skin: each draw's texture is
 * redrawn {@code entityTranslucent}, tinted and faded like the skin, at full brightness. Enchantment glint is dropped.
 */
public final class GhostArmour {
	private GhostArmour() {
	}

	/** Whether {@code player} has anything in an armour slot. */
	public static boolean wearsAny(AbstractClientPlayer player) {
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Draws the armour layer of {@code renderer} for {@code player} at the current pose, as a ghost tinted {@code argb}. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void render(PlayerRenderer renderer, AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffers,
			int argb, float limbPos, float limbSpeed, float ageInTicks, float headYaw, float pitch, float partial) {
		MultiBufferSource ghost = type -> {
			Optional<ResourceLocation> tex = texture(type);
			if (tex.isEmpty() || tex.get().getPath().contains("glint")) {
				return NOTHING;
			}
			return new Tinted(buffers.getBuffer(RenderType.entityTranslucent(tex.get())), argb);
		};
		for (RenderLayer layer : ((LivingEntityRendererLayersAccessor) renderer).projecthero$layers()) {
			if (layer instanceof HumanoidArmorLayer) {
				layer.render(pose, ghost, LightTexture.FULL_BRIGHT, player, limbPos, limbSpeed, partial, ageInTicks, headYaw, pitch);
			}
		}
	}

	private static Optional<ResourceLocation> texture(RenderType type) {
		if (!(type instanceof RenderTypeCompositeInvoker composite)) {
			return Optional.empty();
		}
		return ((TextureShardInvoker) ((CompositeStateAccessor) (Object) composite.projecthero$state()).projecthero$textureState())
				.projecthero$texture();
	}

	/** Multiplies every vertex colour by the tint (alpha included) and lights it fully. */
	private record Tinted(VertexConsumer out, int argb) implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			out.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			out.setColor(r * ((argb >> 16) & 0xFF) / 255, g * ((argb >> 8) & 0xFF) / 255, b * (argb & 0xFF) / 255,
					a * ((argb >>> 24) & 0xFF) / 255);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			out.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			out.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			out.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			out.setNormal(x, y, z);
			return this;
		}
	}

	/** Swallows a draw that has no texture to ghost (or is glint). */
	private static final VertexConsumer NOTHING = new VertexConsumer() {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}
	};
}
