/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.compat;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.compat.AndroidBuildClassifier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class CompatConfigTest {

    @Mock
    private Context mContext;
    @Mock
    private AndroidBuildClassifier mBuildClassifier;

    private ApplicationInfo makeAppInfo(String pName, int targetSdkVersion) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pName;
        ai.targetSdkVersion = targetSdkVersion;
        return ai;
    }

    private File createTempDir() {
        String base = System.getProperty("java.io.tmpdir");
        File dir = new File(base, UUID.randomUUID().toString());
        assertThat(dir.mkdirs()).isTrue();
        return dir;
    }

    private void writeToFile(File dir, String filename, String content) throws IOException {
        OutputStream os = new FileOutputStream(new File(dir, filename));
        os.write(content.getBytes());
        os.close();
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // Assume userdebug/eng non-final build
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(true);
        when(mBuildClassifier.isFinalBuild()).thenReturn(false);
    }

    @Test
    public void testUnknownChangeEnabled() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 1))).isTrue();
    }

    @Test
    public void testDisabledChangeDisabled() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, true, ""));
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 1))).isFalse();
    }

    @Test
    public void testTargetSdkChangeDisabled() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", 2, false, null));
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 2))).isFalse();
    }

    @Test
    public void testTargetSdkChangeEnabled() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", 2, false, ""));
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 3))).isTrue();
    }

    @Test
    public void testDisabledOverrideTargetSdkChange() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", 2, true, null));
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 3))).isFalse();
    }

    @Test
    public void testGetDisabledChanges() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, true, null));
        pc.addChange(new CompatChange(2345L, "OTHER_CHANGE", -1, false, null));
        assertThat(pc.getDisabledChanges(
                makeAppInfo("com.some.package", 2))).asList().containsExactly(1234L);
    }

    @Test
    public void testGetDisabledChangesSorted() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", 2, true, null));
        pc.addChange(new CompatChange(123L, "OTHER_CHANGE", 2, true, null));
        pc.addChange(new CompatChange(12L, "THIRD_CHANGE", 2, true, null));
        assertThat(pc.getDisabledChanges(
                makeAppInfo("com.some.package", 2))).asList().containsExactly(12L, 123L, 1234L);
    }

    @Test
    public void testPackageOverrideEnabled() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, true, null)); // disabled
        pc.addOverride(1234L, "com.some.package", true);
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 2))).isTrue();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.other.package", 2))).isFalse();
    }

    @Test
    public void testPackageOverrideDisabled() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, false, null));
        pc.addOverride(1234L, "com.some.package", false);
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 2))).isFalse();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.other.package", 2))).isTrue();
    }

    @Test
    public void testPackageOverrideUnknownPackage() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addOverride(1234L, "com.some.package", false);
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 2))).isFalse();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.other.package", 2))).isTrue();
    }

    @Test
    public void testPackageOverrideUnknownChange() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 1))).isTrue();
    }

    @Test
    public void testRemovePackageOverride() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, false, null));
        pc.addOverride(1234L, "com.some.package", false);
        pc.removeOverride(1234L, "com.some.package");
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 2))).isTrue();
    }

    @Test
    public void testLookupChangeId() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, false, null));
        pc.addChange(new CompatChange(2345L, "ANOTHER_CHANGE", -1, false, null));
        assertThat(pc.lookupChangeId("MY_CHANGE")).isEqualTo(1234L);
    }

    @Test
    public void testLookupChangeIdNotPresent() throws Exception {
        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        assertThat(pc.lookupChangeId("MY_CHANGE")).isEqualTo(-1L);
    }

    @Test
    public void testReadConfig() throws IOException {
        String configXml = "<config>"
                + "<compat-change id=\"1234\" name=\"MY_CHANGE1\" enableAfterTargetSdk=\"2\" />"
                + "<compat-change id=\"1235\" name=\"MY_CHANGE2\" disabled=\"true\" />"
                + "<compat-change id=\"1236\" name=\"MY_CHANGE3\" />"
                + "</config>";

        File dir = createTempDir();
        writeToFile(dir, "platform_compat_config.xml", configXml);

        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.initConfigFromLib(dir);

        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 1))).isFalse();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 3))).isTrue();
        assertThat(pc.isChangeEnabled(1235L, makeAppInfo("com.some.package", 5))).isFalse();
        assertThat(pc.isChangeEnabled(1236L, makeAppInfo("com.some.package", 1))).isTrue();
    }

    @Test
    public void testReadConfigMultipleFiles() throws IOException {
        String configXml1 = "<config>"
                + "<compat-change id=\"1234\" name=\"MY_CHANGE1\" enableAfterTargetSdk=\"2\" />"
                + "</config>";
        String configXml2 = "<config>"
                + "<compat-change id=\"1235\" name=\"MY_CHANGE2\" disabled=\"true\" />"
                + "<compat-change id=\"1236\" name=\"MY_CHANGE3\" />"
                + "</config>";

        File dir = createTempDir();
        writeToFile(dir, "libcore_platform_compat_config.xml", configXml1);
        writeToFile(dir, "frameworks_platform_compat_config.xml", configXml2);

        CompatConfig pc = new CompatConfig(mBuildClassifier, mContext);
        pc.initConfigFromLib(dir);

        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 1))).isFalse();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 3))).isTrue();
        assertThat(pc.isChangeEnabled(1235L, makeAppInfo("com.some.package", 5))).isFalse();
        assertThat(pc.isChangeEnabled(1236L, makeAppInfo("com.some.package", 1))).isTrue();
    }
}


