package com.order.order.product.service;

import com.order.order.member.domain.Member;
import com.order.order.member.repository.MemberRepository;
import com.order.order.product.domain.Product;
import com.order.order.product.domain.ProductSearchDTO;
import com.order.order.product.dto.ProductCreateDTO;
import com.order.order.product.dto.ProductResDTO;
import com.order.order.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Transactional
@Service
@RequiredArgsConstructor
public class ProductService {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final S3Client s3Client;

    // 상품 등록
    public Long save(ProductCreateDTO productCreateDTO) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow((() -> new EntityNotFoundException("권한 없음")));
        Product product = productRepository.save(productCreateDTO.toEntity(member));

        if (productCreateDTO.getProductImage() != null) {
            // 이미지 파일명 설정
            String fileName = "product-" + product.getId() + "-profileImage-" + productCreateDTO.getProductImage().getOriginalFilename();

            // 저장 객체 구성
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(productCreateDTO.getProductImage().getContentType())         // jpeg, mp4, ...
                    .build();

            // 이미지 업로드 (byte 형태로 업로드)
            try {
                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(productCreateDTO.getProductImage().getBytes()));
            } catch (Exception e) {
                // checked 를 unchecked로 바꿔 전체 rollback 되도록 예외 처리
                throw new IllegalArgumentException("이미지 업로드 실패");
            }

            // S3에서 이미지 url 추출
            String imgUrl = s3Client.utilities().getUrl(a -> a.bucket(bucket).key(fileName)).toExternalForm();
            product.updateImageUrl(imgUrl);

        }
        return product.getId();
    }

    // 상품 목록
    public List<ProductResDTO> findAll() {
        return productRepository.findAll().stream().map(ProductResDTO::fromEntity).collect(Collectors.toList());
    }

    public Page<ProductResDTO> findAll(Pageable pageable, ProductSearchDTO productSearchDTO) {
        Specification<Product> specification = new Specification<Product>() {
            @Override
            public Predicate toPredicate(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                List<Predicate> predicateList = new ArrayList<>();
                // name input값 존재 여부 확인
                if (productSearchDTO.getProductName() != null) {
                    //  and title like '%postSearchDTO.getTitle%'
                    predicateList.add(criteriaBuilder.like(root.get("name"), "%" + productSearchDTO.getProductName() + "%"));
                }
                // category input값 존재 여부 확인
                if (productSearchDTO.getCategory() != null) {
                    //  and category = "postSearchDTO.getCategory";
                    predicateList.add(criteriaBuilder.equal(root.get("category"), productSearchDTO.getCategory()));
                }
                Predicate[] predicateArr = new Predicate[predicateList.size()];
                for (int i = 0; i < predicateList.size(); i++) {
                    predicateArr[i] = predicateList.get(i);
                }

                // 위의 검색 조건을 하나(한 줄)의 Predicate 객체로 만들어서 return
                Predicate predicate = criteriaBuilder.and(predicateArr);

                return predicate;
            }
        };

//        Page<Product> productPage = productRepository.findAll(specification, pageable);
//        return productPage.map(ProductResDTO::fromEntity);
        return productRepository.findAll(specification, pageable).map(ProductResDTO::fromEntity);

    }

    // 상품 상세 조회
    public ProductResDTO findById(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다"));
        return ProductResDTO.fromEntity(product);
    }


}
