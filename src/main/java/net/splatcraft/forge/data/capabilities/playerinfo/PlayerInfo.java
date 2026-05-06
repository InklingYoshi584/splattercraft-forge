package net.splatcraft.forge.data.capabilities.playerinfo;

import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.splatcraft.forge.handlers.SplatcraftCommonHandler;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;
import net.splatcraft.forge.util.PlayerCharge;
import net.splatcraft.forge.util.PlayerCooldown;

public class PlayerInfo
{
    private int color;
    private boolean isSquid = false;
    private boolean initialized = false;
    private NonNullList<ItemStack> matchInventory = NonNullList.create();
    private PlayerCooldown playerCooldown = null;
    private PlayerCharge playerCharge = null;
    private Player player;

    private ItemStack inkBand = ItemStack.EMPTY;
    private BlockPos superJumpSpawnPos = null;
    private String superJumpSpawnDimension = "";
    private int specialSourceSlot = -1;
    private int specialTicksRemaining = 0;
    private int specialMaxTicks = 0;
    private int specialWindupTicksRemaining = 0;
    private int specialWindupMaxTicks = 0;
    private CompoundTag specialData = new CompoundTag();
    private int inkArmorInvincibilityTicks = 0;
    private boolean inkRailRiding = false;
    private boolean inkRailHidden = false;
    private boolean infiniteSpecial = false;

    public PlayerInfo(int defaultColor)
    {
        color = defaultColor;
    }

    public PlayerInfo()
    {
        this(ColorUtils.getRandomStarterColor());
    }
    
    public boolean isInitialized()
    {
        return initialized;
    }
    
    public void setPlayer(Player entity) {
        this.player = entity;
    }
    
    public void setInitialized(boolean init)
    {
        initialized = init;
    }

    
    public int getColor()
    {
        return color;
    }
    
    public void setColor(int color)
    {
        this.color = color;

        if(player != null)
            SplatcraftCommonHandler.LOCAL_COLOR.put(player, color);
    }

    public boolean isSquid()
    {
        return isSquid;
    }
    
    public void setIsSquid(boolean isSquid)
    {
        this.isSquid = isSquid;
    }

    public ItemStack getInkBand() {
        return inkBand;
    }

    public BlockPos getSuperJumpSpawnPos()
    {
        return superJumpSpawnPos;
    }

    public String getSuperJumpSpawnDimension()
    {
        return superJumpSpawnDimension;
    }

    public void setSuperJumpSpawn(BlockPos pos, String dimension)
    {
        superJumpSpawnPos = pos;
        superJumpSpawnDimension = dimension == null ? "" : dimension;
    }

    public void clearSuperJumpSpawn()
    {
        superJumpSpawnPos = null;
        superJumpSpawnDimension = "";
    }

    
    public void setInkBand(ItemStack stack) {
        inkBand = stack;
    }
    
    public InkBlockUtils.InkType getInkType() {
        return InkBlockUtils.getInkTypeFromStack(inkBand);
    }

    public int getSpecialSourceSlot()
    {
        return specialSourceSlot;
    }

    public void startSpecial(int sourceSlot, int windupTicks, int activeTicks)
    {
        this.specialSourceSlot = sourceSlot;
        this.specialWindupTicksRemaining = Math.max(windupTicks, 0);
        this.specialWindupMaxTicks = Math.max(windupTicks, 0);
        this.specialTicksRemaining = Math.max(activeTicks, 0);
        this.specialMaxTicks = Math.max(activeTicks, 0);
        this.specialData = new CompoundTag();
    }

    public boolean hasActiveSpecial()
    {
        return specialSourceSlot >= 0 && (specialWindupTicksRemaining > 0 || specialTicksRemaining > 0);
    }

    public boolean isSpecialInWindup()
    {
        return specialWindupTicksRemaining > 0;
    }

    public int getSpecialTicksRemaining()
    {
        return specialTicksRemaining;
    }

    public int getSpecialMaxTicks()
    {
        return specialMaxTicks;
    }

    public int getSpecialWindupTicksRemaining()
    {
        return specialWindupTicksRemaining;
    }

    public int getSpecialWindupMaxTicks()
    {
        return specialWindupMaxTicks;
    }

    public void tickSpecialWindup()
    {
        specialWindupTicksRemaining = Math.max(0, specialWindupTicksRemaining - 1);
    }

    public void tickActiveSpecial()
    {
        specialTicksRemaining = Math.max(0, specialTicksRemaining - 1);
    }

    public void setSpecialTicksRemaining(int specialTicksRemaining)
    {
        this.specialTicksRemaining = Math.max(0, specialTicksRemaining);
    }

    public void clearSpecial()
    {
        specialSourceSlot = -1;
        specialTicksRemaining = 0;
        specialMaxTicks = 0;
        specialWindupTicksRemaining = 0;
        specialWindupMaxTicks = 0;
        specialData = new CompoundTag();
        infiniteSpecial = false;
    }

    public CompoundTag getSpecialData()
    {
        return specialData;
    }

    public int getInkArmorInvincibilityTicks()
    {
        return inkArmorInvincibilityTicks;
    }

    public boolean hasInkArmorInvincibility()
    {
        return inkArmorInvincibilityTicks > 0;
    }

    public void setInkArmorInvincibilityTicks(int inkArmorInvincibilityTicks)
    {
        this.inkArmorInvincibilityTicks = Math.max(0, inkArmorInvincibilityTicks);
    }

