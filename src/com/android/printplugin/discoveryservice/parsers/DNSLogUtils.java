/*
(c) Copyright 2013 Hewlett-Packard Development Company, L.P.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.android.printplugin.discoveryservice.parsers;

public class DNSLogUtils
{
    private static final int DEBUG_BYTES_PER_LINE = 16;

    public static String byteArrayToDebugString(byte[] array, int length) {
        StringBuilder builder = new StringBuilder();
        if (array == null) {
            length = 0;
        }
        for(int i = 0; length > 0; i++, length -= DEBUG_BYTES_PER_LINE) {
            appendDebugLine(builder, i, DEBUG_BYTES_PER_LINE, Math.min(length, DEBUG_BYTES_PER_LINE), array);
        }
        return builder.toString();
    }

    private static void appendDebugLine(StringBuilder builder, int lineNumber, int bytesPerLine,
            int bytesOnThisLine, byte[] array) {
        builder.append(String.format("%04x", lineNumber * bytesPerLine));
        builder.append(" | ");
        int b, i;
        for (i = 0; i < bytesOnThisLine; i++) {
            b = array[lineNumber * bytesPerLine + i] & 0xff;
            builder.append(String.format("%02x", b));
            builder.append(" ");
        }
        for (i = bytesOnThisLine; i < bytesPerLine; i++) {
            builder.append("   ");
        }
        builder.append("| ");
        for (i = 0; i < bytesOnThisLine; i++) {
            b = array[lineNumber * bytesPerLine + i] & 0xff;
            builder.append(b >= 0x20 && b < 0x80 ? (char) b : '.');
        }
        for (i = bytesOnThisLine; i < bytesPerLine; i++) {
            builder.append(" ");
        }
        builder.append("\n");
    }
}