package org.telegram;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestTimeDelegate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoProxy implements NotificationCenter.NotificationCenterDelegate {

    private static final int MIN_PROXY_COUNT = 15;
    static int currentAccount = UserConfig.selectedAccount;
    private int currentConnectionState;

    private boolean useAutoProxySetting;
    private boolean useAutoBestPingProxySetting;
    private boolean useProxySettings;
    private SharedPreferences preferences;

    public AutoProxy() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceiveNewMessages);
        preferences = MessagesController.getGlobalMainSettings();
        useProxySettings = preferences.getBoolean("proxy_enabled", false) && !SharedConfig.proxyList.isEmpty();
        useAutoProxySetting = preferences.getBoolean("proxy_auto_enabled", true);
        useAutoBestPingProxySetting = preferences.getBoolean("proxy_auto_best_ping_enabled", true);

    }

    public void addAutoProxy(String urlProxy) {

        String user;
        String password;
        String port;
        String address;
        String secret;


        List<String> urls = extractUrls(urlProxy);

        for (int i = 0; i < urls.size(); i++) {
            try {
                Uri data = Uri.parse(urls.get(i));
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
                        String finalUser = user;
                        String finalPassword = password;
                        String finalPort = port;
                        String finalAddress = address;
                        String finalSecret = secret;
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
                                            addProxy(finalUser, finalPassword, finalPort, finalAddress, finalSecret);
                                        }

                                    }
                                });
                            }
                        });


                    }
                }
            } catch (Exception ignore) {

            }
        }


    }

    private void addProxy(String user,
                          String password,
                          String port,
                          String address,
                          String secret
                          ) {

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
        //  editor.commit();

        SharedConfig.addProxy(info);

//        SharedConfig.currentProxy = SharedConfig.addProxy(info);
//
//        ConnectionsManager.setProxySettings(true, address, p, user, password, secret);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        checkProxyList();

    }


    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged) {

            useProxySettings = preferences.getBoolean("proxy_enabled", false) && !SharedConfig.proxyList.isEmpty();
            useAutoProxySetting = preferences.getBoolean("proxy_auto_enabled", true);
            useAutoBestPingProxySetting = preferences.getBoolean("proxy_auto_best_ping_enabled", true);
        }
        if (id == NotificationCenter.didReceiveNewMessages) {

            if(!useProxySettings) {
                return;
            }
            if(!useAutoProxySetting) {
                return;
            }
            Log.d("tdroid", "id is " + id);
            Long dialogId = (Long) args[0];
            ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
            for (int i = 0; i < messageObjects.size(); i++) {
                addAutoProxy(messageObjects.get(i).messageText.toString());
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
        } else if (id == NotificationCenter.didUpdateConnectionState) {

            if(!useProxySettings) {
                return;
            }
            if(!useAutoProxySetting) {
                return;
            }

            if(!useAutoBestPingProxySetting) {
                return;
            }

            final Handler handler = new Handler();
            handler.postDelayed(() -> {
                int state = ConnectionsManager.getInstance(account).getConnectionState();
                if (state != ConnectionsManager.ConnectionStateConnected) {
                    updateCurrentConnectionState();
                }

            }, 500);
        }
    }

    private long delayTimeInConnectionLost = 2000;

    private void updateCurrentConnectionState() {
        checkProxyList();

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
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                        currentPing = proxyInfo.ping;
                        Log.d("tdroid", "connect faster proxy");
                    }
                } else if (!proxyInfo.checking && SharedConfig.proxyList.size() > MIN_PROXY_COUNT && !proxyInfo.available) {
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
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);

                        }
                    });
                }
            });
        }
    }


    public static List<String> extractUrls(String text) {
        List<String> containedUrls = new ArrayList<String>();
        String urlRegex = "((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
        Pattern pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
        Matcher urlMatcher = pattern.matcher(text);

        while (urlMatcher.find()) {
            containedUrls.add(text.substring(urlMatcher.start(0),
                    urlMatcher.end(0)));
        }

        return containedUrls;
    }

}
