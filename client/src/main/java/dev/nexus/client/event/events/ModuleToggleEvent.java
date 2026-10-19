package dev.nexus.client.event.events;

import dev.nexus.client.module.Module;

/** Posted after a module changed its enabled state. {@code byKeybind} is true when toggled via hotkey. */
public record ModuleToggleEvent(Module module, boolean enabled, boolean byKeybind) {}
