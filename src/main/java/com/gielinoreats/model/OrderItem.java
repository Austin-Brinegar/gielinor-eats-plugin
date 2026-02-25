package com.gielinoreats.model;

import com.google.gson.annotations.SerializedName;

public class OrderItem
{
	@SerializedName("itemId")
	public int itemId;

	@SerializedName("quantity")
	public int quantity;

	public OrderItem(int itemId, int quantity)
	{
		this.itemId = itemId;
		this.quantity = quantity;
	}
}
