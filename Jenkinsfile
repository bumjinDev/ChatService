pipeline {
    agent any

    tools {
        jdk 'JDK21'
        gradle 'Gradle-latest'
    }

    environment {
        APP_NAME    = 'chatservice'        // systemd 서비스명과 일치
        PROJECT_DIR = '.'                  // 프로젝트(build.gradle)가 repo 루트에 있으므로 '.'
        DEPLOY_DIR  = '/opt/chatservice'
        WAR_NAME    = 'ChatService.war'    // systemd ExecStart의 파일명과 일치
        APP_PORT    = '8186'               // application.yml의 server.port
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        retry(1)
        timestamps()
    }

    // Git Clone 단계 제거:
    //   'Pipeline script from SCM'이 잡의 Branch Specifier(*/main)로
    //   이미 repo를 워크스페이스에 체크아웃한다. 별도 clone은 중복이고
    //   브랜치를 두 번 지정하게 되어 불일치 시 깨진다.

    stages {
        stage('Setup Build Environment') {
            steps {
                dir("${PROJECT_DIR}") {
                    sh 'chmod +x ./gradlew'
                    sh './gradlew --version'
                }
            }
        }

        stage('Unit Test') {
            steps {
                dir("${PROJECT_DIR}") {
                    // build.gradle의 test 태스크는 @Tag("integration")을 제외하므로 DB·Redis 없이 단위 테스트만 돈다
                    sh './gradlew clean test'
                }
            }
        }

        stage('Build WAR') {
            steps {
                dir("${PROJECT_DIR}") {
                    // bootWar만 실행해 실행용 WAR 하나만 만든다(build를 쓰면 -plain.war도 생긴다)
                    sh './gradlew bootWar'
                    sh 'ls -lah build/libs/*.war'
                }
            }
        }

        stage('Deploy') {
            steps {
                echo '=== Deploy ==='
                // 버전 붙은 산출물명을 고정명(ChatService.war)으로 복사 -> systemd가 이 경로를 실행
                // ProcessTreeKiller 회피 + 부팅 자동기동: 직접 java 실행이 아니라 systemd 재시작
                sh '''
                    WAR=$(find "$PROJECT_DIR/build/libs" -maxdepth 1 -name '*.war' ! -name '*-plain.war')
                    [ "$(echo "$WAR" | grep -c .)" -eq 1 ] || { echo "실행용 WAR가 정확히 1개가 아니다: $WAR"; exit 1; }
                    sudo mkdir -p "$DEPLOY_DIR"
                    sudo cp "$WAR" "$DEPLOY_DIR/$WAR_NAME"
                    sudo systemctl restart "$APP_NAME"
                '''
                echo '=== Deploy Done ==='
            }
        }

        stage('Health Check') {
            steps {
                // 5초 간격으로 최대 120초 동안 확인한다. 메인 화면과 공개 상품 API가 모두 200이어야 성공이다.
                // /api/products는 새 테이블을 조회하므로 운영 DB에 DDL이 없으면 여기서 실패한다.
                sh '''
                    for i in $(seq 1 24); do
                        main=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$APP_PORT/ChatService/" || true)
                        api=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$APP_PORT/ChatService/api/products" || true)
                        echo "health check $i/24: main=$main api=$api"
                        if [ "$main" = "200" ] && [ "$api" = "200" ]; then exit 0; fi
                        sleep 5
                    done
                    echo "health check failed: 120초 안에 200 응답을 받지 못했다"
                    exit 1
                '''
                sh 'sudo systemctl is-active "$APP_NAME"'
            }
        }
    }

    post {
        success { echo 'ChatService Build and Deploy SUCCESS' }
        failure { echo 'ChatService Build or Deploy FAILED' }
    }
}