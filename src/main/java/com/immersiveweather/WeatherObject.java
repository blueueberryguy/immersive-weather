package com.immersiveweather;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.RuneLiteObject;

@Getter
public class WeatherObject
{
    private final RuneLiteObject runeLiteObject;
    private final int objVariant;
    /** Optional ground-shadow object that lives at z=0 directly under the main object. Only clouds use this today. */
    @Setter
    private RuneLiteObject shadowObject;

    /**
     * Drift origin in LocalPoint units. Cloud positions are recomputed each client-tick as
     * (baseX + driftX, baseY + driftY) so they can slide smoothly across the sky instead of
     * teleporting on the relocate cycle. UNSET_BASE marks "not yet initialised".
     */
    public static final int UNSET_BASE = Integer.MIN_VALUE;
    @Setter
    private int baseX = UNSET_BASE;
    @Setter
    private int baseY = UNSET_BASE;
    /** Accumulated sub-tile drift since spawn (kept as float so slow winds don't quantise). */
    @Setter
    private float driftX = 0f;
    @Setter
    private float driftY = 0f;

    public WeatherObject(RuneLiteObject runeLiteObject, int objVariant)
    {
        this.runeLiteObject = runeLiteObject;
        this.objVariant = objVariant;
    }
}
