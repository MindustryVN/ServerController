package mindustrytool.handler;

import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustrytool.ServerController;
import mindustrytool.type.HudOption;
import mindustrytool.type.MenuData;
import mindustrytool.type.PlayerPressCallback;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import mindustry.game.EventType.MenuOptionChooseEvent;
import mindustry.game.EventType.PlayerLeave;

public class HudHandler {

    public static final int HUB_UI = 1;
    public static final int SERVERS_UI = 2;
    public static final int LOGIN_UI = 3;
    public static final int SERVER_REDIRECT = 4;

    private final WeakReference<ServerController> context;

    public HudHandler(WeakReference<ServerController> context) {
        this.context = context;
    }

    public Cache<String, LinkedList<MenuData>> menus = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100)
            .build();

    public void unload() {
        menus.invalidateAll();
        menus = null;
    }

    public void onPlayerLeave(PlayerLeave event) {
        var menu = menus.getIfPresent(event.player.uuid());
        if (menu != null) {
            for (var data : menu) {
                Call.hideFollowUpMenu(event.player.con, data.getId());
            }
        }
        menus.invalidate(event.player.uuid());
    }

    public static HudOption option(PlayerPressCallback callback, String text) {
        return new HudOption(callback, text);
    }

    public void showFollowDisplay(Player player, int id, String title, String description, Object state,
            List<HudOption> options) {
        showFollowDisplays(player, id, title, description, state,
                options.stream().map(option -> Arrays.asList(option)).collect(Collectors.toList()));
    }

    public synchronized void showFollowDisplays(Player player, int id, String title, String description,
            Object state,
            List<List<HudOption>> options) {

        String[][] optionTexts = new String[options.size()][];
        for (int i = 0; i < options.size(); i++) {
            var op = options.get(i);
            optionTexts[i] = op.stream()//
                    .map(data -> data.getText())//
                    .toArray(String[]::new);
        }

        var callbacks = options.stream()//
                .flatMap(option -> option.stream().map(l -> l.getCallback()))//
                .collect(Collectors.toList());

        var userMenu = menus.get(player.uuid(), k -> new LinkedList<>());

        userMenu.removeIf(m -> m.getId() == id);

        if (userMenu.isEmpty()) {
            Call.menu(player.con, id, title, description, optionTexts);
        }

        userMenu.addLast(new MenuData(id, title, description, optionTexts, callbacks, state));
    }

    public void onMenuOptionChoose(MenuOptionChooseEvent event) {
        var menu = menus.getIfPresent(event.player.uuid());

        if (menu == null || menu.isEmpty()) {
            return;
        }

        var data = menu.getFirst();

        var callbacks = data.getCallbacks();

        if (callbacks == null || event.option <= -1 || event.option >= callbacks.size()) {
            return;
        }

        var callback = callbacks.get(event.option);

        if (callback == null) {
            return;
        }

        context.get().BACKGROUND_TASK_EXECUTOR.execute(() -> {
            callback.accept(event.player, data.getState());
        });
    }

    public synchronized void closeFollowDisplay(Player player, int id) {
        Call.hideFollowUpMenu(player.con, id);

        var menu = menus.getIfPresent(player.uuid());

        if (menu == null) {
            return;
        }

        menu.removeIf(data -> data.getId() == id);

        if (menu.isEmpty()) {
            return;
        }

        var first = menu.getFirst();

        Call.menu(player.con, id, first.getTitle(), first.getDescription(), first.getOptionTexts());
    }
}
