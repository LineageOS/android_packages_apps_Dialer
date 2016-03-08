package com.android.dialer.list;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ScaleDrawable;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.dialer.util.ImageUtils;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.dialer.R;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * {@link PhoneNumberListAdapter} with the following added shortcuts, that are displayed as list
 * items:
 * 1) Directly calling the phone number query
 * 2) Adding the phone number query to a contact
 *
 * These shortcuts can be enabled or disabled to toggle whether or not they show up in the
 * list.
 */
public class DialerPhoneNumberListAdapter extends PhoneNumberListAdapter {
    private final static String TAG = DialerPhoneNumberListAdapter.class.getSimpleName();

    private String mFormattedQueryString;
    private String mCountryIso;

    public final static int SHORTCUT_INVALID = -1;
    public final static int SHORTCUT_DIRECT_CALL = 0;
    public final static int SHORTCUT_CREATE_NEW_CONTACT = 1;
    public final static int SHORTCUT_ADD_TO_EXISTING_CONTACT = 2;
    public final static int SHORTCUT_SEND_SMS_MESSAGE = 3;
    public final static int SHORTCUT_MAKE_VIDEO_CALL = 4;

    // this is set to 99 to avoid shortcut conflicts
    public final static int SHORTCUT_PROVIDER_ACTION = 99;

    public final static int HARDCODED_SHORTCUT_COUNT = 5;
    public static int SHORTCUT_COUNT = HARDCODED_SHORTCUT_COUNT;

    private boolean[] mShortcutEnabled = new boolean[SHORTCUT_COUNT];

    private int mShortcutCurrent = SHORTCUT_INVALID;

    private final BidiFormatter mBidiFormatter = BidiFormatter.getInstance();

    private searchMethodClicked mSearchMethodListener = null;

    private CallMethodInfo mCurrentCallMethodInfo;

    public ArrayList<CallMethodInfo> mProviders = new ArrayList<CallMethodInfo>();

    public DialerPhoneNumberListAdapter(Context context) {
        super(context);

        mCountryIso = GeoUtil.getCurrentCountryIso(context);

    }

    public void setAvailableCallMethods(HashMap<ComponentName, CallMethodInfo> callMethods) {
        SHORTCUT_COUNT = HARDCODED_SHORTCUT_COUNT + callMethods.size();
        mShortcutEnabled = new boolean[SHORTCUT_COUNT];
        mProviders.clear();
        mProviders.addAll(callMethods.values());

        notifyDataSetChanged();
    }

    public void setSearchListner(searchMethodClicked clickedListener) {
        mSearchMethodListener = clickedListener;
    }

    @Override
    public int getCount() {
        return super.getCount() + getShortcutCount();
    }

