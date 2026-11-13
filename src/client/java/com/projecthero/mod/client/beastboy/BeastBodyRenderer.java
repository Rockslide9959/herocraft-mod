package com.projecthero.mod.client.beastboy;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastBoyForms;
import com.projecthero.mod.beastboy.BeastForm;
import com.projecthero.mod.client.mixin.BeastBoyIronGolemAccessor;
import com.projecthero.mod.client.mixin.BeastBoyPolarBearAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.PolarBear;

/**
 * v0.17.3 Beast Boy: draws him as the animal. A form with custom art goes to {@link BeastFormArt#render} first (the ART side);
 * when that returns false -- and for every non-custom form -- the <b>fallback</b>: the closest vanilla mob model, green (its
 * texture recoloured by {@link BeastGreen}), scaled to the form and animated from his own movement (limb swing, head turn,
 * flying, the move he's doing). The model is posed on a client-only dummy of that mob that is never ticked or added to the
 * world.
 *
 * <pre>
 *   vanilla stand-ins: cheetah / tiger = ocelot, hawk = parrot, dolphin / shark / whale = dolphin, fly = bee, T-Rex = ravager,
 *   gorilla = iron golem, rhino = hoglin, elephant = sniffer, wolf = wolf, bear = polar bear, the Beast = a standing polar bear,
 *   pterodactyl = phantom, bat = bat, octopus = squid, mouse / snake = silverfish
 * </pre>
 */
public final class BeastBodyRenderer {
	/** The art paints its own greens: white keeps them; a red-dominant tint is the hurt flash (BeastFormArt). */
	public static final int TINT = 0xFFFFFFFF, HURT_TINT = 0xFFFF5050;

	/** A vanilla stand-in: the mob, its scale and a lift (blocks, before scaling). */
	private record Spec(EntityType<?> type, float scale, float lift) {
	}

	private static final Map<EntityType<?>, Entity> DUMMIES = new HashMap<>();

	private BeastBodyRenderer() {
	}

	private static Spec spec(BeastForm f) {
		return switch (f) {
			case CHEETAH -> new Spec(EntityType.OCELOT, 1.1f, 0f);
			case TIGER -> new Spec(EntityType.OCELOT, 1.5f, 0f);
			case HAWK -> new Spec(EntityType.PARROT, 1.25f, 0f);
			case DOLPHIN -> new Spec(EntityType.DOLPHIN, 1.0f, 0f);
			case SHARK -> new Spec(EntityType.DOLPHIN, 1.35f, 0f);
			case WHALE -> new Spec(EntityType.DOLPHIN, 3.6f, 0f);
			case FLY -> new Spec(EntityType.BEE, 0.35f, 0f);
			case TREX -> new Spec(EntityType.RAVAGER, 1.5f, 0f);
			case GORILLA -> new Spec(EntityType.IRON_GOLEM, 0.75f, 0f);
			case RHINO -> new Spec(EntityType.HOGLIN, 1.05f, 0f);
			case ELEPHANT -> new Spec(EntityType.SNIFFER, 1.4f, 0f);
			case WOLF -> new Spec(EntityType.WOLF, 1.0f, 0f);
			case BEAR -> new Spec(EntityType.POLAR_BEAR, 1.0f, 0f);
			case BEAST -> new Spec(EntityType.POLAR_BEAR, 1.12f, 0f);
			case PTERODACTYL -> new Spec(EntityType.PHANTOM, 2.0f, 0f);
			case BAT -> new Spec(EntityType.BAT, 0.45f, 0f);
			case OCTOPUS -> new Spec(EntityType.SQUID, 1.0f, 0.55f);
			case MOUSE -> new Spec(EntityType.SILVERFISH, 1.0f, 0f);
			case SNAKE -> new Spec(EntityType.SILVERFISH, 1.7f, 0f);
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the whole body (in place of the player)

	/**
	 * Draws {@code p} as {@code form}. The pose stack is at his feet (the player renderer's). Tries the art first for custom forms.
	 */
	public static void render(BeastForm form, AbstractClientPlayer p, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		String move = BeastBoy.move(p);
		float prog = move == null ? 0f : BeastBoy.moveProgress(p, partial);
		if (form.customArt) {
			float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw)); // +Z forward (BeastFormArt's contract)
			boolean drawn = BeastFormArt.render(form, p, partial, pose, buffers, light, move, prog, tint(p));
			pose.popPose();
			if (drawn) {
				return;
			}
		}
		fallback(form, p, partial, pose, buffers, light, move, prog);
	}

	/** The human-form quick morph: the art's partial animal on his body, else the whole fallback animal for the half second. */
	public static boolean renderQuick(BeastForm form, AbstractClientPlayer p, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float prog = BeastBoy.quickProgress(p, partial);
		pose.pushPose();
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		// the player model root: what LivingEntityRenderer sets up before drawing the model
		pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
		pose.scale(-1f, -1f, 1f);
		pose.scale(0.9375f, 0.9375f, 0.9375f);
		pose.translate(0f, -1.501f, 0f);
		boolean drawn = BeastFormArt.renderQuickMorph(form, p, partial, pose, buffers, light, prog, tint(p));
		pose.popPose();
		if (drawn) {
			return false; // the art drew its part: the player is drawn as usual underneath
		}
		String move = BeastBoy.move(p);
		fallback(form, p, partial, pose, buffers, light, move, move == null ? prog : BeastBoy.moveProgress(p, partial));
		return true; // the fallback replaced him for the flash
	}

