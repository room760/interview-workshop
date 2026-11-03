package com.cqr.interviewworkshop.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqr.interviewworkshop.common.convention.exception.ClientException;
import com.cqr.interviewworkshop.common.convention.exception.ServiceException;
import com.cqr.interviewworkshop.common.enums.UserErrorCodeEnum;
import com.cqr.interviewworkshop.user.dao.entity.UserDO;
import com.cqr.interviewworkshop.user.dao.mapper.UserMapper;
import com.cqr.interviewworkshop.user.api.io.req.UserLoginReqDTO;
import com.cqr.interviewworkshop.user.api.io.req.UserPageReqDTO;
import com.cqr.interviewworkshop.user.api.io.req.UserRegisterReqDTO;
import com.cqr.interviewworkshop.user.api.io.req.UserUpdateReqDTO;
import com.cqr.interviewworkshop.user.api.io.resp.UserLoginRespDTO;
import com.cqr.interviewworkshop.user.api.io.resp.UserPageRespDTO;
import com.cqr.interviewworkshop.user.api.io.resp.UserRespDTO;
import com.cqr.interviewworkshop.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.cqr.interviewworkshop.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.cqr.interviewworkshop.common.constant.RedisCacheConstant.USER_LOGIN_KEY;
import static com.cqr.interviewworkshop.common.enums.UserErrorCodeEnum.USER_EXIST;
import static com.cqr.interviewworkshop.common.enums.UserErrorCodeEnum.USER_NAME_EXIST;
import static com.cqr.interviewworkshop.common.enums.UserErrorCodeEnum.USER_SAVE_ERROR;

/**
 * User service implementation.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    @Override
    public Boolean hasUsername(String username) {
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void register(UserRegisterReqDTO requestParam) {
        if (!hasUsername(requestParam.getUsername())) {
            throw new ClientException(USER_NAME_EXIST);
        }
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        if (!lock.tryLock()) {
            throw new ClientException(USER_NAME_EXIST);
        }
        try {
            int inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
            if (inserted < 1) {
                throw new ClientException(USER_SAVE_ERROR);
            }
            userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
        } catch (DuplicateKeyException ex) {
            throw new ClientException(USER_EXIST);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void update(UserUpdateReqDTO requestParam, String currentUsername) {
        if (requestParam == null) {
            throw new ClientException("request body cannot be empty");
        }
        if (StrUtil.isBlank(currentUsername)) {
            throw new ClientException("current user is not logged in");
        }
        if (StrUtil.isNotBlank(requestParam.getUsername()) && !currentUsername.equals(requestParam.getUsername())) {
            throw new ClientException("no permission to update other users");
        }

        requestParam.setUsername(currentUsername);
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, currentUsername);
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), updateWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword, requestParam.getPassword())
                .eq(UserDO::getDelFlag, 0);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException("user does not exist");
        }

        Map<Object, Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(USER_LOGIN_KEY + requestParam.getUsername());
        if (CollUtil.isNotEmpty(hasLoginMap)) {
            stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.MINUTES);
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElseThrow(() -> new ClientException("user login state is invalid"));
            return new UserLoginRespDTO(token);
        }

        String uuid = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().put(USER_LOGIN_KEY + requestParam.getUsername(), uuid, JSON.toJSONString(userDO));
        stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.MINUTES);
        return new UserLoginRespDTO(uuid);
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        return stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + username, token) != null;
    }

    @Override
    public void logout(String username, String token) {
        if (checkLogin(username, token)) {
            stringRedisTemplate.delete(USER_LOGIN_KEY + username);
            return;
        }
        throw new ClientException("user token does not exist or user is not logged in");
    }

    @Override
    public IPage<UserPageRespDTO> pageUsers(UserPageReqDTO requestParam) {
        Page<UserDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());

        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getDelFlag, 0);

        if (requestParam.getKeyword() != null && !requestParam.getKeyword().trim().isEmpty()) {
            String keyword = requestParam.getKeyword().trim();
            queryWrapper.and(wrapper -> wrapper
                    .like(UserDO::getUsername, keyword)
                    .or().like(UserDO::getRealName, keyword)
                    .or().like(UserDO::getMail, keyword)
            );
        }

        if (requestParam.getCreateTimeSort() != null) {
            if ("asc".equalsIgnoreCase(requestParam.getCreateTimeSort())) {
                queryWrapper.orderByAsc(UserDO::getCreateTime);
            } else {
                queryWrapper.orderByDesc(UserDO::getCreateTime);
            }
        } else {
            queryWrapper.orderByDesc(UserDO::getCreateTime);
        }

        IPage<UserDO> userPage = baseMapper.selectPage(page, queryWrapper);

        Page<UserPageRespDTO> resultPage = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        resultPage.setTotal(userPage.getTotal());

        if (CollUtil.isNotEmpty(userPage.getRecords())) {
            resultPage.setRecords(userPage.getRecords().stream()
                    .map(userDO -> {
                        UserPageRespDTO respDTO = new UserPageRespDTO();
                        BeanUtils.copyProperties(userDO, respDTO);
                        return respDTO;
                    })
                    .toList());
        }

        return resultPage;
    }
}