    /**
     * @return The number of enabled shortcuts. Ranges from 0 to a maximum of SHORTCUT_COUNT
     */
    public int getShortcutCount() {
        int count = 0;
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            if (mShortcutEnabled[i]) count++;
        }
        return count;
    }

    public void disableAllShortcuts() {
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            mShortcutEnabled[i] = false;
        }
    }

    @Override
    public int getItemViewType(int position) {
        final int shortcut = getShortcutTypeFromPosition(position, true);
        if (shortcut >= 0) {
            // shortcutPos should always range from 1 to SHORTCUT_COUNT
            return super.getViewTypeCount() + shortcut;
        } else {
            return super.getItemViewType(position);
        }
    }

    @Override
    public int getViewTypeCount() {
        // Number of item view types in the super implementation + 2 for the 2 new shortcuts
        return super.getViewTypeCount() + mShortcutEnabled.length;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int shortcutType = getShortcutTypeFromPosition(position, false);
        if (shortcutType >= 0) {
            if (convertView != null) {
                assignShortcutToView((ContactListItemView) convertView, shortcutType, position);
                return convertView;
            } else {
                final ContactListItemView v = new ContactListItemView(getContext(), null);
                assignShortcutToView(v, shortcutType, position);
                return v;
            }
        } else {
            return super.getView(position, convertView, parent);
        }
    }

    /**
     * @param position The position of the item
     * @return The enabled shortcut type matching the given position if the item is a
     * shortcut, -1 otherwise
     */
    public int getShortcutTypeFromPosition(int position, boolean truePos) {
        int shortcutCount = position - super.getCount();
        if (shortcutCount >= 0) {
            // Iterate through the array of shortcuts, looking only for shortcuts where
            // mShortcutEnabled[i] is true
            for (int i = 0; shortcutCount >= 0 && i < mShortcutEnabled.length; i++) {
                if (mShortcutEnabled[i]) {
                    shortcutCount--;
                    if (shortcutCount < 0) {
                        if (!truePos) {
                            if (i >= HARDCODED_SHORTCUT_COUNT
                                    && ((SHORTCUT_COUNT - i - 1) < mProviders.size())) {
                                return SHORTCUT_PROVIDER_ACTION;
                            } else {
                                return i;
                            }
                        } else {
                            return i;
                        }
                    }
                }
            }
            throw new IllegalArgumentException("Invalid position - greater than cursor count "
                    + " but not a shortcut.");
        }

        int returningShortcut = mShortcutCurrent;
        mShortcutCurrent = SHORTCUT_INVALID;

        return returningShortcut;
    }

    public ArrayList<CallMethodInfo> getProviders() {
        return mProviders;
    }

    @Override
    public boolean isEmpty() {
        return getShortcutCount() == 0 && super.isEmpty();
    }

    @Override
    public boolean isEnabled(int position) {
        final int shortcutType = getShortcutTypeFromPosition(position, false);
        if (shortcutType >= 0) {
            return true;
        } else {
            return super.isEnabled(position);
        }
    }

    @Override
    public String getLabelType(Cursor c, int type) {
        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) {
            final String providerLabel = c.getString(PhoneNumberListAdapter.PhoneQuery.PHONE_MIME_TYPE);
            CallMethodInfo cmi = CallMethodHelper.getMethodForMimeType(providerLabel, false);
            if (cmi != null) {
                return cmi.mName;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void assignShortcutToView(ContactListItemView v, int shortcutType, int position) {
        final CharSequence text;
        final int drawableId;
        final Drawable drawableRaw;
        final Resources resources = getContext().getResources();
        final String number = getFormattedQueryString();
        switch (shortcutType) {
            case SHORTCUT_DIRECT_CALL:
                text = resources.getString(
                        R.string.search_shortcut_call_number,
                        mBidiFormatter.unicodeWrap(number, TextDirectionHeuristics.LTR));

                CallMethodInfo ccm = getCurrentCallMethod();
                if (ccm != null && ccm.mIsInCallProvider) {
                    drawableId = 0;
                    drawableRaw = ImageUtils.scaleDrawable(ccm.mBrandIcon, 0.5f);
                } else {
                    drawableId = R.drawable.ic_search_phone;
                    drawableRaw = null;
                }
                break;
            case SHORTCUT_CREATE_NEW_CONTACT:
                text = resources.getString(R.string.search_shortcut_create_new_contact);
                drawableId = R.drawable.ic_search_add_contact;
                drawableRaw = null;
                break;
            case SHORTCUT_ADD_TO_EXISTING_CONTACT:
                text = resources.getString(R.string.search_shortcut_add_to_contact);
                drawableId = R.drawable.ic_person_24dp;
                drawableRaw = null;
                break;
            case SHORTCUT_SEND_SMS_MESSAGE:
                text = resources.getString(R.string.search_shortcut_send_sms_message);
                drawableId = R.drawable.ic_message_24dp;
                drawableRaw = null;
                break;
            case SHORTCUT_MAKE_VIDEO_CALL:
                text = resources.getString(R.string.search_shortcut_make_video_call);
                drawableId = R.drawable.ic_videocam;
                drawableRaw = null;
                break;
            case SHORTCUT_PROVIDER_ACTION:
                // This gives us the original "shortcut type" of the item
                int truePosition = getShortcutTypeFromPosition(position, true);

                // subtract our original "type" from the count to get it's item relation to our
                // mProvider accounting for position zero.
                int index = SHORTCUT_COUNT - truePosition - 1;

                CallMethodInfo cmi = mProviders.get(index);
                if (cmi.mIsAuthenticated) {
                    text = resources.getString(R.string.search_shortcut_call_using, cmi.mName);
                } else {
                    text = resources.getString(R.string.sign_in_hint_text, cmi.mName);
                }
                drawableId = 0;
                drawableRaw = ImageUtils.scaleDrawable(cmi.mBrandIcon, 0.5f);
                break;
            default:
                throw new IllegalArgumentException("Invalid shortcut type");
        }
        if (drawableRaw != null) {
            v.setDrawable(drawableRaw);
        } else {
            v.setDrawableResource(drawableId);
        }
        v.setDisplayName(text);
        v.setPhotoPosition(super.getPhotoPosition());
        v.setAdjustSelectionBoundsEnabled(false);
    }

    public interface searchMethodClicked {
        void onItemClick(int position, long id);
    }

    /**
     * @return True if the shortcut state (disabled vs enabled) was changed by this operation
     */
    public boolean setShortcutEnabled(int shortcutType, boolean visible) {
        final boolean changed = mShortcutEnabled[shortcutType] != visible;
        mShortcutEnabled[shortcutType] = visible;
        return changed;
    }

    public String getFormattedQueryString() {
        if (PhoneNumberHelper.isUriNumber(getQueryString()) || mFormattedQueryString == null) {
            // Return unnormalized address. Either for SIP or mFormatedQueryString not being
            // available.
            return getQueryString();
        }
        return mFormattedQueryString;
    }

    @Override
    public void setQueryString(String queryString) {
        final boolean showNumberShortcuts = !TextUtils.isEmpty(getFormattedQueryString())
                && hasDigitsInQueryString();
        boolean changed = false;
        changed |= setShortcutEnabled(SHORTCUT_DIRECT_CALL,
                showNumberShortcuts || PhoneNumberHelper.isUriNumber(queryString));
        changed |= setShortcutEnabled(SHORTCUT_CREATE_NEW_CONTACT, showNumberShortcuts);
        changed |= setShortcutEnabled(SHORTCUT_ADD_TO_EXISTING_CONTACT, showNumberShortcuts);
        changed |= setShortcutEnabled(SHORTCUT_SEND_SMS_MESSAGE, showNumberShortcuts);
        changed |= setShortcutEnabled(SHORTCUT_MAKE_VIDEO_CALL,
                showNumberShortcuts && CallUtil.isVideoEnabled(getContext()));

        // Loop through available providers and enable or disable them in the quickactions depending
        // on if they are selected in the spinner.
        for (CallMethodInfo cmi : mProviders) {
            int index = HARDCODED_SHORTCUT_COUNT + mProviders.size() - mProviders.indexOf(cmi) - 1;
            changed |= setShortcutEnabled(index, showNumberShortcuts &&
                    !cmi.equals(getCurrentCallMethod()));
        }

        if (changed) {
            notifyDataSetChanged();
        }

        mFormattedQueryString = PhoneNumberUtils.formatNumber(
                PhoneNumberUtils.normalizeNumber(queryString), mCountryIso);
        super.setQueryString(queryString);
    }

    /**
     * Whether there is at least one digit in the query string.
     */
    private boolean hasDigitsInQueryString() {
        String queryString = getQueryString();
        if (queryString == null) {
            return false;
        }
        int length = queryString.length();
        for (int i = 0; i < length; i++) {
            if (Character.isDigit(queryString.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public void setCurrentCallMethod(CallMethodInfo cmi) {
        mCurrentCallMethodInfo = cmi;
    }

    public CallMethodInfo getCurrentCallMethod() {
        return mCurrentCallMethodInfo;
    }
}
