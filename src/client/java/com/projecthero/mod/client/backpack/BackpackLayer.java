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
 * shield slung on the back hangs on the outside of the bag ({@link #shieldZ}).
 *
 * <p><b>Back clearance</b> (v0.17.5, "make the backpacks not clip through capes and wings"): everything else a player can have on
 * the back is drawn outside the bag through one helper, {@link #backClearance}: capes and cloaks hang from the bag's top outer
 * edge ({@link #hangCapeFromBag}, with {@link #CAPE_MIN_SWING} so the swing never cuts back into it), wings and jetpacks mount on
 * its outer face, weapons slung on the back hang over it. A few things that ARE a pack or a body (an Omnitrix alien, Aizawa's
 * sleeping bag, an Iron Man suit-pack) hide the backpack instead ({@link #hiddenBy}). See {@code docs/BACKPACKS_REFERENCE.md}.
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
		if (s == null || !BackpackItem.isBackpack(s) || hiddenBy(p)) {
			return null;
		}
		return s;
	}

	/**
	 * Things that replace the back instead of hanging over it, so the backpack is not drawn: an Omnitrix alien (another body; its
	 * tails, wings and crystals grow out of the back), Aizawa's sleeping bag (zipped round the whole player), the Power Armour (a
	 * mech drawn over the wearer's hidden body, with its own back modules), and an Iron Man
	 * suit-pack carried on the back or deploying (it is a pack itself, and its plates wrap from the back onto the body).
	 */
	public static boolean hiddenBy(Player p) {
		if (com.projecthero.mod.client.omnitrix.AlienClients.shown(p) != null) {
			return true;
		}
		if (com.projecthero.mod.quirk.erasure.ErasureSleepingBag.inBag(p)) {
			return true;
		}
		if (com.projecthero.mod.powerarmour.PowerArmour.state(p) != null) {
			return true; // inside the Power Armour (a mech drawn over a hidden body; its back modules dock on the suit)
		}
		if (!p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_BACK_PACK, "").isEmpty()
				&& !(p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem)) {
			return true;
		}
		return com.projecthero.mod.client.ironman.IronManPortableClient.form(p) != null;
	}

	/** Gap left between the bag and anything drawn outside it (px). */
	public static final float GAP = 0.35f;

	/**
	 * The bag's outer face (px out of the back, body space): the front pocket, a pixel further out over a chestplate, plus
	 * {@link #GAP}. 0 without a backpack.
	 */
	public static float outerZ(Player p) {
		if (worn(p) == null) {
			return 0f;
		}
		return 2f + BackpackModel.DEPTH + (armoured(p) ? 1f : 0f) + GAP;
	}

	/**
	 * THE shared helper: how far (px, along +z out of the back, body space) to push something whose innermost point normally
	 * sits at body z = {@code innerZ} px so it lands outside the bag. 0 without a backpack (or if it already clears it). Every
	 * cape, wing, jetpack and back-slung weapon layer adds this to its own back offset instead of keeping a magic number.
	 */
	public static float backClearance(Player p, float innerZ) {
		return Math.max(0f, outerZ(p) - innerZ);
	}

	/** Top of the bag's carry handle (px, body space, +y down); a cape's top edge hangs just above it. */
	private static final float CAPE_TOP_Y = -1.7f;
	/**
	 * The least a cape hung from the bag may swing back (deg, in the body's frame): with its pivot outside the bag's outer face
	 * this keeps the whole hanging length (and its flutter) outside the bag.
	 */
	public static final float CAPE_MIN_SWING = 4f;

	/**
	 * For cape and cloak layers that draw in model-root space: with a backpack worn, moves {@code pose} into the body's frame
	 * (so it follows a crouch or any pose that bends the body) at the bag's top outer edge, where the cape now hangs from, and
	 * returns the body's forward lean in degrees -- take it off the cape's own swing and clamp at {@link #CAPE_MIN_SWING}.
	 * Without a backpack it returns NaN and leaves {@code pose} alone (draw as usual).
	 */
	public static float hangCapeFromBag(Player p, PlayerModel<?> model, PoseStack pose) {
		float z = capePivotZ(p);
		if (z <= 0f) {
			return Float.NaN;
		}
		model.body.translateAndRotate(pose);
		pose.translate(0f, CAPE_TOP_Y / 16f, z / 16f);
		return model.body.xRot * (180f / (float) Math.PI);
	}

	/** Body-space z (px) a cape hung by {@link #hangCapeFromBag} pivots at (0 without a backpack). */
	public static float capePivotZ(Player p) {
		float z = outerZ(p);
		return z <= 0f ? 0f : z + 0.15f;
	}

	/**
	 * Where a Captain America shield slung on the back should sit (px out from the body pivot): {@code base} without a backpack,
	 * else just outside the bag's front pocket.
	 */
	public static float shieldZ(Player p, float base) {
		return base + backClearance(p, base);
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
