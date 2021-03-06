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

package com.android.sdklib.devices;

import com.android.dvlib.DeviceSchemaTest;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceWriterTest extends TestCase {

    public void testWriteIsValid() throws Exception {
        InputStream devicesFile =
            DeviceSchemaTest.class.getResourceAsStream("devices.xml");
        List<Device> devices = DeviceParser.parse(devicesFile);
        assertEquals("Parsed devices contained an un expected number of devices",
                2, devices.size());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeviceWriter.writeToXml(baos, devices);
        List<Device> writtenDevices = DeviceParser.parse(
                new ByteArrayInputStream(baos.toString().getBytes()));
        assertEquals("Writing and reparsing returns a different number of devices",
                devices.size(), writtenDevices.size());
        for (int i = 0; i < devices.size(); i++){
            assertEquals(devices.get(i), writtenDevices.get(i));
        }
    }

    public void testApiLowerBound() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("api-level", "1-");
        InputStream stream = DeviceSchemaTest.getReplacedStream(replacements);
        List<Device> devices = DeviceParser.parse(stream);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeviceWriter.writeToXml(baos, devices);
        List<Device> writtenDevices = DeviceParser.parse(
                new ByteArrayInputStream(baos.toString().getBytes()));
        assertEquals("Writing and reparsing returns a different number of devices",
                devices.size(), writtenDevices.size());
        for (int i = 0; i < devices.size(); i++){
            assertEquals(devices.get(i), writtenDevices.get(i));
        }
    }

    public void testApiUpperBound() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("api-level", "-10");
        InputStream stream = DeviceSchemaTest.getReplacedStream(replacements);
        List<Device> devices = DeviceParser.parse(stream);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeviceWriter.writeToXml(baos, devices);
        List<Device> writtenDevices = DeviceParser.parse(
                new ByteArrayInputStream(baos.toString().getBytes()));
        assertEquals("Writing and reparsing returns a different number of devices",
                devices.size(), writtenDevices.size());
        for (int i = 0; i < devices.size(); i++){
            assertEquals(devices.get(i), writtenDevices.get(i));
        }
    }

    public void testApiNeitherBound() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("api-level", "-");
        InputStream stream = DeviceSchemaTest.getReplacedStream(replacements);
        List<Device> devices = DeviceParser.parse(stream);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeviceWriter.writeToXml(baos, devices);
        List<Device> writtenDevices = DeviceParser.parse(
                new ByteArrayInputStream(baos.toString().getBytes()));
        assertEquals("Writing and reparsing returns a different number of devices",
                devices.size(), writtenDevices.size());
        for (int i = 0; i < devices.size(); i++){
            assertEquals(devices.get(i), writtenDevices.get(i));
        }
    }

    public void testApiBothBound() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("api-level", "9-10");
        InputStream stream = DeviceSchemaTest.getReplacedStream(replacements);
        List<Device> devices = DeviceParser.parse(stream);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeviceWriter.writeToXml(baos, devices);
        List<Device> writtenDevices = DeviceParser.parse(
                new ByteArrayInputStream(baos.toString().getBytes()));
        assertEquals("Writing and reparsing returns a different number of devices",
                devices.size(), writtenDevices.size());
        for (int i = 0; i < devices.size(); i++){
            assertEquals(devices.get(i), writtenDevices.get(i));
        }
    }
    public void testApiSingle() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("api-level", "10");
        InputStream stream = DeviceSchemaTest.getReplacedStream(replacements);
        List<Device> devices = DeviceParser.parse(stream);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeviceWriter.writeToXml(baos, devices);
        List<Device> writtenDevices = DeviceParser.parse(
                new ByteArrayInputStream(baos.toString().getBytes()));
        assertEquals("Writing and reparsing returns a different number of devices",
                devices.size(), writtenDevices.size());
        for (int i = 0; i < devices.size(); i++){
            assertEquals(devices.get(i), writtenDevices.get(i));
        }
    }
}
