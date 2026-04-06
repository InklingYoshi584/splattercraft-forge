package net.splatcraft.forge.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.crafting.SplatcraftRecipeTypes;
import net.splatcraft.forge.crafting.WeaponWorkbenchRecipe;
import net.splatcraft.forge.crafting.WeaponWorkbenchSubtypeRecipe;
import net.splatcraft.forge.crafting.WeaponWorkbenchTab;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.c2s.CraftWeaponPacket;
import net.splatcraft.forge.tileentities.container.WeaponWorkbenchContainer;
import net.splatcraft.forge.util.ColorUtils;

public class WeaponWorkbenchScreen extends AbstractContainerScreen<WeaponWorkbenchContainer> {
    private static final ResourceLocation TEXTURES = new ResourceLocation(Splatcraft.MODID, "textures/gui/weapon_crafting.png");

    private final Player player;
    private final Inventory inventory;

    private int tabPos = 0;
    private int sectionPos = 0;
    private int typePos = 0;
    private int subTypePos = 0;
    private int ingredientPos = 0;
    private int tickTime = 0;
    private int craftButtonState = -1;

    private WeaponWorkbenchSubtypeRecipe selectedRecipe;
    private WeaponWorkbenchRecipe selectedWeapon;

    public WeaponWorkbenchScreen(WeaponWorkbenchContainer screenContainer, Inventory inv, Component titleIn) {
        super(screenContainer, inv, titleIn);
        this.imageHeight = 226;
        this.titleLabelX = 8;
        this.titleLabelY = this.imageHeight - 92;
        this.player = inv.player;
        this.inventory = inv;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderTooltip(guiGraphics, mouseX, mouseY);
        tickTime++;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURES);
        guiGraphics.blit(TEXTURES, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        Level level = player.level();
        List<WeaponWorkbenchTab> tabList = getTabList(level);
        List<WeaponWorkbenchRecipe> recipeList = getRecipeList(level, tabList);

        if (!recipeList.isEmpty()) {
            WeaponWorkbenchRecipe weapon = recipeList.get(typePos);
            int total = weapon.getAvailableRecipesTotal(player);
            if (total > 0) {
                renderSelectedItems(guiGraphics, weapon, total);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 22, 4210752, false);
        guiGraphics.drawString(font, this.inventory.getDisplayName(), this.titleLabelX, this.titleLabelY, 4210752, false);

        Level level = player.level();
        List<WeaponWorkbenchTab> tabList = getTabList(level);
        List<WeaponWorkbenchRecipe> recipeList = getRecipeList(level, tabList);
        RegistryAccess registryAccess = level.registryAccess();

        selectedWeapon = recipeList.isEmpty() ? null : recipeList.get(typePos);
        selectedRecipe = selectedWeapon == null || selectedWeapon.getAvailableRecipesTotal(player) == 0 ? null : selectedWeapon.getRecipeFromIndex(player, subTypePos);

        if (selectedRecipe == null) {
            Component emptyText = Component.translatable("gui.ammo_knights_workbench.empty");
            int textX = imageWidth / 2 - font.width(emptyText) / 2;
            guiGraphics.drawString(font, emptyText, textX, 73, 0xFFFFFF, false);
        }

        updateCraftButtonState();
        renderTabButtons(guiGraphics, tabList, mouseX, mouseY);
        renderRecipeButtons(guiGraphics, recipeList, registryAccess, mouseX, mouseY);
        renderIngredientButtons(guiGraphics, mouseX, mouseY);
        renderArrows(guiGraphics, recipeList, mouseX, mouseY);
        renderCraftButton(guiGraphics, mouseX, mouseY);
        renderHoverTooltips(guiGraphics, tabList, recipeList, registryAccess, mouseX, mouseY);
    }

    private List<WeaponWorkbenchTab> getTabList(Level level) {
        List<WeaponWorkbenchTab> tabList = new ArrayList<>(level.getRecipeManager().getRecipesFor(SplatcraftRecipeTypes.WEAPON_STATION_TAB_TYPE, inventory, level));
        tabList.removeIf(tab -> tab.hidden && tab.getTabRecipes(level, player).isEmpty());
        tabList.sort(WeaponWorkbenchTab::compareTo);
        if (tabList.isEmpty()) {
            return tabList;
        }
        tabPos = Math.max(0, Math.min(tabPos, tabList.size() - 1));
        return tabList;
    }

    private List<WeaponWorkbenchRecipe> getRecipeList(Level level, List<WeaponWorkbenchTab> tabList) {
        if (tabList.isEmpty()) {
            typePos = 0;
            sectionPos = 0;
            subTypePos = 0;
            ingredientPos = 0;
            return List.of();
        }

        List<WeaponWorkbenchRecipe> recipeList = new ArrayList<>(tabList.get(tabPos).getTabRecipes(level, player));
        recipeList.sort(WeaponWorkbenchRecipe::compareTo);
        if (recipeList.isEmpty()) {
            typePos = 0;
            sectionPos = 0;
            subTypePos = 0;
            ingredientPos = 0;
            return recipeList;
        }

        typePos = Math.max(0, Math.min(typePos, recipeList.size() - 1));
        return recipeList;
    }

    private void updateCraftButtonState() {
        if (selectedRecipe == null) {
            craftButtonState = -1;
            return;
        }

        boolean hasMaterial = true;
        for (int i = ingredientPos * 8; i < selectedRecipe.getInput().size() && i < ingredientPos * 8 + 8; i++) {
            Ingredient ingredient = selectedRecipe.getInput().get(i).getIngredient();
            int count = selectedRecipe.getInput().get(i).getCount();
            if (!SplatcraftRecipeTypes.getItem(player, ingredient, count, false)) {
                hasMaterial = false;
                break;
            }
        }

        craftButtonState = hasMaterial ? Math.max(craftButtonState, 0) : -1;
    }

    private void renderSelectedItems(GuiGraphics guiGraphics, WeaponWorkbenchRecipe weapon, int totalSubtypes) {
        renderSubtypeItem(guiGraphics, weapon, totalSubtypes, 0, 80, 58);
        if (totalSubtypes > 1) {
            renderSubtypeItem(guiGraphics, weapon, totalSubtypes, -1, 56, 66);
            renderSubtypeItem(guiGraphics, weapon, totalSubtypes, 1, 104, 66);
        }
    }

    private void renderSubtypeItem(GuiGraphics guiGraphics, WeaponWorkbenchRecipe weapon, int totalSubtypes, int offset, int x, int y) {
        int index = subTypePos + offset;
        if (index < 0) {
            index = totalSubtypes - 1;
        } else {
            index %= totalSubtypes;
        }

        ItemStack displayStack = weapon.getRecipeFromIndex(player, index).getOutput().copy();
        ColorUtils.setInkColor(displayStack, PlayerInfoCapability.get(player).getColor());
        guiGraphics.renderItem(displayStack, leftPos + x, topPos + y);
        if (offset == 0) {
            guiGraphics.blit(TEXTURES, leftPos + x - 3, topPos + y - 3, 246, 40, 22, 22);
        }
    }

    private void renderTabButtons(GuiGraphics guiGraphics, List<WeaponWorkbenchTab> tabList, int mouseX, int mouseY) {
        for (int i = 0; i < tabList.size(); i++) {
            int ix = imageWidth / 2 - (tabList.size() - 1) * 11 + i * 22;
            int iy = -5;
            int ty = tabPos == i ? 8 : 28;
            guiGraphics.blit(TEXTURES, ix - 10, iy, 211, ty, 20, 20);

            ResourceLocation tabIcon = tabList.get(i).getTabIcon();
            Item itemIcon = BuiltInRegistries.ITEM.containsKey(tabIcon) ? BuiltInRegistries.ITEM.get(tabIcon) : Items.AIR;
            if (itemIcon != Items.AIR) {
                guiGraphics.renderItem(new ItemStack(itemIcon), ix - 8, iy + 2);
            }
        }
    }

    private void renderRecipeButtons(GuiGraphics guiGraphics, List<WeaponWorkbenchRecipe> recipeList, RegistryAccess registryAccess, int mouseX, int mouseY) {
        for (int i = sectionPos * 8; i < recipeList.size() && i < sectionPos * 8 + 8; i++) {
            ItemStack displayStack = recipeList.get(i).getResultItem(registryAccess);
            int j = i - sectionPos * 8;
            int ix = 17 + j * 18;
            int iy = 34;

            guiGraphics.renderItem(displayStack, ix, iy);
            if (isHovering(ix, iy, 16, 16, mouseX, mouseY)) {
                guiGraphics.fillGradient(ix, iy, ix + 16, iy + 16, -2130706433, -2130706433);
            }
        }
    }

    private void renderIngredientButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (selectedRecipe == null) {
            return;
        }

        for (int i = ingredientPos * 8; i < selectedRecipe.getInput().size() && i < ingredientPos * 8 + 8; i++) {
            Ingredient ingredient = selectedRecipe.getInput().get(i).getIngredient();
            int count = selectedRecipe.getInput().get(i).getCount();
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) {
                continue;
            }

            ItemStack displayStack = items[(tickTime / 20) % items.length];
            int j = i - ingredientPos * 8;
            int ix = 17 + j * 18;
            int iy = 108;

            guiGraphics.renderItem(displayStack, ix, iy);
            if (!SplatcraftRecipeTypes.getItem(player, ingredient, count, false)) {
                guiGraphics.fillGradient(ix, iy, ix + 16, iy + 16, 0x40FF0000, 0x40FF0000);
            }
            if (count != 1) {
                guiGraphics.renderItemDecorations(font, displayStack, ix, iy, String.valueOf(count));
            }
        }
    }

    private void renderArrows(GuiGraphics guiGraphics, List<WeaponWorkbenchRecipe> recipeList, int mouseX, int mouseY) {
        int maxSections = (int) Math.ceil(recipeList.size() / 8f);
        if (maxSections > 1) {
            int ty = sectionPos + 1 < maxSections ? (isHovering(162, 36, 7, 11, mouseX, mouseY) ? 24 : 12) : 36;
            guiGraphics.blit(TEXTURES, 162, 36, 231, ty, 7, 11);
            ty = sectionPos - 1 >= 0 ? (isHovering(7, 36, 7, 11, mouseX, mouseY) ? 24 : 12) : 36;
            guiGraphics.blit(TEXTURES, 7, 36, 239, ty, 7, 11);
        }

        boolean hasSubtypes = selectedWeapon != null && selectedWeapon.getAvailableRecipesTotal(player) > 1;
        int ty = hasSubtypes ? (isHovering(126, 67, 7, 11, mouseX, mouseY) ? 24 : 12) : 36;
        guiGraphics.blit(TEXTURES, 126, 67, 231, ty, 7, 11);
        ty = hasSubtypes ? (isHovering(43, 67, 7, 11, mouseX, mouseY) ? 24 : 12) : 36;
        guiGraphics.blit(TEXTURES, 43, 67, 239, ty, 7, 11);

        int ingredientSections = selectedRecipe == null ? 0 : (int) Math.ceil(selectedRecipe.getInput().size() / 8f);
        if (ingredientSections > 1) {
            ty = ingredientPos + 1 < ingredientSections ? (isHovering(162, 110, 7, 11, mouseX, mouseY) ? 24 : 12) : 36;
            guiGraphics.blit(TEXTURES, 162, 110, 231, ty, 7, 11);
            ty = ingredientPos - 1 >= 0 ? (isHovering(7, 110, 7, 11, mouseX, mouseY) ? 24 : 12) : 36;
            guiGraphics.blit(TEXTURES, 7, 110, 239, ty, 7, 11);
        }
    }

    private void renderCraftButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int ty = craftButtonState > 0 ? 12 : craftButtonState == 0 ? (isHovering(71, 93, 34, 12, mouseX, mouseY) ? 24 : 36) : 0;
        guiGraphics.blit(TEXTURES, 71, 93, 177, ty, 34, 12);
        Component craftText = Component.translatable("gui.ammo_knights_workbench.craft");
        guiGraphics.drawString(font, craftText, imageWidth / 2 - font.width(craftText) / 2, 95, ty == 0 ? 0x999999 : 0xEFEFEF, false);
    }

    private void renderHoverTooltips(GuiGraphics guiGraphics, List<WeaponWorkbenchTab> tabList, List<WeaponWorkbenchRecipe> recipeList, RegistryAccess registryAccess, int mouseX, int mouseY) {
        for (int i = 0; i < tabList.size(); i++) {
            int ix = imageWidth / 2 - (tabList.size() - 1) * 11 + i * 22;
            int iy = -5;
            if (isHovering(ix - 10, iy, 18, 18, mouseX, mouseY)) {
                guiGraphics.renderTooltip(font, tabList.get(i).getName(), mouseX - leftPos, mouseY - topPos);
            }
        }

        for (int i = sectionPos * 8; i < recipeList.size() && i < sectionPos * 8 + 8; i++) {
            int j = i - sectionPos * 8;
            int ix = 17 + j * 18;
            int iy = 34;
            if (isHovering(ix, iy, 16, 16, mouseX, mouseY)) {
                guiGraphics.renderTooltip(font, recipeList.get(i).getResultItem(registryAccess), mouseX - leftPos, mouseY - topPos);
            }
        }

        if (selectedRecipe != null) {
            for (int i = ingredientPos * 8; i < selectedRecipe.getInput().size() && i < ingredientPos * 8 + 8; i++) {
                Ingredient ingredient = selectedRecipe.getInput().get(i).getIngredient();
                ItemStack[] items = ingredient.getItems();
                if (items.length == 0) {
                    continue;
                }
                int j = i - ingredientPos * 8;
                int ix = 17 + j * 18;
                int iy = 108;
                if (isHovering(ix, iy, 16, 16, mouseX, mouseY)) {
                    guiGraphics.renderTooltip(font, items[(tickTime / 20) % items.length], mouseX - leftPos, mouseY - topPos);
                }
            }

            if (isHovering(80, 58, 16, 16, mouseX, mouseY)) {
                guiGraphics.renderTooltip(font, selectedRecipe.getOutput(), mouseX - leftPos, mouseY - topPos);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Level level = player.level();
        List<WeaponWorkbenchTab> tabList = getTabList(level);
        List<WeaponWorkbenchRecipe> recipeList = getRecipeList(level, tabList);

        for (int i = 0; i < tabList.size(); i++) {
            int ix = imageWidth / 2 - (tabList.size() - 1) * 11 + i * 22;
            int iy = -4;
            if (tabPos != i && isHovering(ix - 10, iy, 20, 20, mouseX, mouseY)) {
                tabPos = i;
                typePos = 0;
                sectionPos = 0;
                subTypePos = 0;
                ingredientPos = 0;
                playButtonSound();
            }
        }

        for (int i = sectionPos * 8; i < recipeList.size() && i < sectionPos * 8 + 8; i++) {
            int j = i - sectionPos * 8;
            int ix = 17 + j * 18;
            int iy = 34;
            if (typePos != i && isHovering(ix, iy, 16, 16, mouseX, mouseY)) {
                typePos = i;
                subTypePos = 0;
                ingredientPos = 0;
                playButtonSound();
            }
        }

        int maxSections = (int) Math.ceil(recipeList.size() / 8f);
        if (maxSections > 1) {
            if (sectionPos + 1 < maxSections && isHovering(162, 36, 7, 11, mouseX, mouseY)) {
                subTypePos = 0;
                sectionPos++;
                ingredientPos = 0;
                playButtonSound();
            } else if (sectionPos - 1 >= 0 && isHovering(7, 36, 7, 11, mouseX, mouseY)) {
                subTypePos = 0;
                sectionPos--;
                ingredientPos = 0;
                playButtonSound();
            }
        }

        int totalSubtypes = selectedWeapon == null ? 0 : selectedWeapon.getAvailableRecipesTotal(player);
        if (totalSubtypes > 1) {
            if (isHovering(126, 67, 7, 11, mouseX, mouseY) || isHovering(107, 66, 14, 14, mouseX, mouseY)) {
                ingredientPos = 0;
                subTypePos = (subTypePos + 1) % totalSubtypes;
                playButtonSound();
            } else if (isHovering(43, 67, 7, 11, mouseX, mouseY) || isHovering(55, 66, 14, 14, mouseX, mouseY)) {
                ingredientPos = 0;
                subTypePos = subTypePos - 1 < 0 ? totalSubtypes - 1 : subTypePos - 1;
                playButtonSound();
            }
        }

        int ingredientSections = selectedRecipe == null ? 0 : (int) Math.ceil(selectedRecipe.getInput().size() / 8f);
        if (ingredientSections > 1) {
            if (ingredientPos + 1 < ingredientSections && isHovering(162, 110, 7, 11, mouseX, mouseY)) {
                ingredientPos++;
                playButtonSound();
            } else if (ingredientPos - 1 >= 0 && isHovering(7, 110, 7, 11, mouseX, mouseY)) {
                ingredientPos--;
                playButtonSound();
            }
        }

        if (craftButtonState != -1 && isHovering(71, 93, 34, 12, mouseX, mouseY)) {
            craftButtonState = 1;
            playButtonSound();
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (craftButtonState == 1) {
            craftButtonState = 0;
            if (selectedRecipe != null && isHovering(71, 93, 34, 12, mouseX, mouseY)) {
                SplatcraftPacketHandler.sendToServer(new CraftWeaponPacket(selectedWeapon.getId(), subTypePos));
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void playButtonSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    protected static Component getDisplayName(ItemStack stack) {
        MutableComponent text = Component.empty().append(stack.getHoverName());
        if (stack.hasCustomHoverName()) {
            text.withStyle(ChatFormatting.ITALIC);
        }
        text.withStyle(stack.getRarity().color).withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))));
        return text;
    }
}
