package com.nzsk.videodownloader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.nzsk.videodownloader.exception.ConfigurationException;
import com.nzsk.videodownloader.model.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class JsonConfigManager implements ConfigManager {
    private final Path configFile;
    private final ObjectMapper objectMapper;
    private final AppConfig defaultConfig;

    public JsonConfigManager(Path configFile, AppConfig defaultConfig) {
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        this.defaultConfig = Objects.requireNonNull(defaultConfig, "defaultConfig");
        SimpleModule pathModule = new SimpleModule();
        pathModule.addSerializer(Path.class, new ToStringSerializer());
        pathModule.addDeserializer(Path.class, new FromStringDeserializer<Path>(Path.class) {
            @Override
            protected Path _deserialize(String value, DeserializationContext context) {
                return Path.of(value);
            }
        });
        this.objectMapper = JsonMapper.builder()
                .addModule(pathModule)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    @Override
    public AppConfig load() throws ConfigurationException {
        if (!Files.exists(configFile)) {
            return defaultConfig;
        }
        try {
            return objectMapper.readValue(configFile.toFile(), AppConfig.class);
        } catch (IOException | RuntimeException exception) {
            throw new ConfigurationException("配置文件无法读取，请检查文件内容后重试。", exception);
        }
    }

    @Override
    public void save(AppConfig config) throws ConfigurationException {
        Objects.requireNonNull(config, "config");
        try {
            Path parent = configFile.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(configFile.toFile(), config);
        } catch (IOException | RuntimeException exception) {
            throw new ConfigurationException("配置文件无法保存，请检查应用数据目录权限。", exception);
        }
    }
}
