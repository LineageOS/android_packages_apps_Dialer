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

import android.Manifest.permission;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardDismissCallback;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.support.annotation.DrawableRes;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.MathUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.multimedia.MultimediaData;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.ViewUtil;
import com.android.incallui.answer.impl.CreateCustomSmsDialogFragment.CreateCustomSmsHolder;
import com.android.incallui.answer.impl.SmsBottomSheetFragment.SmsSheetHolder;
import com.android.incallui.answer.impl.affordance.SwipeButtonHelper.Callback;
import com.android.incallui.answer.impl.affordance.SwipeButtonView;
import com.android.incallui.answer.impl.answermethod.AnswerMethod;
import com.android.incallui.answer.impl.answermethod.AnswerMethodFactory;
import com.android.incallui.answer.impl.answermethod.AnswerMethodHolder;
import com.android.incallui.answer.impl.utils.Interpolators;
import com.android.incallui.answer.protocol.AnswerScreen;
import com.android.incallui.answer.protocol.AnswerScreenDelegate;
import com.android.incallui.answer.protocol.AnswerScreenDelegateFactory;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.contactgrid.ContactGridManager;
import com.android.incallui.incall.protocol.ContactPhotoType;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.incalluilock.InCallUiLock;
import com.android.incallui.maps.MapsComponent;
import com.android.incallui.sessiondata.AvatarPresenter;
import com.android.incallui.sessiondata.MultimediaFragment;
import com.android.incallui.speakeasy.Annotations.SpeakEasyChipResourceId;
import com.android.incallui.speakeasy.SpeakEasyComponent;
import com.android.incallui.util.AccessibilityUtil;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.videotech.utils.VideoUtils;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The new version of the incoming call screen. */
@SuppressLint("ClickableViewAccessibility")
public class AnswerFragment extends Fragment
    implements AnswerScreen,
        InCallScreen,
        SmsSheetHolder,
        CreateCustomSmsHolder,
        AnswerMethodHolder,
        MultimediaFragment.Holder {

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_CALL_ID = "call_id";

  static final String ARG_IS_RTT_CALL = "is_rtt_call";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_IS_VIDEO_CALL = "is_video_call";

  static final String ARG_ALLOW_ANSWER_AND_RELEASE = "allow_answer_and_release";

  static final String ARG_HAS_CALL_ON_HOLD = "has_call_on_hold";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_IS_VIDEO_UPGRADE_REQUEST = "is_video_upgrade_request";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_IS_SELF_MANAGED_CAMERA = "is_self_managed_camera";

  static final String ARG_ALLOW_SPEAK_EASY = "allow_speak_easy";

  private static final String STATE_HAS_ANIMATED_ENTRY = "hasAnimated";

  private static final int HINT_SECONDARY_SHOW_DURATION_MILLIS = 5000;
  private static final float ANIMATE_LERP_PROGRESS = 0.5f;
  private static final int STATUS_BAR_DISABLE_RECENT = 0x01000000;
  private static final int STATUS_BAR_DISABLE_HOME = 0x00200000;
  private static final int STATUS_BAR_DISABLE_BACK = 0x00400000;

  private static void fadeToward(View view, float newAlpha) {
    view.setAlpha(MathUtil.lerp(view.getAlpha(), newAlpha, ANIMATE_LERP_PROGRESS));
  }

  private static void scaleToward(View view, float newScale) {
    view.setScaleX(MathUtil.lerp(view.getScaleX(), newScale, ANIMATE_LERP_PROGRESS));
    view.setScaleY(MathUtil.lerp(view.getScaleY(), newScale, ANIMATE_LERP_PROGRESS));
  }

  private AnswerScreenDelegate answerScreenDelegate;
  private InCallScreenDelegate inCallScreenDelegate;

  private View importanceBadge;
  private SwipeButtonView secondaryButton;
  private SwipeButtonView answerAndReleaseButton;
  private AffordanceHolderLayout affordanceHolderLayout;
  private LinearLayout chipContainer;
  // Use these flags to prevent user from clicking accept/reject buttons multiple times.
  // We use separate flags because in some rare cases accepting a call may fail to join the room,
  // and then user is stuck in the incoming call view until it times out. Two flags at least give
  // the user a chance to get out of the CallActivity.
  private boolean buttonAcceptClicked;
  private boolean buttonRejectClicked;
  private boolean hasAnimatedEntry;
  private PrimaryInfo primaryInfo = PrimaryInfo.empty();
  private PrimaryCallState primaryCallState;
  private ArrayList<CharSequence> textResponses;
  private SmsBottomSheetFragment textResponsesFragment;
  private CreateCustomSmsDialogFragment createCustomSmsDialogFragment;
  private SecondaryBehavior secondaryBehavior = SecondaryBehavior.REJECT_WITH_SMS;
  private SecondaryBehavior answerAndReleaseBehavior;
  private ContactGridManager contactGridManager;
  private VideoCallScreen answerVideoCallScreen;
  private Handler handler = new Handler(Looper.getMainLooper());

  private enum SecondaryBehavior {
    REJECT_WITH_SMS(
        R.drawable.quantum_ic_message_white_24,
        R.string.a11y_description_incoming_call_reject_with_sms,
        R.string.a11y_incoming_call_reject_with_sms,
        R.string.call_incoming_swipe_to_decline_with_message) {
      @Override
      public void performAction(AnswerFragment fragment) {
        fragment.showMessageMenu();
      }
    },

    ANSWER_VIDEO_AS_AUDIO(
        R.drawable.quantum_ic_videocam_off_vd_theme_24,
        R.string.a11y_description_incoming_call_answer_video_as_audio,
        R.string.a11y_incoming_call_answer_video_as_audio,
        R.string.call_incoming_swipe_to_answer_video_as_audio) {
      @Override
      public void performAction(AnswerFragment fragment) {
        fragment.acceptCallByUser(true /* answerVideoAsAudio */);
      }
    },

    ANSWER_AND_RELEASE(
        R.drawable.ic_end_answer_32,
        R.string.a11y_description_incoming_call_answer_and_release,
        R.string.a11y_incoming_call_answer_and_release,
        R.string.call_incoming_swipe_to_answer_and_release) {
      @Override
      public void performAction(AnswerFragment fragment) {
        fragment.performAnswerAndRelease();
      }
    };

    @DrawableRes public int icon;
    @StringRes public final int contentDescription;
    @StringRes public final int accessibilityLabel;
    @StringRes public final int hintText;

    SecondaryBehavior(
        @DrawableRes int icon,
        @StringRes int contentDescription,
        @StringRes int accessibilityLabel,
        @StringRes int hintText) {
      this.icon = icon;
      this.contentDescription = contentDescription;
      this.accessibilityLabel = accessibilityLabel;
      this.hintText = hintText;
    }

    public abstract void performAction(AnswerFragment fragment);

    public void applyToView(ImageView view) {
      view.setImageResource(icon);
      view.setContentDescription(view.getContext().getText(contentDescription));
    }
  }

  private void performSpeakEasy(View unused) {
    answerScreenDelegate.onSpeakEasyCall();
    buttonAcceptClicked = true;
  }

  private void performAnswerAndRelease() {
    restoreAnswerAndReleaseButtonAnimation();
    answerScreenDelegate.onAnswerAndReleaseCall();
    buttonAcceptClicked = true;
  }

  private void restoreAnswerAndReleaseButtonAnimation() {
    answerAndReleaseButton
        .animate()
        .alpha(0)
        .withEndAction(
            new Runnable() {
              @Override
              public void run() {
                affordanceHolderLayout.reset(false);
                secondaryButton.animate().alpha(1);
              }
            });
  }

  private final AccessibilityDelegate accessibilityDelegate =
      new AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
          super.onInitializeAccessibilityNodeInfo(host, info);
          if (host == secondaryButton) {
            CharSequence label = getText(secondaryBehavior.accessibilityLabel);
            info.addAction(new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, label));
          } else if (host == answerAndReleaseButton) {
            CharSequence label = getText(answerAndReleaseBehavior.accessibilityLabel);
            info.addAction(new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, label));
          }
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
          if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            if (host == secondaryButton) {
              performSecondaryButtonAction();
              return true;
            } else if (host == answerAndReleaseButton) {
              performAnswerAndReleaseButtonAction();
              return true;
            }
          }
          return super.performAccessibilityAction(host, action, args);
        }
      };

  private final Callback affordanceCallback =
      new Callback() {
        @Override
        public void onAnimationToSideStarted(boolean rightPage, float translation, float vel) {}

        @Override
        public void onAnimationToSideEnded(boolean rightPage) {
          if (rightPage) {
            performAnswerAndReleaseButtonAction();
          } else {
            performSecondaryButtonAction();
          }
        }

        @Override
        public float getMaxTranslationDistance() {
          View view = getView();
          if (view == null) {
            return 0;
          }
          return (float) Math.hypot(view.getWidth(), view.getHeight());
        }

        @Override
        public void onSwipingStarted(boolean rightIcon) {}

        @Override
        public void onSwipingAborted() {}

        @Override
        public void onIconClicked(boolean rightIcon) {
          affordanceHolderLayout.startHintAnimation(rightIcon, null);
          getAnswerMethod()
              .setHintText(
                  rightIcon
                      ? getText(answerAndReleaseBehavior.hintText)
                      : getText(secondaryBehavior.hintText));
          handler.removeCallbacks(swipeHintRestoreTimer);
          handler.postDelayed(swipeHintRestoreTimer, HINT_SECONDARY_SHOW_DURATION_MILLIS);
        }

        @Override
        public SwipeButtonView getLeftIcon() {
          return secondaryButton;
        }

        @Override
        public SwipeButtonView getRightIcon() {
          return answerAndReleaseButton;
        }

        @Override
        public View getLeftPreview() {
          return null;
        }

        @Override
        public View getRightPreview() {
          return null;
        }

        @Override
        public float getAffordanceFalsingFactor() {
          return 1.0f;
        }
      };

  private Runnable swipeHintRestoreTimer = this::restoreSwipeHintTexts;

  private void performSecondaryButtonAction() {
    secondaryBehavior.performAction(this);
  }

  private void performAnswerAndReleaseButtonAction() {
    answerAndReleaseBehavior.performAction(this);
  }

  public static AnswerFragment newInstance(
      String callId,
      boolean isRttCall,
      boolean isVideoCall,
      boolean isVideoUpgradeRequest,
      boolean isSelfManagedCamera,
      boolean allowAnswerAndRelease,
      boolean hasCallOnHold,
      boolean allowSpeakEasy) {
    Bundle bundle = new Bundle();
    bundle.putString(ARG_CALL_ID, Assert.isNotNull(callId));
    bundle.putBoolean(ARG_IS_RTT_CALL, isRttCall);
    bundle.putBoolean(ARG_IS_VIDEO_CALL, isVideoCall);
    bundle.putBoolean(ARG_IS_VIDEO_UPGRADE_REQUEST, isVideoUpgradeRequest);
    bundle.putBoolean(ARG_IS_SELF_MANAGED_CAMERA, isSelfManagedCamera);
    bundle.putBoolean(ARG_ALLOW_ANSWER_AND_RELEASE, allowAnswerAndRelease);
    bundle.putBoolean(ARG_HAS_CALL_ON_HOLD, hasCallOnHold);
    bundle.putBoolean(ARG_ALLOW_SPEAK_EASY, allowSpeakEasy);

    AnswerFragment instance = new AnswerFragment();
    instance.setArguments(bundle);
    return instance;
  }

  @Override
  public boolean isActionTimeout() {
    return (buttonAcceptClicked || buttonRejectClicked) && answerScreenDelegate.isActionTimeout();
  }

  @Override
  @NonNull
  public String getCallId() {
    return Assert.isNotNull(getArguments().getString(ARG_CALL_ID));
  }

  @Override
  public boolean isVideoUpgradeRequest() {
    return getArguments().getBoolean(ARG_IS_VIDEO_UPGRADE_REQUEST);
  }

  @Override
  public void setTextResponses(List<String> textResponses) {
    if (isVideoCall() || isVideoUpgradeRequest()) {
      LogUtil.i("AnswerFragment.setTextResponses", "no-op for video calls");
    } else if (textResponses == null) {
      LogUtil.i("AnswerFragment.setTextResponses", "no text responses, hiding secondary button");
      this.textResponses = null;
      secondaryButton.setVisibility(View.INVISIBLE);
    } else if (getActivity().isInMultiWindowMode()) {
      LogUtil.i("AnswerFragment.setTextResponses", "in multiwindow, hiding secondary button");
      this.textResponses = null;
      secondaryButton.setVisibility(View.INVISIBLE);
    } else {
      LogUtil.i("AnswerFragment.setTextResponses", "textResponses.size: " + textResponses.size());
      this.textResponses = new ArrayList<>(textResponses);
      secondaryButton.setVisibility(View.VISIBLE);
    }
  }

  private void initSecondaryButton() {
    secondaryBehavior =
        isVideoCall() || isVideoUpgradeRequest()
            ? SecondaryBehavior.ANSWER_VIDEO_AS_AUDIO
            : SecondaryBehavior.REJECT_WITH_SMS;
    secondaryBehavior.applyToView(secondaryButton);

    secondaryButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            performSecondaryButtonAction();
          }
        });
    secondaryButton.setClickable(AccessibilityUtil.isAccessibilityEnabled(getContext()));
    secondaryButton.setFocusable(AccessibilityUtil.isAccessibilityEnabled(getContext()));
    secondaryButton.setAccessibilityDelegate(accessibilityDelegate);

    if (isVideoUpgradeRequest()) {
      secondaryButton.setVisibility(View.INVISIBLE);
    } else if (isVideoCall()) {
      secondaryButton.setVisibility(View.VISIBLE);
    }

    answerAndReleaseBehavior = SecondaryBehavior.ANSWER_AND_RELEASE;
    answerAndReleaseBehavior.applyToView(answerAndReleaseButton);

    answerAndReleaseButton.setClickable(AccessibilityUtil.isAccessibilityEnabled(getContext()));
    answerAndReleaseButton.setFocusable(AccessibilityUtil.isAccessibilityEnabled(getContext()));
    answerAndReleaseButton.setAccessibilityDelegate(accessibilityDelegate);

    if (allowAnswerAndRelease()) {
      answerAndReleaseButton.setVisibility(View.VISIBLE);
      answerScreenDelegate.onAnswerAndReleaseButtonEnabled();
    } else {
      answerAndReleaseButton.setVisibility(View.INVISIBLE);
      answerScreenDelegate.onAnswerAndReleaseButtonDisabled();
    }
    answerAndReleaseButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            performAnswerAndReleaseButtonAction();
          }
        });
  }

  /** Initialize chip buttons */
  private void initChips() {

    if (!allowSpeakEasy()) {
      chipContainer.setVisibility(View.GONE);
      return;
    }
    chipContainer.setVisibility(View.VISIBLE);

    @SpeakEasyChipResourceId
    Optional<Integer> chipLayoutOptional = SpeakEasyComponent.get(getContext()).speakEasyChip();
    if (chipLayoutOptional.isPresent()) {

      LinearLayout chipLayout =
          (LinearLayout) getLayoutInflater().inflate(chipLayoutOptional.get(), null);

      chipLayout.setOnClickListener(this::performSpeakEasy);

      chipContainer.addView(chipLayout);
    }
  }

  @Override
  public boolean allowAnswerAndRelease() {
    return getArguments().getBoolean(ARG_ALLOW_ANSWER_AND_RELEASE);
  }

  @Override
  public boolean allowSpeakEasy() {
    return getArguments().getBoolean(ARG_ALLOW_SPEAK_EASY);
  }

  private boolean hasCallOnHold() {
    return getArguments().getBoolean(ARG_HAS_CALL_ON_HOLD);
  }

  @Override
  public boolean hasPendingDialogs() {
    boolean hasPendingDialogs =
        textResponsesFragment != null || createCustomSmsDialogFragment != null;
    LogUtil.i("AnswerFragment.hasPendingDialogs", "" + hasPendingDialogs);
    return hasPendingDialogs;
  }

  @Override
  public void dismissPendingDialogs() {
    LogUtil.i("AnswerFragment.dismissPendingDialogs", null);
    if (textResponsesFragment != null) {
      textResponsesFragment.dismiss();
      textResponsesFragment = null;
    }

    if (createCustomSmsDialogFragment != null) {
      createCustomSmsDialogFragment.dismiss();
      createCustomSmsDialogFragment = null;
    }
  }

  @Override
  public boolean isShowingLocationUi() {
    Fragment fragment = getChildFragmentManager().findFragmentById(R.id.incall_location_holder);
    return fragment != null && fragment.isVisible();
  }

  @Override
  public void showLocationUi(@Nullable Fragment locationUi) {
    boolean isShowing = isShowingLocationUi();
    if (!isShowing && locationUi != null) {
      // Show the location fragment.
      getChildFragmentManager()
          .beginTransaction()
          .replace(R.id.incall_location_holder, locationUi)
          .commitAllowingStateLoss();
    } else if (isShowing && locationUi == null) {
      // Hide the location fragment
      Fragment fragment = getChildFragmentManager().findFragmentById(R.id.incall_location_holder);
      getChildFragmentManager().beginTransaction().remove(fragment).commitAllowingStateLoss();
    }
  }

  @Override
  public Fragment getAnswerScreenFragment() {
    return this;
  }

  private AnswerMethod getAnswerMethod() {
    return ((AnswerMethod)
        getChildFragmentManager().findFragmentById(R.id.answer_method_container));
  }

  @Override
  public void setPrimary(PrimaryInfo primaryInfo) {
    LogUtil.i("AnswerFragment.setPrimary", primaryInfo.toString());
    this.primaryInfo = primaryInfo;
    updatePrimaryUI();
    updateImportanceBadgeVisibility();
  }

  private void updatePrimaryUI() {
    if (getView() == null) {
      return;
    }
    contactGridManager.setPrimary(primaryInfo);
    getAnswerMethod().setShowIncomingWillDisconnect(primaryInfo.answeringDisconnectsOngoingCall());
    getAnswerMethod()
        .setContactPhoto(
            primaryInfo.photoType() == ContactPhotoType.CONTACT ? primaryInfo.photo() : null);
    updateDataFragment();

    if (primaryInfo.shouldShowLocation()) {
      // Hide the avatar to make room for location
      contactGridManager.setAvatarHidden(true);
    }
  }

  private void updateDataFragment() {
    if (!isAdded()) {
      return;
    }
    LogUtil.enterBlock("AnswerFragment.updateDataFragment");
    Fragment current = getChildFragmentManager().findFragmentById(R.id.incall_data_container);
    Fragment newFragment = null;

    MultimediaData multimediaData = getSessionData();
    if (multimediaData != null
        && (!TextUtils.isEmpty(multimediaData.getText())
            || (multimediaData.getImageUri() != null)
            || (multimediaData.getLocation() != null && canShowMap()))) {
      // Need message fragment
      String subject = multimediaData.getText();
      Uri imageUri = multimediaData.getImageUri();
      Location location = multimediaData.getLocation();
      if (!(current instanceof MultimediaFragment)
          || !Objects.equals(((MultimediaFragment) current).getSubject(), subject)
          || !Objects.equals(((MultimediaFragment) current).getImageUri(), imageUri)
          || !Objects.equals(((MultimediaFragment) current).getLocation(), location)) {
        LogUtil.i("AnswerFragment.updateDataFragment", "Replacing multimedia fragment");
        // Needs replacement
        newFragment =
            MultimediaFragment.newInstance(
                multimediaData,
                false /* isInteractive */,
                !primaryInfo.isSpam() /* showAvatar */,
                primaryInfo.isSpam());
      }
    } else if (shouldShowAvatar()) {
      // Needs Avatar
      if (!(current instanceof AvatarFragment)) {
        LogUtil.i("AnswerFragment.updateDataFragment", "Replacing avatar fragment");
        // Needs replacement
        newFragment = new AvatarFragment();
      }
    } else {
      // Needs empty
      if (current != null) {
        LogUtil.i("AnswerFragment.updateDataFragment", "Removing current fragment");
        getChildFragmentManager().beginTransaction().remove(current).commitNow();
      }
      contactGridManager.setAvatarImageView(null, 0, false);
    }

    if (newFragment != null) {
      getChildFragmentManager()
          .beginTransaction()
          .replace(R.id.incall_data_container, newFragment)
          .commitNow();
    }
  }

  private boolean shouldShowAvatar() {
    return !isVideoCall() && !isVideoUpgradeRequest();
  }

  private boolean canShowMap() {
    return MapsComponent.get(getContext()).getMaps().isAvailable();
  }

  @Override
  public void updateAvatar(AvatarPresenter avatarContainer) {
    contactGridManager.setAvatarImageView(
        avatarContainer.getAvatarImageView(),
        avatarContainer.getAvatarSize(),
        avatarContainer.shouldShowAnonymousAvatar());
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {}

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("AnswerFragment.setCallState", primaryCallState.toString());
    this.primaryCallState = primaryCallState;
    contactGridManager.setCallState(primaryCallState);
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {}

  @Override
  public void showManageConferenceCallButton(boolean visible) {}

  @Override
  public boolean isManageConferenceVisible() {
    return false;
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
    contactGridManager.dispatchPopulateAccessibilityEvent(event);
    // Add prompt of how to accept/decline call with swipe gesture.
    if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
      event
          .getText()
          .add(getResources().getString(R.string.a11y_incoming_call_swipe_gesture_prompt));
    }
  }

  @Override
  public void showNoteSentToast() {}

  @Override
  public void updateInCallScreenColors() {}

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {}

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Trace.beginSection("AnswerFragment.onCreateView");
    Bundle arguments = getArguments();
    Assert.checkState(arguments.containsKey(ARG_CALL_ID));
    Assert.checkState(arguments.containsKey(ARG_IS_RTT_CALL));
    Assert.checkState(arguments.containsKey(ARG_IS_VIDEO_CALL));
    Assert.checkState(arguments.containsKey(ARG_IS_VIDEO_UPGRADE_REQUEST));

    buttonAcceptClicked = false;
    buttonRejectClicked = false;

    View view = inflater.inflate(R.layout.fragment_incoming_call, container, false);
    secondaryButton = (SwipeButtonView) view.findViewById(R.id.incoming_secondary_button);
    answerAndReleaseButton = (SwipeButtonView) view.findViewById(R.id.incoming_secondary_button2);

    affordanceHolderLayout = (AffordanceHolderLayout) view.findViewById(R.id.incoming_container);
    affordanceHolderLayout.setAffordanceCallback(affordanceCallback);

    chipContainer = view.findViewById(R.id.incall_data_container_chip_container);

    importanceBadge = view.findViewById(R.id.incall_important_call_badge);
    importanceBadge
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                int leftRightPadding = importanceBadge.getHeight() / 2;
                importanceBadge.setPadding(
                    leftRightPadding,
                    importanceBadge.getPaddingTop(),
                    leftRightPadding,
                    importanceBadge.getPaddingBottom());
              }
            });
    updateImportanceBadgeVisibility();

    contactGridManager = new ContactGridManager(view, null, 0, false /* showAnonymousAvatar */);
    boolean isInMultiWindowMode = getActivity().isInMultiWindowMode();
    contactGridManager.onMultiWindowModeChanged(isInMultiWindowMode);

    Fragment answerMethod =
        getChildFragmentManager().findFragmentById(R.id.answer_method_container);
    if (AnswerMethodFactory.needsReplacement(answerMethod)) {
      getChildFragmentManager()
          .beginTransaction()
          .replace(
              R.id.answer_method_container, AnswerMethodFactory.createAnswerMethod(getActivity()))
          .commitNow();
    }

    answerScreenDelegate =
        FragmentUtils.getParentUnsafe(this, AnswerScreenDelegateFactory.class)
            .newAnswerScreenDelegate(this);

    initSecondaryButton();
    initChips();

    int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    if (!isInMultiWindowMode
        && (getActivity().checkSelfPermission(permission.STATUS_BAR)
            == PackageManager.PERMISSION_GRANTED)) {
      LogUtil.i("AnswerFragment.onCreateView", "STATUS_BAR permission granted, disabling nav bar");
      // These flags will suppress the alert that the activity is in full view mode
      // during an incoming call on a fresh system/factory reset of the app
      flags |= STATUS_BAR_DISABLE_BACK | STATUS_BAR_DISABLE_HOME | STATUS_BAR_DISABLE_RECENT;
    }
    view.setSystemUiVisibility(flags);
    if (isVideoCall() || isVideoUpgradeRequest()) {
      if (VideoUtils.hasCameraPermissionAndShownPrivacyToast(getContext())) {
        if (isSelfManagedCamera()) {
          answerVideoCallScreen = new SelfManagedAnswerVideoCallScreen(getCallId(), this, view);
        } else {
          answerVideoCallScreen = new AnswerVideoCallScreen(getCallId(), this, view);
        }
      } else {
        view.findViewById(R.id.videocall_video_off).setVisibility(View.VISIBLE);
      }
    }

    Trace.endSection();
    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    FragmentUtils.checkParent(this, InCallScreenDelegateFactory.class);
  }

  @Override
  public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
    Trace.beginSection("AnswerFragment.onViewCreated");
    super.onViewCreated(view, savedInstanceState);
    createInCallScreenDelegate();
    updateUI();

    if (savedInstanceState == null || !savedInstanceState.getBoolean(STATE_HAS_ANIMATED_ENTRY)) {
      ViewUtil.doOnGlobalLayout(view, this::animateEntry);
    }
    Trace.endSection();
  }

  @Override
  public void onResume() {
    Trace.beginSection("AnswerFragment.onResume");
    super.onResume();
    LogUtil.i("AnswerFragment.onResume", null);
    restoreSwipeHintTexts();
    inCallScreenDelegate.onInCallScreenResumed();
    Trace.endSection();
  }

  @Override
  public void onStart() {
    Trace.beginSection("AnswerFragment.onStart");
    super.onStart();
    LogUtil.i("AnswerFragment.onStart", null);

    updateUI();
    if (answerVideoCallScreen != null) {
      answerVideoCallScreen.onVideoScreenStart();
    }
    Trace.endSection();
  }

  @Override
  public void onStop() {
    Trace.beginSection("AnswerFragment.onStop");
    super.onStop();
    LogUtil.i("AnswerFragment.onStop", null);

    handler.removeCallbacks(swipeHintRestoreTimer);
    if (answerVideoCallScreen != null) {
      answerVideoCallScreen.onVideoScreenStop();
    }
    Trace.endSection();
  }

  @Override
  public void onPause() {
    Trace.beginSection("AnswerFragment.onPause");
    super.onPause();
    LogUtil.i("AnswerFragment.onPause", null);
    inCallScreenDelegate.onInCallScreenPaused();
    Trace.endSection();
  }

  @Override
  public void onDestroyView() {
    LogUtil.i("AnswerFragment.onDestroyView", null);
    if (answerVideoCallScreen != null) {
      answerVideoCallScreen = null;
    }
    super.onDestroyView();
    inCallScreenDelegate.onInCallScreenUnready();
    answerScreenDelegate.onAnswerScreenUnready();
  }

  @Override
  public void onSaveInstanceState(Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putBoolean(STATE_HAS_ANIMATED_ENTRY, hasAnimatedEntry);
  }

  private void updateUI() {
    if (getView() == null) {
      return;
    }

    if (primaryInfo != null) {
      updatePrimaryUI();
    }
    if (primaryCallState != null) {
      contactGridManager.setCallState(primaryCallState);
    }

    restoreBackgroundMaskColor();
  }

  @Override
  public boolean isRttCall() {
    return getArguments().getBoolean(ARG_IS_RTT_CALL);
  }

  @Override
  public boolean isVideoCall() {
    return getArguments().getBoolean(ARG_IS_VIDEO_CALL);
  }

  public boolean isSelfManagedCamera() {
    return getArguments().getBoolean(ARG_IS_SELF_MANAGED_CAMERA);
  }

  @Override
  public void onAnswerProgressUpdate(@FloatRange(from = -1f, to = 1f) float answerProgress) {
    // Don't fade the window background for call waiting or video upgrades. Fading the background
    // shows the system wallpaper which looks bad because on reject we switch to another call.
    if (primaryCallState.state() == DialerCallState.INCOMING && !isVideoCall()) {
      answerScreenDelegate.updateWindowBackgroundColor(answerProgress);
    }

    // Fade and scale contact name and video call text
    float startDelay = .25f;
    // Header progress is zero over positiveAdjustedProgress = [0, startDelay],
    // linearly increases over (startDelay, 1] until reaching 1 when positiveAdjustedProgress = 1
    float headerProgress = Math.max(0, (Math.abs(answerProgress) - 1) / (1 - startDelay) + 1);
    fadeToward(contactGridManager.getContainerView(), 1 - headerProgress);
    scaleToward(contactGridManager.getContainerView(), MathUtil.lerp(1f, .75f, headerProgress));

    if (Math.abs(answerProgress) >= .0001) {
      affordanceHolderLayout.animateHideLeftRightIcon();
      handler.removeCallbacks(swipeHintRestoreTimer);
      restoreSwipeHintTexts();
    }
  }

  @Override
  public void answerFromMethod() {
    acceptCallByUser(false /* answerVideoAsAudio */);
  }

  @Override
  public void rejectFromMethod() {
    rejectCall();
  }

  @Override
  public void resetAnswerProgress() {
    affordanceHolderLayout.reset(true);
    restoreBackgroundMaskColor();
  }

  private void animateEntry(@NonNull View rootView) {
    if (!isAdded()) {
      LogUtil.i(
          "AnswerFragment.animateEntry",
          "Not currently added to Activity. Will not start entry animation.");
      return;
    }
    contactGridManager.getContainerView().setAlpha(0f);
    Animator alpha =
        ObjectAnimator.ofFloat(contactGridManager.getContainerView(), View.ALPHA, 0, 1);
    Animator topRow = createTranslation(rootView.findViewById(R.id.contactgrid_top_row));
    Animator contactName = createTranslation(rootView.findViewById(R.id.contactgrid_contact_name));
    Animator bottomRow = createTranslation(rootView.findViewById(R.id.contactgrid_bottom_row));
    Animator important = createTranslation(importanceBadge);
    Animator dataContainer = createTranslation(rootView.findViewById(R.id.incall_data_container));

    AnimatorSet animatorSet = new AnimatorSet();
    AnimatorSet.Builder builder = animatorSet.play(alpha);
    builder.with(topRow).with(contactName).with(bottomRow).with(important).with(dataContainer);
    if (isShowingLocationUi()) {
      builder.with(createTranslation(rootView.findViewById(R.id.incall_location_holder)));
    }
    animatorSet.setDuration(
        rootView.getResources().getInteger(R.integer.answer_animate_entry_millis));
    animatorSet.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            hasAnimatedEntry = true;
          }
        });
    animatorSet.start();
  }

  private ObjectAnimator createTranslation(View view) {
    float translationY = view.getTop() * 0.5f;
    ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, translationY, 0);
    animator.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
    return animator;
  }

  private void acceptCallByUser(boolean answerVideoAsAudio) {
    LogUtil.i("AnswerFragment.acceptCallByUser", answerVideoAsAudio ? " answerVideoAsAudio" : "");
    if (!buttonAcceptClicked) {
      answerScreenDelegate.onAnswer(answerVideoAsAudio);
      buttonAcceptClicked = true;
    }
  }

  private void rejectCall() {
    LogUtil.i("AnswerFragment.rejectCall", null);
    if (!buttonRejectClicked) {
      Context context = getContext();
      if (context == null) {
        LogUtil.w(
            "AnswerFragment.rejectCall",
            "Null context when rejecting call. Logger call was skipped");
      } else {
        Logger.get(context)
            .logImpression(DialerImpression.Type.REJECT_INCOMING_CALL_FROM_ANSWER_SCREEN);
      }
      buttonRejectClicked = true;
      answerScreenDelegate.onReject();
    }
  }

  private void restoreBackgroundMaskColor() {
    answerScreenDelegate.updateWindowBackgroundColor(0);
  }

  private void restoreSwipeHintTexts() {
    if (getAnswerMethod() != null) {
      if (allowAnswerAndRelease()) {
        if (hasCallOnHold()) {
          getAnswerMethod()
              .setHintText(getText(R.string.call_incoming_default_label_answer_and_release_third));
        } else if (primaryCallState.supportsCallOnHold()) {
          getAnswerMethod()
              .setHintText(getText(R.string.call_incoming_default_label_answer_and_release_second));
        }
      } else {
        getAnswerMethod().setHintText(null);
      }
    }
  }

  private void showMessageMenu() {
    LogUtil.i("AnswerFragment.showMessageMenu", "Show sms menu.");
    if (getContext() == null || isDetached() || getChildFragmentManager().isDestroyed()) {
      return;
    }

    textResponsesFragment = SmsBottomSheetFragment.newInstance(textResponses);
    textResponsesFragment.show(getChildFragmentManager(), null);
    secondaryButton
        .animate()
        .alpha(0)
        .withEndAction(
            new Runnable() {
              @Override
              public void run() {
                affordanceHolderLayout.reset(false);
                secondaryButton.animate().alpha(1);
              }
            });

    TelecomUtil.silenceRinger(getContext());
  }

  @Override
  public InCallUiLock acquireInCallUiLock(String tag) {
    return answerScreenDelegate.acquireInCallUiLock(tag);
  }

  @Override
  @TargetApi(VERSION_CODES.O)
  public void smsSelected(@Nullable CharSequence text) {
    LogUtil.i("AnswerFragment.smsSelected", null);
    textResponsesFragment = null;

    if (text == null) {
      if (VERSION.SDK_INT < VERSION_CODES.O) {
        LogUtil.i("AnswerFragment.smsSelected", "below O, showing dialog directly");
        showCustomSmsDialog();
        return;
      }
      if (!getContext().getSystemService(KeyguardManager.class).isKeyguardLocked()) {
        LogUtil.i("AnswerFragment.smsSelected", "not locked, showing dialog directly");
        showCustomSmsDialog();
        return;
      }

      // Show the custom reply dialog only after device is unlocked, as it may cause impersonation
      // see b/137134588
      LogUtil.i("AnswerFragment.smsSelected", "dismissing keyguard");
      getContext()
          .getSystemService(KeyguardManager.class)
          .requestDismissKeyguard(
              getActivity(),
              new KeyguardDismissCallback() {
                @Override
                public void onDismissCancelled() {
                  LogUtil.i("AnswerFragment.smsSelected", "onDismissCancelled");
                }

                @Override
                public void onDismissError() {
                  LogUtil.i("AnswerFragment.smsSelected", "onDismissError");
                }

                @Override
                public void onDismissSucceeded() {
                  LogUtil.i("AnswerFragment.smsSelected", "onDismissSucceeded");
                  showCustomSmsDialog();
                }
              });return;
    }

    if (primaryCallState != null && canRejectCallWithSms()) {
      rejectCall();
      answerScreenDelegate.onRejectCallWithMessage(text.toString());
    }
  }

  private void showCustomSmsDialog() {
    createCustomSmsDialogFragment = CreateCustomSmsDialogFragment.newInstance();
    createCustomSmsDialogFragment.showNow(getChildFragmentManager(), null);
  }

  @Override
  public void smsDismissed() {
    LogUtil.i("AnswerFragment.smsDismissed", null);
    textResponsesFragment = null;
  }

  @Override
  public void customSmsCreated(@NonNull CharSequence text) {
    LogUtil.i("AnswerFragment.customSmsCreated", null);
    createCustomSmsDialogFragment = null;
    if (primaryCallState != null && canRejectCallWithSms()) {
      rejectCall();
      answerScreenDelegate.onRejectCallWithMessage(text.toString());
    }
  }

  @Override
  public void customSmsDismissed() {
    LogUtil.i("AnswerFragment.customSmsDismissed", null);
    createCustomSmsDialogFragment = null;
  }

  private boolean canRejectCallWithSms() {
    return primaryCallState != null
        && !(primaryCallState.state() == DialerCallState.DISCONNECTED
            || primaryCallState.state() == DialerCallState.DISCONNECTING
            || primaryCallState.state() == DialerCallState.IDLE);
  }

  private void createInCallScreenDelegate() {
    inCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, InCallScreenDelegateFactory.class)
            .newInCallScreenDelegate();
    Assert.isNotNull(inCallScreenDelegate);
    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
  }

  private void updateImportanceBadgeVisibility() {
    if (!isAdded() || getView() == null) {
      return;
    }

    if (!getResources().getBoolean(R.bool.answer_important_call_allowed) || primaryInfo.isSpam()) {
      importanceBadge.setVisibility(View.GONE);
      return;
    }

    MultimediaData multimediaData = getSessionData();
    boolean showImportant = multimediaData != null && multimediaData.isImportant();
    TransitionManager.beginDelayedTransition((ViewGroup) importanceBadge.getParent());
    // TODO (keyboardr): Change this back to being View.INVISIBLE once mocks are available to
    // properly handle smaller screens
    importanceBadge.setVisibility(showImportant ? View.VISIBLE : View.GONE);
  }

  @Nullable
  private MultimediaData getSessionData() {
    if (primaryInfo == null) {
      return null;
    }
    if (isVideoUpgradeRequest()) {
      return null;
    }
    return primaryInfo.multimediaData();
  }

  /** Shows the Avatar image if available. */
  public static class AvatarFragment extends Fragment implements AvatarPresenter {

    private ImageView avatarImageView;

    @Nullable
    @Override
    public View onCreateView(
        LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
      return layoutInflater.inflate(R.layout.fragment_avatar, viewGroup, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle bundle) {
      super.onViewCreated(view, bundle);
      avatarImageView = ((ImageView) view.findViewById(R.id.contactgrid_avatar));
      FragmentUtils.getParentUnsafe(this, MultimediaFragment.Holder.class).updateAvatar(this);
    }

    @NonNull
    @Override
    public ImageView getAvatarImageView() {
      return avatarImageView;
    }

    @Override
    public int getAvatarSize() {
      return getResources().getDimensionPixelSize(R.dimen.answer_avatar_size);
    }

    @Override
    public boolean shouldShowAnonymousAvatar() {
      return false;
    }
  }
}
