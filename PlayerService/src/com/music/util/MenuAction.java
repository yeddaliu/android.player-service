package com.music.util;

import com.music.R;
import com.music.app.Home;
import com.music.bo.data.UserLoginShareInfo;
import com.music.helper.AppConf;
import com.music.helper.TabConf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;

public class MenuAction {
	/**
	 * force to kill process
	 */
	public static void killProcess(final Activity activity) {
		if (!(activity instanceof Home)) {
			Intent intent = new Intent(activity, Home.class);
			intent.putExtra(Home.ASK_FOR_EXIT, true);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			activity.startActivity(intent);
		}

		// stop download service
		if (LoginStatusHandler.isOnline(activity) && UserLoginShareInfo.getInstance(activity).isUserPaid()) {
			Intent intentService = new Intent(AppConf.ACTION_FILTER_DOWNLOAD);
			activity.stopService(intentService);
		}

		// stop player service
		Intent intentPlayerService = new Intent(AppConf.ACTION_FILTER_PLAYER);
		activity.stopService(intentPlayerService);
		activity.finish();

		Log.d(AppConf.LOG_TAG, "kill pid:" + android.os.Process.myPid());
		android.os.Process.killProcess(android.os.Process.myPid());
	}

	public static void doExit(final Activity activity) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(R.string.errHead_Remind).setMessage(R.string.menu_msg_Exit).setCancelable(false)
				.setPositiveButton(R.string.btnConfirm, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						MenuAction.killProcess(activity);
						dialog.dismiss();
					}
				}).setNegativeButton(R.string.btnCancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}
}
