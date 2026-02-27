package com.gielinoreats;

import com.gielinoreats.api.ApiClient;
import com.gielinoreats.model.ActiveOrder;
import com.gielinoreats.model.DeliveryStatus;
import com.gielinoreats.model.NotificationEntry;
import com.gielinoreats.model.OrderItem;
import com.gielinoreats.model.PresenceSyncRequest;
import com.gielinoreats.model.PresenceSyncResponse;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Gielinor Eats",
	description = "In-game delivery service. Requires the Shortest Path plugin.",
	tags = {"delivery", "trade", "social"}
)
public class GielinorEatsPlugin extends Plugin
{
	private static final int PROXIMITY_THRESHOLD = 15;
	private static final String CONFIG_GROUP = "gielinoreats";
	private static final String WEB_APP_URL = "https://gielinoreats.com";

	@Inject
	private Client client;

	@Inject
	private GielinorEatsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Notifier notifier;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OkHttpClient okHttpClient;

	private GielinorEatsPanel panel;
	private NavigationButton navButton;
	private ApiClient apiClient;

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> presenceSyncFuture;

	// Cached state — written on scheduler thread, read on client/EDT thread
	private volatile DeliveryStatus lastKnownOrderStatus;
	private volatile String activeOrderId;
	private volatile ActiveOrder activeOrder;
	private volatile String lastPollTime;
	private volatile boolean lastInventoryCheckResult = false;

	// Inventory snapshot taken when runner enters PENDING_TRADE, used to detect
	// what items were given/received when the trade window closes
	private volatile Map<Integer, Integer> inventorySnapshot = null;

	// ── Plugin lifecycle ───────────────────────────────────────────────────────

	@Override
	protected void startUp()
	{
		ensurePluginToken();
		apiClient = new ApiClient(okHttpClient, config.pluginToken());
		panel = new GielinorEatsPanel(this);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/gielinoreats/gielinoreats_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Gielinor Eats")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		checkShortestPathInstalled();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			startPresenceSync();
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		stopPresenceSync();
		lastKnownOrderStatus = null;
		activeOrderId = null;
		activeOrder = null;
		lastPollTime = null;
		lastInventoryCheckResult = false;
		inventorySnapshot = null;
	}

