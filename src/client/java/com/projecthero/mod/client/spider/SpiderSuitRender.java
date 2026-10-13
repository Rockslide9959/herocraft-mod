package com.projecthero.mod.client.spider;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.spider.SpiderMask;
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
		return item instanceof SpiderManArmorItem && !(item instanceof SymbioteArmorItem);
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
		boolean head = worn(player, EquipmentSlot.HEAD) && !SpiderMask.isOpen(player);
		boolean body = worn(player, EquipmentSlot.CHEST);
		boolean legs = worn(player, EquipmentSlot.LEGS) || worn(player, EquipmentSlot.FEET);
		m.head.visible = head;
		m.hat.visible = head;
		m.body.visible = body;
		m.jacket.visible = body;
		m.rightArm.visible = body;
		m.leftArm.visible = body;
		m.rightSleeve.visible = body;
		m.leftSleeve.visible = body;
		m.rightLeg.visible = legs;
		m.leftLeg.visible = legs;
		m.rightPants.visible = legs;
		m.leftPants.visible = legs;
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		m.setAllVisible(true);
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
		var vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
		arm.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
	}
}
