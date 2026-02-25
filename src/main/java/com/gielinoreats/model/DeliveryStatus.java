package com.gielinoreats.model;

public enum DeliveryStatus
{
	AWAITING_RUNNERS("awaiting_runners"),
	PREPARING("preparing"),
	RUNNER_EN_ROUTE("runner_en_route"),
	RUNNER_NEAR("runner_near"),
	PENDING_TRADE("pending_trade"),
	COMPLETED("completed"),
	DISPUTED("disputed"),
	CANCELLED("cancelled"),
	ABANDONED("abandoned"),
	UNKNOWN("unknown");

	private final String value;

	DeliveryStatus(String value)
	{
		this.value = value;
	}

	public String getValue()
	{
		return value;
	}

	public static DeliveryStatus fromString(String s)
	{
		if (s == null)
		{
			return null;
		}
		for (DeliveryStatus ds : values())
		{
			if (ds.value.equalsIgnoreCase(s))
			{
				return ds;
			}
		}
		return UNKNOWN;
	}

	public boolean isTerminal()
	{
		return this == COMPLETED || this == CANCELLED || this == DISPUTED || this == ABANDONED;
	}
}
