到目前为止,我们已经建立了Connection对象,接下来我们需要进行CRUD操作，就需要创建Statement对象，Statement有多种，我们常用的是PreparedStatement用于执行预编译好的SQL语句，CallableStatement用于调用数据库的存储过程
createStatement在`com.mysql.jdbc.ConnectionImpl`中存在三个重载方法。

```
public java.sql.Statement createStatement() throws SQLException 
public java.sql.Statement createStatement(int resultSetType,int resultSetConcurrency) throws SQLException
public java.sql.Statement createStatement(int resultSetType,int resultSetConcurrency, int resultSetHoldability)throws SQLException

```

我们常用的PreparedStatement在`com.mysql.jdbc.ConnectionImpl`中也存在多个重载方法。

```
/**
	 * sql不管是否带有输入参数 都将被预编译并保存在PreparedStatement对象中
	 * 在之后的使用的时候可以被高效地多次被执行
	 * @param sql sql语句中可能包含一个或者多个'?'作为参数的占位符
	 * @return 返回一个包含预编译sql语句的PreparedStatement对象
	 */
	public java.sql.PreparedStatement prepareStatement(String sql)
			throws SQLException {
		return prepareStatement(sql, DEFAULT_RESULT_SET_TYPE,
				DEFAULT_RESULT_SET_CONCURRENCY);
	}
	/**
	 * JDBC 2.0规范与上面的prepareStatement()相同,但是允许默认的resultSetType和resultSetConcurrencyType被重写
	 * @param sql 包含占位符的sql语句
	 * @param resultSetType see ResultSet.TYPE_XXX
	 * @param resultSetConcurrency see ResultSet.CONCUR_XXX
	 * @return 返回一个包含预编译SQL语句的PreparedStatement对象
	 */
	public java.sql.PreparedStatement prepareStatement(String sql,
			int resultSetType, int resultSetConcurrency) throws SQLException {
		synchronized (getConnectionMutex()) {
			checkClosed();
			//
			// FIXME: Create warnings if can't create results of the given
			// type or concurrency
			//
			PreparedStatement pStmt = null;
			boolean canServerPrepare = true;
			String nativeSql = getProcessEscapeCodesForPrepStmts() ? nativeSQL(sql): sql;
			if (this.useServerPreparedStmts && getEmulateUnsupportedPstmts()) {
				canServerPrepare = canHandleAsServerPreparedStatement(nativeSql);
			}
			//this.useServerPreparedStmts 表示是否使用预编译功能 通过PreparedStatement的子类ServerPreparedStatement来实现PreparedStaetment的预编译功能
			//`com.mysql.jdbc.ServerPreparedStatement`中的`serverExecute`方法负责告诉server使用当前提供的参数来动态绑定到编译好的sql语句上
			if (this.useServerPreparedStmts && canServerPrepare) {//使用预编译功能
				if (this.getCachePreparedStatements()) {
					synchronized (this.serverSideStatementCache) {
						pStmt = (com.mysql.jdbc.ServerPreparedStatement)this.serverSideStatementCache.remove(sql);
						if (pStmt != null) {
							((com.mysql.jdbc.ServerPreparedStatement)pStmt).setClosed(false);
							pStmt.clearParameters();
						}
						if (pStmt == null) {
							try {
								pStmt = ServerPreparedStatement.getInstance(getLoadBalanceSafeProxy(), nativeSql,
										this.database, resultSetType, resultSetConcurrency);
								if (sql.length() < getPreparedStatementCacheSqlLimit()) {
									((com.mysql.jdbc.ServerPreparedStatement)pStmt).isCached = true;
								}
								pStmt.setResultSetType(resultSetType);
								pStmt.setResultSetConcurrency(resultSetConcurrency);
							} catch (SQLException sqlEx) {
								// Punt, if necessary
								if (getEmulateUnsupportedPstmts()) {
									pStmt = (PreparedStatement) clientPrepareStatement(nativeSql, resultSetType, resultSetConcurrency, false);
									if (sql.length() < getPreparedStatementCacheSqlLimit()) {
										this.serverSideStatementCheckCache.put(sql, Boolean.FALSE);
									}
								} else {
									throw sqlEx;
								}
							}
						}
					}
				} else {
					try {
						pStmt = ServerPreparedStatement.getInstance(getLoadBalanceSafeProxy(), nativeSql,
								this.database, resultSetType, resultSetConcurrency);
						pStmt.setResultSetType(resultSetType);
						pStmt.setResultSetConcurrency(resultSetConcurrency);
					} catch (SQLException sqlEx) {
						// Punt, if necessary
						if (getEmulateUnsupportedPstmts()) {
							pStmt = (PreparedStatement) clientPrepareStatement(nativeSql, resultSetType, resultSetConcurrency, false);
						} else {
							throw sqlEx;
						}
					}
				}
			} else {
				pStmt = (PreparedStatement) clientPrepareStatement(nativeSql, resultSetType, resultSetConcurrency, false);
			}
			return pStmt;
		}
	}
	/**
	 * @see Connection#prepareStatement(String, int, int, int)
	 */
	public java.sql.PreparedStatement prepareStatement(String sql,
			int resultSetType, int resultSetConcurrency,
			int resultSetHoldability) throws SQLException {
		if (getPedantic()) {
			if (resultSetHoldability != java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT) {
				throw SQLError.createSQLException(
						"HOLD_CUSRORS_OVER_COMMIT is only supported holdability level",
						SQLError.SQL_STATE_ILLEGAL_ARGUMENT, getExceptionInterceptor());
			}
		}
		return prepareStatement(sql, resultSetType, resultSetConcurrency);
	}
	/**
	 * @see Connection#prepareStatement(String, int[])
	 */
	public java.sql.PreparedStatement prepareStatement(String sql,
			int[] autoGenKeyIndexes) throws SQLException {
		java.sql.PreparedStatement pStmt = prepareStatement(sql);
		((com.mysql.jdbc.PreparedStatement) pStmt)
				.setRetrieveGeneratedKeys((autoGenKeyIndexes != null)
						&& (autoGenKeyIndexes.length > 0));
		return pStmt;
	}
	/**
	 * @see Connection#prepareStatement(String, String[])
	 */
	public java.sql.PreparedStatement prepareStatement(String sql,
			String[] autoGenKeyColNames) throws SQLException {
		java.sql.PreparedStatement pStmt = prepareStatement(sql);
		((com.mysql.jdbc.PreparedStatement) pStmt)
				.setRetrieveGeneratedKeys((autoGenKeyColNames != null)
						&& (autoGenKeyColNames.length > 0));
		return pStmt;
	}
	
```
ResultSet中常用的参数常量主要有:
```
    //按正向处理结果集中的行(从第一个到最后一个) 可以通过方法setFetchDirection来设置
    int FETCH_FORWARD = 1000;
    //按逆向处理结果集中的行(从最后一个到第一个)可以通过方法setFetchDirection来设置
    int FETCH_REVERSE = 1001;
    //结果集中的行处理顺序未知 可以通过方法setFetchDirection来设置
    int FETCH_UNKNOWN = 1002;
    //ResultSet的游标只能向前移动
	int TYPE_FORWARD_ONLY = 1003;
    //ResultSet的游标可以滚动 但是对于ResultSet中的数据变化不敏感
    int TYPE_SCROLL_INSENSITIVE = 1004;
	//ResultSet的游标可以滚动 对于ResultSet中的数据变化敏感
    int TYPE_SCROLL_SENSITIVE = 1005;
    //不可以更新的ResultSet的并发模式
    int CONCUR_READ_ONLY = 1007;
	//可以更新的ResultSet的并发模式
    int CONCUR_UPDATABLE = 1008;
    //当调用connection.commit时保持ResultSet不被关闭
    int HOLD_CURSORS_OVER_COMMIT = 1;
	//当调用connection.commit时应关闭ResultSet
    int CLOSE_CURSORS_AT_COMMIT = 2;
    
```

