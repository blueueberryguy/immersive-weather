package com.immersiveweather.conditions;

import com.immersiveweather.audio.SoundEffect;
import lombok.Getter;
import net.runelite.client.util.ImageUtil;

import java.awt.Color;
import java.awt.image.BufferedImage;

@Getter
public enum Weather {
    ASHFALL("Ashfall", "/Weather - Ashfall.png", "/Weather - Ashfall - Mini.png",true, 3, 60, false, null,
            1200, 800, 4,
            new Color(60, 30, 22), new Color(20, 8, 4), 130),
    AURORA("Aurora", "/Weather - Cosmos.png", "/Weather - Cosmos - Mini.png", true, 3, 60, false, null,
            600, 400, 2000,
            new Color(28, 14, 60), new Color(60, 200, 140), 70),
    CLOUDY("Cloudy", "/Weather - Cloudy.png", "/Weather - Cloudy - Mini.png",true, 3, 300, false, null,
            1000, 600, 200,
            new Color(118, 130, 150), new Color(10, 14, 22), 40),
    STARRY("Otherworldly", "/Weather - Cosmos.png", "/Weather - Cosmos - Mini.png", true, 3, 60, false, null,
            2000, 1000, 8,
            new Color(8, 10, 36), new Color(40, 30, 80), 160),
    COVERED("Sheltered", "/Weather - Covered.png", "/Weather - Covered - Mini.png", false, 1, 60, false, null,
            0, 0, 1,
            new Color(140, 150, 160), new Color(0, 0, 0), 0),
    FOGGY("Foggy", "/Weather - Foggy.png", "/Weather - Foggy - Mini.png", true, 3, 60, false, null,
            1800, 1100, 200,
            new Color(180, 184, 190), new Color(180, 184, 190), 70),
    PARTLY_CLOUDY("Partly Cloudy", "/Weather - Partly Cloudy.png", "/Weather - Partly Cloudy - Mini.png", true, 3, 300, false, null,
            300, 200, 200,
            new Color(150, 175, 205), new Color(10, 14, 22), 15),
    RAINY("Raining", "/Weather - Raining.png", "/Weather - Raining - Mini.png", true, 3, 60, true, SoundEffect.RAIN,
            2000, 1200, 2,
            new Color(78, 88, 102), new Color(8, 12, 18), 130),
    SNOWY("Snowing", "/Weather - Snow.png", "/Weather - Snow - Mini.png", true, 3, 60, true, SoundEffect.WIND,
            1800, 1200, 4,
            new Color(195, 200, 210), new Color(220, 225, 232), 90),
    STORMY("Stormy", "/Weather - Stormy.png", "/Weather - Stormy - Mini.png", true, 3, 60, true, SoundEffect.THUNDERSTORM,
            3000, 1400, 1,
            new Color(38, 42, 52), new Color(0, 0, 0), 180),
    SUNNY("Clear", "/Weather - Sunny.png", "/Weather - Sunny - Mini.png", false, 1, 60, false, null,
            0, 0, 1,
            new Color(135, 200, 235), new Color(0, 0, 0), 0),
    ;

    private final String name;
    private final BufferedImage conditionImage;
    private final BufferedImage miniConditionImage;
    private final boolean hasPrecipitation;
    private final int modelVariety;
    private final int objRadius;
    private final boolean hasSound;
    private final SoundEffect soundEffect;
    private final int maxObjects;
    private final int maxObjectVolume;
    private final int changeRate;
    /** Base sky colour the skybox eases toward when this weather is active. */
    private final Color skyColor;
    /** Tint colour layered on top of the screen (alpha controlled by tintMaxAlpha). */
    private final Color tintColor;
    /** Max alpha (0-255) of the screen tint when weather is at full density. */
    private final int tintMaxAlpha;

    Weather(String name, String imageFile, String miniImageFile, boolean hasPrecipitation, int modelVariety, int objRadius, boolean hasSound, SoundEffect soundEffect, int maxObjects, int maxObjectVolume, int changeRate, Color skyColor, Color tintColor, int tintMaxAlpha)
    {
        this.name = name;
        this.conditionImage = ImageUtil.loadImageResource(getClass(), imageFile);
        this.miniConditionImage = ImageUtil.loadImageResource(getClass(), miniImageFile);
        this.hasPrecipitation = hasPrecipitation;
        this.modelVariety = modelVariety;
        this.objRadius = objRadius;
        this.hasSound = hasSound;
        this.soundEffect = soundEffect;
        this.maxObjects = maxObjects;
        this.maxObjectVolume = maxObjectVolume;
        this.changeRate = changeRate;
        this.skyColor = skyColor;
        this.tintColor = tintColor;
        this.tintMaxAlpha = tintMaxAlpha;
    }
}
