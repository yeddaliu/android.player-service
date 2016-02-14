package com.music.bo.data;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.omusic.helper.AppConf;
import com.omusic.util.Common;
import com.omusic.util.ConnectivityManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * cache user setting data
 */
public class SettingShareInfo {
	public static final int LOGIN_STATUS_TRY = 0;
	public static final int LOGIN_STATUS_ONLINE = 1;
	public static final int LOGIN_STATUS_OFFLINE = 2;

	public static SettingShareInfo instance;

	private static final String REF_NAME = "settingInfo";
	private static final String COL_LOGIN_STATUS = "login_status";
	private static final String COL_AUTO_LOGIN = "is_auto_login";
	private static final String COL_AUTO_PLAY = "is_auto_play";
	private static final String COL_PLAYLIST_DESC = "is_playlist_desc";
	private static final String COL_LOCAL_MP3_PATH = "local_mp3_path";
	private static final String COL_LOCAL_PLAYLIST_COUNTER = "local_playlist_count";
	// check point
	private static final String COL_AUTO_LOGIN_CANCEL = "is_cancel_auto_login_last_time";
	private static final String COL_UNPAID_SONG_COUNT_DATE = "unpaid_song_count_date";
	private static final String COL_UNPAID_SONG_COUNTER = "unpaid_song_count";

	private static final String COL_ACTIVE_FLAG = "is_active";
	private static final long DATE_SECONDS = 60 * 60 * 24 * 1000;

	private SharedPreferences ref;
	private String defValue = "";
	private int defIntValue = 0;
	private boolean defBolValue = false;

	private Context mContext;

	/**
	 * private constructor
	 */
	private SettingShareInfo(Context context) {
		this.ref = context.getSharedPreferences(REF_NAME, Context.MODE_PRIVATE);
		this.mContext = context;
		if (!this.isActive()) {
			this.setAutoLogin(true);
			this.setActive(true);
		}
	}

	/*
	 * get SettingShareInfo
	 */
	public static SettingShareInfo getInstance(Context context) {
		if (instance != null) {
			return instance;
		}
		instance = new SettingShareInfo(context);
		return instance;
	}

	/**
	 * set activation flag
	 * @param val if active, set TRUE, or set FALSE
	 */
	public void setActive(boolean val) {
		ref.edit().putBoolean(COL_ACTIVE_FLAG, val).commit();
	}

	public boolean isActive() {
		return ref.getBoolean(COL_ACTIVE_FLAG, defBolValue);
	}

	/**
	 * set current login status.
	 * @param val SettingShareInfo.LOGIN_STATUS_TRY, or
	 *            SettingShareInfo.LOGIN_STATUS_ONLINE, or
	 *            SettingShareInfo.LOGIN_STATUS_OFFLINE
	 */
	public void setLoginStatus(int val) {
		ref.edit().putInt(COL_LOGIN_STATUS, val).commit();
	}

	public int getLoginStatus() {
		int status = ref.getInt(COL_LOGIN_STATUS, defIntValue);
		return (status < SettingShareInfo.LOGIN_STATUS_TRY || status > SettingShareInfo.LOGIN_STATUS_OFFLINE) ? 0
				: status;
	}

	/**
	 * auto login flag
	 */
	public void setAutoLogin(boolean val) {
		ref.edit().putBoolean(COL_AUTO_LOGIN, val).commit();
	}

	public boolean isAutoLogin() {
		return ref.getBoolean(COL_AUTO_LOGIN, defBolValue);
	}

	/*
	 * cancel auto login when app launch next time
	 */
	public void setCancelAutoLogin(boolean val) {
		ref.edit().putBoolean(COL_AUTO_LOGIN_CANCEL, val).commit();
	}

	public boolean isCancelAutoLogin() {
		return ref.getBoolean(COL_AUTO_LOGIN_CANCEL, defBolValue);
	}

	/*
	 * auto play
	 */
	public void setAutopPlay(boolean val) {
		ref.edit().putBoolean(COL_AUTO_PLAY, val).commit();
	}

	public boolean isAutoPlay() {
		return ref.getBoolean(COL_AUTO_PLAY, defBolValue);
	}

