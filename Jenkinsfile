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

        stage('Compile Test') {
            steps {
                dir("${PROJECT_DIR}") {
                    sh './gradlew compileJava'
                }
            }
        }

        stage('Build WAR') {
            steps {
                dir("${PROJECT_DIR}") {
                    // build.gradle에 war 플러그인이 적용돼 있어야 bootWar 존재
                    sh './gradlew clean bootWar'
                    sh 'ls -lah build/libs/*.war'
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    echo "=== Deploy ==="
                    sh "sudo mkdir -p ${DEPLOY_DIR}"
                    // 버전 붙은 산출물명을 고정명(ChatService.war)으로 복사 -> systemd가 이 경로를 실행
                    sh "sudo cp \$(ls ${PROJECT_DIR}/build/libs/*.war | head -1) ${DEPLOY_DIR}/${WAR_NAME}"
                    // ProcessTreeKiller 회피 + 부팅 자동기동: 직접 java 실행이 아니라 systemd 재시작
                    sh "sudo systemctl restart ${APP_NAME}"
                    echo "=== Deploy Done ==="
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    sleep(20)
                    sh "sudo systemctl is-active ${APP_NAME}"
                    sh "sudo netstat -tlnp | grep :${APP_PORT} || echo 'port ${APP_PORT} not listening'"
                    sh """
                        code=\$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:${APP_PORT}/ChatService/ || echo 000)
                        echo "HTTP \$code (000이면 미응답, 그 외면 컨텍스트 기동됨)"
                    """
                }
            }
        }
    }

    post {
        success { echo 'ChatService Build and Deploy SUCCESS' }
        failure { echo 'ChatService Build or Deploy FAILED' }
    }
}