package com.projecthero.mod.client.beastboy;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastForm;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4 Beast Boy (user: "make the animal hp bars that show up green hearts instead please, make it show up instead of the players
 * hearts when transformed"): while he is an animal, the vanilla heart row shows the <b>form's</b> health as green hearts. Done inside
 * vanilla's own health drawing ({@code BeastBoyHeartsMixin}): {@code Gui.renderPlayerHealth} reads the form's health and maximum
 * instead of his own, so the rows, half hearts, low-health wobble, regeneration wave, the armour bar's lift and the hurt / heal blink
 * all behave exactly like vanilla hearts (the form's hits broadcast a damage event, which sets the blink's invulnerability window);
 * {@code Gui.renderHeart} swaps the plain red heart and the empty container for the green sprites in
 * {@code textures/gui/sprites/hud/beast_heart/}. Poisoned, withered, frozen and absorption hearts keep their vanilla look, they say
 * something. Back in human form nothing is swapped and his own hearts return.
 */
public final class BeastHearts {
	private static final String VANILLA = "hud/heart/";
	private static final ResourceLocation FULL = ProjectHeroMod.id("hud/beast_heart/full");
	private static final ResourceLocation FULL_BLINK = ProjectHeroMod.id("hud/beast_heart/full_blinking");
	private static final ResourceLocation HALF = ProjectHeroMod.id("hud/beast_heart/half");
	private static final ResourceLocation HALF_BLINK = ProjectHeroMod.id("hud/beast_heart/half_blinking");
	private static final ResourceLocation CONTAINER = ProjectHeroMod.id("hud/beast_heart/container");
	private static final ResourceLocation CONTAINER_BLINK = ProjectHeroMod.id("hud/beast_heart/container_blinking");

	/** True between the start and end of {@code renderPlayerHealth} while the camera player is an animal (render thread only). */
	private static boolean drawing;

	private BeastHearts() {
	}

	/** The hearts show the form's health: he's an animal with its own pool (not the quick-morph shapes). */
	public static boolean active(Player p) {
		if (p == null || !BeastBoy.hasPower(p)) {
			return false;
		}
		BeastForm f = BeastBoy.form(p);
		return f != BeastForm.HUMAN && f.formHealth > 0f;
	}

	public static float health(Player p) {
		return Math.max(0f, BeastBoy.formHp(p));
	}

	public static float maxHealth(Player p) {
		return BeastBoy.form(p).formHealth;
	}

	public static void begin(Player p) {
		drawing = active(p);
	}

	public static void end() {
		drawing = false;
	}

	/** The heart sprite to draw instead of {@code vanilla} (unchanged unless the row is the form's). */
	public static ResourceLocation sprite(ResourceLocation vanilla) {
		if (!drawing || !"minecraft".equals(vanilla.getNamespace())) {
			return vanilla;
		}
		String path = vanilla.getPath();
		if (!path.startsWith(VANILLA)) {
			return vanilla;
		}
		return switch (path.substring(VANILLA.length())) {
			case "full", "hardcore_full" -> FULL;
			case "full_blinking", "hardcore_full_blinking" -> FULL_BLINK;
			case "half", "hardcore_half" -> HALF;
			case "half_blinking", "hardcore_half_blinking" -> HALF_BLINK;
			case "container", "container_hardcore" -> CONTAINER;
			case "container_blinking", "container_hardcore_blinking" -> CONTAINER_BLINK;
			default -> vanilla;
		};
	}
}
