# Gielinor Eats — RuneLite Plugin

An in-game delivery service plugin for Old School RuneScape. Browse orders and delivery requests on the [Gielinor Eats web app](https://gielinoreats.com), then use this plugin to sync your in-game presence, monitor your inventory, and detect trade completion.

## Features

- **Presence sync** — shares your world and location with the backend every 60 seconds so runners can navigate to you
- **Inventory check** — automatically notifies the backend when a runner has all required items ready
- **Trade detection** — reports trade completion to the backend for order fulfillment
- **Panel** — sidebar panel showing active order status, lead runner, and a notification feed
- **Account linking** — links your in-game RSN to your web account with a one-time code

## Requirements

- [Shortest Path plugin](https://runelite.net/plugin-hub/show/shortest-path) — strongly recommended for accurate proximity detection

## Setup

1. Install this plugin from the RuneLite Plugin Hub
2. Visit [gielinoreats.com](https://gielinoreats.com) and create an account
3. Click "Link Plugin" to get a 6-character code
4. Paste the code into the plugin panel and click **Link Account**

A plugin token is auto-generated on first launch and stored in your RuneLite config. Do not share it.

## Development

```bash
./gradlew compileJava     # compile
./gradlew runClient       # run RuneLite with the plugin loaded
```

Requires Java 11+.
