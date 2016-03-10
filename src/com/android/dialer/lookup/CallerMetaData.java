package com.android.dialer.lookup;

/**
 * Common strings used in conjunction with a CallerInfoService
 */
public class CallerMetaData {

    public static final String SPAM_COUNT = "CALLER_META_DATA_SPAM_COUNT";
    // tag for storing concise location of a caller (eg: [<city>, <Country>])
    public static final String SUCCINCT_LOCATION = "CALLER_META_DATA_SUCCINCT_LOCATION";
    public static final String INFO_PROVIDER = "CALLER_META_DATA_INFO_PROVIDER";
    public static final String PHOTO_URL = "CALLER_META_DATA_PHOTO_URL";

    // mimetype for the name of service that helped identify the caller
    public static final String MIMETYPE_SERVICE_IDENTIFIER =
            "com.cyanogen.ambient/callerinfoservice/identifier";
}
