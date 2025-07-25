# 동시성 시나리오 테스트 및 결과 분석

## 1. 개요

현대 백엔드 시스템에서 동시성 제어는 성능과 안정성 사이의 균형을 달성하는 핵심 과제다. 본 분석은 4가지 동시성 제어 방식(IF_ELSE, Synchronized, ReentrantLock, Semaphore)을 동일한 조건에서 비교 테스트하여, 각 메커니즘의 성능 특성과 적용 시나리오를 데이터 기반으로 검증한다.

특히 Semaphore의 CAS 기반 Lock-Free 알고리즘이 전통적인 Monitor 패턴 기반 동기화 방식과 어떤 성능 차이를 보이는지를 중심으로 분석한다.

## 2. 테스트 환경 및 조건

- **총 요청 수**: 2,000건
- **스레드 수**: 200개  
- **목표 성공률**: 49.5%
- **테스트 대상**: IF_ELSE, Synchronized, ReentrantLock, Semaphore
- **측정 정밀도**: 마이크로초(μs) 단위

### 2.1 전체 테스트 결과 요약

| 메커니즘 | Total Requests | Success | Success Rate (%) | Capacity Failed |
|----------|----------------|---------|------------------|----------------|
| **IF_ELSE** | 2,000 | 991 | 49.55% | 1,009 (50.45%) |
| **Synchronized** | 2,000 | 990 | 49.5% | 1,010 (50.5%) |
| **ReentrantLock** | 2,000 | 990 | 49.5% | 1,010 (50.5%) |
| **Semaphore** | 2,000 | 990 | 49.5% | 1,010 (50.5%) |

모든 메커니즘이 목표 성공률 49.5%를 정확히 달성하여, 순수한 성능 비교가 가능한 동등한 조건을 확보했다.

## 3. 기술적 아키텍처의 근본적 차이점

Semaphore의 성능 분석은 synchronized나 ReentrantLock과 근본적으로 다른 접근을 요구한다. 기존 Lock 방식들이 뮤텍스 락 구조를 기반으로 접근을 얻지 못한 쓰레드를 명시적인 대기열에 놓아 '대기 시간(Wait Time)'을 소비하게 하는 반면, Semaphore는 **CAS(Compare-And-Swap) 메커니즘**을 기반으로 한 **Lock-Free 알고리즘**으로 동작한다.

**핵심 기술적 차별점:**

1. **무잠금(Lock-Free) 알고리즘**: Semaphore의 내부 구현은 `java.util.concurrent.atomic` 패키지의 원자적 연산을 활용하여 커널 레벨 뮤텍스 없이 동작한다. 이는 **컨텍스트 스위칭 오버헤드를 근본적으로 제거**한다.

2. **허가 기반 리소스 관리**: 전통적인 배타적 락(Exclusive Lock)과 달리, Semaphore는 **counting semaphore** 방식으로 동시 접근 가능한 리소스 수를 명시적으로 제어한다. 이는 **세밀한 동시성 제어**와 **리소스 활용률 최적화**를 동시에 달성한다.

3. **비차단(Non-blocking) 시맨틱**: `tryAcquire()` 메서드는 **즉시 반환(Immediate Return)** 보장으로 쓰레드가 무기한 대기 상태에 빠지는 것을 방지한다. 이는 **데드락 방지**와 **시스템 응답성** 측면에서 결정적 장점을 제공한다.

### 3.1 동시성 제어 패러다임 비교

**뮤텍스 락 구조 (Synchronized, ReentrantLock)**
- 접근을 얻지 못한 스레드를 명시적인 대기열에 배치
- '대기 시간(Wait Time)' 발생으로 컨텍스트 스위칭 오버헤드 존재
- 배타적 락(Exclusive Lock) 방식으로 한 번에 하나의 스레드만 허용
- **Synchronized**: 객체 레벨 모니터 기반, 단순하지만 성능 오버헤드 높음
- **ReentrantLock**: AQS 기반 대기큐 관리, 유연한 제어 가능하지만 복잡한 구현

**Lock-Free 알고리즘 (Semaphore)**
- CAS(Compare-And-Swap) 메커니즘 기반 원자적 연산
- 커널 레벨 뮤텍스 없이 동작하여 컨텍스트 스위칭 오버헤드 제거
- Counting Semaphore 방식으로 N개 동시 접근 허용

### 3.2 Semaphore의 기술적 차별점

1. **무잠금(Lock-Free) 알고리즘**: `java.util.concurrent.atomic` 패키지 활용으로 컨텍스트 스위칭 오버헤드 근본적 제거

2. **허가 기반 리소스 관리**: 전통적인 배타적 락과 달리 동시 접근 가능한 리소스 수를 명시적으로 제어

3. **비차단(Non-blocking) 시맨틱**: `tryAcquire()` 메서드의 즉시 반환으로 데드락 방지와 시스템 응답성 향상

