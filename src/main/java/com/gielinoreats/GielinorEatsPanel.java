package com.gielinoreats;

import com.gielinoreats.model.ActiveOrder;
import com.gielinoreats.model.DeliveryStatus;
import com.gielinoreats.model.NotificationEntry;
import com.gielinoreats.model.PresenceSyncResponse;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

public class GielinorEatsPanel extends PluginPanel
{
	private static final String WEB_APP_URL = "https://gielinoreats.com";
	private static final String SHORTEST_PATH_URL = "https://runelite.net/plugin-hub/show/shortest-path";

	private static final Color COLOR_GREEN = new Color(0, 200, 83);
	private static final Color COLOR_YELLOW = new Color(255, 210, 0);
	private static final Color COLOR_RED = new Color(255, 80, 80);

	// Top section widgets
	private final JLabel statusLabel = new JLabel("No active order");
	private final JLabel secondaryLabel = new JLabel("");
	private final JButton primaryActionButton = new JButton();
	private final JButton openWebButton = new JButton("Open Gielinor Eats \u2192");
	private final JPanel shortestPathWarning;
	private final JPanel linkAccountPanel;
	private final JTextField linkCodeField = new JTextField(8);
	private final JButton linkAccountButton = new JButton("Link Account");
	private final JLabel linkStatusLabel = new JLabel("");

	// Notification feed
	private final JPanel notificationList = new JPanel();
	private final JScrollPane notificationScroll;

	private final GielinorEatsPlugin plugin;
	private boolean showingActionButton = false;

