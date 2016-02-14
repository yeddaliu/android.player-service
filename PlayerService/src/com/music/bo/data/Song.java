package com.music.bo.data;


import java.util.Hashtable;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import com.music.helper.DBConf;

public class Song extends DataObjectBase implements Parcelable {

	public static final Parcelable.Creator<Song> CREATOR = 
        new Parcelable.Creator<Song>() { 
			public Song createFromParcel(Parcel source) {
				return new Song(source);
			}
			public Song[] newArray(int size) {
				return new Song[size];
			}
    	};
	
	protected int mFlowID = 0;
	protected String mSongID = "";
	protected String mSongTitle = "";
	protected String mSingerID = "";
	protected String mSingerName = "";	
	protected String mAlbumID = "";
	protected String mAlbumTitle = "";
	protected String mCoverUrl = "";
	protected String mLrcText = "";
	protected String mPlayUrl = "";
	protected boolean isLocal = false;
	protected String mPlaylistID = "";
	
	/* from db */
	public Song(Cursor cursor, boolean isDownloading) {
		if (cursor!=null) {
			if (!isDownloading) {
				mFlowID = cursor.getInt(DBConf.TB_MyMusic.ID.getIndex());
				mSongID = cursor.getString(DBConf.TB_MyMusic.SongID.getIndex());
				mSongTitle = cursor.getString(DBConf.TB_MyMusic.SongTitle.getIndex());
				mSingerID = cursor.getString(DBConf.TB_MyMusic.SingerID.getIndex());
				mSingerName = cursor.getString(DBConf.TB_MyMusic.SingerName.getIndex());
				mAlbumID = cursor.getString(DBConf.TB_MyMusic.AlbumID.getIndex());
				mAlbumTitle = cursor.getString(DBConf.TB_MyMusic.AlbumTitle.getIndex());
				mCoverUrl = cursor.getString(DBConf.TB_MyMusic.CoverURL.getIndex());
				mLrcText = cursor.getString(DBConf.TB_MyMusic.LyricsText.getIndex());
				mPlayUrl = cursor.getString(DBConf.TB_MyMusic.MP3URL.getIndex());
				
				String localFlag = cursor.getString(DBConf.TB_MyMusic.IsLocal.getIndex());
				isLocal = (localFlag.compareTo("1")==0)?true:false;
				
				mPlaylistID = cursor.getString(DBConf.TB_MyMusic.PlaylistID.getIndex());
			}
			else {
				mFlowID = cursor.getInt(DBConf.TB_Download.ID.getIndex());
				mSongID = cursor.getString(DBConf.TB_Download.SongID.getIndex());
				mSongTitle = cursor.getString(DBConf.TB_Download.SongTitle.getIndex());
				mSingerID = cursor.getString(DBConf.TB_Download.SingerID.getIndex());
				mSingerName = cursor.getString(DBConf.TB_Download.SingerName.getIndex());
				mAlbumID = cursor.getString(DBConf.TB_Download.AlbumID.getIndex());
				mAlbumTitle = cursor.getString(DBConf.TB_Download.AlbumTitle.getIndex());
				mCoverUrl = cursor.getString(DBConf.TB_Download.CoverURL.getIndex());
				mLrcText = cursor.getString(DBConf.TB_Download.LyricsText.getIndex());
				mPlayUrl = cursor.getString(DBConf.TB_Download.MP3URL.getIndex());
				
				isLocal = true;
				mPlaylistID = "";
			}
		}
		else {
			isLocal = false;
		}
	}

	/**
     * This will be used only by the SongListCreator
     * @param source
     */
    public Song(Parcel source){
    	/* write & read member data in ORDER */
    	if (source!=null) {
        	mFlowID = source.readInt();
        	mSongID = source.readString();		
        	mSongTitle = source.readString();
        	mSingerID = source.readString();
        	mSingerName = source.readString();
        	mAlbumID = source.readString();
        	mAlbumTitle = source.readString();
        	mCoverUrl = source.readString();
        	mLrcText = source.readString();
        	mPlayUrl = source.readString();
        	isLocal  = (source.readInt()==1);
    	}
    }
    
	@Override
	public ContentValues getDataValues() {
		ContentValues value = new ContentValues();
		
		value.put(DBConf.TB_Download.ID.getName(), mFlowID);
		value.put(DBConf.TB_Download.SongID.getName(), mSongID);		
		value.put(DBConf.TB_Download.SongTitle.getName(), mSongTitle);
		value.put(DBConf.TB_Download.SingerID.getName(), mSingerID);
		value.put(DBConf.TB_Download.SingerName.getName(), mSingerName);
		value.put(DBConf.TB_Download.AlbumID.getName(), mAlbumID);
		value.put(DBConf.TB_Download.AlbumTitle.getName(), mAlbumTitle);
		value.put(DBConf.TB_Download.LyricsText.getName(), mLrcText);
		value.put(DBConf.TB_Download.MP3URL.getName(), mPlayUrl);
		value.put(DBConf.TB_Download.CoverURL.getName(), mCoverUrl);
		
		return value;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		/* write & read member data in ORDER */
		dest.writeInt(mFlowID);
		dest.writeString(mSongID);
		dest.writeString(mSongTitle);
		dest.writeString(mSingerID);
		dest.writeString(mSingerName);
		dest.writeString(mAlbumID);
		dest.writeString(mAlbumTitle);
		dest.writeString(mCoverUrl);
		dest.writeString(mLrcText);
		dest.writeString(mPlayUrl);
		dest.writeInt(isLocal? 1: 0);
	}
	    
	public int getID() {
		return mFlowID;
	}

	public String getSongID() {
		return mSongID;
	}

	public String getSongTitle() {
		return mSongTitle;
	}

	public String getSingerID() {
		return mSingerID;
	}
	
	public String getSingerName() {
		return mSingerName;
	}

	public String getAlbumID() {
		return mAlbumID;
	}
	
	public String getAlbumTitle() {
		return mAlbumTitle;
	}
	
	public String getCoverPictureUrl() {
		return mCoverUrl;
	}
	
	public String getLyricsText() {
		return mLrcText;
	}
	
	public String getPlayUrl() {
		return mPlayUrl;
	}
	
	public boolean isLocalFile() {
		return isLocal;
	}

	public String getPlaylistID() {
		return mPlaylistID;
	}
	
}
