package com.gielinoreats.model;

import com.google.gson.annotations.SerializedName;

public class NotificationEntry
{
	@SerializedName("timestamp")
	public String timestamp;

	@SerializedName("type")
	public String type;

	@SerializedName("message")
	public String message;
}
