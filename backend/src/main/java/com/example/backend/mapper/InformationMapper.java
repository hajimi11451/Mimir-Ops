package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.Information;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InformationMapper extends BaseMapper<Information> {
    
}
