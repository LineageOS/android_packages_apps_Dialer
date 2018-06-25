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

package com.android.dialer.simulator.service;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import com.android.dialer.simulator.impl.SimulatorMainPortal;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A secured android service that gives clients simulator api access through binder if clients do
 * have registered certificates.
 */
public class SimulatorService extends Service {

  private static final String POPULATE_DATABASE = "Populate database";
  private static final String CLEAN_DATABASE = "Clean database";
  private static final String ENABLE_SIMULATOR_MODE = "Enable simulator mode";
  private static final String DISABLE_SIMULATOR_MODE = "Disable simulator mode";
  private static final String VOICECALL = "VoiceCall";
  private static final String NOTIFICATIONS = "Notifications";
  private static final String CUSTOMIZED_INCOMING_CALL = "Customized incoming call";
  private static final String CUSTOMIZED_OUTGOING_CALL = "Customized outgoing call";
  private static final String INCOMING_ENRICHED_CALL = "Incoming enriched call";
  private static final String OUTGOING_ENRICHED_CALL = "Outgoing enriched call";
  private static final String MISSED_CALL = "Missed calls (few)";

  // Certificates that used for checking whether a client is a trusted client.
  // To get a hashed certificate
  private ImmutableList<String> certificates;

  private SimulatorMainPortal simulatorMainPortal;

  /**
   * The implementation of {@link ISimulatorService} that contains logic for clients to call
   * simulator api.
   */
  private final ISimulatorService.Stub binder =
      new ISimulatorService.Stub() {

        @Override
        public void makeIncomingCall(String callerId, int presentation) {
          doSecurityCheck(
              () -> {
                simulatorMainPortal.setCallerId(callerId);
                simulatorMainPortal.setPresentation(presentation);
                simulatorMainPortal.execute(new String[] {VOICECALL, CUSTOMIZED_INCOMING_CALL});
              });
        }

        @Override
        public void makeOutgoingCall(String callerId, int presentation) {
          doSecurityCheck(
              () -> {
                simulatorMainPortal.setCallerId(callerId);
                simulatorMainPortal.setPresentation(presentation);
                simulatorMainPortal.execute(new String[] {VOICECALL, CUSTOMIZED_OUTGOING_CALL});
              });
        }

        @Override
        public void populateDataBase() throws RemoteException {
          doSecurityCheck(
              () -> {
                simulatorMainPortal.execute(new String[] {POPULATE_DATABASE});
              });
        }

        @Override
        public void cleanDataBase() throws RemoteException {
          doSecurityCheck(
              () -> {
                simulatorMainPortal.execute(new String[] {CLEAN_DATABASE});
              });
        }

        @Override
        public void enableSimulatorMode() throws RemoteException {
          doSecurityCheck(
              () -> {
                simulatorMainPortal.execute(new String[] {ENABLE_SIMULATOR_MODE});
              });
        }

        @Override
        public void disableSimulatorMode() throws RemoteException {
          doSecurityCheck(
              () -> {
                simulatorMainPortal.execute(new String[] {DISABLE_SIMULATOR_MODE});
              });
        }

        @Override
        public void makeIncomingEnrichedCall() throws RemoteException {
          doSecurityCheck(
              () -> {
                simulatorMainPortal.execute(new String[] {VOICECALL, INCOMING_ENRICHED_CALL});
              });
        }

        @Override
        public void makeOutgoingEnrichedCall() throws RemoteException {
          doSecurityCheck(
              () -> {
                simulatorMainPortal.execute(new String[] {VOICECALL, OUTGOING_ENRICHED_CALL});
              });
        }

        @Override
        public void populateMissedCall(int num) throws RemoteException {
          doSecurityCheck(
              () -> {
                simulatorMainPortal.setMissedCallNum(num);
                simulatorMainPortal.execute(new String[] {NOTIFICATIONS, MISSED_CALL});
              });
        }

        private void doSecurityCheck(Runnable runnable) {
          if (!hasAccessToService()) {
            throw new RuntimeException("Client doesn't have access to Simulator service!");
          }
          runnable.run();
        }
      };

  /** Sets SimulatorMainPortal instance for SimulatorService. */
  public void setSimulatorMainPortal(SimulatorMainPortal simulatorMainPortal) {
    this.simulatorMainPortal = simulatorMainPortal;
  }

  /** Sets immutable CertificatesList for SimulatorService. */
  public void setCertificatesList(ImmutableList<String> certificates) {
    this.certificates = certificates;
  }

  private boolean hasAccessToService() {
    int clientPid = Binder.getCallingPid();
    if (clientPid == Process.myPid()) {
      throw new RuntimeException("Client and service have the same PID!");
    }
    Optional<String> packageName = getPackageNameForPid(clientPid);
    if (packageName.isPresent()) {
      try {
        PackageInfo packageInfo =
            getPackageManager().getPackageInfo(packageName.get(), PackageManager.GET_SIGNATURES);
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        if (packageInfo.signatures.length != 1) {
          throw new NotOnlyOneSignatureException("The client has more than one signature!");
        }
        Signature signature = packageInfo.signatures[0];
        return isCertificateValid(messageDigest.digest(signature.toByteArray()), this.certificates);
      } catch (NameNotFoundException | NoSuchAlgorithmException | NotOnlyOneSignatureException e) {
        throw new RuntimeException(e);
      }
    }
    return false;
  }

  private Optional<String> getPackageNameForPid(int pid) {
    ActivityManager activityManager =
        (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
    for (RunningAppProcessInfo processInfo : activityManager.getRunningAppProcesses()) {
      if (processInfo.pid == pid) {
        return Optional.of(processInfo.processName);
      }
    }
    return Optional.absent();
  }

  private static boolean isCertificateValid(
      byte[] clientCerfificate, ImmutableList<String> certificates) {
    for (String certificate : certificates) {
      if (certificate.equals(bytesToHexString(clientCerfificate))) {
        return true;
      }
    }
    return false;
  }

  private static String bytesToHexString(byte[] in) {
    final StringBuilder builder = new StringBuilder();
    for (byte b : in) {
      builder.append(String.format("%02X", b));
    }
    return builder.toString();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  private static class NotOnlyOneSignatureException extends Exception {
    public NotOnlyOneSignatureException(String desc) {
      super(desc);
    }
  }
}
