# S.W.M (Swim Way Makers) - Backend

> 수영 커뮤니티 플랫폼 - **Spring Boot 기반 백엔드 서버**

<br>

<p align="center">
  <a href="https://github.com/Min-su-Jeong/3-daniel-jeong-community-FE">
    <img src="https://img.shields.io/badge/Frontend_Repository-4285F4?style=for-the-badge&logo=github&logoColor=white" alt="Frontend Repository">
  </a>
  <a href="https://github.com/Min-su-Jeong/3-daniel-jeong-community-BE">
    <img src="https://img.shields.io/badge/Backend_Repository_👈-28a745?style=for-the-badge&logo=github&logoColor=white" alt="Backend Repository">
  </a>
</p>

<br>

## 프로젝트 개요
  
### 프로젝트 소개

<strong>S.W.M (Swim Way Makers)</strong>는 수영에 관심있는 모든 사람들을 대상으로 필요한 정보와 서비스를 한 곳에서 제공하는 통합 플랫폼입니다.

<p>
  <img align="center" width="1000" height="500" alt="Mainpage" src="https://github.com/user-attachments/assets/9f03b175-14cf-412d-9776-cb5efde1776a" />
</p>

---

### 홈페이지 링크

[S.W.M (Swim Way Makers)](https://www.swimwaymakers.site/)

### 시연 영상

[![Watch the video](https://img.shields.io/badge/Play%20Demo-Google%20Drive-blue?logo=google-drive)](https://drive.google.com/file/d/1oB1QGWnx1VxZYh6jkUyT-utT7On7D_88/view?usp=drive_link)

---

### 개발 목적

2년간 수영 입문부터 대회 출전까지의 여정을 겪으면서, 수영에 필요한 정보가 매우 분산되어 있다는 것을 느꼈습니다.

- **실제 경험한 문제점**:
  - 중고 수영 용품 구매 시, 여러 사이트 탐색 및 각 도메인별 회원 인증 필요의 번거로움
  - 시/구 단위 수영대회 일정은 공식 홈페이지에 기재 X → 여러 수영장 카페 서칭 필요
  - 주변 수영장 검색 시 별도 앱 접속 및 수영장 외 불필요한 정보가 함께 노출되는 경우 다수
  - 자주 찾는 브랜드가 어느정도 정해져 있는데도 매번 브라우저에서 검색해 공식 홈페이지에 접속 필요

  이러한 불편함을 해결하기 위해 수영 관련 정보와 서비스를 통합한 플랫폼을 구축하고자 하였습니다.

- **핵심 목표**:
  - 제한된 예산($200) 내 3개월 안정적 운영
  - MVP 우선 개발로 초기 개발 속도 확보
  - 확장 가능한 아키텍처 설계
  - CI/CD 자동화 및 무중단 배포

<br>

## 기술 스택

- **Backend**: Spring Boot 3.5.7, Java 21
- **Database**: MySQL 8.0
- **Infra**: AWS
- **CI/CD**: GitHub Actions

<br>

## 아키텍처

### 레이어드 아키텍처

<p align="center">
  <img width="250" height="750" alt="Image" src="https://github.com/user-attachments/assets/8868b326-810e-478e-b6d5-32c99433a9d0" />
</p>

**의도**:
- **MVP 우선 개발이 필요**했습니다. 도메인 중심 아키텍처(DDD)도 고려했지만 초기 복잡도가 높아 개발 속도가 우려되어 레이어드 아키텍처를 선택했습니다.
- 단순한 구조로 빠른 개발이 가능하면서도 계층 분리로 향후 확장 시 영향 범위를 최소화할 수 있다고 판단했습니다.

---

### 데이터베이스 구조

<p align="center">
  <img alt="DB Architecture" src="https://github.com/user-attachments/assets/4947a7b0-9fe0-4ec8-9c69-d4f93dc4bc2f" />
</p>

- User: 사용자 정보 및 프로필 이미지 관리 (Soft Delete로 복구 가능)
- Post: 게시글 정보
- PostStat: 목록 조회 시 통계 집계 쿼리 부하 감소를 위한 분리 테이블
- PostLike: User-Post 복합키로 중복 좋아요 방지 및 조회 성능 향상
- Comment: parent_id와 depth로 대댓글 계층 구조 구현
- Product: 중고거래 상품 정보 (상태 관리: 판매중/예약중/거래완료)
- RefreshToken: JWT Refresh Token 무효화 관리

---

### 인프라 아키텍처

<p align="center">
  <img width="700" height="900" alt="Image" src="https://github.com/user-attachments/assets/ec4708c5-3adc-4419-8111-f76817640d49" />
</p>

**아키텍처 특징**:
- **VPC 엔드포인트**: SSM, ECR 등 AWS 서비스와의 Private 통신 경로 확보
- **Auto Scaling**: CPU/메모리 기반 자동 스케일링 준비 완료
- **무중단 배포**: ASG 롤링 업데이트로 무중단 배포 지원

**네트워크 흐름**:
1. 사용자 요청 → 가비아 DNS → ALB (HTTPS)
2. ALB → Frontend (Public Subnet) 또는 Backend (Private Subnet)
3. Backend → RDS (Private Subnet, VPC 내부 통신)
4. 이미지 업로드:
   - 게시글 이미지: 프론트 → 백엔드 Presigned URL 발급 → S3 직접 업로드
   - 프로필 이미지: 프론트 → 백엔드 직접 처리 → S3 업로드

<br>

## 결과 및 성과

### 목록/상세 응답 450ms → 120ms
- 문제: Offset 페이징 N+1로 10개 조회 시 11개 쿼리, 응답 450ms
- 대응: **커서 페이징 + JOIN FETCH**, 조회수 비동기 처리
- 결과: 쿼리 **11→1회**, 응답 **450ms→120ms**, 정적 **15→3ms**

---

### 크레딧 $200으로 3개월 운영
- 문제: **$200/3개월** 제약, DB 메모리 70%로 EC2 경합
- 대응: **RDS 분리** + t4g.micro(프론트) + t4g.small(백엔드) + ALB + S3/전송 구성
- 비용(월):
  | 리소스 | 비용 | 비고 |
  |--------|------|------|
  | EC2 t4g.micro | ~$6 | 프론트 |
  | EC2 t4g.small | ~$12 | 백엔드 |
  | RDS db.t4g.micro | ~$14 | MySQL |
  | ALB | ~$18 | 로드 밸런서 |
  | VPC/네트워크 | ~$10 | NAT 등 네트워크 기본 비용 |
  | **합계** | **~$60** | |
- 결과: 월 **~$60-65**(3개월 **~$180-195**, 예산 **90-97%**), 평시 최소 인스턴스·피크는 ASG 스케일아웃으로 대응

---

### 롤링 배포로 무중단 전환
- 문제: 소규모 인프라에서 Blue-Green 비용 2배, Canary 복잡, 단순 교체는 다운타임
- 대응: GitHub Actions→ECR→ASG 롤링, 새 인스턴스 헬스체크 후 순차 교체(임시 1대 비용 수용)
- 결과: 무중단 배포·롤백 용이성 확보, 실패 시 기존 인스턴스로 보호

---

### 토큰·업로드 경로 보안 강화
- 문제: Refresh Token 쿠키만 저장 시 탈취·로그아웃 무효화 불가, 게시글 Presigned URL도 경로 검증 부재
- 대응: Refresh Token DB 저장+revoked 플래그, 게시글 Presigned objectKey 검증, 프로필은 백엔드 직접 업로드
- 결과: 탈취 토큰 즉시 무효화, 업로드 경로 오남용 차단, 추적성 확보

<br>

## 트러블슈팅

### 1. JVM 메모리 설정 최적화: 제한된 리소스에서의 성능 극대화

**문제 상황**:
- t4g.small 인스턴스(2GB 메모리)에서 백엔드 애플리케이션 실행
- RDS는 별도이지만 백엔드 애플리케이션도 메모리 최적화 필요
- 기본 JVM 설정으로는 메모리 부족으로 OOM 발생 가능
- GC 빈도가 높아지면 응답 시간 지연

**해결 방법**:
```bash
# entrypoint.sh
JAVA_OPTS="-Xmx512m -Xms256m -Djava.security.egd=file:/dev/./urandom"
```

- `-Xmx512m`: 최대 힙 메모리 512MB로 제한 (인스턴스 메모리 2GB 중 OS 고려)
- `-Xms256m`: 초기 힙 메모리 256MB로 설정하여 시작 시 메모리 할당 시간 단축
- `-Djava.security.egd`: 랜덤 시드 생성 속도 개선

**추가 고려사항**:
- RDS 분리로 백엔드 인스턴스는 애플리케이션에만 집중
- OS 및 기타: 약 100MB
- Spring Boot: 512MB 힙 메모리로 안정적 운영 가능
- 남은 메모리는 OS 캐시 및 네트워크 버퍼로 활용

**성과**:
- **OOM 에러 없이 안정적 운영**
- GC 빈도 최적화로 응답 시간 유지
- 제한된 리소스에서 최대 성능 확보

---

### 2. 커서 기반 페이지네이션으로 N+1 문제 해결

**문제 상황**:
- 초기 구현 시 Offset 기반 페이지네이션 사용
- 게시글 목록 조회 시 작성자 정보를 별도 쿼리로 조회하여 N+1 문제 발생
- 게시글 10개 조회 시 총 11번의 쿼리 실행 (1번 게시글 조회 + 10번 작성자 조회)
- 데이터가 증가할수록 성능 저하 심화

**해결 방법**:
```java
@Query("select p from Post p join fetch p.user u where p.id < :cursor order by p.id desc")
List<Post> findPageByCursorWithUser(@Param("cursor") Integer cursor, Pageable pageable);
```

- **JOIN FETCH**를 사용하여 게시글과 작성자 정보를 한 번의 쿼리로 조회
- **커서 기반 페이지네이션**으로 Offset의 성능 문제 해결
- 인덱스 활용 최적화 (id 기준 정렬)

**성과**:
- 쿼리 실행 횟수: **11회 → 1회 (91% 감소)**
- 응답 시간: **450ms → 120ms (73% 개선)**

---

### 3. 통계 데이터 정합성 문제 해결

**문제 상황**:
- 게시글 통계(조회수, 좋아요 수, 댓글 수)를 PostStat 테이블에 캐싱
- 좋아요/댓글 추가/삭제 시 PostStat 테이블만 업데이트하고 실제 데이터와 불일치 발생
- 트랜잭션 롤백, 동시성 문제 등으로 통계 데이터가 부정확해짐

**고려한 대안**:
- Redis 캐시: 100만 MAU 기준 피크 RPS 250~333 수준에서 오버엔지니어링으로 판단 (ElastiCache 비용 추가($10-15/월) + 캐시 무효화 복잡도 증가) → DB 최적화만으로 목표 성능 달성 가능하다고 판단
- 매번 실데이터 집계: 성능 저하 우려

**해결 방법**:
```java
@Transactional
public PostStat syncStatistics(Integer postId) {
    PostStat stat = findByIdOrCreate(postId);
    
    // 실제 데이터에서 집계
    int likeCount = postLikeRepository.countByIdPostId(postId);
    int commentCount = commentRepository.countByPostId(postId);
    
    // PostStat 테이블 동기화
    stat.syncLikeCount(likeCount);
    stat.syncCommentCount(commentCount);
    
    return stat;
}
```

- 게시글 상세 조회 시 **실제 데이터를 집계하여 PostStat 테이블과 동기화**
- 조회수는 비동기로 증가시키되, 목록 조회 시에는 동기화된 통계 사용
- 통계 조회 성능은 유지하면서 정확성 보장

**성과**:
- 통계 데이터 정확도: **100% 달성**
- 목록 조회 성능: 동기화 로직 추가에도 불구하고 **150ms 이하 유지**

---

### 4. 비동기 조회수 증가로 응답 속도 개선

**문제 상황**:
- 게시글 상세 조회 시 조회수 증가를 동기적으로 처리
- 조회수 증가 쿼리 실행 시간(평균 50ms)이 응답 시간에 직접 영향
- 동시 접속자가 많을수록 DB 부하 증가 및 응답 지연

**해결 방법**:
```java
@Async
@Transactional
public void incrementViewCount(Integer postId) {
    postStatRepository.incrementViewCount(postId);
}
```

- **@Async** 어노테이션을 사용하여 조회수 증가를 비동기로 처리
- 게시글 상세 조회 응답은 조회수 증가 완료를 기다리지 않고 즉시 반환
- `@EnableAsync` 설정으로 비동기 실행 환경 구성

**성과**:
- 응답 시간: **200ms → 120ms (40% 개선)**
- 동시 접속자 처리 능력 향상
- DB 부하 분산

---

### 5. 통계 테이블 분리로 조회 성능 향상

**문제 상황**:
- 초기 설계 시 게시글 통계를 Post 테이블에 포함
- 게시글 목록 조회 시 통계 집계 쿼리로 인한 성능 저하
- 통계 업데이트 시 Post 테이블 락 발생 가능

**해결 방법**:
```java
@Entity
@Table(name = "post_stat")
public class PostStat {
    @Id
    @Column(name = "post_id")
    private Integer id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    @MapsId
    private Post post;
    
    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer commentCount = 0;
}
```

- **PostStat 테이블을 별도로 분리**
- 게시글 목록 조회 시 통계 데이터만 조회하여 성능 향상
- 통계 업데이트 시 Post 테이블 영향 없음

**성과**:
- 목록 조회 성능: **180ms → 120ms (33% 개선)**
- 통계 업데이트 시 락 경합 감소
- 확장성 향상

<br>

## 프로젝트를 통해 배운 점
- 예산 제약 하에서는 실측·부하 추정 후 명시적으로 비용/성능 트레이드오프를 기록해야 의사결정이 빨라진다.
- 사용자 패턴 기반 최적화가 체감 성능 개선에 가장 효과적이다. (최신 글 위주 → 커서 페이징)
- 오버엔지니어링을 경계하고 필요한 지점에서만 비용을 추가한다. (예: Redis 미도입, RDS 분리)

<br>

## 향후 계획
- 모니터링/알림: Prometheus/Grafana, Zipkin/Jaeger로 메트릭·추적 및 알림 강화
- 부하테스트: 시나리오 기반 부하테스트(피크 시간대 목록 조회, 대회 일정 공지 시 동시 접속 급증)로 실제 트래픽 패턴 검증 및 병목 지점 사전 파악

<br>

## 프로젝트의 개선점
- 실시간 알림: 댓글/좋아요 WebSocket 알림 미구현
- 비용 가시성: RDS/전송 비용을 대시보드로 모니터링 필요
