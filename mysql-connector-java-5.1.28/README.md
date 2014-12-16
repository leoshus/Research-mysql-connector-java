###MySQL connector java 5.1.28
-----------------------------
###JDBC方式访问Database
-------------------
作为一个web码农，在我刚入门那会、做点儿小应用一般需要访问数据库的时候，都会写下如下代码

```
Class.forName("com.mysql.jdbc.Driver");
connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
PreparedStatement pstmt = connection.prepareStatement("select * from user");
	ResultSet rs = pstmt.executeQuery();
	if(rs.next()){
		logger.info("username:{}",rs.getString("username"));
	}
	亦或是
	PreparedStatement pstmt = connection.prepareStatement("insert into user (id,username,password,age,address,create_date) values('1','tony','admin','23','beijing',now())");
	int count = pstmt.executeUpdate();
	if(count > 0){
		logger.info("插入成功!插入"+count+"条数据!");
	}		
```
上述代码实际包含了两个步骤

####步骤一:装载驱动类Driver
-------------------------

* 猛戳[这里](https://github.com/sdw2330976/Research-mysql-connector-java/tree/master/mysql-connector-java-5.1.28/mydoc/registerDriver.md)

####步骤二:根据Url、username、password获取connection对象
-----------------------------------------------------

* 猛戳[这里](https://github.com/sdw2330976/Research-mysql-connector-java/tree/master/mysql-connector-java-5.1.28/mydoc/fetchConnection.md)

###Socket 创建
--------------
* 猛戳[这里](https://github.com/sdw2330976/Research-mysql-connector-java/tree/master/mysql-connector-java-5.1.28/mydoc/createSocket.md)

***********************************************************************************************************************
准备工作搞定 下面开始我们的CRUD

###Statement与ResultSet的创建
---------------------------

* 猛戳[这里](https://github.com/sdw2330976/Research-mysql-connector-java/tree/master/mysql-connector-java-5.1.28/mydoc/statement_resultset.md)