    public void tickInkArmorInvincibility()
    {
        inkArmorInvincibilityTicks = Math.max(0, inkArmorInvincibilityTicks - 1);
    }

    public boolean isInkRailRiding()
    {
        return inkRailRiding;
    }

    public void setInkRailRiding(boolean inkRailRiding)
    {
        if (this.inkRailRiding != inkRailRiding)
        {
            this.inkRailRiding = inkRailRiding;
            if (player != null)
                player.refreshDimensions();
        }
    }

    public boolean isInkRailHidden()
    {
        return inkRailHidden;
    }

    public void setInkRailHidden(boolean inkRailHidden)
    {
        if (this.inkRailHidden != inkRailHidden)
        {
            this.inkRailHidden = inkRailHidden;
            if (player != null)
                player.refreshDimensions();
        }
    }

    public boolean isInfiniteSpecial()
    {
        return infiniteSpecial;
    }

    public void setInfiniteSpecial(boolean infinite)
    {
        this.infiniteSpecial = infinite;
    }

    public NonNullList<ItemStack> getMatchInventory()
    {
        return matchInventory;
    }

    public void setMatchInventory(NonNullList<ItemStack> inventory)
    {
        this.matchInventory = inventory;
    }

    public PlayerCooldown getPlayerCooldown()
    {
        return playerCooldown;
    }

    
    public void setPlayerCooldown(PlayerCooldown cooldown)
    {
        this.playerCooldown = cooldown;
    }

    
    public boolean hasPlayerCooldown()
    {
        return playerCooldown != null && playerCooldown.getTime() > 0;
    }

    
    public PlayerCharge getPlayerCharge()
    {
        return playerCharge;
    }
    
    public void setPlayerCharge(PlayerCharge charge)
    {
        playerCharge = charge;
    }
    
    public CompoundTag writeNBT(CompoundTag nbt)
    {
        nbt.putInt("Color", getColor());
        nbt.putBoolean("IsSquid", isSquid());
        nbt.putString("InkType", getInkType().getSerializedName());
        nbt.putBoolean("Initialized", initialized);

        if(!inkBand.isEmpty())
            nbt.put("InkBand", getInkBand().serializeNBT());

        if (superJumpSpawnPos != null)
            nbt.put("SuperJumpSpawnPos", NbtUtils.writeBlockPos(superJumpSpawnPos));
        if (!superJumpSpawnDimension.isEmpty())
            nbt.putString("SuperJumpSpawnDimension", superJumpSpawnDimension);

        nbt.putInt("SpecialSourceSlot", specialSourceSlot);
        nbt.putInt("SpecialTicksRemaining", specialTicksRemaining);
        nbt.putInt("SpecialMaxTicks", specialMaxTicks);
        nbt.putInt("SpecialWindupTicksRemaining", specialWindupTicksRemaining);
        nbt.putInt("SpecialWindupMaxTicks", specialWindupMaxTicks);
        nbt.putBoolean("InkRailRiding", inkRailRiding);
        nbt.putBoolean("InkRailHidden", inkRailHidden);
        if (!specialData.isEmpty())
            nbt.put("SpecialData", specialData.copy());

        if (!matchInventory.isEmpty())
        {
            CompoundTag invNBT = new CompoundTag();
            ContainerHelper.saveAllItems(invNBT, matchInventory);
            nbt.put("MatchInventory", invNBT);
        }

        if (playerCooldown != null)
        {
            CompoundTag cooldownNBT = new CompoundTag();
            playerCooldown.writeNBT(cooldownNBT);
            nbt.put("PlayerCooldown", cooldownNBT);
        }

        return nbt;
    }
    
    public void readNBT(CompoundTag nbt)
    {
        setColor(ColorUtils.getColorFromNbt(nbt));
        setIsSquid(nbt.getBoolean("IsSquid"));
        setInitialized(nbt.getBoolean("Initialized"));

        if(nbt.contains("InkBand"))
            setInkBand(ItemStack.of(nbt.getCompound("InkBand")));
        else setInkBand(ItemStack.EMPTY);

        if (nbt.contains("SuperJumpSpawnPos"))
            superJumpSpawnPos = NbtUtils.readBlockPos(nbt.getCompound("SuperJumpSpawnPos"));
        else superJumpSpawnPos = null;
        superJumpSpawnDimension = nbt.getString("SuperJumpSpawnDimension");

        specialSourceSlot = nbt.getInt("SpecialSourceSlot");
        specialTicksRemaining = nbt.getInt("SpecialTicksRemaining");
        specialMaxTicks = nbt.getInt("SpecialMaxTicks");
        specialWindupTicksRemaining = nbt.getInt("SpecialWindupTicksRemaining");
        specialWindupMaxTicks = nbt.getInt("SpecialWindupMaxTicks");
        setInkRailRiding(nbt.getBoolean("InkRailRiding"));
        setInkRailHidden(nbt.getBoolean("InkRailHidden"));
        specialData = nbt.contains("SpecialData") ? nbt.getCompound("SpecialData").copy() : new CompoundTag();

        if (nbt.contains("MatchInventory"))
        {
            NonNullList<ItemStack> nbtInv = NonNullList.withSize(41, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(nbt.getCompound("MatchInventory"), nbtInv);
            setMatchInventory(nbtInv);
        }

        if (nbt.contains("PlayerCooldown"))
        {
            setPlayerCooldown(PlayerCooldown.readNBT(nbt.getCompound("PlayerCooldown")));
        }
    }
}
