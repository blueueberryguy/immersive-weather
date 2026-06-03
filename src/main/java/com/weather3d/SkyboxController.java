package com.weather3d;

import com.weather3d.conditions.Weather;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;

/**
 * Drives the in-client skybox colour so the sky matches the active weather and intensity.
 *
 * Why this exists: the stock RuneLite Skybox plugin paints a static colour per-region. To make
 * weather feel like it actually changes the sky, we override that colour every frame and ease
 * between target colours so transitions read as cinematic rather than snappy.
 */
@Singleton
public class SkyboxController
{
    @Inject
    private Client client;
    @Inject
    private CyclesPlugin plugin;
    @Inject
    private CyclesConfig config;
    @Inject
    private DayCycleController dayCycle;

    private boolean initialised = false;
    private float curR, curG, curB;

    public void reset()
    {
        initialised = false;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            initialised = false;
        }
    }

    @Subscribe
    public void onBeforeRender(BeforeRender r)
    {
        if (!config.enableSkybox() || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        Weather weather = plugin.getCurrentWeather();
        if (weather == null)
        {
            return;
        }

        Color target = computeTargetColour(weather);
        if (!initialised)
        {
            curR = target.getRed();
            curG = target.getGreen();
            curB = target.getBlue();
            initialised = true;
        }
        else
        {
            // Ease toward the target. Speed 1 = ~0.005 per frame (very slow drift),
            // Speed 20 = ~0.1 per frame (snaps inside ~half a second at 60fps).
            float t = Math.min(1f, Math.max(0.005f, config.skyTransitionSpeed() * 0.005f));
            curR += (target.getRed()   - curR) * t;
            curG += (target.getGreen() - curG) * t;
            curB += (target.getBlue()  - curB) * t;
        }

        int rgb = (clamp((int) curR) << 16) | (clamp((int) curG) << 8) | clamp((int) curB);
        client.setSkyboxColor(rgb);
    }

    private Color computeTargetColour(Weather weather)
    {
        Color base = weather.getSkyColor();
        float intensity = plugin.getIntensityMultiplier();

        // Heavier weather pulls the sky darker (rain/storm) or brighter-foggy (snow).
        if (weather == Weather.RAINY || weather == Weather.STORMY)
        {
            // multiply down by up to 50% based on intensity
            float darken = 1f - 0.45f * intensity;
            base = new Color(
                clamp((int) (base.getRed() * darken)),
                clamp((int) (base.getGreen() * darken)),
                clamp((int) (base.getBlue() * darken))
            );
        }
        else if (weather == Weather.SNOWY)
        {
            float toward = 0.35f * intensity;
            base = blendToward(base, new Color(232, 236, 242), toward);
        }
        else if (weather == Weather.FOGGY)
        {
            float toward = 0.25f * intensity;
            base = blendToward(base, new Color(195, 198, 202), toward);
        }
        else if (weather == Weather.ASHFALL)
        {
            float darken = 1f - 0.3f * intensity;
            base = new Color(
                clamp((int) (base.getRed() * darken)),
                clamp((int) (base.getGreen() * darken)),
                clamp((int) (base.getBlue() * darken))
            );
        }

        // Aurora overlays its own purple/blue base on top of whatever the weather chose, plus
        // a shifting green tint at high intensity. We blend instead of replacing so aurora
        // stacked on a cloudy night still feels like "cloudy night with aurora", not "aurora".
        if (weather == Weather.AURORA)
        {
            float auroraStrength = (config.auroraIntensity() / 100f);
            Color auroraBase = new Color(34, 18, 70);
            base = blendToward(base, auroraBase, 0.5f);
            Color auroraGreen = new Color(40, 180, 130);
            base = blendToward(base, auroraGreen, 0.25f * auroraStrength);
        }

        // Day/night cycle: at full night blend toward a deep navy clamped by nightDarkness so
        // even pitch-black settings keep enough sky luminance for gameplay.
        if (config.enableDayNight())
        {
            float dayLight = dayCycle.getDayLightLevel();
            float darkness = config.nightDarkness() / 100f;
            float nightWeight = (1f - dayLight) * darkness;
            Color nightSky = new Color(10, 12, 30);
            base = blendToward(base, nightSky, nightWeight);
        }

        return base;
    }

    private static Color blendToward(Color from, Color to, float t)
    {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
            clamp((int) (from.getRed()   + (to.getRed()   - from.getRed())   * t)),
            clamp((int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t)),
            clamp((int) (from.getBlue()  + (to.getBlue()  - from.getBlue())  * t))
        );
    }

    private static int clamp(int v)
    {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
