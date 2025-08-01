package com.order.order.common.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

// 상품 등록 + 주문 등록 시 재고 처리
@Component
public class StockInventoryService {

    private final RedisTemplate<String, String> redisTemplate;

    public StockInventoryService(@Qualifier("stockInventory") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 상품 등록 시 재고 수량 세팅
    public void makeStockQuantity(Long productId, int quantity) {
        // redis에 재고 수량 저장
        redisTemplate.opsForValue().set(String.valueOf(productId), String.valueOf(quantity));      // 값을 key-value 형식으로 세팅
    }
    
    // 주문 성공 시 재고 수량 감소
    public int decreaseStockQuantity(Long productId, int orderQuantity) {
        String remainObject = redisTemplate.opsForValue().get(String.valueOf(productId));
        int remains = Integer.parseInt(remainObject);
        if (remains < orderQuantity) {
            // OrderService 단에서 0보다 작으면 에러 발생시키도록 설계함
            return -1;
        } else {
            // decrement(감소 시킬 key(String), 감소 시킬 수)
            Long finalRemains = redisTemplate.opsForValue().decrement(String.valueOf(productId), orderQuantity);    // 남아있는 수량
            return finalRemains.intValue();
        }
    }
    
    
    // 주문 취소 시 재고 수량 증가
    public int increaseStockQuantity(Long productId, int cancelQuantity) {
        Long finalRemains = redisTemplate.opsForValue().increment(String.valueOf(productId), cancelQuantity);
        return finalRemains.intValue();
    }
}
