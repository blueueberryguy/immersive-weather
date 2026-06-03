# Immersive Weather
A plugin that gives highly immersive, 3D weather with dynamic weather cycles, day/night, aurora, and ambience.

A fork of https://github.com/ScreteMonge/3D-Weather.

## Changelog (vs. 3D Weather fork)

### Sky & lighting
- Plugin now drives the in-client skybox colour per weather (sunny, cloudy, rainy/stormy, snowy, foggy, ashfall, starry, aurora), easing between targets each frame.
- Auto-disables RuneLite's stock Skybox plugin while running so the two don't fight over `setSkyboxColor`.
- New full-screen weather tint overlay (darkens during rain/storm, brightens-foggy during snow, etc.).

### Day / night cycle
- New independent day↔night cycle on a real-time clock (default 12 minutes per full cycle).
- Sky eases toward a deep night colour proportional to the cycle's light level and a `Night Darkness` slider.
- Night darkening overlay layered above the scene so the world (not just the sky) actually dims at night.
- Stars at night: an additive star manager spawns the starry weather objects regardless of the active weather while it's night.

### Aurora
- Stacks with the night-stars layer.
- Tunable density and intensity.

### Cloud changes
- Cloud face colours blanket-filled (vs. recolouring only `faceColors[0]`), per-face translucency added, variant luminance tightened — fixes the "lumpy banded puff" look.
- Clouds drift smoothly across the sky each client-tick with scene-edge wrap, instead of teleporting on the relocate cycle.
- New cast ground shadows for clouds with tunable opacity.

### Snow changes
- Snow particle visual revised (Wintertodt ice-burst flattened with low-contrast lighting so it reads as a soft puff rather than a sparkly crystal).
- New ground snow accumulation: paired flat white disc per snow particle, accumulates into a soft white dusting across the visible scene.
- Accumulation respects building interiors via a 3×3 `TILE_FLAG_UNDER_ROOF` neighbourhood check so discs don't bleed into indoor floors.

