package net.splatcraft.forge.worldgen;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.CountConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.worldgen.features.CrateFeature;
import net.splatcraft.forge.worldgen.features.SardiniumDepositFeature;

public class SplatcraftOreGen {
    public static final DeferredRegister<Feature<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.FEATURES, Splatcraft.MODID);

    public static final RegistryObject<Feature<CountConfiguration>> crate_feature = REGISTRY.register("crate", () -> new CrateFeature(CountConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> sardinium_deposit_feature = REGISTRY.register("sardinium_deposit", () -> new SardiniumDepositFeature(NoneFeatureConfiguration.CODEC));

    public static void registerOres() {
    }
}
