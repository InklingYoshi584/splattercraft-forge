package net.splatcraft.forge.items.weapons;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.splatcraft.forge.entities.InkstrikeEntity;
import net.splatcraft.forge.entities.InkstrikeProfile;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkBlockUtils;

public class TripleInkstrikeSpecialItem extends InkstrikeSpecialItem
{
    public static final int POINTS_REQUIRED = 550;
    public static final int ACTIVE_TICKS = 1200;
    private static final int THROW_COUNT = 3;

    public TripleInkstrikeSpecialItem()
    {
        super(POINTS_REQUIRED);
    }

    @Override
    public boolean useSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        boolean used = super.useSpecial(level, player, specialStack, mainWeapon);
        if (!used || level.isClientSide)
            return used;

        clearTargetCenter(player);

        Vec3 look = player.getLookAngle();
        Vec3 lateral = new Vec3(-look.z, 0.0D, look.x);
        if (lateral.lengthSqr() < 1.0E-5D)
            lateral = new Vec3(1.0D, 0.0D, 0.0D);
        else lateral = lateral.normalize();

        ItemStack sourceWeapon = mainWeapon.copy();
        InkBlockUtils.InkType inkType = InkBlockUtils.getInkType(player);
        int color = ColorUtils.getPlayerColor(player);

        level.addFreshEntity(InkstrikeEntity.createLaunchEffect(level, player, sourceWeapon, inkType, color, lateral.scale(0.45D), lateral.scale(0.16D)));
        level.addFreshEntity(InkstrikeEntity.createLaunchEffect(level, player, sourceWeapon, inkType, color, lateral.scale(-0.45D), lateral.scale(-0.16D)));
        return true;
    }

    @Override
    public int getActiveTicks(ItemStack stack)
    {
        return ACTIVE_TICKS;
    }

    @Override
    protected int getThrowCount(ItemStack stack)
    {
        return THROW_COUNT;
    }

    @Override
    protected String getStatusMessageKey()
    {
        return "status.special.triple_ink_strike";
    }

    @Override
    protected String getTooltipKey()
    {
        return "item.splatcraft.triple_ink_strike.tooltip";
    }

    @Override
    protected InkstrikeProfile getProfile(ItemStack specialStack)
    {
        return InkstrikeProfile.TRIPLE;
    }

    @Override
    public void onMainWeaponUseTick(Level level, net.minecraft.world.entity.LivingEntity entity, ItemStack specialStack, ItemStack mainWeapon, int timeLeft)
    {
        if (entity instanceof Player player)
            launchBeacon(level, player, mainWeapon);
    }
}
