package com.music.bo;

public class BOException extends Exception {
	private static final long serialVersionUID = 1L;

	private int errCode;

	public BOException(String detailMessage, int errorCode) {
		super(detailMessage);
		this.errCode = errorCode;
	}

	public BOException(String detailMessage, int errorCode, Throwable throwable) {
		super(detailMessage, throwable);
		this.errCode = errorCode;
	}

	public BOException(String detailMessage) {
		super(detailMessage);
	}

	public int getErrorCode() {
		return this.errCode;
	}
}
