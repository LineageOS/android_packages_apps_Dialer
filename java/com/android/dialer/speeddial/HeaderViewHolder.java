/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.speeddial;

import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/** ViewHolder for headers in {@link SpeedDialFragment}. */
public class HeaderViewHolder extends RecyclerView.ViewHolder implements OnClickListener {

  private final SpeedDialHeaderListener listener;
  private final TextView headerText;
  private final Button addButton;

  public HeaderViewHolder(View view, SpeedDialHeaderListener listener) {
    super(view);
    this.listener = listener;
    headerText = view.findViewById(R.id.speed_dial_header_text);
    addButton = view.findViewById(R.id.speed_dial_add_button);
    addButton.setOnClickListener(this);
  }

  public void setHeaderText(@StringRes int header) {
    headerText.setText(header);
  }

  public void showAddButton(boolean show) {
    addButton.setVisibility(show ? View.VISIBLE : View.GONE);
  }

  @Override
  public void onClick(View v) {
    listener.onAddFavoriteClicked();
  }

  /** Listener/Callback for {@link HeaderViewHolder} parents. */
  public interface SpeedDialHeaderListener {

    /** Called when the user wants to add a contact to their favorites. */
    void onAddFavoriteClicked();
  }
}
