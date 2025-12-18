-- 秒杀Lua脚本 - 实现原子性的库存检查和扣减
-- 参数列表
-- ARGV[1]: 优惠券id
-- ARGV[2]: 用户id
-- ARGV[3]: 订单id

-- 拼接key
-- 1.1 库存key
local stockKey = 'seckill:stock:' .. ARGV[1]
-- 1.2 订单key
local orderKey = 'seckill:order:' .. ARGV[1]

-- 2. 获取库存
local stock = tonumber(redis.call('get', stockKey))

-- 3. 判断库存是否充足
if (stock == nil or stock <= 0) then
    -- 库存不足，返回1
    return 1
end

-- 4. 判断用户是否已经下单 (SISMEMBER: 判断set集合中是否存在某个元素)
if (redis.call('sismember', orderKey, ARGV[2]) == 1) then
    -- 用户已经下过单，返回2
    return 2
end

-- 5. 扣减库存 (INCRBY: 增量操作，-1表示减1)
redis.call('incrby', stockKey, -1)

-- 6. 将用户id加入订单集合
redis.call('sadd', orderKey, ARGV[2])

-- 7. 发送消息到Stream消息队列（使用Redis的Stream，Redis 5.0+支持）
redis.call('xadd', 'stream.orders', '*', 'userId', ARGV[2], 'voucherId', ARGV[1], 'id', ARGV[3])

-- 成功，返回0
return 0
