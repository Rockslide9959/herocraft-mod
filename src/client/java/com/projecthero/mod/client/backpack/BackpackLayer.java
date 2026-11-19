package com.projecthero.mod.client.backpack;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.backpack.BackpackItem;
import com.projecthero.mod.backpack.BackpackMod;
import com.projecthero.mod.backpack.BackpackTier;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5 backpacks: the best backpack a player carries (not in a hand), on their back. The server picks it and syncs a
 * contents-free copy ({@link BackpackMod#WORN}), so every viewer sees it. The canvas is drawn tinted with the backpack's dye (or
 * its tier colour), then the tier's straps, buckles and plates over it. Over a chestplate the bag sits a pixel further out and
 * the straps widen to go round it. The player's cape is hidden while one is worn ({@code BackpackCapeMixin}); a Captain America
 * shield slung on the back hangs on the outside of the bag ({@link #shieldOffset}).
 */
public class BackpackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation BODY = ProjectHeroMod.id("textures/entity/backpack/backpack_body.png");
	private static final ResourceLocation[] TRIM = new ResourceLocation[BackpackTier.values().length];
	static {
		for (BackpackTier t : BackpackTier.values()) {
			TRIM[t.ordinal()] = ProjectHeroMod.id("textures/entity/backpack/backpack_trim_" + t.id() + ".png");
		}
	}

	private static BackpackModel model;

	public BackpackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	/** The backpack drawn on this player's back, or null. */
	public static ItemStack worn(Player p) {
		if (p.isInvisible() || p.isSpectator()) {
			return null;
		}
		ItemStack s = p.getAttached(BackpackMod.WORN);
		return s == null || !BackpackItem.isBackpack(s) ? null : s;
	}

	/**
	 * Where a Captain America shield slung on the back should sit (px out from the body pivot): {@code base} without a backpack,
	 * else just outside the bag's front pocket.
	 */
	public static float shieldZ(Player p, float base) {
		if (worn(p) == null) {
			return base;
		}
		float outer = 2f + BackpackModel.DEPTH + (armoured(p) ? 1f : 0f);
		return Math.max(base, outer + 0.15f);
	}

	private static boolean armoured(Player p) {
		return !p.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbAmount, float partial,
			float age, float headYaw, float headPitch) {
		ItemStack s = worn(p);
		if (s == null) {
			return;
		}
		if (model == null) {
			model = new BackpackModel();
		}
		BackpackTier tier = BackpackItem.tierOf(s);
		int tint = 0xFF000000 | BackpackItem.color(s);
		ResourceLocation trim = TRIM[tier.ordinal()];
		boolean armoured = armoured(p);

		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		pose.pushPose();
		if (armoured) {
			pose.translate(0f, 0f, 1f / 16f);
		}
		model.pack.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BODY)), light, OverlayTexture.NO_OVERLAY, tint);
		model.pack.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(trim)), light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
		pose.pushPose();
		if (armoured) {
			pose.scale(1.28f, 1.05f, 1.45f); // round the chestplate (armour is drawn 1 px bigger all round)
		}
		model.straps.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(trim)), light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
		pose.popPose();
	}
}
