package com.loopers.domain.product;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    /** 삭제되지 않은 상품만 조회한다. */
    Optional<ProductModel> findActive(Long productId);

    /** 삭제되지 않은 상품만 조회한다. 없는 식별자는 결과에서 빠진다. */
    List<ProductModel> findAllActiveByIds(Collection<Long> productIds);

    ProductModel save(ProductModel product);

    /**
     * 브랜드에 연결된 미삭제 상품을 한 번의 bulk UPDATE 로 Soft Delete 한다.
     * 개별 Entity 행동·변경 감지·콜백을 거치지 않으므로 삭제·수정 시각에 같은 값을 직접 기록하며,
     * 이미 적재된 관리 객체를 동기화하지 않는다. 변경된 상품 수를 반환하며 0 도 정상이다.
     */
    int softDeleteAllActiveByBrandId(Long brandId, ZonedDateTime deletedAt);
}
