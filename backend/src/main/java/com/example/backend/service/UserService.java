package com.example.backend.service;

import java.util.Map;

public interface UserService {

    String login(Map<String,String> requerst);

    String register(Map<String, String> request);




}
