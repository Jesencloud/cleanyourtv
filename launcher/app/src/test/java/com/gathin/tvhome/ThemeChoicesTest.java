package com.gathin.tvhome;

import android.content.SharedPreferences;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Theme resets clear appearance overrides once, without treating unavailable storage as a reset. */
public class ThemeChoicesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final Preferences prefs = new Preferences();
    private static final String SIGNATURE = "theme_signature";
    private static final Map<String, Object> APPEARANCE = new HashMap<>();
    static {
        APPEARANCE.put("columns", 7);
        APPEARANCE.put("rows_below_dock", 3);
        APPEARANCE.put("animations", false);
        APPEARANCE.put("advanced_effects", false);
        APPEARANCE.put("wallpaper_choice", "none");
        APPEARANCE.put("dock_opacity", 30);
        APPEARANCE.put("clock_format", "24");
    }

    @Test public void resetWithoutConfigClearsAllThemeOverrides() throws Exception {
        activateTheme();
        prefs.values.putAll(APPEARANCE);
        prefs.values.put("open_on_start", "example.player");
        prefs.values.put("focus_sound", false);
        prefs.values.put("settings_revision", 12L);
        assertTrue(file("config.json").delete());
        write("theme_revision", "reset-a");
        sync();
        assertCleared();
        assertEquals("absent:reset-a", prefs.values.get(SIGNATURE));
        assertEquals("example.player", prefs.values.get("open_on_start"));
        assertEquals(false, prefs.values.get("focus_sound"));
        assertEquals(12L, prefs.values.get("settings_revision"));
    }

    @Test public void resetOnFirstUseAlsoClearsOldAppearanceChoices() throws Exception {
        prefs.values.putAll(APPEARANCE);
        write("theme_revision", "reset-first");
        sync();
        assertCleared();
        assertEquals("absent:reset-first", prefs.values.get(SIGNATURE));
    }

    @Test public void loadingTheSameResetKeepsChoicesMadeAfterIt() throws Exception {
        write("theme_revision", "reset-a");
        sync();
        prefs.values.putAll(APPEARANCE);
        int commits = prefs.commits;
        sync();
        assertAppearanceKept();
        assertEquals(commits, prefs.commits);
    }

    @Test public void anotherResetWorksEvenWhenConfigWasAlreadyAbsent() throws Exception {
        write("theme_revision", "reset-a");
        sync();
        prefs.values.putAll(APPEARANCE);
        write("theme_revision", "reset-b");
        sync();
        assertCleared();
        assertEquals("absent:reset-b", prefs.values.get(SIGNATURE));
    }

    @Test public void pushingIdenticalConfigWithANewRevisionClearsChoices() throws Exception {
        activateTheme();
        prefs.values.putAll(APPEARANCE);
        write("theme_revision", "push-b");
        sync();
        assertCleared();
    }

    @Test public void anUnchangedThemeKeepsChoices() throws Exception {
        activateTheme();
        prefs.values.putAll(APPEARANCE);
        int commits = prefs.commits;
        sync();
        assertAppearanceKept();
        assertEquals(commits, prefs.commits);
    }

    @Test public void upgradingWithoutARevisionKeepsExistingChoices() throws Exception {
        write("config.json", "{}");
        prefs.values.putAll(APPEARANCE);
        sync();
        assertAppearanceKept();
        assertNotNull(prefs.values.get(SIGNATURE));
    }

    @Test public void firstUseWithoutAThemeKeepsExistingChoices() throws Exception {
        prefs.values.putAll(APPEARANCE);
        sync();
        assertAppearanceKept();
        assertEquals("absent:absent", prefs.values.get(SIGNATURE));
    }

    @Test public void anUnavailableDirectoryDoesNotRecordAReset() throws Exception {
        activateTheme();
        prefs.values.putAll(APPEARANCE);
        Map<String, Object> before = new HashMap<>(prefs.values);
        sync(null, null);
        File missing = file("missing-directory");
        sync(missing, new File(missing, "config.json"));
        sync(folder.getRoot(), null);
        assertEquals(before, prefs.values);
    }

    @Test public void anUnreadableDirectoryDoesNotRecordAReset() throws Exception {
        activateTheme();
        prefs.values.putAll(APPEARANCE);
        Map<String, Object> before = new HashMap<>(prefs.values);
        write("theme_revision", "push-b");
        File unreadable = new File(folder.getRoot().getPath()) {
            @Override public boolean canRead() { return false; }
        };
        sync(unreadable, file("config.json"));
        File unsearchable = new File(folder.getRoot().getPath()) {
            @Override public boolean canExecute() { return false; }
        };
        sync(unsearchable, file("config.json"));
        assertEquals(before, prefs.values);
    }

    @Test public void anUnreadableConfigDoesNotRecordAReset() throws Exception {
        activateTheme();
        prefs.values.putAll(APPEARANCE);
        Map<String, Object> before = new HashMap<>(prefs.values);
        write("theme_revision", "push-b");
        File unreadable = new File(file("config.json").getPath()) {
            @Override public boolean canRead() { return false; }
        };
        sync(folder.getRoot(), unreadable);
        assertEquals(before, prefs.values);
    }

    @Test public void aDirectoryInPlaceOfConfigIsNotAReset() throws Exception {
        activateTheme();
        prefs.values.putAll(APPEARANCE);
        Map<String, Object> before = new HashMap<>(prefs.values);
        assertTrue(file("config.json").delete());
        assertTrue(file("config.json").mkdir());
        sync();
        assertEquals(before, prefs.values);
    }

    @Test public void anEmptyRevisionIsRetriedAfterTheWriteFinishes() throws Exception {
        activateTheme();
        prefs.values.putAll(APPEARANCE);
        Map<String, Object> before = new HashMap<>(prefs.values);
        assertTrue(file("config.json").delete());
        write("theme_revision", "");
        sync();
        assertEquals(before, prefs.values);
        write("theme_revision", "reset-finished");
        sync();
        assertCleared();
    }

    @Test public void anInvalidRevisionDoesNotRecordAReset() throws Exception {
        activateTheme();
        prefs.values.putAll(APPEARANCE);
        Map<String, Object> before = new HashMap<>(prefs.values);
        write("theme_revision", "x".repeat(65));
        sync();
        assertEquals(before, prefs.values);
        assertTrue(file("theme_revision").delete());
        assertTrue(file("theme_revision").mkdir());
        sync();
        assertEquals(before, prefs.values);
    }

    private void activateTheme() throws Exception {
        write("config.json", "{}");
        write("theme_revision", "push-a");
        sync();
    }

    private File file(String name) { return new File(folder.getRoot(), name); }

    private void write(String name, String contents) throws Exception {
        Files.write(file(name).toPath(), contents.getBytes(StandardCharsets.US_ASCII));
    }

    private void sync() throws Exception { sync(folder.getRoot(), file("config.json")); }

    private void sync(File dir, File config) throws Exception {
        Method method = Config.class.getDeclaredMethod("syncThemeChoices", SharedPreferences.class, File.class, File.class);
        method.setAccessible(true);
        method.invoke(null, prefs.proxy, dir, config);
    }

    private void assertCleared() {
        for (String key : APPEARANCE.keySet()) assertFalse(key, prefs.values.containsKey(key));
    }

    private void assertAppearanceKept() {
        for (String key : APPEARANCE.keySet()) assertEquals(key, APPEARANCE.get(key), prefs.values.get(key));
    }

    /** In-memory preferences; unexpected API calls fail instead of returning permissive defaults. */
    private static final class Preferences {
        final Map<String, Object> values = new HashMap<>();
        int commits;
        final SharedPreferences proxy = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, (p, method, args) -> {
                    if (method.getName().equals("getString")) return values.getOrDefault(args[0], args[1]);
                    if (method.getName().equals("edit")) return editor();
                    throw new AssertionError("unexpected preferences call: " + method.getName());
                });

        private SharedPreferences.Editor editor() {
            Map<String, Object> writes = new HashMap<>();
            Set<String> removals = new HashSet<>();
            return (SharedPreferences.Editor) Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "remove": removals.add((String) args[0]); return proxy;
                            case "putString": writes.put((String) args[0], args[1]); return proxy;
                            case "commit":
                                values.keySet().removeAll(removals);
                                values.putAll(writes);
                                commits++;
                                return true;
                            default: throw new AssertionError("unexpected editor call: " + method.getName());
                        }
                    });
        }
    }
}
