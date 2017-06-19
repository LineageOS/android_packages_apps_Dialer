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

package com.android.voicemail.impl.protocol;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Network;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.util.ArrayMap;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.voicemail.impl.ActivationTask;
import com.android.voicemail.impl.Assert;
import com.android.voicemail.impl.OmtpEvents;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.sync.VvmNetworkRequest;
import com.android.voicemail.impl.sync.VvmNetworkRequest.NetworkWrapper;
import com.android.voicemail.impl.sync.VvmNetworkRequest.RequestFailedException;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Class to subscribe to basic VVM3 visual voicemail, for example, Verizon. Subscription is required
 * when the user is unprovisioned. This could happen when the user is on a legacy service, or
 * switched over from devices that used other type of visual voicemail.
 *
 * <p>The STATUS SMS will come with a URL to the voicemail management gateway. From it we can find
 * the self provisioning gateway URL that we can modify voicemail services.
 *
 * <p>A request to the self provisioning gateway to activate basic visual voicemail will return us
 * with a web page. If the user hasn't subscribe to it yet it will contain a link to confirm the
 * subscription. This link should be clicked through cellular network, and have cookies enabled.
 *
 * <p>After the process is completed, the carrier should send us another STATUS SMS with a new or
 * ready user.
 */
@TargetApi(VERSION_CODES.O)
public class Vvm3Subscriber {

  private static final String TAG = "Vvm3Subscriber";

  private static final String OPERATION_GET_SPG_URL = "retrieveSPGURL";
  private static final String SPG_URL_TAG = "spgurl";
  private static final String TRANSACTION_ID_TAG = "transactionid";
  //language=XML
  private static final String VMG_XML_REQUEST_FORMAT =
      ""
          + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
          + "<VMGVVMRequest>"
          + "  <MessageHeader>"
          + "    <transactionid>%1$s</transactionid>"
          + "  </MessageHeader>"
          + "  <MessageBody>"
          + "    <mdn>%2$s</mdn>"
          + "    <operation>%3$s</operation>"
          + "    <source>Device</source>"
          + "    <devicemodel>%4$s</devicemodel>"
          + "  </MessageBody>"
          + "</VMGVVMRequest>";

  static final String VMG_URL_KEY = "vmg_url";

  // Self provisioning POST key/values. VVM3 API 2.1.0 12.3
  private static final String SPG_VZW_MDN_PARAM = "VZW_MDN";
  private static final String SPG_VZW_SERVICE_PARAM = "VZW_SERVICE";
  private static final String SPG_VZW_SERVICE_BASIC = "BVVM";
  private static final String SPG_DEVICE_MODEL_PARAM = "DEVICE_MODEL";
  // Value for all android device
  private static final String SPG_DEVICE_MODEL_ANDROID = "DROID_4G";
  private static final String SPG_APP_TOKEN_PARAM = "APP_TOKEN";
  private static final String SPG_APP_TOKEN = "q8e3t5u2o1";
  private static final String SPG_LANGUAGE_PARAM = "SPG_LANGUAGE_PARAM";
  private static final String SPG_LANGUAGE_EN = "ENGLISH";

  @VisibleForTesting
  static final String VVM3_SUBSCRIBE_LINK_PATTERNS_JSON_ARRAY =
      "vvm3_subscribe_link_pattern_json_array";

  private static final String VVM3_SUBSCRIBE_LINK_DEFAULT_PATTERNS =
      "["
          + "\"(?i)Subscribe to Basic Visual Voice Mail\","
          + "\"(?i)Subscribe to Basic Visual Voicemail\""
          + "]";

  private static final int REQUEST_TIMEOUT_SECONDS = 30;

  private final ActivationTask mTask;
  private final PhoneAccountHandle mHandle;
  private final OmtpVvmCarrierConfigHelper mHelper;
  private final VoicemailStatus.Editor mStatus;
  private final Bundle mData;

  private final String mNumber;

  private RequestQueue mRequestQueue;

  @VisibleForTesting
  static class ProvisioningException extends Exception {

    public ProvisioningException(String message) {
      super(message);
    }
  }

