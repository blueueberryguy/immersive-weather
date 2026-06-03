package com.weather3d;

import com.weather3d.conditions.Weather;
import lombok.Getter;
import net.runelite.api.*;

import javax.inject.Inject;
import java.util.Arrays;

@Getter
public class ModelHandler
{
    @Inject
    private Client client;
    @Inject
    private CyclesConfig config;

    private Model ashModel;
    private Model ashModel2;
    private Model ashModel3;
    private Model cloudModel;
    private Model cloudModel2;
    private Model cloudModel3;
    private Model cloudModelTP;
    private Model cloudModelTP2;
    private Model cloudModelTP3;
    private Model cloudShadowModel;
    private Model cloudShadowModel2;
    private Model cloudShadowModel3;
    private Model fogModel;
    private Model fogModel2;
    private Model fogModel3;
    private Model rainModel;
    private Model rainModel2;
    private Model rainModel3;
    private Model snowModel;
    private Model snowModel2;
    private Model snowModel3;
    private Model starModel;
    private Model starModel2;
    private Model starModel3;
    private Model starModelTP;
    private Model starModelTP2;
    private Model starModelTP3;
    private Model stormModel;
    private Model stormModel2;
    private Model stormModel3;
    private Animation ashAnimation;
    private Animation cloudAnimation;
    private Animation fogAnimation;
    private Animation rainAnimation;
    private Animation snowAnimation;
    private Animation starAnimation;
    private final int ASH_MODEL = 27835;
    private final int ASH_ANIMATION = 7000;
    private final int CLOUD_MODEL = 4086;
    private final int CLOUD_ANIMATION = 6470;
    private final int FOG_MODEL = 29290;
    private final int FOG_ANIMATION = 4516;
    private final int RAIN_MODEL = 15524;
    private final int RAIN_ANIMATION = 7001;
    private final int SNOW_MODEL = 27835;
    private final int SNOW_ANIMATION = 7000;
    private final int STAR_MODEL = 16374;
    private final int STAR_ANIMATION = 7971;

