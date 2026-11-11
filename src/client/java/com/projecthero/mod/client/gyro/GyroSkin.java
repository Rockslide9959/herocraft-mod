package com.projecthero.mod.client.gyro;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.gyro.Gyro;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Gyro Zeppeli: his look while he has the Spin -- a 64x64 player skin of Gyro (blond hair, green eyes, the "GO!GO!" gold
 * teeth, the teal coat, the belt with its two ball pouches, riding boots), in a wide and a slim cut so either arm model samples it
 * right ({@code scratchpad/gyro/gen_gyro_textures.js}). Swapped in by {@code GyroSkinMixin} (body and first-person arm); the hat's
 * brim and crown are geometry ({@link GyroLayer}).
 */
public final class GyroSkin {
	public static final ResourceLocation WIDE = ProjectHeroMod.id("textures/entity/gyro/power/gyro_wide.png");
	public static final ResourceLocation SLIM = ProjectHeroMod.id("textures/entity/gyro/power/gyro_slim.png");
	public static final ResourceLocation HAT = ProjectHeroMod.id("textures/entity/gyro/power/gyro_hat.png");

	private GyroSkin() {
	}

	/** Has the look (every viewer reads the synced state). */
	public static boolean shows(Player player) {
		return Gyro.hasPower(player);
	}

	public static ResourceLocation texture(AbstractClientPlayer player) {
		return player.getSkin().model() == PlayerSkin.Model.SLIM ? SLIM : WIDE;
	}
}
