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
 * A full-screen colour wash painted over the 3D scene to sell weather intensity.
 *
 * Rain & storms drag a dark slate over everything (the world "feels" overcast).
 * Snow lays down a soft warm-white fog so things glow but visibility drops.
 * Ash/fog get their own gentle tints. The alpha eases in/out so weather changes don't pop.
 */
public class WeatherTintOverlay extends Overlay
{
    private final Client client;
    private final CyclesPlugin plugin;
    private final CyclesConfig config;

    private float currentAlpha = 0f;
    private float currentR = 0f, currentG = 0f, currentB = 0f;

    @Inject
    private WeatherTintOverlay(Client client, CyclesPlugin plugin, CyclesConfig config)
    {
        super(plugin);
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.enableWeatherTint())
        {
            return null;
        }

        Weather weather = plugin.getCurrentWeather();
        if (weather == null)
        {
            return null;
        }

        float intensity = plugin.getIntensityMultiplier();
        float strength = config.weatherTintStrength() / 100f;
        float targetAlpha = (weather.getTintMaxAlpha() / 255f) * intensity * strength;
        Color targetCol = weather.getTintColor();

        // ease toward target each frame (frame-rate sensitive but acceptable for an ambience effect)
        float k = 0.04f;
        currentAlpha += (targetAlpha - currentAlpha) * k;
        currentR += (targetCol.getRed()   - currentR) * k;
        currentG += (targetCol.getGreen() - currentG) * k;
        currentB += (targetCol.getBlue()  - currentB) * k;

        if (currentAlpha < 0.005f)
        {
            return null;
        }

        Dimension dims = client.getRealDimensions();
        int a = Math.max(0, Math.min(255, (int) (currentAlpha * 255)));
        int r = Math.max(0, Math.min(255, (int) currentR));
        int g = Math.max(0, Math.min(255, (int) currentG));
        int b = Math.max(0, Math.min(255, (int) currentB));

        graphics.setColor(new Color(r, g, b, a));
        graphics.fillRect(0, 0, dims.width, dims.height);
        return null;
    }
}
