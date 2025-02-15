/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.ActivityManager;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiSsid;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/**
 * Unit tests for {@link com.android.server.wifi.WrongPasswordNotifier}.
 */
@SmallTest
public class WrongPasswordNotifierTest extends WifiBaseTest {
    private static final WifiSsid TEST_SSID = WifiSsid.fromUtf8Text("Test SSID");
    private static final String TEST_SETTINGS_PACKAGE = "android";

    @Mock WifiContext mContext;
    @Mock Resources mResources;
    @Mock WifiNotificationManager mWifiNotificationManager;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Notification.Builder mNotificationBuilder;
    WrongPasswordNotifier mWrongPassNotifier;
    private MockitoSession mSession;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ResolveInfo settingsResolveInfo = new ResolveInfo();
        settingsResolveInfo.activityInfo = new ActivityInfo();
        settingsResolveInfo.activityInfo.packageName = TEST_SETTINGS_PACKAGE;
        when(mFrameworkFacade.getSettingsPackageName(any())).thenReturn(TEST_SETTINGS_PACKAGE);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getWifiOverlayApkPkgName()).thenReturn("test.com.android.wifi.resources");
        mWrongPassNotifier = new WrongPasswordNotifier(mContext, mFrameworkFacade,
                mWifiNotificationManager);

        // static mocking
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(ActivityManager.class, withSettings().lenient())
                .startMocking();
        when(ActivityManager.getCurrentUser()).thenReturn(UserHandle.USER_SYSTEM);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /**
     * Verify that a wrong password notification will be generated/pushed when a wrong password
     * error is detected for the current connection.
     *
     * @throws Exception
     */
    @Test
    public void onWrongPasswordError() throws Exception {
        when(mFrameworkFacade.makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS))).thenReturn(mNotificationBuilder);
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_SSID.toString();
        mWrongPassNotifier.onWrongPasswordError(config);
        verify(mWifiNotificationManager).notify(eq(WrongPasswordNotifier.NOTIFICATION_ID), any());
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mFrameworkFacade).getActivity(
                any(Context.class), anyInt(), intent.capture(), anyInt());
        assertEquals(Settings.ACTION_WIFI_SETTINGS, intent.getValue().getAction());
        assertEquals(TEST_SETTINGS_PACKAGE, intent.getValue().getPackage());
        assertEquals(TEST_SSID.getUtf8Text(),
                intent.getValue().getStringExtra("wifi_start_connect_ssid"));
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.getValue().getFlags());
    }

    /**
     * Verify that we will attempt to dismiss the wrong password notification when starting a new
     * connection attempt with the previous connection resulting in a wrong password error.
     *
     * @throws Exception
     */
    @Test
    public void onNewConnectionAttemptWithPreviousWrongPasswordError() throws Exception {
        onWrongPasswordError();
        reset(mWifiNotificationManager);

        mWrongPassNotifier.onNewConnectionAttempt();
        verify(mWifiNotificationManager).cancel(eq(WrongPasswordNotifier.NOTIFICATION_ID));
    }

    /**
     * Verify that we don't attempt to dismiss the wrong password notification when starting a new
     * connection attempt with the previous connection not resulting in a wrong password error.
     *
     * @throws Exception
     */
    @Test
    public void onNewConnectionAttemptWithoutPreviousWrongPasswordError() throws Exception {
        mWrongPassNotifier.onNewConnectionAttempt();
        verify(mWifiNotificationManager, never()).cancel(anyInt());
    }
}
