/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.app.list;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Space;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.app.R;
import com.android.dialer.app.widget.DialpadSearchEmptyContentView;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.LogUtil;
import com.android.dialer.dialpadview.DialpadFragment.ErrorDialogFragment;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.widget.EmptyContentView;

public class SearchFragment extends PhoneNumberPickerFragment {

  protected EmptyContentView emptyView;
  private OnListFragmentScrolledListener activityScrollListener;
  private View.OnTouchListener activityOnTouchListener;
  /*
   * Stores the untouched user-entered string that is used to populate the add to contacts
   * intent.
   */
  private String addToContactNumber;
  private int actionBarHeight;
  private int shadowHeight;
  private int paddingTop;
  private int showDialpadDuration;
  private int hideDialpadDuration;
  /**
   * Used to resize the list view containing search results so that it fits the available space
   * above the dialpad. Does not have a user-visible effect in regular touch usage (since the
   * dialpad hides that portion of the ListView anyway), but improves usability in accessibility
   * mode.
   */
  private Space spacer;

  private HostInterface activity;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    setQuickContactEnabled(true);
    setAdjustSelectionBoundsEnabled(false);
    setDarkTheme(false);
    setUseCallableUri(true);

    try {
      activityScrollListener = (OnListFragmentScrolledListener) activity;
    } catch (ClassCastException e) {
      LogUtil.v(
          "SearchFragment.onAttach",
          activity.toString()
              + " doesn't implement OnListFragmentScrolledListener. "
              + "Ignoring.");
    }
  }

  @Override
  public void onStart() {
    LogUtil.d("SearchFragment.onStart", "");
    super.onStart();

    activity = (HostInterface) getActivity();

    final Resources res = getResources();
    actionBarHeight = activity.getActionBarHeight();
    shadowHeight = res.getDrawable(R.drawable.search_shadow).getIntrinsicHeight();
    paddingTop = res.getDimensionPixelSize(R.dimen.search_list_padding_top);
    showDialpadDuration = res.getInteger(R.integer.dialpad_slide_in_duration);
    hideDialpadDuration = res.getInteger(R.integer.dialpad_slide_out_duration);

    final ListView listView = getListView();

    if (emptyView == null) {
      if (this instanceof SmartDialSearchFragment) {
        emptyView = new DialpadSearchEmptyContentView(getActivity());
      } else {
        emptyView = new EmptyContentView(getActivity());
      }
      ((ViewGroup) getListView().getParent()).addView(emptyView);
      getListView().setEmptyView(emptyView);
      setupEmptyView();
    }

    listView.setBackgroundColor(res.getColor(R.color.background_dialer_results));
    listView.setClipToPadding(false);
    setVisibleScrollbarEnabled(false);

    //Turn of accessibility live region as the list constantly update itself and spam messages.
    listView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
    ContentChangedFilter.addToParent(listView);

    listView.setOnScrollListener(
        new OnScrollListener() {
          @Override
          public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (activityScrollListener != null) {
              activityScrollListener.onListFragmentScrollStateChange(scrollState);
            }
          }

          @Override
          public void onScroll(
              AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
        });
    if (activityOnTouchListener != null) {
      listView.setOnTouchListener(activityOnTouchListener);
    }

    updatePosition(false /* animate */);
  }

  @Override
  public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
    Animator animator = null;
    if (nextAnim != 0) {
      animator = AnimatorInflater.loadAnimator(getActivity(), nextAnim);
    }
    if (animator != null) {
      final View view = getView();
      final int oldLayerType = view.getLayerType();
      animator.addListener(
          new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
              view.setLayerType(oldLayerType, null);
            }
          });
    }
    return animator;
  }

  public void setAddToContactNumber(String addToContactNumber) {
    this.addToContactNumber = addToContactNumber;
  }

  /**
   * Return true if phone number is prohibited by a value -
   * (R.string.config_prohibited_phone_number_regexp) in the config files. False otherwise.
   */
  public boolean checkForProhibitedPhoneNumber(String number) {
    // Regular expression prohibiting manual phone call. Can be empty i.e. "no rule".
    String prohibitedPhoneNumberRegexp =
        getResources().getString(R.string.config_prohibited_phone_number_regexp);

    // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
    // test equipment.
    if (number != null
        && !TextUtils.isEmpty(prohibitedPhoneNumberRegexp)
        && number.matches(prohibitedPhoneNumberRegexp)) {
      LogUtil.i(
          "SearchFragment.checkForProhibitedPhoneNumber",
          "the phone number is prohibited explicitly by a rule");
      if (getActivity() != null) {
        DialogFragment dialogFragment =
            ErrorDialogFragment.newInstance(R.string.dialog_phone_call_prohibited_message);
        dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
      }

      return true;
    }
    return false;
  }

  @Override
  protected ContactEntryListAdapter createListAdapter() {
    DialerPhoneNumberListAdapter adapter = new DialerPhoneNumberListAdapter(getActivity());
    adapter.setDisplayPhotos(true);
    adapter.setUseCallableUri(super.usesCallableUri());
    adapter.setListener(this);
    return adapter;
  }

  @Override
  protected void onItemClick(int position, long id) {
    final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
    final int shortcutType = adapter.getShortcutTypeFromPosition(position);
    final OnPhoneNumberPickerActionListener listener;
    final Intent intent;
    final String number;

    LogUtil.i("SearchFragment.onItemClick", "shortcutType: " + shortcutType);

    switch (shortcutType) {
      case DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL:
        number = adapter.getQueryString();
        listener = getOnPhoneNumberPickerListener();
        if (listener != null && !checkForProhibitedPhoneNumber(number)) {
          CallSpecificAppData callSpecificAppData =
              CallSpecificAppData.newBuilder()
                  .setCallInitiationType(getCallInitiationType(false /* isRemoteDirectory */))
                  .setPositionOfSelectedSearchResult(position)
                  .setCharactersInSearchString(
                      getQueryString() == null ? 0 : getQueryString().length())
                  .build();
          listener.onPickPhoneNumber(number, false /* isVideoCall */, callSpecificAppData);
        }
        break;
      case DialerPhoneNumberListAdapter.SHORTCUT_CREATE_NEW_CONTACT:
        if (this instanceof SmartDialSearchFragment) {
          Logger.get(getContext())
              .logImpression(DialerImpression.Type.CREATE_NEW_CONTACT_FROM_DIALPAD);
        }
        number =
            TextUtils.isEmpty(addToContactNumber)
                ? adapter.getFormattedQueryString()
                : addToContactNumber;
        intent = IntentUtil.getNewContactIntent(number);
        DialerUtils.startActivityWithErrorToast(getActivity(), intent);
        break;
      case DialerPhoneNumberListAdapter.SHORTCUT_ADD_TO_EXISTING_CONTACT:
        if (this instanceof SmartDialSearchFragment) {
          Logger.get(getContext())
              .logImpression(DialerImpression.Type.ADD_TO_A_CONTACT_FROM_DIALPAD);
        }
        number =
            TextUtils.isEmpty(addToContactNumber)
                ? adapter.getFormattedQueryString()
                : addToContactNumber;
        intent = IntentUtil.getAddToExistingContactIntent(number);
        DialerUtils.startActivityWithErrorToast(
            getActivity(), intent, R.string.add_contact_not_available);
        break;
      case DialerPhoneNumberListAdapter.SHORTCUT_SEND_SMS_MESSAGE:
        number =
            TextUtils.isEmpty(addToContactNumber)
                ? adapter.getFormattedQueryString()
                : addToContactNumber;
        intent = IntentUtil.getSendSmsIntent(number);
        DialerUtils.startActivityWithErrorToast(getActivity(), intent);
        break;
      case DialerPhoneNumberListAdapter.SHORTCUT_MAKE_VIDEO_CALL:
        number =
            TextUtils.isEmpty(addToContactNumber) ? adapter.getQueryString() : addToContactNumber;
        listener = getOnPhoneNumberPickerListener();
        if (listener != null && !checkForProhibitedPhoneNumber(number)) {
          CallSpecificAppData callSpecificAppData =
              CallSpecificAppData.newBuilder()
                  .setCallInitiationType(getCallInitiationType(false /* isRemoteDirectory */))
                  .setPositionOfSelectedSearchResult(position)
                  .setCharactersInSearchString(
                      getQueryString() == null ? 0 : getQueryString().length())
                  .build();
          listener.onPickPhoneNumber(number, true /* isVideoCall */, callSpecificAppData);
        }
        break;
      case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
      default:
        super.onItemClick(position, id);
        break;
    }
  }

  /**
   * Updates the position and padding of the search fragment, depending on whether the dialpad is
   * shown. This can be optionally animated.
   */
  public void updatePosition(boolean animate) {
    LogUtil.d("SearchFragment.updatePosition", "animate: %b", animate);
    if (activity == null) {
      // Activity will be set in onStart, and this method will be called again
      return;
    }

    // Use negative shadow height instead of 0 to account for the 9-patch's shadow.
    int startTranslationValue =
        activity.isDialpadShown() ? actionBarHeight - shadowHeight : -shadowHeight;
    int endTranslationValue = 0;
    // Prevents ListView from being translated down after a rotation when the ActionBar is up.
    if (animate || activity.isActionBarShowing()) {
      endTranslationValue = activity.isDialpadShown() ? 0 : actionBarHeight - shadowHeight;
    }
    if (animate) {
      // If the dialpad will be shown, then this animation involves sliding the list up.
      final boolean slideUp = activity.isDialpadShown();

      Interpolator interpolator = slideUp ? AnimUtils.EASE_IN : AnimUtils.EASE_OUT;
      int duration = slideUp ? showDialpadDuration : hideDialpadDuration;
      getView().setTranslationY(startTranslationValue);
      getView()
          .animate()
          .translationY(endTranslationValue)
          .setInterpolator(interpolator)
          .setDuration(duration)
          .setListener(
              new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                  if (!slideUp) {
                    resizeListView();
                  }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                  if (slideUp) {
                    resizeListView();
                  }
                }
              });

    } else {
      getView().setTranslationY(endTranslationValue);
      resizeListView();
    }

    // There is padding which should only be applied when the dialpad is not shown.
    int paddingTop = activity.isDialpadShown() ? 0 : this.paddingTop;
    final ListView listView = getListView();
    listView.setPaddingRelative(
        listView.getPaddingStart(),
        paddingTop,
        listView.getPaddingEnd(),
        listView.getPaddingBottom());
  }

  public void resizeListView() {
    if (spacer == null) {
      return;
    }
    int spacerHeight = activity.isDialpadShown() ? activity.getDialpadHeight() : 0;
    LogUtil.d(
        "SearchFragment.resizeListView",
        "spacerHeight: %d -> %d, isDialpadShown: %b, dialpad height: %d",
        spacer.getHeight(),
        spacerHeight,
        activity.isDialpadShown(),
        activity.getDialpadHeight());
    if (spacerHeight != spacer.getHeight()) {
      final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) spacer.getLayoutParams();
      lp.height = spacerHeight;
      spacer.setLayoutParams(lp);
    }
  }

  @Override
  protected void startLoading() {
    if (getActivity() == null) {
      return;
    }

    if (PermissionsUtil.hasContactsReadPermissions(getActivity())) {
      super.startLoading();
    } else if (TextUtils.isEmpty(getQueryString())) {
      // Clear out any existing call shortcuts.
      final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
      adapter.disableAllShortcuts();
    } else {
      // The contact list is not going to change (we have no results since permissions are
      // denied), but the shortcuts might because of the different query, so update the
      // list.
      getAdapter().notifyDataSetChanged();
    }

    setupEmptyView();
  }

  public void setOnTouchListener(View.OnTouchListener onTouchListener) {
    activityOnTouchListener = onTouchListener;
  }

  @Override
  protected View inflateView(LayoutInflater inflater, ViewGroup container) {
    final LinearLayout parent = (LinearLayout) super.inflateView(inflater, container);
    final int orientation = getResources().getConfiguration().orientation;
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      spacer = new Space(getActivity());
      parent.addView(
          spacer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
    }
    return parent;
  }

  protected void setupEmptyView() {}

  public interface HostInterface {

    boolean isActionBarShowing();

    boolean isDialpadShown();

    int getDialpadHeight();

    int getActionBarHeight();
  }
}
