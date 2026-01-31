package com.example.backend.service;

import com.example.backend.entity.Information;
import com.example.backend.entity.UserProcess;

import java.util.List;
import java.util.Map;

public interface InfoService {


    //查询所有信息
    List<Information> selectAllInfo(Map<String, String> request);



    //按需求(服务器IP,组件名称，风险等级，发生时间)查询信息
    List <Information> selectInfo(Map<String,String> request);

    //存储用户处理记录
   String insertProcess(Map<String,String> request);

    //查寻所有处理记录
    List<UserProcess> selectAllProcess(Map<String, String> request);

    //按需求查询记录
    List<UserProcess> selectProcess(Map<String, String> request);

}
