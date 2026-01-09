package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类 - 异步秒杀优化版本
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Lazy
    @Resource
    private IVoucherOrderService proxyVoucherOrderService; // 延迟注入自身代理以避免循环依赖

    // ============ 异步秒杀相关 ============
    
    // 阻塞队列 - 用于暂存订单信息
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    
    // 线程池 - 用于异步处理订单
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    
    // Lua脚本 - 用于原子性判断和扣减库存
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    
    // 项目启动后立即执行，初始化异步订单处理线程
    @PostConstruct
    private void init() {
        // 创建Redis Stream消费者组（如果不存在）
        try {
            stringRedisTemplate.opsForStream().createGroup("stream.orders", "g1");
            log.info("Redis Stream消费者组创建成功");
        } catch (Exception e) {
            log.info("消费者组已存在或创建失败: {}", e.getMessage());
        }
        
        // 启动异步订单处理线程
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
        log.info("异步订单处理线程启动成功");
    }
    
    // 异步订单处理器
    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        
        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 从Redis Stream消息队列中获取订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    
                    // 2. 判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        // 如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    
                    // 3. 解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = new VoucherOrder();
                    voucherOrder.setId(Long.valueOf((String) values.get("id")));
                    voucherOrder.setUserId(Long.valueOf((String) values.get("userId")));
                    voucherOrder.setVoucherId(Long.valueOf((String) values.get("voucherId")));
                    
                    // 4. 执行数据库操作（创建订单）
                    handleVoucherOrder(voucherOrder);
                    
                    // 5. ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                    
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    // 处理pending-list中的消息
                    handlePendingList();
                }
            }
        }
        
        // 处理pending-list中未确认的消息
        private void handlePendingList() {
            while (true) {
                try {
                    // 1. 获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    
                    // 2. 判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        // 如果获取失败，说明pending-list没有消息，结束循环
                        break;
                    }
                    
                    // 3. 解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = new VoucherOrder();
                    voucherOrder.setId(Long.valueOf((String) values.get("id")));
                    voucherOrder.setUserId(Long.valueOf((String) values.get("userId")));
                    voucherOrder.setVoucherId(Long.valueOf((String) values.get("voucherId")));
                    
                    // 4. 执行数据库操作（创建订单）
                    handleVoucherOrder(voucherOrder);
                    
                    // 5. ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                    
                } catch (Exception e) {
                    log.error("处理pending-list订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }
    
    // 处理订单创建的具体业务逻辑
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1. 获取用户id
        Long userId = voucherOrder.getUserId();
        
        // 2. 创建分布式锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        
        // 3. 尝试获取锁
        boolean isLock = lock.tryLock();
        
        // 4. 判断是否获取锁成功
        if (!isLock) {
            // 获取锁失败
            log.error("不允许重复下单！");
            return;
        }
        
        try {
            // 通过代理对象调用，确保@Transactional注解生效
            proxyVoucherOrderService.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    // ============ 异步秒杀优化版本 ============
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");
        
        // 1. 执行Lua脚本，进行库存判断、用户重复判断、扣减库存、将订单信息发送到Stream消息队列
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        
        // 2. 判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            // 2.1 不为0，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足！" : "不能重复下单！");
        }
        
        // 3. 返回订单id（订单已加入Stream队列，异步处理中）
        return Result.ok(orderId);
    }
    
    // ============ 同步秒杀版本（已弃用） ============
    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     //1.查询优惠券
    //     SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //     //2.判断秒杀是否开始
    //     if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
    //         return Result.fail("秒杀尚未开始");
    //     }
    //     //3.判断秒杀是否已经结束
    //     if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
    //         return Result.fail("秒杀已经结束");
    //     }
    //     //4.判断库存是否充足
    //     if(voucher.getStock()<1)
    //     {
    //         return Result.fail("库存不足！");
    //     }
    //     
    //     //5.一人一单
    //     Long userId = UserHolder.getUser().getId();
    //     
    //     // ===== 使用Redisson实现分布式锁 =====
    //     //5.1 创建锁对象
    //     RLock lock = redissonClient.getLock("lock:order:" + userId);
    //     //5.2 尝试获取锁
    //     boolean isLock = lock.tryLock();
    //     //5.3 判断是否获取锁成功
    //     if (!isLock) {
    //         //获取锁失败，返回错误或重试
    //         return Result.fail("不允许重复下单！");
    //     }
    //     try {
    //         //获取代理对象（事务）
    //         return voucherOrderService.createVoucherOrder(voucherId);
    //     } finally {
    //         //释放锁
    //         lock.unlock();
    //     }
    // }

    // ============ 创建订单（异步调用）============
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 1. 一人一单（双重校验）
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        
        // 1.1 查询订单
        long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 1.2 判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过一次！");
            return;
        }

        // 2. 扣减数据库库存（双重校验）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            log.error("库存扣减失败！");
            return;
        }
        
        // 3. 创建订单并保存到数据库
        save(voucherOrder);
        
        log.info("订单创建成功，订单ID: {}", voucherOrder.getId());
    }
    
    // ============ 创建订单（同步版本 - 已弃用）============
    // @Transactional
    // public Result createVoucherOrder(Long voucherId) {
    //     //5.1 一人一单
    //     Long userId = UserHolder.getUser().getId();
    //     //5.2 查询订单
    //     int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    //     //5.3 判断是否存在
    //     if (count > 0) {
    //         //用户已经购买过了
    //         return Result.fail("用户已经购买过一次！");
    //     }
    //
    //     //6.扣减库存
    //     boolean success = seckillVoucherService.update()
    //             .setSql("stock = stock - 1")
    //             .eq("voucher_id", voucherId)
    //             .gt("stock", 0)
    //             .update();
    //
    //     if (!success) {
    //         return Result.fail("库存扣减失败！");
    //     }
    //     //7.创建订单 
    //     VoucherOrder voucherOrder = new VoucherOrder();
    //     long orderId = redisIdWorker.nextId("order");
    //     voucherOrder.setId(orderId);
    //     voucherOrder.setUserId(userId);
    //     voucherOrder.setVoucherId(voucherId);
    //     save(voucherOrder);
    //     //8.返回订单id
    //     return Result.ok(orderId);
    // }
}
