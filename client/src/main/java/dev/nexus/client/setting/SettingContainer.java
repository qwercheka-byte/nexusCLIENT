package dev.nexus.client.setting;

import java.util.List;

/** Anything owning a list of settings (modules, client settings). */
public interface SettingContainer {
    List<Setting<?>> getSettings();

    /** Called after one of the owned settings changed its value. */
    void onSettingChanged(Setting<?> setting);

    /** Attaches the setting to this container so change notifications are delivered. */
    default <S extends Setting<?>> S attach(S setting) {
        setting.bind(this);
        return setting;
    }

    default Setting<?> findSetting(String id) {
        for (Setting<?> s : getSettings()) if (s.getId().equals(id)) return s;
        return null;
    }
}