  static {
    // Set the default cookie handler to retain session data for the self provisioning gateway.
    // Note; this is not ideal as it is application-wide, and can easily get clobbered.
    // But it seems to be the preferred way to manage cookie for HttpURLConnection, and manually
    // managing cookies will greatly increase complexity.
    CookieManager cookieManager = new CookieManager();
    CookieHandler.setDefault(cookieManager);
  }

  @WorkerThread
  public Vvm3Subscriber(
      ActivationTask task,
      PhoneAccountHandle handle,
      OmtpVvmCarrierConfigHelper helper,
      VoicemailStatus.Editor status,
      Bundle data) {
    Assert.isNotMainThread();
    mTask = task;
    mHandle = handle;
    mHelper = helper;
    mStatus = status;
    mData = data;

    // Assuming getLine1Number() will work with VVM3. For unprovisioned users the IMAP username
    // is not included in the status SMS, thus no other way to get the current phone number.
    mNumber =
        mHelper
            .getContext()
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(mHandle)
            .getLine1Number();
  }

  @WorkerThread
  public void subscribe() {
    Assert.isNotMainThread();
    // Cellular data is required to subscribe.
    // processSubscription() is called after network is available.
    VvmLog.i(TAG, "Subscribing");

    try (NetworkWrapper wrapper = VvmNetworkRequest.getNetwork(mHelper, mHandle, mStatus)) {
      Network network = wrapper.get();
      VvmLog.d(TAG, "provisioning: network available");
      mRequestQueue =
          Volley.newRequestQueue(mHelper.getContext(), new NetworkSpecifiedHurlStack(network));
      processSubscription();
    } catch (RequestFailedException e) {
      mHelper.handleEvent(mStatus, OmtpEvents.VVM3_VMG_CONNECTION_FAILED);
      mTask.fail();
    }
  }

  private void processSubscription() {
    try {
      String gatewayUrl = getSelfProvisioningGateway();
      String selfProvisionResponse = getSelfProvisionResponse(gatewayUrl);
      String subscribeLink =
          findSubscribeLink(getSubscribeLinkPatterns(mHelper.getContext()), selfProvisionResponse);
      clickSubscribeLink(subscribeLink);
    } catch (ProvisioningException e) {
      VvmLog.e(TAG, e.toString());
      mTask.fail();
    }
  }

  /** Get the URL to perform self-provisioning from the voicemail management gateway. */
  private String getSelfProvisioningGateway() throws ProvisioningException {
    VvmLog.i(TAG, "retrieving SPG URL");
    String response = vvm3XmlRequest(OPERATION_GET_SPG_URL);
    return extractText(response, SPG_URL_TAG);
  }

  /**
   * Sent a request to the self-provisioning gateway, which will return us with a webpage. The page
   * might contain a "Subscribe to Basic Visual Voice Mail" link to complete the subscription. The
   * cookie from this response and cellular data is required to click the link.
   */
  private String getSelfProvisionResponse(String url) throws ProvisioningException {
    VvmLog.i(TAG, "Retrieving self provisioning response");

    RequestFuture<String> future = RequestFuture.newFuture();

    StringRequest stringRequest =
        new StringRequest(Request.Method.POST, url, future, future) {
          @Override
          protected Map<String, String> getParams() {
            Map<String, String> params = new ArrayMap<>();
            params.put(SPG_VZW_MDN_PARAM, mNumber);
            params.put(SPG_VZW_SERVICE_PARAM, SPG_VZW_SERVICE_BASIC);
            params.put(SPG_DEVICE_MODEL_PARAM, SPG_DEVICE_MODEL_ANDROID);
            params.put(SPG_APP_TOKEN_PARAM, SPG_APP_TOKEN);
            // Language to display the subscription page. The page is never shown to the user
            // so just use English.
            params.put(SPG_LANGUAGE_PARAM, SPG_LANGUAGE_EN);
            return params;
          }
        };

    mRequestQueue.add(stringRequest);
    try {
      return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      mHelper.handleEvent(mStatus, OmtpEvents.VVM3_SPG_CONNECTION_FAILED);
      throw new ProvisioningException(e.toString());
    }
  }

