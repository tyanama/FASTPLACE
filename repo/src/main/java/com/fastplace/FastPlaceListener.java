package com.fastplace;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class FastPlaceListener implements Listener {

    private final FastPlacePlugin plugin;

    // プレイヤーUUID -> 現在のセッション
    private final Map<UUID, PlaceSession> activeSessions = new HashMap<>();

    // /fastplace toggle でOFFにしたプレイヤー
    private final Set<UUID> disabledPlayers = new HashSet<>();

    // 設定値キャッシュ
    private int maxBlocks;
    private boolean consumeBlocks;
    private boolean survivalOnly;
    private boolean debug;

    public FastPlaceListener(FastPlacePlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        maxBlocks = plugin.getConfig().getInt("max-blocks-per-drag", 64);
        consumeBlocks = plugin.getConfig().getBoolean("consume-blocks", true);
        survivalOnly = plugin.getConfig().getBoolean("survival-only", false);
        debug = plugin.getConfig().getBoolean("debug", false);
    }

    // ---- スニーク中に右クリックでセッション開始 ----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!isEligible(player)) return;
        if (!player.isSneaking()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        ItemStack item = event.getItem();
        if (item == null || !item.getType().isBlock()) return;

        Material mat = item.getType();
        BlockFace face = event.getBlockFace();
        Block target = clicked.getRelative(face);

        // 空気 or 置き換え可能なブロックでなければスキップ
        if (!isReplaceable(target)) return;

        // セッション開始
        PlaceSession session = new PlaceSession(mat, face);
        placeBlock(player, target, mat, session);

        if (session.getCount() > 0) {
            activeSessions.put(player.getUniqueId(), session);
            debugLog(player.getName() + " がセッション開始: " + mat);
        }
    }

    // ---- マウスムーブ（ブロックターゲット変化）でドラッグ設置 ----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!activeSessions.containsKey(uuid)) return;
        if (!player.isSneaking()) {
            endSession(uuid);
            return;
        }

        PlaceSession session = activeSessions.get(uuid);
        if (!session.isActive()) return;
        if (session.getCount() >= maxBlocks) {
            endSession(uuid);
            return;
        }

        // プレイヤーが見ているブロックを取得
        Block target = player.getTargetBlockExact(5, FluidCollisionMode.NEVER);
        if (target == null) return;

        // ターゲットブロックが置き換え可能でない場合、その隣接ブロックを確認
        Block placeAt;
        if (isReplaceable(target)) {
            placeAt = target;
        } else {
            // 最後に使ったフェイスの方向に設置を試みる
            placeAt = target.getRelative(session.getLastFace());
            if (!isReplaceable(placeAt)) return;
        }

        placeBlock(player, placeAt, session.getMaterial(), session);
    }

    // ---- スニーク解除でセッション終了 ----
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            endSession(event.getPlayer().getUniqueId());
        }
    }

    // ---- アイテム持ち替えでセッション終了 ----
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        endSession(event.getPlayer().getUniqueId());
    }

    // ---- 離席・ログアウトでセッション終了 ----
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endSession(event.getPlayer().getUniqueId());
        disabledPlayers.remove(event.getPlayer().getUniqueId());
    }

    // ---- ブロック設置コアロジック ----
    private void placeBlock(Player player, Block target, Material material, PlaceSession session) {
        Location loc = target.getLocation();

        // 既に設置済みならスキップ
        if (session.hasPlaced(loc)) return;

        // インベントリ消費チェック
        if (consumeBlocks && player.getGameMode() == GameMode.SURVIVAL) {
            if (!consumeItem(player, material)) {
                player.sendMessage("§c[FastPlace] ブロックが足りません！");
                session.setActive(false);
                return;
            }
        }

        // ブロックを設置
        target.setType(material);
        session.addPlaced(loc);

        // 設置エフェクト（音）
        target.getWorld().playSound(loc, Sound.BLOCK_STONE_PLACE, 0.5f, 1.0f);

        debugLog("設置: " + material + " at " + loc.toVector());
    }

    // ---- インベントリからアイテムを1個消費 ----
    private boolean consumeItem(Player player, Material material) {
        PlayerInventory inv = player.getInventory();
        ItemStack held = inv.getItemInMainHand();

        // メインハンドのアイテムを優先消費
        if (held.getType() == material && held.getAmount() > 0) {
            if (held.getAmount() == 1) {
                inv.setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                held.setAmount(held.getAmount() - 1);
            }
            return true;
        }

        // インベントリ全体から探す
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() == material) {
                if (stack.getAmount() == 1) {
                    inv.setItem(i, new ItemStack(Material.AIR));
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                }
                return true;
            }
        }
        return false;
    }

    // ---- 置き換え可能ブロック判定 ----
    private boolean isReplaceable(Block block) {
        Material type = block.getType();
        return type == Material.AIR
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR
                || type == Material.WATER
                || type == Material.LAVA
                || type == Material.GRASS
                || type == Material.TALL_GRASS
                || type == Material.SNOW
                || type == Material.VINE;
    }

    // ---- プレイヤーがこのプラグインを使える状態か判定 ----
    private boolean isEligible(Player player) {
        if (!player.hasPermission("fastplace.use")) return false;
        if (disabledPlayers.contains(player.getUniqueId())) return false;
        if (survivalOnly && player.getGameMode() == GameMode.CREATIVE) return false;
        return true;
    }

    // ---- セッション終了 ----
    private void endSession(UUID uuid) {
        PlaceSession session = activeSessions.remove(uuid);
        if (session != null && session.getCount() > 0) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                debugLog(player.getName() + " のセッション終了: " + session.getCount() + " ブロック設置");
            }
        }
    }

    // ---- トグル ----
    public boolean togglePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            return true; // ON
        } else {
            disabledPlayers.add(uuid);
            endSession(uuid);
            return false; // OFF
        }
    }

    // ---- 全セッションクリア（onDisable用） ----
    public void clearAllSessions() {
        activeSessions.clear();
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[Debug] " + msg);
    }
}
