package com.music.helper;

public interface DBConf {

	/*
	 * sqlite db info
	 */
	static final int DB_VERSION = 1;
	static final String DB_NAME = "music.db";

	/*
	 * table list
	 */
	static enum TABLE_SCHEMAS {
		PlayCount("play_count",
			"CREATE TABLE IF NOT EXISTS play_count  (" 
				+ "_id INTEGER PRIMARY KEY ASC AUTOINCREMENT, "
				+ "song_id varchar(35) NOT NULL, "
				+ "singer_id char(11) NOT NULL, "
				+ "album_id char(11) NOT NULL) ",
			null),
		DownloadCount("download_count",
			"CREATE TABLE IF NOT EXISTS download_count  ("
				+ "_id INTEGER PRIMARY KEY ASC AUTOINCREMENT, "
				+ "song_id varchar(35) NOT NULL, "
				+ "singer_id char(11) NOT NULL, "
				+ "album_id char(11) NOT NULL) ",
			null),
		ErrorLog("error_log",
			"CREATE TABLE IF NOT EXISTS error_log  ("
					+ "_id INTEGER PRIMARY KEY ASC AUTOINCREMENT, "
					+ "song_id varchar(35) DEFAULT 'N/A', "
					+ "singer_id char(11) DEFAULT 'N/A', "
					+ "album_id char(11) DEFAULT 'N/A', "
					+ "err_msg varchar(255) NOT NULL) ",
			null);

		private String tableName;
		private String ddlCreate;
		private String[] ddlIndex;

		TABLE_SCHEMAS(String tableName, String createSchema, String[] createIndex) {
			this.tableName = tableName;
			this.ddlCreate = createSchema;
		}

		public String getTableName() {
			return tableName;
		}

		public String getCreationSchema() {
			return ddlCreate;
		}

		public String[] getCreationIndexs() {
			return ddlIndex;
		}
	}

	/*
	 * table column schema
	 *
	 */
	static enum TB_PlayCount {
		ID("_id", 0), SongID("song_id", 1), SingerID("singer_id", 2), AlbumID("album_id", 3);

		private String colName;
		private int colIdx;

		TB_PlayCount(String val1, int val2) {
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

	static enum TB_DownloadCount {
		ID("_id", 0), SongID("song_id", 1), SingerID("singer_id", 2), AlbumID("album_id", 3);

		private String colName;
		private int colIdx;

		TB_DownloadCount(String val1, int val2) {
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

	static enum TB_ErrLog {
		ID("_id", 0), SongID("song_id", 1), SingerID("singer_id", 2), AlbumID("album_id", 3), ErrMsg("err_msg", 4);

		private String colName;
		private int colIdx;

		TB_ErrLog(String val1, int val2) {
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
}
