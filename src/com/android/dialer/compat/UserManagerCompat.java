package com.android.dialer.compat;

import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.contacts.common.compat.CompatUtils;

/**
 * Compatibility class for {@link UserManager}.
 */
public class UserManagerCompat {
    /**
     * A user id constant to indicate the "system" user of the device. Copied from
     * {@link UserHandle}.
     */
    private static final int USER_SYSTEM = 0;
    /**
     * Range of uids allocated for a user.
     */
    private static final int PER_USER_RANGE = 100000;

    /**
     * Used to check if this process is running under the system user. The system user is the
     * initial user that is implicitly created on first boot and hosts most of the system services.
     *
     * @return whether this process is running under the system user.
     */
    public static boolean isSystemUser(UserManager userManager) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return userManager.isSystemUser();
        }
        // Adapted from {@link UserManager} and {@link UserHandle}.
        return (Process.myUid() / PER_USER_RANGE) == USER_SYSTEM;
    }
}
