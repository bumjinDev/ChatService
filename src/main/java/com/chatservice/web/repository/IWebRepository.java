package com.chatservice.web.repository;

import java.util.List;
import com.chatservice.web.model.WebVO;

public interface IWebRepository {
	
	public List<WebVO> webInfo();
}
