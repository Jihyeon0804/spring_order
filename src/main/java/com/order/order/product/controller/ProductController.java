package com.order.order.product.controller;

import com.order.order.common.dto.CommonDTO;
import com.order.order.product.domain.ProductSearchDTO;
import com.order.order.product.dto.ProductCreateDTO;
import com.order.order.product.dto.ProductResDTO;
import com.order.order.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    
    // 상품 등록
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@ModelAttribute ProductCreateDTO productCreateDTO) {
        Long id = productService.save(productCreateDTO);
        return new ResponseEntity<>(CommonDTO.builder()
                .result(id)
                .status_code(HttpStatus.CREATED.value())
                .status_message("상품 등록 성공").build(), HttpStatus.CREATED);
    }

    @GetMapping("/list")
    public ResponseEntity<?> findAll(@PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable
            , ProductSearchDTO productSearchDTO) {
        Page<ProductResDTO> productSearchDTOPage = productService.findAll(pageable, productSearchDTO);
        return new ResponseEntity<>(CommonDTO.builder()
                .result(productSearchDTOPage)
                .status_code(HttpStatus.OK.value())
                .status_message("상품 목록 조회 성공").build(), HttpStatus.OK);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable Long id) {
        return new ResponseEntity<>(CommonDTO.builder()
                .result(productService.findById(id))
                .status_code(HttpStatus.OK.value())
                .status_message("상품 상세 조회 성공").build(), HttpStatus.OK);
    }
}
