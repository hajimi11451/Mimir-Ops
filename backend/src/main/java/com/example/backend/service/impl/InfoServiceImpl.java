package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.Information;
import com.example.backend.mapper.InformationMapper;
import com.example.backend.service.InfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
@Service
public class InfoServiceImpl implements InfoService {


    @Autowired
    private InformationMapper informationMapper;

    //查询信息
    @Override
    public List<Information> selectInfo(Map<String, String> request){
        String userId = request.get("userId");
        QueryWrapper queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId",userId);
        List<Information> response = informationMapper.selectList(queryWrapper);
        if (response.isEmpty()){
            return null;
        }else {
            return response;
        }
    }
}
