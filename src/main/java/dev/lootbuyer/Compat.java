package dev.lootbuyer;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Differences between Paper 1.16.5 and newer servers.
 * Anything that isn't in the 1.16.5 API goes through here.
 */
final class Compat {
    private static Method translationKeyMethod;
    private static boolean translationKeyLookedUp;

    private Compat() {
    }

    /** "1.21.4", "26.3" — for log messages. Newer Bukkit versions look like "26.3.build.223-…". */
    static String version() {
        Matcher m = Pattern.compile("^\\d+(\\.\\d+)*").matcher(Bukkit.getBukkitVersion());
        return m.find() ? m.group() : Bukkit.getBukkitVersion();
    }

    static boolean isSellable(Material m) {
        return m != null && !m.isAir() && m.isItem();
    }

    /**
     * Client translation key, so item names show in each player's own language.
     * The method name changed between Paper versions, so it's looked up once by reflection.
     * Returns null if none exists — callers fall back to an English name.
     */
    static synchronized String translationKey(Material m) {
        if (!translationKeyLookedUp) {
            translationKeyLookedUp = true;
            for (String name : new String[]{"translationKey", "getTranslationKey", "getItemTranslationKey"}) {
                try {
                    Method method = Material.class.getMethod(name);
                    if (method.getReturnType() == String.class) {
                        translationKeyMethod = method;
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                    // try the next name
                }
            }
        }
        if (translationKeyMethod == null) return null;
        try {
            Object key = translationKeyMethod.invoke(m);
            return key instanceof String ? (String) key : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** IRON_INGOT → "Iron Ingot" */
    static String prettyName(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String word : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
