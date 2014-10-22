package com.android.incallui;

import android.content.Context;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;

import java.util.Arrays;

/**
 * Utility methods for contact and caller info related functionality
 */
public class CallerInfoUtils {

    private static final String TAG = CallerInfoUtils.class.getSimpleName();

    /** Define for not a special CNAP string */
    private static final int CNAP_SPECIAL_CASE_NO = -1;

    public CallerInfoUtils() {
    }

    private static final int QUERY_TOKEN = -1;

    /**
     * This is called to get caller info for a call. This will return a CallerInfo
     * object immediately based off information in the call, but
     * more information is returned to the OnQueryCompleteListener (which contains
     * information about the phone number label, user's name, etc).
     */
    public static CallerInfo getCallerInfoForCall(Context context, Call call,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener) {
        CallerInfo info = buildCallerInfo(context, call);

        // TODO: Have phoneapp send a Uri when it knows the contact that triggered this call.

        if (info.numberPresentation == TelecomManager.PRESENTATION_ALLOWED) {
            // Start the query with the number provided from the call.
            Log.d(TAG, "==> Actually starting CallerInfoAsyncQuery.startQuery()...");
            CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context, info, listener, call);
        }
        return info;
    }

    public static CallerInfo buildCallerInfo(Context context, Call call) {
        CallerInfo info = new CallerInfo();

        // Store CNAP information retrieved from the Connection (we want to do this
        // here regardless of whether the number is empty or not).
        info.cnapName = call.getCnapName();
        info.name = info.cnapName;
        info.numberPresentation = call.getNumberPresentation();
        info.namePresentation = call.getCnapNamePresentation();

        String number = call.getNumber();
        if (!TextUtils.isEmpty(number)) {
            final String[] numbers = number.split("&");
            number = numbers[0];
            if (numbers.length > 1) {
                info.forwardingNumber = numbers[1];
            }

            number = modifyForSpecialCnapCases(context, info, number, info.numberPresentation);
            info.phoneNumber = number;
        }

        // Because the InCallUI is immediately launched before the call is connected, occasionally
        // a voicemail call will be passed to InCallUI as a "voicemail:" URI without a number.
        // This call should still be handled as a voicemail call.
        if (call.getHandle() != null &&
                PhoneAccount.SCHEME_VOICEMAIL.equals(call.getHandle().getScheme())) {
            info.markAsVoiceMail(context);
        }

        return info;
    }

    /**
     * Handles certain "corner cases" for CNAP. When we receive weird phone numbers
     * from the network to indicate different number presentations, convert them to
     * expected number and presentation values within the CallerInfo object.
     * @param number number we use to verify if we are in a corner case
     * @param presentation presentation value used to verify if we are in a corner case
     * @return the new String that should be used for the phone number
     */
    /* package */static String modifyForSpecialCnapCases(Context context, CallerInfo ci,
            String number, int presentation) {
        // Obviously we return number if ci == null, but still return number if
        // number == null, because in these cases the correct string will still be
        // displayed/logged after this function returns based on the presentation value.
        if (ci == null || number == null) return number;

        Log.d(TAG, "modifyForSpecialCnapCases: initially, number="
                + toLogSafePhoneNumber(number)
                + ", presentation=" + presentation + " ci " + ci);

        // "ABSENT NUMBER" is a possible value we could get from the network as the
        // phone number, so if this happens, change it to "Unknown" in the CallerInfo
        // and fix the presentation to be the same.
        final String[] absentNumberValues =
                context.getResources().getStringArray(R.array.absent_num);
        if (Arrays.asList(absentNumberValues).contains(number)
                && presentation == TelecomManager.PRESENTATION_ALLOWED) {
            number = context.getString(R.string.unknown);
            ci.numberPresentation = TelecomManager.PRESENTATION_UNKNOWN;
        }

        // Check for other special "corner cases" for CNAP and fix them similarly. Corner
        // cases only apply if we received an allowed presentation from the network, so check
        // if we think we have an allowed presentation, or if the CallerInfo presentation doesn't
        // match the presentation passed in for verification (meaning we changed it previously
        // because it's a corner case and we're being called from a different entry point).
        if (ci.numberPresentation == TelecomManager.PRESENTATION_ALLOWED
                || (ci.numberPresentation != presentation
                        && presentation == TelecomManager.PRESENTATION_ALLOWED)) {
            // For all special strings, change number & numberPrentation.
            if (isCnapSpecialCaseRestricted(number)) {
                number = context.getString(R.string.private_num);
                ci.numberPresentation = TelecomManager.PRESENTATION_RESTRICTED;
            } else if (isCnapSpecialCaseUnknown(number)) {
                number = context.getString(R.string.unknown);
                ci.numberPresentation = TelecomManager.PRESENTATION_UNKNOWN;
            }
            Log.d(TAG, "SpecialCnap: number=" + toLogSafePhoneNumber(number)
                    + "; presentation now=" + ci.numberPresentation);
        }
        Log.d(TAG, "modifyForSpecialCnapCases: returning number string="
                + toLogSafePhoneNumber(number));
        return number;
    }

    private static boolean isCnapSpecialCaseRestricted(String n) {
        return n.equals("PRIVATE") || n.equals("P") || n.equals("RES");
    }

    private static boolean isCnapSpecialCaseUnknown(String n) {
        return n.equals("UNAVAILABLE") || n.equals("UNKNOWN") || n.equals("UNA") || n.equals("U");
    }

    /* package */static String toLogSafePhoneNumber(String number) {
        // For unknown number, log empty string.
        if (number == null) {
            return "";
        }

        // Todo: Figure out an equivalent for VDBG
        if (false) {
            // When VDBG is true we emit PII.
            return number;
        }

        // Do exactly same thing as Uri#toSafeString() does, which will enable us to compare
        // sanitized phone numbers.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c == '-' || c == '@' || c == '.' || c == '&') {
                builder.append(c);
            } else {
                builder.append('x');
            }
        }
        return builder.toString();
    }

    /**
     * Send a notification using a {@link ContactLoader} to inform the sync adapter that we are
     * viewing a particular contact, so that it can download the high-res photo.
     */
    public static void sendViewNotification(Context context, Uri contactUri) {
        final ContactLoader loader = new ContactLoader(context, contactUri,
                true /* postViewNotification */);
        loader.registerListener(0, new OnLoadCompleteListener<Contact>() {
            @Override
            public void onLoadComplete(
                    Loader<Contact> loader, Contact contact) {
                try {
                    loader.reset();
                } catch (RuntimeException e) {
                    Log.e(TAG, "Error resetting loader", e);
                }
            }
        });
        loader.startLoading();
    }
}
