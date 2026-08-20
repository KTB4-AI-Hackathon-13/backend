#!/usr/bin/env bash
# 해커톤 백엔드 정적 검사 + 빌드 검증
#
# 사용법:  bash .claude/skills/backend-qa-check/scripts/verify.sh [--no-build]
#
# 이 스크립트는 "의심 지점"을 좁혀줄 뿐이다. 각 항목은 실제 파일을 열어 확인해야 하며,
# 특히 [의심] 표시 항목은 오탐이 있을 수 있다.

set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

SRC="src/main/java/hackathon/app"
RES="src/main/resources/application.yaml"
RUN_BUILD=1
[ "${1:-}" = "--no-build" ] && RUN_BUILD=0

problems=0
section() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
ok()      { printf '  \033[32mOK\033[0m   %s\n' "$1"; }
warn()    { printf '  \033[33m의심\033[0m %s\n' "$1"; problems=$((problems+1)); }
bad()     { printf '  \033[31m문제\033[0m %s\n' "$1"; problems=$((problems+1)); }

if [ ! -d "$SRC" ]; then
  echo "소스 디렉토리를 찾을 수 없습니다: $SRC"; exit 1
fi

section "1. package 선언 ↔ 파일 경로 일치"
mismatch=0
while IFS= read -r f; do
  expected="hackathon.app$(dirname "${f#$SRC}" | tr '/' '.')"
  expected="${expected%.}"
  declared=$(grep -m1 '^package ' "$f" | sed 's/^package \(.*\);.*/\1/')
  if [ "$declared" != "$expected" ]; then
    bad "$f : package '$declared' ≠ 경로 '$expected'"
    mismatch=1
  fi
done < <(find "$SRC" -name '*.java')
[ $mismatch -eq 0 ] && ok "모든 package 선언이 경로와 일치"

section "2. 패키지 구조 규칙 (도메인/계층)"
allowed="entity enums repository service controller dto domain application infrastructure presentation api error"
struct_bad=0
while IFS= read -r d; do
  rel="${d#$SRC/}"
  depth=$(awk -F'/' '{print NF}' <<< "$rel")
  leaf=$(basename "$d")
  if [ "$depth" -eq 2 ] && ! grep -qw "$leaf" <<< "$allowed"; then
    warn "허용되지 않은 서브패키지: $rel  (허용: entity/enums/repository/service/controller/dto)"
    struct_bad=1
  fi
done < <(find "$SRC" -mindepth 1 -type d)
[ $struct_bad -eq 0 ] && ok "서브패키지 이름 규칙 준수"

section "3. 기동 실패 유발 항목"
if [ -f "$RES" ]; then
  if grep -q 'datasource' "$RES"; then
    ok "application.yaml 에 datasource 설정 있음"
  elif find "$SRC" -name '*.java' -exec grep -l '@Entity' {} + >/dev/null 2>&1; then
    bad "@Entity 가 있는데 application.yaml 에 datasource 설정이 없음 → 기동 실패"
  else
    ok "엔티티가 없어 datasource 불필요"
  fi
else
  warn "application.yaml 을 찾을 수 없음"
fi

# 파생 쿼리 메서드명 ↔ 엔티티 필드 대조
derived_bad=0
while IFS= read -r repo; do
  domain_dir=$(dirname "$(dirname "$repo")")
  fields=$(grep -hoE 'private[[:space:]]+[A-Za-z0-9_<>,.]+[[:space:]]+[a-zA-Z0-9_]+[[:space:]]*;' "$domain_dir"/entity/*.java 2>/dev/null \
           | sed -E 's/.*[[:space:]]([a-zA-Z0-9_]+)[[:space:]]*;/\1/' | sort -u)
  [ -z "$fields" ] && continue
  while IFS= read -r m; do
    # findByXxx / findAllByOrderByXxxDesc 등에서 필드 후보 토큰 추출
    tokens=$(sed -E 's/^(find|count|delete|exists)(All)?(By)?//; s/(Asc|Desc)$//' <<< "$m" \
             | sed -E 's/OrderBy/\n/g; s/(And|Or)/\n/g' | sed -E 's/(Asc|Desc)$//' | grep -v '^$')
    for t in $tokens; do
      lower="$(tr '[:upper:]' '[:lower:]' <<< "${t:0:1}")${t:1}"
      if ! grep -qx "$lower" <<< "$fields"; then
        bad "$repo : 파생 쿼리 '$m' 의 '$t' 가 엔티티 필드에 없음 → 기동 시 PropertyReferenceException"
        derived_bad=1
      fi
    done
    # @Query 가 붙은 메서드는 파생 쿼리가 아니므로 이름 대조 대상에서 제외한다.
    # JPQL 본문(""" 텍스트 블록) 안의 count( 같은 문자열을 메서드로 오인하지 않도록 본문 전체를 건너뛴다.
  done < <(awk '
      /@Query/ { skip=1; if ($0 ~ /"""/) inq=1; next }
      /"""/    { inq = !inq; next }
      inq      { next }
      match($0, /(find|count|delete|exists)[A-Za-z0-9]*\(/) {
        if (skip) { skip=0; next }
        print substr($0, RSTART, RLENGTH-1)
      }' "$repo" | sort -u)
done < <(find "$SRC" -path '*/repository/*.java')
[ $derived_bad -eq 0 ] && ok "Repository 파생 쿼리 메서드명이 엔티티 필드와 일치"

section "4. 조용히 동작을 깨뜨리는 항목"
# @Valid 누락
valid_bad=0
while IFS= read -r c; do
  if grep -qE '@RequestBody' "$c" && ! grep -qE '@Valid[[:space:]]+@RequestBody|@RequestBody[[:space:]]+@Valid' "$c"; then
    reqs=$(grep -oE '@RequestBody[[:space:]]+[A-Za-z0-9_]+' "$c" | awk '{print $2}' | sort -u)
    for r in $reqs; do
      if grep -rqE '@(NotBlank|NotNull|Size|Positive|PositiveOrZero|Min|Max|Email|Pattern)' \
           "$(dirname "$(dirname "$c")")/dto/$r.java" 2>/dev/null; then
        bad "$c : $r 에 검증 애노테이션이 있는데 @RequestBody 에 @Valid 가 없음 → 검증이 무시됨"
        valid_bad=1
      fi
    done
  fi
done < <(find "$SRC" -path '*/controller/*.java')
[ $valid_bad -eq 0 ] && ok "@Valid 누락 없음"

# @Transactional 누락 (쓰기 메서드 의심)
tx_bad=0
while IFS= read -r s; do
  if grep -qE '\.(save|delete|deleteById|saveAll)\(' "$s" && ! grep -q '@Transactional' "$s"; then
    bad "$s : save/delete 를 호출하는데 @Transactional 이 하나도 없음"
    tx_bad=1
  fi
done < <(find "$SRC" -path '*/service/*.java')
[ $tx_bad -eq 0 ] && ok "Service 트랜잭션 애노테이션 존재"

section "5. 금지 패턴"
check_forbidden() {  # $1=패턴  $2=설명  $3=경로글롭(선택)
  hits=$(grep -rnE "$1" "$SRC" 2>/dev/null | grep -v '^[[:space:]]*//' || true)
  if [ -n "$hits" ]; then
    bad "$2"
    sed 's/^/         /' <<< "$hits" | head -5
  fi
}
check_forbidden 'Map[[:space:]]*<[[:space:]]*String[[:space:]]*,[[:space:]]*Object[[:space:]]*>' '응답/파라미터에 Map<String,Object> 사용 (DTO record 를 쓸 것)'
check_forbidden '@Autowired' '@Autowired 필드 주입 (생성자 주입을 쓸 것)'
check_forbidden 'EntityManager|JdbcTemplate' 'EntityManager/JdbcTemplate 직접 사용 (JpaRepository 를 쓸 것)'
check_forbidden 'class[[:space:]]+[A-Za-z0-9_]+ServiceImpl' 'ServiceImpl 패턴 (구현체가 1개면 인터페이스를 두지 말 것)'
check_forbidden 'import[[:space:]]+org\.mapstruct|com\.querydsl|reactor\.core' '금지 라이브러리 (MapStruct/QueryDSL/Reactor)'
check_forbidden 'ApplicationEventPublisher' '이벤트 드리븐 (해커톤 범위에서 추적 불가)'
check_forbidden 'catch[[:space:]]*\([[:space:]]*Exception[[:space:]]+[a-z]+[[:space:]]*\)[[:space:]]*\{[[:space:]]*\}' '빈 catch 블록 (예외 삼킴)'

# 컨트롤러가 엔티티를 그대로 반환하는지
entity_ret=0
while IFS= read -r c; do
  domain=$(basename "$(dirname "$(dirname "$c")")")
  for e in $(find "$SRC/$domain/entity" -name '*.java' 2>/dev/null -exec basename {} .java \;); do
    if grep -qE "public[[:space:]]+(ResponseEntity<)?${e}([[:space:]]|>)" "$c"; then
      bad "$c : 엔티티 $e 를 응답으로 반환 (Response DTO 를 쓸 것 — 직렬화 무한루프/LAZY 예외 위험)"
      entity_ret=1
    fi
  done
done < <(find "$SRC" -path '*/controller/*.java')
[ $entity_ret -eq 0 ] && ok "컨트롤러가 엔티티를 직접 반환하지 않음"

# 폐기된 테스트 애노테이션
if grep -rn '@MockBean' src/test 2>/dev/null | grep -q .; then
  bad "@MockBean 사용 — Spring Boot 4.x 에서 제거됨. @MockitoBean 을 쓸 것"
  grep -rn '@MockBean' src/test | sed 's/^/         /' | head -5
else
  ok "테스트 애노테이션 정상 (@MockBean 미사용)"
fi

section "6. 메서드 길이 (30줄 초과)"
long_found=0
while IFS= read -r f; do
  awk -v file="$f" '
    # 클래스/인터페이스/enum/record 선언은 메서드가 아니다
    /^[[:space:]]*(public|private|protected).*\{[[:space:]]*$/ {
      if ($0 ~ /(class|interface|enum|record)[[:space:]]/) next
      start=NR; name=$0; depth=1; next
    }
    start && /\{/ { depth++ }
    start && /\}/ { depth--; if (depth==0) { if (NR-start > 30) { gsub(/^[[:space:]]+|[[:space:]]+$/,"",name); printf "%s:%d  (%d줄)  %s\n", file, start, NR-start, name }; start=0 } }
  ' "$f"
done < <(find "$SRC" -name '*.java') > /tmp/_longmethods.txt
if [ -s /tmp/_longmethods.txt ]; then
  warn "30줄을 넘는 메서드가 있음 (private 메서드로 쪼갤 것)"
  sed 's/^/         /' /tmp/_longmethods.txt | head -10
else
  ok "모든 메서드가 30줄 이하"
fi
rm -f /tmp/_longmethods.txt

if [ $RUN_BUILD -eq 1 ]; then
  section "7. 빌드 (CI 와 동일: ./gradlew build)"
  if ./gradlew build --no-daemon -q > /tmp/_build.log 2>&1; then
    ok "./gradlew build 통과"
  else
    bad "./gradlew build 실패 — 아래는 마지막 30줄"
    tail -30 /tmp/_build.log | sed 's/^/         /'
  fi
  rm -f /tmp/_build.log
fi

section "결과"
if [ $problems -eq 0 ]; then
  printf '  \033[32m정적 검사 통과 — 이제 실제 기동 + curl 왕복 검증을 진행하라.\033[0m\n\n'
else
  printf '  \033[31m%d건의 문제/의심 항목. 각 항목의 파일을 직접 열어 확인하라.\033[0m\n\n' "$problems"
fi
exit 0
