package com.chatservice.web.service;

import java.util.Map;

import org.springframework.security.core.Authentication;

public interface IWebService {

	public Map<String, Object> loadMainInfo(Authentication authentication);
}
