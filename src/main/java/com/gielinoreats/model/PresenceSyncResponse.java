package com.gielinoreats.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PresenceSyncResponse
{
	@SerializedName("serverTime")
	public String serverTime;

	@SerializedName("orderStatus")
	public String orderStatus;

	@SerializedName("activeOrder")
	public ActiveOrder activeOrder;

	@SerializedName("notifications")
	public List<NotificationEntry> notifications;
}
