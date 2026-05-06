package net.splatcraft.forge.handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.SplatcraftConfig;
import net.splatcraft.forge.blocks.IColoredBlock;
import net.splatcraft.forge.commands.SuperJumpCommand;
import net.splatcraft.forge.client.layer.PlayerInkColoredSkinLayer;
import net.splatcraft.forge.data.SplatcraftTags;
import net.splatcraft.forge.data.capabilities.inkoverlay.InkOverlayCapability;
import net.splatcraft.forge.data.capabilities.inkoverlay.InkOverlayInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfo;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.data.capabilities.saveinfo.SaveInfoCapability;
import net.splatcraft.forge.items.InkTankItem;
import net.splatcraft.forge.items.InkWaxerItem;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.c2s.RequestPlayerInfoPacket;
import net.splatcraft.forge.network.c2s.SendPlayerOverlayPacket;
import net.splatcraft.forge.network.s2c.ReceivePlayerOverlayPacket;
import net.splatcraft.forge.network.s2c.OpenDeathRecapPacket;
import net.splatcraft.forge.network.s2c.UpdateBooleanGamerulesPacket;
import net.splatcraft.forge.network.s2c.UpdateClientColorsPacket;
import net.splatcraft.forge.network.s2c.UpdateColorScoresPacket;
import net.splatcraft.forge.network.s2c.UpdateIntGamerulesPacket;
import net.splatcraft.forge.network.s2c.UpdatePlayerInfoPacket;
import net.splatcraft.forge.network.s2c.UpdateStageListPacket;
import net.splatcraft.forge.network.s2c.UpdateWeaponSettingsPacket;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.registries.SplatcraftGameRules;
import net.splatcraft.forge.registries.SplatcraftItems;
import net.splatcraft.forge.tileentities.InkedBlockTileEntity;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.CommonUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import net.splatcraft.forge.util.PlayerCooldown;

