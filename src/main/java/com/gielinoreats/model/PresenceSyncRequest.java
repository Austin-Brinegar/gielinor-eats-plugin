package com.gielinoreats.model;

import com.google.gson.annotations.SerializedName;

public class PresenceSyncRequest
{
	@SerializedName("rsn")
	public final String rsn;

	@SerializedName("world")
	public final int world;

	@SerializedName("x")
	public final int x;

	@SerializedName("y")
	public final int y;

	@SerializedName("plane")
	public final int plane;

	@SerializedName("lastPollTime")
	public final String lastPollTime;

	public PresenceSyncRequest(String rsn, int world, int x, int y, int plane, String lastPollTime)
	{
		this.rsn = rsn;
		this.world = world;
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.lastPollTime = lastPollTime;
	}
}
