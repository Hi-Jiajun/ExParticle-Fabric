package net.hackermdch.exparticle;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ExParticleConfig {
    public static ConfigData config = new ConfigData();

    public static void init() throws IOException {
        var gson = new Gson();
        // 上游写 `./exparticle.json`（相对进程 CWD）。标准启动器 CWD == gameDir，
        // 但 PCL 的 CWD 是 .minecraft 根（在该机器上还不可写），所以这里显式用 Fabric 的 gameDir，
        // 语义与上游在标准启动器上一致，又不受启动器怪癖影响。
        var file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("exparticle.json");
        if (!Files.exists(file)) {
            try (var writer = Files.newBufferedWriter(file)) {
                gson.toJson(config, writer);
            }
        }
        try (var reader = Files.newBufferedReader(file)) {
            config = gson.fromJson(reader, ConfigData.class);
        }
    }

    public static class ConfigData {
        public int maxParticleCount = 65536;
        public int maxParticleTickMillis = 1000;
        public boolean ParallelParticleUpdate = false;
    }
}
