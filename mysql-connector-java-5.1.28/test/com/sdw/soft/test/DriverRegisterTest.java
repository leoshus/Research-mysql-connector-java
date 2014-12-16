package com.sdw.soft.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

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
	
	private static final String URL = "jdbc:mysql://127.0.0.1:3306/test?useUnicode=true&characterEncoding=GBK&jdbcCompliantTruncation=false";
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
	
	/**
	 * TYPE_FORWARD_ONLY：缺省类型。只允许向前访问一次，并且不会受到其他用户对该数据库所作更改的影响。
	 * TYPE_SCROLL_INSENSITIVE：允许在列表中向前或向后移动，甚至可以进行特定定位，例如移至列表中的第四个记录或者从当前位置向后移动两个记录。
	 *		 不会受到其他用户对该数据库所作更改的影响。
	 * ResultSet.TYPE_SCROLL_SENSITIVE象 TYPE_SCROLL_INSENSITIVE一样，允许在记录中定位。这种类型受到其他用户所作更改的影响。
	 *	 	如果用户在执行完查询之后删除一个记录，那个记录将从 ResultSet中消失。类似的，对数据值的更改也将反映在 ResultSet 中。
	 * 设置 ResultSet 的并发性，该参数确定是否可以更新 ResultSet。其选项有：
	 *  CONCUR_READ_ONLY：这是缺省值，指定不可以更新
	 *	ResultSet CONCUR_UPDATABLE：指定可以更新 ResultSet
	 */
	@Test
	public void test03(){
		try {
			connection.setAutoCommit(false);
			PreparedStatement pstmt = connection.prepareStatement("insert into user (id,username,password,age,address,create_date)"
					+ " values(?,?,?,?,?,?)",ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_READ_ONLY);
			for( int i = 2;i < 4;i++){
				pstmt.setString(1, "4");
				pstmt.setString(2, "tony");
				pstmt.setString(3, "admin");
				pstmt.setString(4, "23");
				pstmt.setString(5, "beijing");
				pstmt.setString(6, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
				pstmt.addBatch();
			}
			int[] count = pstmt.executeBatch();
			if(count.length > 0 ){
				logger.info("插入成功!插入"+count.length+"条数据!");
			}
			connection.commit();
		} catch (Exception e) {
			try {
				connection.rollback();
				logger.info("回滚了!");
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
			logger.info(e.getMessage(),e);
		}
		
	}
	
	@Test
	public void test04(){

	}
}
