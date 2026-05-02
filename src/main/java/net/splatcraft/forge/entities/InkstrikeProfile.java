package net.splatcraft.forge.entities;

public record InkstrikeProfile(int travelTicks, float tornadoDiameter, int tornadoDurationTicks, float damagePerTick)
{
    public static final InkstrikeProfile SINGLE = new InkstrikeProfile(30, 10.0F, 30, 4.0F);
    public static final InkstrikeProfile TRIPLE = new InkstrikeProfile(20, 6.0F, 10, 10.0F);
}
