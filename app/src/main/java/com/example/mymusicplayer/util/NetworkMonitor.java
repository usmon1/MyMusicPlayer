package com.example.mymusicplayer.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class NetworkMonitor {

    private static volatile NetworkMonitor instance;

    private final ConnectivityManager connectivityManager;
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    isConnected.postValue(resolveCurrentState());
                }

                @Override
                public void onLost(@NonNull Network network) {
                    isConnected.postValue(resolveCurrentState());
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                                                  @NonNull NetworkCapabilities networkCapabilities) {
                    isConnected.postValue(resolveCurrentState());
                }
            };

    private NetworkMonitor(Context context) {
        Context appContext = context.getApplicationContext();
        connectivityManager =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        isConnected.setValue(resolveCurrentState());

        if (connectivityManager != null) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
    }

    public static NetworkMonitor getInstance(Context context) {
        if (instance == null) {
            synchronized (NetworkMonitor.class) {
                if (instance == null) {
                    instance = new NetworkMonitor(context);
                }
            }
        }

        return instance;
    }

    public LiveData<Boolean> getIsConnected() {
        return isConnected;
    }

    public boolean isCurrentlyConnected() {
        boolean currentState = resolveCurrentState();
        isConnected.postValue(currentState);
        return currentState;
    }

    public boolean refresh() {
        boolean currentState = resolveCurrentState();
        isConnected.postValue(currentState);
        return currentState;
    }

    private boolean resolveCurrentState() {
        if (connectivityManager == null) {
            return false;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return false;
        }

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
