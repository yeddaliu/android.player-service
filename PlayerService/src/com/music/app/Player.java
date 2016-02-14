package com.music.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import com.music.R;
import com.music.bo.BOException;
import com.music.bo.data.Song;
import com.music.bo.data.UserLoginShareInfo;
import com.music.util.LoginStatusHandler;
import com.music.util.MenuAction;
import com.music.helper.AppConf;
import com.music.service.PlayerService;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class Player extends Activity implements SurfaceHolder.Callback {
	/* menu item id */
	public static final int MENU_Exit = 0;
	public static final int MENU_Setting = 1;
	public static final int MENU_ShowLyrics = 2;
	public static final int MENU_Playlist = 3;
	public static final int MENU_Download = 4;
	public static final int MENU_Share = 5;

	/* control action flag */
	private static final int PLAY_CTRL_STOP = 0;
	private static final int PLAY_CTRL_PLAY = 1;
	private static final int PLAY_CTRL_PAUSE = 2;
	private static final int PLAY_CTRL_NEXT = 3;
	private static final int PLAY_CTRL_PREV = 4;

	private static final int LOOP_NO_REPEAT = 0;
	private static final int LOOP_SINGLE_REPEAT = 1;
	private static final int LOOP_PLAYLIST_REPEAT = 2;

	private static final int LOAD_ALBUM_WAIT_TIMES = 15;

	private SurfaceView mSurfaceView01;
	private SurfaceHolder mSurfaceHolder;

	private int playerStatus = PLAY_CTRL_STOP; // 0:stop 1:play 2:pause
	private int loopMode = LOOP_NO_REPEAT; /*
											 * 0: no repeat , 1 : single repeat ,
											 * 2 : playlist repeat
											 */

	private int intCounter = 0;
	private float thisSongDuration = 0;
	private int thisCurrentPosition = 0;
	private int albumHight = 0;
	private int albumRefHight = 60;
	private int albumRefHightRate = 5;
	private int albumImageDefaultScale = 320;

	private SimpleDateFormat timeFormatter = new SimpleDateFormat("mm:ss");

	private com.music.bo.Player boPlayer = null;
	private ArrayList<Song> songList;
	private int curPlayingIndex = 0;

	private boolean shuffleMode = false;
	private boolean showLyrics = false;
	private boolean fromWidget = false;
	private boolean isCheckSeekBar = false;

	private ProgressDialog dialogLoad;
	private TextView songTotalTimeLength;
	private TextView playingSongNameText;
	private TextView playingSongExtraText;
	private TextView songPlayingTimeLength;
	private ImageView playIcon;
	private ImageView playerAlbumImage;
	private ImageView playerAlbumImageReflection;
	private Bitmap albumBitmap;
	private Bitmap lyricsAlbumBitmap;
	private SeekBar seekBar;

	private Thread seekBarThread;
	private Thread loadAlbumImageThread;
	private Thread loadingAlbumLyricImageThread;

	/* connect service */
	private PlayerService iPlayer;
	private MusicReceiver musicReceiver;
	private boolean isServiceBound = false;
	private ServiceConnection srvconn = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.d(AppConf.LOG_TAG3, "OmusicPlayer ServiceConnection -> onServiceConnected");

			iPlayer = ((PlayerService.PlayerBinder) service).getService();
			try {
				configSeekBar();

				// get Shuffle, Loop icon
				loopMode = iPlayer.getLoop();
				ImageView playerRepeat = (ImageView) findViewById(R.id.player_repeat);
				switch (loopMode) {
				case LOOP_NO_REPEAT:
					playerRepeat.setImageResource(R.drawable.player_repeat0);
					break;
				case LOOP_SINGLE_REPEAT:
					playerRepeat.setImageResource(R.drawable.player_repeat1);
					break;
				case LOOP_PLAYLIST_REPEAT:
					playerRepeat.setImageResource(R.drawable.player_repeat2);
					break;
				}
				shuffleMode = iPlayer.getShuffle();
				ImageView playerShuffle = (ImageView) findViewById(R.id.player_shuffle);
				if (shuffleMode == true)
					playerShuffle.setImageResource(R.drawable.player_shuffle_active);
				else
					playerShuffle.setImageResource(R.drawable.player_shuffle);

				if (fromWidget == false) {
					isCheckSeekBar = false;
					// reset song list
					iPlayer.setSongList(curPlayingIndex, songList);
				} else {
					// show song info when come from Tab
					isCheckSeekBar = true;
					loadAlbumCover(iPlayer.getCurrentSong());
					loadLyricsAlbumCover(iPlayer.getCurrentSong());
					updatePlayingSongRes(iPlayer.getCurrentSong());

					if (dialogLoad.isShowing()) {
						dialogLoad.dismiss();
					}
					fromWidget = false;
				}

			} catch (Exception e) {
				Log.e(AppConf.LOG_TAG3, "exception", e);

				if (dialogLoad.isShowing()) {
					dialogLoad.dismiss();
				}
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			Log.d(AppConf.LOG_TAG3, "OmusicPlayer ServiceConnection -> onServiceDisconnected");
			iPlayer = null;
		};
	};

	/** Called when the activity is first created. */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(AppConf.LOG_TAG3, "Player onCreate");

		/* initial player layout */
		Player.this.setTitle(R.string.palyerNowPlayBanner);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);

		dialogLoad = ProgressDialog.show(this, "", this.getResources().getString(R.string.downloading), true);

		/* show player panel */
		hiddenFullLyricsPanel();
		showPlayerPanel();

		/* set PixnelFormat */
		getWindow().setFormat(PixelFormat.TRANSPARENT);

		/* set SurfaceView */
		mSurfaceView01 = (SurfaceView) findViewById(R.id.mSurfaceView1);
		mSurfaceHolder = mSurfaceView01.getHolder();
		mSurfaceHolder.addCallback(this);
		mSurfaceHolder.setFixedSize(0, 0);
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		/* initial conponents */
		ImageView showFullLyricsPanel = (ImageView) findViewById(R.id.show_full_lyrics_panel);
		LinearLayout hiddenFullLyricsPanel = (LinearLayout) findViewById(R.id.hidden_full_lyrics_panel);
		ImageView playerShuffle = (ImageView) findViewById(R.id.player_shuffle);
		ImageView playerRepeat = (ImageView) findViewById(R.id.player_repeat);
		ImageView playerAddSong = (ImageView) findViewById(R.id.player_add_song);
		ImageView playerPrev = (ImageView) findViewById(R.id.player_prev);
		ImageView playerNext = (ImageView) findViewById(R.id.player_next);
		ImageView playAlbumInfo = (ImageView) findViewById(R.id.title_btn_info);
		ImageView playShowPlaylist = (ImageView) findViewById(R.id.title_btn_list);
		TextView fullLyricsText = (TextView) findViewById(R.id.full_lyrics_text);
		LinearLayout albumBox = (LinearLayout) findViewById(R.id.player_album_image_box);
		ScrollView scrollLyrics = (ScrollView) findViewById(R.id.scroll_lyrics);

		playIcon = (ImageView) findViewById(R.id.player_pause);
		playerAlbumImage = (ImageView) findViewById(R.id.player_album_image);
		playerAlbumImageReflection = (ImageView) findViewById(R.id.player_album_image_reflection);
		playingSongNameText = (TextView) findViewById(R.id.player_playing_song_name_top);
		playingSongExtraText = (TextView) findViewById(R.id.player_playing_singer_and_album_name);
		songTotalTimeLength = (TextView) findViewById(R.id.player_song_total_length);
		songPlayingTimeLength = (TextView) findViewById(R.id.player_song_playing_length);
		seekBar = (SeekBar) findViewById(R.id.playerSeekbar);

		/* convert images to bitmap */
		Bitmap newBm = null;
		albumBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_album250);
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		if ((dm.densityDpi >= dm.DENSITY_HIGH)) {
			// resize image
			newBm = com.music.util.Image.scaleImg(albumBitmap, albumImageDefaultScale, albumImageDefaultScale);
			playerAlbumImage.setImageBitmap(newBm);
			playerAlbumImage.setScaleType(ScaleType.MATRIX);
			LayoutParams imgParams = new LinearLayout.LayoutParams(albumImageDefaultScale, albumImageDefaultScale);
			playerAlbumImage.setLayoutParams(imgParams);

			LinearLayout layout = (LinearLayout) findViewById(R.id.player_album_image_box);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(400, 455);
			layout.setLayoutParams(params);

		} else if (dm.densityDpi < dm.DENSITY_HIGH && dm.densityDpi >= dm.DENSITY_LOW) {
			newBm = albumBitmap;
		} else {
			newBm = albumBitmap;
		}
		// will be process within loadAlbumCover()
		Bitmap refPlayerAlbumImage = com.music.util.Image.makeReflectionBitmap(newBm, albumRefHight);
		playerAlbumImageReflection.setImageBitmap(refPlayerAlbumImage);

		/* register components */
		playIcon.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (playerStatus == PLAY_CTRL_PLAY)
					playPauseClicked();
				else
					playClicked();
			}
		});

		playerPrev.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				playPrevClicked();
			}
		});

		playerNext.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				playNextClicked();
			}
		});

		showFullLyricsPanel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showLyrics = true;
				togglePanel(null);
			}
		});

		hiddenFullLyricsPanel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showLyrics = false;
				togglePanel(null);
			}
		});

		playerShuffle.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ImageView playerShuffle = (ImageView) findViewById(R.id.player_shuffle);
				shuffleMode = (shuffleMode == true) ? false : true;
				if (shuffleMode == true)
					playerShuffle.setImageResource(R.drawable.player_shuffle_active);
				else
					playerShuffle.setImageResource(R.drawable.player_shuffle);

				iPlayer.setShuffle(shuffleMode);
			}
		});

		playerRepeat.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ImageView playerRepeat = (ImageView) findViewById(R.id.player_repeat);
				loopMode = (loopMode + 1 > 2) ? 0 : loopMode + 1;
				switch (loopMode) {
				case LOOP_NO_REPEAT:
					playerRepeat.setImageResource(R.drawable.player_repeat0);
					break;
				case LOOP_SINGLE_REPEAT:
					playerRepeat.setImageResource(R.drawable.player_repeat1);
					break;
				case LOOP_PLAYLIST_REPEAT:
					playerRepeat.setImageResource(R.drawable.player_repeat2);
					break;
				}
				iPlayer.setLoop(loopMode);
			}
		});

		playerAddSong.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Song playingSong = iPlayer.getCurrentSong();
				if (playingSong == null) {
					return;
				}

				final ArrayList<Song> songList = new ArrayList<Song>();
				songList.add(playingSong);
				if (playingSong.getPlaylistID().length() == 0) {
					Intent myPlaylistActivity = new Intent(Player.this, AddPlaylist.class);
					myPlaylistActivity.putParcelableArrayListExtra("songList", songList);
					startActivity(myPlaylistActivity);
					fromWidget = true;
				} else {
					// get playlist name
					String msg = "";
					try {
						String title = boPlayer.getPlaylistTitle(playingSong.getPlaylistID());
						String msgSource = Player.this.getResources().getString(R.string.err_PlaylistAlreadyExists);
						msg = String.format(msgSource, title);
					} catch (BOException e) {
						msg = Player.this.getResources().getString(R.string.err_PlaylistAlreadyExistsWithNoTitle);
					}

					AlertDialog.Builder builder = new AlertDialog.Builder(Player.this);
					builder.setTitle(R.string.errHead_Remind).setMessage(msg).setCancelable(false)
							.setPositiveButton(R.string.btnConfirm, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							Intent myPlaylistActivity = new Intent(Player.this, AddPlaylist.class);
							myPlaylistActivity.putParcelableArrayListExtra("songList", songList);
							startActivity(myPlaylistActivity);
							Z fromWidget = true;
							dialog.dismiss();
						}
					}).setNeutralButton(R.string.btnCancel, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
						}
					});
					AlertDialog alert = builder.create();
					alert.show();
				}
			}
		});

		playAlbumInfo.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent singerHistoryActivity = new Intent(Player.this, AlbumDetail.class);
				singerHistoryActivity.putExtra("albumid", iPlayer.getCurrentSong().getAlbumID());
				startActivity(singerHistoryActivity);

				fromWidget = true;
			}
		});

		playShowPlaylist.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Player.this.finish();
			}
		});

		/* database */
		Bundle bundle = getIntent().getExtras();
		boPlayer = new com.music.bo.Player(Player.this);

		// 取得前activity所傳的song object list
		curPlayingIndex = bundle.getInt("currentPlayIndex");
		if (curPlayingIndex != -1) {
			songList = getIntent().getParcelableArrayListExtra("songList");
			fromWidget = false;
		} else {
			fromWidget = true;
		}

		/* initial play icon status */
		shuffleMode = (bundle.containsKey("shuffleMode")) ? bundle.getBoolean("shuffleMode") : false;
		if (shuffleMode == true) {
			ImageView player_shuffle = (ImageView) findViewById(R.id.player_shuffle);
			player_shuffle.setImageResource(R.drawable.player_shuffle_active);
		}
	}

	@Override
	protected void onStart() {
		if (dialogLoad != null) {
			if (!dialogLoad.isShowing()) {
				dialogLoad.show();
			}
		}

		if (musicReceiver == null) {
			Log.d(AppConf.LOG_TAG3, "MusicReceiver@musicPlayer Start");
			musicReceiver = new MusicReceiver();
			IntentFilter intentFilter = new IntentFilter();
			intentFilter.addAction(AppConf.ACTION_FILTER_PLAYER);
			registerReceiver(musicReceiver, intentFilter);
		}

		doBindService();

		super.onStart();
		Log.d(AppConf.LOG_TAG3, "player onStart");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(AppConf.LOG_TAG3, "player onResume");
	}

	@Override
	protected void onStop() {
		if (dialogLoad != null) {
			if (dialogLoad.isShowing()) {
				dialogLoad.dismiss();
			}
		}

		if (musicReceiver != null) {
			Log.d(AppConf.LOG_TAG3, "MusicReceiver@musicPlayer Stop");
			unregisterReceiver(musicReceiver);
			musicReceiver = null;
		}

		// pause seekBarThread
		isCheckSeekBar = false;
		doUnbindService();

		super.onStop();
		Log.d(AppConf.LOG_TAG3, "player onStop");
	}

	@Override
	protected void onPause() {
		if (dialogLoad != null) {
			if (dialogLoad.isShowing()) {
				dialogLoad.dismiss();
			}
		}

		// pause seekBarThread
		isCheckSeekBar = false;

		super.onPause();
		Log.d(AppConf.LOG_TAG3, "player onPause");
	}

	/* since android 2.0 */
	@Override
	public void onBackPressed() {
		if (dialogLoad != null) {
			if (dialogLoad.isShowing()) {
				dialogLoad.dismiss();
			}
		}
		/*
		 * always back to previous page
		 */
		super.onBackPressed();
		return;
	}

	@Override
	protected void onDestroy() {
		if (dialogLoad != null) {
			if (dialogLoad.isShowing()) {
				dialogLoad.dismiss();
			}
			dialogLoad = null;
		}

		if (seekBarThread != null) {
			seekBarThread.interrupt();
			seekBarThread = null;
		}

		doUnbindService();
		if (srvconn != null) {
			srvconn = null;
		}

		super.onDestroy();
		Log.d(AppConf.LOG_TAG3, "player onDestroy");
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// TODO Auto-generated method stub
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
	}

	/* set Menu */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean downloadable = false;
		if (LoginStatusHandler.isOnline(Player.this) && UserLoginShareInfo.getInstance(Player.this).isUserPaid()) {
			downloadable = true;
		}

		if (showLyrics) {
			menu.add(0, Player.MENU_ShowLyrics, 0, R.string.menu_Play).setIcon(R.drawable.menu_player);
		} else {
			menu.add(0, Player.MENU_ShowLyrics, 0, R.string.menu_ShowLyrics).setIcon(R.drawable.menu_lyrics);
		}
		menu.add(0, Player.MENU_Playlist, 1, R.string.menu_Playlist).setIcon(R.drawable.menu_playlist);
		menu.add(0, Player.MENU_Download, 2, R.string.menu_Download).setIcon(R.drawable.menu_download).setEnabled(downloadable);
		menu.add(1, Player.MENU_Share, 3, R.string.menu_Share).setIcon(R.drawable.menu_clear).setVisible(false);
		menu.add(1, Player.MENU_Exit, 4, R.string.menu_Exit).setIcon(R.drawable.menu_exit);
		menu.add(1, Player.MENU_Setting, 5, R.string.menu_Setting).setIcon(R.drawable.menu_setting);
		return true;
	}

	/* set Menu Listener */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Player.MENU_Exit:
			MenuAction.doExit(Player.this);
			break;
		case Player.MENU_Setting:
			MenuAction.doSetting(Player.this);
			break;
		case Player.MENU_ShowLyrics:
			if (showLyrics) {
				showLyrics = false;
				togglePanel(item);
			} else {
				showLyrics = true;
				togglePanel(item);
			}
			break;
		case Player.MENU_Playlist:
			Player.this.finish();
			break;
		case Player.MENU_Download:
			download();
			break;
		case Player.MENU_Share:
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	/*
	 * view control
	 */
	private void showFullLyricsPanel() {
		LinearLayout fullLyricsPanel = (LinearLayout) findViewById(R.id.full_lyrics_panel);
		fullLyricsPanel.setVisibility(View.VISIBLE);
	}

	private void hiddenFullLyricsPanel() {
		LinearLayout fullLyricsPanel = (LinearLayout) findViewById(R.id.full_lyrics_panel);
		fullLyricsPanel.setVisibility(View.GONE);
	}

	private void showPlayerPanel() {
		LinearLayout playerPanel = (LinearLayout) findViewById(R.id.player_panel);
		playerPanel.setVisibility(View.VISIBLE);
	}

	private void hiddenPlayerPanel() {
		LinearLayout playerPanel = (LinearLayout) findViewById(R.id.player_panel);
		playerPanel.setVisibility(View.GONE);
	}

	private boolean playPauseClicked() {
		Log.d(AppConf.LOG_TAG3, "Player on PlayPauseClicked");
		playIcon.setImageResource(R.drawable.player_playing);
		playerStatus = PLAY_CTRL_PAUSE;
		iPlayer.pause();
		return true;
	}

	private boolean playClicked() {
		Log.d(AppConf.LOG_TAG3, "Player on playClicked");
		playIcon.setImageResource(R.drawable.player_pause);

		if (playerStatus == PLAY_CTRL_STOP) {
			iPlayer.playCurrentSong();
		} else {
			iPlayer.start();
		}
		playerStatus = PLAY_CTRL_PLAY;

		return true;
	}

	private boolean playStopClicked() {
		Log.d(AppConf.LOG_TAG3, "Player on playStopClicked");

		playerStatus = PLAY_CTRL_STOP;
		iPlayer.stop();
		return true;
	}

	private boolean playNextClicked() {
		Log.d(AppConf.LOG_TAG3, "Player on playNextClicked");

		// pause seekBarThread
		isCheckSeekBar = false;

		if (iPlayer.isPlaying()) {
			iPlayer.stop();
		}
		if (iPlayer.toNextSong() == true) {
			// Log.d(AppConf.LOG_TAG3, "===Play next song:"+
			// iPlayer.getCurrentSong().getSongTitle());
		} else {
			Toast.makeText(Player.this, R.string.palyer_msg_NoNext, Toast.LENGTH_SHORT).show();
		}
		return true;
	}

	private boolean playPrevClicked() {
		Log.d(AppConf.LOG_TAG3, "Player on playPrevClicked");

		// pause seekBarThread
		isCheckSeekBar = false;

		if (iPlayer.isPlaying()) {
			playerStatus = PLAY_CTRL_PREV;
			iPlayer.stop();
			iPlayer.reset();
		} else {
			if (iPlayer.toPrevSong() == true) {
				// Log.d(AppConf.LOG_TAG3, "===Play prev song:"+
				// iPlayer.getCurrentSong().getSongTitle());
			} else {
				Toast.makeText(Player.this, R.string.palyer_msg_NoPrev, Toast.LENGTH_SHORT).show();
			}
		}
		return true;
	}

	/* private methods */
	private void doBindService() {
		if (bindService(new Intent(Player.this, PlayerService.class), srvconn, Context.BIND_AUTO_CREATE)) {
			isServiceBound = true;
		}
	}

	private void doUnbindService() {
		if (isServiceBound) {
			if (seekBarThread != null) {
				seekBarThread.interrupt();
				seekBarThread = null;
			}
			unbindService(srvconn);
			isServiceBound = false;
		}
	}

	private void configSeekBar() {
		seekBar.setIndeterminate(false);
		seekBar.setMax(10000);
		seekBar.setProgress(0);

		/* register event listener */
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					iPlayer.seekTo((int) (progress * thisSongDuration / 10000));
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				Log.d(AppConf.LOG_TAG3, "SeekBarChange onStartTrackingTouch");
				isCheckSeekBar = false;

				// refresh UI
				playIcon.setImageResource(R.drawable.player_playing);
				playerStatus = PLAY_CTRL_PAUSE;

				if (dialogLoad != null) {
					if (!dialogLoad.isShowing()) {
						dialogLoad.show();
					}
				}
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Log.d(AppConf.LOG_TAG3, "SeekBarChange onStopTrackingTouch");
				iPlayer.seekTo((int) (seekBar.getProgress() * thisSongDuration / 10000));

			}
		});

		/* config timecode thread action */
		final Handler handler = new Handler();
		final Runnable callback = new Runnable() {
			public void run() {
				if (iPlayer.isPlaying()) {
					thisCurrentPosition = iPlayer.getCurrentPosition();
					int prg = (int) ((float) thisCurrentPosition / thisSongDuration * 10000);

					songPlayingTimeLength.setText(timeFormatter.format(thisCurrentPosition));
					seekBar.setProgress(prg);
					playIcon.setImageResource(R.drawable.player_pause);
				} else {
					playIcon.setImageResource(R.drawable.player_playing);
				}
			}
		};

		seekBarThread = new Thread(new Runnable() {
			public void run() {
				try {
					while (true) {
						if (isCheckSeekBar) {
							handler.post(callback);
						}
						Thread.sleep(1000);
					}

				} catch (InterruptedException e) {
					Log.d(AppConf.LOG_TAG3, "thread::seekBarThread is interrupted.");
				}
				Log.d(AppConf.LOG_TAG3, "thread::seekBarThread is terminated.");
			}
		});
		seekBarThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable ex) {
				Log.d(AppConf.LOG_TAG3, "SeekBar thread recieved unCatchedException:" + ex.getMessage());
			}
		});
		Log.d(AppConf.LOG_TAG3, "thread::seekBarThread start");
		seekBarThread.start();
	}

	private void loadAlbumCover(final Song playingSong) {

		final Handler aHandler = new Handler();
		final Runnable aCallback = new Runnable() {
			public void run() {
				playerAlbumImage.setImageBitmap(albumBitmap);
				// reset image reflection
				Bitmap refPlayerAlbumImage = com.music.util.Image.makeReflectionBitmap(albumBitmap, albumRefHight);
				playerAlbumImageReflection.setImageBitmap(refPlayerAlbumImage);

				loadAlbumImageThread = null;
			}
		};

		loadAlbumImageThread = new Thread(new Runnable() {
			public void run() {
				Drawable defaultAlbum = Player.this.getResources().getDrawable(R.drawable.default_album320);
				DisplayMetrics dm = new DisplayMetrics();
				getWindowManager().getDefaultDisplay().getMetrics(dm);

				try {
					Log.d(AppConf.LOG_TAG3, "loadAlbumCover load=" + playingSong.getCoverPictureUrl());
					Bitmap ablum = com.music.util.Image.getImageBitMap(playingSong.getCoverPictureUrl(), defaultAlbum);
					Log.d(AppConf.LOG_TAG3, "loadAlbumCover bitmap w=" + ablum.getWidth() + "; h=" + ablum.getHeight());
					if ((dm.densityDpi >= DisplayMetrics.DENSITY_HIGH)) {
						albumBitmap = com.music.util.Image.scaleImg(ablum, albumImageDefaultScale,
								albumImageDefaultScale);
					} else
						albumBitmap = ablum;

				} catch (Exception e) {
					albumBitmap = com.music.util.Image.drawableToBitmap(defaultAlbum);
				}
				aHandler.post(aCallback);
			}
		});
		loadAlbumImageThread.start();
	}

	private void loadLyricsAlbumCover(final Song playingSong) {
		final Handler aHandler = new Handler();
		final Runnable aCallback = new Runnable() {
			public void run() {
				ImageView albumView = (ImageView) findViewById(R.id.lite_album_image);
				albumView.setImageBitmap(lyricsAlbumBitmap);

				loadingAlbumLyricImageThread = null;
			}
		};

		loadingAlbumLyricImageThread = new Thread(new Runnable() {
			public void run() {
				Drawable defaultAlbum = Player.this.getResources().getDrawable(R.drawable.default_album60);
				try {
					Log.d(AppConf.LOG_TAG3, "loadLyricsAlbumCover load=" + playingSong.getCoverPictureUrl());
					lyricsAlbumBitmap = com.music.util.Image.getImageBitMap(playingSong.getCoverPictureUrl(),
							defaultAlbum);
				} catch (Exception e) {
					lyricsAlbumBitmap = com.music.util.Image.drawableToBitmap(defaultAlbum);
				}
				aHandler.post(aCallback);
			}
		});
		loadingAlbumLyricImageThread.start();
	}

	private void updatePlayingSongRes(final Song playingSong) {
		try {
			updatePlayingSongLength();
			playingSongNameText.setText(playingSong.getSongTitle());
			playingSongExtraText.setText(playingSong.getSingerName() + " / " + playingSong.getAlbumTitle());

			// lyrics
			TextView lyricsSongNameText = (TextView) findViewById(R.id.lyrics_playing_song_name);
			TextView lyricsSongExtraText = (TextView) findViewById(R.id.lyrics_playing_singer_and_album_name);
			TextView fullLyricsText = (TextView) findViewById(R.id.full_lyrics_text);
			lyricsSongNameText.setText(playingSong.getSongTitle());
			lyricsSongExtraText.setText(playingSong.getSingerName() + " / " + playingSong.getAlbumTitle());

			String lyricsText = playingSong.getLyricsText();
			if (lyricsText.length() > 0) {
				lyricsText = lyricsText.replaceAll("\\[[0-9:.]{8}\\]", "\n");
				fullLyricsText.setText(lyricsText);
			} else
				fullLyricsText.setText("");
		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG3, "updatePlayingSongRes exception", e);
		}
	}

	private void updatePlayingSongLength() {
		try {
			thisSongDuration = (float) iPlayer.getDuration();
			songTotalTimeLength.setText(timeFormatter.format(thisSongDuration));
		} catch (Exception e) {
			// should not be
			Log.e(AppConf.LOG_TAG3, "updatePlayingSongLength exception", e);
		}
	}

	private void clearPlayingSongRes() {
		try {
			songTotalTimeLength.setText("00:00");
			playingSongNameText.setText("");
			playingSongExtraText.setText("");

			TextView lyricsSongNameText = (TextView) findViewById(R.id.lyrics_playing_song_name);
			TextView lyricsSongExtraText = (TextView) findViewById(R.id.lyrics_playing_singer_and_album_name);
			lyricsSongNameText.setText("");
			lyricsSongExtraText.setText("");

			TextView fullLyricsText = (TextView) findViewById(R.id.full_lyrics_text);
			fullLyricsText.setText("");
		} catch (Exception e) {
			// should not be
			Log.e(AppConf.LOG_TAG3, "clearPlayingSongRes exception", e);
		}
	}

	private void togglePanel(MenuItem item) {
		if (showLyrics) {
			TextView fullLyricsText = (TextView) findViewById(R.id.full_lyrics_text);

			if (fullLyricsText.getText().length() > 0) {
				if (item != null) {
					item.setTitle(R.string.menu_Play).setIcon(R.drawable.menu_player);
				}
				hiddenPlayerPanel();
				showFullLyricsPanel();
			} else {
				showLyrics = false;

				AlertDialog.Builder builder = new AlertDialog.Builder(Player.this);
				builder.setTitle(R.string.errHead_Remind).setMessage(R.string.err_LyricsEmpty).setCancelable(false)
						.setPositiveButton(R.string.btnConfirm, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.dismiss();
							}
						});
				AlertDialog alert = builder.create();
				alert.show();
			}
		} else {
			if (item != null) {
				item.setTitle(R.string.menu_ShowLyrics).setIcon(R.drawable.menu_lyrics);
			}
			hiddenFullLyricsPanel();
			showPlayerPanel();
		}
	}

	private void download() {
		AlertDialog.Builder builder = new AlertDialog.Builder(Player.this);
		AlertDialog alert = null;
		try {
			boPlayer.add2Download(iPlayer.getCurrentSong());

			// alert
			builder.setTitle(R.string.errHead_Remind).setMessage(R.string.download_msg_AddSuccess).setCancelable(false)
					.setPositiveButton(R.string.btnConfirm, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
						}
					});
			alert = builder.create();
			alert.show();
		} catch (BOException boe) {
			builder.setTitle(R.string.errHead_Remind).setMessage(boe.getMessage()).setCancelable(false)
					.setPositiveButton(R.string.btnConfirm, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
						}
					});
			alert = builder.create();
			alert.show();
		} finally {
		}
	}

	private void showErrDialog(int msgType) {
		AlertDialog.Builder builder = new AlertDialog.Builder(Player.this);
		AlertDialog alert = null;

		switch (msgType) {
		case PlayerService.PLAYER_MSG_MULTILOGIN:
			builder.setTitle(R.string.errHead_Logout).setMessage(R.string.err_MultiLogin).setCancelable(false)
					.setPositiveButton(R.string.btnLogin, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							com.music.bo.Setting boSetting = new com.music.bo.Setting(Player.this);
							boSetting.logoutProcess(false);
							boSetting = null;

							Intent payActivity = new Intent(Player.this, Home.class);
							payActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
							startActivity(payActivity);

							iPlayer.confirmErrorDialog();
							dialog.dismiss();
							Player.this.finish();
						}
					});
			alert = builder.create();
			alert.show();
			break;
		case PlayerService.PLAYER_MSG_NOSONGLIST:
			builder.setTitle(R.string.err_StatusUnknown).setMessage(R.string.err_setNoSongList).setCancelable(false)
					.setPositiveButton(R.string.btnConfirm, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							iPlayer.confirmErrorDialog();
							dialog.dismiss();
							Player.this.finish();
						}
					});
			alert = builder.create();
			alert.show();
			break;
		case PlayerService.PLAYER_MSG_PLAYER_CRASH:
			builder.setTitle(R.string.err_StatusUnknown).setMessage(R.string.err_playerCrash).setCancelable(false)
					.setPositiveButton(R.string.btnConfirm, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							iPlayer.confirmErrorDialog();
							dialog.dismiss();
							Player.this.finish();
						}
					});
			alert = builder.create();
			alert.show();
			break;
		case PlayerService.PLAYER_MSG_OVER_CREDIT:
			builder.setTitle(R.string.errHead_UnPaidUser).setMessage(R.string.palyer_msg_Up2DailyLimit)
					.setCancelable(false).setPositiveButton(R.string.btnGoPay, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							iPlayer.setOverUnapidCreditAction(PlayerService.OVER_ACTION_STOP);
							Intent overActivity = new Intent(Player.this, WebOperator.class);
							overActivity.putExtra("cmd", WebOperator.CMD_PAY);
							startActivity(overActivity);
							dialog.dismiss();
							Player.this.finish();
						}
					}).setNeutralButton(R.string.btnKeepTry, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							iPlayer.setOverUnapidCreditAction(PlayerService.OVER_ACTION_PLAY);
							dialog.dismiss();
						}
					});
			alert = builder.create();
			alert.show();
			break;
		case PlayerService.PLAYER_MSG_2MANAY_ERROR:
			builder.setTitle(R.string.errHead_Remind).setMessage(R.string.err_TooManyError).setCancelable(false)
					.setPositiveButton(R.string.btnConfirm, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							iPlayer.confirmErrorDialog();
							dialog.dismiss();
							// terminate player
							Player.this.finish();
						}
					});
			alert = builder.create();
			alert.show();
			break;
		}
	}

	/*
	 * inner classes
	 */
	private class MusicReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();
			Integer akind = bundle.getInt("event");

			switch (akind) {
			case PlayerService.SONG_TOAST_ERROR:
				if (dialogLoad != null) {
					if (dialogLoad.isShowing()) {
						dialogLoad.dismiss();
					}
				}
				String msg = bundle.getString("msg");
				Toast.makeText(Player.this, msg, Toast.LENGTH_SHORT).show();
				break;
			case PlayerService.SONG_DIALOG_ERROR:
				Log.d(AppConf.LOG_TAG3, "player recieve: song error dialog:" + bundle.getString("msg_type"));
				if (dialogLoad != null) {
					if (dialogLoad.isShowing()) {
						dialogLoad.dismiss();
					}
				}
				try {
					int type = Integer.parseInt(bundle.getString("msg_type"));
					showErrDialog(type);
				} catch (NumberFormatException ne) {
					Log.e(AppConf.LOG_TAG, "parse integer error:" + bundle.getString("msg_type"), ne);
				}
				break;
			case PlayerService.SONG_PREPARE_ERROR:
				Log.d(AppConf.LOG_TAG3, "recieve: song preapre error");
				break;
			case PlayerService.SONG_PLAY_ERROR:
				if (dialogLoad != null) {
					if (dialogLoad.isShowing()) {
						dialogLoad.dismiss();
					}
				}
				Toast.makeText(Player.this, R.string.err_LoadingMusic, Toast.LENGTH_SHORT).show();
				break;
			case PlayerService.SONG_LOADING:
				Log.d(AppConf.LOG_TAG3, "player recieve: song loading");
				// refresh UI
				playIcon.setImageResource(R.drawable.player_play);
				playerStatus = PLAY_CTRL_STOP;

				if (dialogLoad != null) {
					if (!dialogLoad.isShowing()) {
						dialogLoad.show();
					}
				}
				break;
			case PlayerService.SONG_PLAY_COMPLETION:
				Log.d(AppConf.LOG_TAG3, "player recieve: song play completion");
				// refresh UI
				playIcon.setImageResource(R.drawable.player_play);
				playerStatus = PLAY_CTRL_STOP;
				break;
			case PlayerService.SONG_SEEK_COMPLETION:
				Log.d(AppConf.LOG_TAG3, "player recieve: song seek completion");
				// for seekBarThread using
				isCheckSeekBar = true;

				// refresh UI
				playIcon = (ImageView) findViewById(R.id.player_pause);
				playerStatus = PLAY_CTRL_PLAY;

				if (dialogLoad != null) {
					if (dialogLoad.isShowing()) {
						dialogLoad.dismiss();
					}
				}
				break;
			case PlayerService.SONG_INCOMING_CALL:
				Log.d(AppConf.LOG_TAG3, "player recieve: song play incoming call pause");
				// refresh UI
				playIcon.setImageResource(R.drawable.player_playing);
				playerStatus = PLAY_CTRL_PAUSE;

				if (dialogLoad != null) {
					if (dialogLoad.isShowing()) {
						dialogLoad.dismiss();
					}
				}
				break;
			case PlayerService.SONG_PREPARE_OK:
				Log.d(AppConf.LOG_TAG3, "player recieve: song preapre ok");
				// control flag
				isCheckSeekBar = true;

				// refresh UI
				playIcon = (ImageView) findViewById(R.id.player_pause);
				playerStatus = PLAY_CTRL_PLAY;
				updatePlayingSongLength();

				if (dialogLoad != null) {
					if (dialogLoad.isShowing()) {
						dialogLoad.dismiss();
					}
				}
				break;
			case PlayerService.SONG_PLAY_STOP:
				Log.d(AppConf.LOG_TAG3, "player recieve: song play stop");
				// control flag
				isCheckSeekBar = false;

				// refresh UI
				playIcon.setImageResource(R.drawable.player_play);
				playerStatus = PLAY_CTRL_STOP;
				songPlayingTimeLength.setText("00:00");
				seekBar.setProgress(0);
				break;
			case PlayerService.SONG_PLAY_PAUSE:
				Log.d(AppConf.LOG_TAG3, "player recieve: song play pause");

				// refresh UI
				playIcon.setImageResource(R.drawable.player_playing);
				playerStatus = PLAY_CTRL_PAUSE;

				if (dialogLoad != null) {
					if (dialogLoad.isShowing()) {
						dialogLoad.dismiss();
					}
				}
				break;
			case PlayerService.SONG_INFO_READY:
				Log.d(AppConf.LOG_TAG3, "player recieve: song info ready");
				// refresh UI
				playerStatus = PLAY_CTRL_STOP;
				loadAlbumCover(iPlayer.getCurrentSong());
				loadLyricsAlbumCover(iPlayer.getCurrentSong());
				updatePlayingSongRes(iPlayer.getCurrentSong());
				songPlayingTimeLength.setText("00:00");
				seekBar.setProgress(0);
				break;
			case PlayerService.SONG_RESET:
				Log.d(AppConf.LOG_TAG3, "player recieve: song is reseted");
				if (dialogLoad != null) {
					if (!dialogLoad.isShowing()) {
						dialogLoad.show();
					}
				}

				// refresh UI
				playIcon = (ImageView) findViewById(R.id.player_pause);
				playerStatus = PLAY_CTRL_PLAY;
				songPlayingTimeLength.setText("00:00");
				seekBar.setProgress(0);
				break;
			default:
				break;
			}
		}
	};
}
