package com.chatservice.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.chatservice.auth.exception.handler.JwtAccessDeniedHandler;
import com.chatservice.auth.exception.handler.JwtAuthenticationFailureHandler;
import com.chatservice.auth.filter.customfilter.IndexFilter;
import com.chatservice.auth.filter.customfilter.JwtAuthProcessorFilter;
import com.chatservice.auth.filter.customfilter.LoginFilter;
import com.chatservice.auth.filter.util.CookieUtil;
import com.chatservice.auth.filter.util.JWTUtil;
import com.chatservice.auth.provider.UserAuthenticationProvider;
import com.chatservice.auth.repository.UserEntityRepository;
import com.chatservice.auth.userdetailservice.UserEntityDetailService;
import com.chatservice.redis.handler.RedisHandler;



@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final UserEntityDetailService userEntityDetailService;
    private final UserEntityRepository userEntityRepository;
    private final JWTUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final RedisHandler redisHandler;

    public SecurityConfig(
    		
        AuthenticationConfiguration authenticationConfiguration,
        UserEntityDetailService userEntityDetailService,
        UserEntityRepository userEntityRepository,
        JWTUtil jwtUtil,
        CookieUtil cookieUtil,
        RedisHandler redisHandler
    ) {
        this.authenticationConfiguration = authenticationConfiguration;
        this.userEntityDetailService = userEntityDetailService;
        this.userEntityRepository = userEntityRepository;
        this.jwtUtil = jwtUtil;
        this.cookieUtil = cookieUtil;
        this.redisHandler = redisHandler;
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {    
        return new BCryptPasswordEncoder();
    }
    @Bean
    public UserAuthenticationProvider userAuthenticationProvider() {
        return new UserAuthenticationProvider(
            userEntityDetailService,
            userEntityRepository,
            bCryptPasswordEncoder()
        );
    }
    @Bean   
    public AuthenticationManager authenticationManager() throws Exception {
        AuthenticationManager authenticationManager = authenticationConfiguration.getAuthenticationManager();
        ((ProviderManager) authenticationManager).getProviders().add(userAuthenticationProvider());
        return authenticationManager;
    }
    /* 초기 페이지 */
    @Bean
    public SecurityFilterChain indexFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/")
        	.authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new IndexFilter(cookieUtil, jwtUtil, redisHandler), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    /* [회원관리 서비스 - 로그인] : 로그인 요청 처리를 처리 - Post 한정임 */
    @Bean
    public SecurityFilterChain loginFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/login")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new LoginFilter(authenticationManager(), redisHandler, jwtUtil), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    /* [회원관리 서비스 - 로그 아웃] : Spring MVC Controller 아닌 Spring Security Handler 적용. */
    @Bean
    public SecurityFilterChain logoutFilterChain(HttpSecurity http, CookieLogoutHandler cookieLogoutHandler) throws Exception {
        http.securityMatcher("/logout")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .addLogoutHandler(cookieLogoutHandler)
                .logoutSuccessUrl("/")
            );  return http.build();
    }
    /* [회원관리 서비스 - 모든 요청] : Spring MVC Controller 아닌 Spring Security Handler 적용. */
    @Bean
    public SecurityFilterChain membersServiceFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/members/**")
        .authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST,  "/members/edit").authenticated()
				.requestMatchers(HttpMethod.DELETE,  "/members/edit").authenticated()
				.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, redisHandler), UsernamePasswordAuthenticationFilter.class)
	        .exceptionHandling(exception ->
		        exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler()) // JWT 인증 실패 시 실행될 핸들러 등록
		        		 .accessDeniedHandler(new JwtAccessDeniedHandler()));               // 인가 실패 처리
        return http.build();
    }
    
    /* 방 목록 페이지와 실제 방 목록 페이지 내 포함될 방 목록 데이터를 요청할 때 인증이 필요함 */
    @Bean
    public SecurityFilterChain roomListFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
        					"/rooms",
        					"/api/rooms",
        					"/rooms/new",
        					"/rooms/{roomId}"
        					)
        					/*	/rooms : 방 목록을 표현하는 페이지
								/api/rooms : 방 목록 페이지 내 실제 포함될 실제 방 목록 전체 데이터 요청 받는 api 지점,
								/rooms/new : 새로운 방 생성 관련 요청
								  - GET  : 방 생성 페이지 요청
								  - POST : 실제 방 생성 전, 즉 WebSocket 전 단계로 실제 방 생성이 아닌 대기열 방 목록 요청
        					*/
        .authorizeHttpRequests(auth -> auth
				.anyRequest().authenticated())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, redisHandler), UsernamePasswordAuthenticationFilter.class)
	        .exceptionHandling(exception ->
		        exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler()) 	// JWT 인증 실패 시 실행될 핸들러 등록
		        		 .accessDeniedHandler(new JwtAccessDeniedHandler()));               // 인가 실패 처리
        return http.build();
    }
    
    @Bean
    public SecurityFilterChain chatWebSocketFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/chat", "/chat/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, redisHandler), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler())
                         .accessDeniedHandler(new JwtAccessDeniedHandler()));
        return http.build();
    }

    /* [게시글 서비스 - 모든 요청] : 각 요청 경로 및 HTTP Method 별 권한 필요한 위치 설정 */
//    @Bean
//    public SecurityFilterChain boardServcieFilterChain(HttpSecurity http) throws Exception {
//        http.securityMatcher("/boards/**")
//            .authorizeHttpRequests(auth -> auth
//                .requestMatchers(            
//                    "/boards/new",            // 게시글 작성 페이지 (GET /boards/new)
//                    "/boards/",               // 게시글 작성 요청 (POST /boards)
//                    "/boards/*/auth",         // 인가 요청 (GET /boards/{id}/auth)
//                    "/boards/*/edit",         // 게시글 수정 페이지 요청 (GET /boards/edit?boardId=)
//                    "/boards/comments"        // 댓글 작성 (POST /boards/comments)
//                ).authenticated()
//                .requestMatchers(HttpMethod.POST, "/boards/{id}").authenticated()
//                .requestMatchers(HttpMethod.DELETE, "/boards/{id}").authenticated()
//                .anyRequest().permitAll()
//            )
//            .csrf(csrf -> csrf.disable())
//            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//            .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, redisHandler), UsernamePasswordAuthenticationFilter.class)
//            .exceptionHandling(exception ->
//                exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler())  // JWT 인증 실패 시 실행될 핸들러 등록
//                         .accessDeniedHandler(new JwtAccessDeniedHandler())                // 인가 실패 처리
//        );
//        
//        // CSP(Content Security Policy) 설정 업데이트
//        http.headers(headers -> headers
//		.contentSecurityPolicy(csp -> csp
//	            .policyDirectives(
//	                "default-src 'self'; " +
//	                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://ajax.googleapis.com https://kit.fontawesome.com; " +
//	                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
//	                "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net https://kit.fontawesome.com https://ka-f.fontawesome.com; " +
//	                "img-src 'self' data:; " +
//	                "connect-src 'self' https://ka-f.fontawesome.com https://cdn.jsdelivr.net; " +
//	                "frame-ancestors 'self'; " +
//	                "worker-src 'self'; " +
//	                "object-src 'none';"
//	            )
//	    ));  return http.build();
//    } 
}