**측정 방법론의 근본적 차이:**
허가(Permit) 획득을 위한 재시도(Spinning) 시간은 OS 레벨 스케줄링의 '대기 시간'이 아니라 CPU를 사용하는 '실행 시간'에 포함되므로 측정 방법이 근본적으로 다르다. 이에 따라 Semaphore의 고유한 동작 방식과 성능을 분석하기 위해 '허가 획득 성공률', '허가 거부 처리', '처리 시간' 측면을 종합적으로 평가하여 시스템 과부하 상황에서의 효율적인 요청 제어 성능을 검증했다.

## 4. 성능 측정 결과 및 비교 분석

### 4.1 성공 케이스 성능 비교

#### 4.1.1 전체 처리 시간 (대기시간 + 실행시간 합산)

| 메커니즘 | 평균 (μs) | 중간값 (μs) | 최대값 (μs) | 총합 (μs) | 순위 |
|----------|-----------|-------------|-------------|-----------|------|
| **IF_ELSE** | 189.0 | 76.1 | 5,682.1 | 187,265.9 | 1위 |
| **Semaphore** | **413.1** | **86.2** | **38,176.7** | **408,925.4** | **2위** |
| **Synchronized** | 486.4 | 76.7 | 79,322.0 | 481,486.1 | 3위 |
| **ReentrantLock** | 2,464.7 | 134.6 | 214,513.8 | 2,440,051.5 | 4위 |

**핵심 발견:**
- **Semaphore는 Synchronized 대비 15% 빠른 성능** (413.1μs vs 486.4μs)
- **Semaphore는 ReentrantLock 대비 6배 빠른 성능** (413.1μs vs 2,464.7μs)
- **전체 처리량에서 2위 달성** - 동시성 제어가 있는 방식 중 최고 성능

#### 4.1.2 대기시간 vs 실행시간 분석

**대기시간 비교:**
| 메커니즘 | 평균 대기시간 (μs) | 최대 대기시간 (μs) |
|----------|-------------------|-------------------|
| **IF_ELSE** | 48.4 | 1,609.1 |
| **Synchronized** | 353.1 | 44,233.7 |
| **ReentrantLock** | 2,109.5 | 164,034.8 |
| **Semaphore** | - | - |

**실행시간 비교:**
| 메커니즘 | 평균 실행시간 (μs) | 최대 실행시간 (μs) |
|----------|-------------------|-------------------|
| **IF_ELSE** | 140.6 | 4,073.0 |
| **Synchronized** | 133.3 | 35,088.3 |
| **ReentrantLock** | 355.2 | 50,479.0 |
| **Semaphore** | 413.1 | 38,176.7 |

**주목할 점:**
- **Semaphore는 대기시간이 측정되지 않음** - permit 기반 즉시 처리 방식의 특성
- 다른 메커니즘들은 대기시간 > 실행시간 패턴을 보이지만, Semaphore는 순수 실행시간만 존재

### 4.2 실패 케이스 성능 비교

#### 4.2.1 실패 처리 효율성

| 메커니즘 | 평균 (μs) | 중간값 (μs) | 최대값 (μs) | 총합 (μs) | 순위 |
|----------|-----------|-------------|-------------|-----------|------|
| **IF_ELSE** | 228.8 | 47.6 | 15,209.7 | 230,813.0 | 1위 |
| **Synchronized** | 696.5 | 86.7 | 13,712.7 | 703,440.1 | 2위 |
| **Semaphore** | **971.0** | **119.2** | **23,607.1** | **980,716.9** | **3위** |
| **ReentrantLock** | 4,261.5 | 2,451.3 | 42,557.9 | 4,304,135.2 | 4위 |

**핵심 발견:**
- **Semaphore는 ReentrantLock 대비 4.4배 빠른 실패 처리** (총합: 980,716.9μs vs 4,304,135.2μs)
- **동시성 제어가 있는 방식 중에서는 상당히 우수한 실패 처리 성능**
- Synchronized 대비 39% 느리지만, 허가 기반 즉시 거부 처리의 안정성 확보

## 5. 핵심 성능 특성 분석

### 5.1 시스템 안정성과 처리 효율성

#### 5.1.1 빠른 실패(Fast-Fail) 방식의 우위

**총 처리 시간 효율성 비교 (Sum 기준):**

**성공 케이스:**
1. IF_ELSE: 187,265.9μs (최고 효율)
2. **Semaphore: 408,925.4μs (중간 효율, 2위)**
3. Synchronized: 481,486.1μs (낮은 효율)
4. ReentrantLock: 2,440,051.5μs (최저 효율)

**실패 케이스:**
1. IF_ELSE: 230,813.0μs (최고 효율)
2. Synchronized: 703,440.1μs (중간 효율)
3. **Semaphore: 980,716.9μs (낮은 효율, 3위)**
4. ReentrantLock: 4,304,135.2μs (최저 효율)

tryAcquire 패턴을 사용한 Semaphore는 다른 동시성 구조들이 시스템 용량 초과 요청에 대해 긴 대기열을 생성하는 것과 달리, 즉각적인 실패 처리를 통해 시스템 안정성을 확보한다.

이는 **synchronized와 같은 블록킹 방식이 이미 전체 인원수가 수용할 수 없는 상태임에도 대기 큐에 포함되는 것이 강제되어** 쓰레드들을 차단 상태로 유지하는 것과 대조적으로, 무의미한 컨텍스트 스위칭을 크게 줄여 전체 시스템의 오버헤드를 최소화한다.

