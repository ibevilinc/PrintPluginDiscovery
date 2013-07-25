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
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import com.android.printplugin.discovery.R;

import com.hp.android.printplugin.support.PrintServiceStrings;
import java.io.IOException;
import java.net.*;

public class LocalPrinterDiscoveryTask extends AsyncTask<Void, Void, Intent>
{
    private static final String TAG = "PluginDiscoveryTask";

	private static final int DEFAULT_INITIAL_TIMEOUT = 8000;
	private static final int DEFAULT_TIMEOUT_DECAY = 2000;
	private static final int DEFAULT_TIMEOUT_AFTER_FOUND = 5000;
	private static final int BUFFER_LENGTH = 4 * 1024;

	private final MDnsDiscovery mMDNSDiscovery;
	private final Messenger mClientCallBack;
	private byte[] buffer = new byte[BUFFER_LENGTH];

    protected final Message mRequest;
    protected final Intent mIntent;
    protected final Bundle mBundleData;

    private final Context mContext;

	public LocalPrinterDiscoveryTask(Context context, Message msg) {
		super();

        mContext = context;
        mRequest = msg;

        Intent intent = null;
        Bundle bundleData = null;
        if ((mRequest.obj != null) && (mRequest.obj instanceof Intent))
        {
            intent = (Intent) mRequest.obj;
            bundleData = ((intent != null) ? intent.getExtras() : null);
        }
        mIntent = intent;
        mBundleData = bundleData;

		mClientCallBack = mRequest.replyTo;
		mMDNSDiscovery = new MDnsDiscovery(context);
	}

	@Override
	public Intent doInBackground(Void... params) {
		DatagramSocket socket = null;
		try
		{
			socket = mMDNSDiscovery.createSocket();
			socket.setReuseAddress(true);
			sendQueryPacket(socket);
			receiveResponsePackets(socket);
			
		} catch (UnknownHostException exc)
		{
			Log.i(TAG, "Could not resolve hostname during discovery.", exc);
		} catch (IOException exc)
		{
			Log.e(TAG, "IO error occurred during printer discovery.", exc);
		} finally
		{
			mMDNSDiscovery.releaseSocket(socket);
		}
		return null;
	}
	
	private void sendQueryPacket(DatagramSocket socket) throws UnknownHostException, IOException
	{
		DatagramPacket[] queryPackets = mMDNSDiscovery
				.createQueryPackets();

		for (DatagramPacket packet : queryPackets)
		{
			socket.send(packet);
		}
	}

	/*
	 * The algorithm for receiving the response packets will decrease the
	 * timeout according to the search results. Socket timeout starts with a
	 * value of 8s. If no printer is found, the socket timeout is decreased by
	 * 2s until it reaches 0 and the algorithm stops listening for new
	 * responses. So when no printer is found, the sequence of socket timeouts
	 * is 8, 6, 4, and 2s, adding up to 20s of wait time. When a printer is
	 * found, both timeout and decay are set to 5s, which means that the thread
	 * will receive new packets with a timeout of 5s until no packet is
	 * received. The first time the receive method reaches the timeout without
	 * receiving any packets, the algorithm finishes.
	 */
	private void receiveResponsePackets(final DatagramSocket socket) throws IOException
	{

		int timeout = DEFAULT_INITIAL_TIMEOUT;
		int remainingTO = DEFAULT_TIMEOUT_AFTER_FOUND;
		int decay = DEFAULT_TIMEOUT_DECAY;
		DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);

