package com.music.bo.db;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.dom4j.Document;
import org.dom4j.Element;

import com.music.bo.data.Song;
import com.music.command.ReportCommand;
import com.music.helper.AppConf;
import com.music.helper.DBConf;
import com.music.helper.ErrorCode;
import com.music.service.PlayerService;
import com.music.util.XMLBuilder;
import com.music.util.Base64;
import com.music.xmlcontent.GeneralXMLContent;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public final class DBLogging extends DBBase {

	public static char DOWNLOAD_COUNT = 'd';
	public static char PLAY_COUNT = 'p';
	public static char ERR_MSG = 'e';

	/* add by Yedda 2011-10-21 */
	public static enum CORSOR_LOGCOUNT {
		SingerID("singer_id", 0), AlbumID("album_id", 1), SongID("song_id", 2), PlayCount("cnt", 3),;

		private String colName;
		private int colIdx;

		CORSOR_LOGCOUNT(String val1, int val2) {
			this.colName = val1;
			this.colIdx = val2;
		}

		public String getName() {
			return colName;
		}

		public int getIndex() {
			return colIdx;
		}

	}

	private Context mContext;

	public DBLogging(Context context) {
		super(new SQLLiteConnector(context));
		this.mContext = context;
	}

	/**
	 * Add log queue
	 */
	public long addQueue(ContentValues values, char type) throws SQLException {
		Log.d(AppConf.LOG_TAG, "Add logging queue: type=" + type);

		String tableName = "";
		if (type == DOWNLOAD_COUNT) {
			tableName = DBConf.TABLE_SCHEMAS.DownloadCount.getTableName();
		} else if (type == PLAY_COUNT) {
			tableName = DBConf.TABLE_SCHEMAS.PlayCount.getTableName();
		} else if (type == ERR_MSG) {
			tableName = DBConf.TABLE_SCHEMAS.ErrorLog.getTableName();
		}
		long flow = super.insert(tableName, values);
		Log.d(AppConf.LOG_TAG, "insert logging records ==" + flow);
		return flow;
	}

	/**
	 * delete log queue
	 */
	public int delQueue(ArrayList idL, char type) throws SQLException {
		String where = null;
		String tableName = null;
		for (int i = 0; i < idL.size(); i++) {
			if (type == DOWNLOAD_COUNT) {
				if (i == 0) {
					where = DBConf.TB_DownloadCount.ID.getName() + "in(" + idL.get(i);
					tableName = DBConf.TABLE_SCHEMAS.DownloadCount.getTableName();
				} else {
					where += "," + idL.get(i);
				}
			} else if (type == PLAY_COUNT) {
				if (i == 0) {
					where = DBConf.TB_PlayCount.ID.getName() + "in(" + idL.get(i);
					tableName = DBConf.TABLE_SCHEMAS.PlayCount.getTableName();
				} else {
					where += "," + idL.get(i);
				}
			} else if (type == ERR_MSG) {
				if (i == 0) {
					where = DBConf.TB_ErrLog.ID.getName() + "in(" + idL.get(i);
					tableName = DBConf.TABLE_SCHEMAS.ErrorLog.getTableName();
				} else {
					where += "," + idL.get(i);
				}
			}
		}
		if (where != null && where.indexOf("(") != -1) {
			where += ")";
		}
		int affect = super.delete(tableName, where, null);
		return affect;
	}

	public Cursor getCountLog(char type) {
		String where = String.format("%s, %s, %s, COUNT(*) as cnt", DBConf.TB_PlayCount.SingerID.getName(),
				DBConf.TB_PlayCount.AlbumID.getName(), DBConf.TB_PlayCount.SongID.getName());
		String group = String.format("%s, %s, %s", DBConf.TB_PlayCount.SingerID.getName(),
				DBConf.TB_PlayCount.AlbumID.getName(), DBConf.TB_PlayCount.SongID.getName());
		String tblName = "";
		if (type == DOWNLOAD_COUNT) {
			tblName = DBConf.TABLE_SCHEMAS.DownloadCount.getTableName();
		} else {
			tblName = DBConf.TABLE_SCHEMAS.PlayCount.getTableName();
		}

		Cursor cursor = this.query(tblName, null, where, null, group, null, null, null);
		return cursor;
	}

	public Cursor getErrorLog() {
		Cursor cursor = this.query(DBConf.TABLE_SCHEMAS.ErrorLog.getTableName(), null, null, null, null, null, null,
				null);
		return cursor;
	}
}
