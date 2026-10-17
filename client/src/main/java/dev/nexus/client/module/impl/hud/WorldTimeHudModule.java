package dev.nexus.client.module.impl.hud;

import net.minecraft.world.attribute.EnvironmentAttributes;
import dev.nexus.client.hud.HudModule;
import dev.nexus.client.hud.HudStyle;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.ui.theme.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;

import java.time.LocalTime;

/** In-game clock and day counter, optionally with weather, moon phase and real time. */
public final class WorldTimeHudModule extends HudModule {
    private static final String[] MOON = {"Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
            "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"};

    private final ModeSetting clock = add(new ModeSetting("clock", "Clock format", "12 or 24 hour clock", "24h", "24h", "12h"));
    private final BooleanSetting day = add(new BooleanSetting("day", "Day counter", "Show the current day", true));
    private final BooleanSetting icon = add(new BooleanSetting("icon", "Sun / moon icon", "Show whether it is day or night", true));
    private final BooleanSetting weather = add(new BooleanSetting("weather", "Weather", "Show rain / thunder", false));
    private final BooleanSetting moon = add(new BooleanSetting("moon", "Moon phase", "Show the moon phase", false));
    private final BooleanSetting realTime = add(new BooleanSetting("real_time", "Real time", "Show your local time", false));

    private long cachedMinuteKey = Long.MIN_VALUE;
    private long cachedRealMinute = Long.MIN_VALUE;
    private int stamp = -1, changes;
    private String mainText = "", subText = "", realText = "";
    private boolean night;
    private int cachedWeather = -1;

    public WorldTimeHudModule() {
        super("world_time_hud", "World Time HUD", "Shows the in-game time and day, plus optional weather and moon phase.",
                false, END, END, 4, 4);
    }

    @Override
    public void onSettingChanged(dev.nexus.client.setting.Setting<?> setting) {
        changes++;
        super.onSettingChanged(setting);
    }

    @Override
    public boolean render(DrawContext ctx, boolean editor) {
        ClientWorld world = mc.world;
        if (world == null) return false;
        long time = world.getTimeOfDay();
        long dayTime = Math.floorMod(time, 24000L);
        int minuteOfDay = (int) ((dayTime * 1440L / 24000L + 360) % 1440); // tick 0 = 06:00
        long minuteKey = (time / 24000L) * 1440L + minuteOfDay;
        boolean raining = world.isRaining(), thunder = world.isThundering();
        int weatherKey = thunder ? 2 : raining ? 1 : 0;
        if (minuteKey != cachedMinuteKey || stamp != changes || weatherKey != cachedWeather) {
            cachedWeather = weatherKey;
            cachedMinuteKey = minuteKey;
            stamp = changes;
            int h = minuteOfDay / 60, m = minuteOfDay % 60;
            String clockText;
            if (clock.is("12h")) {
                int h12 = h % 12 == 0 ? 12 : h % 12;
                clockText = h12 + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM");
            } else {
                clockText = (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
            }
            mainText = day.isOn() ? "Day " + (time / 24000L + 1) + "  " + clockText : clockText;
            StringBuilder sb = new StringBuilder();
            if (weather.isOn()) sb.append(thunder ? "Thunder" : raining ? "Rain" : "Clear");
            if (moon.isOn()) {
                if (!sb.isEmpty()) sb.append(" · ");
                sb.append(MOON[Math.floorMod(world.getEnvironmentAttributes().getAttributeValue(EnvironmentAttributes.MOON_PHASE_VISUAL).getIndex(), MOON.length)]);
            }
            subText = sb.toString();
            night = dayTime >= 12542 && dayTime < 23460;
        }
        if (realTime.isOn()) {
            long realMinute = System.currentTimeMillis() / 60000L;
            if (realMinute != cachedRealMinute) {
                cachedRealMinute = realMinute;
                LocalTime now = LocalTime.now();
                realText = "Local " + (now.getHour() < 10 ? "0" : "") + now.getHour() + ":" + (now.getMinute() < 10 ? "0" : "") + now.getMinute();
            }
        }

        float pad = HudStyle.PAD_X;
        float iconW = icon.isOn() ? 12 : 0;
        float textW = TextRender.width(mainText);
        if (!subText.isEmpty()) textW = Math.max(textW, TextRender.width(subText));
        if (realTime.isOn()) textW = Math.max(textW, TextRender.width(realText));
        int lines = 1 + (subText.isEmpty() ? 0 : 1) + (realTime.isOn() ? 1 : 0);
        float w = pad * 2 + iconW + textW;
        float h = HudStyle.PAD_Y * 2 + lines * 10 - 1;
        setSize(w, h);
        HudStyle.panel(ctx, this, w, h);
        float x = pad;
        if (icon.isOn()) {
            IconRender.draw(ctx, night ? Icon.MOON : Icon.SUN, x - 1, HudStyle.PAD_Y, 9, night ? 0xFFB4C2FF : 0xFFFFD166);
            x += iconW;
        }
        float y = HudStyle.PAD_Y + 1;
        HudStyle.text(ctx, mainText, x, y, HudStyle.VALUE);
        if (!subText.isEmpty()) {
            y += 10;
            HudStyle.text(ctx, subText, x, y, HudStyle.LABEL);
        }
        if (realTime.isOn()) {
            y += 10;
            HudStyle.text(ctx, realText, x, y, Theme.accentBright());
        }
        return true;
    }
}
