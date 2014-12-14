package com.sdw.soft.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Sonicery_D
 * @date 2014年12月14日
 * @version 1.0.0
 * @description
 **/
public class DriverRegisterTest {

	private static final Logger logger = LoggerFactory.getLogger(DriverRegisterTest.class);
	
	private static final String URL = "jdbc:mysql://127.0.0.1:3306/test";
	private static final String USERNAME = "root";
	private static final String PASSWORD = "root";
	
	private Connection connection = null;
	@Before
	public void setup(){
		try {
			Class.forName("com.mysql.jdbc.Driver");
			connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("jdbc驱动注册失败,"+e.getMessage(),e);
		}
	}
	@Test
	public void test01(){
		try {
			PreparedStatement pstmt = connection.prepareStatement("select * from user");
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()){
				logger.info("username:{}",rs.getString("username"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void test02(){
		try {
			PreparedStatement pstmt = connection.prepareStatement("insert into user (id,username,password,age,address,create_date) values('1','tony','admin','23','beijing',now())");
			int count = pstmt.executeUpdate();
			if(count > 0){
				logger.info("插入成功!插入"+count+"条数据!");
			}
		} catch (SQLException e) {
			e.printStackTrace();
			logger.info("插入失败!"+e.getMessage(),e);
		}
	}
}
