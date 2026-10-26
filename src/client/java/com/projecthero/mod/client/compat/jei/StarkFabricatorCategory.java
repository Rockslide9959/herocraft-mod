package com.projecthero.mod.client.compat.jei;

import java.util.Locale;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.1: one Stark Fabricator recipe in JEI -- the inputs in the Fabricator's 3x3 input grid, an arrow, the result, and
 * underneath the energy, the time and (for gated recipes) the technology level and blueprint it needs.
 */
final class StarkFabricatorCategory implements IRecipeCategory<FabricationRecipe> {
	private static final int WIDTH = 132;
	private static final int HEIGHT = 78;

	private final IDrawable icon;
	private final IDrawable slot;
	private final IDrawable arrow;

	StarkFabricatorCategory(IGuiHelper gui) {
		this.icon = gui.createDrawableItemStack(new ItemStack(IronManBlocks.STARK_FABRICATOR_ITEM));
		this.slot = gui.getSlotDrawable();
		this.arrow = gui.getRecipeArrow();
	}

	@Override
	public RecipeType<FabricationRecipe> getRecipeType() {
		return ProjectHeroJeiPlugin.FABRICATOR;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("jei.projecthero.stark_fabricator");
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, FabricationRecipe recipe, IFocusGroup focuses) {
		for (int i = 0; i < recipe.inputs().size() && i < 9; i++) {
			FabricationRecipe.Input in = recipe.inputs().get(i);
			builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 3) * 18, 1 + (i / 3) * 18)
					.setBackground(slot, -1, -1)
					.addItemStack(new ItemStack(in.item(), in.count()));
		}
		builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 19)
				.setBackground(slot, -1, -1)
				.addItemStack(recipe.result().copy());
	}

	@Override
	public void draw(FabricationRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
		arrow.draw(g, 63, 19);
		var font = Minecraft.getInstance().font;
		String energy = String.format(Locale.ROOT, "%,d", recipe.energyCost());
		g.drawString(font, Component.translatable("jei.projecthero.stark_fabricator.energy", energy), 0, 58, 0xFF404040, false);
		g.drawString(font, Component.translatable("jei.projecthero.stark_fabricator.time",
				String.format(Locale.ROOT, "%.1f", recipe.timeTicks() / 20f)), 76, 58, 0xFF404040, false);
		if (recipe.requiredTechLevel() > 0) {
			g.drawString(font, Component.translatable("jei.projecthero.stark_fabricator.tech", recipe.requiredTechLevel()), 0, 68, 0xFF8A2020, false);
		}
	}
}
