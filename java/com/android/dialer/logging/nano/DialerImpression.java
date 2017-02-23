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

package com.android.dialer.logging.nano;

@SuppressWarnings("hiding")
public final class DialerImpression extends
    com.google.protobuf.nano.ExtendableMessageNano<DialerImpression> {

  // enum Type
  public interface Type {
    public static final int UNKNOWN_AOSP_EVENT_TYPE = 1000;
    public static final int APP_LAUNCHED = 1001;
    public static final int IN_CALL_SCREEN_TURN_ON_SPEAKERPHONE = 1002;
    public static final int IN_CALL_SCREEN_TURN_ON_WIRED_OR_EARPIECE = 1003;
    public static final int CALL_LOG_BLOCK_REPORT_SPAM = 1004;
    public static final int CALL_LOG_BLOCK_NUMBER = 1005;
    public static final int CALL_LOG_UNBLOCK_NUMBER = 1006;
    public static final int CALL_LOG_REPORT_AS_NOT_SPAM = 1007;
    public static final int DIALOG_ACTION_CONFIRM_NUMBER_NOT_SPAM = 1008;
    public static final int REPORT_AS_NOT_SPAM_VIA_UNBLOCK_NUMBER = 1009;
    public static final int DIALOG_ACTION_CONFIRM_NUMBER_SPAM_INDIRECTLY_VIA_BLOCK_NUMBER = 1010;
    public static final int REPORT_CALL_AS_SPAM_VIA_CALL_LOG_BLOCK_REPORT_SPAM_SENT_VIA_BLOCK_NUMBER_DIALOG = 1011;
    public static final int USER_ACTION_BLOCKED_NUMBER = 1012;
    public static final int USER_ACTION_UNBLOCKED_NUMBER = 1013;
    public static final int SPAM_AFTER_CALL_NOTIFICATION_BLOCK_NUMBER = 1014;
    public static final int SPAM_AFTER_CALL_NOTIFICATION_SHOW_SPAM_DIALOG = 1015;
    public static final int SPAM_AFTER_CALL_NOTIFICATION_SHOW_NON_SPAM_DIALOG = 1016;
    public static final int SPAM_AFTER_CALL_NOTIFICATION_ADD_TO_CONTACTS = 1019;
    public static final int SPAM_AFTER_CALL_NOTIFICATION_MARKED_NUMBER_AS_SPAM = 1020;
    public static final int SPAM_AFTER_CALL_NOTIFICATION_MARKED_NUMBER_AS_NOT_SPAM_AND_BLOCKED = 1021;
    public static final int SPAM_AFTER_CALL_NOTIFICATION_REPORT_NUMBER_AS_NOT_SPAM = 1022;
    public static final int SPAM_AFTER_CALL_NOTIFICATION_ON_DISMISS_SPAM_DIALOG = 1024;
    public static final int SPAM_AFTER_CALL_NOTIFICATION_ON_DISMISS_NON_SPAM_DIALOG = 1025;
    public static final int SPAM_NOTIFICATION_SERVICE_ACTION_MARK_NUMBER_AS_SPAM = 1026;
    public static final int SPAM_NOTIFICATION_SERVICE_ACTION_MARK_NUMBER_AS_NOT_SPAM = 1027;
    public static final int USER_PARTICIPATED_IN_A_CALL = 1028;
    public static final int INCOMING_SPAM_CALL = 1029;
    public static final int INCOMING_NON_SPAM_CALL = 1030;
    public static final int SPAM_NOTIFICATION_SHOWN_AFTER_THROTTLE = 1041;
    public static final int SPAM_NOTIFICATION_NOT_SHOWN_AFTER_THROTTLE = 1042;
    public static final int NON_SPAM_NOTIFICATION_SHOWN_AFTER_THROTTLE = 1043;
    public static final int NON_SPAM_NOTIFICATION_NOT_SHOWN_AFTER_THROTTLE = 1044;
    public static final int VOICEMAIL_ALERT_SET_PIN_SHOWN = 1045;
    public static final int VOICEMAIL_ALERT_SET_PIN_CLICKED = 1046;
    public static final int USER_DID_NOT_PARTICIPATE_IN_CALL = 1047;
    public static final int USER_DELETED_CALL_LOG_ITEM = 1048;
    public static final int CALL_LOG_SEND_MESSAGE = 1049;
    public static final int CALL_LOG_ADD_TO_CONTACT = 1050;
    public static final int CALL_LOG_CREATE_NEW_CONTACT = 1051;
    public static final int VOICEMAIL_DELETE_ENTRY = 1052;
    public static final int VOICEMAIL_EXPAND_ENTRY = 1053;
    public static final int VOICEMAIL_PLAY_AUDIO_DIRECTLY = 1054;
    public static final int VOICEMAIL_PLAY_AUDIO_AFTER_EXPANDING_ENTRY = 1055;
    public static final int REJECT_INCOMING_CALL_FROM_NOTIFICATION = 1056;
    public static final int REJECT_INCOMING_CALL_FROM_ANSWER_SCREEN = 1057;
    public static final int CALL_LOG_CONTEXT_MENU_BLOCK_REPORT_SPAM = 1058;
    public static final int CALL_LOG_CONTEXT_MENU_BLOCK_NUMBER = 1059;
    public static final int CALL_LOG_CONTEXT_MENU_UNBLOCK_NUMBER = 1060;
    public static final int CALL_LOG_CONTEXT_MENU_REPORT_AS_NOT_SPAM = 1061;
    public static final int NEW_CONTACT_OVERFLOW = 1062;
    public static final int NEW_CONTACT_FAB = 1063;
    public static final int VOICEMAIL_VVM3_TOS_SHOWN = 1064;
    public static final int VOICEMAIL_VVM3_TOS_ACCEPTED = 1065;
    public static final int VOICEMAIL_VVM3_TOS_DECLINED = 1066;
    public static final int VOICEMAIL_VVM3_TOS_DECLINE_CLICKED = 1067;
    public static final int VOICEMAIL_VVM3_TOS_DECLINE_CHANGE_PIN_SHOWN = 1068;
    public static final int STORAGE_PERMISSION_DISPLAYED = 1069;
    public static final int CAMERA_PERMISSION_DISPLAYED = 1074;
    public static final int STORAGE_PERMISSION_REQUESTED = 1070;
    public static final int CAMERA_PERMISSION_REQUESTED = 1075;
    public static final int STORAGE_PERMISSION_SETTINGS = 1071;
    public static final int CAMERA_PERMISSION_SETTINGS = 1076;
    public static final int STORAGE_PERMISSION_GRANTED = 1072;
    public static final int CAMERA_PERMISSION_GRANTED = 1077;
    public static final int STORAGE_PERMISSION_DENIED = 1073;
    public static final int CAMERA_PERMISSION_DENIED = 1078;
    public static final int VOICEMAIL_CONFIGURATION_STATE_CORRUPTION_DETECTED_FROM_ACTIVITY = 1079;
    public static final int VOICEMAIL_CONFIGURATION_STATE_CORRUPTION_DETECTED_FROM_NOTIFICATION = 1080;
    public static final int BACKUP_ON_BACKUP = 1081;
    public static final int BACKUP_ON_FULL_BACKUP = 1082;
    public static final int BACKUP_ON_BACKUP_DISABLED = 1083;
    public static final int BACKUP_VOICEMAIL_BACKED_UP = 1084;
    public static final int BACKUP_FULL_BACKED_UP = 1085;
    public static final int BACKUP_ON_BACKUP_JSON_EXCEPTION = 1086;
    public static final int BACKUP_ON_QUOTA_EXCEEDED = 1087;
    public static final int BACKUP_ON_RESTORE = 1088;
    public static final int BACKUP_RESTORED_FILE = 1089;
    public static final int BACKUP_RESTORED_VOICEMAIL = 1090;
    public static final int BACKUP_ON_RESTORE_FINISHED = 1091;
    public static final int BACKUP_ON_RESTORE_DISABLED = 1092;
    public static final int BACKUP_ON_RESTORE_JSON_EXCEPTION = 1093;
    public static final int BACKUP_ON_RESTORE_IO_EXCEPTION = 1094;
    public static final int BACKUP_MAX_VM_BACKUP_REACHED = 1095;
    public static final int EVENT_ANSWER_HINT_ACTIVATED = 1096;
    public static final int EVENT_ANSWER_HINT_DEACTIVATED = 1097;
    public static final int VVM_TAB_VISIBLE = 1098;
    public static final int VVM_SHARE_VISIBLE = 1099;
    public static final int VVM_SHARE_PRESSED = 1100;
    public static final int OUTGOING_VIDEO_CALL = 1101;
    public static final int INCOMING_VIDEO_CALL = 1102;
    public static final int USER_PARTICIPATED_IN_A_VIDEO_CALL = 1103;
    public static final int BACKUP_ON_RESTORE_VM_DUPLICATE_NOT_RESTORING = 1104;
    public static final int CALL_LOG_SHARE_AND_CALL = 1105;
    public static final int CALL_COMPOSER_ACTIVITY_PLACE_RCS_CALL = 1106;
    public static final int CALL_COMPOSER_ACTIVITY_SEND_AND_CALL_PRESSED_WHEN_SESSION_NOT_READY = 1107;
  }

  private static volatile DialerImpression[] _emptyArray;
  public static DialerImpression[] emptyArray() {
    // Lazily initializes the empty array
    if (_emptyArray == null) {
      synchronized (
          com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
        if (_emptyArray == null) {
          _emptyArray = new DialerImpression[0];
        }
      }
    }
    return _emptyArray;
  }

  // @@protoc_insertion_point(class_scope:com.android.dialer.logging.DialerImpression)

  public DialerImpression() {
    clear();
  }

  public DialerImpression clear() {
    unknownFieldData = null;
    cachedSize = -1;
    return this;
  }

  @Override
  public DialerImpression mergeFrom(
          com.google.protobuf.nano.CodedInputByteBufferNano input)
      throws java.io.IOException {
    while (true) {
      int tag = input.readTag();
      switch (tag) {
        case 0:
          return this;
        default: {
          if (!super.storeUnknownField(input, tag)) {
            return this;
          }
          break;
        }
      }
    }
  }

  public static DialerImpression parseFrom(byte[] data)
      throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
    return com.google.protobuf.nano.MessageNano.mergeFrom(new DialerImpression(), data);
  }

  public static DialerImpression parseFrom(
          com.google.protobuf.nano.CodedInputByteBufferNano input)
      throws java.io.IOException {
    return new DialerImpression().mergeFrom(input);
  }
}

