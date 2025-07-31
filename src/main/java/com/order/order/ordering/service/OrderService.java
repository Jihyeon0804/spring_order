package com.order.order.ordering.service;

import com.order.order.common.service.StockInventoryService;
import com.order.order.common.service.StockRabbitMqService;
import com.order.order.member.domain.Member;
import com.order.order.member.repository.MemberRepository;
import com.order.order.ordering.domain.OrderDetail;
import com.order.order.ordering.domain.Ordering;
import com.order.order.ordering.dto.OrderCreateDTO;
import com.order.order.ordering.dto.OrderDetailResDTO;
import com.order.order.ordering.dto.OrderListResDTO;
import com.order.order.ordering.repository.OrderDetailRepository;
import com.order.order.ordering.repository.OrderRepository;
import com.order.order.product.domain.Product;
import com.order.order.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Transactional
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final StockInventoryService stockInventoryService;
    private final StockRabbitMqService stockRabbitMqService;

    // 주문 생성
    // 메서드 앞에 synchronized 를 붙여 동시성 문제를 해결하려고 해도 제3의 시스템(DB 등) 도 멀티스레드로 동작하기 때문에 여전히 해결 안됨
    public Long save(List<OrderCreateDTO> orderCreateDTOList) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("로그인 후 이용 가능"));

        Ordering ordering = Ordering.builder().member(member).build();

        for (OrderCreateDTO orderCreateDTO : orderCreateDTOList) {
            Product product = productRepository.findById(orderCreateDTO.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException("product is not found"));

            if (product.getStockQuantity() < orderCreateDTO.getProductCount()) {
                // 부분 성공되면 안되기 때문에 에러 발생시켜 rollback
                // 예외를 강제 발생시켜 모두 임시 저장 사항들을 rollback 처리
                throw new IllegalArgumentException("재고가 부족합니다.");
            }

            // 상품 재고 조정
            // 1. 동시에 접근하는 상황에서 update 값의 정합성이 꺠지고 갱신 이상 발생 (주문은 100개인데, 재고는 50개만 빠진 경우)
            // 2. spring 버전이나 mysql 버전에 따라 jpa에서 강제 에러 (deadlock) 를 유발시켜 대부분의 요청 실패 발생
            // (주문 100개 중에 20개만 성공, 80개는 deadlock(교착 상태)으로 인한 에러)
            // => 해결책
            // 1) synchronized
//            product.setStockQuantity(product.getStockQuantity() - orderCreateDTO.getProductCount());
            product.updateStockQuantity(orderCreateDTO.getProductCount());

            OrderDetail orderDetail = OrderDetail.builder()
                    .product(product)
                    .quantity(orderCreateDTO.getProductCount())
                    .ordering(ordering)
                    .build();

            ordering.getOrderDetailList().add(orderDetail);
//            orderDetailRepository.save(orderDetail);                      // cascading 사용하지 않는 경우
        }
        orderRepository.save(ordering);

        return ordering.getId();
    }
    
    // 주문 등록 + 동시성 문제 해결
    // 격리 레벨을 낮춤으로서, 성능 향상과 lock 관련 문제 원천 차단
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long saveConcurrent(List<OrderCreateDTO> orderCreateDTOList) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("로그인 후 이용 가능"));

        Ordering ordering = Ordering.builder().member(member).build();

        for (OrderCreateDTO orderCreateDTO : orderCreateDTOList) {
            Product product = productRepository.findById(orderCreateDTO.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException("product is not found"));

            // redis에서 재고 수량 확인 및 재고 수량감소 처리
            int newQuantity = stockInventoryService.decreaseStockQuantity(product.getId(), orderCreateDTO.getProductCount());
            if (newQuantity < 0) {
                throw new IllegalArgumentException("재고 부족");
            }

            OrderDetail orderDetail = OrderDetail.builder()
                    .product(product)
                    .quantity(orderCreateDTO.getProductCount())
                    .ordering(ordering)
                    .build();

            ordering.getOrderDetailList().add(orderDetail);

            // 큐에 메세지를 담는다.
            // rdb 에 사후 update를 위한 메세지 발행 (비동기 처리)
            stockRabbitMqService.publish(orderCreateDTO.getProductId(), orderCreateDTO.getProductCount());
        }

        orderRepository.save(ordering);

        return ordering.getId();
    }
    
    // 주문 목록 조회
    public List<OrderListResDTO> findAll() {
        List<Ordering> orderList = orderRepository.findAll();
        List<OrderListResDTO> orderListResDTOList = new ArrayList<>();
        for (Ordering ordering : orderList) {
            List<OrderDetailResDTO> orderDetailResDTOList = ordering.getOrderDetailList().stream()
                    .map(OrderDetailResDTO::fromEntity).collect(Collectors.toList());

            OrderListResDTO orderListResDTO = OrderListResDTO.builder()
                        .id(ordering.getId())
                        .orderStatus(ordering.getOrderStatus())
                        .orderDetailResDTOList(orderDetailResDTOList)
                        .memberEmail(ordering.getMember().getEmail()).build();

            orderListResDTOList.add(orderListResDTO);
        }
        return orderListResDTOList;
//        return orderRepository.findAll().stream().map(o -> OrderListResDTO.fromEntity(o))
//                .collect(Collectors.toList());
    }
    
    // 나의 주문 목록 조회
    public List<OrderListResDTO> findAllByMemberId() {
        Member member = memberRepository.findByEmail(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow();
        Long id = memberRepository.findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new EntityNotFoundException("")).getId();
        List<Ordering> orderList = orderRepository.findAllByMemberId(id);
        List<OrderListResDTO> orderListResDTOList = new ArrayList<>();
        for (Ordering ordering : orderList) {
            List<OrderDetailResDTO> orderDetailResDTOList = ordering.getOrderDetailList().stream()
                    .map(OrderDetailResDTO::fromEntity).collect(Collectors.toList());

            OrderListResDTO orderListResDTO = OrderListResDTO.builder()
                    .id(ordering.getId())
                    .orderStatus(ordering.getOrderStatus())
                    .orderDetailResDTOList(orderDetailResDTOList)
                    .memberEmail(ordering.getMember().getEmail()).build();

            orderListResDTOList.add(orderListResDTO);
        }
        return orderListResDTOList;
//        return orderRepository.findAllByMember(member).stream().map(OrderListResDTO::fromEntity)
//                .collect(Collectors.toList());



    }
}
