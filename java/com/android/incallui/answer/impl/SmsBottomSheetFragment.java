/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.incallui.answer.impl;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.common.DpUtil;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.incallui.incalluilock.InCallUiLock;
import java.util.ArrayList;
import java.util.List;

/** Shows options for rejecting call with SMS */
public class SmsBottomSheetFragment extends BottomSheetDialogFragment {

  private static final String ARG_OPTIONS = "options";

  private InCallUiLock inCallUiLock;

  public static SmsBottomSheetFragment newInstance(@Nullable ArrayList<CharSequence> options) {
    SmsBottomSheetFragment fragment = new SmsBottomSheetFragment();
    Bundle args = new Bundle();
    args.putCharSequenceArrayList(ARG_OPTIONS, options);
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    LinearLayout layout = new LinearLayout(getContext());
    layout.setOrientation(LinearLayout.VERTICAL);
    List<CharSequence> items = getArguments().getCharSequenceArrayList(ARG_OPTIONS);
    if (items != null) {
      for (CharSequence item : items) {
        layout.addView(newTextViewItem(item));
      }
    }
    layout.addView(newTextViewItem(null));
    layout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    return layout;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    FragmentUtils.checkParent(this, SmsSheetHolder.class);
  }

  @Override
  public Dialog onCreateDialog(final Bundle savedInstanceState) {
    LogUtil.i("SmsBottomSheetFragment.onCreateDialog", null);
    Dialog dialog = super.onCreateDialog(savedInstanceState);
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

    inCallUiLock =
        FragmentUtils.getParentUnsafe(SmsBottomSheetFragment.this, SmsSheetHolder.class)
            .acquireInCallUiLock("SmsBottomSheetFragment");
    return dialog;
  }

  private TextView newTextViewItem(@Nullable final CharSequence text) {
    int[] attrs = new int[] {android.R.attr.selectableItemBackground};
    Context context = new ContextThemeWrapper(getContext(), getTheme());
    TypedArray typedArray = context.obtainStyledAttributes(attrs);
    Drawable background = typedArray.getDrawable(0);
    // noinspection ResourceType
    typedArray.recycle();

    TextView textView = new TextView(context);
    textView.setText(text == null ? getString(R.string.call_incoming_message_custom) : text);
    int padding = (int) DpUtil.dpToPx(context, 16);
    textView.setPadding(padding, padding, padding, padding);
    textView.setBackground(background);
    textView.setTextColor(context.getColor(R.color.blue_grey_100));
    textView.setTextAppearance(R.style.TextAppearance_AppCompat_Widget_PopupMenu_Large);

    LayoutParams params =
        new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    textView.setLayoutParams(params);

    textView.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            FragmentUtils.getParentUnsafe(SmsBottomSheetFragment.this, SmsSheetHolder.class)
                .smsSelected(text);
            dismiss();
          }
        });
    return textView;
  }

  @Override
  public int getTheme() {
    return R.style.Theme_Design_Light_BottomSheetDialog;
  }

  @Override
  public void onDismiss(DialogInterface dialogInterface) {
    super.onDismiss(dialogInterface);
    FragmentUtils.getParentUnsafe(this, SmsSheetHolder.class).smsDismissed();
    inCallUiLock.release();
  }

  /** Callback interface for {@link SmsBottomSheetFragment} */
  public interface SmsSheetHolder {

    InCallUiLock acquireInCallUiLock(String tag);

    void smsSelected(@Nullable CharSequence text);

    void smsDismissed();
  }
}
