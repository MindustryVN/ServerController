package mindustrytool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Config {

        public static Boolean isLoaded = false;
        public static final String PLUGIN_VERSION = "0.0.1";

        public static final String HUB = System.getenv("IS_HUB");
        public static final boolean IS_HUB = HUB != null && HUB.equals("true");

        public static final String ENV = System.getenv("ENV");

        public static final boolean IS_DEVELOPMENT = ENV != null && ENV.equals("DEV");

        public static final ExecutorService BACKGROUND_TASK_EXECUTOR = new ThreadPoolExecutor(
                        0,
                        20,
                        5,
                        TimeUnit.SECONDS,
                        new SynchronousQueue<Runnable>());

        public static final ScheduledExecutorService BACKGROUND_SCHEDULER = Executors
                        .newSingleThreadScheduledExecutor();

        public static final String SERVER_IP = "103.20.96.24";
        public static final String DISCORD_INVITE_URL = "https://discord.com/invite/DCX5yrRUyp";
        public static final String MINDUSTRY_TOOL_URL = "https://mindustry-tool.com";
        public static final String RULE_URL = MINDUSTRY_TOOL_URL + "/rules";

        public static final int MAX_IDENTICAL_IPS = 3;
        public static final String HUB_MESSAGE = """
                        Command
                        [yellow]/servers[white] to show server list
                        [yellow]/rtv[white] to vote for changing map
                        [yellow]/maps[white] to see map list
                        [yellow]/hub[white] show this
                        [green]Log in to get more feature
                        """;

        public static final String CHOOSE_SERVER_MESSAGE = """
                        [accent]Click[] [orange]any server data[] to [lime]play[]
                        [accent]Click[] to [scarlet]offline server[] to [lime]starting & play[] this.
                        """;
}
