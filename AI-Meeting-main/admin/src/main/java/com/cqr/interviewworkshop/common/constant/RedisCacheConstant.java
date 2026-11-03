package com.cqr.interviewworkshop.common.constant;

/**
 * 短链接后管 Redis 缓存常量类
 
 */
public class RedisCacheConstant {

    /**
     * 用户注册分布式锁
     */
    public static final String LOCK_USER_REGISTER_KEY = "interview-workshop-agent:lock_user-register:";

    /**
     * 分组创建分布式锁
     */
    public static final String LOCK_GROUP_CREATE_KEY = "interview-workshop-agent:lock_group-create:%s";

    /**
     * 用户登录缓存标识
     */
    public static final String USER_LOGIN_KEY = "interview-workshop-agent:login:";

    // 移除会话缓存相关常量，不再使用Redis延时队列

    // 移除会话最后同步时间Key，不再使用
}
