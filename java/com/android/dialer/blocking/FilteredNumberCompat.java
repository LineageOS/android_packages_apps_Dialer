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

package com.android.dialer.blocking;

import android.content.Context;
import android.content.Intent;
import android.provider.BlockedNumberContract;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.telecom.TelecomManager;
import com.android.dialer.common.LogUtil;
import com.android.dialer.telecom.TelecomUtil;
import java.util.Objects;

/**
 * Compatibility class to encapsulate logic to switch between call blocking using {@link
 * com.android.dialer.database.FilteredNumberContract} and using {@link
 * android.provider.BlockedNumberContract}. This class should be used rather than explicitly
 * referencing columns from either contract class in situations where both blocking solutions may be
 * used.
 */
@Deprecated
public class FilteredNumberCompat {


}
