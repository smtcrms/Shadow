/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /Users/shifujun/Codes/Android/shadow/projects/sdk/dynamic/dynamic-loader/dynamic-loader-aar/src/main/aidl/com/tencent/shadow/dynamic/loader/PluginLoader.aidl
 */
package com.tencent.shadow.dynamic.loader;

import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.Map;

public interface PluginLoader {
    String DESCRIPTOR = PluginLoader.class.getName();
    int TRANSACTION_loadPlugin = (IBinder.FIRST_CALL_TRANSACTION);
    int TRANSACTION_getLoadedPlugin = (IBinder.FIRST_CALL_TRANSACTION + 1);
    int TRANSACTION_callApplicationOnCreate = (IBinder.FIRST_CALL_TRANSACTION + 2);
    int TRANSACTION_convertActivityIntent = (IBinder.FIRST_CALL_TRANSACTION + 3);
    int TRANSACTION_startPluginService = (IBinder.FIRST_CALL_TRANSACTION + 4);
    int TRANSACTION_stopPluginService = (IBinder.FIRST_CALL_TRANSACTION + 5);
    int TRANSACTION_bindPluginService = (IBinder.FIRST_CALL_TRANSACTION + 6);
    int TRANSACTION_unbindService = (IBinder.FIRST_CALL_TRANSACTION + 7);

    void loadPlugin(String partKey) throws RemoteException;

    Map getLoadedPlugin() throws RemoteException;

    void callApplicationOnCreate(String partKey) throws RemoteException;

    Intent convertActivityIntent(Intent pluginActivityIntent) throws RemoteException;

    ComponentName startPluginService(Intent pluginServiceIntent) throws RemoteException;

    boolean stopPluginService(Intent pluginServiceIntent) throws RemoteException;

    boolean bindPluginService(Intent pluginServiceIntent, PluginServiceConnection connection, int flags) throws RemoteException;

    void unbindService(PluginServiceConnection conn) throws RemoteException;

}