  private void clickSubscribeLink(String subscribeLink) throws ProvisioningException {
    VvmLog.i(TAG, "Clicking subscribe link");
    RequestFuture<String> future = RequestFuture.newFuture();

    StringRequest stringRequest =
        new StringRequest(Request.Method.POST, subscribeLink, future, future);
    mRequestQueue.add(stringRequest);
    try {
      // A new STATUS SMS will be sent after this request.
      future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException | ExecutionException | InterruptedException e) {
      mHelper.handleEvent(mStatus, OmtpEvents.VVM3_SPG_CONNECTION_FAILED);
      throw new ProvisioningException(e.toString());
    }
    // It could take very long for the STATUS SMS to return. Waiting for it is unreliable.
    // Just leave the CONFIG STATUS as CONFIGURING and end the task. The user can always
    // manually retry if it took too long.
  }

  private String vvm3XmlRequest(String operation) throws ProvisioningException {
    VvmLog.d(TAG, "Sending vvm3XmlRequest for " + operation);
    String voicemailManagementGateway = mData.getString(VMG_URL_KEY);
    if (voicemailManagementGateway == null) {
      VvmLog.e(TAG, "voicemailManagementGateway url unknown");
      return null;
    }
    String transactionId = createTransactionId();
    String body =
        String.format(
            Locale.US, VMG_XML_REQUEST_FORMAT, transactionId, mNumber, operation, Build.MODEL);

    RequestFuture<String> future = RequestFuture.newFuture();
    StringRequest stringRequest =
        new StringRequest(Request.Method.POST, voicemailManagementGateway, future, future) {
          @Override
          public byte[] getBody() throws AuthFailureError {
            return body.getBytes();
          }
        };
    mRequestQueue.add(stringRequest);

    try {
      String response = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!transactionId.equals(extractText(response, TRANSACTION_ID_TAG))) {
        throw new ProvisioningException("transactionId mismatch");
      }
      return response;
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      mHelper.handleEvent(mStatus, OmtpEvents.VVM3_VMG_CONNECTION_FAILED);
      throw new ProvisioningException(e.toString());
    }
  }

  @VisibleForTesting
  static List<Pattern> getSubscribeLinkPatterns(Context context) {
    String patternsJsonString =
        ConfigProviderBindings.get(context)
            .getString(
                VVM3_SUBSCRIBE_LINK_PATTERNS_JSON_ARRAY, VVM3_SUBSCRIBE_LINK_DEFAULT_PATTERNS);
    List<Pattern> patterns = new ArrayList<>();
    try {
      JSONArray patternsArray = new JSONArray(patternsJsonString);
      for (int i = 0; i < patternsArray.length(); i++) {
        patterns.add(Pattern.compile(patternsArray.getString(i)));
      }
    } catch (JSONException e) {
      throw new IllegalArgumentException("Unable to parse patterns" + e);
    }
    return patterns;
  }

  @VisibleForTesting
  static String findSubscribeLink(@NonNull List<Pattern> patterns, String response)
      throws ProvisioningException {
    if (patterns.isEmpty()) {
      throw new IllegalArgumentException("empty patterns");
    }
    Spanned doc = Html.fromHtml(response, Html.FROM_HTML_MODE_LEGACY);
    URLSpan[] spans = doc.getSpans(0, doc.length(), URLSpan.class);
    StringBuilder fulltext = new StringBuilder();

    for (URLSpan span : spans) {
      String text = doc.subSequence(doc.getSpanStart(span), doc.getSpanEnd(span)).toString();
      for (Pattern pattern : patterns) {
        if (pattern.matcher(text).matches()) {
          return span.getURL();
        }
      }
      fulltext.append(text);
    }
    throw new ProvisioningException("Subscribe link not found: " + fulltext);
  }

  private String createTransactionId() {
    return String.valueOf(Math.abs(new Random().nextLong()));
  }

  private String extractText(String xml, String tag) throws ProvisioningException {
    Pattern pattern = Pattern.compile("<" + tag + ">(.*)<\\/" + tag + ">");
    Matcher matcher = pattern.matcher(xml);
    if (matcher.find()) {
      return matcher.group(1);
    }
    throw new ProvisioningException("Tag " + tag + " not found in xml response");
  }

  private static class NetworkSpecifiedHurlStack extends HurlStack {

    private final Network mNetwork;

    public NetworkSpecifiedHurlStack(Network network) {
      mNetwork = network;
    }

    @Override
    protected HttpURLConnection createConnection(URL url) throws IOException {
      return (HttpURLConnection) mNetwork.openConnection(url);
    }
  }
}
