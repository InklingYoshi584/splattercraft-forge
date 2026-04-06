package net.splatcraft.forge.registries;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import net.splatcraft.forge.Splatcraft;

public class SplatcraftDamageTypes {
    public static final ResourceKey<DamageType> ENEMY_INK = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Splatcraft.MODID, "enemy_ink"));
    public static final ResourceKey<DamageType> INK_SPLAT = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Splatcraft.MODID, "ink_splat"));
    public static final ResourceKey<DamageType> OUT_OF_STAGE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Splatcraft.MODID, "out_of_stage"));
    public static final ResourceKey<DamageType> ROLL_CRUSH = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Splatcraft.MODID, "roll_crush"));
    public static final ResourceKey<DamageType> WATER = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Splatcraft.MODID, "water"));

    public static Holder<DamageType> get(Level level, ResourceKey<DamageType> key) {
        return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
    }

    public static DamageSource of(Level level, ResourceKey<DamageType> key) {
        return new DamageSource(get(level, key));
    }
}