	private static int tint(AbstractClientPlayer p) {
		return p.hurtTime > 0 ? HURT_TINT : TINT;
	}

	private static Entity dummy(EntityType<?> type) {
		Minecraft mc = Minecraft.getInstance();
		Entity e = DUMMIES.get(type);
		if (e == null || e.level() != mc.level) {
			e = type.create(mc.level);
			if (e == null) {
				return null;
			}
			if (e instanceof Bat bat) {
				bat.setResting(false);
			}
			DUMMIES.put(type, e);
		}
		return e;
	}

	public static void clear() {
		DUMMIES.clear();
		com.projecthero.mod.client.beastboy.model.BeastPose.clearSmoothing();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void fallback(BeastForm form, AbstractClientPlayer p, float partial, PoseStack pose, MultiBufferSource buffers, int light,
			String move, float prog) {
		Spec spec = spec(form);
		if (spec == null) {
			return;
		}
		Entity raw = dummy(spec.type);
		if (!(raw instanceof LivingEntity dummy)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		EntityRenderer r = mc.getEntityRenderDispatcher().getRenderer(dummy);
		if (!(r instanceof LivingEntityRenderer lr)) {
			return;
		}
		EntityModel model = lr.getModel();
		boolean flying = BeastBoyForms.flies(form) && (p.getAbilities().flying || !p.onGround());
		// the dummy mirrors him where its model looks
		dummy.tickCount = p.tickCount;
		dummy.setOnGround(!flying && p.onGround());
		dummy.setSprinting(p.isSprinting());
		dummy.setShiftKeyDown(p.isShiftKeyDown());
		dummy.setDeltaMovement(p.getDeltaMovement());
		dummy.setYRot(p.getYRot());
		dummy.setXRot(p.getXRot());
		dummy.yBodyRot = p.yBodyRot;
		dummy.yHeadRot = p.yHeadRot;
		dummy.hurtTime = p.hurtTime;
		if (dummy instanceof Bat bat) {
			bat.setResting(false);
			bat.flyAnimationState.startIfStopped(0);
		}
		if (dummy instanceof PolarBear bear) {
			float stand = 0f;
			if (form == BeastForm.BEAST) {
				stand = 6f;
			} else if (move != null && ("roar".equals(move) || "swipe".equals(move))) {
				stand = 6f * Mth.sin(prog * Mth.PI);
			}
			((BeastBoyPolarBearAccessor) bear).projecthero$setStand(stand);
			((BeastBoyPolarBearAccessor) bear).projecthero$setStandO(stand);
		}
		if (dummy instanceof IronGolem golem) {
			int t = move != null && ("smash".equals(move) || "grab".equals(move) || "chest_beat".equals(move)) ? Math.max(1, Math.round(10 * (1f - prog))) : 0;
			((BeastBoyIronGolemAccessor) golem).projecthero$setAttackAnimationTick(t);
		}
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot) - bodyYaw);
		float pitch = Mth.lerp(partial, p.xRotO, p.getXRot());
		float limb = p.walkAnimation.position(partial);
		float amount = Math.min(1f, p.walkAnimation.speed(partial));
		float age = p.tickCount + partial;
		if (spec.type == EntityType.PARROT) {
			age = flying ? (Mth.sin(age * 0.9f) + 1f) * 1.1f : 0f; // the renderer's flap "bob"
		} else if (spec.type == EntityType.SQUID) {
			age = 0.35f + 0.3f * Mth.sin(age * 0.25f); // the tentacles' sway
		}

		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
		if (flying) {
			pose.translate(0f, p.getBbHeight() * 0.5f, 0f);
			pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(pitch, -60f, 60f) * 0.6f));
			pose.translate(0f, -p.getBbHeight() * 0.5f, 0f);
		}
		if (move != null) {
			float k = Mth.sin(prog * Mth.PI);
			switch (move) {
				case "charge", "pounce", "dive", "strike", "bite", "slam" -> {
					pose.translate(0f, 0f, -0.35f * k * spec.scale); // a lunge forward (-Z is the front here)
					pose.mulPose(Axis.XP.rotationDegrees(12f * k));
				}
				case "stomp" -> pose.translate(0f, 0.25f * k * spec.scale, 0f);
				case "morph_in" -> {
					float s = 0.55f + 0.45f * prog;
					pose.scale(s, s, s);
				}
				default -> {
				}
			}
		}
		float s = spec.scale;
		pose.scale(-s, -s, s);
		pose.translate(0f, -1.501f - spec.lift, 0f);
		model.attackTime = p.getAttackAnim(partial);
		model.riding = false;
		model.young = false;
		model.prepareMobModel(dummy, limb, amount, partial);
		model.setupAnim(dummy, limb, amount, age, headYaw, pitch);
		ResourceLocation tex = BeastGreen.of(r.getTextureLocation(dummy));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		int overlay = LivingEntityRenderer.getOverlayCoords(p, 0f);
		model.renderToBuffer(pose, vc, light, overlay, 0xFFFFFFFF);
		pose.popPose();
	}
}
