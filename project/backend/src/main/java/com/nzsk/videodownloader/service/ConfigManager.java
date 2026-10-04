package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.exception.ConfigurationException;
import com.nzsk.videodownloader.model.AppConfig;

public interface ConfigManager {
    AppConfig load() throws ConfigurationException;

    void save(AppConfig config) throws ConfigurationException;
}
