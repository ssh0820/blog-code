# AWS Aurora MySQL 병렬 쿼리

Aurora MySQL 병렬 쿼리는 데이터 집약적인 쿼리 처리에 관련된 I/O 및 계산의 일부를 병렬화하는 최적화이다.  
  
병렬화 작업에는 storage에서 행 검색, 열 값 추출, WHERE 절 및 조인 절의 조건과 일치하는 행 확인 등이 포함됩니다.

## 설정 방법

* 1.23 이상 또는 2.09 이상을 실행하는 Aurora MySQL DB 클러스터
* 클러스터의 DB 인스턴스는 db.r* 인스턴스 클래스
  * db.t2 또는 db.t3 에서는 사용 불가
  

```sql
mysql> select @@aurora_parallel_query;
+-------------------------+
| @@aurora_parallel_query |
+-------------------------+
|                       1 |
+-------------------------+

mysql> select @@aurora_disable_hash_join;
+----------------------------+
| @@aurora_disable_hash_join |
+----------------------------+
|                          0 |
+----------------------------+
```

### Hash Join

병렬 쿼리는 해시 조인 최적화에서 이익을 얻는 리소스 집약적인 쿼리 유형에 일반적으로 사용됩니다. 따라서 병렬 쿼리를 사용하려는 클러스터에 대해 해시 조인을 활성화하는 것이 좋습니다.

버전 1.23 이전의 Aurora MySQL 5.6 호환 클러스터인 경우 해시 조인은 병렬 쿼리가 활성화된 클러스터에서 항상 사용할 수 있습니다. 이 경우 해시 조인 기능에 대해 어떤 작업도 수행할 필요가 없습니다. 나중에 이러한 클러스터를 업그레이드하는 경우 그때 해시 조인을 활성화해야 합니다.

Aurora MySQL 1.23 이상 또는 2.09 이상에서는 병렬 쿼리 및 해시 조인 설정이 모두 기본적으로 해제되어 있습니다. 이러한 클러스터에 대해 병렬 쿼리를 활성화하는 경우 해시 조인도 활성화합니다. 이렇게 하는 가장 간단한 방법은 클러스터 구성 파라미터 aurora_disable_hash_join=OFF를 설정하는 것입니다. 해시 조인을 활성화하고 효과적으로 사용하는 방법에 대한 자세한 내용은 Aurora MySQL에서 해시 조인 작업 단원을 참조하세요.

## 병렬 쿼리 확인 (실행계획)

아래처럼 Extra 항목에 `Using parallel query` 가 나와야만 병렬쿼리가 정확히 적용된 쿼리입니다.

### 실행 계획 분석

```sql
explain select p_name, p_mfgr from part
    where p_brand is not null
    and upper(p_type) is not null
    and round(p_retailprice) is not null;

+----+-------------+-------+...+----------+----------------------------------------------------------------------------+
| id | select_type | table |...| rows     | Extra                                                                      |
+----+-------------+-------+...+----------+----------------------------------------------------------------------------+
|  1 | SIMPLE      | part  |...| 20427936 | Using where; Using parallel query (5 columns, 1 filters, 2 exprs; 0 extra) |
+----+-------------+-------+...+----------+----------------------------------------------------------------------------+
```

* `5 columns`
  * 쿼리 전체에 사용된 컬럼
* `1 filters`
  * `p_brand is not null`
  * 특별한 함수 없이 사용된 조건문
* `2 exprs`
  * `and upper(p_type) is not null`, `and round(p_retailprice) is not null;` 로 함수로 이루어진 경우

## 병렬 쿼리가 안되는 케이스

* 파티셔닝 테이블에는 사용되지 않는다.
* `EXPLAIN` (실행계획) 결과에서 `key` (적용된 인덱스명) 이 있을 경우 인덱스를 통해 효율적으로 실행되는 것을 의미해서 병렬쿼리가 사용될 **가능성이 낮다**.
  * 즉, 실행계획상 인덱스가 적용되는게 확인되면, 병렬쿼리가 작동안될수 있다.
* `EXPLAIN` (실행계획) 결과에서 `rows`가 작을 경우 (`rows` 값이 수백만이 아닐 경우) 병렬쿼리가 사용되지 않는다.
* `where` 절이 선언되어있지 않으면 사용되지 않는다.
* `limit` 절에서는 사용되지 않는다.