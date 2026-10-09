package com.chatservice.marketplace.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 메인 화면(index.jsp)을 렌더링한다. 사용을 중단한 방 집계(WebController)를 대신한다.
 * 화면은 상품·판매·지갑·주문을 스크립트(js/market/app.js)로 그리며, 서버는 로그인 여부와 닉네임만 넘긴다.
 * 로그인 여부는 "/" 체인의 IndexFilter 가 넣은 인증 정보의 details(닉네임)로 판단한다.
 * 닉네임에는 어떤 값이든 올 수 있으므로 로그인 여부를 닉네임의 특정 값으로 나타내지 않고 따로 넘긴다.
 */
@Controller
public class MainPageController {

    @GetMapping("/")
    public String loadMainPage(Authentication authentication, Model model) {
        String nickname = (authentication != null && authentication.getDetails() instanceof String value) ? value : null;
        model.addAttribute("loggedIn", nickname != null);
        model.addAttribute("userName", nickname != null ? nickname : "");
        return "index";
    }
}
