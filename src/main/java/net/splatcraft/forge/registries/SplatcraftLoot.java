package net.splatcraft.forge.registries;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.loot.BlueprintLootFunction;
import net.splatcraft.forge.loot.ChestLootModifier;
import net.splatcraft.forge.loot.FishingLootModifier;

import static net.splatcraft.forge.Splatcraft.MODID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = Splatcraft.MODID)
public class SplatcraftLoot
{
    protected static final DeferredRegister<Codec<? extends IGlobalLootModifier>> MODIFIER_REGISTRY = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);
    protected static final DeferredRegister<LootItemFunctionType> REGISTRY = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, MODID);

    public static final RegistryObject<LootItemFunctionType> BLUEPRINT = REGISTRY.register("blueprint_pool", () -> new LootItemFunctionType(new BlueprintLootFunction.Serializer()));
    public static final RegistryObject<Codec<FishingLootModifier>> FISHING_MODIFIER = MODIFIER_REGISTRY.register("fishing", () -> FishingLootModifier.CODEC);
    public static final RegistryObject<Codec<ChestLootModifier>> CHEST_LOOT_MODIFIER = MODIFIER_REGISTRY.register("chest_loot", () -> ChestLootModifier.CODEC);
}
