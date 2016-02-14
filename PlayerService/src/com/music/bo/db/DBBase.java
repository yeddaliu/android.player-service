package com.music.bo.db;

import com.music.bo.db.SQLLiteConnector;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class DBBase {

	private SQLLiteConnector mDBConnector;
	private SQLiteDatabase mDB;

	public DBBase(SQLLiteConnector conn) {
		mDBConnector = conn;
	}

	protected SQLiteDatabase getReadableDatabase() {
		return mDBConnector.getReadableDatabase();
	}

	protected SQLiteDatabase getWritableDatabase() {
		return mDBConnector.getWritableDatabase();
	}

	/**
	 * close connection
	 */
	public void close() {
		mDBConnector.close();
		mDBConnector = null;
	}

	/**
	 * default insert command
	 */
	public long insert(String tableName, ContentValues values) throws SQLException {
		SQLiteDatabase db = this.getWritableDatabase();
		long id = db.insertOrThrow(tableName, null, values);
		db.close();
		return id;
	}

	/**
	 * default replace command
	 */
	public long replace(String tableName, ContentValues values) throws SQLException {
		SQLiteDatabase db = this.getWritableDatabase();
		long id = db.replaceOrThrow(tableName, null, values);
		db.close();
		return id;
	}

	/**
	 * default update command
	 */
	public int update(String tableName, ContentValues values, String whereClause, String[] whereArgs)
			throws SQLException {
		SQLiteDatabase db = this.getWritableDatabase();
		int affect = db.update(tableName, values, whereClause, whereArgs);
		db.close();
		return affect;
	}

	/**
	 * default delete command
	 */
	public int delete(String tableName, String whereClause, String[] whereArgs) throws SQLException {
		SQLiteDatabase db = getWritableDatabase();
		int affect = db.delete(tableName, whereClause, whereArgs);
		db.close();
		return affect;
	}

	/**
	 * default query command
	 */
	public Cursor query(String tableName, String[] projection, String selection, String[] selectionArgs, String groupBy,
			String having, String orderBy, String limit) throws SQLException {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(tableName, projection, selection, selectionArgs, groupBy, having, orderBy, limit);
		return cursor;
	}
}
