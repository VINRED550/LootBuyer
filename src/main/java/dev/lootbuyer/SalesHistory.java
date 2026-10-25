package dev.lootbuyer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Sales log for admins, stored in history.db (SQLite, bundled with every Spigot/Paper server).
 * All database work runs on one background thread so selling never waits on disk.
 */
public final class SalesHistory {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long CLEANUP_INTERVAL = 60L * 60 * 1000;

    /** One row of /buyer history. */
    public static final class Entry {
        public final long time;
        public final String item;
        public final int amount;
        public final double money;

        Entry(long time, String item, int amount, double money) {
            this.time = time;
            this.item = item;
            this.amount = amount;
            this.money = money;
        }
    }

    /** One page of results plus totals for the whole period. */
    public static final class Page {
        public final String player;
        public final List<Entry> entries;
        public final int page;
        public final int pages;
        public final long totalAmount;
        public final double totalMoney;

        Page(String player, List<Entry> entries, int page, int pages, long totalAmount, double totalMoney) {
            this.player = player;
            this.entries = entries;
            this.page = page;
            this.pages = pages;
            this.totalAmount = totalAmount;
            this.totalMoney = totalMoney;
        }
    }

    private final LootBuyerPlugin plugin;
    private final ExecutorService db = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LootBuyer-History");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;
    private volatile boolean available;
    private long lastCleanup;

    public SalesHistory(LootBuyerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        File file = new File(plugin.getDataFolder(), "history.db");
        db.execute(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = open(file);
                try (Statement st = connection.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS sales ("
                            + "time INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT NOT NULL, name_lower TEXT NOT NULL, "
                            + "item TEXT NOT NULL, amount INTEGER NOT NULL, money REAL NOT NULL)");
                    st.executeUpdate("CREATE INDEX IF NOT EXISTS sales_player_time ON sales(name_lower, time)");
                }
                available = true;
                deleteOld();
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("SQLite driver not found on this server, sales history is disabled");
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not open history.db, sales history is disabled: " + e.getMessage());
                if (file.getAbsolutePath().length() > 250) {
                    // SQLite's native code is limited to 260-character paths on Windows
                    plugin.getLogger().warning("The server folder path is very long (" + file.getAbsolutePath().length()
                            + " characters). Move the server to a shorter path, e.g. C:\\server");
                }
            }
        });
    }

    private static Connection open(File file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    public boolean available() {
        return available;
    }

    public void record(Player player, BuyerItem item, int amount, double money) {
        if (!plugin.settings().historyEnabled || !available) return;
        long time = System.currentTimeMillis();
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        String material = item.material().name();
        db.execute(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sales (time, uuid, name, name_lower, item, amount, money) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, time);
                ps.setString(2, uuid);
                ps.setString(3, name);
                ps.setString(4, name.toLowerCase(Locale.ROOT));
                ps.setString(5, material);
                ps.setInt(6, amount);
                ps.setDouble(7, money);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not write sales history: " + e.getMessage());
            }
        });
    }

    /** Called every second from the plugin's ticker; actually cleans up once an hour. */
    public void cleanupIfDue() {
        long now = System.currentTimeMillis();
        if (!available || now - lastCleanup < CLEANUP_INTERVAL) return;
        lastCleanup = now;
        db.execute(this::deleteOld);
    }

    private void deleteOld() {
        long cutoff = System.currentTimeMillis() - plugin.settings().historyKeepDays * DAY;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sales WHERE time < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not clean up sales history: " + e.getMessage());
        }
    }

    /** Looks up a page off the main thread and hands the result back on the main thread. */
    public void query(String playerName, int page, Consumer<Page> callback) {
        int pageSize = plugin.settings().historyPageSize;
        long since = System.currentTimeMillis() - plugin.settings().historyKeepDays * DAY;
        String key = playerName.toLowerCase(Locale.ROOT);
        db.execute(() -> {
            Page result = null;
            try {
                long rows = 0, amount = 0;
                double money = 0;
                String name = playerName;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*), COALESCE(SUM(amount), 0), COALESCE(SUM(money), 0), MAX(name) FROM sales WHERE name_lower = ? AND time >= ?")) {
                    ps.setString(1, key);
                    ps.setLong(2, since);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            rows = rs.getLong(1);
                            amount = rs.getLong(2);
                            money = rs.getDouble(3);
                            if (rs.getString(4) != null) name = rs.getString(4);
                        }
                    }
                }
                int pages = (int) Math.max(1, (rows + pageSize - 1) / pageSize);
                int current = Math.max(1, Math.min(page, pages));
                List<Entry> entries = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT time, item, amount, money FROM sales WHERE name_lower = ? AND time >= ? ORDER BY time DESC LIMIT ? OFFSET ?")) {
                    ps.setString(1, key);
                    ps.setLong(2, since);
                    ps.setInt(3, pageSize);
                    ps.setInt(4, (current - 1) * pageSize);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) entries.add(new Entry(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getDouble(4)));
                    }
                }
                result = new Page(name, entries, current, pages, amount, money);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not read sales history: " + e.getMessage());
            }
            Page done = result;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> callback.accept(done));
        });
    }

    public void shutdown() {
        db.execute(() -> {
            try {
                if (connection != null) connection.close();
            } catch (SQLException ignored) {
                // closing anyway
            }
        });
        db.shutdown();
        try {
            // Let pending sales get written before the server stops
            db.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
