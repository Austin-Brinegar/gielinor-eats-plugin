package com.gielinoreats.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ActiveOrder
{
	@SerializedName("orderId")
	public String orderId;

	/** "requester" or "runner" */
	@SerializedName("role")
	public String role;

	@SerializedName("leadRunnerRsn")
	public String leadRunnerRsn;

	@SerializedName("requesterRsn")
	public String requesterRsn;

	@SerializedName("requesterWorld")
	public int requesterWorld;

	@SerializedName("requesterX")
	public int requesterX;

	@SerializedName("requesterY")
	public int requesterY;

	@SerializedName("requesterPlane")
	public int requesterPlane;

	/** Items the requester wants delivered. Populated by sync-presence when caller is a runner in PREPARING status. */
	@SerializedName("requestedItems")
	public List<OrderItem> requestedItems;

	public boolean isRequester()
	{
		return "requester".equals(role);
	}

	public boolean isRunner()
	{
		return "runner".equals(role);
	}
}
