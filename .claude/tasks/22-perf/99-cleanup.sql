-- 측정이 끝나면 실행. scratch 스키마와 안의 모든 것(테이블, 함수, 인덱스)을 지운다. public은 영향 없음.
DROP SCHEMA IF EXISTS perf_test CASCADE;