	/*
	 * song ordering behavior in playlist
	 */
	public void setPlaylistDescOrder(boolean val) {
		ref.edit().putBoolean(COL_PLAYLIST_DESC, val).commit();
	}

	public boolean isPlaylistDescOrder() {
		return ref.getBoolean(COL_PLAYLIST_DESC, defBolValue);
	}

	/*
	 * local path of storage for songs
	 */
	public void setLocalMusicPath(String val) {
		ref.edit().putString(COL_LOCAL_MP3_PATH, val).commit();
	}

	public String getLocalMusicPath() {
		return ref.getString(COL_LOCAL_MP3_PATH, defValue);
	}

	/*
	 * count of custom playlist
	 */
	private void setLocalPlaylistCount(int val) {
		ref.edit().putInt(COL_LOCAL_PLAYLIST_COUNTER, val).commit();
	}

	public int getLocalPlaylistCount() {
		return ref.getInt(COL_LOCAL_PLAYLIST_COUNTER, defIntValue);
	}

	public int getNewLocalPlaylistCount() {
		int curCnt = this.getLocalPlaylistCount();
		if (++curCnt > 999) {
			curCnt = 1;
		}
		this.setLocalPlaylistCount(curCnt);
		return curCnt;
	}

	/*
	 * count of played songs of un-paid user
	 */
	private void setUnpaidSongCountDate(String val) {
		ref.edit().putString(COL_UNPAID_SONG_COUNT_DATE, val).commit();
	}

	public String getUnpaidSongCountDate() {
		return ref.getString(COL_UNPAID_SONG_COUNT_DATE, defValue);
	}

	private void setUnpaidSongCount(int val) {
		ref.edit().putInt(COL_UNPAID_SONG_COUNTER, val).commit();
	}

	public int getUnpaidSongCount() {
		return ref.getInt(COL_UNPAID_SONG_COUNTER, defIntValue);
	}

	public void addUnpaidSongCount() {
		int curCnt = this.getUnpaidSongCount();
		curCnt++;
		Log.d(AppConf.LOG_TAG3, "更新播放歌曲數：" + curCnt);
		this.setUnpaidSongCount(curCnt);
	}

	public boolean isUnpaidOverSongCredit() {

		try {
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));

			Date today = ConnectivityManager.getTimeServerTime(mContext);
			Calendar timeServer = Common.getTWCalendar(today);
			long timeServerMillis = timeServer.getTimeInMillis();

			Log.d(AppConf.LOG_TAG3, "today in default time zone ==" + today.toString());
			Log.d(AppConf.LOG_TAG3, "today Calendar == " + dateFormat.format(timeServer.getTime()));

			// when unpaid check date never set
			String checkDate = this.getUnpaidSongCountDate();
			if (checkDate.length() == 0) {
				this.setUnpaidSongCountDate(dateFormat.format(timeServer.getTime()));
				this.setUnpaidSongCount(0);
				return false;
			}

			Date dd = dateFormat.parse(checkDate);
			Calendar timeCheckDate = Common.getTWCalendar(dd);
			long timeCheckDateMillis = timeCheckDate.getTimeInMillis();
			Log.d(AppConf.LOG_TAG3, "unpaid song check date Calendar == " + dateFormat.format(timeCheckDate.getTime()));

			if (timeCheckDateMillis < timeServerMillis) {
				if ((timeCheckDateMillis + DATE_SECONDS) > timeServerMillis) {
					if (this.getUnpaidSongCount() >= AppConf.UNPAID_DAILY_SONG_CREDIT) {
						Log.d(AppConf.LOG_TAG3, "songs are over quota");
						return true;
					} else {
						return false;
					}
				} else {
					Log.d(AppConf.LOG_TAG3, "clean up earlier record");

					this.setUnpaidSongCountDate(dateFormat.format(timeServer.getTime()));
					this.setUnpaidSongCount(0);
					return false;
				}
			} else {
				this.setUnpaidSongCountDate(dateFormat.format(timeServer.getTime()));
				return true;
			}
		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG3, "unpaid check song date parse error", e);
			return true;
		}

	}

}