#### 5.1.2 대기열 관리 vs 즉시 처리

**이론적 근거:**
- **대기열 제거**: Semaphore는 대기열을 생성하지 않아 메모리 사용량과 컨텍스트 스위칭 오버헤드를 근본적으로 제거
- **응답성 보장**: 즉시 실패 처리를 통해 시스템 전체의 응답시간 예측 가능성을 크게 향상
- **리소스 효율성**: 불필요한 스레드 블로킹으로 인한 시스템 자원 낭비 방지

### 5.2 Lock-Free 알고리즘의 기술적 우월성

#### 5.2.1 CAS 기반 성능 최적화

**기술적 우월성의 근거:**
1. **원자적 허가 관리**: CAS 기반의 permit 카운터 조작으로 스레드 블로킹 없이 동시 접근 제어
2. **컨텍스트 스위칭 최소화**: 전통적인 락과 달리 스레드를 대기 상태로 전환하지 않아 OS 레벨 스케줄링 오버헤드 제거
3. **스핀락 기반 효율성**: 짧은 대기 시간 동안 CPU 사이클을 활용한 능동적 대기로 락 획득 지연 최소화

#### 5.2.2 성능 수치로 본 우위성

**ReentrantLock과의 극명한 차이:**
- **대기시간**: ReentrantLock 평균 2,109.5μs vs Semaphore 0μs (측정되지 않음)
- **전체 처리시간**: ReentrantLock 2,464.7μs vs Semaphore 413.1μs (**6배 차이**)
- **최대 처리시간**: ReentrantLock 214,513.8μs vs Semaphore 38,176.7μs (**5.6배 차이**)

**Synchronized와의 비교:**
- **평균 성능**: 15% 향상 (486.4μs → 413.1μs)
- **최대 지연시간**: 50% 개선 (79,322.0μs → 38,176.7μs)
- **총 처리 효율성**: 15% 향상 (481,486.1μs → 408,925.4μs)

## 6. 분석 결과 및 최종 결론

### 6.1 동시성 제어 방식별 특성 및 적용 가이드라인

• **IF-ELSE**: 제어 없는 동시성은 **데이터의 원자성이 보장되지 않아 데이터 정합성을 이룰 수 없어 신뢰성이 깨진** 가장 위험한 방식을 증명했다.

• **Synchronized**: 데이터 정합성은 보장하지만, **"비생산적 점유"**라는 심각한 자원 낭비 문제를 야기했다.

• **ReentrantLock**: '공정성(Fairness)' 옵션을 통해, 약간의 평균 성능을 희생하는 대신 **시스템의 안정성(p99)을 크게 향상시킬 수 있는** 선택지를 제공했다.

• **Semaphore**: **허가(Permit) 기반으로 동시 접근 개수를 명시적으로 제어**하여, 시스템 자원을 효율적으로 관리하면서도 높은 처리량을 달성하는 완전히 다른 접근법을 제시했다.

### 6.2 Semaphore 선택의 핵심 근거

#### 6.2.1 성능 측면

1. **동시성 제어가 있는 방식 중 최고 성능** - 성공 케이스에서 2위 달성
2. **Synchronized 대비 15% 성능 향상** - 동일한 동시성 제어 수준에서 우수한 효율성
3. **ReentrantLock 대비 6배 성능 우위** - 압도적인 처리 속도 차이
4. **예측 가능한 성능** - 대기시간 없는 즉시 처리로 응답시간 일관성 확보

#### 6.2.2 아키텍처 측면

1. **Lock-Free 알고리즘의 본질적 우위** - 컨텍스트 스위칭 오버헤드 제거
2. **리소스 기반 제어** - 명확한 동시 접근 한계 설정으로 시스템 안정성 보장
3. **빠른 실패 처리** - 시스템 과부하 시 즉각적인 요청 거부로 전체 안정성 유지

### 6.3 최종 결론

**성능적인 백엔드 시스템 설계는 특정 기술의 속도를 넘어, 문제의 본질(Use Case)을 파악하고, 각 기술이 가진 명확한 트레이드오프를 데이터에 기반하여 이해하며, 주어진 상황에 가장 적합한 아키텍처를 선택하는 공학적 의사결정 능력에 달려있다.**

본 분석을 통해 Semaphore는 **리소스 제한이 필요한 고부하 환경에서 안정성과 성능의 균형을 달성하는 핵심 요소**로서, 전통적인 동시성 제어 방식을 뛰어넘는 새로운 패러다임의 해법임을 데이터로 증명했다.

**Semaphore 사용을 권장하는 경우:**
- 동시 접근 제어가 필요하면서도 성능이 중요한 상황
- 시스템 부하 제어(Throttling)가 필요한 고트래픽 환경
- 예측 가능한 응답시간이 요구되는 실시간 시스템
- 허가 기반의 직관적인 리소스 관리가 필요한 경우

이 테스트 결과는 Semaphore가 실용적인 동시성 제어와 성능 사이의 최적 균형점을 제공함을 보여준다.