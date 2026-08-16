package com.deniscerri.ytdl.ui.more.terminal.virtualkeys;

import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

/** The {@link Class} that maintains a state of a {@link SpecialButton} */
public class SpecialButtonState {

    /** If special button has been created for the {@link VirtualKeysView}. */
    boolean isCreated = false;
    /** If special button is active. */
    boolean isActive = false;
    /**
     * If special button is locked due to long hold on it and should not be deactivated if its state
     * is read.
     */
    boolean isLocked = false;

    List<Button> buttons = new ArrayList<>();

    VirtualKeysView mVirtualKeysView;

    /**
     * Initialize a {@link SpecialButtonState} to maintain state of a {@link SpecialButton}.
     *
     * @param extraKeysView The {@link VirtualKeysView} instance in which the {@link SpecialButton} is
     *     to be registered.
     */
    public SpecialButtonState(VirtualKeysView extraKeysView) {
        mVirtualKeysView = extraKeysView;
    }

    /** Set {@link #isCreated}. */
    public void setIsCreated(boolean value) {
        isCreated = value;
    }

    /** Set {@link #isActive}. */
    public void setIsActive(boolean value) {
        isActive = value;
        for (Button button : buttons) {
            button.setTextColor(
                    value
                            ? mVirtualKeysView.getButtonActiveTextColor()
                            : mVirtualKeysView.getButtonTextColor());
        }
    }

    /** Set {@link #isLocked}. */
    public void setIsLocked(boolean value) {
        isLocked = value;
    }
}