package com.order.order.ordering.dto;

import com.order.order.ordering.domain.OrderDetail;
import com.order.order.ordering.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderListResDTO {
    private Long id;
    private String memberEmail;
    private OrderStatus orderStatus;
    private List<OrderDetailResDTO> orderDetailResDTOList;
}
