package com.deniscerri.ytdl.ui.more.terminal.virtualkeys;

import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A {@link Class} that defines the info needed by {@link VirtualKeysView} to display the extra key
 * views.
 *
 * <p>The {@code propertiesInfo} passed to the constructors of this class must be json array of
 * arrays. Each array element of the json array will be considered a separate row of keys. Each key
 * can either be simple string that defines the name of the key or a json dict that defines advance
 * info for the key. The syntax can be `'KEY'` or `{key: 'KEY'}`. For example `HOME` or `{key:
 * 'HOME', ...}.
 *
 * <p>In advance json dict mode, the key can also be a sequence of space separated keys instead of
 * one key. This can be done by replacing `key` key/value pair of the dict with a `macro` key/value
 * pair. The syntax is `{macro: 'KEY COMBINATION'}`. For example {macro: 'HOME RIGHT', ...}.
 *
 * <p>In advance json dict mode, you can define a nested json dict with the `popup` key which will
 * be used as the popup key and will be triggered on swipe up. The syntax can be `{key: 'KEY',
 * popup: 'POPUP_KEY'}` or `{key: 'KEY', popup: {macro: 'KEY COMBINATION', display: 'Key combo'}}`.
 * For example `{key: 'HOME', popup: {KEY: 'END', ...}, ...}`.
 *
 * <p>In advance json dict mode, the key can also have a custom display name that can be used as the
 * text to display on the button by defining the `display` key. The syntax is `{display:
 * 'DISPLAY'}`. For example {display: 'Custom name', ...}.
 *
 * <p>Examples: {@code # Empty: []
 *
 * <p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p>#
 * Single row: [[ESC, TAB, CTRL, ALT, {key: '-', popup: '|'}, DOWN, UP]]
 *
 * <p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p>#
 * 2 row: [['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'],
 * ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]
 *
 * <p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p>#
 * Advance: [[ {key: ESC, popup: {macro: "CTRL f d", display: "tmux exit"}}, {key: CTRL, popup:
 * {macro: "CTRL f BKSP", display: "tmux ←"}}, {key: ALT, popup: {macro: "CTRL f TAB", display:
 * "tmux →"}}, {key: TAB, popup: {macro: "ALT a", display: A-a}}, {key: LEFT, popup: HOME}, {key:
 * DOWN, popup: PGDN}, {key: UP, popup: PGUP}, {key: RIGHT, popup: END}, {macro: "ALT j", display:
 * A-j, popup: {macro: "ALT g", display: A-g}}, {key: KEYBOARD, popup: {macro: "CTRL d", display:
 * exit}} ]]
 *
 * <p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p><p>}
 *
 * <p>Aliases are also allowed for the keys that you can pass as {@code extraKeyAliasMap}. Check
 * {@link VirtualKeysConstants#CONTROL_CHARS_ALIASES}.
 *
 * <p>Its up to the {@link VirtualKeysView.IVirtualKeysView} client on how to handle individual key
 * values of an {@link VirtualKeyButton}. They are sent as is via {@link
 * VirtualKeysView.IVirtualKeysView#onVirtualKeyButtonClick(View, VirtualKeyButton, Button)}. The
 * {TerminalVirtualKeys} which is an implementation of the
 * interface, checks if the key is one of {@link VirtualKeysConstants#PRIMARY_KEY_CODES_FOR_STRINGS}
 * and generates a {@link android.view.KeyEvent} for it, and if its not, then converts the key to
 * code points by calling {@link CharSequence#codePoints()} and passes them to the terminal as
 * literal strings.
 *
 * <p>Examples: {@code "ENTER" will trigger the ENTER keycode "LEFT" will trigger the LEFT keycode
 * and be displayed as "←" "→" will input a "→" character "−" will input a "−" character "-_-" will
 * input the string "-_-" }
 *
 * <p>For more info, check https://wiki.termux.com/wiki/Touch_Keyboard.
 */
public class VirtualKeysInfo {

    /** Matrix of buttons to be displayed in {@link VirtualKeysView}. */
    private final VirtualKeyButton[][] mButtons;

    /**
     * Initialize {@link VirtualKeysInfo}.
     *
     * @param propertiesInfo The {@link String} containing the info to create the {@link
     *     VirtualKeysInfo}. Check the class javadoc for details.
     * @param style The style to pass to {@link #getCharDisplayMapForStyle(String)} to get the {@link
     *     VirtualKeysConstants.VirtualKeyDisplayMap} that defines the display text mapping for the
     *     keys if a custom value is not defined by {@link VirtualKeyButton#KEY_DISPLAY_NAME} for a
     *     key.
     * @param extraKeyAliasMap The {@link VirtualKeysConstants.VirtualKeyDisplayMap} that defines the
     *     aliases for the actual key names. You can create your own or optionally pass {@link
     *     VirtualKeysConstants#CONTROL_CHARS_ALIASES}.
     */
    public VirtualKeysInfo(
            @NonNull String propertiesInfo,
            String style,
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyAliasMap)
            throws JSONException {
        mButtons =
                initVirtualKeysInfo(propertiesInfo, getCharDisplayMapForStyle(style), extraKeyAliasMap);
    }

    private VirtualKeyButton[][] initVirtualKeysInfo(
            @NonNull String propertiesInfo,
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyDisplayMap,
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyAliasMap)
            throws JSONException {
        // Convert String propertiesInfo to Array of Arrays
        JSONArray arr = new JSONArray(propertiesInfo);
        Object[][] matrix = new Object[arr.length()][];
        for (int i = 0; i < arr.length(); i++) {
            JSONArray line = arr.getJSONArray(i);
            matrix[i] = new Object[line.length()];
            for (int j = 0; j < line.length(); j++) {
                matrix[i][j] = line.get(j);
            }
        }

        // convert matrix to buttons
        VirtualKeyButton[][] buttons = new VirtualKeyButton[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            buttons[i] = new VirtualKeyButton[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                Object key = matrix[i][j];

                JSONObject jobject = normalizeKeyConfig(key);

                VirtualKeyButton button;

                if (!jobject.has(VirtualKeyButton.KEY_POPUP)) {
                    // no popup
                    button = new VirtualKeyButton(jobject, extraKeyDisplayMap, extraKeyAliasMap);
                } else {
                    // a popup
                    JSONObject popupJobject = normalizeKeyConfig(jobject.get(VirtualKeyButton.KEY_POPUP));
                    VirtualKeyButton popup =
                            new VirtualKeyButton(popupJobject, extraKeyDisplayMap, extraKeyAliasMap);
                    button = new VirtualKeyButton(jobject, popup, extraKeyDisplayMap, extraKeyAliasMap);
                }

                buttons[i][j] = button;
            }
        }

        return buttons;
    }

    /**
     * Convert "value" -> {"key": "value"}. Required by {@link
     * VirtualKeyButton#VirtualKeyButton(JSONObject, VirtualKeyButton,
     * VirtualKeysConstants.VirtualKeyDisplayMap, VirtualKeysConstants.VirtualKeyDisplayMap)}.
     */
    private static JSONObject normalizeKeyConfig(Object key) throws JSONException {
        JSONObject jobject;
        if (key instanceof String) {
            jobject = new JSONObject();
            jobject.put(VirtualKeyButton.KEY_KEY_NAME, key);
        } else if (key instanceof JSONObject) {
            jobject = (JSONObject) key;
        } else {
            throw new JSONException("An key in the extra-key matrix must be a string or an object");
        }
        return jobject;
    }

    @NonNull
    public static VirtualKeysConstants.VirtualKeyDisplayMap getCharDisplayMapForStyle(String style) {
        switch (style) {
            case "arrows-only":
                return VirtualKeysConstants.EXTRA_KEY_DISPLAY_MAPS.ARROWS_ONLY_CHAR_DISPLAY;
            case "arrows-all":
                return VirtualKeysConstants.EXTRA_KEY_DISPLAY_MAPS.LOTS_OF_ARROWS_CHAR_DISPLAY;
            case "all":
                return VirtualKeysConstants.EXTRA_KEY_DISPLAY_MAPS.FULL_ISO_CHAR_DISPLAY;
            case "none":
                return new VirtualKeysConstants.VirtualKeyDisplayMap();
            default:
                return VirtualKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY;
        }
    }

    /**
     * Initialize {@link VirtualKeysInfo}.
     *
     * @param propertiesInfo The {@link String} containing the info to create the {@link
     *     VirtualKeysInfo}. Check the class javadoc for details.
     * @param extraKeyDisplayMap The {@link VirtualKeysConstants.VirtualKeyDisplayMap} that defines
     *     the display text mapping for the keys if a custom value is not defined by {@link
     *     VirtualKeyButton#KEY_DISPLAY_NAME} for a key. You can create your own or optionally pass
     *     one of the values defined in {@link #getCharDisplayMapForStyle(String)}.
     * @param extraKeyAliasMap The {@link VirtualKeysConstants.VirtualKeyDisplayMap} that defines the
     *     aliases for the actual key names. You can create your own or optionally pass {@link
     *     VirtualKeysConstants#CONTROL_CHARS_ALIASES}.
     */
    public VirtualKeysInfo(
            @NonNull String propertiesInfo,
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyDisplayMap,
            @NonNull VirtualKeysConstants.VirtualKeyDisplayMap extraKeyAliasMap)
            throws JSONException {
        mButtons = initVirtualKeysInfo(propertiesInfo, extraKeyDisplayMap, extraKeyAliasMap);
    }

    public VirtualKeyButton[][] getMatrix() {
        return mButtons;
    }
}