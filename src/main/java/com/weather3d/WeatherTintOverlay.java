package com.weather3d;

import com.weather3d.conditions.Weather;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Full-screen colour wash painted over the 3D scene to sell weather intensity AND day/night.
 *
 *   Pass 1 — weather tint: rain/storm darken, snow brightens with warm fog, ash darkens warm, etc.
 *   Pass 2 — night darken: a dark blue wash whose alpha rises as dayLight falls. Because this
 *            sits ABOVE the 3D scene (not just the skybox), it actually dims the ground/trees/
 *            mobs you see in normal gameplay, not just the band of sky.
 *
 * Both eased frame-by-frame so transitions don't pop.
 */
public class WeatherTintOverlay extends Overlay
{
    private final Client client;
    private final CyclesPlugin plugin;
    private final CyclesConfig config;
    private final DayCycleController dayCycle;

    private float currentAlpha = 0f;
    private float currentR = 0f, currentG = 0f, currentB = 0f;
    private float currentNightAlpha = 0f;

    @Inject
    private WeatherTintOverlay(Client client, CyclesPlugin plugin, CyclesConfig config, DayCycleController dayCycle)
    {
        super(plugin);
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.dayCycle = dayCycle;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        Dimension dims = client.getRealDimensions();

        // ----- Weather tint pass -----
        if (config.enableWeatherTint())
        {
            Weather weather = plugin.getCurrentWeather();
            if (weather != null)
            {
                float intensity = plugin.getIntensityMultiplier();
                float strength = config.weatherTintStrength() / 100f;
                float targetAlpha = (weather.getTintMaxAlpha() / 255f) * intensity * strength;
                Color targetCol = weather.getTintColor();

                float k = 0.04f;
                currentAlpha += (targetAlpha - currentAlpha) * k;
                currentR += (targetCol.getRed()   - currentR) * k;
                currentG += (targetCol.getGreen() - currentG) * k;
                currentB += (targetCol.getBlue()  - currentB) * k;

                if (currentAlpha >= 0.005f)
                {
                    int a = clamp((int) (currentAlpha * 255));
                    int r = clamp((int) currentR);
                    int g = clamp((int) currentG);
                    int b = clamp((int) currentB);
                    graphics.setColor(new Color(r, g, b, a));
                    graphics.fillRect(0, 0, dims.width, dims.height);
                }
            }
        }

        // ----- Night darken pass -----
        // Cap at ~0.65 alpha at full night × 100% darkness so even pitch-black settings keep
        // enough scene visibility for play. Below ~0.005 we skip the paint entirely.
        if (config.enableDayNight())
        {
            float dayLight = dayCycle.getDayLightLevel();
            float darkness = config.nightDarkness() / 100f;
            float targetNightAlpha = (1f - dayLight) * darkness * 0.65f;
            currentNightAlpha += (targetNightAlpha - currentNightAlpha) * 0.04f;

            if (currentNightAlpha >= 0.005f)
            {
                int a = clamp((int) (currentNightAlpha * 255));
                graphics.setColor(new Color(6, 9, 24, a));
                graphics.fillRect(0, 0, dims.width, dims.height);
            }
        }
        else
        {
            currentNightAlpha = 0f;
        }

        return null;
    }

    private static int clamp(int v)
    {
        return Math.max(0, Math.min(255, v));
    }
}
