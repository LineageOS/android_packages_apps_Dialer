/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.incallui;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import com.android.services.telephony.common.ICallMonitorService;

/**
 * Service used to listen for call state changes.
 */
public class CallMonitorService extends Service {

     @Override
     public void onCreate() {
         super.onCreate();
     }

     @Override
     public void onDestroy() {
     }

     @Override
     public IBinder onBind(Intent intent) {
         return mBinder;
     }

     private final ICallMonitorService.Stub mBinder = new ICallMonitorService.Stub() {
         public void onIncomingCall(int callId) {
             showAlert("New Call came in: " + callId);
         }
     };

     private void showAlert(String message) {
         Context context = getApplicationContext();
         int duration = Toast.LENGTH_SHORT;

         Toast.makeText(context, message, duration).show();
     }
 }
