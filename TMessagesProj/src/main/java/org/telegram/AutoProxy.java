package org.telegram;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestTimeDelegate;

public class AutoProxy {

    static String user = null;
    static String password = null;
    static String port = null;
    static String address = null;
    static String secret = null;

    public static boolean addAutoProxy(String urlProxy) {

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

                    int currentAccount = UserConfig.selectedAccount;
                    final SharedConfig.ProxyInfo proxyInfo = new SharedConfig.ProxyInfo(secret,Integer.valueOf(port),user,password,secret);
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

    private static void addProxy() {
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

        SharedConfig.currentProxy = SharedConfig.addProxy(info);

        ConnectionsManager.setProxySettings(true, address, p, user, password, secret);

    }


}
