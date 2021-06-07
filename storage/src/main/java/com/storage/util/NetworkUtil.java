package com.storage.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkUtil {
    private static final String TAG = "DVR-" + NetworkUtil.class.getSimpleName();

    public enum NetType {
        /**
         * wifi网络
         */
        WIFI,

        /**
         * 移动网络
         */
        MOBILE,

        /**
         * 其他网络
         */
        UNKNOWN,

        /**
         * 没有任何网络
         */
        NONE
    }

    /**
     * 获取网络类型
     *
     * @return {@link NetType}
     */
    public static NetType getNetType(Context context) {
        NetworkInfo networkInfo = ((ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

        if (null == networkInfo) {
            return NetType.NONE;
        }

        int type = networkInfo.getType();

        if (ConnectivityManager.TYPE_MOBILE == type) {
            return NetType.MOBILE;
        } else if (ConnectivityManager.TYPE_WIFI == type) {
            return NetType.WIFI;
        }

        return NetType.UNKNOWN;
    }

    /**
     * 判断网络是否可用
     *
     * @return True if available, or false.
     */
    public static boolean isNetworkAvailable(Context context) {
        NetworkInfo networkInfo = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

        if (null == networkInfo) {
            return false;
        }

        return networkInfo.isConnected();
    }
}
