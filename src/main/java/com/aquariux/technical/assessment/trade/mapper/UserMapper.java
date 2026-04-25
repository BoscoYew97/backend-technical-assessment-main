package com.aquariux.technical.assessment.trade.mapper;

import com.aquariux.technical.assessment.trade.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("""
            SELECT id, username, email 
            FROM users where id=#{userId}
            """)
    User findByUserId(Long userId);
}
