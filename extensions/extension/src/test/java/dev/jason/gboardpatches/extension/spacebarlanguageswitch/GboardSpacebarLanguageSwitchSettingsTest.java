package dev.jason.gboardpatches.extension.spacebarlanguageswitch;

import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class GboardSpacebarLanguageSwitchSettingsTest {
    @Test
    public void missingPreferenceDefaultsToEnabled() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();

        Assert.assertTrue(GboardSpacebarLanguageSwitchSettings.DEFAULT_ENABLED);
        Assert.assertTrue(GboardSpacebarLanguageSwitchSettings.readEnabled(preferences));
        Assert.assertTrue(GboardSpacebarLanguageSwitchSettings.readEnabled((SharedPreferences) null));
    }

    @Test
    public void ensureDefaultPersistsEnabledAndPreservesAnExistingChoice() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();

        GboardSpacebarLanguageSwitchSettings.ensureDefault(preferences);
        Assert.assertEquals(Boolean.TRUE,
                preferences.values.get(GboardSpacebarLanguageSwitchSettings.PREF_KEY_ENABLED));

        preferences.values.put(
                GboardSpacebarLanguageSwitchSettings.PREF_KEY_ENABLED, Boolean.FALSE);
        GboardSpacebarLanguageSwitchSettings.ensureDefault(preferences);
        Assert.assertEquals(Boolean.FALSE,
                preferences.values.get(GboardSpacebarLanguageSwitchSettings.PREF_KEY_ENABLED));
    }

    @Test
    public void readsAndWritesBothEnabledStates() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();

        Assert.assertTrue(GboardSpacebarLanguageSwitchSettings.writeEnabled(preferences, false));
        Assert.assertFalse(GboardSpacebarLanguageSwitchSettings.readEnabled(preferences));

        Assert.assertTrue(GboardSpacebarLanguageSwitchSettings.writeEnabled(preferences, true));
        Assert.assertTrue(GboardSpacebarLanguageSwitchSettings.readEnabled(preferences));
    }

    @Test
    public void malformedValuesFallBackToEnabled() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        preferences.values.put(
                GboardSpacebarLanguageSwitchSettings.PREF_KEY_ENABLED, Integer.valueOf(0));

        Assert.assertTrue(GboardSpacebarLanguageSwitchSettings.readEnabled(preferences));
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return Collections.unmodifiableMap(values);
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> pending = new HashMap<>();

                @Override
                public Editor putString(String key, String value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putStringSet(String key, Set<String> value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putInt(String key, int value) {
                    pending.put(key, Integer.valueOf(value));
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    pending.put(key, Long.valueOf(value));
                    return this;
                }

                @Override
                public Editor putFloat(String key, float value) {
                    pending.put(key, Float.valueOf(value));
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    pending.put(key, Boolean.valueOf(value));
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    pending.put(key, null);
                    return this;
                }

                @Override
                public Editor clear() {
                    values.clear();
                    pending.clear();
                    return this;
                }

                @Override
                public boolean commit() {
                    apply();
                    return true;
                }

                @Override
                public void apply() {
                    for (Map.Entry<String, Object> entry : pending.entrySet()) {
                        if (entry.getValue() == null) {
                            values.remove(entry.getKey());
                        } else {
                            values.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            };
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }
    }
}
