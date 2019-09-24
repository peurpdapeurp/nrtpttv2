package com.example.ndnpttv2.back_end.threads;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.wifi_module.WifiModule;
import com.intel.jndn.management.ManagementException;
import com.intel.jndn.management.Nfdc;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;

import java.io.IOException;
import java.util.List;

public class NetworkThread extends HandlerThread {

    private static final String TAG = "NetworkThread";

    // Private constants
    private static final int PROCESSING_INTERVAL_MS = 50;
    public static final int ROUTE_REGISTRATION_DELAY_MS = 500; // delay registering the route after reconnecting to avoid "Network unreachable"

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;
    private static final int MSG_NEW_WIFI_STATE = 1;
    private static final int MSG_REGISTER_SLASH_PREFIX = 2;

    private Face face_;
    private KeyChain keyChain_;
    private Callbacks callbacks_;
    private Handler handler_;
    private Name applicationDataPrefix_;
    private Options options_;
    private int wifiConnectionState_;

    public static class Options {
        public Options(String accessPointIpAddress) {
            this.accessPointIpAddress = accessPointIpAddress;
        }
        public String accessPointIpAddress;
    }

    public static class Info {
        public Info(Looper looper, Face face) {
            this.looper = looper;
            this.face = face;
        }
        public Looper looper;
        public Face face;
    }

    public interface Callbacks {
        void onInitialized(Info info);
    }

    public NetworkThread(Name applicationDataPrefix, Callbacks callbacks, Options options) {
        super(TAG);
        applicationDataPrefix_ = applicationDataPrefix;
        callbacks_ = callbacks;
        options_ = options;
    }

    @SuppressLint("HandlerLeak")
    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        // set up keychain
        keyChain_ = configureKeyChain();
        // set up face
        face_ = new Face();
        try {
            face_.setCommandSigningInfo(keyChain_, keyChain_.getDefaultCertificateName());
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        try {
            face_.registerPrefix(applicationDataPrefix_,
                    new OnInterestCallback() {
                        @Override
                        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

                        }
                    }, new OnRegisterFailed() {
                        @Override
                        public void onRegisterFailed(Name prefix) {

                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        handler_ = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_DO_SOME_WORK: {
                        doSomeWork();
                        break;
                    }
                    case MSG_NEW_WIFI_STATE: {
                        int newWifiState = msg.arg1;
                        wifiConnectionState_ = newWifiState;
                        Log.d(TAG, "notified of new wifi state " + newWifiState);
                        if (newWifiState == WifiModule.CONNECTED) {
                            Log.d(TAG, "new wifi state was connected, registering / prefix");
                            Message registerSlashPrefixMsg = handler_.obtainMessage(MSG_REGISTER_SLASH_PREFIX);
                            handler_.sendMessageAtTime(registerSlashPrefixMsg,
                                    SystemClock.uptimeMillis() + ROUTE_REGISTRATION_DELAY_MS);
                        }
                        break;
                    }
                    case MSG_REGISTER_SLASH_PREFIX: {
                        registerSlashPrefix();
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        handler_.obtainMessage(MSG_DO_SOME_WORK).sendToTarget();

        callbacks_.onInitialized(new Info(getLooper(), face_));
    }

    public void notifyNewWifiState(int newWifiState) {
        handler_.obtainMessage(MSG_NEW_WIFI_STATE, newWifiState, 0).sendToTarget();
    }

    private void registerSlashPrefix() {
        String accessPointUri = "udp4://" + options_.accessPointIpAddress + ":6363";
        Log.d(TAG, "registering / route to remote uri " + accessPointUri);
        try {
            Nfdc.register(face_, accessPointUri, new Name("/"), 0);
        } catch (ManagementException e) {
            e.printStackTrace();
        }
    }

    private void doSomeWork() {
        try {
            face_.processEvents();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (EncodingException e) {
            e.printStackTrace();
        }
        scheduleNextWork(SystemClock.uptimeMillis());
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        handler_.removeMessages(MSG_DO_SOME_WORK);
        handler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
    }

    // taken from https://github.com/named-data-mobile/NFD-android/blob/4a20a88fb288403c6776f81c1d117cfc7fced122/app/src/main/java/net/named_data/nfd/utils/NfdcHelper.java
    private KeyChain configureKeyChain() {
        final MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        final MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        final KeyChain keyChain = new KeyChain(new IdentityManager(identityStorage, privateKeyStorage),
                new SelfVerifyPolicyManager(identityStorage));
        Name name = new Name("/tmp-identity");
        try {
            // create keys, certs if necessary
            if (!identityStorage.doesIdentityExist(name)) {
                keyChain.createIdentityAndCertificate(name);

                // set default identity
                keyChain.getIdentityManager().setDefaultIdentity(name);
            }
        }
        catch (SecurityException e){
            e.printStackTrace();
        }
        return keyChain;
    }
}
