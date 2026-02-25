package com.gielinoreats.api;

import com.gielinoreats.model.OrderItem;
import com.gielinoreats.model.PresenceSyncRequest;
import com.gielinoreats.model.PresenceSyncResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class ApiClient
{
	private static final String BASE_URL = "https://jnlteucpmtaotdmdsiht.supabase.co/functions/v1/";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	private final OkHttpClient http;
	private final Gson gson = new Gson();
	private final String token;

	public ApiClient(OkHttpClient http, String token)
	{
		this.http = http;
		this.token = token;
	}

	// ── Presence sync ─────────────────────────────────────────────────────────

	public void syncPresence(PresenceSyncRequest payload, Consumer<PresenceSyncResponse> onSuccess)
	{
		String json = gson.toJson(payload);
		Request req = buildPost("sync-presence", json);
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("sync-presence failed", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("sync-presence returned {}", response.code());
						return;
					}
					String body = response.body().string();
					PresenceSyncResponse parsed = gson.fromJson(body, PresenceSyncResponse.class);
					SwingUtilities.invokeLater(() -> onSuccess.accept(parsed));
				}
			}
		});
	}

	// ── Report inventory ready ─────────────────────────────────────────────────

	public void reportInventoryReady(String orderId, Consumer<InventoryReadyResponse> onSuccess)
	{
		JsonObject body = new JsonObject();
		body.addProperty("orderId", orderId);
		Request req = buildPost("report-inventory-ready", gson.toJson(body));
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("report-inventory-ready failed", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("report-inventory-ready returned {}", response.code());
						return;
					}
					String responseBody = response.body().string();
					InventoryReadyResponse parsed = gson.fromJson(responseBody, InventoryReadyResponse.class);
					SwingUtilities.invokeLater(() -> onSuccess.accept(parsed));
				}
			}
		});
	}

	// ── Report trade completed ─────────────────────────────────────────────────

	public void reportTradeCompleted(String orderId, List<OrderItem> itemsGiven, List<OrderItem> itemsReceived)
	{
		JsonObject body = new JsonObject();
		body.addProperty("orderId", orderId);
		body.add("itemsGiven", gson.toJsonTree(itemsGiven));
		body.add("itemsReceived", gson.toJsonTree(itemsReceived));
		Request req = buildPost("report-trade-completed", gson.toJson(body));
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("report-trade-completed failed", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (response)
				{
					if (!response.isSuccessful())
					{
						log.warn("report-trade-completed returned {}", response.code());
					}
				}
			}
		});
	}

	// ── Report trade abandoned ─────────────────────────────────────────────────

	public void reportTradeAbandoned(String orderId)
	{
		JsonObject body = new JsonObject();
		body.addProperty("orderId", orderId);
		Request req = buildPost("report-trade-abandoned", gson.toJson(body));
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("report-trade-abandoned failed", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (response)
				{
					if (!response.isSuccessful())
					{
						log.warn("report-trade-abandoned returned {}", response.code());
					}
				}
			}
		});
	}

	// ── Link account ──────────────────────────────────────────────────────────

	public void linkAccount(String rsn, String linkCode, Consumer<Boolean> onResult)
	{
		JsonObject body = new JsonObject();
		body.addProperty("rsn", rsn);
		body.addProperty("linkCode", linkCode);
		Request req = buildPost("link-account", gson.toJson(body));
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("link-account failed", e);
				SwingUtilities.invokeLater(() -> onResult.accept(false));
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (response)
				{
					boolean success = response.isSuccessful();
					SwingUtilities.invokeLater(() -> onResult.accept(success));
				}
			}
		});
	}

	// ── Internal helpers ──────────────────────────────────────────────────────

	private Request buildPost(String endpoint, String jsonBody)
	{
		return new Request.Builder()
			.url(BASE_URL + endpoint)
			.addHeader("Authorization", "Bearer " + token)
			.addHeader("Content-Type", "application/json")
			.post(RequestBody.create(jsonBody, JSON))
			.build();
	}

	// ── Response POJOs ────────────────────────────────────────────────────────

	public static class InventoryReadyResponse
	{
		public int requesterWorld;
		public int requesterX;
		public int requesterY;
		public int requesterPlane;
	}
}
