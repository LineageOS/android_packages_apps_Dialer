/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.contacts.hiresphoto;

import android.net.Uri;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Requests the contacts sync adapter to load a high resolution photo for the contact, typically
 * when we will try to show the contact in a larger view (favorites, incall UI, etc.). If a high
 * resolution photo is synced, the uri will be notified.
 */
public interface HighResolutionPhotoRequester {

  ListenableFuture<Void> request(Uri contactUri);
}
