package com.android.dialer.service;

import android.view.View;

import java.util.List;

/**
 * Interface responsible for rendering spam buttons.
 */
public interface SpamButtonRenderer {

    /**
     * Renders buttons for a phone number.
     */
    void render(String number, String countryIso);

    void setCompleteListItemViews(List<View> views);

    void setSpamFilteredViews(List<View> views);

    void setFilteredNumberViews(List<View> views);

}
