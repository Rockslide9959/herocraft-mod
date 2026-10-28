package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadBlocks;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Diamondhead's client kit -- the faceted green Petrosapien skin, a pointed crystal head (a stepped diamond turned 45
 * degrees, cheek fins), crystal shard ridges down his back and over his shoulders, and the crystal BLADE that grows out of his
 * right forearm while Blade Arm is on (third and first person). A pose for every move. Also hands his crystal block to the
 * translucent render layer.
 */
public final class DiamondheadClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/diamondhead.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/diamondhead_extras.png");

	private static final float[] CRYSTAL = { 0f, 0f, 0.5f, 0.5f };
	private static final float[] DARK = { 0.5f, 0f, 0.75f, 0.25f };
	private static final float[] GLOW = { 0.75f, 0f, 1f, 0.25f };
	private static final float[] PALE = { 0.5f, 0.25f, 0.75f, 0.5f };

	private static final float[][] VOLLEY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.3f, 0.6f, -0.3f, -0.3f, -0.6f, 0.3f, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.6f, -0.35f, 0, -1.6f, 0.35f, 0, 0.2f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] SPEAR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.9f, 0.3f, 0.3f, -0.8f, 0, -0.3f, -0.25f, 0.5f, 0.2f, 0, 0, -0.3f, 0, 0, -0.2f),
			QuirkKeyframes.f(7, -1.2f, -0.2f, 0, 0.4f, 0, 0, 0.4f, -0.4f, -0.3f, 0, 0, 0.4f, 0, 0, 0.2f), QuirkKeyframes.rest(15) };
	private static final float[][] BLADE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.4f, -0.7f, 0, 0, 0, 0, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(9, -1.3f, -0.6f, 0.1f, 0, 0, 0, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(13) };
	private static final float[][] SPIKES = { QuirkKeyframes.rest(0), QuirkKeyframes.fc(3, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.3f, 0),
			QuirkKeyframes.fc(6, -0.5f, 0, 0.1f, -0.5f, 0, -0.1f, 0.7f, 0, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.4f, 0.8f),
			QuirkKeyframes.fc(14, -0.4f, 0, 0.1f, -0.4f, 0, -0.1f, 0.6f, 0, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.3f, 0.7f), QuirkKeyframes.restC(20) };
	private static final float[][] SLIDE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.2f, 0, 1.3f, -0.2f, 0, -1.3f, 0.2f, 0.9f, -0.4f, 0.3f, 0.2f, 0.3f, 0.3f, -0.2f, 0),
			QuirkKeyframes.f(18, -0.2f, 0, 1.2f, -0.2f, 0, -1.2f, 0.2f, 0.9f, -0.4f, 0.3f, 0.2f, 0.3f, 0.3f, -0.2f, 0), QuirkKeyframes.rest(22) };
	private static final float[][] VAULT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -3.0f, 0, 0.25f, -3.0f, 0, -0.25f, -0.2f, 0, 0.2f, 0, 0.1f, 0.2f, 0, -0.1f, -0.5f),
			QuirkKeyframes.f(14, -2.8f, 0, 0.3f, -2.8f, 0, -0.3f, -0.15f, 0, 0.3f, 0, 0.1f, 0.3f, 0, -0.1f, -0.4f), QuirkKeyframes.rest(20) };
	private static final float[][] WALL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, -0.3f, 0, -1.5f, 0.3f, 0, 0.3f, 0, -0.5f, 0, 0, 0.5f, 0, 0, 0),
			QuirkKeyframes.f(10, -1.55f, -0.35f, 0, -1.55f, 0.35f, 0, 0.3f, 0, -0.5f, 0, 0, 0.5f, 0, 0, 0), QuirkKeyframes.rest(15) };
	private static final float[][] DOME = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.6f, 0, 0.9f, -2.6f, 0, -0.9f, -0.15f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.5f),
			QuirkKeyframes.f(14, -2.5f, 0, 1.0f, -2.5f, 0, -1.0f, -0.15f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.5f), QuirkKeyframes.rest(20) };
	private static final float[][] BARRAGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -3.1f, 0, 0.1f, -0.3f, 0, -0.4f, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.6f),
			QuirkKeyframes.f(12, -3.1f, 0, 0.1f, -0.3f, 0, -0.4f, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.6f),
			QuirkKeyframes.f(16, -1.4f, 0, 0, -0.3f, 0, -0.3f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(24) };
	private static final float[][] ARMOUR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.0f, 0.9f, -0.4f, -1.0f, -0.9f, 0.4f, 0.35f, 0, -0.2f, 0, 0.2f, -0.2f, 0, -0.2f, 0.4f),
			QuirkKeyframes.f(8, -0.4f, 0, 0.6f, -0.4f, 0, -0.6f, -0.1f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.3f), QuirkKeyframes.rest(16) };
	private static final float[][] SLASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.9f, 0.9f, 0.4f, 0.2f, 0, 0, 0, 0.5f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.2f, -1.0f, -0.2f, 0.3f, 0, 0, 0.2f, -0.5f, 0.2f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] PLACE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.1f, 0, 0, 0, 0, 0, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(8) };

	private static final FirstPersonClip FP_VOLLEY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 15, -15, 0, -0.1f, -0.1f, 0.1f),
			FirstPersonClip.f(5, -25, 25, 0, 0.2f, 0.12f, -0.25f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPEAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 0, 15, 0.05f, 0.5f, 0.25f),
			FirstPersonClip.f(7, -10, 25, 0, 0.15f, 0.05f, -0.3f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BLADE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 45, -30, 0.25f, 0.2f, -0.05f),
			FirstPersonClip.f(9, -28, 42, -28, 0.24f, 0.2f, -0.05f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(6, 35, 10, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUSH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 30, 0, 0.2f, 0.15f, -0.3f),
			FirstPersonClip.f(10, -30, 30, 0, 0.2f, 0.15f, -0.3f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RAISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -70, -20, 20, -0.1f, 0.45f, 0.1f),
			FirstPersonClip.f(14, -70, -20, 20, -0.1f, 0.45f, 0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SURF = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 10, -45, 40, -0.35f, 0.05f, 0.1f),
			FirstPersonClip.f(18, 10, -45, 40, -0.35f, 0.05f, 0.1f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -40, -40, -20, -0.2f, 0.25f, 0),
			FirstPersonClip.f(5, -10, 50, 20, 0.35f, 0.0f, -0.25f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PLACE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 15, 0, 0.1f, 0.0f, -0.2f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_FLEX = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, 40, 0, 0.25f, 0.15f, 0.05f),
			FirstPersonClip.f(8, -20, -30, 30, -0.2f, 0.1f, 0), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();

	public DiamondheadClient() {
		// his conjured crystal is see-through
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(DiamondheadBlocks.CRYSTAL, RenderType.translucent());
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int light) {
		AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
	}

	/** One shard: a thin box from {@code (x, y, z)} pointing up its local -Y, tipped by {@code rx} / {@code rz}. */
	private static void shard(PoseStack pose, VertexConsumer vc, float x, float y, float z, float rx, float rz, float w, float h, float[] uv, int light) {
		pose.pushPose();
		pose.translate(x / 16f, y / 16f, z / 16f);
		pose.mulPose(Axis.XP.rotation(rx));
		pose.mulPose(Axis.ZP.rotation(rz));
		pose.mulPose(Axis.YP.rotationDegrees(45));
		box(pose, vc, -w / 2, -h, -w / 2, w / 2, 0, w / 2, uv, light);
		box(pose, vc, -w / 4, -h - w * 0.8f, -w / 4, w / 4, -h + 0.01f, w / 4, uv, light);
		pose.popPose();
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		// the pointed crystal head: a stepped diamond, turned 45 degrees, with fins at the cheeks
		AlienParts.on(pose, model.head, () -> {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(45));
			box(pose, vc, -3.6f, -9.6f, -3.6f, 3.6f, -7.6f, 3.6f, CRYSTAL, light);
			box(pose, vc, -2.6f, -11.6f, -2.6f, 2.6f, -9.6f, 2.6f, CRYSTAL, light);
			box(pose, vc, -1.6f, -13.4f, -1.6f, 1.6f, -11.6f, 1.6f, PALE, light);
			box(pose, vc, -0.7f, -15.0f, -0.7f, 0.7f, -13.4f, 0.7f, PALE, light);
			pose.popPose();
			shard(pose, vc, -4.2f, -3.0f, 0f, 0f, 0.35f, 1.2f, 4.5f, CRYSTAL, light);
			shard(pose, vc, 4.2f, -3.0f, 0f, 0f, -0.35f, 1.2f, 4.5f, CRYSTAL, light);
		});
		// the ridges down the back
		AlienParts.on(pose, model.body, () -> {
			for (int k = 0; k < 3; k++) {
				float y = 2.5f + k * 3.4f;
				shard(pose, vc, -1.6f, y, 2.0f, -0.7f, 0.25f, 1.4f, 4.5f - k * 0.8f, CRYSTAL, light);
				shard(pose, vc, 1.6f, y, 2.0f, -0.7f, -0.25f, 1.4f, 4.5f - k * 0.8f, CRYSTAL, light);
			}
		});
		// shoulder shards, and the blade
		boolean blade = (Omnitrix.state(p).kitFlags & DiamondheadKit.F_BLADE) != 0;
		for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
			boolean right = arm == model.rightArm;
			AlienParts.on(pose, arm, () -> {
				float x = right ? -1.0f : 1.0f;
				shard(pose, vc, x, -2.0f, 0f, 0f, right ? 0.45f : -0.45f, 1.6f, 4.0f, CRYSTAL, light);
				shard(pose, vc, x + (right ? -1.2f : 1.2f), -1.2f, 1.0f, -0.3f, right ? 0.9f : -0.9f, 1.1f, 2.8f, CRYSTAL, light);
			});
		}
		if (blade) {
			// last: its glowing edge fetches another buffer type, which ends the translucent one
			AlienParts.on(pose, model.rightArm, () -> blade(pose, buffers, vc, light, age));
		}
	}

	/** The crystal blade on the right forearm (arm-bone space, pixels). */
	private static void blade(PoseStack pose, MultiBufferSource buffers, VertexConsumer vc, int light, float age) {
		box(pose, vc, -1.4f, 4.0f, -2.0f, -0.6f, 18.5f, 2.0f, PALE, light);
		box(pose, vc, -1.3f, 18.5f, -1.2f, -0.7f, 21.5f, 1.2f, PALE, light);
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EXTRAS));
		float pulse = 0.75f + 0.25f * Mth.sin(age * 0.2f);
		int a = (int) (255 * pulse);
		AlienParts.box(pose, glow, -1.45f, 6.0f, -2.15f, -0.55f, 20.0f, -1.95f, GLOW[0], GLOW[1], GLOW[2], GLOW[3], (a << 24) | 0xFFFFFF,
				LightTexture.FULL_BRIGHT);
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		if (right && (Omnitrix.state(p).kitFlags & DiamondheadKit.F_BLADE) != 0) {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
			blade(pose, buffers, vc, light, p.tickCount);
		}
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case DiamondheadKit.ANIM_VOLLEY -> VOLLEY;
			case DiamondheadKit.ANIM_SPEAR -> SPEAR;
			case DiamondheadKit.ANIM_BLADE -> BLADE;
			case DiamondheadKit.ANIM_SPIKES -> SPIKES;
			case DiamondheadKit.ANIM_SLIDE -> SLIDE;
			case DiamondheadKit.ANIM_VAULT -> VAULT;
			case DiamondheadKit.ANIM_WALL -> WALL;
			case DiamondheadKit.ANIM_DOME -> DOME;
			case DiamondheadKit.ANIM_BARRAGE -> BARRAGE;
			case DiamondheadKit.ANIM_ARMOUR -> ARMOUR;
			case DiamondheadKit.ANIM_SLASH -> SLASH;
			case DiamondheadKit.ANIM_PLACE -> PLACE;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played && (Omnitrix.state(p).kitFlags & DiamondheadKit.F_BLADE) != 0) {
			// the blade held ready, a little out from the body
			m.rightArm.xRot = m.rightArm.xRot * 0.5f - 0.35f;
			m.rightArm.zRot += 0.15f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case DiamondheadKit.ANIM_VOLLEY, DiamondheadKit.ANIM_BARRAGE -> FP_VOLLEY;
			case DiamondheadKit.ANIM_SPEAR -> FP_SPEAR;
			case DiamondheadKit.ANIM_BLADE -> FP_BLADE;
			case DiamondheadKit.ANIM_SPIKES -> FP_SLAM;
			case DiamondheadKit.ANIM_WALL -> FP_PUSH;
			case DiamondheadKit.ANIM_DOME, DiamondheadKit.ANIM_VAULT -> FP_RAISE;
			case DiamondheadKit.ANIM_SLIDE -> FP_SURF;
			case DiamondheadKit.ANIM_SLASH -> FP_SLASH;
			case DiamondheadKit.ANIM_PLACE -> FP_PLACE;
			case DiamondheadKit.ANIM_ARMOUR -> FP_FLEX;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		if (r.nextInt(6) == 0) {
			p.level().addParticle(ParticleTypes.WAX_OFF, p.getX() + (r.nextDouble() - 0.5) * 0.8, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
		}
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & DiamondheadKit.F_SLIDING) != 0 && p.onGround()) {
			p.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, DiamondheadBlocks.CRYSTAL.defaultBlockState()), p.getX(), p.getY() + 0.1,
					p.getZ(), 0, 0.1, 0);
		}
		if ((flags & DiamondheadKit.F_ARMOUR) != 0 && r.nextInt(2) == 0) {
			p.level().addParticle(ParticleTypes.END_ROD, p.getX() + (r.nextDouble() - 0.5) * 1.2, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 1.2, 0, 0, 0);
		}
	}

	@Override
	public float[] badge() {
		return new float[] { 0f, 4.5f, -2.06f, 3.2f };
	}
}
