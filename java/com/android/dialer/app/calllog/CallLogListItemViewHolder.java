/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.app.calllog;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.contacts.common.ClipboardUtils;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.contacts.common.dialog.CallSubjectDialog;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.contacts.common.lettertiles.LetterTileDrawable.ContactType;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogAdapter.OnActionModeStateChangedListener;
import com.android.dialer.app.calllog.calllogcache.CallLogCache;
import com.android.dialer.app.voicemail.VoicemailPlaybackLayout;
import com.android.dialer.app.voicemail.VoicemailPlaybackPresenter;
import com.android.dialer.blocking.BlockedNumbersMigrator;
import com.android.dialer.blocking.FilteredNumberCompat;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.callcomposer.CallComposerActivity;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.calldetails.CallDetailsEntries;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.dialercontact.SimDetails;
import com.android.dialer.lightbringer.Lightbringer;
import com.android.dialer.lightbringer.LightbringerComponent;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.logging.UiAction;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.phonenumbercache.CachedNumberLookupService;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.PhoneNumberCache;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.CallUtil;
import com.android.dialer.util.DialerUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This is an object containing references to views contained by the call log list item. This
 * improves performance by reducing the frequency with which we need to find views by IDs.
 *
 * <p>This object also contains UI logic pertaining to the view, to isolate it from the
 * CallLogAdapter.
 */