		while (!Thread.interrupted() && (timeout > 0))
		{
			try
			{
				socket.setSoTimeout(timeout);
				socket.receive(packet);
				Log.d(TAG,
						"Response from " + packet.getAddress() + ":"
								+ packet.getPort());
				if (!Thread.interrupted())
				{
					if (processIncomingPacket(packet))
					{

						// A valid printer was found: wait for another
						// 5s and stop listening.
						timeout = remainingTO;
						decay = timeout;
					} else
					{
						Log.w(TAG,
								"Printer could not be parsed or is not supported.");
					}
					// Resets the packet length to reuse the packet.
					packet.setLength(BUFFER_LENGTH);
				} else {
					timeout = 0;
				}
			} catch (SocketTimeoutException exc)
			{

				// Socket timed out and we received no packets. If at
				// least one printer has
				// already been found, timeout is set to zero and the
				// algorithm finishes.
				// Otherwise, we will decrease the timeout by 2s and try
				// again.
				sendQueryPacket(socket);
				timeout -= decay;
			} catch (SocketException e)
			{
				e.printStackTrace();
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private boolean processIncomingPacket(DatagramPacket packet)
	{
		boolean foundSupportedPrinter = false;
		Printer[] printers = mMDNSDiscovery.parseResponse(packet);

		if ((printers != null) && (printers.length > 0))
		{
			for (Printer printer : printers)
			{
				printerFound(printer);
				foundSupportedPrinter = true;
			}
		}
		return foundSupportedPrinter;
	}

	protected void printerFound(Printer printer)
	{
		String value;
		Intent returnIntent = new Intent(
                PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_DEVICE_RESOLVED);
		
		// Add Name
		returnIntent.putExtra(PrintServiceStrings.DISCOVERY_DEVICE_NAME,
				printer.getModel());
		// Add IP Address
		returnIntent.putExtra(PrintServiceStrings.DISCOVERY_DEVICE_ADDRESS,
				printer.getInetAddress().getHostAddress());

		value = printer.getBonjourName();
		if (!TextUtils.isEmpty(value))
			returnIntent.putExtra(PrintServiceStrings.DISCOVERY_DEVICE_BONJOUR_NAME, value);
		value = printer.getBonjourDomainName();
		if (!TextUtils.isEmpty(value))
			returnIntent.putExtra(PrintServiceStrings.DISCOVERY_DEVICE_BONJOUR_DOMAIN_NAME, value);

        Intent installIntent = getVendorIntent(printer.getVendor());
        if (installIntent != null) {
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            returnIntent.putExtra(Intent.EXTRA_INTENT, installIntent);
        }

        if (mClientCallBack != null) {
		    try {
				mClientCallBack.send(Message.obtain(null, 0, returnIntent));
		    } catch (RemoteException e) {
		    }
        }
	}

    private Intent getVendorIntent(String vendor) {
        if (!TextUtils.isEmpty(vendor)) {
            final Resources resources = mContext.getResources();
            String[] knownVendors = resources.getStringArray(R.array.known_print_plugin_vendors);
            if ((knownVendors != null) && (knownVendors.length > 0)) {
                for(String knownVendor : knownVendors) {

                    String vendorIDsResource = resources.getString(R.string.known_print_plugin_ids_for_vendor, knownVendor);
                    String vendorPackageResource = resources.getString(R.string.print_plugin_package_for_vendor, knownVendor);

                    int vendorIDsResourceID = resources.getIdentifier(vendorIDsResource, "array", mContext.getPackageName());
                    int vendorPackageResourceID = resources.getIdentifier(vendorPackageResource, "string", mContext.getPackageName());

                    if ((vendorIDsResourceID != 0) && (vendorPackageResourceID != 0)) {
                        String[] vendorIDs = resources.getStringArray(vendorIDsResourceID);
                        if ((vendorIDs != null) && (vendorIDs.length > 0)) {
                            for(String vendorID : vendorIDs) {
                                if (vendor.equalsIgnoreCase(vendorID)) {
                                    String vendorPackageName = resources.getString(vendorPackageResourceID);
                                    return new Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.market_package_search, vendorPackageName))).putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, vendorPackageName);
                                }
                            }
                        }
                    }

                }
            }
            return new Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.market_generic_search, vendor)));
        }
        return null;
    }
}
