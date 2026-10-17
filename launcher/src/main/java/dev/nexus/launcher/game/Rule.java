package dev.nexus.launcher.game;

import dev.nexus.launcher.core.OS;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** A rule of a library or argument: allow/disallow depending on OS, architecture and launcher features. */
public final class Rule {
    public String action;
    public OsRule os;
    public Map<String, Boolean> features;

    public static final class OsRule {
        public String name;
        public String version;
        public String arch;
    }

    /**
     * Mojang semantics: without rules the entry applies; with rules it starts disallowed and every matching
     * rule sets the result to its action.
     */
    public static boolean allows(List<Rule> rules, Map<String, Boolean> enabledFeatures) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (Rule r : rules) {
            if (r != null && r.matches(enabledFeatures)) allowed = !"disallow".equals(r.action);
        }
        return allowed;
    }

    private boolean matches(Map<String, Boolean> enabledFeatures) {
        if (os != null) {
            if (os.name != null && !os.name.equals(OS.ruleName())) return false;
            if (os.arch != null && !archMatches(os.arch)) return false;
            if (os.version != null) {
                try {
                    if (!Pattern.compile(os.version).matcher(OS.VERSION).find()) return false;
                } catch (PatternSyntaxException e) {
                    return false;
                }
            }
        }
        if (features != null) {
            for (Map.Entry<String, Boolean> f : features.entrySet()) {
                boolean have = enabledFeatures.getOrDefault(f.getKey(), false);
                if (have != Boolean.TRUE.equals(f.getValue())) return false;
            }
        }
        return true;
    }

    private static boolean archMatches(String arch) {
        return switch (arch) {
            case "x86" -> OS.ARCH.equals("x86");
            case "x86_64", "amd64" -> OS.ARCH.equals("x86_64");
            case "arm64", "aarch64" -> OS.ARCH.equals("arm64");
            default -> arch.equals(OS.ARCH);
        };
    }
}
