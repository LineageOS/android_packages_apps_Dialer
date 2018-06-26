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
 * limitations under the License.
 */

package com.android.dialer.callcomposer;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.android.dialer.callcomposer.CallComposerFragment.CallComposerListener;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.UiUtil;
import com.android.dialer.common.concurrent.DialerExecutor;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.constants.Constants;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.enrichedcall.Session;
import com.android.dialer.enrichedcall.Session.State;
import com.android.dialer.enrichedcall.extensions.StateExtension;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.multimedia.MultimediaData;
import com.android.dialer.precall.PreCall;
import com.android.dialer.protos.ProtoParsers;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.UriUtils;
import com.android.dialer.util.ViewUtil;
import com.android.dialer.widget.BidiTextView;
import com.android.dialer.widget.DialerToolbar;
import com.android.dialer.widget.LockableViewPager;
import com.android.incallui.callpending.CallPendingActivity;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.File;

/**
 * Implements an activity which prompts for a call with additional media for an outgoing call. The
 * activity includes a pop up with:
 *
 * <ul>
 *   <li>Contact galleryIcon
 *   <li>Name
 *   <li>Number
 *   <li>Media options to attach a gallery image, camera image or a message
 * </ul>
 */
public class CallComposerActivity extends AppCompatActivity
    implements OnClickListener,
        OnPageChangeListener,
        CallComposerListener,
        EnrichedCallManager.StateChangedListener {

  public static final String KEY_CONTACT_NAME = "contact_name";
  private static final String KEY_IS_FIRST_CALL_COMPOSE = "is_first_call_compose";

  private static final int ENTRANCE_ANIMATION_DURATION_MILLIS = 500;
  private static final int EXIT_ANIMATION_DURATION_MILLIS = 500;

  private static final String ARG_CALL_COMPOSER_CONTACT = "CALL_COMPOSER_CONTACT";
  private static final String ARG_CALL_COMPOSER_CONTACT_BASE64 = "CALL_COMPOSER_CONTACT_BASE64";

  private static final String ENTRANCE_ANIMATION_KEY = "entrance_animation_key";
  private static final String SEND_AND_CALL_READY_KEY = "send_and_call_ready_key";
  private static final String CURRENT_INDEX_KEY = "current_index_key";
  private static final String VIEW_PAGER_STATE_KEY = "view_pager_state_key";
  private static final String SESSION_ID_KEY = "session_id_key";

  private final Handler timeoutHandler = ThreadUtil.getUiThreadHandler();
  private final Runnable sessionStartedTimedOut =
      () -> {
        LogUtil.i("CallComposerActivity.sessionStartedTimedOutRunnable", "session never started");
        setFailedResultAndFinish();
      };
  private final Runnable placeTelecomCallRunnable =
      () -> {
        LogUtil.i("CallComposerActivity.placeTelecomCallRunnable", "upload timed out.");
        placeTelecomCall();
      };
  // Counter for the number of message sent updates received from EnrichedCallManager
  private int messageSentCounter;
  private boolean pendingCallStarted;

  private DialerContact contact;
  private Long sessionId = Session.NO_SESSION_ID;

  private TextView nameView;
  private BidiTextView numberView;
  private QuickContactBadge contactPhoto;
  private RelativeLayout contactContainer;
  private DialerToolbar toolbar;
  private View sendAndCall;
  private TextView sendAndCallText;

  private ProgressBar loading;
  private ImageView cameraIcon;
  private ImageView galleryIcon;
  private ImageView messageIcon;
  private LockableViewPager pager;
  private CallComposerPagerAdapter adapter;

  private FrameLayout background;
  private LinearLayout windowContainer;

  private DialerExecutor<Uri> copyAndResizeExecutor;
  private FastOutSlowInInterpolator interpolator;
  private boolean shouldAnimateEntrance = true;
  private boolean inFullscreenMode;
  private boolean isSendAndCallHidingOrHidden = true;
  private boolean sendAndCallReady;
  private boolean runningExitAnimation;
  private int currentIndex;

  public static Intent newIntent(Context context, DialerContact contact) {
    Intent intent = new Intent(context, CallComposerActivity.class);
    ProtoParsers.put(intent, ARG_CALL_COMPOSER_CONTACT, contact);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.call_composer_activity);

    nameView = findViewById(R.id.contact_name);
    numberView = findViewById(R.id.phone_number);
    contactPhoto = findViewById(R.id.contact_photo);
    cameraIcon = findViewById(R.id.call_composer_camera);
    galleryIcon = findViewById(R.id.call_composer_photo);
    messageIcon = findViewById(R.id.call_composer_message);
    contactContainer = findViewById(R.id.contact_bar);
    pager = findViewById(R.id.call_composer_view_pager);
    background = findViewById(R.id.background);
    windowContainer = findViewById(R.id.call_composer_container);
    toolbar = findViewById(R.id.toolbar);
    sendAndCall = findViewById(R.id.send_and_call_button);
    sendAndCallText = findViewById(R.id.send_and_call_text);
    loading = findViewById(R.id.call_composer_loading);

    interpolator = new FastOutSlowInInterpolator();
    adapter =
        new CallComposerPagerAdapter(
            getSupportFragmentManager(),
            getResources().getInteger(R.integer.call_composer_message_limit));
    pager.setAdapter(adapter);
    pager.addOnPageChangeListener(this);

    cameraIcon.setOnClickListener(this);
    galleryIcon.setOnClickListener(this);
    messageIcon.setOnClickListener(this);
    sendAndCall.setOnClickListener(this);

    onHandleIntent(getIntent());

    if (savedInstanceState != null) {
      shouldAnimateEntrance = savedInstanceState.getBoolean(ENTRANCE_ANIMATION_KEY);
      sendAndCallReady = savedInstanceState.getBoolean(SEND_AND_CALL_READY_KEY);
      pager.onRestoreInstanceState(savedInstanceState.getParcelable(VIEW_PAGER_STATE_KEY));
      currentIndex = savedInstanceState.getInt(CURRENT_INDEX_KEY);
      sessionId = savedInstanceState.getLong(SESSION_ID_KEY, Session.NO_SESSION_ID);
      onPageSelected(currentIndex);
    }

    // Since we can't animate the views until they are ready to be drawn, we use this listener to
    // track that and animate the call compose UI as soon as it's ready.
    ViewUtil.doOnPreDraw(
        windowContainer,
        false,
        () -> {
          showFullscreen(inFullscreenMode);
          runEntranceAnimation();
        });

    setMediaIconSelected(currentIndex);

    copyAndResizeExecutor =
        DialerExecutorComponent.get(getApplicationContext())
            .dialerExecutorFactory()
            .createUiTaskBuilder(
                getFragmentManager(),
                "copyAndResizeImageToSend",
                new CopyAndResizeImageWorker(this.getApplicationContext()))
            .onSuccess(this::onCopyAndResizeImageSuccess)
            .onFailure(this::onCopyAndResizeImageFailure)
            .build();
  }

  private void onCopyAndResizeImageSuccess(Pair<File, String> output) {
    Uri shareableUri =
        FileProvider.getUriForFile(
            CallComposerActivity.this, Constants.get().getFileProviderAuthority(), output.first);

    placeRCSCall(
        MultimediaData.builder().setImage(grantUriPermission(shareableUri), output.second));
  }

  private void onCopyAndResizeImageFailure(Throwable throwable) {
    // TODO(a bug) - gracefully handle message failure
    LogUtil.e("CallComposerActivity.onCopyAndResizeImageFailure", "copy Failed", throwable);
  }

  @Override
  protected void onResume() {
    super.onResume();
    getEnrichedCallManager().registerStateChangedListener(this);
    if (pendingCallStarted) {
      // User went into incall ui and pressed disconnect before the image was done uploading.
      // Kill the activity and cancel the telecom call.
      timeoutHandler.removeCallbacks(placeTelecomCallRunnable);
      setResult(RESULT_OK);
      finish();
    } else if (sessionId == Session.NO_SESSION_ID) {
      LogUtil.i("CallComposerActivity.onResume", "creating new session");
      sessionId = getEnrichedCallManager().startCallComposerSession(contact.getNumber());
    } else if (getEnrichedCallManager().getSession(sessionId) == null) {
      LogUtil.i(
          "CallComposerActivity.onResume", "session closed while activity paused, creating new");
      sessionId = getEnrichedCallManager().startCallComposerSession(contact.getNumber());
    } else {
      LogUtil.i("CallComposerActivity.onResume", "session still open, using old");
    }
    if (sessionId == Session.NO_SESSION_ID) {
      LogUtil.w("CallComposerActivity.onResume", "failed to create call composer session");
      setFailedResultAndFinish();
    }
    refreshUiForCallComposerState();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    getEnrichedCallManager().unregisterStateChangedListener(this);
    timeoutHandler.removeCallbacksAndMessages(null);
  }

  /**
   * This listener is registered in onResume and removed in onDestroy, meaning that calls to this
   * method can come after onStop and updates to UI could cause crashes.
   */
  @Override
  public void onEnrichedCallStateChanged() {
    refreshUiForCallComposerState();
  }

  private void refreshUiForCallComposerState() {
    Session session = getEnrichedCallManager().getSession(sessionId);
    if (session == null) {
      return;
    }

    @State int state = session.getState();
    LogUtil.i(
        "CallComposerActivity.refreshUiForCallComposerState",
        "state: %s",
        StateExtension.toString(state));

    switch (state) {
      case Session.STATE_STARTING:
        timeoutHandler.postDelayed(sessionStartedTimedOut, getSessionStartedTimeoutMillis());
        if (sendAndCallReady) {
          showLoadingUi();
        }
        break;
      case Session.STATE_STARTED:
        timeoutHandler.removeCallbacks(sessionStartedTimedOut);
        if (sendAndCallReady) {
          sendAndCall();
        }
        break;
      case Session.STATE_START_FAILED:
      case Session.STATE_CLOSED:
        if (pendingCallStarted) {
          placeTelecomCall();
        } else {
          setFailedResultAndFinish();
        }
        break;
      case Session.STATE_MESSAGE_SENT:
        if (++messageSentCounter == 3) {
          // When we compose EC with images, there are 3 steps:
          //  1. Message sent with no data
          //  2. Image uploaded
          //  3. url sent
          // Once we receive 3 message sent updates, we know that we can proceed with the call.
          timeoutHandler.removeCallbacks(placeTelecomCallRunnable);
          placeTelecomCall();
        }
        break;
      case Session.STATE_MESSAGE_FAILED:
      case Session.STATE_NONE:
      default:
        break;
    }
  }

  @VisibleForTesting
  public long getSessionStartedTimeoutMillis() {
    return ConfigProviderComponent.get(this)
        .getConfigProvider()
        .getLong("ec_session_started_timeout", 10_000);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    onHandleIntent(intent);
  }

  @Override
  public void onClick(View view) {
    LogUtil.enterBlock("CallComposerActivity.onClick");
    if (view == cameraIcon) {
      pager.setCurrentItem(CallComposerPagerAdapter.INDEX_CAMERA, true /* animate */);
    } else if (view == galleryIcon) {
      pager.setCurrentItem(CallComposerPagerAdapter.INDEX_GALLERY, true /* animate */);
    } else if (view == messageIcon) {
      pager.setCurrentItem(CallComposerPagerAdapter.INDEX_MESSAGE, true /* animate */);
    } else if (view == sendAndCall) {
      sendAndCall();
    } else {
      throw Assert.createIllegalStateFailException("View on click not implemented: " + view);
    }
  }

  @Override
  public void sendAndCall() {
    if (!sessionReady()) {
      sendAndCallReady = true;
      showLoadingUi();
      LogUtil.i("CallComposerActivity.onClick", "sendAndCall pressed, but the session isn't ready");
      Logger.get(this)
          .logImpression(
              DialerImpression.Type
                  .CALL_COMPOSER_ACTIVITY_SEND_AND_CALL_PRESSED_WHEN_SESSION_NOT_READY);
      return;
    }
    sendAndCall.setEnabled(false);
    CallComposerFragment fragment =
        (CallComposerFragment) adapter.instantiateItem(pager, currentIndex);
    MultimediaData.Builder builder = MultimediaData.builder();

    if (fragment instanceof MessageComposerFragment) {
      MessageComposerFragment messageComposerFragment = (MessageComposerFragment) fragment;
      builder.setText(messageComposerFragment.getMessage());
      placeRCSCall(builder);
    }
    if (fragment instanceof GalleryComposerFragment) {
      GalleryComposerFragment galleryComposerFragment = (GalleryComposerFragment) fragment;
      // If the current data is not a copy, make one.
      if (!galleryComposerFragment.selectedDataIsCopy()) {
        copyAndResizeExecutor.executeParallel(
            galleryComposerFragment.getGalleryData().getFileUri());
      } else {
        Uri shareableUri =
            FileProvider.getUriForFile(
                this,
                Constants.get().getFileProviderAuthority(),
                new File(galleryComposerFragment.getGalleryData().getFilePath()));

        builder.setImage(
            grantUriPermission(shareableUri),
            galleryComposerFragment.getGalleryData().getMimeType());

        placeRCSCall(builder);
      }
    }
    if (fragment instanceof CameraComposerFragment) {
      CameraComposerFragment cameraComposerFragment = (CameraComposerFragment) fragment;
      cameraComposerFragment.getCameraUriWhenReady(
          uri -> {
            builder.setImage(grantUriPermission(uri), cameraComposerFragment.getMimeType());
            placeRCSCall(builder);
          });
    }
  }

  private void showLoadingUi() {
    loading.setVisibility(View.VISIBLE);
    pager.setSwipingLocked(true);
  }

  private boolean sessionReady() {
    Session session = getEnrichedCallManager().getSession(sessionId);
    return session != null && session.getState() == Session.STATE_STARTED;
  }

  @VisibleForTesting
  public void placeRCSCall(MultimediaData.Builder builder) {
    MultimediaData data = builder.build();
    LogUtil.i("CallComposerActivity.placeRCSCall", "placing enriched call, data: " + data);
    Logger.get(this).logImpression(DialerImpression.Type.CALL_COMPOSER_ACTIVITY_PLACE_RCS_CALL);

    getEnrichedCallManager().sendCallComposerData(sessionId, data);
    maybeShowPrivacyToast(data);
    if (data.hasImageData()
        && ConfigProviderComponent.get(this)
            .getConfigProvider()
            .getBoolean("enable_delayed_ec_images", true)
        && !TelecomUtil.isInManagedCall(this)) {
      timeoutHandler.postDelayed(placeTelecomCallRunnable, getRCSTimeoutMillis());
      startActivity(
          CallPendingActivity.getIntent(
              this,
              contact.getNameOrNumber(),
              contact.getDisplayNumber(),
              contact.getNumberLabel(),
              UriUtils.getLookupKeyFromUri(Uri.parse(contact.getContactUri())),
              getString(R.string.call_composer_image_uploading),
              Uri.parse(contact.getPhotoUri()),
              sessionId));
      pendingCallStarted = true;
    } else {
      placeTelecomCall();
    }
  }

  private void maybeShowPrivacyToast(MultimediaData data) {
    SharedPreferences preferences = StorageComponent.get(this).unencryptedSharedPrefs();
    // Show a toast for privacy purposes if this is the first time a user uses call composer.
    if (preferences.getBoolean(KEY_IS_FIRST_CALL_COMPOSE, true)) {
      int privacyMessage =
          data.hasImageData() ? R.string.image_sent_messages : R.string.message_sent_messages;
      Toast toast = Toast.makeText(this, privacyMessage, Toast.LENGTH_LONG);
      int yOffset = getResources().getDimensionPixelOffset(R.dimen.privacy_toast_y_offset);
      toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, yOffset);
      toast.show();
      preferences.edit().putBoolean(KEY_IS_FIRST_CALL_COMPOSE, false).apply();
    }
  }

  @VisibleForTesting
  public long getRCSTimeoutMillis() {
    return ConfigProviderComponent.get(this)
        .getConfigProvider()
        .getLong("ec_image_upload_timeout", 15_000);
  }

  private void placeTelecomCall() {
    PreCall.start(
        this,
        new CallIntentBuilder(contact.getNumber(), CallInitiationType.Type.CALL_COMPOSER)
            // Call composer is only active if the number is associated with a known contact.
            .setAllowAssistedDial(true));
    setResult(RESULT_OK);
    finish();
  }

  /** Give permission to Messenger to view our image for RCS purposes. */
  private Uri grantUriPermission(Uri uri) {
    // TODO(sail): Move this to the enriched call manager.
    grantUriPermission(
        "com.google.android.apps.messaging", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    return uri;
  }

  /** Animates {@code contactContainer} to align with content inside viewpager. */
  @Override
  public void onPageSelected(int position) {
    if (position == CallComposerPagerAdapter.INDEX_MESSAGE) {
      sendAndCallText.setText(R.string.send_and_call);
    } else {
      sendAndCallText.setText(R.string.share_and_call);
    }
    if (currentIndex == CallComposerPagerAdapter.INDEX_MESSAGE) {
      UiUtil.hideKeyboardFrom(this, windowContainer);
    }
    currentIndex = position;
    CallComposerFragment fragment = (CallComposerFragment) adapter.instantiateItem(pager, position);
    animateSendAndCall(fragment.shouldHide());
    setMediaIconSelected(position);
  }

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

  @Override
  public void onPageScrollStateChanged(int state) {}

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable(VIEW_PAGER_STATE_KEY, pager.onSaveInstanceState());
    outState.putBoolean(ENTRANCE_ANIMATION_KEY, shouldAnimateEntrance);
    outState.putBoolean(SEND_AND_CALL_READY_KEY, sendAndCallReady);
    outState.putInt(CURRENT_INDEX_KEY, currentIndex);
    outState.putLong(SESSION_ID_KEY, sessionId);
  }

  @Override
  public void onBackPressed() {
    LogUtil.enterBlock("CallComposerActivity.onBackPressed");
    if (!isSendAndCallHidingOrHidden) {
      ((CallComposerFragment) adapter.instantiateItem(pager, currentIndex)).clearComposer();
    } else if (!runningExitAnimation) {
      // Unregister first to avoid receiving a callback when the session closes
      getEnrichedCallManager().unregisterStateChangedListener(this);

      // If the user presses the back button when the session fails, there's a race condition here
      // since we clean up failed sessions.
      if (getEnrichedCallManager().getSession(sessionId) != null) {
        getEnrichedCallManager().endCallComposerSession(sessionId);
      }
      runExitAnimation();
    }
  }

  @Override
  public void composeCall(CallComposerFragment fragment) {
    // Since our ViewPager restores state to our fragments, it's possible that they could call
    // #composeCall, so we have to check if the calling fragment is the current fragment.
    if (adapter.instantiateItem(pager, currentIndex) != fragment) {
      return;
    }
    animateSendAndCall(fragment.shouldHide());
  }

  /**
   * Reads arguments from the fragment arguments and populates the necessary instance variables.
   * Copied from {@link com.android.contacts.common.dialog.CallSubjectDialog}.
   */
  private void onHandleIntent(Intent intent) {
    if (intent.getExtras().containsKey(ARG_CALL_COMPOSER_CONTACT_BASE64)) {
      // Invoked from launch_call_composer.py. The proto is provided as a base64 encoded string.
      byte[] bytes =
          Base64.decode(intent.getStringExtra(ARG_CALL_COMPOSER_CONTACT_BASE64), Base64.DEFAULT);
      try {
        contact = DialerContact.parseFrom(bytes);
      } catch (InvalidProtocolBufferException e) {
        throw Assert.createAssertionFailException(e.toString());
      }
    } else {
      contact =
          ProtoParsers.getTrusted(
              intent, ARG_CALL_COMPOSER_CONTACT, DialerContact.getDefaultInstance());
    }
    updateContactInfo();
  }

  @Override
  public boolean isLandscapeLayout() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  /** Populates the contact info fields based on the current contact information. */
  private void updateContactInfo() {
    ContactPhotoManager.getInstance(this)
        .loadDialerThumbnailOrPhoto(
            contactPhoto,
            contact.hasContactUri() ? Uri.parse(contact.getContactUri()) : null,
            contact.getPhotoId(),
            contact.hasPhotoUri() ? Uri.parse(contact.getPhotoUri()) : null,
            contact.getNameOrNumber(),
            contact.getContactType());

    nameView.setText(contact.getNameOrNumber());
    toolbar.setTitle(contact.getNameOrNumber());
    if (!TextUtils.isEmpty(contact.getDisplayNumber())) {
      numberView.setVisibility(View.VISIBLE);
      String secondaryInfo =
          TextUtils.isEmpty(contact.getNumberLabel())
              ? contact.getDisplayNumber()
              : getString(
                  com.android.dialer.contacts.resources.R.string.call_subject_type_and_number,
                  contact.getNumberLabel(),
                  contact.getDisplayNumber());
      numberView.setText(secondaryInfo);
      toolbar.setSubtitle(secondaryInfo);
    } else {
      numberView.setVisibility(View.GONE);
      numberView.setText(null);
    }
  }

  /** Animates compose UI into view */
  private void runEntranceAnimation() {
    if (!shouldAnimateEntrance) {
      return;
    }
    shouldAnimateEntrance = false;

    int value = isLandscapeLayout() ? windowContainer.getWidth() : windowContainer.getHeight();
    ValueAnimator contentAnimation = ValueAnimator.ofFloat(value, 0);
    contentAnimation.setInterpolator(interpolator);
    contentAnimation.setDuration(ENTRANCE_ANIMATION_DURATION_MILLIS);
    contentAnimation.addUpdateListener(
        animation -> {
          if (isLandscapeLayout()) {
            windowContainer.setX((Float) animation.getAnimatedValue());
          } else {
            windowContainer.setY((Float) animation.getAnimatedValue());
          }
        });

    if (!isLandscapeLayout()) {
      int colorFrom = ContextCompat.getColor(this, android.R.color.transparent);
      int colorTo = ContextCompat.getColor(this, R.color.call_composer_background_color);
      ValueAnimator backgroundAnimation =
          ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
      backgroundAnimation.setInterpolator(interpolator);
      backgroundAnimation.setDuration(ENTRANCE_ANIMATION_DURATION_MILLIS); // milliseconds
      backgroundAnimation.addUpdateListener(
          animator -> background.setBackgroundColor((int) animator.getAnimatedValue()));

      AnimatorSet set = new AnimatorSet();
      set.play(contentAnimation).with(backgroundAnimation);
      set.start();
    } else {
      contentAnimation.start();
    }
  }

  /** Animates compose UI out of view and ends the activity. */
  private void runExitAnimation() {
    int value = isLandscapeLayout() ? windowContainer.getWidth() : windowContainer.getHeight();
    ValueAnimator contentAnimation = ValueAnimator.ofFloat(0, value);
    contentAnimation.setInterpolator(interpolator);
    contentAnimation.setDuration(EXIT_ANIMATION_DURATION_MILLIS);
    contentAnimation.addUpdateListener(
        animation -> {
          if (isLandscapeLayout()) {
            windowContainer.setX((Float) animation.getAnimatedValue());
          } else {
            windowContainer.setY((Float) animation.getAnimatedValue());
          }
          if (animation.getAnimatedFraction() > .95) {
            finish();
          }
        });

    if (!isLandscapeLayout()) {
      int colorTo = ContextCompat.getColor(this, android.R.color.transparent);
      int colorFrom = ContextCompat.getColor(this, R.color.call_composer_background_color);
      ValueAnimator backgroundAnimation =
          ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
      backgroundAnimation.setInterpolator(interpolator);
      backgroundAnimation.setDuration(EXIT_ANIMATION_DURATION_MILLIS);
      backgroundAnimation.addUpdateListener(
          animator -> background.setBackgroundColor((int) animator.getAnimatedValue()));

      AnimatorSet set = new AnimatorSet();
      set.play(contentAnimation).with(backgroundAnimation);
      set.start();
    } else {
      contentAnimation.start();
    }
    runningExitAnimation = true;
  }

  @Override
  public void showFullscreen(boolean fullscreen) {
    inFullscreenMode = fullscreen;
    ViewGroup.LayoutParams layoutParams = pager.getLayoutParams();
    if (isLandscapeLayout()) {
      layoutParams.height = background.getHeight();
      toolbar.setVisibility(View.INVISIBLE);
      contactContainer.setVisibility(View.GONE);
    } else if (fullscreen || getResources().getBoolean(R.bool.show_toolbar)) {
      layoutParams.height = background.getHeight() - toolbar.getHeight();
      toolbar.setVisibility(View.VISIBLE);
      contactContainer.setVisibility(View.GONE);
    } else {
      layoutParams.height =
          getResources().getDimensionPixelSize(R.dimen.call_composer_view_pager_height);
      toolbar.setVisibility(View.INVISIBLE);
      contactContainer.setVisibility(View.VISIBLE);
    }
    pager.setLayoutParams(layoutParams);
  }

  @Override
  public boolean isFullscreen() {
    return inFullscreenMode;
  }

  private void animateSendAndCall(final boolean shouldHide) {
    // createCircularReveal doesn't respect animations being disabled, handle it here.
    if (ViewUtil.areAnimationsDisabled(this)) {
      isSendAndCallHidingOrHidden = shouldHide;
      sendAndCall.setVisibility(shouldHide ? View.INVISIBLE : View.VISIBLE);
      return;
    }

    // If the animation is changing directions, start it again. Else do nothing.
    if (isSendAndCallHidingOrHidden != shouldHide) {
      int centerX = sendAndCall.getWidth() / 2;
      int centerY = sendAndCall.getHeight() / 2;
      int startRadius = shouldHide ? centerX : 0;
      int endRadius = shouldHide ? 0 : centerX;

      // When the device rotates and state is restored, the send and call button may not be attached
      // yet and this causes a crash when we attempt to to reveal it. To prevent this, we wait until
      // {@code sendAndCall} is ready, then animate and reveal it.
      ViewUtil.doOnPreDraw(
          sendAndCall,
          true,
          () -> {
            Animator animator =
                ViewAnimationUtils.createCircularReveal(
                    sendAndCall, centerX, centerY, startRadius, endRadius);
            animator.addListener(
                new AnimatorListener() {
                  @Override
                  public void onAnimationStart(Animator animation) {
                    isSendAndCallHidingOrHidden = shouldHide;
                    sendAndCall.setVisibility(View.VISIBLE);
                    cameraIcon.setVisibility(View.VISIBLE);
                    galleryIcon.setVisibility(View.VISIBLE);
                    messageIcon.setVisibility(View.VISIBLE);
                  }

                  @Override
                  public void onAnimationEnd(Animator animation) {
                    if (isSendAndCallHidingOrHidden) {
                      sendAndCall.setVisibility(View.INVISIBLE);
                    } else {
                      // hide buttons to prevent overdrawing and talkback discoverability
                      cameraIcon.setVisibility(View.GONE);
                      galleryIcon.setVisibility(View.GONE);
                      messageIcon.setVisibility(View.GONE);
                    }
                  }

                  @Override
                  public void onAnimationCancel(Animator animation) {}

                  @Override
                  public void onAnimationRepeat(Animator animation) {}
                });
            animator.start();
          });
    }
  }

  private void setMediaIconSelected(int position) {
    float alpha = 0.7f;
    cameraIcon.setAlpha(position == CallComposerPagerAdapter.INDEX_CAMERA ? 1 : alpha);
    galleryIcon.setAlpha(position == CallComposerPagerAdapter.INDEX_GALLERY ? 1 : alpha);
    messageIcon.setAlpha(position == CallComposerPagerAdapter.INDEX_MESSAGE ? 1 : alpha);
  }

  private void setFailedResultAndFinish() {
    setResult(
        RESULT_FIRST_USER, new Intent().putExtra(KEY_CONTACT_NAME, contact.getNameOrNumber()));
    finish();
  }

  @NonNull
  private EnrichedCallManager getEnrichedCallManager() {
    return EnrichedCallComponent.get(this).getEnrichedCallManager();
  }
}
