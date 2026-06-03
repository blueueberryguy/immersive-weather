package com.weather3d;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Drives the day/night cycle off real wall-clock time, independent of weather.
 *
 * Phase is computed as (now % periodMs) / periodMs and runs 0..1 every cycle. The light level
 * is a half-cosine wave on that phase so noon and midnight are smooth peaks/troughs rather
 * than abrupt cuts — players see dusk and dawn transitions as a continuous fade.
 *
 *   phase 0.00 → light 0.0 (midnight)
 *   phase 0.25 → light 0.5 (sunrise)
 *   phase 0.50 → light 1.0 (noon)
 *   phase 0.75 → light 0.5 (sunset)
 */
@Singleton
public class DayCycleController
{
    @Inject
    private CyclesConfig config;

    /** 0.0 = full night, 1.0 = full day. Smooth across the cycle so dawn/dusk feel gradual. */
    public float getDayLightLevel()
    {
        if (!config.enableDayNight())
        {
            return 1.0f;
        }
        // Manual override for previewing / testing weather combinations under a fixed time of day.
        switch (config.timeOfDay())
        {
            case DAY:   return 1.0f;
            case DUSK:  return 0.5f;
            case NIGHT: return 0.0f;
            default:
            case DYNAMIC:
                break;
        }
        long periodMs = (long) config.dayNightPeriodMinutes() * 60_000L;
        if (periodMs <= 0)
        {
            return 1.0f;
        }
        long now = System.currentTimeMillis();
        float phase = (float) ((now % periodMs) / (double) periodMs);
        // 0.5 + 0.5*sin(2π*phase - π/2): minimum at phase 0 (midnight), maximum at phase 0.5 (noon).
        return 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * phase - Math.PI / 2.0);
    }

    public boolean isNight()
    {
        return config.enableDayNight() && getDayLightLevel() < 0.4f;
    }

    /** True only at the deepest part of night, used to clamp how dark the sky can drift. */
    public boolean isDeepNight()
    {
        return config.enableDayNight() && getDayLightLevel() < 0.15f;
    }
}
