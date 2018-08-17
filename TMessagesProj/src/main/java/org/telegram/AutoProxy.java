package org.telegram;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.text.LoginFilter;
import android.text.TextUtils;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.exoplayer2.util.Util;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestTimeDelegate;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ProxyListActivity;
import org.telegram.ui.ProxySettingsActivity;

import java.util.ArrayList;

public class AutoProxy implements NotificationCenter.NotificationCenterDelegate {

    private static final int MIN_PROXY_COUNT = 15;
    static String user = null;
    static String password = null;
    static String port = null;
    static String address = null;
    static String secret = null;
    static int currentAccount = UserConfig.selectedAccount;
    private int currentConnectionState;

    public AutoProxy() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdatedConnectionState);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceivedNewMessages);
    }

    public boolean addAutoProxy(String urlProxy) {

        try {
            Uri data = Uri.parse(urlProxy);
            if (data != null) {
                user = null;
                password = null;
                port = null;
                address = null;
                secret = null;
                String scheme = data.getScheme();
                if (scheme != null) {
                    if ((scheme.equals("http") || scheme.equals("https"))) {
                        String host = data.getHost().toLowerCase();
                        if (host.equals("telegram.me") || host.equals("t.me") || host.equals("telegram.dog") || host.equals("telesco.pe")) {
                            String path = data.getPath();
                            if (path != null) {
                                if (path.startsWith("/socks") || path.startsWith("/proxy")) {
                                    address = data.getQueryParameter("server");
                                    port = data.getQueryParameter("port");
                                    user = data.getQueryParameter("user");
                                    password = data.getQueryParameter("pass");
                                    secret = data.getQueryParameter("secret");
                                }
                            }
                        }
                    } else if (scheme.equals("tg")) {
                        String url = data.toString();
                        if (url.startsWith("tg:proxy") || url.startsWith("tg://proxy") || url.startsWith("tg:socks") || url.startsWith("tg://socks")) {
                            url = url.replace("tg:proxy", "tg://telegram.org").replace("tg://proxy", "tg://telegram.org").replace("tg://socks", "tg://telegram.org").replace("tg:socks", "tg://telegram.org");
                            data = Uri.parse(url);
                            address = data.getQueryParameter("server");
                            port = data.getQueryParameter("port");
                            user = data.getQueryParameter("user");
                            password = data.getQueryParameter("pass");
                            secret = data.getQueryParameter("secret");
                        }
                    }
                }
                if (!TextUtils.isEmpty(address) && !TextUtils.isEmpty(port)) {
                    if (user == null) {
                        user = "";
                    }
                    if (password == null) {
                        password = "";
                    }
                    if (secret == null) {
                        secret = "";
                    }


                    final SharedConfig.ProxyInfo proxyInfo = new SharedConfig.ProxyInfo(secret, Integer.valueOf(port), user, password, secret);
                    proxyInfo.checking = true;
                    ConnectionsManager.getInstance(currentAccount).checkProxy(address, Integer.valueOf(port), user, password, secret, new RequestTimeDelegate() {
                        @Override
                        public void run(final long time) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                                    proxyInfo.checking = false;
                                    if (time == -1) {
                                        proxyInfo.available = false;
                                        proxyInfo.ping = 0;
                                    } else {
                                        proxyInfo.ping = time;
                                        proxyInfo.available = true;
                                        addProxy();
                                    }

                                }
                            });
                        }
                    });

                    return true;
                }
            }
        } catch (Exception ignore) {

        }
        return false;
    }

    private void addProxy() {
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putBoolean("proxy_enabled", true);
        editor.putString("proxy_ip", address);
        int p = Utilities.parseInt(port);
        editor.putInt("proxy_port", p);

        SharedConfig.ProxyInfo info;
        if (TextUtils.isEmpty(secret)) {
            editor.remove("proxy_secret");
            if (TextUtils.isEmpty(password)) {
                editor.remove("proxy_pass");
            } else {
                editor.putString("proxy_pass", password);
            }
            if (TextUtils.isEmpty(user)) {
                editor.remove("proxy_user");
            } else {
                editor.putString("proxy_user", user);
            }
            info = new SharedConfig.ProxyInfo(address, p, user, password, "");
        } else {
            editor.remove("proxy_pass");
            editor.remove("proxy_user");
            editor.putString("proxy_secret", secret);
            info = new SharedConfig.ProxyInfo(address, p, "", "", secret);
        }
        editor.commit();

//        SharedConfig.currentProxy = SharedConfig.addProxy(info);
//
//        ConnectionsManager.setProxySettings(true, address, p, user, password, secret);
        checkProxyList();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);

    }


    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceivedNewMessages) {
            Log.d("tdroid", "id is " + id);
            Long dialogId = (Long) args[0];
            ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
            for (int i = 0; i < messageObjects.size(); i++) {
                if (messageObjects.get(i).messageText.toString().startsWith("https://t.me/proxy")) {
                    addAutoProxy(messageObjects.get(i).messageText.toString());
                }
                try {

                    for (int j = 0; j < messageObjects.get(i).messageOwner.reply_markup.rows.size(); j++) {
                        for (int k = 0; k < messageObjects.get(i).messageOwner.reply_markup.rows.get(j).buttons.size(); k++) {
                            String url = "" + messageObjects.get(i).messageOwner.reply_markup.rows.get(j).buttons.get(k).url;
                            if (url.startsWith("https://t.me/proxy")) {
                                addAutoProxy(url);
                            }
                        }
                    }
                } catch (Exception e) {

                }
            }
        } else if (id == NotificationCenter.didUpdatedConnectionState) {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            updateCurrentConnectionState(account);
            Log.d("tdroid", "didUpdatedConnectionState status is" + state);
//            if (currentConnectionState != state) {
//                currentConnectionState = state;
//                updateCurrentConnectionState(account);
//            }
        }
    }

    private long lastCheckInConnectionLost;

    private void updateCurrentConnectionState(int account) {


        currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
        if (currentConnectionState == ConnectionsManager.ConnectionStateWaitingForNetwork) {

        } else if (currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {

        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //Do something after 100ms
                    if (currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {
                        currentPing = 10000;
                        checkProxyList();
//                        handler.postDelayed(this,10000);
                        Log.d("tdroid", "ConnectionStateConnectingToProxy" + " checkProxyList ");
                    }
                }
            }, 100);

        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting) {

        }

    }

    public static boolean isAutoProxy(Long chat) {
        return false;

    }

    public static void addToAutoProxy(Long chat) {
        Log.d("tdroid", "addToAutoProxy");


    }

    public static void removeFromAutoProxy(Long chat) {
        Log.d("tdroid", "removeFromAutoProxy");

    }


    long currentPing = 100000L;

    private void checkProxyList() {
        currentPing = 100000L;
        for (int a = 0, count = SharedConfig.proxyList.size(); a < count; a++) {
            final SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(a);
            if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000) {
                if (!proxyInfo.checking && proxyInfo.available) {
                    if (proxyInfo.ping < currentPing) {
                        SharedConfig.currentProxy = proxyInfo;
                        ConnectionsManager.setProxySettings(true, proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret);
                        currentPing = proxyInfo.ping;
                        Log.d("tdroid", "connect faster proxy");
                    }
                } else if (!proxyInfo.checking && SharedConfig.proxyList.size() > MIN_PROXY_COUNT) {
                    SharedConfig.deleteProxy(proxyInfo);
                    count = SharedConfig.proxyList.size();
                    Log.i("tdroid", "run: delete proxy ");
                }

                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);

                continue;
            }
            proxyInfo.checking = true;
            // todo mohammad check proxy ping
            proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy(proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret, new RequestTimeDelegate() {
                @Override
                public void run(final long time) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                            proxyInfo.checking = false;
                            if (time == -1) {
                                proxyInfo.available = false;
                                proxyInfo.ping = 0;
                                if (SharedConfig.proxyList.size() > MIN_PROXY_COUNT) {
                                    SharedConfig.deleteProxy(proxyInfo);
                                    Log.i("tdroid", "run: delete proxy ");
                                }
                            } else {
                                proxyInfo.ping = time;
                                proxyInfo.available = true;
                                if (proxyInfo.ping < currentPing) {
                                    SharedConfig.currentProxy = proxyInfo;
                                    ConnectionsManager.setProxySettings(true, proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret);
                                    currentPing = proxyInfo.ping;
                                    Log.d("tdroid", "connect faster proxy");
                                }
                            }
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
                        }
                    });
                }
            });
        }
    }

}
