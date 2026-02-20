package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.Information;
import com.example.backend.entity.UserProcess;
import com.example.backend.mapper.InformationMapper;
import com.example.backend.mapper.UserProcessMapper;
import com.example.backend.service.InfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Service
public class InfoServiceImpl implements InfoService {

    @Autowired
    private InformationMapper informationMapper;

    @Autowired
    private UserProcessMapper userProcessMapper;

    //查询所有信息
    @Override
    public List<Information> selectAllInfo(Map<String, String> request){
        String userId = request.get("userId");
        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        if (userId != null && !userId.isEmpty()) {
            queryWrapper.eq("userId", userId);
        }
        // 如果没有 userId，查询所有（或者查询系统日志，视需求而定，这里暂定查询所有）
        List<Information> response = informationMapper.selectList(queryWrapper);
        if (response.isEmpty()){
            return null;
        }else {
            return response;
        }
    }

    //按需求(服务器IP,组件名称，风险等级，发生时间)查询信息
    @Override
    public List <Information> selectInfo(Map<String,String> request){
        //用户ID
        String userId = request.get("userId");
        //服务器IP
        String serverIp = request.get("serverIp");
        //组件名称
        String component = request.get("component");
        //问题摘要
        String errorSummary = request.get("errorSummary");
        //风险等级
        String riskLevel = request.get("riskLevel");
        //发生时间段
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        try {
            startTime = LocalDateTime.parse(request.get("startTime"));
            endTime = LocalDateTime.parse(request.get("endTime"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        if (userId != null && !userId.isEmpty()) {
            queryWrapper.eq("userId", userId);
        }
        queryWrapper.eq("serverIp",serverIp);
        queryWrapper.eq("component",component);
        queryWrapper.eq("errorSummary",errorSummary);
        queryWrapper.eq("riskLevel",riskLevel);
        queryWrapper.between("createdAt",startTime,endTime);
        List<Information> response = informationMapper.selectList(queryWrapper);
        if (response.isEmpty()){
            return null;
        }else {
            return response;
        }
    }

    //存储用户处理记录
    @Override
    public String insertProcess(Map<String,String> request){

        String userId=request.get("userId");
        String serverIp = request.get("serverIp");
        String component =request.get("component");
        String processMethod = request.get("processMethod");
        LocalDateTime processTime = null;
        try {
            processTime = LocalDateTime.parse(request.get("endTime"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (userId.isEmpty()||serverIp.isEmpty()||component.isEmpty()
                ||processMethod.isEmpty()||processTime==null){
            return "输入某数值为空";
        }
        UserProcess userProcess = new UserProcess();
        userProcess.setUserId(Long.valueOf(userId));
        userProcess.setServerIp(serverIp);
        userProcess.setComponent(component);
        userProcess.setProcessMethod(processMethod);
        userProcess.setProcessTime(processTime);
        int res = userProcessMapper.insert(userProcess);
        if (res==1){
            return "录入成功";
        }else {
            return "录入失败";
        }

    }

    //查寻所有处理记录
    @Override
    public List<UserProcess> selectAllProcess(Map<String, String> request){
        String userId = request.get("userId");
        QueryWrapper<UserProcess> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId",userId);
        List<UserProcess> response = userProcessMapper.selectList(queryWrapper);
        if (response.isEmpty()){
            return null;
        }else {
            return response;
        }

    }

    //按需求查询记录
    @Override
    public List<UserProcess> selectProcess(Map<String, String> request){
        //用户Id
        String userId =request.get("uerId");
        //服务器IP
        String serverIp = request.get("serverIp");
        //组件名称
        String component = request.get("component");
        //处理时间
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        try {
            startTime = LocalDateTime.parse(request.get("startTime"));
            endTime = LocalDateTime.parse(request.get("endTime"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        QueryWrapper<UserProcess> queryWrapper =new QueryWrapper<>();
        queryWrapper.eq("userId",userId);
        queryWrapper.eq("serverIp",serverIp);
        queryWrapper.eq("component",component);
        queryWrapper.between("processTime",startTime,endTime);
        List<UserProcess> response = userProcessMapper.selectList(queryWrapper);
        if (response.isEmpty()){
            return null;
        }else {
            return response;
        }
    }

}
