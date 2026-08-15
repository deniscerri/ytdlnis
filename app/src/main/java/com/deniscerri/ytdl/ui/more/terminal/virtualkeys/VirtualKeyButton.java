package com.deniscerri.ytdl.ui.more.terminal.virtualkeys;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.stream.Collectors;

public class VirtualKeyButton {

    /**
     * The key name for the name of the extra key if using a dict to define the extra key. {key: name,
     * ...}
     */
    public static final String KEY_KEY_NAME = "key";

    /**
     * The key name for the macro value of the extra key if using a dict to define the extra key.
     * {macro: value, ...}
     */
    public static final String KEY_MACRO = "macro";

    /**
     * The key name for the alternate display name of the extra key if using a dict to define the
     * extra key. {display: name, ...}
     */
    public static final String KEY_DISPLAY_NAME = "display";

    /**
     * The key name for the nested dict to define popup extra key info if using a dict to define the
     * extra key. {popup: {key: name, ...}, ...}
     */
    public static final String KEY_POPUP = "popup";

    /**
     * The key that will be sent to the terminal, either a control character, like defined in {@link
     * VirtualKeysConstants#PRIMARY_KEY_CODES_FOR_STRINGS} (LEFT, RIGHT, PGUP...) or some text.
     */
    private final String key;

    /** If the key is a macro, i.e. a sequence of keys separated by space. */
    private final boolean macro;

    /** The text that will be displayed on the button. */
    private final String display;

    /**
     * The {@link VirtualKeyButton} containing the information of the popup button (triggered by swipe
     * up).
     */
    @Nullable private final VirtualKeyButton popup;

    /**
     * Initialize a {@link VirtualKeyButton}.
     *
     * @param config The {@link JSONObject} containing the info to create the {@link
     *     VirtualKeyButton}.
     * @param extraKeyDisplayMap The {@link VirtualKeysConstants.VirtualKeyDisplayMap} that defines
     *     the display text mapping for the keys if a custom value is not defined by {@link
     *     #KEY_DISPLAY_NAME}.
     * @param extraKeyAliasMap The {@link VirtualKeysConstants.VirtualKeyDisplayMap} that defines the
     *     aliases for the actual key names.
     */
    public VirtualKeyButton(
            @NonNull JSONObject config,
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyDisplayMap,
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyAliasMap)
            throws JSONException {
        this(config, null, extraKeyDisplayMap, extraKeyAliasMap);
    }

    /**
     * Initialize a {@link VirtualKeyButton}.
     *
     * @param config The {@link JSONObject} containing the info to create the {@link
     *     VirtualKeyButton}.
     * @param popup The {@link VirtualKeyButton} optional {@link #popup} button.
     * @param extraKeyDisplayMap The {@link VirtualKeysConstants.VirtualKeyDisplayMap} that defines
     *     the display text mapping for the keys if a custom value is not defined by {@link
     *     #KEY_DISPLAY_NAME}.
     * @param extraKeyAliasMap The {@link VirtualKeysConstants.VirtualKeyDisplayMap} that defines the
     *     aliases for the actual key names.
     */
    public VirtualKeyButton(
            @NonNull JSONObject config,
            @Nullable VirtualKeyButton popup,
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyDisplayMap,
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyAliasMap)
            throws JSONException {
        String keyFromConfig = getStringFromJson(config, KEY_KEY_NAME);
        String macroFromConfig = getStringFromJson(config, KEY_MACRO);
        String[] keys;
        if (keyFromConfig != null && macroFromConfig != null) {
            throw new JSONException(
                    "Both key and macro can't be set for the same key. key: \""
                            + keyFromConfig
                            + "\", macro: \""
                            + macroFromConfig
                            + "\"");
        } else if (keyFromConfig != null) {
            keys = new String[] {keyFromConfig};
            this.macro = false;
        } else if (macroFromConfig != null) {
            keys = macroFromConfig.split(" ");
            this.macro = true;
        } else {
            throw new JSONException("All keys have to specify either key or macro");
        }

        for (int i = 0; i < keys.length; i++) {
            keys[i] = replaceAlias(extraKeyAliasMap, keys[i]);
        }

        this.key = TextUtils.join(" ", keys);

        String displayFromConfig = getStringFromJson(config, KEY_DISPLAY_NAME);
        if (displayFromConfig != null) {
            this.display = displayFromConfig;
        } else {
            this.display =
                    Arrays.stream(keys)
                            .map(key -> extraKeyDisplayMap.get(key, key))
                            .collect(Collectors.joining(" "));
        }

        this.popup = popup;
    }

    public String getStringFromJson(@NonNull JSONObject config, @NonNull String key) {
        try {
            return config.getString(key);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Replace the alias with its actual key name if found in extraKeyAliasMap. */
    public static String replaceAlias(
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyAliasMap, String key) {
        return extraKeyAliasMap.get(key, key);
    }

    /** Get {@link #key}. */
    public String getKey() {
        return key;
    }

    /** Check whether a {@link #macro} is defined or not. */
    public boolean isMacro() {
        return macro;
    }

    /** Get {@link #display}. */
    public String getDisplay() {
        return display;
    }

    /** Get {@link #popup}. */
    @Nullable
    public VirtualKeyButton getPopup() {
        return popup;
    }
}