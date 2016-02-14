package com.music.helper;

import android.os.Environment;

public interface AppConf {
	/* debug */
	static final boolean DEBUG = true;
	static final String LOG_TAG = "music";
	static final String LOG_TAG2 = "musicDownload";
	static final String LOG_TAG3 = "musicPlayer";
	static final String LOG_TAG4 = "musicPlayerService";

	/* app settings */
	static final String APP_VERSION = "0.1";
	static final String APP_PACKAGE_NAME = "net.smart.appstore.client";
	static final String ACTION_FILTER_PLAYER = "com.music.service.PLAYER";
	static final String APP_AUTHORITY = "com.music";

	/* data path */
	static final String APP_SDROOT = "/musicPlayer";
	static final String LOCAL_MP3_SDROOT = APP_SDROOT + "/local";

	/* XML Communication */
	static final String XMLAPI_CHECKVERSION_URL = "http://192.168.0.1/api/version";
	static final String FROM_DEVICE_ID = "android";

	/* music config */
	static final String ONLINE_STREAM_EXT = ".3gp";
	static final int UNPAID_DAILY_SONG_CREDIT = 5;
	static final int PLAY_RECORDED_SECONDS = 30;
	static final int MAX_PLAY_ERROR_COUNT = 10;

	/* download config */
	static final String DOWNLAOD_MP3_EXT = ".3pm";
	static final String DOWNLAOD_COVER_EXT = ".jpg";
	static final String DOWNLAOD_MP3_SDROOT = APP_SDROOT + "/mp3";
	static final String DOWNLAOD_COVER_SDROOT = APP_SDROOT + "/cover";
	static final String DOWNLAOD_USER_AGENT = "musicOlayer";

	/* playlist default config */
	static final int FAVORITE_PLAY_TOP = 30;
	static final int RECENT_PLAY_TOP = 30;

	/* UI setting */
	static final int DEFAULT_PAGE_RECORDS = 10;
}
