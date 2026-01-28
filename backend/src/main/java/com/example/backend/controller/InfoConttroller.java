package com.example.backend.controller;

import com.example.backend.entity.Information;
import com.example.backend.service.InfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/info")
public class InfoConttroller {

    @Autowired
    private InfoService infoService;

    //查询所有信息
    @PostMapping("/selectInfo")
    public ResponseEntity<?> selectInfo(@RequestBody Map<String,String> request){
        List<Information> information = infoService.selectInfo(request);
        if (information.isEmpty()){
            return ResponseEntity.status(401).body(information);
        }else {
            return ResponseEntity.ok(information);
        }
    }

    //

}