    public void loadModels()
    {
        ModelData ashModelData = client.loadModelData(ASH_MODEL).cloneColors().cloneVertices();
        ModelData ashModelData2 = client.loadModelData(ASH_MODEL).cloneColors().cloneVertices();
        ModelData ashModelData3 = client.loadModelData(ASH_MODEL).cloneColors().cloneVertices();
        short[] ashFaceColours = ashModelData.getFaceColors();
        short[] ashFaceColours2 = ashModelData2.getFaceColors();
        short[] ashFaceColours3 = ashModelData3.getFaceColors();
        short ashColour1 = JagexColor.packHSL(39, 1, 40);
        short ashColour2 = JagexColor.packHSL(39, 1, 40);
        ashModel = ashModelData.scale(128, 192, 128).translate(0, 180, 0).recolor(ashFaceColours[0], ashColour1).recolor(ashFaceColours[2], ashColour2).light();
        ashModel2 = ashModelData2.scale(128, 192, 128).translate(0, 180, 0).recolor(ashFaceColours2[0], ashColour1).recolor(ashFaceColours2[2], ashColour2).rotateY90Ccw().light();
        ashModel3 = ashModelData3.scale(128, 192, 128).translate(0, 180, 0).recolor(ashFaceColours3[0], ashColour1).recolor(ashFaceColours3[2], ashColour2).rotateY270Ccw().light();

        // Clouds: bigger + softer shades so neighbours visually blend into a sheet rather than
        // reading as obvious separate puff models. Three variants with slightly different
        // tints / scales / rotations help avoid a tiled look.
        ModelData cloudModelData = client.loadModelData(CLOUD_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        ModelData cloudModelData2 = client.loadModelData(CLOUD_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        ModelData cloudModelData3 = client.loadModelData(CLOUD_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        short cloudFaceColour = cloudModelData.getFaceColors()[0];
        short cloudBright = JagexColor.packHSL(54, 0, 118);
        short cloudMid    = JagexColor.packHSL(54, 1, 100);
        short cloudShade  = JagexColor.packHSL(50, 2, 78);
        cloudModel  = cloudModelData.scale(1100, 360, 1100).translate(0, -1300, 0).recolor(cloudFaceColour, cloudBright).light();
        cloudModel2 = cloudModelData2.scale(1500, 480, 1500).translate(0, -1700, 0).recolor(cloudFaceColour, cloudMid).rotateY90Ccw().light();
        cloudModel3 = cloudModelData3.scale(1300, 420, 1300).translate(0, -1500, 0).recolor(cloudFaceColour, cloudShade).rotateY180Ccw().light();

        ModelData cloudTPModelData = client.loadModelData(CLOUD_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        ModelData cloudTPModelData2 = client.loadModelData(CLOUD_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        ModelData cloudTPModelData3 = client.loadModelData(CLOUD_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        cloudModelTP  = cloudTPModelData.scale(1100, 360, 1100).translate(0, -1300, 0).recolor(cloudFaceColour, cloudBright).light();
        cloudModelTP2 = cloudTPModelData2.scale(1500, 480, 1500).translate(0, -1700, 0).recolor(cloudFaceColour, cloudMid).rotateY90Ccw().light();
        cloudModelTP3 = cloudTPModelData3.scale(1300, 420, 1300).translate(0, -1500, 0).recolor(cloudFaceColour, cloudShade).rotateY180Ccw().light();
        byte[] cloudMDTP = cloudTPModelData.getFaceTransparencies();
        byte[] cloudMDTP2 = cloudTPModelData2.getFaceTransparencies();
        byte[] cloudMDTP3 = cloudTPModelData3.getFaceTransparencies();
        // Higher (more transparent) so edges of overlapping clouds melt together.
        Arrays.fill(cloudMDTP, (byte) -70);
        Arrays.fill(cloudMDTP2, (byte) -70);
        Arrays.fill(cloudMDTP3, (byte) -70);

        // Cloud ground shadows: same source model but flattened (tiny Y scale) and recoloured
        // near-black, so it lies on the floor like a soft splotch. Heavy transparency so it
        // doesn't read as a solid disc. Translated up off the tile a few units to avoid Z-fighting.
        rebuildCloudShadowModels();

        ModelData fogModelData = client.loadModelData(FOG_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        ModelData fogModelData2 = client.loadModelData(FOG_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        ModelData fogModelData3 = client.loadModelData(FOG_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        short fogFaceColour = fogModelData.getFaceColors()[0];
        short fogReplaceColour = JagexColor.packHSL(54, 0, 95);
        fogModel = fogModelData.scale(240, 140, 240).recolor(fogFaceColour, fogReplaceColour).translate(0, -70, 0).light(220, ModelData.DEFAULT_CONTRAST, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
        fogModel2 = fogModelData2.scale(240, 140, 240).recolor(fogFaceColour, fogReplaceColour).translate(0, -100, 0).light(220, ModelData.DEFAULT_CONTRAST, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
        fogModel3 = fogModelData3.scale(240, 140, 240).recolor(fogFaceColour, fogReplaceColour).translate(0, -85, 0).light(220, ModelData.DEFAULT_CONTRAST, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
        byte[] fogTransparency = fogModelData.getFaceTransparencies();
        byte[] fogTransparency2 = fogModelData2.getFaceTransparencies();
        byte[] fogTransparency3 = fogModelData3.getFaceTransparencies();
        Arrays.fill(fogTransparency, (byte) -25);
        Arrays.fill(fogTransparency2, (byte) -25);
        Arrays.fill(fogTransparency3, (byte) -25);

        // Snow: the Wintertodt ice-burst animation only throws particles a short distance from
        // the model origin, so over-lifting the origin makes flakes spawn above the visible
        // frustum and never reach the ground. Instead, scale Y aggressively (so the bounding
        // box covers from above the player's head down past the tile) and only nudge the origin
        // up modestly so the apparent fall begins above head height and finishes at the floor.
        ModelData snowModelData = client.loadModelData(SNOW_MODEL).cloneVertices();
        ModelData snowModelData2 = client.loadModelData(SNOW_MODEL).cloneVertices();
        ModelData snowModelData3 = client.loadModelData(SNOW_MODEL).cloneVertices();
        snowModel  = snowModelData.scale(200, 900, 200).translate(0, -150, 0).light();
        snowModel2 = snowModelData2.scale(200, 900, 200).translate(0, -150, 0).rotateY90Ccw().light();
        snowModel3 = snowModelData3.scale(200, 900, 200).translate(0, -150, 0).rotateY270Ccw().light();

        // Rain: longer droplets + darker base colour. Length scales with global intensity.
        int rainYScale = rainHeightFor(false);
        ModelData rainModelData = client.loadModelData(RAIN_MODEL).cloneVertices().cloneColors();
        ModelData rainModelData2 = client.loadModelData(RAIN_MODEL).cloneVertices().cloneColors();
        ModelData rainModelData3 = client.loadModelData(RAIN_MODEL).cloneVertices().cloneColors();
        short[] rainFaceColours = rainModelData.getFaceColors();
        short[] rainFaceColours2 = rainModelData2.getFaceColors();
        short[] rainFaceColours3 = rainModelData3.getFaceColors();
        short rainRippleColour = JagexColor.packHSL(32, 1, JagexColor.LUMINANCE_MAX);
        short rainDropColour = JagexColor.packHSL(32, 1, 95);
        rainModel  = rainModelData.scale(100, rainYScale, 100).recolor(rainFaceColours[0], rainRippleColour).recolor(rainFaceColours[23], rainDropColour).light();
        rainModel2 = rainModelData2.scale(90, rainYScale, 90).recolor(rainFaceColours2[0], rainRippleColour).recolor(rainFaceColours2[23], rainDropColour).rotateY90Ccw().light();
        rainModel3 = rainModelData3.scale(110, rainYScale, 110).recolor(rainFaceColours3[0], rainRippleColour).recolor(rainFaceColours3[23], rainDropColour).rotateY270Ccw().light();

        int stormYScale = rainHeightFor(true);
        ModelData stormModelData = client.loadModelData(RAIN_MODEL).cloneColors().cloneVertices();
        ModelData stormModelData2 = client.loadModelData(RAIN_MODEL).cloneColors().cloneVertices();
        ModelData stormModelData3 = client.loadModelData(RAIN_MODEL).cloneColors().cloneVertices();
        short[] stormFaceColours = stormModelData.getFaceColors();
        short[] stormFaceColours2 = stormModelData2.getFaceColors();
        short[] stormFaceColours3 = stormModelData3.getFaceColors();
        short stormRippleColour = JagexColor.packHSL(38, 1, 95);
        short stormDropColour = JagexColor.packHSL(38, 2, 80);
        stormModel  = stormModelData.scale(110, stormYScale, 110).recolor(stormFaceColours[0], stormRippleColour).recolor(stormFaceColours[23], stormDropColour).light();
        stormModel2 = stormModelData2.scale(100, stormYScale, 100).recolor(stormFaceColours2[0], stormRippleColour).recolor(stormFaceColours2[23], stormDropColour).rotateY90Ccw().light();
        stormModel3 = stormModelData3.scale(120, stormYScale, 120).recolor(stormFaceColours3[0], stormRippleColour).recolor(stormFaceColours3[23], stormDropColour).rotateY90Ccw().light();

        ModelData starModelData = client.loadModelData(STAR_MODEL).cloneColors().cloneVertices().cloneTransparencies();
        ModelData starModelData2 = client.loadModelData(STAR_MODEL).cloneColors().cloneVertices().cloneTransparencies();
        ModelData starModelData3 = client.loadModelData(STAR_MODEL).cloneColors().cloneVertices().cloneTransparencies();
        short[] starFaceColours = starModelData.getFaceColors();
        short[] starFaceColours2 = starModelData2.getFaceColors();
        short[] starFaceColours3 = starModelData3.getFaceColors();
        short starShellReplaceColour = JagexColor.packHSL(10, 4, 60);
        short starInsideReplaceColour = JagexColor.packHSL(10, 6, 80);
        starModel = starModelData.scale(80, 80, 80).translate(0, -1400, 0).recolor(starFaceColours[0], starShellReplaceColour).recolor(starFaceColours[45], starInsideReplaceColour).light(ModelData.DEFAULT_AMBIENT, 1400, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
        starModel2 = starModelData2.scale(65, 65, 65).translate(0, -1500, 0).recolor(starFaceColours2[0], starShellReplaceColour).recolor(starFaceColours2[45], starInsideReplaceColour).light(ModelData.DEFAULT_AMBIENT, 1400, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
        starModel3 = starModelData3.scale(95, 95, 95).translate(0, -1300, 0).recolor(starFaceColours3[0], starShellReplaceColour).recolor(starFaceColours3[45], starInsideReplaceColour).light(ModelData.DEFAULT_AMBIENT, 1400, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);

        ModelData starModelDataTP = client.loadModelData(STAR_MODEL).cloneColors().cloneVertices().cloneTransparencies();
        ModelData starModelDataTP2 = client.loadModelData(STAR_MODEL).cloneColors().cloneVertices().cloneTransparencies();
        ModelData starModelDataTP3 = client.loadModelData(STAR_MODEL).cloneColors().cloneVertices().cloneTransparencies();
        short[] starFaceColoursTP = starModelDataTP.getFaceColors();
        short[] starFaceColoursTP2 = starModelDataTP2.getFaceColors();
        short[] starFaceColoursTP3 = starModelDataTP3.getFaceColors();
        starModelTP = starModelDataTP.scale(80, 80, 80).translate(0, -1400, 0).recolor(starFaceColoursTP[0], starShellReplaceColour).recolor(starFaceColoursTP[45], starInsideReplaceColour).light(ModelData.DEFAULT_AMBIENT, 1400, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
        starModelTP2 = starModelDataTP2.scale(65, 65, 65).translate(0, -1500, 0).recolor(starFaceColoursTP2[0], starShellReplaceColour).recolor(starFaceColoursTP2[45], starInsideReplaceColour).light(ModelData.DEFAULT_AMBIENT, 1400, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
        starModelTP3 = starModelDataTP3.scale(95, 95, 95).translate(0, -1300, 0).recolor(starFaceColoursTP3[0], starShellReplaceColour).recolor(starFaceColoursTP3[45], starInsideReplaceColour).light(ModelData.DEFAULT_AMBIENT, 1400, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
        byte[] starMDTP = starModelDataTP.getFaceTransparencies();
        byte[] starMDTP2 = starModelDataTP2.getFaceTransparencies();
        byte[] starMDTP3 = starModelDataTP3.getFaceTransparencies();
        Arrays.fill(starMDTP, (byte) -80);
        Arrays.fill(starMDTP2, (byte) -80);
        Arrays.fill(starMDTP3, (byte) -80);

        ashAnimation = client.loadAnimation(ASH_ANIMATION);
        cloudAnimation = client.loadAnimation(CLOUD_ANIMATION);
        fogAnimation = client.loadAnimation(CLOUD_ANIMATION);
        rainAnimation = client.loadAnimation(RAIN_ANIMATION);
        snowAnimation = client.loadAnimation(SNOW_ANIMATION);
        starAnimation = client.loadAnimation(STAR_ANIMATION);
    }

    /**
     * Rebuilds shadow models so their opacity matches the live config slider. Called both
     * during initial load and whenever the cloudShadowOpacity setting changes.
     */
    public void rebuildCloudShadowModels()
    {
        int opacityCfg = config == null ? 25 : config.cloudShadowOpacity();
        // Face transparency byte is read unsigned: 0 = fully opaque, 255 = fully invisible.
        // We want shadows to read as soft, atmospheric tints — never solid black blobs.
        // Map opacity slider [5..100] onto unsigned [240..150] (i.e. byte [-16..-106]):
        //   5%   → unsigned 240 (~94% transparent — a whisper of darkness)
        //   25%  → unsigned 220 (~86% transparent — the new default, gentle WoW vibe)
        //   100% → unsigned 150 (~59% transparent — distinct but never solid)
        int unsigned = 240 - (int) ((opacityCfg / 100f) * 90);
        if (unsigned > 255) unsigned = 255;
        if (unsigned < 130) unsigned = 130;
        int trans = unsigned > 127 ? unsigned - 256 : unsigned;

        ModelData s1 = client.loadModelData(CLOUD_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        ModelData s2 = client.loadModelData(CLOUD_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        ModelData s3 = client.loadModelData(CLOUD_MODEL).cloneVertices().cloneColors().cloneTransparencies();
        short shadowFaceColour = s1.getFaceColors()[0];
        short shadowReplace = JagexColor.packHSL(0, 0, 0);
        // Y scale ≈ 1 (flat); translate up a few units to sit just above the tile surface.
        cloudShadowModel  = s1.scale(1100, 1, 1100).translate(0, -2, 0).recolor(shadowFaceColour, shadowReplace).light();
        cloudShadowModel2 = s2.scale(1500, 1, 1500).translate(0, -2, 0).recolor(shadowFaceColour, shadowReplace).rotateY90Ccw().light();
        cloudShadowModel3 = s3.scale(1300, 1, 1300).translate(0, -2, 0).recolor(shadowFaceColour, shadowReplace).rotateY180Ccw().light();
        byte[] t1 = s1.getFaceTransparencies();
        byte[] t2 = s2.getFaceTransparencies();
        byte[] t3 = s3.getFaceTransparencies();
        Arrays.fill(t1, (byte) trans);
        Arrays.fill(t2, (byte) trans);
        Arrays.fill(t3, (byte) trans);
    }

    private int rainHeightFor(boolean storm)
    {
        if (config == null)
        {
            return storm ? 410 : 256;
        }
        switch (config.weatherIntensity())
        {
            case LIGHT:    return storm ? 320 : 200;
            default:
            case MODERATE: return storm ? 410 : 256;
            case HEAVY:    return storm ? 560 : 360;
            case EXTREME:  return storm ? 760 : 500;
        }
    }

    public Model getCloudShadowModel(int alternative)
    {
        switch (alternative)
        {
            default:
            case 1:
                return cloudShadowModel;
            case 2:
                return cloudShadowModel2;
            case 3:
                return cloudShadowModel3;
        }
    }

    public Model getWeatherModel(Weather currentWeather, int alternative)
    {
        switch (currentWeather)
        {
            case ASHFALL:
                switch(alternative)
                {
                    default:
                    case 1:
                        return ashModel;
                    case 2:
                        return ashModel2;
                    case 3:
                        return ashModel3;
                }
            case CLOUDY:
            case PARTLY_CLOUDY:
                switch(alternative)
                {
                    default:
                    case 1:
                        return cloudModel;
                    case 2:
                        return cloudModel2;
                    case 3:
                        return cloudModel3;
                }
            case STARRY:
                switch(alternative)
                {
                    default:
                    case 1:
                        return starModel;
                    case 2:
                        return starModel2;
                    case 3:
                        return starModel3;
                }
            case FOGGY:
                return fogModel;
            case SNOWY:
                switch (alternative)
                {
                    default:
                    case 1:
                        return snowModel;
                    case 2:
                        return snowModel2;
                    case 3:
                        return snowModel3;
                }
            case RAINY:
                switch (alternative)
                {
                    default:
                    case 1:
                        return rainModel;
                    case 2:
                        return rainModel2;
                    case 3:
                        return rainModel3;
                }
            case STORMY:
                switch (alternative)
                {
                    default:
                    case 1:
                        return stormModel;
                    case 2:
                        return stormModel2;
                    case 3:
                        return stormModel3;
                }
            default:
            case COVERED:
            case SUNNY:
                return null;
        }
    }

    public Animation getWeatherAnimation(Weather currentWeather)
    {
        switch (currentWeather)
        {
            case ASHFALL:
                return ashAnimation;
            case CLOUDY:
            case PARTLY_CLOUDY:
                return cloudAnimation;
            case STARRY:
                return starAnimation;
            case FOGGY:
                return fogAnimation;
            case SNOWY:
                return snowAnimation;
            case RAINY:
            case STORMY:
                return rainAnimation;
            default:
            case COVERED:
            case SUNNY:
                return null;
        }
    }

    public Model getTransparentModel(Weather weatherType, int objectVariant)
    {
        switch (weatherType)
        {
            default:
            case CLOUDY:
            case PARTLY_CLOUDY:
                switch (objectVariant)
                {
                    default:
                    case 1:
                        return cloudModelTP;
                    case 2:
                        return cloudModelTP2;
                    case 3:
                        return cloudModelTP3;
                }
            case STARRY:
                switch (objectVariant)
                {
                    default:
                    case 1:
                        return starModelTP;
                    case 2:
                        return starModelTP2;
                    case 3:
                        return starModelTP3;
                }
        }
    }

    public Model getRegularModel(Weather weatherType, int objectVariant)
    {
        switch (weatherType)
        {
            default:
            case CLOUDY:
            case PARTLY_CLOUDY:
                switch (objectVariant)
                {
                    default:
                    case 1:
                        return cloudModel;
                    case 2:
                        return cloudModel2;
                    case 3:
                        return cloudModel3;
                }
            case STARRY:
                switch (objectVariant)
                {
                    default:
                    case 1:
                        return starModel;
                    case 2:
                        return starModel2;
                    case 3:
                        return starModel3;
                }
        }
    }

    public int getModelRadius(Weather weatherType)
    {
        switch (weatherType)
        {
            default:
            case ASHFALL:
            case STARRY:
            case RAINY:
            case STORMY:
            case SNOWY:
                return 60;
            case CLOUDY:
            case PARTLY_CLOUDY:
                //Size should be 500 but auto-disappear makes it irrelevant
                return 60;
            case FOGGY:
                return 90;
        }
    }
}
