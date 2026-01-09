package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 保存探店笔记
     * @param blog 笔记内容
     * @return 笔记ID
     */
    Result saveBlog(Blog blog);

    /**
     * 根据ID查询探店笔记详情
     * @param id 笔记ID
     * @return 笔记详情
     */
    Result queryBlogById(Long id);

    /**
     * 点赞博客
     * @param id 博客ID
     * @return 结果
     */
    Result likeBlog(Long id);

    /**
     * 查询热门博客
     * @param current 当前页码
     * @return 博客列表
     */
    Result queryHotBlog(Integer current);

    /**
     * 查询博客点赞排行榜Top5
     * @param id 笔记ID
     * @return 点赞用户列表
     */
    Result queryBlogLikes(Long id);

    /**
     * 滚动分页查询关注的人发布的博客
     * @param max 上一次查询的最小时间戳
     * @param offset 偏移量
     * @return 博客列表和下次查询参数
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
