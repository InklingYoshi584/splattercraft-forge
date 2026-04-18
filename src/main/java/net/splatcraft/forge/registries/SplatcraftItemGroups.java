package net.splatcraft.forge.registries;

import java.util.ArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.items.ColoredBlockItem;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkColor;

import static net.splatcraft.forge.registries.SplatcraftItems.*;

public class SplatcraftItemGroups {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Splatcraft.MODID);

    public static final RegistryObject<CreativeModeTab> GROUP_GENERAL = REGISTRY.register("splatcraft_general", () -> CreativeModeTab.builder()
        .withTabsBefore(CreativeModeTabs.COMBAT)
        .icon(() -> sardiniumBlock.get().getDefaultInstance())
        .title(Component.translatable("itemGroup.splatcraft_general"))
        .displayItems((parameters, output) -> {
            output.accept(sardinium.get());
            output.accept(sardiniumBlock.get());
            output.accept(rawSardinium.get());
            output.accept(rawSardiniumBlock.get());
            output.accept(sardiniumOre.get());
            output.accept(powerEgg.get());
            output.accept(powerEggCan.get());
            output.accept(powerEggBlock.get());
            output.accept(emptyInkwell.get());
            output.accept(ammoKnightsScrap.get());
            output.accept(blueprint.get());
            output.accept(kensaPin.get());

            output.accept(turfScanner.get());
            output.accept(inkDisruptor.get());
            output.accept(colorChanger.get());
            output.accept(remotePedestal.get());
            output.accept(inkRail.get());
            output.accept(inkRailNode.get());
            output.accept(dummySpecial.get());
            output.accept(inkArmor.get());
            output.accept(bombRush.get());
            output.accept(inkzooka.get());
            output.accept(inkStrike.get());
            output.accept(tripleInkStrike.get());
            output.accept(ultraStamp.get());
            output.accept(zipcaster.get());

            output.accept(splatfestBand.get());
            output.accept(clearBand.get());
            output.accept(waxApplicator.get());

            output.accept(emptyFilter.get());
            output.accept(pastelFilter.get());
            output.accept(organicFilter.get());
            output.accept(neonFilter.get());
            output.accept(overgrownFilter.get());
            output.accept(midnightFilter.get());
            output.accept(enchantedFilter.get());
            output.accept(creativeFilter.get());

            output.accept(inkVat.get());
            output.accept(weaponWorkbench.get());

            output.accept(inkwell.get());
            output.accept(spawnPad.get());
            output.accept(squidBumper.get());
            output.accept(inkedWool.get());
            output.accept(inkedCarpet.get());
            output.accept(inkedGlass.get());
            output.accept(inkedGlassPane.get());

            output.accept(canvas.get());
            output.accept(coralite.get());
            output.accept(coraliteSlab.get());
            output.accept(coraliteStairs.get());
            output.accept(grate.get());
            output.accept(grateRamp.get());
            output.accept(barrierBar.get());
            output.accept(platedBarrierBar.get());
            output.accept(cautionBarrierBar.get());
            output.accept(tarp.get());
            output.accept(glassCover.get());
            output.accept(crate.get());
            output.accept(sunkenCrate.get());
            output.accept(splatSwitch.get());
            output.accept(inkRail.get());
            output.accept(inkRailNode.get());

            output.accept(stageBarrier.get());
            output.accept(stageVoid.get());
            output.accept(allowedColorBarrier.get());
            output.accept(deniedColorBarrier.get());
        }).build());

    public static final RegistryObject<CreativeModeTab> GROUP_WEAPONS = REGISTRY.register("splatcraft_weapons", () -> CreativeModeTab.builder()
        .withTabsBefore(GROUP_GENERAL.getKey())
        .icon(() -> ColorUtils.setInkColor(splattershot.get().getDefaultInstance(), ColorUtils.ORANGE))
        .title(Component.translatable("itemGroup.splatcraft_weapons"))
        .displayItems((parameters, output) -> {
            for (Item item : weapons) {
                if (!(item instanceof WeaponBaseItem<?> weapon) || !weapon.isSecret) {
                    output.accept(item);
                }
            }

            output.accept(inkClothHelmet.get());
            output.accept(inkClothChestplate.get());
            output.accept(inkClothLeggings.get());
            output.accept(inkClothBoots.get());
        }).build());

    public static final ArrayList<Item> colorTabItems = new ArrayList<>();

    public static final RegistryObject<CreativeModeTab> GROUP_COLORS = REGISTRY.register("splatcraft_colors", () -> CreativeModeTab.builder()
        .withTabsBefore(GROUP_WEAPONS.getKey())
        .icon(() -> ColorUtils.setInkColor(inkwell.get().getDefaultInstance(), ColorUtils.ORANGE))
        .title(Component.translatable("itemGroup.splatcraft_colors"))
        .displayItems((parameters, output) -> {
            for (Item item : colorTabItems) {
                for (InkColor color : SplatcraftInkColors.REGISTRY.get().getValues().stream().sorted().toList()) {
                    output.accept(ColorUtils.setColorLocked(ColorUtils.setInkColor(new ItemStack(item), color.getColor()), true));
                }

                if (!(item instanceof ColoredBlockItem coloredBlockItem) || coloredBlockItem.matchesColor()) {
                    output.accept(ColorUtils.setInverted(new ItemStack(item), true));
                }
            }
        }).build());
}
