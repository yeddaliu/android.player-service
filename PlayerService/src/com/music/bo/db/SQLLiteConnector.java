package com.music.bo.db;

import com.music.helper.AppConf;
import com.music.helper.DBConf;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class SQLLiteConnector extends SQLiteOpenHelper {
	private static int CREATE_COUNT = 0;
	private static int UPGRADE_COUNT = 0;

	public SQLLiteConnector(Context context) {
		super(context, DBConf.DB_NAME, null, DBConf.DB_VERSION);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite
	 * .SQLiteDatabase)
	 */
	@Override
	public void onCreate(SQLiteDatabase database) {
		if (++SQLLiteConnector.CREATE_COUNT > 1)
			return;
		Log.d(AppConf.LOG_TAG, "Create database:" + SQLLiteConnector.CREATE_COUNT);

		DBConf.TABLE_SCHEMAS[] schemas = DBConf.TABLE_SCHEMAS.values();
		for (int i = 0; i < schemas.length; i++) {
			try {
				database.execSQL(schemas[i].getCreationSchema());

				String[] indexs = schemas[i].getCreationIndexs();
				if (indexs != null) {
					for (int j = 0; j < indexs.length; j++) {
						database.execSQL(indexs[j]);
					}
				}
			} catch (SQLException e) {
				Log.e(AppConf.LOG_TAG, "Failed to create database", e);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.
	 * sqlite.SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		if (++SQLLiteConnector.UPGRADE_COUNT > 1)
			return;

		Log.d(AppConf.LOG_TAG,
				"Upgrade database:" + SQLLiteConnector.UPGRADE_COUNT + ", new=" + newVersion + "; old=" + oldVersion);
		Log.d(AppConf.LOG_TAG, "database version=" + database.getVersion());
		if (newVersion > oldVersion) {
			Log.d(AppConf.LOG_TAG, "upgrade to new version of database:" + newVersion);

			DBConf.TABLE_SCHEMAS[] schemas = DBConf.TABLE_SCHEMAS.values();
			for (int i = 0; i < schemas.length; i++) {
				try {
					database.execSQL("DROP TABLE IF EXISTS " + schemas[i].getTableName());
					database.execSQL(schemas[i].getCreationSchema());
					String[] indexs = schemas[i].getCreationIndexs();
					if (indexs != null) {
						for (int j = 0; j < indexs.length; j++) {
							database.execSQL(indexs[j]);
						}
					}
				} catch (SQLException e) {
					Log.e(AppConf.LOG_TAG, "Failed to create database", e);
				}
			}
		}
	}
}
