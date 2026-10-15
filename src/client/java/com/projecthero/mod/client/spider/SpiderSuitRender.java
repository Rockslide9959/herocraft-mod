package com.projecthero.mod.client.spider;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.spider.item.SpiderManArmorItem;
import com.projecthero.mod.spider.item.SymbioteArmorItem;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * v0.16.1 (user: "the suit is too thick, make it hug the body"): the Spider-Man suit is no longer drawn as a GeckoLib
 * armour shell (inflated 0.30 / 0.55 around every part). Its texture is a standard 64x64 skin, so it is drawn as a
 * second skin on a wide player model a hair larger than the wearer's -- base layer +0.04, outer layer +0.29 -- the same
 * skin-rig idea as Nova's uniform ({@code NovaSuitRender}). Per piece: the mask (only while it is closed -- H still pulls
 * it off), the suit (body + both arms), the legs (leggings or boots). The wearer's own skin overlay under each piece is
 * hidden by {@code PlayerModelMixin} as before; {@code SuperheroArmorRenderer} skips the old shell for this set; first
 * person draws the suit sleeve through {@link #renderArm}. The black Symbiote suit is not affected.
 */
public final class SpiderSuitRender {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/armor/spider_man.png");
	/** v0.16.2: the Spider-Man Suit v2 (the user's classic red-and-blue skin), on the same rig. */
	public static final ResourceLocation TEXTURE_V2 = ProjectHeroMod.id("textures/armor/spider_man_v2.png");
	/** v0.16.4 (user): the Classic suit (a second original-style suit to compare). */
	public static final ResourceLocation TEXTURE_CLASSIC = ProjectHeroMod.id("textures/armor/spider_man_classic.png");
	/** v0.16.4 (user): the Black Suit is skin-tight too now (it was the old inflated shell). */
	public static final ResourceLocation TEXTURE_SYMBIOTE = ProjectHeroMod.id("textures/armor/spider_man_symbiote.png");
	/** Let It Take Over: the monstrous host's skin (same player-skin layout). */
	private static final ResourceLocation TEXTURE_TAKEOVER = ProjectHeroMod.id("textures/armor/symbiote_host.png");

	private static PlayerModel<LivingEntity> model;
	/** Screenshot comparisons only: draw the old inflated GeckoLib shell instead (never set in play). */
	public static boolean legacyShell;

	private SpiderSuitRender() {
	}

	private static PlayerModel<LivingEntity> model() {
		if (model == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.04f), false);
			model = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return model;
	}

	/** The Spider-Man suit set (not the black Symbiote suit, which keeps its own GeckoLib model). */
	public static boolean isSuit(Item item) {
		return item instanceof SpiderManArmorItem; // v0.16.4: the Black Suit (SymbioteArmorItem) included
	}

	private static boolean worn(Player p, EquipmentSlot slot) {
		return isSuit(p.getItemBySlot(slot).getItem());
	}

	/** Is any piece of the suit drawn on this player? */
	public static boolean anyWorn(Player p) {
		return worn(p, EquipmentSlot.HEAD) || worn(p, EquipmentSlot.CHEST) || worn(p, EquipmentSlot.LEGS) || worn(p, EquipmentSlot.FEET);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, Player player) {
		if (legacyShell || player.isInvisible() || !anyWorn(player)) {
			return;
		}
		PlayerModel<LivingEntity> m = model();
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		// v0.16.2: the mask comes off / goes on over a second (SpiderMaskAnim): part-way, only its upper part is drawn
		float cover = worn(player, EquipmentSlot.HEAD) ? SpiderMaskAnim.coverage(player) : 0f;
		boolean head = cover >= 0.999f;
		boolean body = worn(player, EquipmentSlot.CHEST);
		boolean legs = worn(player, EquipmentSlot.LEGS) || worn(player, EquipmentSlot.FEET);
		// v0.16.2: each piece in its own suit's skin (the original and the v2 suit can be mixed piece by piece)
		ResourceLocation headTex = textureFor(player.getItemBySlot(EquipmentSlot.HEAD).getItem());
		ResourceLocation bodyTex = textureFor(player.getItemBySlot(EquipmentSlot.CHEST).getItem());
		ResourceLocation legsTex = textureFor((worn(player, EquipmentSlot.LEGS) ? player.getItemBySlot(EquipmentSlot.LEGS)
				: player.getItemBySlot(EquipmentSlot.FEET)).getItem());
		for (ResourceLocation tex : new ResourceLocation[] { TEXTURE, TEXTURE_V2, TEXTURE_CLASSIC, TEXTURE_SYMBIOTE }) {
			boolean h = head && headTex.equals(tex);
			boolean b = body && bodyTex.equals(tex);
			boolean l = legs && legsTex.equals(tex);
			if (!h && !b && !l) {
				continue;
			}
			m.head.visible = h;
			m.hat.visible = h;
			m.body.visible = b;
			m.jacket.visible = b;
			m.rightArm.visible = b;
			m.leftArm.visible = b;
			m.rightSleeve.visible = b;
			m.leftSleeve.visible = b;
			m.rightLeg.visible = l;
			m.leftLeg.visible = l;
			m.rightPants.visible = l;
			m.leftPants.visible = l;
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(drawn(player, tex))), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		}
		m.setAllVisible(true);
		if (!head && cover > 0.001f) {
			var vc = buffers.getBuffer(RenderType.entityTranslucent(headTex));
			for (int layer = 0; layer < 2; layer++) {
				ModelPart part = SpiderMaskAnim.clippedHead(cover, layer == 1);
				if (part != null) {
					part.copyFrom(m.head);
					part.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
				}
			}
		}
	}

	/**
	 * v0.16.4: the Black Suit keeps the effects its old shell had -- the suit spreading over him from the chest
	 * (SymbioteSpread) and Let It Take Over's monstrous skin.
	 */
	private static ResourceLocation drawn(Player player, ResourceLocation tex) {
		if (!tex.equals(TEXTURE_SYMBIOTE)) {
			return tex;
		}
		if (com.projecthero.mod.symbiote.SymbioteHunger.takingOver(player)) {
			return TEXTURE_TAKEOVER;
		}
		if (com.projecthero.mod.client.symbiote.SymbioteReveal.isRevealing(player)) {
			return com.projecthero.mod.client.symbiote.SymbioteSpread.texture(tex, com.projecthero.mod.client.symbiote.SymbioteReveal.progress(
					player, net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
		}
		return tex;
	}

	/** v0.16.2: the skin a suit piece is drawn in -- the Suit v2's own, or the original's. */
	public static ResourceLocation textureFor(Item item) {
		if (item instanceof SymbioteArmorItem) {
			return TEXTURE_SYMBIOTE;
		}
		return item instanceof com.projecthero.mod.spider.item.SpiderManV2ArmorItem ? TEXTURE_V2
				: item instanceof com.projecthero.mod.spider.item.SpiderManClassicArmorItem ? TEXTURE_CLASSIC : TEXTURE;
	}

	/** First person: the suit sleeve over the arm vanilla just posed ({@code vanillaArm}). */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart vanillaArm, boolean right) {
		PlayerModel<LivingEntity> m = model();
		ModelPart arm = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		arm.copyFrom(vanillaArm);
		sleeve.copyFrom(vanillaArm);
		arm.visible = true;
		sleeve.visible = true;
		Player local = net.minecraft.client.Minecraft.getInstance().player;
		ResourceLocation tex = local == null ? TEXTURE : textureFor(local.getItemBySlot(EquipmentSlot.CHEST).getItem());
		if (local != null) {
			tex = drawn(local, tex); // v0.16.4: the Black Suit's spread-on / Let It Take Over skins on the sleeve too
		}
		var vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
		arm.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
	}
}
