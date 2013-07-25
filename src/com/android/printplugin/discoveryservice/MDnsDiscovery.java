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
package com.android.printplugin.discoveryservice;

import android.content.Context;
import android.util.Log;
import com.android.printplugin.discoveryservice.parsers.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

class MDnsDiscovery {
    private static final String TAG = MDnsDiscovery.class.getSimpleName();

    static final String MDNS_GROUP_ADDRESS = "224.0.0.251";
    static final int MDNS_PORT = 5353;

    private static final byte[] IPP_MDNS_REQUEST_PACKET = {

        // Transaction ID: 0x0000
        0x00, 0x00,

        // Flags: 0x0000 (Standard query)
        0x00, 0x00,

        // Questions: 1
        0x00, 0x01,

        // Answer RRs: 0
        0x00, 0x00,

        // Authority RRs: 0
        0x00, 0x00,

        // Additional RRs: 0
        0x00, 0x00,

        // Queries
        // Name: _ipp._tcp.local
        0x04, 0x5F, 0x69, 0x70, 0x70, 0x04, 0x5F, 0x74, 0x63, 0x70, 0x05, 0x6C, 0x6F,
        0x63, 0x61, 0x6C, 0x00,

        // Type: PTR (Domain name pointer)
        0x00, 0x0C,

        // x000 0000 0000 0001 = Class: IN (0x0001)
        // 1xxx xxxx xxxx xxxx = QU: true
        (byte) 0x80, 0x01
    };

    private static final byte[] PDL_MDNS_REQUEST_PACKET = {

            // Transaction ID: 0x0000
            0x00, 0x00,

            // Flags: 0x0000 (Standard query)
            0x00, 0x00,

            // Questions: 1
            0x00, 0x01,

            // Answer RRs: 0
            0x00, 0x00,

            // Authority RRs: 0
            0x00, 0x00,

            // Additional RRs: 0
            0x00, 0x00,

            // Queries
           // Name: _pdl-datastream._tcp.local
            0x0F, 0x5F, 0x70, 0x64, 0x6C, 0x2D, 0x64, 0x61, 0x74, 0x61, 0x73, 0x74,
            0x72, 0x65, 0x61, 0x6D, 0x04, 0x5F, 0x74, 0x63, 0x70, 0x05, 0x6C, 0x6F,
            0x63, 0x61, 0x6C, 0x00,

            // Type: PTR (Domain name pointer)
            0x00, 0x0C,

            // x000 0000 0000 0001 = Class: IN (0x0001)
            // 1xxx xxxx xxxx xxxx = QU: true
            (byte) 0x80, 0x01
    };

    private static final byte[] PRINTER_MDNS_REQUEST_PACKET = {

            // Transaction ID: 0x0000
            0x00, 0x00,

            // Flags: 0x0000 (Standard query)
            0x00, 0x00,

            // Questions: 1
            0x00, 0x01,

            // Answer RRs: 0
            0x00, 0x00,

            // Authority RRs: 0
            0x00, 0x00,

            // Additional RRs: 0
            0x00, 0x00,

            // Queries
            // Name: _printer._tcp.local
            0x08, 0x5F, 0x70, 0x72, 0x69, 0x6E, 0x74, 0x65, 0x72, 0x04, 0x5F, 0x74, 0x63, 0x70, 0x05, 0x6C, 0x6F,
            0x63, 0x61, 0x6C, 0x00,

            // Type: PTR (Domain name pointer)
            0x00, 0x0C,

            // x000 0000 0000 0001 = Class: IN (0x0001)
            // 1xxx xxxx xxxx xxxx = QU: true
            (byte) 0x80, 0x01
    };

    private Context context;

    public MDnsDiscovery(Context context) {
        this.context = context;
    }

    public DatagramSocket createSocket() throws IOException {
        return WifiUtils.createMulticastSocket(context);
    }

    public void releaseSocket(DatagramSocket socket) {
        if (socket != null) {
            socket.close();
        }
    }

    public DatagramPacket[] createQueryPackets() throws UnknownHostException {
        InetAddress group = InetAddress.getByName(MDNS_GROUP_ADDRESS);

        return new DatagramPacket[] {
                new DatagramPacket(IPP_MDNS_REQUEST_PACKET, IPP_MDNS_REQUEST_PACKET.length, group, MDNS_PORT),
                new DatagramPacket(PDL_MDNS_REQUEST_PACKET, PDL_MDNS_REQUEST_PACKET.length, group, MDNS_PORT),
                new DatagramPacket(PRINTER_MDNS_REQUEST_PACKET, PRINTER_MDNS_REQUEST_PACKET.length, group, MDNS_PORT),
        };
    }

    public Printer[] parseResponse(DatagramPacket packet) {
        ArrayList<Printer> printers = new ArrayList<Printer>();

//        if (V) {
//            Log.v(TAG, "DNS packet contents from " + packet.getAddress() + ":");
//            Log.v(TAG, Util.byteArrayToDebugString(packet.getData(), packet.getLength()));
//        }
        try {
            DnsPacket dnsPacket = new DnsParser().parse(packet);
            DnsService[] services = new DnsSdParser().parse(dnsPacket);

            for (DnsService service : services) {
                BonjourParser bonjourParser = new BonjourParser(service);

                if (bonjourParser.isPrinter()) {
                    String name = bonjourParser.getHostname();
                    String model = bonjourParser.getModel();
                    InetAddress address = bonjourParser.getAddress();
                    String vendor = bonjourParser.getVendor();

                    printers.add(new Printer(address, model, bonjourParser.getBonjourName(), name, vendor));
                }
            }
        } catch (Exception exc) {
            Log.e(TAG, "Error while parsing DNS response.", exc);
        }
        return printers.toArray(new Printer[printers.size()]);
    }

}
