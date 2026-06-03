package com.weather3d;

import com.google.inject.Provides;
import com.weather3d.audio.SoundEffect;
import com.weather3d.audio.SoundPlayer;
import com.weather3d.conditions.Biome;
import com.weather3d.conditions.Season;
import com.weather3d.conditions.Weather;
import com.weather3d.conditions.WeatherManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.*;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Random;

import static com.weather3d.CyclesConfig.SeasonType.DYNAMIC;
import static com.weather3d.CyclesConfig.SeasonType.HD_117;
import static com.weather3d.audio.SoundEffect.*;

@Slf4j
@PluginDescriptor(
	name = "3D Weather",
	description = "Creates immersive 3D Weather with dynamic Weather cycles and ambience",
	tags = {"immersion,", "weather", "ambience", "audio", "graphics"}
)
public class CyclesPlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private CyclesConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private CyclesOverlay cyclesOverlay;
	@Inject
	private LightningOverlay lightningOverlay;
	@Inject
	private ConfigManager configManager;
	@Inject
	private PluginManager pluginManager;
	@Inject
	private ModelHandler modelHandler;
	@Inject
	private SkyboxController skyboxController;
	@Inject
	private WeatherTintOverlay weatherTintOverlay;
	@Inject
	private EventBus eventBus;

	/** Tracks whether we toggled off the stock Skybox plugin so we can restore it on shutdown. */
	private boolean stockSkyboxWasEnabled = false;

	private final Random random = new Random();
	private final ArrayList<WeatherManager> weatherManagerList = new ArrayList<>();
	private boolean loadedAnimsModels = false;
	private boolean conditionsSynced = false;
	private boolean isPlayerIndoors = false;
	public boolean flashLightning = false;
    private int savedChunk = 0;
	private int savedZPlane = -1;
	private int zoneObjRecovery = 0;
	private final int WINTERTODT_CHUNK = 6462;
	private final int OBJ_ROTATION_CONSTANT = 20;
	private final int MODEL_TRANSPARENT_SWAP_DISTANCE = 3000;
	private final int MODEL_DISAPPEAR_DISTANCE = 2500;
	// Sub-tile units per client tick. 1 tile = 128 units; at ~50 fps this is ~1 tile every ~7s.
	// Slight Y component so the wind isn't a pure cardinal direction (more natural-feeling).
	private static final float CLOUD_WIND_X_PER_FRAME = 0.4f;
	private static final float CLOUD_WIND_Y_PER_FRAME = 0.12f;
	private final int FOG_RADIUS = 100;

	@Getter
	private Season currentSeason = Season.SPRING;
	@Getter
	private Biome currentBiome = Biome.GRASSLAND;
	@Getter
	private Weather currentWeather = Weather.COVERED;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(cyclesOverlay);
		overlayManager.add(lightningOverlay);
		overlayManager.add(weatherTintOverlay);
		eventBus.register(skyboxController);
		skyboxController.reset();

		toggleStockSkyboxIfNeeded(true);

		if (client.getLocalPlayer() != null)
		{
			syncBiome();
			syncSeason();
			setConfigWeather();
			handleWeatherManagers();
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invoke(this::clearAllWeatherManagers);
		overlayManager.remove(cyclesOverlay);
		overlayManager.remove(lightningOverlay);
		overlayManager.remove(weatherTintOverlay);
		eventBus.unregister(skyboxController);
		// Hand the sky back to whatever was managing it before us.
		clientThread.invoke(() -> client.setSkyboxColor(0));

		restoreStockSkybox();
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		for (int i = 0; i < weatherManagerList.size(); i++)
		{
			WeatherManager weatherManager = weatherManagerList.get(i);
			Weather weatherType = weatherManager.getWeatherType();

			if (weatherType == Weather.CLOUDY
					|| weatherType == Weather.PARTLY_CLOUDY
					|| weatherType == Weather.STARRY)
			{
				LocalPoint cameraPoint = new LocalPoint(client.getCameraX(), client.getCameraY());
				boolean driftEnabled = (weatherType == Weather.CLOUDY || weatherType == Weather.PARTLY_CLOUDY);
				int plane = client.getPlane();

				for (WeatherObject weatherObject : weatherManager.getWeatherObjArray())
				{
					RuneLiteObject runeLiteObject = weatherObject.getRuneLiteObject();
					RuneLiteObject shadowObject = weatherObject.getShadowObject();
					int objectVariant = weatherObject.getObjVariant();

					// Drift: slide clouds across the sky each client-tick. baseX/baseY are the
					// spawn anchor; we add accumulated driftX so motion is sub-tile-smooth even at
					// frame rate. When a cloud sails off the scene we wrap it to the upwind edge
					// so the world stays populated without obvious pop-in.
					if (driftEnabled)
					{
						if (weatherObject.getBaseX() == WeatherObject.UNSET_BASE)
						{
							LocalPoint anchor = runeLiteObject.getLocation();
							weatherObject.setBaseX(anchor.getX());
							weatherObject.setBaseY(anchor.getY());
						}

						weatherObject.setDriftX(weatherObject.getDriftX() + CLOUD_WIND_X_PER_FRAME);
						weatherObject.setDriftY(weatherObject.getDriftY() + CLOUD_WIND_Y_PER_FRAME);

						int newX = weatherObject.getBaseX() + (int) weatherObject.getDriftX();
						int newY = weatherObject.getBaseY() + (int) weatherObject.getDriftY();

						int sceneMax = Constants.SCENE_SIZE * Perspective.LOCAL_TILE_SIZE;
						int wrapPad = 8 * Perspective.LOCAL_TILE_SIZE; // 8 tiles past the edge before we wrap
						if (newX > sceneMax + wrapPad)
						{
							// Re-anchor to opposite edge so cloud re-enters from upwind without a teleport
							// the player can see (we're far past the scene boundary at this point).
							weatherObject.setBaseX(weatherObject.getBaseX() - (sceneMax + 2 * wrapPad));
							newX = weatherObject.getBaseX() + (int) weatherObject.getDriftX();
						}
						else if (newX < -wrapPad)
						{
							weatherObject.setBaseX(weatherObject.getBaseX() + (sceneMax + 2 * wrapPad));
							newX = weatherObject.getBaseX() + (int) weatherObject.getDriftX();
						}
						if (newY > sceneMax + wrapPad)
						{
							weatherObject.setBaseY(weatherObject.getBaseY() - (sceneMax + 2 * wrapPad));
							newY = weatherObject.getBaseY() + (int) weatherObject.getDriftY();
						}
						else if (newY < -wrapPad)
						{
							weatherObject.setBaseY(weatherObject.getBaseY() + (sceneMax + 2 * wrapPad));
							newY = weatherObject.getBaseY() + (int) weatherObject.getDriftY();
						}

						LocalPoint drifted = new LocalPoint(newX, newY);
						runeLiteObject.setLocation(drifted, plane);
						if (shadowObject != null)
							shadowObject.setLocation(drifted, plane);
					}

					// Clouds always stay rendered: never blink them off by camera distance, that's
					// what was making shadows flicker as the camera panned. The transparent variant
					// at close range still keeps the player's view clean from below.
					int distance = runeLiteObject.getLocation().distanceTo(cameraPoint);

					if (driftEnabled)
					{
						runeLiteObject.setActive(true);
						if (shadowObject != null)
							shadowObject.setActive(config.enableCloudShadows());

						if (distance < MODEL_TRANSPARENT_SWAP_DISTANCE)
							runeLiteObject.setModel(modelHandler.getTransparentModel(weatherType, objectVariant));
						else
							runeLiteObject.setModel(modelHandler.getRegularModel(weatherType, objectVariant));
					}
					else
					{
						// STARRY keeps the original near-camera hide behaviour (no shadows for stars).
						if (distance < MODEL_DISAPPEAR_DISTANCE)
						{
							runeLiteObject.setActive(false);
							continue;
						}

						runeLiteObject.setActive(true);

						if (distance < MODEL_TRANSPARENT_SWAP_DISTANCE)
						{
							runeLiteObject.setModel(modelHandler.getTransparentModel(weatherType, objectVariant));
							continue;
						}

						runeLiteObject.setModel(modelHandler.getRegularModel(weatherType, objectVariant));
					}
				}
			}
		}
	}


	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!loadedAnimsModels)
		{
			modelHandler.loadModels();
			loadedAnimsModels = true;
		}

		if (!conditionsSynced)
		{
			setConfigWeather();
			if (currentWeather.isHasPrecipitation())
			{
				handleWeatherManagers();
			}
			conditionsSynced = true;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
			return;

		syncSeason();
		syncBiome();

		isPlayerIndoors = true;
		WorldPoint playerLoc = client.getLocalPlayer().getWorldLocation();
		for (Tile t : getAvailableTiles())
		{
			if (t.getWorldLocation().getX() == playerLoc.getX() && t.getWorldLocation().getY() == playerLoc.getY())
			{
				isPlayerIndoors = false;
			}
		}

		if (config.weatherType() == CyclesConfig.WeatherType.DYNAMIC)
		{
			Weather nextWeather = syncWeather(currentSeason, currentBiome);

			if (nextWeather != currentWeather)
			{
				setConfigWeather();
				handleWeatherManagers();
			}
			conditionsSynced = true;
		}

		for (int i = 0; i < weatherManagerList.size(); i++)
		{
			WeatherManager wm = weatherManagerList.get(i);
			if (wm.isFading())
			{
				fadeWeatherManager(wm);
			}
			else
			{
				handleWeatherChanges(wm);
			}

			handleSoundChanges(wm);
		}

		clearFadedWeatherManagers();

		if (savedZPlane != client.getPlane())
		{
			transitionZPlane();
			savedZPlane = client.getPlane();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();
		if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.LOGIN_SCREEN_AUTHENTICATOR || gameState == GameState.STARTING)
		{
			clientThread.invoke(this::clearAllWeatherManagers);
			return;
		}

		if (gameState != GameState.LOGGED_IN)
			return;

		syncBiome();
		syncSeason();
		setConfigWeather();
		handleWeatherManagers();
		handleZoneTransition();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("3Dweather"))
			return;

		if (client.getGameState() != GameState.LOGGED_IN)
			return;

		String key = event.getKey();

		if (key.equals("weatherType"))
		{
			setConfigWeather();
			handleWeatherManagers();
			return;
		}

		if (key.equals("disableWeatherUnderground"))
		{
			if (!config.disableWeatherUnderground())
				return;

			if (currentBiome == Biome.CAVE || currentBiome == Biome.LAVA_CAVE)
				clientThread.invoke(this::clearAllWeatherManagers);
		}

		if (key.equals("seasonType"))
		{
			syncSeason();
			return;
		}

		if (key.equals("ashfallDensity"))
			handleConfigDensityChange(Weather.ASHFALL, config.ashfallDensity());

		if (key.equals("rainDensity"))
			handleConfigDensityChange(Weather.RAINY, config.rainDensity());

		if (key.equals("stormDensity"))
			handleConfigDensityChange(Weather.STORMY, config.stormDensity());

		if (key.equals("snowDensity"))
			handleConfigDensityChange(Weather.SNOWY, config.snowDensity());

		if (key.equals("partlyCloudyDensity"))
			handleConfigDensityChange(Weather.PARTLY_CLOUDY, config.partlyCloudyDensity());

		if (key.equals("cloudyDensity"))
			handleConfigDensityChange(Weather.CLOUDY, config.cloudyDensity());

		if (key.equals("foggyDensity"))
			handleConfigDensityChange(Weather.FOGGY, config.foggyDensity());

		if (key.equals("starryDensity"))
			handleConfigDensityChange(Weather.STARRY, config.starryDensity());

		if (event.getKey().equals("toggleOverlay"))
		{
			if (config.toggleOverlay())
			{
				overlayManager.add(cyclesOverlay);
				return;
			}

			overlayManager.remove(cyclesOverlay);
			return;
		}

		if (event.getKey().equals("toggleAmbience"))
		{
			for (int i = 0; i < weatherManagerList.size(); i++)
			{
				WeatherManager weatherManager = weatherManagerList.get(i);
				if (weatherManager.getWeatherType().isHasSound())
				{
					if (config.toggleAmbience())
					{
						handleSoundChanges(weatherManager);
						return;
					}

					weatherManager.stopManagerSoundPlayers();
				}
			}
			return;
		}

		if (key.equals("enableRain"))
		{
			handleConfigEnableChange(Weather.RAINY, Boolean.getBoolean(event.getNewValue()));
			handleConfigEnableChange(Weather.STORMY, Boolean.getBoolean(event.getNewValue()));
		}

		if (key.equals("enableSnow"))
			handleConfigEnableChange(Weather.SNOWY, Boolean.getBoolean(event.getNewValue()));

		if (key.equals("enableClouds"))
		{
			handleConfigEnableChange(Weather.CLOUDY, Boolean.getBoolean(event.getNewValue()));
			handleConfigEnableChange(Weather.PARTLY_CLOUDY, Boolean.getBoolean(event.getNewValue()));
		}

		if (key.equals("enableAsh"))
			handleConfigEnableChange(Weather.ASHFALL, Boolean.getBoolean(event.getNewValue()));

		if (key.equals("enableFog"))
			handleConfigEnableChange(Weather.FOGGY, Boolean.getBoolean(event.getNewValue()));

		if (key.equals("enableStars"))
			handleConfigEnableChange(Weather.STARRY, Boolean.getBoolean(event.getNewValue()));


		if (event.getKey().equals("enableWintertodtSnow"))
		{
			if (config.enableWintertodtSnow())
				return;

			clientThread.invokeLater(() -> {
				Player player = client.getLocalPlayer();
				if (player == null)
					return;

				int playerChunk = player.getWorldLocation().getRegionID();

				if (playerChunk == WINTERTODT_CHUNK)
				{
					for (int i = 0; i < weatherManagerList.size(); i++)
					{
						WeatherManager weatherManager = weatherManagerList.get(i);
						if (weatherManager.getWeatherType() == Weather.SNOWY)
						{
							clearWeatherObjects(weatherManager);
							weatherManager.stopManagerSoundPlayers();
						}
					}
				}
			});
		}

		// When intensity changes, rain droplet length is baked into the model so we need to
		// rebuild the models. Same when shadow opacity changes.
		if (key.equals("weatherIntensity"))
		{
			clientThread.invoke(modelHandler::loadModels);
		}

		if (key.equals("cloudShadowOpacity"))
		{
			clientThread.invoke(modelHandler::rebuildCloudShadowModels);
		}

		if (key.equals("snowAccumulationOpacity"))
		{
			clientThread.invoke(modelHandler::rebuildSnowGroundModels);
		}

		// Clearing or restoring shadows on toggle.
		if (key.equals("enableCloudShadows"))
		{
			clientThread.invoke(() -> {
				for (WeatherManager wm : weatherManagerList)
				{
					if (wm.getWeatherType() != Weather.CLOUDY && wm.getWeatherType() != Weather.PARTLY_CLOUDY)
						continue;
					for (WeatherObject wo : wm.getWeatherObjArray())
					{
						RuneLiteObject shadow = wo.getShadowObject();
						if (shadow != null)
							shadow.setActive(config.enableCloudShadows());
					}
				}
			});
		}

		if (key.equals("enableSnowAccumulation"))
		{
			clientThread.invoke(() -> {
				for (WeatherManager wm : weatherManagerList)
				{
					if (wm.getWeatherType() != Weather.SNOWY)
						continue;
					for (WeatherObject wo : wm.getWeatherObjArray())
					{
						RuneLiteObject accumulation = wo.getShadowObject();
						if (accumulation != null)
							accumulation.setActive(config.enableSnowAccumulation());
					}
				}
			});
		}

		if (key.equals("enableSkybox"))
		{
			skyboxController.reset();
			if (!config.enableSkybox())
				clientThread.invoke(() -> client.setSkyboxColor(0));
		}

		if (key.equals("disableStockSkybox"))
		{
			if (config.disableStockSkybox())
				toggleStockSkyboxIfNeeded(true);
			else
				restoreStockSkybox();
		}
	}

	private int getAmbientVolume()
	{
		return config.useAreaSoundsVolume() ?
			client.getPreferences().getAreaSoundEffectVolume() : config.ambientVolume();
	}

	private void handleConfigEnableChange(Weather weather, boolean enabled)
	{
		if (enabled)
			return;

		for (int i = 0; i < weatherManagerList.size(); i++)
		{
			WeatherManager weatherManager = weatherManagerList.get(i);
			if (weatherManager.getWeatherType() == weather)
			{
				clientThread.invoke(() -> clearWeatherObjects(weatherManager));
				clientThread.invoke(weatherManager::stopManagerSoundPlayers);
			}
		}
	}

	private void handleConfigDensityChange(Weather weatherType, int newDensity)
	{
		for (int i = 0; i < weatherManagerList.size(); i++)
		{
			WeatherManager weatherManager = weatherManagerList.get(i);
			Weather weather = weatherManager.getWeatherType();
			if (weather != weatherType)
				continue;

			if (weather.isHasPrecipitation())
			{
				ArrayList<WeatherObject> array = weatherManager.getWeatherObjArray();

				while (array.size() > newDensity)
				{
					removeWeatherObject(0, array);
				}
			}
		}
	}

	public void handleWeatherManagers()
	{
		boolean activeManager = false;
		for (int i = 0; i < weatherManagerList.size(); i++)
		{
			WeatherManager weatherManager = weatherManagerList.get(i);
			weatherManager.setFading(true);
			if (weatherManager.getWeatherType() == currentWeather)
			{
				weatherManager.setFading(false);
				activeManager = true;
			}
		}

		if (!activeManager && currentWeather.isHasPrecipitation())
			SwingUtilities.invokeLater(this::createWeatherManager);
	}

	public void handleSoundChanges(WeatherManager weatherManager)
	{
		//Update soundplayer timers
		for (SoundPlayer sp : weatherManager.getSoundPlayers())
		{
			if (sp.isPlaying())
			{
				sp.setTimer(sp.getTimer() + 1);
			}
		}

		Weather weather = weatherManager.getWeatherType();

		if (!weather.isHasSound() || !config.toggleAmbience())
			return;

		if (config.weatherType() != CyclesConfig.WeatherType.DYNAMIC && config.disableWeatherUnderground() && (currentBiome == Biome.CAVE || currentBiome == Biome.LAVA_CAVE))
			return;

		//Fade out inappropriate weathermanager soundplayers
		if (weather != currentWeather)
		{
			for (SoundPlayer sp : weatherManager.getSoundPlayers())
			{
				if (sp.isPlaying())
				{
					if (!sp.isFading())
					{
						sp.setFading(true);
						sp.smoothVolumeChange(0, 6000);
					}
				}
			}
			return;
		}
		else
		{
			//This happens when you switch from weather condition A to weather condition B and then back to A again before the first transition was finished
			if (weatherManager.getPrimarySoundPlayer().isFading())
			{
				weatherManager.getPrimarySoundPlayer().setFading(false);
				weatherManager.getPrimarySoundPlayer().getVolumeChangeHandler().interrupt(); //stop fading oot
			}
		}

		//The following code of this function only runs on the appropriate weathermanager

		SoundEffect appropriateSound;
		boolean muffled = false;
		SoundEffect outdoorSound = weatherManager.getWeatherType().getSoundEffect();
		if (isPlayerIndoors && !config.disableIndoorMuffling())
		{
			if (outdoorSound == RAIN)
			{
				appropriateSound = RAIN_MUFFLED;
				muffled = true;
			}
			else if (outdoorSound == THUNDERSTORM)
			{
				appropriateSound = THUNDERSTORM_MUFFLED;
				muffled = true;
			}
			else if (outdoorSound == WIND)
			{
				appropriateSound = WIND_MUFFLED;
				muffled = true;
			}
			else
			{
				appropriateSound = outdoorSound;
			}
		}
		else
		{
			appropriateSound = outdoorSound;
		}

		// Make sure the primary soundplayer is at the right volume, if it's not already fading in or whatever
		int currentVolume = weatherManager.getPrimarySoundPlayer().getCurrentVolume();
		int volumeGoal = getVolumeGoal(muffled, weather);
		int changeRate = 6000;

		if (weatherManager.getPrimarySoundPlayer().getCurrentTrack() != null && currentVolume != volumeGoal)
		{
			//If the volume change handler is uninitialized, or is initialized and isn't currently changing the volume
			if (weatherManager.getPrimarySoundPlayer().getVolumeChangeHandler() == null || !weatherManager.getPrimarySoundPlayer().getVolumeChangeHandler().isAlive())
			{
				log.debug("Primary at wrong volume. Setting back to " + volumeGoal);
				weatherManager.getPrimarySoundPlayer().smoothVolumeChange(volumeGoal, changeRate);
			}
		}

		// Initialize the primary soundplayer if it ain't initialized yet, or is not playing for some reason
		if (weatherManager.getPrimarySoundPlayer().getCurrentTrack() == null || !weatherManager.getPrimarySoundPlayer().isPlaying())
		{
			log.debug("Initializing soundplayer at volume " + (int)(getAmbientVolume() * getWeatherDensityFactor(weather)));
			weatherManager.getPrimarySoundPlayer().setVolumeLevel(0);
			weatherManager.getPrimarySoundPlayer().smoothVolumeChange(volumeGoal, 12000);
			weatherManager.getPrimarySoundPlayer().playClip(appropriateSound);
		}
		//Handle looping, as well as muffling/unmuffling of sound when player walks indoors/outdoors
		else if (weatherManager.getPrimarySoundPlayer().getCurrentTrack() != appropriateSound || weatherManager.getPrimarySoundPlayer().getTimer() > 230)
		{
			log.debug("Looping because " + weatherManager.getPrimarySoundPlayer().getCurrentTrack() + " != " + appropriateSound + " or it was just time to loop");
			weatherManager.getPrimarySoundPlayer().smoothVolumeChange(0, changeRate);
			weatherManager.switchSoundPlayerPriority();
			if (!weatherManager.getPrimarySoundPlayer().isPlaying())
			{
				weatherManager.getPrimarySoundPlayer().setVolumeLevel(0);
				weatherManager.getPrimarySoundPlayer().playClip(appropriateSound);
			}
			weatherManager.getPrimarySoundPlayer().smoothVolumeChange(volumeGoal, changeRate);
		}

		if (weather == Weather.STORMY && (weatherManager.getPrimarySoundPlayer().getTimer() == 90 || weatherManager.getPrimarySoundPlayer().getTimer() == 138))
			flashLightning = true;
	}

	public int getObjectTarget(Weather weather)
	{
		int base;
		switch (weather)
		{
			case ASHFALL:
				base = config.ashfallDensity(); break;
			case FOGGY:
				base = config.foggyDensity(); break;
			case RAINY:
				base = config.rainDensity(); break;
			case SNOWY:
				base = config.snowDensity(); break;
			case CLOUDY:
				base = config.cloudyDensity(); break;
			case STARRY:
				base = config.starryDensity(); break;
			case STORMY:
				base = config.stormDensity(); break;
			case PARTLY_CLOUDY:
				base = config.partlyCloudyDensity(); break;
			default:
			case SUNNY:
			case COVERED:
				return 0;
		}

		// Scale precipitation density with the master intensity so EXTREME genuinely fills the
		// air. Clouds/fog/stars don't get scaled because the per-weather sliders already make
		// sense as-is for those (and clouds use the dedicated shadow path).
		if (weather == Weather.RAINY || weather == Weather.STORMY || weather == Weather.SNOWY || weather == Weather.ASHFALL)
		{
			float scale;
			switch (config.weatherIntensity())
			{
				case LIGHT:    scale = 0.45f; break;
				default:
				case MODERATE: scale = 1.0f; break;
				case HEAVY:    scale = 1.5f; break;
				case EXTREME:  scale = 2.0f; break;
			}
			int max = weather.getMaxObjects();
			int scaled = (int) (base * scale);
			return Math.min(scaled, max);
		}

		return base;
	}

	public int getVolumeGoal(boolean muffled, Weather weather)
	{
		double weatherDensityFactor = getWeatherDensityFactor(weather);
		double volumeDouble = getAmbientVolume() * weatherDensityFactor;

		if (muffled)
			volumeDouble = config.muffledVolume() * weatherDensityFactor;

		return (int) volumeDouble;
	}

	public double getWeatherDensityFactor(Weather weather)
	{
		double factor = (double) weather.getMaxObjects() / (double) weather.getMaxObjectVolume();
		if (factor > 1)
			factor = 1;

		return factor;
	}

	public boolean weatherEnabled(Weather weather)
	{
		switch (weather)
		{
			case ASHFALL:
				return config.enableAsh();
			case FOGGY:
				return config.enableFog();
			case RAINY:
			case STORMY:
				return config.enableRain();
			case SNOWY:
				return config.enableSnow();
			case CLOUDY:
			case PARTLY_CLOUDY:
				return config.enableClouds();
			case STARRY:
				return config.enableStars();
			default:
			case COVERED:
			case SUNNY:
				return true;
		}
	}

	public void createWeatherManager()
	{
		SoundPlayer[] soundPlayers = new SoundPlayer[]{new SoundPlayer(), new SoundPlayer()};
		WeatherManager weatherManager = new WeatherManager(currentWeather, soundPlayers, 0, new ArrayList<>(), 0, false);
		weatherManagerList.add(weatherManager);
	}

	public void fadeWeatherManager(WeatherManager weatherManager)
	{
		int trimNumber = weatherManager.getWeatherType().getMaxObjects() / OBJ_ROTATION_CONSTANT;
		ArrayList<WeatherObject> array = weatherManager.getWeatherObjArray();

		if (trimNumber < array.size() / OBJ_ROTATION_CONSTANT)
			trimNumber = array.size() / OBJ_ROTATION_CONSTANT;

		if (trimNumber == 0)
			trimNumber = 1;

		trimWeatherArray(weatherManager, 0, trimNumber);
	}

	public void clearFadedWeatherManagers()
	{
		for (int i = 0; i < weatherManagerList.size(); i++)
		{
			WeatherManager weatherManager = weatherManagerList.get(i);

			if (weatherManager.isFading() && weatherManager.getWeatherObjArray().isEmpty())
			{
				boolean readyToRemove = true;

				for (SoundPlayer soundPlayer : weatherManager.getSoundPlayers())
				{
					if (soundPlayer.isPlaying())
					{
						readyToRemove = false;
					}
				}

				if (readyToRemove)
				{
					weatherManagerList.remove(weatherManager);
					i--;
				}
			}
		}
	}

	public void clearAllWeatherManagers()
	{
		for (int i = 0; i < weatherManagerList.size(); i++)
		{
			WeatherManager weatherManager = weatherManagerList.get(i);
			clearWeatherObjects(weatherManager);
			weatherManager.stopManagerSoundPlayers();
		}
		weatherManagerList.clear();
	}

	public void handleWeatherChanges(WeatherManager weatherManager)
	{
		Weather weather = weatherManager.getWeatherType();

		if (config.weatherType() != CyclesConfig.WeatherType.DYNAMIC && config.disableWeatherUnderground() && (currentBiome == Biome.CAVE || currentBiome == Biome.LAVA_CAVE))
			return;

		if (!weatherEnabled(weather))
			return;

		if (!config.enableWintertodtSnow() && weather == Weather.SNOWY)
		{
			int playerChunk = client.getLocalPlayer().getWorldLocation().getRegionID();
			if (playerChunk == WINTERTODT_CHUNK)
			{
				return;
			}
		}

		int objectTarget = getObjectTarget(weather);
		ArrayList<WeatherObject> array = weatherManager.getWeatherObjArray();

		if (array.size() < objectTarget)
		{
			if (zoneObjRecovery > 0)
			{
				relocateObjects(weatherManager, objectTarget / (OBJ_ROTATION_CONSTANT / 2));
				zoneObjRecovery--;
			}
			renderWeather(objectTarget / OBJ_ROTATION_CONSTANT, weatherManager);
		}
		else if (array.size() == objectTarget && client.getTickCount() % weather.getChangeRate() == 0)
		{
			relocateObjects(weatherManager, objectTarget / OBJ_ROTATION_CONSTANT);
		}
	}

	private void renderWeather(int objects, WeatherManager weatherManager)
	{
		Weather weather = weatherManager.getWeatherType();
		int z = client.getPlane();
		ArrayList<WeatherObject> array = weatherManager.getWeatherObjArray();
		Animation weatherAnimation = modelHandler.getWeatherAnimation(weather);
		int alternate = 1;
		ArrayList<Tile> availableTiles = getAvailableTiles();

		for (int i = 0; i < objects; i++)
		{
			int roll;
			Tile openTile;
			switch (weather)
			{
				default:
					roll = random.nextInt(availableTiles.size());
					openTile = availableTiles.get(roll);
			}

			WeatherObject weatherObject = createWeatherObject(weather, weatherAnimation, openTile.getLocalLocation(), z, alternate);
			alternate += 1;
			if (alternate > weather.getModelVariety())
				alternate = 1;

			array.add(weatherObject);
			if (array.size() == getObjectTarget(weather))
				return;
		}
	}

	public WeatherObject createWeatherObject(Weather weather, Animation weatherAnimation, LocalPoint lp, int plane, int objectVariant)
	{
		RuneLiteObject runeLiteObject = client.createRuneLiteObject();
		Model weatherModel = modelHandler.getWeatherModel(weather, objectVariant);
		int radius = modelHandler.getModelRadius(weather);
		boolean drawFrontTilesFirst = true;
		if (plane > 0)
		{
			radius = 0;
			drawFrontTilesFirst = false;
		}

		runeLiteObject.setModel(weatherModel);
		runeLiteObject.setRadius(radius);
		runeLiteObject.setDrawFrontTilesFirst(drawFrontTilesFirst);
		runeLiteObject.setAnimation(weatherAnimation);
		runeLiteObject.setLocation(lp, plane);
		runeLiteObject.setShouldLoop(true);
		runeLiteObject.setActive(true);

		WeatherObject wo = new WeatherObject(runeLiteObject, objectVariant);

		// Clouds get a paired ground-shadow object sitting on the same tile at z=0. The shadow
		// model itself is pre-flattened and translated to the floor, so we just place a flat
		// dark disc that visually reads as the cloud's shadow on the terrain.
		if ((weather == Weather.CLOUDY || weather == Weather.PARTLY_CLOUDY) && config.enableCloudShadows())
		{
			RuneLiteObject shadow = client.createRuneLiteObject();
			shadow.setModel(modelHandler.getCloudShadowModel(objectVariant));
			shadow.setRadius(0);
			shadow.setDrawFrontTilesFirst(false);
			shadow.setLocation(lp, plane);
			shadow.setActive(true);
			wo.setShadowObject(shadow);
		}

		// Snow gets a paired ground-accumulation disc — same WeatherObject "shadow" slot, just
		// repurposed as a soft white dusting. As more flakes spawn the discs overlap to form a
		// cumulative blanket of snow on whatever terrain you're standing on.
		if (weather == Weather.SNOWY && config.enableSnowAccumulation())
		{
			RuneLiteObject accumulation = client.createRuneLiteObject();
			accumulation.setModel(modelHandler.getSnowGroundModel(objectVariant));
			accumulation.setRadius(0);
			accumulation.setDrawFrontTilesFirst(false);
			accumulation.setLocation(lp, plane);
			accumulation.setActive(true);
			wo.setShadowObject(accumulation);
		}

		return wo;
	}

	public void removeWeatherObject(int index, ArrayList<WeatherObject> weatherArray)
	{
		if (index >= weatherArray.size())
			return;

		WeatherObject weatherObject = weatherArray.get(index);
		clientThread.invokeLater(() -> {
			weatherObject.getRuneLiteObject().setActive(false);
			if (weatherObject.getShadowObject() != null)
				weatherObject.getShadowObject().setActive(false);
		});
		weatherArray.remove(index);
	}

	public void clearWeatherObjects(WeatherManager weatherManager)
	{
		ArrayList<WeatherObject> array = weatherManager.getWeatherObjArray();

		for (WeatherObject weatherObject : array)
		{
			weatherObject.getRuneLiteObject().setActive(false);
			if (weatherObject.getShadowObject() != null)
				weatherObject.getShadowObject().setActive(false);
		}

		array.clear();
	}

	public void trimWeatherArray(WeatherManager weatherManager, int start, int end)
	{
		for (int i = start; i < end; i++)
			removeWeatherObject(start, weatherManager.getWeatherObjArray());
	}

	public void relocateObjects(WeatherManager weatherManager, int numToRelocate)
	{
		int z = client.getPlane();
		int beginRotation = weatherManager.getStartRotation();
		ArrayList<WeatherObject> array = weatherManager.getWeatherObjArray();
		Weather weather = weatherManager.getWeatherType();
		ArrayList<Tile> availableTiles = getAvailableTiles();

		for (int i = beginRotation; i < beginRotation + numToRelocate; i++)
		{
			int roll;
			Tile nextTile;
			switch (weather)
			{
				/*
				case FOGGY:
					roll = random.nextInt(availableFogTiles.size());
					nextTile = availableFogTiles.get(roll);
					break;

				 */
				default:
					roll = random.nextInt(availableTiles.size());
					nextTile = availableTiles.get(roll);
			}

			if (i >= array.size())
			{
				break;
			}

			WeatherObject weatherObject = array.get(i);
			RuneLiteObject runeLiteObject = weatherObject.getRuneLiteObject();
			runeLiteObject.setLocation(nextTile.getLocalLocation(), z);
			runeLiteObject.setAnimation(modelHandler.getWeatherAnimation(weather));
			if (weatherObject.getShadowObject() != null)
			{
				weatherObject.getShadowObject().setLocation(nextTile.getLocalLocation(), z);
			}
		}

		weatherManager.setStartRotation(beginRotation + numToRelocate);

		if (beginRotation > getObjectTarget(weather))
			weatherManager.setStartRotation(0);
	}

	public void transitionZPlane()
	{
		if (!currentWeather.isHasPrecipitation())
		{
			clearAllWeatherManagers();
			return;
		}

		handleZoneTransition();
	}

	public void handleZoneTransition()
	{
		if (config.weatherType() != CyclesConfig.WeatherType.DYNAMIC && config.disableWeatherUnderground() && (currentBiome == Biome.CAVE || currentBiome == Biome.LAVA_CAVE))
		{
			clientThread.invoke(this::clearAllWeatherManagers);
			return;
		}

		for (int i = 0; i < weatherManagerList.size(); i++)
		{
			WeatherManager weatherManager = weatherManagerList.get(i);
			Weather weatherType = weatherManager.getWeatherType();
			ArrayList<WeatherObject> array = weatherManager.getWeatherObjArray();
			int size = (int) (array.size() * 0.8);
			clearWeatherObjects(weatherManager);
			if (weatherType == currentWeather)
			{
				renderWeather(size, weatherManager);
				zoneObjRecovery = 4;
			}
		}
	}

	public ArrayList<Tile> getAvailableTiles()
	{
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		byte[][][] settings = client.getTileSettings();
		int zLayer = client.getPlane();
		ArrayList<Tile> availableTiles = new ArrayList<>();

		for (int z = 0; z <= zLayer; z++)
		{
			for (int x = 0; x < Constants.SCENE_SIZE; ++x)
			{
				for (int y = 0; y < Constants.SCENE_SIZE; ++y)
				{
					Tile tile = tiles[z][x][y];

					if (tile == null)
						continue;

					int flag = settings[z][x][y];

					if ((flag & Constants.TILE_FLAG_UNDER_ROOF) != 0)
						continue;

					if (zLayer > 0)
					{
						boolean bridgeFlag = false;
						for (int i = 0; i < 4; i++)
						{
							int floorFlag = settings[i][x][y];
							if ((floorFlag & Constants.TILE_FLAG_BRIDGE) != 0)
							{
								bridgeFlag = true;
								break;
							}
						}

						if (bridgeFlag)
						{
							continue;
						}
					}

					availableTiles.add(tile);

					/*
					if (wallCollision(tile))
						continue;

					availableFogTiles.add(tile);

					 */
				}
			}
		}
		return availableTiles;
	}

	private boolean wallCollision(Tile targetTile)
	{
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		int zLayer = client.getPlane();

		for (int z = 0; z <= zLayer; z++)
		{
			for (int x = 0; x < Constants.SCENE_SIZE; ++x)
			{
				for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
					Tile tile = tiles[z][x][y];

					if (tile == null)
						continue;

					if (tile.getWallObject() == null)
						continue;

					if (tile.getLocalLocation().distanceTo(targetTile.getLocalLocation()) < FOG_RADIUS)
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	public void setConfigWeather()
	{
		switch(config.weatherType())
		{
			case ASHFALL:
				currentWeather = Weather.ASHFALL;
				break;
			default:
			case DYNAMIC:
				currentWeather = syncWeather(currentSeason, currentBiome);
				break;
			case CLOUDY:
				currentWeather = Weather.CLOUDY;
				break;
			case CLEAR:
				currentWeather = Weather.SUNNY;
				break;
			case FOGGY:
				currentWeather = Weather.FOGGY;
				break;
			case PARTLY_CLOUDY:
				currentWeather = Weather.PARTLY_CLOUDY;
				break;
			case RAINY:
				currentWeather = Weather.RAINY;
				break;
			case SNOWY:
				currentWeather = Weather.SNOWY;
				break;
			case STARRY:
				currentWeather = Weather.STARRY;
				break;
			case STORMY:
				currentWeather = Weather.STORMY;
				break;
		}
	}

	private Weather syncWeather(Season seasonCondition, Biome biomeCondition)
	{
		int totalMin = CyclesClock.getTimeHours() * 60 + CyclesClock.getTimeMinutes();
		int cycleSegment = (totalMin / 15) % 12;
		for (WeatherForecast forecast : WeatherForecast.values())
		{
			if (forecast.getSeasonCondition() == seasonCondition && forecast.getBiomeCondition() == biomeCondition)
			{
				return forecast.getForecastArray()[cycleSegment];
			}
		}
		return Weather.COVERED;
	}

	private void syncBiome()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
			return;

		WorldPoint wp = WorldPoint.fromLocalInstance(client, player.getLocalLocation(), client.getPlane());
		int playerChunk = wp.getRegionID();

		if (savedChunk != playerChunk)
		{
			currentBiome = BiomeChunkMap.checkBiome(playerChunk);
			savedChunk = playerChunk;
		}
	}

	private void syncSeason()
	{
		boolean is117Enabled = pluginManager.getPlugins().stream()
			.filter((plugin) -> plugin.getName().equals("117 HD"))
			.map((plugin) -> pluginManager.isPluginEnabled(plugin)).findFirst().orElse(false);

		// If the season type is 117 and it's not enabled then fallback to DYNAMIC
		CyclesConfig.SeasonType seasonType = config.seasonType().equals(HD_117) && !is117Enabled ? DYNAMIC : config.seasonType();

        switch (seasonType)
		{
			default:
			case DYNAMIC:
				switch ((CyclesClock.getTimeDays() / 7) % 4)
				{
					default:
					case 0:
						currentSeason = Season.SPRING;
						return;
					case 1:
						currentSeason = Season.SUMMER;
						return;
					case 2:
						currentSeason = Season.AUTUMN;
						return;
					case 3:
						currentSeason = Season.WINTER;
						return;
				}
			case SPRING:
				currentSeason =  Season.SPRING;
				return;
			case SUMMER:
				currentSeason =  Season.SUMMER;
				return;
			case AUTUMN:
				currentSeason =  Season.AUTUMN;
				return;
			case WINTER:
				currentSeason =  Season.WINTER;
			case HD_117:
				try
				{
					String seasonalTheme = configManager.getConfiguration("hd", "seasonalTheme", String.class);
					switch (seasonalTheme)
					{
						case "AUTOMATIC":
							// Not a fan of repeating 117's logic here, but can't think of a better way.
							// Source: https://github.com/117HD/RLHD/blob/ec91118e3190add9b821350576af56af1c723848/src/main/java/rs117/hd/HdPlugin.java#L2399-L2416
							ZonedDateTime time = ZonedDateTime.now(ZoneOffset.UTC);
							switch (time.getMonth()) {
								case SEPTEMBER:
								case OCTOBER:
								case NOVEMBER:
									currentSeason = Season.AUTUMN;
									break;
								case DECEMBER:
								case JANUARY:
								case FEBRUARY:
									currentSeason = Season.WINTER;
                                    break;
								default:
									currentSeason = Season.SUMMER;
                                    break;
							}
							break;
						case "SUMMER":
							currentSeason = Season.SUMMER;
							break;
						case "WINTER":
							currentSeason = Season.WINTER;
                            break;
						case "AUTUMN":
							currentSeason = Season.AUTUMN;
                    }
				}
				catch (Exception e)
				{}
				break;
		}
	}

	/**
	 * 0..1 multiplier driven by the WeatherIntensity config. Used by the skybox, tint, rain model
	 * lengths and cloud shadow strength so a single dial moves the whole "vibe" in lockstep.
	 */
	public float getIntensityMultiplier()
	{
		switch (config.weatherIntensity())
		{
			case LIGHT:    return 0.35f;
			default:
			case MODERATE: return 0.65f;
			case HEAVY:    return 0.9f;
			case EXTREME:  return 1.0f;
		}
	}

	/**
	 * If the stock RuneLite "Skybox" plugin is enabled, turn it off so we get exclusive control
	 * of {@code client.setSkyboxColor}. Remember its prior state so we can restore on shutdown.
	 */
	private void toggleStockSkyboxIfNeeded(boolean takingOver)
	{
		if (!config.disableStockSkybox() || !config.enableSkybox())
			return;

		Plugin stock = pluginManager.getPlugins().stream()
			.filter(p -> "Skybox".equals(p.getClass().getAnnotation(PluginDescriptor.class) != null
				? p.getClass().getAnnotation(PluginDescriptor.class).name() : ""))
			.findFirst().orElse(null);

		if (stock == null)
			return;

		if (takingOver && pluginManager.isPluginEnabled(stock))
		{
			stockSkyboxWasEnabled = true;
			SwingUtilities.invokeLater(() -> {
				try
				{
					pluginManager.setPluginEnabled(stock, false);
					pluginManager.stopPlugin(stock);
				}
				catch (Exception e)
				{
					log.warn("Failed to stop stock Skybox plugin", e);
				}
			});
		}
	}

	private void restoreStockSkybox()
	{
		if (!stockSkyboxWasEnabled)
			return;

		Plugin stock = pluginManager.getPlugins().stream()
			.filter(p -> "Skybox".equals(p.getClass().getAnnotation(PluginDescriptor.class) != null
				? p.getClass().getAnnotation(PluginDescriptor.class).name() : ""))
			.findFirst().orElse(null);

		if (stock == null)
			return;

		final Plugin toStart = stock;
		SwingUtilities.invokeLater(() -> {
			try
			{
				pluginManager.setPluginEnabled(toStart, true);
				pluginManager.startPlugin(toStart);
			}
			catch (Exception e)
			{
				log.warn("Failed to re-enable stock Skybox plugin", e);
			}
		});
		stockSkyboxWasEnabled = false;
	}

	@Provides
    CyclesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CyclesConfig.class);
	}
}