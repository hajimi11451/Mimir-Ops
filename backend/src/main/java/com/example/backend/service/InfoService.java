package com.example.backend.service;

import com.example.backend.entity.Information;

import java.util.List;
import java.util.Map;

public interface InfoService {

    List<Information> selectInfo(Map<String, String> request);
}