### Fog changes
- Fog now uses the cloud model flattened wide-and-short (vs. the original fog model's vertical brushstrokes) — reads as a ground-hugging mist sheet.
- Heavy per-face translucency + flat lighting + smaller patches so accumulating overlaps blend smoothly instead of stacking polygon edges.
- Fog drifts slowly, same drift machinery as clouds at ~1/3 speed.
- Spawn/relocate respect the same 3×3 roof check as snow accumulation, so fog stays outside buildings.

### Rain changes
- Default densities raised.
- Streak heights roughly doubled and lengths vary per variant so the rain field reads as a curtain rather than a uniform grid.
- Droplet colour brightened to read against dark stormy backdrops.

### Intensity & defaults
- New `Weather Intensity` master slider (LIGHT / MODERATE / HEAVY / EXTREME) drives sky darkness, screen tint strength, rain streak length, and precipitation density together.
- New configs: `Sky & Lighting`, `Day & Night`, plus per-feature toggles (cloud shadows, snow accumulation, stars-at-night, aurora intensity & density, etc.).
- Defaults flipped: `enableFog` and `enableAsh` now default on; `toggleOverlay` defaults off.

### Underground / instanced area handling
- Skybox override, night darkening, weather tint, and night-star auxiliary are all suppressed when the player is in a cave, lava cave, or any instanced region. The game's natural lighting comes back through.

### Misc
- Smoother stacking for cloud shadows, snow accumulation, and fog (ultra-translucent per-face so overlaps don't expose polygon edges).

# Gallery

### Rainy/Stormy

![Rain](https://i.imgur.com/rQ5Wa9e.png)

### Snowy

![Snow](https://i.imgur.com/DUsMjVI.png)

### Foggy

![Fog](https://i.imgur.com/M9QG63A.png)

### Ashfall

![Ashfall](https://i.imgur.com/e5GUWoQ.png)

### Starry

![Stars](https://i.imgur.com/SLzokWF.png)

### Cloudy/Partly Cloudy

![Clouds](https://i.imgur.com/9WSINrk.png)


### Weather Type & Season Type
Weather can be manually set or dynamically self-regulated.

![WEATHERSSEASONTYPE](https://imgur.com/qmi45zy.png)

For Dynamic Weather, the Weather will automatically loop based on your Season and Biome every 15 minutes. 
Season can be set or dynamically self-regulated as well, changing every 7 days based on Jagex time.
Naturally, Winter season will feature more frequent colder, precipitous Weathers, while Summer will tend to be drier.

Biome is determined by your chunk on the world map. 
A chunk that is predominantly within the Desert region will therefore feature as a Desert Biome. 
Note that this does result in some awkward gaps where a Biome may be applied to the edge of an area that is clearly a different Biome.

### Day Cycle
The sky cycles between day and night on a real-time clock, independent of weather.

For Dynamic Day Cycle, the sky will automatically loop from day to night and back over a configurable Cycle Length (default 12 minutes), eased smoothly so dawn and dusk fade gradually rather than snapping.
Time of Day can also be set manually — Day, Dusk, or Night — to preview a fixed light level or test how a given weather looks under a specific lighting state.
Naturally, night dims the scene and lets stars appear in the sky; day brightens everything back up.


### Weather Density

![WEATHERDENSITY](https://imgur.com/J4HDRrr.png)

Weather Density gives control over how many Weather objects spawn - how dense the rain is, for example.
This is particularly handy for setting a particular scene or controlling how much the plugin will impact your performance.
Higher Weather Densities can have a significant impact on performance, especially when paired with 117HD.

### Toggle Overlay

![Overlay](https://imgur.com/qP9EIVo.png)

![ToggleOverlay](https://imgur.com/3OXk6Y4.png)

An overlay that indicates the current Weather, Biome, and Season can also be toggled on or off. 
This is purely for informational purposes. 
As of right now, you'll notice some Weathers aren't any different from each other (Cloudy vs Sunny vs Partly Cloudy, for example).
There are hopes that they and the different Seasons will be given some personality of their own in the future.

### Toggle Ambience

![ToggleAmbience](https://imgur.com/dkELFdj.png)

This plugin also features ambient Weather sounds which can be toggled on or off in the config.

### Ambient Volume

![AmbientVolume](https://imgur.com/YCkjkiC.png)

Adjusts the volume of ambience sounds.
Note that the ambient Weather volume is dependent on both the Ambient Volume setting and Weather Density - higher densities of Weather objects will be louder.

### Disable Weather Underground

![Underground](https://imgur.com/6OtSIWM.png)

Prevents the current Weather Type from occurring while underground. This is already true when Weather Type is set to Dynamic.

### Enable Clouds

![Clouds](https://imgur.com/ZDSDyeh.png)

Allows Cloud objects to appear when the Weather is Cloudy or Partly Cloudy. Players may prefer this option to be turned off if they do not like the Cloud objects that spawn.

### Enable Fog

![EnableFog](https://imgur.com/qTrtxIw.png)

Allows Fog objects to appear when the Weather is Foggy. Players may prefer this option to be turned off if they do not like the Fog objects that spawn.

### Enable Stars

![EnableStars](https://imgur.com/IyKRyL1.png)

Allows Star objects to appear when the Weather is Stars. Players may prefer this option to be turned off if they do not like the Star objects that spawn.

### Enable Wintertodt Snow

![EnableWintertodtSnow](https://imgur.com/M4eTD51.png)

Allows Snow objects to appear when the Weather is Snowy and while the player is in the Wintertodt chunk. 
Because the Snow model and animation are taken from Wintertodt's AOE attack, disabling this option reduces confusion over where the Wintertodt is actually attacking.

### Enable Lightning

![EnableLightning](https://imgur.com/RQ4KKwN.png)

WARNING: This option allows the screen to flash white during Storms. This should not be used for photosensitive players.

## Credits

Special thanks to the RLweather plugin by Bogstandard for providing inspiration for this plugin.

Also special thanks to these authors for making the ambient sounds used in this plugin freely available on FreeSound.org:

| Track                        | Author            | URL                                                           |
|------------------------------|-------------------|---------------------------------------------------------------|
| 241102H (Mystic voice)       | Freed             | https://freesound.org/people/Freed/sounds/1105/               |
| Rumble                       | HerbertBoland     | https://freesound.org/people/HerbertBoland/sounds/147661/     |
| Lightning Strike and Thunder | Aeonemi           | https://freesound.org/people/Aeonemi/sounds/180327/           |
| raw_wind                     | rivv3t            | https://freesound.org/people/rivv3t/sounds/201208/            |
| Rain Forest Steady           | mikaelacampbell18 | https://freesound.org/people/mikaelacampbell18/sounds/617078/ |