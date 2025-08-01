package com.order.order.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.order.common.dto.StockRabbitMqDTO;
import com.order.order.product.domain.Product;
import com.order.order.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
@RequiredArgsConstructor
public class StockRabbitMqService {

    private final RabbitTemplate rabbitTemplate;
    private final ProductRepository productRepository;

    // rabbitmq에 메세지 발행
    public void publish(Long productId, int productCount) {
        StockRabbitMqDTO stockRabbitMqDTO = StockRabbitMqDTO.builder()
                .productId(productId)
                .productCount(productCount)
                .build();
//        Map<String, Object> map = new HashMap<>();
//        map.put("productId", productId);
//        map.put("productCount", productCount);

        rabbitTemplate.convertAndSend("stockDecreaseQueue", stockRabbitMqDTO);
    }

    // rabbitmq에 발행된 메세지를 수신
    // 큐에 메세지 들어오는 순간 메서드 실행됨
    // 단일 스레드로 동작 -> 동시성 문제 발생X
    // Listener는 단일 스레드로 메세지를 처리하므로, 동시성 이슈 발생X
    // 메세지 처리 실패 시 그냥 유실 (예외 처리해서 다시 메세지 넣는 걸로 해결하면 됨(아래 코드는 해당 예외 처리하지 않음))
    @Transactional
    @RabbitListener(queues = "stockDecreaseQueue")
    public void subscribe(Message message) throws JsonProcessingException {
        String messageBody = new String(message.getBody());
        ObjectMapper objectMapper = new ObjectMapper();
        StockRabbitMqDTO stockRabbitMqDTO = objectMapper.readValue(messageBody, StockRabbitMqDTO.class);
        Product product = productRepository.findById(stockRabbitMqDTO.getProductId()).orElseThrow(() -> new EntityNotFoundException("product is not found"));
        product.updateStockQuantity(stockRabbitMqDTO.getProductCount());
    }

}
