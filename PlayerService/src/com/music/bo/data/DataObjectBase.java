package com.music.bo.data;

import android.content.ContentValues;

abstract public class DataObjectBase {

	public DataObjectBase() {
	}

	abstract protected ContentValues getDataValues();
}
