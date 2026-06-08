# 미니PC 적용 런북 (TOYCHAT 계정 · DDL 적재 · systemd 환경변수 · 재기동 검증)

`chatservice` 가 부팅 중 `ORA-01017` 로 죽는 원인은 (1) 미니PC Oracle에 `TOYCHAT` 계정/스키마가 없고, (2) systemd에 `ORACLE_PASSWORD`/`REDIS_PASSWORD` 가 주입되지 않아 `${...}` 가 빈 값으로 치환되기 때문이다. 아래 순서대로 미니PC에서 실행한다.

> 사전: 아래 `<...>` 자리표시자를 실제 값으로 바꾼다. **`<TOYCHAT_비밀번호>` 는 SQL의 계정 비밀번호와 systemd의 `ORACLE_PASSWORD` 가 반드시 동일**해야 한다. `<REDIS_비밀번호>` 는 미니PC Redis `requirepass` 값과 동일해야 한다.
>
> 파일 전송: 이 폴더(`db/`)의 `00_create_toychat_user.sql`, `ddl_toychat.sql` 을 미니PC로 복사(scp 등)해 두고 진행한다.

## 1) Oracle TOYCHAT 계정 생성 (SYSTEM 으로 1회)

```bash
# 미니PC에서 SYSTEM 접속 후 계정 생성 스크립트 실행
sqlplus system/<SYSTEM_비밀번호>@127.0.0.1:1521/xe @00_create_toychat_user.sql
```

또는 수동으로:

```sql
CREATE USER TOYCHAT IDENTIFIED BY "<TOYCHAT_비밀번호>";
GRANT CONNECT, RESOURCE TO TOYCHAT;
ALTER USER TOYCHAT QUOTA UNLIMITED ON USERS;
```

> 19c+ CDB/PDB 구조면 `ALTER SESSION SET CONTAINER=<PDB명>;` 로 PDB에 들어가서 생성(공통계정 `C##` 회피).

## 2) 스키마(DDL) 적재 (TOYCHAT 으로)

```bash
sqlplus TOYCHAT/<TOYCHAT_비밀번호>@127.0.0.1:1521/xe @ddl_toychat.sql

# 검증: 4개 테이블 확인
sqlplus -s TOYCHAT/<TOYCHAT_비밀번호>@127.0.0.1:1521/xe <<'SQL'
SET HEADING ON
SELECT TABLE_NAME FROM USER_TABLES
 WHERE TABLE_NAME IN ('USERENTITY','USERENTITY_ROLES','MEMBERTBL','CHAT_ROOM_CREATION_QUEUE')
 ORDER BY TABLE_NAME;
EXIT
SQL
```

기대 출력: `CHAT_ROOM_CREATION_QUEUE`, `MEMBERTBL`, `USERENTITY`, `USERENTITY_ROLES` 4행.

## 3) systemd 환경변수 주입

`application.yml` 이 `${ORACLE_PASSWORD}`/`${REDIS_PASSWORD}` 를 참조하므로 둘을 systemd에 넣는다. **권장: EnvironmentFile** (유닛 파일·`systemctl show` 노출 최소화).

### 3-A) EnvironmentFile 방식 (권장)

```bash
# 1. 시크릿 파일 작성 (root)
sudo tee /etc/chatservice.env >/dev/null <<'EOF'
ORACLE_PASSWORD=<TOYCHAT_비밀번호>
REDIS_PASSWORD=<REDIS_비밀번호>
EOF

# 2. 권한 잠그기 (서비스 실행 계정만 읽도록)
sudo chmod 600 /etc/chatservice.env
# 서비스가 비-root 계정으로 돈다면 소유자도 맞춘다 (예: chatservice 계정)
# sudo chown chatservice:chatservice /etc/chatservice.env

# 3. 유닛에 EnvironmentFile 연결 (override 사용 — 원본 유닛 안 건드림)
sudo systemctl edit chatservice
```

`systemctl edit` 편집기에 아래만 추가하고 저장:

```ini
[Service]
EnvironmentFile=/etc/chatservice.env
```

### 3-B) 인라인 Environment 방식 (대안)

```bash
sudo systemctl edit chatservice
```

```ini
[Service]
Environment=ORACLE_PASSWORD=<TOYCHAT_비밀번호>
Environment=REDIS_PASSWORD=<REDIS_비밀번호>
```

## 4) 재기동 및 검증

```bash
sudo systemctl daemon-reload
sudo systemctl reset-failed chatservice
sudo systemctl restart chatservice

# 로그 팔로우: 아래 두 줄이 보이면 정상 기동
sudo journalctl -u chatservice -f
#   → "Tomcat started on port 8186"
#   → "Started ChatServiceApplication"

# 포트 LISTEN 확인 (다른 셸에서)
sudo ss -tlnp | grep 8186
```

## 5) 환경변수 주입 확인 (선택, 디버그용)

```bash
# 서비스가 실제로 받은 환경에 두 변수가 있는지 (값은 가려서 길이만)
PID=$(systemctl show -p MainPID --value chatservice)
sudo tr '\0' '\n' < /proc/$PID/environ | grep -E '^(ORACLE|REDIS)_PASSWORD=' | sed 's/=.*/=<set>/'
```

## 트러블슈팅

- **다시 `ORA-01017`** : `ORACLE_PASSWORD` 값 ≠ TOYCHAT 실제 비밀번호. 1)의 비밀번호와 3)의 값을 일치시키고 `daemon-reload` → `restart`. 특수문자 포함 시 `EnvironmentFile` 한 줄에 따옴표 없이 그대로(systemd는 값 전체를 그대로 읽음).
- **`ORA-00942`(table or view does not exist) / `ORA-00904`(invalid identifier)** : 식별자 케이스/따옴표 문제. DDL의 식별자에 큰따옴표를 쓰지 않았는지 확인(`ddl_toychat.sql` 주석의 식별자 규칙 참고). 대문자 그대로 두면 된다.
- **Redis `NOAUTH`/연결 거부** : `REDIS_PASSWORD` 가 `requirepass` 와 불일치. `redis-cli -a "<REDIS_비밀번호>" ping` 으로 먼저 검증.
- **기동은 됐는데 8186 LISTEN 없음** : context-path 는 `/ChatService` 이므로 접속 URL은 `http://<host>:8186/ChatService`. 포트/컨텍스트는 변경 금지.

> 참고: 이 런북은 미니PC에서 실행할 "그대로 붙여넣는" 절차서다. 원격 미니PC 접속 권한이 이 작업 환경엔 없어 명령 실행까지는 대행하지 못하고, 검증된 명령 세트만 제공한다.
