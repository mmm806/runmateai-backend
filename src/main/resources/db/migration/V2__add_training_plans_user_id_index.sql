-- V2: training_plans.user_id 인덱스 추가
--
-- 배경: PlanRepository의 쿼리 메서드들을 EXPLAIN ANALYZE로 실측 확인한 결과,
-- is_active=true 조건이 있는 조회(가장 빈번하게 호출되는 경로)는 이미
-- one_active_plan_per_user 부분 유니크 인덱스(V1)로 충분히 커버되고 있었다.
--
-- 반면 is_active 조건이 없는 조회(findFirstByUserOrderByCreatedAtAsc, 기록 삭제 시
-- 최초 플랜을 재활성화하는 예외 경로에서 사용)는 어떤 인덱스도 타지 못하고
-- Seq Scan으로 처리되고 있음을 확인했다.

-- 유니크 인덱스와 역할이 겹치지 않는 단순 user_id 인덱스만 추가한다.
CREATE INDEX idx_training_plans_user_id ON training_plans USING btree (user_id);