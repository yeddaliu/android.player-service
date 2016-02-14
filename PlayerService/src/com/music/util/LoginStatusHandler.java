package com.music.util;

import com.music.bo.data.SettingShareInfo;
import android.content.Context;

public class LoginStatusHandler {

	public static void settingForOffline(Context context) {
		SettingShareInfo.getInstance(context).setLoginStatus(SettingShareInfo.LOGIN_STATUS_OFFLINE);
	}

	public static void settingForTry(Context context) {
		SettingShareInfo.getInstance(context).setLoginStatus(SettingShareInfo.LOGIN_STATUS_TRY);
	}

	public static void settingForOnline(Context context, boolean autoLogin) {
		SettingShareInfo.getInstance(context).setLoginStatus(SettingShareInfo.LOGIN_STATUS_ONLINE);
		SettingShareInfo.getInstance(context).setAutoLogin(autoLogin);
	}

	public static boolean isOnline(Context context) {
		return (SettingShareInfo.getInstance(context).getLoginStatus() == SettingShareInfo.LOGIN_STATUS_ONLINE);
	}

	public static boolean isTry(Context context) {
		return (SettingShareInfo.getInstance(context).getLoginStatus() == SettingShareInfo.LOGIN_STATUS_TRY);
	}

	public static boolean isOffline(Context context) {
		return (SettingShareInfo.getInstance(context).getLoginStatus() == SettingShareInfo.LOGIN_STATUS_OFFLINE);
	}

}
