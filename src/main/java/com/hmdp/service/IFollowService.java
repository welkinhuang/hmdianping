package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注或取关用户
     * @param followUserId 要关注的用户ID
     * @param isFollow true-关注，false-取关
     * @return 结果
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断是否关注了某个用户
     * @param followUserId 用户ID
     * @return 是否关注
     */
    Result isFollow(Long followUserId);

    /**
     * 查询共同关注
     * @param id 目标用户ID
     * @return 共同关注的用户列表
     */
    Result followCommons(Long id);
}
