package com.android.dialer.lookup;

import android.net.Uri;
import android.provider.ContactsContract;

public class DirectoryId {

    // default contacts directory
    public static final long DEFAULT = ContactsContract.Directory.DEFAULT;

    // id for a non existant directory
    public static final long NULL = Long.MAX_VALUE;

    // id for nearby forward lookup results (not a real directory)
    public static final long NEARBY = NULL - 1;

    // id for people forward lookup results (not a real directory)
    public static final long PEOPLE = NULL - 2;

    public static boolean isFakeDirectory(long directory) {
        return directory == NULL || directory == NEARBY || directory == PEOPLE;
    }

    public static long fromUri(Uri lookupUri) {
        long directory = DirectoryId.DEFAULT;
        if (lookupUri != null) {
            String dqp =
                    lookupUri.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY);
            if (dqp != null) {
                directory = Long.valueOf(dqp);
            }
        }
        return directory;
    }
}