	@Provides
	GielinorEatsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GielinorEatsConfig.class);
	}

	// ── Event handlers ─────────────────────────────────────────────────────────

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				startPresenceSync();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				stopPresenceSync();
				inventorySnapshot = null;
				// Clear panel state so a different account on the same client
				// does not see the previous session's notifications or order status
				SwingUtilities.invokeLater(() ->
				{
					panel.showIdleState();
					panel.clearNotifications();
				});
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}

		// ── Runner: inventory ready check (PREPARING) ──────────────────────────
		if (lastKnownOrderStatus == DeliveryStatus.PREPARING
			&& activeOrder != null && activeOrderId != null)
		{
			boolean nowReady = checkInventoryAgainstOrder(event.getItemContainer());
			if (nowReady && !lastInventoryCheckResult)
			{
				apiClient.reportInventoryReady(activeOrderId, response ->
					setShortestPathDestination(new WorldPoint(
						response.requesterX, response.requesterY, response.requesterPlane)));
			}
			lastInventoryCheckResult = nowReady;
		}

		// ── Runner: trade completion detection (PENDING_TRADE) ─────────────────
		// Strategy: snapshot inventory when we enter PENDING_TRADE; on each
		// subsequent inventory change, check whether the items we were supposed
		// to deliver have left our inventory. If so, the trade completed.
		if (lastKnownOrderStatus == DeliveryStatus.PENDING_TRADE
			&& activeOrder != null && activeOrder.isRunner()
			&& activeOrderId != null)
		{
			Map<Integer, Integer> snapshot = inventorySnapshot;
			if (snapshot == null)
			{
				return;
			}

			Map<Integer, Integer> current = countItems(event.getItemContainer());

			// Verify all requested items have left the inventory
			if (activeOrder.requestedItems != null && !activeOrder.requestedItems.isEmpty()
				&& allItemsDelivered(snapshot, current))
			{
				// Prevent double-reporting
				inventorySnapshot = null;

				List<OrderItem> given = itemsDecreased(snapshot, current);
				List<OrderItem> received = itemsIncreased(snapshot, current);
				apiClient.reportTradeCompleted(activeOrderId, given, received);
			}
		}
	}

	// ── Presence sync ──────────────────────────────────────────────────────────

	private void startPresenceSync()
	{
		if (presenceSyncFuture != null && !presenceSyncFuture.isDone())
		{
			return;
		}
		// Fire immediately, then every 60 seconds
		presenceSyncFuture = scheduler.scheduleAtFixedRate(this::syncPresence, 0, 60, TimeUnit.SECONDS);
	}

	private void stopPresenceSync()
	{
		if (presenceSyncFuture != null)
		{
			presenceSyncFuture.cancel(false);
			presenceSyncFuture = null;
		}
	}

	private void syncPresence()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		WorldPoint loc = local.getWorldLocation();
		PresenceSyncRequest request = new PresenceSyncRequest(
			local.getName(),
			client.getWorld(),
			loc.getX(),
			loc.getY(),
			loc.getPlane(),
			lastPollTime
		);

		apiClient.syncPresence(request, this::handlePresenceResponse);
	}

	private void handlePresenceResponse(PresenceSyncResponse response)
	{
		lastPollTime = response.serverTime;

		DeliveryStatus newStatus = DeliveryStatus.fromString(response.orderStatus);
		ActiveOrder newOrder = response.activeOrder;

		// Snapshot inventory on transition into PENDING_TRADE (runner only)
		if (newStatus == DeliveryStatus.PENDING_TRADE
			&& lastKnownOrderStatus != DeliveryStatus.PENDING_TRADE
			&& newOrder != null && newOrder.isRunner()
			&& inventorySnapshot == null)
		{
			ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
			if (inv != null)
			{
				inventorySnapshot = countItems(inv);
			}
		}

		// Clear snapshot if no longer in PENDING_TRADE
		if (newStatus != DeliveryStatus.PENDING_TRADE)
		{
			inventorySnapshot = null;
		}

		lastKnownOrderStatus = newStatus;
		activeOrder = newOrder;
		activeOrderId = newOrder != null ? newOrder.orderId : null;

		// Reset inventory-ready edge guard when leaving PREPARING
		if (newStatus != DeliveryStatus.PREPARING)
		{
			lastInventoryCheckResult = false;
		}

		SwingUtilities.invokeLater(() -> panel.updateStatus(response));
		processNotifications(response.notifications);
		checkTradeAbandonCondition(newStatus, newOrder);
	}

	// ── Trade abandon proximity check ──────────────────────────────────────────

	private void checkTradeAbandonCondition(DeliveryStatus status, ActiveOrder order)
	{
		if (status != DeliveryStatus.PENDING_TRADE)
		{
			return;
		}
		if (order == null || !order.isRunner())
		{
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}

		WorldPoint runnerLoc = local.getWorldLocation();

		boolean wrongWorld = client.getWorld() != order.requesterWorld;
		boolean wrongPlane = runnerLoc.getPlane() != order.requesterPlane;
		int chebyshev = Math.max(
			Math.abs(runnerLoc.getX() - order.requesterX),
			Math.abs(runnerLoc.getY() - order.requesterY)
		);

		if (wrongWorld || wrongPlane || chebyshev > PROXIMITY_THRESHOLD)
		{
			apiClient.reportTradeAbandoned(activeOrderId);
		}
	}

	// ── Notifications ──────────────────────────────────────────────────────────

	private void processNotifications(List<NotificationEntry> notifications)
	{
		if (notifications == null)
		{
			return;
		}
		for (NotificationEntry n : notifications)
		{
			SwingUtilities.invokeLater(() -> panel.addNotification(n));
			fireDesktopNotification(n);
		}
	}

	private void fireDesktopNotification(NotificationEntry n)
	{
		if (n.type == null)
		{
			return;
		}
		switch (n.type)
		{
			case "runner_accepted":
				if (config.notifyRunnerAccepted())
				{
					notifier.notify("A runner has accepted your order!");
				}
				break;
			case "runner_near":
				if (config.notifyRunnerNear())
				{
					notifier.notify("A runner is nearby — prepare to trade.");
				}
				break;
			case "order_cancelled":
				if (config.notifyOrderCancelled())
				{
					notifier.notify("Your order has been cancelled.");
				}
				break;
			case "order_completed":
				if (config.notifyOrderCompleted())
				{
					notifier.notify("Your order has been completed!");
				}
				break;
			case "competing_runner_completed":
				if (config.notifyCompetingRunnerCompleted())
				{
					notifier.notify("Another runner completed this order.");
				}
				break;
			default:
				break;
		}
	}

	// ── Inventory helpers ──────────────────────────────────────────────────────

	/**
	 * Check whether the inventory satisfies all items in the active order.
	 * Uses RAW item IDs — does NOT canonicalize. Noted items must not satisfy
	 * unnoted requests.
	 */
	private boolean checkInventoryAgainstOrder(ItemContainer inventory)
	{
		if (activeOrder == null || activeOrder.requestedItems == null)
		{
			return false;
		}

		Map<Integer, Integer> counts = countItems(inventory);
		for (OrderItem required : activeOrder.requestedItems)
		{
			if (counts.getOrDefault(required.itemId, 0) < required.quantity)
			{
				return false;
			}
		}
		return true;
	}

	/** Returns true if every requested item has decreased by at least the required quantity. */
	private boolean allItemsDelivered(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		for (OrderItem required : activeOrder.requestedItems)
		{
			int had = before.getOrDefault(required.itemId, 0);
			int now = after.getOrDefault(required.itemId, 0);
			if (had < required.quantity || now > had - required.quantity)
			{
				return false;
			}
		}
		return true;
	}

	/** Items whose quantity decreased between two snapshots (i.e. items given). */
	private List<OrderItem> itemsDecreased(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		List<OrderItem> result = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : before.entrySet())
		{
			int delta = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
			if (delta > 0)
			{
				result.add(new OrderItem(entry.getKey(), delta));
			}
		}
		return result;
	}

	/** Items whose quantity increased between two snapshots (i.e. items received). */
	private List<OrderItem> itemsIncreased(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		List<OrderItem> result = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : after.entrySet())
		{
			int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
			if (delta > 0)
			{
				result.add(new OrderItem(entry.getKey(), delta));
			}
		}
		return result;
	}

	/** Counts all non-empty item stacks in a container into a map of itemId → quantity. */
	private static Map<Integer, Integer> countItems(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() != -1)
			{
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return Collections.unmodifiableMap(counts);
	}

	// ── Shortest Path integration ──────────────────────────────────────────────

	private void checkShortestPathInstalled()
	{
		boolean installed = pluginManager.getPlugins().stream()
			.anyMatch(p -> p.getClass().getSimpleName().equals("ShortestPathPlugin"));

		if (!installed && !config.shortestPathWarningDismissed())
		{
			SwingUtilities.invokeLater(() -> panel.showShortestPathWarning());
		}
	}

	private void setShortestPathDestination(WorldPoint destination)
	{
		configManager.setConfiguration("shortestpath", "target",
			destination.getX() + "," + destination.getY() + "," + destination.getPlane());
	}

	// ── Panel callbacks ────────────────────────────────────────────────────────

	public void onCancelOrder(String orderId)
	{
		// Cancel/accept actions are web-only; open the web app
		LinkBrowser.browse(WEB_APP_URL);
	}

	public void onCancelAcceptance(String orderId)
	{
		LinkBrowser.browse(WEB_APP_URL);
	}

	public void onDismissShortestPathWarning()
	{
		configManager.setConfiguration(CONFIG_GROUP, "shortestPathWarningDismissed", true);
	}

	public void onLinkAccount(String linkCode)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			SwingUtilities.invokeLater(() -> panel.showLinkError("You must be logged in to link your account."));
			return;
		}
		String rsn = local.getName();
		apiClient.linkAccount(rsn, linkCode, success ->
		{
			if (success)
			{
				panel.showLinkSuccess(rsn);
			}
			else
			{
				panel.showLinkError("Invalid or expired code. Try again.");
			}
		});
	}

	// ── Config helpers ─────────────────────────────────────────────────────────

	private void ensurePluginToken()
	{
		String token = config.pluginToken();
		if (token == null || token.isEmpty())
		{
			token = UUID.randomUUID().toString();
			configManager.setConfiguration(CONFIG_GROUP, "pluginToken", token);
		}
	}
}
