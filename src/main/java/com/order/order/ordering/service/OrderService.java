package com.order.order.ordering.service;

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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Transactional
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderDetailRepository orderDetailRepository;

    // 주문 생성
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
            product.setStockQuantity(product.getStockQuantity() - orderCreateDTO.getProductCount());

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
    }
}
