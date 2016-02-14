package com.music.bo.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * cache user login type and info
 */
public class UserLoginShareInfo {
	public static UserLoginShareInfo instance;

	private static final String REF_NAME = "userInfo";
	private static final String COL_USER_ID = "user_id";
	private static final String COL_PAYTYPE_ID = "pay_type_id";
	private static final String COL_PAYTYPE_NAME = "pay_type_name";
	private static final String COL_PAY_DUE = "pay_due";
	private static final String COL_SERVER_TIME = "server_time";
	private static final String COL_PAID_TYPE = "paid_type";
	private static final String COL_ACTIVE_FLAG = "is_active";

	private SharedPreferences ref;
	private String defValue = "";
	private int defIntValue = 0;
	private boolean defBolValue = false;

	/**
	 * private constructor
	 */
	private UserLoginShareInfo(Context context) {
		this.ref = context.getSharedPreferences(REF_NAME, Context.MODE_PRIVATE);
		if (!this.isActive()) {
			this.setActive(true);
		}
	}

	/**
	 * get UserLoginShareInfo
	 */
	public static UserLoginShareInfo getInstance(Context context) {
		if (instance != null) {
			return instance;
		}
		instance = new UserLoginShareInfo(context);
		return instance;
	}

	public void setActive(boolean val) {
		ref.edit().putBoolean(COL_ACTIVE_FLAG, val).commit();
	}

	public boolean isActive() {
		return ref.getBoolean(COL_ACTIVE_FLAG, defBolValue);
	}

	public void setUserID(String uid) {
		ref.edit().putString(COL_USER_ID, (uid == null) ? "" : uid).commit();
	}

	public String getUserID() {
		return ref.getString(COL_USER_ID, defValue);
	}

	public void setPayTypeID(String val) {
		ref.edit().putString(COL_PAYTYPE_ID, (val == null) ? "" : val).commit();
	}

	public String getPayTypeID() {
		return ref.getString(COL_PAYTYPE_ID, defValue);
	}

	public boolean isUserPaid() {
		String payID = this.getPayTypeID();
		return payID.compareTo("0") == 0 ? false : true;
	}

	public void setPayTypeName(String val) {
		ref.edit().putString(COL_PAYTYPE_NAME, (val == null) ? "" : val).commit();
	}

	public String getPayTypeName() {
		return ref.getString(COL_PAYTYPE_NAME, defValue);
	}

	public void setPayDueDate(String val) {
		ref.edit().putString(COL_PAY_DUE, (val == null) ? "" : val).commit();
	}

	public String getPayDueDate() {
		return ref.getString(COL_PAY_DUE, defValue);
	}

	public void setServerTime(String val) {
		ref.edit().putString(COL_SERVER_TIME, (val == null) ? "" : val).commit();
	}

	public String getServerTime() {
		return ref.getString(COL_SERVER_TIME, defValue);
	}

	public void setPaidType(String val) {
		ref.edit().putString(COL_PAID_TYPE, (val == null) ? "" : val).commit();
	}

	public String getPaidType() {
		return ref.getString(COL_PAID_TYPE, defValue);
	}
}
