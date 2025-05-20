package com.chatservice.web.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chatservice.web.model.WebVO;

@Repository
public class WebRepository implements IWebRepository {

	JdbcTemplate jdbcTemplate;
	
	public WebRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	@Override
	public List<WebVO> webInfo() {
		
		String roomTotalsql = "SELECT * FROM rooms";
		return jdbcTemplate.query(roomTotalsql, new WebInfoRowMapper());
	}

	class WebInfoRowMapper implements RowMapper<WebVO>{

		@Override
		public WebVO mapRow(ResultSet rs, int rowNum) throws SQLException {
			
			WebVO webVO = new WebVO();
			
			webVO.setRomnumber(rs.getInt("ROOMNUMBER"));
			webVO.setRoomname(rs.getString("ROOMTITLE"));
			webVO.setCurrentpeople(rs.getInt("CURRENTPEOPLE"));
			webVO.setMaxpeople(rs.getInt("MAXPEOPLE"));
			
			return webVO;
		}
		
	}
}
