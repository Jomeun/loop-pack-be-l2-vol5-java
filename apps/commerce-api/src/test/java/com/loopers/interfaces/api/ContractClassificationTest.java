package com.loopers.interfaces.api;

import com.loopers.domain.example.ExampleModel;
import com.loopers.infrastructure.example.ExampleJpaRepository;
import com.loopers.interfaces.api.example.ExampleV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractClassificationTest {

    private static final Function<Long, String> ENDPOINT_GET = id -> "/api/v1/examples/" + id;
    private static final String UNMAPPED_ENDPOINT = "/api/v1/unmapped-contract-classification";

    private final TestRestTemplate testRestTemplate;
    private final ExampleJpaRepository exampleJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ContractClassificationTest(
        TestRestTemplate testRestTemplate,
        ExampleJpaRepository exampleJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.exampleJpaRepository = exampleJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/examples/{id}")
    @Nested
    class Get {
        @DisplayName("존재하는 예시 ID를 주면, 해당 예시 정보를 반환한다.")
        @Test
        void returnsExampleInfo_whenValidIdIsProvided() {
            // arrange
            ExampleModel exampleModel = exampleJpaRepository.save(
                new ExampleModel("예시 제목", "예시 설명")
            );
            String requestUrl = ENDPOINT_GET.apply(exampleModel.getId());

            // act
            ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), responseType);
            ApiResponse<ExampleV1Dto.ExampleResponse> responseBody = response.getBody();

            // assert
            assertThat(responseBody).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(responseBody.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(responseBody.meta().errorCode()).isNull(),
                () -> assertThat(responseBody.data()).isNotNull(),
                () -> assertThat(responseBody.data().id()).isEqualTo(exampleModel.getId()),
                () -> assertThat(responseBody.data().name()).isEqualTo(exampleModel.getName()),
                () -> assertThat(responseBody.data().description()).isEqualTo(exampleModel.getDescription())
            );
        }

        @DisplayName("숫자가 아닌 ID abc로 요청하면, 문법 오류 계약을 반환한다.")
        @Test
        void returnsBadRequestContract_whenIdIsNotNumeric() {
            // arrange
            String requestUrl = "/api/v1/examples/abc";

            // act
            ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), responseType);
            ApiResponse<ExampleV1Dto.ExampleResponse> responseBody = response.getBody();

            // assert
            assertThat(responseBody).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(responseBody.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(responseBody.meta().errorCode()).isEqualTo("Bad Request"),
                () -> assertThat(responseBody.data()).isNull()
            );
        }

        @DisplayName("존재하지 않는 숫자 ID로 요청하면, 자원 없음 계약을 반환한다.")
        @Test
        void returnsNotFoundContract_whenExampleDoesNotExist() {
            // arrange
            Long invalidId = -1L;
            String requestUrl = ENDPOINT_GET.apply(invalidId);

            // act
            ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), responseType);
            ApiResponse<ExampleV1Dto.ExampleResponse> responseBody = response.getBody();

            // assert
            assertThat(responseBody).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(responseBody.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(responseBody.meta().errorCode()).isEqualTo("Not Found"),
                () -> assertThat(responseBody.data()).isNull()
            );
        }

        @DisplayName("매핑되지 않은 URL로 요청하면, 처리기 없음 계약을 반환한다.")
        @Test
        void returnsNotFoundContract_whenUrlIsNotMapped() {
            // act
            ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response =
                testRestTemplate.exchange(UNMAPPED_ENDPOINT, HttpMethod.GET, new HttpEntity<>(null), responseType);
            ApiResponse<ExampleV1Dto.ExampleResponse> responseBody = response.getBody();

            // assert
            assertThat(responseBody).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(responseBody.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(responseBody.meta().errorCode()).isEqualTo("Not Found"),
                () -> assertThat(responseBody.data()).isNull()
            );
        }
    }
}
