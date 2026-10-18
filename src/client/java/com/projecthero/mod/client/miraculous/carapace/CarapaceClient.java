package com.projecthero.mod.client.miraculous.carapace;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.carapace.Carapace;
import com.projecthero.mod.miraculous.carapace.entity.CarapaceEntityTypes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Carapace's client side -- the suit layer and the Shield on players, the poses, the thrown Shield's renderer,
 * the energy shell (Shelter dome, Shell Barrier, Protect bubble: {@link CarapaceWorldFx}) and the Shield Surf ride
 * ({@link CarapaceSurfClient}). The first-person sleeve / Shield and the hidden armour / off-hand item are mixins
 * ({@code CarapaceFirstPersonArmMixin}, {@code CarapaceShieldHandMixin}, {@code CarapaceArmorMixin},
 * {@code CarapaceHeldItemMixin}).
 */
public final class CarapaceClient implements KwamiClientKit {
	private CarapaceClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.WAYZZ, new CarapaceClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new CarapaceLayer(playerRenderer));
			}
		});
		EntityRendererRegistry.register(CarapaceEntityTypes.SHIELD, ShieldThrowRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(CarapaceWorldFx::render);
		ClientTickEvents.START_CLIENT_TICK.register(CarapaceSurfClient::tick);
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		// the suit is a cut-out over his own skin: while any of it shows, his jacket / sleeve / trouser layers go (the
		// reveal redraws them texel by texel); v0.16.6 (user): his hat layer too -- the shell hood covers the head
		if (model instanceof PlayerModel<?> pm && CarapaceRender.cover(player, 0f) > 0f) {
			pm.hat.visible = false;
			pm.jacket.visible = false;
			pm.leftSleeve.visible = false;
			pm.rightSleeve.visible = false;
			pm.leftPants.visible = false;
			pm.rightPants.visible = false;
		}
		CarapacePose.apply(player, model);
	}

	/** False on purpose (as Rena Rouge): the shared hook also hides the hat layer; worn armour is hidden by {@code CarapaceArmorMixin}. */
	@Override
	public boolean hidesSkinOverlay(Player player) {
		return false;
	}

	/** Where the Shield is on {@code player} now. */
	public static CarapaceRender.ShieldAt shieldAt(Player player, float partial) {
		if (!Carapace.is(player) || Carapace.shieldOut(player)) {
			return CarapaceRender.ShieldAt.NONE;
		}
		if (Carapace.rolling(player)) {
			return CarapaceRender.ShieldAt.ROLL;
		}
		if (Carapace.surfing(player)) {
			return CarapaceRender.ShieldAt.FEET;
		}
		if (Carapace.guarding(player) || Carapace.running(player, Carapace.V_BASH)) {
			return CarapaceRender.ShieldAt.GUARD;
		}
		float[] p = CarapacePose.playing(player, partial);
		if (p != null) {
			switch ((int) p[0]) {
				case Carapace.ANIM_BASH -> {
					return CarapaceRender.ShieldAt.GUARD;
				}
				case Carapace.ANIM_CATCH, Carapace.ANIM_SLAM, Carapace.ANIM_TAUNT, Carapace.ANIM_BARRIER, Carapace.ANIM_PROTECT, Carapace.ANIM_STEADFAST,
						Carapace.ANIM_SHELTER -> {
					return CarapaceRender.ShieldAt.ARM;
				}
				default -> {
				}
			}
		}
		return CarapaceRender.ShieldAt.BACK;
	}

	/** First person: the Shield is on his arm / up in front; null = vanilla draws the off hand as usual. */
	public static CarapaceRender.ShieldAt firstPersonShield(Player player, float partial) {
		if (!Carapace.is(player) || CarapaceRender.cover(player, partial) < 0.999f) {
			return null;
		}
		CarapaceRender.ShieldAt at = shieldAt(player, partial);
		return at == CarapaceRender.ShieldAt.ARM || at == CarapaceRender.ShieldAt.GUARD ? at : null;
	}
}
