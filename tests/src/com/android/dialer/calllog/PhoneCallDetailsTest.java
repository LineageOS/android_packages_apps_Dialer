package com.android.dialer.calllog;

import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan;

import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.contacts.common.util.ContactDisplayUtils;

/**
 * Unit tests for {@link PhoneCallDetails}.
 */
public class PhoneCallDetailsTest extends AndroidTestCase {
    private static final String VIA_NUMBER = "+16505551212";
    private static final String PHONE_ACCOUNT_LABEL = "TEST";

    private Resources mResources;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResources = mContext.getResources();
    }

    @SmallTest
    public void testCreateAccountLabelDescription_NoViaNumberNoAccountLabel() {
        CharSequence result = PhoneCallDetails.createAccountLabelDescription(mResources, "","");
        assertEquals("", result);
    }

    @SmallTest
    public void testCreateAccountLabelDescription_ViaNumberAccountLabel() {
        String msg = mResources.getString(R.string.description_via_number_phone_account,
                PHONE_ACCOUNT_LABEL, VIA_NUMBER);
        CharSequence accountNumberLabel = ContactDisplayUtils.getTelephoneTtsSpannable(msg,
                VIA_NUMBER);
        CharSequence result = PhoneCallDetails.createAccountLabelDescription(mResources, VIA_NUMBER,
                PHONE_ACCOUNT_LABEL);
        assertEquals(accountNumberLabel.toString(), result.toString());
    }

    @SmallTest
    public void testCreateAccountLabelDescription_ViaNumber() {
        CharSequence viaNumberLabel = ContactDisplayUtils.getTtsSpannedPhoneNumber(mResources,
                R.string.description_via_number, VIA_NUMBER);
        CharSequence result = PhoneCallDetails.createAccountLabelDescription(mResources, VIA_NUMBER,
                "");
        assertEquals(viaNumberLabel.toString(), result.toString());
    }

    @SmallTest
    public void testCreateAccountLabelDescription_AccountLabel() {
        CharSequence accountLabel = TextUtils.expandTemplate(
                mResources.getString(R.string.description_phone_account), PHONE_ACCOUNT_LABEL);
        CharSequence result = PhoneCallDetails.createAccountLabelDescription(mResources, "",
                PHONE_ACCOUNT_LABEL);
        assertEquals(accountLabel, result);
    }
}
