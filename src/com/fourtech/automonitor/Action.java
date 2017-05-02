package com.fourtech.automonitor;

public class Action {
	public static final byte TRIGGER = 0;
	public static final byte DOOR_OPENED = 1;
	public static final byte DOOR_CLOSED = 2;

	private int mId = 0;
	private long mTime = 0;
	private byte mAction = 0;

	public Action(int id, long time, byte action) {
		super();
		setId(id);
		setTime(time);
		setAction(action);
	}

	public int getId() {
		return mId;
	}

	public void setId(int id) {
		this.mId = id;
	}

	public long getTime() {
		return mTime;
	}

	public void setTime(long time) {
		mTime = time;
	}

	public byte getAction() {
		return mAction;
	}

	public void setAction(byte action) {
		mAction = action;
	}

}