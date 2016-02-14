package com.music.service;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Random;

import com.music.R;
import com.music.app.Player;
import com.music.bo.BOException;
import com.music.bo.data.CurrentPlayShareInfo;
import com.music.bo.data.SettingShareInfo;
import com.music.bo.data.Song;
import com.music.bo.data.UserLoginShareInfo;
import com.music.bo.db.DBDownload;
import com.music.bo.db.DBLogging;
import com.music.bo.db.DBMyMusic;
import com.music.bo.db.DBPlaySongCache;
import com.music.bo.db.DBPlayerSongList;
import com.music.helper.AppConf;
import com.music.helper.DBConf;
import com.music.helper.ErrorCode;
import com.music.util.LoginStatusHandler;
import com.music.util.MusicUtil;
import com.music.util.XMLCommandGateway;
import com.music.util.XMLContentHandler;
import com.music.xmlcontent.SongInfoXMLContent;
import com.music.xmlcontent.XMLContent;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.SQLException;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerService extends Service {
	/* playing song status */
	public static final int SONG_TOAST_ERROR = -4;
	public static final int SONG_DIALOG_ERROR = -3;
	public static final int SONG_PREPARE_ERROR = -2; // no use currently
	public static final int SONG_PLAY_ERROR = -1; // show toast msg
	public static final int SONG_LOADING = 0;
	public static final int SONG_PLAY_COMPLETION = 1;
	public static final int SONG_SEEK_COMPLETION = 2;
	public static final int SONG_PREPARE_OK = 3;
	public static final int SONG_INCOMING_CALL = 4;
	public static final int SONG_PLAY_STOP = 5;
	public static final int SONG_PLAY_PAUSE = 6;
	public static final int SONG_INFO_READY = 7;
	public static final int SONG_RESET = 8;

	/* playing song display message type */
	public static final int PLAYER_MSG_BACKEND = 0;
	public static final int PLAYER_MSG_TRY_DOWNLOAD = 1;
	public static final int PLAYER_MSG_UNPAID_DOWNLOAD = 2;
	public static final int PLAYER_MSG_OFFLINE_STREAM = 3;
	public static final int PLAYER_MSG_MULTILOGIN = 4;
	public static final int PLAYER_MSG_NOSONGLIST = 5;
	public static final int PLAYER_MSG_PLAYER_CRASH = 6;
	public static final int PLAYER_MSG_OVER_CREDIT = 7;
	public static final int PLAYER_MSG_2MANAY_ERROR = 8;

	/* control over credit response action */
	public static final int OVER_ACTION_PLAY = 0;
	public static final int OVER_ACTION_STOP = 1;

	/* control repeat flag */
	private static final int LOOP_NO_REPEAT = 0;
	private static final int LOOP_SINGLE_REPEAT = 1;
	private static final int LOOP_PLAYLIST_REPEAT = 2;

	/* display action flag */
	private static final int DISPLAY_NOINFO = 0;
	private static final int DISPLAY_SONGINFO = 1;
	private static final int DISPLAY_STREAM = 2;
	private static final int DISPLAY_TIMEOUT = 3;
	private static final int DISPLAY_ERRINFO = 4;
	private static final int DISPLAY_MULTILOGIN = 5;

	private static final int LOAD_SONG_WAIT_TIMES = 15;

	private PhoneStatReceiver phoneStatReceiver;
	private HeadsetConnectionReceiver headsetConnectionReceiver;
	private Thread songInfoMonitoringThread;
	private Thread prepareSongStreamThread;
	private Thread playPosThread;

	private Context mContext;
	private DBPlayerSongList dbPlaylist;
	private static MediaPlayer mPlayer;
	private ArrayList<Song> songList = new ArrayList<Song>();
	private int curPlayingIndex = -1;
	private HashSet<String> loadedSongMap = new HashSet<String>();
	private Song curPlaySongInfo;

	private boolean shuffleMode = false;
	private boolean isOverUnpaidCredit = false;
	private boolean playClickSeek = false;
	private int loopMode = LOOP_NO_REPEAT; /*
											 * 0: no repeat , 1 : single repeat ,
											 * 2 : playlist repeat
											 */
	private boolean isPlaying = false;
	private boolean isIllegalState = false;
	private boolean isPosLoged = false;
	private boolean isXMLListenerRun = true;
	private int isSongInfoLoaded = 0;
	private int isDisplayInfo = 0;
	private int isSongPrepared = 0;
	private int isAlbumLoaded = 0;
	private boolean isWaitingErrorStopResponse = false;
	private boolean isSongLoading = false;
	private boolean isComingCall = false;
	private boolean isComingCallPalying = false;
	private String curPlayLocalSongUrl;
	private int playErrorCount = 0;
	private boolean isSeriesError = false;

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(AppConf.LOG_TAG4, "PlayerService onBind()");
		// use local bind
		return new PlayerBinder();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d(AppConf.LOG_TAG4, "PlayerService onUnbind()");
		// use local bind
		return true;
	}

	/* local bind class, return Player Service self */
	public class PlayerBinder extends Binder {
		public PlayerService getService() {
			return PlayerService.this;
		}
	}

	@Override
	public void onCreate() {
		Log.d(AppConf.LOG_TAG4, "PlayerService onCreate()");

		/* database */
		if (mContext == null) {
			mContext = this.getApplicationContext();
		}
		dbPlaylist = new DBPlayerSongList(mContext);

		/* craete media player & register actions */
		mPlayer = new MediaPlayer();
		mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

		mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				Log.d(AppConf.LOG_TAG4, "PlayerService player onPreparedListener() enter");
				/*
				 * media player play step
				 * 
				 * android2.3 1. MediaPlayer.OnPreparedListener() 2.
				 * MediaPlayer.OnSeekCompleteListener() 3. start to play 4.
				 * MediaPlayer.OnCompletionListener()
				 * 
				 * android2.1 1. MediaPlayer.OnPreparedListener() 2.
				 * MediaPlayer.OnSeekCompleteListener() 3. start to play
				 */
				isIllegalState = false;
				broadcast(SONG_PREPARE_OK, null);

			}
		});

		mPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
			@Override
			public void onSeekComplete(MediaPlayer mp) {
				Log.d(AppConf.LOG_TAG4, "PlayerService player onSeekComplete() enter");
				if (playClickSeek == true) {
					Log.d(AppConf.LOG_TAG4, "PlayerService player onSeekComplete() is seeking");
					broadcast(SONG_SEEK_COMPLETION, null);
					playClickSeek = false;
				}

				// start to play
				if (isComingCall == false) {
					Log.d(AppConf.LOG_TAG4, "SONG PLAY START");
					try {
						isPlaying = true;
						PlayerService.this.start();
					} catch (Exception e) {
						// play next song, do not show error dialog 2011-10-19
						broadcast(SONG_PLAY_ERROR, null);
						try {
							PlayerService.this.toNextSong();
						} catch (Exception eNext) {
							Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
							reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
							Hashtable<String, String> htNext = new Hashtable<String, String>();
							htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
							broadcast(SONG_DIALOG_ERROR, htNext);
						}
					}
				} else {
					Log.d(AppConf.LOG_TAG4, "SONG PLAY PAUSE cause incoming call");
				}
			}
		});
		mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
			public void onCompletion(MediaPlayer mp) {
				Log.d(AppConf.LOG_TAG4, "PlayerService player onCompletion() enter");

				if (isPlaying) {
					Log.d(AppConf.LOG_TAG4, "PlayerService player onCompletion() isPlaying");

					if (playPosThread != null) {
						Log.d(AppConf.LOG_TAG4, "Stop playing, and stop observing timestamp");
						playPosThread.interrupt();
						playPosThread = null;
					}

					try {
						if (curPlaySongInfo.isLocalFile() == true) {
							File destFile = new File(curPlayLocalSongUrl);
							if (destFile.exists()) {
								destFile.delete();
							}
						}
						broadcast(SONG_PLAY_COMPLETION, null);

						try {
							PlayerService.this.toNextSong();
						} catch (Exception eNext) {
							Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
							reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
							Hashtable<String, String> htNext = new Hashtable<String, String>();
							htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
							broadcast(SONG_DIALOG_ERROR, htNext);
						}
					} catch (Exception e) {
						Log.e(AppConf.LOG_TAG4, "player onCompletion exception", e);
						PlayerService.this.reportLog(curPlaySongInfo,
								"player onCompletion exception:" + e.getMessage());
					} finally {
						isPlaying = false;
					}
				}
			}
		});
		mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				Log.d(AppConf.LOG_TAG4, "PlayerService player onError() enter:" + what);
				return false;
			}
		});

		// audio control
		// AudioManager mAudioManager = (AudioManager)
		// getSystemService(AUDIO_SERVICE);
		// mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
		// AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
		// mAudioManager.adjustVolume(AudioManager.ADJUST_SAME,
		// AudioManager.FLAG_SHOW_UI);

		/* register recievers */
		IntentFilter intentFilter = null;
		Log.d(AppConf.LOG_TAG4, "PlayerService PhoneStatReceiver Start");
		phoneStatReceiver = new PhoneStatReceiver();
		intentFilter = new IntentFilter();
		intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
		registerReceiver(phoneStatReceiver, intentFilter);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(AppConf.LOG_TAG4, "PlayerService onStartCommand().");
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.d(AppConf.LOG_TAG4, "PlayerService onDestroy()");

		if (dbPlaylist != null) {
			dbPlaylist.close();
			dbPlaylist = null;
		}

		if (mPlayer != null) {
			/* might has bug */
			if (mPlayer.isPlaying()) {
				mPlayer.stop();
			}
			mPlayer.release();
			mPlayer = null;
		}

		unregisterReceiver(phoneStatReceiver);
	}

	/**
	 * public methods for service binder
	 */
	public void setSongList(int newIndex, ArrayList<Song> newList) throws Exception {
		Log.d(AppConf.LOG_TAG4, "PlayerService setSongList()");

		if (newIndex == -1) {
			return;
		}

		/*
		 * reset playlist
		 */
		boolean isSameSongPlaying = false;
		boolean isPlaylistLoaded = false;
		Song newSong = null;

		try {
			if (newList == null || newList.size() == 0) {
				Log.e(AppConf.LOG_TAG4, "service get null song list");
				reportLog(null, "player service get null song list, it should not be.");

				curPlayingIndex = -1;
				Hashtable<String, String> ht = new Hashtable<String, String>();
				ht.put("msg_type", String.valueOf(PLAYER_MSG_NOSONGLIST));
				broadcast(SONG_DIALOG_ERROR, ht);
				return;
			} else {
				Log.d(AppConf.LOG_TAG4, "curPlayingIndex = " + newIndex);
				Log.d(AppConf.LOG_TAG4, "curPlayingIndex = " + newList.size());

				if (mPlayer.isPlaying()) {
					newSong = newList.get(newIndex);
					if (curPlaySongInfo != null && newSong != null) {
						if (newSong.getSongID().equals(curPlaySongInfo.getSongID())) {
							isSameSongPlaying = true;
						}
					}
					if (!isSameSongPlaying) {
						this.stop();
						broadcast(SONG_PLAY_STOP, null);
					}
				}

				if (newList.size() == songList.size()) {
					boolean ck1 = false;
					boolean ck2 = false;
					boolean ck3 = false;
					Song curSong = null;

					newSong = newList.get(curPlayingIndex);
					curSong = songList.get(curPlayingIndex);
					if (curSong != null && newSong != null) {
						if (newSong.getSongID().equals(curPlaySongInfo.getSongID())) {
							ck1 = true;
						}
					}
					if (ck1) {
						newSong = newList.get(0);
						curSong = songList.get(0);
						if (curSong != null && newSong != null) {
							if (newSong.getSongID().equals(curPlaySongInfo.getSongID())) {
								ck2 = true;
							}
						}
					}
					if (ck2) {
						newSong = newList.get(songList.size() - 1);
						curSong = songList.get(songList.size() - 1);
						if (curSong != null && newSong != null) {
							if (newSong.getSongID().equals(curPlaySongInfo.getSongID())) {
								ck3 = true;
							}
						}
					}
					isPlaylistLoaded = (ck1 & ck2 & ck3);
					curSong = null;
				}

				if (isPlaylistLoaded) {
					curPlayingIndex = newIndex;
				} else {
					// reset song list
					curPlayingIndex = newIndex;
					songList = newList;
					loadedSongMap.clear();

					// update play song cache
					try {
						// get current cache count
						int total = 0;
						try {
							total = dbPlaylist.getAllSongsCount();
						} catch (SQLException sqle) {
						}

						if (total > 0) {
							// clear all cache
							int affect = dbPlaylist.delAllSongs();
							if (affect > 0) {
								cachePlayingList();
							} else {
								Log.e(AppConf.LOG_TAG4, "unable to clear cached player songs.");
								reportLog(songList.get(curPlayingIndex), "unable to clear cached player songs");
							}
						} else {
							cachePlayingList();
						}
					} catch (SQLException sqle) {
						Log.e(AppConf.LOG_TAG4, "update cached player songs exception", sqle);
						reportLog(songList.get(curPlayingIndex),
								"update cached player songs exception:" + sqle.getMessage());
					}
				}
			}

			// if playing same song, keep playing and notify receiver to refresh
			// layout
			if (isSameSongPlaying) {
				broadcast(SONG_PREPARE_OK, null);
			} else {
				loadingSongInfo();
			}
		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG4, "setSongList exception", e);
			reportLog(newList.get(newIndex), "setSongList exception:" + e.getMessage());
			// unknown error, play next song
			broadcast(SONG_PLAY_ERROR, null);
			try {
				this.toNextSong();
			} catch (Exception eNext) {
				Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
				reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
				Hashtable<String, String> htNext = new Hashtable<String, String>();
				htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
				broadcast(SONG_DIALOG_ERROR, htNext);
			}
		} finally {
			newSong = null;
		}
	}

	public void playCurrentSong() {
		// Log.d(AppConf.LOG_TAG4, "PlayerService playCurrentSong()");
		if (mPlayer.isPlaying()) {
			return;
		}
		loadingSongInfo();
	}

	public void startAutoPlay() {
		Log.d(AppConf.LOG_TAG4, "PlayerService startAutoPlay()");

		if (mPlayer.isPlaying()) {
			mPlayer.stop();
			mPlayer.reset();
		}

		Cursor cursor = null;
		try {
			cursor = dbPlaylist.getAllSongs();
			if (cursor.getCount() > 0) {
				songList.clear();
				cursor.moveToFirst();
				while (!cursor.isAfterLast()) {
					// not downloading data
					songList.add(new Song(cursor, false));
					if (cursor.getString(DBConf.TB_PlayerSongListCache.IsCurrentSong.getIndex())
							.equalsIgnoreCase("Y")) {
						curPlayingIndex = cursor.getInt(DBConf.TB_PlayerSongListCache.ListNo.getIndex());
					}
					cursor.moveToNext();
				}

				Log.d(AppConf.LOG_TAG4, "PlayerService startAutoPlay() loadingSongInfo()");
				loadedSongMap.clear();
				loadingSongInfo();
			}
		} catch (SQLException sqle) {
			Log.e(AppConf.LOG_TAG4, "get cached player songs exception", sqle);
			reportLog(songList.get(curPlayingIndex), "get cached player songs exception:" + sqle.getMessage());
		} finally {
			if (cursor != null) {
				cursor.close();
				cursor = null;
			}
		}
	}

	public void setAlbumShowNotify() {
		isAlbumLoaded = 1;
	}

	public void start() {
		Log.d(AppConf.LOG_TAG4, "PlayerService start()");
		try {
			/*
			 * android 2.1 won't run OnSeekCompleteListener
			 */
			if (!mPlayer.isPlaying()) {
				if (!isPosLoged && LoginStatusHandler.isTry(PlayerService.this) == false) {
					PlayerService.this.checkPlayPosition();
				}
				mPlayer.start();
				// for MediaPlayer.OnCompletionListener()
				isPlaying = true;
			}
		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG4, "PlayerService start() Exception", e);
			reportLog(songList.get(curPlayingIndex), "PlayerService start() Exception: " + e.getMessage());

			Hashtable<String, String> htNext = new Hashtable<String, String>();
			htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
			broadcast(SONG_DIALOG_ERROR, htNext);
		}
	}

	public void pause() {
		Log.d(AppConf.LOG_TAG4, "PlayerService pause()");
		try {
			if (mPlayer.isPlaying()) {
				mPlayer.pause();
				isPlaying = false;
				broadcast(SONG_PLAY_PAUSE, null);
			}

		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG4, "PlayerService pause() Exception", e);
			reportLog(songList.get(curPlayingIndex), "PlayerService pause() Exception:" + e.getMessage());

			Hashtable<String, String> htNext = new Hashtable<String, String>();
			htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
			broadcast(SONG_DIALOG_ERROR, htNext);
		}
	}

	public void stop() {
		Log.d(AppConf.LOG_TAG4, "PlayerService stop()");

		try {
			if (mPlayer.isPlaying()) {
				if (playPosThread != null) {
					playPosThread.interrupt();
					playPosThread = null;
				}

				mPlayer.stop();
				isPlaying = false;
				broadcast(SONG_PLAY_STOP, null);
			}

		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG4, "PlayerService stop() Exception", e);
			reportLog(songList.get(curPlayingIndex), "PlayerService stop() Exception:" + e.getMessage());

			Hashtable<String, String> htNext = new Hashtable<String, String>();
			htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
			broadcast(SONG_DIALOG_ERROR, htNext);
		}
	}

	public void reset() {
		Log.d(AppConf.LOG_TAG4, "PlayerService reset()");
		try {
			if (playPosThread != null) {
				playPosThread.interrupt();
				playPosThread = null;
			}

			// start to prepare player
			try {
				mPlayer.reset();
				broadcast(SONG_RESET, null);
			} catch (Exception eNext) {
				Log.e(AppConf.LOG_TAG4, "play reset exception", eNext);
				reportLog(curPlaySongInfo, "play reset exception:" + eNext.getMessage());
			}
			isSongPrepared = 0;
			prepareSong();

		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG4, "PlayerService reset() Exception", e);
			reportLog(songList.get(curPlayingIndex), "PlayerService reset() Exception:" + e.getMessage());

			Hashtable<String, String> htNext = new Hashtable<String, String>();
			htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
			broadcast(SONG_DIALOG_ERROR, htNext);
		}
	}

	public boolean toNextSong() {
		// Log.d(AppConf.LOG_TAG4, "PlayerService toNextSong()");
		if (isWaitingErrorStopResponse) {
			this.stop();
			return false;
		}

		switch (loopMode) {
		case LOOP_NO_REPEAT:
			if (shuffleMode) {
				Random generator = new Random();
				curPlayingIndex = generator.nextInt(songList.size());
			} else {
				curPlayingIndex = curPlayingIndex + 1;
				if (curPlayingIndex >= songList.size()) {
					curPlayingIndex = songList.size() - 1;
					this.stop();
					return false;
				}
			}
			loadingSongInfo();
			break;
		case LOOP_SINGLE_REPEAT:
			curPlayingIndex = curPlayingIndex;
			loadingSongInfo();
			break;
		case LOOP_PLAYLIST_REPEAT:
			if (shuffleMode) {
				Random generator = new Random();
				curPlayingIndex = generator.nextInt(songList.size());
			} else {
				curPlayingIndex = (curPlayingIndex + 1 >= songList.size()) ? 0 : curPlayingIndex + 1;
			}
			loadingSongInfo();
			break;
		default:
			curPlayingIndex = (curPlayingIndex + 1 >= songList.size()) ? 0 : curPlayingIndex + 1;
			loadingSongInfo();
			break;
		}
		return true;
	}

	public boolean toPrevSong() {
		Log.d(AppConf.LOG_TAG4, "PlayerService toPrevSong() org:" + curPlayingIndex);
		switch (loopMode) {
		case LOOP_NO_REPEAT:
			if (shuffleMode) {
				Random generator = new Random();
				curPlayingIndex = generator.nextInt(songList.size());
			} else {
				curPlayingIndex = curPlayingIndex - 1;
				if (curPlayingIndex < 0) {
					curPlayingIndex = 0;
					this.stop();
					return false;
				}
			}
			loadingSongInfo();
			break;
		case LOOP_SINGLE_REPEAT:
			curPlayingIndex = curPlayingIndex;
			loadingSongInfo();
			break;
		case LOOP_PLAYLIST_REPEAT:
			if (shuffleMode) {
				Random generator = new Random();
				curPlayingIndex = generator.nextInt(songList.size());
			} else {
				curPlayingIndex = (curPlayingIndex - 1 < 0) ? songList.size() - 1 : curPlayingIndex - 1;
			}
			loadingSongInfo();
			break;
		default:
			curPlayingIndex = (curPlayingIndex - 1 < 0) ? 0 : curPlayingIndex - 1;
			loadingSongInfo();
			break;
		}
		Log.d(AppConf.LOG_TAG4, "PlayerService toPrevSong() prev:" + curPlayingIndex);
		return true;
	}

	public void seekTo(int current) {
		// Log.d(AppConf.LOG_TAG4, "PlayerService seekTo()");
		try {
			// for MediaPlayer.OnSeekCompleteListener()
			playClickSeek = true;
			mPlayer.seekTo(current);
			isPlaying = false;

		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG4, "PlayerService reset() Exception", e);
			reportLog(songList.get(curPlayingIndex), "PlayerService reset() Exception:" + e.getMessage());

			Hashtable<String, String> htNext = new Hashtable<String, String>();
			htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
			broadcast(SONG_DIALOG_ERROR, htNext);
		}
	}

	public int getDuration() {
		// Log.d(AppConf.LOG_TAG4, "PlayerService getDuration()");
		return mPlayer.getDuration();
	}

	public int getCurrentPosition() {
		// Log.d(AppConf.LOG_TAG4, "PlayerService getCurrentPosition()");
		return mPlayer.getCurrentPosition();
	}

	public Song getCurrentSong() {
		// Log.d(AppConf.LOG_TAG4, "PlayerService getCurrentPosition()");
		return this.curPlaySongInfo;
	}

	public boolean isPlaying() {
		// Log.d(AppConf.LOG_TAG4, "PlayerService isPlaying()");
		return mPlayer.isPlaying();
	}

	public void setLoop(int loop) {
		// Log.d(AppConf.LOG_TAG4, "PlayerService setLoop()");
		loopMode = loop;
		return;
	}

	public int getLoop() {
		// Log.d(AppConf.LOG_TAG4, "PlayerService setLoop()");
		return loopMode;
	}

	public void setShuffle(boolean shuffle) {
		// Log.d(AppConf.LOG_TAG4, "PlayerService setLoop()");
		shuffleMode = shuffle;
		return;
	}

	public boolean getShuffle() {
		// Log.d(AppConf.LOG_TAG4, "PlayerService setLoop()");
		return shuffleMode;
	}

	public void setOverUnapidCreditAction(int act) {
		if (isWaitingErrorStopResponse) {
			isWaitingErrorStopResponse = false;

			switch (act) {
			case OVER_ACTION_PLAY:
				// set flag to play try version forcely
				isOverUnpaidCredit = false;

				// keep playgin try version
				try {
					PlayerService.this.dataSourceHandler(curPlaySongInfo.getPlayUrl());
					PlayerService.this.prepareHandler();
					isSongPrepared = 1;
				} catch (Exception e) {
					// do not log error
					Log.e(AppConf.LOG_TAG4, "Load Song error2!", e);

					// play nxt song, do not show error dialog 2011-10-19
					broadcast(SONG_PLAY_ERROR, null);
					try {
						PlayerService.this.toNextSong();
					} catch (Exception eNext) {
						Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
						reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
						Hashtable<String, String> htNext = new Hashtable<String, String>();
						htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
						broadcast(SONG_DIALOG_ERROR, htNext);
					}
				}
				break;
			case OVER_ACTION_STOP:
				isSongPrepared = -1;
				// break prepareSongStreamThread first
				if (prepareSongStreamThread != null) {
					prepareSongStreamThread.interrupt();
					prepareSongStreamThread = null;
				}
				this.stop();
				break;
			default:
				// do nothing
				reportLog(curPlaySongInfo, "setOverUnapidCreditAction is not accepted:" + act);
				break;
			}
		}
	}

	public void confirmErrorDialog() {
		if (isWaitingErrorStopResponse) {
			// reset
			isWaitingErrorStopResponse = false;
			playErrorCount = 0;

			this.stop();
		}
	}

	/* private methods */
	private void loadingSongInfo() {

		isSongLoading = true;
		Hashtable<String, String> ht = new Hashtable<String, String>();
		isPosLoged = false;

		/* check permission and set flags */
		curPlaySongInfo = songList.get(curPlayingIndex);
		cacheCurrentPlayingIndex();
		Log.d(AppConf.LOG_TAG4, "loadingSongInfo Start:" + curPlaySongInfo.getSongTitle());

		/*
		 * get song info based on playing source
		 */
		if (curPlaySongInfo.isLocalFile() == true) {
			if (curPlaySongInfo.isStreamEncoded() == true) {
				Log.d(AppConf.LOG_TAG4, "downloaded mp3:" + curPlaySongInfo.getPlayUrl());

				if (LoginStatusHandler.isTry(PlayerService.this)) {
					// play nxt song, do not show error dialog 2011-10-19
					ht.clear();
					ht.put("msg", mContext.getResources().getString(R.string.err_DownloadOnTry));
					broadcast(SONG_TOAST_ERROR, ht);
					try {
						this.toNextSong();
					} catch (Exception eNext) {
						Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
						reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
						Hashtable<String, String> htNext = new Hashtable<String, String>();
						htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
						broadcast(SONG_DIALOG_ERROR, htNext);
					}
					return;
				} else if (LoginStatusHandler.isOnline(PlayerService.this)) {
					if (UserLoginShareInfo.getInstance(PlayerService.this).isUserPaid() == false) {
						ht.clear();
						ht.put("msg", mContext.getResources().getString(R.string.err_DownloadOnUnpaid));
						broadcast(SONG_TOAST_ERROR, ht);
						try {
							this.toNextSong();
						} catch (Exception eNext) {
							Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
							reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
							Hashtable<String, String> htNext = new Hashtable<String, String>();
							htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
							broadcast(SONG_DIALOG_ERROR, htNext);
						}
						return;
					}
				}

				try {
					// check encoded mp3
					File srcFile = new File(curPlaySongInfo.getPlayUrl());
					if (srcFile.exists() == false) {
						// play nxt song, do not show error dialog 2011-10-19
						ht.clear();
						String msg = String.format(mContext.getResources().getString(R.string.err_mp3Broken),
								curPlaySongInfo.getSongTitle());
						ht.put("msg", msg);
						broadcast(SONG_TOAST_ERROR, ht);
						try {
							this.toNextSong();
						} catch (Exception eNext) {
							Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
							reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
							Hashtable<String, String> htNext = new Hashtable<String, String>();
							htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
							broadcast(SONG_DIALOG_ERROR, htNext);
						}
						return;
					}
					// check decoded mp3
					File cacheDir = this.getCacheDir();
					// create cache dir for decoded mp3
					if (!cacheDir.exists()) {
						cacheDir.mkdir();
					}
					File destFile = new File(cacheDir, curPlaySongInfo.getSongID() + ".dat");
					if (destFile.exists()) {
						destFile.delete();
					}

					boolean isDecoded = MusicUtil.DecodeFile(srcFile.getAbsolutePath(), destFile.getAbsolutePath());
					if (isDecoded == true) {
						curPlayLocalSongUrl = destFile.getAbsolutePath();

						isXMLListenerRun = true;
						isSongInfoLoaded = 1;
						isDisplayInfo = 0;
						isSongPrepared = 0;
						isAlbumLoaded = 0;
					} else {
						// curPlaySongUrl = "";

						isXMLListenerRun = true;
						isSongInfoLoaded = -1;
						isDisplayInfo = -1;
						isSongPrepared = -1;
						isAlbumLoaded = -1;
					}
				} catch (Exception e) {
					// curPlaySongUrl = "";

					isXMLListenerRun = true;
					isSongInfoLoaded = -1;
					isDisplayInfo = -1;
					isSongPrepared = -1;
					isAlbumLoaded = -1;
				}
			} else {
				Log.d(AppConf.LOG_TAG4, "local mp3:" + curPlaySongInfo.getPlayUrl());

				isXMLListenerRun = true;
				isSongInfoLoaded = -1;
				isDisplayInfo = -1;
				isSongPrepared = -1;
				isAlbumLoaded = -1;
			}
		}
		else {
			if (LoginStatusHandler.isOffline(PlayerService.this)) {
				// play nxt song, do not show error dialog 2011-10-19
				ht.clear();
				ht.put("msg", mContext.getResources().getString(R.string.err_OnlineStreamOnOffline));
				broadcast(SONG_TOAST_ERROR, ht);
				try {
					this.toNextSong();
					// TODO:or stop
				} catch (Exception eNext) {
					Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
					reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
					Hashtable<String, String> htNext = new Hashtable<String, String>();
					htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
					broadcast(SONG_DIALOG_ERROR, htNext);
				}
				return;
			}
			// curPlaySongUrl = "";
			if (loadedSongMap.contains(curPlaySongInfo.getSongID())) {
				isXMLListenerRun = true;
				isSongInfoLoaded = 1;
				isDisplayInfo = 0;
				isSongPrepared = 0;
				isAlbumLoaded = 0;
			} else {
				isXMLListenerRun = true;
				isSongInfoLoaded = 0;
				isDisplayInfo = 0;
				isSongPrepared = 0;
				isAlbumLoaded = 0;

				getRemoteSongInfo(curPlaySongInfo.getSongID());
			}
		}

		Log.d(AppConf.LOG_TAG4, "loadingSongInfo isSongInfoLoaded = " + isSongInfoLoaded);
		broadcast(SONG_LOADING, null);
		songInfoLoadingMonitoring();
	}

	private void songInfoLoadingMonitoring() {
		final Handler resultHandler = new Handler() {
			public void handleMessage(Message message) {
				// break songInfoMonitoringThread first
				if (songInfoMonitoringThread != null) {
					songInfoMonitoringThread.interrupt();
					songInfoMonitoringThread = null;
				}

				switch (message.what) {
				case DISPLAY_SONGINFO:
					Log.d(AppConf.LOG_TAG4, "thread::Load songinfo OK.");
					isSongLoading = true;

					songList.set(curPlayingIndex, curPlaySongInfo);
					loadedSongMap.add(curPlaySongInfo.getSongID());

					broadcast(SONG_INFO_READY, null);

					try {
						mPlayer.reset();
					} catch (Exception eNext) {
						Log.e(AppConf.LOG_TAG4, "after isSongInfoLoaded reset exception", eNext);
						reportLog(curPlaySongInfo, "after isSongInfoLoaded reset exception:" + eNext.getMessage());
					}
					// isSongPrepared == 1; start to prepare
					prepareSong();
					break;
				case DISPLAY_MULTILOGIN:
					Log.d(AppConf.LOG_TAG4, "thread::Load songinfo multi login.");
					isSongPrepared = -1;
					isAlbumLoaded = -1;
					isSongLoading = false;

					Hashtable<String, String> ht = new Hashtable<String, String>();
					ht.put("msg_type", String.valueOf(PLAYER_MSG_MULTILOGIN));
					broadcast(SONG_DIALOG_ERROR, ht);
					try {
						PlayerService.this.stop();
						broadcast(SONG_PLAY_STOP, null);
					} catch (Exception eNext) {
						Log.e(AppConf.LOG_TAG4, "play stop exception", eNext);
						reportLog(curPlaySongInfo, "play stop exception:" + eNext.getMessage());
						Hashtable<String, String> htNext = new Hashtable<String, String>();
						htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
						broadcast(SONG_DIALOG_ERROR, htNext);
					}
					break;
				case DISPLAY_ERRINFO:
				case DISPLAY_TIMEOUT:
				case DISPLAY_NOINFO:
				default:
					Log.d(AppConf.LOG_TAG4, "thread::Load songinfo error.");
					isSongPrepared = -1;
					isAlbumLoaded = -1;
					isSongLoading = false;

					// play nxt song, do not show error dialog 2011-10-19
					broadcast(SONG_PLAY_ERROR, null);
					try {
						PlayerService.this.toNextSong();
					} catch (Exception eNext) {
						Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
						reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
						Hashtable<String, String> htNext = new Hashtable<String, String>();
						htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
						broadcast(SONG_DIALOG_ERROR, htNext);
					}
					break;
				}
			}
		};

		/* check loading song status */
		songInfoMonitoringThread = new Thread(new Runnable() {
			Message msg;
			int timeCnt = 0;

			public void run() {
				try {
					while (isXMLListenerRun) {
						Log.d(AppConf.LOG_TAG4, "detect songInfo loading status：" + isSongInfoLoaded);
						switch (isSongInfoLoaded) {
						case 0:
							if (timeCnt > LOAD_SONG_WAIT_TIMES) {
								isXMLListenerRun = false;
								isSongInfoLoaded = -1;

								msg = new Message();
								msg.what = DISPLAY_TIMEOUT;
								resultHandler.sendMessage(msg);
							} else {
								timeCnt++;
								Thread.sleep(1000);
							}
							break;
						case 1:
							isXMLListenerRun = false;

							Log.d(AppConf.LOG_TAG4, "Song Loaded!");
							msg = new Message();
							msg.what = DISPLAY_SONGINFO;
							resultHandler.sendMessage(msg);
							break;
						case -1:
							// multi login flag
							isXMLListenerRun = false;
							isSongInfoLoaded = -1;

							msg = new Message();
							msg.what = DISPLAY_ERRINFO;
							resultHandler.sendMessage(msg);
							break;
						case -2:
							// multi login flag
							isXMLListenerRun = false;
							isSongInfoLoaded = -1;

							msg = new Message();
							msg.what = DISPLAY_MULTILOGIN;
							resultHandler.sendMessage(msg);
							break;
						default:
							isXMLListenerRun = false;
							isSongInfoLoaded = -1;

							msg = new Message();
							msg.what = DISPLAY_NOINFO;
							resultHandler.sendMessage(msg);
							break;
						}
					}
				} catch (InterruptedException ie) {
					Log.d(AppConf.LOG_TAG4, "thread::loadingSongInfoThread is interrupted.");
				}

				Log.d(AppConf.LOG_TAG4, "thread::loadingSongInfoThread is terminated.");
			}
		});
		Log.d(AppConf.LOG_TAG4, "thread::songInfoMonitoringThread start");
		songInfoMonitoringThread.start();
	}

	private void prepareSong() {
		final Handler initHandler = new Handler();
		final Runnable initCallback = new Runnable() {
			public void run() {
				// break prepareSongStreamThread first
				if (prepareSongStreamThread != null) {
					prepareSongStreamThread.interrupt();
					prepareSongStreamThread = null;
				}
			}
		};
		final Runnable errorCallback = new Runnable() {
			public void run() {
				// break prepareSongStreamThread first
				if (prepareSongStreamThread != null) {
					prepareSongStreamThread.interrupt();
					prepareSongStreamThread = null;
				}

				// play nxt song, do not show error dialog 2011-10-19
				broadcast(SONG_PLAY_ERROR, null);
				try {
					PlayerService.this.toNextSong();
				} catch (Exception eNext) {
					Log.e(AppConf.LOG_TAG4, "play next song exception", eNext);
					reportLog(curPlaySongInfo, "play next song exception:" + eNext.getMessage());
					Hashtable<String, String> htNext = new Hashtable<String, String>();
					htNext.put("msg_type", String.valueOf(PLAYER_MSG_PLAYER_CRASH));
					broadcast(SONG_DIALOG_ERROR, htNext);
				}
			}
		};

		/* need to wait for setOverUnapidCreditAction() of receiver */
		final Runnable payCallback = new Runnable() {
			public void run() {
				// break prepareSongStreamThread first
				if (prepareSongStreamThread != null) {
					prepareSongStreamThread.interrupt();
					prepareSongStreamThread = null;
				}

				Hashtable<String, String> htNext = new Hashtable<String, String>();
				htNext.put("msg_type", String.valueOf(PLAYER_MSG_OVER_CREDIT));
				broadcast(SONG_DIALOG_ERROR, htNext);
			}
		};

		prepareSongStreamThread = new Thread(new Runnable() {
			int timeCnt = 0;

			public void run() {
				try {
					while (isSongPrepared == 0) {
						if (curPlaySongInfo.isLocalFile() == true) {
							PlayerService.this.dataSourceHandler(curPlayLocalSongUrl);
							PlayerService.this.prepareHandler();

							isSongPrepared = 1;
							initHandler.post(initCallback);
						} else {
							// play stream
							if (LoginStatusHandler.isOnline(PlayerService.this)) {
								if (UserLoginShareInfo.getInstance(PlayerService.this).isUserPaid()) {
									// paid
									XMLCommandGateway xmlCmd = new XMLCommandGateway(PlayerService.this);
									String playUrl = xmlCmd.getPlayCommandURLString(curPlaySongInfo.getSongID());
									MusicUtil.isValidStreamUrl(playUrl);
									PlayerService.this.dataSourceHandler(MusicUtil.getRedirectedStreamUrl(playUrl));
									PlayerService.this.prepareHandler();

									isSongPrepared = 1;
									initHandler.post(initCallback);
								} else {
									// un-paid and exceed daily quota
									if (SettingShareInfo.getInstance(PlayerService.this).isUnpaidOverSongCredit()) {

										boolean isShowMsg = PlayerService.this.showOverUnapidCreditMsg();
										if (isShowMsg) {
											isWaitingErrorStopResponse = true;
											initHandler.post(payCallback);
										} else {
											PlayerService.this.dataSourceHandler(curPlaySongInfo.getPlayUrl());
											PlayerService.this.prepareHandler();

											isSongPrepared = 1;
											initHandler.post(initCallback);
										}
									} else {
										XMLCommandGateway xmlCmd = new XMLCommandGateway(PlayerService.this);
										String playUrl = xmlCmd.getPlayCommandURLString(curPlaySongInfo.getSongID());
										PlayerService.this.dataSourceHandler(MusicUtil.getRedirectedStreamUrl(playUrl));
										PlayerService.this.prepareHandler();

										isSongPrepared = 1;
										initHandler.post(initCallback);
									}
								}
							} else {
								PlayerService.this.dataSourceHandler(curPlaySongInfo.getPlayUrl());
								PlayerService.this.prepareHandler();

								isSongPrepared = 1;
								initHandler.post(initCallback);
							}
						}
					}
				} catch (InterruptedException ie) {
					Log.d(AppConf.LOG_TAG4, "thread::prepareSongStreamThread is interrupted.");
				} catch (Exception e) {
					// do not log error
					Log.d(AppConf.LOG_TAG4, "Load Song error2!");

					isSongPrepared = -1;
					initHandler.post(errorCallback);
				} finally {
				}
				Log.d(AppConf.LOG_TAG4, "thread::prepareSongStreamThread is terminated.");
			}
		});
		Log.d(AppConf.LOG_TAG4, "thread::prepareSongStreamThread is start.");
		prepareSongStreamThread.start();
	}

	private void prepareHandler() throws Exception {
		Log.d(AppConf.LOG_TAG4, "PlayerService prepareHandler()");
		try {
			// call MediaPlayer.OnPreparedListener()
			mPlayer.prepare();
			Log.d(AppConf.LOG_TAG4, "PlayerService Prepare OK");

		} catch (IllegalStateException e) {
			// call MediaPlayer.OnPreparedListener()
			Log.e(AppConf.LOG_TAG4, "PlayerService Prepare IllegalStateException", e);
			Log.d(AppConf.LOG_TAG4, "If android2.3 will throw IllegalStateException when preparing. This android SDK:"
					+ Build.VERSION.SDK + "==SDK int:" + Build.VERSION.SDK_INT + "==release:" + Build.VERSION.RELEASE);
			isIllegalState = true;
		} catch (IOException e) {
			Log.e(AppConf.LOG_TAG4, "PlayerService Prepare IOException", e);
			reportLog(songList.get(curPlayingIndex), "PlayerService Prepare IOException:" + e.getMessage());
			throw new Exception();
		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG4, "PlayerService Prepare unknown Exception", e);
			reportLog(songList.get(curPlayingIndex), e.getMessage());
			throw new Exception();
		}
	}

	/**
	 * generate stream url
	 */
	private void dataSourceHandler(String path) throws Exception {
		Log.d(AppConf.LOG_TAG4, "PlayerService dataSourceHandler: " + path);

		try {
			if (curPlaySongInfo.isLocalFile() == true) {
				File playFile = new File(path);
				if (playFile.exists()) {
					// MediaPlayer doesn't have access rights to application cache directory
					// Instead should passing a FileDescriptor to MediaPlayer
					FileInputStream fis = new FileInputStream(playFile);
					FileDescriptor fd = fis.getFD();
					mPlayer.setDataSource(fd);
				}
			} else {
				if (MusicUtil.isValidStreamUrl(path)) {
					mPlayer.setDataSource(path);
				} else {
					Log.e(AppConf.LOG_TAG4, "play url is not valid: " + path);
					reportLog(curPlaySongInfo, "play url is not valid: " + path);
					throw new Exception();
				}
			}
		}
		catch (Exception e) {
			Log.e(AppConf.LOG_TAG4, "setDataSource exception", e);
			reportLog(curPlaySongInfo, "setDataSource exception:" + e.getMessage());

			throw new Exception();
		}
	}

	private boolean showOverUnapidCreditMsg() {
		if (isOverUnpaidCredit == true) {
			isOverUnpaidCredit = false;
			return true;
		} else {
			return false;
		}
	}

	/*
	 * broadcast to receiver
	 */
	private void broadcast(int event, Hashtable<String, String> ht) {
		if (isWaitingErrorStopResponse) {
			return;
		}

		if (event == SONG_TOAST_ERROR || event == SONG_PREPARE_ERROR || event == SONG_PLAY_ERROR) {
			playErrorCount++;
			Log.d(AppConf.LOG_TAG4, "==>current error count:" + playErrorCount);
			if (playErrorCount > AppConf.MAX_PLAY_ERROR_COUNT) {
				this.stop();

				event = SONG_DIALOG_ERROR;
				ht = new Hashtable<String, String>();
				ht.put("msg_type", String.valueOf(PLAYER_MSG_2MANAY_ERROR));
			}
		}

		if (event == SONG_DIALOG_ERROR) {
			isWaitingErrorStopResponse = true;
		}

		Intent intent = new Intent();
		intent.setAction(AppConf.ACTION_FILTER_PLAYER);
		intent.putExtra("event", event);

		if (ht != null && ht.size() > 0) {
			Enumeration<String> e = ht.keys();
			while (e.hasMoreElements()) {
				String key = e.nextElement();
				intent.putExtra(key, ht.get(key));
			}
		}
		sendBroadcast(intent);
	}

	/*
	 * log report
	 */
	private void reportLog(Song currentSong, String msg) {
		Log.d(AppConf.LOG_TAG, "reportLog:" + msg);

		ContentValues values = new ContentValues();
		values.put(DBConf.TB_ErrLog.ErrMsg.getName(), msg);
		if (currentSong != null) {
			values.put(DBConf.TB_PlayCount.SongID.getName(), currentSong.getSongID());
			values.put(DBConf.TB_DownloadCount.SingerID.getName(), currentSong.getSingerID());
			values.put(DBConf.TB_DownloadCount.AlbumID.getName(), currentSong.getAlbumID());
		}

		DBLogging dbLogging = null;
		try {
			dbLogging = new DBLogging(PlayerService.this);
			dbLogging.addQueue(values, DBLogging.ERR_MSG);
		} catch (SQLException sqle) {
			Log.e(AppConf.LOG_TAG4, ErrorCode.SQLException.getCode() + " "
					+ getResources().getString(ErrorCode.SQLException.getMessageResourceID()), sqle);
		} finally {
			if (dbLogging != null) {
				dbLogging.close();
			}
		}
	}

	private void cachePlayingList() {
		try {
			// re-cache this song list
			for (int i = 0; i < songList.size(); i++) {
				Song song = songList.get(i);

				ContentValues values = new ContentValues();
				values.put(DBConf.TB_PlayerSongListCache.ListNo.getName(), i);
				values.put(DBConf.TB_PlayerSongListCache.IsCurrentSong.getName(), (i == curPlayingIndex) ? "Y" : "N");
				values.put(DBConf.TB_PlayerSongListCache.ID.getName(), song.getID());
				values.put(DBConf.TB_PlayerSongListCache.SongID.getName(), song.getSongID());
				values.put(DBConf.TB_PlayerSongListCache.SongTitle.getName(), song.getSongTitle());
				values.put(DBConf.TB_PlayerSongListCache.SingerID.getName(), song.getSingerID());
				values.put(DBConf.TB_PlayerSongListCache.SingerName.getName(), song.getSingerName());
				values.put(DBConf.TB_PlayerSongListCache.AlbumID.getName(), song.getAlbumID());
				values.put(DBConf.TB_PlayerSongListCache.AlbumTitle.getName(), song.getAlbumTitle());
				values.put(DBConf.TB_PlayerSongListCache.MP3URL.getName(), song.getPlayUrl());
				values.put(DBConf.TB_PlayerSongListCache.CoverURL.getName(), song.getCoverPictureUrl());
				values.put(DBConf.TB_PlayerSongListCache.LyricsText.getName(), song.getLyricsText());
				values.put(DBConf.TB_PlayerSongListCache.IsLocal.getName(), (song.isLocalFile() ? "1" : "0"));
				values.put(DBConf.TB_PlayerSongListCache.IsEncrypt.getName(), (song.isStreamEncoded() ? "1" : "0"));
				values.put(DBConf.TB_PlayerSongListCache.PlaylistID.getName(), song.getPlaylistID());

				dbPlaylist.addSong(values);
			}
		} catch (SQLException sqle) {
			// log error
			Log.e(AppConf.LOG_TAG4, "update cached playing list exception", sqle);
			reportLog(songList.get(curPlayingIndex), "update cached playing list exception:" + sqle.getMessage());
		}

	}

	private void cacheCurrentPlayingIndex() {
		Log.d(AppConf.LOG_TAG4, "cacheCurrentPlayingIndex:" + this.curPlayingIndex);

		try {
			dbPlaylist.resetCurrentSong(curPlayingIndex);
		} catch (SQLException sqle) {
			// log error
			Log.e(AppConf.LOG_TAG4, "update cached playing index exception", sqle);
			reportLog(songList.get(curPlayingIndex), "update cached playing index exception:" + sqle.getMessage());
		}

	}

	private void addPlaySongCache(Song song) throws BOException {
		ContentValues values = new ContentValues();
		values.put(DBConf.TB_PlaySongCache.SongID.getName(), song.getSongID());
		values.put(DBConf.TB_PlaySongCache.SongTitle.getName(), song.getSongTitle());
		values.put(DBConf.TB_PlaySongCache.SingerID.getName(), song.getSingerID());
		values.put(DBConf.TB_PlaySongCache.SingerName.getName(), song.getSingerName());
		values.put(DBConf.TB_PlaySongCache.AlbumID.getName(), song.getAlbumID());
		values.put(DBConf.TB_PlaySongCache.AlbumTitle.getName(), song.getAlbumTitle());
		values.put(DBConf.TB_PlaySongCache.MP3URL.getName(), song.getPlayUrl());
		values.put(DBConf.TB_PlaySongCache.CoverURL.getName(), song.getCoverPictureUrl());
		values.put(DBConf.TB_PlaySongCache.LyricsText.getName(), song.getLyricsText());
		values.put(DBConf.TB_PlaySongCache.IsLocal.getName(), (song.isLocalFile()) ? "1" : "0");

		DBPlaySongCache db = null;
		try {
			db = new DBPlaySongCache(PlayerService.this);
			db.addPlaySong(values);
		} catch (SQLException sqle) {
			Log.e(AppConf.LOG_TAG4, ErrorCode.SQLException.getCode() + " "
					+ getResources().getString(ErrorCode.SQLException.getMessageResourceID()), sqle);
		} finally {
			if (db != null) {
				db.close();
			}
		}
	}

	private void addPlayCount(Song currentSong) {
		Log.d(AppConf.LOG_TAG4, "addPlayCount:" + currentSong.getSongTitle());
		ContentValues values = new ContentValues();
		values.put(DBConf.TB_PlayCount.SongID.getName(), currentSong.getSongID());
		values.put(DBConf.TB_DownloadCount.SingerID.getName(), currentSong.getSingerID());
		values.put(DBConf.TB_DownloadCount.AlbumID.getName(), currentSong.getAlbumID());

		DBLogging dbLogging = null;
		try {
			dbLogging = new DBLogging(PlayerService.this);
			dbLogging.addQueue(values, DBLogging.PLAY_COUNT);
		} catch (SQLException sqle) {
			Log.e(AppConf.LOG_TAG4, ErrorCode.SQLException.getCode() + " "
					+ getResources().getString(ErrorCode.SQLException.getMessageResourceID()), sqle);
		} finally {
			if (dbLogging != null) {
				dbLogging.close();
			}
		}

	}

	private void checkPlayPosition() {

		final Handler handler = new Handler();
		final Runnable callback = new Runnable() {
			public void run() {
				if (mPlayer.isPlaying()) {
					int position = mPlayer.getCurrentPosition();
					Log.d(AppConf.LOG_TAG4, "thread::playPosThread current sec.：" + position);

					if (!isPosLoged && (position / 1000) > AppConf.PLAY_RECORDED_SECONDS) {
						Log.d(AppConf.LOG_TAG4, "thread::playPosThread log sec.>20");
						try {
							Song CurrentPlaySongInfo = songList.get(curPlayingIndex);

							PlayerService.this.addPlayCount(CurrentPlaySongInfo);
							PlayerService.this.addPlaySongCache(CurrentPlaySongInfo);

							if (UserLoginShareInfo.getInstance(PlayerService.this).isUserPaid() == false) {
								SettingShareInfo.getInstance(PlayerService.this).addUnpaidSongCount();
								if (SettingShareInfo.getInstance(PlayerService.this)
										.getUnpaidSongCount() == AppConf.UNPAID_DAILY_SONG_CREDIT) {
									isOverUnpaidCredit = true;
								}
							}
							isPosLoged = true;
						} catch (BOException boe) {
							Log.e(AppConf.LOG_TAG4, boe.getMessage(), boe);
						} finally {
							if (playPosThread != null) {
								playPosThread.interrupt();
								playPosThread = null;
							}
						}
					}
				}
			}
		};

		// checkProgressBar = true;
		playPosThread = new Thread(new Runnable() {
			public void run() {
				try {
					while (true) {
						handler.post(callback);
						Thread.sleep(1000);
					}
				} catch (InterruptedException e) {
					Log.d(AppConf.LOG_TAG4, "thread::playPosThread is interrupted");
				}
				Log.d(AppConf.LOG_TAG4, "thread::playPosThread is terminated.");
			}
		});
		Log.d(AppConf.LOG_TAG4, "thread::playPosThread start");
		playPosThread.start();

	}

	/**
	 * get remote song info before play
	 * 
	 * @param songid
	 * @param UIHandler
	 *            message.what=0 and message.obj = Song message.what<0, else
	 *            message.obj = String (error message)
	 */
	public void getRemoteSongInfo(final String songid) {
		// create handler to receive feedback from XMLCommand
		XMLContentHandler boHandler = new XMLContentHandler(mContext, XMLCommandGateway.CMD_SONG_INFO) {
			protected void postMessage(Message message, XMLContent content) {
				Log.d(AppConf.LOG_TAG4, "getRemoteSongInfo status:" + message.what);

				switch (message.what) {
				case 0:
					// reset object
					curPlaySongInfo = new Song(songid, (SongInfoXMLContent) content);
					isSongInfoLoaded = 1;
					break;
				case -2:
					/* Yedda add for multi-login in 2011/10/21 */
					isSongInfoLoaded = -2;
					break;
				default:
					isSongInfoLoaded = -1;
					break;
				}
			}
		};

		XMLCommandGateway xmlCmd = new XMLCommandGateway(mContext);
		xmlCmd.sendSongInfoCommand(songid, boHandler);
	}

	/* inner classes */
	private class PhoneStatReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
			Log.d(AppConf.LOG_TAG4, "PlayerService PhoneStatReceiver Receive " + tm.getCallState());

			try {
				switch (tm.getCallState()) {
				case TelephonyManager.CALL_STATE_RINGING:
					broadcast(SONG_INCOMING_CALL, null);
					isComingCall = true;

					if (mPlayer.isPlaying()) {
						if (playClickSeek) {
							playClickSeek = false;
						}

						PlayerService.this.pause();
						isComingCallPalying = true;
					}
					break;
				case TelephonyManager.CALL_STATE_OFFHOOK:
					break;
				case TelephonyManager.CALL_STATE_IDLE:
					isComingCall = false;
					if (isComingCallPalying) {
						PlayerService.this.start();
 						isComingCallPalying = false;
					}
					break;
				default:
					break;
				}
			} catch (Exception e) {
				Log.e(AppConf.LOG_TAG4, "PlayerService PhoneStatReceiver Exception", e);
				PlayerService.this.reportLog(curPlaySongInfo,
						"PlayerService PhoneStatReceiver Exception:" + e.getMessage());
			}
		}

	}

	private class HeadsetConnectionReceiver extends BroadcastReceiver {
		private boolean headsetConnected = false;

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(AppConf.LOG_TAG4, "PlayerService HeadsetConnectionReceiver Receive.");
			if (mPlayer.isPlaying() && intent.hasExtra("state")) {
				AudioManager mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
				if (headsetConnected && intent.getIntExtra("state", 0) == 0) {
					headsetConnected = false;
					mAudioManager.setWiredHeadsetOn(false);
				} else if (!headsetConnected && intent.getIntExtra("state", 0) == 1) {
					headsetConnected = true;
					mAudioManager.setWiredHeadsetOn(true);
				}
			}
		}

	}
}
