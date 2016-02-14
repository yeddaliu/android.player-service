package com.music.helper;

import com.music.R;

public enum ErrorCode {
	StatusUnknown(R.string.err_StatusUnknown, -1001),
	XMLWrong(R.string.err_WrongXML, -1002),
	HTTPException(R.string.err_HTTPException, -1003),
	NetworkMissing(R.string.err_NetworkMissing, -1004),
	SQLException(R.string.err_SQLException, -1005),
	SyncPlaylistSQLException(R.string.err_SyncPlaylistSQLException, -1006);

	private int errMsgID;
	private int errCode;

	private ErrorCode(int msgid, int code) {
		this.errCode = code;
		this.errMsgID = msgid;
	}

	public int getCode() {
		return errCode;
	}

	public int getMessageResourceID() {
		return errMsgID;
	}

}