public final class CallLogListItemViewHolder extends RecyclerView.ViewHolder
    implements View.OnClickListener,
        View.OnLongClickListener,
        MenuItem.OnMenuItemClickListener,
        View.OnCreateContextMenuListener {
  /** The root view of the call log list item */
  public final View rootView;
  /** The quick contact badge for the contact. */
  public final DialerQuickContactBadge quickContactView;
  /** The primary action view of the entry. */
  public final View primaryActionView;
  /** The details of the phone call. */
  public final PhoneCallDetailsViews phoneCallDetailsViews;
  /** The text of the header for a day grouping. */
  public final TextView dayGroupHeader;
  /** The view containing the details for the call log row, including the action buttons. */
  public final CardView callLogEntryView;
  /** The actionable view which places a call to the number corresponding to the call log row. */
  public final ImageView primaryActionButtonView;

  private final Context mContext;
  @Nullable private final PhoneAccountHandle mDefaultPhoneAccountHandle;
  private final CallLogCache mCallLogCache;
  private final CallLogListItemHelper mCallLogListItemHelper;
  private final CachedNumberLookupService mCachedNumberLookupService;
  private final VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;
  private final OnClickListener mBlockReportListener;
  @HostUi private final int hostUi;
  /** Whether the data fields are populated by the worker thread, ready to be shown. */
  public boolean isLoaded;
  /** The view containing call log item actions. Null until the ViewStub is inflated. */
  public View actionsView;
  /** The button views below are assigned only when the action section is expanded. */
  public VoicemailPlaybackLayout voicemailPlaybackView;

  public View callButtonView;
  public View videoCallButtonView;
  public View createNewContactButtonView;
  public View addToExistingContactButtonView;
  public View sendMessageView;
  public View blockReportView;
  public View blockView;
  public View unblockView;
  public View reportNotSpamView;
  public View detailsButtonView;
  public View callWithNoteButtonView;
  public View callComposeButtonView;
  public View sendVoicemailButtonView;
  public ImageView workIconView;
  public ImageView checkBoxView;
  /**
   * The row Id for the first call associated with the call log entry. Used as a key for the map
   * used to track which call log entries have the action button section expanded.
   */
  public long rowId;
  /**
   * The call Ids for the calls represented by the current call log entry. Used when the user
   * deletes a call log entry.
   */
  public long[] callIds;
  /**
   * The callable phone number for the current call log entry. Cached here as the call back intent
   * is set only when the actions ViewStub is inflated.
   */
  public String number;
  /** The post-dial numbers that are dialed following the phone number. */
  public String postDialDigits;
  /** The formatted phone number to display. */
  public String displayNumber;
  /**
   * The phone number presentation for the current call log entry. Cached here as the call back
   * intent is set only when the actions ViewStub is inflated.
   */
  public int numberPresentation;
  /** The type of the phone number (e.g. main, work, etc). */
  public String numberType;
  /**
   * The country iso for the call. Cached here as the call back intent is set only when the actions
   * ViewStub is inflated.
   */
  public String countryIso;
  /**
   * The type of call for the current call log entry. Cached here as the call back intent is set
   * only when the actions ViewStub is inflated.
   */
  public int callType;
  /**
   * ID for blocked numbers database. Set when context menu is created, if the number is blocked.
   */
  public Integer blockId;
  /**
   * The account for the current call log entry. Cached here as the call back intent is set only
   * when the actions ViewStub is inflated.
   */
  public PhoneAccountHandle accountHandle;
  /**
   * If the call has an associated voicemail message, the URI of the voicemail message for playback.
   * Cached here as the voicemail intent is only set when the actions ViewStub is inflated.
   */
  public String voicemailUri;
  /**
   * The name or number associated with the call. Cached here for use when setting content
   * descriptions on buttons in the actions ViewStub when it is inflated.
   */
  @Nullable public CharSequence nameOrNumber;
  /**
   * The call type or Location associated with the call. Cached here for use when setting text for a
   * voicemail log's call button
   */
  public CharSequence callTypeOrLocation;
  /** The contact info for the contact displayed in this list item. */
  public volatile ContactInfo info;
  /** Whether spam feature is enabled, which affects UI. */
  public boolean isSpamFeatureEnabled;
  /** Whether the current log entry is a spam number or not. */
  public boolean isSpam;

  public boolean isCallComposerCapable;
  public boolean lightbringerReady;

  private View.OnClickListener mExpandCollapseListener;
  private final OnActionModeStateChangedListener onActionModeStateChangedListener;
  private final View.OnLongClickListener longPressListener;
  private boolean mVoicemailPrimaryActionButtonClicked;

  public int dayGroupHeaderVisibility;
  public CharSequence dayGroupHeaderText;
  public boolean isAttachedToWindow;

  public AsyncTask<Void, Void, ?> asyncTask;
  private CallDetailsEntries callDetailsEntries;

  private CallLogListItemViewHolder(
      Context context,
      OnClickListener blockReportListener,
      View.OnClickListener expandCollapseListener,
      View.OnLongClickListener longClickListener,
      CallLogAdapter.OnActionModeStateChangedListener actionModeStateChangedListener,
      CallLogCache callLogCache,
      CallLogListItemHelper callLogListItemHelper,
      VoicemailPlaybackPresenter voicemailPlaybackPresenter,
      View rootView,
      DialerQuickContactBadge dialerQuickContactView,
      View primaryActionView,
      PhoneCallDetailsViews phoneCallDetailsViews,
      CardView callLogEntryView,
      TextView dayGroupHeader,
      ImageView primaryActionButtonView) {
    super(rootView);

    mContext = context;
    mExpandCollapseListener = expandCollapseListener;
    onActionModeStateChangedListener = actionModeStateChangedListener;
    longPressListener = longClickListener;
    mCallLogCache = callLogCache;
    mCallLogListItemHelper = callLogListItemHelper;
    mVoicemailPlaybackPresenter = voicemailPlaybackPresenter;
    mBlockReportListener = blockReportListener;
    mCachedNumberLookupService = PhoneNumberCache.get(mContext).getCachedNumberLookupService();

    // Cache this to avoid having to look it up each time we bind to a call log entry
    mDefaultPhoneAccountHandle =
        TelecomUtil.getDefaultOutgoingPhoneAccount(context, PhoneAccount.SCHEME_TEL);

    this.rootView = rootView;
    this.quickContactView = dialerQuickContactView;
    this.primaryActionView = primaryActionView;
    this.phoneCallDetailsViews = phoneCallDetailsViews;
    this.callLogEntryView = callLogEntryView;
    this.dayGroupHeader = dayGroupHeader;
    this.primaryActionButtonView = primaryActionButtonView;
    this.workIconView = (ImageView) rootView.findViewById(R.id.work_profile_icon);
    this.checkBoxView = (ImageView) rootView.findViewById(R.id.quick_contact_checkbox);

    // Set text height to false on the TextViews so they don't have extra padding.
    phoneCallDetailsViews.nameView.setElegantTextHeight(false);
    phoneCallDetailsViews.callLocationAndDate.setElegantTextHeight(false);

    if (mContext instanceof CallLogActivity) {
      hostUi = HostUi.CALL_HISTORY;
      Logger.get(mContext)
          .logQuickContactOnTouch(
              quickContactView, InteractionEvent.Type.OPEN_QUICK_CONTACT_FROM_CALL_HISTORY, true);
    } else if (mVoicemailPlaybackPresenter == null) {
      hostUi = HostUi.CALL_LOG;
      Logger.get(mContext)
          .logQuickContactOnTouch(
              quickContactView, InteractionEvent.Type.OPEN_QUICK_CONTACT_FROM_CALL_LOG, true);
    } else {
      hostUi = HostUi.VOICEMAIL;
      Logger.get(mContext)
          .logQuickContactOnTouch(
              quickContactView, InteractionEvent.Type.OPEN_QUICK_CONTACT_FROM_VOICEMAIL, false);
    }

    quickContactView.setOverlay(null);
    if (CompatUtils.hasPrioritizedMimeType()) {
      quickContactView.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);
    }
    primaryActionButtonView.setOnClickListener(this);
    primaryActionButtonView.setOnLongClickListener(this);
    primaryActionView.setOnClickListener(mExpandCollapseListener);
    if (mVoicemailPlaybackPresenter != null
        && ConfigProviderBindings.get(mContext)
            .getBoolean(
                CallLogAdapter.ENABLE_CALL_LOG_MULTI_SELECT,
                CallLogAdapter.ENABLE_CALL_LOG_MULTI_SELECT_FLAG)) {
      primaryActionView.setOnLongClickListener(longPressListener);
      quickContactView.setOnLongClickListener(longPressListener);
      quickContactView.setMulitSelectListeners(
          mExpandCollapseListener, onActionModeStateChangedListener);
    } else {
      primaryActionView.setOnCreateContextMenuListener(this);
    }
  }

  public static CallLogListItemViewHolder create(
      View view,
      Context context,
      OnClickListener blockReportListener,
      View.OnClickListener expandCollapseListener,
      View.OnLongClickListener longClickListener,
      CallLogAdapter.OnActionModeStateChangedListener actionModeStateChangeListener,
      CallLogCache callLogCache,
      CallLogListItemHelper callLogListItemHelper,
      VoicemailPlaybackPresenter voicemailPlaybackPresenter) {

    return new CallLogListItemViewHolder(
        context,
        blockReportListener,
        expandCollapseListener,
        longClickListener,
        actionModeStateChangeListener,
        callLogCache,
        callLogListItemHelper,
        voicemailPlaybackPresenter,
        view,
        (DialerQuickContactBadge) view.findViewById(R.id.quick_contact_photo),
        view.findViewById(R.id.primary_action_view),
        PhoneCallDetailsViews.fromView(view),
        (CardView) view.findViewById(R.id.call_log_row),
        (TextView) view.findViewById(R.id.call_log_day_group_label),
        (ImageView) view.findViewById(R.id.primary_action_button));
  }

  public static CallLogListItemViewHolder createForTest(Context context) {
    return createForTest(context, null, null);
  }

  public static CallLogListItemViewHolder createForTest(
      Context context,
      View.OnClickListener expandCollapseListener,
      VoicemailPlaybackPresenter voicemailPlaybackPresenter) {
    Resources resources = context.getResources();
    CallLogCache callLogCache = new CallLogCache(context);
    PhoneCallDetailsHelper phoneCallDetailsHelper =
        new PhoneCallDetailsHelper(context, resources, callLogCache);

    CallLogListItemViewHolder viewHolder =
        new CallLogListItemViewHolder(
            context,
            null,
            expandCollapseListener /* expandCollapseListener */,
            null,
            null,
            callLogCache,
            new CallLogListItemHelper(phoneCallDetailsHelper, resources, callLogCache),
            voicemailPlaybackPresenter,
            LayoutInflater.from(context).inflate(R.layout.call_log_list_item, null),
            new DialerQuickContactBadge(context),
            new View(context),
            PhoneCallDetailsViews.createForTest(context),
            new CardView(context),
            new TextView(context),
            new ImageView(context));
    viewHolder.detailsButtonView = new TextView(context);
    viewHolder.actionsView = new View(context);
    viewHolder.voicemailPlaybackView = new VoicemailPlaybackLayout(context);
    viewHolder.workIconView = new ImageButton(context);
    viewHolder.checkBoxView = new ImageButton(context);
    return viewHolder;
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    int resId = item.getItemId();
    if (resId == R.id.context_menu_copy_to_clipboard) {
      ClipboardUtils.copyText(mContext, null, number, true);
      return true;
    } else if (resId == R.id.context_menu_copy_transcript_to_clipboard) {
      ClipboardUtils.copyText(
          mContext, null, phoneCallDetailsViews.voicemailTranscriptionView.getText(), true);
      return true;
    } else if (resId == R.id.context_menu_edit_before_call) {
      final Intent intent = new Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(number));
      intent.setClass(mContext, DialtactsActivity.class);
      DialerUtils.startActivityWithErrorToast(mContext, intent);
      return true;
    } else if (resId == R.id.context_menu_block_report_spam) {
      Logger.get(mContext)
          .logImpression(DialerImpression.Type.CALL_LOG_CONTEXT_MENU_BLOCK_REPORT_SPAM);
      maybeShowBlockNumberMigrationDialog(
          new BlockedNumbersMigrator.Listener() {
            @Override
            public void onComplete() {
              mBlockReportListener.onBlockReportSpam(
                  displayNumber, number, countryIso, callType, info.sourceType);
            }
          });
    } else if (resId == R.id.context_menu_block) {
      Logger.get(mContext).logImpression(DialerImpression.Type.CALL_LOG_CONTEXT_MENU_BLOCK_NUMBER);
      maybeShowBlockNumberMigrationDialog(
          new BlockedNumbersMigrator.Listener() {
            @Override
            public void onComplete() {
              mBlockReportListener.onBlock(
                  displayNumber, number, countryIso, callType, info.sourceType);
            }
          });
    } else if (resId == R.id.context_menu_unblock) {
      Logger.get(mContext)
          .logImpression(DialerImpression.Type.CALL_LOG_CONTEXT_MENU_UNBLOCK_NUMBER);
      mBlockReportListener.onUnblock(
          displayNumber, number, countryIso, callType, info.sourceType, isSpam, blockId);
    } else if (resId == R.id.context_menu_report_not_spam) {
      Logger.get(mContext)
          .logImpression(DialerImpression.Type.CALL_LOG_CONTEXT_MENU_REPORT_AS_NOT_SPAM);
      mBlockReportListener.onReportNotSpam(
          displayNumber, number, countryIso, callType, info.sourceType);
    }
    return false;
  }

  /**
   * Configures the action buttons in the expandable actions ViewStub. The ViewStub is not inflated
   * during initial binding, so click handlers, tags and accessibility text must be set here, if
   * necessary.
   */
  public void inflateActionViewStub() {
    ViewStub stub = (ViewStub) rootView.findViewById(R.id.call_log_entry_actions_stub);
    if (stub != null) {
      actionsView = stub.inflate();

      voicemailPlaybackView =
          (VoicemailPlaybackLayout) actionsView.findViewById(R.id.voicemail_playback_layout);
      voicemailPlaybackView.setViewHolder(this);

      callButtonView = actionsView.findViewById(R.id.call_action);
      callButtonView.setOnClickListener(this);

      videoCallButtonView = actionsView.findViewById(R.id.video_call_action);
      videoCallButtonView.setOnClickListener(this);

      createNewContactButtonView = actionsView.findViewById(R.id.create_new_contact_action);
      createNewContactButtonView.setOnClickListener(this);

      addToExistingContactButtonView =
          actionsView.findViewById(R.id.add_to_existing_contact_action);
      addToExistingContactButtonView.setOnClickListener(this);

      sendMessageView = actionsView.findViewById(R.id.send_message_action);
      sendMessageView.setOnClickListener(this);

      blockReportView = actionsView.findViewById(R.id.block_report_action);
      blockReportView.setOnClickListener(this);

      blockView = actionsView.findViewById(R.id.block_action);
      blockView.setOnClickListener(this);

      unblockView = actionsView.findViewById(R.id.unblock_action);
      unblockView.setOnClickListener(this);

      reportNotSpamView = actionsView.findViewById(R.id.report_not_spam_action);
      reportNotSpamView.setOnClickListener(this);

      detailsButtonView = actionsView.findViewById(R.id.details_action);
      detailsButtonView.setOnClickListener(this);

      callWithNoteButtonView = actionsView.findViewById(R.id.call_with_note_action);
      callWithNoteButtonView.setOnClickListener(this);

      callComposeButtonView = actionsView.findViewById(R.id.call_compose_action);
      callComposeButtonView.setOnClickListener(this);

      sendVoicemailButtonView = actionsView.findViewById(R.id.share_voicemail);
      sendVoicemailButtonView.setOnClickListener(this);
    }
  }

  private void updatePrimaryActionButton(boolean isExpanded) {

    if (nameOrNumber == null) {
      LogUtil.e("CallLogListItemViewHolder.updatePrimaryActionButton", "name or number is null");
    }

    // Calling expandTemplate with a null parameter will cause a NullPointerException.
    CharSequence validNameOrNumber = nameOrNumber == null ? "" : nameOrNumber;

    if (!TextUtils.isEmpty(voicemailUri)) {
      // Treat as voicemail list item; show play button if not expanded.
      if (!isExpanded) {
        primaryActionButtonView.setImageResource(R.drawable.quantum_ic_play_arrow_white_24);
        primaryActionButtonView.setContentDescription(
            TextUtils.expandTemplate(
                mContext.getString(R.string.description_voicemail_action), validNameOrNumber));
        primaryActionButtonView.setTag(null);
        primaryActionButtonView.setVisibility(View.VISIBLE);
      } else {
        primaryActionButtonView.setVisibility(View.GONE);
      }
    } else {
      // Treat as normal list item; show call button, if possible.
      if (PhoneNumberHelper.canPlaceCallsTo(number, numberPresentation)) {
        boolean isVoicemailNumber = mCallLogCache.isVoicemailNumber(accountHandle, number);

        if (!isVoicemailNumber && showLightbringerPrimaryButton()) {
          CallIntentBuilder.increaseLightbringerCallButtonAppearInCollapsedCallLogItemCount();
          primaryActionButtonView.setTag(IntentProvider.getLightbringerIntentProvider(number));
          primaryActionButtonView.setContentDescription(
              TextUtils.expandTemplate(
                  mContext.getString(R.string.description_video_call_action), validNameOrNumber));
          primaryActionButtonView.setImageResource(R.drawable.quantum_ic_videocam_vd_theme_24);
          primaryActionButtonView.setVisibility(View.VISIBLE);
          return;
        }

        if (isVoicemailNumber) {
          // Call to generic voicemail number, in case there are multiple accounts.
          primaryActionButtonView.setTag(IntentProvider.getReturnVoicemailCallIntentProvider());
        } else {
          primaryActionButtonView.setTag(
              IntentProvider.getReturnCallIntentProvider(number + postDialDigits));
        }

        primaryActionButtonView.setContentDescription(
            TextUtils.expandTemplate(
                mContext.getString(R.string.description_call_action), validNameOrNumber));
        primaryActionButtonView.setImageResource(R.drawable.quantum_ic_call_vd_theme_24);
        primaryActionButtonView.setVisibility(View.VISIBLE);
      } else {
        primaryActionButtonView.setTag(null);
        primaryActionButtonView.setVisibility(View.GONE);
      }
    }
  }

  /**
   * Binds text titles, click handlers and intents to the voicemail, details and callback action
   * buttons.
   */
  private void bindActionButtons() {
    boolean canPlaceCallToNumber = PhoneNumberHelper.canPlaceCallsTo(number, numberPresentation);

    // Hide the call buttons by default. We then set it to be visible when appropriate below.
    // This saves us having to remember to set it to GONE in multiple places.
    callButtonView.setVisibility(View.GONE);
    videoCallButtonView.setVisibility(View.GONE);

    if (isFullyUndialableVoicemail()) {
      // Sometimes the voicemail server will report the message is from some non phone number
      // source. If the number does not contains any dialable digit treat it as it is from a unknown
      // number, remove all action buttons but still show the voicemail playback layout.
      detailsButtonView.setVisibility(View.GONE);
      createNewContactButtonView.setVisibility(View.GONE);
      addToExistingContactButtonView.setVisibility(View.GONE);
      sendMessageView.setVisibility(View.GONE);
      callWithNoteButtonView.setVisibility(View.GONE);
      callComposeButtonView.setVisibility(View.GONE);
      blockReportView.setVisibility(View.GONE);
      blockView.setVisibility(View.GONE);
      unblockView.setVisibility(View.GONE);
      reportNotSpamView.setVisibility(View.GONE);

      voicemailPlaybackView.setVisibility(View.VISIBLE);
      Uri uri = Uri.parse(voicemailUri);
      mVoicemailPlaybackPresenter.setPlaybackView(
          voicemailPlaybackView,
          rowId,
          uri,
          mVoicemailPrimaryActionButtonClicked,
          sendVoicemailButtonView);
      mVoicemailPrimaryActionButtonClicked = false;
      CallLogAsyncTaskUtil.markVoicemailAsRead(mContext, uri);
      return;
    }

    if (canPlaceCallToNumber) {
      callButtonView.setTag(IntentProvider.getReturnCallIntentProvider(number));
    }

    if (!TextUtils.isEmpty(voicemailUri) && canPlaceCallToNumber) {
      ((TextView) callButtonView.findViewById(R.id.call_action_text))
          .setText(
              TextUtils.expandTemplate(
                  mContext.getString(R.string.call_log_action_call),
                  nameOrNumber == null ? "" : nameOrNumber));
      TextView callTypeOrLocationView =
          ((TextView) callButtonView.findViewById(R.id.call_type_or_location_text));
      if (callType == Calls.VOICEMAIL_TYPE && !TextUtils.isEmpty(callTypeOrLocation)) {
        callTypeOrLocationView.setText(callTypeOrLocation);
        callTypeOrLocationView.setVisibility(View.VISIBLE);
      } else {
        callTypeOrLocationView.setVisibility(View.GONE);
      }
      callButtonView.setVisibility(View.VISIBLE);
    }

    // We need to check if we are showing the Lightbringer primary button. If we are, then we should
    // show the "Call" button here regardless of IMS availability.
    if (showLightbringerPrimaryButton()) {
      callButtonView.setVisibility(View.VISIBLE);
      videoCallButtonView.setVisibility(View.GONE);
    } else if (CallUtil.isVideoEnabled(mContext)
        && (hasPlacedCarrierVideoCall() || canSupportCarrierVideoCall())) {
      videoCallButtonView.setTag(IntentProvider.getReturnVideoCallIntentProvider(number));
      videoCallButtonView.setVisibility(View.VISIBLE);
    } else if (lightbringerReady) {
      videoCallButtonView.setTag(IntentProvider.getLightbringerIntentProvider(number));
      videoCallButtonView.setVisibility(View.VISIBLE);
    }

    // For voicemail calls, show the voicemail playback layout; hide otherwise.
    if (callType == Calls.VOICEMAIL_TYPE
        && mVoicemailPlaybackPresenter != null
        && !TextUtils.isEmpty(voicemailUri)) {
      voicemailPlaybackView.setVisibility(View.VISIBLE);

      Uri uri = Uri.parse(voicemailUri);
      mVoicemailPlaybackPresenter.setPlaybackView(
          voicemailPlaybackView,
          rowId,
          uri,
          mVoicemailPrimaryActionButtonClicked,
          sendVoicemailButtonView);
      mVoicemailPrimaryActionButtonClicked = false;
      CallLogAsyncTaskUtil.markVoicemailAsRead(mContext, uri);
    } else {
      voicemailPlaybackView.setVisibility(View.GONE);
      sendVoicemailButtonView.setVisibility(View.GONE);
    }

    if (callType == Calls.VOICEMAIL_TYPE) {
      detailsButtonView.setVisibility(View.GONE);
    } else {
      detailsButtonView.setVisibility(View.VISIBLE);
      boolean canReportCallerId =
          mCachedNumberLookupService != null
              && mCachedNumberLookupService.canReportAsInvalid(info.sourceType, info.objectId);
      detailsButtonView.setTag(
          IntentProvider.getCallDetailIntentProvider(
              callDetailsEntries, buildContact(), canReportCallerId));
    }

    boolean isBlockedOrSpam = blockId != null || (isSpamFeatureEnabled && isSpam);

    if (!isBlockedOrSpam && info != null && UriUtils.isEncodedContactUri(info.lookupUri)) {
      createNewContactButtonView.setTag(
          IntentProvider.getAddContactIntentProvider(
              info.lookupUri, info.name, info.number, info.type, true /* isNewContact */));
      createNewContactButtonView.setVisibility(View.VISIBLE);

      addToExistingContactButtonView.setTag(
          IntentProvider.getAddContactIntentProvider(
              info.lookupUri, info.name, info.number, info.type, false /* isNewContact */));
      addToExistingContactButtonView.setVisibility(View.VISIBLE);
    } else {
      createNewContactButtonView.setVisibility(View.GONE);
      addToExistingContactButtonView.setVisibility(View.GONE);
    }

    boolean isVoicemailNumber = mCallLogCache.isVoicemailNumber(accountHandle, number);
    if (canPlaceCallToNumber && !isBlockedOrSpam && !isVoicemailNumber) {
      sendMessageView.setTag(IntentProvider.getSendSmsIntentProvider(number));
      sendMessageView.setVisibility(View.VISIBLE);
    } else {
      sendMessageView.setVisibility(View.GONE);
    }

    mCallLogListItemHelper.setActionContentDescriptions(this);

    boolean supportsCallSubject = mCallLogCache.doesAccountSupportCallSubject(accountHandle);
    callWithNoteButtonView.setVisibility(
        supportsCallSubject && !isVoicemailNumber && info != null ? View.VISIBLE : View.GONE);

    callComposeButtonView.setVisibility(isCallComposerCapable ? View.VISIBLE : View.GONE);

    updateBlockReportActions(isVoicemailNumber);
  }

  private boolean isFullyUndialableVoicemail() {
    if (callType == Calls.VOICEMAIL_TYPE) {
      if (!hasDialableChar(number)) {
        return true;
      }
    }
    return false;
  }

  private boolean showLightbringerPrimaryButton() {
    return accountHandle != null
        && accountHandle.getComponentName().equals(getLightbringer().getPhoneAccountComponentName())
        && lightbringerReady;
  }

  private static boolean hasDialableChar(CharSequence number) {
    if (TextUtils.isEmpty(number)) {
      return false;
    }
    for (char c : number.toString().toCharArray()) {
      if (PhoneNumberUtils.isDialable(c)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasPlacedCarrierVideoCall() {
    if (!phoneCallDetailsViews.callTypeIcons.isVideoShown()) {
      return false;
    }
    if (accountHandle == null) {
      return false;
    }
    if (mDefaultPhoneAccountHandle == null) {
      return false;
    }
    return accountHandle.getComponentName().equals(mDefaultPhoneAccountHandle.getComponentName());
  }

  private boolean canSupportCarrierVideoCall() {
    return mCallLogCache.canRelyOnVideoPresence()
        && info != null
        && (info.carrierPresence & Phone.CARRIER_PRESENCE_VT_CAPABLE) != 0;
  }

  /**
   * Show or hide the action views, such as voicemail, details, and add contact.
   *
   * <p>If the action views have never been shown yet for this view, inflate the view stub.
   */
  public void showActions(boolean show) {
    showOrHideVoicemailTranscriptionView(show);

    if (show) {
      if (!isLoaded) {
        // b/31268128 for some unidentified reason showActions() can be called before the item is
        // loaded, causing NPE on uninitialized fields. Just log and return here, showActions() will
        // be called again once the item is loaded.
        LogUtil.e(
            "CallLogListItemViewHolder.showActions",
            "called before item is loaded",
            new Exception());
        return;
      }

      // Inflate the view stub if necessary, and wire up the event handlers.
      inflateActionViewStub();
      bindActionButtons();
      actionsView.setVisibility(View.VISIBLE);
      actionsView.setAlpha(1.0f);
    } else {
      // When recycling a view, it is possible the actionsView ViewStub was previously
      // inflated so we should hide it in this case.
      if (actionsView != null) {
        actionsView.setVisibility(View.GONE);
      }
    }

    updatePrimaryActionButton(show);
  }

  private void showOrHideVoicemailTranscriptionView(boolean isExpanded) {
    if (callType != Calls.VOICEMAIL_TYPE) {
      return;
    }

    final TextView view = phoneCallDetailsViews.voicemailTranscriptionView;
    if (!isExpanded || TextUtils.isEmpty(view.getText())) {
      view.setVisibility(View.GONE);
      return;
    }
    view.setVisibility(View.VISIBLE);
  }

  public void updatePhoto() {
    quickContactView.assignContactUri(info.lookupUri);

    if (isSpamFeatureEnabled && isSpam) {
      quickContactView.setImageDrawable(mContext.getDrawable(R.drawable.blocked_contact));
      return;
    }

    final String displayName = TextUtils.isEmpty(info.name) ? displayNumber : info.name;
    ContactPhotoManager.getInstance(mContext)
        .loadDialerThumbnailOrPhoto(
            quickContactView,
            info.lookupUri,
            info.photoId,
            info.photoUri,
            displayName,
            getContactType());
  }

  private @ContactType int getContactType() {
    return LetterTileDrawable.getContactTypeFromPrimitives(
        mCallLogCache.isVoicemailNumber(accountHandle, number),
        isSpam,
        mCachedNumberLookupService != null
            && mCachedNumberLookupService.isBusiness(info.sourceType),
        numberPresentation,
        false);
  }

  @Override
  public void onClick(View view) {
    if (view.getId() == R.id.primary_action_button) {
      CallLogAsyncTaskUtil.markCallAsRead(mContext, callIds);
    }

    if (view.getId() == R.id.primary_action_button && !TextUtils.isEmpty(voicemailUri)) {
      Logger.get(mContext).logImpression(DialerImpression.Type.VOICEMAIL_PLAY_AUDIO_DIRECTLY);
      mVoicemailPrimaryActionButtonClicked = true;
      mExpandCollapseListener.onClick(primaryActionView);
    } else if (view.getId() == R.id.call_with_note_action) {
      CallSubjectDialog.start(
          (Activity) mContext,
          info.photoId,
          info.photoUri,
          info.lookupUri,
          (String) nameOrNumber /* top line of contact view in call subject dialog */,
          number,
          TextUtils.isEmpty(info.name) ? null : displayNumber, /* second line of contact
                                                                           view in dialog. */
          numberType, /* phone number type (e.g. mobile) in second line of contact view */
          getContactType(),
          accountHandle);
    } else if (view.getId() == R.id.block_report_action) {
      Logger.get(mContext).logImpression(DialerImpression.Type.CALL_LOG_BLOCK_REPORT_SPAM);
      maybeShowBlockNumberMigrationDialog(
          new BlockedNumbersMigrator.Listener() {
            @Override
            public void onComplete() {
              mBlockReportListener.onBlockReportSpam(
                  displayNumber, number, countryIso, callType, info.sourceType);
            }
          });
    } else if (view.getId() == R.id.block_action) {
      Logger.get(mContext).logImpression(DialerImpression.Type.CALL_LOG_BLOCK_NUMBER);
      maybeShowBlockNumberMigrationDialog(
          new BlockedNumbersMigrator.Listener() {
            @Override
            public void onComplete() {
              mBlockReportListener.onBlock(
                  displayNumber, number, countryIso, callType, info.sourceType);
            }
          });
    } else if (view.getId() == R.id.unblock_action) {
      Logger.get(mContext).logImpression(DialerImpression.Type.CALL_LOG_UNBLOCK_NUMBER);
      mBlockReportListener.onUnblock(
          displayNumber, number, countryIso, callType, info.sourceType, isSpam, blockId);
    } else if (view.getId() == R.id.report_not_spam_action) {
      Logger.get(mContext).logImpression(DialerImpression.Type.CALL_LOG_REPORT_AS_NOT_SPAM);
      mBlockReportListener.onReportNotSpam(
          displayNumber, number, countryIso, callType, info.sourceType);
    } else if (view.getId() == R.id.call_compose_action) {
      LogUtil.i("CallLogListItemViewHolder.onClick", "share and call pressed");
      Logger.get(mContext).logImpression(DialerImpression.Type.CALL_LOG_SHARE_AND_CALL);
      Activity activity = (Activity) mContext;
      activity.startActivityForResult(
          CallComposerActivity.newIntent(activity, buildContact()),
          DialtactsActivity.ACTIVITY_REQUEST_CODE_CALL_COMPOSE);
    } else if (view.getId() == R.id.share_voicemail) {
      Logger.get(mContext).logImpression(DialerImpression.Type.VVM_SHARE_PRESSED);
      mVoicemailPlaybackPresenter.shareVoicemail();
    } else {
      logCallLogAction(view.getId());

      final IntentProvider intentProvider = (IntentProvider) view.getTag();
      if (intentProvider == null) {
        return;
      }

      final Intent intent = intentProvider.getClickIntent(mContext);
      // See IntentProvider.getCallDetailIntentProvider() for why this may be null.
      if (intent == null) {
        return;
      }

      // We check to see if we are starting a Lightbringer intent. The reason is Lightbringer
      // intents need to be started using startActivityForResult instead of the usual startActivity
      String packageName = intent.getPackage();
      if (packageName != null && packageName.equals(getLightbringer().getPackageName())) {
        Logger.get(mContext)
            .logImpression(DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FROM_CALL_LOG);
        startLightbringerActivity(intent);
      } else if (CallDetailsActivity.isLaunchIntent(intent)) {
        PerformanceReport.recordClick(UiAction.Type.OPEN_CALL_DETAIL);
        ((Activity) mContext)
            .startActivityForResult(intent, DialtactsActivity.ACTIVITY_REQUEST_CODE_CALL_DETAILS);
      } else {
        if (Intent.ACTION_CALL.equals(intent.getAction())
            && intent.getIntExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, -1)
                == VideoProfile.STATE_BIDIRECTIONAL) {
          Logger.get(mContext)
              .logImpression(DialerImpression.Type.IMS_VIDEO_REQUESTED_FROM_CALL_LOG);
        }
        DialerUtils.startActivityWithErrorToast(mContext, intent);
      }
    }
  }

  @Override
  public boolean onLongClick(View view) {
    final IntentProvider intentProvider = (IntentProvider) view.getTag();
    final Intent intent = intentProvider != null
        ? intentProvider.getLongClickIntent(mContext) : null;
    if (intent != null) {
      DialerUtils.startActivityWithErrorToast(mContext, intent);
      return true;
    }
    return false;
  }

  private void startLightbringerActivity(Intent intent) {
    try {
      Activity activity = (Activity) mContext;
      activity.startActivityForResult(intent, DialtactsActivity.ACTIVITY_REQUEST_CODE_LIGHTBRINGER);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(mContext, R.string.activity_not_available, Toast.LENGTH_SHORT).show();
    }
  }

  private DialerContact buildContact() {
    DialerContact.Builder contact = DialerContact.newBuilder();
    contact.setPhotoId(info.photoId);
    if (info.photoUri != null) {
      contact.setPhotoUri(info.photoUri.toString());
    }
    if (info.lookupUri != null) {
      contact.setContactUri(info.lookupUri.toString());
    }
    if (nameOrNumber != null) {
      contact.setNameOrNumber((String) nameOrNumber);
    }
    contact.setContactType(getContactType());
    contact.setNumber(number);
    /* second line of contact view. */
    if (!TextUtils.isEmpty(info.name)) {
      contact.setDisplayNumber(displayNumber);
    }
    /* phone number type (e.g. mobile) in second line of contact view */
    contact.setNumberLabel(numberType);

    /* third line of contact view. */
    String accountLabel = mCallLogCache.getAccountLabel(accountHandle);
    if (!TextUtils.isEmpty(accountLabel)) {
      SimDetails.Builder simDetails = SimDetails.newBuilder().setNetwork(accountLabel);
      simDetails.setColor(mCallLogCache.getAccountColor(accountHandle));
      contact.setSimDetails(simDetails.build());
    }
    return contact.build();
  }

  private void logCallLogAction(int id) {
    if (id == R.id.send_message_action) {
      Logger.get(mContext).logImpression(DialerImpression.Type.CALL_LOG_SEND_MESSAGE);
    } else if (id == R.id.add_to_existing_contact_action) {
      Logger.get(mContext).logImpression(DialerImpression.Type.CALL_LOG_ADD_TO_CONTACT);
      switch (hostUi) {
        case HostUi.CALL_HISTORY:
          Logger.get(mContext)
              .logImpression(DialerImpression.Type.ADD_TO_A_CONTACT_FROM_CALL_HISTORY);
          break;
        case HostUi.CALL_LOG:
          Logger.get(mContext).logImpression(DialerImpression.Type.ADD_TO_A_CONTACT_FROM_CALL_LOG);
          break;
        case HostUi.VOICEMAIL:
          Logger.get(mContext).logImpression(DialerImpression.Type.ADD_TO_A_CONTACT_FROM_VOICEMAIL);
          break;
        default:
          throw Assert.createIllegalStateFailException();
      }
    } else if (id == R.id.create_new_contact_action) {
      Logger.get(mContext).logImpression(DialerImpression.Type.CALL_LOG_CREATE_NEW_CONTACT);
      switch (hostUi) {
        case HostUi.CALL_HISTORY:
          Logger.get(mContext)
              .logImpression(DialerImpression.Type.CREATE_NEW_CONTACT_FROM_CALL_HISTORY);
          break;
        case HostUi.CALL_LOG:
          Logger.get(mContext)
              .logImpression(DialerImpression.Type.CREATE_NEW_CONTACT_FROM_CALL_LOG);
          break;
        case HostUi.VOICEMAIL:
          Logger.get(mContext)
              .logImpression(DialerImpression.Type.CREATE_NEW_CONTACT_FROM_VOICEMAIL);
          break;
        default:
          throw Assert.createIllegalStateFailException();
      }
    }
  }

  private void maybeShowBlockNumberMigrationDialog(BlockedNumbersMigrator.Listener listener) {
    if (!FilteredNumberCompat.maybeShowBlockNumberMigrationDialog(
        mContext, ((Activity) mContext).getFragmentManager(), listener)) {
      listener.onComplete();
    }
  }

  private void updateBlockReportActions(boolean isVoicemailNumber) {
    // Set block/spam actions.
    blockReportView.setVisibility(View.GONE);
    blockView.setVisibility(View.GONE);
    unblockView.setVisibility(View.GONE);
    reportNotSpamView.setVisibility(View.GONE);
    String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
    if (isVoicemailNumber
        || !FilteredNumbersUtil.canBlockNumber(mContext, e164Number, number)
        || !FilteredNumberCompat.canAttemptBlockOperations(mContext)) {
      return;
    }
    boolean isBlocked = blockId != null;
    if (isBlocked) {
      unblockView.setVisibility(View.VISIBLE);
    } else {
      if (isSpamFeatureEnabled) {
        if (isSpam) {
          blockView.setVisibility(View.VISIBLE);
          reportNotSpamView.setVisibility(View.VISIBLE);
        } else {
          blockReportView.setVisibility(View.VISIBLE);
        }
      } else {
        blockView.setVisibility(View.VISIBLE);
      }
    }
  }

  public void setDetailedPhoneDetails(CallDetailsEntries callDetailsEntries) {
    this.callDetailsEntries = callDetailsEntries;
  }

  @VisibleForTesting
  public CallDetailsEntries getDetailedPhoneDetails() {
    return callDetailsEntries;
  }

  @NonNull
  private Lightbringer getLightbringer() {
    return LightbringerComponent.get(mContext).getLightbringer();
  }

  @Override
  public void onCreateContextMenu(
      final ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    if (TextUtils.isEmpty(number)) {
      return;
    }

    if (callType == CallLog.Calls.VOICEMAIL_TYPE) {
      menu.setHeaderTitle(mContext.getResources().getText(R.string.voicemail));
    } else {
      menu.setHeaderTitle(
          PhoneNumberUtilsCompat.createTtsSpannable(
              BidiFormatter.getInstance().unicodeWrap(number, TextDirectionHeuristics.LTR)));
    }

    menu.add(
            ContextMenu.NONE,
            R.id.context_menu_copy_to_clipboard,
            ContextMenu.NONE,
            R.string.action_copy_number_text)
        .setOnMenuItemClickListener(this);

    // The edit number before call does not show up if any of the conditions apply:
    // 1) Number cannot be called
    // 2) Number is the voicemail number
    // 3) Number is a SIP address

    if (PhoneNumberHelper.canPlaceCallsTo(number, numberPresentation)
        && !mCallLogCache.isVoicemailNumber(accountHandle, number)
        && !PhoneNumberHelper.isSipNumber(number)) {
      menu.add(
              ContextMenu.NONE,
              R.id.context_menu_edit_before_call,
              ContextMenu.NONE,
              R.string.action_edit_number_before_call)
          .setOnMenuItemClickListener(this);
    }

    if (callType == CallLog.Calls.VOICEMAIL_TYPE
        && phoneCallDetailsViews.voicemailTranscriptionView.length() > 0) {
      menu.add(
              ContextMenu.NONE,
              R.id.context_menu_copy_transcript_to_clipboard,
              ContextMenu.NONE,
              R.string.copy_transcript_text)
          .setOnMenuItemClickListener(this);
    }

    String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
    boolean isVoicemailNumber = mCallLogCache.isVoicemailNumber(accountHandle, number);
    if (!isVoicemailNumber
        && FilteredNumbersUtil.canBlockNumber(mContext, e164Number, number)
        && FilteredNumberCompat.canAttemptBlockOperations(mContext)) {
      boolean isBlocked = blockId != null;
      if (isBlocked) {
        menu.add(
                ContextMenu.NONE,
                R.id.context_menu_unblock,
                ContextMenu.NONE,
                R.string.call_log_action_unblock_number)
            .setOnMenuItemClickListener(this);
      } else {
        if (isSpamFeatureEnabled) {
          if (isSpam) {
            menu.add(
                    ContextMenu.NONE,
                    R.id.context_menu_report_not_spam,
                    ContextMenu.NONE,
                    R.string.call_log_action_remove_spam)
                .setOnMenuItemClickListener(this);
            menu.add(
                    ContextMenu.NONE,
                    R.id.context_menu_block,
                    ContextMenu.NONE,
                    R.string.call_log_action_block_number)
                .setOnMenuItemClickListener(this);
          } else {
            menu.add(
                    ContextMenu.NONE,
                    R.id.context_menu_block_report_spam,
                    ContextMenu.NONE,
                    R.string.call_log_action_block_report_number)
                .setOnMenuItemClickListener(this);
          }
        } else {
          menu.add(
                  ContextMenu.NONE,
                  R.id.context_menu_block,
                  ContextMenu.NONE,
                  R.string.call_log_action_block_number)
              .setOnMenuItemClickListener(this);
        }
      }
    }

    Logger.get(mContext).logScreenView(ScreenEvent.Type.CALL_LOG_CONTEXT_MENU, (Activity) mContext);
  }

  /** Specifies where the view holder belongs. */
  @IntDef({HostUi.CALL_LOG, HostUi.CALL_HISTORY, HostUi.VOICEMAIL})
  @Retention(RetentionPolicy.SOURCE)
  private @interface HostUi {
    int CALL_LOG = 0;
    int CALL_HISTORY = 1;
    int VOICEMAIL = 2;
  }

  public interface OnClickListener {

    void onBlockReportSpam(
        String displayNumber,
        String number,
        String countryIso,
        int callType,
        ContactSource.Type contactSourceType);

    void onBlock(
        String displayNumber,
        String number,
        String countryIso,
        int callType,
        ContactSource.Type contactSourceType);

    void onUnblock(
        String displayNumber,
        String number,
        String countryIso,
        int callType,
        ContactSource.Type contactSourceType,
        boolean isSpam,
        Integer blockId);

    void onReportNotSpam(
        String displayNumber,
        String number,
        String countryIso,
        int callType,
        ContactSource.Type contactSourceType);
  }
}