	public GielinorEatsPanel(GielinorEatsPlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// ── Top section ────────────────────────────────────────────────────────
		JPanel topSection = new JPanel();
		topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
		topSection.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Status label
		statusLabel.setForeground(Color.WHITE);
		statusLabel.setFont(FontManager.getRunescapeBoldFont());
		statusLabel.setAlignmentX(LEFT_ALIGNMENT);
		topSection.add(statusLabel);

		// Secondary info (lead runner / target RSN etc.)
		secondaryLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		secondaryLabel.setFont(FontManager.getRunescapeSmallFont());
		secondaryLabel.setAlignmentX(LEFT_ALIGNMENT);
		topSection.add(secondaryLabel);

		topSection.add(buildSpacer(4));

		// Primary action button (Cancel Order / Cancel Acceptance)
		primaryActionButton.setVisible(false);
		primaryActionButton.setAlignmentX(LEFT_ALIGNMENT);
		topSection.add(primaryActionButton);

		// Open web button
		openWebButton.setAlignmentX(LEFT_ALIGNMENT);
		openWebButton.addActionListener(e -> LinkBrowser.browse(WEB_APP_URL));
		topSection.add(buildSpacer(4));
		topSection.add(openWebButton);

		// ── Shortest Path warning ──────────────────────────────────────────────
		shortestPathWarning = buildShortestPathWarning();
		shortestPathWarning.setVisible(false);
		topSection.add(buildSpacer(8));
		topSection.add(shortestPathWarning);

		// ── Account linking ────────────────────────────────────────────────────
		linkAccountPanel = buildLinkAccountPanel();
		topSection.add(buildSpacer(8));
		topSection.add(linkAccountPanel);

		add(topSection, BorderLayout.NORTH);

		// ── Notification feed ──────────────────────────────────────────────────
		notificationList.setLayout(new BoxLayout(notificationList, BoxLayout.Y_AXIS));
		notificationList.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		notificationScroll = new JScrollPane(notificationList);
		notificationScroll.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			"Notifications",
			0,
			0,
			FontManager.getRunescapeSmallFont(),
			ColorScheme.LIGHT_GRAY_COLOR));
		notificationScroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		notificationScroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		notificationScroll.setPreferredSize(new Dimension(0, 200));

		JPanel bottomSection = new JPanel(new BorderLayout());
		bottomSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bottomSection.setBorder(new EmptyBorder(8, 0, 0, 0));

		JButton clearButton = new JButton("Clear");
		clearButton.addActionListener(e -> clearNotifications());
		JPanel clearRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
		clearRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		clearRow.add(clearButton);

		bottomSection.add(notificationScroll, BorderLayout.CENTER);
		bottomSection.add(clearRow, BorderLayout.SOUTH);

		add(bottomSection, BorderLayout.CENTER);
	}

	// ── Public API ─────────────────────────────────────────────────────────────

	public void updateStatus(PresenceSyncResponse response)
	{
		DeliveryStatus status = DeliveryStatus.fromString(response.orderStatus);
		ActiveOrder order = response.activeOrder;

		if (status == null || order == null)
		{
			showIdleState();
			return;
		}

		if (order.isRequester())
		{
			showRequesterState(status, order);
		}
		else if (order.isRunner())
		{
			showRunnerState(status, order);
		}
	}

	public void addNotification(NotificationEntry n)
	{
		JPanel entry = new JPanel(new GridBagLayout());
		entry.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		entry.setBorder(new EmptyBorder(4, 6, 4, 6));
		entry.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(0, 0, 0, 6);

		// Colored dot
		JLabel dot = new JLabel("\u25CF");
		dot.setForeground(dotColorForType(n.type));
		dot.setFont(FontManager.getRunescapeSmallFont());
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridheight = 2;
		entry.add(dot, gbc);

		// Timestamp
		JLabel ts = new JLabel(formatTimestamp(n.timestamp));
		ts.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		ts.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.PLAIN, 9f));
		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		entry.add(ts, gbc);

		// Message — escape HTML to prevent injection via server-supplied notification text
		JLabel msg = new JLabel("<html>" + escapeHtml(n.message) + "</html>");
		msg.setForeground(Color.WHITE);
		msg.setFont(FontManager.getRunescapeSmallFont());
		gbc.gridy = 1;
		entry.add(msg, gbc);

		// Insert newest at top
		notificationList.add(entry, 0);
		notificationList.add(buildDivider(), 1);
		notificationList.revalidate();
		notificationList.repaint();
	}

	public void showShortestPathWarning()
	{
		shortestPathWarning.setVisible(true);
		revalidate();
	}

	public void hideShortestPathWarning()
	{
		shortestPathWarning.setVisible(false);
		revalidate();
	}

	public void showLinkSuccess(String rsn)
	{
		linkCodeField.setEnabled(false);
		linkAccountButton.setEnabled(false);
		linkStatusLabel.setText("Linked as " + rsn);
		linkStatusLabel.setForeground(COLOR_GREEN);
		revalidate();
	}

	public void showLinkError(String message)
	{
		linkStatusLabel.setText(message);
		linkStatusLabel.setForeground(COLOR_RED);
		revalidate();
	}

	// ── Private helpers ────────────────────────────────────────────────────────

	public void showIdleState()
	{
		statusLabel.setText("No active order");
		secondaryLabel.setText("");
		primaryActionButton.setVisible(false);
		openWebButton.setVisible(true);
	}

	private void showRequesterState(DeliveryStatus status, ActiveOrder order)
	{
		statusLabel.setText(statusLabel(status));
		openWebButton.setVisible(false);

		if (order.leadRunnerRsn != null && !order.leadRunnerRsn.isEmpty())
		{
			secondaryLabel.setText("Runner: " + order.leadRunnerRsn);
		}
		else
		{
			secondaryLabel.setText("Awaiting runners...");
		}

		if (!status.isTerminal())
		{
			primaryActionButton.setText("Cancel Order");
			primaryActionButton.setVisible(true);
			primaryActionButton.addActionListener(e -> plugin.onCancelOrder(order.orderId));
		}
		else
		{
			primaryActionButton.setVisible(false);
			openWebButton.setVisible(true);
		}
	}

	private void showRunnerState(DeliveryStatus status, ActiveOrder order)
	{
		statusLabel.setText(statusLabel(status));
		openWebButton.setVisible(false);

		String target = order.requesterRsn != null ? order.requesterRsn : "Unknown";
		secondaryLabel.setText("Delivering to: " + target + " (W" + order.requesterWorld + ")");

		if (!status.isTerminal())
		{
			primaryActionButton.setText("Cancel Acceptance");
			primaryActionButton.setVisible(true);
			primaryActionButton.addActionListener(e -> plugin.onCancelAcceptance(order.orderId));
		}
		else
		{
			primaryActionButton.setVisible(false);
			openWebButton.setVisible(true);
		}
	}

	private String statusLabel(DeliveryStatus status)
	{
		switch (status)
		{
			case AWAITING_RUNNERS:
				return "Awaiting runners";
			case PREPARING:
				return "Runner preparing";
			case RUNNER_EN_ROUTE:
				return "Runner en route";
			case RUNNER_NEAR:
				return "Runner is nearby!";
			case PENDING_TRADE:
				return "Pending trade";
			case COMPLETED:
				return "Order completed";
			case DISPUTED:
				return "Order disputed";
			case CANCELLED:
				return "Order cancelled";
			default:
				return status.getValue();
		}
	}

	private Color dotColorForType(String type)
	{
		if (type == null)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		switch (type)
		{
			case "order_completed":
			case "runner_accepted":
			case "runner_en_route":
				return COLOR_GREEN;
			case "runner_near":
			case "competing_runner_completed":
				return COLOR_YELLOW;
			case "order_cancelled":
			case "order_disputed":
				return COLOR_RED;
			default:
				return ColorScheme.LIGHT_GRAY_COLOR;
		}
	}

	private static final DateTimeFormatter TIME_FMT =
		DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

	private static String formatTimestamp(String isoTimestamp)
	{
		if (isoTimestamp == null)
		{
			return "";
		}
		try
		{
			return TIME_FMT.format(Instant.parse(isoTimestamp));
		}
		catch (Exception e)
		{
			return "";
		}
	}

	/** Escapes characters that have special meaning in Swing HTML rendering. */
	private static String escapeHtml(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;");
	}

	private JPanel buildShortestPathWarning()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(new Color(60, 50, 20));
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(COLOR_YELLOW),
			new EmptyBorder(6, 8, 6, 8)));

		JLabel warning = new JLabel("<html><b>Shortest Path plugin not detected.</b><br>"
			+ "Install it for accurate proximity detection.</html>");
		warning.setForeground(COLOR_YELLOW);
		warning.setFont(FontManager.getRunescapeSmallFont());
		warning.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(warning);
		panel.add(buildSpacer(4));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setBackground(new Color(60, 50, 20));

		JButton getPlugin = new JButton("Get Shortest Path");
		getPlugin.addActionListener(e -> LinkBrowser.browse(SHORTEST_PATH_URL));
		buttons.add(getPlugin);

		JButton dismiss = new JButton("Dismiss");
		dismiss.addActionListener(e ->
		{
			plugin.onDismissShortestPathWarning();
			hideShortestPathWarning();
		});
		buttons.add(dismiss);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(buttons);

		return panel;
	}

	private JPanel buildLinkAccountPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			"Link Web Account",
			0,
			0,
			FontManager.getRunescapeSmallFont(),
			ColorScheme.LIGHT_GRAY_COLOR));

		JLabel instructions = new JLabel("<html>Paste the 6-character code from the Gielinor Eats website.</html>");
		instructions.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		instructions.setFont(FontManager.getRunescapeSmallFont());
		instructions.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(instructions);
		panel.add(buildSpacer(4));

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		linkCodeField.setColumns(8);
		linkCodeField.setDocument(new UpperCaseDocument(6));
		row.add(linkCodeField);
		linkAccountButton.addActionListener(e -> onLinkAccountClicked());
		row.add(linkAccountButton);
		row.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(row);

		linkStatusLabel.setFont(FontManager.getRunescapeSmallFont());
		linkStatusLabel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(linkStatusLabel);

		return panel;
	}

	private static final java.util.regex.Pattern LINK_CODE_PATTERN =
		java.util.regex.Pattern.compile("^[A-Z0-9]{6}$");

	private void onLinkAccountClicked()
	{
		String code = linkCodeField.getText().trim();
		if (!LINK_CODE_PATTERN.matcher(code).matches())
		{
			showLinkError("Code must be 6 alphanumeric characters.");
			return;
		}
		linkStatusLabel.setText("Linking...");
		linkStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		linkAccountButton.setEnabled(false);
		plugin.onLinkAccount(code);
	}

	public void clearNotifications()
	{
		notificationList.removeAll();
		notificationList.revalidate();
		notificationList.repaint();
	}

	private JPanel buildDivider()
	{
		JPanel divider = new JPanel();
		divider.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		divider.setPreferredSize(new Dimension(0, 1));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return divider;
	}

	private JPanel buildSpacer(int height)
	{
		JPanel spacer = new JPanel();
		spacer.setOpaque(false);
		spacer.setPreferredSize(new Dimension(0, height));
		spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return spacer;
	}

	// ── Inner class: upper-case limited-length document ───────────────────────

	private static class UpperCaseDocument extends javax.swing.text.PlainDocument
	{
		private final int maxLen;

		UpperCaseDocument(int maxLen)
		{
			this.maxLen = maxLen;
		}

		@Override
		public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
			throws javax.swing.text.BadLocationException
		{
			if (str == null)
			{
				return;
			}
			str = str.toUpperCase();
			if ((getLength() + str.length()) > maxLen)
			{
				str = str.substring(0, maxLen - getLength());
			}
			super.insertString(offs, str, a);
		}
	}
}