@Mod.EventBusSubscriber
public class SplatcraftCommonHandler {
    private static final int DEATH_RECAP_RESPAWN_TICKS = 100;
    private static final HashMap<UUID, Long> ACTIVE_DEATH_RECAPS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player)) {
            return;
        }

        if (InkBlockUtils.onEnemyInk(event.getEntity())) {
            entity.setDeltaMovement(entity.getDeltaMovement().x, Math.min(entity.getDeltaMovement().y, 0.1f), entity.getDeltaMovement().z);
        }
    }


    @SubscribeEvent
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (!(event.getEntity().level().getBlockEntity(event.getPos()) instanceof InkedBlockTileEntity te)) {
            return;
        }

        BlockState savedState = te.getSavedState();
        if (event.getState().getBlock() instanceof IColoredBlock && (event.isCanceled() ||
                (event.getEntity() instanceof EnderDragon && savedState.is(BlockTags.DRAGON_IMMUNE)) ||
                (event.getEntity() instanceof WitherBoss && savedState.is(BlockTags.WITHER_IMMUNE)))) {
            ((IColoredBlock) event.getState().getBlock()).remoteInkClear(event.getEntity().level(), event.getPos());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(final PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }

        Player player = event.getEntity();
        event.getOriginal().reviveCaps(); // Mod devs should not have to do this
        PlayerInfoCapability.get(player).readNBT(PlayerInfoCapability.get(event.getOriginal()).writeNBT(new CompoundTag()));
        event.getOriginal().invalidateCaps();

        event.getOriginal().invalidateCaps();

        NonNullList<ItemStack> matchInv = PlayerInfoCapability.get(player).getMatchInventory();

        if (!matchInv.isEmpty()) {
            for (int i = 0; i < matchInv.size(); i++) {
                ItemStack stack = matchInv.get(i);
                if (!stack.isEmpty() && !putStackInSlot(player.getInventory(), stack, i) && !player.getInventory().add(stack)) {
                    player.drop(stack, true, true);
                }
            }

            PlayerInfoCapability.get(player).setMatchInventory(NonNullList.create());
        }
        PlayerCooldown.setPlayerCooldown(player, null);
        player.setInvisible(false);
        player.noPhysics = false;

        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.setCamera(serverPlayer);

        ACTIVE_DEATH_RECAPS.remove(player.getUUID());
    }

    private static boolean putStackInSlot(Inventory inventory, ItemStack stack, int i) {
        ItemStack invStack = inventory.getItem(i);

        if (invStack.isEmpty()) {
            inventory.setItem(i, stack);
            return true;
        }
        if (ItemStack.isSameItem(invStack, stack)) {
            int invCount = invStack.getCount();
            int count = Math.min(invStack.getMaxStackSize(), stack.getCount() + invStack.getCount());
            invStack.setCount(count);
            stack.shrink(count - invCount);

            return stack.isEmpty();
        }
        return false;
    }

    @SubscribeEvent
    public static void onLivingDeath(final LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        ItemStack stack = entity.getItemBySlot(EquipmentSlot.CHEST);

        if (stack.getItem() instanceof InkTankItem) {
            ((InkTankItem) stack.getItem()).refill(stack);
        }

        if (entity instanceof Player player)
        {
            applyDeathSpecialPenalty(player);
            sendDeathRecap(player, event);
        }
    }

    private static void sendDeathRecap(Player player, LivingDeathEvent event)
    {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !SplatcraftGameRules.getBooleanRuleValue(player.level(), SplatcraftGameRules.DEATH_RECAP))
            return;

        Component deathMessage = player.getCombatTracker().getDeathMessage();
        if (deathMessage == null)
            deathMessage = event.getSource().getLocalizedDeathMessage(player);

        Player killer = event.getSource().getEntity() instanceof Player sourcePlayer ? sourcePlayer : null;
        ResourceLocation dimension = player.level().dimension().location();

        ACTIVE_DEATH_RECAPS.put(serverPlayer.getUUID(), serverPlayer.level().getGameTime() + DEATH_RECAP_RESPAWN_TICKS + 20L);
        serverPlayer.setInvisible(true);
        serverPlayer.noPhysics = true;

        SplatcraftPacketHandler.sendToPlayer(
                new OpenDeathRecapPacket(
                        deathMessage,
                        player.position(),
                        dimension,
                        killer != null ? killer.getUUID() : null,
                        ColorUtils.getPlayerColor(player),
                        DEATH_RECAP_RESPAWN_TICKS),
                serverPlayer);
    }

    private static Vec3 getRecapFocus(Entity target)
    {
        return new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.75D, target.getZ());
    }

    private static void applyDeathSpecialPenalty(Player player)
    {
        if (!PlayerInfoCapability.hasCapability(player))
            return;

        PlayerInfo info = PlayerInfoCapability.get(player);
        ItemStack weaponStack = player.getMainHandItem();

        if (info.hasActiveSpecial())
        {
            int sourceSlot = info.getSpecialSourceSlot();
            if (sourceSlot >= 0 && sourceSlot < player.getInventory().getContainerSize())
                weaponStack = player.getInventory().getItem(sourceSlot);

            if (weaponStack.getItem() instanceof WeaponBaseItem<?>)
                WeaponBaseItem.setSpecialPoints(weaponStack, 0);
            return;
        }

        if (weaponStack.getItem() instanceof WeaponBaseItem<?>)
            WeaponBaseItem.setSpecialPoints(weaponStack, WeaponBaseItem.getSpecialPoints(weaponStack) / 2);
    }

    public static boolean shouldHoldServerPlayerDeath(ServerPlayer player)
    {
        Long expiresAt = ACTIVE_DEATH_RECAPS.get(player.getUUID());
        return expiresAt != null && player.level().getGameTime() <= expiresAt;
    }

    @SubscribeEvent
    public static void onLivingDeathDrops(LivingDropsEvent event) {
        //handle inked wool drops
        if (event.getEntity() instanceof Sheep && InkOverlayCapability.hasCapability(event.getEntity())) {
            InkOverlayInfo info = InkOverlayCapability.get(event.getEntity());


            if (info.getWoolColor() > -1) {
                for (ItemEntity itemEntity : event.getDrops())
                {
                    ItemStack stack = itemEntity.getItem();
                    if (stack.is(ItemTags.WOOL)) {
                        itemEntity.setItem(ColorUtils.setColorLocked(ColorUtils.setInkColor(new ItemStack(SplatcraftItems.inkedWool.get(), stack.getCount()), info.getWoolColor()), true));
                    }
                }
            }
        }

        //Handle keepMatchItems
        if (event.getEntity() instanceof Player player) {
            NonNullList<ItemStack> matchInv = PlayerInfoCapability.get(player).getMatchInventory();

            event.getDrops().removeIf(drop -> matchInv.contains(drop.getItem()));

            for (int i = 0; i < matchInv.size(); i++) {
                ItemStack stack = matchInv.get(i);
                if (!stack.isEmpty() && !putStackInSlot(player.getInventory(), stack, i)) {
                    player.getInventory().add(stack);
                }
            }

        }
    }

    @SubscribeEvent
    public static void onPlayerAboutToDie(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getEntity().getHealth() - event.getAmount() > 0) {
            return;
        }

        if (!player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) && SplatcraftGameRules.getLocalizedRule(player.level(), player.blockPosition(), SplatcraftGameRules.KEEP_MATCH_ITEMS)) {
            PlayerInfo playerCapability;
            try {
                playerCapability = PlayerInfoCapability.get(player);
            } catch (NullPointerException e) {
                return;
            }

            NonNullList<ItemStack> matchInv = NonNullList.withSize(player.getInventory().getContainerSize(), ItemStack.EMPTY);

            for (int i = 0; i < matchInv.size(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(SplatcraftTags.Items.MATCH_ITEMS)) {
                    matchInv.set(i, stack);
                }
            }

            playerCapability.setMatchInventory(matchInv);
        }
    }

    public static final HashMap<UUID, byte[]> COLOR_SKIN_OVERLAY_SERVER_CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        SplatcraftPacketHandler.sendToPlayer(new UpdateBooleanGamerulesPacket(SplatcraftGameRules.booleanRules), player);
        SplatcraftPacketHandler.sendToPlayer(new UpdateIntGamerulesPacket(SplatcraftGameRules.intRules), player);
        SplatcraftPacketHandler.sendToPlayer(new UpdateWeaponSettingsPacket(), player);

        int[] criteriaColors = new int[ScoreboardHandler.getCriteriaKeySet().size()];
        int criteriaColorIndex = 0;
        for (int criteriaColor : ScoreboardHandler.getCriteriaKeySet()) {
            criteriaColors[criteriaColorIndex++] = criteriaColor;
        }

        TreeMap<String, Integer> playerColors = new TreeMap<>();

        for (Player p : event.getEntity().level().players()) {
            if (PlayerInfoCapability.hasCapability(p)) {
                playerColors.put(p.getDisplayName().getString(), PlayerInfoCapability.get(p).getColor());
            }
        }

        SplatcraftPacketHandler.sendToAll(new UpdateClientColorsPacket(event.getEntity().getDisplayName().getString(), PlayerInfoCapability.get(event.getEntity()).getColor()));
        SplatcraftPacketHandler.sendToPlayer(new UpdateClientColorsPacket(playerColors), player);
        SplatcraftPacketHandler.sendToPlayer(new UpdateColorScoresPacket(true, true, criteriaColors), player);
        SplatcraftPacketHandler.sendToPlayer(new UpdateStageListPacket(SaveInfoCapability.get(event.getEntity().level().getServer()).getStages()), player);
        if (!COLOR_SKIN_OVERLAY_SERVER_CACHE.isEmpty()) {
            COLOR_SKIN_OVERLAY_SERVER_CACHE.forEach(((uuid, bytes) -> SplatcraftPacketHandler.sendToPlayer(new ReceivePlayerOverlayPacket(uuid, bytes), player)));
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientLogIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LocalPlayer player = event.getPlayer();
        File file = Paths.get(SplatcraftConfig.Client.inkColoredSkinLayerPath).toFile();
        if (player != null && file.exists()) {
            try {
                SplatcraftPacketHandler.sendToServer(new SendPlayerOverlayPacket(player.getUUID(), file));
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PlayerInkColoredSkinLayer.TEXTURES.values().forEach(Minecraft.getInstance().getTextureManager()::release);
        PlayerInkColoredSkinLayer.TEXTURES.clear();

        if (event.getPlayer() != null) {
            SplatcraftPacketHandler.sendToServer(new SendPlayerOverlayPacket(event.getPlayer().getUUID(), new byte[0]));
        }
    }

    @Deprecated
    public static final HashMap<Player, Integer> LOCAL_COLOR = new HashMap<>();

    @SubscribeEvent
    public static void capabilityUpdateEvent(TickEvent.PlayerTickEvent event) {
        if (event.side.isServer() && event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer serverPlayer) {
            Long recapExpires = ACTIVE_DEATH_RECAPS.get(serverPlayer.getUUID());
            if (recapExpires != null && (!serverPlayer.isDeadOrDying() || serverPlayer.level().getGameTime() > recapExpires)) {
                ACTIVE_DEATH_RECAPS.remove(serverPlayer.getUUID());
                if (serverPlayer.getCamera() != serverPlayer)
                    serverPlayer.setCamera(serverPlayer);
                serverPlayer.setInvisible(false);
                serverPlayer.noPhysics = false;
            }
        }

        if (PlayerInfoCapability.hasCapability(event.player)) {
            PlayerInfo info = PlayerInfoCapability.get(event.player);
            if (event.player.deathTime <= 0 && !info.isInitialized()) {
                info.setInitialized(true);
                info.setPlayer(event.player);
                if (LOCAL_COLOR.containsKey(event.player)) {
                    info.setColor(LOCAL_COLOR.get(event.player));
                }

                if (event.side.isClient()) {
                    SplatcraftPacketHandler.sendToServer(new RequestPlayerInfoPacket(event.player));
                } else if (event.player instanceof ServerPlayer serverPlayer) {
                    SuperJumpCommand.syncSpawnPosition(serverPlayer);
                }
            }

            if (event.side.isServer()) {
                ItemStack inkBand = CommonUtils.getItemInInventory(event.player, itemStack -> itemStack.is(SplatcraftTags.Items.INK_BANDS) && InkBlockUtils.hasInkType(itemStack));

                if (!info.getInkBand().equals(inkBand, false)) {
                    info.setInkBand(inkBand);
                    SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(event.player), event.player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        Level level = event.level;
        if (level.isClientSide()) {
            return;
        }
        for (Map.Entry<Integer, Boolean> rule : SplatcraftGameRules.booleanRules.entrySet()) {
            boolean levelValue = level.getGameRules().getBoolean(SplatcraftGameRules.getRuleFromIndex(rule.getKey()));
            if (rule.getValue() != levelValue) {
                SplatcraftGameRules.booleanRules.put(rule.getKey(), levelValue);
                SplatcraftPacketHandler.sendToAll(new UpdateBooleanGamerulesPacket(SplatcraftGameRules.getRuleFromIndex(rule.getKey()), rule.getValue()));
            }
        }
        for (Map.Entry<Integer, Integer> rule : SplatcraftGameRules.intRules.entrySet()) {
            int levelValue = level.getGameRules().getInt(SplatcraftGameRules.getRuleFromIndex(rule.getKey()));
            if (rule.getValue() != levelValue) {
                SplatcraftGameRules.intRules.put(rule.getKey(), levelValue);
                SplatcraftPacketHandler.sendToAll(new UpdateIntGamerulesPacket(SplatcraftGameRules.getRuleFromIndex(rule.getKey()), rule.getValue()));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (InkOverlayCapability.hasCapability(entity)) {
            if (entity.isInWater()) {
                InkOverlayCapability.get(entity).setAmount(0);
            } else {
                InkOverlayCapability.get(entity).addAmount(-0.01f);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getItemStack().getItem() instanceof InkWaxerItem) {
            ((InkWaxerItem) event.getItemStack().getItem()).onBlockStartBreak(event.getItemStack(), event.getPos(), event.getLevel());
        }
    }

}
