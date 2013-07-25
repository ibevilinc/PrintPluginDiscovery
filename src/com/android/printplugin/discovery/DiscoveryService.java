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
package com.android.printplugin.discovery;

import java.lang.ref.WeakReference;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;

import android.util.Log;
import com.hp.android.printplugin.support.PrintServiceStrings;
import com.android.printplugin.discoveryservice.LocalPrinterDiscoveryTask;


public class DiscoveryService extends Service {

    private static final int DISCOVERY_SERVICE_MSG__SERVICE_BIND = 1;
    private static final int DISCOVERY_SERVICE_MSG__SERVICE_UNBIND = 2;

    private static final String TAG = "DiscoveryPrintService";

    private static final int QUIT_DELAY = 60000;

    private static class ServiceHandler extends Handler {

        private final WeakReference<DiscoveryService> mServiceRef;

        public ServiceHandler(DiscoveryService service) {
            super();
            mServiceRef = new WeakReference<DiscoveryService>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            Intent intent = null;
            String action = null;
            if ((msg.obj != null) && (msg.obj instanceof Intent)) {
                intent = (Intent) msg.obj;
                action = intent.getAction();
            }
            DiscoveryService service = mServiceRef.get();
            if (service == null) {
                return;
            } else if (msg.obj == null) {
                service.queueStopRequest();
            } else {
                boolean priorityMessage = false;
                int msgWhat = 0;
                if (msg.what == DISCOVERY_SERVICE_MSG__SERVICE_BIND) {
                    service.removeStopRequest();
                } else if (msg.what == DISCOVERY_SERVICE_MSG__SERVICE_UNBIND) {
                    service.queueStopRequest();
                } else if (!TextUtils.isEmpty(action) && action.equals(PrintServiceStrings.ACTION_PRINT_SERVICE_START_DISCOVERY)) {
                     new LocalPrinterDiscoveryTask(mServiceRef.get(), Message.obtain(msg)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        }
    }

    private ServiceHandler mServiceHandler = null;
    private Messenger mServiceMessenger = null;

    private int mStartID = 0;

    private Runnable mQuitRunnable = new Runnable() {
        @Override
        public void run() {
            stopSelf(mStartID);
        }
    };

    private synchronized void removeStopRequest() {
        mServiceHandler.removeCallbacks(mQuitRunnable);
    }

    private synchronized void queueStopRequest() {
        removeStopRequest();
        mServiceHandler.postDelayed(mQuitRunnable, QUIT_DELAY);
    }

    @Override
    public void onCreate() {
        mServiceHandler = new ServiceHandler(this);
        mServiceMessenger = new Messenger(mServiceHandler);
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        mStartID = startID;
        int result = super.onStartCommand(intent, flags, startID);
        if (intent == null) {
            queueStopRequest();
        } else if (intent.getAction().equals(
                PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINT_SERVICE)) {
        } else {
            mServiceHandler.sendMessage(mServiceHandler
                    .obtainMessage(0, intent));
        }
        return result;
    }

    public IBinder onBind(Intent intent) {
        IBinder binder = null;
        if ((intent == null) || TextUtils.isEmpty(intent.getAction())) {
        } else if (intent.getAction().equals(
                PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINT_SERVICE)) {
            Log.d("IBEVIL", "binding request");
            mServiceHandler
                    .removeMessages(DISCOVERY_SERVICE_MSG__SERVICE_BIND);
            mServiceHandler
                    .removeMessages(DISCOVERY_SERVICE_MSG__SERVICE_UNBIND);
            mServiceHandler
                    .sendMessageAtFrontOfQueue(mServiceHandler
                            .obtainMessage(
                                    DISCOVERY_SERVICE_MSG__SERVICE_BIND,
                                    intent));
            binder = mServiceMessenger.getBinder();
        }
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mServiceHandler
                .removeMessages(DISCOVERY_SERVICE_MSG__SERVICE_UNBIND);
        mServiceHandler
                .removeMessages(DISCOVERY_SERVICE_MSG__SERVICE_BIND);
        mServiceHandler
                .sendMessageAtFrontOfQueue(mServiceHandler
                        .obtainMessage(DISCOVERY_SERVICE_MSG__SERVICE_UNBIND));
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        if ((intent == null) || TextUtils.isEmpty(intent.getAction())) {
        } else if (intent.getAction().equals(
                PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINT_SERVICE)) {
            Intent startIntent = new Intent(this, DiscoveryService.class);
            startIntent.setAction(intent.getAction());
            mServiceHandler
                    .removeMessages(DISCOVERY_SERVICE_MSG__SERVICE_BIND);
            mServiceHandler
                    .removeMessages(DISCOVERY_SERVICE_MSG__SERVICE_UNBIND);
            mServiceHandler
                    .sendMessageAtFrontOfQueue(mServiceHandler
                            .obtainMessage(
                                    DISCOVERY_SERVICE_MSG__SERVICE_BIND,
                                    intent));
        }
    }
}
